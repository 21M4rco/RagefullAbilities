package com.hexhaki.fruit;

import java.util.Locale;

/**
 * The Gear 5 repertoire of the Hito Hito no Mi, Model: Nika.
 *
 * <p><b>Research cutoff: September 2026.</b> Every entry carries a {@link Provenance} value so a
 * reader can tell a manga-named technique from an on-panel action that was never named and from a
 * mechanic that exists only because Minecraft needs it. Nothing here is a fan invention presented
 * as canon, and translation variants (Mogura Pistol / Mole Pistol, Fusen / Balloon) are folded into
 * one entry rather than duplicated into two abilities.
 *
 * <p>Balance figures are deliberate Minecraft adaptations. Damage is in half-hearts, ranges are in
 * blocks, cooldowns and windows are in ticks. They are read by the server only; the client never
 * decides whether a technique may fire.
 */
public enum NikaTechnique {

    // ---------------------------------------------------------------- strikes

    /** Gomu Gomu no Dawn Pistol. The plain extended punch; the baseline every other reach tunes against. */
    DAWN_PISTOL("Dawn Pistol", Provenance.NAMED, Kind.STRIKE,
            18.0, 7.0f, 30, 6f, 10),

    /** Gomu Gomu no Dawn Gatling. A burst of elastic punches along the same corridor. */
    DAWN_GATLING("Dawn Gatling", Provenance.NAMED, Kind.BARRAGE,
            14.0, 3.2f, 90, 18f, 34),

    /** Gomu Gomu no Dawn Whip. Rotating stretched-leg sweep; see NikaWhip for the pose work. */
    DAWN_WHIP("Dawn Whip", Provenance.NAMED, Kind.SWEEP,
            11.0, 11.0f, 140, 22f, 30),

    /** Gomu Gomu no Dawn Stamp. Sole-first stomp along an extended leg. */
    DAWN_STAMP("Dawn Stamp", Provenance.NAMED, Kind.STRIKE,
            13.0, 12.0f, 110, 18f, 24),

    /** Gomu Gomu no Star Gun / White Star Gun. Heavy single punch with a star impact motif. */
    STAR_GUN("Star Gun", Provenance.NAMED, Kind.STRIKE,
            9.0, 18.0f, 220, 34f, 26),

    /**
     * Gomu Gomu no Mogura Pistol (Mole Pistol). The punch enters the ground and the terrain itself
     * carries it: it surfaces beneath a target rather than travelling through open air.
     */
    MOLE_PISTOL("Mole Pistol", Provenance.NAMED, Kind.GROUND,
            16.0, 13.0f, 160, 26f, 28),

    /** Gomu Gomu no Dawn Rocket. Rubberises a surface, then rides its rebound into a punch. */
    DAWN_ROCKET("Dawn Rocket", Provenance.NAMED, Kind.MOBILITY_STRIKE,
            20.0, 12.0f, 130, 24f, 30),

    /**
     * Gomu Gomu no Dawn Thor Rifle (ch. 1175, Elbaf). Twisted arm plus a grasped bolt; the matured
     * form of the Dressrosa Thor Rifle. Requires Lightning's grasp to have landed first.
     */
    DAWN_THOR_RIFLE("Dawn Thor Rifle", Provenance.NAMED, Kind.STRIKE,
            22.0, 26.0f, 420, 55f, 44),

    // ------------------------------------------------------------ body volume

    /** Gomu Gomu no Gigant. Proportional enlargement of the whole body, not a separate "gear". */
    GIGANT("Gigant", Provenance.NAMED, Kind.BODY,
            0.0, 0.0f, 400, 40f, 200),

    /** Gomu Gomu no Fusen / Dawn Balloon. Inflate; incoming force is returned elastically. */
    DAWN_BALLOON("Dawn Balloon", Provenance.NAMED, Kind.BODY,
            0.0, 0.0f, 240, 20f, 80),

    /**
     * Gomu Gomu no Hara Senbei. The belly flattens into a broad sheet and becomes a landing
     * cushion for whatever falls onto it. Not an attack.
     */
    HARA_SENBEI("Hara Senbei", Provenance.NAMED, Kind.UTILITY,
            0.0, 0.0f, 300, 24f, 120),

    // ---------------------------------------------------- opponent-dependent

    /**
     * Gomu Gomu no Dawn Cymbal. Two enlarged hands compress a target flat, then throw it.
     * Needs a supported living target; fails audibly rather than silently when there is none.
     */
    DAWN_CYMBAL("Dawn Cymbal", Provenance.NAMED, Kind.GRAB,
            6.0, 20.0f, 300, 36f, 50),

    /**
     * Gomu Gomu no Nawatobi (Jump Rope). Grab a target and swing it as a skipping rope, striking
     * everything the arc passes through. Target-dependent in the same way as Dawn Cymbal.
     */
    JUMP_ROPE("Jump Rope", Provenance.NAMED, Kind.GRAB,
            6.0, 9.0f, 340, 30f, 70),

    /**
     * Gomu Gomu no Kaminari (Lightning). Physically take hold of a bolt and throw it. Requires a
     * thunderstorm and open sky, which is the Minecraft-legible reading of "reach into the clouds".
     */
    LIGHTNING("Lightning", Provenance.NAMED, Kind.SPECIAL,
            30.0, 22.0f, 360, 45f, 40),

    /** Gomu Gomu no Dasshutsu Rocket (Escape Rocket). Anchor an arm and launch away from danger. */
    ESCAPE_ROCKET("Escape Rocket", Provenance.NAMED, Kind.MOBILITY,
            24.0, 0.0f, 200, 18f, 26),

    /** Gomu Gomu no Bajrang Gun. The colossal descending fist. The finisher. */
    BAJRANG_GUN("Bajrang Gun", Provenance.NAMED, Kind.ULTIMATE,
            34.0, 90.0f, 1800, 160f, 170);

    /** How well attested a technique's name is. */
    public enum Provenance {
        /** Named on panel in the manga or anime. */
        NAMED,
        /** Demonstrated on panel but never given a name; our label is descriptive. */
        UNNAMED_DEMONSTRATED,
        /** No canon counterpart; exists to make the fruit playable in Minecraft. */
        MINECRAFT_ADAPTATION
    }

    /** Which underlying system drives the technique. Shared systems, distinct visible behaviour. */
    public enum Kind { STRIKE, BARRAGE, SWEEP, GROUND, MOBILITY, MOBILITY_STRIKE, BODY, GRAB, SPECIAL, UTILITY, ULTIMATE }

    private final String display;
    private final Provenance provenance;
    private final Kind kind;
    private final double range;
    private final float damage;
    private final int cooldownTicks;
    private final float energyCost;
    private final int recoveryTicks;

    NikaTechnique(String display, Provenance provenance, Kind kind,
                  double range, float damage, int cooldownTicks, float energyCost, int recoveryTicks) {
        this.display = display;
        this.provenance = provenance;
        this.kind = kind;
        this.range = range;
        this.damage = damage;
        this.cooldownTicks = cooldownTicks;
        this.energyCost = energyCost;
        this.recoveryTicks = recoveryTicks;
    }

    public String display() { return display; }
    public Provenance provenance() { return provenance; }
    public Kind kind() { return kind; }
    /** Reach in blocks of the fully extended limb, measured from the shoulder or hip. */
    public double range() { return range; }
    /** Impact damage in half-hearts before Haki and mastery scaling. */
    public float damage() { return damage; }
    public int cooldownTicks() { return cooldownTicks; }
    public float energyCost() { return energyCost; }
    /** Ticks of committed recovery after the strike lands, during which no other technique starts. */
    public int recoveryTicks() { return recoveryTicks; }

    /** Persistent-data key for this technique's cooldown, matching the existing Haki convention. */
    public String cooldownKey() { return "nika" + name() + "CooldownUntil"; }

    /** Translation key for the HUD and the loadout screen. */
    public String translationKey() { return "hexhaki.nika." + name().toLowerCase(Locale.ROOT); }

    /** True when the technique needs a living target it can actually take hold of. */
    public boolean needsGrabTarget() { return kind == Kind.GRAB; }
}
