package com.hexhaki.client;

import com.hexhaki.HexHaki;
import com.hexhaki.data.HakiRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Compact Haki status strip.
 *
 * <p>The original HUD was an eight-card ability deck rebuilt from scratch every frame: eight
 * record allocations, eight {@code getTranslatedKeyMessage().getString()} calls, three
 * {@code HakiRank.name().replace()} allocations, ~40 {@code fill} calls and ~30 {@code drawString}
 * calls <b>each wrapped in its own pushPose/scale</b>, so no two strings could batch. That is the
 * lag the panel was reported for.
 *
 * <p>This keeps the cheap structure -- cached strings, a single pushPose, bars instead of cards --
 * and spends the headroom on readability instead:
 *
 * <ul>
 *   <li>a bevelled frame with a status stripe down the left edge, tinted by whatever is active,
 *       so the panel's state is legible from peripheral vision alone;</li>
 *   <li>bars that ease toward their target instead of snapping, with a ghost trail showing the
 *       value they just left -- damage and drain become visible rather than instant;</li>
 *   <li>rank pips on each mastery bar, so progress toward the next rank is readable at a glance
 *       without spelling out three rank names;</li>
 *   <li>an energy bar that reddens and pulses under a quarter full;</li>
 *   <li>a charge bar that only exists while something is charging, and takes the panel's accent
 *       with it.</li>
 * </ul>
 *
 * <p>Steady-state cost is ~26 fills and 6 strings under one matrix, and a frame that changes
 * nothing allocates nothing. The full ability list stays in the Mastery Log, which is where
 * reference information belongs.
 */
@Mod.EventBusSubscriber(modid = HexHaki.MODID, value = Dist.CLIENT)
public final class HakiHud {

    private static final int PANEL_TOP = 0xD216100E;
    private static final int PANEL_BOT = 0xE60A0706;
    private static final int EDGE = 0xFF6E5330;
    private static final int EDGE_SOFT = 0x40FFD9A0;
    private static final int GOLD = 0xFFE3C47A;
    private static final int TEXT = 0xFFF0E4CE;
    private static final int MUTED = 0xFF8E8072;
    private static final int DIM = 0xFF5B5048;
    private static final int RED = 0xFFE23A46;
    private static final int LOW = 0xFFFF6A4A;
    private static final int BLUE = 0xFF6FB8E8;
    private static final int PINK = 0xFFFFC0C7;
    private static final int TRACK = 0xFF241A16;
    private static final int GHOST = 0x66FFFFFF;

    private static final int W = 112;
    private static final int H = 50;
    private static final float SCALE = 0.75f;
    private static final int BAR_X = 46;
    private static final int BAR_W = W - 4 - BAR_X;

    // ---- cached strings; rebuilt only when the underlying value changes -------------------
    private static int cachedArm = -1, cachedObs = -1, cachedHao = -1;
    private static int cachedEnergy = -1, cachedMaxEnergy = -1, cachedCharge = -1;
    private static String armText = "", obsText = "", haoText = "", energyText = "", chargeText = "";
    private static String armRank = "";
    private static boolean chargeIsKing;

    // ---- eased bar values; the ghost lags further behind so a drop leaves a visible trail ---
    private static float easedEnergy = -1f, ghostEnergy;
    private static float easedArm, easedObs, easedHao, easedCharge;
    private static long lastFrameNanos;

    private HakiHud() {}

    @SubscribeEvent
    public static void draw(RenderGuiOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null
                || !HakiClientState.enabled || !HakiClientState.boardEnabled) return;

        refreshCache();
        float dt = frameDelta();

        GuiGraphics g = e.getGuiGraphics();
        int sh = mc.getWindow().getGuiScaledHeight();
        // Positioned in unscaled space, then the whole panel is drawn inside a single scale.
        int x = Math.round(8 / SCALE);
        int y = Math.round((sh - 8) / SCALE) - H;

        float energyFraction = HakiClientState.energy / Math.max(1f, HakiClientState.maxEnergy);
        boolean lowEnergy = energyFraction < .25f;
        int accent = accentColor();

        easedEnergy = ease(easedEnergy, energyFraction, dt, 9f);
        ghostEnergy = Math.max(easedEnergy, ease(ghostEnergy, energyFraction, dt, 2.2f));
        easedArm = ease(easedArm, HakiClientState.armament / 1000f, dt, 7f);
        easedObs = ease(easedObs, HakiClientState.observation / 1000f, dt, 7f);
        easedHao = ease(easedHao, HakiClientState.conqueror / 1000f, dt, 7f);
        easedCharge = ease(easedCharge, cachedCharge / 100f, dt, 12f);

        g.pose().pushPose();
        g.pose().scale(SCALE, SCALE, 1f);

        // Frame: gradient body, hairline top/bottom rules, and a status stripe down the left
        // edge tinted by whatever is currently active.
        g.fillGradient(x, y, x + W, y + H, PANEL_TOP, PANEL_BOT);
        g.fill(x, y, x + W, y + 1, EDGE);
        g.fill(x, y + H - 1, x + W, y + H, EDGE_SOFT);
        g.fill(x, y + 1, x + 2, y + H - 1, accent);

        // Header: identity on the left, energy on the right.
        g.drawString(mc.font, HakiClientState.joyBoy ? "JOY BOY" : "HEXHAKI",
                x + 6, y + 4, HakiClientState.joyBoy ? RED : GOLD, false);
        g.drawString(mc.font, energyText,
                x + W - 4 - mc.font.width(energyText), y + 4, lowEnergy ? LOW : TEXT, false);

        int energyColor = lowEnergy ? LOW : GOLD;
        if (lowEnergy && (System.currentTimeMillis() / 220 & 1L) == 0L) energyColor = 0xFFFFB199;
        g.fill(x + 6, y + 14, x + W - 4, y + 18, TRACK);
        bar(g, x + 6, y + 14, W - 10, 4, ghostEnergy, GHOST);
        bar(g, x + 6, y + 14, W - 10, 4, easedEnergy, energyColor);

        // Three mastery bars. Only Armament spells out its rank name -- the pips carry the rest.
        mastery(g, mc, x, y + 23, armText, easedArm, HakiClientState.armament,
                HakiClientState.armamentOn ? TEXT : MUTED, HakiClientState.armamentOn);
        mastery(g, mc, x, y + 31, obsText, easedObs, HakiClientState.observation,
                HakiClientState.observationOn ? BLUE : MUTED, HakiClientState.observationOn);
        mastery(g, mc, x, y + 39, haoText, easedHao, HakiClientState.conqueror,
                HakiClientState.acocOn || HakiClientState.dominionOn ? RED : MUTED,
                HakiClientState.acocOn || HakiClientState.dominionOn);

        // The charge readout takes over the bottom row only while something is charging.
        if (cachedCharge > 0) {
            int color = chargeIsKing ? RED : PINK;
            g.fill(x + 2, y + H - 4, x + W, y + H - 1, TRACK);
            bar(g, x + 2, y + H - 4, W - 2, 3, easedCharge, color);
            g.drawString(mc.font, chargeText,
                    x + W - 4 - mc.font.width(chargeText), y + 39, color, false);
        } else if (!armRank.isEmpty()) {
            g.drawString(mc.font, armRank, x + W - 4 - mc.font.width(armRank), y + 23, MUTED, false);
        }

        g.pose().popPose();
    }

    /** Left-edge stripe colour: the strongest thing currently running. */
    private static int accentColor() {
        if (HakiClientState.conquerorCharge > 0 || HakiClientState.acocOn
                || HakiClientState.dominionOn) return RED;
        if (HakiClientState.convergenceCharge > 0) return PINK;
        if (HakiClientState.armamentOn) return GOLD;
        if (HakiClientState.observationOn) return BLUE;
        return DIM;
    }

    /** Rebuilds display strings only on change, so a steady frame allocates nothing. */
    private static void refreshCache() {
        if (HakiClientState.armament != cachedArm) {
            cachedArm = HakiClientState.armament;
            armText = "ARM " + cachedArm;
            armRank = HakiRank.of(cachedArm).name().replace('_', ' ');
        }
        if (HakiClientState.observation != cachedObs) {
            cachedObs = HakiClientState.observation;
            obsText = "OBS " + cachedObs;
        }
        if (HakiClientState.conqueror != cachedHao) {
            cachedHao = HakiClientState.conqueror;
            haoText = "HAO " + cachedHao;
        }
        int energy = Math.round(HakiClientState.energy);
        int maxEnergy = Math.round(HakiClientState.maxEnergy);
        if (energy != cachedEnergy || maxEnergy != cachedMaxEnergy) {
            cachedEnergy = energy;
            cachedMaxEnergy = maxEnergy;
            energyText = energy + "/" + maxEnergy;
        }
        int charge = Math.min(100, Math.max(HakiClientState.conquerorCharge,
                HakiClientState.convergenceCharge));
        boolean king = HakiClientState.conquerorCharge >= HakiClientState.convergenceCharge;
        if (charge != cachedCharge || king != chargeIsKing) {
            cachedCharge = charge;
            chargeIsKing = king;
            chargeText = charge > 0 ? (king ? "KING " : "GALAXY ") + charge + "%" : "";
        }
    }

    /** Seconds since the last HUD frame, clamped so an alt-tab does not snap every bar. */
    private static float frameDelta() {
        long now = System.nanoTime();
        long previous = lastFrameNanos;
        lastFrameNanos = now;
        if (previous == 0L) return 1f / 60f;
        return Mth.clamp((now - previous) / 1_000_000_000f, 0f, .25f);
    }

    /** Frame-rate independent exponential approach; -1 seeds straight to the target. */
    private static float ease(float current, float target, float dt, float rate) {
        if (current < 0f) return target;
        return current + (target - current) * (1f - (float) Math.exp(-rate * dt));
    }

    private static void mastery(GuiGraphics g, Minecraft mc, int x, int y, String label,
                                float eased, int value, int color, boolean active) {
        g.drawString(mc.font, label, x + 6, y, color, false);
        int barX = x + BAR_X;
        g.fill(barX, y + 2, barX + BAR_W, y + 5, TRACK);
        bar(g, barX, y + 2, BAR_W, 3, eased, color);
        // Rank pips: one notch per rank threshold, lit up to the rank actually held.
        HakiRank held = HakiRank.of(value);
        for (HakiRank rank : HakiRank.values()) {
            if (rank.min <= 0) continue;
            int px = barX + Math.round(BAR_W * (rank.min / 1000f));
            g.fill(px, y + 1, px + 1, y + 6, rank.min <= value ? color : TRACK);
        }
        if (active) g.fill(x + 3, y + 2, x + 5, y + 5, color);
        if (held == HakiRank.PINNACLE) g.fill(barX, y + 6, barX + BAR_W, y + 7, color);
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        int fill = Math.round(w * Mth.clamp(fraction, 0f, 1f));
        if (fill > 0) g.fill(x, y, x + fill, y + h, color);
    }
}
