package com.hexhaki.network.msg;

import com.hexhaki.client.render.ConquerorShockScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Conqueror's pressure landing on a player's senses.
 *
 * @param power    0..1 release charge; drives how violent the spasm is and how fast sight fails
 * @param ticks    how long the victim's vision is compromised
 * @param blindAt  tick at which vision has failed completely
 */
public record S2CConquerorShock(float power, int ticks, int blindAt) {
    public static void encode(S2CConquerorShock m, FriendlyByteBuf b) {
        b.writeFloat(m.power); b.writeVarInt(m.ticks); b.writeVarInt(m.blindAt);
    }
    public static S2CConquerorShock decode(FriendlyByteBuf b) {
        return new S2CConquerorShock(b.readFloat(), b.readVarInt(), b.readVarInt());
    }
    public static void handle(S2CConquerorShock m, Supplier<NetworkEvent.Context> s) {
        s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ConquerorShockScreen.begin(m)));
        s.get().setPacketHandled(true);
    }
}
