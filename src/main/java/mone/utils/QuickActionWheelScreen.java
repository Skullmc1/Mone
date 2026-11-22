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
import mone.Mone;
import mone.api.IMone;
import mone.api.MoneAPI;
import mone.api.Settings;
import mone.api.utils.Helper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * True pie-slice Quick Action Radial Wheel Menu (default keybind: V).
 * Renders triangular cake slices radiating from a central hub in Mone's black & orange theme.
 */
public class QuickActionWheelScreen extends Screen implements Helper {

    public record RadialEntry(String label, String command) {}

    public static final String DEFAULT_RADIAL_ACTIONS =
            "Pause/Resume|@pause,Stop|@stop,Open Map|@mapfull,Minimap|@minimap,HUD|@hud,Settings|@gui";

    private static final Identifier WHEEL_TEXTURE_ID = Identifier.fromNamespaceAndPath("mone", "radial_wheel");
    private static final int WHEEL_TEX_SIZE = 224;
    private static final double OUTER_RADIUS = 104.0;
    private static final double INNER_RADIUS = 30.0;

    private static DynamicTexture wheelTexture;
    private static int renderedHover = Integer.MIN_VALUE;
    private static int renderedSliceCount = -1;

    private final List<RadialEntry> entries;
    private int hoveredIndex = -1;

    public QuickActionWheelScreen() {
        super(Component.literal("Mone Quick Actions"));
        this.entries = parseEntries(MoneAPI.getSettings().radialActions.value);
    }

    public static int resolveRadialKeyCode() {
        String keyName = MoneAPI.getSettings().radialMenuKey.value;
        if (keyName == null || keyName.isBlank()) {
            return InputConstants.KEY_V;
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
        return InputConstants.KEY_V;
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.level != null) {
            mc.gui.setScreen(new QuickActionWheelScreen());
        }
    }

    public static List<RadialEntry> parseEntries(String raw) {
        List<RadialEntry> list = new ArrayList<>();
        String src = (raw == null || raw.isBlank()) ? DEFAULT_RADIAL_ACTIONS : raw;
        for (String item : src.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int sep = trimmed.indexOf('|');
            if (sep > 0 && sep < trimmed.length() - 1) {
                list.add(new RadialEntry(trimmed.substring(0, sep).trim(), trimmed.substring(sep + 1).trim()));
            } else {
                list.add(new RadialEntry(trimmed, trimmed));
            }
        }
        if (list.isEmpty()) {
            return parseEntries(DEFAULT_RADIAL_ACTIONS);
        }
        return list;
    }

    public static String serializeEntries(List<RadialEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(entries.get(i).label()).append('|').append(entries.get(i).command());
        }
        return sb.toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x55000000);
    }

    private static void ensureWheelTexture(Minecraft mc, int sliceCount, int hovered) {
        if (wheelTexture == null) {
            wheelTexture = new DynamicTexture("mone_radial_wheel", WHEEL_TEX_SIZE, WHEEL_TEX_SIZE, false);
            mc.getTextureManager().register(WHEEL_TEXTURE_ID, wheelTexture);
            renderedHover = Integer.MIN_VALUE;
            renderedSliceCount = -1;
        }
        if (renderedHover == hovered && renderedSliceCount == sliceCount) {
            return;
        }

        NativeImage img = wheelTexture.getPixels();
        if (img == null) {
            return;
        }

        double center = (WHEEL_TEX_SIZE - 1) * 0.5;
        double sliceAngle = (Math.PI * 2.0) / Math.max(1, sliceCount);
        double halfSlice = sliceAngle * 0.5;

        for (int y = 0; y < WHEEL_TEX_SIZE; y++) {
            double dy = y - center;
            for (int x = 0; x < WHEEL_TEX_SIZE; x++) {
                double dx = x - center;
                double r = Math.sqrt(dx * dx + dy * dy);

                if (r > OUTER_RADIUS + 1.5) {
                    img.setPixel(x, y, 0x00000000);
                    continue;
                }

                // Inner center hub
                if (r < INNER_RADIUS - 1.5) {
                    img.setPixel(x, y, 0xEE09090B);
                    continue;
                }
                if (r < INNER_RADIUS + 0.5) {
                    img.setPixel(x, y, 0xFFFF7A29);
                    continue;
                }

                // Angle 0 at top (-Y), clockwise 0..2*PI
                double angle = Math.atan2(dx, -dy);
                if (angle < 0) {
                    angle += Math.PI * 2.0;
                }

                double shifted = (angle + halfSlice) % (Math.PI * 2.0);
                int idx = (int) Math.floor(shifted / sliceAngle) % sliceCount;
                double localAngle = shifted - idx * sliceAngle;
                double angleToEdge = Math.min(localAngle, sliceAngle - localAngle);
                double pixelDistToDivider = r * Math.sin(angleToEdge);

                boolean isHot = (idx == hovered);

                // Outer rim
                if (r >= OUTER_RADIUS - 1.8) {
                    img.setPixel(x, y, isHot ? 0xFFFFAA44 : 0xCCFF7A29);
                    continue;
                }

                // Crisp triangular slice divider line
                if (sliceCount > 1 && pixelDistToDivider < 1.35) {
                    img.setPixel(x, y, isHot ? 0xFFFF9B42 : 0xCC2A1A10);
                    continue;
                }

                if (isHot) {
                    // Subtle radial highlight gradient on the hovered orange pie slice
                    double t = (r - INNER_RADIUS) / (OUTER_RADIUS - INNER_RADIUS);
                    int red = (int) (205 + 45 * t);
                    int green = (int) (72 + 44 * t);
                    int blue = (int) (18 + 16 * t);
                    img.setPixel(x, y, (0xEC << 24) | (red << 16) | (green << 8) | blue);
                } else {
                    // Pure dark translucent black pie slice
                    img.setPixel(x, y, 0xDF0B0B0D);
                }
            }
        }

        wheelTexture.upload();
        renderedHover = hovered;
        renderedSliceCount = sliceCount;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int n = entries.size();

        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.hypot(dx, dy);
        hoveredIndex = -1;
        if (dist >= INNER_RADIUS && dist <= OUTER_RADIUS + 36.0 && n > 0) {
            double angle = Math.atan2(dx, -dy);
            if (angle < 0) {
                angle += Math.PI * 2.0;
            }
            double sliceAngle = (Math.PI * 2.0) / n;
            hoveredIndex = (int) Math.floor((angle + sliceAngle * 0.5) / sliceAngle) % n;
        }

        Minecraft mc = Minecraft.getInstance();
        ensureWheelTexture(mc, n, hoveredIndex);

        int halfTex = WHEEL_TEX_SIZE / 2;
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                WHEEL_TEXTURE_ID,
                cx - halfTex, cy - halfTex,
                0.0f, 0.0f,
                WHEEL_TEX_SIZE, WHEEL_TEX_SIZE,
                WHEEL_TEX_SIZE, WHEEL_TEX_SIZE,
                WHEEL_TEX_SIZE, WHEEL_TEX_SIZE
        );

        // Center hub label
        String hubLabel = (hoveredIndex >= 0) ? entries.get(hoveredIndex).label() : "Mone";
        if (hubLabel.length() > 9) {
            hubLabel = hubLabel.substring(0, 8) + "…";
        }
        guiGraphics.centeredText(this.font, hubLabel, cx, cy - 4, hoveredIndex >= 0 ? 0xFFFF8833 : 0xFFFFFFFF);

        // Draw each slice label inside its triangular wedge
        double labelR = (INNER_RADIUS + OUTER_RADIUS) * 0.52;
        for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2.0 * i) / n;
            int lx = cx + (int) Math.round(Math.sin(a) * labelR);
            int ly = cy - (int) Math.round(Math.cos(a) * labelR);
            boolean hot = (i == hoveredIndex);

            String lbl = entries.get(i).label();
            if (lbl.length() > 12) {
                lbl = lbl.substring(0, 11) + "…";
            }
            guiGraphics.centeredText(this.font, lbl, lx, ly - 4, hot ? 0xFFFFFFFF : 0xFFEAEAEA);
        }

        guiGraphics.centeredText(this.font, "Click or release key to run  |  Esc: Cancel",
                cx, cy + (int) OUTER_RADIUS + 14, 0xFFBBBBBB);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (hoveredIndex >= 0 && hoveredIndex < entries.size()) {
                executeEntry(entries.get(hoveredIndex));
            } else {
                this.onClose();
            }
            return true;
        }
        if (event.button() == 1) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == resolveRadialKeyCode()) {
            if (hoveredIndex >= 0 && hoveredIndex < entries.size()) {
                executeEntry(entries.get(hoveredIndex));
            } else {
                this.onClose();
            }
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void executeEntry(RadialEntry entry) {
        this.onClose();
        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        Settings settings = MoneAPI.getSettings();
        String cmd = entry.command();

        switch (cmd.toLowerCase(Locale.ROOT)) {
            case "@pause" -> {
                if (mone != null) {
                    if (mone.getPathingBehavior().isPathing()) {
                        mone.getCommandManager().execute("pause");
                    } else {
                        mone.getCommandManager().execute("resume");
                    }
                }
            }
            case "@stop" -> {
                if (mone != null) {
                    mone.getPathingBehavior().cancelEverything();
                    if (mone instanceof Mone m) {
                        m.getQueueBehavior().clear();
                    }
                    Helper.HELPER.logDirect("Stopped all Mone tasks.");
                }
            }
            case "@mapfull" -> MinimapRenderer.openFullscreenMap();
            case "@minimap" -> {
                settings.minimap.value = !settings.minimap.value;
                MinimapRenderer.invalidate();
                Helper.HELPER.logDirect("Minimap " + (settings.minimap.value ? "enabled" : "disabled"));
            }
            case "@hud" -> {
                settings.hud.value = !settings.hud.value;
                Helper.HELPER.logDirect("HUD " + (settings.hud.value ? "enabled" : "disabled"));
            }
            case "@gui" -> SettingsGuiScreen.open();
            case "@qgui" -> QueueBuilderScreen.open();
            default -> {
                if (mone != null && !cmd.isBlank()) {
                    mone.getCommandManager().execute(cmd);
                }
            }
        }
    }
}
