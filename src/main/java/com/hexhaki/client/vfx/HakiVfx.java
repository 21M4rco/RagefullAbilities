package com.hexhaki.client.vfx;

import com.hexhaki.HexHaki;
import com.hexhaki.data.ConquerorMode;
import com.hexhaki.gameplay.ConquerorLightningPath;
import com.hexhaki.network.msg.S2CHakiVisual;
import com.hexhaki.network.msg.S2CWorldFx;
import com.hexhaki.network.msg.S2CArmamentImpact;
import com.hexhaki.network.msg.S2CGalaxyImpact;
import com.hexhaki.network.msg.S2CGalaxyWave;
import com.hexhaki.network.msg.S2CGalaxyHakiStrike;
import com.hexhaki.network.msg.S2CRyoRelease;
import com.hexhaki.network.msg.S2CWifiHaki;
import com.hexhaki.client.render.GalaxyRenderer;
import com.hexhaki.client.render.PerceptionRenderer;
import com.hexhaki.client.render.HakiImpactScreen;
import com.lowdragmc.photon.client.fx.EntityEffect;
import com.lowdragmc.photon.client.fx.BlockEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Circle;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Cone;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.trail.TrailEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** Actual Photon 1.1.17 runtime composition. No vanilla particle fallback is used here. */
public final class HakiVfx {
    private static final Logger LOGGER=LogUtils.getLogger();
    private static final ResourceLocation SOFT=HexHaki.id("textures/effect/soft.png");
    private static final ResourceLocation SPARK=HexHaki.id("textures/effect/spark.png");
    private static final ResourceLocation BEAM=HexHaki.id("textures/effect/beam.png");
    private static final ResourceLocation RAGE_GLOW=HexHaki.id("textures/effect/rage_glow.png");
    private static final ResourceLocation RAGE_FLAME=HexHaki.id("textures/effect/rage_flame.png");
    private static final ResourceLocation RAGE_LIGHTNING=HexHaki.id("textures/effect/rage_lightning_beam.png");
    private static final ResourceLocation CONQUEROR_SOFT=HexHaki.id("textures/effect/conqueror_soft.png");
    private static final Map<Integer,EntityEffect> GALAXY_FIST_EFFECTS=new HashMap<>();
    // Absorption: once the charge reads READY the orb is drawn into the hand over a short beat
    // instead of simply hanging there, and what remains is a charged fist.
    private static final Map<Integer,Integer> GALAXY_ABSORB_TICKS=new HashMap<>();
    private static final Map<Integer,GalaxyTier> GALAXY_FIST_TIERS=new HashMap<>();
    /** Current un-absorbed scale per fist, so the absorption contracts from wherever the charge
     *  actually got to rather than snapping to a fixed size first. */
    private static final Map<Integer,Float> GALAXY_FIST_SCALE=new HashMap<>();
    private record GalaxyTier(boolean coated,boolean advanced){ static final GalaxyTier PLAIN=new GalaxyTier(false,false); }
    private static final Map<Integer,EntityEffect> GALAXY_CHARGED_FIST=new HashMap<>();
    private static final int GALAXY_ABSORB_DURATION=26;
    private static final List<PendingGalaxyDetonation> GALAXY_PENDING_DETONATIONS=new ArrayList<>();
    // Advanced King's Grip fire must follow the animated LEFT hand, not the entity root.
    // The wind-up packet refreshes this short TTL and HakiHandLayer renders the volumetric fire
    // directly in the live leftArm ModelPart transform.
    private static final Map<Integer,Long> KINGS_GRIP_LEFT_FIST_FIRE_UNTIL=new HashMap<>();
    // Photon render modes. Billboard always faces the camera, which is why a sprite-only effect
    // reads as flat no matter how many sprites it has: every quad turns with the view together, so
    // there is no parallax between them. Horizontal lies flat on the ground and VerticalBillboard
    // only spins about Y, and both stay put as the camera moves -- which is what gives an effect
    // depth. Mixing the three is worth far more than adding particles.
    private static final RendererSetting.Particle.Mode FLAT=RendererSetting.Particle.Mode.Horizontal;
    private static final RendererSetting.Particle.Mode UPRIGHT=RendererSetting.Particle.Mode.VerticalBillboard;

    private HakiVfx(){}

    private record PendingGalaxyDetonation(int entityId,float power,boolean hakiCoated,boolean advancedHaki,double x,double y,double z,long seed,int ticks){}

    public static void accept(S2CHakiVisual m){
        Minecraft mc=Minecraft.getInstance(); if(mc.level==null)return; Entity entity=mc.level.getEntity(m.entityId()); if(entity==null)return;
        switch(m.visual()){
            case ARMAMENT_ON -> play(entity,armament(m.power()));
            case ARMAMENT_HIT -> { HakiImpactScreen.impact(entity,.12f+.20f*m.power()); play(entity,armamentImpact(m.power(),m.variant()>0,m.seed())); }
            case RYO_CHARGE -> play(entity,compression(m.power(),false));
            case RYO_AURA -> play(entity,compression(Math.max(.25f,m.power()),false));
            case RYO_RELEASE -> {
                // Debug/fallback path. Real gameplay uses S2CRyoRelease so the authoritative server
                // punch vector is preserved exactly across every client.
                Vec3 dir=entity.getLookAngle().normalize();
                Vec3 origin=entity.getEyePosition().add(dir.scale(.48)).add(0,-.34,0);
                HakiImpactScreen.impact(entity,.20f+.22f*m.power());
                playAtWorld(entity,origin.x,origin.y,origin.z,ryoReleaseWorld(dir,m.power(),5.5D+4.0D*m.power(),m.seed()));
                spawnRyoDirectionalAir(Minecraft.getInstance(),origin,dir,m.power(),5.5D+4.0D*m.power(),m.seed());
            }
            case INTERNAL_HIT -> { HakiImpactScreen.impact(entity,.16f+.12f*m.power()); play(entity,internalPulse(m.power())); }
            case OBSERVATION_PULSE -> { PerceptionRenderer.danger(m.variant()); play(entity,observationPulse(m.power())); }
            case CONQUEROR_CHARGE -> playAtWorld(entity,entity.getX(),entity.getY(),entity.getZ(),
                    conquerorCharge(m.power(),m.variant(),m.seed()));
            case CONQUEROR_RELEASE -> {
                int mode=Math.floorMod(m.variant(),4);
                boolean tap=mode==ConquerorMode.CONE.ordinal();
                boolean supreme=!tap && m.power()>=.96f && m.variant()/4>=1000;
                if(supreme) {
                    HakiImpactScreen.supreme(entity);
                } else if(tap) {
                    HakiImpactScreen.impact(entity,.18f);
                } else {
                    HakiImpactScreen.impact(entity,.28f+.34f*m.power());
                }

                if(tap) {
                    // Fallback path: the server sends CONQUEROR_TAP_CONE for taps now, but an
                    // older server on the same protocol still routes them through here.
                    Vec3 direction=entity.getLookAngle().normalize();
                    Vec3 origin=entity.getEyePosition().add(direction.scale(.7D));
                    playAtWorld(entity,origin.x,origin.y,origin.z,
                            conquerorTapCone(direction,m.power(),m.seed()));
                } else {
                    playAtWorld(entity,entity.getX(),entity.getY(),entity.getZ(),
                            conquerorRelease(m.power(),m.variant(),m.seed()));
                }
            }
            case CONQUEROR_TAP_CONE -> {
                HakiImpactScreen.impact(entity,.22f);
                Vec3 aim=entity.getLookAngle().normalize();
                Vec3 mouth=entity.getEyePosition().add(aim.scale(.7));
                playAtWorld(entity,mouth.x,mouth.y,mouth.z,conquerorTapCone(aim,m.power(),m.seed()));
            }
            case CONQUEROR_AFTERSHOCK -> play(entity,aftershock(m.power(),m.seed()));
            case CONQUEROR_HIT -> play(entity,conquerorPressureHit(m.power(),m.seed()));
            case ACOC -> play(entity,acoc(m.seed()));
            case DOMINION -> play(entity,dominion(m.power()));
            case KINGS_GRIP_LOCK -> {
                if(m.variant()==1) play(entity,kingsGripLock3D(.55f,m.seed()));
                else if(m.variant()>=2) play(entity,kingsGripLock3D(.92f,m.seed()));
            }
            case KINGS_GRIP_WINDUP -> {
                // Advanced keeps its bone-attached left-fist fire; every tier now also gets a
                // world-space chamber build so the wind-up is visibly loading, not just waiting.
                if(m.variant()>=2) markKingsGripLeftFistFire(entity.getId());
                Vec3 fist=kingsGripFist(entity);
                playAtWorld(entity,fist.x,fist.y,fist.z,
                        kingsGripWindupWorld(entity,m.variant(),m.power(),m.seed()));
            }
            case KINGS_GRIP_IMPACT -> {
                // variant: 0 physical air-only, 1 Armament, 2 Advanced/J Haki. The directional
                // air sheet itself comes from S2CArmamentImpact so it exits the victim's back.
                if(m.variant()<=0){
                    HakiImpactScreen.impact(entity,.48f);
                }else if(m.variant()==1){
                    HakiImpactScreen.impact(entity,.82f);
                    play(entity,kingsGripHakiImpact3D(m.seed()));
                }else{
                    HakiImpactScreen.impact(entity,1.0f);
                    play(entity,kingsGripAdvancedImpact(m.seed()));
                }
                // The knuckles themselves. The chamber gathers onto the fist for two seconds, so
                // releasing all of it at the body's centre threw the payoff away from where the
                // eye was already looking.
                Vec3 knuckles=kingsGripFist(entity);
                playAtWorld(entity,knuckles.x,knuckles.y,knuckles.z,
                        kingsGripFistDetonation(m.variant(),m.seed()));

                // Ground reaction. A strike this size that leaves the floor untouched is most of
                // why the execution used to read as weightless.
                playAtWorld(entity,entity.getX(),entity.getY(),entity.getZ(),
                        kingsGripGroundReaction(m.variant(),m.seed()));
            }
            case THRAGG_DRAW -> {
                // Advanced keeps the bone-attached fist fire; HakiHandLayer renders it in the live
                // arm transform so it follows the chamber rather than the entity root.
                if(m.variant()>=2) markKingsGripLeftFistFire(entity.getId());
                Vec3 fist=thraggFist(entity);
                playAtWorld(entity,fist.x,fist.y,fist.z,thraggDraw(entity,m.variant(),m.power(),m.seed()));
            }
            case THRAGG_UPPERCUT -> {
                HakiImpactScreen.impact(entity,m.variant()>=2?1.0f:(m.variant()==1?.82f:.55f));
                playAtWorld(entity,entity.getX(),entity.getY(),entity.getZ(),
                        thraggUppercut(m.variant(),m.seed()));
            }
            case THRAGG_BLINK -> playAtWorld(entity,entity.getX(),entity.getY()+entity.getBbHeight()*.5,entity.getZ(),
                    thraggBlink(m.variant(),m.seed()));
            case THRAGG_SLAM -> {
                HakiImpactScreen.impact(entity,m.variant()>=2?.95f:.68f);
                playAtWorld(entity,entity.getX(),entity.getY()+entity.getBbHeight(),entity.getZ(),
                        thraggSlam(m.variant(),m.seed()));
            }
            case THRAGG_TRAIL -> playAtWorld(entity,entity.getX(),entity.getY()+entity.getBbHeight()*.5,entity.getZ(),
                    thraggTrail(Math.floorMod(m.variant(),4),m.variant()>=4,m.power(),m.seed()));
            // THRAGG_CRATER is delivered by S2CWorldFx instead: see acceptWorld.
            case SOVEREIGN_LOCK -> play(entity,sovereignLock(m.power(),m.seed()));
            case SOVEREIGN_IMPACT -> { HakiImpactScreen.impact(entity,.55f); play(entity,sovereign(m.seed())); }
            case CONVERGENCE_PUNCH -> { HakiImpactScreen.impact(entity,.36f+.18f*m.power()); play(entity,convergencePunch(m.power(),m.variant()>0,m.seed())); }
            case CONVERGENCE_CHARGE -> play(entity,convergenceCharge(m.power(),m.variant()>0,m.seed()));
            case GALAXY_FIST_START -> {
                startGalaxyFistCharge(entity,(m.variant() & 4) != 0,(m.variant() & 2) != 0);
                // The overhead spiral is the actual Galaxy Impact read; the fist effect is now
                // only the near-field anchor it collapses into.
                GalaxyRenderer.begin(entity.getId(),(m.variant() & 4) != 0,(m.variant() & 2) != 0);
            }
            case GALAXY_FIST_INTENSIFY -> {
                intensifyGalaxyFist(entity.getId(),(m.variant() & 4) != 0,(m.variant() & 2) != 0);
                GalaxyRenderer.intensify(entity.getId(),(m.variant() & 4) != 0,(m.variant() & 2) != 0);
            }
            case GALAXY_FIST_ABSORB -> absorbGalaxyFist(entity.getId(),(m.variant() & 4) != 0,(m.variant() & 2) != 0);
            case GALAXY_FIST_STOP -> {
                stopGalaxyFist(entity.getId());
                // FIST_STOP is sent on the punch frame itself, seven ticks before contact, so this
                // is exactly where the galaxy should be dragged down into the fist.
                GalaxyRenderer.collapse(entity.getId());
            }
            case GALAXY_ASCENT -> play(entity,galaxyAscent(m.power(),m.variant()>0,m.seed()));
            case CONVERGENCE_RELEASE -> { HakiImpactScreen.impact(entity,.45f+.25f*m.power()); play(entity,convergenceRelease(m.power(),m.variant()>0,m.seed())); }
            case CONVERGENCE_HIT -> { HakiImpactScreen.impact(entity,.68f); play(entity,convergenceHit(m.power(),m.variant()>0,m.seed())); }
            case GALAXY_IMPACT -> {
                boolean coated=(m.variant() & 4) != 0;
                boolean advanced=(m.variant() & 2) != 0;
                HakiImpactScreen.impact(entity,advanced?1.0f:(coated?.82f+.18f*m.power():.54f));
                play(entity,rageGalaxyImpactFx(new Vec3(0,12,-6),m.seed(),coated,advanced));
            }
            case JOYBOY_AWAKENING -> { HakiImpactScreen.supreme(entity); play(entity,joyBoyAwakening(m.seed())); }
            case LAST_STAND_AURA -> play(entity,lastStandAura(m.variant()));
            case LAST_STAND_BLAST -> play(entity,lastStandPulse(m.power(),m.seed()));
            case LAST_STAND_FINISH -> {
                HakiImpactScreen.impact(entity,1.0f);
                // The floor moves for everyone within a hundred blocks, well past the damage
                // radius, so distant players feel the detonation rather than only seeing it.
                HakiImpactScreen.quake(entity,1.0f,70,100.0);
                play(entity,lastStandFinalBlast(m.seed(),Math.max(15,m.variant())));
                playAtWorld(entity,entity.getX(),entity.getY(),entity.getZ(),
                        lastStandDetonation(m.seed(),Math.max(15,m.variant())));
            }
            case LAST_STAND_HIT -> play(entity,convergenceHit(1f,true,m.seed()));
        }
    }

    private static void markKingsGripLeftFistFire(int entityId){
        Minecraft mc=Minecraft.getInstance();
        long now=mc.level==null?0L:mc.level.getGameTime();
        // Packets refresh every 10 ticks during the Advanced wind-up; 14 ticks bridges packets
        // without letting the effect linger after the punch.
        KINGS_GRIP_LEFT_FIST_FIRE_UNTIL.put(entityId,now+14L);
    }

    public static boolean isKingsGripLeftFistFireActive(int entityId){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null){ KINGS_GRIP_LEFT_FIST_FIRE_UNTIL.remove(entityId); return false; }
        long until=KINGS_GRIP_LEFT_FIST_FIRE_UNTIL.getOrDefault(entityId,Long.MIN_VALUE);
        if(until<mc.level.getGameTime()){ KINGS_GRIP_LEFT_FIST_FIRE_UNTIL.remove(entityId); return false; }
        return true;
    }

    /** Long-range Shanks-style Supreme King projection. The server gives both endpoints and a seed,
     * so every viewer sees the SAME crooked high arc. This is intentionally not a ray/laser: the
     * bolt climbs into the air, meanders laterally, then hooks down into the target. */
    public static void acceptWifiHaki(S2CWifiHaki m){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        Entity source=mc.level.getEntity(m.sourceId());
        Entity target=mc.level.getEntity(m.targetId());
        Entity reference=source!=null?source:(target!=null?target:mc.player);
        if(reference==null)return;
        Vec3 start=new Vec3(m.startX(),m.startY(),m.startZ());
        Vec3 end=new Vec3(m.endX(),m.endY(),m.endZ());
        Vec3 delta=end.subtract(start);
        if(delta.lengthSqr()<1.0E-6)return;
        playAtWorld(reference,start.x,start.y,start.z,wifiHakiArc(delta,m.power(),m.travelTicks(),m.pulseIndex(),m.beamCount(),m.seed()));
    }

    /** World-space coated punch impact. The server supplies the exact contact point and the direction
     * the fist was travelling, so the air burst always tears away from the punch instead of inheriting
     * the victim's yaw/pitch and spraying sideways. */
    public static void acceptArmamentImpact(S2CArmamentImpact m){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        Entity attacker=mc.level.getEntity(m.attackerId());
        Entity target=mc.level.getEntity(m.targetId());
        Entity reference=attacker!=null?attacker:target;
        if(reference==null)return;

        Vec3 direction=new Vec3(m.dirX(),m.dirY(),m.dirZ());
        if(direction.lengthSqr()<1.0E-6) direction=new Vec3(0,0,1);
        else direction=direction.normalize();
        Entity impactTarget=target!=null?target:reference;
        HakiImpactScreen.impact(impactTarget,.12f+.20f*m.power());
        boolean kingsGrip=m.style()==1;
        playAtWorld(reference,m.x(),m.y(),m.z(),
                kingsGrip?kingsGripDirectionalImpactWorld(direction,m.power(),m.acoc(),m.seed())
                        :armamentImpactWorld(direction,m.power(),m.acoc(),m.seed()));
        if(!kingsGrip) spawnDirectionalAir(mc,new Vec3(m.x(),m.y(),m.z()),direction,m.power(),m.seed());
    }

    /** Ryuo release with a server-authored fist origin and look vector. This deliberately avoids
     *  EntityEffect rotation: the FX is a fixed world-space composition whose forward axis is built
     *  mathematically from the exact vector used for reach, damage and knockback. */
    public static void acceptRyoRelease(S2CRyoRelease m){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        Entity entity=mc.level.getEntity(m.entityId());
        if(entity==null)return;
        Vec3 direction=new Vec3(m.dirX(),m.dirY(),m.dirZ());
        if(direction.lengthSqr()<1.0E-6) direction=new Vec3(0,0,1);
        else direction=direction.normalize();
        Vec3 origin=new Vec3(m.x(),m.y(),m.z());
        HakiImpactScreen.impact(entity,.20f+.22f*m.power());
        playAtWorld(entity,origin.x,origin.y,origin.z,ryoReleaseWorld(direction,m.power(),m.reach(),m.seed()));
        spawnRyoDirectionalAir(mc,origin,direction,m.power(),m.reach(),m.seed());
    }

    /** Exact RAGE Galaxy Impact receiver. The server supplies both the right-fist release point
     * and the remote ground endpoint, so the client never guesses from player/camera rotation. */
    public static void acceptGalaxyImpact(S2CGalaxyImpact m){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        Entity entity=mc.level.getEntity(m.entityId());
        if(entity==null)return;

        Vec3 start=new Vec3(m.startX(),m.startY(),m.startZ());
        Vec3 impact=new Vec3(m.x(),m.y(),m.z());
        Vec3 delta=start.subtract(impact); // local source position when the FX is anchored at impact
        playAtWorld(entity,m.x(),m.y(),m.z(),rageGalaxyImpactFx(delta,m.seed(),m.hakiCoated(),m.advancedHaki()));

        Vec3 flight=impact.subtract(start);
        if(flight.lengthSqr()>.01D){
            Vec3 direction=flight.normalize();
            double length=flight.length();
            for(int i=1;i<=13;i++){
                double t=i/14.0D;
                Vec3 point=start.add(direction.scale(length*t));
                mc.level.addParticle(ParticleTypes.SONIC_BOOM,point.x,point.y,point.z,0,0,0);
                for(int j=0;j<5;j++){
                    double angle=i*1.71D+j*(Math.PI*2.0D/5.0D);
                    double radius=.28D+i*.055D;
                    mc.level.addParticle(ParticleTypes.POOF,
                            point.x+Math.cos(angle)*radius,
                            point.y+(j-2)*.045D,
                            point.z+Math.sin(angle)*radius,
                            direction.x*.18D,direction.y*.18D,direction.z*.18D);
                }
            }
        }
        // RAGE holds the center compressed for five ticks before the real detonation.
        GALAXY_PENDING_DETONATIONS.add(new PendingGalaxyDetonation(m.entityId(),m.power(),m.hakiCoated(),m.advancedHaki(),m.x(),m.y(),m.z(),m.seed(),5));
    }


    /** Exact receiver for the Armament-500 tap wave. Both endpoints come from the same server-side
     * look vector used by collision/knockback, so the visible cylinder cannot disagree with aim. */
    public static void acceptGalaxyWave(S2CGalaxyWave m){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        Entity entity=mc.level.getEntity(m.entityId());
        if(entity==null)return;

        Vec3 start=new Vec3(m.startX(),m.startY(),m.startZ());
        Vec3 end=new Vec3(m.endX(),m.endY(),m.endZ());
        if(end.subtract(start).lengthSqr()<.01D)return;

        // Harder camera punctuation on the actual contact frame. The wave itself is entirely Photon-based;
        // do not layer vanilla Warden SONIC_BOOM rings over it.
        float impactStrength=m.hakiTier()==0
                ? .24f+.10f*m.power()+.10f*m.charge()
                : .56f+.22f*m.power()+.22f*m.charge();
        HakiImpactScreen.impact(entity,impactStrength);
        playAtWorld(entity,start.x,start.y,start.z,galaxyWaveWorld(start,end,m.power(),m.charge(),m.radius(),m.joyBoy(),m.hakiTier(),m.seed()));
    }

    /** Galaxy storm packet. radius <= 0 restores the classic normal-Haki sky strike; positive
     * radius is the Advanced/J horizontal ground-crawling field. */
    public static void acceptGalaxyHakiStrike(S2CGalaxyHakiStrike m){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        Entity entity=mc.level.getEntity(m.entityId());
        if(entity==null)return;
        FX strike=m.radius()<=.001f?galaxyHakiSkyStrikeFx(m.seed()):galaxyHakiStrikeFx(m.radius(),m.seed());
        playAtWorld(entity,m.x(),m.y(),m.z(),strike);
    }

    /** Fixed world-space Photon anchor. BlockEffect does not follow the caster, so the detonation
     *  remains exactly under the crosshair even while the player is diving after release. The
     *  sub-block offset preserves the precise ray-trace hit rather than snapping to block centers. */
    /**
     * World-addressed effects: drawn at a coordinate, with no entity to resolve first.
     *
     * <p>The Haki Grip's crater used to be addressed by the victim's entity id, which meant it only
     * appeared if that entity resolved on the viewer's client at the exact tick it fired -- and a
     * body that has just been driven sixty blocks into the floor is exactly the case where that
     * does not hold. A blast happens at a place, so it is now sent to a place.
     */
    public static void acceptWorld(S2CWorldFx m){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return;
        if(m.kind()==S2CWorldFx.THRAGG_CRATER){
            int tier=Math.max(0,Math.min(2,m.variant()));
            double distance=mc.player.distanceToSqr(m.x(),m.y(),m.z());
            HakiImpactScreen.quakeAt(m.x(),m.y(),m.z(),tier>=2?1.0f:.62f,tier>=2?34:22,tier>=2?70.0:40.0);
            if(distance<40000.0){
                playAtFixed(mc.level,m.x(),m.y(),m.z(),thraggCrater(tier,m.seed()));
                if(tier>=2){
                    // Advanced cracks the floor open, the same fracture the full-charge Conqueror
                    // release leaves behind at a smaller radius.
                    playAtFixed(mc.level,m.x(),m.y()+.04,m.z(),thraggFloorCrack(m.seed()));
                }
            }
        }
    }

    /** Starts a composition at an absolute world position with no reference entity. */
    private static void playAtFixed(net.minecraft.client.multiplayer.ClientLevel level,
                                    double x,double y,double z,FX fx){
        if(fx.getMainFX().objects().isEmpty()){
            LOGGER.error("Rejected an empty HexHaki world composition at {} {} {}",x,y,z);
            return;
        }
        BlockPos pos=BlockPos.containing(x,y,z);
        BlockEffect effect=new BlockEffect(fx,level,pos);
        effect.setOffset(new Vector3f((float)(x-(pos.getX()+.5)),(float)(y-(pos.getY()+.5)),(float)(z-(pos.getZ()+.5))));
        effect.setAllowMulti(true);
        effect.setForcedDeath(false);
        try{
            effect.start();
        }catch(RuntimeException failure){
            LOGGER.error("HexHaki world composition failed to start at {} {} {}",x,y,z,failure);
        }
    }

    /**
     * Advanced-only floor fracture, flat against the ground.
     *
     * <p>The same crack sprite the full-charge Conqueror release leaves, at a smaller radius: it is
     * a fist-sized hole in the world, not a fifty-block one, and drawing it Horizontal is what makes
     * it read as damage to the floor rather than a decal hanging in the air.
     */
    private static FX thraggFloorCrack(long seed){
        FX f=fx();
        Random r=new Random(seed^0xA54FF53A5F1D36F1L);
        ParticleEmitter fracture=HakiFx.additive(
                HakiFx.emitter(HakiFx.CRACK,30,26,0f,7.2f,0xC8FF7A5A,true));
        HakiFx.mode(fracture,FLAT);
        HakiFx.size(fracture,HakiCurves.expand(1.22f));
        HakiFx.color(fracture,HakiCurves.fadeOut());
        HakiFx.at(fracture,new Vec3(0,.06,0));
        fracture.config.setMaxParticles(6);
        HakiFx.burst(fracture,1,0);
        add(f,fracture);

        // A couple of smaller offset fractures so the break is not one tidy stamp.
        for(int i=0;i<3;i++){
            double a=r.nextDouble()*Math.PI*2D;
            double at=1.6D+r.nextDouble()*3.4D;
            ParticleEmitter chip=HakiFx.additive(
                    HakiFx.emitter(HakiFx.CRACK,26,22,0f,3.0f+r.nextFloat()*2.2f,0xA8FF9A6A,true));
            HakiFx.mode(chip,FLAT);
            HakiFx.size(chip,HakiCurves.expand(1.15f));
            HakiFx.color(chip,HakiCurves.fadeOut());
            HakiFx.scatterRoll(chip,r);
            HakiFx.at(chip,new Vec3(Math.cos(a)*at,.05,Math.sin(a)*at));
            chip.config.setMaxParticles(3);
            HakiFx.burst(chip,1,2+i*2);
            add(f,chip);
        }
        return f;
    }

    private static void playAtWorld(Entity reference,double x,double y,double z,FX fx){
        if(fx.getMainFX().objects().isEmpty()){
            LOGGER.error("Rejected an empty HexHaki world composition for entity {}",reference.getId());
            return;
        }
        BlockPos pos=BlockPos.containing(x,y,z);
        BlockEffect effect=new BlockEffect(fx,reference.level(),pos);
        effect.setOffset(new Vector3f((float)(x-(pos.getX()+.5)),(float)(y-(pos.getY()+.5)),(float)(z-(pos.getZ()+.5))));
        effect.setAllowMulti(true);
        effect.setForcedDeath(false);
        try{
            effect.start();
            if(effect.getRuntime()==null)LOGGER.error("Photon did not create a fixed Galaxy Impact runtime for entity {}",reference.getId());
        }catch(RuntimeException failure){
            LOGGER.error("HexHaki fixed Galaxy Impact failed to start for entity {}",reference.getId(),failure);
        }
    }

    private static void play(Entity entity,FX fx){
        if(fx.getMainFX().objects().isEmpty()){
            LOGGER.error("Rejected an empty HexHaki Photon composition for entity {}",entity.getId());
            return;
        }
        EntityEffect effect=new EntityEffect(fx,entity.level(),entity,EntityEffect.AutoRotate.XROT);
        // Programmatic FX have no fxLocation. Photon treats two null-located effects as the same
        // effect unless multi-spawn is explicitly allowed, which caused the max-charge pulse to
        // silently suppress the Conqueror release that arrived immediately after it.
        effect.setAllowMulti(true);
        try{
            effect.start();
            if(effect.getRuntime()==null)LOGGER.error("Photon did not create a runtime for HexHaki entity {}",entity.getId());
        }catch(RuntimeException failure){
            LOGGER.error("HexHaki Photon composition failed to start for entity {}",entity.getId(),failure);
        }
    }
    private static FX fx(){return new FX();}
    private static void add(FX fx,com.lowdragmc.photon.client.gameobject.IFXObject object){fx.getMainFX().objects().add(object);}

    private static ParticleEmitter particle(int duration,int life,float speed,float size,int color,boolean bloom){
        return particleTex(duration,life,speed,size,color,bloom,SOFT);
    }
    private static ParticleEmitter particleTex(int duration,int life,float speed,float size,int color,boolean bloom,ResourceLocation texture){
        ParticleEmitter p=new ParticleEmitter(); p.config.setDuration(duration);p.config.setLooping(false);p.config.setStartLifetime(NumberFunction.constant(life));p.config.setStartSpeed(NumberFunction.constant(speed));p.config.setStartSize(new NumberFunction3(size,size,size));p.config.setStartColor(NumberFunction.color(color));p.config.setMaxParticles(450);
        p.config.material.setMaterial(new TextureMaterial(texture));p.config.material.setCull(false);p.config.renderer.setBloomEffect(bloom);p.config.renderer.setBloomColor(color);p.config.renderer.setLayer(RendererSetting.Layer.Translucent);return p;
    }

    private static ParticleEmitter conquerorParticle(int duration,int life,float speed,float size,int color,boolean bloom){
        return particleTex(duration,life,speed,size,color,bloom,CONQUEROR_SOFT);
    }
    private static void burst(ParticleEmitter p,int count,int delay){ p.config.emission.setEmissionRate(NumberFunction.constant(0)); EmissionSetting.Burst b=new EmissionSetting.Burst();b.time=delay;b.cycles=1;b.interval=1;b.setCount(NumberFunction.constant(count));p.config.emission.getBursts().add(b); }
    private static void burstCycles(ParticleEmitter p,int count,int delay,int cycles,int interval){ p.config.emission.setEmissionRate(NumberFunction.constant(0)); EmissionSetting.Burst b=new EmissionSetting.Burst();b.time=delay;b.cycles=Math.max(1,cycles);b.interval=Math.max(1,interval);b.setCount(NumberFunction.constant(count));p.config.emission.getBursts().add(b); }

    private static BeamEmitter beam(Vector3f a,Vector3f b,float width,int color,int duration,int delay,boolean bloom){
        BeamEmitter e=new BeamEmitter();e.setPos(a.x,a.y,a.z);e.setDelay(delay);e.getConfig().setDuration(duration);e.getConfig().setLooping(false);e.getConfig().setWidth(NumberFunction.constant(width));e.getConfig().setColor(NumberFunction.color(color));e.getConfig().getEnd().set(b.x-a.x,b.y-a.y,b.z-a.z);e.getConfig().material.setMaterial(new TextureMaterial(BEAM));e.getConfig().material.setCull(false);e.getConfig().renderer.setBloomEffect(bloom);e.getConfig().renderer.setBloomColor(color);return e;
    }

    private static ParticleEmitter loopParticle(ResourceLocation texture,int life,float speed,float size,int color,boolean bloom,float rate,float radius,float thickness){
        ParticleEmitter p=particleTex(240,life,speed,size,color,bloom,texture);
        p.config.setLooping(true);
        p.config.setMaxParticles(1200);
        p.config.emission.setEmissionRate(NumberFunction.constant(rate));
        Sphere sphere=new Sphere();sphere.setRadius(radius);sphere.setRadiusThickness(thickness);
        p.config.shape.setShape(sphere);
        return p;
    }

    private static BeamEmitter rageBeam(Vector3f a,Vector3f b,float width,int color,int duration,int delay,boolean bloom){
        BeamEmitter e=beam(a,b,width,color,duration,delay,bloom);
        e.getConfig().material.setMaterial(new TextureMaterial(RAGE_LIGHTNING));
        e.getConfig().material.setCull(false);
        return e;
    }

    /** Persistent Armament lightning presentation. The player model itself stays pitch-black.
     *  Full-body black/red lightning remains Photon-driven here; the red arm-only aura is rendered
     *  separately in HakiHandLayer so it can follow the animated arm bones exactly. */
    public static void armamentAmbient(Entity entity,float mastery,boolean acoc,long seed,boolean slimArms){
        FX f=fx();
        float q=Math.max(0f,Math.min(1f,mastery));
        Random r=new Random(seed);

        // IMPORTANT: the red arm aura is NOT spawned here. EntityEffect is rooted to the player
        // transform, not the animated arm bones, so a Photon sleeve created in this method drifts
        // through the torso during punches. The arm-only aura is rendered by HakiHandLayer using
        // the live left/right ModelPart transforms so it remains physically attached to each arm.

        // Aggressive full-body Armament lightning, but NEVER through the head/face. Each pulse
        // guarantees legs + torso + shoulders/arms, then adds mastery-scaled extra arcs below the
        // neck. This keeps first/third-person visibility clean while the body still looks violent.
        int guaranteedBands=3;
        int extra=1+(int)(q*2f)+(acoc?1:0);
        int boltCount=guaranteedBands+extra;
        for(int n=0;n<boltCount;n++){
            int band=n<guaranteedBands?n:r.nextInt(3);
            float baseY;
            float bodyHalfWidth;
            switch(band){
                case 0 -> { baseY=.24f+r.nextFloat()*.50f; bodyHalfWidth=.18f; } // legs
                case 1 -> { baseY=.74f+r.nextFloat()*.40f; bodyHalfWidth=.27f; } // waist/torso
                default -> { baseY=1.12f+r.nextFloat()*.25f; bodyHalfWidth=.45f; } // shoulders/arms; < face
            }

            int side=r.nextBoolean()?1:-1;
            float surfaceX=side*(bodyHalfWidth*(.70f+r.nextFloat()*.38f));
            float surfaceZ=(r.nextFloat()-.5f)*(.28f+(band==2?.12f:0f));
            Vector3f p0=new Vector3f(surfaceX,baseY,surfaceZ);

            // Keep the arc wrapped around the silhouette: short violent zig-zags that travel
            // across/up/down the body before kicking slightly outward at the end.
            float cross=(r.nextFloat()-.5f)*(.38f+.22f*q);
            float rise=(r.nextFloat()-.5f)*(.34f+.20f*q);
            float depth=(r.nextFloat()-.5f)*(.40f+.18f*q);
            Vector3f p1=new Vector3f(
                    p0.x+cross*.34f+(r.nextFloat()-.5f)*.08f,
                    p0.y+rise*.34f+(r.nextFloat()-.5f)*.07f,
                    p0.z+depth*.34f+(r.nextFloat()-.5f)*.08f);
            Vector3f p2=new Vector3f(
                    p0.x+cross*.70f+(r.nextFloat()-.5f)*.10f,
                    p0.y+rise*.70f+(r.nextFloat()-.5f)*.09f,
                    p0.z+depth*.70f+(r.nextFloat()-.5f)*.10f);
            Vector3f p3=new Vector3f(
                    p0.x+cross+side*(.08f+r.nextFloat()*.14f),
                    p0.y+rise+(r.nextFloat()-.5f)*.10f,
                    p0.z+depth+(r.nextFloat()-.5f)*.11f);

            float outer=.031f+.019f*q+(acoc?.010f:0f);
            float inner=.0125f+.0075f*q+(acoc?.004f:0f);
            // The tapered arc sprite carries the jaggedness and fades each segment's ends, so
            // consecutive segments join instead of butting together as visible rectangles.
            int arcLife=7+r.nextInt(3);
            add(f,HakiFx.beam(p0,p1,outer,0xEF050407,arcLife,0,false,HakiFx.ARC_SOFT));
            add(f,HakiFx.beam(p0,p1,inner,0xFFFF1E3C,arcLife,0,true,HakiFx.ARC));
            add(f,HakiFx.beam(p1,p2,outer*.90f,0xE9050407,arcLife+1,1,false,HakiFx.ARC_SOFT));
            add(f,HakiFx.beam(p1,p2,inner*.94f,0xFFFF263F,arcLife,1,true,HakiFx.ARC));
            add(f,HakiFx.beam(p2,p3,outer*.76f,0xDF050407,arcLife+1,2,false,HakiFx.ARC_SOFT));
            add(f,HakiFx.beam(p2,p3,inner*.82f,0xFFFF3048,arcLife,2,true,HakiFx.ARC));
            // Advanced Haki adds a restrained white-hot filament inside the red core.
            if(acoc) add(f,HakiFx.beam(p0,p1,inner*.42f,0xFFFFC8BE,arcLife-1,1,true,HakiFx.ARC));

            // Mastered Armament frequently forks below the neck so the body stays alive without face clutter.
            if(q>.45f && r.nextFloat()<.12f+.14f*q+(acoc?.05f:0f)){
                Vector3f fork=new Vector3f(p2).add(
                        (r.nextFloat()-.5f)*(.34f+.18f*q),
                        (r.nextFloat()-.5f)*(.28f+.12f*q),
                        (r.nextFloat()-.5f)*(.34f+.18f*q));
                add(f,HakiFx.beam(p1,fork,outer*.58f,0xD9050407,3,1,false,HakiFx.ARC_SOFT));
                add(f,HakiFx.beam(p1,fork,inner*.66f,0xFFFF2944,3,1,true,HakiFx.ARC));
            }
        }

        // Sparse black sparks are deliberately kept below the upper chest. The previous sphere
        // reached eye level and could place motes directly in front of the player's face.
        // Cooling embers rather than flat sparks; still kept below the upper chest so nothing
        // can sit in front of the player's face in first person.
        ParticleEmitter motes=HakiFx.embers(9,10,.055f,.030f+.012f*q,acoc?0xFFFFC49A:0xFFFF5A48,r);
        HakiFx.sphere(motes,.46f,.24f);
        HakiFx.at(motes,new Vec3(0,.72,0));
        HakiFx.burst(motes,8+(int)(10*q),0);
        add(f,motes);

        play(entity,f);
    }

    /**
     * Environmental Armament storm: bolts that leave the body entirely, strike the ground around
     * the user and lash outward.
     *
     * <p>{@link #armamentAmbient} only ever wrapped arcs around the silhouette, so an active
     * coating never affected the world it was standing in. These strikes land on the actual
     * surface (found by clipping downward, so they sit on terrain rather than floating at the
     * player's foot level) and each one leaves a flash, a dust ring and scattered embers.
     *
     * <p>Deliberately suppressed for the local player while they are in first person: these arcs
     * reach across the camera and would sit in the middle of the user's own view. Everyone else
     * sees the full storm, and the user sees it the moment they look from third person.
     *
     * <p>Sky strikes are <b>Advanced Haki only</b>. Normal Armament discharges outward from the
     * body; calling lightning down out of the sky is the Advanced tier's signature, and giving it
     * to both left the two tiers reading identically.
     */
    public static void armamentGroundStorm(Entity entity,float mastery,boolean acoc,long seed){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        if(entity==mc.player&&mc.options.getCameraType().isFirstPerson())return;

        float q=Math.max(0f,Math.min(1f,mastery));
        Random r=new Random(seed);
        // One sky strike per call at most, and only on roughly every third eligible call, so
        // Advanced reads as intermittent lightning rather than a downpour.
        boolean skyBeat=acoc && ((seed>>>17)&3L)==0L;
        int strikes=skyBeat?1+(int)(q*1.5f):0;
        double maxRange=3.0D+4.5D*q+(acoc?1.5D:0D);

        for(int i=0;i<strikes;i++){
            double angle=r.nextDouble()*Math.PI*2D;
            double distance=2.0D+r.nextDouble()*(maxRange-2.0D);
            double gx=entity.getX()+Math.cos(angle)*distance;
            double gz=entity.getZ()+Math.sin(angle)*distance;

            // Land the bolt on the real surface instead of the player's foot plane.
            Vec3 from=new Vec3(gx,entity.getY()+2.5D,gz);
            Vec3 to=new Vec3(gx,entity.getY()-7.0D,gz);
            net.minecraft.world.phys.HitResult hit=mc.level.clip(new net.minecraft.world.level.ClipContext(
                    from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,entity));
            if(hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS)continue;
            Vec3 ground=hit.getLocation();

            FX f=fx();
            // The descending bolt: it comes down out of the air, not out of the player.
            double height=5.5D+r.nextDouble()*4.0D+(acoc?2.0D:0D);
            Vec3 top=new Vec3((r.nextDouble()-.5D)*1.6D,height,(r.nextDouble()-.5D)*1.6D);
            HakiFx.boltPath(f,top,Vec3.ZERO,6+r.nextInt(3),.070D,
                    (acoc?1.30f:1.0f)*(.75f+.45f*q),acoc,13+r.nextInt(4),0,acoc?.26f:.18f,r);

            // Contact flash, scorch ring and thrown grit.
            ParticleEmitter flash=HakiFx.flash(10,8,.04f,.42f,acoc?0xFFFFD0B4:0xFFFF6A72);
            HakiFx.sphere(flash,.16f,1f);
            HakiFx.burst(flash,8,0);
            add(f,flash);

            ParticleEmitter ring=HakiFx.groundRing(22,18,.42f,3.10f,acoc?0xC8FFB894:0xB4FF8890);
            HakiFx.at(ring,new Vec3(0,.06,0));
            HakiFx.burst(ring,1,0);
            add(f,ring);

            ParticleEmitter grit=HakiFx.debris(26,20,.55f,.14f,0x9E8F8880,r);
            HakiFx.circle(grit,.34f,.65f);
            HakiFx.at(grit,new Vec3(0,.10,0));
            HakiFx.burst(grit,18+(int)(16*q),0);
            add(f,grit);

            ParticleEmitter sparks=HakiFx.embers(22,17,.72f,.075f,acoc?0xFFFFC08A:0xFFFF5A4A,r);
            HakiFx.sphere(sparks,.28f,.55f);
            HakiFx.at(sparks,new Vec3(0,.14,0));
            HakiFx.burst(sparks,14+(int)(18*q),0);
            add(f,sparks);

            playAtWorld(entity,ground.x,ground.y,ground.z,f);
        }

        // Discharge OUTWARD from the body. This is the primary read for normal Armament, so it is
        // dense and reaches properly rather than being a decorative afterthought: arcs leave the
        // torso in every direction, including upward and outward at head height, and earth
        // themselves at varying ranges.
        int lashes=2+(int)(q*2f)+(acoc?1:0);
        FX outward=fx();
        for(int i=0;i<lashes;i++){
            double angle=(Math.PI*2D*i/lashes)+(r.nextDouble()-.5D)*.9D;
            double distance=2.0D+r.nextDouble()*(2.8D+3.4D*q);
            // launch from anywhere up the torso, and fire at a spread of elevations
            Vec3 origin=new Vec3(0,.45D+r.nextDouble()*1.05D,0);
            double elevation=(r.nextDouble()-.35D)*1.5D;
            Vec3 end=new Vec3(Math.cos(angle)*distance,
                    origin.y+elevation,
                    Math.sin(angle)*distance);
            HakiFx.boltPath(outward,origin,end,5+r.nextInt(3),.13D,
                    (acoc?1.20f:.95f)*(.72f+.42f*q),acoc,11+r.nextInt(4),0,acoc?.28f:.20f,r);
        }
        // Sparks thrown off the discharge points.
        ParticleEmitter shed=HakiFx.embers(16,13,.85f,.060f+.020f*q,acoc?0xFFFFC08A:0xFFFF4A3C,r);
        HakiFx.sphere(shed,1.05f,.45f);
        HakiFx.at(shed,new Vec3(0,1.0,0));
        HakiFx.burst(shed,12+(int)(18*q),0);
        add(outward,shed);
        playAtWorld(entity,entity.getX(),entity.getY(),entity.getZ(),outward);
    }

    private static FX armament(float mastery){
        FX f=fx();
        ParticleEmitter coat=particle(10,10,.22f,.045f,0xEE111015,mastery>.55f);
        Sphere shell=new Sphere();shell.setRadius(.62f);shell.setRadiusThickness(.24f);coat.config.shape.setShape(shell);coat.config.shape.setPosition(new NumberFunction3(0,1.0,0));
        burst(coat,18+(int)(mastery*20),0);add(f,coat);
        ParticleEmitter sparks=particleTex(8,8,.48f,.045f,0xFFFF263F,true,SPARK);
        Sphere sparkShape=new Sphere();sparkShape.setRadius(.52f);sparkShape.setRadiusThickness(.18f);sparks.config.shape.setShape(sparkShape);sparks.config.shape.setPosition(new NumberFunction3(0,1.05,0));
        burst(sparks,6+(int)(mastery*24),2);add(f,sparks);
        ParticleEmitter ring=particle(8,9,.78f,.035f,mastery>.7f?0x99FF263F:0x884A4650,mastery>.7f);
        Circle circle=new Circle();circle.setRadius(.42f);circle.setRadiusThickness(.035f);ring.config.shape.setShape(circle);ring.config.shape.setPosition(new NumberFunction3(0,.18,0));
        burst(ring,20+(int)(mastery*18),1);add(f,ring);
        return f;
    }
    private static FX compression(float q,boolean red){
        FX f=fx();
        int coreColor=red?0xFFE2233A:0xFF17141B;
        ParticleEmitter inner=particle(12,12,.045f,.050f,coreColor,red);
        Circle c=new Circle();c.setRadius(.22f+.28f*q);c.setRadiusThickness(.035f);inner.config.shape.setShape(c);inner.config.shape.setPosition(new NumberFunction3(0,1.34,-.34));burst(inner,24+(int)(26*q),0);add(f,inner);

        ParticleEmitter outer=particle(14,13,.24f,.028f,red?0x99FF5467:0x886A6470,red);
        Circle c2=new Circle();c2.setRadius(.40f+.32f*q);c2.setRadiusThickness(.018f);outer.config.shape.setShape(c2);outer.config.shape.setPosition(new NumberFunction3(0,1.34,-.34));burst(outer,28+(int)(22*q),2);add(f,outer);

        ParticleEmitter sparks=particleTex(10,9,.34f,.040f,red?0xFFFF3048:0xFFAAA3B0,true,SPARK);
        Sphere ss=new Sphere();ss.setRadius(.42f+.18f*q);ss.setRadiusThickness(.18f);sparks.config.shape.setShape(ss);sparks.config.shape.setPosition(new NumberFunction3(0,1.34,-.34));burst(sparks,8+(int)(16*q),1);add(f,sparks);
        return f;
    }

    /** Direction-locked Ryuo emission. Every major geometry element advances along {@code direction};
     *  there are no radial lightning families or entity-local rotated cone emitters left in this move. */
    private static FX ryoReleaseWorld(Vec3 direction,float q,double reach,long seed){
        FX f=fx();
        Vec3 dir=direction.lengthSqr()<1.0E-6?new Vec3(0,0,1):direction.normalize();
        Vec3 reference=Math.abs(dir.y)<.94D?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 right=dir.cross(reference).normalize();
        Vec3 up=right.cross(dir).normalize();
        double length=Math.max(3.0D,reach);
        Random random=new Random(seed);

        // Tight fist compression. Start speed is intentionally tiny so it reads as a source flash,
        // not another spray with an unrelated velocity vector.
        ParticleEmitter fist=particleTex(7,8,.08f,.080f+.025f*q,0xFFF9F3F2,true,RAGE_GLOW);
        Sphere fistShape=new Sphere();fistShape.setRadius(.16f+.08f*q);fistShape.setRadiusThickness(.92f);
        fist.config.shape.setShape(fistShape);fist.config.shape.setPosition(new NumberFunction3(0,0,0));
        burst(fist,34+(int)(24*q),0);add(f,fist);

        // Stacked pressure discs: all are mathematically perpendicular to the authoritative punch
        // vector, so looking up/down or turning during combat cannot rotate them into world axes.
        int rings=6+(int)(3*q);
        for(int i=0;i<rings;i++){
            double t=(i+1)/(double)rings;
            double travel=.35D+(length-.55D)*t;
            double radius=.20D+t*(.52D+.40D*q);
            addDirectionalPressureRing(f,dir.scale(travel),right,up,radius,16+(i%3)*2,
                    .030f+.012f*q,i<2?0xE8FFF8F5:(i%2==0?0xCCEF233D:0xA9DDD7D8),
                    5+(i%3),Math.min(5,i/2));
        }

        // Dense straight pressure lanes reinforce the actual punch path instead of forming random
        // vertical streaks around the player's body.
        int lanes=10+(int)(8*q);
        for(int i=0;i<lanes;i++){
            double angle=random.nextDouble()*Math.PI*2.0D;
            double radial=.04D+random.nextDouble()*(.14D+.16D*q);
            Vec3 side=right.scale(Math.cos(angle)*radial).add(up.scale(Math.sin(angle)*radial));
            Vec3 from=dir.scale(.10D+random.nextDouble()*.18D).add(side.scale(.40D));
            Vec3 to=dir.scale(length*(.72D+random.nextDouble()*.28D)).add(side.scale(1.2D+random.nextDouble()*1.5D));
            add(f,beam(v3(from),v3(to),.018f+.012f*q,0xA8F2EEEE,5+random.nextInt(4),random.nextInt(3),true));
        }

        // Black/red Ryuo Haki snakes FORWARD. Each segment's axial distance only increases, so a
        // branch may wobble around the emission but can never curl behind or beside the player.
        int bolts=4+(int)(4*q);
        for(int bolt=0;bolt<bolts;bolt++){
            double angle=random.nextDouble()*Math.PI*2.0D;
            int segments=5+random.nextInt(3);
            Vec3 previous=dir.scale(.08D);
            for(int segment=1;segment<=segments;segment++){
                double t=segment/(double)segments;
                double envelope=Math.sin(Math.PI*t);
                angle+=(random.nextDouble()-.5D)*.65D;
                double radial=(.10D+.32D*q)*envelope*(.55D+random.nextDouble()*.75D);
                Vec3 lateral=right.scale(Math.cos(angle)*radial).add(up.scale(Math.sin(angle)*radial));
                Vec3 next=dir.scale(length*t).add(lateral);
                int delay=Math.min(5,segment/2+bolt%2);
                add(f,beam(v3(previous),v3(next),.080f+.030f*q,0xE7050407,6+random.nextInt(3),delay,false));
                add(f,beam(v3(previous),v3(next),.021f+.010f*q,0xFFFF2944,5+random.nextInt(3),delay,true));
                if(segment>1&&random.nextFloat()<.25f){
                    Vec3 branch=next.add(dir.scale(.28D+random.nextDouble()*.55D))
                            .add(right.scale((random.nextDouble()-.5D)*(.26D+.18D*q)))
                            .add(up.scale((random.nextDouble()-.5D)*(.26D+.18D*q)));
                    add(f,beam(v3(next),v3(branch),.040f,0xD8050407,4,delay+1,false));
                    add(f,beam(v3(next),v3(branch),.011f,0xFFFF3851,3,delay+1,true));
                }
                previous=next;
            }
        }

        // Compact terminal pressure flash at the end of the same forward axis.
        Vec3 end=dir.scale(length);
        ParticleEmitter endFlash=particleTex(8,9,.10f,.115f+.035f*q,0xFFF42740,true,RAGE_GLOW);
        Sphere endShape=new Sphere();endShape.setRadius(.24f+.13f*q);endShape.setRadiusThickness(.82f);
        endFlash.config.shape.setShape(endShape);endFlash.config.shape.setPosition(new NumberFunction3(end.x,end.y,end.z));
        burst(endFlash,42+(int)(34*q),4);add(f,endFlash);
        return f;
    }

    /** Vanilla air is only a readability layer; positions and velocities are both projected from the
     *  same authoritative forward basis, eliminating the old vertical/random particle columns. */
    private static void spawnRyoDirectionalAir(Minecraft mc,Vec3 origin,Vec3 direction,float q,double reach,long seed){
        if(mc.level==null)return;
        Vec3 dir=direction.lengthSqr()<1.0E-6?new Vec3(0,0,1):direction.normalize();
        Vec3 reference=Math.abs(dir.y)<.94D?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 right=dir.cross(reference).normalize();
        Vec3 up=right.cross(dir).normalize();
        Random random=new Random(seed^0x5EEDBEEFL);
        int count=34+(int)(34*q);
        double length=Math.max(3.0D,reach);
        for(int i=0;i<count;i++){
            double t=.06D+random.nextDouble()*.94D;
            double angle=random.nextDouble()*Math.PI*2.0D;
            double spread=Math.sqrt(random.nextDouble())*(.035D+.14D*q)*(.35D+.65D*t);
            Vec3 radial=right.scale(Math.cos(angle)*spread).add(up.scale(Math.sin(angle)*spread));
            Vec3 pos=origin.add(dir.scale(length*t)).add(radial);
            Vec3 velocity=dir.scale(.20D+random.nextDouble()*(.16D+.12D*q)).add(radial.scale(.12D));
            mc.level.addParticle((i%6)==0?ParticleTypes.CLOUD:ParticleTypes.POOF,
                    pos.x,pos.y,pos.z,velocity.x,velocity.y,velocity.z);
        }
    }

    private static FX internalPulse(float q){
        FX f=fx();
        ParticleEmitter core=particle(9,9,.28f,.105f,0xEEFFF3F1,true);
        Sphere cs=new Sphere();cs.setRadius(.18f);cs.setRadiusThickness(.12f);core.config.shape.setShape(cs);core.config.shape.setPosition(new NumberFunction3(0,1,0));burst(core,22,0);add(f,core);

        ParticleEmitter red=particle(16,14,.72f+.65f*q,.075f,0xCCDF1D36,true);
        Sphere rs=new Sphere();rs.setRadius(.30f);rs.setRadiusThickness(.075f);red.config.shape.setShape(rs);red.config.shape.setPosition(new NumberFunction3(0,1,0));burst(red,38+(int)(30*q),2);add(f,red);

        ParticleEmitter black=particleTex(18,15,.42f,.050f,0xDD09070B,false,SPARK);
        Sphere bs=new Sphere();bs.setRadius(.48f);bs.setRadiusThickness(.22f);black.config.shape.setShape(bs);black.config.shape.setPosition(new NumberFunction3(0,1,0));burst(black,16+(int)(18*q),3);add(f,black);
        return f;
    }

    /** Compressed-air punch bloom. This is deliberately separate from toggle coating. */
    private static FX armamentImpact(float q,boolean acoc,long seed){
        FX f=fx();
        ParticleEmitter air=particle(12,14,1.8f+2.8f*q,.032f,0xC8E5E0E2,false);
        Circle ring=new Circle();ring.setRadius(.18f+.16f*q);ring.setRadiusThickness(.018f);air.config.shape.setShape(ring);air.config.shape.setRotation(new NumberFunction3(90,0,0));air.config.shape.setPosition(new NumberFunction3(0,1.2,-.48));
        burst(air,38+(int)(70*q),0);add(f,air);

        ParticleEmitter compression=particle(10,12,1.1f+1.6f*q,.060f,0xAA151219,acoc);
        Cone cone=new Cone();cone.setRadius(.10f);cone.setAngle(12+18*q);compression.config.shape.setShape(cone);compression.config.shape.setRotation(new NumberFunction3(90,0,0));compression.config.shape.setPosition(new NumberFunction3(0,1.2,-.42));
        burst(compression,26+(int)(54*q),1);add(f,compression);

        ParticleEmitter sparks=particleTex(10,10,.58f+.45f*q,.045f,acoc?0xFFFF2944:0xFFE2DDE1,acoc,SPARK);
        Sphere sphere=new Sphere();sphere.setRadius(.32f+.18f*q);sphere.setRadiusThickness(.18f);sparks.config.shape.setShape(sphere);sparks.config.shape.setPosition(new NumberFunction3(0,1.2,-.35));
        burst(sparks,10+(int)(28*q),0);add(f,sparks);
        if(acoc||q>.74f) lightningFamilies(f,.38f+.36f*q,0xE0060508,0xFFFF2944,acoc?5:2,2,4,new Random(seed));
        return f;
    }

    /** Direction-locked punch bloom anchored at the real hit point. The grey pressure rings are
     * built as world-space beam loops perpendicular to the attack vector, while the red/black
     * channels remain Haki accents around the contact instead of dictating travel direction. */
    private static FX armamentImpactWorld(Vec3 direction,float q,boolean acoc,long seed){
        FX f=fx();
        Vec3 dir=direction.lengthSqr()<1.0E-6?new Vec3(0,0,1):direction.normalize();
        Vec3 reference=Math.abs(dir.y)<.94D?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 right=dir.cross(reference).normalize();
        Vec3 up=right.cross(dir).normalize();
        Random random=new Random(seed);

        ParticleEmitter core=particle(8,9,.34f+.24f*q,.11f,0xD8FFFDF8,true);
        Sphere coreShape=new Sphere();coreShape.setRadius(.16f+.08f*q);coreShape.setRadiusThickness(.10f);
        core.config.shape.setShape(coreShape);core.config.shape.setPosition(new NumberFunction3(0,0,0));
        burst(core,18+(int)(20*q),0);add(f,core);

        int rings=3+(q>.72f?1:0);
        for(int i=0;i<rings;i++){
            double travel=.18+i*(.34+.08*q);
            double radius=.24+i*(.16+.07*q);
            addDirectionalPressureRing(f,dir.scale(travel),right,up,radius,14+i*2,.030f+i*.003f,
                    i==0?0xD9F4F0F1:0xA8DCD6D8,5+i*2,i);
        }

        int streaks=8+(int)(8*q);
        for(int i=0;i<streaks;i++){
            double angle=random.nextDouble()*Math.PI*2.0D;
            double radial=.05D+random.nextDouble()*(.15D+.11D*q);
            Vec3 side=right.scale(Math.cos(angle)*radial).add(up.scale(Math.sin(angle)*radial));
            Vec3 from=side.scale(.35D);
            Vec3 to=dir.scale(.72D+random.nextDouble()*(1.15D+1.0D*q)).add(side.scale(1.1D+random.nextDouble()*.9D));
            add(f,beam(new Vector3f((float)from.x,(float)from.y,(float)from.z),
                    new Vector3f((float)to.x,(float)to.y,(float)to.z),
                    .018f+.014f*q,0x8FE8E3E4,5+random.nextInt(4),random.nextInt(2),false));
        }

        if(acoc||q>.74f){
            int bolts=acoc?7:3;
            for(int i=0;i<bolts;i++){
                double angle=random.nextDouble()*Math.PI*2.0D;
                double radial=.16D+random.nextDouble()*(.28D+.22D*q);
                Vec3 side=right.scale(Math.cos(angle)*radial).add(up.scale(Math.sin(angle)*radial));
                Vec3 from=side.scale(.25D);
                Vec3 to=dir.scale(.55D+random.nextDouble()*(.75D+.35D*q)).add(side.scale(1.0D+random.nextDouble()*.7D));
                addHakiLightningSegment(f,from,to,6+random.nextInt(4),random.nextInt(2));
            }
        }
        return f;
    }


    /** King's Grip back-blast. This is the ONLY presentation changed by the smoke/lightning pass:
     * the authored grab/punch animations, camera timing and victim pose are untouched. The effect is
     * anchored at the victim contact point and grows only along the server-authored punch direction. */
    /**
     * The blast that tears out of the victim's back on a landed King's Grip.
     *
     * <p>Rebuilt from scratch. The previous version stacked nineteen sphere emitters of ~50-60
     * constant-size sprites each, plus hundreds of flat beam ribbons and speed lines: roughly two
     * thousand billboards that all appeared at full size and vanished together at their lifetime
     * cutoff. It read as a lumpy grey wall rather than as compressed air being punched through a
     * body.
     *
     * <p>This is built as an actual exit wound instead: a tight white-hot muzzle flash at the
     * contact point, an expanding cone whose smoke bodies grow and cool along their travel, ring
     * fronts that ride outward down the axis, and (for the coated tiers) lightning threaded
     * through the cone rather than scattered around it. It uses roughly a fifth of the emitters.
     *
     * @param q visual power; the tiers arrive as .55 physical, .96 Armament, 1.55 Advanced
     */
    private static FX kingsGripDirectionalImpactWorld(Vec3 direction,float q,boolean acoc,long seed){
        FX f=fx();
        Vec3 dir=direction.lengthSqr()<1.0E-6?new Vec3(0,0,1):direction.normalize();
        Vec3 reference=Math.abs(dir.y)<.94D?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 right=dir.cross(reference).normalize();
        Vec3 up=right.cross(dir).normalize();
        Random r=new Random(seed^0x4B1D5EEDL);
        boolean advanced=q>1.20f;
        boolean haki=q>.72f;

        double reach=advanced?13.5D:(haki?10.0D:7.2D);
        double spread=advanced?3.30D:(haki?2.45D:1.80D);
        float tier=advanced?1.55f:(haki?1.10f:.80f);

        // 1. Muzzle flash. A hard, short, white-hot core right at the exit point; this is the
        //    single frame that sells the strike passing through the body.
        ParticleEmitter flash=HakiFx.flash(9,7,.05f,.62f*tier,advanced?0xFFFFE0C0:0xFFFFFFFF);
        HakiFx.sphere(flash,.22f*tier,1f);
        HakiFx.burst(flash,12,0);
        add(f,flash);

        // 2. Exit cone. Each stage sits further down the axis, is wider, slower and cooler than
        //    the last, and swells as it travels, so the wake expands the way vented pressure does.
        int stages=advanced?9:(haki?8:6);
        for(int i=0;i<stages;i++){
            double t=(i+.35D)/stages;
            double along=.25D+reach*t;
            double radius=spread*(.16D+.92D*t);
            Vec3 centre=dir.scale(along)
                    .add(right.scale((r.nextDouble()-.5D)*radius*.35D))
                    .add(up.scale((r.nextDouble()-.5D)*radius*.28D));

            int colour;
            if(advanced) colour=i<2?0xD8564E50:(i<5?0xC44A4548:0xA8726C6C);
            else if(haki) colour=i<2?0xD05B5457:(i<5?0xBA6A6467:0x9E8B8688);
            else colour=i<2?0xC8E4E0DC:(i<5?0xB0D6D2CE:0x94F2F0EC);

            ParticleEmitter body=HakiFx.smoke(30+i*3,22+i*3,.055f+.020f*(float)(1D-t),
                    (float)(.34D+.62D*t)*tier,colour,1.85f+.35f*i,r);
            HakiFx.sphere(body,(float)radius*.82f,.62f);
            HakiFx.at(body,centre);
            HakiFx.burst(body,(advanced?26:(haki?21:16))+i*2,Math.min(8,i));
            add(f,body);
        }

        // 3. Ring fronts riding out along the axis. One billboard each, so the compression reads
        //    as continuous shells instead of a scatter of dots.
        int fronts=advanced?5:(haki?4:3);
        for(int i=0;i<fronts;i++){
            ParticleEmitter front=HakiFx.airRing(26+i*4,20+i*3,.55f*tier,
                    (4.2f+i*2.1f)*tier,i==0?0xE0FFFFFF:(haki?0xA8D8CFCF:0x9EF0EDEA));
            HakiFx.at(front,dir.scale(.55D+i*1.35D));
            HakiFx.burst(front,1,i*2);
            add(f,front);
        }

        // 4. Debris punched out of the victim's back and dragged along the cone.
        ParticleEmitter grit=HakiFx.debris(34,26,.85f*tier,.16f*tier,0xA89A918A,r);
        HakiFx.sphere(grit,.42f*tier,.70f);
        HakiFx.at(grit,dir.scale(.45D));
        HakiFx.burst(grit,advanced?90:(haki?66:44),0);
        add(f,grit);

        // 5. Coated tiers thread lightning through the cone. Threading it along the axis (rather
        //    than scattering arcs around the outside) keeps the blast reading as one directed
        //    strike instead of a general explosion.
        if(haki){
            int paths=advanced?9:6;
            for(int path=0;path<paths;path++){
                double angle=r.nextDouble()*Math.PI*2D;
                double lane=spread*(.20D+r.nextDouble()*.55D);
                Vec3 from=right.scale(Math.cos(angle)*lane*.14D).add(up.scale(Math.sin(angle)*lane*.14D));
                Vec3 to=dir.scale(reach*(.72D+r.nextDouble()*.34D))
                        .add(right.scale(Math.cos(angle)*lane))
                        .add(up.scale(Math.sin(angle)*lane*.80D));
                HakiFx.boltPath(f,from,to,advanced?7:6,.13D,tier,advanced,
                        9+r.nextInt(4),r.nextInt(2),advanced?.62f:.42f,r);
            }
        }

        // 6. Advanced keeps its hot layer, now as cooling embers blown down the cone.
        if(advanced){
            ParticleEmitter sparks=HakiFx.embers(30,24,1.55f,.10f,0xFFFFC48A,r);
            HakiFx.sphere(sparks,.55f,.60f);
            HakiFx.at(sparks,dir.scale(.60D));
            HakiFx.burst(sparks,120,0);
            add(f,sparks);
        }
        return f;
    }

    /** World position of the attacker's chambered RIGHT fist during a Haki Grip draw. The clips
     *  chamber the uppercut on the right, so the vortex has to converge there and not on the
     *  hauling hand. Yaw is pinned server-side for the whole sequence, so this stays stable. */
    private static Vec3 thraggFist(Entity attacker){
        double yaw=Math.toRadians(attacker.getYRot());
        Vec3 forward=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        Vec3 right=new Vec3(-forward.z,0,forward.x);
        return attacker.position()
                .add(right.scale(.52))
                .add(forward.scale(-.40))
                .add(0,attacker.getBbHeight()*.58,0);
    }

    /** World position of the attacker's chambered LEFT fist during a King's Grip wind-up.
     *  The attacker's yaw is pinned server-side for the whole cinematic, so this stays stable. */
    private static Vec3 kingsGripFist(Entity attacker){
        double yaw=Math.toRadians(attacker.getYRot());
        Vec3 forward=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        Vec3 right=new Vec3(-forward.z,0,forward.x);
        return attacker.position()
                .add(right.scale(-.52))
                .add(forward.scale(-.46))
                .add(0,attacker.getBbHeight()*.72,0);
    }

    /**
     * Escalating King's Grip chamber, anchored on the cocked fist.
     *
     * <p>Air is drawn <i>into</i> the fist rather than blown out of it (Photon takes a signed
     * start speed, so a negative speed on a shell shape converges instead of expanding).  That
     * inward pull is what sells a strike being loaded; the outward pressure ring underneath it
     * keeps the beat readable even at a distance.
     *
     * @param tier 0 physical, 1 Armament, 2 Advanced
     */
    /**
     * The fist's own detonation on contact: everything the chamber spent two seconds gathering
     * onto the knuckles leaves them at once. Deliberately small and dense rather than wide -- the
     * wide part of the impact is already owned by the directional air sheet and the ground
     * reaction, and stacking a third large shell on top just washed the frame out.
     */
    /**
     * Beat 1: the draw. A vortex that tightens onto the cocked fist as the target is reeled in.
     *
     * <p>Pulsed every three ticks with a rising ramp, so the wind visibly accelerates rather than
     * sitting at one strength for two seconds.
     */
    private static FX thraggDraw(Entity attacker,int tier,float power,long seed){
        FX f=fx();
        Random r=new Random(seed^0x452821E638D01377L);
        boolean haki=tier>=1;
        boolean advanced=tier>=2;
        float scale=advanced?1.55f:(haki?1.12f:.82f);
        float ramp=Math.max(.08f,Math.min(1f,power));
        float load=.35f+.65f*ramp;

        // Air torn inward from a long way out. Negative speed on an outward shape is what reads
        // as suction: the particles are spawned on the rim and travel back toward the fist.
        ParticleEmitter intake=HakiFx.smoke(18,15,-1.35f*scale*load,.34f*scale,
                advanced?0xA8201A22:(haki?0x9E302A2E:0x92C8C4C2),.75f,r);
        HakiFx.sphere(intake,4.6f*scale,.28f);
        HakiFx.burst(intake,(int)((advanced?58:(haki?44:32))*load),0);
        add(f,intake);

        // Ground debris dragged in along the floor.
        ParticleEmitter drag=HakiFx.debris(22,18,-0.95f*scale*load,.30f*scale,0x96BDB5AE,r);
        HakiFx.circle(drag,4.2f*scale,.30f);
        HakiFx.at(drag,new Vec3(0,-attacker.getBbHeight()*.72+.08,0));
        HakiFx.burst(drag,(int)((advanced?46:(haki?34:24))*load),0);
        add(f,drag);

        // Streaks spiralling in, so the wind has direction and not just density.
        int lanes=2+(int)((advanced?6:(haki?4:3))*ramp);
        for(int i=0;i<lanes;i++){
            double angle=r.nextDouble()*Math.PI*2D;
            double radius=(2.4D+r.nextDouble()*2.6D)*scale;
            double height=(r.nextDouble()-.35D)*2.2D;
            Vec3 from=new Vec3(Math.cos(angle)*radius,height,Math.sin(angle)*radius);
            Vec3 mid=from.scale(.45D).add(new Vec3(-Math.sin(angle),0,Math.cos(angle)).scale(radius*.30D));
            add(f,beam(v3(from),v3(mid),.055f*scale,advanced?0x9AE7B4FF:(haki?0x92FF9AA6:0x8AEFECE8),
                    9+r.nextInt(4),r.nextInt(3),false));
            add(f,beam(v3(mid),v3(Vec3.ZERO),.045f*scale,advanced?0xB2E7B4FF:(haki?0xA8FF9AA6:0x9AEFECE8),
                    8+r.nextInt(4),1+r.nextInt(3),false));
        }

        // The fist itself keeps loading through the draw.
        ParticleEmitter core=HakiFx.flash(14,11,.02f,(.14f+.08f*ramp)*scale,
                advanced?0xFFFFCEA8:(haki?0xFFFF4A5E:0xFFE8E4E0));
        HakiFx.sphere(core,.16f*scale,1f);
        HakiFx.burst(core,(int)((advanced?22:(haki?15:9))*load),1);
        add(f,core);
        if(haki){
            int wraps=1+(int)((advanced?4:2)*ramp);
            for(int i=0;i<wraps;i++){
                double a=r.nextDouble()*Math.PI*2D;
                double b=(r.nextDouble()-.5D)*Math.PI;
                double rad=(.26D+r.nextDouble()*.30D)*scale;
                Vec3 from=new Vec3(Math.cos(a)*Math.cos(b)*rad,Math.sin(b)*rad,Math.sin(a)*Math.cos(b)*rad);
                HakiFx.boltPath(f,from,from.scale(-.35D),3,.05D,scale*.42f,advanced,
                        5+r.nextInt(3),r.nextInt(4),advanced?.30f:.16f,r);
            }
        }
        return f;
    }

    /** Beat 2: the uppercut. Everything leaves along the launch column, straight up. */
    private static FX thraggUppercut(int tier,long seed){
        FX f=fx();
        Random r=new Random(seed^0xBE5466CF34E90C6CL);
        boolean haki=tier>=1;
        boolean advanced=tier>=2;
        float scale=advanced?1.55f:(haki?1.12f:.82f);
        double column=advanced?18.0D:(haki?13.0D:9.0D);

        ParticleEmitter flash=HakiFx.flash(7,6,.16f*scale,.70f*scale,
                advanced?0xFFFFE7C8:(haki?0xFFFFD2D8:0xFFF6F3EF));
        HakiFx.sphere(flash,.22f,1f);
        HakiFx.burst(flash,advanced?26:(haki?18:12),0);
        add(f,flash);

        // The column itself: rings climbing the path the target is about to take.
        int rings=advanced?9:(haki?7:5);
        for(int i=0;i<rings;i++){
            double t=(i+1)/(double)rings;
            float width=(float)(1.0D+2.4D*t)*scale;
            ParticleEmitter ring=HakiFx.airRing(16+i*2,12+i*2,width*.34f,width*1.5f,
                    i<2?0xE0FFFFFF:(advanced?0xB4E8B4FF:0xB4FF9AA6));
            HakiFx.at(ring,new Vec3(0,column*t*.55D,0));
            HakiFx.burst(ring,1,i*2);
            add(f,ring);
        }

        // Sparks and smoke driven off the ground by the launch.
        ParticleEmitter blow=HakiFx.smoke(24,20,1.15f*scale,.46f*scale,
                advanced?0xA8241C24:(haki?0x9E322A2E:0x92C4C0BE),1.55f,r);
        HakiFx.circle(blow,1.6f*scale,.55f);
        HakiFx.mode(blow,UPRIGHT);
        HakiFx.scatterRoll(blow,r);
        HakiFx.burst(blow,advanced?86:(haki?64:44),0);
        add(f,blow);

        // The dust ring the launch tears off the floor: flat on the ground, so it reads as ground.
        ParticleEmitter kick=HakiFx.smoke(20,17,2.2f*scale,1.1f*scale,0x96C0BCB8,1.2f,r);
        HakiFx.circle(kick,.9f*scale,.5f);
        HakiFx.mode(kick,FLAT);
        HakiFx.scatterRoll(kick,r);
        HakiFx.burst(kick,advanced?70:(haki?50:32),0);
        add(f,kick);

        ParticleEmitter shards=HakiFx.embers(22,18,1.35f*scale,.085f,
                advanced?0xFFFFB472:(haki?0xFFFF3D50:0xFFDCD6D0),r);
        HakiFx.sphere(shards,.30f*scale,1f);
        HakiFx.burst(shards,advanced?80:(haki?56:36),0);
        add(f,shards);

        if(haki){
            int bolts=advanced?9:5;
            for(int i=0;i<bolts;i++){
                double a=r.nextDouble()*Math.PI*2D;
                double spread=(.6D+r.nextDouble()*1.9D)*scale;
                Vec3 to=new Vec3(Math.cos(a)*spread,column*(.35D+r.nextDouble()*.65D),Math.sin(a)*spread);
                HakiFx.boltPath(f,Vec3.ZERO,to,6,.14D,scale*.80f,advanced,
                        8+r.nextInt(4),r.nextInt(3),advanced?.50f:.28f,r);
            }
        }
        return f;
    }

    /** Beat 3: the blink. A short, bright displacement -- deliberately small, it is a punctuation
     *  mark between two big hits, not a third one. */
    private static FX thraggBlink(int tier,long seed){
        FX f=fx();
        Random r=new Random(seed^0xC0AC29B7C97C50DDL);
        boolean advanced=tier>=2;
        float scale=advanced?1.4f:(tier>=1?1.05f:.8f);

        ParticleEmitter flash=HakiFx.flash(6,5,.42f*scale,.44f*scale,
                advanced?0xFFE7C8FF:0xFFF2EFEA);
        HakiFx.sphere(flash,.26f*scale,1f);
        HakiFx.burst(flash,advanced?20:12,0);
        add(f,flash);

        ParticleEmitter shear=HakiFx.stars(14,11,.85f*scale,.055f,
                advanced?0xFFD86CFF:0xFFCFCBC6);
        HakiFx.sphere(shear,.40f*scale,.70f);
        HakiFx.burst(shear,advanced?34:20,0);
        add(f,shear);

        ParticleEmitter ring=HakiFx.airRing(12,10,.20f*scale,2.1f*scale,
                advanced?0xC8E8B4FF:0xAAEFECE8);
        HakiFx.burst(ring,1,0);
        add(f,ring);
        if(advanced){
            for(int i=0;i<4;i++){
                double a=r.nextDouble()*Math.PI*2D;
                Vec3 to=new Vec3(Math.cos(a)*1.5D,(r.nextDouble()-.5D)*1.2D,Math.sin(a)*1.5D);
                HakiFx.boltPath(f,Vec3.ZERO,to,4,.10D,scale*.5f,true,6+r.nextInt(3),r.nextInt(2),.35f,r);
            }
        }
        return f;
    }

    /** Beat 4: the double hammer, driven straight down onto the back of their head. */
    private static FX thraggSlam(int tier,long seed){
        FX f=fx();
        Random r=new Random(seed^0x9216D5D98979FB1BL);
        boolean haki=tier>=1;
        boolean advanced=tier>=2;
        float scale=advanced?1.5f:(haki?1.10f:.82f);

        ParticleEmitter flash=HakiFx.flash(6,5,.12f*scale,.50f*scale,
                advanced?0xFFFFE7C8:(haki?0xFFFFD2D8:0xFFF6F3EF));
        HakiFx.sphere(flash,.14f,1f);
        HakiFx.burst(flash,advanced?20:(haki?14:9),0);
        add(f,flash);

        // A flat plate of displaced air at the contact, spreading sideways.
        ParticleEmitter plate=HakiFx.airRing(14,11,.24f*scale,3.4f*scale,
                advanced?0xD2E8B4FF:(haki?0xC8FFA8B2:0xB0EFECE8));
        HakiFx.burst(plate,1,0);
        add(f,plate);

        ParticleEmitter shards=HakiFx.embers(18,15,1.05f*scale,.075f,
                advanced?0xFFFFB472:(haki?0xFFFF3D50:0xFFDCD6D0),r);
        HakiFx.sphere(shards,.20f*scale,1f);
        HakiFx.burst(shards,advanced?66:(haki?46:30),0);
        add(f,shards);

        // Downward smoke, following them into the floor.
        ParticleEmitter wake=HakiFx.smoke(22,19,-0.85f*scale,.36f*scale,
                advanced?0xA8241C24:(haki?0x9E322A2E:0x92C4C0BE),1.30f,r);
        HakiFx.sphere(wake,.55f*scale,.60f);
        HakiFx.mode(wake,UPRIGHT);
        HakiFx.scatterRoll(wake,r);
        HakiFx.burst(wake,advanced?58:(haki?42:28),1);
        add(f,wake);

        // The pressure plate driven out sideways by the hammer, held flat.
        ParticleEmitter plateDust=HakiFx.smoke(18,15,2.6f*scale,.85f*scale,0xA0CFCBC6,1.15f,r);
        HakiFx.circle(plateDust,.5f*scale,.4f);
        HakiFx.mode(plateDust,FLAT);
        HakiFx.scatterRoll(plateDust,r);
        HakiFx.burst(plateDust,advanced?60:(haki?42:26),0);
        add(f,plateDust);

        int arcs=advanced?10:(haki?6:2);
        for(int i=0;i<arcs;i++){
            double a=r.nextDouble()*Math.PI*2D;
            double rad=(.7D+r.nextDouble()*1.5D)*scale;
            Vec3 to=new Vec3(Math.cos(a)*rad,-(.4D+r.nextDouble()*2.0D),Math.sin(a)*rad);
            HakiFx.boltPath(f,Vec3.ZERO,to,5,.12D,scale*.65f,advanced,
                    7+r.nextInt(4),r.nextInt(2),advanced?.48f:.26f,r);
        }
        return f;
    }

    /**
     * The flight itself: burning on the way up, a wake on the way down.
     *
     * <p>Pulsed every four ticks for the whole two-and-a-half-second flight, because a single
     * burst at the launch is long gone before they reach the top. On the way up the fire streams
     * off them <b>downward</b> -- flames trail behind motion, so a rising body burns toward the
     * floor it left. On the way down the same layers invert and lengthen into a dive wake.
     */
    private static FX thraggTrail(int tier,boolean falling,float span,long seed){
        FX f=fx();
        Random r=new Random(seed^0xD1310BA698DFB5ACL);
        boolean haki=tier>=1;
        boolean advanced=tier>=2;
        float scale=advanced?1.55f:(haki?1.15f:.85f);
        // Fire is fiercest at the launch and at the moment of arrival, thinnest at the apex.
        float heat=falling?(.55f+.45f*span):(1.05f-.45f*span);
        // Trailing direction: opposite the travel, so it always streams away from the impact.
        float streak=falling?.95f:-1.35f;

        ParticleEmitter fire=HakiFx.embers(20,17,streak*scale*heat,.18f*scale*heat,
                advanced?0xFFFFC98A:(haki?0xFFFF6A3C:0xFFFF9A4E),r);
        HakiFx.sphere(fire,.52f*scale,.85f);
        HakiFx.mode(fire,UPRIGHT);
        HakiFx.burst(fire,(int)((advanced?38:(haki?28:19))*heat),0);
        add(f,fire);

        // A hotter, tighter core wrapped right on the body.
        ParticleEmitter core=HakiFx.flash(14,11,streak*scale*.45f,.13f*scale*heat,
                advanced?0xFFFFE0B4:(haki?0xFFFFB07A:0xFFFFD4A0));
        HakiFx.sphere(core,.30f*scale,1f);
        HakiFx.burst(core,(int)((advanced?16:(haki?11:7))*heat),0);
        add(f,core);

        // Smoke lagging behind the fire, so the column has a body and not just sparks.
        ParticleEmitter smoke=HakiFx.smoke(26,22,streak*scale*.55f,.38f*scale,
                advanced?0x9A2A2028:(haki?0x8E322A2E:0x86C4C0BE),1.45f,r);
        HakiFx.sphere(smoke,.62f*scale,.70f);
        HakiFx.mode(smoke,UPRIGHT);
        HakiFx.scatterRoll(smoke,r);
        HakiFx.burst(smoke,(int)((advanced?34:(haki?24:16))*heat),1);
        add(f,smoke);

        if(haki){
            // Arcs crawling along the body, more of them the further they have travelled.
            int arcs=1+(int)((advanced?4:2)*(falling?span:1f-span*.5f));
            for(int i=0;i<arcs;i++){
                double a=r.nextDouble()*Math.PI*2D;
                double rad=(.35D+r.nextDouble()*.55D)*scale;
                Vec3 from=new Vec3(Math.cos(a)*rad,.55D,Math.sin(a)*rad);
                Vec3 to=new Vec3(Math.cos(a)*rad*.4D,-.75D,Math.sin(a)*rad*.4D);
                HakiFx.boltPath(f,from,to,4,.10D,scale*.5f,advanced,
                        6+r.nextInt(3),r.nextInt(3),advanced?.34f:.18f,r);
            }
        }
        return f;
    }

    /**
     * Beat 5: arrival.
     *
     * <p>Rebuilt as volume rather than as three enormous flat rings. The old version drew shock
     * rings up to forty-eight blocks across as single billboards: from anywhere near the crater you
     * were <b>inside</b> one, so the explosion read as a coloured hoop hanging in the sky rather
     * than as anything hitting the ground. Two things fix that, and both matter:
     *
     * <ul>
     *   <li><b>Many small particles instead of one huge sprite.</b> A ring made of a hundred motes
     *       travelling outward occupies the same space but has parallax, so it reads as a wave
     *       crossing the ground. A single quad that size can only ever be a flat shape.</li>
     *   <li><b>Real geometry.</b> Beams are oriented quads in world space rather than billboards,
     *       so the debris pillars and ground lashes keep their direction as the camera swings
     *       around the crater, which is what actually sells depth.</li>
     * </ul>
     */
    private static FX thraggCrater(int tier,long seed){
        FX f=fx();
        Random r=new Random(seed^0x3F84D5B5B5470917L);
        boolean haki=tier>=1;
        boolean advanced=tier>=2;
        // Matches HakiServerController's crater radius: 5.5 / 9 / 14 blocks.
        float reach=advanced?14f:(haki?9f:5.5f);

        // Core flash, kept small: the light is the event, not the shape.
        ParticleEmitter flash=HakiFx.flash(9,7,.35f,2.2f+reach*.10f,
                advanced?0xFFFFE7C8:(haki?0xFFFFD2D8:0xFFF6F3EF));
        HakiFx.sphere(flash,.35f,1f);
        HakiFx.burst(flash,advanced?30:(haki?22:14),0);
        add(f,flash);

        // The shock wave, lying ON the floor. Horizontal mode is the whole trick: these quads are
        // flat against the ground and stay there, so walking around the crater changes what you
        // see, which is the definition of not-flat. As billboards the same particles turned to
        // face you and the wave collapsed into a disc pointed at the camera.
        for(int i=0;i<3;i++){
            ParticleEmitter wave=HakiFx.smoke(24+i*4,20+i*4,reach*(.26f-i*.04f),1.15f+i*.55f,
                    i==0?0xC8FFFFFF:(advanced?0x9AE8B4FF:0x9AFF9AA6),1.25f,r);
            HakiFx.circle(wave,.8f+i*1.1f,.35f);
            HakiFx.mode(wave,FLAT);
            HakiFx.scatterRoll(wave,r);
            HakiFx.burst(wave,advanced?110:(haki?76:46),i*3);
            add(f,wave);
        }

        // Scorch left on the ground under it, also flat.
        ParticleEmitter scorch=HakiFx.smoke(34,30,reach*.05f,1.9f,0xA6120E10,.85f,r);
        HakiFx.circle(scorch,reach*.35f,1f);
        HakiFx.mode(scorch,FLAT);
        HakiFx.scatterRoll(scorch,r);
        HakiFx.burst(scorch,advanced?60:(haki?42:26),0);
        add(f,scorch);

        // Dust dome. VerticalBillboard turns about Y only, so each puff keeps its own facing as
        // the camera swings -- the difference between a cloud and a decal.
        ParticleEmitter dome=HakiFx.smoke(32,27,reach*.14f,1.9f,
                advanced?0xB2241C24:(haki?0xA6322A2E:0x9AC4C0BE),1.75f,r);
        HakiFx.sphere(dome,1.6f,.55f);
        HakiFx.mode(dome,UPRIGHT);
        HakiFx.scatterRoll(dome,r);
        HakiFx.burst(dome,advanced?190:(haki?125:74),0);
        add(f,dome);

        // A second, taller column of dust standing in the middle of it.
        ParticleEmitter column=HakiFx.smoke(36,32,.30f,2.3f,
                advanced?0xA8241C24:(haki?0x9C322A2E:0x90C4C0BE),1.9f,r);
        HakiFx.circle(column,reach*.16f,.9f);
        HakiFx.mode(column,UPRIGHT);
        HakiFx.scatterRoll(column,r);
        HakiFx.burst(column,advanced?90:(haki?60:34),2);
        add(f,column);

        // Rubble on real arcs.
        ParticleEmitter rubble=HakiFx.debris(38,32,reach*.20f,.34f,0xB4A79C90,r);
        HakiFx.sphere(rubble,1.1f,.9f);
        HakiFx.gravity(rubble,.058f);
        HakiFx.burst(rubble,advanced?160:(haki?105:62),0);
        add(f,rubble);

        ParticleEmitter shards=HakiFx.embers(28,23,reach*.24f,.13f,
                advanced?0xFFFFB472:(haki?0xFFFF3D50:0xFFDCD6D0),r);
        HakiFx.sphere(shards,.5f,1f);
        HakiFx.burst(shards,advanced?150:(haki?100:58),0);
        add(f,shards);

        // Everything that has been burning the whole way down arrives at once. Upright, like a
        // real fire: flames do not lie down to face the viewer.
        ParticleEmitter fire=HakiFx.embers(26,22,reach*.16f,.52f,
                advanced?0xFFFFC98A:(haki?0xFFFF6A3C:0xFFFF9A4E),r);
        HakiFx.sphere(fire,1.3f,.8f);
        HakiFx.mode(fire,UPRIGHT);
        HakiFx.burst(fire,advanced?150:(haki?98:56),0);
        add(f,fire);

        // Debris pillars around the rim: true geometry, so they hold their direction as the camera
        // swings around the crater instead of turning to face it.
        int pillars=advanced?16:(haki?11:7);
        for(int i=0;i<pillars;i++){
            double a=Math.PI*2D*i/pillars+r.nextDouble()*.35D;
            double at=reach*(.35D+r.nextDouble()*.55D);
            Vec3 foot=new Vec3(Math.cos(a)*at,0,Math.sin(a)*at);
            Vec3 top=foot.add(new Vec3((r.nextDouble()-.5D)*1.2D,
                    2.2D+r.nextDouble()*(advanced?5.5D:3.0D),(r.nextDouble()-.5D)*1.2D));
            add(f,beam(v3(foot),v3(top),.28f+r.nextFloat()*.22f,
                    advanced?0xA8342830:0x9E3A3436,16+r.nextInt(8),r.nextInt(6),false));
        }

        if(haki){
            // Ground lightning crawling outward from the crater, along the floor and up the rim.
            int lashes=advanced?22:12;
            for(int i=0;i<lashes;i++){
                double a=Math.PI*2D*i/lashes+r.nextDouble()*.4D;
                double at=reach*(.55D+r.nextDouble()*.45D);
                Vec3 to=new Vec3(Math.cos(a)*at,.25D+r.nextDouble()*1.4D,Math.sin(a)*at);
                HakiFx.boltPath(f,new Vec3(0,.15D,0),to,7,.20D,advanced?1.5f:1.1f,advanced,
                        10+r.nextInt(5),r.nextInt(4),advanced?.60f:.32f,r);
            }
        }
        return f;
    }

    private static FX kingsGripFistDetonation(int tier,long seed){
        FX f=fx();
        Random r=new Random(seed^0x1F83D9ABFB41BD6BL);
        boolean haki=tier>=1;
        boolean advanced=tier>=2;
        float scale=advanced?1.45f:(haki?1.05f:.78f);

        // White-hot flash on the knuckles, gone in a few ticks.
        ParticleEmitter flash=HakiFx.flash(6,5,.10f*scale,.42f*scale,
                advanced?0xFFFFE7C8:(haki?0xFFFFD2D8:0xFFF6F3EF));
        HakiFx.sphere(flash,.10f,1f);
        HakiFx.burst(flash,advanced?18:(haki?13:9),0);
        add(f,flash);

        // Sparks thrown off the contact along every axis.
        ParticleEmitter shards=HakiFx.embers(16,13,.92f*scale,.070f,
                advanced?0xFFFFB472:(haki?0xFFFF3D50:0xFFDCD6D0),r);
        HakiFx.sphere(shards,.16f*scale,1f);
        HakiFx.burst(shards,advanced?64:(haki?46:30),0);
        add(f,shards);

        // A short cone of smoke driven off the knuckles as the air is displaced.
        ParticleEmitter blow=HakiFx.smoke(20,17,.62f*scale,.30f*scale,
                advanced?0xA8241C24:(haki?0x9E322A2E:0x92C4C0BE),1.35f,r);
        HakiFx.sphere(blow,.30f*scale,.70f);
        HakiFx.burst(blow,advanced?40:(haki?30:22),1);
        add(f,blow);

        // Tight shockring right at the hand, so the contact has a readable frame of its own.
        ParticleEmitter ring=HakiFx.airRing(14,11,.22f*scale,1.45f*scale,
                advanced?0xC8E8B4FF:(haki?0xC0FFA8B2:0xAAEFECE8));
        HakiFx.burst(ring,1,0);
        add(f,ring);

        int arcs=advanced?8:(haki?5:2);
        for(int i=0;i<arcs;i++){
            double a=r.nextDouble()*Math.PI*2D;
            double b=(r.nextDouble()-.5D)*Math.PI;
            double rad=(.55D+r.nextDouble()*.95D)*scale;
            Vec3 to=new Vec3(Math.cos(a)*Math.cos(b)*rad,Math.sin(b)*rad,Math.sin(a)*Math.cos(b)*rad);
            HakiFx.boltPath(f,Vec3.ZERO,to,4,.10D,scale*.60f,advanced,
                    6+r.nextInt(4),r.nextInt(2),advanced?.45f:.24f,r);
        }
        return f;
    }

    private static FX kingsGripWindupWorld(Entity attacker,int tier,float power,long seed){
        FX f=fx();
        Random r=new Random(seed^0x6A09E667F3BCC909L);
        boolean haki=tier>=1;
        boolean advanced=tier>=2;
        float scale=advanced?1.55f:(haki?1.12f:.82f);
        // The chamber pulses every eight ticks with a rising ramp. Density has to ride that ramp,
        // or every pulse looks the same and the fist never reads as loading toward anything.
        float ramp=Math.max(.10f,Math.min(1f,power));
        float load=.45f+.55f*ramp;

        // Compressed air converging on the fist.
        ParticleEmitter intake=HakiFx.smoke(20,16,-0.34f*scale,.26f*scale,
                advanced?0xB2201A22:(haki?0xA8302A2E:0x9AC8C4C2),.55f,r);
        HakiFx.sphere(intake,1.35f*scale,.30f);
        HakiFx.burst(intake,(int)((advanced?54:(haki?42:30))*load),0);
        add(f,intake);

        // Hot core building inside the fist itself.
        ParticleEmitter core=HakiFx.flash(16,12,.02f,(.16f+.07f*ramp)*scale,
                advanced?0xFFFFCEA8:(haki?0xFFFF4A5E:0xFFE8E4E0));
        HakiFx.sphere(core,.16f*scale,1f);
        HakiFx.burst(core,(int)((advanced?26:(haki?18:11))*load),1);
        add(f,core);

        // Tight orbiting swarm right on the knuckles. This is the layer the fist was missing:
        // the old chamber gathered air from a metre out but left the hand itself bare.
        ParticleEmitter swarm=HakiFx.embers(18,14,.06f*scale,.055f+.030f*ramp,
                advanced?0xFFFFC98A:(haki?0xFFFF5E6C:0xFFD8D2CC),r);
        HakiFx.sphere(swarm,(.30f+.10f*ramp)*scale,.85f);
        HakiFx.spin(swarm,advanced?26f:16f);
        HakiFx.burst(swarm,(int)((advanced?34:(haki?26:18))*load),0);
        add(f,swarm);

        // A shell that collapses onto the knuckles, so each pulse visibly tightens.
        ParticleEmitter collapse=HakiFx.stars(16,13,-0.55f*scale*load,.05f+.025f*ramp,
                advanced?0xFFE7B4FF:(haki?0xFFFF9AA6:0xFFF2EFEA));
        HakiFx.sphere(collapse,(.85f+.35f*ramp)*scale,.16f);
        HakiFx.burst(collapse,(int)((advanced?30:(haki?22:15))*load),1);
        add(f,collapse);

        // Ground-level dust dragged toward the attacker as the pressure builds.
        ParticleEmitter drag=HakiFx.debris(24,20,-0.22f*scale,.30f*scale,0x96BDB5AE,r);
        HakiFx.circle(drag,2.10f*scale,.24f);
        HakiFx.at(drag,new Vec3(0,-attacker.getBbHeight()*.72+.08,0));
        HakiFx.burst(drag,(int)((advanced?46:(haki?34:24))*load),0);
        add(f,drag);

        // Short arcs wrapping the knuckles themselves. Every tier gets these; only the ground
        // bolts below stay gated, because those are the ones that read as Haki rather than force.
        int wraps=1+(int)((advanced?5:(haki?4:2))*ramp);
        for(int i=0;i<wraps;i++){
            double a=r.nextDouble()*Math.PI*2D;
            double b=(r.nextDouble()-.5D)*Math.PI;
            double rad=(.26D+r.nextDouble()*.30D)*scale;
            Vec3 from=new Vec3(Math.cos(a)*Math.cos(b)*rad,Math.sin(b)*rad,Math.sin(a)*Math.cos(b)*rad);
            HakiFx.boltPath(f,from,from.scale(-.35D),3,.05D,scale*.42f,advanced,
                    5+r.nextInt(3),r.nextInt(4),advanced?.30f:.16f,r);
        }

        if(haki){
            // Bolts crawl up out of the ground and into the loaded fist.
            int paths=(int)Math.max(2,(advanced?7:4)*load);
            for(int i=0;i<paths;i++){
                double angle=r.nextDouble()*Math.PI*2D;
                double radius=.85D+r.nextDouble()*1.65D*scale;
                Vec3 from=new Vec3(Math.cos(angle)*radius,
                        -attacker.getBbHeight()*.72+.05,
                        Math.sin(angle)*radius);
                HakiFx.boltPath(f,from,Vec3.ZERO,5,.16D,scale*.85f,advanced,
                        7+r.nextInt(4),r.nextInt(3),advanced?.55f:.32f,r);
            }
        }
        if(advanced){
            ParticleEmitter sparks=HakiFx.embers(20,15,.42f,.075f,0xFFFFB472,r);
            HakiFx.sphere(sparks,.62f,.55f);
            HakiFx.burst(sparks,(int)(34*load),1);
            add(f,sparks);
        }
        return f;
    }

    /**
     * Floor fracture and dust displacement under a landed King's Grip.
     *
     * <p>Rendered flat against the ground (a horizontal billboard rather than a camera-facing
     * one) so the crack reads as damage to the world instead of a decal hanging in the air.
     */
    private static FX kingsGripGroundReaction(int tier,long seed){
        FX f=fx();
        Random r=new Random(seed^0xBB67AE8584CAA73BL);
        boolean haki=tier>=1;
        boolean advanced=tier>=2;
        float scale=advanced?2.35f:(haki?1.60f:1.15f);

        ParticleEmitter fracture=HakiFx.additive(
                HakiFx.emitter(HakiFx.CRACK,26,22,0f,2.30f*scale,advanced?0xC8FF7A5A:0xB8FFFFFF,true));
        HakiFx.mode(fracture,com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting.Particle.Mode.Horizontal);
        HakiFx.size(fracture,HakiCurves.expand(1.30f));
        HakiFx.color(fracture,HakiCurves.fadeOut());
        HakiFx.at(fracture,new Vec3(0,.06,0));
        fracture.config.setMaxParticles(8);
        HakiFx.burst(fracture,1,0);
        add(f,fracture);

        ParticleEmitter ring=HakiFx.groundRing(30,24,1.15f*scale,4.20f,
                advanced?0xE0FFC8A8:(haki?0xD8FFD2D2:0xC8FFFFFF));
        HakiFx.at(ring,new Vec3(0,.10,0));
        HakiFx.burst(ring,1,0);
        add(f,ring);
        ParticleEmitter ring2=HakiFx.groundRing(34,28,.85f*scale,5.60f,0x8CE8E2DC);
        HakiFx.at(ring2,new Vec3(0,.14,0));
        HakiFx.burst(ring2,1,3);
        add(f,ring2);

        ParticleEmitter kick=HakiFx.debris(34,28,.62f*scale,.42f*scale,0xAAB0A79E,r);
        HakiFx.circle(kick,.85f*scale,.55f);
        HakiFx.at(kick,new Vec3(0,.12,0));
        HakiFx.burst(kick,advanced?110:(haki?78:52),0);
        add(f,kick);
        return f;
    }

    private static void addDirectionalPressureRing(FX f,Vec3 center,Vec3 right,Vec3 up,double radius,
                                                    int segments,float width,int color,int duration,int delay){
        Vec3 previous=center.add(right.scale(radius));
        for(int i=1;i<=segments;i++){
            double angle=Math.PI*2.0D*i/segments;
            Vec3 next=center.add(right.scale(Math.cos(angle)*radius)).add(up.scale(Math.sin(angle)*radius));
            add(f,beam(new Vector3f((float)previous.x,(float)previous.y,(float)previous.z),
                    new Vector3f((float)next.x,(float)next.y,(float)next.z),width,color,duration,delay,false));
            previous=next;
        }
    }

    /** Vanilla air motes are only a readability layer. Every mote begins at the contact disc and its
     * velocity has a strong positive component along the authoritative punch direction. */
    private static void spawnDirectionalAir(Minecraft mc,Vec3 origin,Vec3 direction,float q,long seed){
        if(mc.level==null)return;
        Vec3 dir=direction.lengthSqr()<1.0E-6?new Vec3(0,0,1):direction.normalize();
        Vec3 reference=Math.abs(dir.y)<.94D?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 right=dir.cross(reference).normalize();
        Vec3 up=right.cross(dir).normalize();
        Random random=new Random(seed^0xA24BAED4963EE407L);
        int count=26+(int)(30*q);
        for(int i=0;i<count;i++){
            double angle=random.nextDouble()*Math.PI*2.0D;
            double spread=Math.sqrt(random.nextDouble())*(.08D+.16D*q);
            Vec3 radial=right.scale(Math.cos(angle)*spread).add(up.scale(Math.sin(angle)*spread));
            Vec3 pos=origin.add(radial).subtract(dir.scale(random.nextDouble()*.05D));
            double forward=.18D+random.nextDouble()*(.23D+.16D*q);
            Vec3 velocity=dir.scale(forward).add(radial.scale(.10D+random.nextDouble()*.18D));
            mc.level.addParticle((i%5)==0?ParticleTypes.CLOUD:ParticleTypes.POOF,
                    pos.x,pos.y,pos.z,velocity.x,velocity.y,velocity.z);
        }
    }

    private static FX pressureHit(float q,long seed){
        FX f=internalPulse(Math.max(.5f,q));
        ParticleEmitter dust=particle(12,13,.9f+1.4f*q,.038f,0x99BDB5B4,false);
        Circle circle=new Circle();circle.setRadius(.22f);circle.setRadiusThickness(.03f);dust.config.shape.setShape(circle);dust.config.shape.setPosition(new NumberFunction3(0,.06,0));
        burst(dust,32+(int)(42*q),1);add(f,dust);
        if(q>.55f)lightningFamilies(f,.35f+.35f*q,0xCB070508,0xFFFF2A44,2+(int)(q*3),2,4,new Random(seed));
        return f;
    }

    /** Conqueror-specific hit bloom using only the clean transparent-edge Haoshoku sprite.
     *  This avoids the faint square corners inherited from the old generic soft texture. */
    private static FX conquerorPressureHit(float q,long seed){
        FX f=fx();
        ParticleEmitter core=conquerorParticle(9,9,.28f,.105f,0xEEFFF3F1,true);
        Sphere cs=new Sphere();cs.setRadius(.18f);cs.setRadiusThickness(.12f);core.config.shape.setShape(cs);core.config.shape.setPosition(new NumberFunction3(0,1,0));burst(core,22,0);add(f,core);

        ParticleEmitter red=conquerorParticle(16,14,.72f+.65f*q,.075f,0xCCDF1D36,true);
        Sphere rs=new Sphere();rs.setRadius(.30f);rs.setRadiusThickness(.075f);red.config.shape.setShape(rs);red.config.shape.setPosition(new NumberFunction3(0,1,0));burst(red,38+(int)(30*q),2);add(f,red);

        ParticleEmitter black=conquerorParticle(18,15,.42f,.050f,0xDD09070B,false);
        Sphere bs=new Sphere();bs.setRadius(.48f);bs.setRadiusThickness(.22f);black.config.shape.setShape(bs);black.config.shape.setPosition(new NumberFunction3(0,1,0));burst(black,16+(int)(18*q),3);add(f,black);

        ParticleEmitter dust=conquerorParticle(12,13,.9f+1.4f*q,.038f,0x99BDB5B4,false);
        Circle circle=new Circle();circle.setRadius(.22f);circle.setRadiusThickness(.03f);dust.config.shape.setShape(circle);dust.config.shape.setPosition(new NumberFunction3(0,.06,0));
        burst(dust,32+(int)(42*q),1);add(f,dust);
        if(q>.55f)lightningFamilies(f,.35f+.35f*q,0xCB070508,0xFFFF2A44,2+(int)(q*3),2,4,new Random(seed));
        return f;
    }
    private static FX observationPulse(float q){
        FX f=fx();
        ParticleEmitter ring=particle(14,13,.70f,.028f,0xFFB7E4F0,true);
        Circle c=new Circle();c.setRadius(.24f);c.setRadiusThickness(.025f);ring.config.shape.setShape(c);ring.config.shape.setPosition(new NumberFunction3(0,.10,0));burst(ring,44,0);add(f,ring);
        ParticleEmitter motes=particle(16,14,.20f,.022f,0xAADAF6FF,true);
        Sphere s=new Sphere();s.setRadius(.72f);s.setRadiusThickness(.30f);motes.config.shape.setShape(s);motes.config.shape.setPosition(new NumberFunction3(0,1.0,0));burst(motes,20,2);add(f,motes);
        return f;
    }

    /** World-space tap-wave composition. All geometry is generated from the packet's exact direction
     * instead of relying on EntityEffect auto-rotation. Local FX coordinates equal world offsets
     * because the BlockEffect is anchored at the authoritative server start point. */
    /**
     * Galaxy Impact: Haki Wave -- the M tap.
     *
     * <p>Completely rebuilt. The previous version drew its pressure rings as <b>wireframe
     * polygons</b>: up to seventeen slices of twelve flat beam segments each, roughly four hundred
     * hard-edged bars forming a low-poly cylinder, plus more bars for the lanes. It read as
     * exposed mesh rather than as compressed air.
     *
     * <p>This is built as an actual air lance instead: a smooth tapered core beam drawn with the
     * dedicated lance sprite, a smoke sleeve that widens into a cone along the shot, ring fronts
     * riding outward, and helical vortex lines wrapping the shaft to show the air spinning. The
     * Haki tiers thread bolts through the shaft and Advanced adds embers, but the shape of the
     * blast no longer depends on drawing its own geometry outlines.
     *
     * @param charge stored ground charge, 0..1, which lengthens and thickens the lance
     */
    private static FX galaxyWaveWorld(Vec3 start,Vec3 end,float q,float charge,float exactRadius,boolean joy,int hakiTier,long seed){
        FX f=fx();
        float mastery=Math.max(.5f,Math.min(1f,q));
        float trained=Math.max(0f,Math.min(1f,(mastery-.5f)/.5f));
        float charged=Math.max(0f,Math.min(.99f,charge));
        Vec3 delta=end.subtract(start);
        double length=delta.length();
        if(length<.01D) return f;
        Vec3 dir=delta.normalize();
        Vec3 reference=Math.abs(dir.y)<.94D?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 right=dir.cross(reference).normalize();
        Vec3 up=right.cross(dir).normalize();
        float radius=Math.max(2.9f,exactRadius);
        Random r=new Random(seed);
        boolean haki=hakiTier>=1;
        boolean advanced=hakiTier>=2;

        // 1. Muzzle flash at the fist.
        ParticleEmitter flash=HakiFx.flash(10,8,.06f,.44f+.20f*charged,0xFFFFFFFF);
        HakiFx.sphere(flash,.20f+.10f*trained,1f);
        HakiFx.burst(flash,14,0);
        add(f,flash);

        // 2. The lance itself: layered tapered beams along the exact authoritative axis. The
        //    lance sprite carries the soft sheath and the end taper, so no ring outlines are
        //    needed to describe the shaft.
        float coreWidth=radius*(.26f+.10f*trained+.10f*charged);
        add(f,HakiFx.beam(v3(Vec3.ZERO),v3(dir.scale(length)),coreWidth*1.55f,
                haki?0x66301018:0x60C8CCCE,9,0,false,HakiFx.LANCE));
        add(f,HakiFx.beam(v3(Vec3.ZERO),v3(dir.scale(length)),coreWidth,
                haki?0xC8FF4A50:0xC8EFF4F5,8,0,true,HakiFx.LANCE));
        add(f,HakiFx.beam(v3(Vec3.ZERO),v3(dir.scale(length*.96D)),coreWidth*.34f,
                0xF2FFFFFF,7,0,true,HakiFx.LANCE));

        // 3. Smoke sleeve widening into a cone. This is what gives the blast volume; it replaces
        //    the wireframe rings entirely.
        int stages=7+(int)(3*trained);
        for(int i=0;i<stages;i++){
            double t=(i+.4D)/stages;
            double along=length*t;
            float sleeve=(float)(radius*(.22D+.62D*t));
            int colour=haki?(i<3?0xB4433C40:0x9A6A6468):(i<3?0xAEE2DEDA:0x92F2EFEC);
            ParticleEmitter sleeveSmoke=HakiFx.smoke(24+i*2,18+i*2,.075f,sleeve*.52f,colour,1.75f,r);
            HakiFx.sphere(sleeveSmoke,sleeve*.72f,.55f);
            HakiFx.at(sleeveSmoke,dir.scale(along));
            HakiFx.burst(sleeveSmoke,14+i*2,Math.min(6,i));
            add(f,sleeveSmoke);
        }

        // 4. Ring fronts running down the shaft.
        int fronts=4+(int)(2*charged);
        for(int i=0;i<fronts;i++){
            ParticleEmitter front=HakiFx.airRing(24+i*3,18+i*3,radius*.42f,
                    radius*(1.35f+.35f*i),i==0?0xE4FFFFFF:(haki?0xA6FFB0B4:0x9EE8E5E2));
            HakiFx.at(front,dir.scale(length*(.18D+.20D*i)));
            HakiFx.burst(front,1,i*2);
            add(f,front);
        }

        // 5. Helical vortex lines wrapping the shaft: the air is spinning, and a helix shows that
        //    far better than stacked ring outlines ever did.
        int helices=3+(int)(2*trained);
        for(int h=0;h<helices;h++){
            double phase=Math.PI*2D*h/helices;
            int steps=10;
            Vec3 previous=null;
            for(int i=0;i<=steps;i++){
                double t=i/(double)steps;
                double turn=phase+t*(5.2D+1.8D*charged);
                double spin=radius*(.30D+.52D*t);
                Vec3 point=dir.scale(length*t)
                        .add(right.scale(Math.cos(turn)*spin))
                        .add(up.scale(Math.sin(turn)*spin));
                if(previous!=null){
                    add(f,HakiFx.beam(v3(previous),v3(point),.055f+.030f*trained,
                            haki?0x8CFFB2B8:0x8CE6EAEC,7,Math.min(5,i/2),true,HakiFx.LANCE));
                }
                previous=point;
            }
        }

        // 6. Coating threads bolts through the shaft. Without a coating the tap stays a clean
        //    white pressure punch, exactly as before.
        if(haki){
            int bolts=5+(int)(4*trained)+(int)(4*charged)+(joy?2:0);
            for(int i=0;i<bolts;i++){
                double angle=r.nextDouble()*Math.PI*2D;
                double lane=radius*(.24D+r.nextDouble()*.58D);
                Vec3 from=right.scale(Math.cos(angle)*lane*.18D).add(up.scale(Math.sin(angle)*lane*.18D));
                Vec3 to=dir.scale(length*(.86D+r.nextDouble()*.16D))
                        .add(right.scale(Math.cos(angle)*lane))
                        .add(up.scale(Math.sin(angle)*lane));
                HakiFx.boltPath(f,from,to,7,.09D,.85f+.45f*trained,advanced,
                        8+r.nextInt(3),r.nextInt(2),advanced?.52f:.34f,r);
            }
        }

        // 7. Advanced adds heat rather than merely more bolts.
        if(advanced){
            ParticleEmitter heat=HakiFx.embers(26,20,.95f,.085f,0xFFFFB48C,r);
            HakiFx.sphere(heat,radius*.42f,.62f);
            HakiFx.at(heat,dir.scale(length*.42D));
            HakiFx.burst(heat,70+(int)(40*charged),1);
            add(f,heat);
        }

        // 8. Terminal burst where the lance lands.
        ParticleEmitter terminal=HakiFx.flash(16,12,.55f,radius*.30f,haki?0xFFFFC8C0:0xFFFFFFFF);
        HakiFx.sphere(terminal,radius*.26f,.70f);
        HakiFx.at(terminal,dir.scale(length));
        HakiFx.burst(terminal,26,2);
        add(f,terminal);

        ParticleEmitter terminalSmoke=HakiFx.smoke(34,28,.85f,radius*.34f,
                haki?0xB0514A4C:0xA8E0DCD8,2.05f,r);
        HakiFx.sphere(terminalSmoke,radius*.34f,.60f);
        HakiFx.at(terminalSmoke,dir.scale(length));
        HakiFx.burst(terminalSmoke,44,2);
        add(f,terminalSmoke);
        return f;
    }

    private static Vector3f v3(Vec3 v){return new Vector3f((float)v.x,(float)v.y,(float)v.z);}

    /** Armament-500 tap: a long, nearly constant-width pressure cylinder aimed with the player. */
    private static FX convergencePunch(float q,boolean joy,long seed){
        FX f=fx();
        float mastery=Math.max(.5f,Math.min(1f,q));
        float trained=Math.max(0f,Math.min(1f,(mastery-.5f)/.5f));
        float length=15f+5f*trained;
        float radius=2.9f+1.1f*trained;
        Vector3f fist=new Vector3f(.42f,1.18f,-.42f);
        Random r=new Random(seed);

        // The emission starts visibly on the fist, then becomes a tube rather than a cone.
        ParticleEmitter flash=particle(10,10,1.7f+.9f*trained,.120f,0xFAFFFDF7,true);
        Sphere fs=new Sphere();fs.setRadius(.18f+.08f*trained);fs.setRadiusThickness(.20f);
        flash.config.shape.setShape(fs);flash.config.shape.setPosition(new NumberFunction3(fist.x,fist.y,fist.z));
        burst(flash,44+(int)(28*trained),0);add(f,flash);

        // Stacked rings preserve almost the same diameter from start to finish: visually a cylinder.
        int slices=9+(int)(4*trained);
        for(int i=0;i<slices;i++){
            float t=(i+1)/(float)slices;
            float z=-.72f-length*t;
            float ringRadius=radius*(.94f+.06f*(float)Math.sin(t*Math.PI));
            ParticleEmitter ring=particle(15,13,.55f+.35f*trained,.034f,0xBFE9E5E7,true);
            Circle circle=new Circle();circle.setRadius(ringRadius);circle.setRadiusThickness(.018f+.010f*trained);
            ring.config.shape.setShape(circle);ring.config.shape.setRotation(new NumberFunction3(90,0,0));
            ring.config.shape.setPosition(new NumberFunction3(0,1.14f,z));
            burst(ring,34+(int)(20*trained),Math.min(5,i/2));add(f,ring);

            ParticleEmitter shell=particleTex(14,12,.24f,.030f,0x78FFFFFF,false,SOFT);
            Circle shellCircle=new Circle();shellCircle.setRadius(ringRadius*.82f);shellCircle.setRadiusThickness(.14f);
            shell.config.shape.setShape(shellCircle);shell.config.shape.setRotation(new NumberFunction3(90,0,0));
            shell.config.shape.setPosition(new NumberFunction3(0,1.14f,z));
            burst(shell,18+(int)(12*trained),Math.min(5,i/2));add(f,shell);
        }

        // Fast white pressure lanes make the volume read as one coherent forward-moving tube.
        int lanes=8+(int)(4*trained);
        for(int i=0;i<lanes;i++){
            double a=Math.PI*2.0*i/lanes+r.nextDouble()*.16;
            float laneRadius=radius*(.40f+r.nextFloat()*.38f);
            float x=(float)Math.cos(a)*laneRadius;
            float y=1.14f+(float)Math.sin(a)*laneRadius;
            Vector3f a0=new Vector3f(x*.30f,1.14f+(y-1.14f)*.30f,-.85f);
            Vector3f b0=new Vector3f(x,y,-length-.72f);
            add(f,beam(a0,b0,.010f+.005f*trained,0xBDEDE9EA,6+r.nextInt(3),i%3,true));
        }

        // Jagged black/red Haki lightning corkscrews down the full cylinder.
        int bolts=8+(int)(8*trained)+(joy?3:0);
        for(int bolt=0;bolt<bolts;bolt++){
            double angle=r.nextDouble()*Math.PI*2.0;
            float lane=radius*(.24f+r.nextFloat()*.68f);
            int segments=5+r.nextInt(3);
            Vector3f prev=new Vector3f(
                    (float)Math.cos(angle)*lane*.18f,
                    1.14f+(float)Math.sin(angle)*lane*.18f,
                    -.72f);
            for(int seg=1;seg<=segments;seg++){
                float t=seg/(float)segments;
                angle+=(r.nextDouble()-.5)*.70;
                float jitter=.16f+.24f*trained;
                float x=(float)Math.cos(angle)*lane+(r.nextFloat()-.5f)*jitter;
                float y=1.14f+(float)Math.sin(angle)*lane+(r.nextFloat()-.5f)*jitter;
                float z=-.72f-length*t+(r.nextFloat()-.5f)*.42f;
                Vector3f next=new Vector3f(x,y,z);
                int delay=Math.min(5,(bolt+seg)/4);
                add(f,beam(prev,next,.055f+.025f*trained,0xE0060508,5+r.nextInt(3),delay,false));
                add(f,beam(prev,next,.014f+.010f*trained,0xFFFF2944,4+r.nextInt(3),delay,true));
                if(seg>1&&r.nextFloat()<.18f){
                    Vector3f branch=new Vector3f(next).add((r.nextFloat()-.5f)*1.2f,(r.nextFloat()-.5f)*1.2f,(r.nextFloat()-.5f)*1.0f);
                    add(f,beam(next,branch,.030f,0xC8060508,4,delay+1,false));
                    add(f,beam(next,branch,.008f,0xFFFF3851,3,delay+1,true));
                }
                prev=next;
            }
        }
        return f;
    }

    /** Debug/fallback charge accent. The real held charge is world-space in galaxyChargeWorld. */
    private static FX convergenceCharge(float q,boolean joy,long seed){
        FX f=fx();
        float power=Math.max(.08f,Math.min(1f,q));
        ParticleEmitter spark=particleTex(8,10,.02f,.013f,joy?0xFFFF3C55:0xFFFFD979,true,SPARK);
        Sphere s=new Sphere();s.setRadius(.18f+.08f*power);s.setRadiusThickness(.20f);
        spark.config.shape.setShape(s);spark.config.shape.setPosition(new NumberFunction3(.45f,1.25f,-.20f));
        burst(spark,8+(int)(8*power),0);add(f,spark);
        return f;
    }

    /** Clean vertical launch: air pressure and speed lines only. */
    private static FX galaxyAscent(float q,boolean joy,long seed){
        FX f=fx();
        Random r=new Random(seed);
        ParticleEmitter floor=particle(14,14,6.2f,.042f,0xCFF1ECEE,true);
        Circle fc=new Circle();fc.setRadius(.44f);fc.setRadiusThickness(.025f);
        floor.config.shape.setShape(fc);floor.config.shape.setPosition(new NumberFunction3(0,.04f,0));
        burst(floor,120,0);add(f,floor);

        ParticleEmitter dust=particle(17,16,3.2f,.034f,0x76C5BDBC,false);
        Sphere ds=new Sphere();ds.setRadius(.48f);ds.setRadiusThickness(.34f);
        dust.config.shape.setShape(ds);dust.config.shape.setPosition(new NumberFunction3(0,.10f,0));
        burst(dust,70,1);add(f,dust);

        for(int i=0;i<12;i++){
            float x=(r.nextFloat()-.5f)*1.20f,z=(r.nextFloat()-.5f)*1.20f;
            float y=.12f+r.nextFloat()*.50f,len=1.8f+r.nextFloat()*2.8f;
            add(f,beam(new Vector3f(x,y,z),new Vector3f(x+(r.nextFloat()-.5f)*.08f,y-len,z+(r.nextFloat()-.5f)*.08f),.014f,0xDFF7F2F3,7,r.nextInt(2),true));
        }
        return f;
    }

    /** Final punch frame: no galaxy here, only the stored energy snapping through the right fist. */
    private static FX convergenceRelease(float q,boolean joy,long seed){
        FX f=fx();
        Random r=new Random(seed);
        Vector3f fist=new Vector3f(.48f,1.26f,-.34f);
        ParticleEmitter flash=particle(10,11,1.8f,.145f,0xFFFFFFFF,true);
        Sphere fs=new Sphere();fs.setRadius(.22f);fs.setRadiusThickness(.24f);
        flash.config.shape.setShape(fs);flash.config.shape.setPosition(new NumberFunction3(fist.x,fist.y,fist.z));
        burst(flash,72,0);add(f,flash);

        ParticleEmitter gold=particle(11,12,1.5f,.100f,0xFFFFC950,true);
        Sphere gs=new Sphere();gs.setRadius(.30f);gs.setRadiusThickness(.20f);
        gold.config.shape.setShape(gs);gold.config.shape.setPosition(new NumberFunction3(fist.x,fist.y,fist.z));
        burst(gold,54,0);add(f,gold);

        ParticleEmitter lance=particle(14,14,6.6f,.055f,0xEFFFF9F4,true);
        Cone lc=new Cone();lc.setRadius(.18f);lc.setAngle(10);
        lance.config.shape.setShape(lc);lance.config.shape.setRotation(new NumberFunction3(90,0,0));lance.config.shape.setPosition(new NumberFunction3(.28f,1.18f,-.55f));
        burst(lance,130,1);add(f,lance);

        for(int i=0;i<4;i++){
            double a=Math.PI*2*i/4.0+r.nextDouble()*.25;
            Vector3f p1=new Vector3f(fist.x+(float)Math.cos(a)*.22f,fist.y+(r.nextFloat()-.5f)*.16f,fist.z+(float)Math.sin(a)*.22f);
            Vector3f p2=new Vector3f(fist.x+(float)Math.cos(a)*(.48f+r.nextFloat()*.16f),p1.y+(r.nextFloat()-.5f)*.12f,fist.z+(float)Math.sin(a)*(.48f+r.nextFloat()*.16f));
            add(f,beam(fist,p1,.009f,0xE008060A,4,0,false));add(f,beam(fist,p1,.004f,0xFFFF2B46,4,0,true));
            add(f,beam(p1,p2,.007f,0xCC08060A,4,1,false));add(f,beam(p1,p2,.0033f,0xFFFF3750,4,1,true));
        }
        return f;
    }

    private static FX convergenceHit(float q,boolean joy,long seed){
        FX f=fx();
        ParticleEmitter flash=particle(10,11,3.6f,.12f,0xF5FFF9F4,true);
        Sphere fs=new Sphere();fs.setRadius(.30f);fs.setRadiusThickness(.22f);flash.config.shape.setShape(fs);flash.config.shape.setPosition(new NumberFunction3(0,1,0));
        burst(flash,80,0);add(f,flash);
        ParticleEmitter air=particle(16,14,5.0f,.035f,0xBFE3DEE0,false);
        Sphere as=new Sphere();as.setRadius(.58f);as.setRadiusThickness(.42f);air.config.shape.setShape(as);air.config.shape.setPosition(new NumberFunction3(0,.8f,0));
        burst(air,120,0);add(f,air);
        return f;
    }

    /** Keeps the RAGE charge glued to the animated right fist instead of leaving a world-space orb behind. */
    private static void startGalaxyFistCharge(Entity entity,boolean hakiCoated,boolean advancedHaki){
        stopGalaxyFist(entity.getId());
        FX f=fx();

        // The red Armament/Conqueror core remains the visual anchor. Advanced Haki wraps it in
        // nebula-like violet/blue/cyan energy instead of replacing the move with a rainbow sphere.
        ParticleEmitter armament=loopParticle(SOFT,54,.035f,.24f,0xD6080008,false,10.5f,.30f,.96f);
        add(f,armament);
        ParticleEmitter corona=loopParticle(RAGE_FLAME,50,.075f,.115f,0xE8FF1208,true,11.5f,.34f,.92f);
        add(f,corona);
        ParticleEmitter core=loopParticle(RAGE_GLOW,42,.035f,.060f,0xF8FF6A30,true,7.2f,.19f,1f);
        add(f,core);
        ParticleEmitter sparks=loopParticle(RAGE_GLOW,32,.26f,.040f,0xF8FF1808,true,4.5f,.43f,.82f);
        add(f,sparks);

        // Cosmic intake shared by every coated tier: the fist is visibly swallowing the disc
        // hanging overhead, so the two halves of the move read as one technique rather than as a
        // red glow that happens to have a galaxy above it.
        if(hakiCoated||advancedHaki){
            // Negative start speed on a shell shape converges instead of expanding, which is what
            // makes this look like accretion rather than an ordinary emission.
            ParticleEmitter accretion=HakiFx.stars(240,26,-0.19f,.052f,0xF2D8C0FF);
            accretion.config.setLooping(true);
            accretion.config.setMaxParticles(600);
            accretion.config.emission.setEmissionRate(NumberFunction.constant(16f));
            HakiFx.sphere(accretion,1.45f,.28f);
            HakiFx.size(accretion,HakiCurves.collapseIn());
            add(f,accretion);

            ParticleEmitter gas=HakiFx.additive(HakiFx.emitter(HakiFx.NEBULA,240,34,-0.10f,.30f,0x9A7A3CFF,true));
            gas.config.setLooping(true);
            gas.config.setMaxParticles(420);
            gas.config.emission.setEmissionRate(NumberFunction.constant(9f));
            HakiFx.sphere(gas,1.05f,.42f);
            HakiFx.size(gas,HakiCurves.collapseIn());
            HakiFx.color(gas,HakiCurves.cosmicOut());
            HakiFx.spin(gas,2.6f);
            add(f,gas);
        }

        if(advancedHaki){
            ParticleEmitter violet=loopParticle(SOFT,66,.040f,.34f,0xB58C2BFF,true,13.5f,.52f,.90f);
            add(f,violet);
            ParticleEmitter blue=loopParticle(RAGE_GLOW,60,.050f,.20f,0xC53872FF,true,14.0f,.62f,.92f);
            add(f,blue);
            ParticleEmitter cyan=loopParticle(RAGE_GLOW,46,.075f,.11f,0xC86EEBFF,true,12.0f,.72f,.88f);
            add(f,cyan);
            ParticleEmitter magenta=loopParticle(RAGE_FLAME,42,.060f,.15f,0xBDE842FF,true,10.0f,.84f,.82f);
            add(f,magenta);
            ParticleEmitter stars=loopParticle(RAGE_GLOW,28,.14f,.028f,0xF4FFFFFF,true,8.0f,1.02f,.86f);
            add(f,stars);
            addAdvancedGalaxyFistLightning(f);
        }
        if(hakiCoated) addGarpFistLightning(f);

        EntityEffect fist=new EntityEffect(f,entity.level(),entity,EntityEffect.AutoRotate.NONE);
        fist.setOffset(galaxyFistOffset(entity));
        float initialScale=advancedHaki?.82f:(hakiCoated?.56f:.27f);
        fist.setScale(new Vector3f(initialScale,initialScale,initialScale));
        GALAXY_FIST_TIERS.put(entity.getId(),new GalaxyTier(hakiCoated,advancedHaki));
        GALAXY_FIST_SCALE.put(entity.getId(),initialScale);
        fist.setAllowMulti(true);
        fist.setForcedDeath(false);
        try{
            fist.start();
            GALAXY_FIST_EFFECTS.put(entity.getId(),fist);
        }catch(RuntimeException failure){
            LOGGER.error("Galaxy Impact fist charge failed to start for entity {}",entity.getId(),failure);
        }
    }

    private static void intensifyGalaxyFist(int entityId,boolean hakiCoated,boolean advancedHaki){
        EntityEffect fist=GALAXY_FIST_EFFECTS.get(entityId);
        float scale=advancedHaki?1.34f:(hakiCoated?.82f:.40f);
        GALAXY_FIST_TIERS.put(entityId,new GalaxyTier(hakiCoated,advancedHaki));
        GALAXY_FIST_SCALE.put(entityId,scale);
        if(fist!=null&&fist.getRuntime()!=null){
            fist.getRuntime().getRoot().updateScale(new Vector3f(scale,scale,scale));
        }
    }

    /** Scale the orb had reached before absorption started. */
    private static float galaxyFistBaseScale(int entityId){
        return GALAXY_FIST_SCALE.getOrDefault(entityId,.40f);
    }

    /** Colour of the charge orb for a tier. The charged fist inherits it so the absorbed energy
     *  is visibly the same energy that was orbiting a moment earlier. */
    private static int galaxyOrbColor(boolean coated,boolean advanced){
        if(advanced) return 0xFF9A5AFF;   // violet, matching the overhead disc's arms
        if(coated) return 0xFFFF5A20;     // red-orange Armament core
        return 0xFFCFE6FF;                // pale compressed air
    }

    /**
     * Begins drawing the orbiting charge ball into the fist.
     *
     * <p>Sent when the charge reaches READY. The orb is not cut: {@link #tickGalaxyFistEffects}
     * scales it down over {@value #GALAXY_ABSORB_DURATION} ticks so it visibly contracts into the
     * hand, and only once it has collapsed does the charged-fist effect take over.
     */
    private static void absorbGalaxyFist(int entityId,boolean coated,boolean advanced){
        if(!GALAXY_FIST_EFFECTS.containsKey(entityId))return;
        GALAXY_ABSORB_TICKS.putIfAbsent(entityId,GALAXY_ABSORB_DURATION);
    }

    /** The hand once the orb is inside it: a tight core in the orb's colour with the same
     *  energy still turning over on the knuckles. */
    private static void startChargedFist(Entity entity,boolean coated,boolean advanced){
        stopChargedFist(entity.getId());
        int color=galaxyOrbColor(coated,advanced);
        FX f=fx();

        ParticleEmitter core=HakiFx.additive(HakiFx.emitter(HakiFx.GLOW,240,14,.012f,
                advanced?.30f:(coated?.24f:.17f),color,true));
        core.config.setLooping(true);
        core.config.setMaxParticles(180);
        core.config.emission.setEmissionRate(NumberFunction.constant(7f));
        HakiFx.sphere(core,.055f,1f);
        HakiFx.size(core,HakiCurves.holdCollapse());
        HakiFx.color(core,HakiCurves.flashOut());
        add(f,core);

        ParticleEmitter motes=HakiFx.stars(240,20,.055f,advanced?.055f:.040f,color);
        motes.config.setLooping(true);
        motes.config.setMaxParticles(240);
        motes.config.emission.setEmissionRate(NumberFunction.constant(advanced?9f:6f));
        HakiFx.sphere(motes,advanced?.26f:.19f,.55f);
        add(f,motes);

        if(advanced){
            ParticleEmitter gas=HakiFx.additive(HakiFx.emitter(HakiFx.NEBULA,240,26,.030f,.19f,0x8A7A3CFF,true));
            gas.config.setLooping(true);
            gas.config.setMaxParticles(160);
            gas.config.emission.setEmissionRate(NumberFunction.constant(5f));
            HakiFx.sphere(gas,.22f,.50f);
            HakiFx.color(gas,HakiCurves.cosmicOut());
            HakiFx.spin(gas,3.1f);
            add(f,gas);
        }

        EntityEffect charged=new EntityEffect(f,entity.level(),entity,EntityEffect.AutoRotate.NONE);
        charged.setOffset(galaxyFistOffset(entity));
        charged.setAllowMulti(true);
        charged.setForcedDeath(false);
        try{
            charged.start();
            GALAXY_CHARGED_FIST.put(entity.getId(),charged);
        }catch(RuntimeException failure){
            LOGGER.error("Charged Galaxy fist failed to start for entity {}",entity.getId(),failure);
        }
    }

    private static void stopChargedFist(int entityId){
        EntityEffect charged=GALAXY_CHARGED_FIST.remove(entityId);
        if(charged!=null&&charged.getRuntime()!=null) charged.getRuntime().destroy(true);
    }

    private static void stopGalaxyFist(int entityId){
        EntityEffect fist=GALAXY_FIST_EFFECTS.remove(entityId);
        if(fist!=null&&fist.getRuntime()!=null) fist.getRuntime().destroy(true);
        GALAXY_ABSORB_TICKS.remove(entityId);
        GALAXY_FIST_TIERS.remove(entityId);
        GALAXY_FIST_SCALE.remove(entityId);
        stopChargedFist(entityId);
    }

    private static Vector3f galaxyFistOffset(Entity entity){
        double yaw=Math.toRadians(entity.getYRot());
        Vec3 forward=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        Vec3 right=new Vec3(-forward.z,0,forward.x);
        Vec3 offset=right.scale(.34D).add(forward.scale(-.34D));
        return new Vector3f((float)offset.x,entity.getBbHeight()+.42f,(float)offset.z);
    }

    /** Called from the normal client tick hook: updates moving fist anchors and executes the delayed RAGE detonation. */
    public static void tickGalaxyFistEffects(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null){
            for(EntityEffect effect:GALAXY_FIST_EFFECTS.values()) if(effect.getRuntime()!=null) effect.getRuntime().destroy(true);
            for(EntityEffect effect:GALAXY_CHARGED_FIST.values()) if(effect.getRuntime()!=null) effect.getRuntime().destroy(true);
            GALAXY_FIST_EFFECTS.clear();
            GALAXY_CHARGED_FIST.clear();
            GALAXY_ABSORB_TICKS.clear();
            GALAXY_FIST_TIERS.clear();
            GALAXY_FIST_SCALE.clear();
            GALAXY_PENDING_DETONATIONS.clear();
            return;
        }

        Iterator<Map.Entry<Integer,EntityEffect>> iterator=GALAXY_FIST_EFFECTS.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<Integer,EntityEffect> entry=iterator.next();
            Entity entity=mc.level.getEntity(entry.getKey());
            EntityEffect effect=entry.getValue();
            if(entity==null||entity.isRemoved()){
                if(effect.getRuntime()!=null) effect.getRuntime().destroy(true);
                iterator.remove();
                GALAXY_ABSORB_TICKS.remove(entry.getKey());
                stopChargedFist(entry.getKey());
                continue;
            }
            effect.setOffset(galaxyFistOffset(entity));

            // Contract the orb into the hand rather than cutting it. Once it has fully collapsed
            // the orbiting ball is destroyed and the charged fist takes its place.
            Integer remaining=GALAXY_ABSORB_TICKS.get(entry.getKey());
            if(remaining!=null){
                int left=remaining-1;
                float t=1f-Math.max(0f,Math.min(1f,left/(float)GALAXY_ABSORB_DURATION));
                float eased=t*t*(3f-2f*t);
                if(effect.getRuntime()!=null){
                    float scale=galaxyFistBaseScale(entry.getKey())*(1f-.94f*eased);
                    effect.getRuntime().getRoot().updateScale(new Vector3f(scale,scale,scale));
                }
                if(left<=0){
                    GALAXY_ABSORB_TICKS.remove(entry.getKey());
                    if(effect.getRuntime()!=null) effect.getRuntime().destroy(true);
                    iterator.remove();
                    GalaxyTier tier=GALAXY_FIST_TIERS.getOrDefault(entry.getKey(),GalaxyTier.PLAIN);
                    startChargedFist(entity,tier.coated(),tier.advanced());
                }else{
                    GALAXY_ABSORB_TICKS.put(entry.getKey(),left);
                }
            }
        }

        // Keep the charged fist welded to the hand for as long as it is held.
        for(Map.Entry<Integer,EntityEffect> entry:GALAXY_CHARGED_FIST.entrySet()){
            Entity entity=mc.level.getEntity(entry.getKey());
            if(entity!=null&&!entity.isRemoved()) entry.getValue().setOffset(galaxyFistOffset(entity));
        }

        for(int i=GALAXY_PENDING_DETONATIONS.size()-1;i>=0;i--){
            PendingGalaxyDetonation pending=GALAXY_PENDING_DETONATIONS.get(i);
            if(pending.ticks()>1){
                GALAXY_PENDING_DETONATIONS.set(i,new PendingGalaxyDetonation(pending.entityId(),pending.power(),pending.hakiCoated(),pending.advancedHaki(),pending.x(),pending.y(),pending.z(),pending.seed(),pending.ticks()-1));
                continue;
            }
            Entity source=mc.level.getEntity(pending.entityId());
            if(source!=null){
                HakiImpactScreen.impact(source,pending.advancedHaki()?1.0f:(pending.hakiCoated()?.82f+.18f*pending.power():.54f));
                spawnGalaxyDebris(mc,pending.x(),pending.y(),pending.z(),pending.advancedHaki());
            }
            GALAXY_PENDING_DETONATIONS.remove(i);
        }
    }

    private static void spawnGalaxyDebris(Minecraft mc,double x,double y,double z,boolean advancedHaki){
        if(mc.level==null)return;
        BlockPos pos=BlockPos.containing(x,y-.20D,z);
        BlockState state=mc.level.getBlockState(pos);
        if(state.isAir())return;
        int debrisCount=advancedHaki?240:150;
        for(int i=0;i<debrisCount;i++){
            double angle=i*2.399963229728653D;
            double speed=.18D+(i%11)*.045D;
            double lift=.16D+(i%9)*.045D;
            double radius=(i%7)*.12D;
            mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,state),
                    x+Math.cos(angle)*radius,y+.20D+(i%5)*.08D,z+Math.sin(angle)*radius,
                    Math.cos(angle)*speed,lift,Math.sin(angle)*speed);
        }
    }

    /** RAGE's actual release: pressure path first, compressed center one tick later, dome detonation five ticks later. */
    private static FX rageGalaxyImpactFx(Vec3 sourceOffset,long seed,boolean hakiCoated,boolean advancedHaki){
        if(!hakiCoated) return rageGalaxyImpactUncoatedFx(sourceOffset,seed);
        FX f=fx();
        Random random=new Random(seed);
        Vector3f source=new Vector3f((float)sourceOffset.x,(float)sourceOffset.y,(float)sourceOffset.z);
        Vector3f center=new Vector3f(0,0,0);

        // Six jagged black/red Conqueror's paths travel from the fist to the remote impact point.
        Vec3 direction=sourceOffset.scale(-1);
        double len=Math.max(.001D,direction.length());
        Vec3 dir=direction.scale(1D/len);
        Vec3 reference=Math.abs(dir.y)>.88D?new Vec3(1,0,0):new Vec3(0,1,0);
        Vec3 right=dir.cross(reference).normalize();
        Vec3 up=right.cross(dir).normalize();
        for(int path=0;path<6;path++){
            Vec3 previous=sourceOffset;
            for(int segment=1;segment<=9;segment++){
                double t=segment/9.0D;
                double envelope=Math.sin(Math.PI*t);
                double lateral=(random.nextDouble()*2D-1D)*(0.9D+path*.23D)*envelope;
                double vertical=(random.nextDouble()*2D-1D)*(0.75D+path*.20D)*envelope;
                Vec3 current=sourceOffset.add(direction.scale(t)).add(right.scale(lateral)).add(up.scale(vertical));
                addHakiLightningSegment(f,previous,current,11,path%3);
                previous=current;
            }
        }

        // Tiny over-compressed center at +1 tick.
        ParticleEmitter dark=particleTex(10,10,.35f,.42f,0xEE050006,false,SOFT);
        Sphere ds=new Sphere();ds.setRadius(.38f);ds.setRadiusThickness(1f);dark.config.shape.setShape(ds);burst(dark,90,1);add(f,dark);
        ParticleEmitter white=particleTex(8,8,.22f,.075f,0xFFFFFFFF,true,RAGE_GLOW);
        Sphere ws=new Sphere();ws.setRadius(.20f);ws.setRadiusThickness(1f);white.config.shape.setShape(ws);burst(white,70,1);add(f,white);
        ParticleEmitter red=particleTex(9,9,.30f,.16f,0xFFFF1808,true,RAGE_FLAME);
        Sphere rs=new Sphere();rs.setRadius(.28f);rs.setRadiusThickness(1f);red.config.shape.setShape(rs);burst(red,80,1);add(f,red);
        addGarpCompressionLightning(f,1);

        // White-hot nucleus and pressure dome begin exactly five ticks after the strike.
        ParticleEmitter hot=particleTex(advancedHaki?52:32,advancedHaki?40:26,advancedHaki?27.5f:14.5f,advancedHaki?.58f:.34f,0xFFFFFFFF,true,RAGE_GLOW);
        Sphere hs=new Sphere();hs.setRadius(advancedHaki?1.72f:.90f);hs.setRadiusThickness(1f);hot.config.shape.setShape(hs);burst(hot,advancedHaki?640:320,5);add(f,hot);
        ParticleEmitter flame=particleTex(advancedHaki?70:42,advancedHaki?56:34,advancedHaki?31.5f:16.8f,advancedHaki?1.18f:.72f,0xF8FF1A08,true,RAGE_FLAME);
        Sphere fls=new Sphere();fls.setRadius(advancedHaki?3.65f:1.90f);fls.setRadiusThickness(.95f);flame.config.shape.setShape(fls);burst(flame,advancedHaki?720:360,5);add(f,flame);
        ParticleEmitter darkCore=particleTex(advancedHaki?88:56,advancedHaki?70:42,advancedHaki?27.0f:14.0f,advancedHaki?2.55f:1.55f,0xE0180008,false,SOFT);
        Sphere dcs=new Sphere();dcs.setRadius(advancedHaki?5.45f:2.9f);dcs.setRadiusThickness(.90f);darkCore.config.shape.setShape(dcs);burst(darkCore,advancedHaki?780:390,5);add(f,darkCore);

        for(int shell=0;shell<4;shell++){
            int color=shell==0?0xD8FFFFFF:(shell==1?0xC8FF3A18:0xA8180008);
            ResourceLocation tex=shell%2==0?RAGE_GLOW:SOFT;
            float shellScale=advancedHaki?1.88f:1f;
            ParticleEmitter pressure=particleTex(advancedHaki?82+shell*10:56+shell*8,advancedHaki?62+shell*8:38+shell*6,(15.0f+shell*5.2f)*shellScale,(shell==0?.24f:.82f+shell*.28f)*shellScale,color,shell<2,tex);
            Sphere shape=new Sphere();shape.setRadius((2.15f+shell*.88f)*shellScale);shape.setRadiusThickness(.92f);pressure.config.shape.setShape(shape);
            burst(pressure,(advancedHaki?2:1)*(300-shell*28),5+shell*2);add(f,pressure);
        }

        // Shock rings are now one expanding billboard each instead of a couple of hundred sprites
        // scattered around a circle. A ring drawn as a ring is continuous rather than dotted, and
        // this drops roughly two thousand particles per detonation.
        int[] delays={5,7,9,11,14,18,22};
        float[] growth={9.5f,12.5f,16.0f,20.0f,25.0f,30.0f,35.0f};
        float ringScale=advancedHaki?1.85f:1f;
        for(int ringIndex=0;ringIndex<delays.length;ringIndex++){
            int color=ringIndex<2?0xE8FFFFFF:(ringIndex%2==0?0xE8FF5A30:0xC8402028);
            ParticleEmitter ring=HakiFx.groundRing(advancedHaki?76:52,advancedHaki?56:38,
                    (1.25f+ringIndex*.26f)*ringScale,growth[ringIndex]*ringScale,color);
            HakiFx.at(ring,new Vec3(0,.35f+ringIndex*.34f,0));
            HakiFx.burst(ring,1,delays[ringIndex]);
            add(f,ring);
        }
        // A pair of vertical fronts so the blast reads as a sphere of pressure, not just a floor wave.
        for(int i=0;i<2;i++){
            ParticleEmitter front=HakiFx.airRing(advancedHaki?64:44,advancedHaki?48:32,
                    1.10f*ringScale,(15f+i*9f)*ringScale,i==0?0xD0FFFFFF:0x9AFF7A50);
            HakiFx.at(front,new Vec3(0,1.10f+i*.85f,0));
            HakiFx.burst(front,1,6+i*4);
            add(f,front);
        }
        // Cooling ejecta thrown out of the crater.
        ParticleEmitter ejecta=HakiFx.embers(advancedHaki?70:48,advancedHaki?54:36,
                advancedHaki?1.35f:.95f,.11f*ringScale,0xFFFFC98A,random);
        HakiFx.sphere(ejecta,1.60f*ringScale,.55f);
        HakiFx.at(ejecta,new Vec3(0,.60,0));
        HakiFx.burst(ejecta,advancedHaki?260:150,5);
        add(f,ejecta);

        ParticleEmitter dust=particle(82,66,16f,1.70f,0xB85A4638,false);
        Circle dustCircle=new Circle();dustCircle.setRadius(1.75f);dustCircle.setRadiusThickness(.24f);dust.config.shape.setShape(dustCircle);dust.config.shape.setPosition(new NumberFunction3(0,.22f,0));burst(dust,450,6);add(f,dust);
        ParticleEmitter wind=particleTex(54,30,36f,.15f,0x72FFFFFF,true,RAGE_GLOW);
        Circle windCircle=new Circle();windCircle.setRadius(1.45f);windCircle.setRadiusThickness(.14f);wind.config.shape.setShape(windCircle);wind.config.shape.setPosition(new NumberFunction3(0,.75f,0));burst(wind,240,7);add(f,wind);
        addGarpImpactLightning(f,5);
        addGalaxyMushroomCloud(f,advancedHaki);
        if(advancedHaki) addEmpoweredGalaxyHaki(f,seed);
        return f;
    }

    /** Uncoated full Galaxy Impact: pure compressed air/pressure. Deliberately contains no red/black
     * nuclear center, Haki lightning, explosion sphere or mushroom cloud. */
    private static FX rageGalaxyImpactUncoatedFx(Vec3 sourceOffset,long seed){
        FX f=fx();
        Random r=new Random(seed);

        Vec3 direction=sourceOffset.scale(-1);
        double len=Math.max(.001D,direction.length());
        Vec3 dir=direction.scale(1D/len);
        Vec3 reference=Math.abs(dir.y)>.88D?new Vec3(1,0,0):new Vec3(0,1,0);
        Vec3 right=dir.cross(reference).normalize();
        Vec3 up=right.cross(dir).normalize();

        // White/gray pressure lanes track the fist-to-impact path without pretending to be Haki.
        for(int lane=0;lane<4;lane++){
            Vec3 previous=sourceOffset;
            for(int segment=1;segment<=8;segment++){
                double t=segment/8.0D;
                double envelope=Math.sin(Math.PI*t);
                double lateral=(r.nextDouble()*2D-1D)*(.35D+lane*.10D)*envelope;
                double vertical=(r.nextDouble()*2D-1D)*(.28D+lane*.08D)*envelope;
                Vec3 current=sourceOffset.add(direction.scale(t)).add(right.scale(lateral)).add(up.scale(vertical));
                Vector3f a=new Vector3f((float)previous.x,(float)previous.y,(float)previous.z);
                Vector3f b=new Vector3f((float)current.x,(float)current.y,(float)current.z);
                add(f,beam(a,b,.018f,0x80E9E7E6,5,lane%2,false));
                previous=current;
            }
        }

        // No central sphere: only flat shock rings and dust exploding away from the contact point.
        int[] delays={4,6,9,13,18};
        float[] speeds={10f,14f,19f,25f,32f};
        for(int i=0;i<delays.length;i++){
            ParticleEmitter ring=particleTex(42+i*4,28+i*3,speeds[i],.10f+i*.025f,
                    i<2?0xB8FFFFFF:0x7AD8D5D2,i<2,RAGE_GLOW);
            Circle c=new Circle();c.setRadius(1.1f+i*.34f);c.setRadiusThickness(.14f);
            ring.config.shape.setShape(c);ring.config.shape.setPosition(new NumberFunction3(0,.22f+i*.12f,0));
            burst(ring,120+i*28,delays[i]);add(f,ring);
        }

        ParticleEmitter dust=particle(58,44,13.5f,.72f,0x8A6A5A50,false);
        Circle dc=new Circle();dc.setRadius(1.35f);dc.setRadiusThickness(.24f);
        dust.config.shape.setShape(dc);dust.config.shape.setPosition(new NumberFunction3(0,.12f,0));
        burst(dust,260,5);add(f,dust);

        ParticleEmitter wind=particleTex(38,24,28f,.085f,0x86FFFFFF,true,RAGE_GLOW);
        Circle wc=new Circle();wc.setRadius(1.15f);wc.setRadiusThickness(.10f);
        wind.config.shape.setShape(wc);wind.config.shape.setPosition(new NumberFunction3(0,.42f,0));
        burst(wind,180,5);add(f,wind);
        return f;
    }

    /** Delayed nuclear smoke silhouette: a narrow vertical stem grows after the pressure flash,
     * then several broad, layered caps roll outward above it. This is intentionally smoke-heavy
     * and delayed so the first read is still the violent Haki explosion, not an instant gray blob. */
    /** Delayed nuclear smoke silhouette: a narrow vertical stem grows after the pressure flash,
     * then several broad, layered caps roll outward above it. This is intentionally smoke-heavy
     * and delayed so the first read is still the violent Haki explosion, not an instant gray blob.
     *
     * <p>Now built from the billowing smoke sprite with swell/cool envelopes and per-particle
     * roll, so the column churns as it climbs instead of being a stack of identical discs that
     * blink out together at their lifetime cutoff. */
    private static void addGalaxyMushroomCloud(FX f,boolean advancedHaki){
        float scale=advancedHaki?1.55f:1f;
        Random r=new Random(0x9E3779B9L ^ (advancedHaki?1L:0L));
        int smoke=0xC85C5A58;
        int deepSmoke=0xD83B3837;
        int hotSmoke=0xB875665B;

        // Stem: four compact smoke bodies climb upward in sequence.
        for(int stage=0;stage<4;stage++){
            int delay=12+stage*5;
            float y=2.6f+stage*2.8f;
            ParticleEmitter stem=HakiFx.smoke(62,52,1.4f+stage*.25f,(1.00f+stage*.18f)*scale,
                    stage<2?hotSmoke:smoke,1.65f,r);
            HakiFx.sphere(stem,(.85f+stage*.16f)*scale,.92f);
            HakiFx.at(stem,new Vec3(0,y,0));
            HakiFx.burst(stem,105+stage*24,delay);
            add(f,stem);
        }

        // Neck under the cap keeps the iconic mushroom shape connected instead of floating.
        ParticleEmitter neck=HakiFx.smoke(78,62,1.7f,1.30f*scale,deepSmoke,1.50f,r);
        HakiFx.sphere(neck,1.45f*scale,.86f);
        HakiFx.at(neck,new Vec3(0,11.2f*scale,0));
        HakiFx.burst(neck,170,28);
        add(f,neck);

        // Cap: overlapping sphere and wide ring layers roll outward after the stem reaches height.
        ParticleEmitter capCore=HakiFx.smoke(92,72,2.1f,1.75f*scale,smoke,1.80f,r);
        HakiFx.sphere(capCore,3.3f*scale,.78f);
        HakiFx.at(capCore,new Vec3(0,13.5f*scale,0));
        HakiFx.burst(capCore,advancedHaki?420:340,30);
        add(f,capCore);

        for(int layer=0;layer<3;layer++){
            ParticleEmitter capRing=HakiFx.smoke(96,76,2.6f+layer*.65f,(1.55f+layer*.22f)*scale,
                    layer==2?deepSmoke:smoke,1.95f,r);
            HakiFx.circle(capRing,(3.0f+layer*1.75f)*scale,.48f+.08f*layer);
            HakiFx.at(capRing,new Vec3(0,(13.0f+layer*.85f)*scale,0));
            HakiFx.burst(capRing,180+layer*45,32+layer*4);
            add(f,capRing);
        }

        ParticleEmitter crown=HakiFx.smoke(100,80,2.0f,1.90f*scale,0xB96A6865,2.15f,r);
        HakiFx.sphere(crown,4.4f*scale,.55f);
        HakiFx.at(crown,new Vec3(0,15.0f*scale,0));
        HakiFx.burst(crown,advancedHaki?360:280,40);
        add(f,crown);
    }

    /** Restored normal-Haki Galaxy strike: a thick jagged black/red Conqueror bolt falls from
     * 11-15 blocks above the exact server-selected terrain point, then forks and crawls briefly
     * across the floor. This is intentionally separate from Advanced/J's horizontal field. */
    private static FX galaxyHakiSkyStrikeFx(long seed){
        FX f=fx();
        Random r=new Random(seed);

        double height=11.0D+r.nextDouble()*4.0D;
        Vec3 previous=new Vec3((r.nextDouble()-.5D)*1.2D,height,(r.nextDouble()-.5D)*1.2D);
        int segments=7+r.nextInt(3);
        for(int i=1;i<=segments;i++){
            double t=i/(double)segments;
            double envelope=Math.sin(Math.PI*t);
            Vec3 current=new Vec3(
                    (r.nextDouble()-.5D)*2.5D*envelope,
                    height*(1.0D-t),
                    (r.nextDouble()-.5D)*2.5D*envelope);
            addConquerorStormSegment(f,previous,current,10,0);
            previous=current;
        }

        for(int fork=0;fork<2;fork++){
            double angle=r.nextDouble()*Math.PI*2.0D;
            Vec3 a=new Vec3(Math.cos(angle)*(1.0D+r.nextDouble()),4.0D+r.nextDouble()*4.0D,Math.sin(angle)*(1.0D+r.nextDouble()));
            Vec3 b=new Vec3((r.nextDouble()-.5D)*.8D,.35D,(r.nextDouble()-.5D)*.8D);
            addConquerorStormSegment(f,a,b,8,1+fork);
        }

        for(int branch=0;branch<6;branch++){
            double angle=Math.PI*2.0D*branch/6.0D+(r.nextDouble()-.5D)*.45D;
            Vec3 a=new Vec3(0,.12D,0);
            Vec3 b=new Vec3(Math.cos(angle)*(1.5D+r.nextDouble()*2.0D),.10D+r.nextDouble()*.18D,Math.sin(angle)*(1.5D+r.nextDouble()*2.0D));
            addConquerorStormSegment(f,a,b,7,2+r.nextInt(2));
        }

        ParticleEmitter flash=particleTex(12,9,2.6f,.15f,0xEEFF243E,true,RAGE_GLOW);
        Sphere fs=new Sphere();fs.setRadius(.32f);fs.setRadiusThickness(.95f);flash.config.shape.setShape(fs);burst(flash,70,0);add(f,flash);
        ParticleEmitter dust=particle(18,16,2.8f,.34f,0x8A5A4638,false);
        Circle dc=new Circle();dc.setRadius(.35f);dc.setRadiusThickness(.18f);dust.config.shape.setShape(dc);dust.config.shape.setPosition(new NumberFunction3(0,.10f,0));burst(dust,70,1);add(f,dust);
        return f;
    }

    /** Advanced Galaxy ground field: black/red Conqueror lightning crawls horizontally across
     * terrain from the crater. Repeated server pulses overlap this composition for about five seconds. */
    private static FX galaxyHakiStrikeFx(float radius,long seed){
        FX f=fx();
        Random r=new Random(seed);
        double reach=Math.max(8.0D,radius);
        int branches=12+r.nextInt(5);

        for(int branch=0;branch<branches;branch++){
            double angle=Math.PI*2.0D*branch/branches+(r.nextDouble()-.5D)*.28D;
            Vec3 previous=new Vec3(0,.10D,0);
            int segments=5+r.nextInt(3);
            for(int segment=1;segment<=segments;segment++){
                double t=segment/(double)segments;
                double distance=reach*t;
                double side=(r.nextDouble()*2D-1D)*(1.2D+3.2D*Math.sin(Math.PI*t));
                double y=.08D+r.nextDouble()*.22D;
                Vec3 current=new Vec3(
                        Math.cos(angle)*distance+Math.cos(angle+Math.PI/2D)*side,
                        y,
                        Math.sin(angle)*distance+Math.sin(angle+Math.PI/2D)*side);
                addConquerorStormSegment(f,previous,current,13,branch%3+segment/2);

                // Sparse forks keep the floor alive without turning it into a filled red disk.
                if(segment>1 && segment<segments && (segment+branch)%2==0){
                    double forkAngle=angle+(r.nextBoolean()?1:-1)*(.24D+r.nextDouble()*.30D);
                    Vec3 fork=current.add(Math.cos(forkAngle)*(2.5D+r.nextDouble()*5.0D),.04D,Math.sin(forkAngle)*(2.5D+r.nextDouble()*5.0D));
                    addConquerorStormSegment(f,current,fork,9,branch%3+segment/2+1);
                }
                previous=current;
            }
        }

        ParticleEmitter pulse=particleTex(20,14,Math.max(10f,radius*.58f),.10f,0xC8FF243E,true,RAGE_GLOW);
        Circle pc=new Circle();pc.setRadius(.55f);pc.setRadiusThickness(.10f);pulse.config.shape.setShape(pc);pulse.config.shape.setPosition(new NumberFunction3(0,.08f,0));burst(pulse,130,0);add(f,pulse);
        ParticleEmitter dust=particle(28,20,Math.max(8f,radius*.34f),.28f,0x705A4638,false);
        Circle dc=new Circle();dc.setRadius(.50f);dc.setRadiusThickness(.16f);dust.config.shape.setShape(dc);dust.config.shape.setPosition(new NumberFunction3(0,.08f,0));burst(dust,100,1);add(f,dust);
        return f;
    }

    /** J/Advanced-Haki Galaxy Impact augmentation. The lightning waves deliberately reuse the
     *  exact black/red beam architecture and colors from Conqueror's Haki instead of inventing a
     *  separate Galaxy-only bolt style. Delayed waves keep snapping through the impact zone long
     *  after the pressure dome has expanded. */
    private static void addEmpoweredGalaxyHaki(FX f,long seed){
        // Extra outer pressure makes the Haki-active detonation visibly larger than the base RAGE move.
        ParticleEmitter outer=particleTex(62,44,34f,.72f,0xA8180008,true,RAGE_FLAME);
        Sphere os=new Sphere();os.setRadius(2.8f);os.setRadiusThickness(.94f);outer.config.shape.setShape(os);burst(outer,330,5);add(f,outer);
        ParticleEmitter crown=particleTex(52,36,39f,.16f,0xA8FFFFFF,true,RAGE_GLOW);
        Circle cc=new Circle();cc.setRadius(1.55f);cc.setRadiusThickness(.18f);crown.config.shape.setShape(cc);crown.config.shape.setPosition(new NumberFunction3(0,1.0f,0));burst(crown,240,7);add(f,crown);

        // Galaxy palette rides outside the red/black Haki nucleus: violet, electric blue and cyan
        // pressure shells make Advanced Galaxy Impact look cosmic without losing its red identity.
        int[] nebulaColors={0xB58C2BFF,0xB8426DFF,0xAC5FD8FF,0x9EEA4DFF};
        for(int shell=0;shell<nebulaColors.length;shell++){
            ParticleEmitter nebula=particleTex(64+shell*8,48+shell*6,34f+shell*7f,.48f+shell*.16f,nebulaColors[shell],true,shell%2==0?RAGE_GLOW:SOFT);
            Sphere ns=new Sphere();ns.setRadius(3.8f+shell*1.15f);ns.setRadiusThickness(.42f);nebula.config.shape.setShape(ns);
            burst(nebula,260+shell*70,6+shell*2);add(f,nebula);
        }

        // Immediate dense Conqueror-style discharge. The actual lingering storm is now emitted as
        // separate authoritative world-space strike packets so it visibly stays on the crater and
        // matches the localized server damage points.
        lightningFamiliesDelayed(f,1f,0xD9080609,0xFFE31F36,20,4,8,new Random(seed^0x243F6A8885A308D3L),7);
    }

    private static void addAdvancedGalaxyFistLightning(FX f){
        int[] colors={0xD23E5BFF,0xC98C2BFF,0xC260D9FF};
        for(int branch=0;branch<10;branch++){
            double angle=Math.PI*2D*branch/10D+branch*.19D;
            Vec3 a=new Vec3(Math.cos(angle)*.38D,(branch%3-.8D)*.10D,Math.sin(angle)*.38D);
            Vec3 b=new Vec3(Math.cos(angle+.30D)*(1.05D+(branch%4)*.10D),(branch%5-.8D)*.16D,Math.sin(angle+.30D)*(1.05D+(branch%4)*.10D));
            Vector3f va=new Vector3f((float)a.x,(float)a.y,(float)a.z);
            Vector3f vb=new Vector3f((float)b.x,(float)b.y,(float)b.z);
            add(f,rageBeam(va,vb,.030f,colors[branch%colors.length],9,branch%3,true));
        }
    }

    private static void addGarpFistLightning(FX f){
        for(int pulse=0;pulse<16;pulse++){
            int delay=pulse*12;
            double base=pulse*1.31D;
            for(int branch=0;branch<3;branch++){
                double angle=base+branch*(Math.PI*2D/3D);
                Vec3 mid=new Vec3(Math.cos(angle)*.28D,galaxyNoise(7000+pulse*17+branch)*.22D,Math.sin(angle)*.28D);
                Vec3 end=new Vec3(Math.cos(angle+.42D)*(.60D+.08D*branch),galaxyNoise(7200+pulse*23+branch)*.45D,Math.sin(angle+.42D)*(.60D+.08D*branch));
                addHakiLightningSegment(f,Vec3.ZERO,mid,7,delay);
                addHakiLightningSegment(f,mid,end,7,delay+1);
            }
        }
    }

    private static void addGarpCompressionLightning(FX f,int baseDelay){
        for(int branch=0;branch<10;branch++){
            double angle=Math.PI*2D*branch/10D+branch*.11D;
            Vec3 outer=new Vec3(Math.cos(angle)*2.2D,.20D+galaxyNoise(9000+branch)*1.2D,Math.sin(angle)*2.2D);
            Vec3 mid=outer.scale(.46D).add(0,galaxyNoise(9100+branch)*.25D,0);
            addHakiLightningSegment(f,outer,mid,6,baseDelay+branch%2);
            addHakiLightningSegment(f,mid,Vec3.ZERO,6,baseDelay+branch%2+1);
        }
    }

    private static void addGarpImpactLightning(FX f,int baseDelay){
        for(int branch=0;branch<14;branch++){
            double angle=Math.PI*2D*branch/14D+galaxyNoise(9600+branch)*.16D;
            Vec3 previous=new Vec3(0,.35D,0);
            for(int segment=1;segment<=5;segment++){
                double t=segment/5D;
                double radius=4D+t*(18D+(branch%4)*2.2D);
                double sideways=galaxyNoise(9700+branch*31+segment*7)*2.6D*Math.sin(Math.PI*t);
                double height=Math.sin(t*Math.PI)*(7D+(branch%3)*2.4D)+galaxyNoise(9900+branch*19+segment)*.55D;
                Vec3 current=new Vec3(Math.cos(angle)*radius+Math.cos(angle+Math.PI/2D)*sideways,Math.max(.20D,height),Math.sin(angle)*radius+Math.sin(angle+Math.PI/2D)*sideways);
                addHakiLightningSegment(f,previous,current,13,baseDelay+branch%4+segment/2);
                previous=current;
            }
        }
        for(int branch=0;branch<12;branch++){
            double angle=Math.PI*2D*branch/12D+galaxyNoise(10300+branch)*.20D;
            Vec3 previous=new Vec3(0,.20D,0);
            for(int segment=1;segment<=4;segment++){
                double distance=segment*6.4D+galaxyNoise(10400+branch*17+segment)*1.8D;
                double side=galaxyNoise(10500+branch*23+segment)*2.8D;
                Vec3 current=new Vec3(Math.cos(angle)*distance+Math.cos(angle+Math.PI/2D)*side,.18D+Math.abs(galaxyNoise(10600+branch*13+segment))*.38D,Math.sin(angle)*distance+Math.sin(angle+Math.PI/2D)*side);
                addHakiLightningSegment(f,previous,current,15,baseDelay+1+branch%5);
                previous=current;
            }
        }
    }

    private static void addHakiLightningSegment(FX f,Vec3 from,Vec3 to,int duration,int delay){
        Vector3f a=new Vector3f((float)from.x,(float)from.y,(float)from.z);
        Vector3f b=new Vector3f((float)to.x,(float)to.y,(float)to.z);
        add(f,rageBeam(a,b,.072f,0xF0060008,duration+2,delay,false));
        add(f,rageBeam(a,b,.019f,0xFFFF1208,duration,delay,true));
    }

    /** Exact Conqueror-family beam language used by the lingering Galaxy storm: the same dark outer
     * channel and saturated red inner core as normal Haoshoku release lightning. */
    private static void addConquerorStormSegment(FX f,Vec3 from,Vec3 to,int duration,int delay){
        Vector3f a=new Vector3f((float)from.x,(float)from.y,(float)from.z);
        Vector3f b=new Vector3f((float)to.x,(float)to.y,(float)to.z);
        add(f,beam(a,b,.16f,0xD9080609,duration+2,delay,false));
        add(f,beam(a,b,.040f,0xFFE31F36,duration,delay,true));
    }

    private static double galaxyNoise(int seed){
        long x=seed*0x9E3779B97F4A7C15L+0xD1B54A32D192ED03L;
        x^=x>>>30;x*=0xBF58476D1CE4E5B9L;x^=x>>>27;x*=0x94D049BB133111EBL;x^=x>>>31;
        return ((x>>>11)*(1.0/(1L<<53)))*2D-1D;
    }

    private static FX joyBoyAwakening(long seed){
        FX f=conquerorRelease(1f,4000,seed);
        merge(f,convergenceCharge(1f,true,seed^0xC2B2AE3D27D4EB4FL));
        lightningFamiliesDelayed(f,1f,0xF0050507,0xFFFF2A45,16,4,8,new Random(seed^0x165667B19E3779F9L),9);
        return f;
    }

    private static FX aftershock(float q,long seed){
        FX f=fx();
        ParticleEmitter wave=conquerorParticle(20,20,8f+8f*q,.055f,0x77D8D2D4,true);
        Circle c=new Circle();c.setRadius(.65f);c.setRadiusThickness(.015f);wave.config.shape.setShape(c);wave.config.shape.setPosition(new NumberFunction3(0,.10,0));burst(wave,130+(int)(90*q),0);add(f,wave);
        lightningFamilies(f,.65f+.3f*q,0xD5060508,0xFFFF2B45,4+(int)(5*q),3,6,new Random(seed));
        return f;
    }

    private static void merge(FX into,FX from){
        into.getMainFX().objects().addAll(from.getMainFX().objects());
    }
    /** A low-alpha red shell that stays attached to the kneeling body for the full Last Stand. */
    private static FX lastStandAura(int duration){
        int ticks=Math.max(40,duration);
        int emissionTicks=Math.max(20,ticks-18);
        FX f=fx();

        ParticleEmitter lower=conquerorParticle(emissionTicks,18,.025f,.145f,0x4DFF1735,false);
        lower.config.setMaxParticles(1400);
        lower.config.emission.setEmissionRate(NumberFunction.constant(14.0f));
        Sphere lowerShape=new Sphere();lowerShape.setRadius(.48f);lowerShape.setRadiusThickness(.34f);
        lower.config.shape.setShape(lowerShape);lower.config.shape.setPosition(new NumberFunction3(0,.48,0));add(f,lower);

        ParticleEmitter upper=conquerorParticle(emissionTicks,18,.032f,.165f,0x42D90C29,false);
        upper.config.setMaxParticles(1400);
        upper.config.emission.setEmissionRate(NumberFunction.constant(12.0f));
        Sphere upperShape=new Sphere();upperShape.setRadius(.57f);upperShape.setRadiusThickness(.36f);
        upper.config.shape.setShape(upperShape);upper.config.shape.setPosition(new NumberFunction3(0,1.02,0));add(f,upper);

        ParticleEmitter wisps=conquerorParticle(emissionTicks,16,.090f,.070f,0x55FF2944,true);
        wisps.config.setMaxParticles(1100);
        wisps.config.emission.setEmissionRate(NumberFunction.constant(7.0f));
        Sphere wispShape=new Sphere();wispShape.setRadius(.66f);wispShape.setRadiusThickness(.58f);
        wisps.config.shape.setShape(wispShape);wisps.config.shape.setPosition(new NumberFunction3(0,.78,0));add(f,wisps);

        ParticleEmitter stormMotes=particleTex(emissionTicks,14,.14f,.048f,0x58FF2A46,true,SPARK);
        stormMotes.config.setMaxParticles(1200);
        stormMotes.config.emission.setEmissionRate(NumberFunction.constant(5.5f));
        Sphere moteShape=new Sphere();moteShape.setRadius(1.35f);moteShape.setRadiusThickness(.86f);
        stormMotes.config.shape.setShape(moteShape);stormMotes.config.shape.setPosition(new NumberFunction3(0,.72,0));add(f,stormMotes);
        return f;
    }

    /** Layered MAX-Conqueror death field: the original lightning still arches above the kneeling
     * player, while a separate non-arched family erupts from random points around the body. */
    private static FX lastStandPulse(float q,long seed){
        q=Math.max(.02f,Math.min(1f,q));
        FX f=fx();
        // Visual storm deliberately reaches beyond the 25-block-radius control field. At full power
        // the main channels tear 32-41 blocks across the scene instead of dying at the stun edge.
        float reach=6.0f+30.0f*q;
        int travelTicks=8+Math.round(5.0f*q);

        addLastStandStormParticles(f,q,reach);
        addLastStandOverheadArches(f,q,reach,travelTicks,new Random(seed^0x4C4153545354414EL));
        addLastStandBodyBolts(f,q,reach,travelTicks,new Random(seed^0x5A49475A41475A47L));

        ParticleEmitter bodySnap=particleTex(22,15,1.15f+.85f*q,.064f,0xEFFF2743,true,SPARK);
        Sphere bodySnapShape=new Sphere();bodySnapShape.setRadius(.56f);bodySnapShape.setRadiusThickness(.46f);
        bodySnap.config.shape.setShape(bodySnapShape);bodySnap.config.shape.setPosition(new NumberFunction3(0,.82,0));
        bodySnap.config.setMaxParticles(420);burstCycles(bodySnap,42+(int)(34*q),travelTicks,2,4);add(f,bodySnap);
        return f;
    }

    /** Body-level lightning that shares the overhead crown's travelling animation but not its arch.
     * Every pulse chooses new points in an air shell just outside the player's body and new
     * 360-degree directions. The paths use only six to eight irregular sections, avoiding the
     * fixed four-lane sawtooth pattern. */
    private static void addLastStandBodyBolts(FX f,float q,float reach,int travelTicks,Random r){
        int families=3+r.nextInt(3)+(q>.82f?1:0);
        double ringOffset=r.nextDouble()*Math.PI*2.0;
        double sector=Math.PI*2.0/families;

        for(int bolt=0;bolt<families;bolt++){
            // One randomized direction per loose sector prevents both a fixed cardinal cross and an
            // unlucky pulse where every bolt piles into the same side of the player.
            double angle=ringOffset+bolt*sector+(r.nextDouble()-.5D)*sector*.72D;
            double originAngle=angle+(r.nextDouble()-.5D)*.55D;
            // Keep the thick shell and its twisting filament visibly clear of the model. Starting
            // 0.88-1.16 blocks from the entity centre leaves a small pocket of air around the body
            // instead of making every channel look as if it is piercing out through the torso.
            double bodyRadius=.88D+r.nextDouble()*.28D;
            double originY=.30D+r.nextDouble()*1.15D;
            Vec3 origin=new Vec3(Math.cos(originAngle)*bodyRadius,originY,
                    Math.sin(originAngle)*bodyRadius);
            Vec3 direction=new Vec3(Math.cos(angle),0,Math.sin(angle));
            Vec3 perpendicular=new Vec3(-direction.z,0,direction.x);

            double radius=Math.max(7.0,reach*(.88D+r.nextDouble()*.24D));
            double endY=Math.max(.15D,Math.min(2.40D,
                    originY+(r.nextDouble()-.5D)*(1.20D+1.10D*q)));
            int segments=6+r.nextInt(3);
            double phase=r.nextDouble()*Math.PI*2.0;
            double lateralMemory=(r.nextDouble()*2.0D-1.0D)*(.28D+.30D*q);
            double verticalMemory=(r.nextDouble()*2.0D-1.0D)*(.20D+.26D*q);
            Vec3 previous=origin;
            Vec3 previousFilament=origin;

            for(int i=1;i<=segments;i++){
                double t=i/(double)segments;
                double envelope=Math.sin(Math.PI*t);
                Vec3 next;
                if(i==segments){
                    next=origin.add(direction.scale(radius)).add(0,endY-originY,0);
                }else{
                    // Correlated randomness creates a few natural lightning bends instead of the
                    // old forced left/right/left/right triangle wave.
                    lateralMemory=lateralMemory*.28D
                            +(r.nextDouble()*2.0D-1.0D)*(.48D+.50D*q);
                    verticalMemory=verticalMemory*.28D
                            +(r.nextDouble()*2.0D-1.0D)*(.34D+.40D*q);
                    double forwardJitter=(r.nextDouble()*2.0D-1.0D)*(.18D+.24D*q)*envelope;
                    next=origin.add(direction.scale(radius*t+forwardJitter))
                            .add(perpendicular.scale(lateralMemory*(.72D+.28D*envelope)))
                            .add(0,(endY-originY)*t+verticalMemory*(.72D+.28D*envelope),0);
                }

                // Like the accepted overhead family, the channel travels outward from the body.
                int delay=(int)Math.floor((i-1)*(travelTicks/(double)segments));
                int duration=8+(i%3);
                Vector3f a=v3(previous);
                Vector3f b=v3(next);
                add(f,beam(a,b,.176f+.030f*q,0xEC050307,duration+2,delay,false));
                add(f,rageBeam(a,b,.054f+.015f*q,0xFFFF1433,duration+1,delay,true));
                add(f,beam(a,b,.014f+.005f*q,0xFFFF9AA7,duration,delay,true));

                double helix=t*Math.PI*(4.0D+.80D*q)+phase;
                double filamentRadius=(.15D+.18D*q)*envelope;
                Vec3 filamentNext=next
                        .add(perpendicular.scale(Math.cos(helix)*filamentRadius))
                        .add(0,Math.sin(helix)*filamentRadius,0);
                if(i==segments)filamentNext=next;
                add(f,rageBeam(v3(previousFilament),v3(filamentNext),.020f+.006f*q,
                        0xEFFF2A47,duration-1,delay+1,true));

                if((i+bolt)%3==0){
                    add(f,rageBeam(a,b,.019f+.004f*q,0xD6FF4B62,5,delay+5,true));
                }

                if(i>1&&i<segments&&r.nextFloat()<(.18f+.12f*q)){
                    double branchLength=.70D+r.nextDouble()*(.95D+1.25D*q);
                    double branchSide=r.nextBoolean()?1.0D:-1.0D;
                    Vec3 branch=next
                            .add(perpendicular.scale(branchSide*branchLength))
                            .add(direction.scale((r.nextDouble()-.45D)*.80D))
                            .add(0,(r.nextDouble()-.42D)*(1.00D+.85D*q),0);
                    Vector3f c=v3(branch);
                    add(f,beam(b,c,.070f+.015f*q,0xDF060408,6,delay+1,false));
                    add(f,rageBeam(b,c,.019f+.004f*q,0xFFFF2441,5,delay+1,true));
                }
                previous=next;
                previousFilament=filamentNext;
            }

            ParticleEmitter endpoint=particleTex(14,12,.62f+.36f*q,.058f,0xDFFF2946,true,SPARK);
            Sphere endpointShape=new Sphere();endpointShape.setRadius(.34f+.12f*q);endpointShape.setRadiusThickness(.68f);
            endpoint.config.shape.setShape(endpointShape);
            endpoint.config.shape.setPosition(new NumberFunction3(previous.x,previous.y,previous.z));
            endpoint.config.setMaxParticles(180);burst(endpoint,24+(int)(28*q),0);add(f,endpoint);
        }
    }

    /** Original overhead crown, now given WiFi-style sequential travel, filaments and live forks. */
    private static void addLastStandOverheadArches(FX f,float q,float reach,int travelTicks,Random r){
        int families=3+r.nextInt(3)+(q>.72f?1:0);
        // Crown origins float clearly above/outside the head and shoulders. None of these
        // start points touch the player's model, so the arches read as surrounding Haki.
        Vec3[] anchors={
                new Vec3(-.58,1.92,.00),
                new Vec3( .58,1.92,.00),
                new Vec3(-.72,1.74,.10),
                new Vec3( .72,1.74,.10),
                new Vec3(-.48,1.82,.34),
                new Vec3( .48,1.82,.34)
        };
        int anchorOffset=r.nextInt(anchors.length);

        for(int arc=0;arc<families;arc++){
            Vec3 origin=anchors[(anchorOffset+arc*2)%anchors.length];
            double side=Math.signum(origin.x);
            if(side==0) side=r.nextBoolean()?1D:-1D;

            double angle=(side<0?Math.PI:0D)+(r.nextDouble()-.5)*1.35;
            if(arc==families-1&&r.nextFloat()<.45f) angle=r.nextDouble()*Math.PI*2D;
            double radius=Math.max(7.0,reach*(.72+r.nextDouble()*.30));
            double endY=.25+r.nextDouble()*(1.35+1.30*q);
            double archHeight=Math.min(14.0,4.5+radius*.21+r.nextDouble()*3.0);
            double phase=r.nextDouble()*Math.PI*2D;
            int segments=10+r.nextInt(4)+(int)(q*3.0f);
            Vec3 radialDirection=new Vec3(Math.cos(angle),0,Math.sin(angle));
            Vec3 sideDirection=new Vec3(-Math.sin(angle),0,Math.cos(angle));

            Vec3 previous=origin;
            Vec3 previousFilament=origin;
            for(int i=1;i<=segments;i++){
                double t=i/(double)segments;
                double envelope=Math.sin(Math.PI*t);
                double radial=radius*t;
                double lateral=(Math.sin(t*Math.PI*(2.4+r.nextDouble()*.9)+phase)*(.36+.58*q)
                        +(r.nextDouble()*2D-1D)*(.24+.42*q))*envelope;
                double x=origin.x+Math.cos(angle)*radial-Math.sin(angle)*lateral;
                double z=origin.z+Math.sin(angle)*radial+Math.cos(angle)*lateral;
                double y=origin.y+(endY-origin.y)*t+archHeight*envelope
                        +Math.sin(t*Math.PI*4D+phase)*(.20+.30*q)*envelope;
                Vec3 next=i==segments
                        ?new Vec3(origin.x+Math.cos(angle)*radius,endY,origin.z+Math.sin(angle)*radius)
                        :new Vec3(x,y,z);

                int delay=(int)Math.floor((i-1)*(travelTicks/(double)segments));
                int duration=8+(i%3);
                Vector3f a=v3(previous);
                Vector3f b=v3(next);
                add(f,beam(a,b,.176f+.030f*q,0xEC050307,duration+2,delay,false));
                add(f,rageBeam(a,b,.054f+.015f*q,0xFFFF1433,duration+1,delay,true));
                add(f,beam(a,b,.014f+.005f*q,0xFFFF9AA7,duration,delay,true));

                double helix=t*Math.PI*(5.2+q*1.3)+phase;
                double filamentRadius=(.16D+.20D*q)*envelope;
                Vec3 filamentNext=next
                        .add(sideDirection.scale(Math.cos(helix)*filamentRadius))
                        .add(0,Math.sin(helix)*filamentRadius,0);
                if(i==segments)filamentNext=next;
                add(f,rageBeam(v3(previousFilament),v3(filamentNext),.021f+.006f*q,
                        0xEFFF2A47,duration-1,delay+1,true));

                if((i+arc)%4==0){
                    add(f,rageBeam(a,b,.019f+.004f*q,0xD6FF4B62,5,delay+5,true));
                }

                if(i>1&&i<segments-1&&r.nextFloat()<(.30f+.18f*q)){
                    double forkSide=(r.nextBoolean()?1D:-1D)*(1.0+r.nextDouble()*(1.55+2.0*q));
                    Vec3 fork=next
                            .add(radialDirection.scale(.55+r.nextDouble()*(1.2+q)))
                            .add(sideDirection.scale(forkSide))
                            .add(0,(r.nextDouble()-.30D)*(1.8D+1.6D*q),0);
                    Vector3f c=v3(fork);
                    add(f,beam(b,c,.078f+.018f*q,0xDF060408,6,delay+1,false));
                    add(f,rageBeam(b,c,.020f+.005f*q,0xFFFF2441,5,delay+1,true));
                }
                previous=next;
                previousFilament=filamentNext;
            }

            int endpointDelay=Math.max(0,travelTicks-1);
            for(int fork=0;fork<3;fork++){
                double forkAngle=r.nextDouble()*Math.PI*2.0;
                double length=.65+r.nextDouble()*(1.15+q);
                Vec3 tip=previous.add(Math.cos(forkAngle)*length,
                        (r.nextDouble()-.45D)*(1.5D+q),Math.sin(forkAngle)*length);
                add(f,beam(v3(previous),v3(tip),.060f,0xDD070408,6,endpointDelay,false));
                add(f,rageBeam(v3(previous),v3(tip),.016f,0xFFFF2A46,5,endpointDelay,true));
            }
        }

        for(int side=-1;side<=1;side+=2){
            Vector3f a=new Vector3f(side*.31f,1.05f,.02f);
            Vector3f b=new Vector3f(side*(1.05f+r.nextFloat()*.75f),
                    1.20f+r.nextFloat()*1.25f,(r.nextFloat()-.5f)*.80f);
            add(f,beam(a,b,.085f,0xDE060408,8,0,false));
            add(f,rageBeam(a,b,.024f,0xFFFF2945,7,0,true));
        }
    }

    /** Dense moving atmosphere around the long channels so the battlefield never reads as empty. */
    private static void addLastStandStormParticles(FX f,float q,float reach){
        float fieldRadius=1.6f+Math.min(9.0f,reach*.24f);

        ParticleEmitter redSparks=particleTex(28,18,.65f+.85f*q,.052f+.010f*q,0xDFFF2441,true,SPARK);
        Sphere redShape=new Sphere();redShape.setRadius(fieldRadius);redShape.setRadiusThickness(.82f);
        redSparks.config.shape.setShape(redShape);redSparks.config.shape.setPosition(new NumberFunction3(0,.92,0));
        redSparks.config.setMaxParticles(900);burstCycles(redSparks,34+(int)(42*q),0,3,4);add(f,redSparks);

        ParticleEmitter blackMotes=conquerorParticle(30,20,.38f+.46f*q,.082f,0xA8060409,false);
        Sphere blackShape=new Sphere();blackShape.setRadius(fieldRadius*.78f);blackShape.setRadiusThickness(.88f);
        blackMotes.config.shape.setShape(blackShape);blackMotes.config.shape.setPosition(new NumberFunction3(0,.78,0));
        blackMotes.config.setMaxParticles(720);burstCycles(blackMotes,24+(int)(34*q),1,3,5);add(f,blackMotes);

        ParticleEmitter energyFlares=particleTex(24,16,1.35f+1.75f*q,.070f,0xAFFF3A53,true,RAGE_GLOW);
        Sphere flareShape=new Sphere();flareShape.setRadius(1.2f+3.8f*q);flareShape.setRadiusThickness(.72f);
        energyFlares.config.shape.setShape(flareShape);energyFlares.config.shape.setPosition(new NumberFunction3(0,.82,0));
        energyFlares.config.setMaxParticles(620);burstCycles(energyFlares,30+(int)(38*q),1,3,4);add(f,energyFlares);

        ParticleEmitter pressure=conquerorParticle(22,20,4.8f+7.5f*q,.050f,0x72E4DEE0,true);
        Circle pressureShape=new Circle();pressureShape.setRadius(.68f);pressureShape.setRadiusThickness(.018f);
        pressure.config.shape.setShape(pressureShape);pressure.config.shape.setPosition(new NumberFunction3(0,.08,0));
        pressure.config.setMaxParticles(620);burst(pressure,120+(int)(110*q),1);add(f,pressure);
    }

    /** The pre-death detonation scales to the authoritative gameplay radius. */
    private static FX lastStandFinalBlast(long seed,int radius){
        FX f=fx();
        float scale=Math.max(1.0f,radius/15.0f);

        ParticleEmitter core=conquerorParticle(14,15,4.2f,.280f,0xF8FFF6F4,true);
        Sphere coreShape=new Sphere();coreShape.setRadius(.42f);coreShape.setRadiusThickness(.30f);
        core.config.shape.setShape(coreShape);core.config.shape.setPosition(new NumberFunction3(0,.78,0));
        core.config.setMaxParticles(500);burst(core,180,0);add(f,core);

        ParticleEmitter blackShell=conquerorParticle(30,28,10.8f*scale,.340f,0xE8060308,false);
        Sphere blackShape=new Sphere();blackShape.setRadius(.86f);blackShape.setRadiusThickness(.48f);
        blackShell.config.shape.setShape(blackShape);blackShell.config.shape.setPosition(new NumberFunction3(0,.72,0));
        blackShell.config.setMaxParticles(1200);burst(blackShell,420,1);add(f,blackShell);

        ParticleEmitter redShell=conquerorParticle(28,26,13.2f*scale,.300f,0xCFFF1736,true);
        Sphere redShape=new Sphere();redShape.setRadius(.78f);redShape.setRadiusThickness(.44f);
        redShell.config.shape.setShape(redShape);redShell.config.shape.setPosition(new NumberFunction3(0,.74,0));
        redShell.config.setMaxParticles(1400);burst(redShell,520,2);add(f,redShell);

        // A broad pale pressure body makes the detonation read as one enormous outward volume,
        // rather than only two thin ground rings racing away from a small cluster of red dots.
        ParticleEmitter outerAir=conquerorParticle(26,24,15.0f*scale,.205f,0x86E8E2E4,true);
        Sphere airShape=new Sphere();airShape.setRadius(1.08f);airShape.setRadiusThickness(.36f);
        outerAir.config.shape.setShape(airShape);outerAir.config.shape.setPosition(new NumberFunction3(0,.76,0));
        outerAir.config.setMaxParticles(1100);burst(outerAir,400,3);add(f,outerAir);

        ParticleEmitter pressure=conquerorParticle(24,22,16.5f*scale,.140f,0xA8E2DCDE,true);
        Circle pressureShape=new Circle();pressureShape.setRadius(.90f);pressureShape.setRadiusThickness(.026f);
        pressure.config.shape.setShape(pressureShape);pressure.config.shape.setPosition(new NumberFunction3(0,.10,0));
        pressure.config.setMaxParticles(1100);burst(pressure,430,0);add(f,pressure);

        ParticleEmitter secondFront=conquerorParticle(28,24,21.0f*scale,.100f,0x6BFF334D,true);
        Circle secondShape=new Circle();secondShape.setRadius(1.20f);secondShape.setRadiusThickness(.022f);
        secondFront.config.shape.setShape(secondShape);secondFront.config.shape.setPosition(new NumberFunction3(0,.12,0));
        secondFront.config.setMaxParticles(950);burst(secondFront,350,5);add(f,secondFront);

        // The final storm rides over the full 25-block-radius shove so the death beat fills the scene.
        merge(f,lastStandPulse(.72f,seed^0x46494E414C424C54L));
        return f;
    }

    /**
     * The Last Stand detonation, layered on top of the existing final blast.
     *
     * <p>Built to be grand without being expensive: the huge shapes are single expanding
     * billboards rather than clouds of sprites, so the whole thing costs a few dozen emitters
     * instead of thousands. A white core, stacked ground and air shock rings that keep arriving,
     * a debris skirt, a rising ember column and a slow smoke crown that outlives everything else.
     *
     * @param radius the detonation radius in blocks, straight from the server
     */
    private static FX lastStandDetonation(long seed,int radius){
        FX f=fx();
        Random r=new Random(seed^0x1A57574E44L);
        float R=Math.max(8f,radius);

        // Core: a hard white nucleus that blows out and collapses.
        ParticleEmitter core=HakiFx.flash(26,20,.10f,R*.30f,0xFFFFFFFF);
        HakiFx.sphere(core,R*.10f,1f);
        HakiFx.burst(core,20,0);
        add(f,core);

        // Ground rings, arriving in sequence and reaching past the damage radius.
        for(int i=0;i<6;i++){
            ParticleEmitter ring=HakiFx.groundRing(46+i*5,36+i*4,R*.16f,
                    R*(.55f+.30f*i),i<2?0xF0FFFFFF:(i<4?0xD8FFC0A0:0xB0FF6048));
            HakiFx.at(ring,new Vec3(0,.12+i*.05,0));
            HakiFx.burst(ring,1,i*3);
            add(f,ring);
        }
        // Vertical fronts so it reads as a sphere of pressure, not just a floor wave.
        for(int i=0;i<3;i++){
            ParticleEmitter front=HakiFx.airRing(40+i*6,32+i*5,R*.14f,
                    R*(.70f+.34f*i),i==0?0xE8FFFFFF:0xA8FFB090);
            HakiFx.at(front,new Vec3(0,1.2+i*1.5,0));
            HakiFx.burst(front,1,2+i*4);
            add(f,front);
        }

        // Debris skirt thrown off the crater rim.
        ParticleEmitter skirt=HakiFx.debris(60,48,R*.30f,R*.045f,0xB09A9089,r);
        HakiFx.circle(skirt,R*.20f,.55f);
        HakiFx.at(skirt,new Vec3(0,.20,0));
        HakiFx.burst(skirt,220,1);
        add(f,skirt);

        // Ember column climbing out of the core.
        ParticleEmitter column=HakiFx.embers(70,56,R*.16f,R*.030f,0xFFFFD2A0,r);
        HakiFx.sphere(column,R*.12f,.70f);
        HakiFx.at(column,new Vec3(0,.60,0));
        HakiFx.burst(column,260,0);
        add(f,column);

        // Smoke crown: slow, huge, and the last thing left standing.
        for(int i=0;i<4;i++){
            ParticleEmitter crown=HakiFx.smoke(120+i*12,96+i*10,R*.055f,R*.16f,
                    i<2?0xC8564E4C:0xB83A3634,2.4f,r);
            HakiFx.sphere(crown,R*(.26f+.13f*i),.72f);
            HakiFx.at(crown,new Vec3(0,1.0+i*2.4,0));
            HakiFx.burst(crown,150,6+i*5);
            add(f,crown);
        }
        return f;
    }

    /**
     * The quick G tap: a forward pressure cone, not a scaled-down version of the full release.
     *
     * <p>The tap used to borrow the held release's omnidirectional burst, which both misrepresented
     * what it does -- it only affects a 75-degree forward cone -- and buried a light move under a
     * heavy effect. This draws the cone itself: ring fronts that widen as they run out along the
     * aim vector, a compressed core at the mouth, and a few short bolts riding the leading edge, so
     * the shape on screen is the shape that hits.
     */
    private static FX conquerorTapCone(Vec3 aim,float power,long seed){
        FX f=fx();
        Random r=new Random(seed^0x7A9C09E1L);
        Vec3 dir=aim.lengthSqr()<1.0E-6?new Vec3(0,0,1):aim.normalize();
        Vec3 reference=Math.abs(dir.y)<.94D?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 right=dir.cross(reference).normalize();
        Vec3 up=right.cross(dir).normalize();
        float q=Math.max(.15f,Math.min(1f,power));
        double reach=10.0D;

        // Compressed core right at the mouth of the cone.
        ParticleEmitter core=HakiFx.flash(10,8,.05f,.30f+.12f*q,0xFFFFE0EA);
        HakiFx.sphere(core,.16f,1f);
        HakiFx.burst(core,10,0);
        add(f,core);

        // Ring fronts widening as they travel: this is the cone made visible.
        int fronts=5;
        for(int i=0;i<fronts;i++){
            double along=reach*(i+1)/(double)(fronts+1);
            float width=(float)(.55D+along*.62D);
            ParticleEmitter front=HakiFx.airRing(20+i*3,15+i*3,width*.42f,width*1.35f,
                    i<2?0xE0FFFFFF:0xB4FF9AA6);
            HakiFx.at(front,dir.scale(along));
            HakiFx.burst(front,1,i*2);
            add(f,front);
        }

        // Air dragged along the cone wall so the volume reads, not just its rings.
        int walls=6;
        for(int i=0;i<walls;i++){
            double t=(i+.5D)/walls;
            double along=reach*t;
            double spread=.45D+2.05D*t;
            double angle=r.nextDouble()*Math.PI*2D;
            Vec3 centre=dir.scale(along)
                    .add(right.scale(Math.cos(angle)*spread*.6D))
                    .add(up.scale(Math.sin(angle)*spread*.6D));
            ParticleEmitter wall=HakiFx.smoke(22+i*2,17+i*2,.05f,(float)(.22D+.30D*t),
                    0xA6584E55,1.7f,r);
            HakiFx.sphere(wall,(float)spread*.55f,.55f);
            HakiFx.at(wall,centre);
            HakiFx.burst(wall,10+i*2,Math.min(5,i));
            add(f,wall);
        }

        // A few short bolts riding the leading edge.
        int bolts=2+(int)(q*3f);
        for(int i=0;i<bolts;i++){
            double angle=r.nextDouble()*Math.PI*2D;
            double spread=1.15D+r.nextDouble()*1.35D;
            Vec3 end=dir.scale(reach*(.72D+r.nextDouble()*.26D))
                    .add(right.scale(Math.cos(angle)*spread))
                    .add(up.scale(Math.sin(angle)*spread));
            HakiFx.boltPath(f,dir.scale(.35D),end,6,.10D,.80f,false,7+r.nextInt(3),0,.34f,r);
        }
        return f;
    }

    private static FX conquerorCharge(float q,int mastery,long seed){
        FX f=fx();
        ParticleEmitter aura=conquerorParticle(12,12,.08f+.08f*q,.050f,0xB8C51E30,q>.35f);
        Sphere s=new Sphere();s.setRadius(.50f+q*.82f);s.setRadiusThickness(.16f+.08f*q);aura.config.shape.setShape(s);aura.config.shape.setPosition(new NumberFunction3(0,1,0));burst(aura,8+(int)(24*q),0);add(f,aura);

        ParticleEmitter sparks=conquerorParticle(10,9,.32f+.28f*q,.050f,0xFFFF2B45,true);
        Sphere ss=new Sphere();ss.setRadius(.55f+q*.68f);ss.setRadiusThickness(.24f);sparks.config.shape.setShape(ss);sparks.config.shape.setPosition(new NumberFunction3(0,1,0));burst(sparks,4+(int)(22*q),1);add(f,sparks);

        if(q>.30f){
            ParticleEmitter pressure=conquerorParticle(10,10,.85f+1.8f*q,.026f,0x77D9D3D5,q>.7f);
            Circle c=new Circle();c.setRadius(.34f+.20f*q);c.setRadiusThickness(.02f);pressure.config.shape.setShape(c);pressure.config.shape.setPosition(new NumberFunction3(0,.10,0));burst(pressure,24+(int)(35*q),1);add(f,pressure);
        }
        // Lightning is present from the first tap and its endpoints show the exact current server
        // contact radius. Every pulse receives a fresh seed, so charging never becomes a static cage.
        addConquerorRangeLightning(f,q,mastery,seed,false,0);
        return f;
    }

    /**
     * Tapped G release: a single forward pressure cone aligned to the player's look vector.
     * Sparse black/red edge channels frame the wave; most of the read comes from delayed pressure
     * discs, so this cannot be mistaken for the held release's radial lightning explosion.
     */
    private static FX conquerorRelease(float q,int variant,long seed){
        FX f=fx();
        int mastery=Math.max(0,variant/4);
        boolean fullCharge=q>=.999f;
        boolean supreme=q>=.96f && mastery>=1000;

        addConquerorRangeLightning(f,q,mastery,seed,true,0);
        if(fullCharge) fullChargeConquerorBurst(f,supreme);

        // A max-mastery release gets a second independently randomized wave at the same real range.
        if(supreme){
            addConquerorRangeLightning(f,q,mastery,seed^0x6A09E667F3BCC909L,true,7);
        }

        // Low, flat pressure front.
        ParticleEmitter ring=conquerorParticle(supreme?18:14,supreme?22:18,supreme?10.5f:2.8f+4.2f*q,supreme?.105f:.07f,0x99D7D0D2,supreme);
        Circle circle=new Circle();circle.setRadius(supreme?.55f:.35f);circle.setRadiusThickness(.02f);ring.config.shape.setShape(circle);ring.config.shape.setPosition(new NumberFunction3(0,.08,0));burst(ring,supreme?210:60+(int)(80*q),1);add(f,ring);

        // Red pressure shell exploding through the player's full body.
        ParticleEmitter shell=conquerorParticle(supreme?20:15,supreme?22:16,supreme?5.8f:1.8f+2.3f*q,supreme?.13f:.085f,0x77D3182E,true);
        Sphere sphere=new Sphere();sphere.setRadius(supreme?.68f:.45f);sphere.setRadiusThickness(.04f);shell.config.shape.setShape(sphere);shell.config.shape.setPosition(new NumberFunction3(0,1,0));burst(shell,supreme?170:48+(int)(52*q),3);add(f,shell);

        // Fast white/grey air displacement layer.
        ParticleEmitter air=conquerorParticle(supreme?16:12,supreme?17:13,supreme?7.6f:1.4f+2.1f*q,supreme?.04f:.025f,0xC8D8D2D4,false);
        Sphere as=new Sphere();as.setRadius(supreme?1.05f:.7f);as.setRadiusThickness(supreme?.62f:.4f);air.config.shape.setShape(as);air.config.shape.setPosition(new NumberFunction3(0,.7,0));burst(air,supreme?260:50+(int)(95*q),0);add(f,air);

        ParticleEmitter sparks=conquerorParticle(supreme?18:12,supreme?15:10,supreme?1.7f:.72f+.7f*q,supreme?.075f:.045f,0xFFFF2944,true);
        Sphere sparkShape=new Sphere();sparkShape.setRadius(supreme?.82f:.55f);sparkShape.setRadiusThickness(supreme?.42f:.24f);sparks.config.shape.setShape(sparkShape);sparks.config.shape.setPosition(new NumberFunction3(0,1.0,0));burst(sparks,supreme?92:16+(int)(28*q),1);add(f,sparks);

        if(supreme){
            ParticleEmitter core=conquerorParticle(12,12,2.2f,.16f,0xEEFFF8F5,true);
            Sphere coreShape=new Sphere();coreShape.setRadius(.48f);coreShape.setRadiusThickness(.24f);core.config.shape.setShape(coreShape);core.config.shape.setPosition(new NumberFunction3(0,1.0,0));burst(core,96,0);add(f,core);

            // The anime release floods the entire frame with a red pressure field before the
            // lightning clears. This wide corona gives the blast that "the whole world just
            // turned red" read without resorting to a flat full-screen overlay.
            ParticleEmitter corona=conquerorParticle(30,26,2.25f,.16f,0x66FF1732,true);
            Sphere coronaShape=new Sphere();coronaShape.setRadius(1.10f);coronaShape.setRadiusThickness(.62f);corona.config.shape.setShape(coronaShape);corona.config.shape.setPosition(new NumberFunction3(0,1.0,0));burst(corona,190,1);add(f,corona);

            ParticleEmitter ink=conquerorParticle(24,18,1.15f,.075f,0xEE08060A,false);
            Sphere inkShape=new Sphere();inkShape.setRadius(.90f);inkShape.setRadiusThickness(.48f);ink.config.shape.setShape(inkShape);ink.config.shape.setPosition(new NumberFunction3(0,1.0,0));burst(ink,72,2);add(f,ink);

            supremePressureFronts(f);
            skyBreak(f,new Random(seed^0xBB67AE8584CAA73BL));
            skyRift(f,new Random(seed^0xA54FF53A5F1D36F1L));
            horizonCracks(f,new Random(seed^0x3C6EF372FE94F82BL));

            // Two delayed pressure fronts make the max release read as a real blast wave from a distance.
            ParticleEmitter ring2=conquerorParticle(22,20,13.5f,.075f,0x77C9C2C5,true);
            Circle c2=new Circle();c2.setRadius(.72f);c2.setRadiusThickness(.018f);ring2.config.shape.setShape(c2);ring2.config.shape.setPosition(new NumberFunction3(0,.1,0));burst(ring2,180,5);add(f,ring2);

            ParticleEmitter ring3=conquerorParticle(26,22,16.5f,.055f,0x55E4233A,true);
            Circle c3=new Circle();c3.setRadius(.9f);c3.setRadiusThickness(.014f);ring3.config.shape.setShape(c3);ring3.config.shape.setPosition(new NumberFunction3(0,.12,0));burst(ring3,150,9);add(f,ring3);

        }
        return f;
    }

    /**
     * Draws the exact deterministic paths used by the server. Release remains dominated by outward
     * Final-Stand-style channels; charge mixes a sparse radial family with a small overhead crown.
     * Each path keeps the black shell, animated rage-lightning core and hot filament. Roots remain
     * in an air shell around the model so the lightning never appears to grow out of the player's
     * skull, and visible charging contacts stay authoritative rather than decorative.
     */
    private static void addConquerorRangeLightning(FX f,float q,int mastery,long seed,
                                                    boolean release,int baseDelay){
        q=Math.max(ConquerorLightningPath.MIN_RELEASE_CHARGE/100.0f,Math.min(1.0f,q));
        float trained=Math.max(0.0f,Math.min(1.0f,mastery/1000.0f));
        double reach=ConquerorLightningPath.radius(mastery,q);
        List<ConquerorLightningPath.Bolt> bolts=ConquerorLightningPath.generate(
                q,mastery,seed,release);
        Random style=new Random(seed^(release?0xD1B54A32D192ED03L:0x94D049BB133111EBL));
        // Charge pulses must clear before the next server-authored pulse arrives. The old
        // 6-10 tick travel plus 7-9 tick segment lifetime caused two or three independent MAX
        // pulses to coexist and visually multiply into dozens of radial "lasers".
        int travelTicks=release?14+Math.round(7.0f*q):9+Math.round(4.0f*q);
        float shellWidth=release?.176f+.030f*q:.125f+.025f*q;
        float coreWidth=release?.054f+.015f*q:.040f+.010f*q;
        float hotWidth=release?.014f+.005f*q:.011f+.003f*q;

        for(int boltIndex=0;boltIndex<bolts.size();boltIndex++){
            List<Vec3> points=bolts.get(boltIndex).points();
            if(points.size()<2)continue;
            Vec3 endpoint=points.get(points.size()-1);
            Vec3 radial=new Vec3(endpoint.x,0.0D,endpoint.z);
            if(radial.lengthSqr()<1.0E-6D)radial=new Vec3(1.0D,0.0D,0.0D);
            radial=radial.normalize();
            Vec3 perpendicular=new Vec3(-radial.z,0.0D,radial.x);
            double filamentPhase=style.nextDouble()*Math.PI*2.0D;
            Vec3 previousFilament=points.get(0);
            int segments=points.size()-1;

            for(int section=1;section<points.size();section++){
                Vec3 previous=points.get(section-1);
                Vec3 next=points.get(section);
                double t=section/(double)segments;
                double envelope=Math.sin(Math.PI*t);
                int delay=baseDelay+(int)Math.floor((section-1)
                        *(travelTicks/(double)segments));
                int duration=release?12+style.nextInt(4):8+style.nextInt(3);
                Vector3f a=v3(previous);
                Vector3f b=v3(next);

                add(f,beam(a,b,shellWidth,release?0xEC050307:0xE3050307,
                        duration+2,delay,false));
                add(f,rageBeam(a,b,coreWidth,release?0xFFFF1433:0xF5FF1A38,
                        duration+1,delay,true));
                add(f,beam(a,b,hotWidth,release?0xFFFF9AA7:0xFFFF7889,
                        duration,delay,true));

                // A continuous twisting filament gives each sparse, natural bend the same live
                // animation as Final Stand without turning it into a dense sawtooth.
                double helix=t*Math.PI*(release?4.8D:4.0D)+filamentPhase;
                double filamentRadius=(release?.15D+.18D*q:.045D+.055D*q)*envelope;
                Vec3 filamentNext=next
                        .add(perpendicular.scale(Math.cos(helix)*filamentRadius))
                        .add(0.0D,Math.sin(helix)*filamentRadius,0.0D);
                if(section==segments)filamentNext=next;
                add(f,rageBeam(v3(previousFilament),v3(filamentNext),
                        release?.020f+.006f*q:.010f+.002f*q,0xEFFF2A47,
                        Math.max(4,duration-1),delay+1,true));

                // Delayed redraws keep long channels animated after the travelling front passes.
                if(release&&(section+boltIndex)%3==0){
                    add(f,rageBeam(a,b,.019f+.004f*q,
                            0xD6FF4B62,5,delay+4,true));
                }

                // Release-only forks are visual texture inside the already affected radial field.
                // Charging has no decorative forks, so every visible contact path remains exactly
                // collision-testable by the server.
                if(release&&section>1&&section<segments
                        &&style.nextFloat()<(.08f+.07f*q+.025f*trained)){
                    double branchLength=.65D+style.nextDouble()*(.85D+1.15D*q);
                    double side=style.nextBoolean()?1.0D:-1.0D;
                    Vec3 branch=next
                            .add(perpendicular.scale(side*branchLength))
                            .add(radial.scale((style.nextDouble()-.48D)*.70D))
                            .add(0.0D,(style.nextDouble()-.42D)*(1.00D+.70D*q),0.0D);
                    double branchRadius=Math.sqrt(branch.x*branch.x+branch.z*branch.z);
                    if(branchRadius>reach&&branchRadius>1.0E-6D){
                        double clamp=reach/branchRadius;
                        branch=new Vec3(branch.x*clamp,branch.y,branch.z*clamp);
                    }
                    Vector3f c=v3(branch);
                    add(f,beam(b,c,.068f+.014f*q,0xDF060408,6,delay+1,false));
                    add(f,rageBeam(b,c,.018f+.004f*q,0xFFFF2441,5,delay+1,true));
                }
                previousFilament=filamentNext;
            }

            int endpointDelay=baseDelay+Math.max(0,travelTicks-1);
            ParticleEmitter endpointBurst=particleTex(release?16:12,release?13:10,
                    release?.66f+.38f*q:.44f+.24f*q,
                    release?.060f:.046f,0xE8FF2946,true,SPARK);
            Sphere endpointShape=new Sphere();
            endpointShape.setRadius(release?.36f+.14f*q:.25f+.09f*q);
            endpointShape.setRadiusThickness(.68f);
            endpointBurst.config.shape.setShape(endpointShape);
            endpointBurst.config.shape.setPosition(new NumberFunction3(
                    endpoint.x,endpoint.y,endpoint.z));
            endpointBurst.config.setMaxParticles(release?220:130);
            burst(endpointBurst,release?18+(int)(20*q):7+(int)(9*q),endpointDelay);
            add(f,endpointBurst);
        }
    }

    /** Full-charge Haoshoku release: a dense black/red energy sphere detonates outward while
     *  the shared range lightning marks the exact edge of the gameplay field. */
    private static void fullChargeConquerorBurst(FX f,boolean supreme){
        // The MAX release first forms a thick, overlapping pressure WALL around the player.
        // Its lower half is buried into the ground so the visible silhouette reads as a huge dome,
        // and repeated burst cycles keep the wall coherent while it expands instead of becoming dots.
        fullChargeConquerorDome(f,supreme);

        // Dense red shell: starts compact around the caster and races outward as a true spherical blast.
        ParticleEmitter redSphere=conquerorParticle(30,28,supreme?12.5f:10.5f,supreme?.20f:.17f,0xB8FF1738,true);
        redSphere.config.setMaxParticles(900);
        Sphere redShape=new Sphere();redShape.setRadius(.82f);redShape.setRadiusThickness(.16f);
        redSphere.config.shape.setShape(redShape);redSphere.config.shape.setPosition(new NumberFunction3(0,1.0,0));
        burst(redSphere,supreme?420:340,0);add(f,redSphere);

        // Slightly slower black shell stays visible behind the red front, giving the blast a black/red body.
        ParticleEmitter blackSphere=conquerorParticle(34,30,supreme?10.8f:9.0f,supreme?.23f:.20f,0xEE050408,false);
        blackSphere.config.setMaxParticles(850);
        Sphere blackShape=new Sphere();blackShape.setRadius(.94f);blackShape.setRadiusThickness(.20f);
        blackSphere.config.shape.setShape(blackShape);blackSphere.config.shape.setPosition(new NumberFunction3(0,1.0,0));
        burst(blackSphere,supreme?360:300,1);add(f,blackSphere);

        // A second red front punches through the dark shell so the sphere visibly explodes instead of fading.
        ParticleEmitter redFront=conquerorParticle(28,25,supreme?15.0f:12.8f,supreme?.12f:.10f,0xDDFF263F,true);
        redFront.config.setMaxParticles(950);
        Sphere frontShape=new Sphere();frontShape.setRadius(1.10f);frontShape.setRadiusThickness(.30f);
        redFront.config.shape.setShape(frontShape);redFront.config.shape.setPosition(new NumberFunction3(0,1.0,0));
        burst(redFront,supreme?460:380,3);add(f,redFront);

    }


    /**
     * A long-lived MAX Haoshoku pressure dome. These are deliberately oversized soft particles with
     * heavy overlap, not spark sprites: at normal camera distance they merge into a continuous
     * red/black energy wall. Multiple speeds make the wall peel outward in layers for ~2-3 seconds.
     */
    private static void fullChargeConquerorDome(FX f,boolean supreme){
        float mul=supreme?1.18f:1.0f;

        // Slow near-black body: the dense mass behind the blast front.
        ParticleEmitter blackWall=conquerorParticle(90,58,3.8f*mul,.72f*mul,0xEE050307,false);
        blackWall.config.setMaxParticles(1800);
        Sphere blackDome=new Sphere();blackDome.setRadius(1.20f);blackDome.setRadiusThickness(.88f);
        blackWall.config.shape.setShape(blackDome);blackWall.config.shape.setPosition(new NumberFunction3(0,.28,0));
        burstCycles(blackWall,supreme?150:125,0,5,3);add(f,blackWall);

        // Deep crimson body interlocks with the black layer so there are no transparent holes.
        ParticleEmitter crimsonWall=conquerorParticle(94,54,4.9f*mul,.62f*mul,0xDDB40924,true);
        crimsonWall.config.setMaxParticles(1800);
        Sphere crimsonDome=new Sphere();crimsonDome.setRadius(1.05f);crimsonDome.setRadiusThickness(.92f);
        crimsonWall.config.shape.setShape(crimsonDome);crimsonWall.config.shape.setPosition(new NumberFunction3(0,.32,0));
        burstCycles(crimsonWall,supreme?165:140,1,5,3);add(f,crimsonWall);

        // Bright red leading surface. Large soft sprites overlap into the visible dome skin.
        ParticleEmitter redSkin=conquerorParticle(88,48,6.3f*mul,.54f*mul,0xE8FF1738,true);
        redSkin.config.setMaxParticles(1900);
        Sphere redDome=new Sphere();redDome.setRadius(.92f);redDome.setRadiusThickness(.78f);
        redSkin.config.shape.setShape(redDome);redSkin.config.shape.setPosition(new NumberFunction3(0,.35,0));
        burstCycles(redSkin,supreme?180:155,2,5,3);add(f,redSkin);

        // A faster dark skin overtakes the body, keeping the expanding edge black/red rather than pink.
        ParticleEmitter darkFront=conquerorParticle(82,43,7.5f*mul,.46f*mul,0xD80A0308,false);
        darkFront.config.setMaxParticles(1500);
        Sphere darkFrontDome=new Sphere();darkFrontDome.setRadius(1.0f);darkFrontDome.setRadiusThickness(.70f);
        darkFront.config.shape.setShape(darkFrontDome);darkFront.config.shape.setPosition(new NumberFunction3(0,.38,0));
        burstCycles(darkFront,supreme?135:110,5,4,4);add(f,darkFront);

        // Delayed hot-red pressure lip. This is what makes the dome visibly keep exploding outward
        // after the first blast rather than flashing once and instantly disappearing.
        ParticleEmitter afterWall=conquerorParticle(84,40,9.0f*mul,.42f*mul,0xDFFF2946,true);
        afterWall.config.setMaxParticles(1500);
        Sphere afterDome=new Sphere();afterDome.setRadius(1.12f);afterDome.setRadiusThickness(.62f);
        afterWall.config.shape.setShape(afterDome);afterWall.config.shape.setPosition(new NumberFunction3(0,.40,0));
        burstCycles(afterWall,supreme?140:115,10,4,4);add(f,afterWall);
    }

    private static void supremePressureFronts(FX f){
        int[] delays={0,3,7,12};
        float[] speeds={8.5f,12.0f,16.0f,20.0f};
        int[] colors={0xDDECE8E8,0xAAE6273E,0x88D8D2D4,0x66FF263F};
        for(int i=0;i<delays.length;i++){
            ParticleEmitter ring=conquerorParticle(24+i*2,22,speeds[i],.045f+(3-i)*.012f,colors[i],i!=2);
            Circle c=new Circle();c.setRadius(.48f+i*.16f);c.setRadiusThickness(.018f);ring.config.shape.setShape(c);ring.config.shape.setPosition(new NumberFunction3(0,.10+i*.025,0));
            burst(ring,170-i*20,delays[i]);add(f,ring);
        }
    }

    private static void skyBreak(FX f,Random r){
        for(int trunk=0;trunk<6;trunk++){
            double a=Math.PI*2*trunk/6.0+(r.nextDouble()-.5)*.35;
            Vector3f prev=new Vector3f((r.nextFloat()-.5f)*.8f,.9f,(r.nextFloat()-.5f)*.8f);
            int seg=4+r.nextInt(3);
            for(int i=1;i<=seg;i++){
                float t=i/(float)seg;
                float radial=(1.6f+trunk*.10f)*t;
                Vector3f next=new Vector3f((float)Math.cos(a)*radial+(r.nextFloat()-.5f)*1.6f,2.0f+t*(17f+r.nextFloat()*11f),(float)Math.sin(a)*radial+(r.nextFloat()-.5f)*1.6f);
                int delay=i/2;
                add(f,beam(prev,next,.21f,0xE0050507,8+r.nextInt(4),delay,false));
                add(f,beam(prev,next,.052f,0xFFFF2E48,7+r.nextInt(4),delay,true));
                if(i>2 && r.nextFloat()<.48f){
                    Vector3f branch=new Vector3f(next).add((r.nextFloat()-.5f)*6f,(r.nextFloat()-.15f)*4f,(r.nextFloat()-.5f)*6f);
                    add(f,beam(next,branch,.10f,0xD9080609,7,delay+1,false));
                    add(f,beam(next,branch,.025f,0xFFFF314A,6,delay+1,true));
                }
                prev=next;
            }
        }
    }

    private static void skyRift(FX f,Random r){
        // One enormous jagged fracture hangs high above the caster for a few frames.
        // It is not literal sky destruction; it is the visual "sky splitting" pressure cue.
        for(int rift=0;rift<1;rift++){
            float z=(r.nextFloat()-.5f)*5.0f;
            Vector3f prev=new Vector3f(-36f,20f+r.nextFloat()*4f,z);
            for(int i=1;i<=9;i++){
                float x=-36f+i*8.0f;
                Vector3f next=new Vector3f(x,19f+r.nextFloat()*6.5f,z+(r.nextFloat()-.5f)*6.0f);
                int delay=4+i/3+rift;
                add(f,beam(prev,next,.26f,0xE8040406,12+r.nextInt(4),delay,false));
                add(f,beam(prev,next,.058f,0xFFFF304C,11+r.nextInt(4),delay,true));
                if(i>1 && i<9 && r.nextFloat()<.58f){
                    Vector3f fork=new Vector3f(next).add((r.nextFloat()-.5f)*8f,(r.nextFloat()-.25f)*7f,(r.nextFloat()-.5f)*8f);
                    add(f,beam(next,fork,.105f,0xC9060508,8,delay+1,false));
                    add(f,beam(next,fork,.024f,0xFFFF3A54,7,delay+1,true));
                }
                prev=next;
            }
        }
    }

    private static void horizonCracks(FX f,Random r){
        final int rays=10;
        for(int ray=0;ray<rays;ray++){
            double a=Math.PI*2*ray/rays+(r.nextDouble()-.5)*.20;
            int seg=4+r.nextInt(3);
            float reach=26f+r.nextFloat()*18f;
            Vector3f prev=new Vector3f(0,.9f+r.nextFloat()*.8f,0);
            for(int i=1;i<=seg;i++){
                float t=i/(float)seg;
                float rr=reach*t;
                Vector3f next=new Vector3f((float)Math.cos(a)*rr+(r.nextFloat()-.5f)*2.0f,.4f+r.nextFloat()*4.6f+(float)Math.sin(t*Math.PI)*4.0f,(float)Math.sin(a)*rr+(r.nextFloat()-.5f)*2.0f);
                int delay=2+i;
                add(f,beam(prev,next,.17f,0xDC050507,9+r.nextInt(4),delay,false));
                add(f,beam(prev,next,.040f,0xFFFF223E,8+r.nextInt(3),delay,true));
                prev=next;
            }
        }
    }

    private static FX dominion(float q){
        FX f=fx();
        ParticleEmitter haze=particle(35,26,.30f,.055f,0x77210B18,true);
        Sphere s=new Sphere();s.setRadius(1.25f+q*.8f);s.setRadiusThickness(.42f);haze.config.shape.setShape(s);haze.config.shape.setPosition(new NumberFunction3(0,1,0));burst(haze,60+(int)(q*45),0);add(f,haze);
        lightningFamilies(f,.45f+.35f*q,0xAA060508,0xFFD51D36,3+(int)(q*3),3,5,new Random((long)(q*47711)+17));
        return f;
    }

    /** Same jagged Haki bolt architecture as lightningFamilies, but rooted at an arbitrary local
     *  point (fist, feet, or remote impact) instead of the player's torso. */
    private static void lightningFamiliesAt(FX f,Vector3f origin,float q,int outer,int inner,int families,int minSeg,int maxSeg,Random r){
        for(int family=0;family<families;family++){
            double ang=(Math.PI*2*family/families)+(r.nextDouble()-.5)*.60;
            float reach=(float)(.65+q*(1.5+r.nextDouble()*5.8));
            int seg=minSeg+r.nextInt(Math.max(1,maxSeg-minSeg+1));
            Vector3f prev=new Vector3f(origin).add((r.nextFloat()-.5f)*.18f,(r.nextFloat()-.5f)*.18f,(r.nextFloat()-.5f)*.18f);
            for(int i=1;i<=seg;i++){
                float t=i/(float)seg;
                float radial=reach*t;
                float jitter=(.10f+.40f*q)*(1-t*.22f);
                Vector3f next=new Vector3f(origin.x+(float)Math.cos(ang)*radial+(r.nextFloat()-.5f)*jitter,
                        prev.y+(r.nextFloat()-.5f)*(.42f+.60f*q),
                        origin.z+(float)Math.sin(ang)*radial+(r.nextFloat()-.5f)*jitter);
                int delay=i/3;
                add(f,beam(prev,next,.055f+.055f*q,outer,5+r.nextInt(4),delay,false));
                add(f,beam(prev,next,.014f+.020f*q,inner,4+r.nextInt(4),delay,true));
                if(r.nextFloat()<(.10f+.14f*q)&&i>1){
                    Vector3f branch=new Vector3f(next).add((r.nextFloat()-.5f)*1.3f,(r.nextFloat()-.40f)*.8f,(r.nextFloat()-.5f)*1.3f);
                    add(f,beam(next,branch,.034f,outer,4,delay+1,false));
                    add(f,beam(next,branch,.010f,inner,3,delay+1,true));
                }
                prev=next;
            }
        }
    }

    private static void lightningFamilies(FX f,float q,int outer,int inner,int families,int minSeg,int maxSeg,Random r){
        for(int family=0;family<families;family++){
            double ang=(Math.PI*2*family/families)+(r.nextDouble()-.5)*.55;float reach=(float)(2.2+q*(4+r.nextDouble()*18));int seg=minSeg+r.nextInt(Math.max(1,maxSeg-minSeg+1));Vector3f prev=new Vector3f(0,.75f+(r.nextFloat()*.8f),0);
            for(int i=1;i<=seg;i++){
                float t=i/(float)seg;float radial=reach*t;float jitter=(.2f+.75f*q)*(1-t*.25f);Vector3f next=new Vector3f((float)Math.cos(ang)*radial+(r.nextFloat()-.5f)*jitter,prev.y+(r.nextFloat()-.5f)*(.9f+q), (float)Math.sin(ang)*radial+(r.nextFloat()-.5f)*jitter);
                int delay=i/3;add(f,beam(prev,next,.10f+.08f*q,outer,5+r.nextInt(4),delay,false));add(f,beam(prev,next,.025f+.025f*q,inner,4+r.nextInt(4),delay,true));
                if(r.nextFloat()<(.10f+.17f*q)&&i>1){Vector3f branch=new Vector3f(next).add((r.nextFloat()-.5f)*2f,(r.nextFloat()-.35f)*1.4f,(r.nextFloat()-.5f)*2f);add(f,beam(next,branch,.055f,outer,4,delay+1,false));add(f,beam(next,branch,.015f,inner,3,delay+1,true));}
                prev=next;
            }
            if(q>.6f&&r.nextFloat()<.45f){Vector3f a=new Vector3f(prev).mul(.35f);Vector3f b=new Vector3f(a).add((r.nextFloat()-.5f)*3f,(r.nextFloat()-.5f), (r.nextFloat()-.5f)*3f);add(f,beam(a,b,.035f,inner,4,10+r.nextInt(13),true));}
        }
    }

    private static void lightningFamiliesDelayed(FX f,float q,int outer,int inner,int families,int minSeg,int maxSeg,Random r,int baseDelay){
        for(int family=0;family<families;family++){
            double ang=(Math.PI*2*family/families)+(r.nextDouble()-.5)*.5;
            float reach=(float)(7+q*(8+r.nextDouble()*18));
            int seg=minSeg+r.nextInt(Math.max(1,maxSeg-minSeg+1));
            Vector3f prev=new Vector3f(0,.7f+r.nextFloat()*1.2f,0);
            for(int i=1;i<=seg;i++){
                float t=i/(float)seg;
                float radial=reach*t;
                float jitter=(.3f+.8f*q)*(1-t*.2f);
                Vector3f next=new Vector3f((float)Math.cos(ang)*radial+(r.nextFloat()-.5f)*jitter,prev.y+(r.nextFloat()-.5f)*1.3f,(float)Math.sin(ang)*radial+(r.nextFloat()-.5f)*jitter);
                int delay=baseDelay+i/2;
                add(f,beam(prev,next,.14f,outer,7+r.nextInt(4),delay,false));
                add(f,beam(prev,next,.035f,inner,6+r.nextInt(4),delay,true));
                prev=next;
            }
        }
    }

    private static FX acoc(long seed){
        FX f=fx();
        // Activation gets three deliberate arcs. Persistent fire and the yellow cores are rendered
        // from the live hand bones in HakiHandLayer; entity-root flame bursts were the source of
        // detached particles floating beside the fists during animation.
        lightningFamilies(f,.72f,0xDD050507,0xFFFF233E,3,3,5,new Random(seed));
        TrailEmitter right=new TrailEmitter();right.config.setDuration(85);right.config.setLooping(false);right.config.setTime(12);right.config.setWidthOverTrail(NumberFunction.constant(.075f));right.config.setColorOverTrail(NumberFunction.color(0xFFE31D36));right.config.renderer.setBloomEffect(true);right.setPos(.28,1.15,-.18);add(f,right);
        TrailEmitter left=new TrailEmitter();left.config.setDuration(85);left.config.setLooping(false);left.config.setTime(12);left.config.setWidthOverTrail(NumberFunction.constant(.055f));left.config.setColorOverTrail(NumberFunction.color(0xFF09070B));left.config.renderer.setBloomEffect(false);left.setPos(-.28,1.15,-.18);add(f,left);
        ParticleEmitter sparks=particleTex(14,11,.46f,.050f,0xFFFF2A43,true,SPARK);
        Sphere ss=new Sphere();ss.setRadius(.72f);ss.setRadiusThickness(.22f);sparks.config.shape.setShape(ss);sparks.config.shape.setPosition(new NumberFunction3(0,1.05,0));burst(sparks,18,1);add(f,sparks);
        return f;
    }

    /** WiFi Haki is a sustained, server-authored Conqueror connection. Every refresh redraws the
     * same high arch with a different violent corkscrew/kink phase: black shell, saturated red core,
     * a hot inner filament and sparse fork lightning. There are deliberately no rings/circles and
     * never a straight laser between caster and victim. */
    private static FX wifiHakiArc(Vec3 delta,float q,int travelTicks,int pulseIndex,int beamCount,long seed){
        FX f=fx();
        double distance=delta.length();
        Vec3 forward=delta.normalize();
        Vec3 worldUp=new Vec3(0,1,0);
        Vec3 right=forward.cross(worldUp);
        if(right.lengthSqr()<1.0E-5)right=new Vec3(1,0,0);
        else right=right.normalize();
        Vec3 sideUp=right.cross(forward).normalize();

        int beams=Math.max(1,Math.min(3,beamCount));
        double laneSpacing=Math.max(1.65,Math.min(6.25,1.10+distance*.036));
        for(int beamIndex=0;beamIndex<beams;beamIndex++){
            // 1 beam: centre. 2 beams: clean left/right lanes. 3 beams: left / higher centre / right.
            // The lane offset is multiplied by sin(pi*t), so every beam starts from the SAME hand,
            // immediately diverges, stays separated through the crown, then converges on the target.
            double lane=beams==1?0.0:(beams==2?(beamIndex==0?-1.0:1.0):(beamIndex-1.0));
            double archBias=(beams==3&&beamIndex==1)?1.18:(1.00+.035*beamIndex);
            long beamSeed=seed+(beamIndex+1L)*0x2C1B3C6D1A7F49E5L;
            Random r=new Random(beamSeed^0x5749464948414B49L^((long)pulseIndex*0x9E3779B97F4A7C15L));

            int segments=Math.max(10,Math.min(28,9+(int)Math.ceil(distance/7.0)));
            double archHeight=Math.max(5.5,Math.min(29.0,4.0+distance*.155))*archBias;
            double phase=pulseIndex*.83+(beamSeed&255L)*.013+beamIndex*1.73;
            double turns=2.15+.32*Math.sin(phase*.47+beamIndex*.71);
            Vec3 prev=Vec3.ZERO;
            Vec3 prevCoil=Vec3.ZERO;

            for(int i=1;i<=segments;i++){
                double t=i/(double)segments;
                double envelope=Math.sin(Math.PI*t);
                double arch=Math.sin(Math.PI*t)*archHeight;
                arch*=1.0+.12*Math.sin(t*Math.PI*3.0+phase*.35);
                double helix=t*Math.PI*2.0*turns+phase;
                double verticalCoil=Math.sin(helix)*archHeight*.115*envelope;
                double depthCoil=Math.sin(helix+Math.PI*.5)*laneSpacing*.20*envelope;

                // Permanent lane separation dominates the random kink. This is the anti-overlap rule.
                double laneOffset=lane*laneSpacing*envelope;
                double jag=(r.nextDouble()*2.0-1.0)*laneSpacing*.18*envelope;
                jag+=((i/2)%2==0?1:-1)*laneSpacing*.08*envelope;
                double verticalJitter=(r.nextDouble()*2.0-1.0)*(.52+q*.72)*envelope;
                double depthJitter=(r.nextDouble()*2.0-1.0)*(.38+distance*.0035)*envelope;

                Vec3 next=delta.scale(t)
                        .add(worldUp.scale(arch+verticalCoil+verticalJitter))
                        .add(right.scale(laneOffset+jag))
                        .add(sideUp.scale(depthCoil+depthJitter));
                if(i==segments)next=delta;

                int delay=(int)Math.floor((i-1)*(travelTicks/(double)segments));
                int duration=7+(i%3);
                Vector3f a=new Vector3f((float)prev.x,(float)prev.y,(float)prev.z);
                Vector3f b=new Vector3f((float)next.x,(float)next.y,(float)next.z);
                float shell=.135f+.050f*q+(beams>1?.008f:0f);
                add(f,HakiFx.beam(a,b,shell,0xEA050407,duration+2,delay,false,HakiFx.ARC_SOFT));
                add(f,HakiFx.beam(a,b,.047f+.020f*q,0xFFFF1532,duration+1,delay,true,HakiFx.ARC));
                add(f,HakiFx.beam(a,b,.013f+.007f*q,0xFFFF7888,duration,delay,true,HakiFx.ARC));

                // Thin twisting filament per lane. It wraps its OWN beam, never the neighbouring lane.
                double coilRadius=(.16+.15*q)*envelope;
                Vec3 coilOffset=right.scale(Math.cos(helix)*coilRadius)
                        .add(sideUp.scale(Math.sin(helix)*coilRadius));
                Vec3 coilNext=next.add(coilOffset);
                if(i==segments)coilNext=delta;
                Vector3f ca=new Vector3f((float)prevCoil.x,(float)prevCoil.y,(float)prevCoil.z);
                Vector3f cb=new Vector3f((float)coilNext.x,(float)coilNext.y,(float)coilNext.z);
                add(f,HakiFx.beam(ca,cb,.027f,0xC9080508,duration,delay,false,HakiFx.ARC_SOFT));
                add(f,HakiFx.beam(ca,cb,.009f,0xEFFF2945,duration-1,delay,true,HakiFx.ARC));

                if(i>1&&i<segments&&r.nextFloat()<(.27f+.16f*q)){
                    Vec3 tangent=next.subtract(prev).normalize();
                    Vec3 branchDir=tangent.cross(worldUp);
                    if(branchDir.lengthSqr()<1.0E-5)branchDir=right;
                    else branchDir=branchDir.normalize();
                    double branchLen=.55+r.nextDouble()*(1.05+1.45*q);
                    // Bias branches outward from the lane so adjacent main beams remain readable.
                    double outward=lane==0.0?(r.nextBoolean()?1:-1):Math.signum(lane);
                    Vec3 branch=next.add(branchDir.scale(outward*branchLen))
                            .add(worldUp.scale((r.nextDouble()-.43)*1.55))
                            .add(sideUp.scale((r.nextDouble()-.5)*.70));
                    Vector3f c=new Vector3f((float)branch.x,(float)branch.y,(float)branch.z);
                    add(f,HakiFx.beam(b,c,.062f,0xDD060408,5,delay+1,false,HakiFx.ARC_SOFT));
                    add(f,HakiFx.beam(b,c,.016f,0xFFFF2440,4,delay+1,true,HakiFx.ARC));
                }
                // The bolt now carries a travelling head and sheds embers. Both use the same
                // per-segment delay as the beam, so they stay welded to the advancing tip
                // instead of appearing along the whole path at once.
                if((i%2)==0||i==segments){
                    ParticleEmitter head=HakiFx.flash(6,5,.05f,.30f+.14f*q,0xFFFF9AA4);
                    HakiFx.sphere(head,.14f,1f);
                    HakiFx.at(head,next);
                    HakiFx.burst(head,4,delay);
                    add(f,head);

                    ParticleEmitter shed=HakiFx.embers(12,10,.30f,.055f+.020f*q,0xFFFF4038,r);
                    HakiFx.sphere(shed,.24f,.60f);
                    HakiFx.at(shed,next);
                    HakiFx.burst(shed,5,delay);
                    add(f,shed);
                }

                prev=next;
                prevCoil=coilNext;
            }

            // Each arc independently japs the endpoint. These are short crooked forks, not an AoE shell.
            int finalDelay=Math.max(0,travelTicks-1);
            for(int i=0;i<3;i++){
                double a=r.nextDouble()*Math.PI*2.0;
                double len=.42+r.nextDouble()*.78;
                Vec3 tip=delta.add(Math.cos(a)*len,(r.nextDouble()-.5)*1.30,Math.sin(a)*len);
                Vector3f from=new Vector3f((float)delta.x,(float)delta.y,(float)delta.z);
                Vector3f to=new Vector3f((float)tip.x,(float)tip.y,(float)tip.z);
                add(f,HakiFx.beam(from,to,.055f,0xDD070408,5,finalDelay,false,HakiFx.ARC_SOFT));
                add(f,HakiFx.beam(from,to,.014f,0xFFFF2A46,4,finalDelay,true,HakiFx.ARC));
            }

            // Arrival: a hot core and one pressure ring at the endpoint, so the strike lands
            // rather than simply stopping.
            if(beamIndex==0){
                ParticleEmitter land=HakiFx.flash(14,11,.30f,.52f+.20f*q,0xFFFFD2CE);
                HakiFx.sphere(land,.26f,.85f);
                HakiFx.at(land,delta);
                HakiFx.burst(land,22,finalDelay);
                add(f,land);

                ParticleEmitter shock=HakiFx.airRing(20,16,.55f,3.40f,0xC8FF9098);
                HakiFx.at(shock,delta);
                HakiFx.burst(shock,1,finalDelay);
                add(f,shock);
            }
        }
        return f;
    }

    private static FX sovereignLock(float q,long seed){
        FX f=fx();
        Random r=new Random(seed);
        Vector3f corePos=new Vector3f(0,1.0f,0);

        // Black/red Haoshoku lines collapse from a loose cage into the victim instead of exploding
        // outward immediately. This visually sells the control phase before the Verdict detonates.
        int spokes=12;
        for(int i=0;i<spokes;i++){
            double angle=(Math.PI*2.0*i/spokes)+(r.nextDouble()-.5)*.18;
            float radius=2.3f+q*1.15f+r.nextFloat()*.45f;
            float y=.25f+r.nextFloat()*1.75f;
            Vector3f outer=new Vector3f((float)Math.cos(angle)*radius,y,(float)Math.sin(angle)*radius);
            Vector3f inner=new Vector3f((r.nextFloat()-.5f)*.12f,.86f+r.nextFloat()*.30f,(r.nextFloat()-.5f)*.12f);
            int delay=i%4;
            add(f,beam(outer,inner,.12f,0xE0050407,10+delay,delay,false));
            add(f,beam(outer,inner,.032f,0xFFFF2039,9+delay,delay,true));
        }

        ParticleEmitter cage=particleTex(12,12,.15f,.050f,0xE8FF233E,true,SPARK);
        Sphere sphere=new Sphere();sphere.setRadius(2.25f+q*.75f);sphere.setRadiusThickness(.18f);
        cage.config.shape.setShape(sphere);cage.config.shape.setPosition(new NumberFunction3(0,1.0,0));burst(cage,64,0);add(f,cage);

        ParticleEmitter core=particle(12,11,.18f,.105f,0xF4FFF8F6,true);
        Sphere cs=new Sphere();cs.setRadius(.18f);cs.setRadiusThickness(.12f);
        core.config.shape.setShape(cs);core.config.shape.setPosition(new NumberFunction3(0,1.0,0));burst(core,36,5);add(f,core);

        ParticleEmitter ring=particle(12,12,.20f,.045f,0xC8E51D38,true);
        Circle rc=new Circle();rc.setRadius(1.55f+q*.55f);rc.setRadiusThickness(.025f);
        ring.config.shape.setShape(rc);ring.config.shape.setRotation(new NumberFunction3(90,0,0));ring.config.shape.setPosition(new NumberFunction3(0,1.0,0));burst(ring,54,3);add(f,ring);
        return f;
    }

    private static FX sovereign(long seed){
        FX f=fx();
        Random r=new Random(seed);
        lightningFamilies(f,.82f,0xE0060508,0xFFFF243E,8,3,6,r);
        ParticleEmitter core=particle(9,10,2.2f,.12f,0xEEFFF6F4,true);
        Sphere cs=new Sphere();cs.setRadius(.24f);cs.setRadiusThickness(.15f);core.config.shape.setShape(cs);core.config.shape.setPosition(new NumberFunction3(0,1.22,-.38));burst(core,52,0);add(f,core);
        ParticleEmitter cone=particle(11,14,2.35f,.075f,0xE6E02238,true);
        Cone c=new Cone();c.setRadius(.22f);c.setAngle(16);cone.config.shape.setShape(c);cone.config.shape.setRotation(new NumberFunction3(90,0,0));cone.config.shape.setPosition(new NumberFunction3(0,1.2,-.46));burst(cone,88,0);add(f,cone);
        ParticleEmitter ring=particle(14,15,4.8f,.048f,0xA8E7E1E2,true);
        Circle rc=new Circle();rc.setRadius(.28f);rc.setRadiusThickness(.018f);ring.config.shape.setShape(rc);ring.config.shape.setRotation(new NumberFunction3(90,0,0));ring.config.shape.setPosition(new NumberFunction3(0,1.2,-.60));burst(ring,72,2);add(f,ring);
        return f;
    }
    /** King's Grip lock uses only beams arranged in true 3D volume. */
    private static FX kingsGripLock3D(float q,long seed){
        FX f=fx();
        Random r=new Random(seed^0x51A7EEDL);
        Vec3 center=new Vec3(0,1.0,0);

        int spokes=14;
        for(int i=0;i<spokes;i++){
            double angle=(Math.PI*2.0*i/spokes)+(r.nextDouble()-.5)*.20;
            float radius=2.0f+q*1.10f+r.nextFloat()*.55f;
            float y=.20f+r.nextFloat()*1.75f;
            Vector3f outer=new Vector3f((float)Math.cos(angle)*radius,y,(float)Math.sin(angle)*radius);
            Vector3f inner=new Vector3f((r.nextFloat()-.5f)*.16f,.82f+r.nextFloat()*.38f,(r.nextFloat()-.5f)*.16f);
            int delay=i%4;
            add(f,beam(outer,inner,.115f,0xE0050407,10+delay,delay,false));
            add(f,beam(outer,inner,.030f,0xFFFF2039,9+delay,delay,true));
        }

        // Deliberately NO wire hoops/orbits here. The old rings dominated the whole cutscene
        // and looked like a red cage around both actors. Radial beams + lightning read in 3D
        // without drawing any circular silhouette.

        for(int i=0;i<10;i++){
            Vector3f a=new Vector3f((r.nextFloat()-.5f)*.30f,.72f+r.nextFloat()*.58f,(r.nextFloat()-.5f)*.30f);
            Vector3f b=new Vector3f(a).add((r.nextFloat()-.5f)*(1.0f+q),(r.nextFloat()-.5f)*.85f,(r.nextFloat()-.5f)*(1.0f+q));
            add(f,beam(a,b,.038f,0xFFFF2A43,5+r.nextInt(3),r.nextInt(3),true));
        }
        return f;
    }

    /** Armament King's Grip impact: solid pressure hoops + black/red lightning, no sprite particles. */
    private static FX kingsGripHakiImpact3D(long seed){
        FX f=fx();
        Random r=new Random(seed^0x6A09E667F3BCC909L);
        Vector3f center=new Vector3f(0,1.14f,-.34f);
        lightningFamiliesAt(f,center,.95f,0xEF050307,0xFFFF243E,10,3,6,r);

        // Impact burst without circular hoops: short 3D pressure splinters shoot away from contact.
        for(int i=0;i<22;i++){
            double a=r.nextDouble()*Math.PI*2.0D;
            double y=(r.nextDouble()-.5D)*.95D;
            double radial=.28D+r.nextDouble()*.82D;
            Vector3f a0=new Vector3f(center).add((r.nextFloat()-.5f)*.08f,(r.nextFloat()-.5f)*.08f,(r.nextFloat()-.5f)*.08f);
            Vector3f b0=new Vector3f(a0).add((float)(Math.cos(a)*radial),(float)y,(float)(Math.sin(a)*radial));
            add(f,beam(a0,b0,.026f+r.nextFloat()*.018f,i%4==0?0xD9F4EFF0:0xB8FF263F,6+r.nextInt(3),r.nextInt(2),i%4==0));
        }
        return f;
    }

    // Advanced King's Grip wind-up fire is rendered by HakiHandLayer in the live LEFT-hand
    // bone transform. A root-level Photon composition cannot follow a Player Animator fist reliably.

    /** Advanced-Haki detonation: denser 3D pressure geometry, lightning and fire tongues only. */
    private static FX kingsGripAdvancedImpact(long seed){
        FX f=kingsGripHakiImpact3D(seed);
        Random r=new Random(seed^0xBB67AE8584CAA73BL);
        Vector3f center=new Vector3f(0,1.16f,-.46f);
        lightningFamiliesAt(f,center,1.35f,0xF2050307,0xFFFF1835,16,4,8,r);

        // No circular pressure rings in the Advanced detonation either. Keep the center violent
        // with lightning and irregular 3D shards only.

        // Short expanding flame shards at contact; still beam geometry, never billboard sprites.
        for(int i=0;i<18;i++){
            Vector3f a=new Vector3f(center).add((r.nextFloat()-.5f)*.22f,(r.nextFloat()-.5f)*.22f,(r.nextFloat()-.5f)*.22f);
            Vector3f b=new Vector3f(a).add((r.nextFloat()-.5f)*1.25f,.18f+r.nextFloat()*.85f,(r.nextFloat()-.5f)*1.25f);
            add(f,beam(a,b,.065f,0xE8FF220B,6+r.nextInt(3),r.nextInt(2),false));
            add(f,beam(a,b,.021f,0xFFFFB21E,5+r.nextInt(3),r.nextInt(2),true));
        }
        return f;
    }

}
