package com.hexhaki.network.msg;

import com.hexhaki.client.vfx.HakiVfx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Exact world-space Galaxy Impact strike. Carries both the fist release point and the remote
 * impact point so the client can render the RAGE ultimate's downward pressure/lightning path
 * without guessing from camera pitch.
 */
public record S2CGalaxyImpact(int entityId, float power, boolean joyBoy, boolean hakiCoated, boolean advancedHaki,
                              double startX, double startY, double startZ,
                              double x, double y, double z, long seed) {
    public static void encode(S2CGalaxyImpact m, FriendlyByteBuf b) {
        b.writeVarInt(m.entityId);
        b.writeFloat(m.power);
        b.writeBoolean(m.joyBoy);
        b.writeBoolean(m.hakiCoated);
        b.writeBoolean(m.advancedHaki);
        b.writeDouble(m.startX);
        b.writeDouble(m.startY);
        b.writeDouble(m.startZ);
        b.writeDouble(m.x);
        b.writeDouble(m.y);
        b.writeDouble(m.z);
        b.writeLong(m.seed);
    }

    public static S2CGalaxyImpact decode(FriendlyByteBuf b) {
        return new S2CGalaxyImpact(b.readVarInt(), b.readFloat(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                b.readDouble(), b.readDouble(), b.readDouble(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readLong());
    }

    public static void handle(S2CGalaxyImpact m, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> HakiVfx.acceptGalaxyImpact(m)));
        supplier.get().setPacketHandled(true);
    }
}
