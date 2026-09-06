package com.hexhaki.client.render;

import com.hexhaki.HexHaki;
import com.hexhaki.network.msg.S2CConquerorShock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What Conqueror's Haki does to a victim who survives it.
 *
 * <p>Two things run at once and they are deliberately different in character:
 *
 * <ul>
 *   <li><b>Spasm.</b> The view twitches uncontrollably. This is not the smooth sinusoidal shake
 *       used for impacts -- it is a stepped, high-frequency jitter that re-randomises several
 *       times a second, so the victim cannot track anything or aim through it.</li>
 *   <li><b>Sight failure.</b> A dark vignette closes inward from the edges and reaches total
 *       blackout at {@code blindAt}, then holds before receding. It ramps rather than snapping,
 *       so the victim can watch their own sight go.</li>
 * </ul>
 *
 * <p>Both scale with the caster's charge, so a tap is a brief stagger and a full hold is a
 * genuine sensory shutdown.
 */
@Mod.EventBusSubscriber(modid = HexHaki.MODID, value = Dist.CLIENT)
public final class ConquerorShockScreen {

    private static int ticks;
    private static int duration;
    private static int blindAt;
    private static float power;

    private ConquerorShockScreen() {}

    public static void begin(S2CConquerorShock m) {
        duration = Math.max(1, m.ticks());
        ticks = duration;
        blindAt = Math.max(1, m.blindAt());
        power = Mth.clamp(m.power(), 0f, 1f);
    }

    public static void clear() {
        ticks = 0; duration = 0; power = 0f;
    }

    private static int age() {
        return duration - ticks;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ticks <= 0) return;
        if (--ticks <= 0) clear();
    }

    /** Stepped jitter: re-randomised on a fast integer clock rather than interpolated, because a
     *  smooth wobble reads as a camera effect while a stepped one reads as losing control. */
    private static float jitter(long step, int salt) {
        long h = step * 0x9E3779B97F4A7C15L + salt * 0x632BE59BD9B4E019L;
        h ^= (h >>> 29); h *= 0xBF58476D1CE4E5B9L; h ^= (h >>> 32);
        return ((h & 0xFFFF) / 65535f) * 2f - 1f;
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (ticks <= 0 || power <= 0f) return;
        int age = age();
        // strongest immediately, easing off across the effect
        float decay = 1f - Mth.clamp(age / (float) duration, 0f, 1f);
        float amount = power * decay;
        double t = age + event.getPartialTick();
        long fast = (long) (t * 5.0);      // ~5 re-randomisations per second
        long slow = (long) (t * 1.7);

        event.setYaw(event.getYaw() + (jitter(fast, 1) * 7.5f + jitter(slow, 4) * 3.0f) * amount);
        event.setPitch(Mth.clamp(event.getPitch()
                + (jitter(fast, 2) * 5.5f + jitter(slow, 5) * 2.2f) * amount, -90f, 90f));
        event.setRoll(event.getRoll() + (jitter(fast, 3) * 9.0f + jitter(slow, 6) * 3.5f) * amount);
    }

    @SubscribeEvent
    public static void overlay(RenderGuiOverlayEvent.Post event) {
        if (ticks <= 0 || power <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        int age = age();

        // Sight closes in, blacks out at blindAt, then recedes over the remainder.
        float blind;
        if (age <= blindAt) blind = Mth.clamp(age / (float) blindAt, 0f, 1f);
        else blind = Mth.clamp(1f - (age - blindAt) / (float) Math.max(1, duration - blindAt), 0f, 1f);
        blind *= power;
        if (blind <= .004f) return;

        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // Concentric bands closing from the edges: cheap (a handful of fills) and reads as
        // tunnel vision rather than a flat screen tint.
        final int BANDS = 7;
        for (int i = 0; i < BANDS; i++) {
            float k = (i + 1) / (float) BANDS;
            // inner edge of this band pulls toward the centre as blind rises
            float inset = (1f - k) * (1f - blind * .96f);
            int x = (int) (w * .5f * inset);
            int y = (int) (h * .5f * inset);
            int alpha = (int) (Mth.clamp(blind * k * 1.15f, 0f, 1f) * 255f);
            if (alpha <= 2) continue;
            int color = alpha << 24;
            g.fill(0, 0, w, y, color);            // top
            g.fill(0, h - y, w, h, color);        // bottom
            g.fill(0, y, x, h - y, color);        // left
            g.fill(w - x, y, w, h - y, color);    // right
        }
        if (blind > .97f) g.fill(0, 0, w, h, 0xFF000000);
    }
}
