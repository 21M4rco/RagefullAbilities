package com.hexhaki.network.msg;

import com.hexhaki.client.vfx.HakiVfx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Authoritative world-space contact point + travel direction for coated Armament punches.
 *  This prevents the compressed-air burst from inheriting the victim's rotation and spraying sideways.
 *  style 0 = normal impact presentation; style 1 = King's Grip all-3D geometry (no billboard flakes). */
public record S2CArmamentImpact(int attackerId, int targetId, float power, boolean acoc, int style,
                                double x, double y, double z,
                                double dirX, double dirY, double dirZ, long seed) {
    public static void encode(S2CArmamentImpact m, FriendlyByteBuf b) {
        b.writeVarInt(m.attackerId);
        b.writeVarInt(m.targetId);
        b.writeFloat(m.power);
        b.writeBoolean(m.acoc);
        b.writeVarInt(m.style);
        b.writeDouble(m.x);
        b.writeDouble(m.y);
        b.writeDouble(m.z);
        b.writeDouble(m.dirX);
        b.writeDouble(m.dirY);
        b.writeDouble(m.dirZ);
        b.writeLong(m.seed);
    }

    public static S2CArmamentImpact decode(FriendlyByteBuf b) {
        return new S2CArmamentImpact(
                b.readVarInt(), b.readVarInt(), b.readFloat(), b.readBoolean(), b.readVarInt(),
                b.readDouble(), b.readDouble(), b.readDouble(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readLong());
    }

    public static void handle(S2CArmamentImpact m, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> HakiVfx.acceptArmamentImpact(m)));
        supplier.get().setPacketHandled(true);
    }
}
