package com.hexhaki.client;

import com.hexhaki.network.msg.S2CStateSync;
import com.hexhaki.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class HakiClientState {
    public static int armament, observation, conqueror, conquerorCharge, ryoCharge, convergenceCharge, mode, legend;
    public static long armamentXp, observationXp, conquerorXp;
    public static float energy = 100, maxEnergy = 100;
    public static boolean enabled, joyBoy, boardEnabled = true, armamentOn, observationOn, acocOn, dominionOn;
    private HakiClientState() {}

    public static void accept(S2CStateSync m) {
        boolean wasEnabled = enabled;
        boolean wasArmamentOn = armamentOn;
        boolean wasObservationOn = observationOn;
        boolean wasAcocOn = acocOn;
        boolean wasDominionOn = dominionOn;

        armament=m.armament(); observation=m.observation(); conqueror=m.conqueror();
        armamentXp=m.armamentXp(); observationXp=m.observationXp(); conquerorXp=m.conquerorXp();
        energy=m.energy(); maxEnergy=m.maxEnergy(); enabled=m.enabled(); joyBoy=m.joyBoy(); boardEnabled=m.boardEnabled(); legend=m.legend();
        armamentOn=m.armamentOn(); observationOn=m.observationOn(); acocOn=m.acocOn(); dominionOn=m.dominionOn();
        conquerorCharge=m.conquerorCharge(); ryoCharge=m.ryoCharge(); convergenceCharge=m.convergenceCharge(); mode=m.mode();

        // Long Haki samples (notably armament_crackle) are one-shot sounds, so Minecraft keeps
        // playing them even after the server state is turned off unless the client explicitly
        // stops the active instance. Keep this tied only to real ON -> OFF state transitions.
        if (wasEnabled && !enabled) {
            stopAllHakiSounds();
            return;
        }
        if (wasArmamentOn && !armamentOn) {
            stop(new SoundEvent[]{ModSounds.ARMAMENT_ON.get(), ModSounds.ARMAMENT_CRACKLE.get(), ModSounds.ARMAMENT_AMBIENT_ZAP.get()});
        }
        // Advanced Haki owns the activation/ambience layer the instant it comes on. If the long
        // normal-Haki startup sample is still playing, cut it before the Advanced cue begins.
        if (!wasAcocOn && acocOn) {
            stop(new SoundEvent[]{ModSounds.ARMAMENT_ON.get(), ModSounds.ARMAMENT_CRACKLE.get(), ModSounds.ARMAMENT_AMBIENT_ZAP.get()});
        }
        if (wasObservationOn && !observationOn) {
            stop(new SoundEvent[]{ModSounds.OBSERVATION_ON.get(), ModSounds.OBSERVATION_DANGER.get()});
        }
        if (wasAcocOn && !acocOn) {
            stop(new SoundEvent[]{ModSounds.ACOC_ON.get(), ModSounds.ARMAMENT_AMBIENT_ZAP.get()});
        }
        if (wasDominionOn && !dominionOn) {
            stop(new SoundEvent[]{ModSounds.DOMINION_ON.get()});
        }
    }

    private static void stopAllHakiSounds() {
        stop(new SoundEvent[]{
                ModSounds.ARMAMENT_ON.get(), ModSounds.ARMAMENT_OFF.get(), ModSounds.ARMAMENT_CRACKLE.get(), ModSounds.ARMAMENT_AMBIENT_ZAP.get(),
                ModSounds.OBSERVATION_ON.get(), ModSounds.OBSERVATION_OFF.get(), ModSounds.OBSERVATION_DANGER.get(),
                ModSounds.RYO_CHARGE.get(), ModSounds.RYO_RELEASE.get(), ModSounds.INTERNAL_HIT.get(),
                ModSounds.CONQUEROR_CHARGE.get(), ModSounds.CONQUEROR_RELEASE.get(),
                ModSounds.CONQUEROR_SUPREME_BLAST.get(), ModSounds.CONQUEROR_SUPREME_RUMBLE.get(), ModSounds.CONQUEROR_SUPREME_SNAP.get(),
                ModSounds.LAST_STAND_LIGHTNING.get(), ModSounds.MODE_CYCLE.get(),
                ModSounds.ACOC_ON.get(), ModSounds.ACOC_OFF.get(),
                ModSounds.DOMINION_ON.get(), ModSounds.DOMINION_OFF.get(), ModSounds.SOVEREIGN_STRIKE.get(),
                ModSounds.WIFI_HAKI_START.get(), ModSounds.WIFI_HAKI_PULSE.get(), ModSounds.WIFI_HAKI_END.get(),
                ModSounds.CONVERGENCE_CHARGE.get(), ModSounds.CONVERGENCE_RELEASE.get(), ModSounds.CONVERGENCE_HIT.get(),
                ModSounds.GALAXY_RAGE_CHARGE.get(), ModSounds.GALAXY_RAGE_ABSORB.get(),
                ModSounds.GALAXY_RAGE_PUNCH.get(), ModSounds.GALAXY_WAVE_PUNCH.get(),
                ModSounds.GALAXY_RAGE_IMPACT.get(), ModSounds.GALAXY_RAGE_CRACK.get()});
    }

    private static void stop(SoundEvent[] sounds) {
        var manager = Minecraft.getInstance().getSoundManager();
        for (SoundEvent sound : sounds) {
            manager.stop(sound.getLocation(), SoundSource.PLAYERS);
        }
    }
}
