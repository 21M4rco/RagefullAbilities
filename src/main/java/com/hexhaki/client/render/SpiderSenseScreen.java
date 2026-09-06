package com.hexhaki.client.render;

import com.hexhaki.HexHaki;
import com.hexhaki.network.msg.S2CPerception;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Observation's danger sense: the warning a Haki user feels a moment before they are hit.
 *
 * <p>Built entirely from the {@link S2CPerception.ThreatCue} data the server already sends, so it
 * costs no extra protocol. Three layers, deliberately readable at a glance:
 *
 * <ul>
 *   <li><b>Direction.</b> A chevron sits at the screen edge in the direction the threat is coming
 *       from -- including from behind, which is the case the outline renderers cannot help
 *       with.</li>
 *   <li><b>Urgency.</b> The chevron pulses faster and brighter as the impact tick approaches, so
 *       the player can time a dodge rather than merely knowing something is out there.</li>
 *   <li><b>Imminence.</b> Inside the last few ticks a hard rim flash fires, which is the actual
 *       "move now" signal.</li>
 * </ul>
 *
 * <p>Everything is drawn with plain fills at a handful of draw calls; nothing here allocates on a
 * frame where no threat is present.
 */
@Mod.EventBusSubscriber(modid = HexHaki.MODID, value = Dist.CLIENT)
public final class SpiderSenseScreen {

    private record Warning(double x, double y, double z, float urgency, int impactTick, long bornMs) {}

    private static final List<Warning> ACTIVE = new ArrayList<>();
    private static final long LIFETIME_MS = 1400L;

    private SpiderSenseScreen() {}

    /** Fed from the perception snapshot; replaces the previous set rather than accumulating. */
    public static void accept(List<S2CPerception.ThreatCue> threats) {
        ACTIVE.clear();
        if (threats == null || threats.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (S2CPerception.ThreatCue cue : threats) {
            ACTIVE.add(new Warning(cue.x(), cue.y(), cue.z(),
                    Mth.clamp(cue.urgency(), 0f, 1f), cue.ticksToImpact(), now));
        }
    }

    /** Raises a single warning from outside the perception snapshot (modded damage fallback). */
    public static void warn(double x, double y, double z, float urgency, int ticksToImpact) {
        ACTIVE.add(new Warning(x, y, z, Mth.clamp(urgency, 0f, 1f), ticksToImpact,
                System.currentTimeMillis()));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(w -> now - w.bornMs() > LIFETIME_MS);
    }

    @SubscribeEvent
    public static void overlay(RenderGuiOverlayEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int cx = w / 2, cy = h / 2;
        long now = System.currentTimeMillis();

        // Camera basis, flattened: the chevron only needs a bearing on screen.
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double rx = -fz, rz = fx;

        float peak = 0f;
        for (Warning warn : ACTIVE) {
            float age = (now - warn.bornMs()) / (float) LIFETIME_MS;
            if (age >= 1f) continue;
            float fade = 1f - age;

            Vec3 delta = new Vec3(warn.x() - mc.player.getX(), 0, warn.z() - mc.player.getZ());
            if (delta.lengthSqr() < 1.0E-6) continue;
            delta = delta.normalize();
            double forward = delta.x * fx + delta.z * fz;
            double side = delta.x * rx + delta.z * rz;
            // screen bearing: 0 = dead ahead, +/-PI = directly behind
            double bearing = Math.atan2(side, forward);

            // Urgency climbs as the impact tick approaches.
            float closeness = 1f - Mth.clamp(warn.impactTick() / 20f, 0f, 1f);
            float urgency = Mth.clamp(warn.urgency() * .5f + closeness * .5f, 0f, 1f);
            peak = Math.max(peak, urgency * fade);

            // Pulse rate scales with urgency: a distant threat breathes, an imminent one strobes.
            float pulse = .55f + .45f * Mth.sin((now % 100000L) * (.004f + .020f * urgency));
            int alpha = (int) (Mth.clamp((.30f + .70f * urgency) * fade * pulse, 0f, 1f) * 255f);
            if (alpha < 8) continue;
            int colour = (alpha << 24) | (urgency > .66f ? 0xFF4038 : (urgency > .33f ? 0xFFA040 : 0xFFD880));

            // Place the chevron on an ellipse just inside the screen edge.
            double px = cx + Math.sin(bearing) * (w * .40);
            double py = cy - Math.cos(bearing) * (h * .34);
            chevron(g, (int) px, (int) py, bearing, 7 + (int) (urgency * 6f), colour);
        }

        // Imminent-impact rim flash: the actual "move now" cue.
        if (peak > .72f) {
            int rim = (int) (Mth.clamp((peak - .72f) / .28f, 0f, 1f) * 120f);
            int colour = (rim << 24) | 0xFF3830;
            int band = 3;
            g.fill(0, 0, w, band, colour);
            g.fill(0, h - band, w, h, colour);
            g.fill(0, 0, band, h, colour);
            g.fill(w - band, 0, w, h, colour);
        }
    }

    /** A small solid arrowhead pointing away from the player, built from stacked fills so it
     *  needs no texture and no matrix work. */
    private static void chevron(GuiGraphics g, int x, int y, double bearing, int size, int colour) {
        // Bias the rows along the bearing so the wedge visibly points outward.
        int dx = (int) Math.round(Math.sin(bearing));
        int dy = (int) Math.round(-Math.cos(bearing));
        for (int i = 0; i < size; i++) {
            int half = size - i;
            int ox = x + dx * i;
            int oy = y + dy * i;
            g.fill(ox - half, oy - 1, ox + half, oy + 1, colour);
        }
    }
}
