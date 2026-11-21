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
import mone.api.pathing.goals.GoalRunAway;
import mone.api.process.PathingCommand;
import mone.api.process.PathingCommandType;
import mone.utils.McCompat;
import mone.utils.MoneProcessHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Survival safety & dynamic enemy mob evasion process.
 * - Continuously readjusts pathing using GoalRunAway to evade nearby hostile mobs.
 * - Triggers emergency retreat at low health.
 * - Swaps out tools before they break when a healthier backup is in the inventory.
 */
public final class SurvivalSafetyProcess extends MoneProcessHelper {

    private static final int REEVALUATE_INTERVAL_TICKS = 10;

    private boolean evading = false;
    private int ticksSinceReeval = 0;
    private int lastThreatCount = 0;

    public SurvivalSafetyProcess(Mone mone) {
        super(mone);
    }

    @Override
    public boolean isActive() {
        Settings settings = MoneAPI.getSettings();
        if (!settings.survivalSafety.value || ctx.player() == null || ctx.world() == null) {
            evading = false;
            return false;
        }

        LocalPlayer player = ctx.player();
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            evading = false;
            return false;
        }

        // Check tool durability protection passively every tick
        if (settings.swapLowDurabilityTool.value) {
            checkAndSwapLowDurabilityTool(player);
        }

        if (!settings.evadeEnemyMobs.value && player.getHealth() > settings.lowHealthRetreatThreshold.value) {
            evading = false;
            return false;
        }

        double triggerRange = evading ? settings.evadeSafeDistance.value : settings.evadeMobDistance.value;
        if (player.getHealth() <= settings.lowHealthRetreatThreshold.value) {
            triggerRange = Math.max(triggerRange, settings.evadeSafeDistance.value);
        }

        List<BlockPos> threats = findNearbyThreats(player, triggerRange);
        if (threats.isEmpty()) {
            if (evading) {
                logDirect("[Safety] Safe from enemy mobs, resuming.");
            }
            evading = false;
            ticksSinceReeval = 0;
            lastThreatCount = 0;
            return false;
        }

        return true;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        Settings settings = MoneAPI.getSettings();
        LocalPlayer player = ctx.player();

        double safeDist = Math.max(settings.evadeSafeDistance.value, settings.evadeMobDistance.value + 4.0);
        List<BlockPos> threats = findNearbyThreats(player, safeDist);
        if (threats.isEmpty()) {
            evading = false;
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        if (!evading) {
            evading = true;
            ticksSinceReeval = REEVALUATE_INTERVAL_TICKS;
            logDirect("[Safety] Evading " + threats.size() + " nearby enemy mob(s)!");
        }

        GoalRunAway runAwayGoal = new GoalRunAway(safeDist, threats.toArray(new BlockPos[0]));

        // Periodically readjust the path as enemy mobs move toward us
        ticksSinceReeval++;
        if (calcFailed || ticksSinceReeval >= REEVALUATE_INTERVAL_TICKS || threats.size() != lastThreatCount) {
            ticksSinceReeval = 0;
            lastThreatCount = threats.size();
            return new PathingCommand(runAwayGoal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
        }

        return new PathingCommand(runAwayGoal, PathingCommandType.SET_GOAL_AND_PATH);
    }

    private List<BlockPos> findNearbyThreats(LocalPlayer player, double range) {
        double rangeSq = range * range;
        List<BlockPos> threats = new ArrayList<>();
        for (Entity entity : ctx.entities()) {
            if (entity == null || entity == player || !entity.isAlive() || entity.isRemoved()) {
                continue;
            }
            if (!(entity instanceof Enemy) && entity.getType().getCategory() != MobCategory.MONSTER) {
                continue;
            }
            // Ignore passive Endermen and un-aggroed Zombified Piglins
            if (!McCompat.isNotPassiveEnderman(entity)) {
                continue;
            }
            if (entity instanceof ZombifiedPiglin zp && zp.getLastHurtByMob() == null) {
                continue;
            }
            if (entity.distanceToSqr(player) <= rangeSq) {
                threats.add(entity.blockPosition());
            }
        }
        return threats;
    }

    private void checkAndSwapLowDurabilityTool(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int selected = inv.getSelectedSlot();
        ItemStack held = inv.getItem(selected);
        if (held.isEmpty() || !held.isDamageableItem()) {
            return;
        }
        int remaining = held.getMaxDamage() - held.getDamageValue();
        if (remaining > 3) {
            return;
        }

        // Look for a healthier replacement of the same item type in inventory
        for (int i = 0; i < 36; i++) {
            if (i == selected) {
                continue;
            }
            ItemStack candidate = inv.getItem(i);
            if (!candidate.isEmpty()
                    && candidate.getItem() == held.getItem()
                    && candidate.isDamageableItem()
                    && (candidate.getMaxDamage() - candidate.getDamageValue()) > 10) {
                if (i < 9) {
                    inv.setSelectedSlot(i);
                } else if (ctx.minecraft().gameMode != null) {
                    ctx.minecraft().gameMode.handleContainerInput(0, i, selected, ContainerInput.SWAP, player);
                }
                logDirect("[Safety] Swapped low-durability " + held.getHoverName().getString());
                return;
            }
        }
    }

    @Override
    public void onLostControl() {
        evading = false;
        ticksSinceReeval = 0;
        lastThreatCount = 0;
    }

    @Override
    public String displayName0() {
        return "Evading Enemy Mobs";
    }

    @Override
    public double priority() {
        return 8.0; // Highest priority so survival overrides mining/pathing
    }

    @Override
    public boolean isTemporary() {
        return true;
    }
}
