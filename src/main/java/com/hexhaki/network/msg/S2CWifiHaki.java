package com.hexhaki.network.msg;

import com.hexhaki.client.vfx.HakiVfx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-authored long-range Conqueror discharge. During WiFi Haki channeling the server
 * refreshes this packet with new endpoints/seed/pulse index so every viewer sees the same living,
 * crooked lightning connection without trusting client packet spam. */
public record S2CWifiHaki(
        int sourceId, int targetId,
        double startX, double startY, double startZ,
        double endX, double endY, double endZ,
        float power, int travelTicks, int pulseIndex, int beamCount, long seed) {

    public static void encode(S2CWifiHaki m, FriendlyByteBuf b) {
        b.writeVarInt(m.sourceId);
        b.writeVarInt(m.targetId);
        b.writeDouble(m.startX); b.writeDouble(m.startY); b.writeDouble(m.startZ);
        b.writeDouble(m.endX); b.writeDouble(m.endY); b.writeDouble(m.endZ);
        b.writeFloat(m.power);
        b.writeVarInt(m.travelTicks);
        b.writeVarInt(m.pulseIndex);
        b.writeVarInt(m.beamCount);
        b.writeLong(m.seed);
    }

    public static S2CWifiHaki decode(FriendlyByteBuf b) {
        return new S2CWifiHaki(
                b.readVarInt(), b.readVarInt(),
                b.readDouble(), b.readDouble(), b.readDouble(),
                b.readDouble(), b.readDouble(), b.readDouble(),
                b.readFloat(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong());
    }

    public static void handle(S2CWifiHaki m, Supplier<NetworkEvent.Context> s) {
        s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HakiVfx.acceptWifiHaki(m)));
        s.get().setPacketHandled(true);
    }
}
