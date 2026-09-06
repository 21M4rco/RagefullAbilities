package com.hexhaki.network.msg;

import com.hexhaki.client.anim.HakiAnimations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record S2CAnimation(int entityId, String animation, int fadeTicks) {
    public static void encode(S2CAnimation m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); b.writeUtf(m.animation); b.writeVarInt(m.fadeTicks); }
    public static S2CAnimation decode(FriendlyByteBuf b) { return new S2CAnimation(b.readVarInt(), b.readUtf(96), b.readVarInt()); }
    public static void handle(S2CAnimation m, Supplier<NetworkEvent.Context> s) {
        s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HakiAnimations.playRemote(m.entityId, m.animation, m.fadeTicks)));
        s.get().setPacketHandled(true);
    }
}
