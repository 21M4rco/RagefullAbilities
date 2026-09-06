package com.hexhaki.gameplay;

import com.hexhaki.data.HakiData;
import com.hexhaki.data.HakiRank;
import com.hexhaki.data.HakiUnlocks;
import com.hexhaki.data.HakiType;
import com.hexhaki.data.MasteryCurve;
import com.hexhaki.network.HakiNetwork;
import com.hexhaki.network.msg.S2CCinematic;
import com.hexhaki.network.msg.S2CHakiVisual;
import com.hexhaki.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class HakiProgression {
    public static final int JOY_BOY_LEGEND_REQUIREMENT = HakiUnlocks.JOY_BOY_LEGEND;
    public static final int CONVERGENCE_LEGEND_REQUIREMENT = HakiUnlocks.GALAXY_FULL_LEGEND;
    public static final int CONQUEROR_ARMAMENT_REQUIREMENT = HakiUnlocks.CONQUEROR_AWAKEN_ARMAMENT;
    public static final int CONQUEROR_OBSERVATION_REQUIREMENT = HakiUnlocks.CONQUEROR_AWAKEN_OBSERVATION;
    public static final int ACOC_ARMAMENT_REQUIREMENT = HakiUnlocks.ADVANCED_HAKI_ARMAMENT;
    public static final int ACOC_CONQUEROR_REQUIREMENT = HakiUnlocks.ADVANCED_HAKI_CONQUEROR;
    public static final int WIFI_HAKI_ARMAMENT_REQUIREMENT = HakiUnlocks.WIFI_HAKI_ARMAMENT;
    public static final int WIFI_HAKI_OBSERVATION_REQUIREMENT = HakiUnlocks.WIFI_HAKI_OBSERVATION;
    public static final int WIFI_HAKI_CONQUEROR_REQUIREMENT = HakiUnlocks.WIFI_HAKI_CONQUEROR;

    private HakiProgression() {}

    public static boolean award(ServerPlayer p, HakiType type, int amount, String source, int cooldownTicks) {
        if (!HakiData.enabled(p) || amount <= 0) return false;
        // Conqueror XP cannot be farmed before the user's will has actually awakened.
        if (type == HakiType.CONQUEROR && !conquerorAwakened(p)) return false;

        long now = p.level().getGameTime();
        String key = "xp_cd_" + type.name().toLowerCase() + "_" + source;
        if (now < HakiData.longValue(p, key)) return false;
        HakiData.longValue(p, key, now + Math.max(0, cooldownTicks));

        boolean awakeningBefore = conquerorAwakened(p);
        int before = HakiData.mastery(p, type);
        HakiRank oldRank = HakiRank.of(before);
        HakiData.addXp(p, type, MasteryCurve.trainingXp(type, amount, oldRank));
        int after = HakiData.mastery(p, type);
        HakiRank newRank = HakiRank.of(after);
        if (newRank != oldRank) {
            p.displayClientMessage(Component.literal(type.display + " mastery: " + pretty(newRank)), true);
        }
        if (!awakeningBefore && conquerorAwakened(p)) {
            p.displayClientMessage(Component.literal("CONQUEROR'S HAKI AWAKENED  ·  Conqueror blast unlocked [G]"), false);
        }
        if (after >= 1000) tryUnlockJoyBoy(p);
        return HakiData.mastery(p, type) > before;
    }

    /**
     * Survival progression is intentionally path-specific:
     * - simply surviving Minecraft days and moving through the world trains Observation;
     * - combat events train Armament in HakiEvents;
     * - Conqueror only grows from actually imposing King's Haki after it awakens.
     */
    public static void tickPassive(ServerPlayer p) {
        if (!HakiData.enabled(p) || p.tickCount % 20 != 0) return;
        CompoundTag tag = HakiData.root(p);
        long day = Math.max(0L, p.level().getDayTime() / 24000L);
        if (!tag.contains("progressionDay")) {
            tag.putLong("progressionDay", day);
        } else if (day > tag.getLong("progressionDay")) {
            tag.putLong("progressionDay", day);
            award(p, HakiType.OBSERVATION, 8, "survived_day", 0);
        }

        double x = p.getX(), y = p.getY(), z = p.getZ();
        if (!tag.contains("progressX")) {
            tag.putDouble("progressX", x); tag.putDouble("progressY", y); tag.putDouble("progressZ", z);
            tag.putDouble("exploreDistance", 0D);
            return;
        }
        double dx = x - tag.getDouble("progressX");
        double dy = y - tag.getDouble("progressY");
        double dz = z - tag.getDouble("progressZ");
        tag.putDouble("progressX", x); tag.putDouble("progressY", y); tag.putDouble("progressZ", z);
        double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Ignore teleports/respawns; exploration means actually traversing the world.
        if (moved > 0.05D && moved < 24D) {
            double accumulated = tag.getDouble("exploreDistance") + moved;
            while (accumulated >= 80D) {
                award(p, HakiType.OBSERVATION, 2, "exploration", 0);
                accumulated -= 80D;
            }
            tag.putDouble("exploreDistance", accumulated);
        }
    }

    public static boolean conquerorAwakened(ServerPlayer p) {
        return HakiData.mastery(p, HakiType.ARMAMENT) >= CONQUEROR_ARMAMENT_REQUIREMENT
                && HakiData.mastery(p, HakiType.OBSERVATION) >= CONQUEROR_OBSERVATION_REQUIREMENT;
    }

    public static boolean advancedHakiUnlocked(ServerPlayer p) {
        return HakiData.mastery(p, HakiType.ARMAMENT) >= ACOC_ARMAMENT_REQUIREMENT
                && HakiData.mastery(p, HakiType.CONQUEROR) >= ACOC_CONQUEROR_REQUIREMENT;
    }

    public static boolean wifiHakiUnlocked(ServerPlayer p) {
        return HakiData.mastery(p, HakiType.ARMAMENT) >= WIFI_HAKI_ARMAMENT_REQUIREMENT
                && HakiData.mastery(p, HakiType.OBSERVATION) >= WIFI_HAKI_OBSERVATION_REQUIREMENT
                && HakiData.mastery(p, HakiType.CONQUEROR) >= WIFI_HAKI_CONQUEROR_REQUIREMENT;
    }

    public static void legend(ServerPlayer p, int amount, String source, int cooldownTicks) {
        if (!HakiData.enabled(p) || amount <= 0) return;
        long now = p.level().getGameTime();
        String key = "legend_cd_" + source;
        if (now < HakiData.longValue(p, key)) return;
        HakiData.longValue(p, key, now + Math.max(0, cooldownTicks));
        HakiData.addLegend(p, amount);
        tryUnlockJoyBoy(p);
    }

    public static boolean kingsGripUnlocked(ServerPlayer p) {
        return HakiData.mastery(p, HakiType.ARMAMENT) >= HakiUnlocks.KINGS_GRIP_ARMAMENT
                && HakiData.mastery(p, HakiType.OBSERVATION) >= HakiUnlocks.KINGS_GRIP_OBSERVATION
                && HakiData.mastery(p, HakiType.CONQUEROR) >= HakiUnlocks.KINGS_GRIP_CONQUEROR;
    }

    public static boolean convergenceUnlocked(ServerPlayer p) {
        return HakiData.joyBoy(p) || (HakiData.mastery(p, HakiType.ARMAMENT) >= HakiUnlocks.GALAXY_FULL_ARMAMENT
                && HakiData.mastery(p, HakiType.OBSERVATION) >= HakiUnlocks.GALAXY_FULL_OBSERVATION
                && HakiData.mastery(p, HakiType.CONQUEROR) >= HakiUnlocks.GALAXY_FULL_CONQUEROR
                && HakiData.legend(p) >= CONVERGENCE_LEGEND_REQUIREMENT);
    }

    public static boolean qualifiesJoyBoy(ServerPlayer p) {
        return HakiData.mastery(p, HakiType.ARMAMENT) >= HakiUnlocks.JOY_BOY_MASTERY
                && HakiData.mastery(p, HakiType.OBSERVATION) >= HakiUnlocks.JOY_BOY_MASTERY
                && HakiData.mastery(p, HakiType.CONQUEROR) >= HakiUnlocks.JOY_BOY_MASTERY
                && HakiData.legend(p) >= JOY_BOY_LEGEND_REQUIREMENT;
    }

    public static boolean tryUnlockJoyBoy(ServerPlayer p) {
        if (HakiData.joyBoy(p) || !qualifiesJoyBoy(p)) return false;
        HakiData.joyBoy(p, true);
        HakiData.energy(p, HakiData.maxEnergy(p));
        p.displayClientMessage(Component.literal("JOY BOY LEVEL HAKI AWAKENED"), false);
        HakiServerController.animate(p, "supreme_conqueror_release", 2);
        HakiServerController.visual(p, S2CHakiVisual.Visual.JOYBOY_AWAKENING, 1f, 1);
        HakiNetwork.to(p, new S2CCinematic("supreme_conqueror"));
        HakiServerController.delayedSound(p, ModSounds.CONQUEROR_SUPREME_SNAP.get(), 3f, .92f);
        HakiServerController.delayedSound(p, ModSounds.CONQUEROR_SUPREME_RUMBLE.get(), 2.7f, .72f);
        return true;
    }

    private static String pretty(HakiRank rank) {
        return rank.name().replace('_', ' ');
    }
}
