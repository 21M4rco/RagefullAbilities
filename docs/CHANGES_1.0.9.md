# HexHaki 1.0.9 — Joy Boy Conqueror Knockback Hotfix

- Full-charge Conqueror blast propulsion is now independent from the 75-block stun core.
- Every living mob/player inside the full MAX release radius receives forced radial launch state.
- Joy Boy full release therefore pushes targets through the full 120-block radius.
- Blast motion is reasserted at server LevelTick END so AI, bosses, modded movement logic and friction cannot overwrite it.
- Server players receive authoritative motion packets during the pressure-carry window.
- Non-player living entities receive collision-aware positional carry in addition to velocity, bypassing knockback-resistance-style movement suppression.
- Temporary NoAI overrides are disabled during launch and original NoAI state is restored after the effect.
- Forced-stunned entities cannot attack during the launch window.
