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
import mone.api.Settings;
import mone.api.cache.IWaypoint;
import mone.api.cache.IWorldData;
import mone.api.pathing.goals.GoalNear;
import mone.api.process.PathingCommand;
import mone.api.process.PathingCommandType;
import mone.api.utils.BetterBlockPos;
import mone.api.utils.Rotation;
import mone.api.utils.RotationUtils;
import mone.api.utils.input.Input;
import mone.utils.MoneProcessHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;

import java.util.Optional;

/**
 * Smart Auto-Drop-Off process.
 * When inventory fills up during #mine or #farm (or when triggered via #dropoff now),
 * paths to the configured storage waypoint, opens a nearby chest/barrel, deposits
 * non-essential loot, and returns to resume the previous task.
 */
public final class AutoDropOffProcess extends MoneProcessHelper {

    private enum Phase {
        IDLE,
        GOING_TO_STORAGE,
        OPENING_CHEST,
        DEPOSITING,
        RETURNING
    }

    private Phase phase = Phase.IDLE;
    private BetterBlockPos storagePos = null;
    private BetterBlockPos returnPos = null;
    private BlockPos targetChestPos = null;
    private int phaseTicks = 0;
    private boolean manualTrigger = false;

    public AutoDropOffProcess(Mone mone) {
        super(mone);
    }

    public boolean triggerNow() {
        BetterBlockPos wp = resolveStorageLocation();
        if (wp == null) {
            logDirect("[DropOff] No storage waypoint found. Run '#dropoff set' next to your storage chest first.");
            return false;
        }
        this.storagePos = wp;
        this.returnPos = ctx.playerFeet();
        this.targetChestPos = null;
        this.phase = Phase.GOING_TO_STORAGE;
        this.phaseTicks = 0;
        this.manualTrigger = true;
        logDirect("[DropOff] Heading to storage at " + wp.x + ", " + wp.y + ", " + wp.z);
        return true;
    }

    @Override
    public boolean isActive() {
        if (ctx.player() == null || ctx.world() == null) {
            reset();
            return false;
        }
        if (phase != Phase.IDLE) {
            return true;
        }
        Settings settings = MoneAPI.getSettings();
        if (!settings.autoDropOff.value) {
            return false;
        }
        // Only auto-trigger while gathering (#mine or #farm)
        if (!Mone.getMineProcess().isActive() && !Mone.getFarmProcess().isActive()) {
            return false;
        }
        int freeSlots = countFreeMainSlots(ctx.player().getInventory());
        if (freeSlots <= settings.autoDropOffMinFreeSlots.value) {
            BetterBlockPos wp = resolveStorageLocation();
            if (wp != null) {
                this.storagePos = wp;
                this.returnPos = ctx.playerFeet();
                this.targetChestPos = null;
                this.phase = Phase.GOING_TO_STORAGE;
                this.phaseTicks = 0;
                this.manualTrigger = false;
                logDirect("[DropOff] Inventory nearly full (" + freeSlots + " free). Heading to storage...");
                return true;
            }
        }
        return false;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        phaseTicks++;
        LocalPlayer player = ctx.player();

        switch (phase) {
            case GOING_TO_STORAGE -> {
                if (calcFailed || storagePos == null) {
                    logDirect("[DropOff] Could not reach storage, cancelling drop-off.");
                    reset();
                    return new PathingCommand(null, PathingCommandType.DEFER);
                }
                if (ctx.playerFeet().distSqr(storagePos) <= 16.0) {
                    targetChestPos = findNearestContainer(storagePos, 5);
                    if (targetChestPos == null) {
                        logDirect("[DropOff] No chest/barrel found near storage waypoint.");
                        reset();
                        return new PathingCommand(null, PathingCommandType.DEFER);
                    }
                    phase = Phase.OPENING_CHEST;
                    phaseTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                return new PathingCommand(new GoalNear(storagePos, 3), PathingCommandType.SET_GOAL_AND_PATH);
            }

            case OPENING_CHEST -> {
                if (player.containerMenu != player.inventoryMenu) {
                    Mone.getInputOverrideHandler().clearAllKeys();
                    phase = Phase.DEPOSITING;
                    phaseTicks = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                if (phaseTicks > 80 || targetChestPos == null) {
                    logDirect("[DropOff] Timed out opening storage chest.");
                    reset();
                    return new PathingCommand(null, PathingCommandType.DEFER);
                }
                Optional<Rotation> rot = RotationUtils.reachable(ctx, targetChestPos);
                if (rot.isPresent()) {
                    Mone.getLookBehavior().updateTarget(rot.get(), true);
                    if (ctx.isLookingAt(targetChestPos) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                        Mone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, phaseTicks % 4 == 0);
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                return new PathingCommand(new GoalNear(targetChestPos, 2), PathingCommandType.SET_GOAL_AND_PATH);
            }

            case DEPOSITING -> {
                Mone.getInputOverrideHandler().clearAllKeys();
                AbstractContainerMenu menu = player.containerMenu;
                if (menu == player.inventoryMenu) {
                    // Container closed
                    finishDeposit();
                    return new PathingCommand(null, PathingCommandType.SET_GOAL_AND_PATH);
                }
                if (phaseTicks > 200) {
                    player.closeContainer();
                    finishDeposit();
                    return new PathingCommand(null, PathingCommandType.DEFER);
                }
                // Move 1 non-essential stack every 2 ticks
                if (phaseTicks % 2 == 0 && ctx.minecraft().gameMode != null) {
                    int totalSlots = menu.slots.size();
                    int playerStart = Math.max(0, totalSlots - 36);
                    boolean movedAny = false;
                    for (int s = playerStart; s < totalSlots; s++) {
                        Slot slot = menu.slots.get(s);
                        ItemStack stack = slot.getItem();
                        if (!stack.isEmpty() && shouldDeposit(stack)) {
                            ctx.minecraft().gameMode.handleContainerInput(
                                    menu.containerId, s, 0, ContainerInput.QUICK_MOVE, player
                            );
                            movedAny = true;
                            break;
                        }
                    }
                    if (!movedAny) {
                        player.closeContainer();
                        finishDeposit();
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            case RETURNING -> {
                if (manualTrigger || returnPos == null || calcFailed || ctx.playerFeet().distSqr(returnPos) <= 16.0) {
                    logDirect("[DropOff] Complete!");
                    reset();
                    return new PathingCommand(null, PathingCommandType.DEFER);
                }
                return new PathingCommand(new GoalNear(returnPos, 3), PathingCommandType.SET_GOAL_AND_PATH);
            }

            default -> {
                reset();
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
        }
    }

    private void finishDeposit() {
        if (manualTrigger || returnPos == null) {
            logDirect("[DropOff] Deposit complete!");
            reset();
        } else {
            logDirect("[DropOff] Deposit complete, returning to previous area...");
            phase = Phase.RETURNING;
            phaseTicks = 0;
        }
    }

    private static boolean shouldDeposit(ItemStack stack) {
        // Keep tools, weapons, armor, food, torches, buckets, fireworks, and totems
        if (stack.isDamageableItem() || stack.has(DataComponents.FOOD) || stack.has(DataComponents.TOOL)) {
            return false;
        }
        var item = stack.getItem();
        if (item == Items.TORCH || item == Items.SOUL_TORCH
                || item == Items.WATER_BUCKET || item == Items.BUCKET
                || item == Items.TOTEM_OF_UNDYING || item == Items.FIREWORK_ROCKET
                || item == Items.SHIELD || item == Items.ENDER_PEARL) {
            return false;
        }
        return true;
    }

    private static int countFreeMainSlots(Inventory inv) {
        int free = 0;
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private BetterBlockPos resolveStorageLocation() {
        IWorldData wd = Mone.getWorldProvider().getCurrentWorld();
        if (wd == null || wd.getWaypoints() == null) {
            return null;
        }
        String targetName = MoneAPI.getSettings().autoDropOffWaypoint.value;
        if (targetName != null && !targetName.isBlank()) {
            for (IWaypoint wp : wd.getWaypoints().getAllWaypoints()) {
                if (targetName.equalsIgnoreCase(wp.getName())) {
                    return wp.getLocation();
                }
            }
        }
        // Fallback to HOME waypoint
        IWaypoint home = wd.getWaypoints().getMostRecentByTag(IWaypoint.Tag.HOME);
        return home != null ? home.getLocation() : null;
    }

    private BlockPos findNearestContainer(BetterBlockPos center, int radius) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 3; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    pos.set(center.x + dx, center.y + dy, center.z + dz);
                    Block b = ctx.world().getBlockState(pos).getBlock();
                    if (b instanceof ChestBlock || b instanceof BarrelBlock) {
                        double d = pos.distSqr(center);
                        if (d < bestDistSq) {
                            bestDistSq = d;
                            best = pos.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    private void reset() {
        Mone.getInputOverrideHandler().clearAllKeys();
        phase = Phase.IDLE;
        storagePos = null;
        returnPos = null;
        targetChestPos = null;
        phaseTicks = 0;
        manualTrigger = false;
    }

    @Override
    public void onLostControl() {
        reset();
    }

    @Override
    public String displayName0() {
        return switch (phase) {
            case GOING_TO_STORAGE -> "Drop-Off (To Storage)";
            case OPENING_CHEST -> "Drop-Off (Opening Chest)";
            case DEPOSITING -> "Drop-Off (Depositing)";
            case RETURNING -> "Drop-Off (Returning)";
            default -> "Auto Drop-Off";
        };
    }

    @Override
    public double priority() {
        return 6.0;
    }

    @Override
    public boolean isTemporary() {
        return true;
    }
}
