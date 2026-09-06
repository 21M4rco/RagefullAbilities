package com.hexhaki.mixin;

import com.hexhaki.client.cinematic.CinematicController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * King's Grip owns the camera for both participating players.
 *
 * CinematicController already pins the rendered camera and the player's authored yaw/pitch, but
 * vanilla MouseHandler can still apply raw mouse deltas between client ticks. That creates a tiny
 * fight between mouse-look, entity interpolation, the third-person camera offset and the next
 * cinematic correction -- which reads as camera shake even when the camera keyframes themselves
 * contain zero shake.
 *
 * Redirecting only LocalPlayer.turn keeps MouseHandler's normal delta consumption/smoothing path
 * alive (so no movement piles up and snaps the camera when the cinematic ends) while preventing
 * those deltas from rotating either King's Grip participant. Both attacker and player victim run
 * the same client-side cinematic, so both get the same hard view lock.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Redirect(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void hexhaki$freezeKingsGripMouseLook(LocalPlayer player, double yawDelta, double pitchDelta) {
        if (!CinematicController.isKingsGripActive()) {
            player.turn(yawDelta, pitchDelta);
        }
    }
}
