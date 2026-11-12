# QuasarLib

QuasarLib (`quasar_lib`) is a cinematic-effects director for Minecraft. It
provides visual, segmented, cinematic, gameplay, terrain-edit, audio, and
persistence layers so effect authors can focus on the show instead of engine
plumbing. The engine owns lifecycle, replication, composition, rollback, and
effect-specific optimization; it is not a terrain or shader-pack replacement.

The same jar also contains the sibling `quasar_content` mod, which owns the
demo effects, commands, entities, sounds, and assets.

## Requirements

| Requirement | Value |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.264+ (built against 21.1.265) |
| Java | 21 |
| Gradle wrapper | 9.2.1 |
| Sodium | required on the client |
| Server | no Sodium requirement |

## Build and test

```bash
./gradlew build
./gradlew test
```

Inside the workspace, run the helper scripts from the workspace root to keep
full logs out of the console:

```bash
../scripts/build.sh quasar-lib build
../scripts/quality.sh quasar-lib
```

## Install

Put `quasar_lib-0.1.0.jar` in the `mods/` directory on both the client and the
server. The client half renders effects; entities, hitboxes, and block changes
are server-owned. Sodium must be installed on the client.

## Quick check

Run as an operator in a world:

```text
/quasarcontent rune
/quasarlib budget
```

Other demos include `wave`, `blur`, `bloom`, `blackhole`, `blackhole-mesh`,
`segmented`, and `probe`. Engine diagnostics stay under `/quasarlib`.

## Documentation

Design notes, API contracts, limits, and guides live in the workspace
`docs/quasar-lib/` directory (start at `docs/quasar-lib/INDEX.md`). Canonical
validation limits are in `docs/quasar-lib/api/limits.md`; do not duplicate them
here.

## License

MIT. See [LICENSE](LICENSE).
