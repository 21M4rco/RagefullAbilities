package com.hexhaki.network;

import com.hexhaki.HexHaki;
import com.hexhaki.network.msg.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class HakiNetwork {
    // Legacy entity-state fields are retained for packet stability; the dedicated Ryuo Punch input was removed in V2.
    private static final String VERSION = "32";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            HexHaki.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static int id;
    private HakiNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(id++, C2SHakiAction.class, C2SHakiAction::encode, C2SHakiAction::decode, C2SHakiAction::handle);
        CHANNEL.registerMessage(id++, S2CHakiVisual.class, S2CHakiVisual::encode, S2CHakiVisual::decode, S2CHakiVisual::handle);
        CHANNEL.registerMessage(id++, S2CArmamentImpact.class, S2CArmamentImpact::encode, S2CArmamentImpact::decode, S2CArmamentImpact::handle);
        CHANNEL.registerMessage(id++, S2CRyoRelease.class, S2CRyoRelease::encode, S2CRyoRelease::decode, S2CRyoRelease::handle);
        CHANNEL.registerMessage(id++, S2CGalaxyImpact.class, S2CGalaxyImpact::encode, S2CGalaxyImpact::decode, S2CGalaxyImpact::handle);
        CHANNEL.registerMessage(id++, S2CGalaxyWave.class, S2CGalaxyWave::encode, S2CGalaxyWave::decode, S2CGalaxyWave::handle);
        CHANNEL.registerMessage(id++, S2CGalaxyHakiStrike.class, S2CGalaxyHakiStrike::encode, S2CGalaxyHakiStrike::decode, S2CGalaxyHakiStrike::handle);
        CHANNEL.registerMessage(id++, S2CAnimation.class, S2CAnimation::encode, S2CAnimation::decode, S2CAnimation::handle);
        CHANNEL.registerMessage(id++, S2CPerception.class, S2CPerception::encode, S2CPerception::decode, S2CPerception::handle);
        CHANNEL.registerMessage(id++, S2CBladeSlash.class, S2CBladeSlash::encode, S2CBladeSlash::decode, S2CBladeSlash::handle);
        CHANNEL.registerMessage(id++, S2CWifiHaki.class, S2CWifiHaki::encode, S2CWifiHaki::decode, S2CWifiHaki::handle);
        CHANNEL.registerMessage(id++, S2CCinematic.class, S2CCinematic::encode, S2CCinematic::decode, S2CCinematic::handle);
        CHANNEL.registerMessage(id++, S2CKingsGripImpactFrame.class, S2CKingsGripImpactFrame::encode, S2CKingsGripImpactFrame::decode, S2CKingsGripImpactFrame::handle);
        CHANNEL.registerMessage(id++, S2CStateSync.class, S2CStateSync::encode, S2CStateSync::decode, S2CStateSync::handle);
        CHANNEL.registerMessage(id++, S2CEntityState.class, S2CEntityState::encode, S2CEntityState::decode, S2CEntityState::handle);
        CHANNEL.registerMessage(id++, S2CConquerorShock.class, S2CConquerorShock::encode, S2CConquerorShock::decode, S2CConquerorShock::handle);
        CHANNEL.registerMessage(id++, S2CWorldFx.class, S2CWorldFx::encode, S2CWorldFx::decode, S2CWorldFx::handle);
    }

    public static void tracking(ServerPlayer source, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> source), packet);
    }
    public static void to(ServerPlayer player, Object packet) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet); }

    /**
     * Sends to everyone within {@code radius} of a point, regardless of what they are tracking.
     *
     * <p>Entity-addressed delivery cannot carry an explosion reliably: it depends on the entity
     * still being resolvable on each viewer's client at the exact tick the effect fires, and a body
     * that has just been driven sixty blocks into the floor is precisely the case where that fails.
     */
    public static void near(net.minecraft.server.level.ServerLevel level, double x, double y, double z,
                            double radius, Object packet) {
        CHANNEL.send(PacketDistributor.NEAR.with(
                () -> new PacketDistributor.TargetPoint(x, y, z, radius, level.dimension())), packet);
    }
}
