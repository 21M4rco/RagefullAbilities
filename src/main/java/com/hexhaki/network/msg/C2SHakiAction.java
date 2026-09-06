package com.hexhaki.network.msg;

import com.hexhaki.gameplay.HakiServerController;
import com.hexhaki.network.HakiAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SHakiAction(HakiAction action) {
    public static void encode(C2SHakiAction msg, FriendlyByteBuf buf) { buf.writeEnum(msg.action); }
    public static C2SHakiAction decode(FriendlyByteBuf buf) { return new C2SHakiAction(buf.readEnum(HakiAction.class)); }
    public static void handle(C2SHakiAction msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> { if (ctx.getSender() != null) HakiServerController.onAction(ctx.getSender(), msg.action); });
        ctx.setPacketHandled(true);
    }
}
