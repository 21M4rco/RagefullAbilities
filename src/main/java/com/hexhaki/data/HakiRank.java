package com.hexhaki.data;

public enum HakiRank {
    DORMANT(0),
    AWAKENED(40),
    INITIATE(120),
    TRAINED(260),
    ADEPT(430),
    ADVANCED(610),
    MASTER(780),
    SUPREME(910),
    PINNACLE(1000);

    public final int min;
    HakiRank(int min) { this.min = min; }

    public static HakiRank of(int mastery) {
        HakiRank result = DORMANT;
        for (HakiRank rank : values()) if (mastery >= rank.min) result = rank;
        return result;
    }

    public int tier() { return ordinal(); }
    public int nextMin() { return ordinal() == values().length - 1 ? 1000 : values()[ordinal() + 1].min; }
    public float visualScale() { return 0.35f + 0.085f * tier(); }
    public float armamentDamage() { return 0.75f + 0.65f * tier(); }
    public float armamentReduction() { return Math.min(.42f, .035f + .045f * tier()); }
    public double observationRange() { return 8.0 + 4.5 * tier(); }
    public float conquerorScale() { return .35f + .10f * tier(); }
}
