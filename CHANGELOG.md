# Changelog

## v1.0.0 (2026-08-24)

First release. A fully server-side corpse mod for Fabric / Minecraft 26.2 — vanilla clients need nothing installed.

- Death leaves a lootable corpse wearing the player's skin (packet-only fake player in the sleeping pose; no client mod needed).
- Corpse holds the full inventory (armor, offhand, hotbar, main) plus stored XP; right-click opens a chest-style screen, sneak-right-click returns everything to its original slots.
- Body physics ported from Fallen: falls and settles on the ground, floats on lava/water pools, held just inside the world over the void, re-falls when the block beneath is broken.
- Owner-locked corpses with configurable public unlock and despawn-with-drop timers; ops bypass.
- `/deathhistory` command (self for everyone, other players for ops).
- Config at `config/ssc.json`.
