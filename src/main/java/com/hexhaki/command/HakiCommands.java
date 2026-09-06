package com.hexhaki.command;

import com.hexhaki.HexHaki;
import com.hexhaki.data.HakiData;
import com.hexhaki.data.HakiType;
import com.hexhaki.gameplay.HakiServerController;
import com.hexhaki.gameplay.HakiProgression;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=HexHaki.MODID)
public final class HakiCommands {
    private HakiCommands(){}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(root("hh"));
        event.getDispatcher().register(root("haki"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name){
        return Commands.literal(name).requires(s->s.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("player",EntityArgument.player())
                                .then(Commands.argument("enabled",BoolArgumentType.bool())
                                        .executes(c->setEnabled(c.getSource(),EntityArgument.getPlayer(c,"player"),BoolArgumentType.getBool(c,"enabled"))))))
                .then(Commands.literal("status")
                        .then(Commands.argument("player",EntityArgument.player())
                                .executes(c->status(c.getSource(),EntityArgument.getPlayer(c,"player")))))
                .then(Commands.literal("mastery")
                        .then(Commands.argument("player",EntityArgument.player())
                                .then(masteryType("armament",HakiType.ARMAMENT))
                                .then(masteryType("observation",HakiType.OBSERVATION))
                                .then(masteryType("conqueror",HakiType.CONQUEROR))
                                .then(Commands.literal("all")
                                        .then(Commands.literal("set").then(Commands.argument("value",IntegerArgumentType.integer(0,1000)).executes(c->setAll(c.getSource(),EntityArgument.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"value")))))
                                        .then(Commands.literal("add").then(Commands.argument("value",IntegerArgumentType.integer(-1000,1000)).executes(c->addAll(c.getSource(),EntityArgument.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"value"))))))
                                .then(Commands.literal("max").executes(c->setAll(c.getSource(),EntityArgument.getPlayer(c,"player"),1000)))
                                .then(Commands.literal("reset").executes(c->setAll(c.getSource(),EntityArgument.getPlayer(c,"player"),0)))))
                .then(Commands.literal("energy")
                        .then(Commands.literal("unlimited")
                                .then(Commands.literal("on").executes(c->setUnlimitedEnergy(c.getSource(),c.getSource().getPlayerOrException(),true)))
                                .then(Commands.literal("off").executes(c->setUnlimitedEnergy(c.getSource(),c.getSource().getPlayerOrException(),false)))
                                .then(Commands.argument("player",EntityArgument.player())
                                        .then(Commands.literal("on").executes(c->setUnlimitedEnergy(c.getSource(),EntityArgument.getPlayer(c,"player"),true)))
                                        .then(Commands.literal("off").executes(c->setUnlimitedEnergy(c.getSource(),EntityArgument.getPlayer(c,"player"),false)))))
                        .then(Commands.argument("player",EntityArgument.player())
                                .then(Commands.argument("value",IntegerArgumentType.integer(0,1500))
                                        .executes(c->setEnergy(c.getSource(),EntityArgument.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"value"))))
                                .then(Commands.literal("unlimited")
                                        .then(Commands.literal("on").executes(c->setUnlimitedEnergy(c.getSource(),EntityArgument.getPlayer(c,"player"),true)))
                                        .then(Commands.literal("off").executes(c->setUnlimitedEnergy(c.getSource(),EntityArgument.getPlayer(c,"player"),false))))))
                .then(Commands.literal("legend")
                        .then(Commands.argument("player",EntityArgument.player())
                                .then(Commands.argument("value",IntegerArgumentType.integer(0,9999))
                                        .executes(c->setLegend(c.getSource(),EntityArgument.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"value"))))))
                .then(Commands.literal("joyboy")
                        .then(Commands.argument("player",EntityArgument.player())
                                .then(Commands.argument("unlocked",BoolArgumentType.bool())
                                        .executes(c->setJoyBoy(c.getSource(),EntityArgument.getPlayer(c,"player"),BoolArgumentType.getBool(c,"unlocked"))))))
                .then(Commands.literal("board")
                        .then(Commands.literal("on").executes(c->setBoard(c.getSource(),c.getSource().getPlayerOrException(),true)))
                        .then(Commands.literal("off").executes(c->setBoard(c.getSource(),c.getSource().getPlayerOrException(),false))))
                .then(Commands.literal("cooldowns")
                        .then(Commands.literal("off").executes(c->setCooldowns(c.getSource(),c.getSource().getPlayerOrException(),false)))
                        .then(Commands.literal("on").executes(c->setCooldowns(c.getSource(),c.getSource().getPlayerOrException(),true)))
                        .then(Commands.argument("player",EntityArgument.player())
                                .then(Commands.literal("off").executes(c->setCooldowns(c.getSource(),EntityArgument.getPlayer(c,"player"),false)))
                                .then(Commands.literal("on").executes(c->setCooldowns(c.getSource(),EntityArgument.getPlayer(c,"player"),true)))))
                .then(Commands.literal("vfx")
                        .then(Commands.argument("player",EntityArgument.player())
                                .then(Commands.literal("all").executes(c->vfx(c.getSource(),EntityArgument.getPlayer(c,"player"),"all")))
                                .then(Commands.literal("conqueror").executes(c->vfx(c.getSource(),EntityArgument.getPlayer(c,"player"),"conqueror")))
                                .then(Commands.literal("armament").executes(c->vfx(c.getSource(),EntityArgument.getPlayer(c,"player"),"armament")))
                                .then(Commands.literal("shockwave").executes(c->vfx(c.getSource(),EntityArgument.getPlayer(c,"player"),"shockwave")))
                                .then(Commands.literal("aura").executes(c->vfx(c.getSource(),EntityArgument.getPlayer(c,"player"),"aura")))
                                .then(Commands.literal("convergence").executes(c->vfx(c.getSource(),EntityArgument.getPlayer(c,"player"),"convergence")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> masteryType(String name,HakiType type){
        return Commands.literal(name)
                .then(Commands.literal("set").then(Commands.argument("value",IntegerArgumentType.integer(0,1000))
                        .executes(c->setMastery(c.getSource(),EntityArgument.getPlayer(c,"player"),type,IntegerArgumentType.getInteger(c,"value")))))
                .then(Commands.literal("add").then(Commands.argument("value",IntegerArgumentType.integer(-1000,1000))
                        .executes(c->addMastery(c.getSource(),EntityArgument.getPlayer(c,"player"),type,IntegerArgumentType.getInteger(c,"value")))));
    }

    private static int setEnabled(CommandSourceStack s,ServerPlayer p,boolean value){
        HakiData.enabled(p,value); if(!value)HakiServerController.cancelAll(p); HakiServerController.sync(p);
        s.sendSuccess(()->Component.literal("HexHaki for "+p.getGameProfile().getName()+": "+value),true); return value?1:0;
    }
    private static int setMastery(CommandSourceStack s,ServerPlayer p,HakiType type,int value){HakiData.setMastery(p,type,value);int actual=HakiData.mastery(p,type);HakiProgression.tryUnlockJoyBoy(p);HakiServerController.sync(p);s.sendSuccess(()->Component.literal(type.display+" mastery for "+p.getGameProfile().getName()+" = "+actual+"/1000"),true);return actual;}
    private static int addMastery(CommandSourceStack s,ServerPlayer p,HakiType type,int value){return setMastery(s,p,type,HakiData.mastery(p,type)+value);}
    private static int setAll(CommandSourceStack s,ServerPlayer p,int value){for(HakiType t:HakiType.values())HakiData.setMastery(p,t,value);if(value==0){HakiData.legend(p,0);HakiData.joyBoy(p,false);}else HakiProgression.tryUnlockJoyBoy(p);HakiServerController.sync(p);s.sendSuccess(()->Component.literal("All Haki mastery for "+p.getGameProfile().getName()+" = "+value+"/1000"),true);return value;}
    private static int addAll(CommandSourceStack s,ServerPlayer p,int value){for(HakiType t:HakiType.values())HakiData.addMastery(p,t,value);HakiProgression.tryUnlockJoyBoy(p);HakiServerController.sync(p);s.sendSuccess(()->Component.literal("Added "+value+" to all Haki mastery for "+p.getGameProfile().getName()),true);return value;}
    private static int setEnergy(CommandSourceStack s,ServerPlayer p,int value){HakiData.energy(p,value);HakiServerController.sync(p);s.sendSuccess(()->Component.literal("Haki energy for "+p.getGameProfile().getName()+" = "+Math.round(HakiData.energy(p))+"/"+Math.round(HakiData.maxEnergy(p))),true);return value;}
    private static int setUnlimitedEnergy(CommandSourceStack s,ServerPlayer p,boolean enabled){
        HakiData.unlimitedEnergy(p,enabled);
        HakiServerController.sync(p);
        s.sendSuccess(()->Component.literal("Unlimited Haki energy for "+p.getGameProfile().getName()+": "+(enabled?"ON (TEST MODE)":"OFF")),true);
        return enabled?1:0;
    }
    private static int setLegend(CommandSourceStack s,ServerPlayer p,int value){HakiData.legend(p,value);HakiProgression.tryUnlockJoyBoy(p);HakiServerController.sync(p);s.sendSuccess(()->Component.literal("Legend for "+p.getGameProfile().getName()+" = "+value),true);return value;}
    private static int setJoyBoy(CommandSourceStack s,ServerPlayer p,boolean value){HakiData.joyBoy(p,value);HakiServerController.sync(p);s.sendSuccess(()->Component.literal("Joy Boy Haki for "+p.getGameProfile().getName()+": "+value),true);return value?1:0;}
    private static int setBoard(CommandSourceStack s,ServerPlayer p,boolean enabled){
        HakiData.boardEnabled(p, enabled);
        HakiServerController.sync(p);
        s.sendSuccess(()->Component.literal("HexHaki board: "+(enabled?"ON":"OFF")),false);
        return enabled?1:0;
    }
    private static int setCooldowns(CommandSourceStack s,ServerPlayer p,boolean enabled){
        HakiServerController.setNoCooldowns(p,!enabled);
        s.sendSuccess(()->Component.literal("HexHaki ability cooldowns for "+p.getGameProfile().getName()+": "+(enabled?"ON":"OFF (TEST MODE)")),true);
        return enabled?1:0;
    }
    private static int status(CommandSourceStack s,ServerPlayer p){s.sendSuccess(()->Component.literal(p.getGameProfile().getName()+" | enabled="+HakiData.enabled(p)+" ARM="+HakiData.mastery(p,HakiType.ARMAMENT)+" ("+HakiData.xp(p,HakiType.ARMAMENT)+" XP) OBS="+HakiData.mastery(p,HakiType.OBSERVATION)+" ("+HakiData.xp(p,HakiType.OBSERVATION)+" XP) HAO="+HakiData.mastery(p,HakiType.CONQUEROR)+" ("+HakiData.xp(p,HakiType.CONQUEROR)+" XP) legend="+HakiData.legend(p)+" joyBoy="+HakiData.joyBoy(p)+" unlimitedEnergy="+HakiData.unlimitedEnergy(p)+" cooldowns="+(HakiServerController.noCooldowns(p)?"OFF(TEST)":"ON")),false);return 1;}
    private static int vfx(CommandSourceStack s,ServerPlayer p,String test){HakiServerController.debugVfx(p,test);s.sendSuccess(()->Component.literal("Triggered Haki VFX diagnostic: "+test+" on "+p.getGameProfile().getName()),false);return 1;}
}
