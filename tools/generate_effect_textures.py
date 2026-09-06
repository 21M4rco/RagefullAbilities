#!/usr/bin/env python3
"""
HexHaki procedural VFX sprite authoring.

Photon billboards a sprite per particle and stretches a sprite along each beam, so the
sprite's own alpha profile *is* the look of the effect.  The legacy set could not carry the
presentation this mod wants:

    soft.png   64x64  linear radial ramp   -> large clouds read as flat discs with a visible rim
    spark.png  32x32  blurred dot          -> no glint, mushy at size
    beam.png   32x8   uniform alpha 211    -> every bolt is a hard-edged rectangle

Those four legacy files are deliberately NOT regenerated: WiFi Haki and the Conqueror's
Release are locked presentations and must keep rendering byte-identically.  Everything here
is authored as an *additional* sprite set for the reworked effects.

Pure stdlib (zlib + struct) so it runs anywhere; output is deterministic for a fixed seed.

    python3 tools/generate_effect_textures.py
"""

import math
import os
import random
import struct
import zlib

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "src", "main", "resources", "assets", "hexhaki", "textures", "effect")

# --------------------------------------------------------------------------------------
# PNG output
# --------------------------------------------------------------------------------------

def write_png(path, width, height, pixels):
    """pixels: flat list of (r,g,b,a) ints, row-major."""
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter: none
        row = pixels[y * width:(y + 1) * width]
        for (r, g, b, a) in row:
            raw += bytes((r & 255, g & 255, b & 255, a & 255))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as fh:
        fh.write(png)
    print("  %-26s %dx%d  %6d bytes" % (os.path.basename(path), width, height, len(png)))


# --------------------------------------------------------------------------------------
# maths helpers
# --------------------------------------------------------------------------------------

def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else (hi if v > hi else v)


def smoothstep(edge0, edge1, x):
    if edge0 == edge1:
        return 0.0 if x < edge0 else 1.0
    t = clamp((x - edge0) / (edge1 - edge0))
    return t * t * (3.0 - 2.0 * t)


def lerp(a, b, t):
    return a + (b - a) * t


class Noise:
    """Small tileable value-noise + fbm. Deterministic per seed."""

    def __init__(self, seed, period=64):
        self.period = period
        rng = random.Random(seed)
        self.grid = [[rng.random() for _ in range(period)] for _ in range(period)]

    def value(self, x, y):
        p = self.period
        x0, y0 = int(math.floor(x)) % p, int(math.floor(y)) % p
        x1, y1 = (x0 + 1) % p, (y0 + 1) % p
        fx, fy = x - math.floor(x), y - math.floor(y)
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = lerp(self.grid[y0][x0], self.grid[y0][x1], sx)
        b = lerp(self.grid[y1][x0], self.grid[y1][x1], sx)
        return lerp(a, b, sy)

    def fbm(self, x, y, octaves=4, lacunarity=2.0, gain=0.5):
        total, amp, norm, freq = 0.0, 1.0, 0.0, 1.0
        for _ in range(octaves):
            total += self.value(x * freq, y * freq) * amp
            norm += amp
            amp *= gain
            freq *= lacunarity
        return total / norm

    def ridged(self, x, y, octaves=4):
        return 1.0 - abs(self.fbm(x, y, octaves) * 2.0 - 1.0)


def px(r, g, b, a):
    return (int(clamp(r) * 255 + .5), int(clamp(g) * 255 + .5),
            int(clamp(b) * 255 + .5), int(clamp(a) * 255 + .5))


# --------------------------------------------------------------------------------------
# sprites
# --------------------------------------------------------------------------------------

def smoke_soft(size=256, seed=1337):
    """Billowing smoke body. Gaussian core, fbm break-up, fully faded rim.

    The rim fade is the point: a linear ramp that still has alpha at the UV border is what
    makes big Photon clouds show their square/circular sprite boundary."""
    n = Noise(seed, 48)
    out = []
    for y in range(size):
        for x in range(size):
            u = (x + .5) / size * 2 - 1
            v = (y + .5) / size * 2 - 1
            r = math.hypot(u, v)
            body = math.exp(-(r / .46) ** 2.15)
            billow = .58 + .42 * n.fbm(x / size * 5.0, y / size * 5.0, 5)
            puff = .82 + .34 * n.ridged(x / size * 9.0 + 11.0, y / size * 9.0 + 7.0, 3)
            a = body * billow * puff
            a *= smoothstep(1.0, .58, r)          # hard guarantee of a dead rim
            shade = .80 + .20 * n.fbm(x / size * 3.0 + 31.0, y / size * 3.0 + 17.0, 3)
            out.append(px(shade, shade, shade, clamp(a)))
    return size, size, out


def glow_core(size=256):
    """Hot nucleus: needle-sharp core plus a wide inverse-square halo."""
    out = []
    for y in range(size):
        for x in range(size):
            u = (x + .5) / size * 2 - 1
            v = (y + .5) / size * 2 - 1
            r = math.hypot(u, v)
            core = math.exp(-(r / .085) ** 2)
            inner = math.exp(-(r / .21) ** 1.7) * .70
            halo = .055 / (r * r + .055) * .55
            a = clamp(core + inner + halo)
            a *= smoothstep(1.0, .60, r)
            # the very centre stays pure white, the halo cools slightly
            warm = clamp(core + inner)
            out.append(px(1.0, lerp(.90, 1.0, warm), lerp(.86, 1.0, warm), a))
    return size, size, out


def _bolt_path(width, seed, amplitude, octaves=4):
    """1D jagged centreline in [-1,1] used by the arc sprites."""
    n = Noise(seed, 32)
    path = []
    for x in range(width):
        t = x / (width - 1.0)
        j = n.fbm(t * 7.0, 3.7, octaves) * 2.0 - 1.0
        j += (n.fbm(t * 19.0, 9.1, 2) * 2.0 - 1.0) * .45
        # tips converge so consecutive segments join without a visible kink
        j *= math.sin(math.pi * clamp(t)) ** .55
        path.append(j * amplitude)
    return path


def arc(width=256, height=64, seed=99, amplitude=.34, thickness=.115, glow=False):
    """Lightning strip for BeamEmitter.

    Replaces the flat 211-alpha bar with a jagged, longitudinally tapered bolt that has a
    white-hot filament and a soft falloff, so beam segments read as electricity rather than
    as rectangles."""
    path = _bolt_path(width, seed, amplitude)
    n = Noise(seed + 5, 32)
    out = []
    for y in range(height):
        for x in range(width):
            t = x / (width - 1.0)
            v = (y + .5) / height * 2 - 1
            dy = v - path[x]

            # thickness pulses along the bolt and tapers to nothing at both ends
            pulse = .68 + .52 * n.fbm(t * 11.0, 1.3, 3)
            taper = math.sin(math.pi * clamp(t)) ** (.32 if glow else .45)
            th = thickness * pulse * max(.12, taper)

            d = abs(dy) / max(1e-4, th)
            if glow:
                a = math.exp(-(d ** 1.5)) * .52
                a += math.exp(-((abs(dy) / (th * 3.4)) ** 2)) * .30
            else:
                filament = math.exp(-(d ** 2.6))            # white-hot centre
                bloom = math.exp(-((abs(dy) / (th * 2.6)) ** 2)) * .42
                a = clamp(filament + bloom)

            # short branch spurs give the sprite internal structure at large scales
            spur = n.ridged(t * 23.0 + 3.0, v * 6.0 + 13.0, 2)
            if spur > .90:
                a = clamp(a + (spur - .90) * (2.4 if not glow else 1.1))

            a *= taper
            a *= smoothstep(1.0, .80, abs(v))
            hot = clamp(math.exp(-(d ** 2.6)))
            out.append(px(1.0, lerp(.72, 1.0, hot), lerp(.68, 1.0, hot), clamp(a)))
    return width, height, out


def lance(width=256, height=64, seed=1717):
    """Smooth pressure-beam streak for straight compressed-air blasts.

    The arc sprites are deliberately jagged, which reads as electricity. A pressure lance needs
    the opposite: a clean bright filament with a soft sheath and long tapered ends, so a beam
    drawn with it looks like compressed air rather than lightning."""
    n = Noise(seed, 32)
    out = []
    for y in range(height):
        for x in range(width):
            t = x / (width - 1.0)
            v = (y + .5) / height * 2 - 1

            # sharp filament plus a wide soft sheath
            core = math.exp(-((abs(v) / .055) ** 2))
            sheath = math.exp(-((abs(v) / .34) ** 1.6)) * .40
            # long taper: quick ramp at the muzzle, slow dissolve at the far end
            taper = smoothstep(.0, .09, t) * (1.0 - smoothstep(.62, 1.0, t))
            grain = .86 + .28 * n.fbm(t * 13.0, v * 3.0 + 5.0, 3)

            a = clamp((core + sheath * grain) * taper)
            a *= smoothstep(1.0, .86, abs(v))
            hot = clamp(core)
            out.append(px(1.0, lerp(.86, 1.0, hot), lerp(.80, 1.0, hot), a))
    return width, height, out


def flame(width=128, height=256, seed=909):
    """Upright tapered flame tongue for the Advanced Haki fists.

    Authored tall rather than square: a flame needs a wide base, a licking tip and ragged edges,
    none of which a radial sprite can give. The alpha carries the tongue shape and the RGB carries
    the internal heat gradient, so one sprite reads as white-hot at the base through orange to a
    dark smoky tip."""
    n = Noise(seed, 40)
    out = []
    for y in range(height):
        for x in range(width):
            u = (x + .5) / width * 2 - 1
            v = (y + .5) / height          # 0 = tip, 1 = base
            # tongue narrows toward the tip and wavers as it climbs
            waver = (n.fbm(v * 4.0, 2.0, 4) - .5) * .55 * (1.0 - v)
            halfWidth = (.16 + .74 * (v ** 1.35)) * .95
            d = abs(u - waver) / max(1e-4, halfWidth)
            body = math.exp(-(d ** 2.4))
            # ragged edge and internal licks
            lick = n.ridged(u * 5.0 + 9.0, v * 8.0, 4)
            body *= .62 + .58 * lick
            body *= smoothstep(.0, .12, v) * (1.0 - smoothstep(.90, 1.0, v))
            a = clamp(body)
            heat = clamp(body * (0.35 + 0.9 * v))     # hottest at the base
            cr = 1.0
            cg = lerp(.16, .95, heat)
            cb = lerp(.03, .72, heat ** 2)
            out.append(px(cr, cg, cb, a))
    return width, height, out


def star(size=128):
    """Four-point diffraction glint for galaxy/ember star fields."""
    out = []
    for y in range(size):
        for x in range(size):
            u = (x + .5) / size * 2 - 1
            v = (y + .5) / size * 2 - 1
            r = math.hypot(u, v)
            core = math.exp(-(r / .055) ** 2)
            halo = math.exp(-(r / .17) ** 1.6) * .45
            hstreak = math.exp(-((v / .020) ** 2)) * math.exp(-(abs(u) / .52) ** 1.5) * .82
            vstreak = math.exp(-((u / .020) ** 2)) * math.exp(-(abs(v) / .52) ** 1.5) * .82
            du, dv = (u + v) * .7071, (u - v) * .7071
            d1 = math.exp(-((dv / .014) ** 2)) * math.exp(-(abs(du) / .30) ** 1.5) * .34
            d2 = math.exp(-((du / .014) ** 2)) * math.exp(-(abs(dv) / .30) ** 1.5) * .34
            a = clamp(core + halo + hstreak + vstreak + d1 + d2)
            a *= smoothstep(1.0, .70, r)
            out.append(px(1.0, 1.0, 1.0, a))
    return size, size, out


def nebula(size=256, seed=4242):
    """Wispy cosmic gas used for the Galaxy Impact arms and the overhead disc's outer haze."""
    n = Noise(seed, 40)
    out = []
    for y in range(size):
        for x in range(size):
            u = (x + .5) / size * 2 - 1
            v = (y + .5) / size * 2 - 1
            r = math.hypot(u, v)
            # domain-warped fbm reads as turbulent gas rather than as blobby cloud
            wx = n.fbm(x / size * 3.0, y / size * 3.0, 3) * 1.7
            wy = n.fbm(x / size * 3.0 + 5.0, y / size * 3.0 + 5.0, 3) * 1.7
            gas = n.ridged((x / size * 6.0) + wx, (y / size * 6.0) + wy, 5)
            gas = clamp((gas - .28) / .72)
            a = gas * math.exp(-(r / .52) ** 1.9)
            a *= smoothstep(1.0, .60, r)
            out.append(px(1.0, 1.0, 1.0, clamp(a * 1.15)))
    return size, size, out


def shock_ring(size=256, seed=77):
    """A single expanding pressure ring on one billboard instead of 300 ring particles."""
    n = Noise(seed, 32)
    out = []
    for y in range(size):
        for x in range(size):
            u = (x + .5) / size * 2 - 1
            v = (y + .5) / size * 2 - 1
            r = math.hypot(u, v)
            ang = math.atan2(v, u)
            wobble = (n.fbm(math.cos(ang) * 2.4 + 3.0, math.sin(ang) * 2.4 + 3.0, 3) - .5) * .055
            edge = .74 + wobble
            band = math.exp(-((r - edge) / .052) ** 2)
            inner = math.exp(-((r - edge * .80) / .20) ** 2) * .16   # faint compression haze
            a = clamp(band + inner)
            a *= smoothstep(1.0, .90, r)
            out.append(px(1.0, 1.0, 1.0, a))
    return size, size, out


def crescent(width=256, height=128, seed=8181):
    """Blade-wave sprite: razor leading edge, long dissolving trail, tapered tips.

    The blade renderer used flat POSITION_COLOR quads, so every cut had visible polygon
    edges.  Sampling this gives a real honed edge and a soft trail."""
    n = Noise(seed, 32)
    out = []
    for y in range(height):
        for x in range(width):
            u = x / (width - 1.0)            # along the arc
            v = (y + .5) / height            # 0 = leading edge, 1 = trailing
            tip = math.sin(math.pi * clamp(u)) ** .48
            edge = math.exp(-((v - .16) / .085) ** 2)              # honed cutting edge
            trail = math.exp(-(abs(v - .16) / .46) ** 1.5) * .52    # dissolving wake
            if v < .16:
                trail *= smoothstep(.0, .16, v)                 # nothing ahead of the edge
            grain = .74 + .40 * n.fbm(u * 9.0, v * 5.0, 4)
            a = clamp((edge + trail * grain) * tip)
            a *= smoothstep(1.0, .90, v)
            hot = clamp(edge)
            out.append(px(1.0, lerp(.55, 1.0, hot), lerp(.52, 1.0, hot), a))
    return width, height, out


def ember(size=64, seed=31):
    """Small hot ember with a short motion streak."""
    n = Noise(seed, 16)
    out = []
    for y in range(size):
        for x in range(size):
            u = (x + .5) / size * 2 - 1
            v = (y + .5) / size * 2 - 1
            r = math.hypot(u, v)
            core = math.exp(-(r / .13) ** 2)
            streak = math.exp(-((u / .045) ** 2)) * math.exp(-(abs(v) / .46) ** 1.4) * .55
            a = clamp(core + streak + math.exp(-(r / .34) ** 1.8) * .22)
            a *= smoothstep(1.0, .72, r) * (.80 + .30 * n.fbm(x / 6.0, y / 6.0, 2))
            out.append(px(1.0, lerp(.62, .98, core), lerp(.34, .86, core), clamp(a)))
    return size, size, out


def dust(size=256, seed=555):
    """Grainy debris puff for ground-contact dust; deliberately dirtier than smoke_soft."""
    n = Noise(seed, 56)
    out = []
    for y in range(size):
        for x in range(size):
            u = (x + .5) / size * 2 - 1
            v = (y + .5) / size * 2 - 1
            r = math.hypot(u, v)
            grain = n.fbm(x / size * 11.0, y / size * 11.0, 5)
            speck = n.value(x / size * 34.0, y / size * 34.0)
            a = math.exp(-(r / .48) ** 1.8) * (.42 + .70 * grain)
            if speck > .86:
                a = clamp(a + (speck - .86) * 1.6)
            a *= smoothstep(1.0, .62, r)
            tone = .70 + .30 * grain
            out.append(px(tone, tone * .97, tone * .93, clamp(a)))
    return size, size, out


def crack(size=256, seed=606):
    """Radial fracture decal stamped under heavy impacts."""
    rng = random.Random(seed)
    n = Noise(seed + 2, 32)
    field = [0.0] * (size * size)

    def stroke(x0, y0, ang, length, width, depth):
        steps = int(length * 1.6) + 2
        x, y = x0, y0
        for i in range(steps):
            t = i / steps
            ang += (rng.random() - .5) * .42
            x += math.cos(ang) * (length / steps)
            y += math.sin(ang) * (length / steps)
            w = width * (1.0 - t) + .6
            xi, yi = int(x), int(y)
            rad = int(w) + 2
            for dy in range(-rad, rad + 1):
                for dx in range(-rad, rad + 1):
                    px_, py_ = xi + dx, yi + dy
                    if 0 <= px_ < size and 0 <= py_ < size:
                        d = math.hypot(dx, dy)
                        v = math.exp(-((d / max(.35, w)) ** 2)) * (1.0 - t * .55)
                        idx = py_ * size + px_
                        if v > field[idx]:
                            field[idx] = v
            if depth > 0 and rng.random() < .16:
                stroke(x, y, ang + (rng.random() - .5) * 2.2,
                       length * .42, width * .55, depth - 1)

    c = size / 2.0
    for i in range(11):
        a0 = (2 * math.pi * i / 11.0) + (rng.random() - .5) * .40
        stroke(c, c, a0, size * (.30 + rng.random() * .17), 2.6 + rng.random() * 1.5, 2)

    out = []
    for y in range(size):
        for x in range(size):
            u = (x + .5) / size * 2 - 1
            v = (y + .5) / size * 2 - 1
            r = math.hypot(u, v)
            a = field[y * size + x] * (.86 + .28 * n.fbm(x / 9.0, y / 9.0, 3))
            a *= smoothstep(1.0, .55, r)
            out.append(px(1.0, .96, .94, clamp(a)))
    return size, size, out


def galaxy_disc(size=512, seed=2718):
    """The overhead Galaxy Impact disc.

    A logarithmic two-arm spiral with a hot core bulge, dust lanes and a star field, with
    its cosmic palette baked in (this sprite is NOT runtime-tinted white like the others)."""
    rng = random.Random(seed)
    n = Noise(seed, 64)
    ARMS, TWIST, ARM_W = 2, 2.55, .58
    out = []

    stars = []
    for _ in range(1400):
        rr = rng.random() ** .55
        th = rng.random() * math.tau
        stars.append((rr * math.cos(th), rr * math.sin(th),
                      rng.random() * .8 + .2, rng.random()))

    starfield = [0.0] * (size * size)
    for (sx, sy, br, kind) in stars:
        px_ = int((sx * .5 + .5) * size)
        py_ = int((sy * .5 + .5) * size)
        rad = 1 if kind < .82 else 2
        for dy in range(-rad, rad + 1):
            for dx in range(-rad, rad + 1):
                x, y = px_ + dx, py_ + dy
                if 0 <= x < size and 0 <= y < size:
                    d = math.hypot(dx, dy)
                    v = br * math.exp(-(d / .85) ** 2)
                    idx = y * size + x
                    starfield[idx] = min(1.0, starfield[idx] + v)

    for y in range(size):
        for x in range(size):
            u = (x + .5) / size * 2 - 1
            v = (y + .5) / size * 2 - 1
            r = math.hypot(u, v)
            if r >= 1.0:
                out.append((0, 0, 0, 0))
                continue
            th = math.atan2(v, u)

            # logarithmic spiral arms
            rr = max(.035, r)
            phase = th - TWIST * math.log(rr)
            arm = 0.0
            for k in range(ARMS):
                d = (phase - k * math.tau / ARMS) % math.tau
                if d > math.pi:
                    d -= math.tau
                arm = max(arm, math.exp(-(d / ARM_W) ** 2))

            warp = n.fbm(u * 2.4 + 9.0, v * 2.4 + 9.0, 4)
            arm *= .62 + .76 * warp
            arm *= smoothstep(.035, .30, r) * smoothstep(1.0, .70, r)

            bulge = math.exp(-(r / .105) ** 1.75)
            core = math.exp(-(r / .036) ** 2)
            halo = math.exp(-(r / .46) ** 1.55) * .30

            # dust lanes: a phase-shifted spiral that subtracts from the arms
            dust_d = (phase + .40) % math.tau
            if dust_d > math.pi:
                dust_d -= math.tau
            lane = math.exp(-(dust_d / .24) ** 2) * .58 * smoothstep(.10, .40, r)

            density = clamp(arm * .92 + bulge * .95 + halo)
            density = clamp(density * (1.0 - lane))
            sf = starfield[y * size + x] * (.30 + .70 * clamp(arm + bulge))
            a = clamp(density + core + sf * .85)
            a *= smoothstep(1.0, .86, r)

            # palette: white-gold core -> violet inner arms -> deep indigo rim
            t = clamp(r / .85)
            cr = lerp(1.00, .38, t)
            cg = lerp(.94, .30, t)
            cb = lerp(.78, .92, t)
            cr = lerp(cr, .78, clamp(arm) * .45)
            cg = lerp(cg, .42, clamp(arm) * .45)
            cb = lerp(cb, 1.00, clamp(arm) * .45)
            hot = clamp(core + bulge * .85)
            cr = lerp(cr, 1.0, hot)
            cg = lerp(cg, 1.0, hot)
            cb = lerp(cb, 1.0, hot * .85)
            if sf > .05:
                s = clamp(sf)
                cr, cg, cb = lerp(cr, 1.0, s), lerp(cg, 1.0, s), lerp(cb, 1.0, s)
            out.append(px(cr, cg, cb, a))
    return size, size, out


def galaxy_disc_red(size=512, seed=2718):
    """Black/red galaxy for the un-coated and normal-Armament tiers.

    Same structure as the violet disc so the two read as one technique at different intensities;
    only the palette changes -- a white-hot core bleeding through blood red into near-black at the
    rim, which is the Armament colour language the rest of the mod uses."""
    w, h, pixels = galaxy_disc(size, seed)
    out = []
    for (r, g, b, a) in pixels:
        # luminance drives a black -> red -> white-hot ramp
        lum = (r * .30 + g * .59 + b * .11) / 255.0
        if lum < .5:
            k = lum / .5
            cr, cg, cb = .06 + .94 * k, .01 + .07 * k, .02 + .04 * k
        else:
            k = (lum - .5) / .5
            cr, cg, cb = 1.0, .08 + .92 * k, .06 + .94 * k
        out.append(px(cr, cg, cb, a / 255.0))
    return w, h, out


def galaxy_core(size=256):
    """Accretion core the disc collapses into on release: blinding centre, violet corona."""
    out = []
    for y in range(size):
        for x in range(size):
            u = (x + .5) / size * 2 - 1
            v = (y + .5) / size * 2 - 1
            r = math.hypot(u, v)
            core = math.exp(-(r / .07) ** 2)
            corona = math.exp(-(r / .26) ** 1.5) * .72
            halo = math.exp(-(r / .60) ** 1.8) * .30
            a = clamp(core + corona + halo)
            a *= smoothstep(1.0, .72, r)
            hot = clamp(core + corona * .55)
            cr = lerp(.60, 1.00, hot)
            cg = lerp(.30, 1.00, hot)
            cb = lerp(1.00, .96, hot)
            out.append(px(cr, cg, cb, a))
    return size, size, out


SPRITES = {
    "smoke_soft.png":  smoke_soft,
    "glow_core.png":   glow_core,
    "arc.png":         lambda: arc(256, 64, 99, .34, .115, False),
    "arc_soft.png":    lambda: arc(256, 64, 99, .34, .150, True),
    "star.png":        star,
    "flame.png":       lambda: flame(),
    "lance.png":       lambda: lance(),
    "nebula.png":      nebula,
    "shock_ring.png":  shock_ring,
    "crescent.png":    crescent,
    "ember.png":       ember,
    "dust.png":        dust,
    "crack.png":       crack,
    "galaxy_disc.png": galaxy_disc,
    "galaxy_disc_red.png": galaxy_disc_red,
    "galaxy_core.png": galaxy_core,
}

if __name__ == "__main__":
    target = os.path.normpath(OUT)
    os.makedirs(target, exist_ok=True)
    print("Writing HexHaki effect sprites to %s" % target)
    for name, fn in SPRITES.items():
        w, h, pixels = fn()
        write_png(os.path.join(target, name), w, h, pixels)
    print("done: %d sprites" % len(SPRITES))
