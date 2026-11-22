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

import mone.Mone;
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
import mone.api.utils.interfaces.IGoalRenderPos;
import mone.process.AutoDropOffProcess;
import mone.process.AutoEatProcess;
import mone.process.AutoFishProcess;
import mone.process.BackfillProcess;
import mone.process.BuilderProcess;
import mone.process.CustomGoalProcess;
import mone.process.ElytraProcess;
import mone.process.ExploreProcess;
import mone.process.FarmProcess;
import mone.process.FollowProcess;
import mone.process.GetToBlockProcess;
import mone.process.HighwayProcess;
import mone.process.InventoryPauserProcess;
import mone.process.LumberProcess;
import mone.process.MineProcess;
import mone.process.SurvivalSafetyProcess;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Active-work HUD overlay (#hud).
 * Renders nothing when idle; when a Mone process is running, draws a fading orange
 * gradient border around all four edges of the screen, a sleek black top-center status banner,
 * and a realistic EMA-smoothed ETA / Queue sub-pill.
 */
public final class MoneHudRenderer {

    private static final int GRADIENT_DEPTH = 26;
    private static final int GRADIENT_RGB = 0xFF6A1A; // Mone orange glow

    // Smoothed ETA tracking
    private static String trackedGoalKey = null;
    private static double lastDist = -1.0;
    private static long lastSampleMs = 0L;
    private static double smoothedSpeed = 4.5; // blocks/sec
    private static double smoothedEtaSec = -1.0;

    private MoneHudRenderer() {}

    public static void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        if (!MoneAPI.getSettings().hud.value) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            resetEta();
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
            resetEta();
            return;
        }

        int w = guiGraphics.guiWidth();
        int h = guiGraphics.guiHeight();

        // Subtle breathing pulse on border alpha
        double pulse = 0.85 + 0.15 * Math.sin(System.currentTimeMillis() / 450.0);
        int maxAlpha = (int) Math.round(115 * pulse);

        int edgeColor = (maxAlpha << 24) | GRADIENT_RGB;
        int clearColor = GRADIENT_RGB;
        guiGraphics.fillGradient(0, 0, w, GRADIENT_DEPTH, edgeColor, clearColor);
        guiGraphics.fillGradient(0, h - GRADIENT_DEPTH, w, h, clearColor, edgeColor);

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

        int borderLine = (Math.min(255, maxAlpha + 80) << 24) | 0xFF7A29;
        guiGraphics.outline(0, 0, w, h, borderLine);

        // Top-center black banner with orange accent
        Font font = mc.font;
        String banner = "Mone is working. (" + workDesc + ")";
        int textW = font.width(banner);
        int padX = 10;
        int pillW = textW + padX * 2;
        int pillH = 18;
        int pillX = (w - pillW) / 2;
        int pillY = 6;

        guiGraphics.fill(pillX, pillY, pillX + pillW, pillY + pillH, 0xE609090B);
        guiGraphics.outline(pillX, pillY, pillW, pillH, 0xFFFF7A29);
        guiGraphics.centeredText(font, banner, w / 2, pillY + 5, 0xFFFFFFFF);

        // Realistic EMA-smoothed ETA & Queue sub-pill
        String subLine = buildSubLine(mone, mc.player);
        if (subLine != null) {
            int subW = font.width(subLine) + 14;
            int subX = (w - subW) / 2;
            int subY = pillY + pillH + 2;
            guiGraphics.fill(subX, subY, subX + subW, subY + 13, 0xD809090B);
            guiGraphics.outline(subX, subY, subW, 13, 0xBBCC5518);
            guiGraphics.centeredText(font, subLine, w / 2, subY + 3, 0xFFFFAA55);
        }
    }

    private static void resetEta() {
        trackedGoalKey = null;
        lastDist = -1.0;
        lastSampleMs = 0L;
        smoothedSpeed = 4.5;
        smoothedEtaSec = -1.0;
    }

    private static String buildSubLine(IMone mone, LocalPlayer player) {
        List<String> parts = new ArrayList<>();

        double etaSec = computeRealisticEtaSeconds(mone, player);
        if (etaSec >= 1.0 && etaSec < 36000.0) {
            int total = (int) Math.round(etaSec);
            if (total >= 3600) {
                parts.add("ETA " + (total / 3600) + "h " + ((total % 3600) / 60) + "m");
            } else if (total >= 60) {
                parts.add("ETA " + (total / 60) + "m " + (total % 60) + "s");
            } else {
                parts.add("ETA " + total + "s");
            }
        }

        if (mone instanceof Mone m) {
            int queued = m.getQueueBehavior().getQueuedCommands().size();
            if (m.getQueueBehavior().isRunning() && queued > 0) {
                parts.add("Queue: " + queued + " left");
            }
        }
        return parts.isEmpty() ? null : String.join("  |  ", parts);
    }

    private static double computeRealisticEtaSeconds(IMone mone, LocalPlayer player) {
        IPathingBehavior pathing = mone.getPathingBehavior();
        if (pathing == null) {
            resetEta();
            return -1.0;
        }
        Goal goal = pathing.getGoal();
        double dist = distanceToGoal(player, goal);
        if (dist < 0.0) {
            resetEta();
            return -1.0;
        }

        String key = goal.toString();
        long now = System.currentTimeMillis();
        boolean isElytra = mone.getElytraProcess().isActive();

        if (!key.equals(trackedGoalKey)) {
            trackedGoalKey = key;
            lastDist = dist;
            lastSampleMs = now;
            smoothedSpeed = isElytra ? 18.0 : 4.5;
            smoothedEtaSec = dist / smoothedSpeed;
            return smoothedEtaSec;
        }

        long elapsedMs = now - lastSampleMs;
        if (elapsedMs >= 500L) {
            double dtSec = elapsedMs / 1000.0;
            double deltaDist = lastDist - dist;
            // Only update speed EMA when making forward progress (prevents wild spikes on detours/jumps)
            if (deltaDist > 0.1) {
                double measuredSpeed = deltaDist / dtSec;
                double maxSpeed = isElytra ? 35.0 : 9.0;
                measuredSpeed = Math.max(0.8, Math.min(maxSpeed, measuredSpeed));
                smoothedSpeed = smoothedSpeed * 0.85 + measuredSpeed * 0.15;
            }
            double rawEta = dist / Math.max(0.8, smoothedSpeed);
            smoothedEtaSec = (smoothedEtaSec < 0) ? rawEta : (smoothedEtaSec * 0.72 + rawEta * 0.28);
            lastDist = dist;
            lastSampleMs = now;
        }

        return smoothedEtaSec;
    }

    private static double distanceToGoal(LocalPlayer player, Goal goal) {
        if (goal == null) {
            return -1.0;
        }
        if (goal instanceof GoalBlock gb) {
            double dx = (gb.x + 0.5) - player.getX();
            double dy = gb.y - player.getY();
            double dz = (gb.z + 0.5) - player.getZ();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        if (goal instanceof GoalXZ gxz) {
            double dx = (gxz.getX() + 0.5) - player.getX();
            double dz = (gxz.getZ() + 0.5) - player.getZ();
            return Math.hypot(dx, dz);
        }
        if (goal instanceof IGoalRenderPos rp) {
            BlockPos p = rp.getGoalPos();
            if (p != null) {
                double dx = (p.getX() + 0.5) - player.getX();
                double dy = p.getY() - player.getY();
                double dz = (p.getZ() + 0.5) - player.getZ();
                return Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }
        return -1.0;
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
            if (proc instanceof LumberProcess) {
                return "Chopping trees";
            }
            if (proc instanceof HighwayProcess hp) {
                return hp.displayName();
            }
            if (proc instanceof AutoFishProcess fp) {
                return fp.displayName();
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
