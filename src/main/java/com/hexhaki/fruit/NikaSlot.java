package com.hexhaki.fruit;

/**
 * The seven ability inputs a Gear 5 player can reach.
 *
 * <p>Each constant names an <em>existing</em> HexHaki binding rather than a keyboard letter. The
 * defaults are only defaults: the player may rebind any of them in Minecraft's controls menu and
 * the fruit moveset follows the rebind, because routing is done by binding identity and never by
 * scanning for a hard-coded key code.
 */
public enum NikaSlot {
    /** Vanilla attack. Shared with the contextual strikes. */
    ATTACK("key.attack"),
    /** HexHaki "Observation" binding, default V. */
    OBSERVATION("key.hexhaki.observation"),
    /** HexHaki "Conqueror" binding, default G. */
    CONQUEROR("key.hexhaki.conqueror"),
    /** HexHaki "Advanced Haki" binding, default J. */
    ACOC("key.hexhaki.acoc"),
    /** HexHaki "Dominion" binding, default K. */
    DOMINION("key.hexhaki.dominion"),
    /** HexHaki "Sovereign Strike" binding, default L. */
    STRIKE("key.hexhaki.strike"),
    /** HexHaki "Convergence" binding, default M. */
    CONVERGENCE("key.hexhaki.convergence");

    private final String bindingKey;

    NikaSlot(String bindingKey) { this.bindingKey = bindingKey; }

    /** The translation key of the keybinding that owns this slot, for HUD display. */
    public String bindingKey() { return bindingKey; }

    public static NikaSlot byOrdinal(int ordinal) {
        NikaSlot[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
