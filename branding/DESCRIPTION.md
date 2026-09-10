# Server Sided Corpse (SSC) — listing copy

**GitHub description:**

Lootable corpses on death for fully vanilla clients. The whole mod runs on the server: your body keeps your items and XP until you walk back and reclaim it.

**GitHub topics:**

minecraft, minecraft-mod, fabric, fabricmc, server-side, death, graves, corpse, survival, multiplayer, java

**Summary (CurseForge summary field, 255 char max):**

Your body stays where you fell, wearing your skin and holding your items and XP, until you walk back and take it. Runs entirely on the server, so every player can join with a completely vanilla client and install nothing.

---

# Description (paste into the CurseForge description editor in Markdown mode)

## Server Sided Corpse

Death drops a body, not a mess

When you die, your body stays where you fell. It wears your skin, holds everything you were carrying plus your experience, and waits. Walk back and right-click to take it all.

The whole mod runs on the server. **Players connect with completely vanilla clients** and install nothing at all. Drop the jar in the server's `mods` folder and everyone gets corpses, modded or not.

## What it does

- **Your body, your skin.** The corpse appears as you, lying flat where you died, rendered from your real skin. No nametag floats over it.
- **Everything is kept.** Your whole inventory and your experience go into the body. Nothing scatters across the floor and nothing despawns out from under you.
- **A familiar screen.** Right-click opens the body as a plain chest: armour across the top row, then your inventory and hotbar in their own slots. Take-only, so nothing can be put in by mistake.
- **Items go back where they belong.** Sneak-right-click sweeps everything into its original slots, armour to armour and offhand to offhand, and returns your experience. If your pack is full the rest stays in the body until you have room. Loot is never spilled.
- **It settles like a real body.** It falls to the ground where you died, floats on lava and water instead of burning or sinking, and over the void is held just inside the world. Break the block under a resting body and it drops and settles again.
- **Yours until it isn't.** For a configurable time only you and operators can loot your body. After that it is fair game. A body left far too long drops its contents rather than vanishing, so nothing is ever truly lost.
- **`/deathhistory`.** Your recent deaths with location, item count, experience, and whether the body is still out there. Operators can review any player.

It respects `keepInventory`: with that game rule on, death behaves as vanilla does.

## How can vanilla clients see any of this?

Three vanilla-protocol tricks, all driven from the server.

The **visible body** is a packet-level fake player in the sleeping pose, which is the one pose that lies flat without a bed, carrying the dead player's real signed skin through an unlisted tab entry. A packet-only scoreboard team hides its nametag.

The **hitbox** is a pair of invisible vanilla `minecraft:interaction` entities hugging the lying body.

The **loot screen** is an ordinary six-row chest menu. Only the server knows its slots map back to your inventory.

Body physics, the falling and floating and void-holding, are simulated in the server tick and streamed to clients as entity teleports.

## Configuration

`config/ssc.json`:

| Setting | Default | What it does |
|---|---|---|
| `enabled` | true | Master switch. false makes death behave exactly like vanilla. |
| `skeletonMinutes` | 1440 | When a body unlocks for other players, one day by default. 0 means never. |
| `skeletonStageIsPublic` | true | Whether an aged body may be looted by anyone. false keeps it owner-only forever. |
| `despawnMinutes` | 2880 | When the body despawns and drops its contents, two days by default. 0 means never. |
| `keepExperience` | true | Store the player's experience in the body and return it on recovery. |
| `spawnInLava` and `spawnOverVoid` | true | Whether a hazard death still forms a body. false lets items drop as vanilla. |
| `voidScanDepth` | 12 | How far down to look for ground before a spot counts as being over the void. |
| `opsBypassProtection` | true | Operators can loot any body, ignoring the owner lock. |
| `deathHistorySize` | 20 | How many past deaths to keep per player. |

## Claim mods

The corpse hitbox is an ordinary `minecraft:interaction` entity, so claim mods see looting a body as interacting with an entity and block it inside claims. SSC ships an entity tag for exactly this. For **Open Parties and Claims**, add it to the forced exceptions in `<world>/serverconfig/openpartiesandclaims-server.toml`, with the server stopped:

```toml
forcedEntityProtectionExceptionList = ["minecraft:minecart", "anything$#ssc:corpses"]
```

The `anything$` prefix matters. Without it the exception only applies when the item in your hand isn't itself blocked, so a player holding a sword still couldn't loot their own body. Claims never make a body public either way, because SSC does its own owner check regardless.

SSC prints this line to the console at startup whenever it detects the claim mod, so you don't have to remember it.

## Diagnosing a missing body

Bodies are drawn with packets rather than being real entities, so `/data` and the F3 entity list cannot see them. These operator commands can:

| Command | What it does |
|---|---|
| `/ssc list` | Every body on the server: owner, position, dimension, items, experience, age, and how many players can currently see it. |
| `/ssc resend [player]` | Forget what that player's client has been sent, so every body in range is re-sent within two seconds. |
| `/ssc debug <true\|false>` | Log every decision the death handler makes, including each reason it declines to create a body. |

**No body appeared for anyone.** Turn on debug and watch the console on the next death. Every path that skips a body says why, whether that is `keepInventory`, an empty inventory, a hazard setting, or the world refusing the hitbox entity. A body that cannot be placed logs a warning naming the player and position, and their items drop normally rather than being lost.

**Everyone sees it except one player.** That client lost the packets. `/ssc resend <player>` puts it back at once, and an automatic resync catches this within two seconds anyway. Clients silently drop packet-only bodies whenever they rebuild their world, which is what respawning, changing dimension and reconnecting all do.

## SSC or Fallen?

**Fallen** is the full experience, with a custom corpse screen, a death-history interface with operator respawn and move tools, and Trinkets and backpack support. It has to be installed on both the client and the server.

**SSC** is the drop-in server version: the same corpse rules presented in a way vanilla clients can see, for servers that cannot ask players to install anything.

Don't run both on the same server.

## Installation

1. Drop the jar into the server's `mods` folder, along with [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
2. Start the server. `config/ssc.json` is written on first run
3. Tell nobody to install anything, because they don't have to

Singleplayer works as well, running on the integrated server.

Not affiliated with or endorsed by Mojang.
