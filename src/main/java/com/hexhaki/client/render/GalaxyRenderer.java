package com.hexhaki.client.render;

import com.hexhaki.HexHaki;
import com.hexhaki.client.vfx.HakiFx;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Show-accurate Galaxy Impact presentation.
 *
 * <p>In the source material the technique is not a glow on the fist: an entire spiral galaxy
 * manifests <b>above and behind</b> the user while they wind up, then collapses into the punch.
 * The previous implementation deliberately went the other way ("the Haki is on the fist from frame
 * one, never an overhead galaxy"), which is why the move never read as Galaxy Impact.
 *
 * <p>The galaxy is drawn as real world-space geometry rather than as particles, because a spiral
 * is a *structure*: no amount of billboarded sprites will produce arms, a bulge and dust lanes.
 * Three counter-rotating textured discs give it depth and parallax, a camera-facing core flare
 * gives it a nucleus, and spiral inflow ribbons connect the rim to the charging fist so the
 * absorption is legible.
 *
 * <p>Each disc is billboarded to the camera and then squashed along its vertical axis, so every
 * viewer sees the spiral face rather than an edge, while the foreshortening keeps it reading as a
 * disc in space instead of a flat sticker. The sprite content rotates inside that fixed ellipse,
 * which is exactly how a spinning inclined disc behaves.
 *
 * <p>Timing is derived locally from the server's fixed charge timeline (see
 * {@code GALAXY_READY_TICK}), so no new packet is required and every viewer stays in step.
 */
@Mod.EventBusSubscriber(modid = HexHaki.MODID, value = Dist.CLIENT)
public final class GalaxyRenderer {

    /** Matches HakiServerController.GALAXY_READY_TICK. */
    private static final int READY_TICK = 88;
    /** Ticks the collapse-into-the-fist beat lasts once the punch is thrown. */
    /** After the punch the galaxy stays put and dissolves; it is not thrown with the fist. */
    private static final int LINGER_TICKS = 70;


    private static final float DISC_TILT_DEGREES = 58f;

    private static final Map<Integer, Galaxy> ACTIVE = new HashMap<>();

    private GalaxyRenderer() {}

    private static final class Galaxy {
        boolean advanced;
        boolean coated;
        float age;          // ticks since the charge began
        float spin;         // accumulated disc rotation, degrees
        int linger = -1;    // >=0 once the punch has been thrown; counts out the slow fade

        Galaxy(boolean coated, boolean advanced) {
            this.coated = coated;
            this.advanced = advanced;
        }

        /** 0..1 charge ramp; holds at 1 while the player hovers waiting to punch. */
        float charge() {
            return Mth.clamp(age / READY_TICK, 0f, 1f);
        }
    }

    // ----------------------------------------------------------------------------------
    // lifecycle, driven from HakiVfx's existing GALAXY_FIST_* protocol
    // ----------------------------------------------------------------------------------

    public static void begin(int entityId, boolean coated, boolean advanced) {
        ACTIVE.put(entityId, new Galaxy(coated, advanced));
    }

    public static void intensify(int entityId, boolean coated, boolean advanced) {
        Galaxy g = ACTIVE.get(entityId);
        if (g == null) begin(entityId, coated, advanced);
        else {
            g.coated = coated;
            g.advanced = advanced;
        }
    }

    /**
     * The punch was thrown.
     *
     * <p>The galaxy is deliberately <b>not</b> dragged along with the projectile: it stays where
     * it formed and dissolves over {@value #LINGER_TICKS} ticks, so the sky keeps the afterimage
     * of what was just fired while the strike travels.
     */
    public static void collapse(int entityId) {
        Galaxy g = ACTIVE.get(entityId);
        if (g != null && g.linger < 0) g.linger = 0;
    }

    /** Cancelled charge: dissolve through the same collapse beat rather than snapping off. */
    public static void stop(int entityId) {
        collapse(entityId);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static boolean isActive(int entityId) {
        return ACTIVE.containsKey(entityId);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Galaxy>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Galaxy> entry = it.next();
            Entity entity = mc.level.getEntity(entry.getKey());
            Galaxy g = entry.getValue();
            if (entity == null || entity.isRemoved()) {
                it.remove();
                continue;
            }
            g.age++;
            // Spin accelerates as the galaxy compresses; the collapse whips it round hard.
            // The spin winds DOWN as it dissolves rather than whipping round, which reads as the
            // structure losing cohesion instead of being sucked away.
            float rate = .55f + 1.35f * g.charge() + (g.advanced ? .45f : 0f);
            if (g.linger >= 0) rate *= Math.max(.15f, 1f - g.linger / (float) LINGER_TICKS);
            g.spin += rate;

            if (g.linger >= 0) {
                g.linger++;
                if (g.linger > LINGER_TICKS) it.remove();
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // rendering
    // ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float partial = event.getPartialTick();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        for (Map.Entry<Integer, Galaxy> entry : ACTIVE.entrySet()) {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (entity == null) continue;
            drawGalaxy(event.getPoseStack(), camera, cameraPos, entity, entry.getValue(), partial);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void drawGalaxy(PoseStack pose, Camera camera, Vec3 cameraPos,
                                   Entity entity, Galaxy g, float partial) {
        float age = g.age + partial;
        float charge = Mth.clamp(age / READY_TICK, 0f, 1f);
        float ease = charge * charge * (3f - 2f * charge);

        // Dissolve envelope. Eased so it holds bright for a beat, then fades away.
        float lingerT = g.linger < 0 ? 0f
                : Mth.clamp((g.linger + partial) / LINGER_TICKS, 0f, 1f);
        float dissolve = lingerT * lingerT;

        Vec3 anchor = new Vec3(
                Mth.lerp(partial, entity.xo, entity.getX()),
                Mth.lerp(partial, entity.yo, entity.getY()),
                Mth.lerp(partial, entity.zo, entity.getZ()));

        // The fist the galaxy is being drawn into. Matches HakiVfx's charge-anchor offset.
        double yaw = Math.toRadians(entity.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 fist = anchor
                .add(right.scale(.34))
                .add(forward.scale(-.34))
                .add(0, entity.getBbHeight() + .42, 0);

        // Advanced is a different order of magnitude, not a slightly bigger version. Every tier
        // was then roughly doubled again: the spiral is meant to be the thing the whole fight
        // looks up at, and at the old sizes an un-coated hold sat at ten blocks across, which
        // reads as an effect above the player rather than a galaxy over the field.
        float tier = g.advanced ? 4.20f : (g.coated ? 2.35f : 1.65f);
        ResourceLocation disc = g.advanced ? HakiFx.GALAXY_DISC : HakiFx.GALAXY_DISC_RED;
        // violet/pink for Advanced; blood red for the coated and un-coated tiers
        int armTint = g.advanced ? 0xFFD86CFF : 0xFFFF4038;
        int hazeTint = g.advanced ? 0x8CB44CFF : 0x7CFF2418;

        // Rises and grows as it charges, then is dragged down into the fist on release. Height
        // deliberately grows slower than radius: scaling both by the full tier factor pushed the
        // Advanced disc so far up that its own size no longer read from the ground.
        double height = (5.6 + 5.4 * ease) * (0.45 + 0.55 * tier);
        float radius = (3.4f + 10.6f * ease) * tier;
        Vec3 centre = fist.add(0, height, 0).add(forward.scale(-1.15 * tier));

        // It stays where it formed and only swells slightly as it loses cohesion.
        radius *= 1f + .12f * dissolve;

        // Brightness: builds in, flares at full charge, blows out white on collapse.
        float alpha = Mth.clamp(age / 14f, 0f, 1f);
        if (charge >= .995f) alpha *= 1f + .18f * (float) Math.sin(age * .42);
        alpha *= (1f - dissolve);
        // A brief flash on the release frame, then no white-out at all while it dissolves.
        float whiteOut = lingerT <= 0f ? 0f : Math.max(0f, 1f - lingerT * 6f);
        if (alpha <= .01f) return;

        Vec3 local = centre.subtract(cameraPos);
        Vec3 camRight = new Vec3(camera.getLeftVector().x, camera.getLeftVector().y, camera.getLeftVector().z).scale(-1);
        Vec3 camUp = new Vec3(camera.getUpVector().x, camera.getUpVector().y, camera.getUpVector().z);
        if (camRight.lengthSqr() < 1.0E-6 || camUp.lengthSqr() < 1.0E-6) return;
        camRight = camRight.normalize();
        camUp = camUp.normalize();

        // Inclination: squashing the vertical axis turns the billboard into a disc in space.
        float squash = Mth.cos(DISC_TILT_DEGREES * Mth.DEG_TO_RAD);

        Matrix4f matrix = pose.last().pose();

        // Outer nebula haze -- widest, faintest, counter-rotating.
        drawDisc(matrix, local, camRight, camUp, radius * 1.72f, squash * .92f,
                -g.spin * .38f, HakiFx.NEBULA, tint(hazeTint, alpha * .46f, whiteOut));

        // Extra depth: two counter-rotating ghosts instead of one, so the arms visibly pass
        // through each other and the disc reads as volume rather than a single flat plate.
        drawDisc(matrix, local, camRight, camUp, radius * 1.30f, squash,
                g.spin * .48f, disc, tint(armTint, alpha * .34f, whiteOut));
        drawDisc(matrix, local, camRight, camUp, radius * 1.09f, squash * .97f,
                -g.spin * .72f, disc, tint(0xFFFFFFFF, alpha * .30f, whiteOut));

        // Main galaxy.
        drawDisc(matrix, local, camRight, camUp, radius, squash,
                g.spin, disc, tint(0xFFFFFFFF, alpha * .98f, whiteOut));

        // Nucleus: camera-facing, unsquashed, pulsing with the charge.
        float corePulse = .18f + .10f * (float) Math.sin(age * .55) + .16f * ease;
        drawDisc(matrix, local, camRight, camUp, radius * corePulse, 1f,
                -g.spin * 1.7f, HakiFx.GALAXY_CORE, tint(0xFFFFFFFF, alpha, whiteOut));

        // Absorption: spiral ribbons running from the rim down into the fist.
        if (charge > .12f && lingerT <= 0f) {
            drawInflow(matrix, centre, fist, cameraPos, camRight, camUp,
                    radius, g.spin, alpha * Mth.clamp(charge * 1.4f, 0f, 1f), whiteOut,
                    g.advanced, armTint);
        }
    }

    /**
     * One textured disc quad, billboarded to the camera, squashed vertically and spun about its
     * own normal.  Rotating the corner offsets before squashing is what makes the arms turn
     * inside a stable ellipse instead of the whole shape wobbling.
     */
    private static void drawDisc(Matrix4f matrix, Vec3 centre, Vec3 camRight, Vec3 camUp,
                                 float radius, float squash, float spinDegrees,
                                 ResourceLocation texture, int argb) {
        if (radius <= .001f) return;
        float cos = Mth.cos(spinDegrees * Mth.DEG_TO_RAD);
        float sin = Mth.sin(spinDegrees * Mth.DEG_TO_RAD);

        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float a = ((argb >>> 24) & 255) / 255f;
        float r = ((argb >>> 16) & 255) / 255f;
        float g = ((argb >>> 8) & 255) / 255f;
        float b = (argb & 255) / 255f;

        // corner order must match the UV order below
        float[][] corners = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (float[] c : corners) {
            float cx = c[0] * radius;
            float cy = c[1] * radius;
            float rx = cx * cos - cy * sin;
            float ry = (cx * sin + cy * cos) * squash;
            Vec3 p = centre.add(camRight.scale(rx)).add(camUp.scale(ry));
            buf.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                    .uv(c[2], c[3])
                    .color(r, g, b, a)
                    .endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
    }

    /** Logarithmic-spiral ribbons feeding the disc's mass into the charging fist. */
    private static void drawInflow(Matrix4f matrix, Vec3 centre, Vec3 fist, Vec3 cameraPos,
                                   Vec3 camRight, Vec3 camUp, float radius, float spin,
                                   float alpha, float whiteOut, boolean advanced, int armTint) {
        final int ribbons = advanced ? 7 : 5;
        final int steps = 16;

        RenderSystem.setShaderTexture(0, HakiFx.ARC);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int ribbon = 0; ribbon < ribbons; ribbon++) {
            float phase = (float) (Math.PI * 2.0 * ribbon / ribbons) + spin * Mth.DEG_TO_RAD * .5f;
            Vec3 previous = null;
            float previousWidth = 0f;
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                // t=0 at the rim, t=1 at the fist; the arm tightens as it falls inward
                float turn = phase + t * 4.1f;
                float armRadius = radius * (1f - t) * (1f - t) * .92f;
                Vec3 orbit = camRight.scale(Mth.cos(turn) * armRadius)
                        .add(camUp.scale(Mth.sin(turn) * armRadius * .42f));
                Vec3 point = centre.add(fist.subtract(centre).scale(t * t)).add(orbit);
                float width = (.11f + .20f * (1f - t)) * (advanced ? 1.5f : 1f);

                if (previous != null) {
                    float fade = alpha * (.30f + .70f * (1f - t));
                    int argb = tint(armTint, fade, whiteOut);
                    ribbonQuad(buf, matrix, previous.subtract(cameraPos), point.subtract(cameraPos),
                            camUp, previousWidth, width, argb, (i - 1) / (float) steps, t);
                }
                previous = point;
                previousWidth = width;
            }
        }
        BufferUploader.drawWithShader(buf.end());
    }

    private static void ribbonQuad(BufferBuilder buf, Matrix4f matrix, Vec3 a, Vec3 b,
                                   Vec3 widthAxis, float wa, float wb, int argb, float ua, float ub) {
        Vec3 axis = widthAxis.normalize();
        Vec3 a0 = a.add(axis.scale(wa));
        Vec3 a1 = a.subtract(axis.scale(wa));
        Vec3 b0 = b.add(axis.scale(wb));
        Vec3 b1 = b.subtract(axis.scale(wb));

        float alpha = ((argb >>> 24) & 255) / 255f;
        float r = ((argb >>> 16) & 255) / 255f;
        float g = ((argb >>> 8) & 255) / 255f;
        float bl = (argb & 255) / 255f;

        buf.vertex(matrix, (float) a0.x, (float) a0.y, (float) a0.z).uv(ua, 0f).color(r, g, bl, alpha).endVertex();
        buf.vertex(matrix, (float) b0.x, (float) b0.y, (float) b0.z).uv(ub, 0f).color(r, g, bl, alpha).endVertex();
        buf.vertex(matrix, (float) b1.x, (float) b1.y, (float) b1.z).uv(ub, 1f).color(r, g, bl, alpha).endVertex();
        buf.vertex(matrix, (float) a1.x, (float) a1.y, (float) a1.z).uv(ua, 1f).color(r, g, bl, alpha).endVertex();
    }

    /** Applies an alpha scale and blends the colour toward white for the collapse blow-out. */
    private static int tint(int argb, float alphaScale, float whiteOut) {
        int a = (int) Mth.clamp(((argb >>> 24) & 255) * alphaScale, 0f, 255f);
        int r = (argb >>> 16) & 255;
        int g = (argb >>> 8) & 255;
        int b = argb & 255;
        if (whiteOut > 0f) {
            r = (int) Mth.lerp(whiteOut, r, 255);
            g = (int) Mth.lerp(whiteOut, g, 255);
            b = (int) Mth.lerp(whiteOut, b, 255);
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
