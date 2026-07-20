# MilitaryCraft

English · [Уззкий](README.ru.md) · [Українська](README.ua.md)

[![Build](https://github.com/Omny-1/MilitaryCraft/actions/workflows/build.yml/badge.svg)](https://github.com/Omny-1/MilitaryCraft/actions/workflows/build.yml)

MilitaryCraft is a single Paper plugin that adds drivable vehicles, aircraft, placeable
weapon emplacements, air support strikes and a set of infantry equipment to Minecraft
1.21.4. Everything is built from vanilla display entities and standard server API calls,
so the server needs no client mod and no additional plugins.

---

## Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [First steps](#first-steps)
- [Modules](#modules)
  - [Ground vehicles](#ground-vehicles)
  - [Aircraft](#aircraft)
  - [Emplacements](#emplacements)
  - [Air support](#air-support)
  - [Infantry equipment](#infantry-equipment)
  - [Camera](#camera)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [Resource pack](#resource-pack)
- [Building from source](#building-from-source)
- [Project layout](#project-layout)
- [Limitations](#limitations)
- [License](#license)

---

## Requirements

| Item | Value |
| --- | --- |
| Server software | Paper 1.21.4 or later |
| Verified range | 1.21.4 through 1.21.8 |
| Java | 21 |
| Dependencies | None |
| Folia | Not supported |

The plugin is compiled against the Paper API. Spigot and CraftBukkit are not supported,
because several modules rely on Paper-only calls. Folia is explicitly marked unsupported
in `plugin.yml`, since the module schedulers assume a single main thread.

### Why the range starts at 1.21.4

The minimum is not a preference. It is where the APIs the plugin is built on first appear,
and it is measured rather than estimated: a workflow compiles the sources against each Paper
API in turn, and 1.21.3 does not build.

The reasons older versions are out of reach at all are structural. Every vehicle reads its
driver through `Player.getCurrentInput()`, which arrived in 1.21.3, and no earlier API
reports which movement keys a player holds, so on older versions there is nothing to steer
with short of intercepting packets and rewriting all eight controllers around a third-party
library. The camera distance, vehicle health and armour values use attribute constants that
were renamed around the same time, and the `SCALE` attribute behind the camera needs 1.20.5.
Custom item models and item cooldown components need 1.21.2.

Display entities, which every vehicle model is built from, go back to 1.19.4, so the models
are not what sets the limit.

The jar is compiled against 1.21.4, the same version as the floor. That is deliberate: it
makes it impossible to start using a newer API by accident without the matrix turning red.

### How far forward it goes

There is no version-specific code anywhere in the project: no NMS, no CraftBukkit imports
and no reflection into server internals. That is what makes one jar viable across versions
instead of a separate build per release, and it is why no per-version downloads are offered.
They would be identical files.

Every push compiles the sources against each published Paper API from 1.21.4 upward, so the
supported range is measured. A green result means every API the code calls still exists in
that version. That is a strong signal, but it is not a play test: it confirms the plugin
resolves, not that a tank drives correctly there. Reports from real servers are welcome as
issues.

The range stops at 1.21.8 for a measured reason rather than a lack of interest. Building
against 1.21.9, 1.21.10 and 1.21.11 fails. Paper also raised its own Java requirement to 25
at 1.21.9, but that is not the cause: the builds still fail on a Java 25 compiler, and they
fail while compiling rather than while setting up, so those releases changed something the
plugin calls. Supporting them means working through those errors first, which has not been
done. Until then the honest answer is 1.21.4 through 1.21.8.

## Installation

1. Download or build `MilitaryCraft-1.0.0.jar` (see [Building from source](#building-from-source)).
2. Place the jar in the server's `plugins` directory.
3. Start the server once. A default `plugins/MilitaryCraft/config.yml` is written on first run.
4. Adjust the configuration if needed, then run `/mc reload` or restart the server.

The resource pack is optional and is described in its own section below. Without it, all
items keep their normal vanilla appearance and every function still works.

## First steps

By default, only server operators can use MilitaryCraft commands. As an operator:

```text
/mc menu           open the graphical command menu
/mc modules        list every module and whether it is enabled
/tank give         receive a tank placer item
/tank spawn        place a tank at your position
/warkit giveall    receive every piece of infantry equipment
```

Most vehicles work the same way. You receive a placer item with `give`, put it down like a
block, then right-click the vehicle to board it. `spawn` skips the item and creates the
vehicle directly, and `place x y z [world]` does the same at fixed coordinates, which also
works from the server console.

To remove things again:

```text
/tank remove       remove the tank you are looking at
/tank cleanup      remove leftover tank entities
/mc cleanup        remove every tracked vehicle and stray entity from all modules
```

---

## Modules

Sixteen modules ship in the jar. Each one can be switched off individually in the
`modules:` block of the configuration file, and each has its own section with the movement,
weapon and model settings it uses.

Durability for vehicles is expressed in creeper blasts. The configuration stores
`creeper-damage` and `creepers-to-destroy`, and the total health is the two multiplied
together. This keeps the tuning readable: a value of three means a vehicle survives two
point-blank creeper explosions and is destroyed by the third.

### Ground vehicles

**Tank.** A tracked vehicle with an articulated hull, rotating turret and elevating
barrel. The hull turns toward where the driver looks, and the turret aims independently.
It fires ballistic shells that damage terrain and other vehicles, and it has a reload
timer, a weapon lock after boarding and a barrel overheat gauge. Driving into water drowns
the crew. Top speed is about 18 km/h, and it withstands four creeper blasts.
Command: `/tank`.

**Kamaz "Pushinka".** A heavy armoured truck. It carries a driver and six passengers, and
its defining trait is mass: it takes roughly six seconds to reach its top speed of about
47 km/h, and it turns slowly. At full speed it throws entities aside; below that it pushes
them without killing them. It carries no weapon and withstands three creeper blasts.
Command: `/kamaz`.

**Pickup.** A light truck with three crew positions: a driver, a passenger, and a gunner
who operates a roof-mounted machine gun with an independently aimed camera. Either seat can
be left empty, so a lone gunner can fire from a parked truck and a lone driver can drive
without a gunner. Acceleration ramps up the longer the throttle is held. It withstands one
and a half creeper blasts.
Command: `/pickup`.

**Motorcycle.** A two-wheeler with a right-hand sidecar, seating a driver, a pillion
passenger and a sidecar passenger. It steers by camera like the truck, reaches about
66 km/h, takes fall damage, and can climb a one-block step. Spawn limits, cooldowns and a
durable index of placed motorcycles are kept on disk so counts survive a restart.
Command: `/moto`.

**Train "Desert Express".** A steam locomotive with three carriages that runs on ordinary
vanilla rails as one rigid formation. The coupling is rigid, so the gap between carriages
does not stretch on curves or slopes. It travels at a constant nine blocks per second with
no acceleration, brakes smoothly when the rails end, and injures anything standing on the
track. Passengers board by right-clicking a carriage, including while the train is moving.
Command: `/train`.

### Aircraft

All four aircraft are destroyed only by accumulated damage. Striking terrain hurts them but
never deletes them instantly.

**Jet.** A fixed-wing fighter steered by the camera. `W` and `S` control throttle and
braking, `A` and `D` roll the airframe, and `Space` engages an afterburner limited by an
engine heat gauge. Left-click fires rockets, right-click drops bombs. It handles stalls and
crashes, cruises at about 115 km/h and boosts well past that, and withstands two creeper
blasts.
Command: `/jet`.

**Helicopter.** Hovers in place with no vertical input, as a real helicopter does at flat
pitch. The mouse sets heading and altitude trim, `W` and `S` move forward and back, and
`Space` is a collective boost limited by engine heat. The main and tail rotors turn
whenever it is hovering. Left-click fires rockets, right-click drops bombs. It seats a
pilot and three passengers and withstands two creeper blasts.
Command: `/helicopter`.

**Airship.** A large lighter-than-air craft that holds its altitude with no input at all.
It is deliberately ponderous: a top speed near 25 km/h, very low acceleration and slow,
wide turns. `Space` fires a gas burner for lift, limited by a burner heat gauge. It drops
bombs that leave lingering smoke and a wide blast. It seats a pilot and two passengers and
is destroyed by a single creeper blast.
Command: `/airship`.

**Drone.** An unmanned aerial vehicle flown in first person. It moves forward on its own,
as though the throttle were always held, and is steered only with the camera: look up to
climb, look down to dive. Right-click fires one of four single-use rockets, and left-click
detonates the warhead. Flying the nose into terrain or a target also detonates it. Pressing
shift twice dismounts the operator, after which the drone keeps flying straight rather than
falling. It travels at about 122 km/h and is destroyed by one creeper blast.
Command: `/drone`, aliases `/bpla` and `/uav`.

### Emplacements

**Anti-air turret.** A close-in weapon system placed like a block, with a grey body, a
white radar dome that traverses and a six-barrel rotary cannon that elevates. It runs on
furnace fuel: right-click to open a panel where fuel is loaded and a targeting mode is
chosen. One mode engages hostile mobs and the other engages players. Fire is hitscan with
distance-based spread, and it respects blocks in the line of fire. It re-scans for targets
four times per second, slews at 22 degrees per tick and only fires once aimed within seven
degrees of the target. It is destroyed only by explosion damage, calibrated to one creeper
blast.
Command: `/pvo`, aliases `/antiair`, `/aa`, `/ciws`, `/flak`.

**Artillery "Belochka".** An indirect-fire emplacement with a visible three-dimensional
howitzer model. The operator enters a top-down camera positioned above the emplacement and
calls fire onto real world coordinates with `/shoot x z`. Each salvo fires exactly three
shells, with dispersion that grows with range, so distant targets are hit less precisely.
An emplacement holds three salvos with a two-minute cooldown between them. Sessions and
emplacement state are stored on disk, and block protection stops shells from destroying
protected terrain.
Commands: `/shoot`, `/artillery`.

**Trap bus.** A placeable static bus with a detailed van model and two hostile worker
NPCs. The workers approach a nearby player and strike them, which stuns the player briefly.
If two workers are within four blocks of a stunned player, the player is pulled into the
bus and killed; otherwise the player escapes. Killing both workers lets you break the bus
itself, which yields a configurable custom drop that an operator sets globally. Players
holding a dedicated exemption permission are never targeted. The bus is destroyed by one
creeper blast.
Command: `/tck`, aliases `/tckbus`, `/bus`.

### Air support

**Airstrike.** A beacon item, or a command, calls a fighter that makes a bombing run over
the chosen point. It drops twelve explosive charges spread over a fifty-block carpet
centred on the target, flying at an altitude of 75 blocks. A warning radius alerts nearby
players, and cooldowns and a cap on simultaneous strikes prevent abuse. Chunks along the
flight path are kept loaded for the duration and released afterwards.
Command: `/airstrike`, aliases `/strike`, `/aviaudar`.

**Nuclear strike.** A briefcase item, or a command, sends a bomber over the target that
releases a single large bomb. The bomb is visible during its slow fall and rotates
nose-down as it descends. On impact it produces a crater, a rising cloud, damage across a
wide radius, a blinding flash for onlookers and lingering radiation zones for survivors.
Like the airstrike, it manages its own chunk loading and releases it when the sequence ends.
Command: `/nuke`, aliases `/nukestrike`, `/yaderka`, `/yadernyudar`.

### Infantry equipment

The equipment module adds roughly forty items intended for round-based or battle-royale
gameplay. They are grouped as follows.

| Group | Items |
| --- | --- |
| Medical and consumable | Medkit, painkiller, ration, combat stimulant, repair kit |
| Armour and wearables | Vest, kevlar helmet, exosuit, gas mask, impact pads, camouflage cloak, visor helmet |
| Firearms | Rifle, pistol, grenade launcher, anti-air launcher, heavy machine gun, flamethrower, chemical sprayer |
| Thrown | Fragmentation grenade, smoke grenade, flash grenade, impulse grenade, molotov, sleep gas |
| Deployable and traps | Barbed wire, tripwire trap, proximity mine, sentry gun, firing wall, demolition charge, suicide vest |
| Utility | Marker, grappling hook, jump jet, reconnaissance scanner, trench shovel |

A few behaviours are worth calling out. The medkit heals over a channel of several seconds
that any real damage interrupts. The painkiller reduces incoming damage for a period
shorter than its own cooldown, so it cannot be maintained permanently. The vest trades
armour and knockback resistance for a small movement penalty. The repair kit restores the
vehicle the player is riding before falling back to repairing a held item. Firearms are
hitscan and damage vehicles as well as players. Armour and attribute values apply to items
created after a reload; items already issued keep the stats they were made with.

Command: `/warkit`, alias `/wk`.

### Camera

Third-person view in Minecraft sits a fixed distance behind the player, which is too close
for a large vehicle. The camera module gives a rider a temporary scale attribute while they
are seated, which moves the third-person camera further back. Each vehicle type has its own
value, so a tank pulls the camera back modestly while an airship pulls it back a long way.

The module identifies vehicles by the scoreboard tags the vehicle modules already apply, so
adding a new vehicle needs only a new line in the configuration. It re-checks what the
player is riding four times per second.

Because the attribute also enlarges the player model and its hitbox, the vehicle modules
hide the driver from view. Values that are too high can make a ground vehicle's occupant
clip into terrain, so the setting is clamped between 0.0625 and 16.
Command: `/vehiclecamera`, alias `/vcam`.

---

## Commands

Every module is reachable two ways: through its own command, and through the shared
`/mc <module> <action>` tree. Both run the same code, so either can be used.

### Root command

| Command | Description |
| --- | --- |
| `/mc menu` | Open the graphical command menu |
| `/mc reload` | Reload the configuration and every active module |
| `/mc modules` | List all modules and their enabled state |
| `/mc cleanup` | Remove all tracked vehicles and stray entities |
| `/mc <module> <action>` | Run any module action listed below |

`/mc` also accepts the alias `/militarycraft`.

The graphical menu is worth mentioning separately. It lists the modules the viewer is
allowed to use, then the actions within each, and it only shows entries the player has
access to. Actions that take parameters offer clickable choices or ask for a value in chat,
so most administration can be done without typing commands.

### Module commands

| Command | Aliases | Actions |
| --- | --- | --- |
| `/tank` | | give, spawn, place, remove, cleanup, list, reload |
| `/kamaz` | pushinka, kamazcraft | give, spawn, place, remove, cleanup, list, reload |
| `/pickup` | | give, spawn, place, remove, cleanup, list, reload, migrate |
| `/moto` | motorcycle, bike, motocraft | give, spawn, place, remove, cleanup, list, reload |
| `/train` | poezd | give, place, remove, removeall, list, reload |
| `/jet` | | give, spawn, place, remove, list, reload, cleanup |
| `/helicopter` | heli, verto, vertolet | give, spawn, place, remove, list, reload, cleanup |
| `/airship` | dirizhabl, zeppelin, dirigible | give, spawn, place, remove, list, reload, cleanup |
| `/drone` | bpla, uav | give, spawn, place, fire, exit, remove, list, reload, cleanup |
| `/pvo` | antiair, aa, ciws, flak | give, place, remove, list, reload, cleanup |
| `/tck` | tckbus, bus | give, place, setdrop, cleardrop, showdrop, remove, list, reload, cleanup |
| `/artillery` | artillert | give, create, remove, list, reload |
| `/shoot` | arta | `<x> <z>`, fires a salvo at map coordinates |
| `/airstrike` | strike, aviaudar | item, reload |
| `/nuke` | nukestrike, yaderka, yadernyudar | item, reload, place |
| `/warkit` | wk | list, give, giveall, reload |
| `/vehiclecamera` | vcam | reload |

Common actions behave consistently across modules:

- `give` creates the placer item for that vehicle or device.
- `spawn` creates it at your position without needing an item.
- `place x y z [world]` creates it at exact coordinates and works from the console.
- `remove` deletes the one you are looking at; `cleanup` clears leftover entities.
- `list` reports what is currently loaded and where.
- `reload` re-reads that module's configuration section.

When a `give` command is run and the player's inventory is full, the item is dropped at
their feet rather than being lost.

## Permissions

MilitaryCraft has two layers of access control, and it is important to understand that the
first one overrides the second by default.

### The command gate

By default `command-access.allow-non-op-commands` is `false`. In that state, permissions are
not consulted at all for players: only operators can run MilitaryCraft commands, regardless
of what any permissions plugin grants. Non-operators are shown a configurable message, and
commands they cannot use are hidden from tab completion.

Two settings adjust this:

- `honor-admin-permission` (default `true`) lets a non-operator holding
  `militarycraft.admin` pass the gate as though they were an operator.
- `allowed-non-op-actions` lists specific actions that stay available to everyone. It
  defaults to the artillery and drone actions that are part of normal play:
  `artillery.enter`, `artillery.fire`, `artillery.exit`, `drone.fire`, `drone.exit`.
  These are additionally checked against the situation, so firing artillery requires an
  active session and drone actions require actually piloting one.

Set `allow-non-op-commands` to `true` to disable the gate and use ordinary Bukkit
permissions exactly as declared. This is the setting to change if you manage access with a
permissions plugin.

### Permission nodes

With the gate open, these are the nodes the plugin checks.

| Node | Default | Grants |
| --- | --- | --- |
| `militarycraft.use` | everyone | Access to the `/mc` command itself |
| `militarycraft.menu` | everyone | Open the graphical menu |
| `militarycraft.admin` | operators | Every module, and passes the command gate |

Each module also has its own family, named after the plugin it came from. They follow one
pattern: `use` for normal play, `place` for putting the vehicle down, `give` and `spawn`
for creating items and vehicles, and `admin` for removal, cleanup and reloading.

| Family | Nodes |
| --- | --- |
| `tankcraft.` | use, drive, place, give, spawn, admin |
| `kamazcraft.` | use, place, give, spawn, admin |
| `pickupcraft.` | use, drive, passenger, gun, place, give, spawn, admin |
| `motocraft.` | use, place, give, spawn, admin |
| `traincraft.` | use, place, give, admin |
| `jetcraft.` | use, place, admin |
| `helicraft.` | use, place, admin |
| `airshipcraft.` | use, place, admin |
| `dronecraft.` | use, place, admin |
| `antiaircraft.` | use, place, admin |
| `tckbus.` | use, place, admin, immune |
| `svoart.` | use, admin |
| `airstrike.` | use, give, reload, bypass-cooldown |
| `nuke.` | use, give, reload, bypass-cooldown |
| `warkit.admin` | Equipment commands |
| `vehiclecamera.admin` | Reload the camera configuration |

`tckbus.immune` is the exception to the pattern. It defaults to nobody and marks players the
trap bus workers will never target.

## Configuration

The plugin uses one file, `plugins/MilitaryCraft/config.yml`, of roughly 2,400 lines. It is
extensively commented: every value states its unit, its practical effect and, where useful,
the reasoning behind the default. Distances are in blocks, speeds in blocks per tick, and
angles in degrees. One block per tick equals twenty blocks per second, and multiplying a
speed by 72 gives the figure in kilometres per hour shown on the in-game display.

The file is organised as follows.

| Section | Purpose |
| --- | --- |
| `modules` | Enable or disable each of the sixteen modules |
| `command-access` | The command gate described in the permissions section |
| `camera` | Third-person camera distance per vehicle type |
| One section per module | All movement, weapon, model and durability settings |

To turn a module off completely, set its flag and reload:

```yaml
modules:
  nuke:
    enabled: false
```

`/mc reload` re-reads the file and pushes fresh settings to every active module. It reports
by name any module that failed to reload rather than claiming success, so a mistake in the
file is visible immediately. Some values, such as equipment armour statistics, only affect
items created after the reload.

Numeric settings are range-checked when read. Values outside a sensible range, and values
that are not numbers at all, fall back to the documented default instead of producing
broken behaviour.

## Resource pack

An optional resource pack gives the 38 equipment items their custom three-dimensional
models. The models are built from vanilla block textures, so the pack contains no image or
sound files of its own.

Custom models are off by default and are controlled by one setting:

```yaml
resource-pack:
  models: false
```

Leave it off unless the pack is actually being served to players. A client without the pack
cannot fall back gracefully: an unresolved model reference renders as the missing-model
placeholder rather than as the ordinary item. With the setting off, items simply look like
the vanilla material they are built from, and nothing else changes.

The setting takes effect when an item is created, so it does not alter items already in
inventories. After changing it, run `/mc reload` and issue fresh items.

The pack source is `resourcepack/warkit/`. It holds the item and model definitions plus a
`HOW_TO_ENABLE.txt` that documents the process step by step. No prebuilt archive is
committed: build one by zipping the contents of that directory so that `pack.mcmeta` and
`assets/` sit at the root of the archive, not inside an extra folder.

To use it, host the zip at a direct download address and point the server at it in
`server.properties`:

```properties
resource-pack=https://example.com/WarKit.zip
resource-pack-sha1=<sha1 of the file>
require-resource-pack=false
```

On Windows the hash can be produced with `certutil -hashfile WarKit.zip SHA1`. Players can
also install the zip locally in `.minecraft/resourcepacks` instead.

Once the pack is being distributed, enable the custom models in the configuration and issue
fresh items, because the model reference is written into the item when it is created. Enable
the models only while the pack is actually being served, or the custom items will render as
missing models for players without it.

## Building from source

You need JDK 21 and Maven. The only dependency is the Paper API, which is resolved from the
PaperMC repository, plus JUnit for the tests.

```bash
git clone https://github.com/Omny-1/MilitaryCraft.git
cd MilitaryCraft
mvn package
```

The finished plugin is written to `target/MilitaryCraft-1.0.0.jar`.

Other useful commands:

```bash
mvn test                    # run the test suite
mvn -DskipTests package     # build without running tests
mvn -o package              # build offline, once dependencies are cached
```

The test suite covers the parts that can be verified without a running server: the
mathematics behind artillery ballistics and target validation, motorcycle drive physics and
its persistent index, nuclear strike settings, the vehicle provider, and a
set of resource checks that parse `plugin.yml` and `config.yml` to confirm every module has
a section, that no permission node is declared twice, and that every permission the command
classes reference is actually declared.

## Project layout

```text
src/main/java/me/bibo/militarycraft/
    MilitaryCraftPlugin.java   entry point and module registration
    core/                      shared foundation used by every module
        combat/                vehicle damage, projectiles, explosions
        command/               command tree, argument parsing, graphical menu
        config/                configuration access and range checking
        event/                 event distribution to modules
        item/  key/  text/  util/
    camera/                    third-person camera distance
    vehicles/                  tank, kamaz, pickup, moto, train,
                               jet, helicopter, airship, drone
    weapons/                   antiair, tckbus, artillery, airstrike, nuke
    gear/warkit/               infantry equipment
src/main/resources/
    plugin.yml                 commands and permission declarations
    config.yml                 all settings
src/test/java/                 unit and resource tests
resourcepack/                  optional resource pack source
```

Modules are registered explicitly in `MilitaryCraftPlugin`, in a fixed order, with no
classpath scanning. Each module implements a small interface with an identifier and enable,
disable and reload steps, and it receives a single object holding the shared services:
events, commands, item creation, vehicle registry, combat and camera. A module touches only
its own configuration section and its own entities.

Vehicles are built from display entities positioned by matrix transforms, with an invisible
core entity holding the state. Vehicle state is stored in the entity's persistent data, so
vehicles survive chunk unloads and server restarts and are rebuilt when their chunk loads
again.

## Limitations

These are known and intentional, listed so they are not discovered as surprises.

- Paper only. Spigot and CraftBukkit will not work.
- Folia is not supported.
- Vehicle models are display entities, so they have no true collision volume. Contact is
  handled by distance checks rather than by the physics engine.
- The camera distance is set through an entity scale attribute, which also enlarges the
  rider's hitbox. Vehicle modules hide the rider to compensate, but very large values can
  still cause a ground vehicle's occupant to clip into terrain.
- Automated tests cover mathematics, persistence and configuration integrity. Rendering,
  driving and combat behaviour can only be verified on a running server.
- The plugin writes its own persistent state for artillery sessions, motorcycle counts and
  trap bus drops. Deleting the plugin's data directory resets those.

## Contributing

Bug reports and pull requests are welcome. [CONTRIBUTING.md](CONTRIBUTING.md) covers
building the project, what the tests do and do not cover, and what is expected of a change.
Notable changes are recorded in [CHANGELOG.md](CHANGELOG.md).

Security problems should be reported privately rather than in a public issue. See
[SECURITY.md](SECURITY.md).

## License

Released under the MIT License. See [LICENSE](LICENSE) for the full text.
