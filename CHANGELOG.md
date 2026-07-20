# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
