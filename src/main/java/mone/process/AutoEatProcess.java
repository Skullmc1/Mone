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
import mone.api.process.PathingCommand;
import mone.api.process.PathingCommandType;
import mone.api.utils.input.Input;
import mone.utils.MoneProcessHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Automatically pauses pathing to eat safe food from the hotbar or inventory
 * when hunger or health drops below configured thresholds.
 */
public final class AutoEatProcess extends MoneProcessHelper {

    private boolean eating = false;
    private int previousSlot = -1;
    private int eatSlot = -1;
    private int eatTicks = 0;

    public AutoEatProcess(Mone mone) {
        super(mone);
    }

    @Override
    public boolean isActive() {
        Settings settings = MoneAPI.getSettings();
        if (!settings.autoEat.value || ctx.player() == null || ctx.world() == null) {
            stopEating();
            return false;
        }
        LocalPlayer player = ctx.player();
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            stopEating();
            return false;
        }

        int food = player.getFoodData().getFoodLevel();
        float health = player.getHealth();

        if (eating) {
            if (food >= 20 || eatTicks > 80 || findBestFoodSlot(player.getInventory()) < 0) {
                stopEating();
                return false;
            }
            return true;
        }

        boolean hungry = food <= settings.autoEatHungerThreshold.value;
        boolean needRegen = health <= settings.autoEatHealthThreshold.value && food < 20;
        if ((hungry || needRegen) && findBestFoodSlot(player.getInventory()) >= 0) {
            return true;
        }
        return false;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        LocalPlayer player = ctx.player();
        Inventory inv = player.getInventory();

        if (!eating) {
            int slot = findBestFoodSlot(inv);
            if (slot < 0) {
                stopEating();
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
            previousSlot = inv.getSelectedSlot();
            if (slot >= 9) {
                // Swap food from main inventory into current hotbar slot
                int targetHotbar = previousSlot;
                if (ctx.minecraft().gameMode != null) {
                    ctx.minecraft().gameMode.handleContainerInput(0, slot, targetHotbar, ContainerInput.SWAP, player);
                    eatSlot = targetHotbar;
                } else {
                    return new PathingCommand(null, PathingCommandType.DEFER);
                }
            } else {
                eatSlot = slot;
            }
            eating = true;
            eatTicks = 0;
        }

        inv.setSelectedSlot(eatSlot);
        Mone.getInputOverrideHandler().clearAllKeys();
        Mone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
        eatTicks++;

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private void stopEating() {
        if (eating) {
            Mone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            if (ctx.player() != null && previousSlot >= 0 && previousSlot < 9) {
                ctx.player().getInventory().setSelectedSlot(previousSlot);
            }
        }
        eating = false;
        previousSlot = -1;
        eatSlot = -1;
        eatTicks = 0;
    }

    private static int findBestFoodSlot(Inventory inv) {
        int bestSlot = -1;
        int bestNutrition = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) {
                continue;
            }
            Item item = stack.getItem();
            if (isUnsafeFood(item)) {
                continue;
            }
            FoodProperties props = stack.get(DataComponents.FOOD);
            if (props == null) {
                continue;
            }
            // Prefer hotbar slightly, otherwise highest nutrition
            int score = props.nutrition() * 10 + (i < 9 ? 5 : 0);
            if (score > bestNutrition) {
                bestNutrition = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private static boolean isUnsafeFood(Item item) {
        return item == Items.ROTTEN_FLESH
                || item == Items.SPIDER_EYE
                || item == Items.PUFFERFISH
                || item == Items.POISONOUS_POTATO
                || item == Items.CHORUS_FRUIT
                || item == Items.SUSPICIOUS_STEW;
    }

    @Override
    public void onLostControl() {
        stopEating();
    }

    @Override
    public String displayName0() {
        return "Auto-Eating";
    }

    @Override
    public double priority() {
        return 7.0;
    }

    @Override
    public boolean isTemporary() {
        return true;
    }
}
