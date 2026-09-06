package com.hexhaki.mixin;

import com.hexhaki.client.cinematic.CinematicController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void move(double distanceOffset,double verticalOffset,double horizontalOffset);
    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method="setup",at=@At("TAIL"))
    private void hexhaki$cinematicOffset(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci){
        // King's Grip is placed absolutely. Going through move() left the shot riding on top of
        // vanilla's terrain-clipped third-person zoom and anchored to whichever body happened to
        // be the local camera entity, which is what made the framing drift and differ between the
        // two participants. Every other sequence keeps the original relative offset exactly.
        Vec3 absolute=CinematicController.kingsGripCameraPosition(entity,partialTick);
        if(absolute!=null){ setPosition(absolute); return; }
        Vec3 o=CinematicController.cameraOffset(); if(o.lengthSqr()>1.0E-8) move(o.x,o.y,o.z);
    }
}
