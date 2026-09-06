#!/usr/bin/env python3
"""Structural and joint-integrity validation for HexHaki player_animation files.

Runs with nothing but a Python 3 interpreter - no Forge, no Gradle, no Minecraft.

Two classes of check:

1. Schema - the shape Player Animator actually loads, plus the `bend` channel that
   build.gradle's validateHakiResources task requires of every animation.

2. Joint integrity - the failures that produce a visibly broken body. Rules are derived
   from the 45 pre-existing HexHaki animations rather than invented, so a rule that
   fires on a new file would also have fired on the shipped ones:

     * Knees never bend backwards. Across the existing corpus leg `bend` is positive
       237 times and negative zero times.
     * No limb is hidden by teleporting it out of the world. The reference mod
       TruePrimePiece hides its real arm with a -74957 block translation; body
       translation here is bounded to +/- 3 blocks.
     * Hands stay clear of the face, checked by forward kinematics from the shoulder
       rather than by eye.
     * Rotations stay inside one turn, keyframes are ordered and in range, and the
       final pose returns to rest for non-looping animations so the body cannot be
       left deformed.
"""
import json
import math
import sys
from pathlib import Path

BONES = {"body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg"}
ROTATION_AXES = {"pitch", "yaw", "roll"}
BEND_BONES = {"torso", "rightArm", "leftArm", "rightLeg", "leftLeg"}
TRANSLATION_BONES = {"body"}
EASINGS = {
    "linear", "instant",
    "insine", "outsine", "inoutsine",
    "inquad", "outquad", "inoutquad",
    "incubic", "outcubic", "inoutcubic",
    "inquart", "outquart", "inoutquart",
    "inquint", "outquint", "inoutquint",
    "inexpo", "outexpo", "inoutexpo",
    "incirc", "outcirc", "inoutcirc",
    "inback", "outback", "inoutback",
    "inelastic", "outelastic", "inoutelastic",
    "inbounce", "outbounce", "inoutbounce",
}

# Model-space landmarks in Minecraft model units, from the vanilla player model.
SHOULDER = {"rightArm": (-5.0, 22.0, 0.0), "leftArm": (5.0, 22.0, 0.0)}
# The slim ("Alex") model is one pixel narrower per arm, so its shoulder pivot sits half a
# unit inboard. A pose that clears the face on the standard model can still graze it on slim,
# so face clearance is checked against both.
SLIM_SHOULDER = {"rightArm": (-4.5, 22.0, 0.0), "leftArm": (4.5, 22.0, 0.0)}
ARM_SEGMENT = 6.0          # upper arm and forearm are each half of the 12-unit limb
HEAD_CENTRE = (0.0, 28.0, 0.0)
HEAD_RADIUS = 4.6          # 8-unit cube, plus a little clearance
MAX_BODY_TRANSLATION = 3.0 # blocks

# Angular rate limits, calibrated against the 45 pre-existing animations: their median rate is
# 0.5 deg/tick, p99 is 21, and the fastest deliberate punch reaches 113. Past ~130 a keyframe
# pair stops reading as motion and starts reading as a snap, so that is the hard failure. The
# awakening is meant to be buoyant rather than snappy, so nika_* files are held to a tighter
# advisory bound as well.
MAX_DEGREES_PER_TICK = 130.0
NIKA_ADVISORY_DEGREES_PER_TICK = 45.0


def rotate(vec, pitch, yaw, roll):
    """Apply ZYX rotation, matching ModelPart's zRot -> yRot -> xRot order."""
    x, y, z = vec
    rz, ry, rx = math.radians(roll), math.radians(yaw), math.radians(pitch)
    c, s = math.cos(rz), math.sin(rz)
    x, y = x * c - y * s, x * s + y * c
    c, s = math.cos(ry), math.sin(ry)
    x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(rx), math.sin(rx)
    y, z = y * c - z * s, y * s + z * c
    return (x, y, z)


def hand_position(side, pose, shoulders=None):
    """Forward-kinematics the hand from the shoulder so face intrusion is measurable."""
    pitch = pose.get("pitch", 0.0) or 0.0
    yaw = pose.get("yaw", 0.0) or 0.0
    roll = pose.get("roll", 0.0) or 0.0
    bend = pose.get("bend", 0.0) or 0.0
    sx, sy, sz = (shoulders or SHOULDER)[side]
    upper = rotate((0.0, -ARM_SEGMENT, 0.0), pitch, yaw, roll)
    elbow = (sx + upper[0], sy + upper[1], sz + upper[2])
    fore = rotate((0.0, -ARM_SEGMENT, 0.0), pitch + bend, yaw, roll)
    return (elbow[0] + fore[0], elbow[1] + fore[1], elbow[2] + fore[2])


def validate(path):
    errors, warnings = [], []
    # Face clearance is a Nika requirement ("keep the face clear", "hands stay clear of the
    # face"). Elsewhere in HexHaki a hand near the head is often the whole point of the pose -
    # catching an arrow, charging beside the temple - so it is reported but not failed there.
    strict_face = path.name.startswith("nika_")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        return [f"unparseable JSON: {exc}"], []

    if "emote" not in data:
        return ["missing 'emote' object"], []
    emote = data["emote"]

    for field in ("beginTick", "endTick", "stopTick", "isLoop", "returnTick", "degrees"):
        if field not in emote:
            errors.append(f"emote is missing '{field}'")
    if not emote.get("degrees", False):
        errors.append("degrees must be true; every existing HexHaki animation is authored in degrees")

    moves = emote.get("moves")
    if not isinstance(moves, list) or not moves:
        return errors + ["emote.moves is missing or empty"], warnings

    begin = emote.get("beginTick", 0)
    end = emote.get("endTick", 0)
    if end <= begin:
        errors.append(f"endTick ({end}) must be after beginTick ({begin})")

    seen_bend = False
    previous_tick = None

    for index, move in enumerate(moves):
        where = f"move[{index}]"
        if "tick" not in move:
            errors.append(f"{where} has no tick")
            continue
        tick = move["tick"]
        if previous_tick is not None and tick < previous_tick:
            errors.append(f"{where} tick {tick} goes backwards after {previous_tick}")
        previous_tick = tick
        if not (begin <= tick <= end):
            errors.append(f"{where} tick {tick} falls outside [{begin},{end}]")

        easing = move.get("easing")
        if easing is not None and easing.lower() not in EASINGS:
            errors.append(f"{where} unknown easing '{easing}'")

        for bone, pose in move.items():
            if bone in ("tick", "easing"):
                continue
            if bone not in BONES:
                errors.append(f"{where} unknown bone '{bone}'")
                continue
            if not isinstance(pose, dict):
                errors.append(f"{where} bone '{bone}' is not an object")
                continue

            for axis, value in pose.items():
                if not isinstance(value, (int, float)):
                    errors.append(f"{where} {bone}.{axis} is not numeric")
                    continue
                if axis in ("x", "y", "z"):
                    if bone not in TRANSLATION_BONES:
                        errors.append(f"{where} {bone} may not translate; only 'body' may")
                    elif abs(value) > MAX_BODY_TRANSLATION:
                        errors.append(
                            f"{where} body.{axis}={value} exceeds +/-{MAX_BODY_TRANSLATION} blocks "
                            "- limbs must not be hidden by translating them out of the world")
                elif axis == "bend":
                    seen_bend = True
                    if bone not in BEND_BONES:
                        errors.append(f"{where} {bone} has no bend joint")
                    elif bone.endswith("Leg") and value < -0.001:
                        errors.append(
                            f"{where} {bone}.bend={value} bends the knee backwards "
                            "(leg bend is non-negative in all 45 existing animations)")
                    elif abs(value) > 160:
                        errors.append(f"{where} {bone}.bend={value} exceeds a plausible joint range")
                elif axis in ROTATION_AXES:
                    if abs(value) > 360:
                        errors.append(f"{where} {bone}.{axis}={value} exceeds one full turn")
                else:
                    errors.append(f"{where} {bone} has unknown axis '{axis}'")

            # Hands clear of the face.
            if bone in SHOULDER:
                for build, shoulders in (("standard", SHOULDER), ("slim", SLIM_SHOULDER)):
                    hx, hy, hz = hand_position(bone, pose, shoulders)
                    dx = hx - HEAD_CENTRE[0]
                    dy = hy - HEAD_CENTRE[1]
                    dz = hz - HEAD_CENTRE[2]
                    distance = math.sqrt(dx * dx + dy * dy + dz * dz)
                    if distance < HEAD_RADIUS:
                        message = (
                            f"{where} {bone} puts the hand {distance:.2f}u from the head centre "
                            f"on the {build} model (needs >= {HEAD_RADIUS}u) - it would cover the face")
                        (errors if strict_face else warnings).append(message)

    # Motion continuity: a huge jump between adjacent keyframes tears the pose.
    tracked = {}
    for index, move in enumerate(moves):
        tick = move.get("tick")
        if not isinstance(tick, (int, float)):
            continue
        for bone, pose in move.items():
            if bone in ("tick", "easing") or not isinstance(pose, dict):
                continue
            for axis, value in pose.items():
                if axis in ("x", "y", "z") or not isinstance(value, (int, float)):
                    continue
                key = (bone, axis)
                if key in tracked:
                    last_tick, last_value = tracked[key]
                    span = tick - last_tick
                    if span > 0:
                        rate = abs(value - last_value) / span
                        if rate > MAX_DEGREES_PER_TICK:
                            errors.append(
                                f"move[{index}] {bone}.{axis} moves {rate:.0f} deg/tick "
                                f"(limit {MAX_DEGREES_PER_TICK:.0f}) - this snaps rather than moves")
                        elif strict_face and rate > NIKA_ADVISORY_DEGREES_PER_TICK:
                            warnings.append(
                                f"move[{index}] {bone}.{axis} moves {rate:.0f} deg/tick, above the "
                                f"{NIKA_ADVISORY_DEGREES_PER_TICK:.0f} advisory for a buoyant pose")
                tracked[key] = (tick, value)

    # Foot contact: legs posed as if airborne while the body sits at or below ground level.
    for index, move in enumerate(moves):
        body = move.get("body")
        right = move.get("rightLeg")
        left = move.get("leftLeg")
        if not isinstance(body, dict) or not isinstance(right, dict) or not isinstance(left, dict):
            continue
        lift = body.get("y", 0.0) or 0.0
        right_pitch = right.get("pitch", 0.0) or 0.0
        left_pitch = left.get("pitch", 0.0) or 0.0
        if lift <= 0.05 and right_pitch < -8 and left_pitch < -8:
            warnings.append(
                f"move[{index}] both legs are swung back ({right_pitch}, {left_pitch}) while the "
                f"body is grounded (y={lift}) - the feet would not be making contact")

    if not seen_bend:
        errors.append("no 'bend' channel anywhere; build.gradle's validateHakiResources rejects this")

    # A non-looping animation must finish at rest, or the body stays deformed afterwards.
    if not emote.get("isLoop", False):
        final = moves[-1]
        resting = True
        for bone, pose in final.items():
            if bone in ("tick", "easing") or not isinstance(pose, dict):
                continue
            for axis, value in pose.items():
                if isinstance(value, (int, float)) and abs(value) > 6.0:
                    resting = False
        if not resting:
            warnings.append(
                "final keyframe is not near rest; a non-looping animation should return the body "
                "to neutral so nothing is left deformed")

    return errors, warnings


def main(argv):
    root = Path(argv[1]) if len(argv) > 1 else Path("src/main/resources/assets/hexhaki/player_animation")
    files = sorted(root.glob("*.json"))
    if not files:
        print(f"no animations found under {root}")
        return 1

    total_errors = 0
    total_warnings = 0
    for path in files:
        errors, warnings = validate(path)
        total_errors += len(errors)
        total_warnings += len(warnings)
        if errors:
            print(f"FAIL  {path.name}")
            for message in errors:
                print(f"        ERROR   {message}")
        elif warnings:
            print(f"warn  {path.name}")
        else:
            print(f"ok    {path.name}")
        for message in warnings:
            print(f"        warning {message}")

    print(f"\n{len(files)} animation(s): {total_errors} error(s), {total_warnings} warning(s)")
    return 1 if total_errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
