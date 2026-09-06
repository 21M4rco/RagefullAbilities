# HexHaki 1.1 progression

## Philosophy

V2 uses **techniques first, perfection later**. Signature moves arrive during the middle of the grind so the player can train by actually using Haki. Mastery then improves range, damage, duration, control, charge speed and presentation. **Advanced Haki [J] is intentionally late** because it is the multiplier that turns already-learned techniques into their endgame forms.

Mastery remains persistent and server-authoritative:

```text
XP(level) = 40 × level + floor(0.3 × level²)
```

Level 1000 requires 340,000 XP in one path. Source cooldowns prevent one action from granting XP every tick.

## Technique-use training

Successful technique use grants the paths that technique actually exercises:

- **Armament:** living-target hits, coated hits, guarding, Blade Cuts, Haki Leap, King's Grip impact, Galaxy attacks.
- **Observation:** surviving/exploring, danger reads, real projectile auto-dodges, King's Grip targeting, successful WiFi Haki locks.
- **Conqueror:** only after awakening; pressure releases, Advanced-Haki combat, King's Grip, WiFi Haki, Galaxy Impact and King's-Haki feats.
- **WiFi Haki:** longer successful channels train more than a tiny tap, with anti-spam cooldowns still applied.
- **King's Grip:** XP is awarded on the actual gut-punch impact, not on pressing K into empty air.

## Main unlock ladder

| Technique | Requirement | Control |
| --- | --- | --- |
| Observation / Presence Sense | HexHaki enabled | V |
| Armament Reinforcement + Blade Cuts | ARM 40 | R / vanilla sword swing |
| Haki Leap | ARM 240 | Shift + Space |
| Conqueror Awakening | ARM 240 + OBS 240 | Hold G |
| King's Grip | ARM 340 + OBS 300 + HAO 320 | K |
| Flowing Armament / Ryuo | ARM 400 | Passive mastery milestone |
| WiFi Haki | ARM 450 + OBS 450 + HAO 500 | Hold L |
| Galaxy Impact: Haki Wave | ARM 520 | Hold/release M before 100% |
| Full Galaxy Impact | ARM 650 + OBS 550 + HAO 650 + Legend 20 | M to 100%, then Attack |
| Internal Destruction Control | ARM 700 | Passive mastery milestone |
| Advanced Haki | ARM 850 + HAO 850 + active Armament | J |
| Joy Boy Presence | ARM/OBS/HAO 1000 + Legend 120 | automatic |

## Why King's Grip and WiFi Haki unlock before endgame

They are progression tools, not pinnacle-only rewards.

**King's Grip** starts as the physical/no-Haki version and its existing Armament-scaled damage continues growing toward 1000. Active Armament upgrades the hit; late-game Advanced Haki unlocks the fire/lightning execution tier.

**WiFi Haki** starts with enough mastery to be impressive but not perfected. Range is 60 blocks at the unlock floor, is controlled jointly by Armament + Observation, and reaches 160 blocks at 1000/1000. The channel keeps its 1 / 2 / 3 beam behavior depending on no coating / Armament / Advanced Haki. Max channel duration scales from 4.5 seconds at its unlock floor to 6 seconds at maximum mastery. Cooldown still scales with actual usage from 10 seconds to 50 seconds.

## Advanced Haki

Advanced Haki is intentionally pushed to **ARM 850 + HAO 850**. It is not another ordinary move unlock; it is the late-game multiplier that upgrades nearly the whole kit:

- stronger King's Grip execution tier,
- third WiFi Haki beam,
- stronger Blade Cuts,
- Advanced Galaxy effects,
- stronger coating passives and black/red King's lightning.

This keeps the 850–1000 grind meaningful even though most actual techniques are already available.

## Observation

Observation is a complete active progression path, not a simple outline toggle. OBS 100 begins threat classification; 240 adds probable movement ghosts; 400 exposes projectile trajectories; 550 adds pre-attack warnings and limited deliberately-vague terrain sensing; 700 shows alternate future branches; 850 widens Supreme awareness and off-screen danger reads. At **OBS 1000**, genuine imminent danger can trigger a **1.8-second Future Sight burst** with 1–2 second forecasting. The burst costs 24 energy and has a 5-second internal cooldown, so pinnacle prediction is powerful without being permanently active. Existing projectile immunity/body slips remain intact. See `OBSERVATION_1.1.md`.

## Galaxy Impact

**Haki Wave:** ARM 520. Releasing M before full-ultimate commitment fires the forward pressure-cylinder punch. Charge and mastery increase its size, damage and knockback. Ryuo at ARM 400 and Internal Destruction at ARM 700 now provide real 8% / 16% emitted-technique damage steps; Advanced Haki adds a further modest bonus.

**Full Galaxy Impact:** ARM 650 + OBS 550 + HAO 650 + Legend 20. Reaching a true 100% ground charge commits the aerial ultimate, and the Attack key fires the downward punch. It costs 120 Haki energy on firing and keeps a fixed 60-second cooldown. Damage/radius continue scaling after unlock; Advanced Haki and Joy Boy remain the major visual/power escalations.

## Cooldowns

Most cooldowns remain survival constraints and some shrink with mastery. Important fixed/special cases:

- King's Grip: **10s physical / 20s Armament / 30s Advanced**.
- WiFi Haki: **10s minimum → 50s maximum**, based on actual channel usage.
- Full Galaxy Impact: **60s fixed** after firing.

## Legend and Joy Boy

Legend is earned from advanced feats with anti-farm cooldowns. Full Galaxy Impact now starts at Legend 20 instead of being pushed into the extreme endgame. Joy Boy remains the true capstone at **1000/1000/1000 + Legend 120**.

The **P** menu is the authoritative in-game roadmap. In 1.0 it is a clickable HexHaki pirate logbook with wrapped technique descriptions, requirements, training hints and current path progress.


> 1.0 note: the dedicated Ryuo Punch / B-key ability has been removed. Ryuo is now represented as an Armament mastery milestone rather than a standalone move.


## Final 1.0 balance audit

- Ryuo (ARM 400) and Internal Destruction (ARM 700) are now functional emitted-force milestones: 1.08x and 1.16x on Haki Blade Cuts / Haki Wave rather than UI-only labels.
- Advanced/J Haki Wave receives a further 1.12x damage modifier to justify its longer 7-second tap cooldown.
- WiFi Haki range now scales from 60 blocks at unlock to 160 at 1000/1000 instead of front-loading most of its reach immediately.
- Full Galaxy Impact costs 120 Haki energy on release; its 60-second cooldown and authored damage/radius are unchanged.
- Conqueror pressure-release training favors committed charge: the XP unit award scales from 1 on a feather-tap to 9 at true MAX.
- No approved King's Grip damage curve, Last Stand rules, Observation projectile immunity, persistent Haki drain rates, or unlock thresholds were changed in this pass.
