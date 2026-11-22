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
import mone.api.process.PathingCommand;
import mone.api.process.PathingCommandType;
import mone.api.utils.input.Input;
import mone.utils.MoneProcessHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Automated fishing process (#fish).
 * Casts a fishing rod, detects bobber splash/bite downward velocity, reels in,
 * re-casts after a brief cooldown, and swaps rods before breaking.
 */
public final class AutoFishProcess extends MoneProcessHelper {

    private boolean active = false;
    private int cooldownTicks = 0;
    private int castAgeTicks = 0;
    private int catches = 0;

    public AutoFishProcess(Mone mone) {
        super(mone);
    }

    public void start() {
        this.active = true;
        this.cooldownTicks = 0;
        this.castAgeTicks = 0;
        this.catches = 0;
        logDirect("Auto-Fish started. Face water with a fishing rod.");
    }

    public void stop() {
        this.active = false;
        Mone.getInputOverrideHandler().clearAllKeys();
    }

    public int getCatches() {
        return catches;
    }

    @Override
    public boolean isActive() {
        return active && ctx.player() != null && ctx.world() != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        LocalPlayer player = ctx.player();
        Mone.getInputOverrideHandler().clearAllKeys();

        if (!ensureFishingRod(player)) {
            logDirect("No usable fishing rod found. Stopping Auto-Fish.");
            stop();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        FishingHook hook = player.fishing;
        if (hook == null || hook.isRemoved()) {
            // Cast the rod
            Mone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            cooldownTicks = 20;
            castAgeTicks = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        castAgeTicks++;

        // Wait at least 30 ticks for bobber to settle in water before detecting bite
        if (castAgeTicks > 30) {
            Vec3 vel = hook.getDeltaMovement();
            boolean horizontalStill = Math.abs(vel.x) < 0.01 && Math.abs(vel.z) < 0.01;
            // Bobber plunges downward sharply when a fish bites
            if (horizontalStill && vel.y < -0.08) {
                Mone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                catches++;
                cooldownTicks = 16;
                castAgeTicks = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
        }

        // Re-cast if bobber gets stuck out of water for too long
        if (castAgeTicks > 1200) {
            Mone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            cooldownTicks = 20;
            castAgeTicks = 0;
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private boolean ensureFishingRod(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int sel = inv.getSelectedSlot();
        ItemStack held = inv.getItem(sel);
        if (isUsableRod(held)) {
            return true;
        }
        // Look in hotbar first
        for (int i = 0; i < 9; i++) {
            if (isUsableRod(inv.getItem(i))) {
                inv.setSelectedSlot(i);
                return true;
            }
        }
        // Look in main inventory and swap into current hotbar slot
        if (ctx.minecraft().gameMode != null) {
            for (int i = 9; i < 36; i++) {
                if (isUsableRod(inv.getItem(i))) {
                    ctx.minecraft().gameMode.handleContainerInput(0, i, sel, ContainerInput.SWAP, player);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isUsableRod(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof FishingRodItem)) {
            return false;
        }
        return !stack.isDamageableItem() || (stack.getMaxDamage() - stack.getDamageValue()) > 2;
    }

    @Override
    public void onLostControl() {
        stop();
    }

    @Override
    public String displayName0() {
        return catches > 0 ? ("Fishing (" + catches + " caught)") : "Fishing";
    }

    @Override
    public double priority() {
        return 3.0;
    }
}
