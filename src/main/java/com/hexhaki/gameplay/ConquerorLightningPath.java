package com.hexhaki.gameplay;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Shared deterministic geometry for Conqueror charge/release lightning.
 *
 * The server uses these exact segments for one-second contact stuns and the client uses them for
 * Photon beams. Keeping the path generator common prevents a visual bolt from touching an entity
 * while the authoritative server thinks the bolt travelled somewhere else.
 */
public final class ConquerorLightningPath {
    public static final int MIN_RELEASE_CHARGE = 4;
    public static final double MIN_RADIUS = 5.0D; // 10-block diameter tap.
    public static final double MAX_RADIUS = 50.0D; // Full mastery / Joy Boy endpoint.

    private ConquerorLightningPath() {}

    public record Bolt(List<Vec3> points) {
        public Bolt {
            points = List.copyOf(points);
        }
    }

    /** Tap begins at exactly five blocks; hold and mastery grow the reachable cap to fifty. */
    public static double radius(int mastery, float power) {
        double trained = Math.max(0.0D, Math.min(1.0D, mastery / 1000.0D));
        double hold = Math.max(0.0D, Math.min(1.0D,
                (power - MIN_RELEASE_CHARGE / 100.0D)
                        / (1.0D - MIN_RELEASE_CHARGE / 100.0D)));
        double masteryCap = 10.0D + 40.0D * trained;
        return MIN_RADIUS + (masteryCap - MIN_RADIUS) * hold;
    }

    /**
     * Shared Conqueror charge/release paths. Release stays dominated by outward eruptions; charging
     * uses fewer outward families plus a restrained Last-Stand-style overhead crown. Roots always
     * sit in a loose air shell outside the model and every pulse chooses new directions.
     */
    public static List<Bolt> generate(float power, int mastery, long seed, boolean release) {
        float q = Math.max(MIN_RELEASE_CHARGE / 100.0F, Math.min(1.0F, power));
        float trained = Math.max(0.0F, Math.min(1.0F, mastery / 1000.0F));
        double reach = radius(mastery, q);
        Random random = new Random(seed ^ (release ? 0x52454C454153454CL : 0x4348415247454C4EL));

        int families = release
                ? Math.max(3, Math.min(6, 3 + (int)Math.floor(q * 1.5F + trained * 1.5F)))
                // One authored channel is enough to read as charging lightning. A second appears
                // only at genuinely high charge/mastery, leaving dark air between discharges.
                : 1 + ((q >= .78F || trained >= .88F) ? 1 : 0);
        double ringOffset = random.nextDouble() * Math.PI * 2.0D;
        double sector = Math.PI * 2.0D / families;
        int archFamilies = release ? 0 : (q >= .58F ? 1 : 0);
        List<Bolt> bolts = new ArrayList<>(families + archFamilies);

        for (int bolt = 0; bolt < families; bolt++) {
            double angle = ringOffset + bolt * sector
                    + (random.nextDouble() - .5D) * sector * .72D;
            double originAngle = angle + (random.nextDouble() - .5D) * .48D;
            double bodyRadius = .88D + random.nextDouble() * .28D;
            double originY = .30D + random.nextDouble() * 1.18D;
            Vec3 origin = new Vec3(Math.cos(originAngle) * bodyRadius, originY,
                    Math.sin(originAngle) * bodyRadius);

            double verticalSpan = release ? 1.95D : 1.30D;
            double endY = Math.max(.15D, Math.min(2.55D,
                    originY + (random.nextDouble() - .5D) * verticalSpan));
            Vec3 endpoint = new Vec3(Math.cos(angle) * reach, endY, Math.sin(angle) * reach);
            Vec3 perpendicular = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
            int segments = release ? 6 + random.nextInt(3) : 4 + random.nextInt(2);

            double lateralMemory = (random.nextDouble() * 2.0D - 1.0D)
                    * (release ? .62D : .38D);
            double verticalMemory = (random.nextDouble() * 2.0D - 1.0D)
                    * (release ? .48D : .30D);
            List<Vec3> points = new ArrayList<>(segments + 1);
            points.add(origin);

            for (int section = 1; section <= segments; section++) {
                double t = section / (double)segments;
                if (section == segments) {
                    points.add(endpoint);
                    continue;
                }

                double envelope = Math.sin(Math.PI * t);
                double lateralScale = release ? .78D + .52D * q : .42D + .34D * q;
                double verticalScale = release ? .56D + .44D * q : .30D + .28D * q;
                lateralMemory = lateralMemory * .28D
                        + (random.nextDouble() * 2.0D - 1.0D) * lateralScale;
                verticalMemory = verticalMemory * .28D
                        + (random.nextDouble() * 2.0D - 1.0D) * verticalScale;
                double forwardJitter = (random.nextDouble() * 2.0D - 1.0D)
                        * (release ? .42D : .22D) * envelope;

                Vec3 base = origin.lerp(endpoint, t)
                        .add(Math.cos(angle) * forwardJitter, 0.0D,
                                Math.sin(angle) * forwardJitter);
                points.add(base
                        .add(perpendicular.scale(lateralMemory * (.72D + .28D * envelope)))
                        .add(0.0D, verticalMemory * (.72D + .28D * envelope), 0.0D));
            }
            bolts.add(new Bolt(points));
        }

        if (!release) {
            // Charging also carries a small Last-Stand-style crown. These arches are generated in
            // the shared path class rather than as client-only decoration, so anything visibly
            // touched by one is still eligible for the normal one-second charge stun.
            //
            // Roots deliberately float just outside / above the shoulder-head silhouette instead
            // of spawning from the face or skull.
            double archOffset = random.nextDouble() * Math.PI * 2.0D;
            for (int arc = 0; arc < archFamilies; arc++) {
                double side = (arc & 1) == 0 ? -1.0D : 1.0D;
                double originAngle = archOffset + side * (.42D + random.nextDouble() * .18D);
                double originRadius = .52D + random.nextDouble() * .12D;
                Vec3 origin = new Vec3(
                        Math.cos(originAngle) * originRadius,
                        1.92D + random.nextDouble() * .18D,
                        Math.sin(originAngle) * originRadius);

                double angle = archOffset + arc * Math.PI
                        + (random.nextDouble() - .5D) * 1.05D;
                double archReach = Math.max(4.0D, reach * (.52D + random.nextDouble() * .16D));
                double endY = .30D + random.nextDouble() * (1.10D + .55D * q);
                double archHeight = Math.min(11.5D,
                        2.8D + archReach * .16D + random.nextDouble() * 1.8D);
                Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
                Vec3 perpendicular = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
                int segments = 8 + random.nextInt(3) + (q > .80F ? 1 : 0);
                double phase = random.nextDouble() * Math.PI * 2.0D;
                List<Vec3> points = new ArrayList<>(segments + 1);
                points.add(origin);

                for (int section = 1; section <= segments; section++) {
                    double t = section / (double)segments;
                    if (section == segments) {
                        points.add(origin.add(radial.scale(archReach))
                                .add(0.0D, endY - origin.y, 0.0D));
                        continue;
                    }

                    double envelope = Math.sin(Math.PI * t);
                    double lateral = (Math.sin(t * Math.PI * 2.7D + phase)
                            * (.28D + .34D * q)
                            + (random.nextDouble() * 2.0D - 1.0D) * (.12D + .18D * q))
                            * envelope;
                    double verticalJitter = Math.sin(t * Math.PI * 4.0D + phase)
                            * (.12D + .18D * q) * envelope;
                    Vec3 next = origin
                            .add(radial.scale(archReach * t))
                            .add(perpendicular.scale(lateral))
                            .add(0.0D,
                                    (endY - origin.y) * t + archHeight * envelope + verticalJitter,
                                    0.0D);
                    points.add(next);
                }
                bolts.add(new Bolt(points));
            }
        }

        return List.copyOf(bolts);
    }
}
