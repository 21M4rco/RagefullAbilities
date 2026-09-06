package com.hexhaki.client.vfx;

import com.hexhaki.HexHaki;
import com.mojang.blaze3d.platform.GlStateManager;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Circle;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Reworked Photon primitives for HexHaki.
 *
 * <p>The original effects were built from constant-size, constant-colour emitters drawn with a
 * 64px linear-ramp sprite and a 32x8 flat-alpha beam strip.  That combination is what made the
 * particles read as hard-edged blobs that pop out of existence and bolts that read as bars.
 * Everything here fixes those three things at once:
 *
 * <ul>
 *   <li>the high-resolution sprite set authored by {@code tools/generate_effect_textures.py};</li>
 *   <li>{@link HakiCurves} envelopes on size and colour so particles are born, breathe and die;</li>
 *   <li>additive blending for anything that is supposed to emit light.</li>
 * </ul>
 *
 * <p>The legacy {@code soft/spark/beam/coating} sprites and the emitters in {@code HakiVfx} that
 * use them are untouched on purpose: WiFi Haki and the Conqueror's Release are locked
 * presentations and must keep rendering exactly as they always have.
 */
public final class HakiFx {

    public static final ResourceLocation SMOKE = HexHaki.id("textures/effect/smoke_soft.png");
    public static final ResourceLocation GLOW = HexHaki.id("textures/effect/glow_core.png");
    public static final ResourceLocation ARC = HexHaki.id("textures/effect/arc.png");
    public static final ResourceLocation ARC_SOFT = HexHaki.id("textures/effect/arc_soft.png");
    public static final ResourceLocation STAR = HexHaki.id("textures/effect/star.png");
    /** Smooth tapered streak for pressure beams; the arc sprites are jagged by design. */
    public static final ResourceLocation LANCE = HexHaki.id("textures/effect/lance.png");
    public static final ResourceLocation NEBULA = HexHaki.id("textures/effect/nebula.png");
    public static final ResourceLocation RING = HexHaki.id("textures/effect/shock_ring.png");
    public static final ResourceLocation CRESCENT = HexHaki.id("textures/effect/crescent.png");
    public static final ResourceLocation EMBER = HexHaki.id("textures/effect/ember.png");
    /** Upright tapered flame tongue; authored tall because a radial sprite cannot lick. */
    public static final ResourceLocation FLAME = HexHaki.id("textures/effect/flame.png");
    public static final ResourceLocation DUST = HexHaki.id("textures/effect/dust.png");
    public static final ResourceLocation CRACK = HexHaki.id("textures/effect/crack.png");
    public static final ResourceLocation GALAXY_DISC = HexHaki.id("textures/effect/galaxy_disc.png");
    /** Black/red galaxy for the un-coated and normal-Armament tiers. */
    public static final ResourceLocation GALAXY_DISC_RED = HexHaki.id("textures/effect/galaxy_disc_red.png");
    public static final ResourceLocation GALAXY_CORE = HexHaki.id("textures/effect/galaxy_core.png");

    private HakiFx() {}

    // ----------------------------------------------------------------------------------
    // emitter construction
    // ----------------------------------------------------------------------------------

    /** Base emitter with sane defaults; callers layer envelopes on top. */
    public static ParticleEmitter emitter(ResourceLocation texture, int duration, int life,
                                          float speed, float size, int color, boolean bloom) {
        ParticleEmitter p = new ParticleEmitter();
        p.config.setDuration(duration);
        p.config.setLooping(false);
        p.config.setStartLifetime(NumberFunction.constant(life));
        p.config.setStartSpeed(NumberFunction.constant(speed));
        p.config.setStartSize(new NumberFunction3(size, size, size));
        p.config.setStartColor(NumberFunction.color(color));
        p.config.setMaxParticles(900);
        p.config.material.setMaterial(new TextureMaterial(texture));
        p.config.material.setCull(false);
        p.config.renderer.setBloomEffect(bloom);
        p.config.renderer.setBloomColor(color);
        p.config.renderer.setLayer(RendererSetting.Layer.Translucent);
        return p;
    }

    /** Switches an emitter to additive so overlapping particles build light instead of grey. */
    public static ParticleEmitter additive(ParticleEmitter p) {
        BlendMode blend = p.config.material.getBlendMode();
        blend.setEnableBlend(true);
        blend.setSrcColorFactor(GlStateManager.SourceFactor.SRC_ALPHA);
        blend.setDstColorFactor(GlStateManager.DestFactor.ONE);
        blend.setSrcAlphaFactor(GlStateManager.SourceFactor.ONE);
        blend.setDstAlphaFactor(GlStateManager.DestFactor.ONE);
        p.config.material.setDepthMask(false);
        return p;
    }

    /** Applies a size envelope (multiplies start size across the particle's life). */
    public static ParticleEmitter size(ParticleEmitter p, NumberFunction3 envelope) {
        p.config.sizeOverLifetime.setEnable(true);
        p.config.sizeOverLifetime.setSize(envelope);
        return p;
    }

    /** Applies a colour envelope (multiplies start colour, so alpha ramps fade the particle). */
    public static ParticleEmitter color(ParticleEmitter p, NumberFunction envelope) {
        p.config.colorOverLifetime.setEnable(true);
        p.config.colorOverLifetime.setColor(envelope);
        return p;
    }

    /** Slow roll so large sprites do not betray themselves as repeated identical billboards. */
    public static ParticleEmitter spin(ParticleEmitter p, float degreesPerTick) {
        p.config.rotationOverLifetime.setEnable(true);
        p.config.rotationOverLifetime.setRoll(NumberFunction.constant(degreesPerTick));
        return p;
    }

    public static ParticleEmitter gravity(ParticleEmitter p, float g) {
        p.config.physics.setEnable(true);
        p.config.physics.setGravity(NumberFunction.constant(g));
        return p;
    }

    /** Random start roll; essential whenever many copies of one sprite overlap. */
    public static ParticleEmitter scatterRoll(ParticleEmitter p, Random random) {
        p.config.setStartRotation(new NumberFunction3(0, 0, random.nextInt(360)));
        return p;
    }

    public static ParticleEmitter mode(ParticleEmitter p, RendererSetting.Particle.Mode m) {
        p.config.renderer.setRenderMode(m);
        return p;
    }

    // ----------------------------------------------------------------------------------
    // ready-made bodies
    // ----------------------------------------------------------------------------------

    /** Billowing smoke that swells and cools instead of vanishing at its lifetime cutoff. */
    public static ParticleEmitter smoke(int duration, int life, float speed, float size,
                                        int color, float swell, Random random) {
        ParticleEmitter p = emitter(SMOKE, duration, life, speed, size, color, false);
        p.config.setMaxParticles(1400);
        size(p, HakiCurves.swell(swell));
        color(p, HakiCurves.smokeOut());
        spin(p, (random.nextFloat() - .5f) * 2.2f);
        scatterRoll(p, random);
        return p;
    }

    /** Additive hot core; flashes to full then decays on a long tail. */
    public static ParticleEmitter flash(int duration, int life, float speed, float size, int color) {
        ParticleEmitter p = additive(emitter(GLOW, duration, life, speed, size, color, true));
        size(p, HakiCurves.holdCollapse());
        color(p, HakiCurves.flashOut());
        return p;
    }

    /** Additive embers that cool white -> orange -> red and fall slightly. */
    public static ParticleEmitter embers(int duration, int life, float speed, float size,
                                         int color, Random random) {
        ParticleEmitter p = additive(emitter(EMBER, duration, life, speed, size, color, true));
        size(p, HakiCurves.popShrink());
        color(p, HakiCurves.emberOut());
        gravity(p, .012f);
        scatterRoll(p, random);
        return p;
    }

    /** Star field motes for the Galaxy presentation. */
    public static ParticleEmitter stars(int duration, int life, float speed, float size, int color) {
        ParticleEmitter p = additive(emitter(STAR, duration, life, speed, size, color, true));
        size(p, HakiCurves.popShrink());
        color(p, HakiCurves.cosmicOut());
        return p;
    }

    /** One flat expanding pressure ring, drawn horizontally on a single billboard. */
    public static ParticleEmitter groundRing(int duration, int life, float startSize,
                                             float grow, int color) {
        ParticleEmitter p = additive(emitter(RING, duration, life, 0f, startSize, color, true));
        mode(p, RendererSetting.Particle.Mode.Horizontal);
        size(p, HakiCurves.expand(grow));
        color(p, HakiCurves.fadeOut());
        p.config.setMaxParticles(24);
        return p;
    }

    /** Camera-facing pressure ring for airborne shockwaves. */
    public static ParticleEmitter airRing(int duration, int life, float startSize,
                                          float grow, int color) {
        ParticleEmitter p = additive(emitter(RING, duration, life, 0f, startSize, color, true));
        size(p, HakiCurves.expand(grow));
        color(p, HakiCurves.fadeOut());
        p.config.setMaxParticles(24);
        return p;
    }

    /** Ground debris that actually falls back down. */
    public static ParticleEmitter debris(int duration, int life, float speed, float size,
                                         int color, Random random) {
        ParticleEmitter p = emitter(DUST, duration, life, speed, size, color, false);
        p.config.setMaxParticles(1200);
        size(p, HakiCurves.swell(1.55f));
        color(p, HakiCurves.smokeOut());
        gravity(p, .045f);
        spin(p, (random.nextFloat() - .5f) * 3.4f);
        scatterRoll(p, random);
        return p;
    }

    // ----------------------------------------------------------------------------------
    // emission shaping
    // ----------------------------------------------------------------------------------

    public static void burst(ParticleEmitter p, int count, int delay) {
        p.config.emission.setEmissionRate(NumberFunction.constant(0));
        EmissionSetting.Burst b = new EmissionSetting.Burst();
        b.time = delay;
        b.cycles = 1;
        b.interval = 1;
        b.setCount(NumberFunction.constant(count));
        p.config.emission.getBursts().add(b);
    }

    public static void burstCycles(ParticleEmitter p, int count, int delay, int cycles, int interval) {
        p.config.emission.setEmissionRate(NumberFunction.constant(0));
        EmissionSetting.Burst b = new EmissionSetting.Burst();
        b.time = delay;
        b.cycles = Math.max(1, cycles);
        b.interval = Math.max(1, interval);
        b.setCount(NumberFunction.constant(count));
        p.config.emission.getBursts().add(b);
    }

    public static ParticleEmitter at(ParticleEmitter p, Vec3 position) {
        p.config.shape.setPosition(new NumberFunction3(position.x, position.y, position.z));
        return p;
    }

    public static ParticleEmitter sphere(ParticleEmitter p, float radius, float thickness) {
        Sphere s = new Sphere();
        s.setRadius(radius);
        s.setRadiusThickness(thickness);
        p.config.shape.setShape(s);
        return p;
    }

    public static ParticleEmitter circle(ParticleEmitter p, float radius, float thickness) {
        Circle c = new Circle();
        c.setRadius(radius);
        c.setRadiusThickness(thickness);
        p.config.shape.setShape(c);
        return p;
    }

    // ----------------------------------------------------------------------------------
    // beams
    // ----------------------------------------------------------------------------------

    /** Single textured beam segment. The arc sprite carries the jaggedness and the end taper. */
    public static BeamEmitter beam(Vector3f a, Vector3f b, float width, int color,
                                   int duration, int delay, boolean bloom, ResourceLocation texture) {
        BeamEmitter e = new BeamEmitter();
        e.setPos(a.x, a.y, a.z);
        e.setDelay(delay);
        e.getConfig().setDuration(duration);
        e.getConfig().setLooping(false);
        e.getConfig().setWidth(NumberFunction.constant(width));
        e.getConfig().setColor(NumberFunction.color(color));
        e.getConfig().getEnd().set(b.x - a.x, b.y - a.y, b.z - a.z);
        e.getConfig().material.setMaterial(new TextureMaterial(texture));
        e.getConfig().material.setCull(false);
        e.getConfig().renderer.setBloomEffect(bloom);
        e.getConfig().renderer.setBloomColor(color);
        return e;
    }

    /**
     * A three-layer Haki bolt: dark outer shell, saturated core, white-hot filament.
     *
     * <p>Layering three widths of the tapered arc sprite is what gives the bolt volume; a single
     * flat quad can only ever look like a coloured stick.
     */
    public static void bolt(FX fx, Vec3 from, Vec3 to, float scale, int shellColor,
                            int coreColor, int hotColor, int duration, int delay) {
        Vector3f a = vec(from);
        Vector3f b = vec(to);
        add(fx, beam(a, b, .150f * scale, shellColor, duration + 2, delay, false, ARC_SOFT));
        add(fx, beam(a, b, .052f * scale, coreColor, duration + 1, delay, true, ARC));
        add(fx, beam(a, b, .017f * scale, hotColor, duration, delay, true, ARC));
    }

    /** Armament black/red bolt. */
    public static void armamentBolt(FX fx, Vec3 from, Vec3 to, float scale, int duration, int delay) {
        bolt(fx, from, to, scale, 0xEC050307, 0xFFFF1835, 0xFFFF6778, duration, delay);
    }

    /** Advanced Haki bolt: hotter, whiter filament. */
    public static void advancedBolt(FX fx, Vec3 from, Vec3 to, float scale, int duration, int delay) {
        bolt(fx, from, to, scale, 0xF3050207, 0xFFFF102D, 0xFFFFD2C0, duration, delay);
    }

    /**
     * Walks a jagged multi-segment bolt between two points.
     *
     * @param chaos lateral wander as a fraction of the span
     * @param forks probability per interior joint of throwing a branch
     */
    public static void boltPath(FX fx, Vec3 from, Vec3 to, int segments, double chaos,
                                float scale, boolean advanced, int duration, int baseDelay,
                                float forks, Random random) {
        boltPath(fx, from, to, segments, chaos, scale, advanced, duration, baseDelay, forks, random, 2);
    }

    /**
     * Walks a jagged multi-segment bolt between two points.
     *
     * <p>Shaped after how lightning actually behaves rather than as a random zig-zag:
     *
     * <ul>
     *   <li>the channel wanders most in the middle and converges on both endpoints, because both
     *       ends are pinned;</li>
     *   <li>branches leave at a <b>shallow forward angle</b> and keep most of the parent's
     *       direction. Perpendicular spurs are the single biggest thing that makes drawn lightning
     *       read as scribble;</li>
     *   <li>each branch generation is shorter, thinner and shorter-lived than its parent, and
     *       recurses, so the bolt has a bright main channel with a decaying tree hanging off it
     *       instead of one uniform width everywhere.</li>
     * </ul>
     *
     * @param depth remaining branch generations; 0 draws a bare channel
     */
    public static void boltPath(FX fx, Vec3 from, Vec3 to, int segments, double chaos,
                                float scale, boolean advanced, int duration, int baseDelay,
                                float forks, Random random, int depth) {
        Vec3 span = to.subtract(from);
        double length = span.length();
        if (length < 1.0E-4 || segments < 1) return;
        Vec3 dir = span.scale(1.0 / length);
        Vec3 reference = Math.abs(dir.y) > .88 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(reference).normalize();
        Vec3 up = right.cross(dir).normalize();

        Vec3 previous = from;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            // pinned at both ends, wildest in the middle
            double envelope = Math.sin(Math.PI * t);
            double lateral = (random.nextDouble() * 2 - 1) * chaos * length * envelope;
            double vertical = (random.nextDouble() * 2 - 1) * chaos * length * envelope * .78;
            Vec3 next = i == segments ? to
                    : from.add(span.scale(t)).add(right.scale(lateral)).add(up.scale(vertical));
            int delay = baseDelay + Math.min(6, (i - 1) / 2);
            if (advanced) advancedBolt(fx, previous, next, scale, duration, delay);
            else armamentBolt(fx, previous, next, scale, duration, delay);

            if (depth > 0 && i > 1 && i < segments && random.nextFloat() < forks) {
                // Shallow forward split: keep most of the parent's heading and lean off it.
                Vec3 heading = next.subtract(previous);
                if (heading.lengthSqr() > 1.0E-8) {
                    heading = heading.normalize();
                    double lean = .32 + random.nextDouble() * .38;
                    Vec3 off = right.scale((random.nextDouble() - .5) * 2)
                            .add(up.scale((random.nextDouble() - .5) * 2));
                    if (off.lengthSqr() < 1.0E-8) off = right;
                    Vec3 branchDir = heading.add(off.normalize().scale(lean)).normalize();
                    // Each generation is markedly shorter than the last.
                    double branchLength = length * (.22 + random.nextDouble() * .20) / (depth + 1);
                    Vec3 branchEnd = next.add(branchDir.scale(branchLength));
                    boltPath(fx, next, branchEnd, Math.max(2, segments / 2), chaos * 1.25,
                            scale * .52f, advanced, Math.max(3, duration - 3), delay + 1,
                            forks * .45f, random, depth - 1);
                }
            }
            previous = next;
        }
    }

    // ----------------------------------------------------------------------------------
    // misc
    // ----------------------------------------------------------------------------------

    public static Vector3f vec(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    public static void add(FX fx, com.lowdragmc.photon.client.gameobject.IFXObject object) {
        fx.getMainFX().objects().add(object);
    }
}
