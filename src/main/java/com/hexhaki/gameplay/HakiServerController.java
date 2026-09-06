package com.hexhaki.gameplay;

import com.hexhaki.data.ConquerorMode;
import com.hexhaki.data.HakiData;
import com.hexhaki.data.HakiRank;
import com.hexhaki.data.HakiType;
import com.hexhaki.data.HakiUnlocks;
import com.hexhaki.network.HakiAction;
import com.hexhaki.network.HakiNetwork;
import com.hexhaki.network.msg.S2CAnimation;
import com.hexhaki.network.msg.S2CArmamentImpact;
import com.hexhaki.network.msg.S2CBladeSlash;
import com.hexhaki.network.msg.S2CCinematic;
import com.hexhaki.network.msg.S2CConquerorShock;
import com.hexhaki.network.msg.S2CKingsGripImpactFrame;
import com.hexhaki.network.msg.S2CEntityState;
import com.hexhaki.network.msg.S2CHakiVisual;
import com.hexhaki.network.msg.S2CWorldFx;
import com.hexhaki.network.msg.S2CGalaxyImpact;
import com.hexhaki.network.msg.S2CGalaxyWave;
import com.hexhaki.network.msg.S2CGalaxyHakiStrike;
import com.hexhaki.network.msg.S2CPerception;
import com.hexhaki.network.msg.S2CRyoRelease;
import com.hexhaki.network.msg.S2CStateSync;
import com.hexhaki.network.msg.S2CWifiHaki;
import com.hexhaki.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import org.joml.Vector3f;

/** Server-authoritative Haki state machine. Every VFX packet is broadcast to tracking clients. */
public final class HakiServerController {
    private static final int CONQUEROR_MAX = 100;
    private static final long ADVANCED_HAKI_NORMAL_WARMUP_TICKS = 100L; // 5 seconds of normal Armament before every Advanced-Haki activation.
    private static final String ADVANCED_HAKI_NORMAL_SINCE_KEY = "advancedHakiNormalSince";
    // Full-charge Haoshoku is intentionally a commitment: 160 ticks = 8 seconds at 20 TPS.
    // The meter is derived from elapsed charge ticks so it can genuinely advance slower than
    // one whole percentage point per game tick.
    private static final int CONQUEROR_FULL_CHARGE_TICKS = 160;
    // Charging lightning is a deliberate beat, not a continuously redrawn cage.
    private static final int CONQUEROR_CHARGE_LIGHTNING_INTERVAL_TICKS = 24;
    private static final int CONQUEROR_MAX_CHARGE_LIGHTNING_INTERVAL_TICKS = 36;
    private static final int CONQUEROR_CHARGE_CONTACT_STUN_TICKS = 20;
    private static final int CONQUEROR_TAP_MAX_CHARGE_TICKS = 10;
    private static final int CONQUEROR_TAP_STUN_TICKS = 30;
    private static final int CONQUEROR_TAP_PUSH_TICKS = 4;
    private static final double CONQUEROR_TAP_RANGE = 10.0D;
    private static final double CONQUEROR_TAP_CONE_DOT = 0.7933533403D; // 75-degree full cone.
    private static final float CONQUEROR_TAP_DAMAGE = 4.0F;
    private static final long CONQUEROR_MIN_COOLDOWN_TICKS = 20L * 10L;
    private static final long CONQUEROR_MAX_COOLDOWN_TICKS = 20L * 60L;
    // A MAX release is a physical battlefield blast as well as a stun. The temporary launch
    // window prevents the absolute stun controller from erasing the radial explosion velocity.
    private static final int CONQUEROR_MAX_STUN_TICKS = 200; // exactly 10 seconds at 20 TPS, applied on release only.
    private static final int CONQUEROR_MAX_BLAST_MOTION_TICKS = 12; // short pressure shove, not a sustained launch.
    private static final double CONQUEROR_MAX_BLAST_EDGE_FORCE = 0.55;
    private static final double CONQUEROR_MAX_BLAST_CORE_FORCE = 1.65;
    private static final int CONVERGENCE_MAX = 100;
    private static final int GALAXY_PRECHARGE_GAIN = 2;
    private static final int DOMINION_MAX = 100; // retained only for old-save compatibility; V2 Dominion is King's Grip.
    private static final int THRAGG_NO_HAKI = 0;
    private static final int THRAGG_HAKI = 1;
    private static final int THRAGG_ADVANCED = 2;
    /** The draw hauls them in, so the move starts from well outside melee. */
    private static final double THRAGG_RANGE = 14.0;

    private static final Map<UUID, GrabState> ACTIVE_GRABS = new HashMap<>();
    private static final Map<UUID, WifiHakiState> ACTIVE_WIFI_HAKI = new HashMap<>();
    private static final int WIFI_HAKI_VFX_INTERVAL_TICKS = 4;
    private static final int WIFI_HAKI_DAMAGE_INTERVAL_TICKS = 10;
    private static final int WIFI_HAKI_CONNECT_TICKS = 4;
    private static final int WIFI_HAKI_LOS_GRACE_TICKS = 8;
    private static final float WIFI_HAKI_START_COST = 8f;
    private static final float WIFI_HAKI_BASE_ENERGY_PER_TICK = .40f;
    private static final float WIFI_HAKI_EXTRA_BEAM_ENERGY_PER_TICK = .12f;
    private static final long WIFI_HAKI_MIN_COOLDOWN_TICKS = 200L;   // 10s tap / short channel.
    private static final long WIFI_HAKI_MAX_COOLDOWN_TICKS = 1000L; // 50s at maximum channel duration.

    /** Tier is frozen when the grab connects so toggling Haki mid-cinematic cannot desync timing/VFX. */
    /**
     * @param groundY the real floor height under the launch column, sampled once at commit. The
     *                column used to be built off the attacker's own feet, so on any slope the
     *                victim was driven to a height that was not the floor -- left hanging a block
     *                up, or teleported into terrain, where the crater fired inside a block and
     *                nothing at all was visible.
     */
    private record GrabState(UUID targetId, Vec3 attackerAnchor, float attackerYaw, long startTick,
                             int tier, double groundY) {}

    /** Mutable server-only channel state. The client only says press/release; it never owns damage,
     * duration, target stun, energy drain, or redraw cadence. */
    private static final class WifiHakiState {
        final UUID targetId;
        final long startTick;
        final int maxTicks;
        final long seed;
        final boolean targetWasNoAi;
        final int hakiTier;  // 0 = bare projection, 1 = Armament, 2 = Advanced Armament.
        final int beamCount; // Visually 1 / 2 / 3 separated arched connections.
        int lostSightTicks;
        int visualPulse;
        int damagePulses;
        WifiHakiState(UUID targetId, long startTick, int maxTicks, long seed, boolean targetWasNoAi, int hakiTier) {
            this.targetId=targetId;
            this.startTick=startTick;
            this.maxTicks=maxTicks;
            this.seed=seed;
            this.targetWasNoAi=targetWasNoAi;
            this.hakiTier=Mth.clamp(hakiTier, 0, 2);
            this.beamCount=1+this.hakiTier;
        }
    }

    /**
     * Haki Grip beat sheet, in ticks from the start of the sequence.
     *
     * <p>These are mirrored exactly in {@code tools/generate_haki_animations.py}
     * (THRAGG_TIERS): the clips are cut to this schedule, so a change here has to be made in both
     * places or the pose and the physics come apart.
     *
     * @param uppercut the fist lands and the target is launched straight up
     * @param ascend   the attacker appears above the rising target
     * @param slam     both arms come down on the back of their head
     * @param ground   the target hits the floor
     */
    private record ThraggBeats(int uppercut, int ascend, int slam, int ground, int duration) {}

    private static ThraggBeats thraggBeats(int tier) {
        return switch (tier) {
            case THRAGG_ADVANCED -> new ThraggBeats(52, 60, 116, 140, 170);
            case THRAGG_HAKI -> new ThraggBeats(44, 52, 100, 120, 150);
            default -> new ThraggBeats(36, 44, 86, 104, 134);
        };
    }

    /** How far the uppercut throws them, in blocks. The attacker waits above the top of this. */
    private static double thraggRiseHeight(int tier) {
        return switch (tier) {
            case THRAGG_ADVANCED -> 58.0;
            case THRAGG_HAKI -> 42.0;
            default -> 30.0;
        };
    }

    private static float thraggEnergyCost(int tier) {
        return switch (tier) {
            case THRAGG_ADVANCED -> 450f;
            case THRAGG_HAKI -> 80f;
            default -> 28f;
        };
    }

    private static long thraggCooldownTicks(int tier) {
        return switch (tier) {
            case THRAGG_ADVANCED -> 600L; // 30 seconds
            case THRAGG_HAKI -> 400L;     // 20 seconds
            default -> 240L;              // 12 seconds
        };
    }

    /** Total damage across the three hits, before the split. Armament is the scaling axis. */
    private static float thraggTotalDamage(ServerPlayer attacker, int tier) {
        float armamentScale = Mth.clamp(HakiData.mastery(attacker, HakiType.ARMAMENT) / 1000f, 0f, 1f);
        return switch (tier) {
            case THRAGG_ADVANCED -> Mth.lerp(armamentScale, 20f, 220f);
            case THRAGG_HAKI -> Mth.lerp(armamentScale, 12f, 87f);
            default -> Mth.lerp(armamentScale, 6f, 36f);
        };
    }

    /**
     * The 400 Ryuo and 700 Internal Destruction milestones are real progression steps, not just
     * logbook labels. They modestly improve emitted Armament techniques without inflating basic
     * melee or replacing the much larger Advanced-Haki jump at 850.
     */
    private static float armamentEmissionMultiplier(int mastery) {
        if (mastery >= HakiUnlocks.INTERNAL_DESTRUCTION_ARMAMENT) return 1.16f;
        if (mastery >= HakiUnlocks.RYUO_ARMAMENT) return 1.08f;
        return 1.0f;
    }

    private static String thraggAttackerAnimation(int tier) {
        return switch (tier) {
            case THRAGG_ADVANCED -> "thragg_attacker_advanced";
            case THRAGG_HAKI -> "thragg_attacker_haki";
            default -> "thragg_attacker_none";
        };
    }

    private static String thraggVictimAnimation(int tier) {
        return switch (tier) {
            case THRAGG_ADVANCED -> "thragg_victim_advanced";
            case THRAGG_HAKI -> "thragg_victim_haki";
            default -> "thragg_victim_none";
        };
    }
    // RAGE ultimate timing transplanted verbatim into the full Galaxy Impact sequence.
    private static final int GALAXY_LAUNCH_IMPULSE_TICK = 7;
    private static final int GALAXY_ASCEND_TICKS = 30;
    private static final int GALAXY_CHARGE_ANIMATION_TICK = 20;
    private static final int GALAXY_ABSORB_START_TICK = 28;
    private static final int GALAXY_READY_TICK = 88;
    private static final int GALAXY_PUNCH_IMPACT_TICK = 7;
    private static final int GALAXY_TAP_IMPACT_TICK = 8;
    private static final int GALAXY_TAP_RECOVERY_TICKS = 22;
    private static final long GALAXY_TAP_NO_HAKI_COOLDOWN_TICKS = 25L; // 1.25s: weak physical/air tap.
    private static final long GALAXY_TAP_ADVANCED_COOLDOWN_TICKS = 140L; // 7s: J/Advanced Haki tap.
    private static final int GALAXY_DETONATION_TICK = 12;
    private static final double GALAXY_MAX_AIM_RANGE = 120.0;
    private static final double GALAXY_IMPACT_RADIUS = 40.0;
    private static final double GALAXY_ADVANCED_RADIUS_AT_UNLOCK = 70.0;
    private static final double GALAXY_ADVANCED_RADIUS_MAX = 76.0;
    private static final double GALAXY_ADVANCED_RADIUS_JOYBOY = 78.0;
    // Restored V22 normal-Armament Galaxy storm: discrete black/red bolts fall from the sky.
    private static final double GALAXY_SKY_LIGHTNING_RADIUS = 20.0;
    private static final double GALAXY_SKY_STRIKE_DAMAGE_RADIUS = 3.6;
    private static final int GALAXY_SKY_LIGHTNING_PULSES = 10;
    private static final int GALAXY_SKY_LIGHTNING_INTERVAL = 8;
    private static final int GALAXY_SKY_STRIKES_PER_PULSE = 3;
    // Advanced/J adds a separate five-second horizontal ground-crawling field on top of the sky storm.
    private static final double GALAXY_HAKI_STRIKE_DAMAGE_RADIUS = 4.5;
    private static final int GALAXY_HAKI_LIGHTNING_PULSES = 11;
    private static final int GALAXY_HAKI_LIGHTNING_INTERVAL = 10; // 0..100 ticks = about five seconds.
    private static final int GALAXY_HAKI_STRIKES_PER_PULSE = 4;
    private static final long GALAXY_FULL_COOLDOWN_TICKS = 1200L; // hard one-minute cooldown after firing.
    // Final-release commitment: the ultimate should cost meaningfully more than a tap/release,
    // while still leaving enough reserve for survival at its 650/550/650 unlock floor.
    private static final float GALAXY_FULL_ENERGY_COST = 120.0f;
    private static final float GALAXY_MAX_DAMAGE = 70.0f;
    private static final float GALAXY_MIN_DAMAGE = 16.0f;
    private static final float GALAXY_HAKI_LIGHTNING_MAX_DAMAGE = 7.5f;
    private static final float GALAXY_HAKI_LIGHTNING_MIN_DAMAGE = 2.5f;
    private static final int HAKI_LEAP_UNLOCK_MASTERY = HakiUnlocks.HAKI_LEAP_ARMAMENT;
    private static final int HAKI_LEAP_MIN_CHARGE_TICKS = 0;
    private static final int HAKI_LEAP_MAX_CHARGE_TICKS = 40; // Shift+Space starts the real Haki charge immediately.
    private static final String TEST_NO_COOLDOWNS = "testNoCooldowns";
    private static final String[] ABILITY_COOLDOWN_KEYS = {
            "conquerorCooldownUntil", "dominionCooldownUntil",
            "sovereignCooldownUntil", "galaxyTapLockUntil", "galaxyFullCooldownUntil", "dodgeCooldown", "bladeSlashCooldownUntil", "lastStandCooldownUntil"
    };

    private HakiServerController() {}

    /** Admin/test switch. Survival balance is untouched unless an operator explicitly disables cooldowns. */
    public static boolean noCooldowns(ServerPlayer player) {
        return HakiData.flag(player, TEST_NO_COOLDOWNS);
    }

    public static void setNoCooldowns(ServerPlayer player, boolean disabled) {
        HakiData.flag(player, TEST_NO_COOLDOWNS, disabled);
        clearAbilityCooldowns(player);
    }

    private static void clearAbilityCooldowns(ServerPlayer player) {
        for (String key : ABILITY_COOLDOWN_KEYS) HakiData.longValue(player, key, 0L);
    }

    /** Armament passives are short hidden-particle vanilla effects refreshed server-side while coating is active. */
    private static void applyHakiMovement(ServerPlayer player) {
        if (HakiData.flag(player, "acocOn")) {
            // Advanced Haki replaces the normal Armament bonuses: Speed III, Resistance III,
            // Jump Boost I and complete Fire Resistance while J/ACoC is active.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 8, 2, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 8, 2, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 8, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 8, 0, false, false, true));
        } else if (HakiData.flag(player, "armamentOn")) {
            // Normal Armament: Speed II + Resistance II.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 8, 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 8, 1, false, false, true));
        }
    }

    public static void onAction(ServerPlayer player, HakiAction action) {
        if (!player.isAlive() || player.isSpectator() || !HakiData.enabled(player)) {
            cancelAll(player);
            return;
        }
        long now = player.level().getGameTime();
        // Final Stand is intentionally helpless: the passive owns the player's state until death.
        // Reject every Haki key packet here so toggles, charges, Galaxy, Grip, WiFi, Leap and slashes
        // cannot be started/released while the kneeling animation is active.
        if (HakiData.longValue(player, "lastStandUntil") > now) return;
        if (ACTIVE_GRABS.containsKey(player.getUUID())
                || player.getPersistentData().getLong("HexHakiGripUntil") > now) return;
        switch (action) {
            case ARMAMENT_TOGGLE -> toggleArmament(player);
            case OBSERVATION_TOGGLE -> toggleObservation(player);
            case CONQUEROR_START -> beginConqueror(player);
            case CONQUEROR_RELEASE -> releaseConqueror(player);
            case CONQUEROR_CYCLE_MODE -> {}
            case ACOC_TOGGLE -> toggleAcoc(player);
            case DOMINION_START -> beginDominion(player);
            case DOMINION_RELEASE -> releaseDominion(player);
            case SOVEREIGN_STRIKE -> sovereignStrike(player);
            case SOVEREIGN_STRIKE_RELEASE -> releaseSovereignStrike(player);
            case CONVERGENCE_START -> beginConvergence(player);
            case CONVERGENCE_RELEASE -> releaseConvergence(player);
            case GALAXY_PUNCH -> fireGalaxyImpact(player);
            case HAKI_LEAP_START -> beginHakiLeap(player);
            case HAKI_LEAP_RELEASE -> releaseHakiLeap(player);
            case HAKI_BLADE_SLASH -> bladeSlash(player);
        }
        broadcastEntityState(player);
        sync(player);
    }

    private static void bladeSlash(ServerPlayer player) {
        if (!HakiData.flag(player, "armamentOn") || !(player.getMainHandItem().getItem() instanceof SwordItem)) return;
        long now = player.level().getGameTime();
        int mastery = HakiData.mastery(player, HakiType.ARMAMENT);
        boolean acoc = HakiData.flag(player, "acocOn");
        long readyAt = HakiData.longValue(player, "bladeSlashCooldownUntil");
        if (!noCooldowns(player) && now < readyAt) return;
        float cost = acoc ? 2.4f : 1.6f;
        if (!HakiData.consume(player, cost)) return;

        int cooldown = acoc ? 5 : mastery >= 780 ? 6 : 7;
        HakiData.longValue(player, "bladeSlashCooldownUntil", now + cooldown);
        boolean horizontal = !HakiData.flag(player, "bladeSlashHorizontal");
        HakiData.flag(player, "bladeSlashHorizontal", horizontal);

        Vec3 dir = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(dir.scale(.72)).add(0, -.26, 0);
        double maxRange = 12.0 + mastery * .012 + (acoc ? 5.0 : 0.0);
        Vec3 desiredEnd = start.add(dir.scale(maxRange));
        HitResult blockHit = player.serverLevel().clip(new ClipContext(start, desiredEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double travel = blockHit.getType() == HitResult.Type.MISS ? maxRange : Math.max(.8, start.distanceTo(blockHit.getLocation()));
        Vec3 end = start.add(dir.scale(travel));

        float power = Math.max(.18f, mastery / 1000f);
        double width = 1.0 + power * .72 + (acoc ? .22 : 0);
        float baseDamage = (float)(player.getAttributeValue(Attributes.ATTACK_DAMAGE) * .68 + 2.0 + mastery / 160.0);
        baseDamage *= armamentEmissionMultiplier(mastery);
        if (acoc) baseDamage += 4.0f + HakiData.mastery(player, HakiType.CONQUEROR) / 250f;

        AABB search = new AABB(start, end).inflate(width + 1.0);
        for (LivingEntity living : player.serverLevel().getEntitiesOfClass(LivingEntity.class, search, e -> e != player && e.isAlive())) {
            Vec3 center = living.getBoundingBox().getCenter();
            Vec3 seg = end.subtract(start);
            double len2 = seg.lengthSqr();
            double t = len2 < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, center.subtract(start).dot(seg) / len2));
            Vec3 closest = start.add(seg.scale(t));
            double hitWidth = width + Math.max(.15, living.getBbWidth() * .45);
            if (center.distanceToSqr(closest) > hitWidth * hitWidth) continue;
            if (!player.hasLineOfSight(living)) continue;

            living.hurt(player.damageSources().playerAttack(player), baseDamage);
            double push = .42 + power * .48 + (acoc ? .35 : 0);
            living.setDeltaMovement(living.getDeltaMovement().add(dir.x * push, .08 + power * .08, dir.z * push));
            living.hurtMarked = true;
        }

        HakiNetwork.tracking(player, new S2CBladeSlash(
                player.getId(), start.x, start.y, start.z,
                (float)dir.x, (float)dir.y, (float)dir.z,
                (float)travel, horizontal, power, acoc, player.getRandom().nextLong()));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .92f, acoc ? .78f : .92f);
        if (acoc) player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, .34f, 1.45f);
        HakiProgression.award(player, HakiType.ARMAMENT, 1, "blade_slash", 10);
        if (acoc) HakiProgression.award(player, HakiType.CONQUEROR, 1, "blade_slash_acoc", 14);
    }

    private static void beginHakiLeap(ServerPlayer player) {
        int mastery = HakiData.mastery(player, HakiType.ARMAMENT);
        if (mastery < HAKI_LEAP_UNLOCK_MASTERY || !HakiData.flag(player, "armamentOn") || !player.onGround()) return;
        if (player.getAbilities().flying || player.isFallFlying() || HakiData.flag(player, "hakiLeapCharging")) return;
        HakiData.flag(player, "hakiLeapCharging", true);
        HakiData.integer(player, "hakiLeapChargeTicks", 0);
        animate(player, "haki_leap_charge", 2);
        player.displayClientMessage(Component.literal("Haki Leap  ·  CHARGING"), true);
    }

    private static void releaseHakiLeap(ServerPlayer player) {
        if (!HakiData.flag(player, "hakiLeapCharging")) return;
        HakiData.flag(player, "hakiLeapCharging", false);
        int ticks = Math.min(HAKI_LEAP_MAX_CHARGE_TICKS, HakiData.integer(player, "hakiLeapChargeTicks"));
        HakiData.integer(player, "hakiLeapChargeTicks", 0);

        int mastery = HakiData.mastery(player, HakiType.ARMAMENT);
        if (mastery < HAKI_LEAP_UNLOCK_MASTERY || !HakiData.flag(player, "armamentOn")) {
            animate(player, "__clear__", 2);
            performNormalJump(player);
            return;
        }

        float charge = Math.max(0f, Math.min(1f, ticks / (float)HAKI_LEAP_MAX_CHARGE_TICKS));
        float masteryScale = Math.max(0f, Math.min(1f, (mastery - HAKI_LEAP_UNLOCK_MASTERY) / 740f));
        float energyCost = 3.5f + 4.5f * charge;
        if (!HakiData.consume(player, energyCost)) {
            animate(player, "__clear__", 2);
            performNormalJump(player);
            return;
        }

        // Deliberately use only the horizontal component of look direction. Looking at the ground
        // or sky must never turn Haki Leap into a dive or a vertical rocket: it is always a high,
        // forward arc in the direction the player is facing.
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        if (forward.lengthSqr() < 1.0E-6) {
            double yaw = Math.toRadians(player.getYRot());
            forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        } else {
            forward = forward.normalize();
        }

        // This is meant to be a REAL traversal leap, not a slightly buffed sprint-jump. Even the
        // newly-unlocked version has a strong forward arc; charge + mastery roughly double the old
        // maximum, while Advanced Haki gives a modest endgame ignition bonus.
        double forwardSpeed = 1.85 + 1.55 * charge + 0.75 * masteryScale;
        double upwardSpeed = 0.92 + 0.50 * charge + 0.24 * masteryScale;
        if (HakiData.flag(player, "acocOn")) {
            forwardSpeed += 0.20;
            upwardSpeed += 0.08;
        }

        player.setDeltaMovement(forward.x * forwardSpeed, upwardSpeed, forward.z * forwardSpeed);
        player.hurtMarked = true;
        player.fallDistance = 0;
        HakiData.flag(player, "hakiLeapAirborne", true);
        HakiData.longValue(player, "hakiLeapLaunchTick", player.level().getGameTime());
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        animate(player, "haki_leap_release", 1);
        spawnHakiLeapBurst(player, charge, HakiData.flag(player, "acocOn"));
        sound(player, ModSounds.ARMAMENT_CRACKLE.get(), .42f, 1.05f + .12f * charge);
        HakiProgression.award(player, HakiType.ARMAMENT, charge >= .75f ? 2 : 1, "haki_leap", 60);
    }

    private static void performNormalJump(ServerPlayer player) {
        if (!player.onGround()) return;
        Vec3 motion = player.getDeltaMovement();
        double jumpY = 0.42D;
        MobEffectInstance jump = player.getEffect(MobEffects.JUMP);
        if (jump != null) jumpY += 0.10D * (jump.getAmplifier() + 1);
        double x = motion.x;
        double z = motion.z;
        if (player.isSprinting()) {
            double yaw = Math.toRadians(player.getYRot());
            x -= Math.sin(yaw) * 0.20D;
            z += Math.cos(yaw) * 0.20D;
        }
        player.setDeltaMovement(x, jumpY, z);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static void tickHakiLeap(ServerPlayer player) {
        if (!HakiData.flag(player, "hakiLeapCharging")) return;
        if (!HakiData.flag(player, "armamentOn") || HakiData.mastery(player, HakiType.ARMAMENT) < HAKI_LEAP_UNLOCK_MASTERY
                || player.getAbilities().flying || player.isFallFlying()) {
            HakiData.flag(player, "hakiLeapCharging", false);
            HakiData.integer(player, "hakiLeapChargeTicks", 0);
            animate(player, "__clear__", 2);
            return;
        }

        int before = HakiData.integer(player, "hakiLeapChargeTicks");
        int after = Math.min(HAKI_LEAP_MAX_CHARGE_TICKS, before + 1);
        HakiData.integer(player, "hakiLeapChargeTicks", after);

        if (player.tickCount % 5 == 0) {
            float charge = Math.max(0f, Math.min(1f, after / (float)HAKI_LEAP_MAX_CHARGE_TICKS));
            spawnHakiLeapChargeParticles(player, charge, HakiData.flag(player, "acocOn"));
        }
        if (before < HAKI_LEAP_MAX_CHARGE_TICKS && after == HAKI_LEAP_MAX_CHARGE_TICKS) {
            player.displayClientMessage(Component.literal("Haki Leap  ·  MAX"), true);
        }
    }

    private static void spawnHakiLeapChargeParticles(ServerPlayer player, float charge, boolean advanced) {
        ServerLevel level = player.serverLevel();
        double y = player.getY() + 0.12;
        DustParticleOptions dark = new DustParticleOptions(new Vector3f(0.035f, 0.018f, 0.025f), 1.1f + .35f * charge);
        DustParticleOptions red = new DustParticleOptions(new Vector3f(0.95f, 0.035f, 0.06f), 1.0f + .45f * charge);
        level.sendParticles(dark, player.getX(), y, player.getZ(), 7, .34, .05, .34, .025 + .02 * charge);
        level.sendParticles(red, player.getX(), y + .04, player.getZ(), 5, .30, .04, .30, .035 + .03 * charge);
        if (advanced) {
            DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.58f, 0.05f), 1.05f + .35f * charge);
            level.sendParticles(gold, player.getX(), y + .08, player.getZ(), 4, .27, .06, .27, .045);
        }
    }

    private static void spawnHakiLeapBurst(ServerPlayer player, float charge, boolean advanced) {
        ServerLevel level = player.serverLevel();
        double y = player.getY() + .10;
        DustParticleOptions red = new DustParticleOptions(new Vector3f(1.0f, 0.025f, 0.04f), 1.4f);
        DustParticleOptions black = new DustParticleOptions(new Vector3f(0.025f, 0.012f, 0.018f), 1.6f);
        level.sendParticles(black, player.getX(), y, player.getZ(), 22 + Math.round(charge * 14), .50, .08, .50, .11);
        level.sendParticles(red, player.getX(), y + .08, player.getZ(), 18 + Math.round(charge * 12), .46, .10, .46, .14);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), y, player.getZ(), 18, .48, .05, .48, .16 + .08 * charge);
        if (advanced) {
            DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.63f, 0.06f), 1.25f);
            level.sendParticles(gold, player.getX(), y + .12, player.getZ(), 12, .38, .11, .38, .12);
        }
    }

    private static void toggleArmament(ServerPlayer player) {
        boolean next = !HakiData.flag(player, "armamentOn");
        int mastery = HakiData.mastery(player, HakiType.ARMAMENT);
        if (next && mastery < HakiUnlocks.ARMAMENT_COATING) {
            locked(player, "Armament coating", HakiUnlocks.ARMAMENT_COATING, mastery);
            return;
        }
        if (next && !HakiData.consume(player, 2f)) return;
        HakiData.flag(player, "armamentOn", next);
        if (next) {
            // The Advanced-Haki gate measures uninterrupted time spent in normal Armament.
            HakiData.longValue(player, ADVANCED_HAKI_NORMAL_SINCE_KEY, player.level().getGameTime());
            animate(player, "armament_activate", 4);
            visual(player, S2CHakiVisual.Visual.ARMAMENT_ON, mastery01(player, HakiType.ARMAMENT), HakiData.mastery(player, HakiType.ARMAMENT));
            sound(player, ModSounds.ARMAMENT_ON.get(), .9f, 1.0f);
            HakiProgression.award(player, HakiType.ARMAMENT, 1, "activate", 200);
        } else {
            HakiData.flag(player, "acocOn", false);
            HakiData.longValue(player, ADVANCED_HAKI_NORMAL_SINCE_KEY, 0L);
            sound(player, ModSounds.ARMAMENT_OFF.get(), .65f, 1f);
        }
    }

    private static void toggleObservation(ServerPlayer player) {
        boolean next = !HakiData.flag(player, "observationOn");
        if (next && !HakiData.consume(player, 2f)) return;
        HakiData.flag(player, "observationOn", next);
        if (next) {
            animate(player, "observation_activate", 4);
            visual(player, S2CHakiVisual.Visual.OBSERVATION_PULSE, mastery01(player, HakiType.OBSERVATION), -1);
            sound(player, ModSounds.OBSERVATION_ON.get(), .8f, 1f);
            HakiProgression.award(player, HakiType.OBSERVATION, 1, "activate", 200);
        } else {
            sound(player, ModSounds.OBSERVATION_OFF.get(), .6f, 1f);
        }
    }

    private static void beginConqueror(ServerPlayer player) {
        if (!HakiProgression.conquerorAwakened(player)) {
            player.displayClientMessage(Component.literal("Conqueror's Haki awakens at " + HakiUnlocks.CONQUEROR_AWAKEN_ARMAMENT + " Armament + " + HakiUnlocks.CONQUEROR_AWAKEN_OBSERVATION + " Observation."), true);
            return;
        }
        if (HakiData.flag(player, "conquerorCharging") || HakiData.energy(player) < 7f) return;
        if (!cooldownReady(player, "conquerorCooldownUntil", "Conqueror's Haki")) return;
        int mastery = HakiData.mastery(player, HakiType.CONQUEROR);
        HakiData.flag(player, "conquerorCharging", true);
        HakiData.integer(player, "conquerorCharge", 0);
        HakiData.integer(player, "conquerorChargeTicks", 0);
        // The authored loop starts with a readable entry and then settles into a slow,
        // chest-and-shoulder laugh. Lightning is intentionally deferred: a quick G tap must
        // produce only its forward shockwave, never a stray radial charge bolt.
        animate(player, "conqueror_gear5_charge", 5);
        sound(player, ModSounds.CONQUEROR_CHARGE.get(), .85f, .78f);
    }

    private static void releaseConqueror(ServerPlayer player) {
        if (!HakiData.flag(player, "conquerorCharging")) return;
        HakiData.flag(player, "conquerorCharging", false);
        int chargeTicks = Math.max(0, HakiData.integer(player, "conquerorChargeTicks"));
        int charge = Math.max(ConquerorLightningPath.MIN_RELEASE_CHARGE,
                Math.min(CONQUEROR_MAX, HakiData.integer(player, "conquerorCharge")));
        HakiData.integer(player, "conquerorCharge", 0);
        HakiData.integer(player, "conquerorChargeTicks", 0);
        float power = charge / (float) CONQUEROR_MAX;
        int mastery = HakiData.mastery(player, HakiType.CONQUEROR);
        if (!HakiData.consume(player, 6f + 18f * power)) {
            animate(player, "__clear__", 3);
            return;
        }

        boolean tapped = chargeTicks <= CONQUEROR_TAP_MAX_CHARGE_TICKS;
        ConquerorMode mode = tapped ? ConquerorMode.CONE : ConquerorMode.RADIAL;
        boolean joyRelease = !tapped && HakiData.joyBoy(player) && mastery >= 1000 && power >= .96f;
        boolean maxHold = !tapped && charge >= CONQUEROR_MAX;
        // A tap is its own ten-block forward shockwave. Committed holds retain the trained
        // radial pressure curve and its exact fifty-block Joy Boy endpoint.
        double radius = tapped ? CONQUEROR_TAP_RANGE
                : ConquerorLightningPath.radius(mastery, power);

        // A committed full-charge release is a roar, not a gesture. Joy Boy keeps its own
        // authored supreme pose; every other maxed hold now screams.
        // A tap is a bare arm point with the lower body untouched; anything committed gets the
        // Gear 5 release, a full hold screams, and Joy Boy keeps its authored supreme pose.
        String releasePose = joyRelease ? "supreme_conqueror_release" : maxHold ? "conqueror_scream" : tapped ? "conqueror_tap_point" : "conqueror_gear5_release";
        animate(player, releasePose, 1);
        // A tap gets its own forward-cone effect. Reusing the held release's omnidirectional burst
        // both misrepresented the 75-degree cone it actually applies and buried a light move under
        // a heavy effect.
        if (tapped) visual(player, S2CHakiVisual.Visual.CONQUEROR_TAP_CONE, power, 0);
        else visual(player, S2CHakiVisual.Visual.CONQUEROR_RELEASE, power, encodeConquerorVariant(mastery, mode));
        applyConqueror(player, radius, power, mode, joyRelease, maxHold, tapped);
        // A full hold turns the complete visible lightning radius into the absolute stun core.
        if (maxHold) forceMaxConquerorStun(player, radius, CONQUEROR_MAX_STUN_TICKS);

        if (joyRelease) {
            HakiNetwork.to(player, new S2CCinematic("supreme_conqueror"));
            SupremeHakiDestruction.unleash(player, radius);
            sound(player, ModSounds.CONQUEROR_SUPREME_SNAP.get(), 3.3f, .96f);
            sound(player, ModSounds.CONQUEROR_SUPREME_BLAST.get(), 4f, .88f);
            scheduleSupremeAudio(player);
            scheduleAftershocks(player);
            HakiProgression.legend(player, 3, "joy_release", 1200);
        } else {
            sound(player, ModSounds.CONQUEROR_RELEASE.get(), 1.45f + power * .65f, .84f + .18f * power);
        }
        HakiData.longValue(player, "conquerorCooldownUntil",
                player.level().getGameTime() + conquerorReleaseCooldownTicks(charge));
        // Committed holds should train King's Haki faster than repeatedly feathering weak taps.
        // Tap remains 1 unit; a true MAX release reaches 9 units.
        HakiProgression.award(player, HakiType.CONQUEROR, 1 + Math.round(power * 8f), "pressure_release", 35);
    }

    private static void applyConqueror(ServerPlayer player, double radius, float power, ConquerorMode mode,
                                       boolean joyRelease, boolean maxHold, boolean tapped) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().normalize();
        int mastery = HakiData.mastery(player, HakiType.CONQUEROR);
        int visualBudget = joyRelease ? 24 : 12;
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && isConquerorCombatTarget(player, e));
        for (LivingEntity target : targets) {
            Vec3 delta = target.position().subtract(player.position());
            double distance = delta.length();
            if (distance > radius) continue;
            Vec3 aimDelta = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
            double facing = aimDelta.lengthSqr() < .01 ? 1 : look.dot(aimDelta.normalize());
            boolean absoluteMaxCore = maxHold;
            // Control modes shape ordinary Haoshoku. A true 100% release is different: the
            // visible explosion is a complete sphere/dome, so every valid combat target inside
            // the full release radius is caught regardless of Drive/Focus selection.
            if (!maxHold && mode == ConquerorMode.CONE && facing < CONQUEROR_TAP_CONE_DOT) continue;
            if (!maxHold && mode == ConquerorMode.FOCUS && facing < .965) continue;

            // A tap is intentionally self-contained: one modest hit and one short hard stun in
            // the forward cone. Its four-tick nudge is deliberately separate from the full-charge
            // radial launch; it inherits no will checks, blindness, damage bands, or battlefield effects.
            double rangeFraction = distance / Math.max(1.0D, radius);
            if (tapped) {
                target.hurt(player.damageSources().playerAttack(player), CONQUEROR_TAP_DAMAGE);
                stunConquerorTap(level, target, delta, rangeFraction);
                if (target instanceof ServerPlayer shocked) {
                    HakiNetwork.to(shocked, new S2CConquerorShock(.22f,
                            CONQUEROR_TAP_STUN_TICKS, CONQUEROR_TAP_STUN_TICKS + 15));
                }
                if (visualBudget-- > 0) {
                    HakiNetwork.tracking(player, new S2CHakiVisual(target.getId(),
                            S2CHakiVisual.Visual.CONQUEROR_HIT, .24f, 0,
                            player.getRandom().nextLong()));
                }
                continue;
            }

            // Held-release damage follows proportional bands inside the same visible radius.
            // Docile creatures never enter this target list and therefore cannot be collateral.
            if (maxHold) {
                float pressureDamage = rangeFraction <= .34D ? 30f : rangeFraction <= .68D ? 18f : 10f;
                target.hurt(player.damageSources().playerAttack(player), pressureDamage);
            } else if (power > .12f) {
                // Partial charges now bite too. Previously only a full hold could hurt anything,
                // so every release short of MAX was purely cosmetic.
                float banded = rangeFraction <= .34D ? 14f : rangeFraction <= .68D ? 8f : 4f;
                target.hurt(player.damageSources().playerAttack(player), banded * power);
            }

            // Conqueror's overwhelms the senses of anyone it catches. Scales hard with the charge:
            // a tap is a brief stagger, a full hold is a genuine sensory shutdown ending in
            // blindness. Sent only to players, since only they have a view to disrupt.
            if (target instanceof ServerPlayer shocked) {
                float shock = Math.min(1f, (maxHold ? 1f : power) * (float) (.45 + .55 * (1.0 - rangeFraction)));
                if (shock > .05f) {
                    int shockTicks = 40 + Math.round(shock * 150f);
                    int blindAt = Math.round(shockTicks * .55f);
                    HakiNetwork.to(shocked, new S2CConquerorShock(shock, shockTicks, blindAt));
                    // A real blindness effect backs the overlay so it also blocks the HUD/hand and
                    // cannot be defeated by turning the overlay off.
                    int blindTicks = Math.round(shockTicks * .60f);
                    if (blindTicks > 10) {
                        shocked.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindTicks, 0, false, false));
                    }
                    shocked.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                            Math.round(shockTicks * 1.4f), 0, false, false));
                }
            }

            double falloff = Math.max(.08, 1 - distance / radius);
            double control = mode == ConquerorMode.FOCUS ? 1.70 : mode == ConquerorMode.CONE ? 1.30 : 1.0;
            double will = (85 + mastery * .7 + power * 360) * (.3 + .7 * falloff) * control * (joyRelease ? 1.65 : 1);
            double resistance = target.getMaxHealth() * 2.4 + target.getArmorValue() * 9.5;
            if (target instanceof ServerPlayer other) resistance += HakiData.mastery(other, HakiType.CONQUEROR) * .75;
            if (isBoss(target)) resistance += 1100;
            double ratio = will / Math.max(1, resistance);

            if (maxHold) {
                // MAX release = true radial detonation. Even targets that are simultaneously
                // stunned must be physically thrown away from the caster. The edge of the
                // battlefield still receives a heavy shove; the inner pressure core is enormous.
                Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z);
                if (horizontal.lengthSqr() < 1.0E-6) {
                    Vec3 fallback = player.getLookAngle();
                    horizontal = new Vec3(-fallback.x, 0.0, -fallback.z);
                    if (horizontal.lengthSqr() < 1.0E-6) horizontal = new Vec3(1.0, 0.0, 0.0);
                }
                Vec3 away = horizontal.normalize();
                double blastFalloff = Math.max(0.0, Math.min(1.0, 1.0 - distance / Math.max(1.0, radius)));
                double shaped = Math.sqrt(blastFalloff);
                double force = CONQUEROR_MAX_BLAST_EDGE_FORCE
                        + (CONQUEROR_MAX_BLAST_CORE_FORCE - CONQUEROR_MAX_BLAST_EDGE_FORCE) * shaped;
                if (joyRelease) force *= 1.08;
                double lift = (.24 + .34 * shaped) * (joyRelease ? 1.06 : 1.0);

                // Replace most existing horizontal motion so the explosion direction wins cleanly,
                // while preserving a little prior momentum. This reads as one huge pressure wall.
                Vec3 previous = target.getDeltaMovement();
                target.setDeltaMovement(
                        away.x * force + previous.x * .15,
                        Math.max(previous.y * .15, lift),
                        away.z * force + previous.z * .15
                );
                long blastStart = level.getGameTime();
                long blastUntil = blastStart + CONQUEROR_MAX_BLAST_MOTION_TICKS;
                target.getPersistentData().putLong("HexHakiConquerorBlastMotionStart", blastStart);
                target.getPersistentData().putLong("HexHakiConquerorBlastMotionUntil", blastUntil);
                target.getPersistentData().putDouble("HexHakiConquerorBlastDirX", away.x);
                target.getPersistentData().putDouble("HexHakiConquerorBlastDirZ", away.z);
                target.getPersistentData().putDouble("HexHakiConquerorBlastForce", force);
                target.getPersistentData().putDouble("HexHakiConquerorBlastLift", lift);
                HakiEvents.trackConquerorBlast(target);
                target.hurtMarked = true;
                // ServerPlayer movement is client-authoritative enough that a one-shot delta can be
                // visually swallowed by the next movement packet. Send the explosion velocity
                // immediately; HakiEvents keeps enforcing the decaying radial launch afterwards.
                if (target instanceof ServerPlayer launchedPlayer) {
                    launchedPlayer.connection.send(new ClientboundSetEntityMotionPacket(launchedPlayer));
                }
            } else {
                Vec3 push = delta.lengthSqr() < .01 ? Vec3.ZERO : delta.normalize().scale((.3 + power * 1.7) * (.35 + .65 * falloff) * control * (joyRelease ? 1.8 : 1));
                target.setDeltaMovement(target.getDeltaMovement().add(push.x, .12 + power * .22, push.z));
                target.hurtMarked = true;
            }

            if (absoluteMaxCore) {
                // No resistance roll, boss exception, armor check, player exception, or willpower gate.
                // The dedicated max-charge stun helper locks control without cancelling blast propulsion.
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CONQUEROR_MAX_STUN_TICKS, 10, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CONQUEROR_MAX_STUN_TICKS, 6, false, false));
            } else if (isBoss(target)) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, joyRelease ? 80 : 24, joyRelease ? 3 : 1, false, false));
                if (joyRelease) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 2, false, false));
            } else if (ratio >= (joyRelease ? .5 : 1.05)) {
                int downTicks = joyRelease ? 180 : 60 + HakiRank.of(mastery).tier() * 8;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, downTicks, 10, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, downTicks, joyRelease ? 6 : 4, false, false));
                knockDown(level, target, downTicks);
            } else if (ratio >= .35) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, joyRelease ? 90 : 35, joyRelease ? 5 : 2, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, joyRelease ? 90 : 35, joyRelease ? 3 : 1, false, false));
            }

            if (visualBudget-- > 0) {
                HakiNetwork.tracking(player, new S2CHakiVisual(target.getId(), S2CHakiVisual.Visual.CONQUEROR_HIT, (float) Math.min(1, ratio), ratio >= 1 ? 1 : 0, player.getRandom().nextLong()));
            }
        }
    }

    private static void stunConquerorTap(ServerLevel level, LivingEntity target,
                                          Vec3 delta, double rangeFraction) {
        long now = level.getGameTime();
        long previousUntil = target.getPersistentData().getLong("HexHakiForcedConquerorStunUntil");
        boolean newlyStunned = previousUntil <= now;
        target.getPersistentData().putLong("HexHakiForcedConquerorStunUntil",
                Math.max(previousUntil, now + CONQUEROR_TAP_STUN_TICKS));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                CONQUEROR_TAP_STUN_TICKS, 10, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                CONQUEROR_TAP_STUN_TICKS, 2, false, false));

        // Let the pressure front carry the victim for four ticks, then the remaining stun takes
        // over. Reusing the existing authoritative blast-motion tags prevents players and
        // aggressive modded mobs from cancelling the small push with their next movement update.
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() < 1.0E-6D) horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 away = horizontal.normalize();
        double falloff = Math.max(0.0D, Math.min(1.0D, 1.0D - rangeFraction));
        double push = .32D + .22D * Math.sqrt(falloff);
        double lift = .08D + .06D * falloff;
        target.setDeltaMovement(away.x * push, Math.max(target.getDeltaMovement().y, lift),
                away.z * push);
        target.getPersistentData().putLong("HexHakiConquerorBlastMotionStart", now);
        target.getPersistentData().putLong("HexHakiConquerorBlastMotionUntil",
                now + CONQUEROR_TAP_PUSH_TICKS);
        target.getPersistentData().putDouble("HexHakiConquerorBlastDirX", away.x);
        target.getPersistentData().putDouble("HexHakiConquerorBlastDirZ", away.z);
        target.getPersistentData().putDouble("HexHakiConquerorBlastForce", push);
        target.getPersistentData().putDouble("HexHakiConquerorBlastLift", lift);
        HakiEvents.trackConquerorBlast(target);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer pushedPlayer) {
            pushedPlayer.connection.send(new ClientboundSetEntityMotionPacket(pushedPlayer));
        }
        if (newlyStunned) knockDown(level, target, CONQUEROR_TAP_STUN_TICKS);
    }

    /**
     * Absolute battlefield-control core for a 100% Conqueror charge. Every valid combat target
     * except the caster inside {@code radius} is stunned, including bosses and players.
     * Player movement is additionally hard-frozen by HakiEvents until the stored expiry.
     */
    private static void forceMaxConquerorStun(ServerPlayer player, double radius, int ticks) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long until = now + Math.max(1, ticks);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && e.distanceToSqr(player) <= radius * radius
                        && isConquerorCombatTarget(player, e))) {
            long previousUntil = target.getPersistentData().getLong("HexHakiForcedConquerorStunUntil");
            boolean newlyStunned = previousUntil <= now;
            target.getPersistentData().putLong("HexHakiForcedConquerorStunUntil", until);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 10, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 6, false, false));
            // Never erase velocity from the actual MAX release. HakiEvents preserves this motion
            // window as well, so stunned victims can be launched while remaining unable to act.
            if (target.getPersistentData().getLong("HexHakiConquerorBlastMotionUntil") <= now) {
                target.setDeltaMovement(Vec3.ZERO);
            }
            target.hurtMarked = true;
            // Only fire the knockdown animation/pose once. MAX stun is now applied on release
            // only and has a hard 200-tick lifetime.
            if (newlyStunned) knockDown(level, target, ticks);
        }
    }

    private static void knockDown(ServerLevel level, LivingEntity target, int ticks) {
        knockDown(level, target, ticks, false);
    }

    /** Last Stand reuses the real Conqueror knockdown, but holds a subtle twitch loop until release. */
    public static void knockDownForLastStand(ServerLevel level, LivingEntity target, int ticks) {
        knockDown(level, target, ticks, true);
    }

    private static void knockDown(ServerLevel level, LivingEntity target, int ticks, boolean twitch) {
        if (target instanceof ServerPlayer player) {
            // Conqueror's Haki overwhelms the will; a victim should read as being in pain, not
            // merely as having fallen over. Longer stuns get the sustained reactions, brief ones
            // keep the sharp knockdowns so short taps still feel like a hit rather than a scene.
            String painPose = twitch ? "haki_knockdown_twitch"
                    : ticks >= 60 ? (player.getRandom().nextBoolean() ? "haki_pain_convulse" : "haki_pain_clutch")
                    : ticks >= 30 ? "haki_pain_clutch"
                    : (player.getRandom().nextBoolean() ? "haki_knockdown_left" : "haki_knockdown_right");
            animate(player, painPose, 1);
            UUID id = player.getUUID();
            ServerTimeline.later(level, ticks, () -> {
                ServerPlayer found = level.getServer().getPlayerList().getPlayer(id);
                if (found != null) animate(found, "__clear__", 3);
            });
            return;
        }
        Pose oldPose = target.getPose();
        target.setPose(Pose.SWIMMING);
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            // Never toggle NoAI for Conqueror stun. A stale NoAI flag was the source of mobs
            // remaining permanently paralysed after the effect had visually ended.
        }
        UUID id = target.getUUID();
        ServerTimeline.later(level, ticks, () -> {
            Entity found = level.getEntity(id);
            if (!(found instanceof LivingEntity living) || !living.isAlive()) return;
            living.setPose(oldPose == Pose.SWIMMING ? Pose.STANDING : oldPose);
            if (living instanceof Mob mob) mob.getNavigation().stop();
        });
    }

    private static void cycleMode(ServerPlayer player) {
        HakiData.conquerorMode(player, ConquerorMode.RADIAL);
        player.displayClientMessage(Component.literal("Conqueror mode selection was removed. Releases are now always radial."), true);
    }

    private static void toggleAcoc(ServerPlayer player) {
        int mastery = HakiData.mastery(player, HakiType.CONQUEROR);
        int armament = HakiData.mastery(player, HakiType.ARMAMENT);
        if (!HakiProgression.advancedHakiUnlocked(player) || !HakiData.flag(player, "armamentOn")) {
            player.displayClientMessage(Component.literal("Advanced Haki [J] requires " + HakiUnlocks.ADVANCED_HAKI_ARMAMENT + " Armament + " + HakiUnlocks.ADVANCED_HAKI_CONQUEROR + " Conqueror mastery and active Armament coating."), true);
            return;
        }
        boolean next = !HakiData.flag(player, "acocOn");
        long now = player.level().getGameTime();
        if (next) {
            long normalSince = HakiData.longValue(player, ADVANCED_HAKI_NORMAL_SINCE_KEY);
            if (normalSince <= 0L || normalSince > now) {
                normalSince = now;
                HakiData.longValue(player, ADVANCED_HAKI_NORMAL_SINCE_KEY, normalSince);
            }
            long normalTicks = now - normalSince;
            if (normalTicks < ADVANCED_HAKI_NORMAL_WARMUP_TICKS) {
                float remaining = (ADVANCED_HAKI_NORMAL_WARMUP_TICKS - normalTicks) / 20.0f;
                player.displayClientMessage(Component.literal("Advanced Haki: stay in normal Armament for "
                        + String.format(Locale.ROOT, "%.1f", remaining) + "s more."), true);
                return;
            }
            if (!HakiData.consume(player, 5f)) return;
        }
        HakiData.flag(player, "acocOn", next);
        if (next) {
            // Leaving normal Armament ends this warmup window. The next activation must earn a fresh 5s window.
            HakiData.longValue(player, ADVANCED_HAKI_NORMAL_SINCE_KEY, 0L);
            animate(player, "acoc_activate", 2);
            visual(player, S2CHakiVisual.Visual.ACOC, mastery01(player, HakiType.CONQUEROR), mastery);
            // Advanced Haki replaces the normal activation cue immediately instead of inheriting the global 1s sound delay.
            immediateSound(player, ModSounds.ACOC_ON.get(), .45f, 1f);
        } else {
            // Back in normal Armament: a new uninterrupted 5-second warmup starts now.
            HakiData.longValue(player, ADVANCED_HAKI_NORMAL_SINCE_KEY, now);
            sound(player, ModSounds.ACOC_OFF.get(), .9f, 1f);
        }
    }

    /**
     * V2: Dominion has been removed. K now activates King's Grip, a single-target synchronized
     * throat-grab cinematic. Both positions, stun, damage and release are server-authoritative;
     * player victims receive their own forced camera sequence instead of watching from normal POV.
     */
    private static void beginDominion(ServerPlayer player) {
        boolean gripTestMode = HakiData.unlimitedEnergy(player);
        if (!gripTestMode && !HakiProgression.kingsGripUnlocked(player)) {
            player.displayClientMessage(Component.literal("Haki Grip [K] requires ARM "
                    + HakiUnlocks.KINGS_GRIP_ARMAMENT + " + OBS " + HakiUnlocks.KINGS_GRIP_OBSERVATION
                    + " + HAO " + HakiUnlocks.KINGS_GRIP_CONQUEROR + "."), true);
            return;
        }
        long now = player.level().getGameTime();
        long readyAt = HakiData.longValue(player, "dominionCooldownUntil");
        if (!noCooldowns(player) && now < readyAt) {
            long tenths = Math.max(1L, (readyAt - now + 1L) / 2L);
            player.displayClientMessage(Component.literal("Haki Grip recovering: " + (tenths / 10f) + "s"), true);
            return;
        }
        if (HakiData.flag(player, "dominionOn") || ACTIVE_GRABS.containsKey(player.getUUID())) return;

        // The draw hauls them in, so acquisition reaches well past melee. The wider tolerance is
        // deliberate: you are pulling a target toward you, not threading a grab through a hitbox.
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().normalize().scale(THRAGG_RANGE));
        LivingEntity target = nearestOnSegment(player, eye, end, 1.60);
        if (target == null || !player.hasLineOfSight(target)) {
            player.displayClientMessage(Component.literal("Haki Grip needs a living target in your sights."), true);
            return;
        }
        if (target.getBbWidth() > 3.0f || target.getBbHeight() > 4.5f) {
            player.displayClientMessage(Component.literal("That target is too massive to haul in and launch."), true);
            return;
        }
        if (isEntityInActiveGrab(target.getUUID())) {
            player.displayClientMessage(Component.literal("That target is already caught in a Haki lock."), true);
            return;
        }

        // The move works with coating disabled; the active coating only decides which tier of the
        // sequence commits. J/ACoC is the Advanced-Haki tier.
        int tier = HakiData.flag(player, "acocOn") ? THRAGG_ADVANCED
                : (HakiData.flag(player, "armamentOn") ? THRAGG_HAKI : THRAGG_NO_HAKI);
        float energyCost = thraggEnergyCost(tier);
        if (HakiData.energy(player) < energyCost) {
            player.displayClientMessage(Component.literal("Haki Grip needs " + Math.round(energyCost)
                    + " Haki energy for this version."), true);
            return;
        }
        if (!HakiData.consume(player, energyCost)) return;

        HakiData.longValue(player, "dominionCooldownUntil", now + thraggCooldownTicks(tier));
        HakiData.flag(player, "dominionOn", true);

        // Face the target at commit. Everything after this is driven off the frozen yaw, so the
        // draw, the launch column and the slam all share one axis on every client.
        Vec3 toTarget = target.position().subtract(player.position());
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        Vec3 anchor = player.position();
        double yawRad = Math.toRadians(yaw);
        Vec3 columnFoot = anchor.add(new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).scale(1.35));
        GrabState state = new GrabState(target.getUUID(), anchor, yaw, now, tier,
                groundBelow(player.serverLevel(), columnFoot, anchor.y));
        ACTIVE_GRABS.put(player.getUUID(), state);
        ThraggBeats beats = thraggBeats(tier);
        player.getPersistentData().putLong("HexHakiGripAttackerUntil", now + beats.duration() + 4L);

        tagGrip(target, player, now + beats.duration() + 4L);
        // Both of them are about to be moved vertically by the server, so neither should be billed
        // for the drop. The window outlasts the sequence so the recovery fall is covered too.
        grantFallImmunity(player, now + beats.duration() + 60L);
        grantFallImmunity(target, now + beats.duration() + 60L);
        orientGrabPair(player, target, state);
        driveThraggPair(player, target, state, 0);

        String attackerAnimation = thraggAttackerAnimation(tier);
        String victimAnimation = thraggVictimAnimation(tier);
        animate(player, attackerAnimation, 1);
        if (target instanceof ServerPlayer victim) {
            animate(victim, victimAnimation, 1);
            HakiNetwork.to(victim, new S2CCinematic(victimAnimation, yaw + 180f, 0f, true, target.getId()));
        }
        HakiNetwork.to(player, new S2CCinematic(attackerAnimation, yaw, 0f, true, target.getId()));
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.DOMINION_ON.get(),
                SoundSource.PLAYERS, 1.15f, .82f);
    }

    /** Key-up does not cancel the Haki Grip. Once the draw starts, the sequence is committed. */
    private static void releaseDominion(ServerPlayer player) {
        // Intentionally empty. DOMINION_RELEASE remains in the protocol so old keybind/client code
        // cannot desync a server, but the sequence commits on DOMINION_START.
    }

    /** First solid surface at or below {@code from}, within eight blocks; the start Y otherwise. */
    private static double groundBelow(ServerLevel level, Vec3 from, double fallbackY) {
        Vec3 start = new Vec3(from.x, fallbackY + 1.0, from.z);
        Vec3 end = new Vec3(from.x, fallbackY - 8.0, from.z);
        var hit = level.clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, null));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? fallbackY : hit.getLocation().y;
    }

    private static boolean isEntityInActiveGrab(UUID entityId) {
        if (ACTIVE_GRABS.containsKey(entityId)) return true;
        for (GrabState state : ACTIVE_GRABS.values()) if (state.targetId().equals(entityId)) return true;
        return false;
    }

    private static void tagGrip(LivingEntity target, ServerPlayer owner, long until) {
        var tag = target.getPersistentData();
        tag.putLong("HexHakiGripUntil", until);
        tag.putUUID("HexHakiGripOwner", owner.getUUID());
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    private static void clearGripTag(LivingEntity target) {
        var tag = target.getPersistentData();
        tag.remove("HexHakiGripUntil");
        tag.remove("HexHakiGripOwner");
    }

    private static void orientGrabPair(ServerPlayer attacker, LivingEntity target, GrabState state) {
        attacker.setYRot(state.attackerYaw());
        attacker.setYHeadRot(state.attackerYaw());
        attacker.yBodyRot = state.attackerYaw();
        // Pitch is owned by driveThraggPair (it looks down at the drop), so it is deliberately
        // not reset here.
        float targetYaw = state.attackerYaw() + 180f;
        target.setYRot(targetYaw);
        target.setYHeadRot(targetYaw);
        target.yBodyRot = targetYaw;
        target.setXRot(0f);
    }

    /**
     * Drives both bodies through the whole Haki Grip. Everything is server-scripted rather than
     * physical, because the attacker has to be reliably above a target that is already in the air:
     * a launch left to velocity lands somewhere different on every surface, ping and hitbox.
     *
     * <ol>
     *   <li><b>Draw</b> (0 → uppercut). The attacker is planted. The target is hauled toward a hold
     *       point a stride in front of them, on a curve that barely moves at first and then rips.</li>
     *   <li><b>Rise</b> (uppercut → slam). The target climbs the launch column on a decelerating
     *       curve, so it slows as it reaches the top the way a real launch does.</li>
     *   <li><b>Ascend</b> (ascend → slam). The attacker holds above the top of the column, and the
     *       target rises to meet them.</li>
     *   <li><b>Drive</b> (slam → ground). Both come down, the target accelerating into the floor
     *       and the attacker riding it a beat behind.</li>
     * </ol>
     */
    private static void driveThraggPair(ServerPlayer attacker, LivingEntity target, GrabState state, int age) {
        ThraggBeats beats = thraggBeats(state.tier());
        double yawRad = Math.toRadians(state.attackerYaw());
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3 anchor = state.attackerAnchor();
        // The column stands on the sampled floor, not on the attacker's feet.
        Vec3 hold = anchor.add(forward.scale(1.35));
        double floorY = state.groundY();
        double rise = thraggRiseHeight(state.tier());
        double hoverY = floorY + rise + 2.4;

        // ---- the victim ---------------------------------------------------------------------
        if (age <= beats.uppercut()) {
            // Accelerating haul. A linear pull reads as being dragged on a rope; this reads as
            // losing a fight against something that keeps getting stronger.
            double t = Math.min(1.0, age / (double) Math.max(1, beats.uppercut()));
            double bite = .05 + .34 * t * t;
            Vec3 pull = target.position().add(hold.subtract(target.position()).scale(bite));
            placeVictim(target, pull);
        } else if (age <= beats.slam()) {
            double t = (age - beats.uppercut()) / (double) Math.max(1, beats.slam() - beats.uppercut());
            // Decelerating climb: fast off the fist, slowing as it tops out.
            double eased = 1.0 - (1.0 - t) * (1.0 - t);
            placeVictim(target, new Vec3(hold.x, floorY + rise * eased, hold.z));
        } else if (age <= beats.ground()) {
            double t = (age - beats.slam()) / (double) Math.max(1, beats.ground() - beats.slam());
            // Accelerating drive down -- the mirror of the climb.
            double eased = t * t;
            placeVictim(target, new Vec3(hold.x, floorY + rise * (1.0 - eased), hold.z));
        }

        // ---- the attacker -------------------------------------------------------------------
        // He waits above and deliberately BEHIND the launch column rather than inside it. Sitting
        // on the column put him in the same cubic metre as a body travelling at two blocks a tick,
        // which is both unreadable and a good way to get shoved into the crater with them.
        int release = beats.slam() + 6;
        if (age >= beats.ascend() && age <= release) {
            Vec3 perch = hold.subtract(forward.scale(1.6));
            double follow = age <= beats.slam() ? 0.0
                    : (age - beats.slam()) / (double) Math.max(1, release - beats.slam());
            holdEntityAt(attacker, new Vec3(perch.x, floorY + rise + 1.9 - follow * 4.5, perch.z));
            attacker.setXRot(62f);
        } else if (age < beats.ascend()) {
            holdEntityAt(attacker, anchor);
        }
        orientGrabPair(attacker, target, state);
    }

    /**
     * Grants a window in which a participant cannot take fall damage.
     *
     * <p>The sequence puts one player twenty-odd blocks up and drops the other from the same
     * height, both by teleport. Zeroing {@code fallDistance} covers the scripted part, but the
     * attacker is still airborne when the script hands control back, so vanilla starts counting
     * again from wherever the server left them. HakiEvents reads this tag in LivingFallEvent.
     */
    private static void grantFallImmunity(LivingEntity entity, long untilTick) {
        entity.getPersistentData().putLong("HexHakiNoFallUntil", untilTick);
        entity.fallDistance = 0;
    }

    /** Pins one entity at a world position with no motion and no accumulated fall. */
    private static void holdEntityAt(ServerPlayer player, Vec3 where) {
        if (player.position().distanceToSqr(where) > 0.0025) {
            player.teleportTo(where.x, where.y, where.z);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static void placeVictim(LivingEntity target, Vec3 where) {
        boolean moved = target.position().distanceToSqr(where) > 0.0025;
        if (moved) target.teleportTo(where.x, where.y, where.z);
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        target.fallDistance = 0;
        if (moved && target instanceof ServerPlayer victim) {
            victim.connection.send(new ClientboundSetEntityMotionPacket(victim));
        }
    }

    private static void tickKingsGrip(ServerPlayer attacker) {
        GrabState state = ACTIVE_GRABS.get(attacker.getUUID());
        if (state == null) return;
        ServerLevel level = attacker.serverLevel();
        Entity found = level.getEntity(state.targetId());
        if (!(found instanceof LivingEntity target)) {
            finishKingsGrip(attacker, null, false);
            return;
        }
        if (!target.isAlive() || !attacker.isAlive()) {
            finishKingsGrip(attacker, target, false);
            return;
        }

        ThraggBeats beats = thraggBeats(state.tier());
        int age = (int) (level.getGameTime() - state.startTick());
        if (age <= beats.duration()) driveThraggPair(attacker, target, state, age);

        // The draw is a continuous wind, so it pulses rather than firing once: every third tick
        // carries a rising ramp so the vortex tightens visibly as the target is reeled in.
        if (age < beats.uppercut() && age % 3 == 0) {
            float ramp = Mth.clamp(age / (float) Math.max(1, beats.uppercut()), 0f, 1f);
            HakiNetwork.tracking(attacker, new S2CHakiVisual(attacker.getId(),
                    S2CHakiVisual.Visual.THRAGG_DRAW, ramp, state.tier(), attacker.getRandom().nextLong()));
        }
        // Flames on the way up and a burning wake on the way down. Pulsed rather than fired once,
        // because the flight is now over two seconds long and a single burst at the launch would
        // be out long before they reach the top.
        if (age > beats.uppercut() && age < beats.ground() && age % 4 == 0) {
            boolean falling = age > beats.slam();
            float span = falling
                    ? (age - beats.slam()) / (float) Math.max(1, beats.ground() - beats.slam())
                    : (age - beats.uppercut()) / (float) Math.max(1, beats.slam() - beats.uppercut());
            HakiNetwork.tracking(attacker, new S2CHakiVisual(target.getId(),
                    S2CHakiVisual.Visual.THRAGG_TRAIL, Mth.clamp(span, 0f, 1f),
                    state.tier() + (falling ? 4 : 0), attacker.getRandom().nextLong()));
        }
        if (age == beats.slam() + 6) {
            // Hand physics back and let him come down on his own. Slow Falling is the ask and it
            // is also the correct mechanic: it removes the fall damage, it keeps him out of the
            // crater he is about to make, and it reads from the ground as somebody descending
            // rather than a body being lowered on a wire.
            attacker.setXRot(0f);
            attacker.setDeltaMovement(Vec3.ZERO);
            attacker.fallDistance = 0;
            attacker.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 260, 0, false, false, true));
            attacker.connection.send(new ClientboundSetEntityMotionPacket(attacker));
        }
        if (age == beats.uppercut()) thraggUppercut(attacker, target, state);
        if (age == beats.ascend()) thraggAscend(attacker, target, state);
        if (age == beats.slam()) thraggSlam(attacker, target, state);
        if (age == beats.ground()) thraggCrater(attacker, target, state);
        if (age >= beats.duration() || !target.isAlive()) finishKingsGrip(attacker, target, true);
    }

    /** Beat 2: the fist lands and they leave the ground. */
    private static void thraggUppercut(ServerPlayer attacker, LivingEntity target, GrabState state) {
        ServerLevel level = attacker.serverLevel();
        float power = state.tier() == THRAGG_ADVANCED ? 1.55f : state.tier() == THRAGG_HAKI ? .96f : .55f;
        Vec3 contact = target.position().add(0, target.getBbHeight() * .45, 0);

        long frameSeed = attacker.getRandom().nextLong();
        HakiNetwork.to(attacker, new S2CKingsGripImpactFrame(state.tier(), frameSeed));
        if (target instanceof ServerPlayer victim) {
            HakiNetwork.to(victim, new S2CKingsGripImpactFrame(state.tier(), frameSeed));
        }
        HakiNetwork.tracking(attacker, new S2CHakiVisual(target.getId(),
                S2CHakiVisual.Visual.THRAGG_UPPERCUT, power, state.tier(), attacker.getRandom().nextLong()));
        // The air sheet leaves through the top of them, because that is where the fist went.
        HakiNetwork.tracking(attacker, new S2CArmamentImpact(
                attacker.getId(), target.getId(), power, state.tier() == THRAGG_ADVANCED, 1,
                contact.x, contact.y, contact.z, 0, 1, 0, attacker.getRandom().nextLong()));

        if (state.tier() == THRAGG_ADVANCED) {
            level.playSound(null, target.blockPosition(), ModSounds.SOVEREIGN_STRIKE.get(), SoundSource.PLAYERS, 2.5f, .68f);
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f, .70f);
        } else if (state.tier() == THRAGG_HAKI) {
            level.playSound(null, target.blockPosition(), ModSounds.SOVEREIGN_STRIKE.get(), SoundSource.PLAYERS, 2.0f, .78f);
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, .95f, .82f);
        } else {
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.35f, .78f);
        }
        // The uppercut sets the sequence up; it is not the finisher. Most of the damage waits
        // for the landing, so surviving the launch is not the same as surviving the move.
        thraggHurt(attacker, target, thraggTotalDamage(attacker, state.tier()) * .15f, state.tier(), false);
        HakiProgression.legend(attacker, 2 + state.tier(), "haki_grip", 600);
        HakiProgression.award(attacker, HakiType.ARMAMENT, 5 + state.tier() * 3, "haki_grip", 120);
        HakiProgression.award(attacker, HakiType.OBSERVATION, 4, "haki_grip", 120);
        HakiProgression.award(attacker, HakiType.CONQUEROR, 6 + state.tier() * 2, "haki_grip", 120);
    }

    /** Beat 3: the attacker is suddenly above them. */
    private static void thraggAscend(ServerPlayer attacker, LivingEntity target, GrabState state) {
        ServerLevel level = attacker.serverLevel();
        float power = state.tier() == THRAGG_ADVANCED ? 1.55f : state.tier() == THRAGG_HAKI ? .96f : .55f;
        // Two visuals: one where they left, one where they arrived, so the blink has both ends.
        HakiNetwork.tracking(attacker, new S2CHakiVisual(attacker.getId(),
                S2CHakiVisual.Visual.THRAGG_BLINK, power, state.tier(), attacker.getRandom().nextLong()));
        level.playSound(null, attacker.blockPosition(), ModSounds.SOVEREIGN_STRIKE.get(),
                SoundSource.PLAYERS, 1.1f, 1.55f);
    }

    /** Beat 4: both arms come down on the back of their head. */
    private static void thraggSlam(ServerPlayer attacker, LivingEntity target, GrabState state) {
        ServerLevel level = attacker.serverLevel();
        float power = state.tier() == THRAGG_ADVANCED ? 1.55f : state.tier() == THRAGG_HAKI ? .96f : .55f;
        Vec3 contact = target.position().add(0, target.getBbHeight() * .85, 0);
        HakiNetwork.tracking(attacker, new S2CHakiVisual(target.getId(),
                S2CHakiVisual.Visual.THRAGG_SLAM, power, state.tier(), attacker.getRandom().nextLong()));
        HakiNetwork.tracking(attacker, new S2CArmamentImpact(
                attacker.getId(), target.getId(), power, state.tier() == THRAGG_ADVANCED, 1,
                contact.x, contact.y, contact.z, 0, -1, 0, attacker.getRandom().nextLong()));
        level.playSound(null, target.blockPosition(), ModSounds.SOVEREIGN_STRIKE.get(),
                SoundSource.PLAYERS, 2.2f, .58f);
        thraggHurt(attacker, target, thraggTotalDamage(attacker, state.tier()) * .25f, state.tier(), false);
    }

    /** Beat 5: they arrive. The floor does not survive it and neither does anything standing on it. */
    private static void thraggCrater(ServerPlayer attacker, LivingEntity target, GrabState state) {
        ServerLevel level = attacker.serverLevel();
        float power = state.tier() == THRAGG_ADVANCED ? 1.55f : state.tier() == THRAGG_HAKI ? .96f : .55f;
        double radius = switch (state.tier()) {
            case THRAGG_ADVANCED -> 14.0;
            case THRAGG_HAKI -> 9.0;
            default -> 5.5;
        };
        // Addressed to the impact point, not to the body that made it. Entity-addressed delivery
        // needs the victim to still resolve on every viewer's client at this exact tick, and a body
        // that has just been driven sixty blocks into the floor is where that quietly fails -- which
        // is why the explosion kept not appearing at all.
        Vec3 at = target.position();
        HakiNetwork.near(level, at.x, at.y, at.z, 128.0,
                new S2CWorldFx(S2CWorldFx.THRAGG_CRATER, power, state.tier(),
                        at.x, at.y, at.z, attacker.getRandom().nextLong()));
        level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, state.tier() == THRAGG_ADVANCED ? 2.4f : 1.4f, .52f);

        float total = thraggTotalDamage(attacker, state.tier());
        thraggHurt(attacker, target, total * .60f, state.tier(), true);
        clearGripTag(target);
        target.fallDistance = 0;

        // Everything else standing in the crater is thrown clear and takes a share.
        for (LivingEntity bystander : level.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(radius),
                living -> living != target && living != attacker && living.isAlive())) {
            Vec3 away = bystander.position().subtract(target.position());
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(0, 1, 0);
            away = away.normalize();
            bystander.invulnerableTime = 0;
            bystander.hurt(attacker.damageSources().playerAttack(attacker), total * .24f);
            bystander.setDeltaMovement(away.x * 1.1, .55, away.z * 1.1);
            bystander.hurtMarked = true;
            if (bystander instanceof ServerPlayer pushed) {
                pushed.connection.send(new ClientboundSetEntityMotionPacket(pushed));
            }
        }
    }

    /**
     * Applies one beat of the sequence's damage.
     *
     * <p>The generic cinematic combat lock cancels attacks from both participants, so the scripted
     * hits need a scoped exception or Forge's LivingAttackEvent eats them. Vanilla hurt immunity is
     * also reset, since three hits land inside the ten-tick invulnerability window.
     */
    private static void thraggHurt(ServerPlayer attacker, LivingEntity target, float damage, int tier,
                                   boolean lethal) {
        if (!target.isAlive()) return;
        if (isBoss(target)) damage *= tier == THRAGG_ADVANCED ? .58f : .66f;
        if (!lethal) {
            // The uppercut and the slam are set-up, and the sequence cannot finish without a body
            // to land: if either one killed the target the run aborted mid-air and the crater --
            // the finisher, and 60% of the damage -- never happened at all. They are clamped to
            // leave a sliver of health so the landing is always the kill.
            float survivable = Math.max(0f, target.getHealth() - 0.5f);
            if (damage >= survivable) damage = survivable;
            if (damage <= 0f) return;
        }
        var attackerTag = attacker.getPersistentData();
        attackerTag.putBoolean("HexHakiAllowGripImpact", true);
        try {
            target.invulnerableTime = 0;
            target.hurt(attacker.damageSources().playerAttack(attacker), damage);
        } finally {
            attackerTag.remove("HexHakiAllowGripImpact");
        }
    }

    private static void finishKingsGrip(ServerPlayer attacker, LivingEntity target, boolean normalEnd) {
        ACTIVE_GRABS.remove(attacker.getUUID());
        attacker.getPersistentData().remove("HexHakiGripAttackerUntil");
        HakiData.flag(attacker, "dominionOn", false);
        attacker.setDeltaMovement(Vec3.ZERO);
        // The sequence puts the attacker twenty blocks up and pitches them to look down at it.
        // Both have to be undone, or an aborted run drops them out of the sky taking fall damage
        // for a height the server put them at, staring at the floor.
        attacker.setXRot(0f);
        attacker.fallDistance = 0;
        if (!attacker.onGround()) {
            attacker.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 260, 0, false, false, true));
        }
        attacker.connection.send(new ClientboundSetEntityMotionPacket(attacker));
        animate(attacker, "__clear__", 3);
        if (target != null) {
            clearGripTag(target);
            target.fallDistance = 0;
            if (target instanceof ServerPlayer victim) animate(victim, "__clear__", 3);
        }
        if (normalEnd) {
            attacker.serverLevel().playSound(null, attacker.blockPosition(), ModSounds.DOMINION_OFF.get(), SoundSource.PLAYERS, .65f, .95f);
        }
        broadcastEntityState(attacker);
        sync(attacker);
    }

    private static void cancelGripInvolving(ServerPlayer player) {
        GrabState own = ACTIVE_GRABS.remove(player.getUUID());
        player.getPersistentData().remove("HexHakiGripAttackerUntil");
        if (own != null) {
            Entity targetEntity = player.serverLevel().getEntity(own.targetId());
            if (targetEntity instanceof LivingEntity target) clearGripTag(target);
        }
        Iterator<Map.Entry<UUID, GrabState>> iterator = ACTIVE_GRABS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, GrabState> entry = iterator.next();
            if (!entry.getValue().targetId().equals(player.getUUID())) continue;
            ServerPlayer attacker = player.getServer().getPlayerList().getPlayer(entry.getKey());
            if (attacker != null) {
                attacker.getPersistentData().remove("HexHakiGripAttackerUntil");
                HakiData.flag(attacker, "dominionOn", false);
                animate(attacker, "__clear__", 3);
            }
            clearGripTag(player);
            iterator.remove();
        }
        HakiData.flag(player, "dominionOn", false);
    }

    /**
     * WiFi Haki replaces King's Verdict with a sustained Shanks-style Supreme King projection.
     * Press L once to lock one visible target; releasing L ends the channel. The server owns the
     * timer, energy drain, line-of-sight checks, target arrest, damage pulses, and every lightning
     * redraw. The client therefore cannot accelerate damage by spamming action packets.
     */
    private static void sovereignStrike(ServerPlayer player) {
        if (ACTIVE_WIFI_HAKI.containsKey(player.getUUID())) return;

        int armament = HakiData.mastery(player, HakiType.ARMAMENT);
        int observation = HakiData.mastery(player, HakiType.OBSERVATION);
        int conqueror = HakiData.mastery(player, HakiType.CONQUEROR);
        if (!HakiData.unlimitedEnergy(player) && !HakiProgression.wifiHakiUnlocked(player)) {
            player.displayClientMessage(Component.literal("WiFi Haki [Hold L] requires ARM " + HakiUnlocks.WIFI_HAKI_ARMAMENT + " + OBS " + HakiUnlocks.WIFI_HAKI_OBSERVATION + " + HAO " + HakiUnlocks.WIFI_HAKI_CONQUEROR + "."), true);
            return;
        }

        long now = player.level().getGameTime();
        if (!noCooldowns(player) && now < HakiData.longValue(player, "sovereignCooldownUntil")) return;

        double range = wifiHakiRange(armament, observation);
        Vec3 start = wifiHakiHandPoint(player);
        Vec3 end = player.getEyePosition().add(player.getLookAngle().normalize().scale(range));
        float obsScale = Mth.clamp(observation / 1000f, 0f, 1f);
        double aimWidth = .95 + .70 * obsScale;
        LivingEntity target = nearestOnSegment(player, player.getEyePosition(), end, aimWidth);
        if (target == null || !player.hasLineOfSight(target)) {
            player.displayClientMessage(Component.literal("WiFi Haki needs a living target in sight (" + Math.round(range) + " block reach)."), true);
            return;
        }
        if (!HakiData.consume(player, WIFI_HAKI_START_COST)) return;

        boolean wasNoAi = target instanceof Mob mob && mob.isNoAi();
        if (target instanceof Mob mob && !wasNoAi) mob.setNoAi(true);
        int maxTicks = wifiHakiMaxTicks(armament, observation, conqueror);
        long seed = player.getRandom().nextLong();
        int hakiTier = HakiData.flag(player, "acocOn") ? 2 : (HakiData.flag(player, "armamentOn") ? 1 : 0);
        WifiHakiState state = new WifiHakiState(target.getUUID(), now, maxTicks, seed, wasNoAi, hakiTier);
        ACTIVE_WIFI_HAKI.put(player.getUUID(), state);
        HakiData.flag(player, "wifiHakiChanneling", true);

        animate(player, "wifi_haki", 2);
        sendWifiHakiArc(player, target, state, WIFI_HAKI_CONNECT_TICKS);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.WIFI_HAKI_START.get(), SoundSource.PLAYERS, 1.15f, .88f);
        player.displayClientMessage(Component.literal("WiFi Haki  ·  HOLD"), true);
    }

    private static void releaseSovereignStrike(ServerPlayer player) {
        stopWifiHaki(player, true);
    }

    private static double wifiHakiRange(int armament, int observation) {
        // Range progression starts from the actual unlock floor instead of counting mastery earned
        // before WiFi Haki exists. This keeps the unlock impressive at 60 blocks while preserving
        // a large, visible climb to the approved 160-block pinnacle.
        float armProgress = Mth.clamp((armament - HakiProgression.WIFI_HAKI_ARMAMENT_REQUIREMENT)
                / (float)(1000 - HakiProgression.WIFI_HAKI_ARMAMENT_REQUIREMENT), 0f, 1f);
        float obsProgress = Mth.clamp((observation - HakiProgression.WIFI_HAKI_OBSERVATION_REQUIREMENT)
                / (float)(1000 - HakiProgression.WIFI_HAKI_OBSERVATION_REQUIREMENT), 0f, 1f);
        return 60.0 + 40.0 * armProgress + 60.0 * obsProgress;
    }

    private static int wifiHakiMaxTicks(int armament, int observation, int conqueror) {
        // WiFi Haki is already a Supreme-tier unlock. At the unlock floor it can be held for 4.5s;
        // finishing all three paths stretches the same technique to 6s without making the first
        // unlocked version feel crippled. Unlimited-energy test mode can still probe below the gate.
        float armProgress = Mth.clamp((armament - HakiProgression.WIFI_HAKI_ARMAMENT_REQUIREMENT)
                / (float)(1000 - HakiProgression.WIFI_HAKI_ARMAMENT_REQUIREMENT), 0f, 1f);
        float obsProgress = Mth.clamp((observation - HakiProgression.WIFI_HAKI_OBSERVATION_REQUIREMENT)
                / (float)(1000 - HakiProgression.WIFI_HAKI_OBSERVATION_REQUIREMENT), 0f, 1f);
        float conqProgress = Mth.clamp((conqueror - HakiProgression.WIFI_HAKI_CONQUEROR_REQUIREMENT)
                / (float)(1000 - HakiProgression.WIFI_HAKI_CONQUEROR_REQUIREMENT), 0f, 1f);
        float progress = (armProgress + obsProgress + conqProgress) / 3f;
        return 90 + Math.round(30f * progress); // 4.5s -> 6s.
    }

    /** The projection arm is authored straight forward, so this server-side point sits at the end of
     * that visible right arm rather than spawning the bolt from the player's eyes/chest. */
    private static Vec3 wifiHakiHandPoint(ServerPlayer player) {
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-6) right = new Vec3(1, 0, 0);
        else right = right.normalize();
        return player.position().add(0, 1.38, 0).add(forward.scale(.72)).add(right.scale(.31));
    }

    private static Vec3 wifiHakiTargetPoint(LivingEntity target) {
        return target.getBoundingBox().getCenter().add(0, target.getBbHeight() * .08, 0);
    }

    /** Send one living redraw of the connection. New pulse indices deliberately mutate the crown,
     * corkscrew and branch layout so holding L never leaves one frozen lightning noodle on screen. */
    private static void sendWifiHakiArc(ServerPlayer player, LivingEntity target, WifiHakiState state, int travelTicks) {
        Vec3 start = wifiHakiHandPoint(player);
        Vec3 targetPoint = wifiHakiTargetPoint(target);
        int armament = HakiData.mastery(player, HakiType.ARMAMENT);
        int observation = HakiData.mastery(player, HakiType.OBSERVATION);
        int conqueror = HakiData.mastery(player, HakiType.CONQUEROR);
        float power = Mth.clamp(.40f + .25f * (armament / 1000f) + .20f * (observation / 1000f) + .15f * (conqueror / 1000f), 0f, 1f);
        int pulse = state.visualPulse++;
        long pulseSeed = state.seed + pulse * 0x632BE59BD9B4E019L;
        broadcastWifiHaki(player.serverLevel(), player, target, new S2CWifiHaki(
                player.getId(), target.getId(),
                start.x, start.y, start.z,
                targetPoint.x, targetPoint.y, targetPoint.z,
                power, travelTicks, pulse, state.beamCount, pulseSeed));
    }

    /** Send the bolt to players close enough to either endpoint or the corridor between them. This
     * keeps extreme-distance PvP visually synchronized even beyond normal entity tracking range. */
    private static void broadcastWifiHaki(ServerLevel level, ServerPlayer caster, LivingEntity target, S2CWifiHaki packet) {
        Vec3 a = new Vec3(packet.startX(), packet.startY(), packet.startZ());
        Vec3 b = new Vec3(packet.endX(), packet.endY(), packet.endZ());
        Vec3 ab = b.subtract(a);
        double len2 = Math.max(1.0E-6, ab.lengthSqr());
        for (ServerPlayer viewer : level.players()) {
            Vec3 p = viewer.getEyePosition();
            double t = Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
            double d2 = p.distanceToSqr(a.add(ab.scale(t)));
            if (viewer == caster || viewer == target || d2 <= 96.0 * 96.0) HakiNetwork.to(viewer, packet);
        }
    }

    private static void tickWifiHaki(ServerPlayer player) {
        WifiHakiState state = ACTIVE_WIFI_HAKI.get(player.getUUID());
        if (state == null) return;
        ServerLevel level = player.serverLevel();
        Entity found = level.getEntity(state.targetId);
        if (!(found instanceof LivingEntity target) || !target.isAlive() || !player.isAlive()) {
            stopWifiHaki(player, true);
            return;
        }

        long age = level.getGameTime() - state.startTick;
        if (age >= state.maxTicks) {
            stopWifiHaki(player, true);
            return;
        }
        float upkeep = WIFI_HAKI_BASE_ENERGY_PER_TICK
                + WIFI_HAKI_EXTRA_BEAM_ENERGY_PER_TICK * (state.beamCount - 1);
        if (!HakiData.consume(player, upkeep)) {
            stopWifiHaki(player, true);
            return;
        }

        double range = wifiHakiRange(HakiData.mastery(player, HakiType.ARMAMENT), HakiData.mastery(player, HakiType.OBSERVATION));
        boolean visible = player.distanceToSqr(target) <= (range + 5.0) * (range + 5.0) && player.hasLineOfSight(target);
        if (!visible) {
            state.lostSightTicks++;
            if (state.lostSightTicks > WIFI_HAKI_LOS_GRACE_TICKS) stopWifiHaki(player, true);
            return;
        }
        state.lostSightTicks = 0;

        // The victim is continuously arrested while the channel is alive. Mobs have AI suspended;
        // players have their server velocity erased every tick so the repeated zap reads as paralysis.
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        target.fallDistance = 0;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 10, false, false));
        if (target instanceof Mob mob) mob.setNoAi(true);
        if (target instanceof ServerPlayer victim) victim.connection.send(new ClientboundSetEntityMotionPacket(victim));

        if (age > 0 && age % WIFI_HAKI_VFX_INTERVAL_TICKS == 0) sendWifiHakiArc(player, target, state, 2);
        if (age >= WIFI_HAKI_CONNECT_TICKS && (age - WIFI_HAKI_CONNECT_TICKS) % WIFI_HAKI_DAMAGE_INTERVAL_TICKS == 0) {
            wifiHakiDamagePulse(player, target, state);
        }
    }

    private static void wifiHakiDamagePulse(ServerPlayer player, LivingEntity target, WifiHakiState state) {
        int armament = HakiData.mastery(player, HakiType.ARMAMENT);
        int observation = HakiData.mastery(player, HakiType.OBSERVATION);
        int conqueror = HakiData.mastery(player, HakiType.CONQUEROR);

        // Deliberately low per-pulse damage: the power of WiFi Haki is the sustained arrest, huge
        // range and multi-arc channel, not deleting a boss in one cast. Armament tiers increase the
        // COMBINED pulse damage, while the three visual beams remain one authored technique.
        float armProgress = Mth.clamp((armament - HakiProgression.WIFI_HAKI_ARMAMENT_REQUIREMENT)
                / (float)(1000 - HakiProgression.WIFI_HAKI_ARMAMENT_REQUIREMENT), 0f, 1f);
        float obsProgress = Mth.clamp((observation - HakiProgression.WIFI_HAKI_OBSERVATION_REQUIREMENT)
                / (float)(1000 - HakiProgression.WIFI_HAKI_OBSERVATION_REQUIREMENT), 0f, 1f);
        float conqProgress = Mth.clamp((conqueror - HakiProgression.WIFI_HAKI_CONQUEROR_REQUIREMENT)
                / (float)(1000 - HakiProgression.WIFI_HAKI_CONQUEROR_REQUIREMENT), 0f, 1f);
        float masteryProgress = (armProgress + obsProgress + conqProgress) / 3f;
        // User-approved late-game damage is preserved: 2.25 base pulse at 1000/1000/1000. The
        // Supreme-tier unlock begins at 2.00, only ~11% lower, so earning the move still feels strong.
        float basePulse = 2.00f + .25f * masteryProgress;
        float tierMultiplier = switch (state.hakiTier) {
            case 2 -> 2.10f; // Three-beam Advanced Armament channel.
            case 1 -> 1.55f; // Two-beam Armament channel.
            default -> 1.00f;
        };
        float damage = basePulse * tierMultiplier;
        if (isBoss(target)) damage *= .68f;

        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), damage);
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        state.damagePulses++;
        player.serverLevel().playSound(null, target.blockPosition(), ModSounds.WIFI_HAKI_PULSE.get(), SoundSource.PLAYERS, .82f + .08f * state.hakiTier, .93f + player.getRandom().nextFloat() * .06f);
    }

    private static void stopWifiHaki(ServerPlayer player, boolean applyCooldown) {
        WifiHakiState state = ACTIVE_WIFI_HAKI.remove(player.getUUID());
        if (state == null) {
            HakiData.flag(player, "wifiHakiChanneling", false);
            return;
        }
        HakiData.flag(player, "wifiHakiChanneling", false);
        Entity found = player.serverLevel().getEntity(state.targetId);
        if (found instanceof Mob mob && !state.targetWasNoAi) mob.setNoAi(false);
        animate(player, "__clear__", 3);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.WIFI_HAKI_END.get(), SoundSource.PLAYERS, .72f, .92f);

        if (applyCooldown && !noCooldowns(player)) {
            long usedTicks = Math.max(0L, Math.min((long)state.maxTicks, player.level().getGameTime() - state.startTick));
            double usage = state.maxTicks <= 0 ? 0.0 : usedTicks / (double)state.maxTicks;
            long cooldownTicks = WIFI_HAKI_MIN_COOLDOWN_TICKS
                    + Math.round((WIFI_HAKI_MAX_COOLDOWN_TICKS - WIFI_HAKI_MIN_COOLDOWN_TICKS) * usage);
            HakiData.longValue(player, "sovereignCooldownUntil", player.level().getGameTime() + cooldownTicks);
        }
        if (state.damagePulses > 0) {
            // Technique-use progression: a real maintained connection trains all three skills that
            // make WiFi Haki possible. Longer successful channels earn more than a tap, but the
            // per-source XP cooldown still prevents packet/input spam from farming mastery.
            int practice = Mth.clamp(1 + state.damagePulses / 2, 1, 7);
            HakiProgression.legend(player, 1, "wifi_haki", 300);
            HakiProgression.award(player, HakiType.ARMAMENT, 2 + practice, "wifi_haki", 100);
            HakiProgression.award(player, HakiType.OBSERVATION, 2 + practice / 2, "wifi_haki", 100);
            HakiProgression.award(player, HakiType.CONQUEROR, 3 + practice, "wifi_haki", 100);
        }
    }

    /**
     * Galaxy Impact input is intentionally split in two:
     * - release M before the ground charge reaches 100% -> forward cylindrical Haki pressure wave,
     *   increasingly empowered by the stored charge
     * - only reaching a true 100% ground charge with the full convergence requirements commits the
     *   player into the RAGE aerial ultimate
     *
     * This state machine is self-contained so the move no longer inherits timing/poses from the
     * older Convergence implementation.
     */
    private static void beginConvergence(ServerPlayer player) {
        int armament = HakiData.mastery(player, HakiType.ARMAMENT);
        if (armament < HakiUnlocks.GALAXY_WAVE_ARMAMENT) {
            locked(player, "Galaxy Impact: Haki Wave", HakiUnlocks.GALAXY_WAVE_ARMAMENT, armament);
            return;
        }
        if (HakiData.flag(player, "convergenceCharging") || HakiData.energy(player) < 8f) return;

        HakiData.flag(player, "convergenceCharging", true);
        HakiData.flag(player, "convergenceHeld", true);
        HakiData.flag(player, "convergenceCommitted", false);
        HakiData.flag(player, "convergenceDivePose", false);
        HakiData.flag(player, "galaxyPunching", false);
        HakiData.integer(player, "convergenceHoldTicks", 0);
        HakiData.integer(player, "convergenceCharge", 0);
        HakiData.integer(player, "galaxyAge", 0);
        HakiData.longValue(player, "galaxyLaunchTick", -1L);
        HakiData.longValue(player, "hakiLeapLaunchTick", 0L);
        HakiData.longValue(player, "futureSightBurstUntil", 0L);
        HakiData.longValue(player, "galaxySafeFallUntil", player.level().getGameTime() + 140);

        // Grounded pre-charge shared by both branches. The player does NOT launch just because the
        // button has been held for a few ticks anymore: the full RAGE sequence is hard-gated behind
        // a real 100% charge. Releasing at 0-99% feeds that stored charge into the cylindrical tap wave.
        // Keep this state visually quiet; the held ultimate begins its RAGE charge sound only at 100%.
        animate(player, "galaxy_ready", 2);
    }

    private static void releaseConvergence(ServerPlayer player) {
        if (!HakiData.flag(player, "convergenceCharging")) return;
        HakiData.flag(player, "convergenceHeld", false);

        if (!HakiData.flag(player, "convergenceCommitted")) {
            int charge = Math.max(0, Math.min(CONVERGENCE_MAX, HakiData.integer(player, "convergenceCharge")));
            clearGalaxyState(player);
            galaxyHakiWave(player, charge);
            return;
        }

        // Once the hold has committed, releasing M never fires the ultimate. The donor RAGE move
        // has a distinct READY phase and only the vanilla Attack key can begin the downward punch.
    }

    private static void commitGalaxyImpact(ServerPlayer player) {
        if (!HakiData.flag(player, "convergenceCharging") || HakiData.flag(player, "convergenceCommitted")) return;
        if (!cooldownReady(player, "galaxyFullCooldownUntil", "Full Galaxy Impact")) return;

        HakiData.flag(player, "convergenceCommitted", true);
        HakiData.flag(player, "convergenceHeld", false);
        HakiData.flag(player, "galaxyPunching", false);
        HakiData.flag(player, "galaxyOriginalNoGravity", player.isNoGravity());
        HakiData.integer(player, "galaxyAge", 0);
        HakiData.integer(player, "convergenceCharge", 0);
        // Snapshot the coating state at commitment so the full cinematic stays internally consistent
        // even if toggles change while airborne. Normal Armament restores the original sky-lightning
        // storm; J/Advanced Haki counts as coated and additionally unlocks the ground-crawling field.
        boolean galaxyAdvanced = HakiData.flag(player, "acocOn");
        HakiData.flag(player, "galaxyAdvanced", galaxyAdvanced);
        HakiData.flag(player, "galaxyHakiCoated", HakiData.flag(player, "armamentOn") || galaxyAdvanced);
        HakiData.longValue(player, "galaxySafeFallUntil", player.level().getGameTime() + 360);

        player.setNoGravity(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.setSprinting(false);
        player.hurtMarked = true;
        player.fallDistance = 0;

        // Donor RAGE sequence: the Haki is on the fist from frame one, never an overhead galaxy.
        animate(player, "galaxy_leap", 2);
        visual(player, S2CHakiVisual.Visual.GALAXY_FIST_START, 1f, galaxyFistVariant(player));
        sound(player, ModSounds.GALAXY_RAGE_CHARGE.get(), 3.0f, 1.0f);
        sound(player, ModSounds.GALAXY_RAGE_CRACK.get(), 1.6f, .78f);
    }

    /** Armament-500 tap technique: a cinematic straight punch that releases a true cylinder of emitted pressure.
     *  Releasing the Galaxy key before 100% turns the stored ground charge into extra range, width,
     *  damage, knockback and Photon density rather than accidentally committing the aerial ultimate. */
    private static void galaxyHakiWave(ServerPlayer player, int chargePercent) {
        int armament = HakiData.mastery(player, HakiType.ARMAMENT);
        if (armament < HakiUnlocks.GALAXY_WAVE_ARMAMENT) {
            locked(player, "Galaxy Impact: Haki Wave", HakiUnlocks.GALAXY_WAVE_ARMAMENT, armament);
            animate(player, "__clear__", 2);
            return;
        }

        long now = player.level().getGameTime();
        if (!noCooldowns(player) && now < HakiData.longValue(player, "galaxyTapLockUntil")) return;

        float charge = Math.max(0f, Math.min(.99f, chargePercent / (float) CONVERGENCE_MAX));
        float energyCost = 8f + 12f * charge;
        if (!HakiData.consume(player, energyCost)) {
            animate(player, "__clear__", 2);
            return;
        }

        // Tap tiers are based on the coating that is actually active when M is released:
        // 0 = no coating (weak physical/air punch, no Haki lightning)
        // 1 = normal Armament (the existing tap exactly as-authored)
        // 2 = Advanced/J coating (existing Haki tap + red energy layer, longest cooldown)
        final int hakiTier = HakiData.flag(player, "acocOn") ? 2 : (HakiData.flag(player, "armamentOn") ? 1 : 0);

        float mastery = Math.max(.5f, Math.min(1f, armament / 1000f));
        float trained = Math.max(0f, Math.min(1f, (armament - HakiUnlocks.GALAXY_WAVE_ARMAMENT) / (float)(1000 - HakiUnlocks.GALAXY_WAVE_ARMAMENT)));
        double length = 15.0 + 5.0 * trained + 8.0 * charge;
        double radius = 2.9 + 1.1 * trained + 1.5 * charge;

        long tapCooldown = switch (hakiTier) {
            case 0 -> GALAXY_TAP_NO_HAKI_COOLDOWN_TICKS;
            case 2 -> GALAXY_TAP_ADVANCED_COOLDOWN_TICKS;
            default -> scaledCooldown(armament, HakiUnlocks.GALAXY_WAVE_ARMAMENT, 100, 45); // preserve current normal-Haki balance.
        };

        // The V2 tap punch has a fast 8-tick contact frame; charge only strengthens what comes out of it.
        HakiData.longValue(player, "galaxyTapLockUntil", now + tapCooldown);
        animate(player, "galaxy_tap_punch", 2);
        player.setDeltaMovement(player.getDeltaMovement().multiply(.42, 1.0, .42));
        player.hurtMarked = true;

        UUID owner = player.getUUID();
        ServerLevel level = player.serverLevel();
        // The impact mix used to be played inside releaseGalaxyHakiWave, i.e. a full 8 ticks
        // (0.4s) after the key press, which reads as audio lag rather than as a wind-up. Fire it
        // two ticks ahead of contact: sound reaching the ear just before the wave appears is what
        // makes a heavy hit feel connected.
        ServerTimeline.later(level, Math.max(1, GALAXY_TAP_IMPACT_TICK - 2), () -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
            if (p == null || !p.isAlive()) return;
            sound(p, ModSounds.GALAXY_WAVE_PUNCH.get(), 3.25f + 1.10f * charge,
                    .88f + .03f * trained - .055f * charge);
        });
        ServerTimeline.later(level, GALAXY_TAP_IMPACT_TICK, () -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
            if (p == null || !p.isAlive()) return;
            releaseGalaxyHakiWave(p, mastery, trained, charge, length, radius, armament, hakiTier);
        });
    }

    private static void releaseGalaxyHakiWave(ServerPlayer player, float mastery, float trained, float charge,
                                               double length, double radius, int armament, int hakiTier) {
        // Aim is sampled on the actual punch-contact frame so the cylinder leaves the current fist/crosshair,
        // not the place the player happened to be standing during the wind-up.
        Vec3 look = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(look.scale(.65));
        Vec3 end = start.add(look.scale(length));
        HakiNetwork.tracking(player, new S2CGalaxyWave(
                player.getId(), mastery, charge, (float) radius, HakiData.joyBoy(player), hakiTier,
                start.x, start.y, start.z, end.x, end.y, end.z, player.getRandom().nextLong()));

        // The impact mix is played two ticks earlier, alongside the punch frame, so it lands with
        // the strike instead of trailing it.

        Vec3 segment = end.subtract(start);
        double lenSq = segment.lengthSqr();
        AABB hitArea = new AABB(start, end).inflate(radius);
        ServerLevel level = player.serverLevel();

        // No vanilla SONIC_BOOM/Warden scream rings here. Photon owns the cylindrical pressure geometry.
        // The neutral air fallback is direction-locked too: every mote moves along the exact punch vector
        // instead of using random XYZ velocity spread that can visually shoot sideways.
        Vec3 fallbackRef = Math.abs(look.y) < .94D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 fallbackRight = look.cross(fallbackRef).normalize();
        Vec3 fallbackUp = fallbackRight.cross(look).normalize();
        int waveSteps = Math.max(7, (int)Math.ceil(length / 1.8));
        int laneParticles = 3 + (int)Math.ceil(charge * 3.0f);
        for (int i = 1; i <= waveSteps; i++) {
            Vec3 center = start.add(look.scale(length * i / waveSteps));
            for (int j = 0; j < laneParticles; j++) {
                double angle = (Math.PI * 2.0D * j / laneParticles) + i * .41D;
                double laneRadius = .10D + radius * .06D;
                Vec3 radial = fallbackRight.scale(Math.cos(angle) * laneRadius)
                        .add(fallbackUp.scale(Math.sin(angle) * laneRadius));
                Vec3 point = center.add(radial);
                Vec3 velocity = look.scale(.24D + .16D * charge).add(radial.scale(.035D));
                level.sendParticles(ParticleTypes.POOF, point.x, point.y, point.z,
                        0, velocity.x, velocity.y, velocity.z, 1.0D);
            }
        }

        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, hitArea, e -> e != player && e.isAlive())) {
            Vec3 center = living.getBoundingBox().getCenter();
            double t = lenSq < 1.0E-6 ? 0 : Math.max(0, Math.min(1, center.subtract(start).dot(segment) / lenSq));
            Vec3 closest = start.add(segment.scale(t));
            Vec3 radial = center.subtract(closest);
            double distanceToAxis = radial.length();
            if (distanceToAxis > radius) continue;

            float edge = (float)Math.max(.30, 1.0 - distanceToAxis / radius);
            float damage;
            if (hakiTier == 0) {
                // Deliberately much weaker: this is just the raw punch/air pressure with Haki coating off.
                damage = (2.0f + armament / 500f) * (1.0f + .25f * charge) * (.68f + .32f * edge);
            } else {
                damage = (8.0f + armament / 100f) * (1.0f + .80f * charge) * (.62f + .38f * edge);
                // Ryuo/Internal Destruction now have a real emitted-force payoff. Advanced Haki
                // adds one final modest multiplier so its 7s tap cooldown is not purely cosmetic.
                damage *= armamentEmissionMultiplier(armament);
                if (hakiTier == 2) damage *= 1.12f;
            }
            living.hurt(player.damageSources().playerAttack(player), damage);

            Vec3 sideways = radial.lengthSqr() < .001 ? Vec3.ZERO : radial.normalize();
            double forwardForce = 2.15 + 1.55 * edge + .55 * trained + 1.20 * charge;
            double outwardForce = .45 + .70 * edge + .25 * trained + .60 * charge;
            Vec3 push = look.scale(forwardForce).add(sideways.scale(outwardForce)).add(0, .16 + .28 * edge, 0);
            living.setDeltaMovement(living.getDeltaMovement().scale(.18).add(push));
            living.hurtMarked = true;
        }

        HakiProgression.award(player, HakiType.ARMAMENT, 3, "galaxy_tap", 80);
    }

    private static void fireGalaxyImpact(ServerPlayer player) {
        if (!HakiData.flag(player, "convergenceCharging")
                || !HakiData.flag(player, "convergenceCommitted")
                || HakiData.flag(player, "galaxyPunching")) return;

        if (HakiData.integer(player, "convergenceCharge") < CONVERGENCE_MAX) return;
        if (!HakiData.consume(player, GALAXY_FULL_ENERGY_COST)) {
            clearGalaxyState(player);
            animate(player, "__clear__", 3);
            return;
        }

        int armament = HakiData.mastery(player, HakiType.ARMAMENT);
        int observation = HakiData.mastery(player, HakiType.OBSERVATION);
        int conqueror = HakiData.mastery(player, HakiType.CONQUEROR);
        // Final-release rule: once the full move is actually fired it always carries a hard
        // one-minute cooldown. Mastery can improve the Advanced-Haki blast itself, never bypass it.
        HakiData.longValue(player, "galaxyFullCooldownUntil", player.level().getGameTime() + GALAXY_FULL_COOLDOWN_TICKS);
        // The endgame augmentation belongs specifically to J / Advanced Haki (ACoC), not merely
        // ordinary Armament coating. The state was snapshotted when the full sequence committed.
        final boolean advancedHaki = HakiData.flag(player, "galaxyAdvanced");
        final boolean hakiCoated = HakiData.flag(player, "galaxyHakiCoated");
        ServerLevel level = player.serverLevel();

        // Final-alpha targeting: the full Galaxy Impact follows the player's exact crosshair in 3D.
        // Up, down, sideways or distant ground are all valid, capped at 120 blocks.
        Vec3 aim = player.getLookAngle().normalize();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 flatForward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-flatForward.z, 0.0, flatForward.x);

        // Pressure still leaves the extended right fist; only the target selection changed.
        Vec3 start = player.getEyePosition()
                .add(right.scale(.30))
                .add(aim.scale(.52))
                .add(0.0, -.34, 0.0);
        Vec3 end = start.add(aim.scale(GALAXY_MAX_AIM_RANGE));
        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 impact = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();

        LivingEntity aimed = nearestOnSegment(player, start, impact, 1.5);
        if (aimed != null && start.distanceToSqr(aimed.getBoundingBox().getCenter()) < start.distanceToSqr(impact)) {
            impact = aimed.getBoundingBox().getCenter();
        }

        final Vec3 strikeStart = start;
        final Vec3 target = impact;
        final UUID owner = player.getUUID();

        HakiData.flag(player, "galaxyPunching", true);
        HakiData.integer(player, "convergenceCharge", 0);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.setSprinting(false);
        player.hurtMarked = true;
        player.fallDistance = 0;

        visual(player, S2CHakiVisual.Visual.GALAXY_FIST_STOP, 1f, 0);
        animate(player, "galaxy_release", 1);
        HakiNetwork.to(player, new S2CCinematic("galaxy_impact"));

        // At punch contact: render the downward Haki/air path and schedule the compressed center
        // + dome in one world-space Photon composition.
        ServerTimeline.later(level, GALAXY_PUNCH_IMPACT_TICK, () -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
            if (p == null || !p.isAlive()) return;
            HakiNetwork.tracking(p, new S2CGalaxyImpact(p.getId(), 1f, HakiData.joyBoy(p), hakiCoated, advancedHaki,
                    strikeStart.x, strikeStart.y, strikeStart.z,
                    target.x, target.y, target.z, p.getRandom().nextLong()));
            sound(p, ModSounds.GALAXY_RAGE_PUNCH.get(), 4.0f, 1.0f);
            sound(p, ModSounds.GALAXY_RAGE_CRACK.get(), 2.0f, 1.02f);
        });

        // One tick after contact the center is visibly over-compressed; RAGE punctuates that frame
        // with a lower lightning crack before the pressure dome actually detonates.
        ServerTimeline.later(level, GALAXY_PUNCH_IMPACT_TICK + 1, () -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
            if (p != null && p.isAlive()) delayedWorldSound(level, BlockPos.containing(target), ModSounds.GALAXY_RAGE_CRACK.get(), 2.0f, .56f);
        });

        // Donor RAGE detonation happens five ticks after the punch impact frame.
        ServerTimeline.later(level, GALAXY_DETONATION_TICK, () ->
                applyGalaxyImpact(level, owner, strikeStart, target, armament, observation, conqueror, hakiCoated, advancedHaki));
    }

    private static void applyGalaxyImpact(ServerLevel level, UUID ownerId, Vec3 start, Vec3 impact, int armament, int observation, int conqueror, boolean hakiCoated, boolean advancedHaki) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
        if (player == null || !player.isAlive()) return;

        delayedWorldSound(level, BlockPos.containing(impact), ModSounds.GALAXY_RAGE_IMPACT.get(), 4.8f, .86f);
        delayedWorldSound(level, BlockPos.containing(impact), ModSounds.GALAXY_RAGE_CRACK.get(), 5.0f, .70f);

        // Same server fallback as the donor: the Photon dome is client-side, but the blast still
        // throws pressure, dust, smoke and electrical sparks if Photon is visually obstructed.
        double progression = galaxyAdvancedProgress(player, armament, observation, conqueror);
        if (hakiCoated) {
            // Haki-coated Galaxy Impact owns the nuclear center, sparks and delayed mushroom cloud.
            // The uncoated version is intentionally a pure air/pressure smash and must never fake
            // this Haki detonation in its center.
            double spread = advancedHaki ? 2.00 : 1.15;
            int countBoost = advancedHaki ? 2 : 1;
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y + .9, impact.z,
                    12 * countBoost, 4.2 * spread, 2.2 * spread, 4.2 * spread, 0.0);
            level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 1.6, impact.z,
                    140 * countBoost, 12.0 * spread, 5.5 * spread, 12.0 * spread, .38);
            level.sendParticles(ParticleTypes.CLOUD, impact.x, impact.y + 1.0, impact.z,
                    340 * countBoost, 14.0 * spread, 3.0 * spread, 14.0 * spread, .62);
            level.sendParticles(ParticleTypes.POOF, impact.x, impact.y + 1.3, impact.z,
                    240 * countBoost, 12.5 * spread, 4.2 * spread, 12.5 * spread, .72);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, impact.x, impact.y + 2.5, impact.z,
                    300 * countBoost, 17.0 * spread, 10.0 * spread, 17.0 * spread, .72);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, impact.x, impact.y + 3.0, impact.z,
                    150 * countBoost, 10.0 * spread, 6.0 * spread, 10.0 * spread, .22);
            scheduleGalaxyMushroomCloud(level, impact, advancedHaki);
        } else {
            // Raw Galaxy Impact: violent wind, dust and outward pressure only. No explosion-emitter,
            // no electrical sparks and no mushroom cloud/nuclear center.
            level.sendParticles(ParticleTypes.CLOUD, impact.x, impact.y + .55, impact.z,
                    180, 9.5, 1.8, 9.5, .52);
            level.sendParticles(ParticleTypes.POOF, impact.x, impact.y + .75, impact.z,
                    220, 11.0, 2.4, 11.0, .68);
        }

        // Same second set of pressure markers as the donor: the first trace rides the punch packet,
        // these reinforce the route as the detonation tears back through the air.
        for (int pulse = 1; pulse <= 10; pulse++) {
            Vec3 wave = start.lerp(impact, pulse / 10.0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, wave.x, wave.y, wave.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // No block destruction. The full-charge nuke now owns a wider damage field and heavier knockback.
        double impactRadius = advancedHaki
                ? galaxyAdvancedImpactRadius(player, armament, observation, conqueror)
                : GALAXY_IMPACT_RADIUS;
        AABB box = new AABB(impact, impact).inflate(impactRadius);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            double distance = living.position().distanceTo(impact);
            if (distance > impactRadius) continue;

            double normalized = Math.max(0.0, 1.0 - distance / impactRadius);
            float damage = (float)(GALAXY_MIN_DAMAGE
                    + (GALAXY_MAX_DAMAGE - GALAXY_MIN_DAMAGE) * normalized * normalized);
            if (advancedHaki) damage *= (float)(1.10 + .25 * progression);
            living.hurt(player.damageSources().playerAttack(player), damage);

            Vec3 away = living.position().subtract(impact);
            if (away.lengthSqr() < .001) away = new Vec3(0.0, 1.0, 0.0);
            else away = away.normalize();
            double force = 1.65 + 3.75 * (1.0 - distance / impactRadius);
            living.push(away.x * force, 1.0 + force * .28, away.z * force);
            living.hurtMarked = true;
        }

        if (hakiCoated) {
            // Restore the original normal-Haki Galaxy storm: discrete black/red bolts rain down
            // around the crater for ~4 seconds. Advanced/J keeps this storm and adds the separate
            // horizontal ground field below, rather than replacing the classic sky barrage.
            for (int pulse = 1; pulse <= GALAXY_SKY_LIGHTNING_PULSES; pulse++) {
                final int pulseIndex = pulse;
                ServerTimeline.later(level, pulse * GALAXY_SKY_LIGHTNING_INTERVAL, () ->
                        applyGalaxySkyLightningPulse(level, ownerId, impact, pulseIndex));
            }
        }

        if (advancedHaki) {
            // J/Advanced Haki leaves a five-second ground storm anchored to the crater.
            // The visible black/red branches crawl horizontally from the blast while localized
            // server damage samples the same widening field so survival balance is preserved.
            double lightningRadius = Math.min(impactRadius * .78, 58.0);
            for (int pulse = 0; pulse < GALAXY_HAKI_LIGHTNING_PULSES; pulse++) {
                final int pulseIndex = pulse;
                final double pulseRadius = lightningRadius;
                ServerTimeline.later(level, pulse * GALAXY_HAKI_LIGHTNING_INTERVAL, () ->
                        applyGalaxyHakiLightningPulse(level, ownerId, impact, pulseRadius, pulseIndex));
            }
        }

        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0, false, false, true));
        clearGalaxyState(player);

        HakiProgression.award(player, HakiType.ARMAMENT, 6, "galaxy_impact", 120);
        HakiProgression.award(player, HakiType.OBSERVATION, 6, "galaxy_impact", 120);
        HakiProgression.award(player, HakiType.CONQUEROR, 6, "galaxy_impact", 120);
        HakiProgression.legend(player, 2, "galaxy_impact", 600);
    }

    private static void scheduleGalaxyMushroomCloud(ServerLevel level, Vec3 impact, boolean advancedHaki) {
        final int boost = advancedHaki ? 2 : 1;
        final double scale = advancedHaki ? 1.55 : 1.0;

        // Rising stem: each stage appears after the previous one and climbs vertically.
        for (int stage = 0; stage < 4; stage++) {
            final int index = stage;
            ServerTimeline.later(level, 8 + stage * 7, () -> {
                double y = impact.y + 3.0 + index * 3.1;
                double width = (1.8 + index * .55) * scale;
                level.sendParticles(ParticleTypes.LARGE_SMOKE, impact.x, y, impact.z,
                        (36 + index * 12) * boost, width, 1.15, width, .055 + index * .015);
                level.sendParticles(ParticleTypes.CLOUD, impact.x, y - .35, impact.z,
                        (48 + index * 14) * boost, width * .85, 1.0, width * .85, .09);
            });
        }

        // Mushroom cap: broad layered smoke rolls outward well after the initial white-hot blast.
        ServerTimeline.later(level, 34, () -> {
            double capY = impact.y + (advancedHaki ? 16.0 : 14.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, impact.x, capY, impact.z,
                    150 * boost, 8.0 * scale, 2.0, 8.0 * scale, .10);
            level.sendParticles(ParticleTypes.CLOUD, impact.x, capY + .8, impact.z,
                    180 * boost, 10.5 * scale, 2.2, 10.5 * scale, .16);
            level.sendParticles(ParticleTypes.POOF, impact.x, capY - .4, impact.z,
                    90 * boost, 7.0 * scale, 1.4, 7.0 * scale, .10);
        });
        ServerTimeline.later(level, 48, () -> {
            double capY = impact.y + (advancedHaki ? 17.5 : 15.5);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, impact.x, capY, impact.z,
                    120 * boost, 11.0 * scale, 2.5, 11.0 * scale, .08);
            level.sendParticles(ParticleTypes.CLOUD, impact.x, capY + .5, impact.z,
                    150 * boost, 13.5 * scale, 2.8, 13.5 * scale, .12);
        });
    }

    /** Original normal-Armament Galaxy storm restored from the pre-Advanced implementation.
     * Each packet is anchored to an exact terrain point and renders a jagged black/red bolt falling
     * from the sky. Damage is localized around the actual landing point. */
    private static void applyGalaxySkyLightningPulse(ServerLevel level, UUID ownerId, Vec3 impact, int pulseIndex) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
        if (player == null || !player.isAlive()) return;

        java.util.Random random = new java.util.Random(ownerId.getMostSignificantBits()
                ^ ownerId.getLeastSignificantBits()
                ^ Double.doubleToLongBits(impact.x * 31.0 + impact.z * 17.0)
                ^ (0x9E3779B97F4A7C15L * pulseIndex));

        for (int strike = 0; strike < GALAXY_SKY_STRIKES_PER_PULSE; strike++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = Math.sqrt(random.nextDouble()) * GALAXY_SKY_LIGHTNING_RADIUS;
            double x = impact.x + Math.cos(angle) * radius;
            double z = impact.z + Math.sin(angle) * radius;

            Vec3 high = new Vec3(x, impact.y + 16.0, z);
            Vec3 low = new Vec3(x, impact.y - 16.0, z);
            HitResult hit = level.clip(new ClipContext(high, low, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            double y = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y + 0.06 : impact.y + 0.06;
            Vec3 strikePoint = new Vec3(x, y, z);
            long seed = random.nextLong();

            // radius <= 0 is deliberately reserved for the classic sky-strike visual.
            HakiNetwork.tracking(player, new S2CGalaxyHakiStrike(player.getId(), x, y, z, 0.0f, seed));

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 2.0, z,
                    48, 1.8, 4.8, 1.8, .22);
            level.sendParticles(ParticleTypes.CLOUD, x, y + .12, z,
                    18, 1.1, .12, 1.1, .10);

            AABB strikeBox = new AABB(strikePoint, strikePoint).inflate(
                    GALAXY_SKY_STRIKE_DAMAGE_RADIUS, 6.0, GALAXY_SKY_STRIKE_DAMAGE_RADIUS);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, strikeBox,
                    e -> e != player && e.isAlive())) {
                double dx = living.getX() - x;
                double dz = living.getZ() - z;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                if (horizontal > GALAXY_SKY_STRIKE_DAMAGE_RADIUS || Math.abs(living.getY() - y) > 6.0) continue;

                double normalized = Math.max(0.0, 1.0 - horizontal / GALAXY_SKY_STRIKE_DAMAGE_RADIUS);
                float damage = (float)(GALAXY_HAKI_LIGHTNING_MIN_DAMAGE
                        + (GALAXY_HAKI_LIGHTNING_MAX_DAMAGE - GALAXY_HAKI_LIGHTNING_MIN_DAMAGE) * normalized);
                living.hurt(player.damageSources().playerAttack(player), damage);
                living.hurtMarked = true;
            }
        }

        delayedWorldSound(level, BlockPos.containing(impact), ModSounds.GALAXY_RAGE_CRACK.get(),
                2.8f, .68f + random.nextFloat() * .20f);
    }

    private static void applyGalaxyHakiLightningPulse(ServerLevel level, UUID ownerId, Vec3 impact, double fieldRadius, int pulseIndex) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
        if (player == null || !player.isAlive()) return;

        java.util.Random random = new java.util.Random(ownerId.getMostSignificantBits()
                ^ ownerId.getLeastSignificantBits()
                ^ Double.doubleToLongBits(impact.x * 31.0 + impact.z * 17.0)
                ^ (0x9E3779B97F4A7C15L * (pulseIndex + 1L)));

        // One authoritative packet per pulse draws a large black/red lightning family crawling
        // horizontally away from the crater. Eleven overlapping pulses cover ~5 seconds.
        double visualRadius = fieldRadius * (.30 + .70 * (pulseIndex / (double)Math.max(1, GALAXY_HAKI_LIGHTNING_PULSES - 1)));
        HakiNetwork.tracking(player, new S2CGalaxyHakiStrike(player.getId(), impact.x, impact.y + .08, impact.z,
                (float)visualRadius, random.nextLong()));

        // Server-side damage follows several points on the same widening ground field. The visual
        // remains continuous while damage stays localized enough to be fair in survival.
        for (int strike = 0; strike < GALAXY_HAKI_STRIKES_PER_PULSE; strike++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = Math.sqrt(random.nextDouble()) * Math.max(6.0, visualRadius);
            double x = impact.x + Math.cos(angle) * radius;
            double z = impact.z + Math.sin(angle) * radius;

            Vec3 high = new Vec3(x, impact.y + 16.0, z);
            Vec3 low = new Vec3(x, impact.y - 16.0, z);
            HitResult hit = level.clip(new ClipContext(high, low, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            double y = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y + 0.06 : impact.y + 0.06;
            Vec3 strikePoint = new Vec3(x, y, z);

            // Neutral fallback deliberately hugs the floor rather than looking like sky lightning.
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + .10, z,
                    30, 2.2, .18, 2.2, .18);
            level.sendParticles(ParticleTypes.CLOUD, x, y + .08, z,
                    12, 1.4, .08, 1.4, .08);

            AABB strikeBox = new AABB(strikePoint, strikePoint).inflate(
                    GALAXY_HAKI_STRIKE_DAMAGE_RADIUS, 2.2, GALAXY_HAKI_STRIKE_DAMAGE_RADIUS);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, strikeBox,
                    e -> e != player && e.isAlive())) {
                double dx = living.getX() - x;
                double dz = living.getZ() - z;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                if (horizontal > GALAXY_HAKI_STRIKE_DAMAGE_RADIUS || Math.abs(living.getY() - y) > 2.2) continue;

                double normalized = Math.max(0.0, 1.0 - horizontal / GALAXY_HAKI_STRIKE_DAMAGE_RADIUS);
                float damage = (float)(GALAXY_HAKI_LIGHTNING_MIN_DAMAGE
                        + (GALAXY_HAKI_LIGHTNING_MAX_DAMAGE - GALAXY_HAKI_LIGHTNING_MIN_DAMAGE) * normalized);
                living.hurt(player.damageSources().playerAttack(player), damage);
                living.hurtMarked = true;
            }
        }

        delayedWorldSound(level, BlockPos.containing(impact), ModSounds.GALAXY_RAGE_CRACK.get(),
                2.6f, .66f + random.nextFloat() * .22f);
    }


    /** Full Galaxy Impact arrives before endgame and keeps growing afterward. Each path contributes
     * independently from its own unlock floor so the move is useful at 650-ish mastery without
     * stealing the payoff of reaching 1000 and Joy Boy. */
    private static double galaxyAdvancedProgress(ServerPlayer player, int armament, int observation, int conqueror) {
        double armProgress = Mth.clamp((armament - HakiUnlocks.GALAXY_FULL_ARMAMENT)
                / (double)(1000 - HakiUnlocks.GALAXY_FULL_ARMAMENT), 0.0, 1.0);
        double obsProgress = Mth.clamp((observation - HakiUnlocks.GALAXY_FULL_OBSERVATION)
                / (double)(1000 - HakiUnlocks.GALAXY_FULL_OBSERVATION), 0.0, 1.0);
        double conqProgress = Mth.clamp((conqueror - HakiUnlocks.GALAXY_FULL_CONQUEROR)
                / (double)(1000 - HakiUnlocks.GALAXY_FULL_CONQUEROR), 0.0, 1.0);
        double masteryProgress = (armProgress + obsProgress + conqProgress) / 3.0;
        double legendProgress = Math.max(0.0, Math.min(1.0, (HakiData.legend(player) - HakiProgression.CONVERGENCE_LEGEND_REQUIREMENT)
                / (double)(HakiProgression.JOY_BOY_LEGEND_REQUIREMENT - HakiProgression.CONVERGENCE_LEGEND_REQUIREMENT)));
        return masteryProgress * .72 + legendProgress * .28;
    }

    private static double galaxyAdvancedImpactRadius(ServerPlayer player, int armament, int observation, int conqueror) {
        if (HakiData.joyBoy(player)) return GALAXY_ADVANCED_RADIUS_JOYBOY;
        double progress = galaxyAdvancedProgress(player, armament, observation, conqueror);
        return GALAXY_ADVANCED_RADIUS_AT_UNLOCK + (GALAXY_ADVANCED_RADIUS_MAX - GALAXY_ADVANCED_RADIUS_AT_UNLOCK) * progress;
    }

    /** bit 0 = Joy Boy, bit 1 = J/Advanced Haki, bit 2 = any Haki coating. */
    private static int galaxyFistVariant(ServerPlayer player) {
        return (HakiData.joyBoy(player) ? 1 : 0)
                | (HakiData.flag(player, "galaxyAdvanced") ? 2 : 0)
                | (HakiData.flag(player, "galaxyHakiCoated") ? 4 : 0);
    }

    private static void clearGalaxyState(ServerPlayer player) {
        boolean hadFullMove = HakiData.flag(player, "convergenceCommitted") || HakiData.flag(player, "galaxyPunching");
        if (hadFullMove) visual(player, S2CHakiVisual.Visual.GALAXY_FIST_STOP, 1f, 0);

        if (hadFullMove) {
            boolean originalNoGravity = HakiData.flag(player, "galaxyOriginalNoGravity");
            player.setNoGravity(originalNoGravity);
            player.fallDistance = 0;
        }

        HakiData.flag(player, "convergenceCharging", false);
        HakiData.flag(player, "convergenceHeld", false);
        HakiData.flag(player, "convergenceCommitted", false);
        HakiData.flag(player, "convergenceDivePose", false);
        HakiData.flag(player, "galaxyPunching", false);
        HakiData.flag(player, "galaxyOriginalNoGravity", false);
        HakiData.flag(player, "galaxyAdvanced", false);
        HakiData.flag(player, "galaxyHakiCoated", false);
        HakiData.integer(player, "convergenceHoldTicks", 0);
        HakiData.integer(player, "convergenceCharge", 0);
        HakiData.integer(player, "galaxyAge", 0);
        HakiData.longValue(player, "galaxyLaunchTick", -1L);
        HakiData.longValue(player, "hakiLeapLaunchTick", 0L);
    }

    private static int encodeGalaxyImpactVariant(float distance, boolean joy) {
        int tenths = Math.max(0, Math.min(999, Math.round(distance * 10f)));
        return tenths * 2 + (joy ? 1 : 0);
    }

    public static void tick(ServerPlayer player) {
        if (!HakiData.enabled(player)) return;
        // V2 removed the dedicated Ryuo Punch/B-key channel. Scrub any stale state left by an
        // older world so legacy NBT can never re-enable its charge pose or remote hand effect.
        if (HakiData.flag(player, "ryoCharging")) HakiData.flag(player, "ryoCharging", false);
        if (HakiData.integer(player, "ryoCharge") != 0) HakiData.integer(player, "ryoCharge", 0);
        long now = player.level().getGameTime();
        applyHakiMovement(player);
        tickKingsGrip(player);
        tickWifiHaki(player);
        if (noCooldowns(player)) clearAbilityCooldowns(player);
        // Conqueror uses elapsed ticks rather than integer +1% steps so a true MAX release can
        // take longer than five seconds. 100% now requires a deliberate eight-second hold.
        tickConquerorCharge(player);
        tickHakiLeap(player);

        long safeFallUntil = HakiData.longValue(player, "galaxySafeFallUntil");
        if (now < safeFallUntil) player.fallDistance = 0;
        if (HakiData.flag(player, "hakiLeapAirborne")) {
            player.fallDistance = 0;
            long launchedAt = HakiData.longValue(player, "hakiLeapLaunchTick");
            if (now - launchedAt > 5 && player.onGround()) {
                HakiData.flag(player, "hakiLeapAirborne", false);
                HakiData.longValue(player, "hakiLeapLaunchTick", 0L);
            }
        }

        if (HakiData.flag(player, "convergenceCharging")) {
            if (!HakiData.flag(player, "convergenceCommitted")) {
                if (HakiData.flag(player, "convergenceHeld")) {
                    int held = HakiData.integer(player, "convergenceHoldTicks") + 1;
                    HakiData.integer(player, "convergenceHoldTicks", held);

                    int before = HakiData.integer(player, "convergenceCharge");
                    int charge = Math.min(CONVERGENCE_MAX, before + GALAXY_PRECHARGE_GAIN);
                    HakiData.integer(player, "convergenceCharge", charge);

                    // 100% is a hard gate. Commit only on the tick that crosses to 100 so a
                    // blocked full-Galaxy cooldown cannot spam the actionbar every held tick.
                    if (before < CONVERGENCE_MAX && charge >= CONVERGENCE_MAX && HakiProgression.convergenceUnlocked(player)) {
                        commitGalaxyImpact(player);
                    } else if (charge >= CONVERGENCE_MAX && HakiProgression.convergenceUnlocked(player)
                            && (noCooldowns(player) || player.level().getGameTime() >= HakiData.longValue(player, "galaxyFullCooldownUntil"))) {
                        // If 100% was reached while the one-minute lockout was still active, keeping
                        // the key held commits automatically on the first legal tick instead of getting stuck.
                        commitGalaxyImpact(player);
                    } else if (before < CONVERGENCE_MAX && charge >= CONVERGENCE_MAX) {
                        player.displayClientMessage(Component.literal("100% Haki Wave ready  ·  Full Galaxy Impact requires ARM " + HakiUnlocks.GALAXY_FULL_ARMAMENT + " + OBS " + HakiUnlocks.GALAXY_FULL_OBSERVATION + " + HAO " + HakiUnlocks.GALAXY_FULL_CONQUEROR + " + Legend " + HakiUnlocks.GALAXY_FULL_LEGEND + "."), true);
                    }
                }
            } else if (HakiData.flag(player, "galaxyPunching")) {
                // Keep the user suspended through the seven-frame punch and compressed detonation.
                player.setNoGravity(true);
                player.setDeltaMovement(Vec3.ZERO);
                player.setSprinting(false);
                player.hurtMarked = true;
                player.fallDistance = 0;
            } else {
                int age = HakiData.integer(player, "galaxyAge");
                HakiData.integer(player, "galaxyAge", age + 1);

                player.setSprinting(false);
                player.fallDistance = 0;

                // Donor RAGE movement: crouch/compress, hard launch, controlled ascent, then hover.
                if (age < GALAXY_LAUNCH_IMPULSE_TICK) {
                    player.setDeltaMovement(Vec3.ZERO);
                    player.hurtMarked = true;
                } else if (age == GALAXY_LAUNCH_IMPULSE_TICK) {
                    player.setNoGravity(false);
                    player.setDeltaMovement(0.0, 1.95, 0.0);
                    player.hurtMarked = true;
                    visual(player, S2CHakiVisual.Visual.GALAXY_ASCENT, 1f, HakiData.joyBoy(player) ? 1 : 0);
                    sound(player, ModSounds.GALAXY_RAGE_CRACK.get(), 1.9f, .78f);
                } else if (age < GALAXY_ASCEND_TICKS) {
                    Vec3 movement = player.getDeltaMovement();
                    player.setDeltaMovement(0.0, Math.max(.12, movement.y), 0.0);
                    player.hurtMarked = true;
                } else {
                    player.setNoGravity(true);
                    player.setDeltaMovement(Vec3.ZERO);
                    player.hurtMarked = true;
                }

                if (age == GALAXY_CHARGE_ANIMATION_TICK) {
                    animate(player, "galaxy_charge", 3);
                }
                if (age == GALAXY_ABSORB_START_TICK) {
                    visual(player, S2CHakiVisual.Visual.GALAXY_FIST_INTENSIFY, 1f, galaxyFistVariant(player));
                    sound(player, ModSounds.GALAXY_RAGE_ABSORB.get(), 2.3f, .92f);
                    sound(player, ModSounds.GALAXY_RAGE_CRACK.get(), 1.8f, .84f);
                }
                if (age == 42 || age == 58 || age == 74) {
                    float pitch = .88f + (age / (float) GALAXY_READY_TICK) * .14f;
                    sound(player, ModSounds.GALAXY_RAGE_CRACK.get(), 1.65f, pitch);
                }

                // Fill the existing Haki charge meter as a cinematic progress bar. 100 means READY
                // and is also the client-side gate that lets vanilla Attack trigger the punch.
                if (age < GALAXY_READY_TICK) {
                    int progress = Math.min(99, Math.round((age / (float)GALAXY_READY_TICK) * 100f));
                    HakiData.integer(player, "convergenceCharge", progress);
                } else if (HakiData.integer(player, "convergenceCharge") < CONVERGENCE_MAX) {
                    HakiData.integer(player, "convergenceCharge", CONVERGENCE_MAX);
                    player.setNoGravity(true);
                    player.setDeltaMovement(Vec3.ZERO);
                    player.hurtMarked = true;
                    animate(player, "galaxy_rage_ready", 4);
                    // READY: draw the orbiting charge ball into the fist rather than leaving it
                    // hanging there. The client contracts it over ~1.3s and leaves a charged fist.
                    visual(player, S2CHakiVisual.Visual.GALAXY_FIST_ABSORB, 1f, galaxyFistVariant(player));
                    sound(player, ModSounds.GALAXY_RAGE_ABSORB.get(), 2.6f, 1.06f);
                    player.displayClientMessage(Component.literal("Galaxy Impact  ·  ATTACK"), true);
                    sound(player, ModSounds.GALAXY_RAGE_CRACK.get(), 2.0f, 1.02f);
                }
            }
        }

        boolean galaxyEnergyLock = HakiData.flag(player, "convergenceCharging");
        boolean armamentActive = HakiData.flag(player, "armamentOn");
        boolean observationActive = HakiData.flag(player, "observationOn");
        boolean advancedActive = HakiData.flag(player, "acocOn");

        // Energy regeneration is an OFF-Haki recovery mechanic. While any persistent Haki state is
        // active, regeneration pauses so the upkeep figures below are the real visible drain rates.
        boolean suppressRegen = HakiData.flag(player, "dominionOn") || HakiData.flag(player, "conquerorCharging")
                || HakiData.flag(player, "wifiHakiChanneling") || galaxyEnergyLock || armamentActive || observationActive || advancedActive;
        if (!suppressRegen) HakiData.energy(player, HakiData.energy(player) + .08f + (HakiData.joyBoy(player) ? .04f : 0));

        // Persistent Haki upkeep is intentionally meaningful now:
        //   Armament       = 0.20/tick = 4 energy/sec
        //   Observation    = 0.35/tick = 7 energy/sec
        //   Advanced Haki  = 0.40/tick = 8 energy/sec TOTAL, replacing normal Armament upkeep
        // Observation stacks with whichever Armament tier is active if the player deliberately runs both.
        // Galaxy Impact still owns its own energy transaction during the cinematic charge/setup.
        boolean kingsGripEnergyLock = HakiData.flag(player, "dominionOn");
        if (!galaxyEnergyLock && !kingsGripEnergyLock) {
            if (advancedActive) HakiData.energy(player, HakiData.energy(player) - .40f);
            else if (armamentActive) HakiData.energy(player, HakiData.energy(player) - .20f);
            if (observationActive) HakiData.energy(player, HakiData.energy(player) - .35f);
        }
        if (HakiData.energy(player) <= 0) cancelActive(player);

        if (HakiData.flag(player, "observationOn")) {
            tickObservationProjectileDodge(player);
            int observation = HakiData.mastery(player, HakiType.OBSERVATION);
            boolean futureSightBurst = observation >= 1000
                    && now < HakiData.longValue(player, "futureSightBurstUntil");
            // Normal Observation snapshots stay cheap. A real Pinnacle Future Sight burst temporarily
            // refreshes at 10 Hz so the 1-2 second forecast feels immediate without permanently
            // multiplying network traffic for every Observation user on the server.
            int interval = futureSightBurst ? 2 : observation >= 780 ? 4 : observation >= 430 ? 6 : 8;
            if (now % interval == 0) sendPerception(player);
        }
        if (now % 5 == 0) sync(player);
        if (now % 20 == 0) broadcastEntityState(player);
    }

    private static void tickConquerorCharge(ServerPlayer player) {
        if (!HakiData.flag(player, "conquerorCharging")) return;

        // Planted while charging. The Gear 5 pose is a committed stance, and letting the user
        // walk around in it both broke the silhouette and made the hold risk-free.
        player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
        player.setSprinting(false);
        player.hurtMarked = true;
        player.fallDistance = 0;

        int mastery = HakiData.mastery(player, HakiType.CONQUEROR);
        int cap = conquerorChargeCap(mastery);
        int before = HakiData.integer(player, "conquerorCharge");
        int previousTicks = HakiData.integer(player, "conquerorChargeTicks");
        int ticks = Math.min(CONQUEROR_FULL_CHARGE_TICKS, previousTicks + 1);
        HakiData.integer(player, "conquerorChargeTicks", ticks);

        // floor() is intentional: 100% is reached only on the exact final tick of the hold.
        int timedCharge = (int) Math.floor(ticks * (CONQUEROR_MAX / (double) CONQUEROR_FULL_CHARGE_TICKS));
        int after = Math.min(cap, timedCharge);
        HakiData.integer(player, "conquerorCharge", after);

        // Re-erupt throughout the hold. Near/MAX charge refreshes at half the normal rate because
        // the much larger bolt reach otherwise turns the view into a wall of red lines. Once the
        // 160-tick cap is reached, use world time for the held-MAX cadence: testing only
        // `ticks % interval` here was a bug because `ticks` remains 160 forever and therefore made
        // MAX emit a complete pulse every game tick (20 pulses/sec).
        int lightningInterval = after >= 95
                ? CONQUEROR_MAX_CHARGE_LIGHTNING_INTERVAL_TICKS
                : CONQUEROR_CHARGE_LIGHTNING_INTERVAL_TICKS;
        boolean advancedThisTick = ticks > previousTicks;
        boolean progressionPulse = advancedThisTick && ticks % lightningInterval == 0;
        boolean heldMaxPulse = !advancedThisTick && ticks >= CONQUEROR_FULL_CHARGE_TICKS
                && player.serverLevel().getGameTime() % CONQUEROR_MAX_CHARGE_LIGHTNING_INTERVAL_TICKS == 0;
        if (progressionPulse || heldMaxPulse) {
            float pulsePower = Math.max(ConquerorLightningPath.MIN_RELEASE_CHARGE, after)
                    / (float)CONQUEROR_MAX;
            emitConquerorChargeLightning(player, pulsePower, mastery);
        }

        int[] stages = {8, 20, 38, 58, 78, 100};
        for (int stage : stages) {
            if (!crossed(before, after, stage)) continue;
            float power = stage / (float) CONQUEROR_MAX;
            if (stage >= 38) sound(player, ModSounds.CONQUEROR_CHARGE.get(), .45f + power * .55f, .75f + power * .42f);
            // Keep the authored laugh loop at MAX; replacing it with the old head-clutch pose
            // made the charging animation visibly snap at the final stage.
        }
    }

    private static void emitConquerorChargeLightning(ServerPlayer player, float power, int mastery) {
        long seed = player.getRandom().nextLong();
        HakiNetwork.tracking(player, new S2CHakiVisual(player.getId(),
                S2CHakiVisual.Visual.CONQUEROR_CHARGE, power, mastery, seed));
        applyConquerorChargeLightningContact(player, power, mastery, seed);
    }

    /** One visual bolt equals one server bolt. Only a widened segment/entity-box intersection stuns. */
    private static void applyConquerorChargeLightningContact(ServerPlayer player, float power,
                                                               int mastery, long seed) {
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.position();
        double radius = ConquerorLightningPath.radius(mastery, power);
        double contactPadding = .52D + .24D * Math.max(0.0F, Math.min(1.0F, power));
        List<ConquerorLightningPath.Bolt> bolts = ConquerorLightningPath.generate(
                power, mastery, seed, false);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius + 1.5D),
                living -> living != player && living.isAlive()
                        && isConquerorCombatTarget(player, living));

        for (LivingEntity target : candidates) {
            AABB hitbox = target.getBoundingBox().inflate(contactPadding);
            boolean touched = false;
            for (ConquerorLightningPath.Bolt bolt : bolts) {
                List<Vec3> points = bolt.points();
                for (int index = 1; index < points.size(); index++) {
                    Vec3 start = origin.add(points.get(index - 1));
                    Vec3 end = origin.add(points.get(index));
                    if (hitbox.clip(start, end).isPresent()) {
                        touched = true;
                        break;
                    }
                }
                if (touched) break;
            }
            if (touched) stunConquerorChargeContact(level, target);
        }
    }

    private static void stunConquerorChargeContact(ServerLevel level, LivingEntity target) {
        long now = level.getGameTime();
        long previousUntil = target.getPersistentData().getLong("HexHakiForcedConquerorStunUntil");
        boolean newlyStunned = previousUntil <= now;
        target.getPersistentData().putLong("HexHakiForcedConquerorStunUntil",
                Math.max(previousUntil, now + CONQUEROR_CHARGE_CONTACT_STUN_TICKS));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                CONQUEROR_CHARGE_CONTACT_STUN_TICKS, 10, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                CONQUEROR_CHARGE_CONTACT_STUN_TICKS, 3, false, false));
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        if (newlyStunned) knockDown(level, target, CONQUEROR_CHARGE_CONTACT_STUN_TICKS);
    }

    /**
     * Players, bosses and always-hostile enemies are valid. A neutral mob is valid only while it
     * is actively targeting this caster, so passive animals, villagers and friendly combat pets
     * never become collateral damage merely because they have some unrelated target.
     */
    private static boolean isConquerorCombatTarget(ServerPlayer source, LivingEntity target) {
        if (target instanceof Player || target instanceof Enemy || isBoss(target)) return true;
        return target instanceof Mob mob && mob.getTarget() == source;
    }

    /** A valid tap is exactly 10 seconds; a true 100% hold is exactly one minute. */
    private static long conquerorReleaseCooldownTicks(int charge) {
        double hold = Math.max(0.0D, Math.min(1.0D,
                (charge - ConquerorLightningPath.MIN_RELEASE_CHARGE)
                        / (double)(CONQUEROR_MAX - ConquerorLightningPath.MIN_RELEASE_CHARGE)));
        return CONQUEROR_MIN_COOLDOWN_TICKS
                + Math.round((CONQUEROR_MAX_COOLDOWN_TICKS - CONQUEROR_MIN_COOLDOWN_TICKS) * hold);
    }

    private static boolean crossed(int before, int after, int threshold) {
        return before < threshold && after >= threshold;
    }

    /**
     * Conqueror starts as a genuine basic tap and earns deeper pressure control through mastery.
     * MAX (and therefore the full current-range stun field) cannot exist before Master-tier Haki.
     */
    private static int conquerorChargeCap(int mastery) {
        if (mastery < 120) return 25;
        if (mastery < 260) return 45;
        if (mastery < 430) return 65;
        if (mastery < 610) return 80;
        if (mastery < 780) return 90;
        return CONQUEROR_MAX;
    }

    /**
     * V2 Observation does not wait for an arrow to physically overlap the player. Every server tick
     * it solves closest approach for nearby projectiles and physically sidesteps a predicted impact.
     * The old ProjectileImpactEvent cancellation remains only as a latency/modded-projectile failsafe.
     */
    private static void tickObservationProjectileDodge(ServerPlayer player) {
        int mastery = HakiData.mastery(player, HakiType.OBSERVATION);
        long now = player.level().getGameTime();
        if (!noCooldowns(player) && now < HakiData.longValue(player, "dodgeCooldown")) return;

        double scanRadius = 5.0 + mastery * .010;      // 5 -> 15 blocks.
        double horizonTicks = 4.0 + mastery * .012;    // 0.2s -> 0.8s future read.
        Vec3 playerCenter = player.getBoundingBox().getCenter();
        Vec3 playerMotion = player.getDeltaMovement();
        Projectile best = null;
        double bestT = Double.MAX_VALUE;
        Vec3 bestVelocity = Vec3.ZERO;
        Vec3 bestImpact = Vec3.ZERO;

        for (Projectile projectile : player.serverLevel().getEntitiesOfClass(Projectile.class,
                player.getBoundingBox().inflate(scanRadius), p -> p.isAlive() && p.getOwner() != player)) {
            Vec3 velocity = projectile.getDeltaMovement().subtract(playerMotion.scale(.35));
            double speed2 = velocity.lengthSqr();
            if (speed2 < .0025) continue;
            Vec3 toPlayer = playerCenter.subtract(projectile.position());
            double t = toPlayer.dot(velocity) / speed2;
            if (t < .15 || t > horizonTicks) continue;
            Vec3 closest = projectile.position().add(velocity.scale(t));
            double hitRadius = .46 + player.getBbWidth() * .42;
            if (closest.distanceToSqr(playerCenter) > hitRadius * hitRadius) continue;
            if (t < bestT) {
                bestT = t;
                best = projectile;
                bestVelocity = velocity;
                bestImpact = closest;
            }
        }
        if (best == null) return;

        Vec3 incoming = new Vec3(bestVelocity.x, 0, bestVelocity.z);
        if (incoming.lengthSqr() < 1.0E-5) incoming = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
        if (incoming.lengthSqr() < 1.0E-5) incoming = new Vec3(0, 0, 1);
        incoming = incoming.normalize();
        Vec3 side = new Vec3(-incoming.z, 0, incoming.x);
        double distance = .52 + mastery * .00068; // 0.52 -> 1.20 blocks.

        // Pick the side with more room. At max mastery this reads as an intentional body slip,
        // while lower mastery reacts later and with less displacement.
        Vec3 leftMove = side.scale(distance);
        Vec3 rightMove = side.scale(-distance);
        boolean leftClear = player.serverLevel().noCollision(player, player.getBoundingBox().move(leftMove));
        boolean rightClear = player.serverLevel().noCollision(player, player.getBoundingBox().move(rightMove));
        Vec3 dodge;
        if (leftClear && rightClear) {
            // Bias away from nearby solid geometry by comparing the two destination collision margins
            // indirectly through an extra half-step; deterministic enough to avoid visual jitter.
            boolean leftWide = player.serverLevel().noCollision(player, player.getBoundingBox().move(leftMove.scale(1.35)));
            boolean rightWide = player.serverLevel().noCollision(player, player.getBoundingBox().move(rightMove.scale(1.35)));
            dodge = leftWide != rightWide ? (leftWide ? leftMove : rightMove) : (player.getRandom().nextBoolean() ? leftMove : rightMove);
        } else if (leftClear) dodge = leftMove;
        else if (rightClear) dodge = rightMove;
        else dodge = null;

        // A shot arriving above shoulder height is ducked under rather than stepped around --
        // sidestepping a head-height arrow reads wrong even when it works. Both the duck and the
        // lean-away are reads, not reflexes, so they are gated behind the same Observation tier
        // that unlocks projectile forecasting; below it the user only has the basic slip.
        boolean advancedReads = mastery >= HakiUnlocks.OBSERVATION_PROJECTILE_FORECAST;
        double shoulder = player.getY() + player.getBbHeight() * .72;
        boolean highShot = advancedReads && bestImpact.y >= shoulder;

        Vec3 retreat = incoming.scale(-Math.min(.72, distance));
        boolean retreatClear = player.serverLevel().noCollision(player, player.getBoundingBox().move(retreat));
        boolean duck = highShot && retreatClear;
        boolean leanAway = !duck && dodge == null;
        if (leanAway && (!advancedReads || !retreatClear)) return;
        if (duck || leanAway) dodge = retreat;
        // Single-line assignment on purpose: validateHakiResources scans line-bounded, so clips
        // named inside an if/else or a multi-line call are silently dropped from its checks.
        String readPose = duck ? "observation_dodge_duck" : leanAway ? "observation_dodge_back" : null;

        player.move(MoverType.SELF, dodge);
        player.setDeltaMovement(player.getDeltaMovement().add(dodge.scale(.22)));
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        int cooldown = Math.max(4, 11 - mastery / 145);
        HakiData.longValue(player, "dodgeCooldown", now + cooldown);

        Vec3 look = player.getLookAngle();
        Vec3 playerRight = new Vec3(-look.z, 0, look.x);
        String slipPose = dodge.dot(playerRight) < 0 ? "observation_dodge_left" : "observation_dodge_right";
        animate(player, readPose != null ? readPose : slipPose, 1);
        HakiNetwork.to(player, new S2CHakiVisual(player.getId(), S2CHakiVisual.Visual.OBSERVATION_PULSE,
                Math.max(.25f, mastery / 1000f), best.getId(), player.getRandom().nextLong()));
        HakiProgression.award(player, HakiType.OBSERVATION, 2, "predicted_projectile_dodge", 12);
    }

    public static void sendPerception(ServerPlayer player) {
        int mastery = HakiData.mastery(player, HakiType.OBSERVATION);
        long now = player.level().getGameTime();
        boolean futureSightActive = mastery >= 1000 && now < HakiData.longValue(player, "futureSightBurstUntil");

        List<S2CPerception.ThreatCue> threats = new ArrayList<>();
        List<S2CPerception.ProjectileForecast> projectiles = new ArrayList<>();
        collectObservationAttackThreats(player, mastery, futureSightActive, threats);
        collectObservationProjectileForecasts(player, mastery, futureSightActive, projectiles, threats);

        // Pinnacle Observation is NOT a permanent 1-2 second wallhack. Genuine imminent danger can
        // trigger a short 1.8 second burst, costs extra Haki energy and then respects a 5 second
        // internal cooldown. Normal Presence/Intent reading continues between bursts.
        if (!futureSightActive && mastery >= 1000 && hasUrgentObservationThreat(threats)
                && activateFutureSight(player)) {
            futureSightActive = true;
            threats.clear();
            projectiles.clear();
            collectObservationAttackThreats(player, mastery, true, threats);
            collectObservationProjectileForecasts(player, mastery, true, projectiles, threats);
        }

        double radius = 12.0 + mastery * .068; // 12 blocks at 0 -> 80 blocks at 1000.
        double hiddenRadius = mastery < 550 ? 0.0 : Math.min(radius, 18.0 + (mastery - 550) * .138);
        List<S2CPerception.Entry> entries = new ArrayList<>();
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), e -> e != player && e.isAlive())) {
            boolean hidden = !player.hasLineOfSight(target);
            if (hidden && (hiddenRadius <= 0.0 || target.distanceToSqr(player) > hiddenRadius * hiddenRadius)) continue;

            byte classification = 0;
            if (mastery >= 100) {
                if (target instanceof Mob mob && mob.getTarget() == player) classification = 2;
                else if (target instanceof Enemy) classification = 1;
                if (target.getMaxHealth() + target.getArmorValue() * 3 > 80) classification = 3;
            }
            float threat = Math.min(1f, (target.getMaxHealth() + target.getArmorValue() * 4) / 180f);

            Vec3 sensed = hidden ? softenedHiddenPresence(target, mastery, now) : target.position();
            Vec3 prediction = mastery >= 240 ? futureSightVector(target, mastery, futureSightActive) : Vec3.ZERO;
            float confidence = futureSightConfidence(target, mastery, prediction, futureSightActive);
            entries.add(new S2CPerception.Entry(target.getId(), classification, threat,
                    (float)sensed.x, (float)sensed.y, (float)sensed.z,
                    (float)prediction.x, (float)prediction.y, (float)prediction.z,
                    confidence, hidden));
            // Presence packets are intentionally bounded. This keeps 80-block Supreme Observation
            // useful on multiplayer servers without shipping hundreds of entity forecasts every refresh.
            if (entries.size() >= 64) break;
        }

        long burstUntil = HakiData.longValue(player, "futureSightBurstUntil");
        int futureTicks = futureSightActive ? (int)Math.max(1L, Math.min(40L, burstUntil - now)) : 0;
        HakiNetwork.to(player, new S2CPerception(entries, projectiles, threats,
                futureSightActive ? 8 : mastery >= 780 ? 14 : 12,
                futureSightActive, futureTicks));
    }

    private static boolean activateFutureSight(ServerPlayer player) {
        int mastery = HakiData.mastery(player, HakiType.OBSERVATION);
        long now = player.level().getGameTime();
        if (mastery < 1000 || now < HakiData.longValue(player, "futureSightCooldownUntil")
                || now < HakiData.longValue(player, "futureSightBurstUntil")) return false;
        if (!HakiData.consume(player, 24f)) return false;

        HakiData.longValue(player, "futureSightBurstUntil", now + 36L);     // 1.8 seconds.
        HakiData.longValue(player, "futureSightCooldownUntil", now + 100L); // 5 seconds between bursts.
        delayedSound(player, ModSounds.OBSERVATION_DANGER.get(), .48f, 1.18f);
        HakiNetwork.to(player, new S2CHakiVisual(player.getId(), S2CHakiVisual.Visual.OBSERVATION_PULSE,
                1f, -1, player.getRandom().nextLong()));
        HakiProgression.award(player, HakiType.OBSERVATION, 2, "future_sight_burst", 80);
        return true;
    }

    /** Called by the damage-event fallback so modded attacks can still wake Pinnacle Future Sight. */
    public static void triggerFutureSightFromDanger(ServerPlayer player) {
        if (HakiData.enabled(player) && HakiData.flag(player, "observationOn")) activateFutureSight(player);
    }

    private static boolean hasUrgentObservationThreat(List<S2CPerception.ThreatCue> threats) {
        for (S2CPerception.ThreatCue cue : threats) if (cue.urgency() >= .72f) return true;
        return false;
    }

    private static void collectObservationAttackThreats(ServerPlayer player, int mastery, boolean futureSightActive,
                                                        List<S2CPerception.ThreatCue> threats) {
        if (mastery < 550) return;
        double warningRange = futureSightActive ? 14.0 : 6.0 + (mastery - 550) * .012;
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(warningRange), e -> e != player && e.isAlive())) {
            if (player.isAlliedTo(target)) continue;
            double distance = Math.sqrt(target.distanceToSqr(player));
            if (distance > warningRange) continue;

            int ticksToImpact = 99;
            float urgency = 0f;
            boolean intent = false;

            if (target instanceof Mob mob && mob.getTarget() == player) {
                // Modded hostile mobs are covered as long as they use the normal Mob target contract.
                // Supreme Observation can read pursuit through a wall; lower tiers require line of sight.
                if (!player.hasLineOfSight(target) && mastery < 850) continue;
                double reach = 2.35 + target.getBbWidth() * .65 + player.getBbWidth() * .35;
                double closing = Math.max(.075, new Vec3(target.getDeltaMovement().x, 0, target.getDeltaMovement().z).length());
                ticksToImpact = (int)Math.max(2, Math.min(24, Math.ceil(Math.max(0.0, distance - reach) / closing)));
                urgency = Mth.clamp((float)(1.02 - ticksToImpact / 25.0 + Math.max(0.0, reach + .8 - distance) * .12), .28f, 1f);
                intent = true;
            } else if (target instanceof Player other && !other.isSpectator()) {
                // Players have no Mob target field. Treat a fully-readied, close, non-allied player
                // looking almost directly at the user as probable combat intent, not a guaranteed hit.
                if (distance > (futureSightActive ? 5.5 : 4.25)) continue;
                Vec3 toward = player.getEyePosition().subtract(other.getEyePosition());
                if (toward.lengthSqr() < .001) continue;
                double facing = other.getLookAngle().normalize().dot(toward.normalize());
                if (facing < .82 || other.getAttackStrengthScale(0.0F) < .80F) continue;
                ticksToImpact = futureSightActive ? 4 : 7;
                urgency = distance < 2.8 ? .78f : .58f;
                intent = true;
            }

            if (intent) {
                threats.add(new S2CPerception.ThreatCue(target.getId(), (byte)0, urgency, ticksToImpact,
                        (float)target.getX(), (float)target.getY(), (float)target.getZ()));
                if (threats.size() >= 12) return;
            }
        }
    }

    private static void collectObservationProjectileForecasts(ServerPlayer player, int mastery, boolean futureSightActive,
                                                               List<S2CPerception.ProjectileForecast> forecasts,
                                                               List<S2CPerception.ThreatCue> threats) {
        if (mastery < 400) return;
        double scanRadius = futureSightActive ? 64.0 : 12.0 + mastery * .035;
        int horizon = futureSightActive ? 36 : Math.min(24, 10 + (mastery - 400) / 50);
        Vec3 playerCenter = player.getBoundingBox().getCenter();
        Vec3 playerMotion = player.getDeltaMovement();

        for (Projectile projectile : player.serverLevel().getEntitiesOfClass(Projectile.class,
                player.getBoundingBox().inflate(scanRadius), p -> p.isAlive() && p.getOwner() != player)) {
            Vec3 velocity = projectile.getDeltaMovement();
            double speed2 = velocity.lengthSqr();
            if (speed2 < .0025) continue;
            Vec3 relativeVelocity = velocity.subtract(playerMotion.scale(.35));
            double relativeSpeed2 = relativeVelocity.lengthSqr();
            if (relativeSpeed2 < .0025) continue;
            Vec3 toPlayer = playerCenter.subtract(projectile.position());
            if (toPlayer.dot(relativeVelocity) <= 0) continue; // moving away or tangential.

            double t = Mth.clamp(toPlayer.dot(relativeVelocity) / relativeSpeed2, 0.0, horizon);
            Vec3 closest = projectile.position().add(relativeVelocity.scale(t));
            double miss = Math.sqrt(closest.distanceToSqr(playerCenter));
            if (miss > 10.0) continue; // do not paint every unrelated arrow in a crowded battlefield.

            double hitRadius = .48 + player.getBbWidth() * .44;
            float danger;
            if (miss <= hitRadius) danger = Mth.clamp((float)(.72 + .28 * (1.0 - t / Math.max(1.0, horizon))), .72f, 1f);
            else danger = Mth.clamp((float)(.12 + .34 * (1.0 - Math.min(1.0, miss / 10.0))), .12f, .52f);

            forecasts.add(new S2CPerception.ProjectileForecast(projectile.getId(),
                    (float)projectile.getX(), (float)projectile.getY(), (float)projectile.getZ(),
                    (float)velocity.x, (float)velocity.y, (float)velocity.z,
                    horizon, danger));

            if (danger >= .72f && threats.size() < 20) {
                threats.add(new S2CPerception.ThreatCue(projectile.getId(), (byte)1, danger,
                        (int)Math.max(1, Math.ceil(t)),
                        (float)projectile.getX(), (float)projectile.getY(), (float)projectile.getZ()));
            }
            if (forecasts.size() >= 16) return;
        }
    }

    private static Vec3 softenedHiddenPresence(LivingEntity target, int mastery, long now) {
        // Through-terrain Presence Sense is intentionally vague. The server quantizes and gently
        // offsets hidden positions before they ever reach the renderer, so this cannot become a
        // body-perfect ESP outline simply by changing client render depth.
        double grid = mastery >= 850 ? .50 : mastery >= 700 ? 1.0 : 2.0;
        double jitter = mastery >= 850 ? .18 : mastery >= 700 ? .42 : .82;
        long phase = now / 20L + target.getId() * 31L;
        double jx = Math.sin(phase * .73) * jitter;
        double jz = Math.cos(phase * .61) * jitter;
        double x = Math.rint(target.getX() / grid) * grid + jx;
        double y = Math.rint(target.getY() / grid) * grid;
        double z = Math.rint(target.getZ() / grid) * grid + jz;
        return new Vec3(x, y, z);
    }

    private static Vec3 futureSightVector(LivingEntity target, int mastery, boolean futureSightActive) {
        // Intent Reading begins at 240. Normal mastery grows the temporal reach gradually; the true
        // 1000-mastery Future Sight burst reaches 36 ticks = 1.8 seconds into current movement intent.
        double horizon;
        if (futureSightActive) horizon = 36.0;
        else if (mastery < 550) horizon = 5.0 + (mastery - 240) * .016;
        else if (mastery < 700) horizon = 10.0 + (mastery - 550) * .027;
        else if (mastery < 850) horizon = 14.0 + (mastery - 700) * .040;
        else horizon = 20.0 + (mastery - 850) * .027;
        horizon = Math.max(4.0, Math.min(futureSightActive ? 36.0 : 24.0, horizon));

        Vec3 motion = target.getDeltaMovement();
        Vec3 horizontalMotion = new Vec3(motion.x, 0, motion.z);
        Vec3 intended = horizontalMotion;
        if (target instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            Vec3 toward = mob.getTarget().position().subtract(target.position());
            toward = new Vec3(toward.x, 0, toward.z);
            if (toward.lengthSqr() > .01) {
                double intentSpeed = Math.max(.055, Math.min(.18, mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * .48));
                Vec3 intent = toward.normalize().scale(intentSpeed);
                intended = horizontalMotion.scale(.58).add(intent.scale(.42));
            }
        } else if (horizontalMotion.lengthSqr() < .0015 && target instanceof Mob) {
            intended = Vec3.ZERO;
        }

        Vec3 predicted = new Vec3(intended.x * horizon,
                Math.max(-1.8, Math.min(1.8, motion.y * Math.min(10.0, horizon))),
                intended.z * horizon);
        double max = futureSightActive ? 11.0 : mastery >= 850 ? 8.0 : mastery >= 550 ? 5.5 : 3.5;
        double horizontal = Math.sqrt(predicted.x * predicted.x + predicted.z * predicted.z);
        if (horizontal > max) {
            double scale = max / horizontal;
            predicted = new Vec3(predicted.x * scale, predicted.y, predicted.z * scale);
        }
        return predicted;
    }

    private static float futureSightConfidence(LivingEntity target, int mastery, Vec3 prediction, boolean futureSightActive) {
        float base = .30f + mastery * .00050f + (futureSightActive ? .14f : 0f);
        if (target instanceof Mob mob && mob.getTarget() != null) base += .10f;
        if (target instanceof Player) base -= .08f; // human input is inherently less deterministic than mob pathing.
        if (prediction.lengthSqr() < .03) base -= .08f;
        return Math.max(.20f, Math.min(.97f, base));
    }

    public static void cancelAll(ServerPlayer player) {
        cancelActive(player);
        animate(player, "__clear__", 3);
        broadcastEntityState(player);
        sync(player);
    }

    private static void cancelActive(ServerPlayer player) {
        stopWifiHaki(player, false);
        cancelGripInvolving(player);
        if (HakiData.flag(player, "convergenceCharging")
                || HakiData.flag(player, "convergenceCommitted")
                || HakiData.flag(player, "galaxyPunching")) {
            clearGalaxyState(player);
        }

        for (String key : new String[]{"conquerorCharging", "convergenceCharging", "convergenceHeld", "convergenceCommitted", "convergenceDivePose", "galaxyPunching", "galaxyOriginalNoGravity", "galaxyAdvanced", "galaxyHakiCoated", "dominionOn", "acocOn", "armamentOn", "observationOn", "wifiHakiChanneling", "hakiLeapCharging", "hakiLeapAirborne"}) HakiData.flag(player, key, false);
        for (String key : new String[]{"conquerorCharge", "conquerorChargeTicks", "convergenceCharge", "convergenceHoldTicks", "galaxyAge", "dominionCharge", "hakiLeapChargeTicks"}) HakiData.integer(player, key, 0);
        HakiData.longValue(player, "galaxyLaunchTick", -1L);
        HakiData.longValue(player, "hakiLeapLaunchTick", 0L);
    }

    public static void sync(ServerPlayer player) {
        HakiNetwork.to(player, new S2CStateSync(
                HakiData.mastery(player, HakiType.ARMAMENT), HakiData.mastery(player, HakiType.OBSERVATION), HakiData.mastery(player, HakiType.CONQUEROR),
                HakiData.xp(player, HakiType.ARMAMENT), HakiData.xp(player, HakiType.OBSERVATION), HakiData.xp(player, HakiType.CONQUEROR),
                HakiData.energy(player), HakiData.maxEnergy(player), HakiData.enabled(player), HakiData.joyBoy(player), HakiData.boardEnabled(player), HakiData.legend(player),
                HakiData.flag(player, "armamentOn"), HakiData.flag(player, "observationOn"), HakiData.flag(player, "acocOn"), HakiData.flag(player, "dominionOn"),
                HakiData.integer(player, "conquerorCharge"), 0, HakiData.integer(player, "convergenceCharge"),
                0));
    }

    public static void debugVfx(ServerPlayer player, String group) {
        List<S2CHakiVisual.Visual> tests = switch (group.toLowerCase(Locale.ROOT)) {
            case "armament" -> List.of(S2CHakiVisual.Visual.ARMAMENT_ON, S2CHakiVisual.Visual.ARMAMENT_HIT, S2CHakiVisual.Visual.RYO_RELEASE, S2CHakiVisual.Visual.INTERNAL_HIT);
            case "conqueror" -> List.of(S2CHakiVisual.Visual.CONQUEROR_CHARGE, S2CHakiVisual.Visual.CONQUEROR_RELEASE, S2CHakiVisual.Visual.CONQUEROR_AFTERSHOCK, S2CHakiVisual.Visual.CONQUEROR_HIT);
            case "shockwave" -> List.of(S2CHakiVisual.Visual.ARMAMENT_HIT, S2CHakiVisual.Visual.RYO_RELEASE, S2CHakiVisual.Visual.CONQUEROR_RELEASE, S2CHakiVisual.Visual.CONVERGENCE_HIT);
            case "aura" -> List.of(S2CHakiVisual.Visual.ARMAMENT_ON, S2CHakiVisual.Visual.CONQUEROR_CHARGE, S2CHakiVisual.Visual.DOMINION, S2CHakiVisual.Visual.JOYBOY_AWAKENING);
            case "convergence" -> List.of(S2CHakiVisual.Visual.GALAXY_ASCENT, S2CHakiVisual.Visual.CONVERGENCE_CHARGE, S2CHakiVisual.Visual.CONVERGENCE_RELEASE, S2CHakiVisual.Visual.GALAXY_IMPACT);
            default -> List.of(S2CHakiVisual.Visual.values());
        };
        for (int index = 0; index < tests.size(); index++) {
            int delay = index * 12;
            S2CHakiVisual.Visual visual = tests.get(index);
            int variant = visual == S2CHakiVisual.Visual.CONQUEROR_RELEASE
                    ? encodeConquerorVariant(1000, ConquerorMode.RADIAL)
                    : visual == S2CHakiVisual.Visual.GALAXY_IMPACT ? encodeGalaxyImpactVariant(14f, true) : 1000;
            ServerTimeline.later(player.serverLevel(), delay + 1, () -> HakiNetwork.tracking(player, new S2CHakiVisual(player.getId(), visual, 1f, variant, player.getRandom().nextLong())));
        }
    }

    private static int encodeConquerorVariant(int mastery, ConquerorMode mode) {
        return mastery * 4 + mode.ordinal();
    }

    private static void scheduleSupremeAudio(ServerPlayer player) {
        UUID id = player.getUUID();
        ServerLevel level = player.serverLevel();
        ServerTimeline.later(level, 5, () -> playFor(level, id, ModSounds.CONQUEROR_SUPREME_RUMBLE.get(), 3.4f, .78f));
    }

    private static void scheduleAftershocks(ServerPlayer player) {
        UUID id = player.getUUID();
        ServerLevel level = player.serverLevel();
        int[] delays = {18, 42, 74};
        for (int index = 0; index < delays.length; index++) {
            int wave = index;
            ServerTimeline.later(level, delays[index], () -> {
                ServerPlayer found = level.getServer().getPlayerList().getPlayer(id);
                if (found != null && found.isAlive()) visual(found, S2CHakiVisual.Visual.CONQUEROR_AFTERSHOCK, .72f + wave * .12f, 1000);
            });
        }
    }

    private static void playFor(ServerLevel level, UUID id, SoundEvent sound, float volume, float pitch) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        if (player != null) delayedSound(player, sound, volume, pitch);
    }

    private static void locked(ServerPlayer player, String technique, int required, int current) {
        if (current < required) player.displayClientMessage(Component.literal(technique + " unlocks at " + required + " mastery (current " + current + ")."), true);
    }

    private static boolean cooldownReady(ServerPlayer player, String key, String label) {
        if (noCooldowns(player)) return true;
        long now = player.level().getGameTime();
        long ready = HakiData.longValue(player, key);
        if (now >= ready) return true;
        float seconds = (ready - now) / 20f;
        player.displayClientMessage(Component.literal(label + " cooldown: " + String.format(Locale.ROOT, "%.1fs", seconds)), true);
        return false;
    }

    private static int scaledCooldown(int mastery, int unlockMastery, int baseTicks, int minTicks) {
        if (mastery <= unlockMastery) return baseTicks;
        float progress = Math.max(0f, Math.min(1f, (mastery - unlockMastery) / (float)Math.max(1, 1000 - unlockMastery)));
        return Math.max(minTicks, Math.round(baseTicks + (minTicks - baseTicks) * progress));
    }

    /** All authored Haki cues intentionally land ~1 second after the trigger at half the old volume. */
    public static void delayedSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        ServerTimeline.later(level, 20, () -> {
            ServerPlayer found = level.getServer().getPlayerList().getPlayer(id);
            if (found == null || !found.isAlive() || !HakiData.enabled(found)) return;

            // Never let a delayed activation/ambient cue arrive after its Haki state has already
            // been switched off. The OFF cue itself is still allowed to play normally.
            if ((sound == ModSounds.ARMAMENT_ON.get() || sound == ModSounds.ARMAMENT_CRACKLE.get())
                    && !HakiData.flag(found, "armamentOn")) return;
            if (sound == ModSounds.OBSERVATION_ON.get() && !HakiData.flag(found, "observationOn")) return;
            if (sound == ModSounds.ACOC_ON.get() && !HakiData.flag(found, "acocOn")) return;
            if (sound == ModSounds.DOMINION_ON.get() && !HakiData.flag(found, "dominionOn")) return;

            // Advanced Haki owns the passive/activation audio layer while active.
            if (HakiData.flag(found, "acocOn") && (sound == ModSounds.ARMAMENT_ON.get() || sound == ModSounds.ARMAMENT_OFF.get() || sound == ModSounds.ARMAMENT_CRACKLE.get())) return;
            level.playSound(null, found.blockPosition(), sound, SoundSource.PLAYERS, volume * .50f, pitch);
        });
    }

    private static void delayedWorldSound(ServerLevel level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        ServerTimeline.later(level, 20, () -> level.playSound(null, pos, sound, SoundSource.PLAYERS, volume * .50f, pitch));
    }

    private static void immediateSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        delayedSound(player, sound, volume, pitch);
    }

    public static void animate(ServerPlayer player, String name, int fade) {
        HakiNetwork.tracking(player, new S2CAnimation(player.getId(), name, fade));
    }

    public static void visual(ServerPlayer player, S2CHakiVisual.Visual visual, float power, int variant) {
        HakiNetwork.tracking(player, new S2CHakiVisual(player.getId(), visual, power, variant, player.getRandom().nextLong()));
    }

    private static void broadcastEntityState(ServerPlayer player) {
        HakiNetwork.tracking(player, new S2CEntityState(player.getId(), HakiData.flag(player, "armamentOn"), HakiData.flag(player, "acocOn"), false, HakiData.mastery(player, HakiType.ARMAMENT), HakiData.mastery(player, HakiType.CONQUEROR)));
    }

    public static float mastery01(ServerPlayer player, HakiType type) {
        return HakiData.mastery(player, type) / 1000f;
    }

    private static boolean isBoss(LivingEntity living) {
        return living instanceof EnderDragon || living instanceof WitherBoss;
    }

    private static LivingEntity nearestOnSegment(ServerPlayer player, Vec3 start, Vec3 end, double width) {
        AABB box = new AABB(start, end).inflate(width);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        for (LivingEntity living : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            Vec3 center = living.getBoundingBox().getCenter();
            double t = lengthSquared < 1.0E-6 ? 0 : Math.max(0, Math.min(1, center.subtract(start).dot(segment) / lengthSquared));
            Vec3 closest = start.add(segment.scale(t));
            double distance = center.distanceToSqr(closest);
            if (distance <= width * width && distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }
}
