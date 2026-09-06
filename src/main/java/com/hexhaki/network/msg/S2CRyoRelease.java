package com.hexhaki.network.msg;

import com.hexhaki.client.vfx.HakiVfx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Authoritative Ryuo emission origin + punch direction.
 *  The client renders every pressure ring, streak and Haki bolt from this basis so the release
 *  can never drift away from the direction used by server-side reach, damage and knockback. */
public record S2CRyoRelease(int entityId, float power, double reach,
                            double x, double y, double z,
                            double dirX, double dirY, double dirZ, long seed) {
    public static void encode(S2CRyoRelease m, FriendlyByteBuf b) {
        b.writeVarInt(m.entityId);
        b.writeFloat(m.power);
        b.writeDouble(m.reach);
        b.writeDouble(m.x);
        b.writeDouble(m.y);
        b.writeDouble(m.z);
        b.writeDouble(m.dirX);
        b.writeDouble(m.dirY);
        b.writeDouble(m.dirZ);
        b.writeLong(m.seed);
    }

    public static S2CRyoRelease decode(FriendlyByteBuf b) {
        return new S2CRyoRelease(
                b.readVarInt(), b.readFloat(), b.readDouble(),
                b.readDouble(), b.readDouble(), b.readDouble(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readLong());
    }

    public static void handle(S2CRyoRelease m, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> HakiVfx.acceptRyoRelease(m)));
        supplier.get().setPacketHandled(true);
    }
}
