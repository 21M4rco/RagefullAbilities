package com.hexhaki.data;

/**
 * V2 progression thresholds kept in one common/client-safe place so HUD, mastery menu and
 * server gameplay cannot drift apart. The philosophy is "techniques first, perfection later":
 * most moves arrive during the middle of the grind and grow with mastery; Advanced Haki is the
 * deliberately late multiplier that turns the whole kit into its endgame form.
 */
public final class HakiUnlocks {
    private HakiUnlocks() {}

    public static final int ARMAMENT_COATING = 40;
    public static final int HAKI_LEAP_ARMAMENT = 240;
    public static final int RYUO_ARMAMENT = 400;
    public static final int GALAXY_WAVE_ARMAMENT = 520;
    public static final int INTERNAL_DESTRUCTION_ARMAMENT = 700;

    public static final int CONQUEROR_AWAKEN_ARMAMENT = 240;
    public static final int CONQUEROR_AWAKEN_OBSERVATION = 240;

    public static final int KINGS_GRIP_ARMAMENT = 340;
    public static final int KINGS_GRIP_OBSERVATION = 300;
    public static final int KINGS_GRIP_CONQUEROR = 320;

    public static final int WIFI_HAKI_ARMAMENT = 450;
    /** Projectile-path forecasting, and the tier where dodges become reads rather than
     *  reflexes: ducking under a high shot and leaning away when both flanks are blocked. */
    public static final int OBSERVATION_PROJECTILE_FORECAST = 400;
    /** Plucking an incoming shot out of the air and returning it. */
    public static final int OBSERVATION_ARROW_CATCH = 620;
    /** Backflipping off a read melee swing and countering with a dash punch. */
    public static final int OBSERVATION_COUNTER_FLIP = 700;
    public static final int WIFI_HAKI_OBSERVATION = 450;
    public static final int WIFI_HAKI_CONQUEROR = 500;

    public static final int GALAXY_FULL_ARMAMENT = 650;
    public static final int GALAXY_FULL_OBSERVATION = 550;
    public static final int GALAXY_FULL_CONQUEROR = 650;
    public static final int GALAXY_FULL_LEGEND = 20;

    public static final int ADVANCED_HAKI_ARMAMENT = 850;
    public static final int ADVANCED_HAKI_CONQUEROR = 850;

    public static final int JOY_BOY_MASTERY = 1000;
    public static final int JOY_BOY_LEGEND = 120;
}
