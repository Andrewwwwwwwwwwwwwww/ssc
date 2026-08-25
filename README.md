# Server Sided Corpse (SSC)

A **fully server-side** Fabric mod for Minecraft 26.2. When you die, your body stays where you fell — a corpse wearing your skin, holding everything you were carrying plus your XP — and **players connect with completely vanilla clients**. Nothing to install client-side.

A server-side rewrite of [Fallen](https://github.com/Andrewwwwwwwwwwwwwww/fallen). All the original rules apply:

- Death leaves a lootable corpse instead of loose, despawning item drops (respects `keepInventory`).
- The body **falls and settles** on the ground, **floats** on lava/water pools, and is **held just inside the world** over the void — loot is never destroyed.
- Right-click opens the corpse as a chest (armor, offhand, inventory and hotbar laid out in their own slots); **sneak-right-click returns everything to its original slots** plus your XP. A full inventory never spills loot.
- **Owner-locked** until the corpse ages into its public stage; operators can bypass. Left too long, it drops its contents so nothing is ever truly lost.
- `/deathhistory` lists your recent deaths (ops can inspect other players).

## How it works (no client mod!)

- The visible body is a **packet-only fake player** in the sleeping pose, carrying the dead player's signed skin via an unlisted tab entry; a packet-only scoreboard team hides its nametag.
- The clickable hitbox is a pair of invisible vanilla `minecraft:interaction` entities hugging the lying body.
- The loot screen is a vanilla 6-row chest menu — only the server knows its slots map back to your inventory.
- Body physics (falling, floating, void-holding, re-falling when the block beneath breaks) are simulated in the server tick and streamed as entity teleports.

## Config (`config/ssc.json`)

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch; when false, death is vanilla. |
| `despawnMinutes` | `2880` | Minutes before a corpse despawns and drops its loot (0 = never). |
| `keepExperience` | `true` | Store the player's XP in the corpse and return it. |
| `opsBypassProtection` | `true` | Operators can loot any corpse. |
| `spawnInLava` / `spawnOverVoid` | `true` | Whether hazard deaths still form a (floated / void-held) corpse. |
| `voidScanDepth` | `12` | How far down to scan before treating a spot as "over the void". |
| `skeletonMinutes` | `1440` | Minutes before a corpse unlocks for everyone (0 = never). |
| `skeletonStageIsPublic` | `true` | Aged corpses may be looted by anyone. |
| `deathHistorySize` | `20` | Deaths kept per player for `/deathhistory`. |

## License

Original, independent work — a clean-room implementation built on public Minecraft/Fabric APIs. See `LICENSE`.
