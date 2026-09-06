package com.hexhaki.client.screen;

import com.hexhaki.client.HakiClientState;
import com.hexhaki.data.HakiRank;
import com.hexhaki.data.HakiTechnique;
import com.hexhaki.data.HakiType;
import com.hexhaki.data.HakiUnlocks;
import com.hexhaki.data.MasteryCurve;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Legacy asset compatibility marker: mastery_logbook.png.
 * Clean pirate captain's ledger for HexHaki. Every label lives in a fixed region; long ability names
 * are fitted or wrapped instead of spilling across neighboring boxes.
 */
public final class HakiMasteryScreen extends Screen {
    private static final int W = 510;
    private static final int H = 324;
    private static final int HEADER_H = 42;
    private static final int TABS_Y = 48;
    private static final int TABS_H = 24;
    private static final int LIST_X = 18;
    private static final int LIST_Y = 108;
    private static final int LIST_W = 222;
    private static final int ROW_H = 29;
    private static final int VISIBLE_ROWS = 6;
    private static final int DETAIL_X = 256;
    private static final int DETAIL_W = 236;

    private static final int BG = 0xF0161211;
    private static final int PANEL = 0xE91F1815;
    private static final int PANEL_LIGHT = 0xE62A201A;
    private static final int BORDER = 0xFF89663B;
    private static final int BORDER_DARK = 0xFF4E3928;
    private static final int GOLD = 0xFFE4C47A;
    private static final int TEXT = 0xFFF4E8D2;
    private static final int MUTED = 0xFFB39F84;
    private static final int RED = 0xFFE13A47;
    private static final int GREEN = 0xFF77B886;
    private static final int BLUE = 0xFF6DB9E9;

    private int tab;
    private int scroll;
    private final int[] selected = {0, 0, 0};

    public HakiMasteryScreen() {
        super(Component.literal("HexHaki Mastery"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = (width - W) / 2;
        int y = (height - H) / 2;

        drawFrame(g, x, y);
        drawHeader(g, x, y, mouseX, mouseY);
        drawTabs(g, x, y, mouseX, mouseY);

        HakiType type = HakiType.values()[tab];
        int mastery = mastery(type);
        HakiRank rank = HakiRank.of(mastery);

        drawPathSummary(g, x, y, type, mastery, rank);
        drawTechniqueList(g, x, y, type, mastery, mouseX, mouseY);

        List<HakiTechnique> techniques = HakiTechnique.forType(type);
        if (!techniques.isEmpty()) {
            selected[tab] = Math.max(0, Math.min(selected[tab], techniques.size() - 1));
            drawTechniqueDetails(g, x, y, type, mastery, techniques.get(selected[tab]));
        }

        drawFooter(g, x, y);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawFrame(GuiGraphics g, int x, int y) {
        // Dark leather base.
        g.fill(x, y, x + W, y + H, BG);
        border(g, x, y, W, H, BORDER);
        border(g, x + 3, y + 3, W - 6, H - 6, BORDER_DARK);

        // Header and footer brass/leather strips.
        g.fill(x + 7, y + 7, x + W - 7, y + HEADER_H, 0xF21B1413);
        g.fill(x + 7, y + H - 27, x + W - 7, y + H - 7, 0xF21B1413);
        g.fill(x + 8, y + HEADER_H - 1, x + W - 8, y + HEADER_H, 0x887B5A35);

        // Main left/right panels.
        panel(g, x + 10, y + 78, 238, 211);
        panel(g, x + 252, y + 78, 248, 211);

        // Subtle center seam like a captain's ledger binding.
        g.fill(x + 249, y + 82, x + 251, y + 285, 0xFF4C3427);
        g.fill(x + 250, y + 82, x + 251, y + 285, 0xFF9A7242);
    }

    private void drawHeader(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawFitted(g, "HEXHAKI", x + 18, y + 14, 126, 1.28f, RED);
        drawFitted(g, "CAPTAIN'S HAKI LEDGER", x + 154, y + 17, 190, .75f, GOLD);

        String state = HakiClientState.joyBoy ? "JOY BOY AWAKENED" : "GRAND LINE PROGRESSION";
        drawFittedRight(g, state, x + W - 45, y + 18, 140, .62f,
                HakiClientState.joyBoy ? RED : MUTED);

        boolean hover = mouseX >= x + W - 34 && mouseX <= x + W - 14
                && mouseY >= y + 12 && mouseY <= y + 31;
        g.fill(x + W - 34, y + 12, x + W - 14, y + 31, hover ? 0x994B1E22 : 0x6630201C);
        border(g, x + W - 34, y + 12, 20, 19, hover ? RED : BORDER_DARK);
        drawCenteredFitted(g, "X", x + W - 34, y + 17, 20, .72f, hover ? 0xFFFFD8DC : GOLD);
    }

    private void drawTabs(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        HakiType[] types = HakiType.values();
        int totalW = W - 36;
        int gap = 6;
        int tabW = (totalW - gap * 2) / 3;
        for (int i = 0; i < types.length; i++) {
            int tx = x + 18 + i * (tabW + gap);
            boolean active = i == tab;
            boolean hover = mouseX >= tx && mouseX < tx + tabW
                    && mouseY >= y + TABS_Y && mouseY < y + TABS_Y + TABS_H;
            int fill = active ? 0xE0312420 : (hover ? 0xC628201C : 0xA51C1715);
            g.fill(tx, y + TABS_Y, tx + tabW, y + TABS_Y + TABS_H, fill);
            border(g, tx, y + TABS_Y, tabW, TABS_H, active ? color(types[i]) : BORDER_DARK);
            drawCenteredFitted(g, types[i].display.toUpperCase(), tx + 4, y + TABS_Y + 7,
                    tabW - 8, .72f, active ? color(types[i]) : MUTED);
        }
    }

    private void drawPathSummary(GuiGraphics g, int x, int y, HakiType type, int mastery, HakiRank rank) {
        int sx = x + 18;
        int sy = y + 84;
        int sw = 222;

        drawFitted(g, type.display.toUpperCase(), sx, sy, 110, .74f, color(type));
        drawFittedRight(g, mastery + " / 1000", sx + sw, sy, 88, .70f, TEXT);

        long totalXp = xp(type);
        long into = MasteryCurve.intoLevel(totalXp);
        long next = MasteryCurve.nextLevelCost(totalXp);
        String rankText = rank.name().replace('_', ' ');
        String xpText = next == 0 ? "MAX MASTERY" : "XP " + into + "/" + next;
        drawFitted(g, rankText, sx, sy + 12, 102, .55f, MUTED);
        drawFittedRight(g, xpText, sx + sw, sy + 12, 104, .52f, MUTED);
        bar(g, sx, sy + 22, sw, 4, rankProgress(mastery, rank), color(type));
    }

    private void drawTechniqueList(GuiGraphics g, int x, int y, HakiType type, int mastery, int mouseX, int mouseY) {
        List<HakiTechnique> techniques = HakiTechnique.forType(type);
        int maxScroll = Math.max(0, techniques.size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        selected[tab] = Math.max(0, Math.min(selected[tab], Math.max(0, techniques.size() - 1)));

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= techniques.size()) break;
            HakiTechnique t = techniques.get(index);
            int rx = x + LIST_X;
            int ry = y + LIST_Y + row * ROW_H;
            boolean isUnlocked = unlocked(t, mastery);
            boolean active = index == selected[tab];
            boolean hover = mouseX >= rx && mouseX < rx + LIST_W
                    && mouseY >= ry && mouseY < ry + ROW_H - 3;

            g.fill(rx, ry, rx + LIST_W, ry + ROW_H - 3,
                    active ? 0xE23B2924 : (hover ? 0xD22F2420 : 0xB9201A17));
            border(g, rx, ry, LIST_W, ROW_H - 3,
                    active ? color(type) : (isUnlocked ? BORDER_DARK : 0xFF573333));

            // Lock/status badge gets its own fixed region.
            int badgeW = 43;
            g.fill(rx + 4, ry + 4, rx + badgeW, ry + ROW_H - 7,
                    isUnlocked ? 0xFF273326 : 0xFF3A2021);
            border(g, rx + 4, ry + 4, badgeW - 4, ROW_H - 11,
                    isUnlocked ? GREEN : RED);
            drawCenteredFitted(g, isUnlocked ? "READY" : "LOCK", rx + 4, ry + 8,
                    badgeW - 4, .49f, isUnlocked ? GREEN : RED);

            int textX = rx + badgeW + 5;
            int textW = LIST_W - badgeW - 10;
            drawFitted(g, t.display, textX, ry + 4, textW, .65f, isUnlocked ? TEXT : 0xFFBB8986);
            drawFitted(g, compactRequirement(t), textX, ry + 15, textW, .49f, MUTED);
        }

        if (maxScroll > 0) {
            int fy = y + 281;
            drawFitted(g, (scroll + 1) + "-" + Math.min(techniques.size(), scroll + VISIBLE_ROWS)
                    + " / " + techniques.size(), x + 18, fy, 72, .52f, MUTED);
            drawMiniButton(g, x + 192, fy - 3, 21, 14, "^", false);
            drawMiniButton(g, x + 218, fy - 3, 21, 14, "v", false);
        }
    }

    private void drawTechniqueDetails(GuiGraphics g, int x, int y, HakiType type, int mastery, HakiTechnique t) {
        int dx = x + DETAIL_X;
        int dy = y + 86;
        int maxW = DETAIL_W;
        boolean isUnlocked = unlocked(t, mastery);

        drawFitted(g, isUnlocked ? "UNLOCKED" : "LOCKED", dx, dy, 86, .68f, isUnlocked ? GREEN : RED);
        drawFittedRight(g, requirement(t), dx + maxW, dy, 135, .54f, isUnlocked ? GOLD : 0xFFBB7773);

        // Name box guarantees even the longest technique names do not invade the body text.
        g.fill(dx, dy + 16, dx + maxW, dy + 43, 0xB91B1614);
        border(g, dx, dy + 16, maxW, 27, color(type));
        drawCenteredFitted(g, t.display, dx + 7, dy + 25, maxW - 14, .78f, TEXT);

        drawSectionLabel(g, "ABILITY", dx, dy + 51, maxW);
        int descEnd = drawWrappedFitted(g, t.description, dx, dy + 65, maxW, TEXT, 10, 5);

        int trainY = Math.max(dy + 121, descEnd + 5);
        drawSectionLabel(g, "HOW TO TRAIN", dx, trainY, maxW);
        drawWrappedFitted(g, trainingHint(type, mastery), dx, trainY + 14, maxW, MUTED, 10, 4);

        HakiTechnique next = nextTechnique(type, mastery);
        int nextY = dy + 185;
        drawSectionLabel(g, "NEXT MILESTONE", dx, nextY, maxW);
        if (next == null) {
            drawFitted(g, "PATH COMPLETE", dx, nextY + 15, maxW, .70f, GREEN);
        } else {
            drawFitted(g, next.display, dx, nextY + 15, maxW, .65f, GOLD);
            drawFitted(g, requirement(next), dx, nextY + 27, maxW, .52f, MUTED);
        }
    }

    private void drawSectionLabel(GuiGraphics g, String label, int x, int y, int w) {
        drawFitted(g, label, x, y, 100, .58f, RED);
        int start = x + Math.min(104, font.width(label) + 12);
        g.fill(start, y + 4, x + w, y + 5, 0x665D4430);
    }

    private int drawWrappedFitted(GuiGraphics g, String text, int x, int y, int maxWidth, int color, int lineHeight, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), maxWidth);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            g.drawString(font, lines.get(i), x, y + i * lineHeight, color, false);
        }
        return y + count * lineHeight;
    }

    private void drawFooter(GuiGraphics g, int x, int y) {
        String left = "Legend " + HakiClientState.legend + " / 120";
        String right = "Haki " + Math.round(HakiClientState.energy) + " / " + Math.round(HakiClientState.maxEnergy);
        drawFitted(g, left, x + 18, y + H - 21, 105, .58f, GOLD);
        drawFittedRight(g, right, x + W - 18, y + H - 21, 120, .58f, TEXT);
        drawCenteredFitted(g, "1 / 2 / 3 switch paths   •   arrows or wheel browse abilities",
                x + 132, y + H - 21, W - 264, .48f, MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int x = (width - W) / 2;
        int y = (height - H) / 2;

        if (mouseX >= x + W - 34 && mouseX <= x + W - 14
                && mouseY >= y + 12 && mouseY <= y + 31) {
            onClose();
            return true;
        }

        int totalW = W - 36;
        int gap = 6;
        int tabW = (totalW - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            int tx = x + 18 + i * (tabW + gap);
            if (mouseX >= tx && mouseX < tx + tabW
                    && mouseY >= y + TABS_Y && mouseY < y + TABS_Y + TABS_H) {
                tab = i;
                scroll = 0;
                ensureSelectionVisible();
                return true;
            }
        }

        List<HakiTechnique> techniques = HakiTechnique.forType(HakiType.values()[tab]);
        if (mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W
                && mouseY >= y + LIST_Y && mouseY < y + LIST_Y + VISIBLE_ROWS * ROW_H) {
            int row = (int)((mouseY - (y + LIST_Y)) / ROW_H);
            int index = scroll + row;
            if (index >= 0 && index < techniques.size()) {
                selected[tab] = index;
                return true;
            }
        }

        int maxScroll = Math.max(0, techniques.size() - VISIBLE_ROWS);
        int fy = y + 278;
        if (mouseY >= fy && mouseY <= fy + 18) {
            if (mouseX >= x + 192 && mouseX <= x + 213) {
                scroll = Math.max(0, scroll - 1);
                return true;
            }
            if (mouseX >= x + 218 && mouseX <= x + 239) {
                scroll = Math.min(maxScroll, scroll + 1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_1) { tab = 0; scroll = 0; ensureSelectionVisible(); return true; }
        if (keyCode == GLFW.GLFW_KEY_2) { tab = 1; scroll = 0; ensureSelectionVisible(); return true; }
        if (keyCode == GLFW.GLFW_KEY_3) { tab = 2; scroll = 0; ensureSelectionVisible(); return true; }
        if (keyCode == GLFW.GLFW_KEY_LEFT) { tab = Math.floorMod(tab - 1, 3); scroll = 0; ensureSelectionVisible(); return true; }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) { tab = (tab + 1) % 3; scroll = 0; ensureSelectionVisible(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<HakiTechnique> techniques = HakiTechnique.forType(HakiType.values()[tab]);
        int maxScroll = Math.max(0, techniques.size() - VISIBLE_ROWS);
        if (delta < 0) scroll = Math.min(maxScroll, scroll + 1);
        if (delta > 0) scroll = Math.max(0, scroll - 1);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void ensureSelectionVisible() {
        List<HakiTechnique> techniques = HakiTechnique.forType(HakiType.values()[tab]);
        if (techniques.isEmpty()) { selected[tab] = 0; scroll = 0; return; }
        selected[tab] = Math.max(0, Math.min(selected[tab], techniques.size() - 1));
        if (selected[tab] < scroll) scroll = selected[tab];
        if (selected[tab] >= scroll + VISIBLE_ROWS) scroll = selected[tab] - VISIBLE_ROWS + 1;
    }

    private void drawMiniButton(GuiGraphics g, int x, int y, int w, int h, String text, boolean active) {
        g.fill(x, y, x + w, y + h, active ? 0xAA44252A : 0xAA211A17);
        border(g, x, y, w, h, active ? RED : BORDER_DARK);
        drawCenteredFitted(g, text, x, y + 3, w, .58f, GOLD);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        border(g, x, y, w, h, BORDER_DARK);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x444F3B2A);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawFitted(GuiGraphics g, String text, int x, int y, int maxWidth, float preferredScale, int color) {
        int width = Math.max(1, font.width(text));
        float scale = Math.max(.46f, Math.min(preferredScale, maxWidth / (float) width));
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, Math.round(x / scale), Math.round(y / scale), color, false);
        g.pose().popPose();
    }

    private void drawFittedRight(GuiGraphics g, String text, int rightX, int y, int maxWidth, float preferredScale, int color) {
        int width = Math.max(1, font.width(text));
        float scale = Math.max(.46f, Math.min(preferredScale, maxWidth / (float) width));
        drawFitted(g, text, Math.round(rightX - width * scale), y, maxWidth, scale, color);
    }

    private void drawCenteredFitted(GuiGraphics g, String text, int x, int y, int maxWidth, float preferredScale, int color) {
        int width = Math.max(1, font.width(text));
        float scale = Math.max(.46f, Math.min(preferredScale, maxWidth / (float) width));
        drawFitted(g, text, Math.round(x + (maxWidth - width * scale) / 2f), y, maxWidth, scale, color);
    }

    private void bar(GuiGraphics g, int x, int y, int w, int h, float f, int color) {
        g.fill(x, y, x + w, y + h, 0xFF211814);
        int fill = (int)(w * Math.max(0, Math.min(1, f)));
        if (fill > 0) g.fill(x, y, x + fill, y + h, color);
        border(g, x, y, w, h, BORDER_DARK);
    }

    private static int mastery(HakiType type) { return switch (type) {
        case ARMAMENT -> HakiClientState.armament;
        case OBSERVATION -> HakiClientState.observation;
        case CONQUEROR -> HakiClientState.conqueror;
    }; }

    private static long xp(HakiType type) { return switch (type) {
        case ARMAMENT -> HakiClientState.armamentXp;
        case OBSERVATION -> HakiClientState.observationXp;
        case CONQUEROR -> HakiClientState.conquerorXp;
    }; }

    private static int color(HakiType type) { return switch (type) {
        case ARMAMENT -> 0xFFC5B8AA;
        case OBSERVATION -> BLUE;
        case CONQUEROR -> RED;
    }; }

    private static boolean unlocked(HakiTechnique t, int mastery) {
        if (t == HakiTechnique.CONQ_AWAKENING) return HakiClientState.armament >= HakiUnlocks.CONQUEROR_AWAKEN_ARMAMENT
                && HakiClientState.observation >= HakiUnlocks.CONQUEROR_AWAKEN_OBSERVATION;
        if (t == HakiTechnique.CONQ_DOMINION) return HakiClientState.armament >= HakiUnlocks.KINGS_GRIP_ARMAMENT
                && HakiClientState.observation >= HakiUnlocks.KINGS_GRIP_OBSERVATION
                && HakiClientState.conqueror >= HakiUnlocks.KINGS_GRIP_CONQUEROR;
        if (t == HakiTechnique.CONQ_SOVEREIGN) return HakiClientState.armament >= HakiUnlocks.WIFI_HAKI_ARMAMENT
                && HakiClientState.observation >= HakiUnlocks.WIFI_HAKI_OBSERVATION
                && HakiClientState.conqueror >= HakiUnlocks.WIFI_HAKI_CONQUEROR;
        if (t == HakiTechnique.CONQ_CONVERGENCE) return HakiClientState.armament >= HakiUnlocks.GALAXY_FULL_ARMAMENT
                && HakiClientState.observation >= HakiUnlocks.GALAXY_FULL_OBSERVATION
                && HakiClientState.conqueror >= HakiUnlocks.GALAXY_FULL_CONQUEROR
                && HakiClientState.legend >= HakiUnlocks.GALAXY_FULL_LEGEND;
        if (t == HakiTechnique.CONQ_ACOC) return HakiClientState.armament >= HakiUnlocks.ADVANCED_HAKI_ARMAMENT
                && HakiClientState.conqueror >= HakiUnlocks.ADVANCED_HAKI_CONQUEROR;
        if (t == HakiTechnique.CONQ_JOYBOY) return HakiClientState.joyBoy;
        return t.unlocked(mastery);
    }

    private static String requirement(HakiTechnique t) {
        if (t == HakiTechnique.CONQ_AWAKENING) return "ARM 240 + OBS 240";
        if (t == HakiTechnique.CONQ_DOMINION) return "ARM 340 + OBS 300 + HAO 320";
        if (t == HakiTechnique.CONQ_SOVEREIGN) return "ARM 450 + OBS 450 + HAO 500";
        if (t == HakiTechnique.CONQ_CONVERGENCE) return "ARM 650 + OBS 550 + HAO 650 + L20";
        if (t == HakiTechnique.CONQ_ACOC) return "ARM 850 + HAO 850";
        if (t == HakiTechnique.CONQ_JOYBOY) return "ALL 1000 + L120";
        if (t == HakiTechnique.CONQ_PINNACLE) return "HAO 1000 · FINAL PASSIVE";
        return "Mastery " + t.mastery;
    }

    private static String compactRequirement(HakiTechnique t) {
        if (t == HakiTechnique.CONQ_AWAKENING) return "ARM240 / OBS240";
        if (t == HakiTechnique.CONQ_DOMINION) return "340 / 300 / 320";
        if (t == HakiTechnique.CONQ_SOVEREIGN) return "450 / 450 / 500";
        if (t == HakiTechnique.CONQ_CONVERGENCE) return "650 / 550 / 650 + L20";
        if (t == HakiTechnique.CONQ_ACOC) return "ARM850 / HAO850";
        if (t == HakiTechnique.CONQ_JOYBOY) return "ALL1000 + L120";
        if (t == HakiTechnique.CONQ_PINNACLE) return "HAO1000 · FINAL";
        return "Mastery " + t.mastery;
    }

    private static HakiTechnique nextTechnique(HakiType type, int mastery) {
        for (HakiTechnique t : HakiTechnique.forType(type)) if (!unlocked(t, mastery)) return t;
        return null;
    }

    private static String trainingHint(HakiType type, int mastery) {
        return switch (type) {
            case ARMAMENT -> "Land coated hits and use Blade Cuts, Haki Leap, the Haki Grip and Galaxy attacks successfully.";
            case OBSERVATION -> "Explore, survive, read danger, forecast movement/attacks, auto-dodge projectiles and maintain accurate WiFi Haki locks.";
            case CONQUEROR -> HakiClientState.armament < HakiUnlocks.CONQUEROR_AWAKEN_ARMAMENT
                    || HakiClientState.observation < HakiUnlocks.CONQUEROR_AWAKEN_OBSERVATION
                    ? "Dormant until ARM 240 + OBS 240."
                    : "Release King's pressure and land the Haki Grip, WiFi Haki and Galaxy techniques. Successful use grants HAO mastery.";
        };
    }

    private static float rankProgress(int mastery, HakiRank rank) {
        int start = rank.min, end = rank.nextMin();
        return end <= start ? 1f : (mastery - start) / (float)(end - start);
    }
}
