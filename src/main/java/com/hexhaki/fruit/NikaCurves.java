package com.hexhaki.fruit;

/**
 * The timing curves that make Gear 5 read as rubber rather than as a reskinned punch.
 *
 * <p>Every technique is built from the same five-beat shape the brief calls for — anticipation,
 * acceleration, impact, follow-through, elastic recoil — so the whole moveset shares one sense of
 * weight while each move keeps its own reach, duration and silhouette.
 *
 * <p>Pure math with no Minecraft types, so it is unit-testable and is safe to call from the server
 * tick and from the renderer alike. All phase inputs are normalised progress in {@code [0,1]};
 * callers clamp nothing themselves.
 */
public final class NikaCurves {

    private NikaCurves() {}

    /** How far past 1.0 an elastic extension is allowed to overshoot before settling back. */
    public static final float DEFAULT_OVERSHOOT = 0.18f;
    /** How far below 0 the wind-up pulls before the strike travels forward. */
    public static final float DEFAULT_ANTICIPATION = 0.22f;
    /** Fraction of the post-impact time spent continuing past contact before the recoil begins. */
    private static final float FOLLOW_THROUGH = 0.25f;

    public static float clamp01(float t) { return t < 0f ? 0f : t > 1f ? 1f : t; }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Smooth Hermite step. Zero velocity at both ends, so limbs never start or stop with a jerk. */
    public static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    /** Quintic smootherstep. Zero velocity <em>and</em> acceleration at the ends; used for scale ramps. */
    public static float smootherstep(float t) {
        t = clamp01(t);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    /** Fast departure, slow arrival. The travel half of a punch. */
    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** Slow departure, fast arrival. The gather half of a wind-up. */
    public static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    /**
     * Damped elastic settle toward 1.0.
     *
     * <p>Returns exactly 0 at {@code t == 0} and exactly 1 at {@code t == 1}; in between it
     * overshoots and rings down. This is the curve a stretched limb uses to snap back.
     *
     * @param oscillations how many times it crosses the target before settling
     * @param damping      higher damps the ringing out faster
     */
    public static float elasticSettle(float t, float oscillations, float damping) {
        t = clamp01(t);
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        double decay = Math.exp(-damping * t);
        double wave = Math.cos(oscillations * Math.PI * t);
        return (float) (1.0 - decay * wave);
    }

    /** Elastic settle with the tuning the limb renderer uses by default. */
    public static float elasticSettle(float t) { return elasticSettle(t, 3f, 5.5f); }

    /**
     * Volume-preserving squash/stretch factor for an axis.
     *
     * <p>Given a stretch factor along one axis, returns the factor the two perpendicular axes must
     * take so the body keeps its apparent volume. A limb stretched to 4x thins to 0.5x across.
     * Keeping this in one place is what stops a stretched arm from looking like an inflated tube.
     */
    public static float perpendicularForStretch(float stretch) {
        if (stretch <= 1.0e-4f) return 1f;
        return (float) (1.0 / Math.sqrt(stretch));
    }

    /**
     * The extension profile of a strike, over the whole move.
     *
     * <p>Result is limb extension as a multiple of resting length: negative during the wind-up
     * (the fist pulls back behind the shoulder), rising past 1 at impact, overshooting, then
     * ringing back to 0. Callers multiply this by the technique's {@code range()}.
     *
     * @param t             normalised progress across the entire technique
     * @param anticipation  fraction of the move spent winding up, in {@code (0,1)}
     * @param impact        fraction of the move at which contact happens, {@code > anticipation}
     */
    public static float strikeExtension(float t, float anticipation, float impact) {
        t = clamp01(t);
        if (anticipation <= 0f) anticipation = 1.0e-3f;
        if (impact <= anticipation) impact = Math.min(1f, anticipation + 1.0e-3f);

        if (t < anticipation) {
            // Gather: ease in to the deepest pull-back.
            float local = t / anticipation;
            return -DEFAULT_ANTICIPATION * easeInCubic(local);
        }
        if (t < impact) {
            // Travel: accelerate out of the wind-up and arrive at full reach.
            float local = (t - anticipation) / (impact - anticipation);
            return lerp(-DEFAULT_ANTICIPATION, 1f, easeOutCubic(local));
        }
        // Follow-through, then elastic recoil back to rest.
        float local = (t - impact) / Math.max(1.0e-3f, 1f - impact);
        if (local < FOLLOW_THROUGH) {
            // The fist keeps going a little past the contact point before the rubber catches it.
            return 1f + DEFAULT_OVERSHOOT * (float) Math.sin(Math.PI * local / FOLLOW_THROUGH);
        }
        // Damped ring-down. The cosine crosses zero on the way in, so the limb recoils slightly
        // past its resting length before settling - and lands exactly on 0 at the final frame.
        float u = (local - FOLLOW_THROUGH) / (1f - FOLLOW_THROUGH);
        return (float) (Math.exp(-3.2 * u) * Math.cos(1.5 * Math.PI * u));
    }

    /** Strike profile with the default 22% wind-up and impact at 55%. */
    public static float strikeExtension(float t) { return strikeExtension(t, 0.22f, 0.55f); }

    /**
     * A damped radial ripple, used for both the ground rebound and torso wobble.
     *
     * <p>Returns displacement at distance {@code distance} from the centre, {@code age} seconds
     * after the impact. Falls to zero beyond the wavefront and decays with both time and radius,
     * so a ripple always dies out instead of ringing forever.
     *
     * @param distance   radial distance from the impact centre, same units as {@code speed}
     * @param age        seconds since impact
     * @param speed      how fast the wavefront travels, units per second
     * @param wavelength distance between crests
     */
    public static float ripple(float distance, float age, float speed, float wavelength) {
        if (age <= 0f || distance < 0f) return 0f;
        float front = speed * age;
        if (distance > front) return 0f;                    // the wave has not arrived yet
        float behind = front - distance;
        float radial = 1f / (1f + distance * distance * 0.35f);   // energy spreads with radius
        float temporal = (float) Math.exp(-2.2 * age);            // and bleeds off with time
        float phase = (float) Math.cos(2.0 * Math.PI * behind / Math.max(1.0e-3f, wavelength));
        float envelope = (float) Math.exp(-1.8 * behind);         // crest rides just behind the front
        return radial * temporal * phase * envelope;
    }

    /**
     * Compression depth of the ground under a Gear 5 footfall.
     *
     * <p>Rises fast on contact, holds briefly, then springs back with a single overshoot so the
     * surface visibly kicks the player away rather than simply relaxing. Returns 0 at both ends,
     * which is what guarantees the deformation is fully temporary.
     */
    public static float groundCompression(float t) {
        t = clamp01(t);
        if (t <= 0f || t >= 1f) return 0f;
        if (t < 0.25f) return easeOutCubic(t / 0.25f);            // dimple forms
        float local = (t - 0.25f) / 0.75f;
        double decay = Math.exp(-4.0 * local);
        double wave = Math.cos(2.5 * Math.PI * local);
        return (float) (decay * wave);                            // rebound, one overshoot, settle
    }

    /**
     * Buoyancy of the Gear 5 idle: a slow rubbery breathe applied to torso scale.
     *
     * @param ticks    the player's tick counter
     * @param partial  partial tick, so the motion is smooth between ticks rather than stepped
     */
    public static float buoyantBreathe(float ticks, float partial) {
        float time = (ticks + partial) * 0.08f;
        return (float) (Math.sin(time) * 0.5 + Math.sin(time * 1.7 + 1.1) * 0.5) * 0.5f;
    }
}
