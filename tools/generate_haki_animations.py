#!/usr/bin/env python3
"""
Authors the Conqueror's Haki scream and the pain reaction clips.

Written as a generator rather than by hand because these clips are dense: the roar and the pain
reactions both need a sustained tremble, and hand-authoring thirty-odd keyframes of correlated
bone values across several files is where mistakes hide. Everything here is a closed-form
function of tick, so the output is deterministic and re-running reproduces it byte-for-byte.

Player Animator conventions used:
  * degrees, version 3 emote format;
  * negative head pitch looks up, so the roar throws the head back;
  * negative arm pitch raises the arm forward, so hands reach the head near -135;
  * `bend` is the BendyLib mid-limb channel and must be present on every clip.

    python3 tools/generate_haki_animations.py
"""

import json
import math
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "src", "main", "resources", "assets", "hexhaki", "player_animation")


def frame(tick, easing="inoutsine", **bones):
    f = {"tick": tick, "easing": easing}
    f.update(bones)
    return f


def body(x=0.0, y=0.0, z=0.0, pitch=0.0, yaw=0.0, roll=0.0):
    return {"x": round(x, 4), "y": round(y, 4), "z": round(z, 4),
            "pitch": round(pitch, 2), "yaw": round(yaw, 2), "roll": round(roll, 2)}


def part(pitch=0.0, yaw=0.0, roll=0.0, bend=0.0):
    """Bendable part: torso, arms and legs all carry BendyLib's mid-limb channel."""
    return {"pitch": round(pitch, 2), "yaw": round(yaw, 2),
            "roll": round(roll, 2), "bend": round(bend, 2)}


def head_part(pitch=0.0, yaw=0.0, roll=0.0):
    """Head rotation only.

    The head has NO bend state in Player Animator. Emitting a `bend` key here makes
    AnimationJson.addPartIfExists dereference a null StateCollection and throw during resource
    load, which crashes the client on boot before the main menu. Only torso, arms and legs bend."""
    return {"pitch": round(pitch, 2), "yaw": round(yaw, 2), "roll": round(roll, 2)}


def clip(name, description, end, moves):
    return {
        "version": 3,
        "name": name,
        "author": "HexHaki V2",
        "description": description,
        "emote": {
            "beginTick": 0,
            "endTick": end,
            "stopTick": end,
            "isLoop": False,
            "returnTick": 0,
            "degrees": True,
            "easeBeforeKeyframe": False,
            "moves": moves,
        },
    }


def conqueror_scream():
    """Full-charge release: coil, then a whole-body roar held on a tremble.

    Posture is torso-only. `body` is the model root, so pitching it takes the legs with it
    and swings the feet back through the floor -- which is what "the legs are messed up"
    turned out to mean even after the leg channels were removed.

    No leg channels: the lower body is left entirely to vanilla, for the same reason the
    Gear 5 charge has none -- a scripted stance fights whatever the player is actually
    standing on or doing, and the result folds in directions no skeleton holds."""
    moves = [
        # settle, then coil down into the shout
        frame(0, "outquad",
              body=body(), torso=part(3, 0, 0, 2), head=head_part(4),
              rightArm=part(6, -4, 4, -8), leftArm=part(6, 4, -4, -8)),
        frame(6, "inquad",
              body=body(), torso=part(19, 0, 0, 14), head=head_part(22),
              rightArm=part(34, -14, 16, -44), leftArm=part(34, 14, -16, -44)),
        # the shout itself: head thrown back, chest opened, arms flung down and out
        frame(11, "outexpo",
              body=body(), torso=part(-24, 0, 0, -19), head=head_part(-44),
              rightArm=part(-26, -46, 34, 26), leftArm=part(-26, 46, -34, 26)),
    ]
    # hold the roar with a tremble so the pose is straining rather than posed
    hold_start, hold_end, step = 14, 44, 3
    tick = hold_start
    while tick <= hold_end:
        t = (tick - hold_start) / float(hold_end - hold_start)
        # tremble decays slightly as the shout runs out of air
        amp = 1.0 - .35 * t
        s = math.sin(tick * 1.15)
        s2 = math.sin(tick * 1.90 + 1.1)
        moves.append(frame(tick, "inoutsine",
                           body=body(),
                           torso=part(-24 + s * 2.8 * amp, s2 * 2.4 * amp, s2 * 1.8 * amp,
                                      -19 + s * 2.2 * amp),
                           head=head_part(-44 + s * 3.2 * amp, s2 * 3.4 * amp, s * 2.0 * amp),
                           rightArm=part(-26 + s2 * 4.0 * amp, -46 + s * 3.0 * amp,
                                         34 + s2 * 2.6 * amp, 26 + s * 3.4 * amp),
                           leftArm=part(-26 + s2 * 4.0 * amp, 46 - s * 3.0 * amp,
                                        -34 - s2 * 2.6 * amp, 26 + s * 3.4 * amp)))
        tick += step
    # let the chest fall and return to stance
    moves.append(frame(52, "inoutquad",
                       body=body(), torso=part(9, 0, 0, 6), head=head_part(10),
                       rightArm=part(12, -8, 8, -14), leftArm=part(12, 8, -8, -14)))
    moves.append(frame(64, "outquad",
                       body=body(), torso=part(), head=head_part(),
                       rightArm=part(), leftArm=part()))
    return clip("conqueror_scream",
                "Full-charge Conqueror's release: the user coils, then roars -- head thrown back, "
                "chest opened, arms flung down and out -- and holds the shout on a decaying "
                "tremble before the chest falls.",
                64, moves)


def pain_clutch():
    """Standing victim: hands snap to the head, then they double over."""
    moves = [
        frame(0, "outexpo",
              body=body(), torso=part(), head=head_part(),
              rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part()),
        # the pressure hits: head snaps, hands rush up to it
        frame(3, "outexpo",
              body=body(y=-0.04, pitch=10), torso=part(14, 0, 3, 10), head=head_part(-14, 0, 6),
              rightArm=part(-124, 22, 32, -36), leftArm=part(-124, -22, -32, -36),
              rightLeg=part(6, -2, 3, 12), leftLeg=part(-6, 2, -3, 12)),
        # doubling forward, knees buckling
        frame(9, "inoutquad",
              body=body(y=-0.16, pitch=34), torso=part(40, 0, 5, 30), head=head_part(24, 0, 8),
              rightArm=part(-136, 28, 40, -52), leftArm=part(-136, -28, -40, -52),
              rightLeg=part(18, -5, 7, 34), leftLeg=part(-16, 5, -7, 34)),
    ]
    # shuddering while the pressure holds
    tick, end, step = 13, 37, 4
    while tick <= end:
        t = (tick - 13) / float(end - 13)
        amp = 1.0 - .25 * t
        s = math.sin(tick * 1.55)
        s2 = math.sin(tick * 2.35 + .8)
        moves.append(frame(tick, "inoutsine",
                           body=body(y=-0.16 + s2 * .018 * amp, pitch=34 + s * 4.5 * amp,
                                     roll=s2 * 3.0 * amp),
                           torso=part(40 + s * 5.0 * amp, s2 * 4.5 * amp, 5 + s2 * 3.5 * amp,
                                      30 + s * 4.0 * amp),
                           head=head_part(24 + s * 6.0 * amp, s2 * 7.0 * amp, 8 + s * 4.0 * amp),
                           rightArm=part(-136 + s * 6.0 * amp, 28 + s2 * 4.0 * amp,
                                         40 + s * 3.0 * amp, -52 + s2 * 5.0 * amp),
                           leftArm=part(-136 + s * 6.0 * amp, -28 - s2 * 4.0 * amp,
                                        -40 - s * 3.0 * amp, -52 + s2 * 5.0 * amp),
                           rightLeg=part(18 + s2 * 3.0 * amp, -5, 7, 34),
                           leftLeg=part(-16 - s2 * 3.0 * amp, 5, -7, 34)))
        tick += step
    moves.append(frame(48, "inoutquad",
                       body=body(y=-0.06, pitch=16), torso=part(20, 0, 2, 15), head=head_part(14),
                       rightArm=part(-60, 14, 20, -28), leftArm=part(-60, -14, -20, -28),
                       rightLeg=part(8, -2, 3, 16), leftLeg=part(-8, 2, -3, 16)))
    moves.append(frame(60, "outquad",
                       body=body(), torso=part(), head=head_part(),
                       rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part()))
    return clip("haki_pain_clutch",
                "Conqueror's pressure reaction: the victim's hands snap to their head, they "
                "double forward over buckling knees and shudder before straightening.",
                60, moves)


def pain_convulse():
    """Downed victim: back arched off the floor, convulsing."""
    moves = [
        frame(0, "outexpo",
              body=body(), torso=part(), head=head_part(),
              rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part()),
        # thrown onto the back, spine arching
        frame(4, "outexpo",
              body=body(y=-0.42, pitch=-64), torso=part(-30, 0, 6, -26), head=head_part(-26, 8, 10),
              rightArm=part(-96, 34, 44, -30), leftArm=part(-96, -34, -44, -30),
              rightLeg=part(-38, -10, 12, 46), leftLeg=part(-28, 10, -12, 40)),
    ]
    tick, end, step = 8, 44, 4
    while tick <= end:
        t = (tick - 8) / float(end - 8)
        amp = 1.0 - .55 * t          # convulsions weaken as they go limp
        s = math.sin(tick * 1.95)
        s2 = math.sin(tick * 3.10 + 1.4)
        moves.append(frame(tick, "inoutsine",
                           body=body(y=-0.42 + abs(s) * .05 * amp, pitch=-64 + s * 7.0 * amp,
                                     roll=s2 * 5.0 * amp),
                           torso=part(-30 + s * 8.0 * amp, s2 * 6.0 * amp, 6 + s2 * 5.0 * amp,
                                      -26 + s * 7.0 * amp),
                           head=head_part(-26 + s * 9.0 * amp, 8 + s2 * 10.0 * amp, 10 + s * 6.0 * amp),
                           rightArm=part(-96 + s2 * 12.0 * amp, 34 + s * 7.0 * amp,
                                         44 + s2 * 5.0 * amp, -30 + s * 9.0 * amp),
                           leftArm=part(-96 - s2 * 12.0 * amp, -34 - s * 7.0 * amp,
                                        -44 - s2 * 5.0 * amp, -30 + s * 9.0 * amp),
                           rightLeg=part(-38 + s * 9.0 * amp, -10, 12, 46 + s2 * 6.0 * amp),
                           leftLeg=part(-28 - s * 9.0 * amp, 10, -12, 40 - s2 * 6.0 * amp)))
        tick += step
    moves.append(frame(56, "inoutquad",
                       body=body(y=-0.40, pitch=-58), torso=part(-24, 0, 3, -20), head=head_part(-18, 4, 5),
                       rightArm=part(-80, 24, 32, -18), leftArm=part(-80, -24, -32, -18),
                       rightLeg=part(-30, -7, 8, 36), leftLeg=part(-22, 7, -8, 32)))
    return clip("haki_pain_convulse",
                "Downed Conqueror's reaction: the victim is thrown onto their back with the spine "
                "arched off the floor, convulsing in waves that weaken as they go limp.",
                56, moves)


def gear5_charge():
    """Conqueror's charge: head thrown back at the sky, twitching.

    Four attempts at posing the body all failed, and each one failed for its own reason -- the legs
    folded, then `body` took the legs with it, then the torso curl put the spine somewhere no spine
    goes. The lesson is not that the poses were bad. It is that **overriding a bone means owning it
    in every situation the player can be in**: walking, sneaking, swimming, on a slab, mid-fall.
    Vanilla already handles all of those correctly, and any authored value replaces that handling
    outright.

    So this animates exactly one bone. The head goes back to look at the sky and twitches there --
    an irregular tremble on three incommensurate frequencies with occasional sharper spasms, no
    rhythm anywhere, because rhythm is what read as dancing. Everything else is vanilla's.

    The `torso` channel is present but flat zero. That is not posing: it is the resource validator,
    which requires a BendyLib `bend` key somewhere in every clip, and the head has no bend state.

    Loops, because the charge is held until the key is released. The twitch has no net drift, so
    the loop point is seamless wherever it falls."""
    flat = part()
    moves = [
        frame(0, "outquad", torso=flat, head=head_part()),
        # the head goes back fast, as if something pulled it
        frame(5, "outexpo", torso=flat, head=head_part(-58, 0, 0)),
    ]
    tick, end, step = 8, 64, 2
    while tick <= end:
        j1 = math.sin(tick * 2.90)
        j2 = math.sin(tick * 4.70 + 1.3)
        j3 = math.sin(tick * 7.30 + 0.4)
        jitter = j1 * .50 + j2 * .33 + j3 * .17
        # A sharp spike rather than an oscillation: mostly nothing, then it seizes.
        spasm = max(0.0, math.sin(tick * .91 + .7)) ** 6
        moves.append(frame(tick, "linear",
                           torso=flat,
                           head=head_part(-58 + jitter * 4.0 - spasm * 11.0,
                                          j2 * 5.0 + jitter * 2.0,
                                          j3 * 4.0 + spasm * 5.0)))
        tick += step
    clip_data = clip("conqueror_gear5_charge",
                     "Conqueror's charge: head thrown back at the sky, twitching on three "
                     "incommensurate frequencies with occasional spasms. Exactly one bone is "
                     "animated; the rest of the body is vanilla's.",
                     66, moves)
    clip_data["emote"]["isLoop"] = True
    clip_data["emote"]["returnTick"] = 8
    return clip_data


def gear5_release():
    """Out of the charge: the head snaps down out of the sky and settles.

    Head-only, for the same reason the charge is: any bone this clip writes is a bone vanilla stops
    handling, in every situation the player might be in when they let go of the key."""
    flat = part()
    moves = [
        frame(0, "inquad", torso=flat, head=head_part(-58, 0, 0)),
        # one last pull further back
        frame(4, "inquad", torso=flat, head=head_part(-74, 0, 0)),
        # it breaks and the head is thrown forward
        frame(10, "outexpo", torso=flat, head=head_part(28, 0, 0)),
    ]
    tick, end, step = 14, 30, 4
    while tick <= end:
        t = (tick - 14) / float(end - 14)
        amp = 1.0 - .65 * t
        w = math.sin(tick * 1.7)
        moves.append(frame(tick, "inoutsine",
                           torso=flat,
                           head=head_part(28 + w * 5.0 * amp, w * 2.5 * amp, w * 2.0 * amp)))
        tick += step
    moves.append(frame(38, "inoutquad", torso=flat, head=head_part(10, 0, 0)))
    moves.append(frame(48, "outquad", torso=flat, head=head_part()))
    return clip("conqueror_gear5_release",
                "Release out of the charge: the head is pulled back one last time, then snaps "
                "forward and settles. Head-only; the rest of the body stays vanilla's.",
                48, moves)


def tap_point():
    """Tapped Conqueror's: a bare arm point.

    No legs, no torso lean, no head move -- the brief is a default stance with one arm. Keeping
    the lower body completely still is what distinguishes a tap from a committed release."""
    moves = [
        frame(0, "outquad", rightArm=part(), leftArm=part()),
        # arm comes up and points forward
        frame(3, "outexpo", rightArm=part(-96, -4, 3, -6), leftArm=part(4, 2, -2, -2)),
        frame(7, "outquad", rightArm=part(-102, -2, 1, -2), leftArm=part(5, 2, -2, -2)),
        # small settle on the point
        frame(13, "inoutsine", rightArm=part(-99, -3, 2, -4), leftArm=part(4, 2, -2, -2)),
        frame(22, "inoutquad", rightArm=part(-92, -4, 3, -6), leftArm=part(3, 1, -1, -1)),
        frame(32, "outquad", rightArm=part(), leftArm=part()),
    ]
    return clip("conqueror_tap_point",
                "Tapped Conqueror's: a bare forward arm point with the legs, torso and head left "
                "at default, so a tap never reads as a committed release.",
                32, moves)


def last_stand():
    """Last Stand: the 14-second death-refusal.

    Beat sheet, matching HakiEvents.LAST_STAND_DURATION_TICKS (280):
      0-60    the legs give out and the user sinks to their knees;
      60-115  they raise their hands and stare down at them -- the moment of realising the body
              is finished;
      115-165 the hands lower and hang;
      165-235 the head tilts back and they stare up at the sky, chest opening as the will builds;
      235-280 held, trembling harder and harder, right up to the detonation.

    Kneeling is built by folding the shins back under the thighs with the bend channel and
    dropping the body, which is the only way to get a real kneel out of a box model."""
    moves = [
        frame(0, "inoutquad",
              body=body(), torso=part(), head=head_part(),
              rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part()),
        # legs buckle
        frame(22, "inquad",
              body=body(y=-0.18, pitch=12), torso=part(15, 0, 2, 12), head=head_part(16),
              rightArm=part(9, -5, 5, -10), leftArm=part(9, 5, -5, -10),
              rightLeg=part(-34, -6, 6, 54), leftLeg=part(-30, 6, -6, 50)),
        # down onto both knees
        frame(46, "inoutquad",
              body=body(y=-0.48, pitch=16), torso=part(19, 0, 3, 15), head=head_part(24),
              rightArm=part(13, -7, 7, -14), leftArm=part(13, 7, -7, -14),
              rightLeg=part(-86, -8, 8, 104), leftLeg=part(-82, 8, -8, 100)),
        frame(60, "outquad",
              body=body(y=-0.58, pitch=14), torso=part(17, 0, 2, 14), head=head_part(28),
              rightArm=part(11, -6, 6, -12), leftArm=part(11, 6, -6, -12),
              rightLeg=part(-92, -8, 8, 112), leftLeg=part(-90, 8, -8, 110)),
        # hands come up; the user stares down at them
        frame(86, "inoutsine",
              body=body(y=-0.58, pitch=13), torso=part(16, 0, 2, 13), head=head_part(38),
              rightArm=part(-58, 14, 18, -44), leftArm=part(-58, -14, -18, -44),
              rightLeg=part(-92, -8, 8, 112), leftLeg=part(-90, 8, -8, 110)),
        frame(115, "inoutsine",
              body=body(y=-0.585, pitch=13), torso=part(16, 0, 2, 13), head=head_part(42),
              rightArm=part(-62, 16, 20, -48), leftArm=part(-62, -16, -20, -48),
              rightLeg=part(-92, -8, 8, 112), leftLeg=part(-90, 8, -8, 110)),
        # hands lower and hang
        frame(150, "inoutquad",
              body=body(y=-0.58, pitch=10), torso=part(12, 0, 1, 10), head=head_part(26),
              rightArm=part(6, -4, 5, -8), leftArm=part(6, 4, -5, -8),
              rightLeg=part(-92, -8, 8, 112), leftLeg=part(-90, 8, -8, 110)),
        frame(165, "inoutquad",
              body=body(y=-0.58, pitch=6), torso=part(7, 0, 1, 6), head=head_part(12),
              rightArm=part(4, -3, 4, -6), leftArm=part(4, 3, -4, -6),
              rightLeg=part(-92, -8, 8, 112), leftLeg=part(-90, 8, -8, 110)),
        # head goes back; chest opens to the sky
        frame(205, "inoutsine",
              body=body(y=-0.56, pitch=-8), torso=part(-13, 0, 0, -10), head=head_part(-40),
              rightArm=part(-6, -10, 10, 6), leftArm=part(-6, 10, -10, 6),
              rightLeg=part(-92, -8, 8, 112), leftLeg=part(-90, 8, -8, 110)),
        frame(235, "inoutsine",
              body=body(y=-0.54, pitch=-14), torso=part(-20, 0, 0, -16), head=head_part(-56),
              rightArm=part(-14, -18, 16, 14), leftArm=part(-14, 18, -16, 14),
              rightLeg=part(-92, -8, 8, 112), leftLeg=part(-90, 8, -8, 110)),
    ]
    # hold, trembling harder as the detonation approaches
    tick, end, step = 240, 280, 4
    while tick <= end:
        t = (tick - 240) / float(end - 240)
        amp = .35 + 1.65 * t            # builds instead of decaying: this one is winding UP
        s1 = math.sin(tick * 1.9)
        s2 = math.sin(tick * 3.1 + .8)
        moves.append(frame(tick, "inoutsine",
                           body=body(y=-0.54 + abs(s2) * .012 * amp, pitch=-14 + s1 * 2.2 * amp,
                                     roll=s2 * 1.8 * amp),
                           torso=part(-20 + s1 * 2.6 * amp, s2 * 2.0 * amp, s2 * 1.6 * amp,
                                      -16 + s1 * 2.2 * amp),
                           head=head_part(-56 + s1 * 3.0 * amp, s2 * 3.0 * amp, s1 * 2.2 * amp),
                           rightArm=part(-14 + s2 * 4.0 * amp, -18, 16, 14 + s1 * 4.0 * amp),
                           leftArm=part(-14 + s2 * 4.0 * amp, 18, -16, 14 + s1 * 4.0 * amp),
                           rightLeg=part(-92, -8, 8, 112), leftLeg=part(-90, 8, -8, 110)))
        tick += step
    return clip("conqueror_last_stand",
                "Last Stand: the legs give out and the user sinks to both knees, raises their "
                "hands and stares down at them, lets them fall, then tilts their head back to "
                "stare at the sky as the will builds -- held on a tremble that grows right up to "
                "the detonation.",
                280, moves)


def _dodge(name, description, peak, settle=(), end=20):
    """Common shape for a reaction dodge: snap into the slip, hold a beat, recover.

    Dodges must land inside the projectile's flight time, so the attack is deliberately violent
    (outexpo over 3 ticks) and the recovery is what takes the remaining time. Anything slower
    reads as a stumble after the fact rather than as reading the shot."""
    moves = [frame(0, "outexpo", body=body(), torso=part(), head=head_part(),
                   rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part())]
    moves.append(frame(3, "outexpo", **peak))
    hold = dict(peak)
    for tick, scale in settle:
        eased = {}
        for bone, channels in hold.items():
            eased[bone] = {k: round(v * scale, 2) for k, v in channels.items()}
        moves.append(frame(tick, "inoutsine", **eased))
    moves.append(frame(end, "inoutquad", body=body(), torso=part(), head=head_part(),
                       rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part()))
    return clip(name, description, end, moves)


def dodge_left():
    return _dodge("observation_dodge_left",
                  "Observation slip to the left: the hips lead, the torso folds away from the "
                  "shot and the head turns back toward where it came from, with a crossing step "
                  "underneath.",
                  dict(body=body(x=-0.22, y=-0.05, roll=-19, yaw=7),
                       torso=part(4, 13, -21, 7),
                       head=head_part(-4, 26, -11),
                       rightArm=part(-24, 16, 34, -30),
                       leftArm=part(16, -12, -40, -22),
                       rightLeg=part(-16, 10, -14, 30),
                       leftLeg=part(14, -8, -20, 24)),
                  settle=((8, .62), (13, .28)))


def dodge_right():
    return _dodge("observation_dodge_right",
                  "Observation slip to the right: mirror of the left slip, hips leading and the "
                  "head tracking back toward the shot.",
                  dict(body=body(x=0.22, y=-0.05, roll=19, yaw=-7),
                       torso=part(4, -13, 21, 7),
                       head=head_part(-4, -26, 11),
                       rightArm=part(16, 12, 40, -22),
                       leftArm=part(-24, -16, -34, -30),
                       rightLeg=part(14, 8, 20, 24),
                       leftLeg=part(-16, -10, 14, 30)),
                  settle=((8, .62), (13, .28)))


def dodge_back():
    """Backward lean-away for the case where there is no room to either side."""
    return _dodge("observation_dodge_back",
                  "Observation lean-away: taken when both flanks are blocked. The spine arches "
                  "hard backwards, the head goes with it and both arms fly forward as "
                  "counterweight while the knees drop.",
                  dict(body=body(y=-0.16, z=-0.20, pitch=-34),
                       torso=part(-40, 0, 0, -32),
                       head=head_part(-44, 0, 0),
                       rightArm=part(-72, -14, 22, -30),
                       leftArm=part(-72, 14, -22, -30),
                       rightLeg=part(-26, -6, 8, 42),
                       leftLeg=part(-22, 6, -8, 38)),
                  settle=((9, .60), (14, .26)), end=22)


def dodge_duck():
    """Drop under a high shot rather than stepping around it."""
    return _dodge("observation_dodge_duck",
                  "Observation duck: used when the predicted impact is above shoulder height. "
                  "The whole body drops under the line, head tucked, arms low and wide for "
                  "balance, knees deeply folded.",
                  dict(body=body(y=-0.46, pitch=24),
                       torso=part(30, 0, 0, 24),
                       head=head_part(30, 0, 0),
                       rightArm=part(28, -22, 30, -18),
                       leftArm=part(28, 22, -30, -18),
                       rightLeg=part(-52, -10, 10, 78),
                       leftLeg=part(-48, 10, -10, 74)),
                  settle=((9, .58), (14, .24)), end=22)


def arrow_catch():
    """Plucking an arrow out of the air, looking at it, then firing it back.

    Four beats. The snatch has to be faster than the shot, so the hand is out and closed inside
    five ticks. Then it slows right down: the arm stays where it caught the shot, out at length and
    a little further from the body, and the head turns to read it for a full second -- that beat is
    the whole point of the move, and rushing it made the catch read as a deflection. Hauling the
    arrow back to the face instead put the shaft inside the head and hid the catch behind the
    player's own arm. Only then does the arm draw back past the ear and the body uncoil into an
    overhand throw."""
    moves = [
        frame(0, "outexpo",
              body=body(), torso=part(), head=head_part(),
              rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part()),
        # Snatch: the right hand fires out to meet the shaft. Straight out along the facing, with
        # no yaw swing -- the arrow is pinned to a computed hand position, and every degree of yaw
        # or elbow bend in the pose moves the real hand away from that computation. A straight arm
        # is the one pose whose hand position is exactly known: pivot + limb length, dead ahead.
        frame(3, "outexpo",
              body=body(x=0.06, pitch=4, yaw=-4), torso=part(5, -6, 3, 4),
              head=head_part(2, -12, 2),
              rightArm=part(-104, 0, 4, -10), leftArm=part(14, 10, -12, -10),
              rightLeg=part(-6, -4, 4, 10), leftLeg=part(6, 4, -4, 8)),
        # closed on it, arm settling straight
        frame(7, "outquad",
              body=body(x=0.03, pitch=0, yaw=-2), torso=part(-1, -3, 2, -1),
              head=head_part(-3, -10, 2),
              rightArm=part(-96, 0, 4, -8), leftArm=part(10, 8, -10, -12),
              rightLeg=part(-5, -3, 3, 9), leftLeg=part(5, 3, -3, 8)),
        # Held out at arm's length, arm straight, head turned along it. The head goes to the hand
        # rather than the hand being hauled back to the head: pulling it in put the shaft inside
        # the face and covered the catch with the player's own arm.
        frame(11, "outcubic",
              body=body(pitch=-2, yaw=-3), torso=part(-3, -4, 2, -2),
              head=head_part(-5, -16, 4),
              rightArm=part(-92, 0, 4, -6), leftArm=part(6, 6, -8, -10),
              rightLeg=part(-3, -2, 2, 7), leftLeg=part(3, 2, -2, 7)),
    ]
    # The look. Twenty ticks of the head tracking down the shaft -- small amplitudes on purpose,
    # because the stillness is what sells the beat, and because the arm has to stay where the
    # server thinks it is.
    tick, end = 15, 25
    while tick <= end:
        t = (tick - 15) / float(end - 15)
        turn = math.sin(tick * .58)
        moves.append(frame(tick, "inoutsine",
                           body=body(pitch=-2 + turn * .8, yaw=-3 + turn * 1.2),
                           torso=part(-3 + turn * 1.0, -4 + turn * 1.4, 2, -2),
                           head=head_part(-5 - t * 3.0 + turn * 2.4, -16 - t * 2.0 + turn * 3.0,
                                          4 + turn * 2.0),
                           rightArm=part(-92 + turn * 1.4, turn * .8, 4 + turn * 2.0,
                                         -6 + turn * 1.2),
                           leftArm=part(6, 6, -8, -10),
                           rightLeg=part(-3, -2, 2, 7), leftLeg=part(3, 2, -2, 7)))
        tick += 5
    moves += [
        # Decision made. The throw is a whole-body action, not an arm action: the hips wind away
        # first, the weight goes onto the back leg and the front foot comes off the ground, and
        # the arm ends up cocked past the ear as a consequence of the turn rather than on its own.
        frame(29, "inquad",
              body=body(x=0.04, y=-0.04, z=-0.06, pitch=-11, yaw=30, roll=-5),
              torso=part(-13, 34, -9, -10), head=head_part(-7, 22, -5),
              rightArm=part(-166, 30, 38, -74), leftArm=part(-38, -24, -28, -26),
              rightLeg=part(-20, -8, 7, 26), leftLeg=part(22, 10, -7, 20)),
        # Hips and front foot fire; the arm is still behind them. That lag is the power.
        frame(32, "inexpo",
              body=body(x=0.02, y=-0.02, z=0.06, pitch=2, yaw=-6, roll=-2),
              torso=part(4, -8, -2, 4), head=head_part(0, -4, -1),
              rightArm=part(-150, 12, 34, -58), leftArm=part(-10, -6, -14, -18),
              rightLeg=part(6, -6, 5, 20), leftLeg=part(-4, 8, -5, 14)),
        # Release: everything is aligned behind the hand and the front leg is planted hard. The
        # projectile leaves on this exact tick -- ARROW_CATCH_HOLD_TICKS is cut to it.
        frame(34, "outexpo",
              body=body(x=-0.05, z=0.14, pitch=22, yaw=-26, roll=3),
              torso=part(28, -34, 8, 22), head=head_part(14, -18, 5),
              rightArm=part(-52, -34, 28, 26), leftArm=part(30, 26, -30, 16),
              rightLeg=part(20, -8, 7, 26), leftLeg=part(-18, 8, -7, 22)),
        # Follow-through: the arm keeps going down and across the body, the shoulder finishes
        # square to where the shot went. Stopping the hand at the release is what made it look
        # like the arrow was handed over rather than thrown.
        frame(37, "outquad",
              body=body(x=-0.03, z=0.06, pitch=26, yaw=-34, roll=4),
              torso=part(31, -44, 10, 24), head=head_part(15, -22, 5),
              rightArm=part(34, -46, 40, 18), leftArm=part(38, 30, -34, 12),
              rightLeg=part(26, -9, 7, 30), leftLeg=part(-22, 9, -7, 26)),
        frame(43, "inoutsine",
              body=body(pitch=9, yaw=-12), torso=part(11, -16, 3, 9), head=head_part(6, -8, 2),
              rightArm=part(14, -18, 16, 6), leftArm=part(14, 11, -12, 4),
              rightLeg=part(9, -3, 3, 11), leftLeg=part(-8, 3, -3, 9)),
        frame(50, "outquad",
              body=body(), torso=part(), head=head_part(),
              rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part()),
    ]
    return clip("observation_arrow_catch",
                "Snatches an incoming arrow out of the air and holds it out at arm's length, "
                "turning the head to read it for a full second, then turns the hips away and "
                "throws it with the whole body, following the hand down and across.",
                50, moves)


def backflip_counter():
    """Backflip away from a read attack, then charge back in behind an extended fist.

    The flip is driven on body pitch, which carries the whole model, with the legs tucked through
    the rotation and the arms thrown up to start it. The full 360 is spent in the first thirteen
    ticks at a steady ~28 degrees a tick, so the rotation visibly completes rather than easing out
    somewhere near the top; the remaining airtime is spent upright with the legs reaching for the
    ground. The server launch (0.66 up, 1.05 back) gives about sixteen ticks of air, which is what
    the landing frame is cut to.

    Then the counter: land into a deep crouch, cock the fist past the hip, and drive back in as a
    long low lunge -- front knee folded under the chest, trailing leg stretched straight out
    behind, chest dropped toward the target and the free arm counterweighting the punch."""
    moves = [
        frame(0, "outexpo",
              body=body(), torso=part(), head=head_part(),
              rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part()),
        # explosive launch: arms thrown up to drive the rotation, legs snapping into the tuck
        frame(2, "outexpo",
              body=body(y=0.30, z=-0.30, pitch=-70), torso=part(-18, 0, 0, -16),
              head=head_part(-32),
              rightArm=part(-134, -18, 20, -22), leftArm=part(-134, 18, -20, -22),
              rightLeg=part(-68, -6, 6, 84), leftLeg=part(-62, 6, -6, 80)),
        # through the top, fully tucked
        frame(5, "linear",
              body=body(y=0.74, z=-0.60, pitch=-160), torso=part(-26, 0, 0, -22),
              head=head_part(-36),
              rightArm=part(-100, -22, 26, -44), leftArm=part(-100, 22, -26, -44),
              rightLeg=part(-106, -8, 8, 124), leftLeg=part(-102, 8, -8, 120)),
        frame(8, "linear",
              body=body(y=0.92, z=-0.92, pitch=-250), torso=part(-24, 0, 0, -20),
              head=head_part(-30),
              rightArm=part(-92, -20, 24, -40), leftArm=part(-92, 20, -24, -40),
              rightLeg=part(-100, -8, 8, 120), leftLeg=part(-96, 8, -8, 116)),
        frame(11, "linear",
              body=body(y=0.68, z=-1.18, pitch=-330), torso=part(-14, 0, 0, -12),
              head=head_part(-16),
              rightArm=part(-60, -16, 18, -26), leftArm=part(-60, 16, -18, -26),
              rightLeg=part(-70, -6, 6, 88), leftLeg=part(-66, 6, -6, 84)),
        # rotation complete and still airborne: upright, legs reaching for the ground
        frame(13, "outquad",
              body=body(y=0.44, z=-1.30, pitch=-360), torso=part(-4, 0, 0, -3),
              head=head_part(-4),
              rightArm=part(-30, -12, 14, -18), leftArm=part(-30, 12, -14, -18),
              rightLeg=part(-22, -6, 6, 34), leftLeg=part(-18, 6, -6, 30)),
        # land, absorbing all of it into a deep crouch
        frame(16, "outquad",
              body=body(y=-0.26, z=-1.36, pitch=-360), torso=part(24, 0, 0, 19),
              head=head_part(16),
              rightArm=part(26, -20, 22, -14), leftArm=part(26, 20, -22, -14),
              rightLeg=part(-46, -8, 9, 70), leftLeg=part(-42, 8, -9, 66)),
        # cock the fist back past the hip, shoulder turned away, weight onto the back leg
        frame(19, "inquad",
              body=body(y=-0.20, z=-1.26, pitch=-350, yaw=-16), torso=part(18, 26, -6, 14),
              head=head_part(2, 20, 0),
              rightArm=part(46, 32, -26, -90), leftArm=part(-32, -22, 22, -24),
              rightLeg=part(-30, -6, 7, 50), leftLeg=part(-24, 6, -7, 44)),
        # The charge itself: a long, low lunge. The front knee drives up and folds under the body
        # while the trailing leg stretches out straight behind, the chest drops toward the target,
        # the punching arm is out at full length and the free arm counterweights it straight back.
        # This is the shape of a committed lunge punch -- an upright dash with a fist held out is
        # what made the counter look like it was jogging into contact.
        frame(22, "outexpo",
              body=body(y=-0.34, z=-0.70, pitch=-334, yaw=6), torso=part(15, -22, 6, 13),
              head=head_part(-20, -14, -3),
              rightArm=part(-108, -8, 6, -2), leftArm=part(62, 18, -22, 8),
              rightLeg=part(68, -6, 6, 78), leftLeg=part(-54, 6, -6, 4)),
        frame(25, "linear",
              body=body(y=-0.36, z=-0.28, pitch=-332, yaw=8), torso=part(17, -25, 7, 14),
              head=head_part(-21, -15, -3),
              rightArm=part(-110, -9, 6, 0), leftArm=part(65, 19, -23, 8),
              rightLeg=part(70, -7, 6, 80), leftLeg=part(-57, 7, -6, 3)),
        # contact: the fist jams back into the shoulder, the chest compresses over the lead knee
        # and the trailing leg kicks up behind from the stop
        frame(28, "outexpo",
              body=body(y=-0.30, z=0.12, pitch=-328, yaw=11), torso=part(22, -30, 8, 18),
              head=head_part(-14, -17, -4),
              rightArm=part(-98, -13, 10, -18), leftArm=part(70, 22, -26, 12),
              rightLeg=part(74, -8, 7, 86), leftLeg=part(-64, 8, -7, 10)),
        # follow-through: the lunge unwinds and the back foot comes under the body
        frame(32, "outquad",
              body=body(y=-0.18, z=0.04, pitch=-340, yaw=5), torso=part(12, -16, 4, 10),
              head=head_part(-8, -9, -2),
              rightArm=part(-66, -10, 8, -28), leftArm=part(38, 14, -16, -6),
              rightLeg=part(40, -6, 5, 48), leftLeg=part(-32, 6, -5, 16)),
        frame(39, "inoutsine",
              body=body(y=-0.05, pitch=-354), torso=part(4, -5, 1, 3), head=head_part(-2, -3, 0),
              rightArm=part(-22, -4, 3, -12), leftArm=part(12, 5, -6, -4),
              rightLeg=part(12, -2, 2, 14), leftLeg=part(-10, 2, -2, 6)),
        frame(46, "outquad",
              body=body(pitch=-360), torso=part(), head=head_part(),
              rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part()),
    ]
    return clip("observation_backflip_counter",
                "A fast full-rotation backflip that carries a long way back from a read attack, "
                "landing into a deep crouch, cocking the fist past the hip and driving back in as "
                "a long low lunge punch -- front knee folded under the chest, trailing leg "
                "stretched out behind, and the free arm counterweighting the strike straight back.",
                46, moves)


# Haki Grip beat sheet, in ticks from the start of the sequence. These numbers are mirrored
# exactly in HakiServerController.THRAGG_BEATS -- the clips are cut to the server's schedule, not
# the other way round, so any change here has to be made in both places.
#   uppercut  the fist lands and the target is launched straight up
#   ascend    the attacker appears above the rising target
#   slam      both arms come down on the back of their head
#   ground    the target hits the floor
THRAGG_TIERS = (
    #  suffix       tier  uppercut ascend slam ground duration
    ("none",         0,      36,     44,   86,   104,    134),
    ("haki",         1,      44,     52,  100,   120,    150),
    ("advanced",     2,      52,     60,  116,   140,    170),
)


def _thragg_attacker(clip_name, tier, uppercut, ascend, slam, ground, duration):
    """Haki Grip, attacker side.

    Five beats, and each one has to be legible on its own because the camera cuts between them:

    1. **Draw.** The fist chambers low and behind while the free hand reaches out and hauls -- the
       server is dragging the target in on a wind, and the pulling hand has to look like the reason.
    2. **Uppercut.** The whole body extends through the fist: heels off the floor, hips through,
       head following the punch up. This is the only frame the attacker is at full stretch.
    3. **Ascend.** They are above the target an instant later, so the pose compresses into a tuck
       and opens straight into the raise -- a blink reads as teleporting only if the pose changes
       across it.
    4. **Raise.** Both arms overhead, hands together, back arched, held just long enough to see it.
    5. **Slam.** Both arms driven straight down with the body folding after them.

    @param tier 0 physical, 1 Armament, 2 Advanced -- deeper draw and a harder slam
    """
    amp = 1.0 + .18 * tier
    frames = {}

    def put(tick, easing, **bones):
        frames[max(0, int(tick))] = frame(max(0, int(tick)), easing, **bones)

    # --- 1. draw: fist chambers low and back, free hand hauls them in -------------------------
    put(0, "outquad",
        body=body(pitch=2), torso=part(3, 0, -1, 2), head=head_part(-2),
        rightArm=part(14, -8, 10, -18), leftArm=part(-30, 12, -14, -26),
        rightLeg=part(4, -4, 4, 12), leftLeg=part(-4, 4, -4, 12))
    put(6, "outexpo",
        body=body(y=-0.07, z=-0.05, pitch=-6, yaw=-18), torso=part(-8, -26, 7, -7),
        head=head_part(-6, 22, -2),
        rightArm=part(38, -26, 22, -56), leftArm=part(-76, 20, -18, -44),
        rightLeg=part(-10, -8, 6, 26), leftLeg=part(12, 8, -6, 22))
    # the draw holds and tightens; the pulling hand claws on its own faster beat
    tick = 11
    while tick <= uppercut - 5:
        t = (tick - 11) / float(max(1, uppercut - 16))
        haul = math.sin(tick * .92)
        strain = math.sin(tick * 1.7) * (.4 + .6 * t) * amp
        put(tick, "inoutsine",
            body=body(y=-0.07 - t * .03, z=-0.05, pitch=-6 - t * 3.0,
                      yaw=-18 - t * 8.0, roll=strain * 1.2),
            torso=part(-8 - t * 5.0, -26 - t * 10.0, 7 + strain * 1.5, -7 - t * 3.0),
            head=head_part(-6 + strain * 2.0, 22 + t * 6.0, -2),
            rightArm=part(38 + t * 12.0 + strain * 3.0, -26 - t * 4.0, 22 + t * 5.0,
                          -56 - t * 22.0 * amp),
            leftArm=part(-76 + haul * 11.0, 20 + haul * 5.0, -18 - haul * 6.0,
                         -44 - abs(haul) * 20.0),
            rightLeg=part(-10 - t * 6.0, -8, 6, 26 + t * 10.0),
            leftLeg=part(12 + t * 5.0, 8, -6, 22 + t * 8.0))
        tick += 5
    # anticipation: everything drops a touch further before it fires
    put(uppercut - 2, "inquad",
        body=body(y=-0.13, z=-0.05, pitch=-11, yaw=-28), torso=part(-15, -38, 9, -11),
        head=head_part(-3, 30, -2),
        rightArm=part(54, -32, 28, -84 * amp), leftArm=part(-64, 24, -22, -60),
        rightLeg=part(-18, -9, 7, 40), leftLeg=part(19, 9, -7, 32))

    # --- 2. the uppercut: the whole body extends through the fist ------------------------------
    put(uppercut, "outexpo",
        body=body(y=0.16, z=0.10, pitch=-16, yaw=10), torso=part(-22, 16, -4, -18),
        head=head_part(-42, -6, 1),
        rightArm=part(-172, -4, 8, -2), leftArm=part(-44, -18, 20, -22),
        rightLeg=part(-26, -5, 4, 16), leftLeg=part(24, 5, -4, 18))
    put(uppercut + 3, "outquad",
        body=body(y=0.10, z=0.06, pitch=-13, yaw=8), torso=part(-18, 13, -3, -15),
        head=head_part(-38, -5, 1),
        rightArm=part(-168, -3, 7, -6), leftArm=part(-38, -15, 17, -20),
        rightLeg=part(-20, -4, 3, 14), leftLeg=part(19, 4, -3, 15))
    # --- 3. the blink: compress on the ground, open out above them -----------------------------
    put(ascend - 2, "inexpo",
        body=body(y=-0.16, pitch=6), torso=part(9, 4, -1, 8), head=head_part(-30, -2, 0),
        rightArm=part(-120, -10, 12, -54), leftArm=part(-112, 10, -12, -50),
        rightLeg=part(-34, -6, 6, 56), leftLeg=part(-30, 6, -6, 52))
    put(ascend, "outexpo",
        body=body(y=0.06, pitch=18), torso=part(14, 0, 0, 12), head=head_part(26),
        rightArm=part(-58, -14, 16, -66), leftArm=part(-58, 14, -16, -66),
        rightLeg=part(-44, -7, 7, 68), leftLeg=part(-40, 7, -7, 64))

    # --- 4. the raise: both arms overhead, hands together, back arched ------------------------
    put(ascend + 5, "outquad",
        body=body(y=0.02, pitch=-10), torso=part(-13, 0, 0, -11), head=head_part(24),
        rightArm=part(-166, -6, 7, -10), leftArm=part(-166, 6, -7, -10),
        rightLeg=part(-22, -6, 6, 40), leftLeg=part(-18, 6, -6, 36))
    put(slam - 3, "inquad",
        body=body(y=0.05, pitch=-19), torso=part(-22, 0, 0, -18), head=head_part(30),
        rightArm=part(-182 * amp, -8, 9, -6), leftArm=part(-182 * amp, 8, -9, -6),
        rightLeg=part(-16, -5, 5, 32), leftLeg=part(-13, 5, -5, 28))

    # --- 5. the slam: both arms driven straight down, body folding after them ------------------
    put(slam, "outexpo",
        body=body(y=-0.06, pitch=34), torso=part(38, 0, 0, 30), head=head_part(26),
        rightArm=part(-14, -6, 7, 4), leftArm=part(-14, 6, -7, 4),
        rightLeg=part(16, -5, 5, 26), leftLeg=part(14, 5, -5, 24))
    put(slam + 3, "linear",
        body=body(y=-0.09, pitch=38), torso=part(42, 0, 0, 33), head=head_part(28),
        rightArm=part(6, -5, 6, 8), leftArm=part(6, 5, -6, 8),
        rightLeg=part(20, -5, 5, 30), leftLeg=part(18, 5, -5, 28))
    # riding it down after them
    put(ground - 4, "inoutsine",
        body=body(y=-0.05, pitch=30), torso=part(34, 0, 0, 26), head=head_part(22),
        rightArm=part(14, -6, 7, -4), leftArm=part(14, 6, -7, -4),
        rightLeg=part(14, -4, 4, 24), leftLeg=part(12, 4, -4, 22))
    # landing next to the crater, absorbing into a crouch
    put(ground, "outexpo",
        body=body(y=-0.24, pitch=18), torso=part(22, 0, 0, 18), head=head_part(10),
        rightArm=part(24, -18, 20, -16), leftArm=part(24, 18, -20, -16),
        rightLeg=part(-40, -8, 9, 64), leftLeg=part(-36, 8, -9, 60))
    put(ground + 10, "inoutquad",
        body=body(y=-0.08, pitch=7), torso=part(9, 0, 0, 7), head=head_part(4),
        rightArm=part(11, -8, 9, -8), leftArm=part(11, 8, -9, -8),
        rightLeg=part(-14, -3, 4, 22), leftLeg=part(-12, 3, -4, 20))
    put(duration, "outquad",
        body=body(), torso=part(), head=head_part(),
        rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part())

    moves = [frames[t] for t in sorted(frames)]
    return clip(clip_name,
                "Haki Grip, attacker: the fist chambers low while the free hand hauls the "
                "target in on the wind, the whole body extends through an uppercut, then a blink "
                "above them, both arms raised overhead and driven straight down.",
                duration, moves)


def _thragg_victim(clip_name, tier, uppercut, ascend, slam, ground, duration):
    """Haki Grip, victim side: dragged in, launched, hammered back down, sprawled."""
    amp = 1.0 + .16 * tier
    frames = {}

    def put(tick, easing, **bones):
        frames[max(0, int(tick))] = frame(max(0, int(tick)), easing, **bones)

    put(0, "outquad",
        body=body(), torso=part(), head=head_part(),
        rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part())
    # hauled off balance: the chest goes first and everything else trails
    put(5, "outexpo",
        body=body(y=-0.04, pitch=16), torso=part(20, 0, 0, 16), head=head_part(-10),
        rightArm=part(34, -16, 20, -20), leftArm=part(34, 16, -20, -20),
        rightLeg=part(-26, -6, 6, 34), leftLeg=part(-20, 6, -6, 30))
    # dragged, feet scraping, trying to plant and failing
    tick = 11
    while tick <= uppercut - 4:
        t = (tick - 11) / float(max(1, uppercut - 15))
        scrape = math.sin(tick * 1.05)
        put(tick, "inoutsine",
            body=body(y=-0.04, pitch=16 + t * 6.0 + scrape * 3.0, roll=scrape * 4.0),
            torso=part(20 + t * 5.0, scrape * 7.0, scrape * 3.0, 16 + t * 4.0),
            head=head_part(-10 - t * 4.0, scrape * 8.0, scrape * 3.0),
            rightArm=part(34 + scrape * 14.0, -16, 20, -20 - abs(scrape) * 12.0),
            leftArm=part(34 - scrape * 14.0, 16, -20, -20 - abs(scrape) * 12.0),
            rightLeg=part(-26 - scrape * 16.0, -6, 6, 34 + scrape * 10.0),
            leftLeg=part(-20 + scrape * 16.0, 6, -6, 30 - scrape * 10.0))
        tick += 5

    # struck: the head snaps back, the spine arches, everything is thrown downward as they rise
    put(uppercut, "outexpo",
        body=body(y=0.06, pitch=-44 * amp), torso=part(-46 * amp, 0, 0, -34),
        head=head_part(-52, 0, 4),
        rightArm=part(52, -30, 34, -14), leftArm=part(52, 30, -34, -14),
        rightLeg=part(46, -8, 8, 20), leftLeg=part(42, 8, -8, 18))
    # Rising limp. The flight is long enough now that two keys stretched across it would read as
    # a mannequin on a wire, so it is sampled: a slow tumble with the limbs trailing and swinging.
    tick = uppercut + 7
    while tick <= slam - 5:
        t = (tick - uppercut - 7) / float(max(1, slam - uppercut - 12))
        swing = math.sin(tick * .38)
        turn = math.sin(tick * .21)
        put(tick, "inoutsine",
            body=body(y=0.04 - t * .04, pitch=-30 + t * 14.0 + swing * 5.0,
                      yaw=turn * 14.0, roll=turn * 9.0),
            torso=part(-30 + t * 12.0 + swing * 4.0, turn * 10.0, 4.0 + turn * 4.0,
                       -22 + t * 8.0),
            head=head_part(-34 + t * 12.0 + swing * 5.0, -turn * 12.0, 3 - turn * 5.0),
            rightArm=part(64 - swing * 8.0, -22 - turn * 6.0, 26, -30 - abs(swing) * 12.0),
            leftArm=part(66 + swing * 8.0, 22 + turn * 6.0, -26, -32 - abs(swing) * 12.0),
            rightLeg=part(34 - t * 10.0 + swing * 7.0, -6, 6, 30 + t * 6.0),
            leftLeg=part(30 - t * 9.0 - swing * 7.0, 6, -6, 28 + t * 6.0))
        tick += 8

    # hammered: the head is driven down and the body folds around it
    put(slam, "outexpo",
        body=body(y=-0.06, pitch=62 * amp), torso=part(56 * amp, 0, 0, 42),
        head=head_part(44, 0, -5),
        rightArm=part(-38, -26, 30, -34), leftArm=part(-38, 26, -30, -34),
        rightLeg=part(-40, -7, 7, 46), leftLeg=part(-36, 7, -7, 44))
    # head-first plummet, arms trailing up behind
    put(slam + 5, "linear",
        body=body(y=-0.04, pitch=74 * amp), torso=part(48, 0, 0, 36), head=head_part(34),
        rightArm=part(-72, -20, 26, -18), leftArm=part(-72, 20, -26, -18),
        rightLeg=part(-52, -6, 6, 30), leftLeg=part(-48, 6, -6, 28))
    # sprawled flat on the floor
    put(ground, "outexpo",
        body=body(y=-0.30, pitch=86), torso=part(24, 0, 0, 16), head=head_part(14),
        rightArm=part(-84, -34, 38, -10), leftArm=part(-84, 34, -38, -10),
        rightLeg=part(-16, -9, 9, 20), leftLeg=part(-12, 9, -9, 18))
    put(ground + 12, "inoutquad",
        body=body(y=-0.20, pitch=62), torso=part(16, 0, 0, 10), head=head_part(9),
        rightArm=part(-56, -24, 27, -14), leftArm=part(-56, 24, -27, -14),
        rightLeg=part(-11, -6, 6, 15), leftLeg=part(-8, 6, -6, 13))
    put(duration, "outquad",
        body=body(), torso=part(), head=head_part(),
        rightArm=part(), leftArm=part(), rightLeg=part(), leftLeg=part())

    moves = [frames[t] for t in sorted(frames)]
    return clip(clip_name,
                "Haki Grip, victim: hauled in off balance, launched by the uppercut, rising "
                "limp, then hammered head-first back into the floor.",
                duration, moves)


def thragg_clips():
    for suffix, tier, uppercut, ascend, slam, ground, duration in THRAGG_TIERS:
        yield _thragg_attacker("thragg_attacker_" + suffix, tier, uppercut, ascend, slam,
                               ground, duration)
        yield _thragg_victim("thragg_victim_" + suffix, tier, uppercut, ascend, slam,
                             ground, duration)


if __name__ == "__main__":
    target = os.path.normpath(OUT)
    os.makedirs(target, exist_ok=True)
    print("Writing Haki animations to %s" % target)
    produced = [conqueror_scream, pain_clutch, pain_convulse,
                gear5_charge, gear5_release, tap_point, last_stand,
                dodge_left, dodge_right, dodge_back, dodge_duck,
                arrow_catch, backflip_counter]
    for data in [p() for p in produced] + list(thragg_clips()):
        path = os.path.join(target, data["name"] + ".json")
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2)
            fh.write("\n")
        ticks = [m["tick"] for m in data["emote"]["moves"]]
        assert ticks == sorted(ticks) and len(set(ticks)) == len(ticks), data["name"]
        assert ticks[-1] <= data["emote"]["endTick"], data["name"]
        print("  %-28s %2d keyframes, %d ticks" % (data["name"] + ".json", len(ticks),
                                                   data["emote"]["endTick"]))
