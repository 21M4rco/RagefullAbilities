package com.hexhaki.network.msg;

import com.hexhaki.client.RemoteHakiStates;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record S2CEntityState(int entityId, boolean armament, boolean acoc, boolean ryuo, int armamentMastery, int conquerorMastery) {
    public static void encode(S2CEntityState m,FriendlyByteBuf b){b.writeVarInt(m.entityId);b.writeBoolean(m.armament);b.writeBoolean(m.acoc);b.writeBoolean(m.ryuo);b.writeVarInt(m.armamentMastery);b.writeVarInt(m.conquerorMastery);}
    public static S2CEntityState decode(FriendlyByteBuf b){return new S2CEntityState(b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readVarInt());}
    public static void handle(S2CEntityState m,Supplier<NetworkEvent.Context> s){s.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->RemoteHakiStates.accept(m)));s.get().setPacketHandled(true);}
}
