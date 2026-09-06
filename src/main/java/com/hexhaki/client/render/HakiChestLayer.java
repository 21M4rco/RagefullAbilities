package com.hexhaki.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The upper half of the Haki physique: a wider chest that does not take the waist with it.
 *
 * <p>Vanilla's torso is one 8x12x4 box, so there is no seam between chest and waist to scale
 * against — widening the part widens the belly in step, which is what made the earlier attempt
 * read as fat. This draws a second, larger copy of only the <b>top six rows</b> of that box over
 * the real one. Being strictly larger, it encloses and hides the original chest, while the bottom
 * six rows stay at their own width. The step where the shell ends is the taper.
 *
 * <p>The shell's cube is defined at the body part's own {@code texOffs(16, 16)} with height 6, so
 * its faces land on exactly the chest half of the skin's torso UVs — it is the player's own skin,
 * not a tint. A matching jacket cube at {@code texOffs(16, 32)} keeps the second skin layer from
 * vanishing at the chest.
 *
 * <p>The mesh is built here rather than registered through {@code RegisterLayerDefinitions}: it is
 * a fixed two-cube box with no variants, so baking it once at class load avoids threading a model
 * layer location through registration for no gain.
 */
public final class HakiChestLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Rows of the 12-tall torso the shell covers, counted down from the neck. */
    private static final float CHEST_HEIGHT = 6.0f;

    private static final ModelPart CHEST = bakeChest();

    public HakiChestLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    private static ModelPart bakeChest() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("haki_chest",
                CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4f, 0f, -2f, 8f, CHEST_HEIGHT, 4f, CubeDeformation.NONE)
                        .texOffs(16, 32).addBox(-4f, 0f, -2f, 8f, CHEST_HEIGHT, 4f, new CubeDeformation(0.25f)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("haki_chest");
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partial, float age,
                       float yaw, float pitch) {
        if (player.isInvisible() || player.isSpectator()) return;

        float bulk = HakiPhysiqueLayer.bulk(player.getId());
        if (bulk <= 0.01f) return;

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        ModelPart body = model.body;
        if (!body.visible) return;

        float width = 1f + HakiPhysiqueLayer.MAX_CHEST_GAIN * bulk;
        float depth = 1f + HakiPhysiqueLayer.MAX_CHEST_GAIN * HakiPhysiqueLayer.CHEST_DEPTH_RATIO * bulk;

        pose.pushPose();
        // Into the torso's own space, so the shell inherits every animation and bend applied to it.
        body.translateAndRotate(pose);
        pose.scale(width, 1f, depth);

        VertexConsumer consumer = buffer.getBuffer(
                RenderType.entityTranslucent(player.getSkinTextureLocation()));
        CHEST.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
