package com.hexhaki.client.cinematic;

import com.hexhaki.HexHaki;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import static com.hexhaki.client.cinematic.CameraKeyframe.Easing.*;

@Mod.EventBusSubscriber(modid=HexHaki.MODID,value=Dist.CLIENT)
public final class CinematicController {
    private static final Map<String,CameraSequence> SEQUENCES=create();
    private static Active active;
    private static CameraType savedCamera;
    private static int ticks;
    private static boolean lockView;
    private static boolean gripVictim;
    private static int focusId=-1;
    private static float lockedYaw, lockedPitch;
    private CinematicController(){}

    public static void startNamed(String name){ startNamed(name,0f,0f,false,-1); }
    public static void startNamed(String name,float forcedYaw,float forcedPitch,boolean forceOrientation){
        startNamed(name,forcedYaw,forcedPitch,forceOrientation,-1);
    }
    public static void startNamed(String name,float forcedYaw,float forcedPitch,boolean forceOrientation,int focus){
        CameraSequence s=SEQUENCES.get(name); if(s==null)return;
        Minecraft mc=Minecraft.getInstance(); if(mc.player==null)return;
        cancel(); savedCamera=mc.options.getCameraType(); ticks=0; active=new Active(s,0); focusId=focus;
        lockView=name.startsWith("thragg_");
        gripVictim=name.startsWith("thragg_victim");
        if(lockView){
            lockedYaw=forceOrientation?forcedYaw:mc.player.getYRot();
            lockedPitch=forceOrientation?forcedPitch:mc.player.getXRot();
            mc.player.setYRot(lockedYaw); mc.player.setYHeadRot(lockedYaw); mc.player.setXRot(lockedPitch);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if(mc.screen!=null) mc.setScreen(null);
        }
    }
    public static void cancel(){
        if(active!=null){ Minecraft mc=Minecraft.getInstance(); if(savedCamera!=null && mc.player!=null) mc.options.setCameraType(savedCamera); }
        active=null; savedCamera=null; ticks=0; lockView=false; gripVictim=false; focusId=-1;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return; Minecraft mc=Minecraft.getInstance();
        if(active==null)return;
        if(mc.player==null||!mc.player.isAlive()||mc.getConnection()==null){cancel();return;}
        if(lockView){
            if(mc.screen!=null) mc.setScreen(null);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.setYRot(lockedYaw); mc.player.setYHeadRot(lockedYaw); mc.player.setXRot(lockedPitch);
        } else if(mc.screen!=null){ cancel(); return; }
        ticks++; active=new Active(active.sequence,ticks/20f); if(active.time>=active.sequence.duration())cancel();
    }

    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e){ if(active!=null){ Sample s=sample(active); e.setFOV(e.getFOV()+s.fov); } }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){
        if(active==null)return; Sample s=sample(active); double t=ticks+e.getPartialTick(); double n=Math.sin(t*4.7)*s.shake;
        if(lockView){
            // A single sine reads as a mechanical wobble. Summing three incommensurate
            // frequencies per axis gives the camera an operator instead of a motor, which is
            // most of the difference between a staged shot and a shot that feels physical.
            double sx=(Math.sin(t*13.1)*.55+Math.sin(t*5.3+1.7)*.32+Math.sin(t*2.1+.6)*.13)*s.shake;
            double sy=(Math.sin(t*11.7+2.4)*.50+Math.sin(t*4.1+.3)*.34+Math.sin(t*1.7+2.2)*.16)*s.shake;
            double sr=(Math.sin(t*9.3+1.1)*.58+Math.sin(t*3.7+2.8)*.30+Math.sin(t*1.3+.9)*.12)*s.shake;

            // Aim at the subject rather than trusting an authored angle. Every keyframe that
            // dollies along the pair axis moves the camera sideways relative to the body it is
            // framing, and a fixed 90-degree yaw cannot know that -- a metre of dolly at five
            // metres out threw the subject twelve degrees off centre, which is exactly the
            // "everything is happening to the left of frame" the shot list kept producing.
            // With a look-at, the authored yaw and pitch become deliberate framing offsets on
            // top of a subject that is always centred.
            float aimYaw=lockedYaw, aimPitch=lockedPitch;
            Minecraft mc=Minecraft.getInstance();
            net.minecraft.world.entity.Entity camEntity=mc.getCameraEntity();
            Vec3 anchor=shotAnchor(camEntity,(float)e.getPartialTick());
            Vec3 from=kingsGripCameraPosition(camEntity,(float)e.getPartialTick());
            if(anchor!=null&&from!=null){
                Vec3 d=anchor.subtract(from);
                double horizontal=Math.sqrt(d.x*d.x+d.z*d.z);
                if(horizontal>1.0E-4||Math.abs(d.y)>1.0E-4){
                    aimYaw=(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90.0);
                    aimPitch=(float)(-Math.toDegrees(Math.atan2(d.y,horizontal)));
                }
            }
            e.setYaw(aimYaw+s.yaw+(float)(sx*1.55));
            e.setPitch(Math.max(-88f,Math.min(88f,aimPitch+s.pitch+(float)(sy*1.05))));
            e.setRoll(s.roll+(float)(sr*2.10));
        } else {
            e.setYaw(e.getYaw()+s.yaw+(float)n); e.setPitch(e.getPitch()+s.pitch+(float)(n*.45)); e.setRoll(e.getRoll()+s.roll+(float)(n*.65));
        }
    }
    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Post e){
        float letter=active==null?0:sample(active).letterbox; if(letter<=.001)return; GuiGraphics g=e.getGuiGraphics();
        int w=Minecraft.getInstance().getWindow().getGuiScaledWidth(),h=Minecraft.getInstance().getWindow().getGuiScaledHeight(),bar=(int)(h*.12*letter);
        g.fill(0,0,w,bar,0xFF000000);g.fill(0,h-bar,w,h,0xFF000000);
    }

    public static Vec3 cameraOffset(){ return active==null?Vec3.ZERO:sample(active).offset; }

    /**
     * Absolute world position for the King's Grip camera, or {@code null} for every other
     * sequence (those keep the original relative {@code Camera.move} behaviour).
     *
     * <p>Routing this shot through {@code Camera.move} was wrong in three compounding ways, and
     * together they are why the framing drifted out of view and why the two participants never saw
     * the same thing:
     *
     * <ul>
     *   <li>{@code move} is applied after vanilla has already pulled the third-person camera back
     *       by {@code getMaxZoom(4.0)}, which <b>clips against terrain</b>. The same shot therefore
     *       started up to four blocks further out in the open than it did against a wall.</li>
     *   <li>{@code move} works in the camera's own basis, so the offset rotated with the view
     *       instead of staying locked to the two bodies.</li>
     *   <li>Its origin is the local camera entity - the attacker on one client and the suspended
     *       victim on the other - so the composition was anchored to a different body on each
     *       side of the same execution.</li>
     * </ul>
     *
     * <p>The shot is instead built directly from the grip pair. Keyframe offsets are read as
     * (dolly along the pair axis, height above the pair, distance out to the side), and the
     * attacker/victim sign flip cancels the victim's 180-degree yaw, so both participants resolve
     * to the <b>same world position and the same world yaw</b> and watch one shared shot.
     */
    /** The subject the shot is composed around, or {@code null} when there is no active lock. */
    private static Vec3 shotAnchor(net.minecraft.world.entity.Entity cameraEntity,float partialTick){
        if(active==null||!lockView||cameraEntity==null)return null;
        net.minecraft.world.entity.Entity anchorEntity=cameraEntity;
        if(focusId>=0&&Minecraft.getInstance().level!=null){
            net.minecraft.world.entity.Entity focus=Minecraft.getInstance().level.getEntity(focusId);
            if(focus!=null) anchorEntity=focus;
        }
        double yaw=Math.toRadians(lockedYaw);
        Vec3 forward=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        Vec3 self=new Vec3(
                net.minecraft.util.Mth.lerp(partialTick,anchorEntity.xo,anchorEntity.getX()),
                net.minecraft.util.Mth.lerp(partialTick,anchorEntity.yo,anchorEntity.getY()),
                net.minecraft.util.Mth.lerp(partialTick,anchorEntity.zo,anchorEntity.getZ()));
        return self.add(forward.scale(.45)).add(0,1.18,0);
    }

    public static Vec3 kingsGripCameraPosition(net.minecraft.world.entity.Entity cameraEntity,float partialTick){
        if(active==null||!lockView||cameraEntity==null)return null;
        Sample s=sample(active);
        double yaw=Math.toRadians(lockedYaw);
        // Both participants face each other, so "forward" is toward the other body on either client.
        Vec3 forward=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        Vec3 right=new Vec3(-forward.z,0,forward.x);
        float sign=gripVictim?-1f:1f;

        // Anchor on the focus entity when the server named one. The Haki Grip throws the victim
        // fifty blocks up while the attacker waits at the top: a camera riding its own body means
        // the attacker watches themselves stand still while everything the move is about happens
        // to somebody else. Both participants are given the same focus, and the attacker/victim
        // sign flip below cancels the victim's 180-degree yaw, so both resolve to one shared shot.
        Vec3 mid=shotAnchor(cameraEntity,partialTick);
        if(mid==null)return null;

        // Side offsets run along LEFT, not right. Vanilla's Camera.move() takes its horizontal
        // argument along `left`, and porting the composition to an absolute placement silently
        // flipped that: the camera sat beside the pair facing directly away from them.
        Vec3 want=mid
                .add(forward.scale(sign*s.offset.x))
                .add(0,s.offset.y,0)
                .subtract(right.scale(sign*s.offset.z));
        return clipToTerrain(mid,want);
    }

    /** Pulls the camera in if the shot would place it inside terrain, so the absolute placement
     *  cannot bury the view in a wall the way an unclipped hard position would. */
    private static Vec3 clipToTerrain(Vec3 from,Vec3 to){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return to;
        Vec3 delta=to.subtract(from);
        double length=delta.length();
        if(length<1.0E-4)return to;
        double best=length;
        // Probe the corners of a small box as vanilla does, so the camera does not skim a wall
        // and clip through it at the edges.
        for(int i=0;i<8;i++){
            Vec3 nudge=new Vec3((i&1)==0?.10:-.10,(i&2)==0?.10:-.10,(i&4)==0?.10:-.10);
            net.minecraft.world.phys.HitResult hit=mc.level.clip(new net.minecraft.world.level.ClipContext(
                    from.add(nudge),to.add(nudge),
                    net.minecraft.world.level.ClipContext.Block.VISUAL,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS){
                double d=hit.getLocation().distanceTo(from);
                if(d<best)best=d;
            }
        }
        if(best>=length)return to;
        return from.add(delta.scale(Math.max(0.0,best-.18)/length));
    }
    public static boolean isKingsGripActive(){ return active!=null && active.sequence.name().startsWith("thragg_"); }
    private static Sample sample(Active a){
        List<CameraKeyframe> f=a.sequence.frames(); if(f.isEmpty())return Sample.ZERO; if(a.time<=f.get(0).time())return Sample.of(f.get(0));
        for(int i=1;i<f.size();i++)if(a.time<=f.get(i).time()){
            CameraKeyframe x=f.get(i-1),y=f.get(i); double raw=(a.time-x.time())/(y.time()-x.time()); double t=CameraKeyframe.ease(y.easing(),raw);
            return new Sample(x.offset().lerp(y.offset(),t),lerp(x.yaw(),y.yaw(),t),lerp(x.pitch(),y.pitch(),t),lerp(x.roll(),y.roll(),t),lerp(x.fovDelta(),y.fovDelta(),t),lerp(x.shake(),y.shake(),t),lerp(x.letterbox(),y.letterbox(),t));
        } return Sample.of(f.get(f.size()-1));
    }
    private static float lerp(float a,float b,double t){return (float)(a+(b-a)*t);}
    private record Active(CameraSequence sequence,float time){}
    private record Sample(Vec3 offset,float yaw,float pitch,float roll,float fov,float shake,float letterbox){
        static final Sample ZERO=new Sample(Vec3.ZERO,0,0,0,0,0,0); static Sample of(CameraKeyframe k){return new Sample(k.offset(),k.yaw(),k.pitch(),k.roll(),k.fovDelta(),k.shake(),k.letterbox());}
    }

    private static Map<String,CameraSequence> create(){
        Map<String,CameraSequence> m=new HashMap<>();
        m.put("supreme_conqueror",new CameraSequence("supreme_conqueror",List.of(
                k(0,0,0,0,0,0,0,0),k(.22,.18,.03,0,-1.5f,-8,.10f,.28f),k(.55,.42,.04,.08,1.2f,-14,.16f,.65f),k(.9,.18,0,.02,0,7,.28f,.8f),k(1.45,0,0,0,0,0,.08f,.25f),k(2.1,0,0,0,0,0,0,0))));
        m.put("galaxy_charge",new CameraSequence("galaxy_charge",List.of(
                k(0,0,0,0,0,-2,.00f,.06f),
                k(.45,.16,.08,.04,-1.0f,-8,.015f,.16f),
                k(1.25,.28,.12,.08,-2.2f,-13,.025f,.28f),
                k(2.40,.34,.15,.10,-3.0f,-17,.020f,.34f),
                k(3.65,.30,.12,.07,-2.0f,-14,.015f,.28f),
                k(4.75,.18,.06,.03,-1.0f,-9,.020f,.18f),
                k(5.15,0,0,0,0,-3,.04f,.08f))));
        m.put("galaxy_impact",new CameraSequence("galaxy_impact",List.of(
                k(0,0,0,0,0,-4,.02f,.12f),
                k(.16,.10,.02,.025,-1.0f,-10,.06f,.30f),
                k(.38,.28,.04,.06,-2.2f,-16,.10f,.52f),
                k(.54,.12,.01,.025,1.0f,8,.24f,.74f),
                k(.82,.05,0,0,.4f,3,.12f,.40f),
                k(1.18,0,0,0,0,0,.02f,.08f),
                k(1.42,0,0,0,0,0,0,0))));

        // Haki Grip tier cinematics. Beats are HakiServerController.thraggBeats in seconds:
        // uppercut / ascend / slam / ground / duration.
        m.put("thragg_attacker_none", thraggCamera("thragg_attacker_none", false, 1.80f, 2.20f, 4.30f, 5.20f, 6.70f, false));
        m.put("thragg_victim_none", thraggCamera("thragg_victim_none", true, 1.80f, 2.20f, 4.30f, 5.20f, 6.70f, false));
        m.put("thragg_attacker_haki", thraggCamera("thragg_attacker_haki", false, 2.20f, 2.60f, 5.00f, 6.00f, 7.50f, false));
        m.put("thragg_victim_haki", thraggCamera("thragg_victim_haki", true, 2.20f, 2.60f, 5.00f, 6.00f, 7.50f, false));
        m.put("thragg_attacker_advanced", thraggCamera("thragg_attacker_advanced", false, 2.60f, 3.00f, 5.80f, 7.00f, 8.50f, true));
        m.put("thragg_victim_advanced", thraggCamera("thragg_victim_advanced", true, 2.60f, 3.00f, 5.80f, 7.00f, 8.50f, true));
        return m;
    }
    /**
     * Haki Grip shot list.
     *
     * <p>Five beats, cut to the server's schedule. Each one has a different job, and the camera
     * has to change its mind at every one of them or the sequence reads as one long take of
     * somebody standing still:
     *
     * <ol>
     *   <li><b>Draw</b> — low and behind the cocked fist, creeping in as the wind builds, shake
     *       rising with it;</li>
     *   <li><b>Uppercut</b> — a hard kick up and out, the lens punched wide, so the launch has
     *       somewhere to go;</li>
     *   <li><b>Ascend</b> — pulled right back for the only wide plate in the sequence: the column,
     *       the rising target, and the attacker waiting at the top of it;</li>
     *   <li><b>Slam</b> — snapped in tight and high above them, narrow lens, looking down the
     *       drop;</li>
     *   <li><b>Ground</b> — hitstop on contact, then a violent kick out to the crater and a wide
     *       settle as the letterbox retracts.</li>
     * </ol>
     *
     * <p>Offsets are (dolly along the facing axis, height above the body, distance out to the
     * side). {@code victim} mirrors which side of the line the camera sits on, so an execution is
     * framed the same way from either seat.
     */
    private static CameraSequence thraggCamera(String name, boolean victim, float uppercut,
                                               float ascend, float slam, float ground,
                                               float duration, boolean advanced){
        float sign=victim?-1f:1f;
        float yaw=victim?-90f:90f;
        float power=advanced?1.35f:1f;

        List<CameraKeyframe> frames=new ArrayList<>();
        // Every offset is measured from the VICTIM: the server gives both participants the same
        // focus entity, so the whole shot list frames the person the move is happening to. The
        // camera aims itself at that body (see ComputeCameraAngles), so the yaw and pitch columns
        // here are small deliberate framing offsets on top of a centred subject -- not the angle
        // the camera points, which is what they used to be and why the dollies threw the shot off.
        // Offsets are (dolly along the facing axis, height above the body, distance out to the side).

        // Arrive low and off the shoulder as the draw starts.
        frames.add(new CameraKeyframe(0.00f,new Vec3(1.10,0.90,4.2),0f,-2.0f,sign*-2.0f,6f,.045f,.30f,EASE_OUT));
        // Close in as the wind takes them.
        frames.add(new CameraKeyframe(0.50f,new Vec3(0.80,0.62,2.9),0f,-1.0f,sign*2.0f,0f,.020f,.95f,EASE_OUT));
        frames.add(new CameraKeyframe(uppercut*.62f,new Vec3(0.60,0.55,2.4),0f,-1.0f,sign*3.2f,-4f,.055f*power,1.00f,EASE_IN_OUT));
        // Load: tight and low, right before it fires.
        frames.add(new CameraKeyframe(uppercut-.10f,new Vec3(0.35,0.40,1.9),0f,0f,sign*4.4f,-13f*power,.190f*power,1.00f,EASE_IN));
        // Contact -- hitstop.
        frames.add(new CameraKeyframe(uppercut,new Vec3(0.35,0.40,1.9),0f,0f,sign*4.6f,-16f*power,0f,1.00f,LINEAR));
        frames.add(new CameraKeyframe(uppercut+.10f,new Vec3(0.33,0.44,1.95),0f,0f,sign*4.6f,-16f*power,0f,1.00f,LINEAR));

        // --- the flight ------------------------------------------------------------------------
        // Locked onto the victim for the entire climb. The camera pulls back a little as they go
        // so the ground falls away underneath them -- with the shot centred on the body, that
        // receding floor is the only thing that reads the speed. Three widely spaced, nearly
        // identical keys mean it drifts rather than cuts.
        frames.add(new CameraKeyframe(uppercut+.35f,new Vec3(0.15,0.85,3.6),0f,2.0f,sign*-1.5f,14f*power,.300f*power,1.00f,EASE_OUT));
        float flight=Math.max(ascend+.20f,slam-.85f);
        frames.add(new CameraKeyframe(ascend,new Vec3(0.00,1.10,5.0),0f,1.0f,sign*0.6f,7f,.055f,1.00f,EASE_OUT));
        frames.add(new CameraKeyframe(ascend+(flight-ascend)*.50f,new Vec3(-0.10,1.35,6.2),0f,0f,sign*1.2f,3f,.035f,1.00f,EASE_IN_OUT));
        // Settle back a touch as the attacker drops into frame above them.
        frames.add(new CameraKeyframe(flight,new Vec3(-0.20,-0.60,6.6),0f,-3.0f,sign*1.6f,1f,.030f,1.00f,EASE_IN_OUT));

        // The hammer.
        frames.add(new CameraKeyframe(slam-.24f,new Vec3(-0.30,-0.30,3.4),0f,-2.0f,sign*3.6f,-9f*power,.150f*power,1.00f,EASE_IN));
        frames.add(new CameraKeyframe(slam,new Vec3(-0.30,-0.30,3.4),0f,-2.0f,sign*3.8f,-16f*power,0f,1.00f,LINEAR));
        frames.add(new CameraKeyframe(slam+.10f,new Vec3(-0.28,-0.34,3.5),0f,-2.0f,sign*3.8f,-16f*power,0f,1.00f,LINEAR));

        // --- the drop --------------------------------------------------------------------------
        // Still on the victim, all the way down. The framing holds while the body it is locked to
        // accelerates into the floor, so the ground arriving is what ends the beat.
        frames.add(new CameraKeyframe(slam+.35f,new Vec3(-0.10,0.60,4.6),0f,0f,sign*1.6f,8f,.110f*power,1.00f,EASE_OUT));
        frames.add(new CameraKeyframe(Math.max(slam+.60f,ground-.30f),new Vec3(0.00,1.00,5.0),0f,0f,sign*0.6f,2f,.080f*power,1.00f,EASE_IN_OUT));
        // Arrival.
        frames.add(new CameraKeyframe(ground,new Vec3(0.10,1.30,5.4),0f,1.0f,sign*-0.6f,-8f*power,.560f*power,1.00f,EASE_OUT));
        // Blow out to the crater. High enough to look down into it rather than stand inside it.
        frames.add(new CameraKeyframe(ground+.55f,new Vec3(-0.40,5.20,11.5),0f,3.0f,sign*-3.0f,18f*power,.200f,1.00f,EASE_OUT));
        frames.add(new CameraKeyframe(ground+1.10f,new Vec3(-0.50,6.00,13.0),0f,2.0f,sign*-2.0f,8f,.070f,1.00f,EASE_IN_OUT));
        // Release.
        frames.add(new CameraKeyframe(duration,new Vec3(-0.45,5.20,12.0),0f,1.0f,0f,3f,.025f,.08f,EASE_IN_OUT));
        return new CameraSequence(name,frames);
    }

    private static CameraKeyframe k(double time,double back,double up,double side,double pitch,double fov,double shake,double letter){return new CameraKeyframe((float)time,new Vec3(back,up,side),0f,(float)pitch,0f,(float)fov,(float)shake,(float)letter,EASE_IN_OUT);}
}
