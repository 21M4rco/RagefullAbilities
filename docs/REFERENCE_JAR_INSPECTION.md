# Reference JAR inspection — `trueprimepiecetwo` (TruePrimePiece 2)

Inspected build: `trueprimepiecetwoDOKUPATREON.jar`, 24.9 MB, 6484 entries.
Decompiler: Vineflower 1.10.1. Mappings in the JAR are SRG (`m_21023_` style), so
member names below are given by behaviour where the SRG name is unhelpful.

## Metadata and dependencies

| Field | Value |
| --- | --- |
| modId | `trueprimepiecetwo` |
| version | 1.0.0 |
| loader | `javafml`, `[47,)` — Forge 1.20.1 |
| authors | Adon, MCreator |
| license | Academic Free License v3.0 |

Declared dependencies, **all optional** (`mandatory=false`):
`kleidersplayerrenderer`, `pehkui`, `use_compiled_mods`, `geckolib`, `playeranimator`.

The mod is MCreator-generated: 1996 classes under `procedures/`, 344 under `potion/`,
236 under `entity/`. Gameplay logic lives in one-shot static `execute(...)` procedures
rather than in a designed controller layer.

## How the stretching actually works

**It is not limb deformation.** Nothing in the mod deforms the player model.

The chain, traced from input to renderer:

1. **Input → mob effect.** A skill procedure applies a marker `MobEffect`
   (`PISTOL`, `BAZOOKA`, `RIFLE`, `ROCKET`, `STAMP`, `REDHAWK`, plus `USINGMOVE` as a
   global "a move is running" gate).
2. **Spawn a second humanoid.** `GumPistolEntity` / `GumgatlingEntity` — a `TamableAnimal` —
   is spawned and **mounted on the player** (`startRiding`).
   `GumPistolOnInitialEntitySpawnProcedure` scans an 8-block radius for the nearest entity
   whose capability variable `devilfruit` equals `"Gomu"` and which has `USINGMOVE`, tames the
   entity to that player, mounts it, and then picks the GeckoLib animation from whichever
   marker effect is present. Each branch schedules `discard()` via
   `TrueprimepiecetwoMod.queueServerWork(n, ...)` — 10 ticks for pistol, 15 rocket,
   20 redhawk, 30 bazooka/stamp, 40 rifle.
3. **The clone is a whole body, not an arm.** `assets/.../geo/gumpistol.geo.json` has bones
   `Head, Body, RightArm, LeftArm, RightLeg, LeftLeg` (plus a `head2` pivot parent for the
   arms). It is a full second player-shaped mesh.
4. **Animation.** `animations/gumpistol.animation.json` holds `idle, rifle, redhawk, bazooka,
   rocket, stamp`. Reach comes from authored per-bone `position`/`rotation` keyframes on that
   clone — hand-keyed, not procedural.
5. **The real player is posed separately** by Player Animator
   (`assets/.../player_animation/pistol.json`).
6. **`GumPistolOnEntityTickUpdateProcedure`** copies the mount's yaw/pitch onto the clone every
   tick (`yRot`, `xRot`, `yBodyRot`, `yHeadRot` and their `prev` fields) so the clone tracks the
   player's facing.

### Skin handling

`GumPistolRenderer.getTextureLocation` genuinely uses the owner's skin: it resolves the owner
UUID via `MinecraftSessionService.getTextures(...)`, downloads the skin to
`<gamedir>/cached_skins/<hash>.png`, and registers it as `skins/<hash>`. Untamed clones fall back
to `textures/entities/gumtemplate.png`.

Two defects worth naming:

- **The slim/standard split is a no-op.** `GumPistolRenderer` holds
  `slimModel` and `defaultModel` but initialises **both** as `new GumPistolModel()` — the same
  geometry. `getModel(uuid)` computes `isSlim` and then returns identical models either way. It
  also leaves a `System.out.println("UUID: … isSlim: …")` in the render path.
- **Hiding the real arm is done by teleporting it.** In `player_animation/pistol.json` the
  `right_arm` position track opens with `"0.0": {"vector": [0, -74957, 0]}` — the vanilla arm is
  translated ~75 000 blocks down so it cannot be seen. That is why no duplicate limb appears.

## Enlargement

Pehkui, and only Pehkui. Across the whole JAR there are 24 uses of
`ScaleTypes.THIRD_PERSON` and 12 of `ScaleOperations.SET`, confined to
`Gum1skillProcedure` … `Gum6skillProcedure`. Gear 3 is literally:

```java
ScaleTypes.THIRD_PERSON.getScaleData(entity).setTargetScale(
    (float) ScaleOperations.SET.applyAsDouble(..., 6.0));
queueServerWork(30, () -> GearThreeHitboxProcedure.execute(world, x, y, z, entity));
queueServerWork(60, () -> ...setTargetScale(...1.0));
```

Uniform whole-body scale, set to 6, restored to 1 after 60 ticks. There is **no** independent
fist/foot scaling and no non-uniform deformation anywhere.

## Collision

`GumHitboxProcedure` steps a counter outward (`index0 < 9`) and for each step runs a
`Level.clip(new ClipContext(eyePos, eyePos + look * Scaling, OUTLINE, NONE, entity))`, stopping at
the first solid block, then queries entities in an AABB at the endpoint.

It is a **discrete ray march evaluated in a single tick**. There is no sweep across the fist's
motion between ticks, so a fast strike can pass a target without registering. Collision is tied to
the player's look vector, not to the animated fist's actual position.

## Synchronisation

Two mechanisms, both inherited rather than designed:

- The clone is a **real server-side entity**, so vanilla entity tracking replicates it to every
  observer for free. That is the whole of the "multiplayer stretching sync".
- Player state rides on an MCreator capability (`TrueprimepiecetwoModVariables.PlayerVariables`,
  fields `devilfruit`, `gear`, `hakiform`) with generated packets
  (`FruitspecialMessage`, `MoveswitchMessage`, `LoadoutswitchMessage`, `Quickuse1..7Message`).

## What is absent

Searched the entire JAR; these do not exist in it:

- **No Gear 5, Nika, Joy Boy, Bajrang Gun, or any `Dawn *` technique.** The only hit for those
  strings anywhere in the archive is inside `sounds.json`. The mod's Gum-Gum line stops at
  Gear 2 / Gear 3.
- **No ground or terrain deformation.** Zero procedures touch block state for a deformation
  effect.
- **No bending, twisting, compression or elastic recoil** beyond what an artist keyed into the
  GeckoLib clips.
- **No per-limb or non-uniform scaling.**
- **No swept/continuous collision.**
- **No skin-correct deformed geometry** — the skin is applied to a rigid pre-modelled mesh, and
  the "deformation" is that mesh's bone transforms.

## What this means for the Gear 5 build

Reusable as an approach: the marker-effect → animation-selection pattern, the owner-skin
resolution trick, and Pehkui for whole-body `Gigant`.

Not reusable, and therefore ours to build: every requirement in sections 5, 6, 7 and 8 of the
brief. Specifically, the brief's requirement that limbs be *continuous, skin-mapped and attached
at the shoulder and hip* is the exact thing this mod avoids by mounting a rigid second body and
teleporting the real arm out of frame. A separate mounted clone is explicitly disqualified by the
brief ("Neither does a detached floating fist"), so the stretched-limb renderer has to be written
from scratch rather than adapted.
