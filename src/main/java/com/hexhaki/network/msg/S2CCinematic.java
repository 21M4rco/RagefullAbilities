package com.hexhaki.network.msg;

import com.hexhaki.client.cinematic.CinematicController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Named client cinematic with an optional server-authored starting view orientation.
 *
 * @param focusId entity the shot is composed around, or -1 for the viewer's own body. A shared
 *                focus is what lets two participants standing forty blocks apart watch the same
 *                thing: without it each camera rides its own body, so the attacker's shot follows
 *                the attacker while the whole point of the sequence is happening to someone else.
 */
public record S2CCinematic(String sequence, float yaw, float pitch, boolean forceOrientation, int focusId) {
    public S2CCinematic(String sequence) { this(sequence, 0f, 0f, false, -1); }
    public S2CCinematic(String sequence, float yaw, float pitch, boolean forceOrientation) {
        this(sequence, yaw, pitch, forceOrientation, -1);
    }

    public static void encode(S2CCinematic m, FriendlyByteBuf b) {
        b.writeUtf(m.sequence, 64);
        b.writeFloat(m.yaw);
        b.writeFloat(m.pitch);
        b.writeBoolean(m.forceOrientation);
        b.writeVarInt(m.focusId);
    }
    public static S2CCinematic decode(FriendlyByteBuf b) {
        return new S2CCinematic(b.readUtf(64), b.readFloat(), b.readFloat(), b.readBoolean(), b.readVarInt());
    }
    public static void handle(S2CCinematic m, Supplier<NetworkEvent.Context> s) {
        s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> CinematicController.startNamed(m.sequence, m.yaw, m.pitch,
                        m.forceOrientation, m.focusId)));
        s.get().setPacketHandled(true);
    }
}
