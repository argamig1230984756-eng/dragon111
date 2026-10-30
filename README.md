# Void Dragon – 3-phase boss fight (v2)

Standalone Paper plugin (1.21.x, Java 21) that adds a second phase to your Void Dragon fight.

## Fight overview (v2)
The dragon now **attacks from the start** and escalates. Attacks stack and are all telegraphed.

| Phase | Starts | New attacks |
|---|---|---|
| 1 | on spawn | Fireballs, Lightning strikes |
| 2 | 50% HP | Void Beam sweep, Shockwave (jump it), shielded crystals + wraiths, Void Rain |
| 3 | 25% HP | Gravity Well, Meteor rain, double shockwave, much faster attack rate |

Test commands: `/voiddragon attack <fireballs|lightning|beam|shockwave|gravity|meteors>`, `/voiddragon phase2`, `/voiddragon phase3`, `/voiddragon status`.
Tune everything in `config.yml`. No model engine needed: the dragon gets a particle aura per phase. Custom models still need a ModelHook (see below).

## What phase 2 does
1. **Trigger** – the dragon is capped at 50% health (`phase2.health-threshold`). The hit that would pass it starts the transition instead.
2. **Transition (6s)** – dragon hovers and roars, is invulnerable, lightning hits the crystal spots, portal rings pulse, model swaps to `void_dragon_enraged`, boss bar turns red.
3. **Shielded crystals** – every pillar crystal is respawned if it was destroyed, then wrapped in a purple shield. While a shield is up:
   - it beams to the dragon and **heals it** (3 HP/s per crystal),
   - the dragon takes only **25% damage** while *any* shield is up,
   - the crystal cannot be damaged.
4. **Void Wraiths** – each shield is guarded by 3 wraiths (invisible Vex + your `void_wraith` model). Kill them all and the shield **shatters**; the crystal can now be destroyed.
5. **Regrowth** – a shattered shield regrows after 40s with 2 new wraiths, unless you destroyed the crystal. Destroyed crystal = its wraiths vanish and it stops healing the dragon.
6. **Void Rain** – every 14s up to 3 players get a closing red ring under them (2s warning), then a void blast (8 dmg, radius 3.5). Move to dodge.

Everything is in `config.yml`.

## Commands
`/voiddragon start` – spawn a dragon in the End and adopt it
`/voiddragon phase2` – force phase 2
`/voiddragon reset` – drop fight state
(permission `voiddragon.admin`)

## Hooking up your models
The fight logic never touches a model engine directly. Implement `ModelHook`
(`attachDragon`, `attachWraith`, `setDragonEnraged`, `setDragonAnimationSpeed`, `detach`)
with BetterModel or ModelEngine and register it:

    getServer().getServicesManager() ... or simply: plugin.setModelHook(new MyHook());

Do that in your own plugin right after this one enables (or merge the classes into your plugin).
Until then a logging hook is used, so the fight works with the vanilla dragon model.

Suggested mapping: `setDragonEnraged(true)` = replace the `void_dragon` model with `void_dragon_enraged`
(same bones and "fly" animation); wraiths use `void_wraith` (an `void_wraith_enraged` is included if you want
them red after phase 2).

## assets/
- `void_dragon_enraged.bbmodel/.png` – your dragon with a crimson/orange retexture (same UV layout, so you can also just swap the texture)
- `void_wraith_enraged.bbmodel/.png` – same for the wraith

## Build
    gradle build      # jar ends up in build/libs/

If you're on 1.21.3 or newer nothing needs changing (attribute lookup handles both naming schemes).

## Credit / licence note
Ideas (shielded crystals, healing beams, a stage transition, a red damaged-dragon variant) were inspired by
McMakistein's "Impossible Dragon" datapack, which is CC BY-NC-ND. No code, models or textures from it are
used here; everything in this project was written from scratch.


## v2.1 additions (nothing removed)
| New attack | What it does |
|---|---|
| `dive_bomb` | Dragon hovers, tracks you, **locks the spot 0.6s before** plunging. Crash = big blast + shockwave (jump it). Clips you on the way down. |
| `tail` | Tail whip: a red sector is shown on the ground, then the blade sweeps across it. Step out of the sector or hide under the dragon (inner safe zone). |
| `breath` | Precise breath beam: warning line, then a beam that follows you at limited speed and only hurts along its line. Strafe to dodge. |
| `minions` | Summons Void Minions (glowing vexes). Also called at the start of phase 2 and 3. |

- **Damage**: every attack (old and new) now goes through `attacks.damage-multiplier` (default 1.6).
- **Precision**: fireballs, lightning and meteors lead your movement and land close to you instead of ±4-6 blocks off.
- **Dome shield**: shielded crystals get a purple glass dome with spinning runes and electric arcs (`shield.dome-radius`).
- New test commands: `/voiddragon attack <dive_bomb|tail|breath|minions>`.
