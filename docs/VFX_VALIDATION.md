# Release-blocking VFX validation

The previous reference build could send visual enum values with no matching renderer case. That failure is now blocked in three places:

1. `validateHakiResources` compares the complete network visual enum with `HakiVfx` switch cases.
2. `tools/validate_source.py` checks protocol coverage, JSON, sounds, animations, textures, and the Gradle wrapper without compiling.
3. The Photon launcher rejects empty compositions and logs a client error if Photon fails to create a runtime.

## In-game test

Use a real client connected to a dedicated server and keep a second player nearby:

```text
/haki vfx <player> all
```

Individual groups:

```text
/haki vfx <player> armament
/haki vfx <player> conqueror
/haki vfx <player> shockwave
/haki vfx <player> aura
/haki vfx <player> convergence
```

The command spaces events twelve ticks apart to avoid one composition hiding another.

## Required observations

| Test | Must be visible |
| --- | --- |
| Armament activation | dark coating shell, sparks, low reinforcement ring |
| Armament hit | forward compressed-air cone, air ring, impact sparks |
| Ryuo | fist compression, projected air burst, optional mastery lightning |
| Conqueror charge | six escalating aura/pressure stages; lightning begins only after trained mastery |
| Conqueror charge/release | ground ring, red shell, air displacement, and shared Final-Stand-style radial lightning whose endpoints mark the authoritative charge-scaled range |
| Supreme/Joy Boy release | huge red/black families, sky fracture, horizon cracks, delayed waves and aftershocks |
| Convergence charge | fist core, Armament compression, Conqueror arcs, escalating aura |
| Convergence hit | white impact flash, large air shell, pressure ring, outward red/black explosion |
| Observation | readable presence silhouettes and danger direction without a full-screen wash |

Both players must see the same seeded lightning geometry. Check `latest.log` for `HexHaki Photon composition failed`, `Photon did not create a runtime`, or `Rejected an empty ... composition`; any occurrence is a release blocker.

## Static validation

```bash
python3 tools/validate_source.py
```

This does not compile or launch Minecraft. It is safe to run while editing resources.
