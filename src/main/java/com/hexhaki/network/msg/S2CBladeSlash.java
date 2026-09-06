package com.hexhaki.network.msg;

import com.hexhaki.client.render.BladeSlashRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V2 world-space Haki blade cut. The server supplies the same origin/direction/range used for damage. */
public record S2CBladeSlash(
        int attackerId,
        double x, double y, double z,
        float dirX, float dirY, float dirZ,
        float distance,
        boolean horizontal,
        float power,
        boolean acoc,
        long seed) {

    public static void encode(S2CBladeSlash m, FriendlyByteBuf b) {
        b.writeVarInt(m.attackerId);
        b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z);
        b.writeFloat(m.dirX); b.writeFloat(m.dirY); b.writeFloat(m.dirZ);
        b.writeFloat(m.distance);
        b.writeBoolean(m.horizontal);
        b.writeFloat(m.power);
        b.writeBoolean(m.acoc);
        b.writeLong(m.seed);
    }

    public static S2CBladeSlash decode(FriendlyByteBuf b) {
        return new S2CBladeSlash(
                b.readVarInt(),
                b.readDouble(), b.readDouble(), b.readDouble(),
                b.readFloat(), b.readFloat(), b.readFloat(),
                b.readFloat(), b.readBoolean(), b.readFloat(), b.readBoolean(), b.readLong());
    }

    public static void handle(S2CBladeSlash m, Supplier<NetworkEvent.Context> s) {
        s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BladeSlashRenderer.spawn(m)));
        s.get().setPacketHandled(true);
    }
}
