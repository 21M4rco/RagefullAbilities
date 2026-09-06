package com.hexhaki.client.render;

import com.hexhaki.HexHaki;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Participant-only anime impact frame for King's Grip.
 *
 * The server triggers this on the exact scripted gut-punch tick for the attacker and, when the
 * victim is another player, for the victim as well.  It is intentionally short (five ticks): the
 * authored animation is already holding the contact pose for four ticks before release, so a
 * black/white flash over those frames reads like a genuine hit-stop without desynchronising the
 * server-side grab timeline.
 */
@Mod.EventBusSubscriber(modid = HexHaki.MODID, value = Dist.CLIENT)
public final class KingsGripImpactFrame {
    private static final int FRAME_TICKS = 5;
    private static int ticks;
    private static int tier;
    private static long seed;

    private KingsGripImpactFrame() {}

    public static void trigger(int hakiTier, long impactSeed) {
        ticks = FRAME_TICKS;
        tier = Mth.clamp(hakiTier, 0, 2);
        seed = impactSeed;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ticks <= 0) return;
        ticks--;
    }

    @SubscribeEvent
    public static void overlay(RenderGuiOverlayEvent.Post event) {
        if (ticks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int age = FRAME_TICKS - ticks;

        // Anime impact-frame rhythm: WHITE -> BLACK -> WHITE -> quick monochrome decay.
        int background;
        int ray;
        float rayAlpha;
        switch (age) {
            case 0 -> { background = 0xF4FFFFFF; ray = 0xF5000000; rayAlpha = 1.00f; }
            case 1 -> { background = 0xF1000000; ray = 0xF8FFFFFF; rayAlpha = 1.00f; }
            case 2 -> { background = 0xC9FFFFFF; ray = 0xE8000000; rayAlpha = .92f; }
            case 3 -> { background = 0x78000000; ray = 0xB8FFFFFF; rayAlpha = .68f; }
            default -> { background = 0x36FFFFFF; ray = 0x74000000; rayAlpha = .38f; }
        }
        g.fill(0, 0, w, h, background);

        // Hard black/white speed cuts converge on the punch contact area. They are deterministic for
        // both participants because both receive the same seed from the authoritative impact tick.
        Random random = new Random(seed ^ (0x9E3779B97F4A7C15L + age * 0x632BE59BD9B4E019L));
        int cx = w / 2;
        int cy = (int)(h * .51f);
        int rays = 16 + tier * 5;
        for (int i = 0; i < rays; i++) {
            float angle = (360f / rays) * i + (random.nextFloat() - .5f) * 12f;
            int width = 2 + random.nextInt(4 + tier * 2);
            int inner = 24 + random.nextInt(34);
            int length = (int)(Math.max(w, h) * (.45f + random.nextFloat() * .32f));
            int alpha = (int)(((ray >>> 24) & 255) * rayAlpha);
            int color = (alpha << 24) | (ray & 0x00FFFFFF);

            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
            g.fill(inner, -width, inner + length, width, color);
            g.pose().popPose();
        }

        // Advanced Haki gets a hotter white contact core, but still only black/white during the frame.
        int core = 15 + tier * 5;
        int coreAlpha = age <= 2 ? 230 : 100;
        g.fill(cx - core, cy - core, cx + core, cy + core, (coreAlpha << 24) | 0x00FFFFFF);
    }
}
