package com.hexhaki.network.msg;

import com.hexhaki.client.vfx.HakiVfx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record S2CHakiVisual(int entityId, Visual visual, float power, int variant, long seed) {
    public enum Visual {
        ARMAMENT_ON,
        ARMAMENT_HIT,
        RYO_CHARGE,
        RYO_AURA,
        RYO_RELEASE,
        INTERNAL_HIT,
        OBSERVATION_PULSE,
        CONQUEROR_CHARGE,
        CONQUEROR_RELEASE,
        CONQUEROR_TAP_CONE,
        CONQUEROR_AFTERSHOCK,
        CONQUEROR_HIT,
        ACOC,
        DOMINION,
        KINGS_GRIP_LOCK,
        KINGS_GRIP_WINDUP,
        KINGS_GRIP_IMPACT,
        THRAGG_DRAW,
        THRAGG_UPPERCUT,
        THRAGG_BLINK,
        THRAGG_SLAM,
        THRAGG_TRAIL,
        SOVEREIGN_LOCK,
        SOVEREIGN_IMPACT,
        CONVERGENCE_PUNCH,
        CONVERGENCE_CHARGE,
        GALAXY_FIST_START,
        GALAXY_FIST_INTENSIFY,
        GALAXY_FIST_ABSORB,
        GALAXY_FIST_STOP,
        GALAXY_ASCENT,
        CONVERGENCE_RELEASE,
        CONVERGENCE_HIT,
        GALAXY_IMPACT,
        JOYBOY_AWAKENING,
        LAST_STAND_AURA,
        LAST_STAND_BLAST,
        LAST_STAND_FINISH,
        LAST_STAND_HIT
    }
    public static void encode(S2CHakiVisual m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); b.writeEnum(m.visual); b.writeFloat(m.power); b.writeVarInt(m.variant); b.writeLong(m.seed); }
    public static S2CHakiVisual decode(FriendlyByteBuf b) { return new S2CHakiVisual(b.readVarInt(), b.readEnum(Visual.class), b.readFloat(), b.readVarInt(), b.readLong()); }
    public static void handle(S2CHakiVisual m, Supplier<NetworkEvent.Context> s) {
        s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HakiVfx.accept(m)));
        s.get().setPacketHandled(true);
    }
}
