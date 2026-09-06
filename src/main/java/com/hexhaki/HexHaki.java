package com.hexhaki;

import com.hexhaki.network.HakiNetwork;
import com.hexhaki.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import software.bernie.geckolib.GeckoLib;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@Mod(HexHaki.MODID)
public final class HexHaki {
    public static final String MODID = "hexhaki";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        ResourceLocation id = ResourceLocation.tryBuild(MODID, path);
        if (id == null) {
            throw new IllegalArgumentException("Invalid HexHaki resource path: " + path);
        }
        return id;
    }

    public HexHaki(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModSounds.SOUNDS.register(modBus);
        GeckoLib.initialize();
        HakiNetwork.init();
    }
}
