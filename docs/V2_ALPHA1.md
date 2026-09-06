# HexHaki V2 — Alpha 1

Base: 1.0.22 ENERGY-ACTIVATION-FINAL
Target: Forge 1.20.1 / Java 17

## V2 systems in this alpha

### Real Haki blade cuts
- Left-clicking with a vanilla SwordItem while Armament is active sends a server-authoritative Haki cut.
- Cuts alternate horizontal / vertical every committed click.
- The server owns range, cooldown, energy, block clipping, collision and damage.
- Range scales with Armament mastery; ACoC extends/empowers the cut.
- Client rendering uses moving crescent geometry (black Haki shell + red inner edge), not smoke pretending to be a slash.
- High mastery / ACoC adds travelling black-red lightning fractures.
- Haki cuts intentionally keep the default Minecraft sword swing; no custom sword-swing animation overrides are used.

### Observation Haki 2.0
- Observation scans nearby projectiles every server tick and solves predicted closest approach before impact.
- A predicted hit causes a real collision-aware sidestep, with mastery-scaled read distance, reaction horizon, displacement and cooldown.
- Existing projectile-impact cancellation remains only as a latency/modded-projectile failsafe.
- Perception packets now include a probable movement vector and confidence for each sensed entity.
- Future Sight renders motion trails and future-position body ghosts; high mastery displays faint alternative branches to communicate uncertainty.
- Reauthored left/right Observation dodge animations use the whole body, torso/head evasion and planted legs.

### King's Grip — Dominion replacement
- The old charged Dominion AoE is removed from gameplay.
- K now starts a committed single-target grab when all three Haki paths are at 1000 mastery.
- 5.5 block precision target acquisition with line-of-sight and large-target rejection.
- Server pins attacker and victim into a synchronized throat-grab position for the full cinematic.
- Short targets are lifted toward hand height.
- Separate attacker/victim Player Animator tracks.
- Separate attacker/victim camera sequences with server-authored facing angles.
- Player victims are forced into the cinematic camera for the sequence and cannot movement-input their way out of the authoritative grab.
- Tier-authored impact timing is synchronized to the 4.6s / 5.4s / 7.4s cinematic tracks; the punch lands only after the suspended hold/wind-up.
- Surviving victims are released immediately after the impact freeze and launched hard; player velocity is explicitly synchronized.
- Grab action/movement cleanup handles death, logout and cancellation.

## Network
Protocol was originally bumped from 22 to 23 because Perception/Cinematic payloads changed and `S2CBladeSlash` was added.
Hotfix 13 bumps it again to **24** because `S2CArmamentImpact` now carries a presentation-style discriminator for King's Grip's all-3D impact path.

## Validation
`tools/validate_source.py` passes with the V2 changes. A full Gradle compile could not be completed inside the generation environment because its Gradle wrapper attempted to fetch Gradle 8.8 from `services.gradle.org`, which is blocked there. The source therefore remains compile-ready for the normal GitHub/Gradle environment but has not been falsely labelled as locally compiled.

## Hotfix 2 - King's Grip camera stabilization
- King's Grip now uses a planted side/profile camera for both attacker and grabbed player.
- Removed orbiting/cross-side camera cuts and most roll/yaw motion.
- Camera only performs a subtle push-in before the gut punch and a very small impact jolt.
- Local movement input is suppressed while the King's Grip cinematic owns the player body.
- Server pinning no longer sends redundant teleport/motion corrections every tick once participants are already in position, reducing multiplayer camera jitter.

### Hotfix 3 — forward-facing blade wave geometry
- Blade slash curvature now bows into the projectile's travel direction instead of curving sideways in the camera plane.
- Vertical cuts remain vertical and horizontal cuts remain horizontal from the attacker's view.
- Centre of each wave leads while the tips trail, producing a forward-facing flying Haki cut.
- ACoC lightning now follows the same forward-bowed geometry.

## Hotfix 4 - King's Grip execution re-animation
- King's Grip is now authored as: RIGHT-hand throat grab -> lift/hold victim off the ground -> LEFT-hand gut punch -> hard straight-forward launch.
- The victim is visibly suspended for a long hold instead of being hit almost immediately.
- Three committed versions are selected from the Haki state at grab time:
  - No coating: ~4.6s, 28 energy, physical gut punch with compressed air blasting through/out the victim's back.
  - Armament: ~5.4s, 80 energy, longer outward LEFT-fist chamber plus black/red Haki lightning at impact.
  - Advanced/J Haki: ~7.4s, 450 energy, very long outward LEFT-elbow chamber, repeated all-3D Photon fire ignition on the left fist, much heavier damage/lightning and the strongest launch.
- Tier is frozen when the grab connects so toggling Haki cannot desynchronize camera/animation timing.
- Launch is almost purely horizontal and scales 5.5 / 7.5 / 10.5 velocity by tier.
- Both parties retain the stable side/profile camera; only the impact instant receives a small camera jolt.


## Hotfix 5 - Reference-pose King's Grip camera/animation
- Reframed King's Grip to match the supplied Broly/Goku reference: pulled-back stable side shot with both full bodies visible.
- Removed explicit camera shake/roll from the sequence; impact uses only a tiny positional recoil.
- Eliminated opening dead air: the first visible motion is the RIGHT-hand throat catch, followed immediately by the slow lift.
- Raised the player-sized victim higher (about one block of clear foot lift) and extended the lift curve before the hold.
- Reauthored all three attacker tracks around a three-quarter turned torso, rigid right-arm throat hold, free left fist, and fully animated asymmetric legs/hips.
- King's Grip is explicitly allowed to animate both legs: wide planted stance, weight shift during lift, loaded rear leg during cock-back, and lower-body drive through the punch.
- Victim tracks now hang more naturally with arms down and loose bent legs before folding around the gut punch.




## Hotfix 13 - Sword animation removal / 3D Grip VFX / snap punch
- Removed the custom Haki sword-swipe Player Animator tracks entirely. Vanilla sword swing animation is preserved while the travelling Haki slash still fires.
- King's Grip lock, Haki impact, Advanced wind-up fire and Advanced impact now use dedicated 3D BeamEmitter geometry rather than camera-facing particle flakes.
- The directional air packet carries a King's Grip presentation style so the cinematic suppresses flat vanilla CLOUD/POOF motes while preserving true 3D pressure hoops and streaks.
- Advanced King's Grip fire is anchored to the LEFT fist (negative-X local arm) instead of the right-hand throat side.
- Reauthored the LEFT punch chamber: the elbow flares outward from the torso, forearm folds, the pose holds until two ticks before impact, then the fist snaps directly forward in ~0.10s.


### Hotfix 14
- Removed all King's Grip orbital/circular VFX geometry.
- Advanced King's Grip fire now follows the live animated LEFT hand via the Player Model arm transform.
- Replaced flat/root-offset wind-up fire with true 3D fist-local volumetric boxes/tongues.
