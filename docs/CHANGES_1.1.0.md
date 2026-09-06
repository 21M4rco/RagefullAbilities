# HexHaki 1.1.0 — Observation Update

## Observation progression

- OBS 100: threat classification.
- OBS 240: probable movement / intent ghosts.
- OBS 400: projectile trajectory forecasting and directional projectile warnings.
- OBS 550: probable melee-intent warnings and limited vague through-terrain presence sensing.
- OBS 700: alternative future branches and improved hidden-presence resolution.
- OBS 850: wider Supreme Observation and earlier off-screen danger reads.
- OBS 1000: danger-triggered Future Sight burst. The burst lasts 36 ticks (1.8s), costs 24 Haki energy and has a 100-tick (5s) internal cooldown.

Prediction remains server-authored. Hidden entities are quantized/jittered before their location is sent to the client, and all snapshot lists are capped to avoid turning Observation into a multiplayer packet flood.

## Last Stand reliability fix

The final Last Stand detonation still uses a true 25-block-radius sphere and 80 raw damage (40 hearts) against hostile living targets only. Before the one-shot pressure hit, the victim's vanilla hurt-resistance timer is cleared so the blast cannot be swallowed by leftover damage i-frames. SONIC_BOOM pressure remains the primary armor-piercing source; entities that explicitly reject that damage type receive a player-attributed fallback hit. Friendly/allied/tag-protected creatures never enter the damage branch.

## Final gameplay hotfix
- Fixed Last Stand's final 10-heart detonation being canceled by its own caster attack lock. The authored passive hit now has a synchronous-only bypass while normal inputs/attacks remain blocked.
- Entering Last Stand now cancels all active Haki channels/toggles and every C2S Haki action is rejected until the kneeling state ends.
- At 1000 Observation mastery, active Observation now has an absolute dodge contract across LivingAttackEvent, LivingHurtEvent and LivingDamageEvent; projectile collision avoidance remains active at every Observation tier.
