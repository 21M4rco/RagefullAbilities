# HexHaki 1.1 — Observation / Future Sight

Version 1.1 is intentionally scoped to Observation Haki. Existing Armament, Conqueror, Galaxy, King's Grip, WiFi Haki, Last Stand and progression thresholds are left alone.

## Mastery behavior

| OBS | Observation feature |
| ---: | --- |
| 0 | Presence Sense for nearby visible living entities; existing absolute projectile-impact protection/body-slip system remains active while V is on. |
| 100 | Threat Reading classifies hostile/aggressive and exceptional presences. |
| 240 | Intent Reading adds server-authored probable movement ghosts. |
| 400 | Approaching projectile trajectories are rendered and dangerous paths get directional warning cues. |
| 550 | Combat Reading predicts probable melee intent before impact; through-terrain Presence Sense begins at a limited radius. Hidden positions are intentionally quantized/jittered server-side. |
| 700 | Alternative future branches become visible and hidden-presence resolution improves. |
| 850 | Supreme Observation expands through-terrain awareness and gives earlier off-screen danger reads. |
| 1000 | Pinnacle Future Sight: genuine imminent danger can automatically trigger a 36-tick (1.8s) forecast burst. It costs 24 Haki energy and has a 100-tick (5s) internal cooldown. |

## Multiplayer / performance rules

- Perception is server-authored; the client does not decide threats or Future Sight activation.
- Normal snapshots run at 2.5–5 Hz depending on mastery. Only the short 1000-OBS Future Sight burst temporarily refreshes at 10 Hz.
- Living-presence snapshots are capped at 64 entries, projectile paths at 16 and threat cues at 20.
- Supreme awareness keeps the existing maximum 80-block Presence radius, but through-wall sensing has a smaller mastery-scaled radius until pinnacle.
- Hidden entities are not rendered as exact body outlines: the server softens their coordinates before sending them.
- Prediction is deliberately probabilistic. Players can change input and mobs can retarget, so Future Branch ghosts communicate uncertainty instead of pretending the future is deterministic.

## Prediction semantics

Movement ghosts extrapolate current velocity and, for mobs, blend in their current navigation/combat target intent. Projectile forecasts use current server velocity and closest-approach math. Melee warnings use active mob targeting and close/readied player facing as probable intent. A real attack event remains the fallback for modded combat systems that bypass ordinary Mob targeting.
