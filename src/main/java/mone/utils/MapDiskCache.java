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

import mone.api.cache.IWaypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persistent disk storage for explored surface chunk tiles (16x16 regions)
 * and custom waypoint colors, scoped per world and dimension.
 */
public final class MapDiskCache {

    private static final int REGION_SHIFT = 4; // 16x16 chunks per region file
    private static final int REGION_MASK = (1 << REGION_SHIFT) - 1;
    private static final int FILE_MAGIC = 0x4D4D4150; // "MMAP"
    private static final int FILE_VERSION = 1;

    private static final class DiskRegion {
        final int rx;
        final int rz;
        final Map<Integer, StoredChunk> chunks = new ConcurrentHashMap<>();
        volatile boolean dirty = false;

        DiskRegion(int rx, int rz) {
            this.rx = rx;
            this.rz = rz;
        }
    }

    static final class StoredChunk {
        final int[] pixelsABGR;
        final int[] heights;

        StoredChunk(int[] pixelsABGR, int[] heights) {
            this.pixelsABGR = pixelsABGR;
            this.heights = heights;
        }
    }

    private static volatile Path currentDir = null;
    private static final ConcurrentHashMap<Long, DiskRegion> LOADED_REGIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> WAYPOINT_COLORS = new ConcurrentHashMap<>();
    private static volatile boolean waypointColorsDirty = false;
    private static volatile long lastFlushMs = 0L;

    private MapDiskCache() {}

    public static synchronized void switchWorld(Minecraft mc, ClientLevel level) {
        flushAll();
        LOADED_REGIONS.clear();
        WAYPOINT_COLORS.clear();
        waypointColorsDirty = false;

        if (mc == null || level == null) {
            currentDir = null;
            return;
        }

        String worldId = resolveWorldId(mc);
        String dimId = sanitize(level.dimension().identifier().getPath());
        Path dir = mc.gameDirectory.toPath()
                .resolve("Mone")
                .resolve("map_cache")
                .resolve(worldId)
                .resolve(dimId);
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        currentDir = dir;
        loadWaypointColors(dir);
    }

    private static String resolveWorldId(Minecraft mc) {
        IntegratedServer sp = mc.getSingleplayerServer();
        if (sp != null) {
            try {
                Path worldPath = sp.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                Path name = worldPath.getFileName();
                if (name != null) {
                    return "sp_" + sanitize(name.toString());
                }
            } catch (Exception ignored) {
            }
            return "sp_world";
        }
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "mp_" + sanitize(server.ip);
        }
        return "default_world";
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static long regionKey(int rx, int rz) {
        return ((long) rx & 0xFFFFFFFFL) | (((long) rz & 0xFFFFFFFFL) << 32);
    }

    private static int localIndex(int cx, int cz) {
        return ((cz & REGION_MASK) << REGION_SHIFT) | (cx & REGION_MASK);
    }

    private static DiskRegion getOrLoadRegion(int rx, int rz) {
        long rk = regionKey(rx, rz);
        DiskRegion existing = LOADED_REGIONS.get(rk);
        if (existing != null) {
            return existing;
        }
        DiskRegion region = new DiskRegion(rx, rz);
        Path dir = currentDir;
        if (dir != null) {
            Path file = dir.resolve("r." + rx + "." + rz + ".mreg");
            if (Files.exists(file)) {
                readRegionFile(file, region);
            }
        }
        DiskRegion prev = LOADED_REGIONS.putIfAbsent(rk, region);
        return prev != null ? prev : region;
    }

    static StoredChunk loadChunk(int cx, int cz) {
        if (currentDir == null) {
            return null;
        }
        DiskRegion region = getOrLoadRegion(cx >> REGION_SHIFT, cz >> REGION_SHIFT);
        return region.chunks.get(localIndex(cx, cz));
    }

    static void saveChunk(int cx, int cz, int[] pixelsABGR, int[] heights) {
        if (currentDir == null) {
            return;
        }
        DiskRegion region = getOrLoadRegion(cx >> REGION_SHIFT, cz >> REGION_SHIFT);
        region.chunks.put(localIndex(cx, cz), new StoredChunk(pixelsABGR.clone(), heights.clone()));
        region.dirty = true;
    }

    static void maybeFlushPeriodic() {
        long now = System.currentTimeMillis();
        if (now - lastFlushMs >= 5000L) {
            lastFlushMs = now;
            flushAll();
        }
    }

    public static void flushAll() {
        Path dir = currentDir;
        if (dir == null) {
            return;
        }
        for (DiskRegion region : LOADED_REGIONS.values()) {
            if (region.dirty) {
                region.dirty = false;
                writeRegionFile(dir.resolve("r." + region.rx + "." + region.rz + ".mreg"), region);
            }
        }
        if (waypointColorsDirty) {
            waypointColorsDirty = false;
            saveWaypointColors(dir);
        }
    }

    private static void readRegionFile(Path file, DiskRegion region) {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file))))) {
            int magic = in.readInt();
            int version = in.readInt();
            if (magic != FILE_MAGIC || version != FILE_VERSION) {
                return;
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int localIdx = in.readInt();
                int[] pixels = new int[256];
                int[] heights = new int[256];
                for (int p = 0; p < 256; p++) {
                    pixels[p] = in.readInt();
                }
                for (int p = 0; p < 256; p++) {
                    heights[p] = in.readShort();
                }
                region.chunks.put(localIdx, new StoredChunk(pixels, heights));
            }
        } catch (IOException ignored) {
        }
    }

    private static void writeRegionFile(Path file, DiskRegion region) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp))))) {
                out.writeInt(FILE_MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(region.chunks.size());
                for (Map.Entry<Integer, StoredChunk> entry : region.chunks.entrySet()) {
                    out.writeInt(entry.getKey());
                    StoredChunk sc = entry.getValue();
                    for (int p = 0; p < 256; p++) {
                        out.writeInt(sc.pixelsABGR[p]);
                    }
                    for (int p = 0; p < 256; p++) {
                        int h = sc.heights[p];
                        short sh = (h == Integer.MIN_VALUE) ? Short.MIN_VALUE : (short) Math.max(-32767, Math.min(32767, h));
                        out.writeShort(sh);
                    }
                }
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    // --- Custom Waypoint Colors ---

    private static String waypointKey(IWaypoint wp) {
        return wp.getTag().getName() + ":" + (wp.getName() == null ? "" : wp.getName()) + ":" + wp.getCreationTimestamp();
    }

    public static int getWaypointColor(IWaypoint wp) {
        if (wp == null) {
            return 0xFFCC66FF;
        }
        Integer custom = WAYPOINT_COLORS.get(waypointKey(wp));
        if (custom != null) {
            return 0xFF000000 | (custom & 0xFFFFFF);
        }
        return MinimapRenderer.getDefaultWaypointColor(wp.getTag());
    }

    public static void setWaypointColor(IWaypoint wp, int argb) {
        if (wp == null) {
            return;
        }
        WAYPOINT_COLORS.put(waypointKey(wp), argb & 0xFFFFFF);
        waypointColorsDirty = true;
        Path dir = currentDir;
        if (dir != null) {
            saveWaypointColors(dir);
            waypointColorsDirty = false;
        }
    }

    public static void removeWaypointColor(IWaypoint wp) {
        if (wp == null) {
            return;
        }
        if (WAYPOINT_COLORS.remove(waypointKey(wp)) != null) {
            waypointColorsDirty = true;
        }
    }

    private static void loadWaypointColors(Path dir) {
        Path file = dir.resolve("waypoint_colors.properties");
        if (!Files.exists(file)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            for (String key : props.stringPropertyNames()) {
                try {
                    int rgb = Integer.parseInt(props.getProperty(key), 16);
                    WAYPOINT_COLORS.put(key, rgb & 0xFFFFFF);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void saveWaypointColors(Path dir) {
        Path file = dir.resolve("waypoint_colors.properties");
        Properties props = new Properties();
        for (Map.Entry<String, Integer> entry : WAYPOINT_COLORS.entrySet()) {
            props.setProperty(entry.getKey(), String.format("%06X", entry.getValue() & 0xFFFFFF));
        }
        try {
            Files.createDirectories(dir);
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Mone custom waypoint colors");
            }
        } catch (IOException ignored) {
        }
    }
}
