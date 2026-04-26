# TrailFollower

TrailFollower is a RusherHack plugin for Minecraft 1.21.4 that follows XaeroPlus chunk trails and can hand movement off to Baritone in the Nether.

It is built for trail-following and stash-hunting workflows where inverse/new/old chunk data can indicate player travel, and large already-loaded areas can indicate possible portal skips.

## Features

- Detects trail chunks from XaeroPlus chunk data.
- Follows trails with yaw-lock movement or Nether Baritone goals.
- Optional Baritone Elytra start for long Nether paths.
- Detects possible portal-skip areas and targets the center.
- Suppresses already-targeted portal-skip areas with a configurable clear radius.
- HUD element with toggleable message, trail, movement, portal, and Baritone info groups.
- Soft-disables without stopping Baritone when it detects missing elytra or fireworks.

## Requirements

- Minecraft `1.21.4`
- Fabric Loader
- RusherHack with plugins enabled
- Baritone
- XaeroPlus
- Xaero's Minimap / World Map, as required by XaeroPlus

For building, this project also expects local compile-only jars:

- `lib/Baritone.jar`
- `lib/XaeroPlus.jar`

These jars are not bundled by Gradle. Add them locally before building.

## Building

```bash
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

The built plugin jar will be in:

```text
build/libs/
```

## Installing

1. Build the project.
2. Copy the generated `trailfollower-*.jar` into your RusherHack plugins folder.
3. Start Minecraft with `-Drusherhack.enablePlugins=true`.
4. Reload or restart RusherHack.
5. Enable `TrailFollower` from the RusherHack ClickGUI.

## Basic Usage

Enable `TrailFollower` while XaeroPlus chunk detection is active.

In the Nether, `Auto` mode uses Baritone goals. In other dimensions, `Auto` uses yaw-based following. You can force either behavior with the `Mode` setting.

If portal landing is enabled, TrailFollower can stop following a trail after detecting a large portal-skip-like loaded area and target the center of that area instead.

## Important Settings

- `Trail`: trail detection length, start threshold, timeout, deviation, and old/new chunk filters.
- `Move`: follow mode, yaw lock, rotation smoothing, and trail-end behavior.
- `Baritone`: Elytra auto-start, goal distance, update rate, and recovery behavior.
- `Portal`: portal-skip landing, scan radius, area size, clear radius, arrival distance, and arrival action.
- `ChatStatus`: prints TrailFollower status messages to chat.

## HUD

The `TrailFollowerInfo` HUD element shows the current state and optional details:

- status messages
- trail and candidate counts
- chunks per second
- yaw target
- Baritone target distance
- portal target distance and area size

## Notes

This plugin depends on XaeroPlus and Baritone internals, so updates to those mods can break behavior. Test new versions before relying on them for long trips.
