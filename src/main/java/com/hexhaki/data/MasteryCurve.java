package com.hexhaki.data;

/**
 * One deterministic curve shared by gameplay, commands and the UI.
 * Mastery is a derived level; XP is the durable progression currency.
 */
public final class MasteryCurve {
    public static final int MAX_LEVEL = 1000;
    public static final long MAX_XP = xpForLevel(MAX_LEVEL);

    private MasteryCurve() {}

    public static long xpForLevel(int level) {
        long value = Math.max(0, Math.min(MAX_LEVEL, level));
        return 40L * value + (3L * value * value) / 10L;
    }

    public static int levelForXp(long xp) {
        long bounded = Math.max(0L, Math.min(MAX_XP, xp));
        int low = 0;
        int high = MAX_LEVEL;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (xpForLevel(middle) <= bounded) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    public static long intoLevel(long xp) {
        int level = levelForXp(xp);
        return Math.max(0L, xp - xpForLevel(level));
    }

    public static long nextLevelCost(long xp) {
        int level = levelForXp(xp);
        if (level >= MAX_LEVEL) return 0L;
        return xpForLevel(level + 1) - xpForLevel(level);
    }

    public static int trainingXp(HakiType type, int units, HakiRank rank) {
        int base = switch (type) {
            case ARMAMENT -> 14;
            case OBSERVATION -> 17;
            case CONQUEROR -> 22;
        };
        float lateGameResistance = Math.max(.42f, 1f - rank.tier() * .065f);
        return Math.max(1, Math.round(base * Math.max(1, units) * lateGameResistance));
    }
}
