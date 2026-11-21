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
import mone.api.pathing.goals.GoalBlock;
import mone.api.pathing.goals.GoalStrictDirection;
import mone.api.pathing.goals.GoalXZ;
import mone.api.selection.ISelection;
import mone.api.utils.BetterBlockPos;
import mone.api.utils.Helper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Interactive fullscreen world map screen for Mone.
 * Opened via configurable keybind (default: M) or #map full.
 * Supports:
 * - Persistent explored world map via MapDiskCache
 * - Pan (drag), Zoom (scroll), L-Click (#goto), Ctrl+Drag (#sel)
 * - R-Click empty space to create a named & colored waypoint
 * - R-Click waypoint for Goto / Rename / Delete context menu
 * - Waypoints sidebar with live search (W key or header button)
 * - Nether <-> Overworld linked coordinates
 * - Chunk grid (G) & mob-spawning light overlay (L)
 */
public class FullscreenMapScreen extends Screen implements Helper {

    private static final Identifier FULLMAP_TEXTURE_ID = Identifier.fromNamespaceAndPath("mone", "fullmap");
    private static final double MIN_PPB = 0.5;
    private static final double MAX_PPB = 8.0;
    private static final int MAX_TEX_DIM = 512;
    private static final int HEADER_H = 22;
    private static final int FOOTER_H = 24;
    private static final int MAX_WP_NAME_LEN = 32;

    // Context menu geometry
    private static final int MENU_W = 76;
    private static final int MENU_ROW_H = 16;
    private static final String[] MENU_ITEMS = {"Goto", "Rename", "Delete"};

    // Name dialog geometry & color palette
    private static final int DIALOG_W = 206;
    private static final int DIALOG_H = 98;
    private static final int[] COLOR_PALETTE = {
            0xFFFF3333, // Red
            0xFFFF8833, // Orange
            0xFFFFCC33, // Gold
            0xFF44DD55, // Green
            0xFF38E8D4, // Cyan
            0xFF4499FF, // Blue
            0xFFCC66FF, // Purple
            0xFFFF66BB, // Pink
            0xFFFFFFFF  // White
    };

    // Waypoints sidebar geometry
    private static final int SIDEBAR_W = 168;
    private static final int SIDEBAR_ROW_H = 20;

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

    // Drag-to-pan state
    private boolean dragging = false;
    private double dragStartX;
    private double dragStartY;
    private boolean didDrag = false;

    // Ctrl+Drag selection (#sel) state
    private boolean selecting = false;
    private int selStartX;
    private int selStartZ;
    private int selEndX;
    private int selEndZ;

    // Right-click waypoint context menu state
    private IWaypoint contextWaypoint = null;
    private int contextMenuX = 0;
    private int contextMenuY = 0;

    // Inline "Name / Rename Waypoint" modal state
    private boolean nameDialogOpen = false;
    private IWaypoint renamingWaypoint = null; // null = creating new waypoint
    private BetterBlockPos pendingWaypointPos = null;
    private final StringBuilder nameInput = new StringBuilder();
    private int selectedColor = 0xFFCC66FF;

    // Waypoints search sidebar state
    private boolean sidebarOpen = false;
    private boolean searchFocused = false;
    private final StringBuilder searchInput = new StringBuilder();
    private int sidebarScroll = 0;
    private List<IWaypoint> filteredWaypoints = new ArrayList<>();

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
        MapDiskCache.flushAll();
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

    private int mapViewX() {
        return sidebarOpen ? SIDEBAR_W : 0;
    }

    private int mapViewW() {
        return Math.max(64, this.width - mapViewX());
    }

    private int mapViewH() {
        return Math.max(32, this.height - HEADER_H - FOOTER_H);
    }

    private double mapViewCenterX() {
        return mapViewX() + mapViewW() * 0.5;
    }

    private double mapViewCenterY() {
        return HEADER_H + mapViewH() * 0.5;
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

        int viewX = mapViewX();
        int viewY = HEADER_H;
        int viewW = mapViewW();
        int viewH = mapViewH();
        double viewCenterX = mapViewCenterX();
        double viewCenterY = mapViewCenterY();

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

        // Schedule visible chunks (loaded or persistent disk cache) on background worker
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

        // Render existing Mone #sel selections + live Ctrl+Drag selection box
        renderSelections(guiGraphics, mone, viewCenterX, viewCenterY);

        // Path & goal overlay
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

        // Entities (mob heads, elevation arrows, 10-block Y limit for non-players)
        if (settings.minimapShowEntities.value) {
            MinimapRenderer.renderEntities(guiGraphics, level, player, partialTick, viewX, viewY, Math.max(viewW, viewH),
                    viewCenterX, viewCenterY, ppb, scheduleRadius, camX, player.getY(), camZ, 1.0, 0.0, -1.0);
        }

        // Player triangle pointing in facing direction
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());
        int playerSx = (int) Math.round(viewCenterX + (px - camX) * ppb);
        int playerSy = (int) Math.round(viewCenterY + (pz - camZ) * ppb);
        float playerRot = (float) Math.toRadians(player.getViewYRot(partialTick) - 180.0);
        MinimapRenderer.renderPlayerTriangle(guiGraphics, playerSx, playerSy, playerRot);

        guiGraphics.disableScissor();

        // Waypoints Search Sidebar
        if (sidebarOpen) {
            renderWaypointsSidebar(guiGraphics, mone, px, pz, mouseX, mouseY);
        }

        // Header bar
        guiGraphics.fill(0, 0, this.width, HEADER_H, 0xDD10101A);
        guiGraphics.fill(0, HEADER_H - 1, this.width, HEADER_H, 0xFF3E3E55);

        // Waypoints toggle button in header
        boolean hoverWpBtn = mouseX >= 6 && mouseX < 74 && mouseY >= 3 && mouseY < 19;
        guiGraphics.fill(6, 3, 74, 19, sidebarOpen ? 0xFF285C52 : (hoverWpBtn ? 0xFF33334A : 0xFF202030));
        guiGraphics.outline(6, 3, 68, 16, sidebarOpen ? 0xFF38E8D4 : 0xFF555570);
        guiGraphics.centeredText(this.font, "Waypoints", 40, 7, 0xFFFFFFFF);

        String modeText = switch (activeMode) {
            case LAYER -> isMining ? ("Mining Y:" + playerY) : ("Layer Y:" + playerY);
            case CAVE -> "Cave Y:" + playerY;
            default -> "Surface";
        };
        String flags = (settings.minimapChunkGrid.value ? " [Grid]" : "")
                + (settings.minimapSpawnOverlay.value ? " [Spawns]" : "");
        guiGraphics.text(this.font, modeText + "  |  " + String.format(Locale.ROOT, "%.1fx", ppb) + flags,
                82, 7, 0xFFDDDDDD, true);

        // Hovered world coordinates + Nether/Overworld linked coordinates
        int hoverWorldX = Mth.floor(camX + (mouseX - viewCenterX) / ppb);
        int hoverWorldZ = Mth.floor(camZ + (mouseY - viewCenterY) / ppb);
        String linked = formatLinkedCoords(level, hoverWorldX, hoverWorldZ);
        String coordStr = "X " + hoverWorldX + ", Z " + hoverWorldZ + linked;
        guiGraphics.text(this.font, coordStr, this.width - this.font.width(coordStr) - 8, 7, 0xFF4AEDD9, true);

        // Footer bar with controls
        int footerY = this.height - FOOTER_H;
        guiGraphics.fill(0, footerY, this.width, this.height, 0xDD10101A);
        guiGraphics.fill(0, footerY, this.width, footerY + 1, 0xFF3E3E55);
        String keyLabel = settings.minimapFullscreenKey.value == null ? "M" : settings.minimapFullscreenKey.value.toUpperCase(Locale.ROOT);
        String hint = "Drag: Pan | Ctrl+Drag: #sel | Scroll: Zoom | L-Click: Goto | R-Click: Waypoint/Menu | W: Search | G: Grid | L: Spawns | " + keyLabel + "/Esc: Close";
        guiGraphics.centeredText(this.font, hint, this.width / 2, footerY + 8, 0xFFCCCCCC);

        // Waypoint hover tooltip (with linked Nether/Overworld coords)
        if (hoveredWaypoint != null && contextWaypoint == null && !nameDialogOpen && mouseX >= viewX) {
            BetterBlockPos loc = hoveredWaypoint.getLocation();
            int dist = (int) Math.round(Math.hypot(loc.x - px, loc.z - pz));
            String wpLinked = formatLinkedCoords(level, loc.x, loc.z);
            String tip = "[" + hoveredWaypoint.getTag().getName().toUpperCase(Locale.ROOT) + "] "
                    + hoveredWaypoint.getName() + " (" + loc.x + ", " + loc.y + ", " + loc.z + ")" + wpLinked + " - " + dist + "m";
            int tw = this.font.width(tip) + 8;
            int tx = Mth.clamp(mouseX + 10, 4, this.width - tw - 4);
            int ty = Mth.clamp(mouseY - 14, HEADER_H + 2, footerY - 16);
            guiGraphics.fill(tx, ty, tx + tw, ty + 14, 0xE610101A);
            guiGraphics.outline(tx, ty, tw, 14, MapDiskCache.getWaypointColor(hoveredWaypoint));
            guiGraphics.text(this.font, tip, tx + 4, ty + 3, 0xFFFFFFFF, true);
        }

        if (contextWaypoint != null) {
            renderContextMenu(guiGraphics, mouseX, mouseY);
        }

        if (nameDialogOpen) {
            renderNameDialog(guiGraphics, mouseX, mouseY);
        }
    }

    private static String formatLinkedCoords(ClientLevel level, int x, int z) {
        if (level.dimension() == Level.OVERWORLD) {
            return "  (Nether: " + Math.floorDiv(x, 8) + ", " + Math.floorDiv(z, 8) + ")";
        }
        if (level.dimension() == Level.NETHER) {
            return "  (Overworld: " + (x * 8) + ", " + (z * 8) + ")";
        }
        return "";
    }

    private void renderSelections(GuiGraphicsExtractor guiGraphics, IMone mone, double viewCenterX, double viewCenterY) {
        ISelection[] selections = mone.getSelectionManager().getSelections();
        if (selections != null) {
            for (ISelection sel : selections) {
                BetterBlockPos min = sel.min();
                BetterBlockPos max = sel.max();
                int x0 = (int) Math.round(viewCenterX + (min.x - camX) * ppb);
                int z0 = (int) Math.round(viewCenterY + (min.z - camZ) * ppb);
                int x1 = (int) Math.round(viewCenterX + (max.x + 1 - camX) * ppb);
                int z1 = (int) Math.round(viewCenterY + (max.z + 1 - camZ) * ppb);
                guiGraphics.fill(x0, z0, x1, z1, 0x3338E8D4);
                guiGraphics.outline(x0, z0, Math.max(1, x1 - x0), Math.max(1, z1 - z0), 0xCC38E8D4);
            }
        }

        if (selecting) {
            int minX = Math.min(selStartX, selEndX);
            int maxX = Math.max(selStartX, selEndX);
            int minZ = Math.min(selStartZ, selEndZ);
            int maxZ = Math.max(selStartZ, selEndZ);
            int x0 = (int) Math.round(viewCenterX + (minX - camX) * ppb);
            int z0 = (int) Math.round(viewCenterY + (minZ - camZ) * ppb);
            int x1 = (int) Math.round(viewCenterX + (maxX + 1 - camX) * ppb);
            int z1 = (int) Math.round(viewCenterY + (maxZ + 1 - camZ) * ppb);
            guiGraphics.fill(x0, z0, x1, z1, 0x444AEDD9);
            guiGraphics.outline(x0, z0, Math.max(1, x1 - x0), Math.max(1, z1 - z0), 0xFF4AEDD9);
            String dim = (maxX - minX + 1) + " x " + (maxZ - minZ + 1);
            guiGraphics.centeredText(this.font, dim, (x0 + x1) / 2, z0 - 11, 0xFF4AEDD9);
        }
    }

    private void renderWaypointsSidebar(GuiGraphicsExtractor guiGraphics, IMone mone, double px, double pz, int mouseX, int mouseY) {
        int topY = HEADER_H;
        int bottomY = this.height - FOOTER_H;
        guiGraphics.fill(0, topY, SIDEBAR_W, bottomY, 0xEB11111B);
        guiGraphics.fill(SIDEBAR_W - 1, topY, SIDEBAR_W, bottomY, 0xFF3E3E55);

        // Search box
        int sbX = 6;
        int sbY = topY + 6;
        int sbW = SIDEBAR_W - 12;
        int sbH = 16;
        guiGraphics.fill(sbX, sbY, sbX + sbW, sbY + sbH, 0xFF0A0A12);
        guiGraphics.outline(sbX, sbY, sbW, sbH, searchFocused ? 0xFF38E8D4 : 0xFF4A4A64);

        String query = searchInput.toString();
        boolean showCursor = searchFocused && (((System.currentTimeMillis() / 500L) & 1L) == 0L);
        if (query.isEmpty()) {
            guiGraphics.text(this.font, showCursor ? "|" : "Search (name/death)...", sbX + 4, sbY + 4,
                    showCursor ? 0xFFFFFFFF : 0xFF6A6A7F, false);
        } else {
            guiGraphics.text(this.font, query + (showCursor ? "|" : ""), sbX + 4, sbY + 4, 0xFFFFFFFF, true);
        }

        // Populate filtered list
        filteredWaypoints = new ArrayList<>();
        IWorldData wd = mone.getWorldProvider().getCurrentWorld();
        if (wd != null && wd.getWaypoints() != null) {
            String qLower = query.trim().toLowerCase(Locale.ROOT);
            for (IWaypoint wp : wd.getWaypoints().getAllWaypoints()) {
                String name = wp.getName() == null ? "" : wp.getName();
                String tag = wp.getTag() == null ? "" : wp.getTag().getName();
                if (qLower.isEmpty()
                        || name.toLowerCase(Locale.ROOT).contains(qLower)
                        || tag.toLowerCase(Locale.ROOT).contains(qLower)) {
                    filteredWaypoints.add(wp);
                }
            }
            // Sort: most recent first
            filteredWaypoints.sort(Comparator.comparingLong(IWaypoint::getCreationTimestamp).reversed());
        }

        int listTop = sbY + sbH + 6;
        int listH = Math.max(0, bottomY - listTop - 4);
        int visibleRows = Math.max(1, listH / SIDEBAR_ROW_H);
        int maxScroll = Math.max(0, filteredWaypoints.size() - visibleRows);
        sidebarScroll = Mth.clamp(sidebarScroll, 0, maxScroll);

        if (filteredWaypoints.isEmpty()) {
            guiGraphics.centeredText(this.font, "No waypoints", SIDEBAR_W / 2, listTop + 12, 0xFF777788);
            return;
        }

        for (int i = 0; i < visibleRows && (i + sidebarScroll) < filteredWaypoints.size(); i++) {
            IWaypoint wp = filteredWaypoints.get(i + sidebarScroll);
            int rowY = listTop + i * SIDEBAR_ROW_H;
            boolean rowHover = mouseX >= 4 && mouseX < SIDEBAR_W - 4 && mouseY >= rowY && mouseY < rowY + SIDEBAR_ROW_H - 1;
            if (rowHover) {
                guiGraphics.fill(4, rowY, SIDEBAR_W - 4, rowY + SIDEBAR_ROW_H - 1, 0xAA262A40);
            }

            int color = MapDiskCache.getWaypointColor(wp);
            guiGraphics.fill(8, rowY + 6, 14, rowY + 12, color);

            BetterBlockPos loc = wp.getLocation();
            int dist = loc != null ? (int) Math.round(Math.hypot(loc.x - px, loc.z - pz)) : 0;
            String title = wp.getName() == null || wp.getName().isEmpty() ? wp.getTag().getName() : wp.getName();
            if (title.length() > 14) {
                title = title.substring(0, 13) + "…";
            }
            guiGraphics.text(this.font, title, 18, rowY + 2, 0xFFFFFFFF, true);
            guiGraphics.text(this.font, wp.getTag().getName() + " · " + dist + "m", 18, rowY + 11, 0xFF8899AA, false);

            // Quick Go & Delete buttons
            int goX = SIDEBAR_W - 34;
            int delX = SIDEBAR_W - 16;
            boolean hoverGo = mouseX >= goX && mouseX < goX + 16 && mouseY >= rowY + 3 && mouseY < rowY + 15;
            boolean hoverDel = mouseX >= delX && mouseX < delX + 12 && mouseY >= rowY + 3 && mouseY < rowY + 15;
            guiGraphics.fill(goX, rowY + 3, goX + 16, rowY + 15, hoverGo ? 0xFF2B7A58 : 0xFF1D4A38);
            guiGraphics.centeredText(this.font, "Go", goX + 8, rowY + 5, 0xFFFFFFFF);
            guiGraphics.fill(delX, rowY + 3, delX + 12, rowY + 15, hoverDel ? 0xFF992A2A : 0xFF5A1D1D);
            guiGraphics.centeredText(this.font, "X", delX + 6, rowY + 5, 0xFFFFAAAA);
        }
    }

    private void renderContextMenu(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int menuH = MENU_ITEMS.length * MENU_ROW_H + 4;
        guiGraphics.fill(contextMenuX, contextMenuY, contextMenuX + MENU_W, contextMenuY + menuH, 0xF0141422);
        guiGraphics.outline(contextMenuX, contextMenuY, MENU_W, menuH, 0xFF6A6A88);

        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int rowY = contextMenuY + 2 + i * MENU_ROW_H;
            boolean hovered = mouseX >= contextMenuX + 1 && mouseX < contextMenuX + MENU_W - 1
                    && mouseY >= rowY && mouseY < rowY + MENU_ROW_H;
            if (hovered) {
                int hoverBg = (i == 2) ? 0xCC7A2222 : 0xCC2E3654;
                guiGraphics.fill(contextMenuX + 1, rowY, contextMenuX + MENU_W - 1, rowY + MENU_ROW_H, hoverBg);
            }
            int textColor = (i == 2) ? 0xFFFF6666 : 0xFFFFFFFF;
            guiGraphics.text(this.font, MENU_ITEMS[i], contextMenuX + 7, rowY + 4, textColor, true);
        }
    }

    private void renderNameDialog(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x66000000);

        int dx = (this.width - DIALOG_W) / 2;
        int dy = (this.height - DIALOG_H) / 2;

        guiGraphics.fill(dx, dy, dx + DIALOG_W, dy + DIALOG_H, 0xF5141422);
        guiGraphics.outline(dx, dy, DIALOG_W, DIALOG_H, 0xFF6A6A88);

        String title = (renamingWaypoint != null) ? "Rename Waypoint" : "Name Waypoint";
        guiGraphics.text(this.font, title, dx + 8, dy + 7, 0xFFFFFFFF, true);

        if (pendingWaypointPos != null) {
            String posText = pendingWaypointPos.x + ", " + pendingWaypointPos.y + ", " + pendingWaypointPos.z;
            guiGraphics.text(this.font, posText, dx + DIALOG_W - this.font.width(posText) - 8, dy + 7, 0xFF99AABB, true);
        }

        // Text input box
        int boxX = dx + 8;
        int boxY = dy + 22;
        int boxW = DIALOG_W - 16;
        int boxH = 18;
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF0A0A12);
        guiGraphics.outline(boxX, boxY, boxW, boxH, selectedColor);

        String displayText = nameInput.toString();
        boolean showCursor = ((System.currentTimeMillis() / 500L) & 1L) == 0L;
        if (displayText.isEmpty()) {
            guiGraphics.text(this.font, showCursor ? "|" : "Enter name...", boxX + 5, boxY + 5,
                    showCursor ? 0xFFFFFFFF : 0xFF666677, false);
        } else {
            guiGraphics.text(this.font, displayText + (showCursor ? "|" : ""), boxX + 5, boxY + 5, 0xFFFFFFFF, true);
        }

        // Color swatches row
        int swatchY = dy + 46;
        int swatchSize = 14;
        int swatchGap = 7;
        int startX = dx + 10;
        for (int i = 0; i < COLOR_PALETTE.length; i++) {
            int sx = startX + i * (swatchSize + swatchGap);
            int c = COLOR_PALETTE[i];
            boolean isSel = (c & 0xFFFFFF) == (selectedColor & 0xFFFFFF);
            guiGraphics.fill(sx, swatchY, sx + swatchSize, swatchY + swatchSize, c);
            guiGraphics.outline(sx - 1, swatchY - 1, swatchSize + 2, swatchSize + 2, isSel ? 0xFFFFFFFF : 0xFF333344);
        }

        // Save & Cancel buttons
        int btnY = dy + 70;
        int btnH = 18;
        int btnW = (DIALOG_W - 24) / 2;
        int saveX = dx + 8;
        int cancelX = dx + 16 + btnW;

        boolean hoverSave = mouseX >= saveX && mouseX < saveX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        boolean hoverCancel = mouseX >= cancelX && mouseX < cancelX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

        guiGraphics.fill(saveX, btnY, saveX + btnW, btnY + btnH, hoverSave ? 0xFF2B7A58 : 0xFF1F5C42);
        guiGraphics.outline(saveX, btnY, btnW, btnH, 0xFF4AEDD9);
        guiGraphics.centeredText(this.font, "Save", saveX + btnW / 2, btnY + 5, 0xFFFFFFFF);

        guiGraphics.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, hoverCancel ? 0xFF4A4A5E : 0xFF333344);
        guiGraphics.outline(cancelX, btnY, btnW, btnH, 0xFF6A6A88);
        guiGraphics.centeredText(this.font, "Cancel", cancelX + btnW / 2, btnY + 5, 0xFFDDDDDD);
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
        double bestHoverDistSq = 100.0;

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

            MinimapRenderer.drawWaypointMarker(guiGraphics, sx, sy, wp, false);
            String label = wp.getName();
            if (label != null && !label.isEmpty()) {
                guiGraphics.centeredText(this.font, label, sx, sy + 6, MapDiskCache.getWaypointColor(wp));
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

    private IWaypoint findWaypointAtScreenPos(double mouseX, double mouseY) {
        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mone == null) {
            return null;
        }
        IWorldData worldData = mone.getWorldProvider().getCurrentWorld();
        if (worldData == null || worldData.getWaypoints() == null) {
            return null;
        }
        double viewCenterX = mapViewCenterX();
        double viewCenterY = mapViewCenterY();

        IWaypoint best = null;
        double bestDistSq = 121.0;
        for (IWaypoint wp : worldData.getWaypoints().getAllWaypoints()) {
            BetterBlockPos loc = wp.getLocation();
            if (loc == null) {
                continue;
            }
            double sx = viewCenterX + (loc.x + 0.5 - camX) * ppb;
            double sy = viewCenterY + (loc.z + 0.5 - camZ) * ppb;
            double ddx = mouseX - sx;
            double ddy = mouseY - sy;
            double distSq = ddx * ddx + ddy * ddy;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = wp;
            }
        }
        return best;
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

        Settings settings = MoneAPI.getSettings();
        boolean showSpawns = settings.minimapSpawnOverlay.value;
        boolean showGrid = settings.minimapChunkGrid.value;

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

                int localIdx = lzRow | (wx & 15);
                int abgr = (cachedTile != null) ? cachedTile.pixelsABGR[localIdx] : MinimapRenderer.ABGR_UNLOADED;
                byte risk = (cachedTile != null) ? cachedTile.spawnRisk[localIdx] : 0;
                abgr = MinimapRenderer.applyMapOverlays(abgr, risk, wx, wz, showSpawns, showGrid);
                pixels.setPixelABGR(tx, tz, abgr);
            }
        }

        mapTexture.upload();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        // Handle clicks inside the Name/Rename Waypoint modal
        if (nameDialogOpen) {
            int dx = (this.width - DIALOG_W) / 2;
            int dy = (this.height - DIALOG_H) / 2;

            if (event.button() == 0) {
                // Color swatches
                int swatchY = dy + 46;
                int swatchSize = 14;
                int swatchGap = 7;
                int startX = dx + 10;
                if (my >= swatchY - 2 && my <= swatchY + swatchSize + 2) {
                    for (int i = 0; i < COLOR_PALETTE.length; i++) {
                        int sx = startX + i * (swatchSize + swatchGap);
                        if (mx >= sx - 1 && mx <= sx + swatchSize + 1) {
                            selectedColor = COLOR_PALETTE[i];
                            return true;
                        }
                    }
                }

                int btnY = dy + 70;
                int btnH = 18;
                int btnW = (DIALOG_W - 24) / 2;
                int saveX = dx + 8;
                int cancelX = dx + 16 + btnW;
                if (mx >= saveX && mx < saveX + btnW && my >= btnY && my < btnY + btnH) {
                    commitNameDialog();
                    return true;
                }
                if (mx >= cancelX && mx < cancelX + btnW && my >= btnY && my < btnY + btnH) {
                    closeNameDialog();
                    return true;
                }
                if (mx < dx || mx > dx + DIALOG_W || my < dy || my > dy + DIALOG_H) {
                    closeNameDialog();
                }
            }
            return true;
        }

        // Header "Waypoints" button
        if (event.button() == 0 && mx >= 6 && mx < 74 && my >= 3 && my < 19) {
            sidebarOpen = !sidebarOpen;
            searchFocused = sidebarOpen;
            forceRebuild = true;
            return true;
        }

        // Handle clicks when waypoint context menu is open
        if (contextWaypoint != null) {
            int menuH = MENU_ITEMS.length * MENU_ROW_H + 4;
            if (event.button() == 0
                    && mx >= contextMenuX && mx < contextMenuX + MENU_W
                    && my >= contextMenuY + 2 && my < contextMenuY + menuH - 2) {
                int row = (int) ((my - (contextMenuY + 2)) / MENU_ROW_H);
                IWaypoint target = contextWaypoint;
                contextWaypoint = null;
                if (row == 0) {
                    gotoWaypoint(target);
                } else if (row == 1) {
                    openRenameDialog(target);
                } else if (row == 2) {
                    deleteWaypoint(target);
                }
                return true;
            }
            contextWaypoint = null;
            if (event.button() == 0) {
                return true;
            }
        }

        // Handle clicks inside the Waypoints Search Sidebar
        if (sidebarOpen && mx < SIDEBAR_W && my >= HEADER_H && my <= this.height - FOOTER_H) {
            int sbY = HEADER_H + 6;
            if (my >= sbY && my <= sbY + 16) {
                searchFocused = true;
                return true;
            }
            searchFocused = false;
            int listTop = sbY + 22;
            int idx = (int) ((my - listTop) / SIDEBAR_ROW_H) + sidebarScroll;
            if (idx >= 0 && idx < filteredWaypoints.size()) {
                IWaypoint wp = filteredWaypoints.get(idx);
                int goX = SIDEBAR_W - 34;
                int delX = SIDEBAR_W - 16;
                if (event.button() == 0 && mx >= goX && mx < goX + 16) {
                    gotoWaypoint(wp);
                } else if (event.button() == 0 && mx >= delX && mx < delX + 12) {
                    deleteWaypoint(wp);
                } else if (event.button() == 0 && wp.getLocation() != null) {
                    // Pan map straight to the clicked waypoint
                    camX = wp.getLocation().x + 0.5;
                    camZ = wp.getLocation().z + 0.5;
                    forceRebuild = true;
                } else if (event.button() == 1) {
                    int menuH = MENU_ITEMS.length * MENU_ROW_H + 4;
                    contextWaypoint = wp;
                    contextMenuX = Mth.clamp((int) Math.round(mx), 4, this.width - MENU_W - 4);
                    contextMenuY = Mth.clamp((int) Math.round(my), HEADER_H + 2, this.height - FOOTER_H - menuH - 2);
                }
            }
            return true;
        }

        searchFocused = false;

        if (event.button() == 0 && mx >= mapViewX() && my >= HEADER_H && my <= this.height - FOOTER_H) {
            if (event.hasControlDown()) {
                // Start Ctrl+Drag selection (#sel)
                selecting = true;
                selStartX = Mth.floor(camX + (mx - mapViewCenterX()) / ppb);
                selStartZ = Mth.floor(camZ + (my - mapViewCenterY()) / ppb);
                selEndX = selStartX;
                selEndZ = selStartZ;
                return true;
            }
            dragging = true;
            didDrag = false;
            dragStartX = mx;
            dragStartY = my;
            return true;
        }
        if (event.button() == 1 && mx >= mapViewX() && my >= HEADER_H && my <= this.height - FOOTER_H) {
            IWaypoint clickedWp = findWaypointAtScreenPos(mx, my);
            if (clickedWp != null) {
                int menuH = MENU_ITEMS.length * MENU_ROW_H + 4;
                contextWaypoint = clickedWp;
                contextMenuX = Mth.clamp((int) Math.round(mx), 4, this.width - MENU_W - 4);
                contextMenuY = Mth.clamp((int) Math.round(my), HEADER_H + 2, this.height - FOOTER_H - menuH - 2);
            } else {
                openCreateWaypointDialog(mx, my);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (nameDialogOpen || contextWaypoint != null) {
            return true;
        }
        if (selecting && event.button() == 0) {
            selEndX = Mth.floor(camX + (event.x() - mapViewCenterX()) / ppb);
            selEndZ = Mth.floor(camZ + (event.y() - mapViewCenterY()) / ppb);
            return true;
        }
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
        if (event.button() == 0 && selecting) {
            selecting = false;
            commitSelection();
            return true;
        }
        if (event.button() == 0 && dragging) {
            dragging = false;
            if (!didDrag && !nameDialogOpen && contextWaypoint == null
                    && event.x() >= mapViewX() && event.y() >= HEADER_H && event.y() <= this.height - FOOTER_H) {
                setGotoAtScreenPos(event.x(), event.y());
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (nameDialogOpen) {
            return true;
        }
        if (sidebarOpen && mouseX < SIDEBAR_W) {
            sidebarScroll += (scrollY > 0 ? -1 : 1);
            return true;
        }
        contextWaypoint = null;
        double factor = scrollY > 0 ? 1.25 : 0.8;
        ppb = Mth.clamp(ppb * factor, MIN_PPB, MAX_PPB);
        forceRebuild = true;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (nameDialogOpen) {
            if (event.isAllowedChatCharacter() && nameInput.length() < MAX_WP_NAME_LEN) {
                nameInput.append(event.codepointAsString());
            }
            return true;
        }
        if (sidebarOpen && searchFocused) {
            if (event.isAllowedChatCharacter() && searchInput.length() < MAX_WP_NAME_LEN) {
                searchInput.append(event.codepointAsString());
                sidebarScroll = 0;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (nameDialogOpen) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                commitNameDialog();
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE) {
                closeNameDialog();
                return true;
            }
            if (event.key() == InputConstants.KEY_BACKSPACE) {
                if (nameInput.length() > 0) {
                    nameInput.deleteCharAt(nameInput.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (sidebarOpen && searchFocused) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (event.key() == InputConstants.KEY_BACKSPACE) {
                if (searchInput.length() > 0) {
                    searchInput.deleteCharAt(searchInput.length() - 1);
                    sidebarScroll = 0;
                }
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                if (!filteredWaypoints.isEmpty() && filteredWaypoints.get(0).getLocation() != null) {
                    BetterBlockPos loc = filteredWaypoints.get(0).getLocation();
                    camX = loc.x + 0.5;
                    camZ = loc.z + 0.5;
                    forceRebuild = true;
                }
                return true;
            }
            return true;
        }

        if (contextWaypoint != null && event.key() == InputConstants.KEY_ESCAPE) {
            contextWaypoint = null;
            return true;
        }

        int fullscreenKey = MinimapRenderer.resolveFullscreenKeyCode();
        if (event.key() == fullscreenKey || event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (event.key() == InputConstants.KEY_W) {
            sidebarOpen = !sidebarOpen;
            searchFocused = sidebarOpen;
            forceRebuild = true;
            return true;
        }
        if (event.key() == InputConstants.KEY_G) {
            Settings settings = MoneAPI.getSettings();
            settings.minimapChunkGrid.value = !settings.minimapChunkGrid.value;
            MinimapRenderer.invalidateTextureOnly();
            forceRebuild = true;
            return true;
        }
        if (event.key() == InputConstants.KEY_L) {
            Settings settings = MoneAPI.getSettings();
            settings.minimapSpawnOverlay.value = !settings.minimapSpawnOverlay.value;
            MinimapRenderer.invalidateTextureOnly();
            forceRebuild = true;
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

    private void commitSelection() {
        Minecraft mc = Minecraft.getInstance();
        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mc == null || mc.player == null || mone == null) {
            return;
        }
        int py = Mth.floor(mc.player.getY());
        BetterBlockPos p1 = new BetterBlockPos(Math.min(selStartX, selEndX), py, Math.min(selStartZ, selEndZ));
        BetterBlockPos p2 = new BetterBlockPos(Math.max(selStartX, selEndX), py, Math.max(selStartZ, selEndZ));
        mone.getSelectionManager().removeAllSelections();
        mone.getSelectionManager().addSelection(p1, p2);
        Helper.HELPER.logDirect("Selection set: (" + p1.x + ", " + p1.y + ", " + p1.z + ") to (" + p2.x + ", " + p2.y + ", " + p2.z + ")");
    }

    private void openCreateWaypointDialog(double mouseX, double mouseY) {
        int wx = Mth.floor(camX + (mouseX - mapViewCenterX()) / ppb);
        int wz = Mth.floor(camZ + (mouseY - mapViewCenterY()) / ppb);

        Minecraft mc = Minecraft.getInstance();
        int wy = 64;
        if (mc != null && mc.player != null && mc.level != null) {
            wy = mc.level.hasChunk(wx >> 4, wz >> 4)
                    ? mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz)
                    : Mth.floor(mc.player.getY());
        }

        this.renamingWaypoint = null;
        this.pendingWaypointPos = new BetterBlockPos(wx, wy, wz);
        this.nameInput.setLength(0);
        this.selectedColor = 0xFFCC66FF;
        this.nameDialogOpen = true;
    }

    private void openRenameDialog(IWaypoint wp) {
        this.renamingWaypoint = wp;
        this.pendingWaypointPos = wp.getLocation();
        this.nameInput.setLength(0);
        if (wp.getName() != null) {
            this.nameInput.append(wp.getName());
        }
        this.selectedColor = MapDiskCache.getWaypointColor(wp);
        this.nameDialogOpen = true;
    }

    private void closeNameDialog() {
        this.nameDialogOpen = false;
        this.renamingWaypoint = null;
        this.pendingWaypointPos = null;
        this.nameInput.setLength(0);
    }

    private void commitNameDialog() {
        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mone == null) {
            closeNameDialog();
            return;
        }
        IWorldData worldData = mone.getWorldProvider().getCurrentWorld();
        if (worldData == null || worldData.getWaypoints() == null || pendingWaypointPos == null) {
            closeNameDialog();
            return;
        }

        String trimmed = nameInput.toString().trim();
        if (renamingWaypoint != null) {
            String newName = trimmed.isEmpty() ? renamingWaypoint.getName() : trimmed;
            IWaypoint.Tag tag = renamingWaypoint.getTag();
            BetterBlockPos loc = renamingWaypoint.getLocation();
            String oldName = renamingWaypoint.getName();
            MapDiskCache.removeWaypointColor(renamingWaypoint);
            worldData.getWaypoints().removeWaypoint(renamingWaypoint);
            Waypoint updated = new Waypoint(newName, tag, loc);
            worldData.getWaypoints().addWaypoint(updated);
            MapDiskCache.setWaypointColor(updated, selectedColor);
            Helper.HELPER.logDirect("Updated waypoint '" + oldName + "' -> '" + newName + "'");
        } else {
            String finalName = trimmed.isEmpty()
                    ? ("wp_" + pendingWaypointPos.x + "_" + pendingWaypointPos.z)
                    : trimmed;
            Waypoint created = new Waypoint(finalName, IWaypoint.Tag.USER, pendingWaypointPos);
            worldData.getWaypoints().addWaypoint(created);
            MapDiskCache.setWaypointColor(created, selectedColor);
            Helper.HELPER.logDirect("Added waypoint '" + finalName + "' at "
                    + pendingWaypointPos.x + ", " + pendingWaypointPos.y + ", " + pendingWaypointPos.z);
        }

        closeNameDialog();
    }

    private void gotoWaypoint(IWaypoint wp) {
        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mone != null && wp != null && wp.getLocation() != null) {
            BetterBlockPos loc = wp.getLocation();
            mone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(loc));
            Helper.HELPER.logDirect("Pathing to waypoint '" + wp.getName() + "' (" + loc.x + ", " + loc.y + ", " + loc.z + ")");
        }
    }

    private void deleteWaypoint(IWaypoint wp) {
        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mone == null || wp == null) {
            return;
        }
        IWorldData worldData = mone.getWorldProvider().getCurrentWorld();
        if (worldData != null && worldData.getWaypoints() != null) {
            MapDiskCache.removeWaypointColor(wp);
            worldData.getWaypoints().removeWaypoint(wp);
            Helper.HELPER.logDirect("Deleted waypoint '" + wp.getName() + "'");
        }
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
        int targetX = Mth.floor(camX + (mouseX - mapViewCenterX()) / ppb);
        int targetZ = Mth.floor(camZ + (mouseY - mapViewCenterY()) / ppb);

        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mone != null) {
            mone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(targetX, targetZ));
            Helper.HELPER.logDirect("Pathing to X: " + targetX + ", Z: " + targetZ);
        }
    }
}
