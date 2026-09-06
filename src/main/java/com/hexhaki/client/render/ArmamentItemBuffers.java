package com.hexhaki.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;

/**
 * MultiBufferSource wrapper used only while an Armament-coated player renders a held item.
 *
 * Every vertex keeps its original geometry, UVs, lighting, normals and alpha, but its RGB is
 * replaced with absolute black. Because this happens in the item render pipeline itself it also
 * catches modded baked models, custom render types and the ordinary first/third-person held-item
 * paths instead of drawing a loose black effect over the item after the fact.
 */
public final class ArmamentItemBuffers {
    private ArmamentItemBuffers() {}

    public static MultiBufferSource black(MultiBufferSource source) {
        return new BlackBufferSource(source);
    }

    private static final class BlackBufferSource implements MultiBufferSource {
        private final MultiBufferSource parent;

        private BlackBufferSource(MultiBufferSource parent) {
            this.parent = parent;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return new BlackVertexConsumer(parent.getBuffer(renderType));
        }
    }

    /**
     * Forge's VertexConsumerWrapper deliberately returns itself from chained vertex operations.
     * Overriding color/defaultColor therefore also catches ItemRenderer's baked-quad bulk path.
     */
    private static final class BlackVertexConsumer extends VertexConsumerWrapper {
        private BlackVertexConsumer(VertexConsumer parent) {
            super(parent);
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            parent.color(0, 0, 0, alpha);
            return this;
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            parent.defaultColor(0, 0, 0, alpha);
        }
    }
}
