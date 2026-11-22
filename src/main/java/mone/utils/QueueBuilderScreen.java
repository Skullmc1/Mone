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
import mone.Mone;
import mone.api.IMone;
import mone.api.MoneAPI;
import mone.api.Settings;
import mone.api.utils.Helper;
import mone.behavior.QueueBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Intuitive visual Queue Script Builder GUI (#queue gui).
 * Each parameterized step has its own dedicated input box and + Add button,
 * and right-clicking any added step opens an inline editor.
 * Styled in Mone's black & orange theme.
 */
public class QueueBuilderScreen extends Screen implements Helper {

    private static final int MARGIN_X = 18;
    private static final int MARGIN_Y = 16;
    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 26;
    private static final int LEFT_W = 166;
    private static final int RIGHT_W = 118;

    private static final String[][] PARAM_TEMPLATES = {
            {"Mine", "mine", "64 diamond_ore"},
            {"Goto Waypoint", "wp goto", "home"},
            {"Goto Coords", "goto", "100 -200"},
            {"Custom Command", "custom", "tunnel 2 1 50"}
    };

    private static final String[][] QUICK_TEMPLATES = {
            {"Drop-Off", "dropoff now"},
            {"Lumber", "lumber"},
            {"Highway", "highway"},
            {"Auto-Fish", "fish"},
            {"Log Out", "logout"}
    };

    private final List<String> steps = new ArrayList<>();
    private final StringBuilder[] paramInputs = new StringBuilder[PARAM_TEMPLATES.length];
    private final StringBuilder presetNameInput = new StringBuilder();

    // Focus: -1 = none, 0..3 = paramInputs[i], 10 = presetNameInput
    private int focusedField = -1;
    private int stepsScroll = 0;
    private int presetsScroll = 0;
    private List<Map.Entry<String, String>> savedPresets = new ArrayList<>();

    // Right-click inline step edit modal
    private int editingStepIndex = -1;
    private final StringBuilder editStepBuffer = new StringBuilder();

    public QueueBuilderScreen() {
        super(Component.literal("Mone Queue Builder"));
        for (int i = 0; i < paramInputs.length; i++) {
            paramInputs[i] = new StringBuilder();
        }
        IMone mone = MoneAPI.getProvider().getPrimaryMone();
        if (mone instanceof Mone m) {
            QueueBehavior qb = m.getQueueBehavior();
            steps.addAll(qb.getQueuedCommands());
            reloadPresets(qb);
        }
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.level != null) {
            mc.gui.setScreen(new QueueBuilderScreen());
        }
    }

    private void reloadPresets(QueueBehavior qb) {
        savedPresets = new ArrayList<>(qb.loadSavedScripts().entrySet());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xBB000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        int px = MARGIN_X;
        int py = MARGIN_Y;
        int pw = Math.max(410, this.width - MARGIN_X * 2);
        int ph = Math.max(220, this.height - MARGIN_Y * 2);

        // Main black panel with orange border
        guiGraphics.fill(px, py, px + pw, py + ph, 0xF50B0B0D);
        guiGraphics.outline(px, py, pw, ph, 0xFF3A3A40);

        // Header
        guiGraphics.fill(px, py, px + pw, py + HEADER_H, 0xFF111114);
        guiGraphics.fill(px, py + HEADER_H - 1, px + pw, py + HEADER_H, 0xFFEA5B1C);
        guiGraphics.text(this.font, "Mone Queue Script Builder", px + 10, py + 10, 0xFFFF7A29, true);

        int swX = px + pw - 90;
        boolean hoverSw = mouseX >= swX && mouseX < swX + 80 && mouseY >= py + 5 && mouseY < py + 23;
        guiGraphics.fill(swX, py + 5, swX + 80, py + 23, hoverSw ? 0xFF26262C : 0xFF18181C);
        guiGraphics.outline(swX, py + 5, 80, 18, 0xFFFF7A29);
        guiGraphics.centeredText(this.font, "Settings GUI", swX + 40, py + 10, 0xFFFFFFFF);

        int bodyTop = py + HEADER_H;
        int bodyBottom = py + ph - FOOTER_H;
        int centerX = px + LEFT_W;
        int centerW = Math.max(120, pw - LEFT_W - RIGHT_W);
        int rightX = centerX + centerW;
        boolean blink = ((System.currentTimeMillis() / 500L) & 1L) == 0L;

        // --- LEFT COLUMN: Per-step builders ---
        guiGraphics.fill(px, bodyTop, centerX, bodyBottom, 0xFF0E0E11);
        guiGraphics.fill(centerX - 1, bodyTop, centerX, bodyBottom, 0xFF2A2A30);
        guiGraphics.text(this.font, "Add Steps", px + 8, bodyTop + 6, 0xFFFF7A29, true);

        int rowY = bodyTop + 20;
        int boxX = px + 8;
        int addW = 28;
        int boxW = LEFT_W - 16 - addW - 4;

        for (int i = 0; i < PARAM_TEMPLATES.length; i++) {
            guiGraphics.text(this.font, PARAM_TEMPLATES[i][0], boxX, rowY, 0xFFDDDDDD, true);
            int inputY = rowY + 10;
            boolean focused = (focusedField == i);
            guiGraphics.fill(boxX, inputY, boxX + boxW, inputY + 16, 0xFF060608);
            guiGraphics.outline(boxX, inputY, boxW, 16, focused ? 0xFFFF7A29 : 0xFF33333A);

            String txt = paramInputs[i].toString();
            if (txt.isEmpty()) {
                String phText = (focused && blink) ? "|" : PARAM_TEMPLATES[i][2];
                guiGraphics.text(this.font, phText, boxX + 4, inputY + 4,
                        (focused && blink) ? 0xFFFFFFFF : 0xFF5A5A66, false);
            } else {
                String disp = txt.length() > 16 ? txt.substring(txt.length() - 16) : txt;
                guiGraphics.text(this.font, disp + ((focused && blink) ? "|" : ""), boxX + 4, inputY + 4, 0xFFFFFFFF, true);
            }

            int btnX = boxX + boxW + 4;
            boolean hovAdd = mouseX >= btnX && mouseX < btnX + addW && mouseY >= inputY && mouseY < inputY + 16;
            guiGraphics.fill(btnX, inputY, btnX + addW, inputY + 16, hovAdd ? 0xFFFF7A29 : 0xFFCC4A14);
            guiGraphics.centeredText(this.font, "+Add", btnX + addW / 2, inputY + 4, 0xFFFFFFFF);

            rowY += 30;
        }

        // Quick one-click actions (no args needed)
        guiGraphics.text(this.font, "One-Click Steps", boxX, rowY + 2, 0xFFFF7A29, true);
        int qY = rowY + 14;
        int halfBtnW = (LEFT_W - 20) / 2;
        for (int i = 0; i < QUICK_TEMPLATES.length; i++) {
            int col = i % 2;
            int r = i / 2;
            int qx = boxX + col * (halfBtnW + 4);
            int qy = qY + r * 19;
            if (qy + 16 > bodyBottom - 4) {
                break;
            }
            boolean hov = mouseX >= qx && mouseX < qx + halfBtnW && mouseY >= qy && mouseY < qy + 16;
            guiGraphics.fill(qx, qy, qx + halfBtnW, qy + 16, hov ? 0xFF282830 : 0xFF16161B);
            guiGraphics.outline(qx, qy, halfBtnW, 16, hov ? 0xFFFF7A29 : 0xFF3A3A44);
            guiGraphics.centeredText(this.font, "+ " + QUICK_TEMPLATES[i][0], qx + halfBtnW / 2, qy + 4, 0xFFFFFFFF);
        }

        // --- CENTER COLUMN: Script Steps ---
        Settings settings = MoneAPI.getSettings();
        boolean recover = settings.queueRecoverOnDeath.value;
        int recX = centerX + 8;
        int recY = bodyTop + 6;
        int recW = centerW - 16;
        boolean hoverRec = mouseX >= recX && mouseX < recX + recW && mouseY >= recY && mouseY < recY + 18;
        guiGraphics.fill(recX, recY, recX + recW, recY + 18, recover ? 0xFFCC4A14 : (hoverRec ? 0xFF222228 : 0xFF151519));
        guiGraphics.outline(recX, recY, recW, 18, recover ? 0xFFFF9B42 : 0xFF3A3A42);
        guiGraphics.centeredText(this.font, "On Death: Recover Items & Resume [" + (recover ? "ON" : "OFF") + "]",
                recX + recW / 2, recY + 5, 0xFFFFFFFF);

        guiGraphics.text(this.font, "Script Steps (Right-click any step to edit)", recX, recY + 23, 0xFF9999A6, false);

        int listTop = recY + 35;
        int listH = Math.max(20, bodyBottom - listTop - 6);
        int rowH = 20;
        int visRows = Math.max(1, listH / rowH);
        stepsScroll = Mth.clamp(stepsScroll, 0, Math.max(0, steps.size() - visRows));

        if (steps.isEmpty()) {
            guiGraphics.centeredText(this.font, "No steps yet. Add steps on the left!", centerX + centerW / 2, listTop + 18, 0xFF666672);
        } else {
            for (int i = 0; i < visRows && (i + stepsScroll) < steps.size(); i++) {
                int idx = i + stepsScroll;
                int ry = listTop + i * rowH;
                boolean hovRow = mouseX >= recX && mouseX < recX + recW && mouseY >= ry && mouseY < ry + rowH - 2;
                guiGraphics.fill(recX, ry, recX + recW, ry + rowH - 2, hovRow ? 0xFF1F1F26 : 0xFF141418);
                guiGraphics.outline(recX, ry, recW, rowH - 2, hovRow ? 0xFFFF7A29 : 0xFF2A2A32);

                String line = (idx + 1) + ". " + steps.get(idx);
                if (line.length() > 28) {
                    line = line.substring(0, 27) + "…";
                }
                guiGraphics.text(this.font, line, recX + 5, ry + 5, 0xFFFFFFFF, true);

                int delX = recX + recW - 16;
                int dnX = delX - 15;
                int upX = dnX - 15;
                guiGraphics.fill(upX, ry + 2, upX + 13, ry + 15, 0xFF222228);
                guiGraphics.centeredText(this.font, "^", upX + 6, ry + 5, 0xFFFFAA55);
                guiGraphics.fill(dnX, ry + 2, dnX + 13, ry + 15, 0xFF222228);
                guiGraphics.centeredText(this.font, "v", dnX + 6, ry + 5, 0xFFFFAA55);
                guiGraphics.fill(delX, ry + 2, delX + 14, ry + 15, 0xFF551A1A);
                guiGraphics.centeredText(this.font, "X", delX + 7, ry + 5, 0xFFFF9999);
            }
        }

        // --- RIGHT COLUMN: Saved Presets ---
        guiGraphics.fill(rightX, bodyTop, px + pw, bodyBottom, 0xFF0E0E11);
        guiGraphics.fill(rightX, bodyTop, rightX + 1, bodyBottom, 0xFF2A2A30);
        guiGraphics.text(this.font, "Saved Scripts", rightX + 8, bodyTop + 6, 0xFFFF7A29, true);

        int pnX = rightX + 6;
        int pnY = bodyTop + 18;
        int pnW = RIGHT_W - 12;
        guiGraphics.fill(pnX, pnY, pnX + pnW, pnY + 16, 0xFF060608);
        guiGraphics.outline(pnX, pnY, pnW, 16, focusedField == 10 ? 0xFFFF7A29 : 0xFF33333A);
        String nText = presetNameInput.toString();
        if (nText.isEmpty()) {
            guiGraphics.text(this.font, (focusedField == 10 && blink) ? "|" : "Script name...",
                    pnX + 4, pnY + 4, (focusedField == 10 && blink) ? 0xFFFFFFFF : 0xFF5A5A66, false);
        } else {
            guiGraphics.text(this.font, nText + ((focusedField == 10 && blink) ? "|" : ""), pnX + 4, pnY + 4, 0xFFFFFFFF, true);
        }

        int saveBtnY = pnY + 20;
        boolean hoverSaveP = mouseX >= pnX && mouseX < pnX + pnW && mouseY >= saveBtnY && mouseY < saveBtnY + 16;
        guiGraphics.fill(pnX, saveBtnY, pnX + pnW, saveBtnY + 16, hoverSaveP ? 0xFFFF7A29 : 0xFFCC4A14);
        guiGraphics.centeredText(this.font, "Save Preset", pnX + pnW / 2, saveBtnY + 4, 0xFFFFFFFF);

        int pListTop = saveBtnY + 22;
        int pVis = Math.max(1, (bodyBottom - pListTop - 4) / 18);
        presetsScroll = Mth.clamp(presetsScroll, 0, Math.max(0, savedPresets.size() - pVis));
        for (int i = 0; i < pVis && (i + presetsScroll) < savedPresets.size(); i++) {
            Map.Entry<String, String> entry = savedPresets.get(i + presetsScroll);
            int ry = pListTop + i * 18;
            boolean hov = mouseX >= pnX && mouseX < pnX + pnW && mouseY >= ry && mouseY < ry + 16;
            guiGraphics.fill(pnX, ry, pnX + pnW, ry + 16, hov ? 0xFF22222A : 0xFF151519);
            guiGraphics.text(this.font, entry.getKey(), pnX + 4, ry + 4, 0xFFEEEEEE, true);
            guiGraphics.fill(pnX + pnW - 14, ry + 1, pnX + pnW - 1, ry + 15, 0xFF551A1A);
            guiGraphics.centeredText(this.font, "X", pnX + pnW - 7, ry + 4, 0xFFFF9999);
        }

        // --- FOOTER ---
        int fy = bodyBottom;
        guiGraphics.fill(px, fy, px + pw, py + ph, 0xFF111114);
        guiGraphics.fill(px, fy, px + pw, fy + 1, 0xFF2A2A30);

        int runX = px + 8;
        int clrX = runX + 94;
        int clsX = px + pw - 64;
        guiGraphics.fill(runX, fy + 4, runX + 86, fy + 21, 0xFFEA5B1C);
        guiGraphics.outline(runX, fy + 4, 86, 17, 0xFFFF9B42);
        guiGraphics.centeredText(this.font, "Run Script", runX + 43, fy + 9, 0xFFFFFFFF);

        guiGraphics.fill(clrX, fy + 4, clrX + 54, fy + 21, 0xFF1E1E24);
        guiGraphics.outline(clrX, fy + 4, 54, 17, 0xFF44444E);
        guiGraphics.centeredText(this.font, "Clear", clrX + 27, fy + 9, 0xFFDDDDDD);

        guiGraphics.fill(clsX, fy + 4, clsX + 56, fy + 21, 0xFF1E1E24);
        guiGraphics.outline(clsX, fy + 4, 56, 17, 0xFF44444E);
        guiGraphics.centeredText(this.font, "Close", clsX + 28, fy + 9, 0xFFDDDDDD);

        // Inline Edit Step Modal (opened by right-clicking any step)
        if (editingStepIndex >= 0 && editingStepIndex < steps.size()) {
            renderEditStepModal(guiGraphics, mouseX, mouseY, blink);
        }
    }

    private void renderEditStepModal(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean blink) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x88000000);
        int dw = 230;
        int dh = 76;
        int dx = (this.width - dw) / 2;
        int dy = (this.height - dh) / 2;

        guiGraphics.fill(dx, dy, dx + dw, dy + dh, 0xF80B0B0D);
        guiGraphics.outline(dx, dy, dw, dh, 0xFFFF7A29);
        guiGraphics.text(this.font, "Edit Step #" + (editingStepIndex + 1), dx + 8, dy + 7, 0xFFFF7A29, true);

        int bx = dx + 8;
        int by = dy + 22;
        int bw = dw - 16;
        guiGraphics.fill(bx, by, bx + bw, by + 18, 0xFF050507);
        guiGraphics.outline(bx, by, bw, 18, 0xFFFF7A29);
        guiGraphics.text(this.font, editStepBuffer.toString() + (blink ? "|" : ""), bx + 5, by + 5, 0xFFFFFFFF, true);

        int btnY = dy + 48;
        int btnW = (dw - 24) / 2;
        int saveX = dx + 8;
        int cancelX = dx + 16 + btnW;

        boolean hovS = mouseX >= saveX && mouseX < saveX + btnW && mouseY >= btnY && mouseY < btnY + 18;
        boolean hovC = mouseX >= cancelX && mouseX < cancelX + btnW && mouseY >= btnY && mouseY < btnY + 18;

        guiGraphics.fill(saveX, btnY, saveX + btnW, btnY + 18, hovS ? 0xFFFF7A29 : 0xFFCC4A14);
        guiGraphics.centeredText(this.font, "Save", saveX + btnW / 2, btnY + 5, 0xFFFFFFFF);
        guiGraphics.fill(cancelX, btnY, cancelX + btnW, btnY + 18, hovC ? 0xFF282830 : 0xFF1A1A20);
        guiGraphics.outline(cancelX, btnY, btnW, 18, 0xFF44444E);
        guiGraphics.centeredText(this.font, "Cancel", cancelX + btnW / 2, btnY + 5, 0xFFDDDDDD);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        // Modal click handling
        if (editingStepIndex >= 0) {
            int dw = 230;
            int dh = 76;
            int dx = (this.width - dw) / 2;
            int dy = (this.height - dh) / 2;
            int btnY = dy + 48;
            int btnW = (dw - 24) / 2;
            int saveX = dx + 8;
            int cancelX = dx + 16 + btnW;

            if (event.button() == 0) {
                if (mx >= saveX && mx < saveX + btnW && my >= btnY && my < btnY + 18) {
                    commitEditStep();
                    return true;
                }
                if (mx >= cancelX && mx < cancelX + btnW && my >= btnY && my < btnY + 18) {
                    editingStepIndex = -1;
                    return true;
                }
                if (mx < dx || mx > dx + dw || my < dy || my > dy + dh) {
                    editingStepIndex = -1;
                }
            }
            return true;
        }

        int px = MARGIN_X;
        int py = MARGIN_Y;
        int pw = Math.max(410, this.width - MARGIN_X * 2);
        int ph = Math.max(220, this.height - MARGIN_Y * 2);
        int bodyTop = py + HEADER_H;
        int bodyBottom = py + ph - FOOTER_H;
        int centerX = px + LEFT_W;
        int centerW = Math.max(120, pw - LEFT_W - RIGHT_W);
        int rightX = centerX + centerW;

        int recX = centerX + 8;
        int recY = bodyTop + 6;
        int recW = centerW - 16;
        int listTop = recY + 35;

        // Right-click a step in the center list to edit its arguments
        if (event.button() == 1 && mx >= recX && mx < recX + recW && my >= listTop && my < bodyBottom) {
            int row = (int) ((my - listTop) / 20);
            int idx = row + stepsScroll;
            if (idx >= 0 && idx < steps.size()) {
                editingStepIndex = idx;
                editStepBuffer.setLength(0);
                editStepBuffer.append(steps.get(idx));
                focusedField = -1;
                return true;
            }
        }

        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        // Header switch button
        int swX = px + pw - 90;
        if (mx >= swX && mx < swX + 80 && my >= py + 5 && my < py + 23) {
            SettingsGuiScreen.open();
            return true;
        }

        // Left parameterized step inputs & +Add buttons
        int rowY = bodyTop + 20;
        int boxX = px + 8;
        int addW = 28;
        int boxW = LEFT_W - 16 - addW - 4;
        for (int i = 0; i < PARAM_TEMPLATES.length; i++) {
            int inputY = rowY + 10;
            int btnX = boxX + boxW + 4;
            if (my >= inputY && my < inputY + 16) {
                if (mx >= boxX && mx < boxX + boxW) {
                    focusedField = i;
                    return true;
                }
                if (mx >= btnX && mx < btnX + addW) {
                    addParamStep(i);
                    return true;
                }
            }
            rowY += 30;
        }

        // Left one-click buttons
        int qY = rowY + 14;
        int halfBtnW = (LEFT_W - 20) / 2;
        for (int i = 0; i < QUICK_TEMPLATES.length; i++) {
            int col = i % 2;
            int r = i / 2;
            int qx = boxX + col * (halfBtnW + 4);
            int qy = qY + r * 19;
            if (mx >= qx && mx < qx + halfBtnW && my >= qy && my < qy + 16) {
                steps.add(QUICK_TEMPLATES[i][1]);
                focusedField = -1;
                return true;
            }
        }

        // Right preset name input
        int pnX = rightX + 6;
        int pnY = bodyTop + 18;
        int pnW = RIGHT_W - 12;
        if (mx >= pnX && mx < pnX + pnW && my >= pnY && my < pnY + 16) {
            focusedField = 10;
            return true;
        }
        focusedField = -1;

        // Center death recovery toggle
        if (mx >= recX && mx < recX + recW && my >= recY && my < recY + 18) {
            Settings s = MoneAPI.getSettings();
            s.queueRecoverOnDeath.value = !s.queueRecoverOnDeath.value;
            return true;
        }

        // Center steps reorder/delete
        if (mx >= recX && mx < recX + recW && my >= listTop && my < bodyBottom) {
            int row = (int) ((my - listTop) / 20);
            int idx = row + stepsScroll;
            if (idx >= 0 && idx < steps.size()) {
                int delX = recX + recW - 16;
                int dnX = delX - 15;
                int upX = dnX - 15;
                if (mx >= delX && mx < delX + 14) {
                    steps.remove(idx);
                    return true;
                }
                if (mx >= upX && mx < upX + 13 && idx > 0) {
                    String s = steps.remove(idx);
                    steps.add(idx - 1, s);
                    return true;
                }
                if (mx >= dnX && mx < dnX + 13 && idx < steps.size() - 1) {
                    String s = steps.remove(idx);
                    steps.add(idx + 1, s);
                    return true;
                }
            }
        }

        // Right save preset & preset list
        int saveBtnY = pnY + 20;
        if (mx >= pnX && mx < pnX + pnW && my >= saveBtnY && my < saveBtnY + 16) {
            IMone mone = MoneAPI.getProvider().getPrimaryMone();
            String name = presetNameInput.toString().trim();
            if (mone instanceof Mone m && !name.isEmpty() && !steps.isEmpty()) {
                m.getQueueBehavior().saveScript(name, steps);
                reloadPresets(m.getQueueBehavior());
                presetNameInput.setLength(0);
                Helper.HELPER.logDirect("Saved queue script '" + name + "'");
            }
            return true;
        }

        int pListTop = saveBtnY + 22;
        if (mx >= pnX && mx < pnX + pnW && my >= pListTop && my < bodyBottom) {
            int row = (int) ((my - pListTop) / 18);
            int idx = row + presetsScroll;
            if (idx >= 0 && idx < savedPresets.size()) {
                Map.Entry<String, String> entry = savedPresets.get(idx);
                if (mx >= pnX + pnW - 14) {
                    IMone mone = MoneAPI.getProvider().getPrimaryMone();
                    if (mone instanceof Mone m) {
                        m.getQueueBehavior().deleteSavedScript(entry.getKey());
                        reloadPresets(m.getQueueBehavior());
                    }
                } else {
                    steps.clear();
                    for (String part : entry.getValue().split(";")) {
                        String t = part.trim();
                        if (!t.isEmpty()) {
                            steps.add(t);
                        }
                    }
                }
                return true;
            }
        }

        // Footer buttons
        int fy = bodyBottom;
        int runX = px + 8;
        int clrX = runX + 94;
        int clsX = px + pw - 64;
        if (my >= fy + 4 && my < fy + 21) {
            if (mx >= runX && mx < runX + 86) {
                IMone mone = MoneAPI.getProvider().getPrimaryMone();
                if (mone instanceof Mone m && !steps.isEmpty()) {
                    m.getQueueBehavior().runScript(steps);
                    this.onClose();
                }
                return true;
            }
            if (mx >= clrX && mx < clrX + 54) {
                steps.clear();
                return true;
            }
            if (mx >= clsX && mx < clsX + 56) {
                this.onClose();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void addParamStep(int idx) {
        String arg = paramInputs[idx].toString().trim();
        String prefix = PARAM_TEMPLATES[idx][1];
        String fallback = PARAM_TEMPLATES[idx][2];
        String cmd;
        if ("custom".equals(prefix)) {
            if (arg.isEmpty()) {
                return;
            }
            cmd = arg;
        } else {
            cmd = prefix + " " + (arg.isEmpty() ? fallback : arg);
        }
        steps.add(cmd);
        paramInputs[idx].setLength(0);
    }

    private void commitEditStep() {
        if (editingStepIndex >= 0 && editingStepIndex < steps.size()) {
            String updated = editStepBuffer.toString().trim();
            if (!updated.isEmpty()) {
                steps.set(editingStepIndex, updated);
            }
        }
        editingStepIndex = -1;
        editStepBuffer.setLength(0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (editingStepIndex >= 0) {
            return true;
        }
        int dir = scrollY > 0 ? -1 : 1;
        int px = MARGIN_X;
        int pw = Math.max(410, this.width - MARGIN_X * 2);
        int rightX = px + pw - RIGHT_W;
        if (mouseX >= rightX) {
            presetsScroll += dir;
        } else {
            stepsScroll += dir;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editingStepIndex >= 0) {
            if (event.isAllowedChatCharacter() && editStepBuffer.length() < 64) {
                editStepBuffer.append(event.codepointAsString());
            }
            return true;
        }
        if (focusedField >= 0 && focusedField < paramInputs.length) {
            if (event.isAllowedChatCharacter() && paramInputs[focusedField].length() < 48) {
                paramInputs[focusedField].append(event.codepointAsString());
            }
            return true;
        }
        if (focusedField == 10) {
            if (event.isAllowedChatCharacter() && presetNameInput.length() < 24) {
                presetNameInput.append(event.codepointAsString());
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editingStepIndex >= 0) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                commitEditStep();
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE) {
                editingStepIndex = -1;
                return true;
            }
            if (event.key() == InputConstants.KEY_BACKSPACE && editStepBuffer.length() > 0) {
                editStepBuffer.deleteCharAt(editStepBuffer.length() - 1);
                return true;
            }
            return true;
        }

        if (focusedField >= 0 && focusedField < paramInputs.length) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                addParamStep(focusedField);
                return true;
            }
            if (event.key() == InputConstants.KEY_BACKSPACE && paramInputs[focusedField].length() > 0) {
                paramInputs[focusedField].deleteCharAt(paramInputs[focusedField].length() - 1);
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE) {
                focusedField = -1;
                return true;
            }
            return true;
        }
        if (focusedField == 10) {
            if (event.key() == InputConstants.KEY_BACKSPACE && presetNameInput.length() > 0) {
                presetNameInput.deleteCharAt(presetNameInput.length() - 1);
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE) {
                focusedField = -1;
                return true;
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
