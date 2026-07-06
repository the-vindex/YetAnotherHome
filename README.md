# Yet Another Home

A NeoForge/Forge mod for Minecraft 1.20.1 adding essential server-side quality-of-life commands: homes, return teleport, and teleport requests.

## Features

- **Homes**: `/home`, `/sethome`, `/delhome`, `/listhomes` — set and teleport to named home locations. Players get a configurable number of homes (default 1); ops have unlimited homes.
- **Back**: `/back` — return to your last death or teleport location.
- **Teleport requests**: `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpcancel` — request-based teleporting between players.
- **Combat-safe**: a hurt lockout and teleport warmup prevent using teleports to escape PvP.

This is a server-only mod — vanilla clients can join without issue.

## Requirements

- Minecraft 1.20.1
- Forge 47.2.0+

## Building

```
./gradlew build
```

The compiled mod jar will be in `build/libs/`.

## License

MIT — see [LICENSE](LICENSE). Inspired by Core Commands by Cobra (MIT).
