package com.hexhaki.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;
import java.util.UUID;

/**
 * Controlled terrain reaction for a fully charged 1000-mastery Conqueror release.
 *
 * This deliberately does not use a vanilla explosion: the Haki front tears exposed
 * terrain outward in timed rings/radial fissures, produces normal block-break feedback,
 * never drops hundreds of items, and refuses to touch unbreakable blocks/block entities.
 */
public final class SupremeHakiDestruction {
    private static final int MAX_TOTAL_BREAKS = 720;

    private SupremeHakiDestruction() {}

    public static void unleash(ServerPlayer source, double requestedRadius) {
        ServerLevel level = source.serverLevel();
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;

        BlockPos origin = source.blockPosition();
        UUID ownerId = source.getUUID();
        long seed = source.getRandom().nextLong();
        double radius = Math.min(30.0, Math.max(18.0, requestedRadius * 0.72));
        BreakBudget budget = new BreakBudget(MAX_TOTAL_BREAKS);

        // The visible blast expands, so terrain damage expands with it rather than appearing at once.
        final int waves = 7;
        for (int wave = 0; wave < waves; wave++) {
            final int w = wave;
            final double ringRadius = 3.5 + (radius - 3.5) * (w / (double) (waves - 1));
            ServerTimeline.later(level, w * 3, () -> damageRing(level, origin, ownerId, ringRadius, seed + w * 0x9E3779B97F4A7C15L, budget));
        }

        // Long radial fractures sell the "island itself reacted" feeling without deleting a full sphere.
        ServerTimeline.later(level, 4, () -> fractureRays(level, origin, ownerId, radius, seed ^ 0xD1B54A32D192ED03L, budget));
    }

    private static void damageRing(ServerLevel level, BlockPos origin, UUID ownerId, double radius, long seed, BreakBudget budget) {
        if (budget.empty()) return;
        Random random = new Random(seed);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        int samples = 72 + (int) (radius * 3.0);

        for (int i = 0; i < samples && !budget.empty(); i++) {
            double angle = Math.PI * 2.0 * i / samples + (random.nextDouble() - 0.5) * 0.09;
            double jitter = (random.nextDouble() - 0.5) * 1.7;
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * (radius + jitter));
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * (radius + jitter));

            // Find exposed terrain/building surfaces around player height.
            for (int dy = 4; dy >= -3 && !budget.empty(); dy--) {
                BlockPos pos = new BlockPos(x, origin.getY() + dy, z);
                if (tryBreak(level, pos, owner, random, 0.74f, budget)) break;
            }
        }
    }

    private static void fractureRays(ServerLevel level, BlockPos origin, UUID ownerId, double radius, long seed, BreakBudget budget) {
        if (budget.empty()) return;
        Random random = new Random(seed);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        int rays = 22;

        for (int ray = 0; ray < rays && !budget.empty(); ray++) {
            double angle = Math.PI * 2.0 * ray / rays + (random.nextDouble() - 0.5) * 0.20;
            double bend = (random.nextDouble() - 0.5) * 0.025;
            for (double r = 3.0; r <= radius && !budget.empty(); r += 0.9) {
                angle += bend;
                int x = origin.getX() + (int) Math.round(Math.cos(angle) * r + (random.nextDouble() - 0.5) * 0.45);
                int z = origin.getZ() + (int) Math.round(Math.sin(angle) * r + (random.nextDouble() - 0.5) * 0.45);

                // Crack the exposed floor first, but also catch low walls around the wave front.
                boolean broke = false;
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos pos = new BlockPos(x, origin.getY() + dy, z);
                    if (tryBreak(level, pos, owner, random, 0.92f, budget)) {
                        broke = true;
                        break;
                    }
                }
                // Occasional second adjacent break widens a fissure without turning it into a crater.
                if (broke && random.nextFloat() < 0.22f && !budget.empty()) {
                    Direction side = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
                    BlockPos pos = new BlockPos(x, origin.getY() - 1 + random.nextInt(3), z).relative(side);
                    tryBreak(level, pos, owner, random, 0.65f, budget);
                }
            }
        }
    }

    private static boolean tryBreak(ServerLevel level, BlockPos pos, ServerPlayer owner, Random random, float strength, BreakBudget budget) {
        if (budget.empty() || !level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) return false;

        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > 5.5f) return false;
        if (!isExposed(level, pos)) return false;

        // Harder blocks resist more often; dirt/glass/leaves/wood fail much more readily than dense masonry.
        float hardnessFactor = hardness <= 0.8f ? 1.0f : hardness <= 2.0f ? 0.78f : hardness <= 3.5f ? 0.52f : 0.28f;
        if (random.nextFloat() > strength * hardnessFactor) return false;

        if (level.destroyBlock(pos, false, owner)) {
            budget.consume();
            return true;
        }
        return false;
    }

    private static boolean isExposed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).isAir()) return true;
        }
        return false;
    }

    private static final class BreakBudget {
        private int remaining;
        private BreakBudget(int remaining) { this.remaining = remaining; }
        private boolean empty() { return remaining <= 0; }
        private void consume() { remaining--; }
    }
}
