package com.hexhaki.fruit;

/**
 * The two mutually exclusive combat stances a HexHaki player can stand in.
 *
 * <p>Exactly one mode owns the ability hotkeys at any moment. This is what keeps a single
 * keypress from ever reaching both a Haki technique and a Devil Fruit technique: input routing
 * reads the mode first and dispatches into one branch only.
 */
public enum BattleMode {
    /** The pre-existing Haki controls, unchanged. */
    HAKI,
    /** Devil Fruit controls. Combat abilities stay locked until Gear 5 is active. */
    FRUIT;

    public BattleMode other() { return this == HAKI ? FRUIT : HAKI; }

    public static BattleMode byOrdinal(int ordinal) {
        BattleMode[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
