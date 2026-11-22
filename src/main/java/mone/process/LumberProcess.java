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
import mone.api.pathing.goals.Goal;
import mone.api.pathing.goals.GoalBlock;
import mone.api.pathing.goals.GoalComposite;
import mone.api.pathing.goals.GoalGetToBlock;
import mone.api.process.PathingCommand;
import mone.api.process.PathingCommandType;
import mone.api.utils.BetterBlockPos;
import mone.api.utils.Rotation;
import mone.api.utils.RotationUtils;
import mone.api.utils.input.Input;
import mone.utils.MoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Dedicated tree-chopping & sapling replanting process (#lumber).
 * Locates nearby tree logs, chops them, collects dropped logs/saplings, and replants saplings.
 */
public final class LumberProcess extends MoneProcessHelper {

    private boolean active = false;
    private int radius = 48;
    private BetterBlockPos origin = null;
    private final List<BlockPos> replantSpots = new ArrayList<>();
    private int tickCounter = 0;

    public LumberProcess(Mone mone) {
        super(mone);
    }

    public void start(int searchRadius) {
        this.radius = Math.max(8, Math.min(128, searchRadius));
        this.origin = ctx.playerFeet();
        this.replantSpots.clear();
        this.active = true;
        this.tickCounter = 0;
        logDirect("Starting lumberjack mode (radius " + this.radius + " blocks)");
    }

    public void stop() {
        this.active = false;
        this.replantSpots.clear();
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

        // 1. Replant sapling if we have one and a recently cleared stump spot is nearby
        int saplingSlot = findSaplingHotbarSlot(ctx.player().getInventory());
        if (saplingSlot >= 0 && !replantSpots.isEmpty()) {
            BlockPos spot = replantSpots.get(0);
            BlockState atSpot = ctx.world().getBlockState(spot);
            BlockState below = ctx.world().getBlockState(spot.below());
            if (!atSpot.isAir() || (!below.is(BlockTags.DIRT) && below.getBlock() != Blocks.GRASS_BLOCK)) {
                replantSpots.remove(0);
            } else if (ctx.playerFeet().distSqr(spot) <= 20.0) {
                Optional<Rotation> rot = RotationUtils.reachable(ctx, spot.below());
                if (rot.isPresent()) {
                    ctx.player().getInventory().setSelectedSlot(saplingSlot);
                    Mone.getLookBehavior().updateTarget(rot.get(), true);
                    if (ctx.isLookingAt(spot.below()) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                        Mone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, tickCounter % 3 == 0);
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
            }
        }

        // 2. Find nearby logs
        List<BlockPos> logs = scanNearbyLogs();
        if (!logs.isEmpty()) {
            // Remember stump bases for replanting
            BlockPos closest = logs.get(0);
            BlockState belowClosest = ctx.world().getBlockState(closest.below());
            if ((belowClosest.is(BlockTags.DIRT) || belowClosest.getBlock() == Blocks.GRASS_BLOCK)
                    && !replantSpots.contains(closest) && replantSpots.size() < 16) {
                replantSpots.add(closest);
            }

            int limit = Math.min(logs.size(), 16);
            Goal[] goals = new Goal[limit];
            for (int i = 0; i < limit; i++) {
                goals[i] = new GoalGetToBlock(logs.get(i));
            }
            return new PathingCommand(new GoalComposite(goals), PathingCommandType.SET_GOAL_AND_PATH);
        }

        // 3. Pick up nearby dropped logs/saplings
        List<Goal> itemGoals = new ArrayList<>();
        for (Entity e : ctx.entities()) {
            if (e instanceof ItemEntity item && e.isAlive()) {
                ItemStack stack = item.getItem();
                if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.SAPLINGS)) {
                    if (origin == null || e.blockPosition().distSqr(origin) <= (double) radius * radius) {
                        itemGoals.add(new GoalBlock(e.blockPosition()));
                    }
                }
            }
        }
        if (!itemGoals.isEmpty()) {
            return new PathingCommand(new GoalComposite(itemGoals.toArray(new Goal[0])), PathingCommandType.SET_GOAL_AND_PATH);
        }

        logDirect("No more trees in range, stopping lumberjack mode.");
        stop();
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private List<BlockPos> scanNearbyLogs() {
        BetterBlockPos center = origin != null ? origin : ctx.playerFeet();
        BetterBlockPos feet = ctx.playerFeet();
        int scanR = Math.min(radius, 36);
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int minY = Math.max(ctx.world().getMinY(), feet.y - 6);
        int maxY = Math.min(ctx.world().getMaxY() - 1, feet.y + 20);

        for (int dx = -scanR; dx <= scanR; dx++) {
            for (int dz = -scanR; dz <= scanR; dz++) {
                if (dx * dx + dz * dz > scanR * scanR) {
                    continue;
                }
                int wx = center.x + dx;
                int wz = center.z + dz;
                if (!ctx.world().hasChunk(wx >> 4, wz >> 4)) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    pos.set(wx, y, wz);
                    if (ctx.world().getBlockState(pos).is(BlockTags.LOGS)) {
                        found.add(pos.immutable());
                        if (found.size() >= 64) {
                            break;
                        }
                    }
                }
                if (found.size() >= 64) {
                    break;
                }
            }
            if (found.size() >= 64) {
                break;
            }
        }

        found.sort(Comparator.comparingDouble(p -> p.distSqr(feet)));
        return found;
    }

    private static int findSaplingHotbarSlot(Inventory inv) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.SAPLINGS)) {
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
        return "Chopping trees";
    }

    @Override
    public double priority() {
        return 3.0;
    }
}
