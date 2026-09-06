package com.hexhaki.client.render;

import com.hexhaki.HexHaki;
import com.hexhaki.client.RemoteHakiStates;
import com.hexhaki.client.vfx.HakiVfx;
import com.hexhaki.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

public final class HakiRenderHooks {
    private static final Map<Integer, Long> NORMAL_ARMAMENT_NEXT_ZAP = new HashMap<>();
    private static final Map<Integer, Long> ADVANCED_HAKI_NEXT_ZAP = new HashMap<>();
    private static final Map<Integer, Boolean> LAST_ADVANCED_STATE = new HashMap<>();
    // New Advanced-Haki startup sample is 19.08s and starts immediately. Give it ~1-2s of clear air
    // before the same mild zap ambience used by normal Armament is allowed to begin.
    private static final long ADVANCED_HAKI_STARTUP_CLEAR_TICKS = 402L;
    @Mod.EventBusSubscriber(modid=HexHaki.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent public static void addLayers(EntityRenderersEvent.AddLayers e){
            for(String skin:e.getSkins()){
                PlayerRenderer r=e.getSkin(skin);
                if(r==null)continue;
                r.addLayer(new HakiChestLayer(r));
                r.addLayer(new HakiHandLayer(r));
                r.addLayer(new HakiCaughtArrowLayer(r));
            }
        }
    }
    @Mod.EventBusSubscriber(modid=HexHaki.MODID,value=Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent public static void arm(RenderArmEvent e){
            RemoteHakiStates.State s=RemoteHakiStates.get(e.getPlayer().getId());
            if(s==null||!s.armament())return;
            EntityRenderer<? super AbstractClientPlayer> er=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e.getPlayer());
            if(!(er instanceof PlayerRenderer pr))return;
            @SuppressWarnings("unchecked") PlayerModel<AbstractClientPlayer> model=(PlayerModel<AbstractClientPlayer>)pr.getModel();

            // RenderArmEvent happens before vanilla's arm. Cancel it so the normal skin/sleeve cannot
            // paint over the Haki coating, then render the coated arm as the actual first-person hand.
            e.setCanceled(true);
            HakiHandLayer.renderFirstPerson(e.getPoseStack(),e.getMultiBufferSource(),e.getPackedLight(),model,e.getPlayer(),
                    e.getArm()==HumanoidArm.RIGHT,"slim".equals(e.getPlayer().getModelName()),
                    Math.min(1,s.armamentMastery()/1000f),s.acoc(),e.getPlayer().tickCount);
        }
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
            if(e.phase!=TickEvent.Phase.END)return;
            Minecraft mc=Minecraft.getInstance();
            if(mc.level==null||mc.player==null){
                NORMAL_ARMAMENT_NEXT_ZAP.clear();
                ADVANCED_HAKI_NEXT_ZAP.clear();
                LAST_ADVANCED_STATE.clear();
                return;
            }
            HakiVfx.tickGalaxyFistEffects();
            long gameTime=mc.level.getGameTime();
            long baseSeed=gameTime*0x9E3779B97F4A7C15L;
            for(AbstractClientPlayer player:mc.level.players()){
                if(player!=mc.player&&player.distanceToSqr(mc.player)>4096.0)continue;
                RemoteHakiStates.State s=RemoteHakiStates.get(player.getId());
                if(s==null||!s.armament()){
                    NORMAL_ARMAMENT_NEXT_ZAP.remove(player.getId());
                    ADVANCED_HAKI_NEXT_ZAP.remove(player.getId());
                    LAST_ADVANCED_STATE.remove(player.getId());
                    continue;
                }

                // A pulse every 0.6s leaves visible darkness between arcs. Each bolt now lives
                // longer inside HakiVfx, so this cadence reads as electricity instead of flicker.
                if((gameTime+player.getId())%12==0){
                    HakiVfx.armamentAmbient(player,Math.min(1f,s.armamentMastery()/1000f),s.acoc(),
                            baseSeed^player.getId()*0x632BE59BD9B4E019L,
                            "slim".equals(player.getModelName()));
                }

                // Environmental lashes are isolated events. Advanced sky strikes remain further
                // gated inside the call, preventing overlapping storms around the player.
                if((gameTime+player.getId()*7L)%40L==0L){
                    HakiVfx.armamentGroundStorm(player,Math.min(1f,s.armamentMastery()/1000f),s.acoc(),
                            baseSeed^player.getId()*0xC2B2AE3D27D4EB4FL);
                }

                // Both normal and Advanced Armament use the same sparse, mild electricity ambience.
                // Each activation cue gets to finish first: normal keeps its existing 4.23s+breathing-room
                // delay; Advanced waits for the new 19.08s startup sample plus ~1-2s of clean air.
                boolean wasAdvanced=LAST_ADVANCED_STATE.getOrDefault(player.getId(),false);
                LAST_ADVANCED_STATE.put(player.getId(),s.acoc());
                if(!s.acoc()){
                    ADVANCED_HAKI_NEXT_ZAP.remove(player.getId());
                    if(wasAdvanced){
                        // Advanced just turned off; normal ambience resumes naturally, not immediately.
                        NORMAL_ARMAMENT_NEXT_ZAP.put(player.getId(),
                                gameTime+110L+player.getRandom().nextInt(61));
                    }
                    long nextZap=NORMAL_ARMAMENT_NEXT_ZAP.computeIfAbsent(player.getId(),
                            ignored->gameTime+130L+player.getRandom().nextInt(21));
                    if(gameTime>=nextZap){
                        playMildArmamentZap(mc,player,s);
                        NORMAL_ARMAMENT_NEXT_ZAP.put(player.getId(),
                                gameTime+110L+player.getRandom().nextInt(61));
                    }
                }else{
                    NORMAL_ARMAMENT_NEXT_ZAP.remove(player.getId());
                    if(!wasAdvanced){
                        ADVANCED_HAKI_NEXT_ZAP.put(player.getId(),
                                gameTime+ADVANCED_HAKI_STARTUP_CLEAR_TICKS+player.getRandom().nextInt(21));
                    }
                    long nextZap=ADVANCED_HAKI_NEXT_ZAP.computeIfAbsent(player.getId(),
                            ignored->gameTime+ADVANCED_HAKI_STARTUP_CLEAR_TICKS+player.getRandom().nextInt(21));
                    if(gameTime>=nextZap){
                        playMildArmamentZap(mc,player,s);
                        ADVANCED_HAKI_NEXT_ZAP.put(player.getId(),
                                gameTime+110L+player.getRandom().nextInt(61));
                    }
                }
            }
        }
    }

    private static void playMildArmamentZap(Minecraft mc, AbstractClientPlayer player, RemoteHakiStates.State s){
        float mastery=Math.min(1f,s.armamentMastery()/1000f);
        float volume=.16f+.05f*mastery;
        float pitch=.94f+player.getRandom().nextFloat()*.12f;
        mc.level.playLocalSound(player.getX(),player.getY()+1.0,player.getZ(),
                ModSounds.ARMAMENT_AMBIENT_ZAP.get(),
                SoundSource.PLAYERS,volume,pitch,false);
    }
}
