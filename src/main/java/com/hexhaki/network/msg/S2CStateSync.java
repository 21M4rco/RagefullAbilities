package com.hexhaki.network.msg;

import com.hexhaki.client.HakiClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record S2CStateSync(
        int armament, int observation, int conqueror,
        long armamentXp, long observationXp, long conquerorXp,
        float energy, float maxEnergy,
        boolean enabled, boolean joyBoy, boolean boardEnabled,
        int legend,
        boolean armamentOn, boolean observationOn, boolean acocOn, boolean dominionOn,
        int conquerorCharge, int ryoCharge, int convergenceCharge,
        int mode) {
    public static void encode(S2CStateSync m, FriendlyByteBuf b) {
        b.writeVarInt(m.armament); b.writeVarInt(m.observation); b.writeVarInt(m.conqueror);
        b.writeVarLong(m.armamentXp); b.writeVarLong(m.observationXp); b.writeVarLong(m.conquerorXp);
        b.writeFloat(m.energy); b.writeFloat(m.maxEnergy);
        b.writeBoolean(m.enabled); b.writeBoolean(m.joyBoy); b.writeBoolean(m.boardEnabled); b.writeVarInt(m.legend);
        b.writeBoolean(m.armamentOn); b.writeBoolean(m.observationOn); b.writeBoolean(m.acocOn); b.writeBoolean(m.dominionOn);
        b.writeVarInt(m.conquerorCharge); b.writeVarInt(m.ryoCharge); b.writeVarInt(m.convergenceCharge);
        b.writeVarInt(m.mode);
    }
    public static S2CStateSync decode(FriendlyByteBuf b) {
        return new S2CStateSync(
                b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarLong(), b.readVarLong(), b.readVarLong(),
                b.readFloat(), b.readFloat(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readVarInt(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt());
    }
    public static void handle(S2CStateSync m, Supplier<NetworkEvent.Context> s) {
        s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HakiClientState.accept(m)));
        s.get().setPacketHandled(true);
    }
}
