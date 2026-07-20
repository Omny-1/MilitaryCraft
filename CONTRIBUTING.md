# Contributing

Thank you for considering a contribution to MilitaryCraft. This document covers how to
build the project, what is expected of a change, and how to report problems.

## Building

You need JDK 21 or newer and Maven. The only compile dependency is the Paper API, resolved
from the PaperMC repository, plus JUnit for tests.

```bash
git clone https://github.com/Omny-1/MilitaryCraft.git
cd MilitaryCraft
mvn package
```

The finished plugin is written to `target/MilitaryCraft-1.0.0.jar`.

```bash
mvn test                    # run the test suite
mvn -DskipTests package     # build without running tests
mvn -o package              # build offline once dependencies are cached
```

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
resourcepack/                  resource pack source
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

## Testing a change

The automated tests cover what can be verified without a running server: artillery
ballistics and target validation, motorcycle drive physics and its persistent index,
nuclear strike settings and configuration bounds, the item model switch, the vehicle
provider, and a set of resource checks that parse `plugin.yml` and `config.yml`.

Those resource checks are strict on purpose. They confirm that every module has a
configuration section, that no permission node is declared twice, and that every permission
referenced by a command class is actually declared. A change that adds a command or a
module will fail the build until the corresponding declarations exist.

Rendering, driving and combat behaviour cannot be covered by tests. Anything touching those
areas needs to be checked on a real Paper 1.21.4 server before it is submitted. Please say
in the pull request what you tested and how.

## Reporting a problem

Open an issue using the bug report template. The most useful reports include the server
version and the exact plugin version, the module involved, what you expected, what actually
happened, and the full console output if there is an error. A stack trace from the console
is worth more than a description of it.

Please do not report security problems in a public issue. See [SECURITY.md](SECURITY.md).

## Code style

Match the surrounding code rather than introducing a new style. A few conventions run
through the project:

- Modules are registered explicitly in `MilitaryCraftPlugin`, in a fixed order. There is no
  classpath scanning, and new modules should keep it that way.
- A module touches only its own configuration section and its own entities. Shared
  behaviour belongs in `core`, not in another module.
- Values read from configuration are range-checked, so a bad or missing value falls back to
  a documented default instead of producing broken behaviour.
- Configuration keys are commented with their unit and their practical effect. A new
  setting without a comment explaining what it does will be asked about in review.
- User-facing text is English, apart from preserved proper names such as the Kamaz
  "Pushinka", the "Desert Express" and the artillery "Belochka".

## Scope of changes

Each module is a port of a plugin that existed on its own, and its behaviour, models,
hitboxes, balance and persistent data keys are deliberate. Please do not change gameplay
behaviour, rebalance values or alter persistent data keys as part of an unrelated change.
Altering a persistent data key orphans vehicles already placed on running servers.

If you want to propose a gameplay change, open an issue first so it can be discussed before
you spend time on it.

## Pull requests

Keep a pull request to one logical change. Write a commit message that explains why the
change is needed, not only what it does. Make sure `mvn test` passes before submitting.
