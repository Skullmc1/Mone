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

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import mone.api.IMone;
import mone.api.MoneAPI;
import mone.api.Settings;
import mone.api.cache.IWaypoint;
import mone.api.cache.IWorldData;
import mone.api.cache.Waypoint;
import mone.api.pathing.goals.GoalStrictDirection;
import mone.api.pathing.goals.GoalXZ;
import mone.api.utils.BetterBlockPos;
import mone.api.utils.Helper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;

/**
 * Interactive fullscreen world map screen for Mone.
 * Opened via configurable keybind (default: M) or #map full.
 * Supports panning, zooming, left-click to #goto, and right-click to add waypoints.
 */
public class FullscreenMapScreen extends Screen implements Helper {

    private static final Identifier FULLMAP_TEXTURE_ID = Identifier.fromNamespaceAndPath("mone", "fullmap");
    private static final double MIN_PPB = 0.5;
    private static final double MAX_PPB = 8.0;
    private static final int MAX_TEX_DIM = 512;
    private static final int HEADER_H = 22;
    private static final int FOOTER_H = 24;

    private DynamicTexture mapTexture;
    private int texW = 0;
    private int texH = 0;
    private int lastCenterBlockX = Integer.MIN_VALUE;
    private int lastCenterBlockZ = Integer.MIN_VALUE;
    private int lastBlockHalfW = -1;
    private int lastBlockHalfH = -1;
    private long lastUploadMs = 0L;
    private boolean forceRebuild = true;

    private double camX;
    private double camZ;
    private double ppb = 2.0; // pixels per block

    private boolean dragging = false;
    private double dragStartX;
    private double dragStartY;
    private boolean didDrag = false;

    public FullscreenMapScreen() {
        super(Component.literal("Mone World Map"));
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            this.camX = mc.player.getX();
            this.camZ = mc.player.getZ();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (mapTexture != null) {
            mapTexture.close();
            mapTexture = null;
            texW = 0;
            texH = 0;
        }
        super.onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xEE0D0D14);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mone == null) {
            return;
        }

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        Settings settings = MoneAPI.getSettings();

        int viewX = 0;
        int viewY = HEADER_H;
        int viewW = this.width;
        int viewH = Math.max(32, this.height - HEADER_H - FOOTER_H);
        double viewCenterX = viewX + viewW * 0.5;
        double viewCenterY = viewY + viewH * 0.5;

        int playerY = Mth.floor(player.getY());
        boolean isMining = mone.getMineProcess().isActive()
                || (mone.getPathingBehavior() != null && mone.getPathingBehavior().getGoal() instanceof GoalStrictDirection);
        MinimapRenderer.ViewMode activeMode = MinimapRenderer.resolveViewMode(
                settings.minimapMode.value, level, Mth.floor(player.getX()), playerY, Mth.floor(player.getZ()), isMining
        );

        int blockHalfW = Math.min(MAX_TEX_DIM / 2, (int) Math.ceil(viewW / (2.0 * ppb)) + 2);
        int blockHalfH = Math.min(MAX_TEX_DIM / 2, (int) Math.ceil(viewH / (2.0 * ppb)) + 2);
        int desiredTexW = blockHalfW * 2 + 1;
        int desiredTexH = blockHalfH * 2 + 1;

        ensureTexture(mc, desiredTexW, desiredTexH);

        int centerBlockX = Mth.floor(camX);
        int centerBlockZ = Mth.floor(camZ);

        // Schedule visible chunks on the shared background worker thread
        int scheduleRadius = Math.max(blockHalfW, blockHalfH);
        MinimapRenderer.scheduleVisibleChunks(level, centerBlockX, playerY, centerBlockZ, scheduleRadius, activeMode, 16);

        long now = System.currentTimeMillis();
        boolean viewChanged = centerBlockX != lastCenterBlockX || centerBlockZ != lastCenterBlockZ
                || blockHalfW != lastBlockHalfW || blockHalfH != lastBlockHalfH;
        boolean workerUpdated = MinimapRenderer.consumeCacheUpdated();
        if (viewChanged || forceRebuild || (workerUpdated && (now - lastUploadMs) >= 40L)) {
            rebuildFullscreenTexture(centerBlockX, centerBlockZ, blockHalfW, blockHalfH);
            lastCenterBlockX = centerBlockX;
            lastCenterBlockZ = centerBlockZ;
            lastBlockHalfW = blockHalfW;
            lastBlockHalfH = blockHalfH;
            forceRebuild = false;
            lastUploadMs = now;
        }

        // Clipped map viewport
        guiGraphics.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);

        // Blit terrain texture (north-up, sub-block smooth pan)
        int drawW = (int) Math.round(desiredTexW * ppb);
        int drawH = (int) Math.round(desiredTexH * ppb);
        double subOffsetX = (centerBlockX + 0.5 - camX) * ppb;
        double subOffsetZ = (centerBlockZ + 0.5 - camZ) * ppb;
        int blitX = (int) Math.round(viewCenterX - drawW * 0.5 + subOffsetX);
        int blitY = (int) Math.round(viewCenterY - drawH * 0.5 + subOffsetZ);

        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                FULLMAP_TEXTURE_ID,
                blitX, blitY,
                0.0f, 0.0f,
                drawW, drawH,
                desiredTexW, desiredTexH,
                desiredTexW, desiredTexH
        );

        // Path & goal overlay (north-up: cos=1, sin=0, no circle clip)
        if (settings.minimapShowPath.value) {
            MinimapRenderer.renderPathAndGoal(guiGraphics, mone, viewX, viewY, Math.max(viewW, viewH),
                    viewCenterX, viewCenterY, ppb, camX, camZ, 1.0, 0.0, -1.0);
        }

        // Waypoints & death markers with names + hover detection
        IWaypoint hoveredWaypoint = null;
        if (settings.minimapShowWaypoints.value) {
            hoveredWaypoint = renderFullscreenWaypoints(guiGraphics, mone, viewX, viewY, viewW, viewH,
                    viewCenterX, viewCenterY, mouseX, mouseY);
        }

        // Entities (with mob heads, elevation arrows, 10-block Y-limit for non-players)
        if (settings.minimapShowEntities.value) {
            MinimapRenderer.renderEntities(guiGraphics, level, player, partialTick, viewX, viewY, Math.max(viewW, viewH),
                    viewCenterX, viewCenterY, ppb, scheduleRadius, camX, player.getY(), camZ, 1.0, 0.0, -1.0);
        }

        // Player triangle pointing in player's facing yaw direction
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());
        int playerSx = (int) Math.round(viewCenterX + (px - camX) * ppb);
        int playerSy = (int) Math.round(viewCenterY + (pz - camZ) * ppb);
        float playerRot = (float) Math.toRadians(player.getViewYRot(partialTick) - 180.0);
        MinimapRenderer.renderPlayerTriangle(guiGraphics, playerSx, playerSy, playerRot);

        guiGraphics.disableScissor();

        // Header bar
        guiGraphics.fill(0, 0, this.width, HEADER_H, 0xDD10101A);
        guiGraphics.fill(0, HEADER_H - 1, this.width, HEADER_H, 0xFF3E3E55);

        String modeText = switch (activeMode) {
            case LAYER -> isMining ? ("Mining Layer (Y:" + playerY + ")") : ("Layer (Y:" + playerY + ")");
            case CAVE -> "Cave Layer (Y:" + playerY + ")";
            default -> "Surface";
        };
        guiGraphics.text(this.font, "Mone World Map  |  Mode: " + modeText + "  |  Zoom: " + String.format(Locale.ROOT, "%.1fx", ppb),
                8, 7, 0xFFFFFFFF, true);

        // Hovered world coordinates
        int hoverWorldX = Mth.floor(camX + (mouseX - viewCenterX) / ppb);
        int hoverWorldZ = Mth.floor(camZ + (mouseY - viewCenterY) / ppb);
        String coordStr = "Cursor: X " + hoverWorldX + ", Z " + hoverWorldZ;
        guiGraphics.text(this.font, coordStr, this.width - this.font.width(coordStr) - 8, 7, 0xFF4AEDD9, true);

        // Footer bar with controls
        int footerY = this.height - FOOTER_H;
        guiGraphics.fill(0, footerY, this.width, this.height, 0xDD10101A);
        guiGraphics.fill(0, footerY, this.width, footerY + 1, 0xFF3E3E55);
        String keyLabel = settings.minimapFullscreenKey.value == null ? "M" : settings.minimapFullscreenKey.value.toUpperCase(Locale.ROOT);
        String hint = "Drag: Pan  |  Scroll: Zoom  |  L-Click: Goto  |  R-Click: Add Waypoint  |  C: Center  |  Tab: Mode  |  " + keyLabel + "/Esc: Close";
        guiGraphics.centeredText(this.font, hint, this.width / 2, footerY + 8, 0xFFCCCCCC);

        // Waypoint hover tooltip
        if (hoveredWaypoint != null) {
            BetterBlockPos loc = hoveredWaypoint.getLocation();
            int dist = (int) Math.round(Math.hypot(loc.x - px, loc.z - pz));
            String tip = "[" + hoveredWaypoint.getTag().getName().toUpperCase(Locale.ROOT) + "] "
                    + hoveredWaypoint.getName() + " (" + loc.x + ", " + loc.y + ", " + loc.z + ") - " + dist + "m";
            int tw = this.font.width(tip) + 8;
            int tx = Mth.clamp(mouseX + 10, 4, this.width - tw - 4);
            int ty = Mth.clamp(mouseY - 14, HEADER_H + 2, footerY - 16);
            guiGraphics.fill(tx, ty, tx + tw, ty + 14, 0xE610101A);
            guiGraphics.outline(tx, ty, tw, 14, MinimapRenderer.getWaypointColor(hoveredWaypoint.getTag()));
            guiGraphics.text(this.font, tip, tx + 4, ty + 3, 0xFFFFFFFF, true);
        }
    }

    private IWaypoint renderFullscreenWaypoints(GuiGraphicsExtractor guiGraphics, IMone mone,
                                                int viewX, int viewY, int viewW, int viewH,
                                                double viewCenterX, double viewCenterY,
                                                int mouseX, int mouseY) {
        IWorldData worldData = mone.getWorldProvider().getCurrentWorld();
        if (worldData == null || worldData.getWaypoints() == null) {
            return null;
        }

        IWaypoint hovered = null;
        double bestHoverDistSq = 100.0; // 10px hover radius

        for (IWaypoint wp : worldData.getWaypoints().getAllWaypoints()) {
            BetterBlockPos loc = wp.getLocation();
            if (loc == null) {
                continue;
            }
            int sx = (int) Math.round(viewCenterX + (loc.x + 0.5 - camX) * ppb);
            int sy = (int) Math.round(viewCenterY + (loc.z + 0.5 - camZ) * ppb);
            if (sx < viewX - 16 || sx > viewX + viewW + 16 || sy < viewY - 16 || sy > viewY + viewH + 16) {
                continue;
            }

            MinimapRenderer.drawWaypointMarker(guiGraphics, sx, sy, wp.getTag(), false);
            String label = wp.getName();
            if (label != null && !label.isEmpty()) {
                guiGraphics.centeredText(this.font, label, sx, sy + 6, MinimapRenderer.getWaypointColor(wp.getTag()));
            }

            double ddx = mouseX - sx;
            double ddy = mouseY - sy;
            double distSq = ddx * ddx + ddy * ddy;
            if (distSq < bestHoverDistSq) {
                bestHoverDistSq = distSq;
                hovered = wp;
            }
        }
        return hovered;
    }

    private void ensureTexture(Minecraft mc, int w, int h) {
        if (mapTexture != null && texW == w && texH == h) {
            return;
        }
        if (mapTexture != null) {
            mapTexture.close();
        }
        mapTexture = new DynamicTexture("mone_fullmap", w, h, false);
        mc.getTextureManager().register(FULLMAP_TEXTURE_ID, mapTexture);
        texW = w;
        texH = h;
        forceRebuild = true;
    }

    private void rebuildFullscreenTexture(int centerX, int centerZ, int halfW, int halfH) {
        NativeImage pixels = mapTexture.getPixels();
        if (pixels == null) {
            return;
        }

        int lastCx = Integer.MIN_VALUE;
        int lastCz = Integer.MIN_VALUE;
        MinimapRenderer.ChunkTile cachedTile = null;

        for (int dz = -halfH; dz <= halfH; dz++) {
            int wz = centerZ + dz;
            int tz = dz + halfH;
            int cz = wz >> 4;
            int lzRow = (wz & 15) << 4;

            for (int dx = -halfW; dx <= halfW; dx++) {
                int wx = centerX + dx;
                int tx = dx + halfW;
                int cx = wx >> 4;

                if (cx != lastCx || cz != lastCz) {
                    cachedTile = MinimapRenderer.getCachedTile(cx, cz);
                    lastCx = cx;
                    lastCz = cz;
                }

                int abgr = (cachedTile != null) ? cachedTile.pixelsABGR[lzRow | (wx & 15)] : MinimapRenderer.ABGR_UNLOADED;
                pixels.setPixelABGR(tx, tz, abgr);
            }
        }

        mapTexture.upload();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            dragging = true;
            didDrag = false;
            dragStartX = event.x();
            dragStartY = event.y();
            return true;
        }
        if (event.button() == 1) {
            addWaypointAtScreenPos(event.x(), event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && event.button() == 0) {
            if (Math.hypot(event.x() - dragStartX, event.y() - dragStartY) > 4.0) {
                didDrag = true;
            }
            camX -= dragX / ppb;
            camZ -= dragY / ppb;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dragging) {
            dragging = false;
            if (!didDrag && event.y() >= HEADER_H && event.y() <= this.height - FOOTER_H) {
                setGotoAtScreenPos(event.x(), event.y());
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double factor = scrollY > 0 ? 1.25 : 0.8;
        ppb = Mth.clamp(ppb * factor, MIN_PPB, MAX_PPB);
        forceRebuild = true;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int fullscreenKey = MinimapRenderer.resolveFullscreenKeyCode();
        if (event.key() == fullscreenKey || event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (event.key() == InputConstants.KEY_C || event.key() == InputConstants.KEY_SPACE) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                camX = mc.player.getX();
                camZ = mc.player.getZ();
                forceRebuild = true;
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_TAB) {
            cycleMode();
            return true;
        }
        return super.keyPressed(event);
    }

    private void cycleMode() {
        Settings settings = MoneAPI.getSettings();
        String cur = settings.minimapMode.value == null ? "auto" : settings.minimapMode.value.toLowerCase(Locale.ROOT);
        String next = switch (cur) {
            case "auto" -> "surface";
            case "surface" -> "cave";
            case "cave" -> "layer";
            default -> "auto";
        };
        settings.minimapMode.value = next;
        MinimapRenderer.invalidate();
        forceRebuild = true;
    }

    private void setGotoAtScreenPos(double mouseX, double mouseY) {
        int viewH = Math.max(32, this.height - HEADER_H - FOOTER_H);
        double viewCenterX = this.width * 0.5;
        double viewCenterY = HEADER_H + viewH * 0.5;
        int targetX = Mth.floor(camX + (mouseX - viewCenterX) / ppb);
        int targetZ = Mth.floor(camZ + (mouseY - viewCenterY) / ppb);

        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mone != null) {
            mone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(targetX, targetZ));
            Helper.HELPER.logDirect("Pathing to X: " + targetX + ", Z: " + targetZ);
        }
    }

    private void addWaypointAtScreenPos(double mouseX, double mouseY) {
        int viewH = Math.max(32, this.height - HEADER_H - FOOTER_H);
        double viewCenterX = this.width * 0.5;
        double viewCenterY = HEADER_H + viewH * 0.5;
        int wx = Mth.floor(camX + (mouseX - viewCenterX) / ppb);
        int wz = Mth.floor(camZ + (mouseY - viewCenterY) / ppb);

        Minecraft mc = Minecraft.getInstance();
        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mc == null || mc.player == null || mc.level == null || mone == null) {
            return;
        }
        IWorldData worldData = mone.getWorldProvider().getCurrentWorld();
        if (worldData == null || worldData.getWaypoints() == null) {
            return;
        }
        int wy = mc.level.hasChunk(wx >> 4, wz >> 4)
                ? mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz)
                : Mth.floor(mc.player.getY());
        String name = "wp_" + wx + "_" + wz;
        worldData.getWaypoints().addWaypoint(new Waypoint(name, IWaypoint.Tag.USER, new BetterBlockPos(wx, wy, wz)));
        Helper.HELPER.logDirect("Added waypoint '" + name + "' at " + wx + ", " + wy + ", " + wz);
    }
}
