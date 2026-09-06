package com.hexhaki.data;

/**
 * Conqueror control styles. The modes are intentionally different jobs, not cosmetic labels.
 * RADIAL is the safe 360-degree crowd-control release, CONE trades coverage for reach/force,
 * and FOCUS is the narrow long-range pressure lane for overpowering a smaller group.
 */
public enum ConquerorMode {
    RADIAL("Radial", "360° crowd control · widest coverage"),
    CONE("Drive", "Forward 75° · longer range + harder knockback"),
    FOCUS("Focus", "Forward 30° · longest reach + strongest will pressure");

    public final String display;
    public final String description;

    ConquerorMode(String display, String description) {
        this.display = display;
        this.description = description;
    }

    public ConquerorMode next(int mastery) {
        if (mastery < 260) return RADIAL;
        if (mastery < 610) return this == RADIAL ? CONE : RADIAL;
        return values()[(ordinal() + 1) % values().length];
    }
}
