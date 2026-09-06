package com.hexhaki.client.render;

import com.hexhaki.HexHaki;
import com.hexhaki.client.RemoteHakiStates;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Haki physique: chest and arms thicken as the user's Haki deepens. Waist, legs and head do not.
 *
 * <p>Three earlier approaches were wrong, each in its own way:
 *
 * <ul>
 *   <li>a skin-UV shading overlay, which painted grey pectoral shading onto the player's own skin
 *       and showed as blotches on any skin it did not happen to suit;</li>
 *   <li>Pehkui model scaling, which grows the <b>whole</b> model, so the head, arms and legs grew
 *       with the chest and the user simply looked further away;</li>
 *   <li>scaling the {@code body} {@link ModelPart}, which is a single 8x12x4 box covering chest
 *       <b>and</b> waist. Widening it widened the belly in step with the chest, so the result read
 *       as fat rather than built.</li>
 * </ul>
 *
 * <p>A built torso is a taper: wide across the chest and lats, normal at the waist. Vanilla has no
 * seam to scale against, so {@link HakiChestLayer} supplies one — an upper-torso shell drawn over
 * the top half of the body at the wider size, leaving the bottom half at its own width. This class
 * owns the half of the effect that <i>can</i> be done with part scaling: the arms.
 *
 * <p>Arms grow slightly less than the chest, since a build that outgrows its own torso reads as a
 * costume, and they are moved outward to sit beside the widened chest rather than in front of it —
 * vanilla arms are flush against a 4-texel torso half-width, so a chest that grows past that is
 * simply hidden behind the shoulders. Height is never touched on either: a taller torso lifts the
 * head off the neck.
 *
 * <p>Everything is restored in {@code Post}: {@code ModelPart}s are shared across every player
 * being rendered, so a leaked scale would inflate everyone drawn after this one.
 */
@Mod.EventBusSubscriber(modid = HexHaki.MODID, value = Dist.CLIENT)
public final class HakiPhysiqueLayer {

    /** Widest the chest gets, as a fraction, at the heaviest stage and full mastery. */
    public static final float MAX_CHEST_GAIN = 0.55f;
    /** Chest depth grows less than width, so the shape is a broad chest and not a barrel. */
    public static final float CHEST_DEPTH_RATIO = 0.65f;
    /** Arms stay a little behind the chest; arms that outgrow the torso read as a costume. */
    private static final float MAX_ARM_GAIN = 0.40f;

    /** Vanilla torso half-width, in model texels. The chest shell grows outward from this. */
    private static final float TORSO_HALF_WIDTH = 4.0f;
    /** Where an arm's inner face sits in its own part space: it touches the torso at rest. */
    private static final float ARM_INNER_FACE = 1.0f;
    /** Deliberate air left between the widened chest and the arm beside it, in texels. */
    private static final float ARM_CLEARANCE = 0.35f;

    /** Arm pivots this frame, so Post can put them back exactly where vanilla had them. */
    private static float restoreRightArmX, restoreLeftArmX;
    private static boolean armsMoved;

    private HakiPhysiqueLayer() {}

    /**
     * How far each arm has to move outward, in model texels.
     *
     * <p>Three terms, because three things push the arm into the chest at once: the chest shell
     * grows outward past the arm's inner face, the arm's own widening grows <i>inward</i> from its
     * pivot, and without clearance the two end up flush and the widened chest is simply hidden
     * behind the shoulder — which is exactly what it looked like.
     */
    private static float armSeparation(float bulk, float armScale) {
        return TORSO_HALF_WIDTH * MAX_CHEST_GAIN * bulk
                + ARM_INNER_FACE * (armScale - 1f)
                + ARM_CLEARANCE * bulk;
    }

    /** 0 when no Haki is active; deepens with the stage and mastery within it. */
    public static float bulk(RemoteHakiStates.State state) {
        if (state == null) return 0f;
        float stage;
        if (state.acoc()) stage = 1.00f;
        else if (state.ryuo()) stage = 0.72f;
        else if (state.armament()) stage = 0.50f;
        else return 0f;
        float mastery = Mth.clamp(state.armamentMastery() / 1000f, 0f, 1f);
        return stage * (0.55f + 0.45f * mastery);
    }

    /** Convenience for the chest shell: 0 when there is nothing to draw. */
    public static float bulk(int entityId) {
        return bulk(RemoteHakiStates.get(entityId));
    }

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre event) {
        // getRenderer() is already a PlayerRenderer on this event -- an instanceof pattern here
        // is a compile error, not a widening cast.
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();

        float bulk = bulk(RemoteHakiStates.get(event.getEntity().getId()));
        float armScale = 1f + MAX_ARM_GAIN * bulk;
        setScale(model.rightArm, armScale);
        setScale(model.leftArm, armScale);
        setScale(model.rightSleeve, armScale);
        setScale(model.leftSleeve, armScale);

        // Remember the real pivots rather than a shift amount: PlayerModel#setupAnim runs after
        // this event, so if anything downstream rewrites x, Post still restores vanilla's value
        // instead of subtracting an offset that is no longer there.
        restoreRightArmX = model.rightArm.x;
        restoreLeftArmX = model.leftArm.x;
        armsMoved = bulk > 0.001f;
        if (!armsMoved) return;

        float separation = armSeparation(bulk, armScale);
        model.rightArm.x = restoreRightArmX - separation;
        model.leftArm.x = restoreLeftArmX + separation;
        // The sleeves copyFrom the arms inside setupAnim, so they follow on their own.
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post event) {
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        setScale(model.rightArm, 1f);
        setScale(model.leftArm, 1f);
        setScale(model.rightSleeve, 1f);
        setScale(model.leftSleeve, 1f);
        if (armsMoved) {
            model.rightArm.x = restoreRightArmX;
            model.leftArm.x = restoreLeftArmX;
            armsMoved = false;
        }
    }

    private static void setScale(ModelPart part, float scale) {
        if (part == null) return;
        part.xScale = scale;
        part.zScale = scale;   // length deliberately untouched: it detaches the hand from the wrist
    }
}
