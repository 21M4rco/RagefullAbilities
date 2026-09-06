package com.hexhaki.client.render;

import com.hexhaki.HexHaki;
import com.hexhaki.client.vfx.HakiFx;
import com.hexhaki.network.msg.S2CBladeSlash;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Haki blade cuts.
 *
 * <p>The previous version drew untextured {@code POSITION_COLOR} quads, so every cut showed its
 * polygon silhouette: a flat coloured ribbon with hard edges and a uniform interior.  Three things
 * change that here.
 *
 * <ul>
 *   <li><b>Geometry.</b> The band now spans the arc and extends <i>backwards</i> along the travel
 *       direction, so it has a leading rim and a trailing wake.  The old band's thickness ran
 *       perpendicular to travel, which gave the sprite nothing meaningful to describe.</li>
 *   <li><b>Texture.</b> That band samples {@code crescent.png} with {@code v = 0} pinned to the
 *       leading rim, which is where the sprite keeps its honed edge; the trail dissolves into
 *       grain behind it and the tips taper out, so the cut has no visible polygon boundary.</li>
 *   <li><b>Motion.</b> A short trail of decaying afterimages is stamped along the path already
 *       travelled, which is what makes a fast object read as fast.</li>
 * </ul>
 *
 * <p>Depth testing is now on: a cut that punched through terrain was a large part of why these
 * read as an overlay rather than as something moving through the world.
 */
@Mod.EventBusSubscriber(modid = HexHaki.MODID, value = Dist.CLIENT)
public final class BladeSlashRenderer {
    private static final List<Slash> ACTIVE = new ArrayList<>();

    /** Blocks per tick; matches the server's travel pacing. */
    private static final double SPEED = 1.65;
    private static final int AFTERIMAGES = 4;

    private record Slash(S2CBladeSlash packet, long bornTick) {}

    private BladeSlashRenderer() {}

    public static void spawn(S2CBladeSlash packet) {
        Minecraft mc = Minecraft.getInstance();
        long tick = mc.level == null ? 0L : mc.level.getGameTime();
        ACTIVE.add(new Slash(packet, tick));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;

        double now = mc.level.getGameTime() + e.getPartialTick();
        Camera camera = e.getCamera();
        Vec3 cameraPos = camera.getPosition();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Iterator<Slash> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Slash active = iterator.next();
            S2CBladeSlash p = active.packet();
            double age = Math.max(0.0, now - active.bornTick());
            double travelTicks = Math.max(1.0, p.distance() / SPEED);
            if (age > travelTicks + 6.0) {
                iterator.remove();
                continue;
            }

            Vec3 dir = new Vec3(p.dirX(), p.dirY(), p.dirZ());
            dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();

            double travelled = Math.min(p.distance(), age * SPEED);
            Vec3 origin = new Vec3(p.x(), p.y(), p.z());
            float fadeIn = (float) Math.min(1.0, age / .9);
            float fadeOut = age <= travelTicks ? 1f : (float) Math.max(0.0, 1.0 - (age - travelTicks) / 6.0);
            float alpha = fadeIn * fadeOut;
            if (alpha <= .01f) continue;

            float radius = 1.35f + p.power() * .95f + (p.acoc() ? .30f : 0f);
            // The wave opens up slightly as it flies, so it never looks like a rigid decal.
            radius *= 1f + .16f * (float) Math.min(1.0, age / travelTicks);
            float depth = radius * .52f;

            // Afterimages first so the live cut composites over them.
            for (int ghost = AFTERIMAGES; ghost >= 1; ghost--) {
                double back = ghost * .42 * SPEED;
                if (travelled - back <= 0) continue;
                Vec3 centre = origin.add(dir.scale(travelled - back)).subtract(cameraPos);
                float ghostAlpha = alpha * (.30f / ghost);
                drawBand(e.getPoseStack(), centre, dir, p.horizontal(),
                        radius * (1f - .05f * ghost), depth * (1f + .22f * ghost),
                        HakiFx.CRESCENT, 0.10f, 0.02f, 0.04f, ghostAlpha * .8f);
                drawBand(e.getPoseStack(), centre, dir, p.horizontal(),
                        radius * (1f - .07f * ghost), depth * (1f + .18f * ghost),
                        HakiFx.CRESCENT, 0.95f, 0.10f, 0.16f, ghostAlpha);
            }

            Vec3 centre = origin.add(dir.scale(travelled)).subtract(cameraPos);

            // Dark Armament shell, saturated core, then a white-hot cutting edge.
            drawBand(e.getPoseStack(), centre, dir, p.horizontal(), radius * 1.06f, depth * 1.35f,
                    HakiFx.CRESCENT, .06f, .01f, .03f, .70f * alpha);
            drawBand(e.getPoseStack(), centre, dir, p.horizontal(), radius, depth,
                    HakiFx.CRESCENT, .96f, .10f, .17f, .95f * alpha);
            drawBand(e.getPoseStack(), centre.add(dir.scale(.03)), dir, p.horizontal(),
                    radius * .90f, depth * .42f,
                    HakiFx.CRESCENT, 1f, p.acoc() ? .74f : .50f, p.acoc() ? .62f : .46f, .90f * alpha);

            if (p.acoc() || p.power() >= .72f) {
                drawFractures(e.getPoseStack(), centre, dir, p.horizontal(), radius, depth,
                        alpha, p.seed(), (int) Math.floor(age), p.acoc());
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Curved band spanning the arc, extending backwards into a trailing wake.
     *
     * <p>{@code v = 0} is the leading rim and {@code v = 1} the trailing edge, which is the
     * orientation {@code crescent.png} is authored for.  The band bows forward at its centre so
     * the middle leads and the tips lag, as a swung cut does.
     */
    private static void drawBand(PoseStack pose, Vec3 centre, Vec3 dir, boolean horizontal,
                                 float radius, float depth, ResourceLocation texture,
                                 float r, float g, float b, float a) {
        if (a <= .003f || radius <= .01f) return;
        Vec3 worldUp = Math.abs(dir.y) > .92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = dir.cross(worldUp).normalize();
        Vec3 up = side.cross(dir).normalize();
        Vec3 primary = horizontal ? side : up;
        // A little offset across the sheet keeps the band from being infinitely thin edge-on.
        Vec3 thicknessAxis = horizontal ? up : side;

        RenderSystem.setShaderTexture(0, texture);
        pose.pushPose();
        pose.translate(centre.x, centre.y, centre.z);
        Matrix4f matrix = pose.last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        final int segments = 22;
        Vec3 prevLead = null, prevTrail = null;
        float prevU = 0f;
        for (int i = 0; i <= segments; i++) {
            float s = -1f + 2f * i / segments;
            float u = (s + 1f) * .5f;
            // centre leads, tips trail
            double bow = (1.0 - s * s) * radius * .34;
            double taper = Math.max(.16, 1.0 - Math.abs(s) * .68);
            Vec3 lead = primary.scale(s * radius).add(dir.scale(bow));
            Vec3 trail = lead.subtract(dir.scale(depth * taper))
                    .add(thicknessAxis.scale((1.0 - Math.abs(s)) * depth * .10));

            if (prevLead != null) {
                vertex(buf, matrix, prevLead, prevU, 0f, r, g, b, a);
                vertex(buf, matrix, lead, u, 0f, r, g, b, a);
                vertex(buf, matrix, trail, u, 1f, r, g, b, a);
                vertex(buf, matrix, prevTrail, prevU, 1f, r, g, b, a);
            }
            prevLead = lead;
            prevTrail = trail;
            prevU = u;
        }
        BufferUploader.drawWithShader(buf.end());
        pose.popPose();
    }

    /** Conqueror's fractures riding the cut; deterministic per seed so every viewer matches. */
    private static void drawFractures(PoseStack pose, Vec3 centre, Vec3 dir, boolean horizontal,
                                      float radius, float depth, float alpha, long seed,
                                      int age, boolean acoc) {
        Vec3 worldUp = Math.abs(dir.y) > .92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = dir.cross(worldUp).normalize();
        Vec3 up = side.cross(dir).normalize();
        Vec3 primary = horizontal ? side : up;
        Vec3 thicknessAxis = horizontal ? up : side;
        Random random = new Random(seed ^ (age * 0x9E3779B97F4A7C15L));

        RenderSystem.setShaderTexture(0, HakiFx.ARC);
        pose.pushPose();
        pose.translate(centre.x, centre.y, centre.z);
        Matrix4f matrix = pose.last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int count = 4 + (int) (radius * 1.7f) + (acoc ? 3 : 0);
        for (int i = 0; i < count; i++) {
            double s = random.nextDouble() * 1.75 - .875;
            double bow = (1.0 - s * s) * radius * .32;
            Vec3 base = primary.scale(s * radius).add(dir.scale(bow));
            // Fractures crawl backwards into the wake rather than sticking out in front.
            Vec3 tip = base
                    .add(primary.scale((random.nextDouble() - .5) * .70))
                    .add(thicknessAxis.scale((random.nextDouble() - .5) * .62))
                    .subtract(dir.scale(depth * (.30 + random.nextDouble() * .85)));
            float width = .030f + random.nextFloat() * .028f;
            float fade = alpha * (.65f + random.nextFloat() * .35f);
            ribbon(buf, matrix, base, tip, thicknessAxis, width,
                    1f, acoc ? .58f : .12f, acoc ? .46f : .18f, fade);
        }
        BufferUploader.drawWithShader(buf.end());
        pose.popPose();
    }

    private static void ribbon(BufferBuilder b, Matrix4f m, Vec3 a, Vec3 c, Vec3 widthAxis,
                               float width, float r, float g, float bl, float alpha) {
        Vec3 w = widthAxis.normalize().scale(width);
        vertex(b, m, a.add(w), 0f, 0f, r, g, bl, alpha);
        vertex(b, m, c.add(w), 1f, 0f, r, g, bl, alpha);
        vertex(b, m, c.subtract(w), 1f, 1f, r, g, bl, alpha);
        vertex(b, m, a.subtract(w), 0f, 1f, r, g, bl, alpha);
    }

    private static void vertex(BufferBuilder b, Matrix4f m, Vec3 v, float u, float tv,
                               float r, float g, float bl, float a) {
        b.vertex(m, (float) v.x, (float) v.y, (float) v.z)
                .uv(u, tv)
                .color(r, g, bl, Mth.clamp(a, 0f, 1f))
                .endVertex();
    }
}
