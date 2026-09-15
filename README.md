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

```sh
./gradlew build
```

The jar lands in `build/libs/`. Drop it in the server's (or client's) `mods/` folder alongside
Fabric API.

The Gradle wrapper jar is not committed. Run `gradle wrapper` once with a local Gradle install to
generate it, or just use your own `gradle build`.

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
