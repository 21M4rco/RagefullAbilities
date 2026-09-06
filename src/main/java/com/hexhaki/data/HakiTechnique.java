package com.hexhaki.data;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** V2 technique ladder: unlock the toys during progression, then let mastery make them terrifying. */
public enum HakiTechnique {
    ARM_CONDITIONING(HakiType.ARMAMENT, 0, "Physical Conditioning", "Fight living targets to build the body before coating awakens."),
    ARM_REINFORCEMENT(HakiType.ARMAMENT, HakiUnlocks.ARMAMENT_COATING, "Reinforcement", "[R] Armament coating. Coated hits, guarding and Haki techniques train Armament mastery."),
    ARM_BLADE_CUT(HakiType.ARMAMENT, HakiUnlocks.ARMAMENT_COATING, "Haki Blade Cut", "With [R] active, vanilla sword swings launch Haki crescents. The normal Minecraft swing stays untouched."),
    ARM_HARDENING(HakiType.ARMAMENT, 120, "Hardening", "Denser coating, stronger defense and heavier impact sparks."),
    ARM_LIGHTNING(HakiType.ARMAMENT, 180, "Haki Lightning", "Coated strikes throw black lightning outward from the body instead of dropping it from the sky, with forked arcs that thin out as they branch."),
    ARM_HAKI_LEAP(HakiType.ARMAMENT, HakiUnlocks.HAKI_LEAP_ARMAMENT, "Haki Leap", "[Shift + Space] Charge a forward Haki launch. Successful use trains Armament."),
    ARM_FULL_COATING(HakiType.ARMAMENT, 300, "Advanced Hardening", "Stable full coating with stronger black/red pressure and better efficiency."),
    ARM_EMISSION(HakiType.ARMAMENT, HakiUnlocks.RYUO_ARMAMENT, "Flowing Armament / Ryuo", "Passive milestone: emitted Armament techniques gain an 8% force/damage step without adding another button."),
    ARM_GALAXY_WAVE(HakiType.ARMAMENT, HakiUnlocks.GALAXY_WAVE_ARMAMENT, "Galaxy Impact: Haki Wave", "[M RELEASE <100%] Fire a forward pressure-cylinder punch. It grows continuously with Armament mastery. Holding [M] forms the spiral overhead first: roughly 46 blocks across bare, 66 coated, and 118 under Advanced Haki."),
    ARM_PROJECTED_FORCE(HakiType.ARMAMENT, 600, "Projected Force", "Haki Wave and other emitted Armament techniques gain heavier pressure, reach and knockback."),
    ARM_INTERNAL(HakiType.ARMAMENT, HakiUnlocks.INTERNAL_DESTRUCTION_ARMAMENT, "Internal Destruction Control", "Passive milestone: emitted Armament techniques reach a 16% total mastery-step bonus and prepare the path to Advanced Haki."),
    ARM_SUPREME(HakiType.ARMAMENT, HakiUnlocks.ADVANCED_HAKI_ARMAMENT, "Supreme Hardening", "Late-game Armament control required for Advanced Haki [J]."),
    ARM_BURNING_FISTS(HakiType.ARMAMENT, 900, "Ember Fists", "With Advanced Haki [J] up, both fists ignite: red-to-orange flame trails on the hands and sky-fed bolts landing between the outward arcs."),
    ARM_PINNACLE(HakiType.ARMAMENT, 1000, "Pinnacle Armament", "Maximum physical Haki output and one part of Joy Boy ascension."),

    OBS_PRESENCE(HakiType.OBSERVATION, 0, "Presence Sense", "[V] Read nearby visible life signatures and automatically slip projectile impacts while Observation is active."),
    OBS_THREAT(HakiType.OBSERVATION, 100, "Threat Reading", "Presence colors begin separating hostile/aggressive and exceptional threats from ordinary life."),
    OBS_SLIPSTREAM(HakiType.OBSERVATION, 180, "Slipstream", "Read hits are answered with a real body dodge -- side-step, lean-back or duck -- instead of the shot simply glancing off you. Arrows are evaded like anything else."),
    OBS_INTENT(HakiType.OBSERVATION, HakiUnlocks.CONQUEROR_AWAKEN_OBSERVATION, "Intent Reading", "Probable future-position ghosts appear from current motion and mob pursuit intent."),
    OBS_DANGER_SENSE(HakiType.OBSERVATION, 300, "Danger Sense", "Danger you cannot see registers anyway: screen-edge pressure marks the direction it is coming from and sharpens as it closes."),
    OBS_DODGE(HakiType.OBSERVATION, 400, "Predictive Evasion", "Approaching projectile trajectories become visible; automatic body slips read farther ahead and recover faster."),
    OBS_PRECOGNITION(HakiType.OBSERVATION, 550, "Combat Reading", "Probable melee intent flashes before impact and limited through-terrain Presence Sense begins as deliberately vague signatures."),
    OBS_ARROW_CATCH(HakiType.OBSERVATION, HakiUnlocks.OBSERVATION_ARROW_CATCH, "Arrow Snatch", "30% chance at unlock, rising to 70% at Observation 1000, to pluck an incoming arrow out of the air, hold it up and read it for a full second, then fire it back at the nearest hostile. The real projectile is thrown, so its damage and enchantments come with it, and the return speed grows with Observation mastery."),
    OBS_EXTENDED(HakiType.OBSERVATION, 700, "Future Branches", "Alternative movement branches appear and hidden presences become clearer without becoming body-perfect wall outlines."),
    OBS_COUNTER_FLIP(HakiType.OBSERVATION, HakiUnlocks.OBSERVATION_COUNTER_FLIP, "Backflip Counter", "30% chance at unlock, rising to 70% at Observation 1000, that a dodged melee swing turns into a high backflip that carries you well clear of it, then a charge straight back in behind an extended fist, auto-locked onto whoever swung. Damage scales with Observation mastery."),
    OBS_SUPREME(HakiType.OBSERVATION, 850, "Supreme Observation", "Wide terrain awareness, earlier off-screen danger direction and deep movement/attack forecasting."),
    OBS_PINNACLE(HakiType.OBSERVATION, 1000, "Pinnacle Observation / Future Sight", "Imminent danger can automatically trigger a 1.8s true Future Sight burst (24 energy, 5s internal cooldown) with rapid 1-2 second forecasts."),

    CONQ_AWAKENING(HakiType.CONQUEROR, 0, "King's Awakening", "[G TAP/HOLD] Radial Conqueror pressure. Final-Stand-style lightning grows from a 10-block-diameter tap toward the Joy Boy 50-block-radius release; charging bolt contact stuns combat targets for one second. Cooldown scales from 10 to 60 seconds. Docile creatures are never affected."),
    CONQ_CONTROL(HakiType.CONQUEROR, 120, "Pressure Control", "Faster charge, wider control and stronger knockback."),
    CONQ_BURST(HakiType.CONQUEROR, 180, "King's Burst", "[G TAP] A pointed arm throws a forward cone of will instead of a ring: everything caught in it is stunned and shoved away from you."),
    CONQ_DIRECTION(HakiType.CONQUEROR, 260, "Pressure Reach", "Conqueror releases travel farther and resist weaker wills more reliably."),
    CONQ_DOMINION(HakiType.CONQUEROR, HakiUnlocks.KINGS_GRIP_CONQUEROR, "Haki Grip", "[K] Midgame cinematic finisher. Hold K on a target in your sights: the wind hauls them in onto a chambered fist, an uppercut launches them straight up, you appear above them mid-flight and hammer them back into the floor with both arms. Most of the damage is in the landing, not the launch, and the crater throws everything else clear. Physical/Haki/Advanced tiers raise the launch height, the damage and the size of the blast."),
    CONQ_CRUSH(HakiType.CONQUEROR, 430, "Overwhelming Will", "Your pressure more reliably knocks weaker enemies down."),
    CONQ_SOVEREIGN(HakiType.CONQUEROR, HakiUnlocks.WIFI_HAKI_CONQUEROR, "WiFi Haki", "[L HOLD] Lock one visible target with 1/2/3 separated arched Haki beams. Range, duration and damage grow with ARM + OBS + HAO mastery."),
    CONQ_FOCUS(HakiType.CONQUEROR, 610, "Focused Pressure", "Conqueror releases become denser and harder to resist at distance."),
    CONQ_CONVERGENCE(HakiType.CONQUEROR, HakiUnlocks.GALAXY_FULL_CONQUEROR, "Galaxy Impact: Full Release", "[M -> 100% -> Attack] Mid/late-game full Galaxy Impact. The overhead galaxy reaches its full span at 100% and is dragged down into the fist on the punch; mastery and coating decide how insane the detonation becomes."),
    CONQ_EMPERORS_ROAR(HakiType.CONQUEROR, 720, "Emperor's Roar", "[G HOLD] The full charge locks you in place and builds into a roar. Targets caught by the release twitch, lose their aim and eventually black out, the longer you charged the worse it gets."),
    CONQ_ACOC(HakiType.CONQUEROR, HakiUnlocks.ADVANCED_HAKI_CONQUEROR, "Advanced Haki", "[J] Late-game multiplier. Requires ARM 850 + HAO 850 and active Armament; upgrades nearly every major technique."),
    CONQ_JOYBOY(HakiType.CONQUEROR, 1000, "Joy Boy Presence", "Unlocked after all three paths reach 1000 and 120 Legend is proven."),
    CONQ_PINNACLE(HakiType.CONQUEROR, 1000, "Supreme King's Last Stand", "FINAL MAX HAO passive: a lethal hit forces a 14-second motionless two-knee last stand inside a translucent red aura, with the head tilted slightly upward. Overhead arches originate clearly above and outside the head/shoulders. A true 50-block-diameter spherical field knocks down and twitches hostile mobs, including modded monster-category mobs. One second before death, a matching 50-block-diameter blast releases the stun, throws entities away and deals exactly forty hearts (80 damage) of armor-piercing pressure damage to hostiles; friendly-tagged/allied creatures take no Last Stand damage. 20-minute cooldown; obeys the global cooldown test command.");

    public final HakiType type;
    public final int mastery;
    public final String display;
    public final String description;

    HakiTechnique(HakiType type, int mastery, String display, String description) {
        this.type = type;
        this.mastery = mastery;
        this.display = display;
        this.description = description;
    }

    public boolean unlocked(int value) { return value >= mastery; }

    public static List<HakiTechnique> forType(HakiType type) {
        return Arrays.stream(values())
                .filter(t -> t.type == type)
                .sorted(Comparator.comparingInt(t -> t.mastery))
                .toList();
    }
}
