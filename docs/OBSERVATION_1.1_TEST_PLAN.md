# HexHaki 1.1 Observation smoke test

Use `/haki set <player> true`, enable Observation with V, and set the requested OBS mastery for each test.

1. OBS 0: visible nearby living entities are sensed; projectile protection still works.
2. OBS 100: hostile/aggressive and exceptional targets classify differently.
3. OBS 240: moving mobs/players create probable future-position ghosts.
4. OBS 400: incoming arrows/projectiles draw a forecast path and dangerous approaches create a direction cue.
5. OBS 550: a mob actively targeting the player produces a pre-impact warning; nearby entities behind terrain appear only as vague presence clouds.
6. OBS 700: predicted movement gains faint alternate branches; hidden sensing is more precise but not body-perfect.
7. OBS 850: off-screen/through-wall hostile pursuit can be warned earlier within the limited combat-read radius.
8. OBS 1000: genuine imminent melee/projectile danger automatically triggers FUTURE SIGHT for 1.8s, spends 24 energy once, and cannot retrigger for 5s.
9. Multiplayer: another player changing direction should make future ghosts update rather than pinning them to an old prediction. Observation visuals remain private to the Observation user.
10. Load: spawn a crowded group plus projectiles; snapshots stay capped at 64 presences, 16 projectile forecasts and 20 threat cues.

Last Stand regression: at 1000 HAO, trigger Last Stand near a hostile with more than 20 HP. One second before death the final blast must apply the configured 20-damage hit through the pressure path (with a fallback for modded entities that reject SONIC_BOOM), while a creature tagged `friendly`, `haki_friendly` or `hexhaki_friendly` takes zero Last Stand damage.
