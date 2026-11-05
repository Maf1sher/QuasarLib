# QuasarLib

QuasarLib is a NeoForge `1.21.1` library mod for reusable visual effects. It provides scoped effect
lifecycles, client-rendered world effects, tracked effect entities, server-originated effect audio,
and server-authoritative effect hitboxes.

## Features

- Server-authored, client-rendered visual effects with nearby or dimension-wide audiences.
- `EffectScope` for grouping visuals and entities and cleaning them up together.
- `TimedEffectEntity` for transient effect entities with synchronized lifetime and state.
- `EffectHitbox` and `EffectDamageShape` for server-side damage volumes.
- A built-in debug harness for the visual and entity paths.

## Requirements

- Minecraft `1.21.1`
- NeoForge `21.1.264` or newer
- Java `21`

## Installation

Place the QuasarLib jar in the `mods` directory on both the client and server. Mods that use
QuasarLib must declare it as a required dependency.

## Usage

Create one `EffectScope` for a group of related effects and close it when they should be removed:

```java
try (EffectScope effects = new EffectScope(serverLevel)) {
    effects.playVisual(new VisualEffectSpec(
        effectId,
        position,
        yaw,
        pitch,
        scale,
        durationTicks,
        seed,
        EffectAudience.nearby(128.0)
    ));
}
```

Register a client renderer for each visual effect ID in `VisualEffectRendererRegistry`. Effect
entities extend `TimedEffectEntity` and use normal entity renderer registration.

## Debug command

Operators with permission level 2 can run:

```text
/quasarlib effect ring
/quasarlib effect entity
/quasarlib effect all
```

## Building

```bash
./gradlew build
```

The mod jar is written to `build/libs/`.

## License

All Rights Reserved.
