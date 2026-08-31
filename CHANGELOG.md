# Changelog

## v1.1.0 (2026-08-31)

Diagnostics and self-healing for missing bodies, plus claim-mod compatibility.

- **Bodies re-send themselves.** The body is drawn with packets, so a client that rebuilds its world — respawning, changing dimension, reconnecting — silently dropped it and was never told again, leaving that one player staring at nothing while everyone else saw the corpse. A resync now checks every two seconds and re-sends whatever a client is missing.
- **`/ssc list`** — every body the server is holding: owner, position, dimension, item and XP count, age, and **how many players can currently see it**.
- **`/ssc resend [player]`** — force a player's bodies to be re-sent immediately.
- **`/ssc debug <true|false>`** — log every decision the death handler makes, including each reason it declines to create a body.
- **Clearer failure logging.** A body that can't be placed now logs the player, position, dimension and the likely cause, instead of a bare warning.
- **Open Parties and Claims support.** Ships an entity tag, `#ssc:corpses`, for OPAC's protection config, and prints the exact config line at startup when OPAC is installed.

## v1.0.0 (2026-08-24)

First release. A fully server-side corpse mod for Fabric / Minecraft 26.2 — vanilla clients need nothing installed.

- Death leaves a lootable corpse wearing the player's skin (packet-only fake player in the sleeping pose; no client mod needed).
- Corpse holds the full inventory (armor, offhand, hotbar, main) plus stored XP; right-click opens a chest-style screen, sneak-right-click returns everything to its original slots.
- Body physics ported from Fallen: falls and settles on the ground, floats on lava/water pools, held just inside the world over the void, re-falls when the block beneath is broken.
- Owner-locked corpses with configurable public unlock and despawn-with-drop timers; ops bypass.
- `/deathhistory` command (self for everyone, other players for ops).
- Config at `config/ssc.json`.
