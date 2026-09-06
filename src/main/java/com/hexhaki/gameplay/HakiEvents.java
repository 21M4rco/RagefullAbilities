package com.hexhaki.gameplay;

import com.hexhaki.HexHaki;
import com.hexhaki.data.HakiData;
import com.hexhaki.data.HakiRank;
import com.hexhaki.data.HakiType;
import com.hexhaki.data.HakiUnlocks;
import com.hexhaki.network.HakiNetwork;
import com.hexhaki.network.msg.S2CAnimation;
import com.hexhaki.network.msg.S2CArmamentImpact;
import com.hexhaki.network.msg.S2CHakiVisual;
import com.hexhaki.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = HexHaki.MODID)
public final class HakiEvents {
    /** Chance an incoming shot is caught and returned instead of merely slipped, at unlock. */
    private static final float ARROW_CATCH_CHANCE_MIN = .30f;
    /** ...and at Observation 1000. */
    private static final float ARROW_CATCH_CHANCE_MAX = .70f;
    /** How far a caught shot will look for something hostile to send itself at. */
    private static final double ARROW_RETURN_RANGE = 28.0D;
    /** Ticks the caught shot is held in the hand and read. Cut to the clip's release frame. */
    private static final int ARROW_CATCH_HOLD_TICKS = 34;
    /** Chance a read melee swing is answered with a backflip and a charge punch, at unlock. */
    private static final float COUNTER_FLIP_CHANCE_MIN = .30f;
    /** ...and at Observation 1000. */
    private static final float COUNTER_FLIP_CHANCE_MAX = .70f;
    /** Tick the charge fires. The flip lands on 16 and the fist is cocked on 19. */
    private static final int COUNTER_FLIP_DASH_TICK = 22;
    /** Tick the extended fist arrives, six ticks of travel after the charge. */
    private static final int COUNTER_FLIP_STRIKE_TICK = 28;
    private static final double COUNTER_FLIP_RANGE = 12.0D;

    private static final int LAST_STAND_DURATION_TICKS = 20 * 14;
    private static final long LAST_STAND_COOLDOWN_TICKS = 20L * 60L * 20L;
    // 50-block diameter true sphere for the Last Stand control field.
    private static final double LAST_STAND_RADIUS = 32.0D;
    private static final int LAST_STAND_FINAL_BLAST_LEAD_TICKS = 20;
    private static final int LAST_STAND_FINAL_BLAST_MOTION_TICKS = 8;
    // Final detonation matches the same 50-block diameter sphere.
    private static final double LAST_STAND_FINAL_BLAST_RADIUS = 32.0D;
    // Forty hearts (80 Minecraft health points) of raw Haoshoku pressure damage to hostile living targets.
    private static final float LAST_STAND_FINAL_BLAST_DAMAGE = 80.0F;
    private static final String LAST_STAND_UNTIL = "lastStandUntil";
    private static final String LAST_STAND_COOLDOWN_UNTIL = "lastStandCooldownUntil";
    private static final String LAST_STAND_FINAL_DEATH = "lastStandFinalDeath";
    private static final String LAST_STAND_X = "lastStandX";
    private static final String LAST_STAND_Y = "lastStandY";
    private static final String LAST_STAND_Z = "lastStandZ";
    private static final String LAST_STAND_NEXT_BURST = "lastStandNextBurst";
    private static final String LAST_STAND_FINAL_BLAST_DONE = "lastStandFinalBlastDone";
    private static final String LAST_STAND_HOSTILE = "HexHakiLastStandHostile";
    // Transient server-only guard used only around the authored final detonation damage call.
    // Without this, the generic "Last Stand caster cannot attack" event guard also cancels
    // the passive blast itself because its DamageSource is player-attributed.
    private static final String LAST_STAND_SCRIPTED_DAMAGE = "HexHakiAllowLastStandBlastDamage";

    // Only entities currently inside a MAX/Joy Boy launch window are tracked. Using a small
    // per-level UUID set avoids scanning every loaded entity each server tick. Weak level keys
    // also guarantee the registry disappears naturally when a world unloads.
    private static final Map<net.minecraft.server.level.ServerLevel, Set<UUID>> ACTIVE_CONQUEROR_BLASTS = new WeakHashMap<>();

    private HakiEvents() {}

    public static void trackConquerorBlast(LivingEntity living) {
        if (!(living.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        ACTIVE_CONQUEROR_BLASTS.computeIfAbsent(level, ignored -> new HashSet<>()).add(living.getUUID());
    }

    /**
     * Final authority for MAX/Joy Boy blast propulsion. Running at LevelTick END is deliberate:
     * vanilla AI, boss movement controllers and modded entity ticks have already had their chance
     * to overwrite velocity, so the Haki pressure wave wins the final movement state each tick.
     */
    @SubscribeEvent
    public static void conquerorBlastLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof net.minecraft.server.level.ServerLevel level)) return;
        Set<UUID> active = ACTIVE_CONQUEROR_BLASTS.get(level);
        if (active == null || active.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<UUID> iterator = active.iterator();
        while (iterator.hasNext()) {
            Entity entity = level.getEntity(iterator.next());
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                iterator.remove();
                continue;
            }
            var tag = living.getPersistentData();
            long blastUntil = tag.getLong("HexHakiConquerorBlastMotionUntil");
            if (blastUntil > now) {
                enforceConquerorBlastMotion(living, tag, now, blastUntil);
            } else {
                clearConquerorBlastMotion(tag);
                iterator.remove();
            }
        }
        if (active.isEmpty()) ACTIVE_CONQUEROR_BLASTS.remove(level);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent e) {
        if(e.phase==TickEvent.Phase.END && e.player instanceof ServerPlayer sp) {
            if (tickLastStand(sp)) return;
            HakiServerController.tick(sp);
            HakiProgression.tickPassive(sp);
        }
    }

    /**
     * Max-Conqueror passive: lethal damage is converted into a fourteen-second kneeling last stand.
     * The camera is deliberately untouched; only physical movement is pinned server-side.
     */
    private static boolean tickLastStand(ServerPlayer player) {
        long until = HakiData.longValue(player, LAST_STAND_UNTIL);
        if (until <= 0L) return false;

        long now = player.level().getGameTime();
        if (now >= until) {
            HakiData.longValue(player, LAST_STAND_UNTIL, 0L);
            HakiData.flag(player, LAST_STAND_FINAL_DEATH, true);
            HakiNetwork.tracking(player, new S2CAnimation(player.getId(), "__clear__", 2));
            try {
                player.kill();
            } finally {
                HakiData.flag(player, LAST_STAND_FINAL_DEATH, false);
            }
            return true;
        }

        // Hard movement lock without touching yaw/pitch, mouse input, FOV or camera mode.
        double x = HakiData.root(player).getDouble(LAST_STAND_X);
        double y = HakiData.root(player).getDouble(LAST_STAND_Y);
        double z = HakiData.root(player).getDouble(LAST_STAND_Z);
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(x, y, z);
        player.hurtMarked = true;
        player.fallDistance = 0f;
        player.setSprinting(false);

        long remaining = Math.max(0L, until - now);
        boolean finalBlastReleased = HakiData.flag(player, LAST_STAND_FINAL_BLAST_DONE);
        if (remaining <= LAST_STAND_FINAL_BLAST_LEAD_TICKS && !finalBlastReleased) {
            HakiData.flag(player, LAST_STAND_FINAL_BLAST_DONE, true);
            releaseLastStandFinalBlast(player);
            finalBlastReleased = true;
        }

        int elapsed = LAST_STAND_DURATION_TICKS - (int)Math.max(0L, until - now);
        float progress = Math.max(0f, Math.min(1f, elapsed / (float)LAST_STAND_DURATION_TICKS));
        double activeRadius = Math.max(1.5D, LAST_STAND_RADIUS * progress);

        // Once the expanding Haoshoku field reaches anything living, keep it knocked down until the
        // final blast. That detonation is a hard handoff from stun control to normal launch physics:
        // never reapply the stun during the last second or victims will freeze in mid-air.
        if (!finalBlastReleased) {
            var box = player.getBoundingBox().inflate(activeRadius);
            for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box, living ->
                    living != player
                            && living.isAlive()
                            && living.getBoundingBox().getCenter()
                                    .distanceToSqr(player.getBoundingBox().getCenter()) <= activeRadius * activeRadius
                            && isLastStandHostile(player, living))) {
                long oldUntil = target.getPersistentData().getLong("HexHakiLastStandStunUntil");
                boolean newlyStunned = oldUntil <= now;
                // Remember the hostile classification before the knockdown clears AI targets.
                target.getPersistentData().putBoolean(LAST_STAND_HOSTILE, true);
                target.getPersistentData().putLong("HexHakiLastStandStunUntil", Math.max(oldUntil, until));
                if (newlyStunned) {
                    HakiServerController.knockDownForLastStand(player.serverLevel(), target,
                            (int)Math.max(1L, until - now));
                }
            }
        }

        // Each layered overhead-and-radial storm now travels in sequential WiFi-style sections,
        // redraws through delayed filaments, then hands off to the next pulse. The invisible stun
        // radius still expands smoothly underneath the much larger visual storm.
        long nextBurst = HakiData.longValue(player, LAST_STAND_NEXT_BURST);
        // LAST_STAND_FINISH already contains its own lightning surge. Stop ordinary pulse audio
        // after detonation so nothing masks the exact MAX Conqueror Release blast cue.
        if (!finalBlastReleased && now >= nextBurst) {
            HakiServerController.visual(player, S2CHakiVisual.Visual.LAST_STAND_BLAST, progress, 1000);

            // Dedicated Last Stand lightning: three original crack/pressure/rumble recordings are
            // selected by the sound event. No vanilla Minecraft lightning sample is used here.
            float lightningPitch = .92f + player.getRandom().nextFloat() * .12f;
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.LAST_STAND_LIGHTNING.get(),
                    SoundSource.PLAYERS, 2.30f, lightningPitch);
            if (player.getRandom().nextFloat() < .35f) {
                player.serverLevel().playSound(null, player.blockPosition(), ModSounds.CONQUEROR_SUPREME_RUMBLE.get(),
                        SoundSource.PLAYERS, .85f, .68f + player.getRandom().nextFloat() * .10f);
            }

            // 10-18 tick cadence lets the animated tail overlap the next travelling pulse slightly,
            // keeping the battlefield alive without turning every bolt into one static cage.
            HakiData.longValue(player, LAST_STAND_NEXT_BURST, now + 10L + player.getRandom().nextInt(9));
        }
        return true;
    }

    /** One second before death, throw every entity in the same true 25-block-radius pressure sphere. */
    private static void releaseLastStandFinalBlast(ServerPlayer player) {
        var level = player.serverLevel();
        long now = level.getGameTime();
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("<" + player.getGameProfile().getName()
                        + "> I'll be taking you all to hell with me!"), false);
        HakiServerController.visual(player, S2CHakiVisual.Visual.LAST_STAND_FINISH, 1f,
                (int)LAST_STAND_FINAL_BLAST_RADIUS);
        // Reproduce the MAX/Joy Boy release's exact impact pair after the global Haki sound path's
        // 50% mix: Supreme Snap (3.3 -> 1.65) plus Supreme Blast (4.0 -> 2.0), at their original
        // pitches. Play them immediately here; scheduling would lose them when the owner dies.
        level.playSound(null, player.blockPosition(), ModSounds.CONQUEROR_SUPREME_SNAP.get(),
                SoundSource.PLAYERS, 1.65f, .96f);
        level.playSound(null, player.blockPosition(), ModSounds.CONQUEROR_SUPREME_BLAST.get(),
                SoundSource.PLAYERS, 2.0f, .88f);

        // True sphere. The old check measured feet-to-feet and then launched purely horizontally,
        // so the blast behaved like a disc at ground level: anything above or below the caster was
        // both mis-ranged and shoved sideways. Range is now measured centre-to-centre and the
        // launch follows the full 3D radius, so the pressure front is spherical in every axis.
        Vec3 source = player.getBoundingBox().getCenter();
        double blastRadiusSq = LAST_STAND_FINAL_BLAST_RADIUS * LAST_STAND_FINAL_BLAST_RADIUS;
        var blastBox = player.getBoundingBox().inflate(LAST_STAND_FINAL_BLAST_RADIUS);
        for (Entity target : level.getEntities(player, blastBox, entity ->
                entity.isAlive()
                        && entity.getBoundingBox().getCenter().distanceToSqr(player.getBoundingBox().getCenter()) <= blastRadiusSq)) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(source);
            double distance = Math.sqrt(Math.max(0.0D, delta.lengthSqr()));
            Vec3 radial = delta.lengthSqr() < 1.0E-6D ? null : delta.normalize();
            if (radial == null) {
                double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
                radial = new Vec3(Math.cos(angle), .35D, Math.sin(angle)).normalize();
            }
            Vec3 away = radial;
            double falloff = Math.max(0.0D, 1.0D - distance / LAST_STAND_FINAL_BLAST_RADIUS);
            double shaped = Math.sqrt(falloff);
            double force = .95D + 1.45D * shaped;
            double lift = .30D + .42D * shaped;
            Vec3 previous = target.getDeltaMovement();
            // Launch along the full radius so targets above the caster are thrown up and out and
            // targets below are driven down and out -- a sphere expanding, not a ground-level ring.
            target.setDeltaMovement(
                    away.x * force + previous.x * .12D,
                    Math.max(previous.y * .12D, lift + away.y * force * .70D),
                    away.z * force + previous.z * .12D);
            target.hasImpulse = true;

            if (target instanceof LivingEntity living) {
                long motionUntil = now + LAST_STAND_FINAL_BLAST_MOTION_TICKS;
                var tag = living.getPersistentData();
                // Release Last Stand's zero-motion controller before launch. The surrounding stun
                // loop is also disabled after this beat, so gravity and the outward shove remain
                // authoritative after the eight-tick pressure-carry window ends.
                tag.remove("HexHakiLastStandStunUntil");

                // Damage is hostile-only and fixed at forty hearts. Friendly-tagged/allied creatures
                // are a hard exclusion even if a mod reports them as monsters or they were hostile earlier.
                boolean hostile = !isLastStandFriendly(player, living)
                        && (tag.getBoolean(LAST_STAND_HOSTILE) || isLastStandHostile(player, living));
                tag.remove(LAST_STAND_HOSTILE);
                if (hostile) {
                    // The general Last Stand input/attack lock intentionally rejects every normal
                    // attack from the kneeling caster. Mark ONLY this synchronous passive hit as
                    // authored damage so LivingAttackEvent does not cancel our own detonation.
                    // The flag is removed in finally before any other server action can run.
                    player.getPersistentData().putBoolean(LAST_STAND_SCRIPTED_DAMAGE, true);
                    try {
                        // Final Stand is a one-shot detonation and must not silently disappear behind
                        // vanilla hurt-resistance frames left by some other recent hit. Reset only the
                        // short damage i-frame before applying the armor-piercing Haoshoku pressure.
                        // Friendly/allied entities never enter this branch.
                        living.invulnerableTime = 0;
                        boolean damaged = living.hurt(player.damageSources().sonicBoom(player),
                                LAST_STAND_FINAL_BLAST_DAMAGE);
                        if (!damaged && living.isAlive() && !living.isInvulnerable()) {
                            // A few modded entities reject SONIC_BOOM specifically even though they are
                            // otherwise damageable. Fall back to a normal player-attributed hit so the
                            // blast still deals damage instead of becoming a visual-only shove.
                            living.invulnerableTime = 0;
                            living.hurt(player.damageSources().playerAttack(player),
                                    LAST_STAND_FINAL_BLAST_DAMAGE);
                        }
                    } finally {
                        player.getPersistentData().remove(LAST_STAND_SCRIPTED_DAMAGE);
                    }
                }

                tag.putLong("HexHakiConquerorBlastMotionStart", now);
                tag.putLong("HexHakiConquerorBlastMotionUntil", motionUntil);
                tag.putDouble("HexHakiConquerorBlastDirX", away.x);
                tag.putDouble("HexHakiConquerorBlastDirZ", away.z);
                tag.putDouble("HexHakiConquerorBlastForce", force);
                tag.putDouble("HexHakiConquerorBlastLift", lift);
                trackConquerorBlast(living);
                living.hurtMarked = true;
                if (living instanceof ServerPlayer launchedPlayer) {
                    launchedPlayer.connection.send(new ClientboundSetEntityMotionPacket(launchedPlayer));
                }
            }
        }
    }

    /**
     * Friendly protection for Last Stand. /tag <entity> add friendly is the canonical opt-out;
     * the HexHaki-specific aliases and normal allied/team ownership checks are honored too.
     */
    private static boolean isLastStandFriendly(ServerPlayer player, LivingEntity target) {
        if (target == player || player.isAlliedTo(target) || target.isAlliedTo(player)) return true;
        for (String tag : target.getTags()) {
            if (tag.equalsIgnoreCase("friendly")
                    || tag.equalsIgnoreCase("haki_friendly")
                    || tag.equalsIgnoreCase("hexhaki_friendly")) return true;
        }
        return false;
    }

    /**
     * Hostility detection deliberately goes beyond vanilla Enemy so modded monsters are caught:
     * monster-category entities, Enemy implementations, and mobs actively targeting the caster.
     */
    private static boolean isLastStandHostile(ServerPlayer player, LivingEntity target) {
        if (isLastStandFriendly(player, target)) return false;
        if (target instanceof Enemy) return true;
        if (target.getType().getCategory() == MobCategory.MONSTER) return true;
        return target instanceof Mob mob && mob.getTarget() == player;
    }

    private static void beginLastStand(ServerPlayer player) {
        long now = player.level().getGameTime();
        // Last Stand is a helpless kneeling state, not a free fourteen-second casting window.
        // Tear down any charge/channel/toggle/cinematic state first; onAction() separately rejects
        // every new Haki input until the passive finishes.
        HakiServerController.cancelAll(player);
        HakiData.longValue(player, LAST_STAND_UNTIL, now + LAST_STAND_DURATION_TICKS);
        // The global /haki cooldowns off test switch applies to this passive exactly like every
        // active technique. Do not leave a hidden 20-minute timer behind while test mode is active.
        HakiData.longValue(player, LAST_STAND_COOLDOWN_UNTIL,
                HakiServerController.noCooldowns(player) ? 0L : now + LAST_STAND_COOLDOWN_TICKS);
        HakiData.root(player).putDouble(LAST_STAND_X, player.getX());
        HakiData.root(player).putDouble(LAST_STAND_Y, player.getY());
        HakiData.root(player).putDouble(LAST_STAND_Z, player.getZ());
        HakiData.longValue(player, LAST_STAND_NEXT_BURST, now + 4L);
        HakiData.flag(player, LAST_STAND_FINAL_BLAST_DONE, false);
        player.setHealth(Math.max(1.0F, player.getHealth()));
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0f;
        HakiServerController.animate(player, "conqueror_last_stand", 1);
        // Spoken so everyone nearby reads the moment, not just the player kneeling in it.
        player.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("<" + player.getGameProfile().getName() + "> Well this is it for me..."), false);
        HakiServerController.visual(player, S2CHakiVisual.Visual.LAST_STAND_AURA, 1f,
                LAST_STAND_DURATION_TICKS);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.CONQUEROR_SUPREME_RUMBLE.get(),
                SoundSource.PLAYERS, 1.75f, .68f);
    }

    /** Post-mitigation lethal check. Armor, Resistance and Haki reduction all resolve first. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void lethalLastStand(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;
        if (!HakiData.enabled(player) || HakiData.mastery(player, HakiType.CONQUEROR) < 1000) return;
        if (HakiData.flag(player, LAST_STAND_FINAL_DEATH)) return;

        long now = player.level().getGameTime();
        if (HakiData.longValue(player, LAST_STAND_UNTIL) > now) {
            e.setAmount(0f);
            return;
        }
        if (!HakiServerController.noCooldowns(player)
                && HakiData.longValue(player, LAST_STAND_COOLDOWN_UNTIL) > now) return;
        if (e.getAmount() < player.getHealth()) return;

        e.setAmount(0f);
        beginLastStand(player);
    }

    /** Absolute MAX-Conqueror stun enforcement, including players, bosses and modded mobs. */
    @SubscribeEvent
    public static void forcedConquerorStun(LivingEvent.LivingTickEvent e) {
        if (e.getEntity().level().isClientSide) return;
        var living = e.getEntity();
        var tag = living.getPersistentData();
        long now = living.level().getGameTime();

        // V2 King's Grip: a real server-authoritative cinematic stun. The attacker controller
        // pins the exact grab pose; this universal tick guard prevents AI/player motion from
        // fighting it between controller ticks.
        long gripUntil = tag.getLong("HexHakiGripUntil");
        if (gripUntil > now) {
            living.setDeltaMovement(Vec3.ZERO);
            living.hurtMarked = true;
            living.fallDistance = 0;
            if (living instanceof Player player) player.setSprinting(false);
            if (living instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
        } else if (gripUntil > 0L) {
            tag.remove("HexHakiGripUntil");
            tag.remove("HexHakiGripOwner");
        }

        long blastMotionUntil = tag.getLong("HexHakiConquerorBlastMotionUntil");
        boolean blasting = blastMotionUntil > now;
        long lastStandStunUntil = tag.getLong("HexHakiLastStandStunUntil");
        if (lastStandStunUntil > now) {
            if (!blasting) living.setDeltaMovement(Vec3.ZERO);
            living.hurtMarked = true;
            living.fallDistance = 0f;
            if (living instanceof Player player) player.setSprinting(false);
            if (living instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            // Player bodies twitch through the authored knockdown animation. Mobs violently thrash
            // in place: rapid opposing body/head snaps plus sharp pitch jolts, without gaining any
            // movement that could let them escape the stun or fight the final blast.
            if (!(living instanceof Player) && !blasting && (now + living.getId()) % 2L == 0L) {
                float direction = living.getRandom().nextBoolean() ? 1.0f : -1.0f;
                float bodyJolt = direction * (11.0f + living.getRandom().nextFloat() * 16.0f);
                float headJolt = -direction * (18.0f + living.getRandom().nextFloat() * 20.0f);
                float pitchJolt = (living.getRandom().nextFloat() - .5f) * 26.0f;
                float bodyYaw = living.getYRot() + bodyJolt;
                living.setYRot(bodyYaw);
                living.setYBodyRot(bodyYaw);
                living.setYHeadRot(living.getYHeadRot() + headJolt);
                living.setXRot(Math.max(-70.0f, Math.min(70.0f, living.getXRot() + pitchJolt)));
                living.hurtMarked = true;
            }
        } else if (lastStandStunUntil > 0L) {
            tag.remove("HexHakiLastStandStunUntil");
            tag.remove(LAST_STAND_HOSTILE);
        }

        long until = tag.getLong("HexHakiForcedConquerorStunUntil");

        // IMPORTANT: blast propulsion is independent from the current MAX-release stun field.
        // Every valid combat target in the full MAX/Joy Boy release radius receives these tags. The
        // actual velocity authority runs at LevelTick END so AI/boss logic cannot overwrite it.
        if (blasting) trackConquerorBlast(living);

        if (until > now) {
            if (!blasting) {
                // Once the explosion has finished physically throwing the target, restore the
                // hard stun. Horizontal self-movement is killed, but an airborne victim is still
                // allowed to descend instead of being frozen unnaturally in mid-air.
                Vec3 motion = living.getDeltaMovement();
                double vertical = living.onGround() ? 0.0 : Math.min(motion.y, -0.08);
                living.setDeltaMovement(0.0, vertical, 0.0);
            }
            living.hurtMarked = true;
            living.fallDistance = 0;
            if (living instanceof Player player) player.setSprinting(false);
            if (living instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
                // Stun control never touches NoAI. Navigation/attacks are suppressed directly,
                // which guarantees the mob returns to normal as soon as the 200-tick timer ends.
            }
        } else {
            // Recover any mobs that were stunned by 1.0.9/1.0.10 before this fix. New stuns never
            // modify NoAI, but legacy saves may still carry the exact pre-stun state we must restore.
            if (tag.getBoolean("HexHakiForcedConquerorStoredNoAi") && living instanceof Mob mob) {
                mob.setNoAi(tag.getBoolean("HexHakiForcedConquerorOldNoAi"));
            }
            tag.remove("HexHakiForcedConquerorStoredNoAi");
            tag.remove("HexHakiForcedConquerorOldNoAi");
            if (until > 0L) tag.remove("HexHakiForcedConquerorStunUntil");
        }

        if (blastMotionUntil > 0L && blastMotionUntil <= now) {
            clearConquerorBlastMotion(tag);
        }
    }

    private static void enforceConquerorBlastMotion(LivingEntity living, net.minecraft.nbt.CompoundTag tag, long now, long blastMotionUntil) {
        // MAX/Joy Boy release gets a short authoritative pressure shove.
        // Reassert a quickly decaying outward speed for only a few ticks so resistant/modded mobs
        // still move without turning the blast into a multi-second rocket launch.
        long blastStart = tag.getLong("HexHakiConquerorBlastMotionStart");
        double total = Math.max(1.0, blastMotionUntil - blastStart);
        double remaining = Math.max(0.0, Math.min(1.0, (blastMotionUntil - now) / total));
        double dirX = tag.getDouble("HexHakiConquerorBlastDirX");
        double dirZ = tag.getDouble("HexHakiConquerorBlastDirZ");
        double dirLen = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (dirLen < 1.0E-6) { dirX = 1.0; dirZ = 0.0; dirLen = 1.0; }
        dirX /= dirLen;
        dirZ /= dirLen;

        double initialForce = Math.max(0.30, tag.getDouble("HexHakiConquerorBlastForce"));
        double initialLift = Math.max(0.12, tag.getDouble("HexHakiConquerorBlastLift"));
        // Strong initial launch, then a long pressure carry. Even at the tail of the
        // window the victim is still moving distinctly away from the caster.
        double desiredSpeed = initialForce * (0.10 + 0.90 * Math.pow(remaining, 1.15));
        Vec3 motion = living.getDeltaMovement();
        double along = motion.x * dirX + motion.z * dirZ;
        double outX = motion.x;
        double outZ = motion.z;
        if (along < desiredSpeed) {
            outX = dirX * desiredSpeed;
            outZ = dirZ * desiredSpeed;
        }
        double verticalFloor = initialLift * (0.12 + 0.88 * remaining);
        double outY = remaining > 0.52 ? Math.max(motion.y, verticalFloor) : motion.y;
        living.setDeltaMovement(outX, outY, outZ);
        living.hurtMarked = true;

        // Non-player entities also receive a small collision-aware positional carry at the END
        // of the level tick. This is intentionally independent of knockback resistance and makes
        // even bosses/modded mobs whose AI overwrites delta movement visibly move with the blast.
        if (!(living instanceof ServerPlayer)) {
            double carry = Math.min(0.16, desiredSpeed * 0.08);
            double carryY = living.onGround() && remaining > 0.65 ? Math.min(0.06, verticalFloor * 0.08) : 0.0;
            living.move(MoverType.SELF, new Vec3(dirX * carry, carryY, dirZ * carry));
        }

        if (living instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }

        // Force the authoritative motion onto the victim's own client. This makes PvP launches
        // reliable instead of letting the next client movement packet visually swallow the push.
        if (living instanceof ServerPlayer launchedPlayer) {
            launchedPlayer.connection.send(new ClientboundSetEntityMotionPacket(launchedPlayer));
        }
    }

    private static void clearConquerorBlastMotion(net.minecraft.nbt.CompoundTag tag) {
        tag.remove("HexHakiConquerorBlastMotionStart");
        tag.remove("HexHakiConquerorBlastMotionUntil");
        tag.remove("HexHakiConquerorBlastDirX");
        tag.remove("HexHakiConquerorBlastDirZ");
        tag.remove("HexHakiConquerorBlastForce");
        tag.remove("HexHakiConquerorBlastLift");
    }

    private static boolean hasAbsoluteObservationDodge(ServerPlayer player) {
        return HakiData.enabled(player)
                && HakiData.flag(player, "observationOn")
                && HakiData.mastery(player, HakiType.OBSERVATION) >= 1000;
    }

    /**
     * Final safety net for modded damage paths that make it past LivingAttackEvent/LivingHurtEvent.
     * At 1000 OBS, active Observation is an absolute dodge: any Forge-routed damage is zeroed before
     * the Last Stand lethal check can see it. Energy drain remains the limiter on this state.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void absoluteObservationDamage(LivingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer player && hasAbsoluteObservationDodge(player)) {
            e.setAmount(0.0F);
        }
    }

    /**
     * Observation/Visual Haki is absolute projectile avoidance while active. This runs before a
     * projectile can execute its normal entity-hit behavior, so arrows, tridents, fireballs,
     * thrown potions and ordinary/modded Projectile subclasses cannot actually connect with the
     * player. Forge is told to skip that entity entirely, so the projectile continues flying
     * through the avoided collision instead of sticking/exploding on the player.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void observationProjectileImpact(ProjectileImpactEvent e) {
        if (!(e.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof ServerPlayer player)) return;
        if (!HakiData.enabled(player) || !HakiData.flag(player, "observationOn")) return;

        Projectile projectile = e.getProjectile();
        if (projectile.level().isClientSide) return;

        // Forge 1.20.1 provides SKIP_ENTITY specifically for this behavior: the projectile
        // treats the Visual-Haki user as though their collision box was not there and continues
        // flying normally. No damage, no sticking, no bouncing off the hitbox.
        e.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);

        int mastery = HakiData.mastery(player, HakiType.OBSERVATION);
        long now = player.level().getGameTime();
        if (now - HakiData.longValue(player, "lastProjectileReactCue") < 3L) return;
        HakiData.longValue(player, "lastProjectileReactCue", now);

        // A shot passing harmlessly through the player looked like nothing happened. It should
        // read as a read: either it gets plucked out of the air and sent back, or the body slips it.
        if (mastery >= HakiUnlocks.OBSERVATION_ARROW_CATCH
                && player.getRandom().nextFloat() < masteryChance(mastery,
                        HakiUnlocks.OBSERVATION_ARROW_CATCH,
                        ARROW_CATCH_CHANCE_MIN, ARROW_CATCH_CHANCE_MAX)
                && catchAndReturnProjectile(player, projectile, mastery)) {
            return;
        }
        reactiveDodge(player, projectile.position(), mastery, "projectile_slip");
    }

    /**
     * Plucks an incoming projectile out of the air and throws it at the nearest hostile.
     *
     * <p>The projectile itself is reused rather than replaced: re-owning it and re-aiming its
     * velocity keeps whatever it actually is (arrow, trident, modded shot) along with its damage
     * and enchantments, which spawning a fresh arrow would throw away.
     *
     * <p>It is not thrown straight back. The shot is parked in the hand for
     * {@link #ARROW_CATCH_HOLD_TICKS} ticks at one fixed grip angle, and re-pinned every one of
     * them, so the player can turn or be knocked around while they read it and the arrow stays
     * where their hand is.
     *
     * @param mastery Observation mastery, which decides how hard the shot is sent back
     * @return false when there is nothing worth throwing it at, so the caller can fall back to a dodge
     */
    private static boolean catchAndReturnProjectile(ServerPlayer player, Projectile projectile,
                                                    int mastery) {
        if (nearestReturnTarget(player, projectile) == null) return false;

        // Take it out of flight and park it in the hand. Gravity has to go as well as its speed:
        // an arrow with no motion still falls, and a falling arrow reads as dropped, not caught.
        projectile.setOwner(player);
        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.setNoGravity(true);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            // Hide the world-space projectile while it is held. A client render layer draws this
            // same arrow directly in the live animated right-hand bone instead, so walking, turning
            // and PlayerAnimator elbow/body motion cannot leave the shaft floating beside the fist.
            arrow.setInvisible(true);
            // Crit only on the way back out. AbstractArrow spawns a CRIT particle every tick it is
            // marked crit, so setting it at catch time buried the held shaft in a cloud of them --
            // which is all you could actually see of it in the hand.
            arrow.setCritArrow(false);
        }
        pinCaughtProjectile(player, projectile);

        HakiNetwork.tracking(player, new S2CAnimation(player.getId(), "observation_arrow_catch", 1));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, .8f, 1.55f);

        ServerLevel level = player.serverLevel();
        UUID ownerId = player.getUUID();
        int projectileId = projectile.getId();
        // The throw is as hard as the reader is good: a 620 catch sends it back at about the
        // speed a bow would, a 1000 one sends it out faster than it arrived.
        double returnSpeed = 3.1D + 1.9D * Math.min(1f, mastery / 1000f);

        // Re-pin every tick of the hold: the player can walk, turn or be knocked around while they
        // are reading it, and a shot left at its catch position would hang in the air behind them.
        for (int tick = 1; tick < ARROW_CATCH_HOLD_TICKS; tick++) {
            ServerTimeline.later(level, tick, () -> {
                ServerPlayer holder = level.getServer().getPlayerList().getPlayer(ownerId);
                if (holder == null || !holder.isAlive()) return;
                if (level.getEntity(projectileId) instanceof Projectile held && held.isAlive()) {
                    pinCaughtProjectile(holder, held);
                }
            });
        }

        ServerTimeline.later(level, ARROW_CATCH_HOLD_TICKS, () -> {
            ServerPlayer thrower = level.getServer().getPlayerList().getPlayer(ownerId);
            if (!(level.getEntity(projectileId) instanceof Projectile held) || !held.isAlive()) return;
            held.setNoGravity(false);
            // The real projectile becomes visible again exactly when it leaves the hand. Do this
            // before validating the thrower as well, so a disconnect/death cannot strand an
            // invisible arrow in the world.
            if (held instanceof AbstractArrow arrow) arrow.setInvisible(false);
            if (thrower == null || !thrower.isAlive()) return;

            // The target is chosen again here rather than at catch time: a second and a half is
            // long enough for the original to die, leave, or be overtaken by something closer.
            LivingEntity mark = nearestReturnTarget(thrower, held);
            Vec3 from = thrower.getEyePosition();
            Vec3 aim = mark != null
                    ? mark.getBoundingBox().getCenter().subtract(from)
                    : thrower.getLookAngle();
            if (aim.lengthSqr() < 1.0E-6D) aim = thrower.getLookAngle();
            aim = aim.normalize();

            held.setPos(from.x + aim.x * .6D, from.y + aim.y * .6D - .1D, from.z + aim.z * .6D);
            held.setOwner(thrower);
            held.setDeltaMovement(aim.scale(returnSpeed));
            held.hasImpulse = true;
            if (held instanceof AbstractArrow arrow) arrow.setCritArrow(true);

            HakiServerController.visual(thrower, S2CHakiVisual.Visual.OBSERVATION_PULSE, 1f,
                    mark != null ? mark.getId() : thrower.getId());
            level.playSound(null, thrower.blockPosition(), SoundEvents.ARROW_SHOOT,
                    SoundSource.PLAYERS, .9f, 1.25f);
            HakiProgression.award(thrower, HakiType.OBSERVATION, 3, "arrow_catch", 20);
        });
        return true;
    }

    /**
     * Holds a caught shot inside the throwing hand, at one fixed angle every time.
     *
     * <p>Keeping the direction it flew in on was the wrong call and it is dropped. That angle is
     * different for every shot -- from above, from the side, at a steep arc -- so the catch never
     * looked the same twice, and roughly half the time the shaft was pointed at or away from the
     * viewer and collapsed into a few pixels. The grip is now a fixed pose: forward, down and
     * slightly across the body, which shows the whole length from every angle a fight is watched
     * from.
     *
     * <p>The orientation is set by giving the arrow a hair of velocity along the hold direction
     * rather than by writing {@code xRot} and {@code yRot}. {@code AbstractArrow#tick} recomputes
     * its own rotation from its motion every tick and lerps toward the result, so a written angle
     * is dragged back out within a few ticks -- but an arrow always renders along the way it is
     * travelling, so choosing the velocity chooses the picture, and it holds.
     *
     * <p>The position offsets are the model's, not estimates. The right arm pivots 5 texels out and
     * 2 below the body top -- 0.3125 out and 1.375 up on a 1.8-high player -- and the limb is 12
     * texels, so the straight-arm catch pose puts the hand 0.73 ahead of that pivot. The origin is
     * then pushed along the shaft, because an arrow's entity origin sits near its head and the
     * model trails behind it: without that, the hand grips empty air and the shaft hangs off it.
     */
    private static void pinCaughtProjectile(ServerPlayer player, Projectile projectile) {
        float yaw = player.getYRot() * ((float) Math.PI / 180f);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        // Scaled off the real height so a shrunken or grown model still grips it.
        double scale = player.getBbHeight() / 1.8D;
        Vec3 hand = player.position()
                .add(forward.scale(.73D * scale))
                .add(right.scale(.3125D * scale))
                .add(0, 1.40D * scale, 0);

        // The fixed grip: tip forward-down and a little across the body.
        Vec3 grip = forward.scale(.62D).add(right.scale(-.26D)).add(0, -.74D, 0).normalize();

        projectile.setPos(hand.x + grip.x * .23D, hand.y + grip.y * .23D, hand.z + grip.z * .23D);
        projectile.setDeltaMovement(grip.scale(.0018D));
        projectile.hasImpulse = true;
    }

    /** Nearest hostile worth returning a shot to, falling back to whoever fired it. */
    private static LivingEntity nearestReturnTarget(ServerPlayer player, Projectile projectile) {
        LivingEntity mark = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(ARROW_RETURN_RANGE),
                living -> living != player && living.isAlive() && living instanceof Enemy)) {
            double distance = candidate.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                mark = candidate;
            }
        }
        if (mark == null && projectile.getOwner() instanceof LivingEntity shooter
                && shooter != player && shooter.isAlive()) {
            mark = shooter;
        }
        return mark;
    }

    /**
     * Ramps a proc chance from its value at the ability's unlock to its value at Observation 1000.
     *
     * <p>Both reactions were flat rolls, which made mastery past their unlock worth nothing to
     * them: a 620 reader and a 1000 reader caught the same share of shots. The floor is what the
     * ability is worth the moment it arrives; the ceiling is what perfect Observation is worth.
     */
    private static float masteryChance(int mastery, int unlock, float atUnlock, float atThousand) {
        int span = Math.max(1, 1000 - unlock);
        float t = Math.max(0f, Math.min(1f, (mastery - unlock) / (float) span));
        return atUnlock + (atThousand - atUnlock) * t;
    }

    /** Shared slip used by both the melee and projectile reactions: animate, displace, credit. */
    private static void reactiveDodge(ServerPlayer player, Vec3 source, int mastery, String reason) {
        Vec3 incoming = player.position().subtract(source);
        if (incoming.lengthSqr() < 1.0E-6D) incoming = player.getLookAngle();
        incoming = new Vec3(incoming.x, 0, incoming.z);
        if (incoming.lengthSqr() < 1.0E-6D) incoming = new Vec3(0, 0, 1);
        incoming = incoming.normalize();
        Vec3 side = new Vec3(-incoming.z, 0, incoming.x)
                .scale(player.getRandom().nextBoolean() ? .34D : -.34D);
        player.setDeltaMovement(player.getDeltaMovement().add(side));
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        HakiNetwork.tracking(player, new S2CAnimation(player.getId(),
                dodgeClip(player, source, side, mastery), 1));
        HakiProgression.award(player, HakiType.OBSERVATION, 2, reason, 16);
    }

    private static boolean isProjectileDamage(net.minecraft.world.damagesource.DamageSource source) {
        return source.getDirectEntity() instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
    }

    /**
     * Extra impact layer for ordinary vanilla bare-fist attacks while Armament Haki is active.
     *
     * <p>AttackEntityEvent is used deliberately instead of the generic damage pipeline: scripted
     * Haki techniques also author player-attributed damage, and they keep their own bespoke audio.
     * This hook therefore follows only a real vanilla player attack with an empty main hand.
     */
    @SubscribeEvent
    public static void hakiBareFistPunch(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer attacker)) return;
        if (!(e.getTarget() instanceof LivingEntity target) || !target.isAlive()) return;
        if (!attacker.getMainHandItem().isEmpty()) return;
        if (!HakiData.enabled(attacker) || !HakiData.flag(attacker, "armamentOn")) return;

        attacker.serverLevel().playSound(null, target.blockPosition(), ModSounds.HAKI_PUNCH.get(),
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hurt(LivingHurtEvent e) {
        // Pinnacle Observation is not chance-based. If a modded hit skips LivingAttackEvent but
        // still enters Forge's hurt pipeline, 1000 OBS cancels it here as a second hard gate.
        if (e.getEntity() instanceof ServerPlayer pinnacle && hasAbsoluteObservationDodge(pinnacle)) {
            e.setCanceled(true);
            return;
        }
        // Second-line safeguard for modded projectile damage that bypasses vanilla projectile
        // impact handling. Visual Haki means projectile damage is zero, full stop.
        if (e.getEntity() instanceof ServerPlayer visualUser
                && HakiData.enabled(visualUser)
                && HakiData.flag(visualUser, "observationOn")
                && isProjectileDamage(e.getSource())) {
            e.setCanceled(true);
            return;
        }
        if (e.getSource().getEntity() instanceof ServerPlayer trainee && HakiData.enabled(trainee)) {
            // You can develop Armament before coating itself unlocks: real combat is the foundation.
            HakiProgression.award(trainee, HakiType.ARMAMENT, 1, "physical_hit", 6);
        }

        if(e.getSource().getEntity() instanceof ServerPlayer attacker && HakiData.enabled(attacker) && HakiData.flag(attacker,"armamentOn")) {
            int m=HakiData.mastery(attacker,HakiType.ARMAMENT);
            HakiRank rank=HakiRank.of(m);
            float bonus=rank.armamentDamage()+m/240f;
            boolean acoc=HakiData.flag(attacker,"acocOn");
            if(acoc) bonus += 3.0f + HakiData.mastery(attacker,HakiType.CONQUEROR)/180f;
            e.setAmount(e.getAmount()+bonus);
            Vec3 targetCenter=e.getEntity().getBoundingBox().getCenter();
            Vec3 attackDir=targetCenter.subtract(attacker.getEyePosition());
            if(attackDir.lengthSqr()<1.0E-6) attackDir=attacker.getLookAngle();
            else attackDir=attackDir.normalize();
            double targetRadius=Math.max(.22,Math.min(.78,Math.max(e.getEntity().getBbWidth(),e.getEntity().getBbHeight()*.35)*.48));
            Vec3 contact=targetCenter.subtract(attackDir.scale(targetRadius));
            HakiNetwork.tracking(attacker,new S2CArmamentImpact(
                    attacker.getId(),e.getEntity().getId(),HakiServerController.mastery01(attacker,HakiType.ARMAMENT),acoc,0,
                    contact.x,contact.y,contact.z,attackDir.x,attackDir.y,attackDir.z,attacker.getRandom().nextLong()));
            HakiProgression.award(attacker,HakiType.ARMAMENT,1,"coated_hit",12);
            if(acoc) HakiProgression.award(attacker,HakiType.CONQUEROR,1,"acoc_hit",18);
        }
        if(e.getEntity() instanceof Player victim && HakiData.enabled(victim) && HakiData.flag(victim,"armamentOn")) {
            HakiRank rank=HakiRank.of(HakiData.mastery(victim,HakiType.ARMAMENT));
            e.setAmount(e.getAmount()*(1f-rank.armamentReduction()));
            if(victim instanceof ServerPlayer sp) HakiProgression.award(sp,HakiType.ARMAMENT,1,"guard",30);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        Entity source = e.getSource().getEntity();
        if (!(source instanceof ServerPlayer player) || !HakiData.enabled(player)) return;
        int units = e.getEntity() instanceof Enemy ? 4 : 2;
        if (e.getEntity() instanceof EnderDragon || e.getEntity() instanceof WitherBoss) units = 12;
        HakiProgression.award(player, HakiType.ARMAMENT, units, "combat_kill", 2);

        // King's Haki grows from imposing King's Haki, not from passive survival farming.
        if (HakiProgression.conquerorAwakened(player)
                && (HakiData.flag(player, "acocOn") || e.getEntity().getPersistentData().getLong("HexHakiForcedConquerorStunUntil") > player.level().getGameTime())) {
            HakiProgression.award(player, HakiType.CONQUEROR, Math.max(1, units / 3), "king_kill", 10);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void danger(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer lastStandPlayer
                && HakiData.longValue(lastStandPlayer, LAST_STAND_UNTIL) > lastStandPlayer.level().getGameTime()
                && !HakiData.flag(lastStandPlayer, LAST_STAND_FINAL_DEATH)) {
            e.setCanceled(true);
            return;
        }
        // A target being physically launched by MAX Conqueror is still stunned. Prevent players
        // and mobs from attacking during the pressure carry even though mob NoAI is intentionally
        // left off until the launch finishes so physics can move them.
        Entity attacker = e.getSource().getEntity();
        if (attacker instanceof LivingEntity lastStandStunned
                && lastStandStunned.getPersistentData().getLong("HexHakiLastStandStunUntil") > lastStandStunned.level().getGameTime()) {
            e.setCanceled(true);
            return;
        }
        if (attacker instanceof ServerPlayer lastStandCaster
                && HakiData.longValue(lastStandCaster, LAST_STAND_UNTIL) > lastStandCaster.level().getGameTime()
                && !lastStandCaster.getPersistentData().getBoolean(LAST_STAND_SCRIPTED_DAMAGE)) {
            e.setCanceled(true);
            return;
        }
        // King's Grip normally suppresses ALL attacks from either cinematic participant. The one
        // exception is the server-authored gut-punch impact itself. That path raises this flag only
        // around its target.hurt(...) call and clears it in a finally block, so normal clicks/other
        // abilities remain impossible during the cutscene.
        boolean scriptedGripImpact = attacker instanceof LivingEntity livingAttacker
                && livingAttacker.getPersistentData().getBoolean("HexHakiAllowGripImpact");
        if (!scriptedGripImpact && attacker instanceof LivingEntity grabbedAttacker
                && (grabbedAttacker.getPersistentData().getLong("HexHakiGripUntil") > grabbedAttacker.level().getGameTime()
                || grabbedAttacker.getPersistentData().getLong("HexHakiGripAttackerUntil") > grabbedAttacker.level().getGameTime())) {
            e.setCanceled(true);
            return;
        }
        if (attacker instanceof LivingEntity stunnedAttacker
                && stunnedAttacker.getPersistentData().getLong("HexHakiForcedConquerorStunUntil") > stunnedAttacker.level().getGameTime()) {
            e.setCanceled(true);
            return;
        }
        if (e.getEntity() instanceof ServerPlayer visualUser
                && HakiData.enabled(visualUser)
                && HakiData.flag(visualUser, "observationOn")
                && isProjectileDamage(e.getSource())) {
            e.setCanceled(true);
            return;
        }
        if(!(e.getEntity() instanceof ServerPlayer p) || !HakiData.enabled(p) || !HakiData.flag(p,"observationOn")) return;
        int m=HakiData.mastery(p,HakiType.OBSERVATION);
        long now=p.level().getGameTime();
        if (m >= 1000) {
            // Max Observation means ABSOLUTE dodge, not a high random chance. Cancel every incoming
            // LivingAttackEvent while the state is powered, then play the existing authored dodge
            // feedback at a small visual/motion cadence so rapid multi-hit attacks cannot spam-jitter.
            e.setCanceled(true);
            HakiServerController.triggerFutureSightFromDanger(p);
            if (now-HakiData.longValue(p,"lastAbsoluteDodgeCue") >= 4L) {
                HakiData.longValue(p,"lastAbsoluteDodgeCue",now);
                Vec3 source=e.getSource().getSourcePosition(); Vec3 side;
                if(source!=null){Vec3 incoming=p.position().subtract(source); if(incoming.lengthSqr()>1.0E-6) incoming=incoming.normalize(); else incoming=p.getLookAngle(); side=new Vec3(-incoming.z,0,incoming.x).scale(p.getRandom().nextBoolean()?.28:-.28);}else side=new Vec3(.2,0,0);
                LivingEntity swinger=e.getSource().getEntity() instanceof LivingEntity le?le:null;
                if(m>=HakiUnlocks.OBSERVATION_COUNTER_FLIP && p.getRandom().nextFloat()<masteryChance(m,HakiUnlocks.OBSERVATION_COUNTER_FLIP,COUNTER_FLIP_CHANCE_MIN,COUNTER_FLIP_CHANCE_MAX)
                        && counterFlip(p,swinger,m)){
                    HakiProgression.award(p,HakiType.OBSERVATION,2,"absolute_dodge",12);
                    return;
                }
                p.setDeltaMovement(p.getDeltaMovement().add(side));
                HakiNetwork.tracking(p,new S2CAnimation(p.getId(),dodgeClip(p,source,side,m),1));
                HakiProgression.award(p,HakiType.OBSERVATION,2,"absolute_dodge",12);
            }
            return;
        }
        // Real damage intent is the universal fallback for modded attacks that cannot be predicted
        // from vanilla Mob target/path data. At 1000 OBS it can wake the short Future Sight burst.
        HakiServerController.triggerFutureSightFromDanger(p);
        if(now-HakiData.longValue(p,"lastDangerCue")>5) {
            HakiData.longValue(p,"lastDangerCue",now);
            HakiServerController.delayedSound(p, ModSounds.OBSERVATION_DANGER.get(), .55f, 1f+.08f*HakiRank.of(m).tier());
            int attackerId=e.getSource().getEntity()!=null?e.getSource().getEntity().getId():-1;
            HakiNetwork.to(p,new S2CHakiVisual(p.getId(),S2CHakiVisual.Visual.OBSERVATION_PULSE,.30f+.7f*(m/1000f),attackerId,p.getRandom().nextLong()));
            HakiProgression.award(p,HakiType.OBSERVATION,1,"danger_read",18);
        }
        if(m>=430 && (HakiServerController.noCooldowns(p) || now>=HakiData.longValue(p,"dodgeCooldown"))) {
            float chance=Math.min(.52f,.08f+(m-430)/1450f);
            if(p.getRandom().nextFloat()<chance) {
                e.setCanceled(true);
                HakiData.longValue(p,"dodgeCooldown",now+Math.max(5,16-HakiRank.of(m).tier()));
                Vec3 source=e.getSource().getSourcePosition(); Vec3 side;
                if(source!=null){Vec3 incoming=p.position().subtract(source).normalize();side=new Vec3(-incoming.z,0,incoming.x).scale(p.getRandom().nextBoolean()?.28:-.28);}else side=new Vec3(.2,0,0);
                LivingEntity swinger=e.getSource().getEntity() instanceof LivingEntity le?le:null;
                if(m>=HakiUnlocks.OBSERVATION_COUNTER_FLIP && p.getRandom().nextFloat()<masteryChance(m,HakiUnlocks.OBSERVATION_COUNTER_FLIP,COUNTER_FLIP_CHANCE_MIN,COUNTER_FLIP_CHANCE_MAX)
                        && counterFlip(p,swinger,m)){
                    HakiProgression.award(p,HakiType.OBSERVATION,2,"successful_dodge",18);
                    return;
                }
                p.setDeltaMovement(p.getDeltaMovement().add(side));
                HakiNetwork.tracking(p,new S2CAnimation(p.getId(),dodgeClip(p,source,side,m),1));
                HakiProgression.award(p,HakiType.OBSERVATION,2,"successful_dodge",18);
            }
        }
    }

    /**
     * Picks a melee dodge clip from the geometry of the incoming hit rather than a coin flip.
     *
     * <p>A strike coming in above shoulder height is ducked; anything else is slipped to the side
     * the body is actually moving toward, so the animation and the displacement agree. The
     * lean-away is reserved for the projectile path, where "no room either side" is a real state
     * the solver can prove; a melee swing always has somewhere to go.
     */
    private static String dodgeClip(ServerPlayer player, Vec3 source, Vec3 sidestep, int mastery) {
        boolean advancedReads = mastery >= HakiUnlocks.OBSERVATION_PROJECTILE_FORECAST;
        if (advancedReads && source != null
                && source.y >= player.getY() + player.getBbHeight() * .72) {
            return "observation_dodge_duck";
        }
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        return sidestep.dot(right) < 0 ? "observation_dodge_left" : "observation_dodge_right";
    }

    /**
     * Backflip off a read swing and dash straight back in behind a punch.
     *
     * <p>The flip and the counter are one continuous beat, so the displacement is scripted in two
     * halves against the animation rather than applied at once: an immediate hop back and up while
     * the body is inverted, then a landing, a cocked fist, and a charge onto the attacker behind
     * the extended arm. The charge and the strike are separate scheduled beats -- six ticks apart
     * -- so the counter visibly covers ground instead of arriving and connecting on one tick.
     * Re-resolving the attacker at each beat (instead of capturing a position) means the charge
     * still lands if they moved during the flip, and cleanly does nothing if they died.
     *
     * @return true when the counter was committed, so the caller skips the ordinary slip
     */
    private static boolean counterFlip(ServerPlayer player, LivingEntity attacker, int mastery) {
        if (attacker == null || !attacker.isAlive()) return false;
        Vec3 toAttacker = attacker.position().subtract(player.position());
        Vec3 flat = new Vec3(toAttacker.x, 0, toAttacker.z);
        if (flat.lengthSqr() < 1.0E-6D) return false;
        Vec3 facing = flat.normalize();

        // Launch: high and a long way back, so the flip clears the swing rather than hopping out
        // of it, but tightened from 0.82 so the whole counter reads quicker. 0.66 upward peaks at
        // about 2.8 blocks and gives roughly sixteen ticks of airtime, which is what the clip's
        // full rotation (finished by tick 13) and its landing on tick 16 are cut against.
        player.setDeltaMovement(-facing.x * 1.05D, .66D, -facing.z * 1.05D);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        float yaw = (float) (Math.toDegrees(Math.atan2(-facing.x, facing.z)));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);

        HakiNetwork.tracking(player, new S2CAnimation(player.getId(), "observation_backflip_counter", 1));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, .7f, 1.35f);

        ServerLevel level = player.serverLevel();
        UUID ownerId = player.getUUID();
        UUID markId = attacker.getUUID();
        float damage = 6.0f + 10.0f * Math.min(1f, mastery / 1000f);

        // The charge and the strike are separate beats now. The old single event dashed and dealt
        // damage on the same tick, which is why the counter had no travel: the player arrived and
        // connected simultaneously. The fist is already extended when the charge fires, and the
        // six ticks between the two are the distance being closed behind it.
        ServerTimeline.later(level, COUNTER_FLIP_DASH_TICK, () -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(ownerId);
            if (p == null || !p.isAlive()) return;
            if (!(level.getEntity(markId) instanceof LivingEntity mark) || !mark.isAlive()) return;
            Vec3 lunge = mark.position().subtract(p.position());
            Vec3 lungeFlat = new Vec3(lunge.x, 0, lunge.z);
            if (lungeFlat.lengthSqr() < 1.0E-6D) return;
            double distance = lungeFlat.length();
            if (distance > COUNTER_FLIP_RANGE) return;
            Vec3 dir = lungeFlat.normalize();

            // Cover the gap in the six ticks before contact, stopping just short rather than
            // teleporting on top of them.
            double gap = Math.max(0.0D, distance - 1.35D);
            double speed = Math.min(2.6D, .55D + gap * .30D);
            p.setDeltaMovement(dir.x * speed, .16D, dir.z * speed);
            p.hurtMarked = true;
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
            float chargeYaw = (float) (Math.toDegrees(Math.atan2(-dir.x, dir.z)));
            p.setYRot(chargeYaw);
            p.setYHeadRot(chargeYaw);
            level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, .85f, .92f);
        });

        ServerTimeline.later(level, COUNTER_FLIP_STRIKE_TICK, () -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(ownerId);
            if (p == null || !p.isAlive()) return;
            if (!(level.getEntity(markId) instanceof LivingEntity mark) || !mark.isAlive()) return;
            Vec3 lunge = mark.position().subtract(p.position());
            Vec3 lungeFlat = new Vec3(lunge.x, 0, lunge.z);
            if (lungeFlat.lengthSqr() < 1.0E-6D) return;
            if (lungeFlat.length() > COUNTER_FLIP_RANGE) return;
            Vec3 dir = lungeFlat.normalize();

            float strikeYaw = (float) (Math.toDegrees(Math.atan2(-dir.x, dir.z)));
            p.setYRot(strikeYaw);
            p.setYHeadRot(strikeYaw);

            mark.invulnerableTime = 0;
            mark.hurt(p.damageSources().playerAttack(p), damage);
            mark.setDeltaMovement(dir.x * 1.45D, .48D, dir.z * 1.45D);
            mark.hurtMarked = true;
            if (mark instanceof ServerPlayer hitPlayer) {
                hitPlayer.connection.send(new ClientboundSetEntityMotionPacket(hitPlayer));
            }

            Vec3 contact = mark.getBoundingBox().getCenter();
            HakiNetwork.tracking(p, new S2CArmamentImpact(
                    p.getId(), mark.getId(), .85f, HakiData.flag(p, "acocOn"), 0,
                    contact.x, contact.y, contact.z, dir.x, 0, dir.z, p.getRandom().nextLong()));
            level.playSound(null, mark.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 1.15f, .82f);
            if (HakiData.enabled(p) && HakiData.flag(p, "armamentOn")) {
                level.playSound(null, mark.blockPosition(), ModSounds.HAKI_PUNCH.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            HakiProgression.award(p, HakiType.OBSERVATION, 4, "counter_flip", 40);
        });
        return true;
    }

    /**
     * Haki Grip participants do not pay for a drop the server arranged.
     *
     * <p>The sequence teleports the attacker twenty-odd blocks up and drives the victim down from
     * the same height. Zeroing {@code fallDistance} covers the scripted ticks, but control is
     * handed back while the attacker is still airborne, so vanilla starts counting again from
     * wherever they were left. HakiServerController stamps a window on both of them; this is where
     * it is honoured.
     */
    @SubscribeEvent
    public static void fall(LivingFallEvent e) {
        var tag = e.getEntity().getPersistentData();
        if (!tag.contains("HexHakiNoFallUntil")) return;
        long until = tag.getLong("HexHakiNoFallUntil");
        if (e.getEntity().level().getGameTime() > until) {
            tag.remove("HexHakiNoFallUntil");
            return;
        }
        e.setDistance(0f);
        e.setDamageMultiplier(0f);
        e.setCanceled(true);
    }

    /**
     * Survival gathering now contributes a deliberately small amount of practical Haki training.
     * Repeated block spam is throttled per activity so mining/woodcutting supplement combat and
     * traversal progression instead of replacing them.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void blockBreakProgression(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) return;
        if (!HakiData.enabled(player)) return;

        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;

        // Gathering XP requires actually using a tool that is effective on the block. This blocks
        // hand-breaking / wrong-tool spam while still supporting modded pickaxes and axes.
        if (tool.getDestroySpeed(e.getState()) > 1.0f) {
            boolean woodcutting = e.getState().is(BlockTags.LOGS);
            boolean mining = e.getState().is(BlockTags.MINEABLE_WITH_PICKAXE);
            if (woodcutting || mining) {
                String source = woodcutting ? "woodcutting" : "mining";
                // One small training unit every few seconds at most: useful during normal survival,
                // intentionally far slower than dedicated Haki combat/technique training.
                int cooldown = woodcutting ? 100 : 80;
                HakiProgression.award(player, HakiType.ARMAMENT, 1, source, cooldown);
                HakiProgression.award(player, HakiType.OBSERVATION, 1, source, cooldown);
            }
        }

        // Active Armament reinforces any damageable tool used to break a block, not just axes and
        // pickaxes. Check one tick later, after vanilla/Unbreaking has decided whether durability
        // was actually consumed, so Haki never accidentally repairs a tool when Unbreaking procs.
        if (HakiData.flag(player, "armamentOn") && tool.isDamageableItem()) {
            float t = Math.max(0f, Math.min(1f, HakiData.mastery(player, HakiType.ARMAMENT) / 1000f));
            float preserveChance = 0.10f + 0.45f * t; // ~12% at coating unlock -> 55% at mastery 1000.
            if (player.getRandom().nextFloat() < preserveChance) {
                int damageBefore = tool.getDamageValue();
                ServerTimeline.later(player.serverLevel(), 1, () -> {
                    ItemStack current = player.getMainHandItem();
                    if (current != tool || !current.isDamageableItem()) return;
                    int damageNow = current.getDamageValue();
                    if (damageNow > damageBefore) current.setDamageValue(Math.max(damageBefore, damageNow - 1));
                });
            }
        }
    }

    /**
     * Active Armament accelerates block breaking with any held tool. The multiplier scales gently
     * with mastery so early coating is noticeable while mastery 1000 tops out at exactly 2x speed.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hakiToolBreakSpeed(PlayerEvent.BreakSpeed e) {
        Player player = e.getEntity();
        if (!HakiData.enabled(player) || !HakiData.flag(player, "armamentOn")) return;
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;

        int mastery = HakiData.mastery(player, HakiType.ARMAMENT);
        float t = Math.max(0f, Math.min(1f, mastery / 1000f));
        float multiplier = 1.20f + 0.80f * t;
        e.setNewSpeed(e.getNewSpeed() * multiplier);
    }

    /** Every completed food item restores 50 Haki energy, capped at mastery-scaled capacity. */
    @SubscribeEvent
    public static void finishEating(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || !e.getItem().isEdible()) return;
        HakiData.energy(player, HakiData.energy(player) + 50f);
        HakiServerController.sync(player);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer sp){HakiData.initialize(sp);if(!HakiData.enabled(sp))HakiServerController.cancelAll(sp);HakiServerController.sync(sp);}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer sp)HakiServerController.sync(sp);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        if(!e.isWasDeath())return;
        var original=e.getOriginal().getPersistentData();
        var target=e.getEntity().getPersistentData();
        if(original.contains("HexHaki")) target.put("HexHaki",original.getCompound("HexHaki").copy());
        else if(original.contains("GrandLineHaki")) target.put("HexHaki",original.getCompound("GrandLineHaki").copy());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer sp)HakiServerController.cancelAll(sp);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer sp){HakiServerController.cancelAll(sp);HakiServerController.sync(sp);}}
}
