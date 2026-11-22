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
import mone.api.MoneAPI;
import mone.api.Settings;
import mone.api.utils.Helper;
import mone.api.utils.SettingsUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Searchable, categorized in-game Settings GUI for Mone (#gui or K).
 * Includes a dedicated Radial Wheel customizer tab and quick switch to the Queue Builder GUI.
 * Styled in Mone's orange/red theme.
 */
public class SettingsGuiScreen extends Screen implements Helper {

    private static final String[] CATEGORIES = {"Quick", "Minimap", "Safety", "Pathing", "Radial", "All"};
    private static final int RADIAL_TAB_INDEX = 4;

    private static final String[][] RADIAL_PRESETS = {
            {"Highway", "highway"},
            {"Lumber", "lumber"},
            {"Auto-Fish", "fish"},
            {"Queue UI", "@qgui"},
            {"Drop-Off", "dropoff now"}
    };

    private static final Set<String> QUICK_SETTINGS = Set.of(
            "minimap", "minimapcircular", "minimaprotate", "minimapmode", "minimapsize", "minimapradius",
            "minimapfullscreenkey", "minimapchunkgrid", "minimapspawnoverlay",
            "hud",
            "autoeat", "autoeathungerthreshold", "autoeathealththreshold",
            "survivalsafety", "evadeenemymobs", "evademobdistance", "evadesafedistance",
            "lowhealthretreatthreshold", "swaplowdurabilitytool",
            "autodropoff", "autodropoffwaypoint", "autodropoffminfreeslots",
            "queuerecoverondeath", "settingsguikey", "radialmenukey"
    );

    private static final int PANEL_MARGIN_X = 28;
    private static final int PANEL_MARGIN_Y = 20;
    private static final int TAB_W = 86;
    private static final int HEADER_H = 30;
    private static final int FOOTER_H = 26;
    private static final int ROW_H = 22;

    private int activeTab = 0;
    private int scrollOffset = 0;
    private boolean searchFocused = false;
    private final StringBuilder searchQuery = new StringBuilder();

    private Settings.Setting<?> editingSetting = null;
    private final StringBuilder editBuffer = new StringBuilder();

    // Radial customizer state
    private final StringBuilder radialLabelInput = new StringBuilder();
    private final StringBuilder radialCmdInput = new StringBuilder();
    private int radialFocus = 0; // 0 = none, 1 = label, 2 = cmd

    private List<Settings.Setting<?>> visibleSettings = new ArrayList<>();

    public SettingsGuiScreen() {
        super(Component.literal("Mone Settings"));
        rebuildVisibleSettings();
    }

    public static int resolveSettingsKeyCode() {
        String keyName = MoneAPI.getSettings().settingsGuiKey.value;
        if (keyName == null || keyName.isBlank()) {
            return InputConstants.KEY_K;
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
        return InputConstants.KEY_K;
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.level != null) {
            mc.gui.setScreen(new SettingsGuiScreen());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xB8000000);
    }

    private void rebuildVisibleSettings() {
        Settings settings = MoneAPI.getSettings();
        String query = searchQuery.toString().trim().toLowerCase(Locale.ROOT);
        String cat = CATEGORIES[activeTab];

        List<Settings.Setting<?>> list = new ArrayList<>();
        for (Settings.Setting<?> s : settings.allSettings) {
            if (s.isJavaOnly() || !isEditableType(s)) {
                continue;
            }
            String lower = s.getName().toLowerCase(Locale.ROOT);
            if (!query.isEmpty()) {
                if (!lower.contains(query)) {
                    continue;
                }
            } else {
                if (!matchesCategory(lower, cat)) {
                    continue;
                }
            }
            list.add(s);
        }
        list.sort(Comparator.comparing(s -> s.getName().toLowerCase(Locale.ROOT)));
        this.visibleSettings = list;
        this.scrollOffset = 0;
    }

    private static boolean isEditableType(Settings.Setting<?> s) {
        Class<?> cls = s.getValueClass();
        return cls == Boolean.class || cls == Integer.class || cls == Double.class
                || cls == Float.class || cls == String.class;
    }

    private static boolean matchesCategory(String lower, String cat) {
        return switch (cat) {
            case "Quick" -> QUICK_SETTINGS.contains(lower);
            case "Minimap" -> lower.startsWith("minimap") || lower.equals("hud");
            case "Safety" -> lower.startsWith("autoeat") || lower.startsWith("survival")
                    || lower.startsWith("evade") || lower.startsWith("lowhealth")
                    || lower.startsWith("swaplow") || lower.startsWith("autodropoff")
                    || lower.contains("avoidance") || lower.equals("allowwaterbucketfall");
            case "Pathing" -> lower.startsWith("allow") || lower.contains("mine")
                    || lower.contains("path") || lower.contains("elytra") || lower.contains("parkour");
            default -> true;
        };
    }

    private boolean isRadialTab() {
        return activeTab == RADIAL_TAB_INDEX && searchQuery.isEmpty();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        int px = PANEL_MARGIN_X;
        int py = PANEL_MARGIN_Y;
        int pw = Math.max(340, this.width - PANEL_MARGIN_X * 2);
        int ph = Math.max(190, this.height - PANEL_MARGIN_Y * 2);

        // Main panel frame (Pure black base + orange accents)
        guiGraphics.fill(px, py, px + pw, py + ph, 0xF50B0B0D);
        guiGraphics.outline(px, py, pw, ph, 0xFFFF7A29);

        // Header bar
        guiGraphics.fill(px, py, px + pw, py + HEADER_H, 0xFF111114);
        guiGraphics.fill(px, py + HEADER_H - 1, px + pw, py + HEADER_H, 0xFFCC4A14);
        guiGraphics.text(this.font, "Mone Settings", px + 10, py + 11, 0xFFFF7A29, true);

        // Queue Builder switch button in header
        int qbW = 84;
        int sbW = Math.min(160, pw - 220);
        int sbX = px + pw - sbW - 10;
        int qbX = sbX - qbW - 8;
        boolean hoverQb = mouseX >= qbX && mouseX < qbX + qbW && mouseY >= py + 6 && mouseY < py + 24;
        guiGraphics.fill(qbX, py + 6, qbX + qbW, py + 24, hoverQb ? 0xFFCC4A14 : 0xFF18181E);
        guiGraphics.outline(qbX, py + 6, qbW, 18, 0xFFFF7A29);
        guiGraphics.centeredText(this.font, "Queue Builder", qbX + qbW / 2, py + 11, 0xFFFFFFFF);

        // Search box in header
        int sbY = py + 7;
        int sbH = 16;
        guiGraphics.fill(sbX, sbY, sbX + sbW, sbY + sbH, 0xFF060608);
        guiGraphics.outline(sbX, sbY, sbW, sbH, searchFocused ? 0xFFFF7A29 : 0xFF33333C);
        boolean cursor = (((System.currentTimeMillis() / 500L) & 1L) == 0L);
        String q = searchQuery.toString();
        if (q.isEmpty()) {
            guiGraphics.text(this.font, (searchFocused && cursor) ? "|" : "Search settings...", sbX + 5, sbY + 4,
                    (searchFocused && cursor) ? 0xFFFFFFFF : 0xFF666670, false);
        } else {
            guiGraphics.text(this.font, q + ((searchFocused && cursor) ? "|" : ""), sbX + 5, sbY + 4, 0xFFFFFFFF, true);
        }

        // Left category tabs
        int bodyTop = py + HEADER_H;
        int bodyBottom = py + ph - FOOTER_H;
        guiGraphics.fill(px, bodyTop, px + TAB_W, bodyBottom, 0xFF0E0E11);
        guiGraphics.fill(px + TAB_W - 1, bodyTop, px + TAB_W, bodyBottom, 0xFF2A2A32);

        for (int i = 0; i < CATEGORIES.length; i++) {
            int ty = bodyTop + 6 + i * 22;
            boolean active = (i == activeTab) && searchQuery.isEmpty();
            boolean hover = mouseX >= px + 4 && mouseX < px + TAB_W - 4 && mouseY >= ty && mouseY < ty + 18;
            if (active) {
                guiGraphics.fill(px + 4, ty, px + TAB_W - 4, ty + 18, 0xFFCC4A14);
                guiGraphics.outline(px + 4, ty, TAB_W - 8, 18, 0xFFFF9B42);
            } else if (hover) {
                guiGraphics.fill(px + 4, ty, px + TAB_W - 4, ty + 18, 0xFF1F1F26);
            }
            guiGraphics.text(this.font, CATEGORIES[i], px + 10, ty + 5, active ? 0xFFFFFFFF : 0xFFCCCCCC, true);
        }

        int listX = px + TAB_W + 6;
        int listW = pw - TAB_W - 12;
        int listTop = bodyTop + 4;

        if (isRadialTab()) {
            renderRadialCustomizer(guiGraphics, listX, listTop, listW, bodyBottom - listTop - 4, mouseX, mouseY, cursor);
        } else {
            renderSettingsList(guiGraphics, listX, listTop, listW, bodyBottom - listTop - 4, mouseX, mouseY, cursor);
        }

        // Footer bar
        int fy = py + ph - FOOTER_H;
        guiGraphics.fill(px, fy, px + pw, py + ph, 0xFF111114);
        guiGraphics.fill(px, fy, px + pw, fy + 1, 0xFF2A2A32);

        int saveX = px + pw - 150;
        int closeX = px + pw - 66;
        boolean hoverSave = mouseX >= saveX && mouseX < saveX + 78 && mouseY >= fy + 4 && mouseY < fy + 21;
        boolean hoverClose = mouseX >= closeX && mouseX < closeX + 56 && mouseY >= fy + 4 && mouseY < fy + 21;

        guiGraphics.fill(saveX, fy + 4, saveX + 78, fy + 21, hoverSave ? 0xFFFF7A29 : 0xFFCC4A14);
        guiGraphics.outline(saveX, fy + 4, 78, 17, 0xFFFF9B42);
        guiGraphics.centeredText(this.font, "Save Config", saveX + 39, fy + 9, 0xFFFFFFFF);

        guiGraphics.fill(closeX, fy + 4, closeX + 56, fy + 21, hoverClose ? 0xFF2A2A32 : 0xFF18181E);
        guiGraphics.outline(closeX, fy + 4, 56, 17, 0xFF44444E);
        guiGraphics.centeredText(this.font, "Close", closeX + 28, fy + 9, 0xFFDDDDDD);

        guiGraphics.text(this.font, "Scroll to browse  |  Click values to toggle/edit  |  R = Reset",
                px + 10, fy + 9, 0xFF888894, false);
    }

    private void renderRadialCustomizer(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h,
                                        int mouseX, int mouseY, boolean blink) {
        guiGraphics.text(this.font, "Quick Action Radial Wheel (Key: " + MoneAPI.getSettings().radialMenuKey.value + ")",
                x + 4, y + 4, 0xFFFF7A29, true);

        // One-click preset chips (Highway, Lumber, Auto-Fish, Queue UI, Drop-Off)
        int chipY = y + 18;
        int chipX = x + 4;
        for (String[] preset : RADIAL_PRESETS) {
            String txt = "+ " + preset[0];
            int cw = this.font.width(txt) + 10;
            if (chipX + cw > x + w - 4) {
                break;
            }
            boolean hov = mouseX >= chipX && mouseX < chipX + cw && mouseY >= chipY && mouseY < chipY + 15;
            guiGraphics.fill(chipX, chipY, chipX + cw, chipY + 15, hov ? 0xFFCC4A14 : 0xFF18181E);
            guiGraphics.outline(chipX, chipY, cw, 15, 0xFFFF7A29);
            guiGraphics.centeredText(this.font, txt, chipX + cw / 2, chipY + 4, 0xFFFFFFFF);
            chipX += cw + 4;
        }

        // Custom add row: Label | Command | + Add | Reset
        int addY = y + 38;
        int lblW = (w - 110) / 2;
        int cmdW = (w - 110) / 2;
        int lblX = x + 4;
        int cmdX = lblX + lblW + 4;
        int addBtnX = cmdX + cmdW + 4;
        int rstBtnX = addBtnX + 42;

        guiGraphics.fill(lblX, addY, lblX + lblW, addY + 16, 0xFF060608);
        guiGraphics.outline(lblX, addY, lblW, 16, radialFocus == 1 ? 0xFFFF7A29 : 0xFF33333C);
        String lTxt = radialLabelInput.toString();
        guiGraphics.text(this.font, lTxt.isEmpty() ? ((radialFocus == 1 && blink) ? "|" : "Label...") : (lTxt + ((radialFocus == 1 && blink) ? "|" : "")),
                lblX + 4, addY + 4, lTxt.isEmpty() && !(radialFocus == 1 && blink) ? 0xFF666670 : 0xFFFFFFFF, !lTxt.isEmpty());

        guiGraphics.fill(cmdX, addY, cmdX + cmdW, addY + 16, 0xFF060608);
        guiGraphics.outline(cmdX, addY, cmdW, 16, radialFocus == 2 ? 0xFFFF7A29 : 0xFF33333C);
        String cTxt = radialCmdInput.toString();
        guiGraphics.text(this.font, cTxt.isEmpty() ? ((radialFocus == 2 && blink) ? "|" : "Command...") : (cTxt + ((radialFocus == 2 && blink) ? "|" : "")),
                cmdX + 4, addY + 4, cTxt.isEmpty() && !(radialFocus == 2 && blink) ? 0xFF666670 : 0xFFFFFFFF, !cTxt.isEmpty());

        guiGraphics.fill(addBtnX, addY, addBtnX + 38, addY + 16, 0xFFCC4A14);
        guiGraphics.outline(addBtnX, addY, 38, 16, 0xFFFF9B42);
        guiGraphics.centeredText(this.font, "+ Add", addBtnX + 19, addY + 4, 0xFFFFFFFF);

        guiGraphics.fill(rstBtnX, addY, rstBtnX + 46, addY + 16, 0xFF18181E);
        guiGraphics.outline(rstBtnX, addY, 46, 16, 0xFF44444E);
        guiGraphics.centeredText(this.font, "Reset", rstBtnX + 23, addY + 4, 0xFFDDDDDD);

        // Current radial entries list
        List<QuickActionWheelScreen.RadialEntry> entries = QuickActionWheelScreen.parseEntries(MoneAPI.getSettings().radialActions.value);
        int listTop = addY + 22;
        int visRows = Math.max(1, (y + h - listTop) / 20);
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, entries.size() - visRows));

        for (int i = 0; i < visRows && (i + scrollOffset) < entries.size(); i++) {
            int idx = i + scrollOffset;
            QuickActionWheelScreen.RadialEntry e = entries.get(idx);
            int ry = listTop + i * 20;
            guiGraphics.fill(x + 4, ry, x + w - 4, ry + 18, 0xFF151519);
            guiGraphics.outline(x + 4, ry, w - 8, 18, 0xFF2A2A32);
            guiGraphics.text(this.font, (idx + 1) + ". " + e.label(), x + 10, ry + 5, 0xFFFFFFFF, true);
            guiGraphics.text(this.font, "-> " + e.command(), x + w / 2 - 10, ry + 5, 0xFFFFAA66, false);

            int delX = x + w - 20;
            guiGraphics.fill(delX, ry + 2, delX + 14, ry + 16, 0xFF551A1A);
            guiGraphics.centeredText(this.font, "X", delX + 7, ry + 5, 0xFFFFAAAA);
        }
    }

    private void renderSettingsList(GuiGraphicsExtractor guiGraphics, int listX, int listTop, int listW, int listH,
                                    int mouseX, int mouseY, boolean cursor) {
        int visibleRows = Math.max(1, listH / ROW_H);
        int maxScroll = Math.max(0, visibleSettings.size() - visibleRows);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        for (int i = 0; i < visibleRows && (i + scrollOffset) < visibleSettings.size(); i++) {
            Settings.Setting<?> s = visibleSettings.get(i + scrollOffset);
            int ry = listTop + i * ROW_H;
            boolean rowHover = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + ROW_H - 1;
            guiGraphics.fill(listX, ry, listX + listW, ry + ROW_H - 2, rowHover ? 0xFF1F1F26 : 0xFF141418);

            boolean modified = !Objects.equals(s.value, s.defaultValue);
            guiGraphics.text(this.font, s.getName(), listX + 6, ry + 6, modified ? 0xFFFF9B42 : 0xFFEEEEEE, true);

            int ctrlRight = listX + listW - 6;
            if (modified) {
                int rX = ctrlRight - 16;
                boolean hoverR = mouseX >= rX && mouseX < rX + 16 && mouseY >= ry + 3 && mouseY < ry + 17;
                guiGraphics.fill(rX, ry + 3, rX + 16, ry + 17, hoverR ? 0xFF7A2222 : 0xFF2A1818);
                guiGraphics.centeredText(this.font, "R", rX + 8, ry + 6, 0xFFFFAAAA);
                ctrlRight -= 20;
            }

            Class<?> cls = s.getValueClass();
            if (cls == Boolean.class) {
                boolean val = Boolean.TRUE.equals(s.value);
                int pillW = 38;
                int pillX = ctrlRight - pillW;
                guiGraphics.fill(pillX, ry + 3, pillX + pillW, ry + 17, val ? 0xFFCC4A14 : 0xFF1C1C22);
                guiGraphics.outline(pillX, ry + 3, pillW, 14, val ? 0xFFFF9B42 : 0xFF3A3A44);
                guiGraphics.centeredText(this.font, val ? "ON" : "OFF", pillX + pillW / 2, ry + 6, 0xFFFFFFFF);
            } else if (cls == Integer.class || cls == Double.class || cls == Float.class) {
                int plusX = ctrlRight - 14;
                int valW = 54;
                int valX = plusX - valW - 2;
                int minusX = valX - 16;

                guiGraphics.fill(minusX, ry + 3, minusX + 14, ry + 17, 0xFF1C1C22);
                guiGraphics.outline(minusX, ry + 3, 14, 14, 0xFF3A3A44);
                guiGraphics.centeredText(this.font, "-", minusX + 7, ry + 6, 0xFFFFFFFF);

                boolean isEditing = (editingSetting == s);
                guiGraphics.fill(valX, ry + 3, valX + valW, ry + 17, 0xFF060608);
                guiGraphics.outline(valX, ry + 3, valW, 14, isEditing ? 0xFFFF7A29 : 0xFF33333C);
                String valStr = isEditing ? (editBuffer.toString() + (cursor ? "|" : "")) : formatNumber(s.value);
                guiGraphics.centeredText(this.font, valStr, valX + valW / 2, ry + 6, 0xFFFFFFFF);

                guiGraphics.fill(plusX, ry + 3, plusX + 14, ry + 17, 0xFF1C1C22);
                guiGraphics.outline(plusX, ry + 3, 14, 14, 0xFF3A3A44);
                guiGraphics.centeredText(this.font, "+", plusX + 7, ry + 6, 0xFFFFFFFF);
            } else if (cls == String.class) {
                int valW = 86;
                int valX = ctrlRight - valW;
                boolean isEditing = (editingSetting == s);
                guiGraphics.fill(valX, ry + 3, valX + valW, ry + 17, 0xFF060608);
                guiGraphics.outline(valX, ry + 3, valW, 14, isEditing ? 0xFFFF7A29 : 0xFF33333C);
                String valStr = isEditing ? (editBuffer.toString() + "|") : String.valueOf(s.value);
                if (valStr.length() > 13) {
                    valStr = valStr.substring(0, 12) + "…";
                }
                guiGraphics.centeredText(this.font, valStr, valX + valW / 2, ry + 6, 0xFFFFFFFF);
            }
        }
    }

    private static String formatNumber(Object v) {
        if (v instanceof Double d) {
            return String.format(Locale.ROOT, "%.1f", d);
        }
        if (v instanceof Float f) {
            return String.format(Locale.ROOT, "%.1f", f);
        }
        return String.valueOf(v);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        double mx = event.x();
        double my = event.y();

        int px = PANEL_MARGIN_X;
        int py = PANEL_MARGIN_Y;
        int pw = Math.max(340, this.width - PANEL_MARGIN_X * 2);
        int ph = Math.max(190, this.height - PANEL_MARGIN_Y * 2);

        commitInlineEdit();

        // Queue Builder button in header
        int qbW = 84;
        int sbW = Math.min(160, pw - 220);
        int sbX = px + pw - sbW - 10;
        int qbX = sbX - qbW - 8;
        if (mx >= qbX && mx < qbX + qbW && my >= py + 6 && my < py + 24) {
            QueueBuilderScreen.open();
            return true;
        }

        // Search box click
        int sbY = py + 7;
        if (mx >= sbX && mx < sbX + sbW && my >= sbY && my < sbY + 16) {
            searchFocused = true;
            radialFocus = 0;
            return true;
        }
        searchFocused = false;

        // Footer buttons
        int fy = py + ph - FOOTER_H;
        int saveX = px + pw - 150;
        int closeX = px + pw - 66;
        if (my >= fy + 4 && my < fy + 21) {
            if (mx >= saveX && mx < saveX + 78) {
                SettingsUtil.save(MoneAPI.getSettings());
                Helper.HELPER.logDirect("Mone settings saved.");
                return true;
            }
            if (mx >= closeX && mx < closeX + 56) {
                this.onClose();
                return true;
            }
        }

        // Category tabs
        int bodyTop = py + HEADER_H;
        int bodyBottom = py + ph - FOOTER_H;
        if (mx >= px + 4 && mx < px + TAB_W - 4 && my >= bodyTop && my < bodyBottom) {
            for (int i = 0; i < CATEGORIES.length; i++) {
                int ty = bodyTop + 6 + i * 22;
                if (my >= ty && my < ty + 18) {
                    activeTab = i;
                    radialFocus = 0;
                    searchQuery.setLength(0);
                    rebuildVisibleSettings();
                    return true;
                }
            }
        }

        int listX = px + TAB_W + 6;
        int listW = pw - TAB_W - 12;
        int listTop = bodyTop + 4;

        if (isRadialTab() && mx >= listX && mx < listX + listW && my >= listTop && my < bodyBottom) {
            return handleRadialClick(listX, listTop, listW, mx, my);
        }

        radialFocus = 0;

        // Setting rows
        if (mx >= listX && mx < listX + listW && my >= listTop && my < bodyBottom) {
            int row = (int) ((my - listTop) / ROW_H);
            int idx = row + scrollOffset;
            if (idx >= 0 && idx < visibleSettings.size()) {
                Settings.Setting<?> s = visibleSettings.get(idx);
                int ctrlRight = listX + listW - 6;
                boolean modified = !Objects.equals(s.value, s.defaultValue);
                if (modified) {
                    int rX = ctrlRight - 16;
                    if (mx >= rX && mx < rX + 16) {
                        s.reset();
                        onSettingChanged(s);
                        return true;
                    }
                    ctrlRight -= 20;
                }

                Class<?> cls = s.getValueClass();
                if (cls == Boolean.class) {
                    Settings.Setting<Boolean> bs = (Settings.Setting<Boolean>) s;
                    bs.value = !Boolean.TRUE.equals(bs.value);
                    onSettingChanged(s);
                    return true;
                } else if (cls == Integer.class || cls == Double.class || cls == Float.class) {
                    int plusX = ctrlRight - 14;
                    int valW = 54;
                    int valX = plusX - valW - 2;
                    int minusX = valX - 16;
                    if (mx >= minusX && mx < minusX + 14) {
                        stepNumber(s, -1);
                        onSettingChanged(s);
                        return true;
                    }
                    if (mx >= plusX && mx < plusX + 14) {
                        stepNumber(s, 1);
                        onSettingChanged(s);
                        return true;
                    }
                    if (mx >= valX && mx < valX + valW) {
                        editingSetting = s;
                        editBuffer.setLength(0);
                        editBuffer.append(s.value);
                        return true;
                    }
                } else if (cls == String.class) {
                    int valW = 86;
                    int valX = ctrlRight - valW;
                    if (mx >= valX && mx < valX + valW) {
                        editingSetting = s;
                        editBuffer.setLength(0);
                        if (s.value != null) {
                            editBuffer.append(s.value);
                        }
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleRadialClick(int x, int y, int w, double mx, double my) {
        // Preset chips
        int chipY = y + 18;
        int chipX = x + 4;
        if (my >= chipY && my < chipY + 15) {
            for (String[] preset : RADIAL_PRESETS) {
                String txt = "+ " + preset[0];
                int cw = this.font.width(txt) + 10;
                if (mx >= chipX && mx < chipX + cw) {
                    addRadialAction(preset[0], preset[1]);
                    return true;
                }
                chipX += cw + 4;
            }
        }

        // Add row inputs/buttons
        int addY = y + 38;
        int lblW = (w - 110) / 2;
        int cmdW = (w - 110) / 2;
        int lblX = x + 4;
        int cmdX = lblX + lblW + 4;
        int addBtnX = cmdX + cmdW + 4;
        int rstBtnX = addBtnX + 42;

        if (my >= addY && my < addY + 16) {
            if (mx >= lblX && mx < lblX + lblW) {
                radialFocus = 1;
                return true;
            }
            if (mx >= cmdX && mx < cmdX + cmdW) {
                radialFocus = 2;
                return true;
            }
            if (mx >= addBtnX && mx < addBtnX + 38) {
                String lbl = radialLabelInput.toString().trim();
                String cmd = radialCmdInput.toString().trim();
                if (!lbl.isEmpty() && !cmd.isEmpty()) {
                    addRadialAction(lbl, cmd);
                    radialLabelInput.setLength(0);
                    radialCmdInput.setLength(0);
                }
                radialFocus = 0;
                return true;
            }
            if (mx >= rstBtnX && mx < rstBtnX + 46) {
                MoneAPI.getSettings().radialActions.value = QuickActionWheelScreen.DEFAULT_RADIAL_ACTIONS;
                radialFocus = 0;
                return true;
            }
        }
        radialFocus = 0;

        // Delete button on existing radial entries
        int listTop = addY + 22;
        if (my >= listTop) {
            int row = (int) ((my - listTop) / 20);
            int idx = row + scrollOffset;
            List<QuickActionWheelScreen.RadialEntry> entries = QuickActionWheelScreen.parseEntries(MoneAPI.getSettings().radialActions.value);
            if (idx >= 0 && idx < entries.size() && mx >= x + w - 20) {
                if (entries.size() > 1) {
                    entries.remove(idx);
                    MoneAPI.getSettings().radialActions.value = QuickActionWheelScreen.serializeEntries(entries);
                }
                return true;
            }
        }
        return true;
    }

    private static void addRadialAction(String label, String command) {
        List<QuickActionWheelScreen.RadialEntry> entries = QuickActionWheelScreen.parseEntries(MoneAPI.getSettings().radialActions.value);
        entries.add(new QuickActionWheelScreen.RadialEntry(label, command));
        MoneAPI.getSettings().radialActions.value = QuickActionWheelScreen.serializeEntries(entries);
    }

    @SuppressWarnings("unchecked")
    private static void stepNumber(Settings.Setting<?> s, int dir) {
        Class<?> cls = s.getValueClass();
        if (cls == Integer.class) {
            Settings.Setting<Integer> is = (Settings.Setting<Integer>) s;
            int step = s.getName().toLowerCase(Locale.ROOT).contains("size")
                    || s.getName().toLowerCase(Locale.ROOT).contains("radius") ? 8 : 1;
            is.value = Math.max(0, is.value + dir * step);
        } else if (cls == Double.class) {
            Settings.Setting<Double> ds = (Settings.Setting<Double>) s;
            ds.value = Math.max(0.0, Math.round((ds.value + dir * 1.0) * 10.0) / 10.0);
        } else if (cls == Float.class) {
            Settings.Setting<Float> fs = (Settings.Setting<Float>) s;
            fs.value = (float) Math.max(0.0, Math.round((fs.value + dir * 0.5) * 10.0) / 10.0);
        }
    }

    @SuppressWarnings("unchecked")
    private void commitInlineEdit() {
        if (editingSetting == null) {
            return;
        }
        String text = editBuffer.toString().trim();
        Class<?> cls = editingSetting.getValueClass();
        try {
            if (cls == Integer.class) {
                ((Settings.Setting<Integer>) editingSetting).value = Integer.parseInt(text);
            } else if (cls == Double.class) {
                ((Settings.Setting<Double>) editingSetting).value = Double.parseDouble(text);
            } else if (cls == Float.class) {
                ((Settings.Setting<Float>) editingSetting).value = Float.parseFloat(text);
            } else if (cls == String.class) {
                ((Settings.Setting<String>) editingSetting).value = text;
            }
            onSettingChanged(editingSetting);
        } catch (NumberFormatException ignored) {
        }
        editingSetting = null;
        editBuffer.setLength(0);
    }

    private static void onSettingChanged(Settings.Setting<?> s) {
        if (s.getName().toLowerCase(Locale.ROOT).startsWith("minimap")) {
            MinimapRenderer.invalidate();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset += (scrollY > 0 ? -2 : 2);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editingSetting != null) {
            if (event.isAllowedChatCharacter() && editBuffer.length() < 32) {
                editBuffer.append(event.codepointAsString());
            }
            return true;
        }
        if (radialFocus == 1) {
            if (event.isAllowedChatCharacter() && radialLabelInput.length() < 20) {
                radialLabelInput.append(event.codepointAsString());
            }
            return true;
        }
        if (radialFocus == 2) {
            if (event.isAllowedChatCharacter() && radialCmdInput.length() < 48) {
                radialCmdInput.append(event.codepointAsString());
            }
            return true;
        }
        if (searchFocused) {
            if (event.isAllowedChatCharacter() && searchQuery.length() < 32) {
                searchQuery.append(event.codepointAsString());
                rebuildVisibleSettings();
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editingSetting != null) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                commitInlineEdit();
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE) {
                editingSetting = null;
                editBuffer.setLength(0);
                return true;
            }
            if (event.key() == InputConstants.KEY_BACKSPACE && editBuffer.length() > 0) {
                editBuffer.deleteCharAt(editBuffer.length() - 1);
                return true;
            }
            return true;
        }

        if (radialFocus != 0) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                radialFocus = 0;
                return true;
            }
            if (event.key() == InputConstants.KEY_BACKSPACE) {
                StringBuilder buf = (radialFocus == 1) ? radialLabelInput : radialCmdInput;
                if (buf.length() > 0) {
                    buf.deleteCharAt(buf.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (searchFocused) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (event.key() == InputConstants.KEY_BACKSPACE && searchQuery.length() > 0) {
                searchQuery.deleteCharAt(searchQuery.length() - 1);
                rebuildVisibleSettings();
                return true;
            }
            return true;
        }

        if (event.key() == InputConstants.KEY_ESCAPE || event.key() == resolveSettingsKeyCode()) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
