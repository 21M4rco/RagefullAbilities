package com.hexhaki.network.msg;

import com.hexhaki.client.render.PerceptionRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Observation 1.1 snapshot. Everything in this packet is server-authored so the client only renders
 * what the server has actually predicted: sensed presences, probable future positions, projectile
 * paths and imminent attack cues. Future Sight is a short danger-triggered state, never a permanent
 * client-side wallhack.
 */
public record S2CPerception(
        List<Entry> entries,
        List<ProjectileForecast> projectiles,
        List<ThreatCue> threats,
        int lifeTicks,
        boolean futureSightActive,
        int futureSightTicks) {

    /** hidden=true means the position has already been intentionally softened/quantized by server. */
    public record Entry(int entityId, byte classification, float threat,
                        float x, float y, float z,
                        float predictX, float predictY, float predictZ,
                        float confidence, boolean hidden) {}

    /** Straight-line probable projectile path; it is prediction, not a promise that the projectile cannot curve. */
    public record ProjectileForecast(int entityId,
                                     float startX, float startY, float startZ,
                                     float velocityX, float velocityY, float velocityZ,
                                     int horizonTicks, float danger) {}

    /** kind: 0 melee/intent, 1 projectile. Position is included so off-screen cues work even if entity is not tracked client-side. */
    public record ThreatCue(int entityId, byte kind, float urgency, int ticksToImpact,
                            float x, float y, float z) {}

    public static void encode(S2CPerception m, FriendlyByteBuf b) {
        b.writeVarInt(m.entries().size());
        for (Entry e : m.entries()) {
            b.writeVarInt(e.entityId());
            b.writeByte(e.classification());
            b.writeFloat(e.threat());
            b.writeFloat(e.x()); b.writeFloat(e.y()); b.writeFloat(e.z());
            b.writeFloat(e.predictX()); b.writeFloat(e.predictY()); b.writeFloat(e.predictZ());
            b.writeFloat(e.confidence());
            b.writeBoolean(e.hidden());
        }

        b.writeVarInt(m.projectiles().size());
        for (ProjectileForecast p : m.projectiles()) {
            b.writeVarInt(p.entityId());
            b.writeFloat(p.startX()); b.writeFloat(p.startY()); b.writeFloat(p.startZ());
            b.writeFloat(p.velocityX()); b.writeFloat(p.velocityY()); b.writeFloat(p.velocityZ());
            b.writeVarInt(p.horizonTicks());
            b.writeFloat(p.danger());
        }

        b.writeVarInt(m.threats().size());
        for (ThreatCue t : m.threats()) {
            b.writeVarInt(t.entityId());
            b.writeByte(t.kind());
            b.writeFloat(t.urgency());
            b.writeVarInt(t.ticksToImpact());
            b.writeFloat(t.x()); b.writeFloat(t.y()); b.writeFloat(t.z());
        }

        b.writeVarInt(m.lifeTicks());
        b.writeBoolean(m.futureSightActive());
        b.writeVarInt(m.futureSightTicks());
    }

    public static S2CPerception decode(FriendlyByteBuf b) {
        int entryCount = Math.min(128, Math.max(0, b.readVarInt()));
        List<Entry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(new Entry(
                    b.readVarInt(), b.readByte(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readBoolean()));
        }

        int projectileCount = Math.min(32, Math.max(0, b.readVarInt()));
        List<ProjectileForecast> projectiles = new ArrayList<>(projectileCount);
        for (int i = 0; i < projectileCount; i++) {
            projectiles.add(new ProjectileForecast(
                    b.readVarInt(),
                    b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readVarInt(), b.readFloat()));
        }

        int threatCount = Math.min(24, Math.max(0, b.readVarInt()));
        List<ThreatCue> threats = new ArrayList<>(threatCount);
        for (int i = 0; i < threatCount; i++) {
            threats.add(new ThreatCue(
                    b.readVarInt(), b.readByte(), b.readFloat(), b.readVarInt(),
                    b.readFloat(), b.readFloat(), b.readFloat()));
        }

        return new S2CPerception(entries, projectiles, threats,
                b.readVarInt(), b.readBoolean(), b.readVarInt());
    }

    public static void handle(S2CPerception m, Supplier<NetworkEvent.Context> s) {
        s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PerceptionRenderer.update(m)));
        s.get().setPacketHandled(true);
    }
}
