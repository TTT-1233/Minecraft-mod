# Pearl Keeper

A Fabric mod that freezes your ender pearls when you log out and resumes them when you come back.

Throw a pearl, disconnect mid-flight, and vanilla usually loses it. Pearl Keeper takes the pearl
out of the world the moment you disconnect, writes its exact position and velocity into the world
save, and spawns it again a second after you rejoin — same spot, same momentum, still flying.

## Server support

The mod is server-side only. It runs on dedicated servers, LAN hosts and singleplayer, and it adds
no packets or registry entries, so **clients do not need to install it** to play on a server that
has it. Pearls are stored per player inside the world folder
(`<world>/pearlkeeper/<player-uuid>.json`), so they survive a server restart and travel with a
world backup.

## Config

Written to `config/pearlkeeper.json` on first run:

| Option | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch; `false` leaves pearls to vanilla behaviour. |
| `maxPearlsPerPlayer` | `8` | Cap on pearls kept per player; extras are dropped. |
| `maxOfflineHours` | `0.0` | Discard pearls after this long offline. `0` = never expire. |
| `restoreDelayTicks` | `20` | Ticks to wait after login before spawning, so chunks are loaded. |
| `notifyPlayer` | `true` | Send a chat message saying how many pearls came back. |

## Building

Needs JDK 21 and an internet connection (Gradle downloads Minecraft and the Fabric toolchain on the
first run).

```sh
git clone https://github.com/TTT-1233/Minecraft-mod.git
cd Minecraft-mod
./gradlew build          # gradlew.bat build on Windows
```

The mod jar lands in `build/libs/pearlkeeper-1.0.0.jar`. Ignore the `-sources` jar next to it.

## Installing

1. Install the **Fabric Loader** for your Minecraft version — <https://fabricmc.net/use/installer/>.
   On a dedicated server, use the installer's *Server* tab to produce the Fabric server jar.
2. Download **Fabric API** for the same Minecraft version from
   <https://modrinth.com/mod/fabric-api> and put it in `mods/`.
3. Put `pearlkeeper-1.0.0.jar` in `mods/` as well.
   - Dedicated server: the `mods/` folder next to the server jar.
   - Singleplayer / LAN: `.minecraft/mods` (`%appdata%\.minecraft\mods` on Windows,
     `~/Library/Application Support/minecraft/mods` on macOS).
4. Start the server (or the game with the Fabric profile). The log should show
   `Loading 2 mods: fabric-api, pearlkeeper`, and `config/pearlkeeper.json` appears on first run.

Players joining a server that runs this mod do **not** need to install anything.

To check it works: throw a pearl, disconnect while it is still in the air, log back in, and about a
second later the pearl resumes its flight — the log prints `Froze 1 ender pearl(s)` on the way out
and `Restored 1 ender pearl(s)` on the way back.

## Version coordinates

This was written for **Minecraft 1.26.2 / Fabric Loader 0.19.5**. The build numbers in
`gradle.properties` — `yarn_mappings`, `fabric_version` and `loom_version` — are the pieces that
change between releases. Look the current ones up at <https://fabricmc.net/develop/> and adjust
before the first build if Gradle complains it cannot resolve them.

## Notes

- Since Minecraft 1.21.2 vanilla itself persists thrown ender pearls across a logout. Pearl Keeper
  removes the pearl entity (`discard()`) before the player is removed from the world, so vanilla
  has nothing left to save and you never get two pearls back from one throw. It also keeps working
  on setups where vanilla's own handling drops the pearl — a dimension change, a chunk unload, or
  the pearl falling outside the owner's loaded area.
- If you disconnect again during the restore delay, the pending pearls stay on disk and come back
  on the next login instead of being lost.
