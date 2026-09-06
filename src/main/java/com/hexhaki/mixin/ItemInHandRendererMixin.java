package com.hexhaki.mixin;

import com.hexhaki.client.RemoteHakiStates;
import com.hexhaki.client.render.ArmamentItemBuffers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Coats whatever an Armament user is actually holding in solid black.
 *
 * ItemInHandRenderer#renderItem is shared by the ordinary held-item layer and first-person hand
 * renderer, so changing the buffer here covers both camera perspectives without touching the
 * ItemStack, NBT, model selection, transforms or gameplay state.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @ModifyVariable(method = "renderItem", at = @At("HEAD"), argsOnly = true, index = 6)
    private MultiBufferSource hexhaki$coatHeldItem(
            MultiBufferSource original,
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            boolean leftHanded,
            PoseStack poseStack,
            MultiBufferSource originalArgument,
            int packedLight) {
        RemoteHakiStates.State state = RemoteHakiStates.get(entity.getId());
        if (state == null || !state.armament() || stack.isEmpty()) return original;
        return ArmamentItemBuffers.black(original);
    }
}
