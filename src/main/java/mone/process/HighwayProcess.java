/*
 * This file is part of Mone.
 *
 * Mone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Mone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Mone.  If not, see <https://www.gnu.org/licenses/>.
 */

package mone.process;

import mone.Mone;
import mone.api.MoneAPI;
import mone.api.pathing.goals.GoalBlock;
import mone.api.process.PathingCommand;
import mone.api.process.PathingCommandType;
import mone.api.utils.BetterBlockPos;
import mone.api.utils.Rotation;
import mone.api.utils.RotationUtils;
import mone.api.utils.input.Input;
import mone.pathing.movement.MovementHelper;
import mone.utils.MoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * Automated highway / tunnel & bridge builder (#highway).
 * Mines a tunnel in the chosen cardinal direction, bridges air/lava floor gaps
 * using throwaway blocks, and places protective side railings.
 */
public final class HighwayProcess extends MoneProcessHelper {

    private boolean active = false;
    private Direction direction = Direction.NORTH;
    private int width = 3;
    private int height = 3;
    private int originX;
    private int originY;
    private int originZ;
    private int progress = 0;
    private int tickCounter = 0;

    public HighwayProcess(Mone mone) {
        super(mone);
    }

    public void start(Direction dir, int width, int height) {
        this.direction = (dir.getAxis().isHorizontal()) ? dir : Direction.NORTH;
        this.width = Math.max(1, Math.min(5, width));
        this.height = Math.max(2, Math.min(4, height));
        BetterBlockPos start = ctx.playerFeet();
        this.originX = start.x;
        this.originY = start.y;
        this.originZ = start.z;
        this.progress = 0;
        this.tickCounter = 0;
        this.active = true;
        logDirect("Starting " + this.width + "x" + this.height + " highway heading " + this.direction.getName());
    }

    public void stop() {
        this.active = false;
        Mone.getInputOverrideHandler().clearAllKeys();
    }

    @Override
    public boolean isActive() {
        return active && ctx.player() != null && ctx.world() != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        tickCounter++;
        Mone.getInputOverrideHandler().clearAllKeys();

        Direction right = direction.getClockWise();
        int halfW = width / 2;

        // Current slice center
        int cx = originX + direction.getStepX() * progress;
        int cy = originY;
        int cz = originZ + direction.getStepZ() * progress;

        // 1. Bridge any air or fluid floor gaps in the current or next slice
        for (int step = 0; step <= 1; step++) {
            int fx = cx + direction.getStepX() * step;
            int fz = cz + direction.getStepZ() * step;
            for (int w = -halfW; w <= halfW; w++) {
                BlockPos floorPos = new BlockPos(fx + right.getStepX() * w, cy - 1, fz + right.getStepZ() * w);
                BlockState floorState = ctx.world().getBlockState(floorPos);
                if (floorState.isAir() || !floorState.getFluidState().isEmpty()) {
                    if (tryPlaceThrowaway(floorPos)) {
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                }
            }
        }

        // 2. Clear blocks inside the tunnel cross-section for the next slice ahead
        int nextX = cx + direction.getStepX();
        int nextZ = cz + direction.getStepZ();
        for (int yOff = 0; yOff < height; yOff++) {
            for (int w = -halfW; w <= halfW; w++) {
                BlockPos digPos = new BlockPos(nextX + right.getStepX() * w, cy + yOff, nextZ + right.getStepZ() * w);
                BlockState state = ctx.world().getBlockState(digPos);
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    Optional<Rotation> rot = RotationUtils.reachable(ctx, digPos);
                    if (rot.isPresent()) {
                        Mone.getLookBehavior().updateTarget(rot.get(), true);
                        MovementHelper.switchToBestToolFor(ctx, state);
                        if (ctx.isLookingAt(digPos) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                            Mone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                        }
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                }
            }
        }

        // 3. Advance forward one step along the highway
        BetterBlockPos target = new BetterBlockPos(nextX, cy, nextZ);
        if (ctx.playerFeet().distSqr(target) <= 2.0) {
            progress++;
        }
        return new PathingCommand(new GoalBlock(target), PathingCommandType.SET_GOAL_AND_PATH);
    }

    private boolean tryPlaceThrowaway(BlockPos placePos) {
        int slot = findThrowawayHotbarSlot(ctx.player().getInventory());
        if (slot < 0) {
            return false;
        }
        // Find a solid neighbor face to place against
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = placePos.relative(dir);
            BlockState nState = ctx.world().getBlockState(neighbor);
            if (!nState.isAir() && nState.getFluidState().isEmpty()) {
                Optional<Rotation> rot = RotationUtils.reachable(ctx, neighbor);
                if (rot.isPresent()) {
                    ctx.player().getInventory().setSelectedSlot(slot);
                    Mone.getLookBehavior().updateTarget(rot.get(), true);
                    if (ctx.isLookingAt(neighbor) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                        Mone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, tickCounter % 3 == 0);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static int findThrowawayHotbarSlot(Inventory inv) {
        List<Item> allowed = MoneAPI.getSettings().acceptableThrowawayItems.value;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && allowed.contains(stack.getItem())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onLostControl() {
        stop();
    }

    @Override
    public String displayName0() {
        return "Building " + width + "x" + height + " highway (" + direction.getName() + ")";
    }

    @Override
    public double priority() {
        return 3.0;
    }
}
