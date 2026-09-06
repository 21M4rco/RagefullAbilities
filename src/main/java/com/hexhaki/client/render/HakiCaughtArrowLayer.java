package com.hexhaki.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.joml.Quaternionf;

/**
 * Draws an Observation-caught arrow directly in the live animated right hand.
 *
 * <p>The server still owns and re-pins the real projectile for gameplay, but that entity is hidden
 * during the hold. Rendering the same entity in right-arm model space removes the visual race
 * between server projectile positions and the client's interpolated player/PlayerAnimator pose:
 * turning, walking, body-yaw interpolation and elbow bend can no longer make the shaft float away
 * from the hand.
 */
public final class HakiCaughtArrowLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final double SEARCH_RADIUS = 2.25D;

    public HakiCaughtArrowLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        AbstractArrow arrow = caughtArrowNear(player);
        if (arrow == null) return;

        pose.pushPose();
        getParentModel().rightArm.translateAndRotate(pose);

        // Player Animator/BendyLib bends below the elbow separately from the vanilla upper-arm
        // ModelPart. Mirror that child transform so the arrow is welded to the actual fist, not
        // merely the shoulder/upper-arm transform.
        applyLiveElbowBend(pose, player);

        // Right-hand center in arm-local model units. A tiny inward shift puts the grip through the
        // palm rather than beside it. The arrow then lies diagonally across the closed hand.
        pose.translate(-0.0125D, 0.665D, -0.015D);
        pose.mulPose(new Quaternionf().rotationXYZ(
                (float)Math.toRadians(82.0D),
                (float)Math.toRadians(-16.0D),
                (float)Math.toRadians(20.0D)));
        pose.scale(0.86F, 0.86F, 0.86F);

        renderArrowAtHand(arrow, partialTick, pose, buffer, light);
        pose.popPose();
    }

    private static AbstractArrow caughtArrowNear(AbstractClientPlayer player) {
        return player.level().getEntitiesOfClass(AbstractArrow.class,
                        player.getBoundingBox().inflate(SEARCH_RADIUS),
                        HakiCaughtArrowLayer::isHeldArrow)
                .stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
                .orElse(null);
    }

    /**
     * True only while the server has parked the real caught arrow for the hand-render pass.
     * EntityRenderDispatcherMixin uses the same predicate to suppress the normal world-space
     * render, otherwise Minecraft draws the parked entity and this hand layer at the same time.
     */
    public static boolean isHeldArrow(AbstractArrow arrow) {
        return arrow.isAlive()
                && arrow.isInvisible()
                && arrow.isNoGravity()
                && arrow.getDeltaMovement().lengthSqr() < 0.0025D;
    }

    private static void applyLiveElbowBend(PoseStack pose, AbstractClientPlayer player) {
        if (!(player instanceof IAnimatedPlayer animated)) return;
        var animation = animated.playerAnimator_getAnimation();
        if (animation == null || !animation.isActive()) return;
        var bend = animation.getBend("rightArm");
        if (bend == null || Math.abs(bend.getRight()) < 0.0001F) return;

        float axis = -bend.getLeft();
        float x = (float)Math.cos(axis);
        float z = (float)Math.sin(axis);
        pose.translate(0.0D, 0.375D, 0.0D);
        pose.mulPose(new Quaternionf().rotationAxis(bend.getRight(), x, 0.0F, z));
        pose.translate(0.0D, -0.375D, 0.0D);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderArrowAtHand(AbstractArrow arrow, float partialTick, PoseStack pose,
                                          MultiBufferSource buffer, int light) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(arrow);

        // AbstractArrowRenderer derives its orientation from the entity's world-space rotations.
        // Neutralize those only for this render call; the arm-local matrix above owns the held
        // orientation. Restore immediately so the projectile resumes normal flight rendering when
        // the server releases it.
        float yRot = arrow.getYRot();
        float yRotO = arrow.yRotO;
        float xRot = arrow.getXRot();
        float xRotO = arrow.xRotO;
        try {
            arrow.setYRot(0.0F);
            arrow.yRotO = 0.0F;
            arrow.setXRot(0.0F);
            arrow.xRotO = 0.0F;
            renderer.render(arrow, 0.0F, partialTick, pose, buffer, light);
        } finally {
            arrow.setYRot(yRot);
            arrow.yRotO = yRotO;
            arrow.setXRot(xRot);
            arrow.xRotO = xRotO;
        }
    }
}
