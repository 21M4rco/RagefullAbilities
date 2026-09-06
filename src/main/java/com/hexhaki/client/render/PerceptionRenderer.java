package com.hexhaki.client.render;

import com.hexhaki.HexHaki;
import com.hexhaki.client.HakiClientState;
import com.hexhaki.network.msg.S2CPerception;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-private Observation renderer. 1.1 deliberately separates three ideas:
 * - exact-ish visible presences;
 * - intentionally vague through-terrain presences authored by the server;
 * - probable futures/attack paths that are explicitly predictions, not guaranteed outcomes.
 */
@Mod.EventBusSubscriber(modid=HexHaki.MODID,value=Dist.CLIENT)
public final class PerceptionRenderer {
    private record Mark(byte cls,float threat,Vec3 sensedPosition,Vec3 prediction,float confidence,boolean hidden,long until){}
    private record Path(Vec3 start,Vec3 velocity,int horizon,float danger,long until){}
    private record Threat(int entityId,byte kind,float urgency,int ticksToImpact,Vec3 position,long until){}

    private static final Map<Integer,Mark> MARKS=new ConcurrentHashMap<>();
    private static final Map<Integer,Path> PATHS=new ConcurrentHashMap<>();
    private static final Map<Integer,Threat> THREATS=new ConcurrentHashMap<>();
    private static long futureSightUntilTick;

    private PerceptionRenderer(){}

    public static void update(S2CPerception p){
        // Danger sense rides the same snapshot; no extra packet.
        SpiderSenseScreen.accept(p.threats());
        Minecraft mc=Minecraft.getInstance();
        long now=mc.level==null?0:mc.level.getGameTime();
        for(S2CPerception.Entry e:p.entries()) {
            MARKS.put(e.entityId(),new Mark(e.classification(),e.threat(),
                    new Vec3(e.x(),e.y(),e.z()),
                    new Vec3(e.predictX(),e.predictY(),e.predictZ()),
                    e.confidence(),e.hidden(),now+p.lifeTicks()));
        }
        for(S2CPerception.ProjectileForecast f:p.projectiles()) {
            PATHS.put(f.entityId(),new Path(
                    new Vec3(f.startX(),f.startY(),f.startZ()),
                    new Vec3(f.velocityX(),f.velocityY(),f.velocityZ()),
                    f.horizonTicks(),f.danger(),now+p.lifeTicks()));
        }
        for(S2CPerception.ThreatCue t:p.threats()) {
            THREATS.put((t.kind()<<24) ^ t.entityId(),new Threat(t.entityId(),t.kind(),t.urgency(),t.ticksToImpact(),
                    new Vec3(t.x(),t.y(),t.z()),now+Math.max(p.lifeTicks(),6)));
        }
        if(p.futureSightActive()) futureSightUntilTick=Math.max(futureSightUntilTick,now+Math.max(1,p.futureSightTicks()));
        else futureSightUntilTick=0L;
    }

    /** Immediate damage-event fallback. Predictive cues normally arrive earlier through S2CPerception. */
    /** Fallback for modded attacks that bypass the intent forecast. It forwards straight into the
     *  warning system rather than keeping its own timer and drawing its own marker. */
    public static void danger(int entityId){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        Entity src=mc.level.getEntity(entityId);
        if(src!=null) SpiderSenseScreen.warn(src.getX(),src.getY(),src.getZ(),1f,2);
    }

    @SubscribeEvent public static void world(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||!HakiClientState.observationOn)return;
        Minecraft mc=Minecraft.getInstance(); if(mc.level==null)return;
        long now=mc.level.getGameTime();
        boolean futureSight=now<futureSightUntilTick;
        Camera cam=e.getCamera(); Vec3 cp=cam.getPosition();

        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        for(var it=MARKS.entrySet().iterator();it.hasNext();){
            var ent=it.next();
            Mark mark=ent.getValue();
            if(mark.until<now){it.remove();continue;}

            Entity target=mc.level.getEntity(ent.getKey());
            LivingEntity living=target instanceof LivingEntity l&&l.isAlive()?l:null;
            float height=living!=null?living.getBbHeight():1.8f;
            float width=living!=null?living.getBbWidth():.65f;

            Vec3 absolute;
            if(!mark.hidden&&living!=null) absolute=living.getPosition((float)e.getPartialTick());
            else absolute=mark.sensedPosition;
            Vec3 p=absolute.subtract(cp);

            if(mark.hidden){
                // Never draw a body-perfect wall silhouette. Hidden presences are deliberately soft and imprecise.
                drawPresenceCloud(e.getPoseStack(),p,height,width,mark.cls,mark.threat,.18f+(futureSight?.08f:0f));
            }else{
                drawSpirit(e.getPoseStack(),p,height,width*1.30f,mark.cls,mark.threat,.14f);
                drawSpirit(e.getPoseStack(),p,height,width,mark.cls,mark.threat,.50f);
            }

            // Intent Reading starts at 240. Hidden targets only expose a future echo during advanced
            // Observation, preventing low/mid mastery from becoming perfect through-wall tracking.
            if(HakiClientState.observation>=240 && mark.prediction.lengthSqr()>.015
                    && (!mark.hidden || HakiClientState.observation>=700 || futureSight)){
                Vec3 future=p.add(mark.prediction);
                int trail=futureSight?6:HakiClientState.observation>=850?5:HakiClientState.observation>=550?4:3;
                for(int i=1;i<=trail;i++){
                    double t=i/(double)trail;
                    float a=(float)(.045+.15*t*mark.confidence+(futureSight?.045:0));
                    if(mark.hidden) drawPresenceCloud(e.getPoseStack(),p.add(mark.prediction.scale(t)),height,width*.82f,mark.cls,mark.threat,a*.72f);
                    else drawSpirit(e.getPoseStack(),p.add(mark.prediction.scale(t)),height,width*.90f,mark.cls,mark.threat,a);
                }
                if(mark.hidden) drawPresenceCloud(e.getPoseStack(),future,height,width,mark.cls,mark.threat,.20f+.18f*mark.confidence);
                else drawSpirit(e.getPoseStack(),future,height,width*1.06f,mark.cls,mark.threat,.28f+.24f*mark.confidence);
                drawPredictionThread(e.getPoseStack(),p.add(0,height*.48,0),future.add(0,height*.48,0),mark.cls,mark.threat,
                        (.26f+(futureSight?.16f:0f))*mark.confidence,.018f);

                // 700+ shows uncertainty branches; they remain faint so they read as possibilities.
                if(HakiClientState.observation>=700){
                    Vec3 horizontal=new Vec3(mark.prediction.x,0,mark.prediction.z);
                    if(horizontal.lengthSqr()>.02){
                        Vec3 side=new Vec3(-horizontal.z,0,horizontal.x).normalize();
                        double uncertainty=.22+(1.0-mark.confidence)*.72;
                        float branchAlpha=futureSight?.16f:.10f;
                        if(mark.hidden){
                            drawPresenceCloud(e.getPoseStack(),future.add(side.scale(uncertainty)),height,width*.84f,mark.cls,mark.threat,branchAlpha);
                            drawPresenceCloud(e.getPoseStack(),future.subtract(side.scale(uncertainty)),height,width*.84f,mark.cls,mark.threat,branchAlpha);
                        }else{
                            drawSpirit(e.getPoseStack(),future.add(side.scale(uncertainty)),height,width*.86f,mark.cls,mark.threat,branchAlpha);
                            drawSpirit(e.getPoseStack(),future.subtract(side.scale(uncertainty)),height,width*.86f,mark.cls,mark.threat,branchAlpha);
                        }
                    }
                }
            }
        }

        // Predictive Evasion (400+) visualizes approaching projectile trajectories. Paths expire with
        // the snapshot and are redrawn from fresh server data rather than extrapolating forever.
        if(HakiClientState.observation>=400){
            for(var it=PATHS.entrySet().iterator();it.hasNext();){
                var ent=it.next(); Path path=ent.getValue();
                if(path.until<now){it.remove();continue;}
                Vec3 start=path.start.subtract(cp);
                int segments=futureSight?8:6;
                Vec3 previous=start;
                for(int i=1;i<=segments;i++){
                    double ticks=path.horizon*(i/(double)segments);
                    Vec3 next=start.add(path.velocity.scale(ticks));
                    float fade=(float)(1.0-i/(double)(segments+2));
                    float alpha=(.18f+.38f*path.danger)*fade+(futureSight?.05f:0f);
                    drawPredictionThread(e.getPoseStack(),previous,next,(byte)(path.danger>.58f?2:0),path.danger,alpha,
                            path.danger>.58f?.032f:.020f);
                    previous=next;
                }
            }
        }

        // World-space warning pulse at the source of a predicted attack. The HUD marker below handles
        // off-screen direction; this makes an on-screen attacker visibly "flash" before impact.
        for(var it=THREATS.entrySet().iterator();it.hasNext();){
            var ent=it.next(); Threat threat=ent.getValue();
            if(threat.until<now){it.remove();continue;}
            Vec3 p=threat.position.subtract(cp);
            float size=.32f+.55f*threat.urgency;
            drawThreatCross(e.getPoseStack(),p.add(0,.9,0),size,threat.kind,threat.urgency,futureSight);
        }

        RenderSystem.depthMask(true); RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
    }

    private static void drawPredictionThread(PoseStack ps,Vec3 a,Vec3 b,byte cls,float threat,float alpha,float thickness){
        Vec3 d=b.subtract(a); if(d.lengthSqr()<.001)return;
        Vec3 side=new Vec3(-d.z,0,d.x); if(side.lengthSqr()<.001)side=new Vec3(thickness,0,0); else side=side.normalize().scale(thickness);
        float[] c=color(cls,threat);
        ps.pushPose(); Matrix4f m=ps.last().pose();
        BufferBuilder buf=Tesselator.getInstance().getBuilder();buf.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        vertex(buf,m,a.add(side),c,alpha);vertex(buf,m,b.add(side),c,alpha);vertex(buf,m,b.subtract(side),c,alpha);vertex(buf,m,a.subtract(side),c,alpha);
        BufferUploader.drawWithShader(buf.end());ps.popPose();
    }

    private static void drawThreatCross(PoseStack ps,Vec3 p,float size,byte kind,float urgency,boolean futureSight){
        float[] c=kind==1?new float[]{.42f,.80f,1f}:new float[]{1f,.16f,.18f};
        float a=Math.min(.88f,.28f+.45f*urgency+(futureSight?.10f:0f));
        ps.pushPose(); ps.translate(p.x,p.y,p.z); Matrix4f m=ps.last().pose();
        BufferBuilder b=Tesselator.getInstance().getBuilder(); b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        float t=Math.max(.025f,size*.10f);
        plane(b,m,-size,-t,0,size,-t,0,size,t,0,-size,t,0,c,a);
        plane(b,m,-t,-size,0,t,-size,0,t,size,0,-t,size,0,c,a);
        plane(b,m,0,-t,-size,0,-t,size,0,t,size,0,t,-size,c,a*.72f);
        BufferUploader.drawWithShader(b.end()); ps.popPose();
    }

    private static void drawPresenceCloud(PoseStack ps,Vec3 p,float h,float w,byte cls,float threat,float alpha){
        float[] c=color(cls,threat);
        float half=Math.max(.30f,w*.78f), mid=Math.max(.22f,w*.55f);
        ps.pushPose(); ps.translate(p.x,p.y,p.z); Matrix4f m=ps.last().pose();
        BufferBuilder b=Tesselator.getInstance().getBuilder(); b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        plane(b,m,-half,h*.18f,0,half,h*.18f,0,mid,h*.82f,0,-mid,h*.82f,0,c,alpha*.62f);
        plane(b,m,0,h*.18f,-half,0,h*.18f,half,0,h*.82f,mid,0,h*.82f,-mid,c,alpha*.62f);
        float crown=Math.max(.18f,w*.34f);
        plane(b,m,-crown,h*.70f,0,crown,h*.70f,0,crown,h*.70f+crown*2,0,-crown,h*.70f+crown*2,0,c,alpha);
        BufferUploader.drawWithShader(b.end()); ps.popPose();
    }

    private static void drawSpirit(PoseStack ps,Vec3 p,float h,float w,byte cls,float threat,float alpha){
        float[] c=color(cls,threat); float half=Math.max(.16f,w*.42f); float head=Math.max(.14f,w*.27f);
        ps.pushPose(); ps.translate(p.x,p.y,p.z); Matrix4f m=ps.last().pose();
        Tesselator t=Tesselator.getInstance(); BufferBuilder b=t.getBuilder(); b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        plane(b,m,-half,.10f,0, half,.10f,0, half*.62f,h*.80f,0,-half*.62f,h*.80f,0,c,alpha);
        plane(b,m,0,.10f,-half,0,.10f,half,0,h*.80f,half*.62f,0,h*.80f,-half*.62f,c,alpha);
        plane(b,m,-half*.72f,.10f,-half*.72f, half*.72f,.10f,half*.72f, half*.44f,h*.80f,half*.44f,-half*.44f,h*.80f,-half*.44f,c,alpha*.86f);
        plane(b,m,-head,h*.80f,0,head,h*.80f,0,head,h*.80f+head*2,0,-head,h*.80f+head*2,0,c,Math.min(1f,alpha*1.12f));
        BufferUploader.drawWithShader(b.end()); ps.popPose();
    }

    private static void plane(BufferBuilder b,Matrix4f m,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float x4,float y4,float z4,float[] c,float a){
        b.vertex(m,x1,y1,z1).color(c[0],c[1],c[2],a).endVertex(); b.vertex(m,x2,y2,z2).color(c[0],c[1],c[2],a).endVertex();
        b.vertex(m,x3,y3,z3).color(c[0],c[1],c[2],a).endVertex(); b.vertex(m,x4,y4,z4).color(c[0],c[1],c[2],a).endVertex();
    }
    private static void vertex(BufferBuilder b,Matrix4f m,Vec3 v,float[] c,float a){b.vertex(m,(float)v.x,(float)v.y,(float)v.z).color(c[0],c[1],c[2],a).endVertex();}
    private static float[] color(byte cls,float t){return switch(cls){case 1->new float[]{1.0f,.34f,.30f};case 2->new float[]{1.0f,.10f,.12f};case 3->new float[]{.88f,.30f,1.0f};default->new float[]{.35f,.82f,1.0f};};}

    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Post e){
        if(!HakiClientState.observationOn)return;
        Minecraft mc=Minecraft.getInstance(); if(mc.player==null||mc.level==null)return;
        GuiGraphics g=e.getGuiGraphics(); int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        long now=mc.level.getGameTime();
        boolean futureSight=now<futureSightUntilTick;

        // The old bars were w/18 tall at alpha 0xE6 and sat there for as long as Observation was
        // active -- a permanent letterbox costing a tenth of the screen. Observation is a state you
        // hold for minutes, so its ambient treatment has to be quiet: a thin edge tint, and the
        // full band only during the short Future Sight burst, where it means something.
        int bar=futureSight?Math.max(10,w/34):Math.max(3,w/110);
        int tint=futureSight?0xB4060A12:0x4A060A12;
        g.fill(0,0,w,bar,tint); g.fill(0,h-bar,w,h,tint);

        if(futureSight){
            String label="FUTURE SIGHT";
            int x=(w-mc.font.width(label))/2;
            g.drawString(mc.font,label,x,bar+4,0xE6B7E7FF,false);
            // Faint cool rim so the burst is legible without another opaque band.
            int rim=0x3C6FC8FF;
            g.fill(0,bar,2,h-bar,rim); g.fill(w-2,bar,w,h-bar,rim);
        }

        // Directional warning is owned entirely by SpiderSenseScreen now: it reads the same
        // threat cues and draws them at the screen edge with urgency pulsing and an
        // imminent-impact flash. The old near-crosshair dots are gone rather than merely unhooked.
        // THREATS is still pruned by the world-space pass, which marks the attacker in the world
        // and is a different piece of feedback from a screen indicator.
    }

}
