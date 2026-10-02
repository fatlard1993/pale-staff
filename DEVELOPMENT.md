# Pale Staff - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Building

`./gradlew build` builds the jar into `build/libs`. Pandorical is compiled as a sibling project (`../pandorical`, see `settings.gradle`).

## How it works

- Everything a staff holds is in vanilla components, so any client can draw it. `Imbuement` owns it: the spell (`Spell`: an effect, Flame, Bloom, Gust, Sonic Boom, Storm or Blink), the charge, the share of a potion each cast carries and the imbuing item live in `custom_data`. An effect is also `potion_contents`, for the tooltip, with `potion_duration_scale` set to one cast's share. The item model reads `custom_model_data` alone: its flag says imbued, its float is the charge, its string picks the socket, and its colour tints the bar.
- `Dose` reads what an ingredient gives. For an effect it asks the server's brewing recipes what the ingredient makes of an awkward potion, then of a water bottle. `ImbueStaffRecipe` is the shapeless crafting rule built on it.
- `Casting` is the cast: a hitscan bolt drawn in particles, which hands each creature struck and each block landed on to the spell. Effects use the game's own splash level event and `AreaEffectCloud`, `Flame` does what a fire charge does, `Bloom` converts mobs and plants flowers, `Gust` sets off the game's own wind-charge burst with an unspawned wind charge as its cause (so the game treats it as one), `Boom` is the warden's sonic boom, `Storm` the game's own lightning, and `Blink` a thrown pearl's teleport, cost and all. `Trap` is Lingering's share for Gust, Boom and Storm, and Blink's gate: a vanilla `AreaEffectCloud` carrying no effects, tagged with its spell, its Potency and, for a gate, where it sends things, re-armed from its tags on `ENTITY_LOAD`, and checked each level tick for entities that have newly stepped in. The crossbow's enchantments come in through its item tag; their vanilla effects never fire on a staff, so `Casting` reads their levels.
- The moobloom is `data/pale-staff-justfatlard/cow_variant/moobloom.json`, a cow variant with no spawn conditions. Bloom's babies are age-locked through `AgeableMobMixin`, which exposes the game's protected lock via `access/AgeLocking`.
- `StaffLoot` appends a pool to the creaking's and the pale oak leaves' vanilla loot tables.
- `PaleStaffConfig` is `config/pale-staff.json`; `StaffSettings` puts it on Pandorical's settings page for ops, saving each change to the file.

## Art

`python3 generate_textures.py` draws the staff, the fallback gem, the charge bar and the icon, and writes every model, including `items/pale_staff.json`. It paints the moobloom from the game's cow. For each item that can imbue a staff (the brewing ingredients, read from the jar's brewing recipes, and the specials), it reads that item's sprite out of the Minecraft jar in the Loom cache and writes a socket model cropping the most colourful five-pixel window of it. The item model picks that socket by the id in `custom_model_data`'s first string. It needs Pillow.

## Tests

`xvfb-run -a -s "-screen 0 2048x1152x24" ./gradlew runClientGameTest` runs the smoke test and `StaffWorks`. `StaffWorks` crafts through the real recipe manager (brewing ingredients fill, top up and swap; potions, flowers and food are refused; a full staff refuses more), casts at a cow (effect, charge, wear), checks Splash reaches a second cow, Flame burns a cow, lights the ground and refuses a self-cast, Bloom makes a baby age-locked moobloom, turns a zombie into a baby creature and flowers the ground, Gust blows a cow away and wind-jumps the caster, Sonic Boom hurts a zombie through a wall and refuses a self-cast, each one's Lingering trap springs on a mob walked into it and spares its caster, Storm strikes a pig and refuses a self-cast, Blink moves the caster and costs a pearl's damage, its gate sends a following cow back, and it trades places with a struck cow, and a drained Wellspring staff draws a charge back while carried. It also frames the hotbar and a moobloom in `build/run/clientGameTest/screenshots/bloom.png`, the picture at the top of the readme.
