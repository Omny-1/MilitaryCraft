# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Removed

- The prebuilt `WarKit-ResourcePack.zip` is no longer distributed. It bundled sounds and
  overrides beyond this project's own models, including third-party material this
  repository cannot license. Build a pack from `resourcepack/warkit/` instead.

### Added

- `resource-pack.models`, off by default, decides whether newly created equipment and
  airstrike beacons carry their custom model. Previously the model reference was written
  unconditionally, so a player without the pack saw the missing-model placeholder instead
  of the ordinary item. The plugin now looks correct out of the box, and servers that
  distribute the pack turn the setting on.

### Fixed

- Train placement now uses the shared coordinate gate, so non-finite coordinates, points
  outside the world border and ungenerated chunks are rejected rather than silently
  turning into block 0 or the maximum integer.
- Nuclear strike settings are capped as well as floored. An unbounded crater radius built
  its column list on the main thread before changing any block, which stalled the server
  rather than producing a larger explosion. Shipped defaults are unchanged.
- Airstrike and nuclear strike no longer generate terrain synchronously while pinning the
  chunks along their flight path.
- Vehicle cleanup continues through the remaining modules when one of them fails.

### Changed

- The impulse grenade now pushes every player within its radius in the same way. Previously
  the thrower was a special case, pushed along their own look direction regardless of
  distance while other entities were pushed away from the blast centre. The default radius
  was lowered from 4.5 to 3.0 to suit the uniform behaviour.

## [1.0.0]

First public release. Fifteen previously separate plugins were merged into a single jar
with a shared core, one configuration file and one command tree, with each module keeping
the behaviour it had as a standalone plugin.

### Added

- **Ground vehicles.** Tank with articulated turret and ballistic shells, Kamaz "Pushinka"
  armoured truck with six passenger seats, pickup with an independent gunner seat,
  motorcycle with sidecar, and the "Desert Express" train running on vanilla rails.
- **Aircraft.** Camera-steered jet with afterburner, helicopter with hover and animated
  rotors, lighter-than-air airship, and a first-person unmanned aerial vehicle.
- **Emplacements.** Anti-air turret running on furnace fuel with selectable targeting
  modes, artillery "Belochka" with a top-down targeting camera and three-shell salvos, and
  a placeable trap bus with worker NPCs.
- **Air support.** Airstrike bombing run and a nuclear strike with crater, blast and
  residual radiation zones.
- **Infantry equipment.** Roughly forty items covering medical supplies, armour, firearms,
  thrown weapons, deployable traps and utility gear.
- **Camera module** giving each vehicle type its own third-person camera distance.
- **Shared core** providing the module system, command tree with a graphical menu,
  configuration access with range checking, vehicle registry and combat routing.
- Optional resource pack with custom item models and sounds.

[Unreleased]: https://github.com/Omny-1/MilitaryCraft/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Omny-1/MilitaryCraft/releases/tag/v1.0.0
