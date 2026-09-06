package com.hexhaki.network.msg;

import com.hexhaki.client.vfx.HakiVfx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Exact world-space packet for the Galaxy Impact tap wave. hakiTier: 0=uncoated, 1=Armament, 2=Advanced/J. */
public record S2CGalaxyWave(int entityId, float power, float charge, float radius, boolean joyBoy, int hakiTier,
                            double startX, double startY, double startZ,
                            double endX, double endY, double endZ, long seed) {
    public static void encode(S2CGalaxyWave m, FriendlyByteBuf b) {
        b.writeVarInt(m.entityId);
        b.writeFloat(m.power);
        b.writeFloat(m.charge);
        b.writeFloat(m.radius);
        b.writeBoolean(m.joyBoy);
        b.writeVarInt(m.hakiTier);
        b.writeDouble(m.startX);
        b.writeDouble(m.startY);
        b.writeDouble(m.startZ);
        b.writeDouble(m.endX);
        b.writeDouble(m.endY);
        b.writeDouble(m.endZ);
        b.writeLong(m.seed);
    }

    public static S2CGalaxyWave decode(FriendlyByteBuf b) {
        return new S2CGalaxyWave(b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readVarInt(),
                b.readDouble(), b.readDouble(), b.readDouble(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readLong());
    }

    public static void handle(S2CGalaxyWave m, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> HakiVfx.acceptGalaxyWave(m)));
        supplier.get().setPacketHandled(true);
    }
}
