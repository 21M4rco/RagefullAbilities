package com.hexhaki.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class HakiData {
    private static final String ROOT = "HexHaki";
    private static final String LEGACY_ROOT = "GrandLineHaki";
    private HakiData() {}

    public static CompoundTag root(Player player) {
        CompoundTag persistent = player.getPersistentData();
        // Rebranding must not wipe existing V22 progression. Copy the old save root once,
        // then use HexHaki as the canonical key from this build onward.
        if (!persistent.contains(ROOT)) {
            if (persistent.contains(LEGACY_ROOT)) persistent.put(ROOT, persistent.getCompound(LEGACY_ROOT).copy());
            else persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }

    /** First-join setup: HexHaki is opt-in/admin-enabled. New players start disabled and untrained. */
    public static void initialize(Player player) {
        CompoundTag tag = root(player);
        if (tag.getBoolean("initialized")) return;
        tag.putBoolean("initialized", true);
        if (!tag.contains("enabled")) tag.putBoolean("enabled", false);
        // Calling xp() performs the legacy level-to-XP migration instead of
        // erasing progress made with the supplied reference build.
        for (HakiType type : HakiType.values()) xp(player, type);
        if (!tag.contains("energy")) tag.putFloat("energy", 100f);
    }

    /** Per-player gate. Missing key intentionally means false. */
    public static boolean enabled(Player p) { return root(p).getBoolean("enabled"); }
    public static void enabled(Player p, boolean value) { root(p).putBoolean("enabled", value); }

    public static int mastery(Player p, HakiType type) {
        return MasteryCurve.levelForXp(xp(p, type));
    }

    public static long xp(Player p, HakiType type) {
        CompoundTag tag = root(p);
        String xpKey = "xp_" + type.name();
        if (!tag.contains(xpKey)) {
            // Seamless migration from the supplied reference JAR's level-only save format.
            int legacy = Math.max(0, Math.min(1000, tag.getInt("mastery_" + type.name())));
            tag.putLong(xpKey, MasteryCurve.xpForLevel(legacy));
        }
        return Math.max(0L, Math.min(MasteryCurve.MAX_XP, tag.getLong(xpKey)));
    }

    public static void setXp(Player p, HakiType type, long value) {
        CompoundTag tag = root(p);
        long bounded = Math.max(0L, Math.min(MasteryCurve.MAX_XP, value));
        tag.putLong("xp_" + type.name(), bounded);
        tag.putInt("mastery_" + type.name(), MasteryCurve.levelForXp(bounded));
    }

    public static void addXp(Player p, HakiType type, long value) {
        setXp(p, type, xp(p, type) + value);
    }

    /** Admin/test helper. Normal gameplay awards XP through HakiProgression. */
    public static void setMastery(Player p, HakiType type, int value) {
        setXp(p, type, MasteryCurve.xpForLevel(value));
    }

    /** Admin/test helper that moves exact mastery levels rather than bypassing the curve. */
    public static void addMastery(Player p, HakiType type, int value) {
        setMastery(p, type, mastery(p, type) + value);
    }

    public static int legend(Player p) { return Math.max(0, root(p).getInt("legend")); }
    public static void legend(Player p, int value) { root(p).putInt("legend", Math.max(0, Math.min(9999, value))); }
    public static void addLegend(Player p, int value) { legend(p, legend(p) + value); }
    public static boolean joyBoy(Player p) { return root(p).getBoolean("joyBoy"); }
    public static void joyBoy(Player p, boolean value) { root(p).putBoolean("joyBoy", value); }

    public static boolean flag(Player p, String key) { return root(p).getBoolean(key); }
    public static void flag(Player p, String key, boolean value) { root(p).putBoolean(key, value); }
    public static int integer(Player p, String key) { return root(p).getInt(key); }
    public static void integer(Player p, String key, int value) { root(p).putInt(key, value); }
    public static long longValue(Player p, String key) { return root(p).getLong(key); }
    public static void longValue(Player p, String key, long value) { root(p).putLong(key, value); }

    public static float maxEnergy(Player p) {
        float average = (mastery(p, HakiType.ARMAMENT) + mastery(p, HakiType.OBSERVATION) + mastery(p, HakiType.CONQUEROR)) / 3000f;
        // Capacity grows continuously with the existing three-path mastery system:
        // 100 energy at zero total mastery -> exactly 3000 at 1000/1000/1000.
        return 100f + 2900f * average;
    }

    /** Admin/test mode: the HUD stays full and every energy transaction succeeds without draining. */
    public static boolean boardEnabled(Player p) {
        CompoundTag tag = root(p);
        return !tag.contains("boardEnabled") || tag.getBoolean("boardEnabled");
    }
    public static void boardEnabled(Player p, boolean value) { root(p).putBoolean("boardEnabled", value); }

    public static boolean unlimitedEnergy(Player p) { return flag(p, "testUnlimitedEnergy"); }
    public static void unlimitedEnergy(Player p, boolean value) {
        flag(p, "testUnlimitedEnergy", value);
        if (value) root(p).putFloat("energy", maxEnergy(p));
    }

    public static float energy(Player p) {
        CompoundTag tag = root(p);
        if (!tag.contains("energy")) tag.putFloat("energy", 100f);
        if (unlimitedEnergy(p)) return maxEnergy(p);
        return Math.min(maxEnergy(p), tag.getFloat("energy"));
    }
    public static void energy(Player p, float value) {
        root(p).putFloat("energy", unlimitedEnergy(p) ? maxEnergy(p) : Math.max(0f, Math.min(maxEnergy(p), value)));
    }
    public static boolean consume(Player p, float amount) {
        if (unlimitedEnergy(p)) return true;
        float e = energy(p);
        if (e < amount) return false;
        energy(p, e - amount);
        return true;
    }

    public static ConquerorMode conquerorMode(Player p) {
        int i = integer(p, "conquerorMode");
        return ConquerorMode.values()[Math.floorMod(i, ConquerorMode.values().length)];
    }
    public static void conquerorMode(Player p, ConquerorMode mode) { integer(p, "conquerorMode", mode.ordinal()); }
}
