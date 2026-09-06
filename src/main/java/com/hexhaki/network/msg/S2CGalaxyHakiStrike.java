package com.hexhaki.network.msg;

import com.hexhaki.client.vfx.HakiVfx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Galaxy Impact lightning packet. radius <= 0 = classic sky strike; radius > 0 = Advanced ground field. */
public record S2CGalaxyHakiStrike(int entityId, double x, double y, double z, float radius, long seed) {
    public static void encode(S2CGalaxyHakiStrike m, FriendlyByteBuf b) {
        b.writeVarInt(m.entityId);
        b.writeDouble(m.x);
        b.writeDouble(m.y);
        b.writeDouble(m.z);
        b.writeFloat(m.radius);
        b.writeLong(m.seed);
    }

    public static S2CGalaxyHakiStrike decode(FriendlyByteBuf b) {
        return new S2CGalaxyHakiStrike(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readLong());
    }

    public static void handle(S2CGalaxyHakiStrike m, java.util.function.Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> HakiVfx.acceptGalaxyHakiStrike(m)));
        supplier.get().setPacketHandled(true);
    }
}
