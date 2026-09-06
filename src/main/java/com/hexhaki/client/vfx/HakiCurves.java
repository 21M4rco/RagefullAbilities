package com.hexhaki.client.vfx;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.gui.editor.configurator.NumberFunctionConfigurator;
import net.minecraft.nbt.CompoundTag;

import java.util.function.Supplier;

/**
 * Programmatic {@link NumberFunction} ramps for Photon's over-lifetime channels.
 *
 * <p>Photon ships {@code Curve} and {@code Gradient}, but both are authored for the in-game
 * editor: their shape lives in cubic-bezier control points that have to be hand-packed as raw
 * float octets.  These two implementations take plain keyframe pairs instead, which keeps the
 * VFX code readable and lets every effect describe its own envelope inline.
 *
 * <p>Both channels are <b>multiplicative</b> in Photon:
 * <ul>
 *   <li>{@code sizeOverLifetime} multiplies the particle's start size, so 1 -> 0 shrinks it;</li>
 *   <li>{@code colorOverLifetime} multiplies the start colour componentwise, so an ARGB ramp of
 *       {@code 0xFFFFFFFF -> 0x00FFFFFF} fades alpha out while preserving the tint.</li>
 * </ul>
 * That is the whole reason the reworked effects stop reading as hard-edged blobs that pop out of
 * existence: every emitter now dies on a curve instead of at a hard lifetime cutoff.
 *
 * <p>These are never serialised (HexHaki builds every FX programmatically and Photon only
 * serialises editor-authored assets), so the NBT hooks are intentionally inert.
 */
public final class HakiCurves {

    private HakiCurves() {}

    // ----------------------------------------------------------------------------------
    // scalar ramp
    // ----------------------------------------------------------------------------------

    /** Smoothstepped piecewise ramp over normalised particle age. */
    public static final class Ramp implements NumberFunction {
        /** Flat {t0,v0, t1,v1, ...} keyframes, ascending in t. */
        private final float[] keys;
        private final boolean smooth;

        public Ramp(boolean smooth, float... keys) {
            if (keys.length < 4 || (keys.length & 1) == 1) {
                throw new IllegalArgumentException("Ramp needs at least two {time,value} pairs");
            }
            this.keys = keys;
            this.smooth = smooth;
        }

        public float value(float t) {
            if (t <= keys[0]) return keys[1];
            int last = keys.length - 2;
            if (t >= keys[last]) return keys[last + 1];
            for (int i = 2; i <= last; i += 2) {
                if (t <= keys[i]) {
                    float t0 = keys[i - 2], v0 = keys[i - 1];
                    float t1 = keys[i], v1 = keys[i + 1];
                    float span = t1 - t0;
                    float k = span <= 1.0E-6f ? 1f : (t - t0) / span;
                    if (smooth) k = k * k * (3f - 2f * k);
                    return v0 + (v1 - v0) * k;
                }
            }
            return keys[last + 1];
        }

        @Override public Number get(float t, Supplier<Float> seed) { return value(t); }
        @Override public NumberFunction copy() { return new Ramp(smooth, keys.clone()); }
        @Override public void createConfigurator(WidgetGroup group, NumberFunctionConfigurator configurator) {}
        @Override public CompoundTag serializeNBT() { return new CompoundTag(); }
        @Override public void deserializeNBT(CompoundTag tag) {}
    }

    // ----------------------------------------------------------------------------------
    // colour ramp
    // ----------------------------------------------------------------------------------

    /** ARGB keyframe ramp; channels interpolate independently and multiply the start colour. */
    public static final class ColorRamp implements NumberFunction {
        private final float[] times;
        private final int[] colors;

        public ColorRamp(float[] times, int[] colors) {
            if (times.length < 2 || times.length != colors.length) {
                throw new IllegalArgumentException("ColorRamp needs matching time/colour arrays of 2+ stops");
            }
            this.times = times;
            this.colors = colors;
        }

        public int value(float t) {
            if (t <= times[0]) return colors[0];
            int last = times.length - 1;
            if (t >= times[last]) return colors[last];
            for (int i = 1; i <= last; i++) {
                if (t <= times[i]) {
                    float span = times[i] - times[i - 1];
                    float k = span <= 1.0E-6f ? 1f : (t - times[i - 1]) / span;
                    return blend(colors[i - 1], colors[i], k);
                }
            }
            return colors[last];
        }

        private static int blend(int a, int b, float k) {
            int aa = (a >>> 24) & 255, ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
            int ba = (b >>> 24) & 255, br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
            int oa = (int) (aa + (ba - aa) * k);
            int or = (int) (ar + (br - ar) * k);
            int og = (int) (ag + (bg - ag) * k);
            int ob = (int) (ab + (bb - ab) * k);
            return (oa << 24) | (or << 16) | (og << 8) | ob;
        }

        @Override public Number get(float t, Supplier<Float> seed) { return value(t); }
        @Override public NumberFunction copy() { return new ColorRamp(times.clone(), colors.clone()); }
        @Override public void createConfigurator(WidgetGroup group, NumberFunctionConfigurator configurator) {}
        @Override public CompoundTag serializeNBT() { return new CompoundTag(); }
        @Override public void deserializeNBT(CompoundTag tag) {}
    }

    // ----------------------------------------------------------------------------------
    // size envelopes (multiply start size)
    // ----------------------------------------------------------------------------------

    /** Snaps to full size, then shrinks away. General-purpose sparks/debris. */
    public static NumberFunction3 popShrink() {
        return uniform(new Ramp(true, 0f, .35f, .12f, 1f, 1f, .06f));
    }

    /** Keeps size, then collapses only at the very end. Good for bolts and hot cores. */
    public static NumberFunction3 holdCollapse() {
        return uniform(new Ramp(true, 0f, 1f, .70f, .96f, 1f, .04f));
    }

    /** Smoke swell: born small, expands continuously as it cools. */
    public static NumberFunction3 swell(float peak) {
        return uniform(new Ramp(true, 0f, .28f, .30f, peak * .78f, 1f, peak));
    }

    /** Shockwave: expands hard and thins out. */
    public static NumberFunction3 expand(float peak) {
        return uniform(new Ramp(false, 0f, .18f, .25f, peak * .62f, 1f, peak));
    }

    /** Accretion: starts wide and is drawn down to nothing (Galaxy Impact inflow). */
    public static NumberFunction3 collapseIn() {
        return uniform(new Ramp(true, 0f, 1f, .55f, .72f, 1f, .02f));
    }

    public static NumberFunction3 uniform(NumberFunction function) {
        return new NumberFunction3(function, function, function);
    }

    // ----------------------------------------------------------------------------------
    // colour envelopes (multiply start colour)
    // ----------------------------------------------------------------------------------

    /** Plain alpha fade with a short opaque hold. */
    public static NumberFunction fadeOut() {
        return new ColorRamp(
                new float[] {0f, .10f, .55f, 1f},
                new int[] {0x00FFFFFF, 0xFFFFFFFF, 0xD8FFFFFF, 0x00FFFFFF});
    }

    /** Fast flash in, long tail out. Used by hot cores and impact flashes. */
    public static NumberFunction flashOut() {
        return new ColorRamp(
                new float[] {0f, .06f, .30f, 1f},
                new int[] {0xB4FFFFFF, 0xFFFFFFFF, 0x9AFFFFFF, 0x00FFFFFF});
    }

    /** Smoke: fades in slowly, dims as it cools, never snaps off. */
    public static NumberFunction smokeOut() {
        return new ColorRamp(
                new float[] {0f, .18f, .62f, 1f},
                new int[] {0x00FFFFFF, 0xE2D6D2CE, 0x9E9A9694, 0x006E6C6B});
    }

    /** White-hot -> orange -> deep red -> gone. Embers and Haki fire. */
    public static NumberFunction emberOut() {
        return new ColorRamp(
                new float[] {0f, .14f, .46f, .78f, 1f},
                new int[] {0xC0FFFFFF, 0xFFFFFFFF, 0xF2FFB25E, 0xB4FF4A22, 0x00902008});
    }

    /** Conqueror/Armament bolt: black-red hold, then a hard cut. Keeps arcs snappy. */
    public static NumberFunction boltOut() {
        return new ColorRamp(
                new float[] {0f, .08f, .58f, 1f},
                new int[] {0xFFFFFFFF, 0xFFFFFFFF, 0xCCFFD8D8, 0x00FF9090});
    }

    /** Cosmic: violet core cooling to deep indigo as the particle dies. */
    public static NumberFunction cosmicOut() {
        return new ColorRamp(
                new float[] {0f, .12f, .52f, 1f},
                new int[] {0x30FFFFFF, 0xFFFFFFFF, 0xD0C0A8FF, 0x004060C0});
    }
}
