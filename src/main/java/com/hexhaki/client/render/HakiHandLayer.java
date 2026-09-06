package com.hexhaki.client.render;

import com.hexhaki.HexHaki;
import com.hexhaki.client.RemoteHakiStates;
import com.hexhaki.client.cinematic.CinematicController;
import com.hexhaki.client.vfx.HakiVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Armament coating + arm-local aura pass.
 *
 * The BLACK coating re-renders the player's real arm ModelParts, so PlayerAnimator/BendyLib
 * deformation remains exact. The red aura is deliberately NOT an entity-root Photon effect:
 * Photon EntityEffect follows the player's root transform and cannot follow individual animated
 * arm bones, which was why the old aura crossed the chest during punches.
 *
 * Instead, small translucent energy wisps are rendered in each arm's live ModelPart space. This
 * keeps the aura physically attached to LEFT ARM + RIGHT ARM ONLY through punches and poses while
 * leaving the skin/coating itself black.
 */
public final class HakiHandLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation COATING_TEX = HexHaki.id("textures/effect/coating.png");
    // The arm aura reads better on the high-resolution smoke sprite: the legacy soft.png is a
    // 64px linear ramp, so every wisp carried a visible disc edge at close range.
    private static final ResourceLocation AURA_TEX = HexHaki.id("textures/effect/smoke_soft.png");
    // Tight core with a wide soft halo: the falloff a point glow needs, which the flat-ish
    // conqueror_soft sprite could not provide.
    private static final ResourceLocation FLAME_TEX = HexHaki.id("textures/effect/flame.png");
    private static final ResourceLocation FIST_GLOW_TEX = HexHaki.id("textures/effect/glow_core.png");
    private static final ResourceLocation BEAM_TEX = HexHaki.id("textures/effect/beam.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public HakiHandLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partial, float age, float yaw, float pitch) {
        RemoteHakiStates.State state = RemoteHakiStates.get(player.getId());
        if (state == null || (!state.armament() && !state.ryuo())) return;

        float gloss = Math.min(1.0f, state.armamentMastery() / 1000.0f);
        PlayerModel<AbstractClientPlayer> model = getParentModel();

        renderCoating(pose, buffer, light, model.rightArm, gloss, state.acoc());
        renderCoating(pose, buffer, light, model.leftArm, gloss, state.acoc());

        // King's Grip owns its own all-3D Photon presentation. Suppress these ordinary arm-local
        // crossed quads during that cinematic so neither participant sees flat sprite "flakes".
        // The black Armament coating above remains, while Advanced Grip fire is supplied only by
        // kingsGripAdvancedWindup() on the LEFT striking fist.
        boolean kingsGrip = CinematicController.isKingsGripActive();
        boolean gripLeftFistFire = HakiVfx.isKingsGripLeftFistFireActive(player.getId());
        boolean slim = "slim".equals(player.getModelName());
        float time = player.tickCount + partial;
        if (!kingsGrip && !gripLeftFistFire) {
            renderArmAura(pose, buffer, player, model.rightArm, gloss, state.acoc(), time, slim, true);
            renderArmAura(pose, buffer, player, model.leftArm, gloss, state.acoc(), time, slim, false);
            // Advanced Haki mode (J / ACoC) owns the normal free-combat flame sheath.
            if (state.acoc()) {
                renderAdvancedFistGlow(pose, buffer, player, model.rightArm, time, slim, true);
                renderAdvancedFistGlow(pose, buffer, player, model.leftArm, time, slim, false);
                renderAdvancedFistFlames(pose, buffer, player, model.rightArm, gloss, time, slim, true);
                renderAdvancedFistFlames(pose, buffer, player, model.leftArm, gloss, time, slim, false);
            }
        }
        // Advanced King's Grip is special: fire is rendered ONLY on the animated LEFT fist.
        // This happens in leftArm ModelPart space, so it follows the real hand through every
        // Player Animator/BendyLib keyframe instead of guessing an entity-root offset.
        if (gripLeftFistFire) {
            renderKingsGripLeftFistFire3D(pose, buffer, player, model.leftArm, time, slim);
        }
    }

    public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffer, int light,
                                         PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player,
                                         boolean right, boolean slim, float gloss, boolean acoc, float time) {
        // RenderArmEvent fires BEFORE vanilla draws the hand. The event is cancelled by the hook, so
        // this method must render the actual first-person Armament arm, not a coplanar overlay that
        // vanilla can paint over afterwards.
        model.attackTime = 0.0F;
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        ModelPart arm = right ? model.rightArm : model.leftArm;
        arm.visible = true;
        arm.xRot = 0.0F;

        renderCoating(pose, buffer, light, arm, gloss, acoc);
        if (!CinematicController.isKingsGripActive()) {
            renderArmAura(pose, buffer, player, arm, gloss, acoc, time, slim, right);
            if (acoc) {
                renderAdvancedFistGlow(pose, buffer, player, arm, time, slim, right);
                renderAdvancedFistFlames(pose, buffer, player, arm, gloss, time, slim, right);
            }
        }
    }

    private static void renderCoating(PoseStack pose, MultiBufferSource buffer, int light, ModelPart arm,
                                      float gloss, boolean advanced) {
        if (!arm.visible) return;

        // Pure Armament hardening. No red material pass touches the arm model.
        VertexConsumer base = buffer.getBuffer(RenderType.entityTranslucent(COATING_TEX));
        float black = 0.025f + 0.025f * (1.0f - gloss);
        // Normal Armament stays exactly as before. Advanced Haki mode (J) adds only a subtle
        // dark-crimson lift; the arm is still visibly black, just hotter/more dangerous.
        float red = advanced ? Math.min(0.125f, black + 0.066f + 0.020f * gloss) : black;
        float green = advanced ? black * 0.58f : black;
        float blue = advanced ? black * 0.66f : black + 0.008f;
        arm.render(pose, base, light, OverlayTexture.NO_OVERLAY,
                red, green, blue, 0.995f);
    }

    /**
     * Renders a loose red particle sheath directly in the animated arm's local coordinate system.
     * Nothing here can appear on the torso because every wisp is transformed through this arm's
     * ModelPart matrix and remains within a tight radius of the arm volume.
     */
    private static void renderArmAura(PoseStack pose, MultiBufferSource buffer, AbstractClientPlayer player, ModelPart arm,
                                      float mastery, boolean acoc, float time, boolean slim, boolean right) {
        if (!arm.visible) return;

        pose.pushPose();
        arm.translateAndRotate(pose);
        ArmBend bend = currentArmBend(player, right);

        VertexConsumer out = buffer.getBuffer(RenderType.entityTranslucent(AURA_TEX));
        float armRadius = slim ? 0.125f : 0.145f;
        float alphaBase = 0.16f + 0.06f * mastery + (acoc ? 0.055f : 0.0f);
        float red = 1.0f;
        float green = acoc ? 0.038f : 0.075f;
        float blue = acoc ? 0.052f : 0.09f;
        float handedPhase = right ? 0.0f : 2.35f;

        // Seven overlapping wisps per arm create a continuous transparent sheath without ever
        // painting the black arm red. Positions orbit just OUTSIDE the arm surface.
        for (int i = 0; i < 7; i++) {
            float along = -0.055f + i * 0.112f;
            float phase = time * (0.19f + 0.018f * mastery) + handedPhase + i * 1.77f;
            float pulse = 0.84f + 0.16f * (float)Math.sin(time * 0.31f + i * 1.13f);
            float radius = armRadius + 0.020f + 0.010f * (float)Math.sin(time * 0.23f + i * 2.0f);
            float x = (float)Math.cos(phase) * radius;
            float z = (float)Math.sin(phase) * radius;
            float size = (0.090f + 0.024f * mastery + (acoc ? 0.010f : 0.0f)) * pulse;
            float alpha = alphaBase * pulse;

            emitCrossWisp(pose.last(), out, bend, x, along, z, size, red, green, blue, alpha);
        }

        // A few smaller sparks hugging the wrist/forearm make fast punches leave a readable red
        // trail, but they are still arm-local rather than body-root particles.
        for (int i = 0; i < 3; i++) {
            float phase = -time * 0.27f + handedPhase + i * 2.094f;
            float radius = armRadius + 0.035f;
            float x = (float)Math.cos(phase) * radius;
            float z = (float)Math.sin(phase) * radius;
            float y = 0.34f + i * 0.115f;
            emitCrossWisp(pose.last(), out, bend, x, y, z,
                    0.048f + 0.014f * mastery, 1.0f, 0.03f, 0.06f,
                    0.22f + 0.08f * mastery + (acoc ? 0.03f : 0.0f));
        }

        pose.popPose();
    }

    /** Advanced King's Grip fire. Unlike the ordinary aura, this is genuine 3D volume: a cluster
     * of emissive tapered boxes welded to the animated LEFT fist. No camera-facing/crossed sprites. */
    private static void renderKingsGripLeftFistFire3D(PoseStack pose, MultiBufferSource buffer,
                                                       AbstractClientPlayer player, ModelPart leftArm,
                                                       float time, boolean slim) {
        if (!leftArm.visible) return;
        pose.pushPose();
        leftArm.translateAndRotate(pose);
        ArmBend bend = currentArmBend(player, false);
        VertexConsumer out = buffer.getBuffer(RenderType.entityTranslucent(BEAM_TEX));

        float radius = slim ? .155f : .182f;
        float handY = .50f;
        // Dense hot core wraps the fist itself. Every box is transformed through the live elbow bend.
        for (int i = 0; i < 24; i++) {
            float phase = i * .4189f + time * .11f;
            float ring = (i % 3) * .035f;
            float x = (float)Math.cos(phase) * (radius * .44f + ring);
            float z = (float)Math.sin(phase) * (radius * .44f + ring);
            float y = handY + ((i % 3) - 1) * .040f;
            float flicker = .88f + .12f * (float)Math.sin(time * .73f + i * 1.31f);
            emitBentBox(pose.last(), out, bend, x, y, z,
                    (.095f + ring*.45f) * flicker, (.115f + ring*.25f) * flicker, (.095f + ring*.45f) * flicker,
                    1f, i%3==0?.72f:.10f, i%3==0?.10f:.025f, .78f);
        }

        // Irregular solid tongues grow directly from the fist volume. They are short 3D prisms,
        // never billboard flakes, and remain attached when the left arm snaps forward.
        for (int i = 0; i < 30; i++) {
            float phase = i * .3491f - time * .075f;
            float x = (float)Math.cos(phase) * (radius*.56f);
            float z = (float)Math.sin(phase) * (radius*.56f);
            float lick = .80f + .20f * (float)Math.sin(time * .91f + i * 1.77f);
            float h = (.20f + (i%5)*.040f) * lick;
            emitBentBox(pose.last(), out, bend, x, handY+.045f+h*.32f, z,
                    .060f*lick, h, .060f*lick,
                    1f, i%2==0?.24f:.58f, .025f, .74f);
        }

        // Taller outer flames make Advanced King's Grip read as a deliberately overcharged fist,
        // while remaining welded to the animated hand transform. These are still solid 3D prisms.
        for (int i = 0; i < 16; i++) {
            float phase = i * .7854f + time * .055f;
            float orbit = radius * .82f;
            float x = (float)Math.cos(phase) * orbit;
            float z = (float)Math.sin(phase) * orbit;
            float surge = .88f + .12f * (float)Math.sin(time * .67f + i * 1.41f);
            float h = (.38f + (i%4)*.060f) * surge;
            emitBentBox(pose.last(), out, bend, x, handY+.055f+h*.30f, z,
                    .052f*surge, h, .052f*surge,
                    1f, i%2==0?.36f:.64f, .02f, .72f);
        }
        pose.popPose();
    }

    /**
     * Full-bright Advanced Haki core, transformed through the live arm bone and BendyLib elbow.
     * The three falloff shells share the real fist centre instead of guessing an entity-root
     * position, so the yellow light cannot drift onto the chest during attacks or animations.
     */
    private static void renderAdvancedFistGlow(PoseStack pose, MultiBufferSource buffer,
                                                AbstractClientPlayer player, ModelPart arm,
                                                float time, boolean slim, boolean right) {
        if (!arm.visible) return;

        pose.pushPose();
        arm.translateAndRotate(pose);
        ArmBend bend = currentArmBend(player, right);
        VertexConsumer glow = buffer.getBuffer(RenderType.entityTranslucent(FIST_GLOW_TEX));

        float armRadius = slim ? 0.125f : 0.145f;
        float fistY = 0.50f;
        float knuckleZ = -armRadius * 0.72f;
        float pulse = 0.92f + 0.08f * (float)Math.sin(time * 0.18f + (right ? 0.0f : 1.7f));

        emitCrossWisp(pose.last(), glow, bend, 0.0f, fistY, knuckleZ,
                0.44f * pulse, 1.0f, 0.68f, 0.015f, 0.24f);
        emitCrossWisp(pose.last(), glow, bend, 0.0f, fistY, knuckleZ,
                0.30f * pulse, 1.0f, 0.88f, 0.08f, 0.62f);
        emitCrossWisp(pose.last(), glow, bend, 0.0f, fistY, knuckleZ,
                0.17f * pulse, 1.0f, 1.0f, 0.55f, 0.96f);
        pose.popPose();
    }

    /**
     * Advanced Haki fists, ignited.
     *
     * <p>Replaces the previous concentric-shell glow entirely. That was a coloured ball around the
     * hand; this is fire. Upright tapered tongues are drawn with the dedicated flame sprite in the
     * arm's local space, so they hinge with the animated elbow, and each one climbs, narrows and
     * dies on its own phase before restarting -- which is what makes a fire look alive rather than
     * like a pulsing light.
     *
     * <p>Kept strictly to the hands: the tallest tongue is about a hand's width, so the forearm
     * stays black hardened coating.
     */
    private static void renderAdvancedFistFlames(PoseStack pose, MultiBufferSource buffer, AbstractClientPlayer player,
                                                 ModelPart arm, float mastery, float time, boolean slim, boolean right) {
        if (!arm.visible) return;

        pose.pushPose();
        arm.translateAndRotate(pose);
        ArmBend bend = currentArmBend(player, right);
        VertexConsumer out = buffer.getBuffer(RenderType.entityTranslucent(FLAME_TEX));

        float armRadius = slim ? 0.125f : 0.145f;
        float sidePhase = right ? 0.0f : 2.35f;
        // The elbow bend begins at .375. Starting here and ending around .62-.72 keeps every
        // tongue over the actual hand cube/knuckles instead of dangling below the model's fist.
        float flameBaseY = 0.36f;
        float knuckleZ = -armRadius * 0.68f;

        int tongues = 10 + (int)(mastery * 5.0f);
        for (int i = 0; i < tongues; i++) {
            float phase = sidePhase + i * (6.2832f / tongues);
            float life = ((time * (.041f + .009f * mastery)) + i * .137f) % 1.0f;
            float fade = 1.0f - life;
            float radius = armRadius * (.18f + .48f * (i % 3) / 2.0f);
            float sway = (float)Math.sin(time * .31f + i * 1.31f) * .016f;
            float x = (float)Math.cos(phase) * radius + sway;
            float z = knuckleZ + (float)Math.sin(phase) * radius * .72f + sway * .35f;

            float height = (.20f + .14f * mastery) * (.62f + .68f * life);
            float width = (.092f + .038f * mastery) * (.62f + .62f * fade);
            float y = flameBaseY + life * (.035f + .018f * mastery);
            float alpha = (.70f + .20f * mastery) * Math.min(1f, life * 5f) * fade;
            boolean gold = i % 3 == 0;
            emitFlameTongue(pose.last(), out, bend, x, y, z, width, height,
                    1.0f, gold ? .82f : .24f, gold ? .08f : .018f, alpha);
        }

        // Dense yellow-white roots sit directly on the fist and visually join the fire to its glow.
        for (int i = 0; i < 6; i++) {
            float phase = sidePhase + i * 1.0472f - time * .028f;
            float radius = armRadius * .30f;
            emitFlameTongue(pose.last(), out, bend,
                    (float)Math.cos(phase) * radius, flameBaseY + .015f,
                    knuckleZ + (float)Math.sin(phase) * radius * .65f,
                    .105f + .030f * mastery, .19f + .045f * mastery,
                    1.0f, .92f, .24f, .88f);
        }
        pose.popPose();
    }

    /** Two crossed upright quads. Upright rather than camera-facing so the tongue keeps its
     *  direction relative to the hand as the arm swings. */
    private static void emitFlameTongue(PoseStack.Pose pose, VertexConsumer out, ArmBend bend,
                                        float x, float y, float z, float width, float height,
                                        float r, float g, float b, float a) {
        float hw = width * .5f;
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        bentQuad(out, m, n, bend, x - hw, y, z, x + hw, y, z, x + hw, y + height, z, x - hw, y + height, z,
                r, g, b, a, 0, 0, 1);
        bentQuad(out, m, n, bend, x, y, z - hw, x, y, z + hw, x, y + height, z + hw, x, y + height, z - hw,
                r, g, b, a, 1, 0, 0);
    }

    /** Current Player Animator/BendyLib elbow bend for this arm. We read the same live bend pair
     *  that Player Animator applies to the model, so the aura and the actual limb use one source of truth. */
    private static ArmBend currentArmBend(AbstractClientPlayer player, boolean right) {
        if (player instanceof IAnimatedPlayer animated) {
            var animation = animated.playerAnimator_getAnimation();
            if (animation != null && animation.isActive()) {
                var bend = animation.getBend(right ? "rightArm" : "leftArm");
                if (bend != null) return new ArmBend(bend.getLeft(), bend.getRight());
            }
        }
        return ArmBend.NONE;
    }

    /** Mirrors Player Animator's BendyLib child transform: bend around the elbow at 0.375 model
     *  units, with the bend-axis sign flipped exactly like IBendHelper.rotateMatrixStack(). */
    private static Vector3f bendPoint(ArmBend bend, float x, float y, float z) {
        if (bend == null || Math.abs(bend.angle()) < 0.0001f || y <= 0.375f) {
            return new Vector3f(x, y, z);
        }
        float axis = -bend.axis();
        float ux = (float)Math.cos(axis);
        float uz = (float)Math.sin(axis);
        float vx = x;
        float vy = y - 0.375f;
        float vz = z;
        float c = (float)Math.cos(bend.angle());
        float s = (float)Math.sin(bend.angle());
        float dot = ux * vx + uz * vz;
        float rx = vx * c + (-uz * vy) * s + ux * dot * (1f - c);
        float ry = vy * c + (uz * vx - ux * vz) * s;
        float rz = vz * c + (ux * vy) * s + uz * dot * (1f - c);
        return new Vector3f(rx, ry + 0.375f, rz);
    }

    private record ArmBend(float axis, float angle) {
        private static final ArmBend NONE = new ArmBend(0f, 0f);
    }

    /** Three crossed translucent quads, bent vertex-by-vertex with the current arm animation. */
    private static void emitCrossWisp(PoseStack.Pose pose, VertexConsumer out, ArmBend bend,
                                      float x, float y, float z, float size,
                                      float r, float g, float b, float a) {
        float h = size * 0.5f;
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        bentQuad(out,m,n,bend, x-h,y-h,z, x+h,y-h,z, x+h,y+h,z, x-h,y+h,z, r,g,b,a, 0,0,1);
        bentQuad(out,m,n,bend, x,y-h,z-h, x,y-h,z+h, x,y+h,z+h, x,y+h,z-h, r,g,b,a, 1,0,0);
        bentQuad(out,m,n,bend, x-h,y,z-h, x+h,y,z-h, x+h,y,z+h, x-h,y,z+h, r,g,b,a, 0,1,0);
    }

    /** Emits a true 3D cuboid in arm-local space, bending all corners with the animated elbow. */
    private static void emitBentBox(PoseStack.Pose pose, VertexConsumer out, ArmBend bend,
                                    float cx,float cy,float cz,float sx,float sy,float sz,
                                    float r,float g,float b,float a) {
        float x0=cx-sx*.5f,x1=cx+sx*.5f,y0=cy-sy*.5f,y1=cy+sy*.5f,z0=cz-sz*.5f,z1=cz+sz*.5f;
        Matrix4f m=pose.pose(); Matrix3f n=pose.normal();
        bentQuad(out,m,n,bend,x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1,r,g,b,a,0,0,1);
        bentQuad(out,m,n,bend,x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0,r,g,b,a,0,0,-1);
        bentQuad(out,m,n,bend,x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1,r,g,b,a,1,0,0);
        bentQuad(out,m,n,bend,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0,r,g,b,a,-1,0,0);
        bentQuad(out,m,n,bend,x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0,r,g,b,a,0,1,0);
        bentQuad(out,m,n,bend,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1,r,g,b,a,0,-1,0);
    }

    private static void bentQuad(VertexConsumer out, Matrix4f m, Matrix3f n, ArmBend bend,
                                 float x0,float y0,float z0, float x1,float y1,float z1,
                                 float x2,float y2,float z2, float x3,float y3,float z3,
                                 float r,float g,float b,float a, float nx,float ny,float nz) {
        Vector3f p0=bendPoint(bend,x0,y0,z0);
        Vector3f p1=bendPoint(bend,x1,y1,z1);
        Vector3f p2=bendPoint(bend,x2,y2,z2);
        Vector3f p3=bendPoint(bend,x3,y3,z3);
        quad(out,m,n, p0.x,p0.y,p0.z, p1.x,p1.y,p1.z, p2.x,p2.y,p2.z, p3.x,p3.y,p3.z, r,g,b,a,nx,ny,nz);
    }

    private static void quad(VertexConsumer out, Matrix4f m, Matrix3f n,
                             float x0,float y0,float z0, float x1,float y1,float z1,
                             float x2,float y2,float z2, float x3,float y3,float z3,
                             float r,float g,float b,float a, float nx,float ny,float nz) {
        vertex(out,m,n,x0,y0,z0,0,0,r,g,b,a,nx,ny,nz);
        vertex(out,m,n,x1,y1,z1,1,0,r,g,b,a,nx,ny,nz);
        vertex(out,m,n,x2,y2,z2,1,1,r,g,b,a,nx,ny,nz);
        vertex(out,m,n,x3,y3,z3,0,1,r,g,b,a,nx,ny,nz);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Matrix3f n,
                               float x,float y,float z,float u,float v,
                               float r,float g,float b,float a,float nx,float ny,float nz) {
        out.vertex(m,x,y,z)
                .color(r,g,b,a)
                .uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(n,nx,ny,nz)
                .endVertex();
    }
}
