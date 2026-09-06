package com.hexhaki.network.msg;

import com.hexhaki.client.vfx.HakiVfx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * A one-shot effect at a world position, with no entity attached.
 *
 * <p>Every other visual in the mod is addressed by entity id, which means the client has to resolve
 * that entity before it can draw anything, and the packet has to reach players who track it. That
 * is fine for an aura on a body and wrong for an explosion: the Haki Grip's crater kept not
 * appearing because it depended on the victim being resolvable on every viewer's client at the one
 * tick it fired. A blast happens at a <i>place</i>, so this addresses a place — sent to everyone
 * near the point, and drawn from coordinates that cannot go stale.
 */
public record S2CWorldFx(int kind, float power, int variant, double x, double y, double z, long seed) {
    /** Haki Grip: the victim arrives. */
    public static final int THRAGG_CRATER = 0;

    public static void encode(S2CWorldFx m, FriendlyByteBuf b) {
        b.writeVarInt(m.kind);
        b.writeFloat(m.power);
        b.writeVarInt(m.variant);
        b.writeDouble(m.x);
        b.writeDouble(m.y);
        b.writeDouble(m.z);
        b.writeLong(m.seed);
    }

    public static S2CWorldFx decode(FriendlyByteBuf b) {
        return new S2CWorldFx(b.readVarInt(), b.readFloat(), b.readVarInt(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readLong());
    }

    public static void handle(S2CWorldFx m, Supplier<NetworkEvent.Context> s) {
        s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> HakiVfx.acceptWorld(m)));
        s.get().setPacketHandled(true);
    }
}
