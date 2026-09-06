package com.hexhaki.client.anim;

import com.hexhaki.HexHaki;
import com.mojang.logging.LogUtils;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

public final class HakiAnimations {
    private static final ResourceLocation LAYER = HexHaki.id("haki_layer");
    private static final Logger LOGGER = LogUtils.getLogger();

    private HakiAnimations() {}

    @Mod.EventBusSubscriber(modid = HexHaki.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Init {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    LAYER, 900, player -> new ModifierLayer<>()));
        }
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layerFor(AbstractClientPlayer player) {
        var associated = PlayerAnimationAccess.getPlayerAssociatedData(player);
        Object existing = associated.get(LAYER);
        if (existing instanceof ModifierLayer<?> layer) {
            return (ModifierLayer<IAnimation>) layer;
        }

        // Be resilient to registration-order/resource-reload quirks. If Player Animator did not
        // create our factory layer for this player, install it now instead of silently dropping
        // every Haki animation packet.
        ModifierLayer<IAnimation> layer = new ModifierLayer<>();
        PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(900, layer);
        associated.set(LAYER, layer);
        LOGGER.info("Created fallback HexHaki animation layer for {}", player.getGameProfile().getName());
        return layer;
    }

    public static void playRemote(int entityId, String name, int fadeTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!(mc.level.getEntity(entityId) instanceof AbstractClientPlayer playerEntity)) return;

        ModifierLayer<IAnimation> layer = layerFor(playerEntity);
        if ("__clear__".equals(name)) {
            layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(Math.max(0, fadeTicks), Ease.INOUTQUAD),
                    null);
            return;
        }

        ResourceLocation id = HexHaki.id(name);
        var animation = PlayerAnimationRegistry.getAnimation(id);
        if (animation == null) {
            LOGGER.warn("HexHaki animation '{}' was requested but not loaded. Expected it under assets/{}/player_animation/.", id, HexHaki.MODID);
            return;
        }

        KeyframeAnimationPlayer player = new KeyframeAnimationPlayer(animation)
                .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration()
                        .setShowRightArm(true)
                        .setShowLeftArm(true)
                        .setShowRightItem(true)
                        .setShowLeftItem(true));

        layer.replaceAnimationWithFade(
                AbstractFadeModifier.standardFadeIn(Math.max(0, fadeTicks), Ease.INOUTQUAD),
                player);
    }
}
