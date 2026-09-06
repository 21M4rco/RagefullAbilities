#!/usr/bin/env python3
"""Non-compiling release checks for protocol/resource integrity."""

from __future__ import annotations

import json
import hashlib
import re
import stat
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/hexhaki"
ASSETS = ROOT / "src/main/resources/assets/hexhaki"
failures: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


def read(path: Path) -> str:
    if not path.is_file():
        fail(f"Missing file: {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")


def enum_values(path: Path, enum_name: str) -> set[str]:
    source = read(path)
    match = re.search(rf"enum\s+{re.escape(enum_name)}\s*\{{(.*?)\n\s*\}}", source, re.S)
    if not match:
        fail(f"Could not parse enum {enum_name} in {path.relative_to(ROOT)}")
        return set()
    return set(re.findall(r"\b[A-Z][A-Z0-9_]+\b", re.sub(r"//.*", "", match.group(1))))


def validate_json() -> None:
    for path in sorted((ROOT / "src/main/resources").rglob("*.json")):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except Exception as error:
            fail(f"Invalid JSON {path.relative_to(ROOT)}: {error}")


def validate_protocols() -> None:
    visuals = enum_values(JAVA / "network/msg/S2CHakiVisual.java", "Visual")
    vfx = read(JAVA / "client/vfx/HakiVfx.java")
    handled_visuals = set(re.findall(r"case\s+([A-Z][A-Z0-9_]+)\s*->", vfx))
    missing = sorted(visuals - handled_visuals)
    extra = sorted(handled_visuals - visuals)
    if missing:
        fail("Visual packets without Photon handlers: " + ", ".join(missing))
    if extra:
        fail("Photon handlers without protocol values: " + ", ".join(extra))
    for visual in visuals:
        case = re.search(rf"case\s+{visual}\s*->\s*(.*?)(?=\n\s*case\s+|\n\s*\}}\n\s*\}})", vfx, re.S)
        persistent = {"GALAXY_FIST_START", "GALAXY_FIST_INTENSIFY", "GALAXY_FIST_STOP", "KINGS_GRIP_WINDUP"}
        if case and "play(" not in case.group(1) and "playAtWorld(" not in case.group(1) and visual not in persistent:
            fail(f"Visual handler {visual} does not launch a Photon composition")

    actions = enum_values(JAVA / "network/HakiAction.java", "HakiAction")
    controller = read(JAVA / "gameplay/HakiServerController.java")
    handled_actions = set(re.findall(r"case\s+([A-Z][A-Z0-9_]+)\s*->", controller))
    missing_actions = sorted(actions - handled_actions)
    if missing_actions:
        fail("Client actions without server handlers: " + ", ".join(missing_actions))

    # HexHaki V2 contract checks. These deliberately validate the entire feature chain rather
    # than accepting a lone enum/animation as evidence that a flagship feature exists.
    if "HAKI_BLADE_SLASH" not in actions:
        fail("V2 Haki blade slash action is missing")
    blade_packet = read(JAVA / "network/msg/S2CBladeSlash.java")
    blade_renderer = read(JAVA / "client/render/BladeSlashRenderer.java")
    keys = read(JAVA / "client/HakiKeys.java")
    if "HAKI_BLADE_SLASH" not in keys or "instanceof SwordItem" not in keys:
        fail("V2 Haki blade slash is not bound to real vanilla sword attacks")
    if "private static void bladeSlash" not in controller or "new S2CBladeSlash" not in controller:
        fail("V2 Haki blade slash has no authoritative server damage/packet path")
    if "BladeSlashRenderer.spawn" not in blade_packet or "RenderLevelStageEvent" not in blade_renderer:
        fail("V2 Haki blade slash packet is not connected to its world-space renderer")
    # Blade cuts must NOT override the player's normal Minecraft sword swing. V2 keeps the
    # travelling Haki slash renderer/server hit path, but deliberately owns no sword-swing animation.
    if 'animate(player, horizontal ? "blade_slash_horizontal" : "blade_slash_vertical"' in controller:
        fail("Haki blade cuts still override the normal Minecraft sword swing animation")
    for removed in ("blade_slash_horizontal", "blade_slash_vertical"):
        if (ASSETS / "player_animation" / f"{removed}.json").is_file():
            fail(f"Deleted sword-swing animation unexpectedly returned: {removed}")

    perception = read(JAVA / "network/msg/S2CPerception.java")
    perception_renderer = read(JAVA / "client/render/PerceptionRenderer.java")
    for token in ("predictX", "predictY", "predictZ", "confidence"):
        if token not in perception:
            fail(f"V2 Observation prediction packet is missing {token}")
    if "tickObservationProjectileDodge" not in controller or "futureSightVector" not in controller:
        fail("V2 Observation is missing predictive dodge/future-motion server logic")
    if "alternative" not in perception_renderer.lower() and "branch" not in perception_renderer.lower():
        fail("V2 Observation renderer is missing future-branch visualization")
    for required in ("observation_dodge_left", "observation_dodge_right"):
        if not (ASSETS / "player_animation" / f"{required}.json").is_file():
            fail(f"Missing V2 Observation dodge animation: {required}")

    cinematic_packet = read(JAVA / "network/msg/S2CCinematic.java")
    cinematic = read(JAVA / "client/cinematic/CinematicController.java")
    events = read(JAVA / "gameplay/HakiEvents.java")
    if any(token not in visuals for token in ("KINGS_GRIP_LOCK", "KINGS_GRIP_WINDUP", "KINGS_GRIP_IMPACT")):
        fail("V2 King's Grip tiered cinematic VFX protocol is incomplete")
    grip_sequences = tuple(
        f"kings_grip_{role}_{tier}"
        for tier in ("none", "haki", "advanced")
        for role in ("attacker", "victim")
    )
    for sequence in grip_sequences:
        if sequence not in cinematic:
            fail(f"V2 cinematic camera sequence missing: {sequence}")
        if not (ASSETS / "player_animation" / f"{sequence}.json").is_file():
            fail(f"V2 synchronized Player Animator track missing: {sequence}")
    if "450f" not in controller or "KINGS_GRIP_ADVANCED" not in controller:
        fail("Advanced King's Grip is missing its high-cost 400+ energy tier")
    if "10.5" not in controller or "7.5" not in controller or "5.5" not in controller:
        fail("King's Grip tiered hard-forward launch forces are missing")
    advanced_anim = read(ASSETS / "player_animation" / "kings_grip_attacker_advanced.json")
    if '"leftArm"' not in advanced_anim or '"rightArm"' not in advanced_anim or '"endTick": 148' not in advanced_anim:
        fail("Advanced King's Grip animation does not preserve the long right-grab/left-punch track")
    if "forceOrientation" not in cinematic_packet or "forceOrientation" not in cinematic:
        fail("V2 player victims are not forced into the server-authored King's Grip view")
    if "ACTIVE_GRABS" not in controller or "tickKingsGrip" not in controller or "kingsGripImpact" not in controller:
        fail("V2 King's Grip server synchronization/impact state machine is missing")
    if "HexHakiGripUntil" not in events or "HexHakiGripAttackerUntil" not in events:
        fail("V2 King's Grip does not suppress victim/attacker combat during the cutscene")
    if "HexHakiAllowGripImpact" not in events or "HexHakiAllowGripImpact" not in controller:
        fail("King's Grip scripted impact has no scoped bypass through its own cinematic attack lock")
    if 'target.invulnerableTime = 0;' not in controller or 'target.hurt(attacker.damageSources().playerAttack(attacker), damage);' not in controller:
        fail("King's Grip scripted impact is not guaranteed to reach the target damage path")
    if 'Mth.lerp(armamentScale, 6f, 36f)' not in controller:
        fail("King's Grip no-Haki damage is not scaling from 3 hearts to the old 1000-mastery maximum")
    if 'Mth.lerp(armamentScale, 12f, 87f)' not in controller or 'Mth.lerp(armamentScale, 20f, 220f)' not in controller:
        fail("King's Grip Haki/Advanced damage tiers are not mastery-scaled to their old 1000-mastery maxima")
    if 'boolean gripTestMode = HakiData.unlimitedEnergy(player);' not in controller or '!HakiProgression.kingsGripUnlocked(player)' not in controller:
        fail("Unlimited-energy test mode cannot bypass the normal midgame King's Grip gate for stage testing")
    mouse_mixin = read(JAVA / "mixin/MouseHandlerMixin.java")
    mixins_json = read(ROOT / "src/main/resources/hexhaki.mixins.json")
    if "MouseHandlerMixin" not in mixins_json:
        fail("King's Grip mouse-look lock mixin is not registered")
    if "method = \"turnPlayer\"" not in mouse_mixin or "CinematicController.isKingsGripActive()" not in mouse_mixin:
        fail("King's Grip does not hard-lock raw mouse look for both cinematic participants")
    if "player.turn(yawDelta, pitchDelta)" not in mouse_mixin:
        fail("Mouse-look lock does not preserve vanilla look outside King's Grip")
    if "int pullTicks = 6;" not in controller or "rightHandOffset" not in controller or "forward.cross(new Vec3(0, 1, 0))" not in controller:
        fail("King's Grip opening is missing the immediate right-hand throat catch / right-side target alignment")
    if ".38 + (1.80 - target.getBbHeight()) * .18" not in controller:
        fail("King's Grip target lift is not clamped to a hand-aligned suspended height")
    grip_camera_start = cinematic.find("private static CameraSequence kingsGripCamera")
    grip_camera_end = cinematic.find("private static CameraKeyframe k(", grip_camera_start)
    grip_camera = cinematic[grip_camera_start:grip_camera_end]
    if "new Vec3(5.10" not in grip_camera or ".000f" not in grip_camera:
        fail("King's Grip reference camera is not using the pulled-back stable profile framing")
    for tier in ("none", "haki", "advanced"):
        attacker_anim = read(ASSETS / "player_animation" / f"kings_grip_attacker_{tier}.json")
        if '"rightLeg"' not in attacker_anim or '"leftLeg"' not in attacker_anim or 'RIGHT-hand throat grab' not in attacker_anim or 'LEFT-hand gut punch' not in attacker_anim:
            fail(f"King's Grip {tier} attacker track is missing the full-body right-grab/left-punch choreography")

    # King's Grip handedness is visual, not just naming. Once the lift is complete the RIGHT palm
    # must remain a world-stable throat anchor through the release tick. The LEFT arm chambers
    # OUTWARD from the torso, stays loaded until two ticks before impact, then snaps straight forward.
    grip_ticks = {
        "none": (38, 50, 63, 66, 68, 70, 72),
        "haki": (42, 58, 77, 80, 82, 84, 86),
        "advanced": (50, 72, 110, 114, 116, 118, 120),
    }
    for tier, (hold_tick, wind_tick, cock_tick, strike_tick, impact_tick, freeze_tick, release_tick) in grip_ticks.items():
        path = ASSETS / "player_animation" / f"kings_grip_attacker_{tier}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        by_tick = {m.get("tick"): m for m in data.get("emote", {}).get("moves", [])}
        required = (hold_tick, wind_tick, cock_tick, strike_tick, impact_tick, freeze_tick, release_tick)
        if any(t not in by_tick for t in required):
            fail(f"King's Grip {tier} is missing hold/chamber/snap/impact/release keyframes")
            continue
        hold = by_tick[hold_tick]
        # The hold/chamber stays planted so the RIGHT hand never drifts before the punch starts.
        for t in (wind_tick, cock_tick, strike_tick):
            frame = by_tick[t]
            for part in ("body", "torso", "rightArm"):
                if frame.get(part) != hold.get(part):
                    fail(f"King's Grip {tier} tick {t} moves the RIGHT-hand throat anchor before impact via {part}")

        # The final punch MUST use the whole kinetic chain instead of arm-only motion. At impact the
        # hips/back/legs drive through, while the RIGHT arm counter-rotates to preserve the neck hold.
        impact = by_tick[impact_tick]
        for t in (freeze_tick, release_tick):
            frame = by_tick[t]
            for part in ("body", "torso", "rightArm", "rightLeg", "leftLeg"):
                if frame.get(part) != impact.get(part):
                    fail(f"King's Grip {tier} tick {t} breaks the committed full-body impact/throat-hold pose via {part}")
        if impact.get("body") == hold.get("body") or impact.get("torso") == hold.get("torso"):
            fail(f"King's Grip {tier} final punch is still arm-only/stick-like")
        if impact.get("rightLeg") == hold.get("rightLeg") or impact.get("leftLeg") == hold.get("leftLeg"):
            fail(f"King's Grip {tier} final punch does not drive through the legs")

        # Approximate throat-anchor compensation: body+torso angular drive must be countered by the
        # right arm by roughly the opposite amount on pitch/yaw/roll. This keeps the hand visually
        # planted even while the back and shoulder commit to the punch.
        for axis in ("pitch", "yaw", "roll"):
            hold_world = hold.get("body", {}).get(axis, 0) + hold.get("torso", {}).get(axis, 0)
            impact_world = impact.get("body", {}).get(axis, 0) + impact.get("torso", {}).get(axis, 0)
            body_delta = impact_world - hold_world
            arm_delta = impact.get("rightArm", {}).get(axis, 0) - hold.get("rightArm", {}).get(axis, 0)
            if abs(body_delta + arm_delta) > 2.5:
                fail(f"King's Grip {tier} impact does not counter-rotate RIGHT arm on {axis} to preserve throat contact")

        # After the lift the attacker must visibly look at the victim.
        for t in (wind_tick, cock_tick, strike_tick, impact_tick):
            head = by_tick[t].get("head", {})
            if head.get("yaw", 0) < 15:
                fail(f"King's Grip {tier} tick {t} does not turn the attacker's head toward the victim")

        # Outward boxer-style chamber: elbow flares away from the ribs (large negative roll on the
        # left arm), forearm folds tightly, and the upper arm is not thrown behind the back.
        wind = by_tick[wind_tick].get("leftArm", {})
        cock = by_tick[cock_tick].get("leftArm", {})
        strike = by_tick[strike_tick].get("leftArm", {})
        if not (-45 <= wind.get("pitch", 999) <= -10) or not (-115 <= wind.get("bend", 0) <= -80):
            fail(f"King's Grip {tier} windup is not a compact outward LEFT punch chamber")
        if wind.get("roll", 0) > -28:
            fail(f"King's Grip {tier} windup does not flare the LEFT elbow outward")
        for label, left in (("cock", cock), ("strike-start", strike)):
            if not (-35 <= left.get("pitch", 999) <= -5) or not (-125 <= left.get("bend", 0) <= -95):
                fail(f"King's Grip {tier} {label} is not holding a folded LEFT punch chamber")
            if left.get("roll", 0) > -45:
                fail(f"King's Grip {tier} {label} does not hold the LEFT elbow outside the body")
        if cock != strike:
            fail(f"King's Grip {tier} starts interpolating the final punch before the two-tick snap window")

        # The final strike is a near-instant direct-forward extension.
        if impact_tick - strike_tick > 2:
            fail(f"King's Grip {tier} final LEFT punch is still too slow")
        for t in (impact_tick, freeze_tick, release_tick):
            left = by_tick[t].get("leftArm", {})
            if not (-110 <= left.get("pitch", 0) <= -88) or abs(left.get("bend", 0)) > 12:
                fail(f"King's Grip {tier} impact/release tick {t} is not a straight forward LEFT gut-punch extension")

    # King's Grip VFX must be truly 3D in the side-view cutscene. Dedicated grip compositions use
    # BeamEmitter geometry only; the directional impact packet style suppresses vanilla/particle motes.
    if "kingsGripLock3D" not in vfx or "kingsGripHakiImpact3D" not in vfx or "kingsGripDirectionalImpactWorld" not in vfx:
        fail("King's Grip dedicated all-3D Photon compositions are missing")
    if "boolean kingsGrip=m.style()==1;" not in vfx or "if(!kingsGrip) spawnDirectionalAir" not in vfx:
        fail("King's Grip directional impact still permits flat vanilla air particles")
    impact_packet = read(JAVA / "network/msg/S2CArmamentImpact.java")
    if "int style" not in impact_packet or "b.writeVarInt(m.style)" not in impact_packet:
        fail("S2CArmamentImpact is missing the King's Grip all-3D style discriminator")
    if "state.tier() == KINGS_GRIP_ADVANCED, 1," not in controller:
        fail("King's Grip does not request the all-3D directional impact style")
    for method_name in ("kingsGripLock3D", "kingsGripHakiImpact3D", "kingsGripAdvancedImpact"):
        method_start = vfx.find(f"private static FX {method_name}")
        if method_start < 0:
            continue
        next_method = vfx.find("private static FX ", method_start + 20)
        block = vfx[method_start:next_method if next_method >= 0 else len(vfx)]
        if "ParticleEmitter" in block or "particleTex(" in block or "particle(" in block:
            fail(f"{method_name} still contains flat ParticleEmitter sprite geometry")
    # No King's Grip composition is allowed to draw orbital/circular pressure geometry.
    for method_name in ("kingsGripDirectionalImpactWorld", "kingsGripLock3D", "kingsGripHakiImpact3D", "kingsGripAdvancedImpact"):
        method_start = vfx.find(f"private static FX {method_name}")
        if method_start < 0:
            continue
        next_method = vfx.find("\n    private static ", method_start + 20)
        block = vfx[method_start:next_method if next_method >= 0 else len(vfx)]
        if "addDirectionalPressureRing(" in block:
            fail(f"{method_name} reintroduced the unwanted King's Grip orbital/ring geometry")
    hand_layer = read(JAVA / "client/render/HakiHandLayer.java")
    if "CinematicController.isKingsGripActive()" not in hand_layer or "if (!kingsGrip && !gripLeftFistFire)" not in hand_layer:
        fail("King's Grip does not suppress ordinary flat arm-aura/flame quads during the cinematic")
    if "isKingsGripLeftFistFireActive" not in vfx or "renderKingsGripLeftFistFire3D" not in hand_layer:
        fail("Advanced King's Grip fire is not routed into the live LEFT-hand ModelPart")
    if "model.leftArm" not in hand_layer or "emitBentBox" not in hand_layer:
        fail("Advanced King's Grip LEFT fist fire is not true arm-bone-bound 3D geometry")
    if "case KINGS_GRIP_WINDUP -> markKingsGripLeftFistFire(entity.getId())" not in vfx:
        fail("Advanced King's Grip wind-up packet still spawns root-offset fire instead of marking the LEFT fist")
    if "Visual.KINGS_GRIP_LOCK" in controller:
        fail("King's Grip still emits victim-centered Haki/lightning before the punch impact")
    if "age < impactTick - 9" not in controller:
        fail("Advanced King's Grip fist fire can linger after the impact instead of ending with the punch")
    fist_fire_start = hand_layer.find("private static void renderKingsGripLeftFistFire3D")
    fist_fire_end = hand_layer.find("private static void renderAdvancedFlames", fist_fire_start)
    fist_fire = hand_layer[fist_fire_start:fist_fire_end]
    if "i < 24" not in fist_fire or "i < 30" not in fist_fire or "i < 16" not in fist_fire:
        fail("Advanced King's Grip LEFT-fist fire is not using the Hotfix 16 overcharged volume")
    network_source = read(JAVA / "network/HakiNetwork.java")
    if 'private static final String VERSION = "30";' not in network_source:
        fail("Network protocol does not match HexHaki 1.1 Observation packet layout")
    impact_frame_packet = read(JAVA / "network/msg/S2CKingsGripImpactFrame.java")
    impact_frame_client = read(JAVA / "client/render/KingsGripImpactFrame.java")
    if "S2CKingsGripImpactFrame.class" not in network_source or "KingsGripImpactFrame.trigger" not in impact_frame_packet:
        fail("King's Grip synchronized impact-frame packet is not registered/handled")
    if "HakiNetwork.to(attacker, new S2CKingsGripImpactFrame" not in controller or "HakiNetwork.to(victim, new S2CKingsGripImpactFrame" not in controller:
        fail("King's Grip impact frame is not sent to both PvP cinematic participants")
    if "FRAME_TICKS = 5" not in impact_frame_client or "Axis.ZP.rotationDegrees" not in impact_frame_client:
        fail("King's Grip black/white anime impact-frame renderer is missing")
    if "smokeLayers=advanced?13:(haki?11:9)" not in vfx or "ribbons=advanced?12:(haki?10:8)" not in vfx:
        fail("King's Grip back-blast is still too small / missing volumetric vapour")
    if "if(advanced){" not in vfx or "for(int i=0;i<38;i++)" not in vfx:
        fail("Advanced King's Grip back-blast is missing fire carried through the air/lightning burst")
    # Final King's Grip tier cooldown contract: physical 10s, Armament 20s, Advanced 30s.
    cooldown_contract = (
        "case KINGS_GRIP_ADVANCED -> 600L",
        "case KINGS_GRIP_HAKI -> 400L",
        "default -> 200L",
        'now + kingsGripCooldownTicks(tier)'
    )
    if not all(token in controller for token in cooldown_contract):
        fail("King's Grip tier cooldowns are not locked to 10s / 20s / 30s")

    # Galaxy Impact must use its exact endpoint packet rather than falling back to the generic
    # entity-local VFX protocol, otherwise crosshair aim and remote rendering diverge.
    # V2 WiFi Haki replaces King's Verdict with a TRUE HOLD channel: no target-centred detonation.
    # The server locks one visible target, redraws a living upward/jagged connection, applies low
    # damage pulses while arresting the victim, and stops immediately when L is released. Armament
    # state freezes the visual tier at cast time: 1 bare beam / 2 Armament beams / 3 Advanced beams.
    wifi_packet = read(JAVA / "network/msg/S2CWifiHaki.java")
    wifi_anim = ASSETS / "player_animation/wifi_haki.json"
    keys_source = read(JAVA / "client/HakiKeys.java")
    actions_source = read(JAVA / "network/HakiAction.java")
    if "S2CWifiHaki.class" not in network_source or "acceptWifiHaki" not in wifi_packet:
        fail("WiFi Haki world-arc packet is not registered/handled")
    if "beamCount" not in wifi_packet or "b.writeVarInt(m.beamCount)" not in wifi_packet:
        fail("WiFi Haki packet does not synchronize the 1/2/3 beam tier")
    if "SOVEREIGN_STRIKE_RELEASE" not in actions_source or "STRIKE.isDown()" not in keys_source:
        fail("WiFi Haki is not a real press-and-hold/release input")
    if "ACTIVE_WIFI_HAKI" not in controller or "tickWifiHaki" not in controller or "stopWifiHaki" not in controller:
        fail("WiFi Haki sustained server channel state machine is missing")
    if "new S2CWifiHaki" not in controller or "broadcastWifiHaki" not in controller:
        fail("WiFi Haki is not broadcasting the server-authored source/target arc")
    if "state.beamCount" not in controller or "this.beamCount=1+this.hakiTier" not in controller:
        fail("WiFi Haki does not freeze 1/2/3 beams from bare/Armament/Advanced state")
    if "wifiHakiArc" not in vfx or "Math.sin(Math.PI*t)*archHeight" not in vfx:
        fail("WiFi Haki is missing its upward arched path")
    if "laneSpacing" not in vfx or "laneOffset=lane*laneSpacing*envelope" not in vfx:
        fail("WiFi Haki multi-beams are not forced into separated lateral lanes")
    if "branchLen" not in vfx or "verticalCoil" not in vfx:
        fail("WiFi Haki arcs are too straight/static; jagged rolling branches are missing")
    if "SOVEREIGN_LOCK" in controller or "SOVEREIGN_IMPACT" in controller:
        fail("Old King's Verdict target-centred Haki explosion is still emitted by gameplay")
    if "return 60.0 + 40.0 * armProgress + 60.0 * obsProgress" not in controller:
        fail("WiFi Haki reach does not scale from the 60-block unlock floor to the 160-block pinnacle")
    if "WIFI_HAKI_DAMAGE_INTERVAL_TICKS = 10" not in controller or "float basePulse = 2.00f + .25f * masteryProgress" not in controller:
        fail("WiFi Haki Supreme-tier mastery-scaled damage pulse balance is missing")
    if "case 2 -> 2.10f" not in controller or "case 1 -> 1.55f" not in controller:
        fail("WiFi Haki Armament/Advanced beam tiers do not increase combined pulse damage")
    unlocks = read(JAVA / "data/HakiUnlocks.java")
    for token in ("WIFI_HAKI_ARMAMENT = 450", "WIFI_HAKI_OBSERVATION = 450", "WIFI_HAKI_CONQUEROR = 500"):
        if token not in unlocks:
            fail("WiFi Haki midgame progression gate drifted: " + token)
    if "return 90 + Math.round(30f * progress)" not in controller:
        fail("WiFi Haki hold duration does not scale from 4.5s at unlock to 6s at max mastery")
    if "WIFI_HAKI_START" not in controller or "WIFI_HAKI_PULSE" not in controller or "WIFI_HAKI_END" not in controller:
        fail("WiFi Haki is missing the new pressure/snap sound lifecycle")
    wifi_sound_names = {p.name for p in (ROOT / "src/main/resources/assets/hexhaki/sounds").glob("wifi_haki_*.ogg")}
    if not {"wifi_haki_start.ogg", "wifi_haki_pulse.ogg", "wifi_haki_end.ogg"}.issubset(wifi_sound_names):
        fail("WiFi Haki custom start/pulse/end sound assets are missing")
    if "WIFI_HAKI_MIN_COOLDOWN_TICKS = 200L" not in controller or "WIFI_HAKI_MAX_COOLDOWN_TICKS = 1000L" not in controller:
        fail("WiFi Haki usage-scaled cooldown is not locked to 10s -> 50s")
    if "usedTicks / (double)state.maxTicks" not in controller or "cooldownTicks" not in controller:
        fail("WiFi Haki cooldown does not scale with actual channel usage")
    if not wifi_anim.is_file() or (ASSETS / "player_animation/kings_verdict.json").is_file():
        fail("WiFi Haki move animation did not replace the stale King's Verdict animation")
    if wifi_anim.is_file():
        wifi_data = json.loads(wifi_anim.read_text(encoding="utf-8"))
        wifi_moves = wifi_data.get("emote", {}).get("moves", [])
        if any("leftLeg" in frame or "rightLeg" in frame for frame in wifi_moves):
            fail("WiFi Haki animation illegally touches the legs")
        if not any("rightArm" in frame for frame in wifi_moves):
            fail("WiFi Haki animation is missing the simple projected arm pose")

    network = read(JAVA / "network/HakiNetwork.java")
    exact_packet = read(JAVA / "network/msg/S2CGalaxyImpact.java")
    if "S2CGalaxyImpact.class" not in network:
        fail("S2CGalaxyImpact is not registered in HakiNetwork")
    if "acceptGalaxyImpact" not in exact_packet or "acceptGalaxyImpact" not in vfx:
        fail("Exact Galaxy Impact packet has no client Photon handler")
    if "new S2CGalaxyImpact" not in controller:
        fail("Galaxy Impact server path does not send the exact world-space impact packet")
    if "new BlockEffect" not in vfx or "setOffset" not in vfx:
        fail("Galaxy Impact is not anchored at a fixed sub-block world position")

    # RAGE's replacement charge is an entity-following fist effect. The old overhead/world-space
    # galaxy packet must not remain in the runtime path after the surgical ultimate replacement.
    for token in ("GALAXY_FIST_START", "GALAXY_FIST_INTENSIFY", "GALAXY_FIST_STOP"):
        if token not in vfx or token not in controller:
            fail(f"RAGE Galaxy fist lifecycle is missing {token}")
    if "startGalaxyFistCharge" not in vfx or "galaxyFistOffset" not in vfx or "tickGalaxyFistEffects" not in vfx:
        fail("RAGE Galaxy fist charge is not persistent/fist-following")
    if "S2CGalaxyChargeWorld" in network or "new S2CGalaxyChargeWorld" in controller or "galaxyChargeWorld(" in vfx:
        fail("Legacy overhead/world-space Galaxy charge code is still active")

    if "CONVERGENCE.isDown()" not in keys or "CONVERGENCE_RELEASE" not in keys:
        fail("Galaxy Impact key is not wired as tap/hold input")
    data = read(JAVA / "data/HakiData.java")
    commands = read(JAVA / "command/HakiCommands.java")
    techniques = read(JAVA / "data/HakiTechnique.java")
    mastery_screen = read(JAVA / "client/screen/HakiMasteryScreen.java")
    hud_source = read(JAVA / "client/HakiHud.java")
    progression_source = read(JAVA / "gameplay/HakiProgression.java")
    unlocks = read(JAVA / "data/HakiUnlocks.java")
    if "advancedHakiUnlocked" not in progression_source or "ADVANCED_HAKI_ARMAMENT = 850" not in unlocks or "ADVANCED_HAKI_CONQUEROR = 850" not in unlocks:
        fail("Advanced Haki is not preserved as the late ARM850 + HAO850 multiplier")
    if "CONQ_SOVEREIGN" not in mastery_screen or "ARM 450 + OBS 450 + HAO 500" not in mastery_screen:
        fail("Mastery screen does not show the midgame WiFi Haki unlock requirement")
    if "WiFi Haki (hold)" not in hud_source or "LOCK 450/450/500" not in hud_source:
        fail("HUD key roadmap does not expose WiFi Haki hold/unlock state")
    if "KINGS_GRIP_ARMAMENT = 340" not in unlocks or "KINGS_GRIP_OBSERVATION = 300" not in unlocks or "KINGS_GRIP_CONQUEROR = 320" not in unlocks:
        fail("King's Grip is no longer on the intended midgame unlock gate")
    if "GALAXY_FULL_ARMAMENT = 650" not in unlocks or "GALAXY_FULL_OBSERVATION = 550" not in unlocks or "GALAXY_FULL_CONQUEROR = 650" not in unlocks:
        fail("Full Galaxy Impact progression gate drifted")
    if "mastery_logbook.png" not in mastery_screen or "hud_ship.png" not in hud_source or "hud_controls.png" not in hud_source:
        fail("Hand-drawn HexHaki pirate GUI assets are not wired into the client")
    for texture in ("mastery_logbook.png", "hud_ship.png", "hud_controls.png"):
        if not (ASSETS / "textures/gui" / texture).is_file():
            fail("Missing pirate GUI texture: " + texture)
    if "mouseClicked" not in mastery_screen or "selected" not in mastery_screen:
        fail("Mastery logbook is not clickable/selectable")
    if "return 100f + 1400f * average" not in data:
        fail("Energy capacity does not scale from 100 to the requested 1500 full-mastery cap")
    energy_command = commands[commands.find('.then(Commands.literal("energy")'):commands.find('.then(Commands.literal("legend")')]
    if "IntegerArgumentType.integer(0,1500)" not in energy_command:
        fail("/haki energy command does not accept the new 1500 ceiling")
    if 'Commands.literal("unlimited")' not in energy_command or "setUnlimitedEnergy" not in commands:
        fail("/haki energy unlimited test command is missing")
    if 'flag(p, "testUnlimitedEnergy")' not in data or "if (unlimitedEnergy(p)) return true;" not in data:
        fail("Unlimited-energy test mode does not bypass all Haki energy consumption")
    wave_packet = read(JAVA / "network/msg/S2CGalaxyWave.java")
    if "startX" not in wave_packet or "endX" not in wave_packet or "acceptGalaxyWave" not in wave_packet:
        fail("Tap Galaxy Impact is missing its exact world-space aim packet")
    if "new S2CGalaxyWave" not in controller or "galaxyWaveWorld(start,end" not in vfx:
        fail("Tap Galaxy Impact visuals are not anchored to the authoritative look-vector endpoints")
    if "GALAXY_WAVE_PUNCH" not in controller or "galaxy_wave_punch.ogg" not in {p.name for p in (ROOT / "src/main/resources/assets/hexhaki/sounds").glob("*.ogg")}:
        fail("Dedicated heavy Galaxy tap-punch sound is missing")
    if "GALAXY_TAP_IMPACT_TICK = 8" not in controller or "GALAXY_TAP_RECOVERY_TICKS = 22" not in controller or "ServerTimeline.later(level, GALAXY_TAP_IMPACT_TICK" not in controller:
        fail("Tap Galaxy Impact is not synchronized to the fast V2 punch-contact/recovery timing")
    server_tap = controller[controller.find("private static void releaseGalaxyHakiWave"):controller.find("private static void fireGalaxyImpact") ]
    client_tap = vfx[vfx.find("public static void acceptGalaxyWave"):vfx.find("public static void acceptGalaxyHakiStrike") ]
    if "ParticleTypes.SONIC_BOOM" in server_tap or "ParticleTypes.SONIC_BOOM" in client_tap:
        fail("Tap Galaxy Impact still contains vanilla Warden SONIC_BOOM effects")
    if ".24, .24, .24" in server_tap or "0, velocity.x, velocity.y, velocity.z, 1.0D" not in server_tap:
        fail("Tap Galaxy air fallback is not locked to the authoritative punch direction")

    impact_packet = read(JAVA / "network/msg/S2CArmamentImpact.java")
    if "S2CArmamentImpact.class" not in network or "acceptArmamentImpact" not in impact_packet:
        fail("Directional Armament impact packet is not registered/handled")
    if "new S2CArmamentImpact" not in events or "contact.x,contact.y,contact.z" not in events:
        fail("Normal Armament punches do not send their exact world-space contact point")
    if "spawnDirectionalAir" not in vfx or "armamentImpactWorld(direction" not in vfx:
        fail("Normal Armament punch air does not use the authoritative hit direction")
    # V2 deliberately removed the dedicated Ryuo Punch. Ryuo remains a mastery concept only;
    # there must be no B-key binding or active server action capable of reviving the old move.
    if "ARM_SPECIAL" in keys or "ARMAMENT_RYO_START" in actions or "ARMAMENT_RYO_RELEASE" in actions:
        fail("Deleted Ryuo Punch is still exposed through a client key/action")
    if "beginRyo(" in controller or "releaseRyo(" in controller or "new S2CRyoRelease" in controller:
        fail("Deleted Ryuo Punch still has an active server execution path")
    for removed in ["ryo_activate.json", "ryo_charge.json", "ryo_charge_max.json", "ryo_release.json"]:
        if (ASSETS / "player_animation" / removed).exists():
            fail("Deleted Ryuo Punch animation still present: " + removed)
    tap_anim = read(ASSETS / "player_animation/galaxy_tap_punch.json")
    if '"endTick": 22' not in tap_anim or '"tick": 8' not in tap_anim:
        fail("Tap Galaxy Impact animation is missing the fast V2 contact/recovery timing")
    tap_data = json.loads(tap_anim)
    tap_frames = {m.get("tick"): m for m in tap_data.get("emote", {}).get("moves", [])}
    if 6 not in tap_frames or 8 not in tap_frames or 10 not in tap_frames:
        fail("Tap Galaxy Impact is missing coil/contact/impact-hold keyframes")
    else:
        coil = tap_frames[6]
        hit = tap_frames[8]
        hold = tap_frames[10]
        if coil.get("rightArm", {}).get("bend", 0) > -80:
            fail("Tap Galaxy Impact does not visibly chamber the right fist before contact")
        if hit.get("rightArm", {}).get("bend", -99) != 0:
            fail("Tap Galaxy Impact right arm is not fully committed on the contact frame")
        if hit.get("body") == coil.get("body") or hit.get("torso") == coil.get("torso"):
            fail("Tap Galaxy Impact contact is still arm-only instead of a whole-body strike")
        if hold.get("rightArm") != {"pitch": -98, "yaw": 3, "roll": 2, "bend": 0}:
            fail("Tap Galaxy Impact does not preserve a brief heavy post-contact extension")

    if "ARM_HAKI_LEAP(HakiType.ARMAMENT, HakiUnlocks.HAKI_LEAP_ARMAMENT" not in techniques:
        fail("Armament midgame Haki Leap unlock is missing")
    if "HAKI_LEAP_START" not in actions or "HAKI_LEAP_RELEASE" not in actions:
        fail("Haki Leap client actions are missing")
    if "MovementInputUpdateEvent" not in keys or "e.getInput().jumping=false" not in keys:
        fail("Space hold is not intercepted cleanly for Haki Leap")
    if "mc.options.keyJump.isDown()" not in keys or "mc.options.keyShift.isDown()" not in keys or "comboHeld=jumpHeld && shiftHeld" not in keys:
        fail("Haki Leap must arm only from the deliberate Shift + Space combo")
    if "leapHoldTicks" in keys or "HAKI_LEAP_ARM_DELAY_TICKS" in keys:
        fail("Legacy four-second Haki Leap gate is still present")
    if "e.getInput().shiftKeyDown=false" not in keys:
        fail("Haki Leap does not suppress vanilla crouch posture while charging")
    if "beginHakiLeap" not in controller or "releaseHakiLeap" not in controller or "forwardSpeed" not in controller or "upwardSpeed" not in controller:
        fail("Server-authoritative forward Haki Leap is missing")
    for required in ["haki_leap_charge", "haki_leap_release"]:
        if not (ASSETS / "player_animation" / f"{required}.json").is_file():
            fail(f"Missing Haki Leap animation: {required}")
        leap_anim = read(ASSETS / "player_animation" / f"{required}.json")
        for forbidden in ['"body"', '"torso"', '"head"', '"rightLeg"', '"leftLeg"']:
            if forbidden in leap_anim:
                fail(f"{required} must be arms-only; found forbidden track {forbidden}")
    if "ARM_GALAXY_WAVE(HakiType.ARMAMENT, HakiUnlocks.GALAXY_WAVE_ARMAMENT" not in techniques:
        fail("Armament midgame Galaxy Haki Wave unlock is missing")
    if "private static void galaxyHakiWave" not in controller or "double length = 15.0 + 5.0 * trained + 8.0 * charge" not in controller or "double radius = 2.9 + 1.1 * trained + 1.5 * charge" not in controller:
        fail("Tap Galaxy Impact is not the requested mastery/charge-scaled cylindrical wave")
    if "charge >= CONVERGENCE_MAX && HakiProgression.convergenceUnlocked(player)" not in controller or "GALAXY_PRECHARGE_GAIN" not in controller:
        fail("Full Galaxy Impact is not hard-gated behind a true 100% ground charge")
    if "chargePercent" not in controller or "1.0f + .80f * charge" not in controller:
        fail("Partial Galaxy charge is not buffing the tap-wave branch")
    if "LivingEntityUseItemEvent.Finish" not in events or "+ 50f" not in events:
        fail("Eating does not restore the requested 50 Haki energy")
    if "HakiProgression.convergenceUnlocked(player)" not in controller[controller.find("public static void tick"):]:
        fail("Held Galaxy Impact no longer preserves the full-ultimate unlock gate")
    if "keyAttack.isDown()" not in keys or "GALAXY_PUNCH" not in keys or "case GALAXY_PUNCH -> fireGalaxyImpact(player)" not in controller:
        fail("RAGE Galaxy READY phase is not bound exclusively to the vanilla Attack key")
    for required in ["galaxy_ready", "galaxy_tap_punch", "galaxy_leap", "galaxy_charge", "galaxy_rage_ready", "galaxy_release"]:
        if not (ASSETS / "player_animation" / f"{required}.json").is_file():
            fail(f"Missing rebuilt Galaxy Impact animation: {required}")
    galaxy_ready = read(ASSETS / "player_animation/galaxy_ready.json")
    if '"rightLeg"' in galaxy_ready or '"leftLeg"' in galaxy_ready:
        fail("Galaxy Impact held laugh must not animate either leg")
    if '"isLoop": true' not in galaxy_ready or '"head"' not in galaxy_ready or '"torso"' not in galaxy_ready:
        fail("Galaxy Impact held laugh is missing its looping upper-body choreography")
    if 'Garp-style laugh' not in galaxy_ready:
        fail("Galaxy Impact ready track is no longer documented/authored as the laugh phase")
    legacy = sorted((ASSETS / "player_animation").glob("convergence_*.json"))
    if legacy:
        fail("Legacy Convergence animations still present: " + ", ".join(p.name for p in legacy))
    galaxy_block = controller[controller.find("private static void beginConvergence"):controller.find("private static int encodeGalaxyImpactVariant")]
    if any(token in galaxy_block for token in ["destroyBlock(", "removeBlock(", "level.explode(", "setBlock("]):
        fail("Galaxy Impact contains terrain destruction despite no-block-breaking requirement")


def validate_sounds() -> None:
    sounds_path = ASSETS / "sounds.json"
    try:
        sounds = json.loads(read(sounds_path))
    except Exception:
        return
    registered = set(re.findall(r'register\("([a-z0-9_]+)"\)', read(JAVA / "registry/ModSounds.java")))
    declared = set(sounds)
    if registered - declared:
        fail("Registered sounds absent from sounds.json: " + ", ".join(sorted(registered - declared)))
    if declared - registered:
        fail("sounds.json entries absent from ModSounds: " + ", ".join(sorted(declared - registered)))
    for sound_id, definition in sounds.items():
        for entry in definition.get("sounds", []):
            name = entry.get("name") if isinstance(entry, dict) else entry
            relative = str(name).removeprefix("hexhaki:")
            path = ASSETS / "sounds" / f"{relative}.ogg"
            if not path.is_file():
                fail(f"Missing OGG for {sound_id}: {path.relative_to(ROOT)}")
            elif path.read_bytes()[:4] != b"OggS":
                fail(f"Invalid OGG header: {path.relative_to(ROOT)}")


def validate_animations() -> None:
    animation_dir = ASSETS / "player_animation"
    available = {path.stem for path in animation_dir.glob("*.json")}
    requested: set[str] = set()
    for path in JAVA.rglob("*.java"):
        source = read(path)
        for call in re.findall(r"animate\([^;\n]+\)", source):
            requested.update(re.findall(r'"([a-z0-9_]+)"', call))
        for assignment in re.findall(r"String\s+\w*(?:Pose|Animation)\s*=\s*([^;]+)", source):
            requested.update(re.findall(r'"([a-z0-9_]+)"', assignment))
    requested.discard("__clear__")
    missing = sorted(requested - available)
    if missing:
        fail("Requested Player Animator resources are missing: " + ", ".join(missing))
    if len(available) < 29:
        fail(f"Expected the full R6 animation set; found only {len(available)} JSON files")

    # R6's promise is deliberately strict: every authored body animation must carry actual bend
    # data for torso, elbows and knees whenever those parts are keyed. A valid JSON with rigid
    # limbs is still a regression for this build.
    bend_parts = {"torso", "rightArm", "leftArm", "rightLeg", "leftLeg",
                  "right_arm", "left_arm", "right_leg", "left_leg"}
    for path in sorted(animation_dir.glob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            moves = data.get("emote", {}).get("moves", [])
        except Exception:
            continue
        if not moves:
            fail(f"Animation has no keyframes: {path.name}")
            continue
        bend_count = 0
        # Player Animator's JSON deserializer is stricter than Gson syntax. Unsupported
        # transform channels can resolve to a null State and crash resource loading.
        allowed_parts = {"body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg"}
        allowed_channels = {
            "body": {"x", "y", "z", "pitch", "yaw", "roll"},
            "head": {"pitch", "yaw", "roll"},
            "torso": {"pitch", "yaw", "roll", "bend"},
            "rightArm": {"pitch", "yaw", "roll", "bend"},
            "leftArm": {"pitch", "yaw", "roll", "bend"},
            "rightLeg": {"pitch", "yaw", "roll", "bend"},
            "leftLeg": {"pitch", "yaw", "roll", "bend"},
        }
        for index, move in enumerate(moves):
            for part, transform in move.items():
                if isinstance(transform, dict):
                    if part not in allowed_parts:
                        fail(f"Unsupported Player Animator part {part!r} in {path.name} keyframe {index}")
                    else:
                        unknown = sorted(set(transform) - allowed_channels[part])
                        if unknown:
                            fail(f"Unsupported Player Animator channel(s) in {path.name} keyframe {index} part {part}: " + ", ".join(unknown))
                if part in bend_parts and isinstance(transform, dict):
                    if "bend" not in transform:
                        fail(f"Missing BendyLib bend in {path.name} keyframe {index} part {part}")
                    else:
                        bend_count += 1
                        bend = transform.get("bend")
                        if isinstance(bend, (int, float)) and abs(bend) > 135:
                            fail(f"Implausibly extreme bend ({bend}) in {path.name} keyframe {index} part {part}")
        if bend_count == 0:
            fail(f"Animation does not use BendyLib at all: {path.name}")

    gradle = read(ROOT / "build.gradle")
    mods = read(ROOT / "src/main/resources/META-INF/mods.toml")
    if "bendy-lib-623373:4550371" not in gradle:
        fail("BendyLib 4.0.0 Forge dependency is missing from build.gradle")
    if 'modId="bendylib"' not in mods:
        fail("BendyLib runtime dependency is missing from mods.toml")


def validate_textures() -> None:
    for name in ("rage_glow.png", "rage_flame.png", "rage_lightning_beam.png"):
        path = ASSETS / "textures/effect" / name
        if not path.is_file():
            fail(f"Missing transplanted RAGE Galaxy texture: {name}")
        elif path.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
            fail(f"Invalid transplanted RAGE Galaxy PNG: {path.relative_to(ROOT)}")
    for name in ("soft.png", "spark.png", "beam.png", "coating.png"):
        path = ASSETS / "textures/effect" / name
        if not path.is_file():
            fail(f"Missing Photon texture: {name}")
        elif path.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
            fail(f"Invalid PNG header: {path.relative_to(ROOT)}")


def validate_wrapper() -> None:
    wrapper = ROOT / "gradle/wrapper/gradle-wrapper.jar"
    if not wrapper.is_file():
        fail("gradle-wrapper.jar is missing")
    else:
        try:
            with zipfile.ZipFile(wrapper) as archive:
                if "org/gradle/wrapper/GradleWrapperMain.class" not in archive.namelist():
                    fail("gradle-wrapper.jar does not contain GradleWrapperMain")
        except zipfile.BadZipFile:
            fail("gradle-wrapper.jar is not a valid JAR")
    gradlew = ROOT / "gradlew"
    if not gradlew.is_file() or not (gradlew.stat().st_mode & stat.S_IXUSR):
        fail("gradlew is missing or not executable")
    if "gradle-8.8-bin.zip" not in read(ROOT / "gradle/wrapper/gradle-wrapper.properties"):
        fail("Wrapper is not pinned to Gradle 8.8")
    wrapper_hash = hashlib.sha256(wrapper.read_bytes()).hexdigest() if wrapper.is_file() else ""
    if wrapper_hash != "cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8":
        fail("gradle-wrapper.jar does not match Gradle's published 8.8 checksum")
    if "distributionSha256Sum=a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612" not in read(ROOT / "gradle/wrapper/gradle-wrapper.properties"):
        fail("Gradle 8.8 distribution checksum is missing or incorrect")



def validate_network_message_imports() -> None:
    """Catch simple compile failures where an S2C packet is referenced without its import."""
    msg_pkg = "com.hexhaki.network.msg"
    for path in JAVA.rglob("*.java"):
        source = read(path)
        package = re.search(r"package\s+([\w.]+);", source)
        if package and package.group(1) == msg_pkg:
            continue
        if f"import {msg_pkg}.*;" in source:
            continue
        uses = set(re.findall(r"\b(S2C[A-Z]\w*)\b", source))
        imports = set(re.findall(rf"import\s+{re.escape(msg_pkg)}\.(S2C[A-Z]\w+);", source))
        missing = sorted(uses - imports)
        if missing:
            fail(f"Missing network packet import(s) in {path.relative_to(ROOT)}: " + ", ".join(missing))

def validate_balanced_java() -> None:
    token_pattern = re.compile(
        r"""("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|//[^\n]*|/\*.*?\*/)""",
        re.S,
    )
    for path in JAVA.rglob("*.java"):
        source = token_pattern.sub("", read(path))
        for opening, closing in (("{", "}"), ("(", ")"), ("[", "]")):
            depth = 0
            for char in source:
                if char == opening:
                    depth += 1
                elif char == closing:
                    depth -= 1
                    if depth < 0:
                        break
            if depth != 0:
                fail(f"Unbalanced {opening}{closing} in {path.relative_to(ROOT)}")



def validate_observation_11() -> None:
    controller = read(JAVA / "gameplay/HakiServerController.java")
    renderer = read(JAVA / "client/render/PerceptionRenderer.java")
    packet = read(JAVA / "network/msg/S2CPerception.java")
    techniques = read(JAVA / "data/HakiTechnique.java")
    props = read(ROOT / "gradle.properties")

    required_controller = (
        'futureSightBurstUntil',
        'futureSightCooldownUntil',
        'now + 36L',
        'now + 100L',
        'HakiData.consume(player, 24f)',
        'collectObservationAttackThreats',
        'collectObservationProjectileForecasts',
        'softenedHiddenPresence',
        'entries.size() >= 64',
        'forecasts.size() >= 16',
    )
    if not all(token in controller for token in required_controller):
        fail("Observation 1.1 server prediction / Future Sight contract is incomplete")

    required_packet = (
        'List<ProjectileForecast> projectiles',
        'List<ThreatCue> threats',
        'boolean futureSightActive',
        'boolean hidden',
    )
    if not all(token in packet for token in required_packet):
        fail("Observation 1.1 perception packet is missing forecasts/threats/hidden-presence state")

    required_renderer = (
        'drawPresenceCloud',
        'drawDirectionalCue',
        'FUTURE SIGHT',
        'HakiClientState.observation>=400',
        'HakiClientState.observation>=700',
    )
    if not all(token in renderer for token in required_renderer):
        fail("Observation 1.1 client prediction renderer is incomplete")

    if 'OBS_PINNACLE(HakiType.OBSERVATION, 1000, "Pinnacle Observation / Future Sight"' not in techniques:
        fail("Observation 1.1 pinnacle progression label is missing")
    events = read(JAVA / "gameplay/HakiEvents.java")
    absolute_obs = (
        'hasAbsoluteObservationDodge',
        'HakiData.mastery(player, HakiType.OBSERVATION) >= 1000',
        'public static void absoluteObservationDamage(LivingDamageEvent e)',
        'public static void hurt(LivingHurtEvent e)',
        'if (m >= 1000)',
        'e.setCanceled(true);',
    )
    if not all(token in events for token in absolute_obs):
        fail("1000 OBS absolute-dodge damage contract is incomplete")

    if 'mod_version=1.1.0' not in props:
        fail("Project is not stamped HexHaki 1.1.0")


def validate_last_stand_final_damage() -> None:
    events = read(JAVA / "gameplay/HakiEvents.java")
    required = (
        'LAST_STAND_FINAL_BLAST_RADIUS = 25.0D',
        'LAST_STAND_FINAL_BLAST_DAMAGE = 80.0F',
        'LAST_STAND_SCRIPTED_DAMAGE = "HexHakiAllowLastStandBlastDamage"',
        'putBoolean(LAST_STAND_SCRIPTED_DAMAGE, true)',
        'remove(LAST_STAND_SCRIPTED_DAMAGE)',
        '!lastStandCaster.getPersistentData().getBoolean(LAST_STAND_SCRIPTED_DAMAGE)',
        'HakiServerController.cancelAll(player);',
        'living.invulnerableTime = 0;',
        'player.damageSources().sonicBoom(player)',
        'player.damageSources().playerAttack(player)',
        'tag.equalsIgnoreCase("friendly")',
        'tag.equalsIgnoreCase("haki_friendly")',
        'tag.equalsIgnoreCase("hexhaki_friendly")',
    )
    if not all(token in events for token in required):
        fail("Final Stand final blast damage/friendly-immunity contract is incomplete")

def main() -> int:
    validate_json()
    validate_protocols()
    validate_sounds()
    validate_animations()
    validate_textures()
    validate_wrapper()
    validate_observation_11()
    validate_last_stand_final_damage()
    validate_network_message_imports()
    validate_balanced_java()
    if failures:
        print("Haki source validation FAILED", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1
    print("Haki source validation passed")
    print(" - every network VFX event has a Photon handler")
    print(" - every client action has a server handler")
    print(" - all player animations carry BendyLib bend channels")
    print(" - exact Galaxy Impact targeting/render anchoring checks passed")
    print(" - V2 blade slash, Observation prediction, and King's Grip cinematic contracts passed")
    print(" - JSON, animation, sound, texture, and wrapper checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
