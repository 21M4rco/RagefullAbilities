#!/usr/bin/env python3
"""
Schema check for player_animation clips.

Player Animator resolves each channel to a StateCollection field and calls addKeyFrame on it
without a null check, so naming a channel a part does not have throws an NPE *during resource
load* -- which crashes the client on boot before the main menu, not at the point of use. The head
has no `bend`; only torso, arms and legs do. This guards that.

    python3 tools/check_animations.py
"""
import json, glob, os, sys

ROT = {"pitch", "yaw", "roll"}
OFF = {"x", "y", "z"}
ALLOWED = {
    "body":     OFF | ROT,           # no bend
    "head":     OFF | ROT,           # no bend  <-- boot crash if violated
    "torso":    OFF | ROT | {"bend"},
    "rightArm": OFF | ROT | {"bend"},
    "leftArm":  OFF | ROT | {"bend"},
    "rightLeg": OFF | ROT | {"bend"},
    "leftLeg":  OFF | ROT | {"bend"},
}

def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                        "src", "main", "resources", "assets", "hexhaki", "player_animation")
    bad = 0
    files = sorted(glob.glob(os.path.join(os.path.normpath(root), "*.json")))
    for path in files:
        name = os.path.basename(path)
        text = open(path, encoding="utf-8").read()
        try:
            data = json.loads(text)
        except Exception as exc:
            print(f"  FAIL {name}: invalid JSON: {exc}"); bad += 1; continue
        if '"bend"' not in text:
            print(f"  FAIL {name}: build.gradle requires a bend channel"); bad += 1
        emote = data.get("emote", {})
        moves = emote.get("moves", [])
        ticks = [m["tick"] for m in moves]
        if ticks != sorted(ticks) or len(set(ticks)) != len(ticks):
            print(f"  FAIL {name}: ticks not strictly ascending"); bad += 1
        if ticks and ticks[-1] > emote.get("endTick", 0):
            print(f"  FAIL {name}: tick {ticks[-1]} > endTick {emote.get('endTick')}"); bad += 1
        for move in moves:
            for part, value in move.items():
                if part in ("tick", "easing", "turn") or not isinstance(value, dict):
                    continue
                if part not in ALLOWED:
                    print(f"  FAIL {name} @{move['tick']}: unknown part '{part}'"); bad += 1; continue
                for channel in value:
                    if channel not in ALLOWED[part]:
                        print(f"  FAIL {name} @{move['tick']}: '{part}' has no '{channel}' state "
                              f"-> NPE in AnimationJson.addPartIfExists on boot"); bad += 1
    print(f"  checked {len(files)} clips: {'OK' if not bad else str(bad) + ' PROBLEM(S)'}")
    return 1 if bad else 0

if __name__ == "__main__":
    sys.exit(main())
