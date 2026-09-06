# HexHaki 1.1.0

Forge 1.20.1 source release for the Haki-only HexHaki mod. No Devil Fruits or unrelated power systems.

## Core progression

HexHaki has independent Armament, Observation and Conqueror mastery paths from 0 to 1000. Mastery is XP-backed, persistent and server-authoritative. Techniques unlock through the middle of the grind and continue scaling; Advanced Haki is the late-game multiplier, and Joy Boy Presence is the capstone.

| Technique | Requirement | Control |
| --- | --- | --- |
| Observation / Presence Sense | HexHaki enabled | V |
| Armament + Haki Blade Cuts | ARM 40 | R / sword swing |
| Haki Leap | ARM 240 | Shift + Space |
| Conqueror Awakening | ARM 240 + OBS 240 | Hold G |
| King's Grip | ARM 340 + OBS 300 + HAO 320 | K |
| Flowing Armament / Ryuo | ARM 400 | passive milestone |
| WiFi Haki | ARM 450 + OBS 450 + HAO 500 | Hold L |
| Galaxy Impact: Haki Wave | ARM 520 | M release below 100% |
| Full Galaxy Impact | ARM 650 + OBS 550 + HAO 650 + Legend 20 | M to 100%, then Attack |
| Internal Destruction Control | ARM 700 | passive milestone |
| Advanced Haki | ARM 850 + HAO 850 + active Armament | J |
| Joy Boy Presence | ARM/OBS/HAO 1000 + Legend 120 | automatic |

Ryuo/Internal Destruction give real emitted-technique power steps in 1.0. WiFi Haki grows from 60 blocks at unlock to 160 at pinnacle. Full Galaxy Impact costs 120 Haki energy and has a fixed 60-second cooldown.

See `docs/PROGRESSION.md` for exact progression/training rules.

## Observation 1.1

Observation now has a real prediction ladder instead of stopping at outlines/auto-dodge: movement ghosts at OBS 240, projectile path forecasting at 400, pre-attack/off-screen danger reading and limited vague terrain sensing from 550, future branches at 700, wide Supreme awareness at 850, and danger-triggered **1.8-second Future Sight bursts at OBS 1000**. Pinnacle bursts cost 24 Haki energy and have a 5-second internal cooldown; they are not permanently active. Hidden presences are softened server-side rather than rendered as exact wallhack silhouettes.

See `docs/OBSERVATION_1.1.md` for the exact rules and multiplayer caps.
Smoke-test steps are in `docs/OBSERVATION_1.1_TEST_PLAN.md`; release changes are in `docs/CHANGES_1.1.0.md`.

## Persistent energy drain

- Armament: 4 energy/sec
- Observation: 7 energy/sec
- Advanced Haki: 8 energy/sec total instead of normal Armament upkeep
- Observation stacks with the active Armament tier
- Energy regeneration resumes when persistent Haki states are off

## Final Last Stand

At maximum Conqueror mastery, lethal damage can trigger the 14-second kneeling Last Stand. Hostile mobs in its true 50-block-diameter sphere are controlled/stunned; the final pressure blast uses the same sphere and deals 40 hearts (80 damage) to hostile targets. Friendly-tagged/allied creatures receive no Last Stand damage. Cooldown: 20 minutes.

## Commands

Both `/haki` and `/hh` are aliases and require permission level 2.

```text
/haki status <player>
/haki set <player> <true|false>
/haki mastery <player> armament|observation|conqueror set <0..1000>
/haki mastery <player> armament|observation|conqueror add <-1000..1000>
/haki mastery <player> all set <0..1000>
/haki mastery <player> max
/haki mastery <player> reset
/haki energy <player> <0..1500>
/haki energy unlimited on|off
/haki energy unlimited <player> on|off
/haki legend <player> <0..9999>
/haki joyboy <player> <true|false>
/haki board on|off
/haki cooldowns off|on
/haki cooldowns <player> off|on
```

Fresh players have HexHaki disabled until an operator runs `/haki set <player> true`.

## Build

- Minecraft 1.20.1
- Forge 47.4.10+
- Java 17
- Gradle 8.8 wrapper

Windows:

```bat
gradlew.bat build
```

Linux/macOS:

```bash
./gradlew build
```

The built JAR is written to `build/libs/`. Runtime dependencies are declared in `build.gradle` / `mods.toml`.
