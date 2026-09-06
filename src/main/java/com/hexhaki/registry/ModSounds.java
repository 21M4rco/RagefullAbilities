package com.hexhaki.registry;

import com.hexhaki.HexHaki;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HexHaki.MODID);
    public static final RegistryObject<SoundEvent> ARMAMENT_ON=register("armament_on");
    public static final RegistryObject<SoundEvent> ARMAMENT_OFF=register("armament_off");
    public static final RegistryObject<SoundEvent> ARMAMENT_CRACKLE=register("armament_crackle");
    public static final RegistryObject<SoundEvent> ARMAMENT_AMBIENT_ZAP=register("armament_ambient_zap");
    public static final RegistryObject<SoundEvent> HAKI_PUNCH=register("haki_punch");
    public static final RegistryObject<SoundEvent> OBSERVATION_ON=register("observation_on");
    public static final RegistryObject<SoundEvent> OBSERVATION_OFF=register("observation_off");
    public static final RegistryObject<SoundEvent> OBSERVATION_DANGER=register("observation_danger");
    public static final RegistryObject<SoundEvent> RYO_CHARGE=register("ryo_charge");
    public static final RegistryObject<SoundEvent> RYO_RELEASE=register("ryo_release");
    public static final RegistryObject<SoundEvent> INTERNAL_HIT=register("internal_hit");
    public static final RegistryObject<SoundEvent> CONQUEROR_CHARGE=register("conqueror_charge");
    public static final RegistryObject<SoundEvent> CONQUEROR_RELEASE=register("conqueror_release");
    public static final RegistryObject<SoundEvent> CONQUEROR_SUPREME_BLAST=register("conqueror_supreme_blast");
    public static final RegistryObject<SoundEvent> CONQUEROR_SUPREME_RUMBLE=register("conqueror_supreme_rumble");
    public static final RegistryObject<SoundEvent> CONQUEROR_SUPREME_SNAP=register("conqueror_supreme_snap");
    public static final RegistryObject<SoundEvent> LAST_STAND_LIGHTNING=register("last_stand_lightning");
    public static final RegistryObject<SoundEvent> MODE_CYCLE=register("mode_cycle");
    public static final RegistryObject<SoundEvent> ACOC_ON=register("acoc_on");
    public static final RegistryObject<SoundEvent> ACOC_OFF=register("acoc_off");
    public static final RegistryObject<SoundEvent> DOMINION_ON=register("dominion_on");
    public static final RegistryObject<SoundEvent> DOMINION_OFF=register("dominion_off");
    public static final RegistryObject<SoundEvent> SOVEREIGN_STRIKE=register("sovereign_strike");
    public static final RegistryObject<SoundEvent> WIFI_HAKI_START=register("wifi_haki_start");
    public static final RegistryObject<SoundEvent> WIFI_HAKI_PULSE=register("wifi_haki_pulse");
    public static final RegistryObject<SoundEvent> WIFI_HAKI_END=register("wifi_haki_end");
    public static final RegistryObject<SoundEvent> CONVERGENCE_CHARGE=register("convergence_charge");
    public static final RegistryObject<SoundEvent> CONVERGENCE_RELEASE=register("convergence_release");
    public static final RegistryObject<SoundEvent> CONVERGENCE_HIT=register("convergence_hit");
    public static final RegistryObject<SoundEvent> GALAXY_RAGE_CHARGE=register("galaxy_rage_charge");
    public static final RegistryObject<SoundEvent> GALAXY_RAGE_ABSORB=register("galaxy_rage_absorb");
    public static final RegistryObject<SoundEvent> GALAXY_RAGE_PUNCH=register("galaxy_rage_punch");
    public static final RegistryObject<SoundEvent> GALAXY_WAVE_PUNCH=register("galaxy_wave_punch");
    public static final RegistryObject<SoundEvent> GALAXY_RAGE_IMPACT=register("galaxy_rage_impact");
    public static final RegistryObject<SoundEvent> GALAXY_RAGE_CRACK=register("galaxy_rage_crack");
    private ModSounds(){}
    private static RegistryObject<SoundEvent> register(String name){ResourceLocation id=HexHaki.id(name);return SOUNDS.register(name,()->SoundEvent.createVariableRangeEvent(id));}
}
