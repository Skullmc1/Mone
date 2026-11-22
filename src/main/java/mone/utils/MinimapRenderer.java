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
import mone.api.behavior.IPathingBehavior;
import mone.api.cache.IWaypoint;
import mone.api.cache.IWorldData;
import mone.api.pathing.calc.IPath;
import mone.api.pathing.goals.Goal;
import mone.api.pathing.goals.GoalComposite;
import mone.api.pathing.goals.GoalStrictDirection;
import mone.api.pathing.path.IPathExecutor;
import mone.api.utils.BetterBlockPos;
import mone.api.utils.interfaces.IGoalRenderPos;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Top-down HUD minimap renderer for Mone (#map).
 * Features a background worker thread for chunk scanning, circular radar shape,
 * heading-up rotation, unique mob head icons with elevation arrows,
 * waypoints & death markers, and active path/goal rendering.
 */
public final class MinimapRenderer {

    public enum ViewMode {
        SURFACE,
        CAVE,
        LAYER
    }

    static final class ChunkTile {
        final int[] pixelsABGR = new int[256];
        final int[] heights = new int[256];
        final byte[] spawnRisk = new byte[256]; // 0 = safe, 1 = night-spawnable, 2 = always-spawnable
        volatile ViewMode mode;
        volatile int refY = Integer.MIN_VALUE;
        volatile boolean dirty = true;
        volatile boolean fromDisk = false;
    }

    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("mone", "minimap");
    private static final Identifier CIRCLE_FRAME_ID = Identifier.fromNamespaceAndPath("mone", "minimap_circle_frame");
    private static final int MARGIN = 8;
    private static final int BORDER = 2;
    private static final int MAX_CHUNKS_SCHEDULED_PER_FRAME = 8;
    private static final long MIN_UPLOAD_INTERVAL_MS = 40L;
    private static final long CACHE_PRUNE_INTERVAL_MS = 5000L;
    private static final double ENTITY_MAX_Y_DIFF = 10.0;
    private static final double ENTITY_ELEVATION_ARROW_THRESHOLD = 2.0;

    static final int ABGR_UNLOADED = toAbgr(0x181820);
    private static final int ABGR_VOID = toAbgr(0x0E0E14);
    private static final int COLOR_BG = 0xCC09090B;
    private static final int COLOR_BORDER_OUTER = 0xFF141418;
    private static final int COLOR_BORDER_INNER = 0xFFFF7A29;

    private static final Map<Block, Integer> SPECIAL_BLOCK_COLORS = new HashMap<>();

    static {
        // Ores (distinct vibrant colors instead of vanilla stone gray)
        SPECIAL_BLOCK_COLORS.put(Blocks.COAL_ORE, 0x2E2E2E);
        SPECIAL_BLOCK_COLORS.put(Blocks.DEEPSLATE_COAL_ORE, 0x262626);
        SPECIAL_BLOCK_COLORS.put(Blocks.IRON_ORE, 0xD8AF93);
        SPECIAL_BLOCK_COLORS.put(Blocks.DEEPSLATE_IRON_ORE, 0xC99E82);
        SPECIAL_BLOCK_COLORS.put(Blocks.COPPER_ORE, 0xC96E4B);
        SPECIAL_BLOCK_COLORS.put(Blocks.DEEPSLATE_COPPER_ORE, 0xB85F3E);
        SPECIAL_BLOCK_COLORS.put(Blocks.GOLD_ORE, 0xFCEE4B);
        SPECIAL_BLOCK_COLORS.put(Blocks.DEEPSLATE_GOLD_ORE, 0xE8D93A);
        SPECIAL_BLOCK_COLORS.put(Blocks.NETHER_GOLD_ORE, 0xF5D742);
        SPECIAL_BLOCK_COLORS.put(Blocks.REDSTONE_ORE, 0xE02424);
        SPECIAL_BLOCK_COLORS.put(Blocks.DEEPSLATE_REDSTONE_ORE, 0xCC1E1E);
        SPECIAL_BLOCK_COLORS.put(Blocks.LAPIS_ORE, 0x2652C9);
        SPECIAL_BLOCK_COLORS.put(Blocks.DEEPSLATE_LAPIS_ORE, 0x1F44B0);
        SPECIAL_BLOCK_COLORS.put(Blocks.DIAMOND_ORE, 0x4AEDD9);
        SPECIAL_BLOCK_COLORS.put(Blocks.DEEPSLATE_DIAMOND_ORE, 0x38D9C5);
        SPECIAL_BLOCK_COLORS.put(Blocks.EMERALD_ORE, 0x17DD62);
        SPECIAL_BLOCK_COLORS.put(Blocks.DEEPSLATE_EMERALD_ORE, 0x12C455);
        SPECIAL_BLOCK_COLORS.put(Blocks.NETHER_QUARTZ_ORE, 0xEAE2D5);
        SPECIAL_BLOCK_COLORS.put(Blocks.ANCIENT_DEBRIS, 0x8F5643);

        // Hazards & Points of Interest
        SPECIAL_BLOCK_COLORS.put(Blocks.LAVA, 0xF25C16);
        SPECIAL_BLOCK_COLORS.put(Blocks.WATER, 0x2F62C8);
        SPECIAL_BLOCK_COLORS.put(Blocks.SPAWNER, 0x5B3A82);
        SPECIAL_BLOCK_COLORS.put(Blocks.CHEST, 0xB8823B);
        SPECIAL_BLOCK_COLORS.put(Blocks.TRAPPED_CHEST, 0xB8753B);
        SPECIAL_BLOCK_COLORS.put(Blocks.ENDER_CHEST, 0x2C5454);
        SPECIAL_BLOCK_COLORS.put(Blocks.BARREL, 0x8C643B);
        SPECIAL_BLOCK_COLORS.put(Blocks.OBSIDIAN, 0x2A1F3D);
        SPECIAL_BLOCK_COLORS.put(Blocks.CRYING_OBSIDIAN, 0x5E2CA5);
        SPECIAL_BLOCK_COLORS.put(Blocks.BEDROCK, 0x1F1F1F);
        SPECIAL_BLOCK_COLORS.put(Blocks.AMETHYST_BLOCK, 0x9B62D6);
        SPECIAL_BLOCK_COLORS.put(Blocks.BUDDING_AMETHYST, 0xB67EF0);
    }

    private static final ConcurrentHashMap<Long, ChunkTile> CHUNK_CACHE = new ConcurrentHashMap<>();
    private static final Set<Long> PENDING_CHUNKS = ConcurrentHashMap.newKeySet();
    private static final Set<Long> DISK_MISSES = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean CACHE_UPDATED = new AtomicBoolean(false);

    private static final ExecutorService WORKER_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mone-Minimap-Worker");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private static final BlockPos.MutableBlockPos RENDER_SCAN_POS = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos WORKER_SCAN_POS = new BlockPos.MutableBlockPos();

    private static DynamicTexture texture;
    private static int textureSize = 0;
    private static boolean textureCircularMask = false;

    private static DynamicTexture circleFrameTexture;
    private static int circleFrameSize = 0;

    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedCenterZ = Integer.MIN_VALUE;
    private static ViewMode cachedMode = null;
    private static volatile Level cachedLevel = null;
    private static boolean textureDirty = true;
    private static long lastUploadMs = 0L;
    private static long lastPruneMs = 0L;

    private MinimapRenderer() {}

    static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }

    private static int keyX(long key) {
        return (int) key;
    }

    private static int keyZ(long key) {
        return (int) (key >>> 32);
    }

    public static void invalidate() {
        for (ChunkTile tile : CHUNK_CACHE.values()) {
            tile.dirty = true;
        }
        textureDirty = true;
    }

    public static void invalidateTextureOnly() {
        textureDirty = true;
    }

    public static void clearCache() {
        MapDiskCache.flushAll();
        CHUNK_CACHE.clear();
        PENDING_CHUNKS.clear();
        DISK_MISSES.clear();
        cachedCenterX = Integer.MIN_VALUE;
        cachedCenterZ = Integer.MIN_VALUE;
        cachedMode = null;
        cachedLevel = null;
        textureDirty = true;
        CACHE_UPDATED.set(false);
    }

    public static void markChunkDirty(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        DISK_MISSES.remove(key);
        ChunkTile tile = CHUNK_CACHE.get(key);
        if (tile != null) {
            tile.dirty = true;
        }
    }

    static ChunkTile getCachedTile(int cx, int cz) {
        return CHUNK_CACHE.get(chunkKey(cx, cz));
    }

    static boolean consumeCacheUpdated() {
        return CACHE_UPDATED.getAndSet(false);
    }

    /**
     * Resolves the GLFW keycode configured in {@link Settings#minimapFullscreenKey}.
     * Defaults to GLFW_KEY_M (77) if unparseable.
     */
    public static int resolveFullscreenKeyCode() {
        String keyName = MoneAPI.getSettings().minimapFullscreenKey.value;
        if (keyName == null || keyName.isBlank()) {
            return InputConstants.KEY_M;
        }
        String normalized = keyName.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("key.keyboard.")) {
            normalized = "key.keyboard." + normalized;
        }
        try {
            InputConstants.Key key = InputConstants.getKey(normalized);
            if (key != null && key != InputConstants.UNKNOWN) {
                return key.getValue();
            }
        } catch (Exception ignored) {
        }
        return InputConstants.KEY_M;
    }

    public static void openFullscreenMap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.level != null) {
            mc.gui.setScreen(new FullscreenMapScreen());
        }
    }

    public static void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Settings settings = MoneAPI.getSettings();
        if (!settings.minimap.value) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        // Hide HUD minimap while fullscreen map is open
        if (mc.gui.screen() instanceof FullscreenMapScreen) {
            return;
        }

        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mone == null) {
            return;
        }

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        if (cachedLevel != level) {
            clearCache();
            cachedLevel = level;
            MapDiskCache.switchWorld(mc, level);
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        double playerX = Mth.lerp(partialTick, player.xo, player.getX());
        double playerZ = Mth.lerp(partialTick, player.zo, player.getZ());
        float playerYaw = player.getViewYRot(partialTick);

        int centerX = Mth.floor(playerX);
        int centerY = Mth.floor(player.getY());
        int centerZ = Mth.floor(playerZ);

        boolean rotate = settings.minimapRotate.value;
        boolean circular = settings.minimapCircular.value;
        int radius = Mth.clamp(settings.minimapRadius.value, 16, 128);
        int mapSize = Mth.clamp(settings.minimapSize.value, 64, 256);

        // Circular minimap needs no 1.42x diagonal expansion; rotating square does
        int texRadius = (!circular && rotate) ? ((int) Math.ceil(radius * 1.42) + 1) : (radius + 1);
        int texSize = texRadius * 2 + 1;

        ensureTexture(mc, texSize, circular);
        if (circular) {
            ensureCircleFrameTexture(mc, mapSize + BORDER * 2);
        }

        boolean isMining = mone.getMineProcess().isActive()
                || (mone.getPathingBehavior() != null && mone.getPathingBehavior().getGoal() instanceof GoalStrictDirection);
        ViewMode activeMode = resolveViewMode(settings.minimapMode.value, level, centerX, centerY, centerZ, isMining);

        if (cachedMode != activeMode) {
            cachedMode = activeMode;
            textureDirty = true;
        }

        // Schedule stale/missing chunks on the background worker thread
        scheduleVisibleChunks(level, centerX, centerY, centerZ, texRadius, activeMode, MAX_CHUNKS_SCHEDULED_PER_FRAME);

        long now = System.currentTimeMillis();
        boolean centerMoved = (cachedCenterX != centerX || cachedCenterZ != centerZ);
        boolean workerFinished = CACHE_UPDATED.get();
        if (centerMoved || textureDirty || (workerFinished && (now - lastUploadMs) >= MIN_UPLOAD_INTERVAL_MS)) {
            CACHE_UPDATED.set(false);
            rebuildTextureFromCache(centerX, centerZ, texRadius, circular, radius);
            cachedCenterX = centerX;
            cachedCenterZ = centerZ;
            textureDirty = false;
            lastUploadMs = now;
        }

        if (now - lastPruneMs >= CACHE_PRUNE_INTERVAL_MS) {
            pruneFarChunks(centerX >> 4, centerZ >> 4, Math.max((texRadius >> 4) + 6, 16));
            lastPruneMs = now;
        }

        int screenW = guiGraphics.guiWidth();
        int screenH = guiGraphics.guiHeight();
        int footerHeight = 22;
        int mapX;
        int mapY;
        String pos = settings.minimapPosition.value == null ? "topright" : settings.minimapPosition.value.toLowerCase(Locale.ROOT);
        switch (pos) {
            case "topleft" -> {
                mapX = MARGIN + BORDER;
                mapY = MARGIN + BORDER;
            }
            case "bottomleft" -> {
                mapX = MARGIN + BORDER;
                mapY = screenH - mapSize - footerHeight - MARGIN - BORDER;
            }
            case "bottomright" -> {
                mapX = screenW - mapSize - MARGIN - BORDER;
                mapY = screenH - mapSize - footerHeight - MARGIN - BORDER;
            }
            default -> { // topright
                mapX = screenW - mapSize - MARGIN - BORDER;
                mapY = MARGIN + BORDER;
            }
        }

        double mapCenterX = mapX + mapSize * 0.5;
        double mapCenterY = mapY + mapSize * 0.5;
        double ppb = (double) mapSize / (2.0 * radius);
        double clipCircleRadius = circular ? (mapSize * 0.5 - 1.5) : -1.0;

        // Rotation angle so player's facing direction points straight UP (-Y on screen)
        double rotRad = rotate ? Math.toRadians(180.0 - playerYaw) : 0.0;
        double cosRot = Math.cos(rotRad);
        double sinRot = Math.sin(rotRad);

        // Background & square frame (circular frame is drawn on top after overlays)
        if (!circular) {
            guiGraphics.fill(mapX - BORDER, mapY - BORDER, mapX + mapSize + BORDER, mapY + mapSize + BORDER, COLOR_BORDER_OUTER);
            guiGraphics.outline(mapX - BORDER, mapY - BORDER, mapSize + BORDER * 2, mapSize + BORDER * 2, COLOR_BORDER_INNER);
            guiGraphics.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, COLOR_BG);
        }

        // Clipped map interior
        guiGraphics.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);

        // Rotated + smoothly sub-block-scrolled terrain texture
        int drawSize = (int) Math.round(texSize * ppb);
        int halfDraw = drawSize / 2;
        float subOffsetX = (float) ((centerX + 0.5 - playerX) * ppb);
        float subOffsetZ = (float) ((centerZ + 0.5 - playerZ) * ppb);

        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate((float) mapCenterX, (float) mapCenterY);
        if (rotate) {
            pose.rotate((float) rotRad);
        }
        if (!circular) {
            pose.translate(subOffsetX, subOffsetZ);
        }
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE_ID,
                -halfDraw, -halfDraw,
                0.0f, 0.0f,
                drawSize, drawSize,
                texSize, texSize,
                texSize, texSize
        );
        pose.popMatrix();

        // Path & goal overlay
        if (settings.minimapShowPath.value) {
            renderPathAndGoal(guiGraphics, mone, mapX, mapY, mapSize, mapCenterX, mapCenterY, ppb,
                    playerX, playerZ, cosRot, sinRot, clipCircleRadius);
        }

        // Waypoints & death markers
        if (settings.minimapShowWaypoints.value) {
            renderWaypoints(guiGraphics, mc.font, mone, mapX, mapY, mapSize, mapCenterX, mapCenterY, ppb,
                    playerX, playerZ, cosRot, sinRot, circular, true);
        }

        // Entity icons (mob heads & player faces with elevation arrows)
        if (settings.minimapShowEntities.value) {
            renderEntities(guiGraphics, level, player, partialTick, mapX, mapY, mapSize, mapCenterX, mapCenterY, ppb,
                    texRadius, playerX, player.getY(), playerZ, cosRot, sinRot, clipCircleRadius);
        }

        // Static upward-pointing triangle player marker (or rotated if north-locked)
        renderPlayerTriangle(guiGraphics, (int) Math.round(mapCenterX), (int) Math.round(mapCenterY),
                rotate ? 0.0f : (float) Math.toRadians(playerYaw - 180.0));

        guiGraphics.disableScissor();

        // Circular border ring overlay
        if (circular && circleFrameTexture != null) {
            int frameSize = mapSize + BORDER * 2;
            guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    CIRCLE_FRAME_ID,
                    mapX - BORDER, mapY - BORDER,
                    0.0f, 0.0f,
                    frameSize, frameSize,
                    frameSize, frameSize,
                    frameSize, frameSize
            );
        }

        // Rotating compass labels & info footer
        renderCompassAndFooter(guiGraphics, mc.font, mapX, mapY, mapSize, mapCenterX, mapCenterY,
                centerX, centerY, centerZ, cosRot, sinRot, circular, activeMode, isMining);
    }

    private static void ensureTexture(Minecraft mc, int texSize, boolean circular) {
        if (texture != null && textureSize == texSize && textureCircularMask == circular) {
            return;
        }
        if (texture != null) {
            texture.close();
        }
        texture = new DynamicTexture("mone_minimap", texSize, texSize, false);
        mc.getTextureManager().register(TEXTURE_ID, texture);
        textureSize = texSize;
        textureCircularMask = circular;
        textureDirty = true;
    }

    private static void ensureCircleFrameTexture(Minecraft mc, int frameSize) {
        if (circleFrameTexture != null && circleFrameSize == frameSize) {
            return;
        }
        if (circleFrameTexture != null) {
            circleFrameTexture.close();
        }
        circleFrameTexture = new DynamicTexture("mone_minimap_circle_frame", frameSize, frameSize, false);
        NativeImage img = circleFrameTexture.getPixels();
        if (img != null) {
            double center = (frameSize - 1) * 0.5;
            double outerR = frameSize * 0.5;
            double midR = outerR - 1.2;
            double innerR = outerR - BORDER - 0.5;
            int abgrOuter = toAbgr(0x141418);
            int abgrInner = toAbgr(0xFF7A29);

            for (int y = 0; y < frameSize; y++) {
                double dy = y - center;
                for (int x = 0; x < frameSize; x++) {
                    double dx = x - center;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist >= innerR && dist <= outerR) {
                        img.setPixelABGR(x, y, dist >= midR ? abgrOuter : abgrInner);
                    } else {
                        img.setPixelABGR(x, y, 0x00000000);
                    }
                }
            }
            circleFrameTexture.upload();
        }
        mc.getTextureManager().register(CIRCLE_FRAME_ID, circleFrameTexture);
        circleFrameSize = frameSize;
    }

    /**
     * Schedules stale or uncached loaded chunks within {@code blockRadius} around {@code (centerX, centerZ)}
     * onto the background worker thread, prioritized closest-first.
     */
    static void scheduleVisibleChunks(ClientLevel level, int centerX, int centerY, int centerZ,
                                      int blockRadius, ViewMode mode, int maxChunks) {
        int minCx = (centerX - blockRadius) >> 4;
        int maxCx = (centerX + blockRadius) >> 4;
        int minCz = (centerZ - blockRadius) >> 4;
        int maxCz = (centerZ + blockRadius) >> 4;
        int playerCx = centerX >> 4;
        int playerCz = centerZ >> 4;

        List<long[]> candidates = null;

        for (int cz = minCz; cz <= maxCz; cz++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                long key = chunkKey(cx, cz);
                if (PENDING_CHUNKS.contains(key)) {
                    continue;
                }
                ChunkTile tile = CHUNK_CACHE.get(key);
                boolean stale = tile == null
                        || tile.dirty
                        || (tile.fromDisk && level.hasChunk(cx, cz))
                        || (!tile.fromDisk && tile.mode != mode)
                        || (!tile.fromDisk && mode != ViewMode.SURFACE && tile.refY != centerY);
                if (stale) {
                    if (tile == null && DISK_MISSES.contains(key) && !level.hasChunk(cx, cz)) {
                        continue;
                    }
                    if (candidates == null) {
                        candidates = new ArrayList<>();
                    }
                    int dcx = cx - playerCx;
                    int dcz = cz - playerCz;
                    candidates.add(new long[]{cx, cz, (long) dcx * dcx + (long) dcz * dcz});
                }
            }
        }

        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        candidates.sort(Comparator.comparingLong(a -> a[2]));

        int scheduled = 0;
        int minY = level.getMinY();
        int maxY = level.getMaxY() - 1;

        for (long[] entry : candidates) {
            if (scheduled >= maxChunks) {
                break;
            }
            int cx = (int) entry[0];
            int cz = (int) entry[1];
            long key = chunkKey(cx, cz);
            LevelChunk chunk = (LevelChunk) level.getChunk(cx, cz, ChunkStatus.FULL, false);
            boolean isLoaded = chunk != null && !chunk.isEmpty();

            if (!isLoaded) {
                if (DISK_MISSES.contains(key) || !PENDING_CHUNKS.add(key)) {
                    continue;
                }
                final ClientLevel capturedLevel = level;
                final int fCx = cx;
                final int fCz = cz;
                WORKER_EXECUTOR.execute(() -> {
                    try {
                        if (cachedLevel != capturedLevel) {
                            return;
                        }
                        MapDiskCache.StoredChunk stored = MapDiskCache.loadChunk(fCx, fCz);
                        if (stored != null) {
                            ChunkTile tile = CHUNK_CACHE.computeIfAbsent(key, k -> new ChunkTile());
                            System.arraycopy(stored.pixelsABGR, 0, tile.pixelsABGR, 0, 256);
                            System.arraycopy(stored.heights, 0, tile.heights, 0, 256);
                            java.util.Arrays.fill(tile.spawnRisk, (byte) 0);
                            tile.mode = ViewMode.SURFACE;
                            tile.fromDisk = true;
                            tile.dirty = false;
                            CACHE_UPDATED.set(true);
                        } else {
                            DISK_MISSES.add(key);
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        PENDING_CHUNKS.remove(key);
                    }
                });
                scheduled++;
                continue;
            }

            if (!PENDING_CHUNKS.add(key)) {
                continue;
            }
            DISK_MISSES.remove(key);

            final ClientLevel capturedLevel = level;
            final LevelChunk capturedChunk = chunk;
            final int fCx = cx;
            final int fCz = cz;
            final int fPlayerY = centerY;
            final ViewMode fMode = mode;

            WORKER_EXECUTOR.execute(() -> {
                try {
                    if (cachedLevel != capturedLevel) {
                        return;
                    }
                    ChunkTile tile = CHUNK_CACHE.computeIfAbsent(key, k -> new ChunkTile());
                    ChunkTile northTile = CHUNK_CACHE.get(chunkKey(fCx, fCz - 1));
                    fillChunkTile(capturedLevel, capturedChunk, tile, northTile, fCx, fCz, fPlayerY, minY, maxY, fMode);
                    if (fMode == ViewMode.SURFACE) {
                        MapDiskCache.saveChunk(fCx, fCz, tile.pixelsABGR, tile.heights);
                        MapDiskCache.maybeFlushPeriodic();
                    }
                    CACHE_UPDATED.set(true);
                } catch (Throwable ignored) {
                    // Safely ignore transient chunk unload races on background worker
                } finally {
                    PENDING_CHUNKS.remove(key);
                }
            });

            scheduled++;
        }
    }

    private static void fillChunkTile(ClientLevel level, LevelChunk chunk, ChunkTile tile, ChunkTile northTile,
                                      int cx, int cz, int playerY, int minY, int maxY, ViewMode mode) {
        int baseX = cx << 4;
        int baseZ = cz << 4;
        int[] pixels = tile.pixelsABGR;
        int[] heights = tile.heights;
        byte[] spawnRisk = tile.spawnRisk;
        int[] northHeights = (northTile != null && !northTile.dirty && northTile.mode == mode) ? northTile.heights : null;

        for (int lz = 0; lz < 16; lz++) {
            int wz = baseZ + lz;
            int rowOffset = lz << 4;
            for (int lx = 0; lx < 16; lx++) {
                int wx = baseX + lx;
                int idx = rowOffset | lx;

                int northY = Integer.MIN_VALUE;
                if (lz > 0) {
                    northY = heights[((lz - 1) << 4) | lx];
                } else if (northHeights != null) {
                    northY = northHeights[(15 << 4) | lx];
                }

                switch (mode) {
                    case SURFACE -> pixels[idx] = sampleSurface(level, chunk, WORKER_SCAN_POS, wx, wz, lx, lz, minY, maxY, northY, heights, spawnRisk, idx);
                    case CAVE -> pixels[idx] = sampleCave(level, chunk, WORKER_SCAN_POS, wx, playerY, wz, minY, maxY, northY, heights, spawnRisk, idx);
                    case LAYER -> pixels[idx] = sampleLayer(level, chunk, WORKER_SCAN_POS, wx, playerY, wz, minY, maxY, heights, spawnRisk, idx);
                }
            }
        }

        tile.mode = mode;
        tile.refY = playerY;
        tile.fromDisk = false;
        tile.dirty = false;
    }

    private static void rebuildTextureFromCache(int centerX, int centerZ, int texRadius, boolean circular, int visibleRadius) {
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }

        Settings settings = MoneAPI.getSettings();
        boolean showSpawns = settings.minimapSpawnOverlay.value;
        boolean showGrid = settings.minimapChunkGrid.value;

        double maxDistSq = (visibleRadius + 0.25) * (visibleRadius + 0.25);
        int lastCx = Integer.MIN_VALUE;
        int lastCz = Integer.MIN_VALUE;
        ChunkTile cachedTile = null;

        for (int dz = -texRadius; dz <= texRadius; dz++) {
            int wz = centerZ + dz;
            int tz = dz + texRadius;
            int cz = wz >> 4;
            int lzRow = (wz & 15) << 4;
            int dzSq = dz * dz;

            for (int dx = -texRadius; dx <= texRadius; dx++) {
                int tx = dx + texRadius;
                if (circular && (dx * dx + dzSq) > maxDistSq) {
                    pixels.setPixelABGR(tx, tz, 0x00000000);
                    continue;
                }

                int wx = centerX + dx;
                int cx = wx >> 4;

                if (cx != lastCx || cz != lastCz) {
                    cachedTile = CHUNK_CACHE.get(chunkKey(cx, cz));
                    lastCx = cx;
                    lastCz = cz;
                }

                int localIdx = lzRow | (wx & 15);
                int abgr = (cachedTile != null) ? cachedTile.pixelsABGR[localIdx] : ABGR_UNLOADED;
                byte risk = (cachedTile != null) ? cachedTile.spawnRisk[localIdx] : 0;
                abgr = applyMapOverlays(abgr, risk, wx, wz, showSpawns, showGrid);
                pixels.setPixelABGR(tx, tz, abgr);
            }
        }

        texture.upload();
    }

    static int applyMapOverlays(int abgr, byte spawnRisk, int wx, int wz, boolean showSpawns, boolean showGrid) {
        if (showSpawns && spawnRisk != 0) {
            int overlayRgb = (spawnRisk == 2) ? 0xFF2828 : 0xFFE033;
            abgr = blendAbgrWithRgb(abgr, overlayRgb, 0.42f);
        }
        if (showGrid && ((wx & 15) == 0 || (wz & 15) == 0)) {
            abgr = blendAbgrWithRgb(abgr, 0xA8C0E8, 0.28f);
        }
        return abgr;
    }

    private static int blendAbgrWithRgb(int abgr, int overlayRgb, float alpha) {
        int r = abgr & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        int or = (overlayRgb >> 16) & 0xFF;
        int og = (overlayRgb >> 8) & 0xFF;
        int ob = overlayRgb & 0xFF;
        float inv = 1.0f - alpha;
        int nr = Mth.clamp((int) (r * inv + or * alpha), 0, 255);
        int ng = Mth.clamp((int) (g * inv + og * alpha), 0, 255);
        int nb = Mth.clamp((int) (b * inv + ob * alpha), 0, 255);
        return 0xFF000000 | (nb << 16) | (ng << 8) | nr;
    }

    private static void pruneFarChunks(int playerCx, int playerCz, int maxChunkDist) {
        Iterator<Map.Entry<Long, ChunkTile>> it = CHUNK_CACHE.entrySet().iterator();
        while (it.hasNext()) {
            long key = it.next().getKey();
            int cx = keyX(key);
            int cz = keyZ(key);
            if (Math.abs(cx - playerCx) > maxChunkDist || Math.abs(cz - playerCz) > maxChunkDist) {
                it.remove();
            }
        }
    }

    static ViewMode resolveViewMode(String modeSetting, ClientLevel level, int px, int py, int pz, boolean isMining) {
        if (modeSetting != null) {
            switch (modeSetting.toLowerCase(Locale.ROOT)) {
                case "surface" -> {
                    return ViewMode.SURFACE;
                }
                case "cave" -> {
                    return ViewMode.CAVE;
                }
                case "layer", "mine", "mining" -> {
                    return ViewMode.LAYER;
                }
                default -> {
                    // auto
                }
            }
        }

        if (isMining) {
            return ViewMode.LAYER;
        }

        if (level.dimensionType().hasCeiling()) {
            return ViewMode.CAVE;
        }

        RENDER_SCAN_POS.set(px, py + 1, pz);
        if (!level.canSeeSky(RENDER_SCAN_POS)) {
            int skyLight = level.getBrightness(LightLayer.SKY, RENDER_SCAN_POS);
            if (skyLight == 0) {
                return ViewMode.CAVE;
            }
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
            if (surfaceY - py >= 6 && hasSolidRoof(level, RENDER_SCAN_POS, px, py + 2, pz, Math.min(surfaceY, py + 20))) {
                return ViewMode.CAVE;
            }
        }

        return ViewMode.SURFACE;
    }

    private static boolean hasSolidRoof(Level level, BlockPos.MutableBlockPos pos, int x, int startY, int z, int endY) {
        int solidCount = 0;
        for (int y = startY; y <= endY; y++) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty() && !state.getCollisionShape(level, pos).isEmpty()) {
                solidCount++;
                if (solidCount >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private static byte computeSpawnRisk(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos,
                                         int wx, int surfaceY, int wz, BlockState surfaceState, int maxY) {
        if (surfaceY >= maxY || surfaceState == null || !surfaceState.getFluidState().isEmpty()) {
            return 0;
        }
        pos.set(wx, surfaceY, wz);
        if (!surfaceState.isCollisionShapeFullBlock(level, pos)) {
            return 0;
        }
        pos.set(wx, surfaceY + 1, wz);
        BlockState above = chunk.getBlockState(pos);
        if (!above.isAir() || !above.getFluidState().isEmpty()) {
            return 0;
        }
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        if (blockLight > 0) {
            return 0;
        }
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        return (skyLight == 0) ? (byte) 2 : (byte) 1;
    }

    private static int sampleSurface(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos,
                                     int wx, int wz, int lx, int lz, int minY, int maxY, int northY,
                                     int[] heights, byte[] spawnRisk, int idx) {
        int y = Math.min(maxY, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz));

        BlockState state = null;
        while (y >= minY) {
            pos.set(wx, y, wz);
            BlockState s = chunk.getBlockState(pos);
            if (!s.isAir() && s.getMapColor(level, pos) != MapColor.NONE) {
                state = s;
                break;
            }
            y--;
        }

        if (state == null) {
            heights[idx] = Integer.MIN_VALUE;
            spawnRisk[idx] = 0;
            return ABGR_VOID;
        }

        heights[idx] = y;

        if (state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.FLOWING_WATER)) {
            spawnRisk[idx] = 0;
            pos.set(wx, y, wz);
            int biomeWater = BiomeColors.getAverageWaterColor(level, pos) & 0xFFFFFF;
            int depth = 0;
            int scanY = y - 1;
            while (scanY >= minY && depth < 16) {
                pos.set(wx, scanY, wz);
                BlockState below = chunk.getBlockState(pos);
                if (!below.getFluidState().is(Fluids.WATER) && !below.getFluidState().is(Fluids.FLOWING_WATER)) {
                    break;
                }
                depth++;
                scanY--;
            }
            float shade = 1.08f - (depth / 16.0f) * 0.45f;
            return toAbgr(shadeRgb(biomeWater, shade));
        }

        spawnRisk[idx] = computeSpawnRisk(level, chunk, pos, wx, y, wz, state, maxY);
        pos.set(wx, y, wz);
        int rgb = getBlockRgb(level, pos, state);
        float shade = 1.0f;
        if (northY != Integer.MIN_VALUE) {
            if (y > northY) {
                shade = 1.14f;
            } else if (y < northY) {
                shade = 0.84f;
            }
        }

        return toAbgr(shadeRgb(rgb, shade));
    }

    private static int sampleCave(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos,
                                  int wx, int playerY, int wz, int minY, int maxY, int northY,
                                  int[] heights, byte[] spawnRisk, int idx) {
        int startY = Mth.clamp(playerY + 1, minY, maxY);

        pos.set(wx, startY, wz);
        BlockState startState = chunk.getBlockState(pos);

        int floorY = Integer.MIN_VALUE;
        BlockState floorState = null;

        if (isPassableForCave(level, pos, startState)) {
            int scanMin = Math.max(minY, playerY - 24);
            for (int y = startY; y >= scanMin; y--) {
                pos.set(wx, y, wz);
                BlockState s = chunk.getBlockState(pos);
                if (!isPassableForCave(level, pos, s)) {
                    floorY = y;
                    floorState = s;
                    break;
                }
            }
        } else {
            int scanMax = Math.min(maxY, playerY + 12);
            for (int y = startY + 1; y <= scanMax; y++) {
                pos.set(wx, y, wz);
                BlockState s = chunk.getBlockState(pos);
                if (isPassableForCave(level, pos, s)) {
                    floorY = y - 1;
                    pos.set(wx, floorY, wz);
                    floorState = chunk.getBlockState(pos);
                    break;
                }
            }

            if (floorState == null) {
                int scanMin = Math.max(minY, playerY - 16);
                boolean foundAir = false;
                for (int y = startY - 1; y >= scanMin; y--) {
                    pos.set(wx, y, wz);
                    BlockState s = chunk.getBlockState(pos);
                    if (!foundAir) {
                        if (isPassableForCave(level, pos, s)) {
                            foundAir = true;
                        }
                    } else if (!isPassableForCave(level, pos, s)) {
                        floorY = y;
                        floorState = s;
                        break;
                    }
                }
            }
        }

        if (floorState == null) {
            heights[idx] = Integer.MIN_VALUE;
            spawnRisk[idx] = 0;
            pos.set(wx, Mth.clamp(playerY, minY, maxY), wz);
            BlockState wallState = chunk.getBlockState(pos);
            Integer special = SPECIAL_BLOCK_COLORS.get(wallState.getBlock());
            if (special != null) {
                return toAbgr(shadeRgb(special, 0.55f));
            }
            int wallRgb = getBlockRgb(level, pos, wallState);
            return toAbgr(shadeRgb(wallRgb, 0.24f));
        }

        heights[idx] = floorY;
        spawnRisk[idx] = computeSpawnRisk(level, chunk, pos, wx, floorY, wz, floorState, maxY);
        pos.set(wx, floorY, wz);
        int rgb = getBlockRgb(level, pos, floorState);

        int dy = floorY - (playerY - 1);
        float shade = Mth.clamp(1.0f + dy * 0.03f, 0.45f, 1.25f);
        if (northY != Integer.MIN_VALUE) {
            if (floorY > northY) {
                shade *= 1.10f;
            } else if (floorY < northY) {
                shade *= 0.88f;
            }
        }

        return toAbgr(shadeRgb(rgb, shade));
    }

    private static int sampleLayer(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos,
                                   int wx, int playerY, int wz, int minY, int maxY,
                                   int[] heights, byte[] spawnRisk, int idx) {
        int footY = Mth.clamp(playerY, minY, maxY);
        int headY = Mth.clamp(playerY + 1, minY, maxY);

        pos.set(wx, footY, wz);
        BlockState footState = chunk.getBlockState(pos);
        pos.set(wx, headY, wz);
        BlockState headState = chunk.getBlockState(pos);

        Integer footSpecial = SPECIAL_BLOCK_COLORS.get(footState.getBlock());
        if (footSpecial != null && footState.getFluidState().isEmpty()) {
            heights[idx] = footY;
            spawnRisk[idx] = 0;
            return toAbgr(footSpecial);
        }
        Integer headSpecial = SPECIAL_BLOCK_COLORS.get(headState.getBlock());
        if (headSpecial != null && headState.getFluidState().isEmpty()) {
            heights[idx] = headY;
            spawnRisk[idx] = 0;
            return toAbgr(headSpecial);
        }

        pos.set(wx, footY, wz);
        if (!isPassableForCave(level, pos, footState)) {
            heights[idx] = footY;
            spawnRisk[idx] = 0;
            int rgb = getBlockRgb(level, pos, footState);
            float shade = footState.getFluidState().isEmpty() ? 0.58f : 1.0f;
            return toAbgr(shadeRgb(rgb, shade));
        }

        int scanMin = Math.max(minY, footY - 12);
        for (int y = footY - 1; y >= scanMin; y--) {
            pos.set(wx, y, wz);
            BlockState below = chunk.getBlockState(pos);
            if (!isPassableForCave(level, pos, below)) {
                heights[idx] = y;
                spawnRisk[idx] = computeSpawnRisk(level, chunk, pos, wx, y, wz, below, maxY);
                pos.set(wx, y, wz);
                int rgb = getBlockRgb(level, pos, below);
                int depth = (footY - 1) - y;
                float shade = below.getFluidState().isEmpty()
                        ? Mth.clamp(1.12f - depth * 0.065f, 0.42f, 1.12f)
                        : 1.0f;
                return toAbgr(shadeRgb(rgb, shade));
            }
        }

        heights[idx] = Integer.MIN_VALUE;
        spawnRisk[idx] = 0;
        return ABGR_VOID;
    }

    private static boolean isPassableForCave(ClientLevel level, BlockPos pos, BlockState state) {
        return state.isAir() || (state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty());
    }

    private static int getBlockRgb(ClientLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        Integer special = SPECIAL_BLOCK_COLORS.get(block);
        if (special != null) {
            return special;
        }
        // Biome-tinted grass, foliage, and water
        if (block == Blocks.GRASS_BLOCK || block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS
                || block == Blocks.FERN || block == Blocks.LARGE_FERN || block == Blocks.SUGAR_CANE) {
            return BiomeColors.getAverageGrassColor(level, pos) & 0xFFFFFF;
        }
        if (state.is(BlockTags.LEAVES)) {
            if (block == Blocks.CHERRY_LEAVES || block == Blocks.AZALEA_LEAVES || block == Blocks.FLOWERING_AZALEA_LEAVES
                    || block == Blocks.PALE_OAK_LEAVES) {
                MapColor mc = state.getMapColor(level, pos);
                if (mc != null && mc != MapColor.NONE) {
                    return mc.col;
                }
            }
            return shadeRgb(BiomeColors.getAverageFoliageColor(level, pos) & 0xFFFFFF, 0.85f);
        }
        if (block == Blocks.VINE || block == Blocks.LILY_PAD) {
            return BiomeColors.getAverageFoliageColor(level, pos) & 0xFFFFFF;
        }
        MapColor mapColor = state.getMapColor(level, pos);
        if (mapColor != null && mapColor != MapColor.NONE) {
            return mapColor.col;
        }
        return 0x555555;
    }

    private static int shadeRgb(int rgb, float factor) {
        int r = Mth.clamp((int) (((rgb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp((int) ((rgb & 0xFF) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    static int toAbgr(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private static int colorToArgb(Color color) {
        return (0xFF << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }

    private static boolean insideCircle(double x, double y, double cx, double cy, double radius) {
        if (radius <= 0.0) {
            return true;
        }
        double dx = x - cx;
        double dy = y - cy;
        return (dx * dx + dy * dy) <= radius * radius;
    }

    static void renderPathAndGoal(GuiGraphicsExtractor guiGraphics, IMone mone,
                                  int mapX, int mapY, int mapSize,
                                  double mapCenterX, double mapCenterY, double ppb,
                                  double originX, double originZ, double cosRot, double sinRot,
                                  double clipCircleRadius) {
        IPathingBehavior pathing = mone.getPathingBehavior();
        if (pathing == null) {
            return;
        }

        Settings settings = MoneAPI.getSettings();

        pathing.getInProgress().ifPresent(finder ->
                finder.bestPathSoFar().ifPresent(best ->
                        drawPath2D(guiGraphics, best.positions(), 0, colorToArgb(settings.colorBestPathSoFar.value),
                                mapX, mapY, mapSize, mapCenterX, mapCenterY, ppb, originX, originZ, cosRot, sinRot, clipCircleRadius)
                )
        );

        IPathExecutor next = pathing.getNext();
        if (next != null && next.getPath() != null) {
            drawPath2D(guiGraphics, next.getPath().positions(), 0, colorToArgb(settings.colorNextPath.value),
                    mapX, mapY, mapSize, mapCenterX, mapCenterY, ppb, originX, originZ, cosRot, sinRot, clipCircleRadius);
        }

        IPathExecutor current = pathing.getCurrent();
        if (current != null && current.getPath() != null) {
            IPath path = current.getPath();
            int startIdx = Math.max(current.getPosition() - 1, 0);
            drawPath2D(guiGraphics, path.positions(), startIdx, colorToArgb(settings.colorCurrentPath.value),
                    mapX, mapY, mapSize, mapCenterX, mapCenterY, ppb, originX, originZ, cosRot, sinRot, clipCircleRadius);
        }

        Goal goal = pathing.getGoal();
        if (goal != null) {
            int goalColor = colorToArgb(settings.colorGoalBox.value);
            renderGoalMarker(guiGraphics, goal, goalColor, mapX, mapY, mapSize, mapCenterX, mapCenterY, ppb,
                    originX, originZ, cosRot, sinRot, clipCircleRadius, 0);
        }
    }

    private static void renderGoalMarker(GuiGraphicsExtractor guiGraphics, Goal goal, int color,
                                         int mapX, int mapY, int mapSize,
                                         double mapCenterX, double mapCenterY, double ppb,
                                         double originX, double originZ, double cosRot, double sinRot,
                                         double clipCircleRadius, int depth) {
        if (depth > 2) {
            return;
        }
        if (goal instanceof IGoalRenderPos renderPos) {
            BlockPos gp = renderPos.getGoalPos();
            if (gp != null) {
                double dx = gp.getX() + 0.5 - originX;
                double dz = gp.getZ() + 0.5 - originZ;
                int sx = (int) Math.round(mapCenterX + (dx * cosRot - dz * sinRot) * ppb);
                int sy = (int) Math.round(mapCenterY + (dx * sinRot + dz * cosRot) * ppb);
                if (sx >= mapX - 4 && sx <= mapX + mapSize + 4 && sy >= mapY - 4 && sy <= mapY + mapSize + 4
                        && insideCircle(sx, sy, mapCenterX, mapCenterY, clipCircleRadius)) {
                    guiGraphics.fill(sx - 3, sy - 3, sx + 4, sy + 4, 0xFF000000);
                    guiGraphics.fill(sx - 2, sy - 2, sx + 3, sy + 3, color);
                    guiGraphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFFFFFFFF);
                }
            }
        } else if (goal instanceof GoalComposite composite) {
            Goal[] subGoals = composite.goals();
            int limit = Math.min(subGoals.length, 32);
            for (int i = 0; i < limit; i++) {
                renderGoalMarker(guiGraphics, subGoals[i], color, mapX, mapY, mapSize,
                        mapCenterX, mapCenterY, ppb, originX, originZ, cosRot, sinRot, clipCircleRadius, depth + 1);
            }
        }
    }

    private static void drawPath2D(GuiGraphicsExtractor guiGraphics, List<BetterBlockPos> positions, int startIndex, int color,
                                   int mapX, int mapY, int mapSize,
                                   double mapCenterX, double mapCenterY, double ppb,
                                   double originX, double originZ, double cosRot, double sinRot,
                                   double clipCircleRadius) {
        if (positions == null || positions.size() < 2 || startIndex >= positions.size() - 1) {
            return;
        }

        BetterBlockPos first = positions.get(startIndex);
        double fdx = first.x + 0.5 - originX;
        double fdz = first.z + 0.5 - originZ;
        int prevSx = (int) Math.round(mapCenterX + (fdx * cosRot - fdz * sinRot) * ppb);
        int prevSy = (int) Math.round(mapCenterY + (fdx * sinRot + fdz * cosRot) * ppb);

        int minX = mapX - 4;
        int maxX = mapX + mapSize + 4;
        int minY = mapY - 4;
        int maxY = mapY + mapSize + 4;

        for (int i = startIndex + 1; i < positions.size(); i++) {
            BetterBlockPos pos = positions.get(i);
            double dx = pos.x + 0.5 - originX;
            double dz = pos.z + 0.5 - originZ;
            int sx = (int) Math.round(mapCenterX + (dx * cosRot - dz * sinRot) * ppb);
            int sy = (int) Math.round(mapCenterY + (dx * sinRot + dz * cosRot) * ppb);

            if ((prevSx < minX && sx < minX) || (prevSx > maxX && sx > maxX)
                    || (prevSy < minY && sy < minY) || (prevSy > maxY && sy > maxY)) {
                prevSx = sx;
                prevSy = sy;
                continue;
            }

            if (sx != prevSx || sy != prevSy) {
                drawLine(guiGraphics, prevSx, prevSy, sx, sy, color, mapCenterX, mapCenterY, clipCircleRadius);
                prevSx = sx;
                prevSy = sy;
            }
        }
    }

    private static void drawLine(GuiGraphicsExtractor guiGraphics, int x0, int y0, int x1, int y1, int color,
                                 double cx, double cy, double clipCircleRadius) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            if (insideCircle(x0, y0, cx, cy, clipCircleRadius)) {
                guiGraphics.fill(x0, y0, x0 + 2, y0 + 2, color);
            }
            return;
        }
        int stepInc = steps > 4 ? 2 : 1;
        for (int i = 0; i <= steps; i += stepInc) {
            int x = x0 + (dx * i) / steps;
            int y = y0 + (dy * i) / steps;
            if (!insideCircle(x, y, cx, cy, clipCircleRadius)) {
                continue;
            }
            guiGraphics.fill(x - 1, y - 1, x + 2, y + 2, 0x99000000);
            guiGraphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    static void renderWaypoints(GuiGraphicsExtractor guiGraphics, Font font, IMone mone,
                                int mapX, int mapY, int mapSize,
                                double mapCenterX, double mapCenterY, double ppb,
                                double originX, double originZ, double cosRot, double sinRot,
                                boolean circular, boolean clampToRim) {
        IWorldData worldData = mone.getWorldProvider().getCurrentWorld();
        if (worldData == null || worldData.getWaypoints() == null) {
            return;
        }

        double rimRadius = mapSize * 0.5 - 5.0;
        int minX = mapX + 4;
        int maxX = mapX + mapSize - 4;
        int minY = mapY + 4;
        int maxY = mapY + mapSize - 4;

        for (IWaypoint wp : worldData.getWaypoints().getAllWaypoints()) {
            BetterBlockPos loc = wp.getLocation();
            if (loc == null) {
                continue;
            }

            double dx = loc.x + 0.5 - originX;
            double dz = loc.z + 0.5 - originZ;
            double rx = (dx * cosRot - dz * sinRot) * ppb;
            double rz = (dx * sinRot + dz * cosRot) * ppb;

            boolean offScreen = false;
            if (circular) {
                double dist = Math.sqrt(rx * rx + rz * rz);
                if (dist > rimRadius) {
                    if (!clampToRim) {
                        continue;
                    }
                    double s = rimRadius / dist;
                    rx *= s;
                    rz *= s;
                    offScreen = true;
                }
            } else {
                double half = mapSize * 0.5 - 4.0;
                if (Math.abs(rx) > half || Math.abs(rz) > half) {
                    if (!clampToRim) {
                        continue;
                    }
                    double s = half / Math.max(Math.abs(rx), Math.abs(rz));
                    rx *= s;
                    rz *= s;
                    offScreen = true;
                }
            }

            int sx = (int) Math.round(mapCenterX + rx);
            int sy = (int) Math.round(mapCenterY + rz);
            if (!clampToRim && (sx < minX || sx > maxX || sy < minY || sy > maxY)) {
                continue;
            }

            drawWaypointMarker(guiGraphics, sx, sy, wp, offScreen);
        }
    }

    static void drawWaypointMarker(GuiGraphicsExtractor guiGraphics, int sx, int sy, IWaypoint wp, boolean compact) {
        int color = MapDiskCache.getWaypointColor(wp);
        IWaypoint.Tag tag = wp.getTag();
        if (tag == IWaypoint.Tag.DEATH) {
            // Red (or custom colored) X death marker
            int r = compact ? 2 : 3;
            for (int d = -r; d <= r; d++) {
                guiGraphics.fill(sx + d - 1, sy + d - 1, sx + d + 2, sy + d + 2, 0xCC000000);
                guiGraphics.fill(sx + d - 1, sy - d - 1, sx + d + 2, sy - d + 2, 0xCC000000);
            }
            for (int d = -r; d <= r; d++) {
                guiGraphics.fill(sx + d, sy + d, sx + d + 1, sy + d + 1, color);
                guiGraphics.fill(sx + d, sy - d, sx + d + 1, sy - d + 1, color);
            }
            guiGraphics.fill(sx, sy, sx + 1, sy + 1, 0xFFFFFFFF);
        } else if (compact) {
            // Directional rim pip for off-screen waypoints
            guiGraphics.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xDD000000);
            guiGraphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, color);
        } else {
            // Crisp diamond marker for in-bounds HOME / BED / USER waypoints
            guiGraphics.fill(sx - 1, sy - 3, sx + 2, sy + 4, 0xDD000000);
            guiGraphics.fill(sx - 3, sy - 1, sx + 4, sy + 2, 0xDD000000);
            guiGraphics.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xDD000000);

            guiGraphics.fill(sx, sy - 2, sx + 1, sy + 3, color);
            guiGraphics.fill(sx - 2, sy, sx + 3, sy + 1, color);
            guiGraphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, color);
            guiGraphics.fill(sx, sy, sx + 1, sy + 1, 0xFFFFFFFF);
        }
    }

    static int getDefaultWaypointColor(IWaypoint.Tag tag) {
        if (tag == null) {
            return 0xFFCC66FF;
        }
        return switch (tag) {
            case DEATH -> 0xFFFF2E2E;
            case HOME -> 0xFFFFCC33;
            case BED -> 0xFF44BBFF;
            case USER -> 0xFFCC66FF;
        };
    }

    static void renderEntities(GuiGraphicsExtractor guiGraphics, ClientLevel level, LocalPlayer player, float partialTick,
                               int mapX, int mapY, int mapSize,
                               double mapCenterX, double mapCenterY, double ppb,
                               int maxRange, double originX, double playerY, double originZ,
                               double cosRot, double sinRot, double clipCircleRadius) {
        double maxDist = maxRange + 2.0;
        int minX = mapX - 6;
        int maxX = mapX + mapSize + 6;
        int minY = mapY - 6;
        int maxY = mapY + mapSize + 6;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity == null || entity == player || entity.isRemoved()) {
                continue;
            }

            boolean isOtherPlayer = entity instanceof Player;

            // Strict 10-block Y limit for non-player entities; players are always shown
            double signedDy = entity.getY() - playerY;
            if (!isOtherPlayer && Math.abs(signedDy) > ENTITY_MAX_Y_DIFF) {
                continue;
            }

            double ex = Mth.lerp(partialTick, entity.xo, entity.getX());
            double ez = Mth.lerp(partialTick, entity.zo, entity.getZ());
            double dx = ex - originX;
            double dz = ez - originZ;
            if (Math.abs(dx) > maxDist || Math.abs(dz) > maxDist) {
                continue;
            }

            int sx = (int) Math.round(mapCenterX + (dx * cosRot - dz * sinRot) * ppb);
            int sy = (int) Math.round(mapCenterY + (dx * sinRot + dz * cosRot) * ppb);
            if (sx < minX || sx > maxX || sy < minY || sy > maxY) {
                continue;
            }
            if (!insideCircle(sx, sy, mapCenterX, mapCenterY, clipCircleRadius)) {
                continue;
            }

            int borderColor;
            if (isOtherPlayer) {
                borderColor = 0xFF33CCFF;
            } else if (entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER) {
                borderColor = 0xFFFF3B3B;
            } else if (entity instanceof ItemEntity) {
                borderColor = 0xFFFFDD44;
            } else if (entity instanceof LivingEntity && entity.getType().getCategory().isFriendly()) {
                borderColor = 0xFF55EE55;
            } else if (entity instanceof LivingEntity) {
                borderColor = 0xFFFFAA33;
            } else {
                borderColor = 0xFFCCCCCC;
            }

            MobHeadRegistry.HeadIcon headIcon = MobHeadRegistry.getIcon(entity);
            int halfIcon;
            if (headIcon != null) {
                int iconSize = isOtherPlayer ? 9 : 8;
                halfIcon = iconSize / 2;
                int ix = sx - halfIcon;
                int iy = sy - halfIcon;
                guiGraphics.fill(ix - 1, iy - 1, ix + iconSize + 1, iy + iconSize + 1, borderColor);
                headIcon.render(guiGraphics, ix, iy, iconSize);
            } else if (entity instanceof ItemEntity) {
                halfIcon = 2;
                guiGraphics.fill(sx - 1, sy - 2, sx + 2, sy + 3, 0xCC000000);
                guiGraphics.fill(sx - 2, sy - 1, sx + 3, sy + 2, 0xCC000000);
                guiGraphics.fill(sx, sy - 1, sx + 1, sy + 2, borderColor);
                guiGraphics.fill(sx - 1, sy, sx + 2, sy + 1, borderColor);
            } else {
                halfIcon = 2;
                guiGraphics.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xCC000000);
                guiGraphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, borderColor);
            }

            // Elevation indicator arrow when entity is >2 blocks above or below the player
            if (signedDy > ENTITY_ELEVATION_ARROW_THRESHOLD) {
                drawElevationArrowUp(guiGraphics, sx, sy - halfIcon - 4);
            } else if (signedDy < -ENTITY_ELEVATION_ARROW_THRESHOLD) {
                drawElevationArrowDown(guiGraphics, sx, sy + halfIcon + 2);
            }
        }
    }

    private static void drawElevationArrowUp(GuiGraphicsExtractor guiGraphics, int cx, int topY) {
        // 5x3 upward arrow with black outline
        guiGraphics.fill(cx - 1, topY - 1, cx + 2, topY + 3, 0xCC000000);
        guiGraphics.fill(cx - 3, topY + 1, cx + 4, topY + 3, 0xCC000000);
        guiGraphics.fill(cx, topY, cx + 1, topY + 1, 0xFFFFFFFF);
        guiGraphics.fill(cx - 1, topY + 1, cx + 2, topY + 2, 0xFFFFFFFF);
        guiGraphics.fill(cx - 2, topY + 2, cx + 3, topY + 3, 0xFFFFFFFF);
    }

    private static void drawElevationArrowDown(GuiGraphicsExtractor guiGraphics, int cx, int topY) {
        // 5x3 downward arrow with black outline
        guiGraphics.fill(cx - 3, topY - 1, cx + 4, topY + 1, 0xCC000000);
        guiGraphics.fill(cx - 1, topY - 1, cx + 2, topY + 3, 0xCC000000);
        guiGraphics.fill(cx - 2, topY, cx + 3, topY + 1, 0xFFBBBBFF);
        guiGraphics.fill(cx - 1, topY + 1, cx + 2, topY + 2, 0xFFBBBBFF);
        guiGraphics.fill(cx, topY + 2, cx + 1, topY + 3, 0xFFBBBBFF);
    }

    /**
     * Draws a crisp GPS-style upward-pointing triangle for the player.
     */
    static void renderPlayerTriangle(GuiGraphicsExtractor guiGraphics, int cx, int cy, float angleRad) {
        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        if (angleRad != 0.0f) {
            pose.rotate(angleRad);
        }

        // Black outline triangle
        guiGraphics.fill(-1, -6, 2, -5, 0xFF000000);
        guiGraphics.fill(-2, -5, 3, -3, 0xFF000000);
        guiGraphics.fill(-3, -3, 4, -1, 0xFF000000);
        guiGraphics.fill(-4, -1, 5, 1, 0xFF000000);
        guiGraphics.fill(-5, 1, 6, 4, 0xFF000000);

        // White inner chevron triangle
        guiGraphics.fill(0, -5, 1, -3, 0xFFFFFFFF);
        guiGraphics.fill(-1, -3, 2, -1, 0xFFFFFFFF);
        guiGraphics.fill(-2, -1, 3, 1, 0xFFFFFFFF);
        guiGraphics.fill(-3, 1, 4, 2, 0xFFFFFFFF);
        guiGraphics.fill(-4, 2, -1, 3, 0xFFFFFFFF);
        guiGraphics.fill(2, 2, 5, 3, 0xFFFFFFFF);

        pose.popMatrix();
    }

    private static void renderCompassAndFooter(GuiGraphicsExtractor guiGraphics, Font font,
                                               int mapX, int mapY, int mapSize,
                                               double mapCenterX, double mapCenterY,
                                               int centerX, int centerY, int centerZ,
                                               double cosRot, double sinRot, boolean circular,
                                               ViewMode mode, boolean isMining) {
        double edgeDist = mapSize * 0.5 - 6.0;
        drawCompassLetter(guiGraphics, font, "N", 0.0, -1.0, cosRot, sinRot, mapCenterX, mapCenterY, edgeDist, circular, 0xFFFF5555);
        drawCompassLetter(guiGraphics, font, "S", 0.0, 1.0, cosRot, sinRot, mapCenterX, mapCenterY, edgeDist, circular, 0xFFDDDDDD);
        drawCompassLetter(guiGraphics, font, "E", 1.0, 0.0, cosRot, sinRot, mapCenterX, mapCenterY, edgeDist, circular, 0xFFDDDDDD);
        drawCompassLetter(guiGraphics, font, "W", -1.0, 0.0, cosRot, sinRot, mapCenterX, mapCenterY, edgeDist, circular, 0xFFDDDDDD);

        int half = mapSize / 2;
        int footerY = mapY + mapSize + BORDER + 1;
        guiGraphics.fill(mapX - BORDER, footerY, mapX + mapSize + BORDER, footerY + 20, 0xD809090B);
        guiGraphics.outline(mapX - BORDER, footerY, mapSize + BORDER * 2, 20, 0xBBFF7A29);

        String coords = centerX + ", " + centerY + ", " + centerZ;
        guiGraphics.centeredText(font, coords, mapX + half, footerY + 2, 0xFFFFFFFF);

        String modeLabel;
        int modeColor;
        switch (mode) {
            case LAYER -> {
                modeLabel = isMining ? ("Mining Y:" + centerY) : ("Layer Y:" + centerY);
                modeColor = 0xFFFF8833;
            }
            case CAVE -> {
                modeLabel = "Cave Y:" + centerY;
                modeColor = 0xFFFF5533;
            }
            default -> {
                modeLabel = "Surface";
                modeColor = 0xFFFFBB66;
            }
        }
        guiGraphics.centeredText(font, modeLabel, mapX + half, footerY + 11, modeColor);
    }

    private static void drawCompassLetter(GuiGraphicsExtractor guiGraphics, Font font, String letter,
                                          double dirX, double dirZ, double cosRot, double sinRot,
                                          double mapCenterX, double mapCenterY, double edgeDist,
                                          boolean circular, int color) {
        double rx = dirX * cosRot - dirZ * sinRot;
        double rz = dirX * sinRot + dirZ * cosRot;
        double scale;
        if (circular) {
            scale = edgeDist;
        } else {
            double maxAbs = Math.max(Math.abs(rx), Math.abs(rz));
            if (maxAbs < 1e-6) {
                return;
            }
            scale = edgeDist / maxAbs;
        }
        int lx = (int) Math.round(mapCenterX + rx * scale) - 2;
        int ly = (int) Math.round(mapCenterY + rz * scale) - 4;
        guiGraphics.text(font, letter, lx, ly, color, true);
    }
}
