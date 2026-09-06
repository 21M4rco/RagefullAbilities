# HexHaki 1.0.1 — Ryuo Direction Hotfix

- Ryuo release now uses an authoritative server packet containing the exact fist origin and look vector.
- Damage, reach, knockback, pressure rings, air streaks and Haki lightning all share the same forward vector.
- Removed the old entity-local rotated cone and radial lightning-family release that could spray vertically/behind the player.
- Added forward-only black/red Ryuo lightning and perpendicular pressure discs along the punch path.
- Added direction-locked air motes with explicit forward velocity.

# HexHaki 1.0.0 FINAL — Galaxy Impact endgame pass

## Advanced Galaxy Impact
- The upgrade now keys specifically from **J / Advanced Haki (ACoC)**, snapshotted when the full Galaxy sequence commits. Normal Armament alone no longer grants the endgame variant.
- The fist-glued red charge core becomes much larger under Advanced Haki and gains layered violet, electric-blue, cyan, magenta and white star-like Photon energy around it.
- Advanced detonation Photon shells, core, pressure fronts and particle density are roughly doubled relative to the normal full Galaxy Impact.
- Real blast radius is progression-aware: **64 blocks at the all-780 + Legend 40 unlock**, scaling with average mastery and Legend toward **76 blocks**, with **78 blocks at Joy Boy**. The normal full Galaxy Impact remains 40 blocks.
- Advanced impact damage receives a modest late-progression multiplier while radius remains the primary power increase.
- Advanced impacts create **11 overlapping ground-lightning pulses across 100 ticks (~5 seconds)**. Thick black/red Conqueror lightning crawls horizontally from the crater instead of relying on sky-to-ground bolts.
- Ground-lightning damage is localized around server-authoritative sample points to preserve survival counterplay.

## Cooldown
- Firing full Galaxy Impact now always sets a **1200-tick / 60-second cooldown**, regardless of mastery or Joy Boy status.
- If the player reaches 100% while that cooldown is still active and keeps holding, the move can commit automatically once the cooldown expires instead of becoming stuck at 100%.

## Release
- Project version set to **1.0.0**.
- Network protocol bumped for the radius-aware ground-lightning packet.

## 1.0.4 — Galaxy Tap Haki Tiers
- M tap now snapshots the active coating at release: uncoated / Armament / Advanced Haki.
- Uncoated tap damage is heavily reduced and has no black/red Haki lightning; cooldown is 25 ticks (1.25s).
- Normal Armament tap preserves the existing damage, lightning VFX, and mastery-scaled cooldown.
- Advanced Haki/J tap adds a dedicated red energy-mote layer along the fist and pressure lane; cooldown is 140 ticks (7s).
- Galaxy tap packet now carries the coating tier; network protocol bumped to 20.



## 1.0.12 official release
- Active Armament grants Speed II.
- Advanced Haki / ACoC replaces it with Speed III and Jump Boost I.
- Added operator testing commands: `/haki cooldowns off|on` and targeted variants.
- Testing no-cooldown mode bypasses Ryuo, Conqueror, Dominion, King's Verdict, Haki Wave/Galaxy Impact and Observation dodge cooldown gates without changing normal survival balance.

## 1.0.14 — Galaxy Tap Punch Sound Hotfix
- Replaced only `galaxy_wave_punch.ogg` with the user-supplied punch SFX.
- Converted to mono 48 kHz and reduced to approximately 50% source loudness.
- Full-charge Galaxy Impact audio is unchanged.
