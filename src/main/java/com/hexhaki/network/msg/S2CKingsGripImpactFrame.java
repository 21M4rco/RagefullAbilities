package com.hexhaki.network.msg;

import com.hexhaki.client.render.KingsGripImpactFrame;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Participant-only synchronized black/white impact frame for King's Grip. */
public record S2CKingsGripImpactFrame(int tier, long seed) {
    public static void encode(S2CKingsGripImpactFrame m, FriendlyByteBuf b) {
        b.writeVarInt(m.tier);
        b.writeLong(m.seed);
    }

    public static S2CKingsGripImpactFrame decode(FriendlyByteBuf b) {
        return new S2CKingsGripImpactFrame(b.readVarInt(), b.readLong());
    }

    public static void handle(S2CKingsGripImpactFrame m, Supplier<NetworkEvent.Context> s) {
        s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> KingsGripImpactFrame.trigger(m.tier, m.seed)));
        s.get().setPacketHandled(true);
    }
}
