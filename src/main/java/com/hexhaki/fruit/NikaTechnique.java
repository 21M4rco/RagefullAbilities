package com.hexhaki.fruit;

import java.util.Locale;

/**
 * The Gear 5 repertoire of the Hito Hito no Mi, Model: Nika.
 *
 * <p><b>Research cutoff: September 2026.</b> Every entry carries a {@link Provenance} so a reader
 * can tell a manga-named technique from an on-panel action that was never named, and from a
 * mechanic that exists only because Minecraft needs it. Nothing here is a fan invention presented
 * as canon, and translation variants (Mogura Pistol / Mole Pistol, Fusen / Balloon) are folded
 * into a single entry rather than duplicated into two abilities.
 *
 * <p>There are only seven ability bindings. Seven techniques are {@link Activation#KEYED}; three
 * are {@link Activation#CONTEXTUAL} and share a binding with a keyed technique, with the situation
 * choosing between them; four are {@link Activation#PASSIVE} and need no input at all.
 *
 * <p>Balance figures are deliberate Minecraft adaptations. Damage is in half-hearts, ranges in
 * blocks, cooldowns and recovery in ticks. They are read server-side only; the client never
 * decides whether a technique may fire.
 */
public enum NikaTechnique {

    // ------------------------------------------------------------------ keyed

    /** Gomu Gomu no Dawn Pistol. The plain extended punch; the baseline every other reach tunes against. */
    DAWN_PISTOL("Dawn Pistol", Provenance.NAMED, Kind.STRIKE, Activation.KEYED, NikaSlot.ATTACK,
            18.0, 7.0f, 30, 6f, 10),

    /** Gomu Gomu no Dawn Gatling. A burst of elastic punches along the same corridor. */
    DAWN_GATLING("Dawn Gatling", Provenance.NAMED, Kind.BARRAGE, Activation.KEYED, NikaSlot.OBSERVATION,
            14.0, 3.2f, 90, 18f, 34),

    /** Gomu Gomu no Dawn Whip. Rotating stretched-leg sweep, unwound from a twisted torso. */
    DAWN_WHIP("Dawn Whip", Provenance.NAMED, Kind.SWEEP, Activation.KEYED, NikaSlot.CONQUEROR,
            11.0, 11.0f, 140, 22f, 30),

    /** Gomu Gomu no Dawn Rocket. Rubberises a surface, then rides its rebound into a punch. */
    DAWN_ROCKET("Dawn Rocket", Provenance.NAMED, Kind.MOBILITY_STRIKE, Activation.KEYED, NikaSlot.ACOC,
            20.0, 12.0f, 130, 24f, 30),

    /** Gomu Gomu no Gigant. Proportional enlargement of the whole body, not a separate "gear". */
    GIGANT("Gigant", Provenance.NAMED, Kind.BODY, Activation.KEYED, NikaSlot.DOMINION,
            0.0, 0.0f, 400, 40f, 200),

    /** Gomu Gomu no Dawn Cymbal. Two enlarged hands compress a target flat, then throw it. */
    DAWN_CYMBAL("Dawn Cymbal", Provenance.NAMED, Kind.GRAB, Activation.KEYED, NikaSlot.STRIKE,
            6.0, 20.0f, 300, 36f, 50),

    /** Gomu Gomu no Bajrang Gun. The colossal descending fist. The finisher. */
    BAJRANG_GUN("Bajrang Gun", Provenance.NAMED, Kind.ULTIMATE, Activation.KEYED, NikaSlot.CONVERGENCE,
            34.0, 90.0f, 1800, 160f, 170),

    // ------------------------------------------------------------- contextual

    /**
     * Gomu Gomu no Dawn Stamp. Sole-first stomp down an extended leg. Shares the attack binding
     * with Dawn Pistol and wins it while the player is airborne.
     */
    DAWN_STAMP("Dawn Stamp", Provenance.NAMED, Kind.STRIKE, Activation.CONTEXTUAL, NikaSlot.ATTACK,
            13.0, 12.0f, 110, 18f, 24),

    /**
     * Gomu Gomu no Mogura Pistol (Mole Pistol). The punch enters the ground and the terrain carries
     * it, surfacing beneath a target rather than crossing open air. Shares the attack binding with
     * Dawn Pistol and wins it while the player is sneaking on solid ground.
     */
    MOLE_PISTOL("Mole Pistol", Provenance.NAMED, Kind.GROUND, Activation.CONTEXTUAL, NikaSlot.ATTACK,
            16.0, 13.0f, 160, 26f, 28),

    /**
     * Gomu Gomu no Dasshutsu Rocket (Escape Rocket). Anchor an arm and launch clear. Bound to the
     * Shift+Space combo, which Haki Leap already owns in Haki mode and which is free in fruit mode.
     */
    ESCAPE_ROCKET("Escape Rocket", Provenance.NAMED, Kind.MOBILITY, Activation.CONTEXTUAL, null,
            24.0, 0.0f, 200, 18f, 26),

    // ---------------------------------------------------------------- passive

    /**
     * Gomu Gomu no Fusen (Dawn Balloon). No input: while Gear 5 is active a heavy incoming blow
     * inflates the torso and returns the force to its source instead of being absorbed.
     */
    DAWN_BALLOON("Dawn Balloon", Provenance.NAMED, Kind.BODY, Activation.PASSIVE, null,
            0.0, 0.0f, 240, 20f, 80),

    /**
     * Gomu Gomu no Hara Senbei. No input: landing from height flattens the belly into a broad
     * sheet, cancelling the fall and cushioning anything that lands on it.
     */
    HARA_SENBEI("Hara Senbei", Provenance.NAMED, Kind.UTILITY, Activation.PASSIVE, null,
            0.0, 0.0f, 300, 24f, 120),

    /**
     * Gomu Gomu no Kaminari (Lightning). No input: during a thunderstorm with open sky, a bolt that
     * would strike nearby is caught in the hand and thrown at the nearest hostile instead.
     */
    LIGHTNING("Lightning", Provenance.NAMED, Kind.SPECIAL, Activation.PASSIVE, null,
            30.0, 22.0f, 360, 45f, 40),

    /**
     * Gomu Gomu no Star Gun. No input: a Gear 5 falling/critical hit lands as Star Gun, carrying
     * the star impact motif rather than an ordinary crit.
     */
    STAR_GUN("Star Gun", Provenance.NAMED, Kind.STRIKE, Activation.PASSIVE, null,
            9.0, 18.0f, 220, 34f, 26);

    // Deliberately absent, and why:
    //   Jump Rope (Gomu Gomu no Nawatobi) - its grab-and-swing loop duplicates Dawn Cymbal's
    //       supported-target rule and reaction without producing a distinct silhouette.
    //   Dawn Thor Rifle (ch. 1175) - depends on the Lightning grasp landing first; as a passive
    //       Lightning cannot reliably feed it, and it does not warrant one of the seven bindings.
    // Both remain documented in docs/ so the omission is a recorded decision, not an oversight.

    /** How well attested a technique's name is. */
    public enum Provenance {
        /** Named on panel in the manga or anime. */
        NAMED,
        /** Demonstrated on panel but never named; the label here is descriptive. */
        UNNAMED_DEMONSTRATED,
        /** No canon counterpart; exists to make the fruit playable in Minecraft. */
        MINECRAFT_ADAPTATION
    }

    /** Which underlying system drives the technique. Shared systems, distinct visible behaviour. */
    public enum Kind { STRIKE, BARRAGE, SWEEP, GROUND, MOBILITY, MOBILITY_STRIKE, BODY, GRAB, SPECIAL, UTILITY, ULTIMATE }

    /** How a technique is reached, given only seven ability bindings exist. */
    public enum Activation {
        /** Owns one of the seven ability bindings outright. */
        KEYED,
        /** Shares a binding with a keyed technique, or rides a movement combo; context decides. */
        CONTEXTUAL,
        /** No input. The server applies it when its trigger condition occurs. */
        PASSIVE
    }

    private final String display;
    private final Provenance provenance;
    private final Kind kind;
    private final Activation activation;
    private final NikaSlot slot;
    private final double range;
    private final float damage;
    private final int cooldownTicks;
    private final float energyCost;
    private final int recoveryTicks;

    NikaTechnique(String display, Provenance provenance, Kind kind, Activation activation, NikaSlot slot,
                  double range, float damage, int cooldownTicks, float energyCost, int recoveryTicks) {
        this.display = display;
        this.provenance = provenance;
        this.kind = kind;
        this.activation = activation;
        this.slot = slot;
        this.range = range;
        this.damage = damage;
        this.cooldownTicks = cooldownTicks;
        this.energyCost = energyCost;
        this.recoveryTicks = recoveryTicks;
    }

    public String display() { return display; }
    public Provenance provenance() { return provenance; }
    public Kind kind() { return kind; }
    public Activation activation() { return activation; }
    /** The binding this technique answers to, or null for passives and movement-combo techniques. */
    public NikaSlot slot() { return slot; }
    /** Reach in blocks of the fully extended limb, measured from the shoulder or hip. */
    public double range() { return range; }
    /** Impact damage in half-hearts before Haki and mastery scaling. */
    public float damage() { return damage; }
    public int cooldownTicks() { return cooldownTicks; }
    public float energyCost() { return energyCost; }
    /** Ticks of committed recovery after impact, during which no other technique may start. */
    public int recoveryTicks() { return recoveryTicks; }

    /** Persistent-data key for this technique's cooldown, matching the existing Haki convention. */
    public String cooldownKey() { return "nika" + name() + "CooldownUntil"; }

    /** Translation key for the HUD. */
    public String translationKey() { return "hexhaki.nika." + name().toLowerCase(Locale.ROOT); }

    /** True when the technique needs a living target it can actually take hold of. */
    public boolean needsGrabTarget() { return kind == Kind.GRAB; }

    /** The technique bound outright to a slot, or null if that slot has none. */
    public static NikaTechnique keyedFor(NikaSlot slot) {
        for (NikaTechnique technique : values()) {
            if (technique.activation == Activation.KEYED && technique.slot == slot) return technique;
        }
        return null;
    }
}
