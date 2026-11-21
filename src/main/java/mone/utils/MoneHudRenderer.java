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

package mone.utils;

import mone.api.IMone;
import mone.api.MoneAPI;
import mone.api.behavior.IPathingBehavior;
import mone.api.pathing.goals.Goal;
import mone.api.pathing.goals.GoalBlock;
import mone.api.pathing.goals.GoalNear;
import mone.api.pathing.goals.GoalRunAway;
import mone.api.pathing.goals.GoalStrictDirection;
import mone.api.pathing.goals.GoalXZ;
import mone.api.pathing.goals.GoalYLevel;
import mone.api.process.IMoneProcess;
import mone.process.AutoDropOffProcess;
import mone.process.AutoEatProcess;
import mone.process.BackfillProcess;
import mone.process.BuilderProcess;
import mone.process.CustomGoalProcess;
import mone.process.ElytraProcess;
import mone.process.ExploreProcess;
import mone.process.FarmProcess;
import mone.process.FollowProcess;
import mone.process.GetToBlockProcess;
import mone.process.InventoryPauserProcess;
import mone.process.MineProcess;
import mone.process.SurvivalSafetyProcess;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Optional;

/**
 * Active-work HUD overlay (#hud).
 * Renders nothing when idle; when a Mone process is running, draws a fading gradient border
 * around all four edges of the screen and a top-center status banner.
 */
public final class MoneHudRenderer {

    private static final int GRADIENT_DEPTH = 26;
    private static final int GRADIENT_RGB = 0x38E8D4; // Sleek cyan/teal glow

    private MoneHudRenderer() {}

    public static void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        if (!MoneAPI.getSettings().hud.value) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        if (mc.gui.screen() instanceof FullscreenMapScreen) {
            return;
        }

        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mone == null) {
            return;
        }

        String workDesc = getActiveWorkDescription(mone);
        if (workDesc == null) {
            // Idle: draw nothing
            return;
        }

        int w = guiGraphics.guiWidth();
        int h = guiGraphics.guiHeight();

        // Subtle breathing pulse on border alpha
        double pulse = 0.85 + 0.15 * Math.sin(System.currentTimeMillis() / 450.0);
        int maxAlpha = (int) Math.round(110 * pulse);

        // Top & bottom fading gradients toward center
        int edgeColor = (maxAlpha << 24) | GRADIENT_RGB;
        int clearColor = GRADIENT_RGB; // alpha 0
        guiGraphics.fillGradient(0, 0, w, GRADIENT_DEPTH, edgeColor, clearColor);
        guiGraphics.fillGradient(0, h - GRADIENT_DEPTH, w, h, clearColor, edgeColor);

        // Left & right fading gradients toward center (vertical strips)
        for (int i = 0; i < GRADIENT_DEPTH; i++) {
            float t = 1.0f - (float) i / (float) GRADIENT_DEPTH;
            int a = (int) (maxAlpha * t * t);
            if (a <= 0) {
                continue;
            }
            int col = (a << 24) | GRADIENT_RGB;
            guiGraphics.fill(i, 0, i + 1, h, col);
            guiGraphics.fill(w - 1 - i, 0, w - i, h, col);
        }

        // Crisp 1px outer accent line
        int borderLine = (Math.min(255, maxAlpha + 70) << 24) | GRADIENT_RGB;
        guiGraphics.outline(0, 0, w, h, borderLine);

        // Top-center banner: "Mone is working. (<work>)"
        Font font = mc.font;
        String banner = "Mone is working. (" + workDesc + ")";
        int textW = font.width(banner);
        int padX = 10;
        int pillW = textW + padX * 2;
        int pillH = 18;
        int pillX = (w - pillW) / 2;
        int pillY = 6;

        guiGraphics.fill(pillX, pillY, pillX + pillW, pillY + pillH, 0xCC0E141C);
        guiGraphics.outline(pillX, pillY, pillW, pillH, 0xCC38E8D4);
        guiGraphics.centeredText(font, banner, w / 2, pillY + 5, 0xFFFFFFFF);
    }

    private static String getActiveWorkDescription(IMone mone) {
        Optional<IMoneProcess> activeProc = mone.getPathingControlManager().mostRecentInControl();
        IPathingBehavior pathing = mone.getPathingBehavior();
        boolean isPathing = pathing != null && (pathing.isPathing() || pathing.hasPath() || pathing.getInProgress().isPresent());

        if (activeProc.isPresent() && activeProc.get().isActive()) {
            IMoneProcess proc = activeProc.get();
            if (proc instanceof MineProcess mp) {
                return mp.getFriendlySummary();
            }
            if (proc instanceof CustomGoalProcess) {
                return formatGoal(pathing != null ? pathing.getGoal() : null);
            }
            if (proc instanceof FarmProcess) {
                return "Farming";
            }
            if (proc instanceof ExploreProcess) {
                return "Exploring";
            }
            if (proc instanceof FollowProcess) {
                return "Following";
            }
            if (proc instanceof BuilderProcess) {
                return "Building";
            }
            if (proc instanceof GetToBlockProcess) {
                return "Finding block";
            }
            if (proc instanceof ElytraProcess) {
                return "Elytra flying";
            }
            if (proc instanceof AutoEatProcess) {
                return "Eating food";
            }
            if (proc instanceof SurvivalSafetyProcess) {
                return "Evading enemy mobs";
            }
            if (proc instanceof AutoDropOffProcess) {
                return proc.displayName();
            }
            if (proc instanceof BackfillProcess) {
                return "Backfilling";
            }
            if (proc instanceof InventoryPauserProcess) {
                return "Managing inventory";
            }
            return "Working";
        }

        if (isPathing) {
            return formatGoal(pathing.getGoal());
        }

        return null;
    }

    private static String formatGoal(Goal goal) {
        if (goal == null) {
            return "Pathing";
        }
        if (goal instanceof GoalBlock gb) {
            return "Going to " + gb.x + ", " + gb.y + ", " + gb.z;
        }
        if (goal instanceof GoalXZ gxz) {
            return "Going to " + gxz.getX() + ", " + gxz.getZ();
        }
        if (goal instanceof GoalNear gn) {
            var p = gn.getGoalPos();
            return "Going near " + p.getX() + ", " + p.getY() + ", " + p.getZ();
        }
        if (goal instanceof GoalStrictDirection) {
            return "Tunneling";
        }
        if (goal instanceof GoalYLevel gy) {
            return "Going to Y=" + gy.level;
        }
        if (goal instanceof GoalRunAway) {
            return "Running away";
        }
        return "Pathing";
    }
}
