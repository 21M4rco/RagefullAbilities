package com.hexhaki.client;

import com.hexhaki.client.cinematic.CinematicController;
import com.hexhaki.HexHaki;
import com.hexhaki.client.screen.HakiMasteryScreen;
import com.hexhaki.data.HakiUnlocks;
import com.hexhaki.network.HakiAction;
import com.hexhaki.network.HakiNetwork;
import com.hexhaki.network.msg.C2SHakiAction;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class HakiKeys {
    public static final String CAT="key.categories.hexhaki";
    public static final KeyMapping ARMAMENT=new KeyMapping("key.hexhaki.armament", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,CAT);
    public static final KeyMapping OBSERVATION=new KeyMapping("key.hexhaki.observation",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,CAT);
    public static final KeyMapping CONQUEROR=new KeyMapping("key.hexhaki.conqueror",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_G,CAT);
    public static final KeyMapping MODE=new KeyMapping("key.hexhaki.mode",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_H,CAT);
    public static final KeyMapping ACOC=new KeyMapping("key.hexhaki.acoc",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_J,CAT);
    public static final KeyMapping DOMINION=new KeyMapping("key.hexhaki.dominion",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,CAT);
    public static final KeyMapping STRIKE=new KeyMapping("key.hexhaki.strike",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_L,CAT);
    public static final KeyMapping CONVERGENCE=new KeyMapping("key.hexhaki.convergence",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_M,CAT);
    public static final KeyMapping MASTERY=new KeyMapping("key.hexhaki.mastery",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_P,CAT);
    private static boolean conDown, dominionDown, strikeDown, convergenceDown, attackDown, leapDown;

    private static void send(HakiAction a){ if(Minecraft.getInstance().getConnection()!=null) HakiNetwork.CHANNEL.sendToServer(new C2SHakiAction(a)); }

    @Mod.EventBusSubscriber(modid=HexHaki.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){
            e.register(ARMAMENT);e.register(OBSERVATION);e.register(CONQUEROR);
            e.register(ACOC);e.register(DOMINION);e.register(STRIKE);e.register(CONVERGENCE);e.register(MASTERY);
        }
    }

    @Mod.EventBusSubscriber(modid=HexHaki.MODID,value=Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
            Minecraft mc=Minecraft.getInstance();
            if(e.phase!=TickEvent.Phase.END)return;
            if(mc.player==null){ leapDown=false; strikeDown=false; return; }
            while(MASTERY.consumeClick()) mc.setScreen(new HakiMasteryScreen());
            if(!HakiClientState.enabled){
                if(strikeDown) send(HakiAction.SOVEREIGN_STRIKE_RELEASE);
                strikeDown=false;
                return;
            }
            while(ARMAMENT.consumeClick()) send(HakiAction.ARMAMENT_TOGGLE);
            while(OBSERVATION.consumeClick()) send(HakiAction.OBSERVATION_TOGGLE);
            while(ACOC.consumeClick()) send(HakiAction.ACOC_TOGGLE);
            boolean s=STRIKE.isDown();
            if(s&&!strikeDown)send(HakiAction.SOVEREIGN_STRIKE);
            if(!s&&strikeDown)send(HakiAction.SOVEREIGN_STRIKE_RELEASE);
            strikeDown=s;
            boolean c=CONQUEROR.isDown(); if(c&&!conDown)send(HakiAction.CONQUEROR_START); if(!c&&conDown)send(HakiAction.CONQUEROR_RELEASE); conDown=c;
            boolean d=DOMINION.isDown(); if(d&&!dominionDown)send(HakiAction.DOMINION_START); if(!d&&dominionDown)send(HakiAction.DOMINION_RELEASE); dominionDown=d;
            boolean m=CONVERGENCE.isDown(); if(m&&!convergenceDown)send(HakiAction.CONVERGENCE_START); if(!m&&convergenceDown)send(HakiAction.CONVERGENCE_RELEASE); convergenceDown=m;
            boolean attack=mc.options.keyAttack.isDown();
            if(attack&&!attackDown){
                if(HakiClientState.convergenceCharge>=100) {
                    send(HakiAction.GALAXY_PUNCH);
                } else if(HakiClientState.armamentOn && mc.player.getMainHandItem().getItem() instanceof SwordItem) {
                    // V2 blade cuts are sent even when the swing hits only air. The server remains
                    // authoritative for cooldown, energy, range, block clipping and damage.
                    send(HakiAction.HAKI_BLADE_SLASH);
                }
            }
            attackDown=attack;
        }

        /**
         * Haki Leap is an intentional movement technique: Shift + Space starts charging immediately.
         * Ordinary Space is never intercepted. While the combo is held, vanilla jump/sneak posture is
         * suppressed so Player Animator only adds the two bent-arm tracks from haki_leap_charge.
         */
        @SubscribeEvent
        public static void movementInput(MovementInputUpdateEvent e){
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null || e.getEntity()!=mc.player)return;

            // King's Grip owns the local body for the duration of the cinematic. Suppressing
            // movement input here prevents client prediction from fighting the server pin and
            // producing visible third-person camera correction jitter for either participant.
            if(CinematicController.isKingsGripActive()){
                e.getInput().leftImpulse=0f;
                e.getInput().forwardImpulse=0f;
                e.getInput().jumping=false;
                e.getInput().shiftKeyDown=false;
                return;
            }

            // Read the physical bindings directly. We intentionally clear the Input booleans below
            // while charging, so using e.getInput() as the source of truth would lose the held combo.
            boolean jumpHeld=mc.options.keyJump.isDown();
            boolean shiftHeld=mc.options.keyShift.isDown();
            boolean comboHeld=jumpHeld && shiftHeld;
            boolean eligible=HakiClientState.enabled && HakiClientState.armamentOn && HakiClientState.armament>=HakiUnlocks.HAKI_LEAP_ARMAMENT
                    && !mc.player.getAbilities().flying && !mc.player.isFallFlying();

            if(leapDown){
                if(comboHeld && eligible){
                    // Haki Leap owns Shift+Space while charging. No vanilla jump or crouch animation;
                    // WASD, sprint and camera remain completely normal.
                    e.getInput().jumping=false;
                    e.getInput().shiftKeyDown=false;
                }else{
                    send(HakiAction.HAKI_LEAP_RELEASE);
                    leapDown=false;
                }
                return;
            }

            // Space by itself is 100% vanilla. Only the deliberate Shift+Space combo can arm Haki Leap.
            if(!comboHeld || !eligible || !mc.player.onGround()) return;

            leapDown=true;
            e.getInput().jumping=false;
            e.getInput().shiftKeyDown=false;
            send(HakiAction.HAKI_LEAP_START);
        }
    }
}
