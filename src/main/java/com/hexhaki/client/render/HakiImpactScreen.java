package com.hexhaki.client.render;

import com.hexhaki.HexHaki;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Screen-space impact response shared by the caster and nearby viewers. */
@Mod.EventBusSubscriber(modid = HexHaki.MODID, value = Dist.CLIENT)
public final class HakiImpactScreen {
    private static int ticks;
    private static int duration;
    private static float strength;

    private HakiImpactScreen() {}

    public static void supreme(Entity source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || source == null) return;
        double distance = mc.player.distanceTo(source);
        if (distance > 72.0) return;
        float falloff = (float) Mth.clamp(1.0 - distance / 72.0, 0.0, 1.0);
        if (source.getId() == mc.player.getId()) falloff = 1.0f;
        duration = 32;
        ticks = duration;
        strength = Math.max(strength, 0.30f + falloff * 0.70f);
    }

    /**
     * Long-range ground quake for the Last Stand detonation.
     *
     * <p>{@link #supreme} and {@link #impact} both cap out well inside a hundred blocks, and both
     * decay in a dozen ticks. This is a separate, slower, much wider tremor: everyone within
     * {@code range} feels the floor move, falling off with distance but never snapping off.
     */
    public static void quake(Entity source, float amount, int durationTicks, double range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || source == null) return;
        double distance = mc.player.distanceTo(source);
        if (distance > range) return;
        float falloff = (float) Mth.clamp(1.0 - distance / range, 0.0, 1.0);
        // squared falloff so the epicentre is violent and the rim is a rumble
        falloff *= falloff;
        if (source.getId() == mc.player.getId()) falloff = 1.0f;
        duration = Math.max(duration, durationTicks);
        ticks = Math.max(ticks, durationTicks);
        strength = Math.max(strength, amount * (0.18f + 0.82f * falloff));
    }

    /**
     * The same quake addressed to a point instead of an entity.
     *
     * <p>An explosion is a place, not a body -- and the body that caused this one has usually just
     * been driven into the floor, so keying the tremor off it is exactly where it goes missing.
     */
    public static void quakeAt(double x, double y, double z, float amount, int durationTicks, double range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double distance = Math.sqrt(mc.player.distanceToSqr(x, y, z));
        if (distance > range) return;
        float falloff = (float) Mth.clamp(1.0 - distance / range, 0.0, 1.0);
        falloff *= falloff;
        duration = Math.max(duration, durationTicks);
        ticks = Math.max(ticks, durationTicks);
        strength = Math.max(strength, amount * (0.18f + 0.82f * falloff));
    }

    public static void impact(Entity source, float amount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || source == null) return;
        double distance = mc.player.distanceTo(source);
        if (distance > 34.0) return;
        float falloff = (float) Mth.clamp(1.0 - distance / 34.0, 0.0, 1.0);
        duration = Math.max(duration, 12);
        ticks = Math.max(ticks, 12);
        strength = Math.max(strength, amount * falloff);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ticks <= 0) return;
        ticks--;
        if (ticks <= 0) {
            duration = 0;
            strength = 0;
        }
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (ticks <= 0 || strength <= 0) return;
        float progress = duration <= 0 ? 1f : 1f - ticks / (float) duration;
        float decay = (1f - progress) * (1f - progress);
        double time = (duration - ticks + event.getPartialTick()) * 5.7;
        float kick = strength * decay;
        event.setYaw(event.getYaw() + (float) Math.sin(time * 1.31) * 2.4f * kick);
        event.setPitch(event.getPitch() + (float) Math.sin(time * 1.77 + 1.2) * 1.55f * kick);
        event.setRoll(event.getRoll() + (float) Math.sin(time * .93 + 2.7) * 2.9f * kick);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (ticks <= 0 || strength <= 0) return;
        int age = duration - ticks;
        // A fast outward lens punch, then a small rebound.
        double pulse = Math.exp(-age * .24) * Math.sin(age * .62);
        event.setFOV(event.getFOV() + pulse * 7.0 * strength);
    }

    @SubscribeEvent
    public static void overlay(RenderGuiOverlayEvent.Post event) {
        if (ticks <= 0 || strength <= 0 || duration < 20) return;
        int age = duration - ticks;
        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        if (age <= 2) {
            int alpha = (int) (Mth.clamp((3 - age) / 3f, 0f, 1f) * 150f * strength);
            graphics.fill(0, 0, w, h, (alpha << 24) | 0xFFF5F5);
        } else if (age <= 10) {
            float fade = 1f - (age - 2) / 8f;
            int alpha = (int) (Mth.clamp(fade, 0f, 1f) * 42f * strength);
            graphics.fill(0, 0, w, h, (alpha << 24) | 0x5A0710);
        }
    }
}
