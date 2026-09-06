#!/usr/bin/env python3
"""
Injects a struggle phase into the King's Grip victim animations.

The authored victim clips anchor the throat catch and the gut-punch recoil correctly, but the
suspended hold between them is almost static: on the Advanced tier the victim goes from
`body.pitch = -8` at tick 24 to `body.pitch = -10` at tick 112, i.e. barely two degrees of change
across 4.4 seconds of screen time.  A person being lifted by the throat does not hang still, and
that stillness is most of why the execution reads as staged rather than brutal.

This rewrites only the hold segment of each `kings_grip_victim_*.json`, leaving the catch and the
recoil keyframes exactly as authored.  The inserted frames are the interpolated original pose plus
a struggle offset with two phases:

  * fight   - alternating leg kicks, both arms hauling up at the hand crushing the throat, head
              thrashing, torso twisting against the grip;
  * fade    - the same motion decaying toward limpness as the victim runs out of air, so the
              punch lands on a body that has already gone slack.

Deterministic: the offsets are closed-form functions of tick, so every client renders identical
frames and re-running this script reproduces the same files byte-for-byte.

    python3 tools/generate_kings_grip_struggle.py
"""

import json
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ANIM = os.path.normpath(os.path.join(
    HERE, "..", "src", "main", "resources", "assets", "hexhaki",
    "player_animation"))

TARGETS = [
    "kings_grip_victim.json",
    "kings_grip_victim_none.json",
    "kings_grip_victim_haki.json",
    "kings_grip_victim_advanced.json",
]

# Struggle frames are placed this many ticks apart; dense enough to read as thrashing without
# bloating the clip.
STEP = 6


def lerp(a, b, t):
    return a + (b - a) * t


def blend(pose_a, pose_b, t):
    """Interpolates two keyframe bodies channel by channel."""
    out = {}
    for bone, channels in pose_a.items():
        if bone in ("tick", "easing") or not isinstance(channels, dict):
            continue
        other = pose_b.get(bone, {})
        merged = {}
        for channel, value in channels.items():
            merged[channel] = lerp(value, other.get(channel, value), t)
        out[bone] = merged
    return out


def struggle(pose, phase, intensity):
    """Adds thrashing on top of an interpolated hold pose.

    `phase` advances with the tick and drives the oscillators; `intensity` is 1 at the start of
    the hold and decays to roughly 0.15 as the victim weakens.
    """
    kick = math.sin(phase * 2.10)
    kick2 = math.sin(phase * 2.10 + math.pi)          # opposite leg
    thrash = math.sin(phase * 1.35 + .7)
    twist = math.sin(phase * .85 + 1.9)
    claw = math.sin(phase * 1.75 + .35)
    jolt = math.sin(phase * 3.30)                      # short sharp spasms

    def bump(bone, channel, amount):
        if bone in pose and channel in pose[bone]:
            pose[bone][channel] += amount * intensity

    # Legs kick alternately and hard; this is the most legible signal that the victim is alive.
    bump("rightLeg", "pitch", kick * 16.0)
    bump("leftLeg", "pitch", kick2 * 16.0)
    bump("rightLeg", "bend", kick2 * 13.0)
    bump("leftLeg", "bend", kick * 13.0)
    bump("rightLeg", "roll", twist * 4.5)
    bump("leftLeg", "roll", -twist * 4.5)

    # Both arms haul upward at the hand on the throat, with the grab pulsing.
    bump("rightArm", "pitch", -abs(claw) * 22.0 - 6.0)
    bump("leftArm", "pitch", -abs(claw) * 22.0 - 6.0)
    bump("rightArm", "bend", -abs(claw) * 20.0)
    bump("leftArm", "bend", -abs(claw) * 20.0)
    bump("rightArm", "yaw", claw * 5.0)
    bump("leftArm", "yaw", -claw * 5.0)

    # Head thrashes; torso twists and jerks against the grip.
    bump("head", "yaw", thrash * 13.0)
    bump("head", "roll", jolt * 5.5)
    bump("head", "pitch", -abs(jolt) * 4.0)
    bump("torso", "yaw", twist * 7.5)
    bump("torso", "roll", -twist * 4.0)
    bump("torso", "bend", jolt * 3.5)
    bump("body", "roll", twist * 2.6)
    bump("body", "yaw", twist * 3.2)
    # A slight bob: the victim's own weight fighting the arm holding them up.
    if "body" in pose and "y" in pose["body"]:
        pose["body"]["y"] += jolt * .012 * intensity
    return pose


def round_pose(pose):
    for channels in pose.values():
        for channel in channels:
            value = channels[channel]
            channels[channel] = round(value, 4) if channel in "xyz" else round(value, 2)
    return pose


def find_impact(moves):
    """Index of the gut-punch recoil keyframe: the largest single-step torso/body pitch jump.

    Everything before it is the hold and may be filled; the recoil and settle must stay exactly
    as authored, since they are timed against the server's scripted impact tick."""
    best, best_delta = len(moves) - 1, -1.0
    for i in range(1, len(moves)):
        prev, cur = moves[i - 1], moves[i]
        delta = 0.0
        for bone in ("body", "torso"):
            a = prev.get(bone, {}).get("pitch", 0.0)
            b = cur.get(bone, {}).get("pitch", 0.0)
            delta += abs(b - a)
        if delta > best_delta:
            best, best_delta = i, delta
    return best


def process(path):
    with open(path, "r", encoding="utf-8") as fh:
        data = json.load(fh)
    moves = data["emote"]["moves"]

    impact = find_impact(moves)
    hold_start = moves[0]["tick"]
    hold_end = moves[impact - 1]["tick"] if impact > 0 else moves[-1]["tick"]
    if hold_end <= hold_start:
        print("  %-38s no hold segment worth filling" % os.path.basename(path))
        return False

    # Fill every gap in the hold, not just the largest one: leaving even one long gap unfilled
    # puts a second or more of dead stillness back into the middle of the shot.
    rebuilt = []
    added = 0
    for i in range(len(moves)):
        rebuilt.append(moves[i])
        if i >= impact - 1 or i + 1 >= len(moves):
            continue
        start, end = moves[i], moves[i + 1]
        t0, t1 = start["tick"], end["tick"]
        if t1 - t0 < 2 * STEP:
            continue
        tick = t0 + STEP
        while tick < t1 - 1:
            # Progress across the whole hold, so the fade tracks the victim running out of air
            # rather than restarting inside every gap.
            overall = (tick - hold_start) / float(max(1, hold_end - hold_start))
            local = (tick - t0) / float(t1 - t0)
            pose = blend(start, end, local)
            intensity = 1.0 if overall < .45 else max(.15, 1.0 - (overall - .45) / .55)
            intensity *= min(1.0, (tick - hold_start) / float(3 * STEP))
            pose = round_pose(struggle(pose, (tick - hold_start) * .55, intensity))
            frame = {"tick": tick, "easing": "inoutsine"}
            frame.update(pose)
            rebuilt.append(frame)
            added += 1
            tick += STEP

    if not added:
        print("  %-38s no hold segment worth filling" % os.path.basename(path))
        return False

    data["emote"]["moves"] = rebuilt
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")
    print("  %-38s hold %d-%d  +%d struggle frames (%d total)" %
          (os.path.basename(path), hold_start, hold_end, added, len(rebuilt)))
    return True


if __name__ == "__main__":
    print("Injecting King's Grip victim struggle into %s" % ANIM)
    for name in TARGETS:
        target = os.path.join(ANIM, name)
        if os.path.isfile(target):
            process(target)
        else:
            print("  %-38s missing, skipped" % name)
