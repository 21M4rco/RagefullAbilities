package com.hexhaki.fruit;

/**
 * Continuous collision for the moving parts of a Gear 5 attack.
 *
 * <p>A Gear 5 fist can cross more than twenty blocks in a single tick. Testing only where the fist
 * <em>is</em> at the end of each tick lets it teleport straight through a target, which is exactly
 * the failure the brief rules out. Everything here works on the segment the fist swept
 * <em>between</em> two ticks instead of on its endpoint.
 *
 * <p>The swept fist is treated as a capsule: the segment from last tick's centre to this tick's
 * centre, thickened by the fist's radius. Rather than solve capsule-versus-box exactly, the target
 * box is inflated by that radius and tested against the bare segment. At the box's corners this
 * over-approximates slightly — a true capsule has rounded corners — which biases toward
 * registering a hit. For an attack whose whole job is to connect, that is the right way to be
 * wrong.
 *
 * <p>Pure math on primitives: no Minecraft types, so it is unit-testable and usable from both
 * sides. Callers pass entity bounds as raw min/max doubles.
 */
public final class NikaSweep {

    private NikaSweep() {}

    /** Returned when a sweep does not touch the target. */
    public static final double NO_HIT = Double.NaN;

    public static boolean hit(double t) { return !Double.isNaN(t); }

    /**
     * Fraction along the sweep at which the fist first touches the target.
     *
     * @param ax,ay,az fist centre at the end of the previous tick
     * @param bx,by,bz fist centre at the end of this tick
     * @param radius   fist radius, which grows as the fist enlarges
     * @return the entry fraction in {@code [0,1]}, or {@link #NO_HIT}. A sweep that begins already
     *         overlapping returns {@code 0}.
     */
    public static double sweep(double ax, double ay, double az,
                               double bx, double by, double bz,
                               double radius,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {
        // Minkowski-inflate the target by the fist radius, then it is segment-versus-box.
        double lx = minX - radius, ly = minY - radius, lz = minZ - radius;
        double hx = maxX + radius, hy = maxY + radius, hz = maxZ + radius;

        double dx = bx - ax, dy = by - ay, dz = bz - az;

        // Degenerate sweep (the fist did not move this tick): a pure containment test.
        if (dx == 0 && dy == 0 && dz == 0) {
            boolean inside = ax >= lx && ax <= hx && ay >= ly && ay <= hy && az >= lz && az <= hz;
            return inside ? 0.0 : NO_HIT;
        }

        double enter = 0.0, exit = 1.0;

        double[] origin = {ax, ay, az};
        double[] delta = {dx, dy, dz};
        double[] low = {lx, ly, lz};
        double[] high = {hx, hy, hz};

        for (int axis = 0; axis < 3; axis++) {
            double o = origin[axis], d = delta[axis], lo = low[axis], hi = high[axis];
            if (d == 0.0) {
                // Parallel to this slab: if it starts outside it can never enter.
                if (o < lo || o > hi) return NO_HIT;
                continue;
            }
            double inv = 1.0 / d;
            double t1 = (lo - o) * inv;
            double t2 = (hi - o) * inv;
            if (t1 > t2) { double swap = t1; t1 = t2; t2 = swap; }
            if (t1 > enter) enter = t1;
            if (t2 < exit) exit = t2;
            if (enter > exit) return NO_HIT;
        }
        if (exit < 0.0 || enter > 1.0) return NO_HIT;
        return Math.max(0.0, enter);
    }

    /** Point on the sweep at fraction {@code t}, so the impact effect plays where contact happened. */
    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /**
     * Shortest squared distance from a point to a box. Used to rank several candidate targets
     * caught by one sweep so the closest one takes the impact.
     */
    public static double distanceSqToBox(double px, double py, double pz,
                                         double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ) {
        double cx = px < minX ? minX - px : px > maxX ? px - maxX : 0.0;
        double cy = py < minY ? minY - py : py > maxY ? py - maxY : 0.0;
        double cz = pz < minZ ? minZ - pz : pz > maxZ ? pz - maxZ : 0.0;
        return cx * cx + cy * cy + cz * cz;
    }

    /**
     * Number of sub-steps a curved path needs so its chords stay within {@code tolerance} of the
     * true arc.
     *
     * <p>Dawn Whip's foot travels an arc, not a straight line, so one segment per tick would cut
     * the corner and miss a target standing inside the curve. The sweep is subdivided instead.
     */
    public static int arcSubdivisions(double radius, double sweptRadians, double tolerance) {
        if (radius <= 0 || sweptRadians <= 0) return 1;
        if (tolerance <= 0) return 64;
        // Sagitta of a chord subtending angle a on radius r is r*(1-cos(a/2)).
        // Solve r*(1-cos(a/2)) <= tolerance for the per-step angle a.
        double ratio = 1.0 - tolerance / radius;
        if (ratio <= -1.0) return 1;
        double maxStep = 2.0 * Math.acos(Math.max(-1.0, Math.min(1.0, ratio)));
        if (maxStep <= 1.0e-6) return 64;
        return Math.max(1, Math.min(64, (int) Math.ceil(sweptRadians / maxStep)));
    }
}
