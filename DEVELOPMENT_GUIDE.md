# MilitaryCraft — Development Guide (master handoff for the next developer / Codex)

> **START HERE.** This is the single, self-contained document for continuing development of
> `MilitaryCraft`. If you are a fresh coding agent with no prior context, read this file top to
> bottom before writing a line. It tells you *what* we are building, *why*, the *exact APIs* to use,
> the *reference module to copy*, and a *per-module spec* for everything still to do.
>
> Companion files in this folder (read in this order after this one):
> **`CODEX_RULES.md` (hard do/don't rules + verified API cheat-sheet — READ IT, it stops you going off-track)** →
> `STATUS.md` (what is done / where to resume) → `PLAN.md` (roadmap + per-module table) →
> `BUILD_SPEC.md` (the original architecture contract). This guide supersedes and expands all three
> where they differ.

---

# PART A — WHAT WE ARE BUILDING

## A1. The goal
Merge **15 separate, independently-written Paper 1.21.4 Minecraft plugins** (military theme: vehicles,
weapons, gear, support systems) into **one** clean, modular plugin called **MilitaryCraft**. This is a
real refactor into a shared product, **not** a mechanical file-append. The 15 plugins share a huge amount
of duplicated code (each vehicle re-implemented spawn/despawn, PDC persistence, pilot protection, model
math, config parsing, the same 4 listeners, the same command skeleton). All of that common machinery is
extracted into a **`core` framework**, and each original plugin becomes a thin **module** on top of it.

## A2. The 15 source plugins (all under `F:\program\<Name>`, READ-ONLY reference — never modify them)
| # | Source folder | Type | What it is | Target module |
|---|---|---|---|---|
| 1 | `TankCraft` | vehicle | drivable + shooting tank (turret/barrel, shells, water/drown) | `vehicles/tank` |
| 2 | `KamazCraft` | vehicle | armored 6×6 truck "Pushinka" (drive-only, run-over/fling) | `vehicles/kamaz` ✅ DONE (reference) |
| 3 | `PickupCraft` | vehicle | pickup: driver + passenger + **independent gunner seat** + HUD | `vehicles/pickup` |
| 4 | `JetCraft` | vehicle | fighter jet: camera-steer flight, rockets + bombs | `vehicles/jet` |
| 5 | `HeliCraft` | vehicle | Mi-8 helicopter: hover, spinning rotor, forward rockets/bombs | `vehicles/helicopter` |
| 6 | `AirshipCraft` | vehicle | steampunk dirigible: lighter-than-air, heading+altitude, bombs | `vehicles/airship` |
| 7 | `DroneCraft` | vehicle | FPV kamikaze UAV (piloted "from inside"), one-shot rockets | `vehicles/drone` |
| 8 | `MotoCraft` | vehicle | motorcycle + sidecar. **Best-quality source** (has JUnit tests + durable index) | `vehicles/moto` |
| 9 | `TrainCraft` | vehicle | steam locomotive + 3 cars on vanilla rails (**outlier**: rail-following) | `vehicles/train` |
| 10 | `AntiAirCraft` | weapon | placeable CIWS anti-air turret: **GUI + fuel + auto-targeting**, modes | `weapons/antiair` |
| 11 | `AirstrikePlugin` | weapon | Su-57 airstrike: bomber flies over, carpet of TNT (`ru.airstrike`) | `weapons/airstrike` |
| 12 | `NukeStrike` | weapon | nuclear strike: one slow Fat-Man + lingering radiation (`ru.nukestrike`) | `weapons/nuke` |
| 13 | `WarKit` | gear | 12 battle-royale gadget items (medkit, armor, gasmask, marker…), 31 classes | `gear/warkit` |
| 14 | `TCKBus` | weapon/placeable | placeable "TCK" van that snatches passers-by (Pillager AI) + custom drop | `weapons/tckbus` |
| 15 | `VehicleCamera` | support | reads vehicle scoreboard tags, applies `scale` attribute for a pulled-back F5 camera | `camera` ✅ DONE |

## A3. Facts that shaped the architecture (verified by reading all 15)
- **Uniform build:** every plugin is Maven, Java 21, single dependency `paper-api 1.21.4` (provided),
  **no shaded libs, no ProtocolLib/Vault/PlaceholderAPI/WorldGuard.** Adventure is used (bundled in Paper).
- **Uniform storage:** state lives **on the entities** via `PersistentDataContainer` (written on the seat
  ArmorStand); settings in YAML. **No SQL anywhere.** Only MotoCraft adds a durable YAML index (tombstones
  + spawn cooldowns) — that becomes an *optional* core service (`core.persistence.EntityIndex`), not mandatory.
- **Cross-plugin coupling was fragile string-based hacks**, now replaced by explicit services:
  - vehicle ↔ camera: was scoreboard-tag scanning → now `CameraService` + `VehicleService`.
  - AntiAir → vehicle damage: was spawning a throwaway ArmorStand tagged `antiaircraft_entity` and firing a
    sourced explosion that each vehicle's listener recognised → now a direct `VehicleCombatService.antiAirHit(entity)`
    call. **Semantics preserved exactly: 1 creeper of HP, flat, no knockback, no block break.**

## A4. User's fixed decisions (do NOT revisit)
- One Maven project / one jar / one `plugin.yml` / one `JavaPlugin`. Root package `me.bibo.militarycraft`.
- **Permissions:** unified `militarycraft.<module>.<action>` (+ `militarycraft.admin` implies all).
- **Config:** a single `config.yml` with one top-level section per module + a `modules:` toggle section.
- **Commands:** ONLY `/mc <module> <action> [args]` (alias `/militarycraft`). No per-plugin commands/aliases.
  Global admin: `/mc reload`, `/mc modules`, `/mc cleanup`.
- **Clean slate:** no migration of existing in-world entities from the old plugins.

---

# PART B — ENVIRONMENT & BUILD

- **Project root:** `F:\program\MilitaryCraft` (writable). NOTE: the session's "primary" dir
  `F:\programa\Claud` is **read-only** — always work in `F:\program\MilitaryCraft`.
- **Toolchain:** Java 21.0.9, Maven 4.0.0-rc-5. `paper-api 1.21.4` is cached in `~/.m2` → **offline builds work.**
- **Build commands (run from `F:\program\MilitaryCraft`):**
  ```
  mvn -o -q compile              # fast compile check — run after EVERY change; green = EXIT 0
  mvn -o -q -DskipTests package   # produce target/MilitaryCraft-1.0.0.jar
  mvn -o -q test                 # unit tests (Moto DriveMath / MotorcycleIndex — wired in CP7)
  ```
- **Test server (for the human to run — you cannot):** `F:\program\server 1 21 4` (Paper 1.21.4).
  You can only compile; runtime behaviour (rendering, driving, combat) is verified by the user on that server.

---

# PART C — ARCHITECTURE

## C1. Layering
```
MilitaryCraftPlugin (the ONE JavaPlugin)
  ├─ Core            facade passed to every module: config · events · commands · items · models · vehicles · combat · camera
  └─ ModuleManager   hardcoded List<MilitaryModule>, each gated by modules.<id>.enabled (default true)

Bukkit glue (listeners / commands / GUI)   ← thin, per module
        │
Feature modules (tank, jet, antiair, warkit, …)   ← all the game logic
        │
core framework   ← everything shared; ZERO duplication lives here
        │
Paper API
```

## C2. The plugin main (already written — `MilitaryCraftPlugin.java`)
`onEnable`: `saveDefaultConfig()` → `Keys.init(this)` → build `EventBus`, services, `Core` → build
`ModuleManager` with the hardcoded module list → `enableAll(core)` → wire the `mc` command.
`onDisable`: `moduleManager.disableAll()`. `reloadAll()`: `reloadConfig()` → `core.refreshConfig()` →
`moduleManager.reloadAll(core)`.
**To add a module:** add `new XModule()` to the list literal in `onEnable`. That's the only wiring.

## C3. `MilitaryModule` (the module contract — `core/module/MilitaryModule.java`)
```java
public interface MilitaryModule {
    String id();                    // "tank", "antiair", "airstrike", "warkit", ...
    void enable(Core core);         // build settings, stand up manager, register listeners/subcommands/camera scale
    void disable();                 // eject riders, persist, cancel tasks, unregister — DO NOT delete entities
    default void reload(Core core) {}   // rebuild the settings snapshot; push to live objects
}
```

## C4. `Core` facade (`core/Core.java`) — the only handle a module gets
| Call | Returns / does |
|---|---|
| `core.plugin()` | the `MilitaryCraftPlugin` (`JavaPlugin`) |
| `core.config()` | root `ModuleConfig`; `core.config().section("<id>")` → module's `ModuleConfig` |
| `core.events()` | `EventBus` — `register(sink)` / `unregister(sink)` |
| `core.commands()` | `RootCommand` — `register("<id>", List<SubCommand>)` / `unregister("<id>")` |
| `core.items()` | `ItemFactory` |
| `core.models()` | `ModelBuilder` |
| `core.vehicles()` | `VehicleService` |
| `core.combat()` | `VehicleCombatService` |
| `core.camera()` | `CameraService` |
| `core.logger()` | `java.util.logging.Logger` |
| `core.registerListener(Listener)` | register a normal Bukkit listener for events not on the EventBus |
| `core.runSync(Runnable)` / `core.runAsync(Runnable)` / `core.scheduler()` | scheduling |

> `Keys` and `Text` are **static utilities**, NOT reached through `core`. Call `Keys.of(...)`, `Text.of(...)` directly.

---

# PART D — CONVENTIONS (every module obeys; these are the real signatures)

### D1. Keys & PDC (`core/key/`)
- **Only** way to make a key: `Keys.of("<moduleId>", "name")` → `NamespacedKey militarycraft:<module>_<name>`.
  (After the merge every key is in the `militarycraft` namespace, so the module prefix prevents collisions.)
- `Pdc` typed helpers on a `PersistentDataContainer`: `setString/getString(pdc,key,def)`,
  `setDouble/getDouble`, `setInt/getInt`, `setByte/getByte`, `getUuid(pdc,key,def)`.
- `EntityTag`: `EntityTag.tag(entity, "<moduleId>")` stamps the global scoreboard tag `"militarycraft"` +
  PDC `core_module = <moduleId>`. `EntityTag.moduleOf(entity)` reads it back. A world sweep / `/mc cleanup`
  finds all our entities by the scoreboard tag; each manager filters its own by `moduleOf`.
- `ModelData`: one `public static final int` per placer/gear item (Tank=7341, Kamaz=7342, …). **Never reuse.**

### D2. Text (`core/text/Text.java`) — Adventure, hex-capable
- `Text.of("&a...&#ff0000...")` → `Component` (legacy ampersand incl. `&#rrggbb`).
- `Text.msg(sender, "&a...")` → send a colored chat line (with the `&8[&bMC&8]&r ` prefix scheme).
- `Text.lore("line1", "line2", ...)` → `List<Component>` (gray, non-italic) for item lore.
- Keep user-facing strings in English after the 2026-07-15 localization pass. Transliterate proper names
  inside object names, for example Kamaz "Pushinka", Train "Desert Express" and Artillery "Belochka".

### D3. Config (`core/config/`)
- `ModuleConfig c = core.config().section("<id>")`. Getters (clamped variants available):
  `c.getDouble(path, def)`, `c.getDouble(path, def, min, max)`, `c.getInt(path, def)`, `c.getInt(path, def, min, max)`,
  `c.getBoolean(path, def)`, `c.getString(path, def)`, `c.block(path, fallbackMaterial)` (validated block material),
  `c.section(name)` (nested).
- Build an **immutable `<X>Settings`** object from the section in the constructor (all `public final` fields),
  rebuilt on enable and on reload. See `KamazSettings` — copy that pattern exactly.

### D4. Items (`core/item/ItemFactory.java`)
- `core.items().build(Material, "name", NamedTextColor, List<Component> lore, int modelData, NamespacedKey tagKey)` → `ItemStack`.
- `core.items().isTagged(item, tagKey)` → boolean. Tag key is `Keys.of("<module>","item")`.

### D5. Commands (`/mc` only)
- A module builds a `List<SubCommand>` and calls `core.commands().register("<id>", list)`.
- `SubCommand` = `{ String name(); String permission(); void execute(sender,args); List<String> tabComplete(sender,args); }`.
  `permission()` returns `militarycraft.<id>.<action>` (or null for no extra check; `militarycraft.admin` implies all).
- `args` are the tokens AFTER `/mc <id> <sub>`. Reuse `CommandArgs`: `player(sender)`, `resolvePlayer(name)`,
  `coord(token, base, allowRelative)` (the `~`/`~n` parser), `giveItem(player, stack)` (drops overflow).
- The simplest way to declare subcommands is the tiny record adapter at the bottom of `KamazCommands` — copy it.

### D6. Permissions
`militarycraft.<module>.<action>` (e.g. `militarycraft.tank.use`, `militarycraft.antiair.admin`).
Declare them in `plugin.yml`. Convention for vehicles: `use`/`place` default `true`, `give`/`spawn`/`remove`
default `op`. `militarycraft.admin` (default `op`) bypasses per-module limits and implies everything.

### D7. Events (`core/event/`) — avoid duplicate Bukkit listeners
- Shared hot events fan out through **one** `EventBus` listener to module-registered sinks. Implement the sink
  interface(s) you need and `core.events().register(this)`:
  - `ExplosionSink` → `onEntityExplode(EntityExplodeEvent)`, `onBlockExplode(BlockExplodeEvent)`
  - `EntityLifecycleSink` → `onEntitiesLoad(EntitiesLoadEvent)`, `onEntitiesUnload(EntitiesUnloadEvent)`
  - `InteractSink` → `onPlayerInteract(PlayerInteractEvent)`, `onPlayerInteractEntity(PlayerInteractEntityEvent)`
  - `DamageSink` → `onEntityDamage(EntityDamageEvent)`
- `VehicleManager` already implements `EntityLifecycleSink` + `DamageSink` for you (via `attach`).
- Events NOT on the EventBus (dismount, arm-swing, quit, GUI clicks, chunk-specific) → a normal `Listener` via
  `core.registerListener(this)`. A module class can implement **both** a sink AND `Listener` (see `KamazListener`).

---

# PART E — CORE MODEL & VEHICLE API (the machinery you build vehicles on)

## E1. Model primitives (`core/model/`)
- **`Part`** — one immutable model piece in vehicle-local space (origin at ground anchor; +X right, +Y up,
  +Z forward). Fields: `int group` (vehicle-defined articulation channel; `PartGroup.STATIC` = 0 = body),
  `Vector3f offset, scale`; `float pitch, yaw, roll` (base rotation in degrees); `Material material` (null for text);
  `String text` (non-null → TextDisplay). Factories: `Part.block(group, offset, scale, material)`,
  `Part.block(group, offset, scale, material, pitch, yaw, roll)`, `Part.text(group, offset, text)`.
- **`VehicleModel`** (record): `(List<Part> parts, float width, float height, float length, double seatHeight,
  float[] hitboxZOffsets, int centerHitboxIndex)`. Dims feed the base ray/melee/sweep hit tests; the hitbox row
  is spaced along local Z; `centerHitboxIndex` defines the anchor on rehydrate.
- **`Transforms`** — primitives ONLY (each vehicle writes its own articulation using these):
  `yawQuat(deg)`, `pitchQuat(deg)`, `rollQuat(deg)` → `Quaternionf`; `rotateAbout(point, pivot, quat)`;
  `localPointToWorld(Vector3f localPoint, double hullYaw)` → `Vector3f` world offset;
  `build(Part part, Vector3f worldOffset, Quaternionf worldRot)` → `Transformation` (centres the unit cube on the offset).
- **`ModelBuilder`** (`core.models()`): `spawnSeat(loc, moduleId, id)`, `spawnHitbox(loc, w, h, index, moduleId, id)`,
  `spawnBlockDisplay(loc, part, index, moduleId, id, DisplayConfig)`, `spawnTextDisplay(...)`. Each tags the entity
  (scoreboard + `core_module` + `core_role` + `core_index` + `<module>_id`) so `DisplayVehicle.group` reads it back.
- **`DisplayConfig`** — standard display setup (brightness 15/15, view-range ~4 for far camera,
  teleport-duration == interpolation-duration, persistent). Use `DisplayConfig.STANDARD` unless you need custom.

## E2. `DisplayVehicle` (abstract base — `core/vehicle/DisplayVehicle.java`)
Holds: `id, world, anchor (x/y/z only), health, driver, seat, hitboxes, displays, partDefs, extraSeats`.
The base owns spawn/rehydrate/refresh/persist/damage/destroy/ride and implements `VehicleHandle`.

**You MUST implement (abstract):**
| Method | Purpose |
|---|---|
| `String moduleId()` | e.g. `"tank"` |
| `VehicleModel model()` | usually a cached `XModel.model(settings)` |
| `double maxHealth()` | from settings |
| `Transformation transformFor(Part part)` | your articulation, using `Transforms` primitives + your own angle fields |
| `Location hitboxLocation(int index)` | articulated hitbox placement |
| `void writeExtraState(PDC)` / `void readExtraState(PDC)` | persist/restore YOUR angle fields on the seat |

**You MAY override (sane defaults exist):**
`onSpawnEffects()`, `onDestroyEffects()` (default = internal explosion + smoke), `partNeedsRetransform(part, moved, anythingChanged)`
(default true; override to restore Tank's per-group skip), `seatLocation()` (default straight up), `extraSeatLocation(int)`
(multi-seat), `facingYaw()` (default 0 — return hull yaw/heading; feeds base ray/melee/sweep), `seatCount()`, `seatHeight()`,
`creeperDamageUnit()` (default `maxHealth()/4`; override to your config's creeper-damage so N creepers = destroy).

**The base gives you (call these):** `spawnCluster(models)`, static `group(entities, partCount, hitboxCount)` +
instance `adopt(groups, parts, models)` + static `readHealth(pdc, fallback)` (the rehydrate recipe),
`persistState()`, `tickPersist()`, `markStateDirty()`, `refreshModel()`, `damage(amount)→destroyedBool`,
`applyAntiAirHit()`, `applyExplosion(loc,power)`, `tickDamageEffects()`, `destroy(effects)`, `removeEntities()`,
`mount(player)`, `eject()`, `clearDriver()`, `isActive()`, `isSpawned()`, `isOccupied()`, getters `world()/anchor()/seat()/health()/driver()`.

**Rehydrate recipe (copy from `Kamaz.rehydrate`):**
```
1. Groups g = DisplayVehicle.group(entities, XModel.parts(s).size(), XModel.HITBOX_COUNT);
2. if (g.seat == null) return null;
3. Interaction anchorBox = g.hitboxes[CENTER] != null ? g.hitboxes[CENTER]
        : (g.strayHitboxes.isEmpty() ? null : g.strayHitboxes.get(0));   if (anchorBox == null) return null;
4. for (Display d : g.parts) if (d == null) return null;   // incomplete model
5. double hp = DisplayVehicle.readHealth(g.seat.getPersistentDataContainer(), s.maxHealth);
6. X x = new X(mgr, id, anchorBox.getWorld(), loc.x, loc.y, loc.z, 0, hp, ...);
7. x.readExtraState(g.seat.getPersistentDataContainer());
8. x.adopt(g, x.model().parts(), mgr.core().models());
9. return x;
```

## E3. `VehicleManager<V extends DisplayVehicle>` (abstract — `core/vehicle/VehicleManager.java`)
Owns: registry `Map<UUID,V>`, `driverToVehicle`, the per-tick loop, pilot protection/cloak, adopt/forget,
purge, and ray/melee/sweep helpers.

**You MUST implement:** `String moduleId()`, `V create(Location, double yaw)`, `V rehydrate(UUID, List<Entity>)`,
`void driveTick(V, Player driver)` (delegate to your `XController`).
**You MAY override:** `onVehicleTick(V)` (runs EVERY tick per active vehicle **regardless of driver** — water/drown,
projectile-sweep, cooldown countdown, rotor animation), `onDriverAttacked(V, driver, EntityDamageByEntityEvent)`
(route weapon damage to the vehicle; default no-op — driver still takes 0), `enter(V, player)` / `handleDismount(player)`
(extend for extra seats), `onEntityDamage` (only if you protect passengers too, like Kamaz).
**The base gives you:** `attach(core)` (registers with VehicleService + EventBus — call in module enable),
`start()/stop()/adoptExisting()/shutdown()`, `spawn(loc,yaw)` (create + register), `byId/byDriver/byEntity/all/count`,
`enter(v,player)` (mount + invisibility + cloak), `handleDismount(player)`, `remove(v, effects)`, `purgeAll()→[tracked,strays]`,
`rayTraceFrom(eye,reach)→V`, `findMeleeTarget(player,reach,pad)→MeleeHit`, `projectilesInBody(v,pad)→List<Projectile>`.

## E4. Combat & camera services
- `core.combat()` (`VehicleCombatService`): `antiAirHit(Entity vehiclePart)→boolean` (direct 1-creeper hit),
  `explosionDamage(Location, double power)` (routes a blast to all vehicles). AntiAir calls `antiAirHit`.
- `core.vehicles()` (`VehicleService`): `vehicleOf(Entity)→VehicleHandle`, `riddenBy(Player)→VehicleHandle`,
  `typeOf(Entity)→String`, `all()`, `registerManager(mgr)` (done by `attach`). AntiAir targeting uses `riddenBy`.
- `core.camera()` (`CameraService`): `registerScale("<type>", scale)` — each vehicle module calls this in enable
  (and reload); the camera reconcile loop applies the `scale` attribute to riders. Scale comes from the module's
  `camera-scale` config value.
- `core.combat().Explosions` helpers: `Explosions.createExplosion(world, loc, power, setFire, breakBlocks)` (with the
  internal-explosion guard so our own blast doesn't re-damage us), `Explosions.impactFx(loc)`.
  `Projectiles.isWeaponProjectile(proj)` classifies arrow/fireball/firework.

---

# PART F — THE REFERENCE MODULE (Kamaz) — copy this structure for every vehicle

`vehicles/kamaz/` (9 classes) is the **template**. Every vehicle module has the same shape:

| File | Role | Key points (from the real code) |
|---|---|---|
| `KamazModule` | `MilitaryModule` | `enable`: build `KamazSettings`; `new KamazManager(core, settings)`; `manager.attach(core)`; `adoptExisting()`; `start()`; register `KamazListener` (both `core.events().register(l)` for InteractSink AND `core.registerListener(l)` for dismount/swing/quit); `core.commands().register("kamaz", new KamazCommands(mgr).all())`; `core.camera().registerScale("kamaz", settings.cameraScale)`. `disable`: `manager.shutdown()`, unregister listener + command. `reload`: rebuild settings, `manager.setSettings(...)`, re-register camera scale. |
| `Kamaz` | `extends DisplayVehicle` | Owns hull yaw + wheel spin/steer + submersion + run-over cooldown + transient passenger seats. Implements the 6 abstract seams; overrides `facingYaw`, `creeperDamageUnit`, `seatLocation`, `onDestroyEffects`, `removeEntities`. Static `create(mgr,at,yaw,owner)` → `new Kamaz(...); k.spawnCluster(mgr.core().models())`. Static `rehydrate(...)` = the recipe. `transformFor` composes hull-yaw quat × per-group wheel spin/steer via `Transforms.build`. |
| `KamazManager` | `extends VehicleManager<Kamaz>` | Implements `create/rehydrate/moduleId/driveTick`; overrides `onVehicleTick` (drown + passenger prune + projectile sweep), `onDriverAttacked` (weapon %), `onEntityDamage` (protect passengers too), `enter/handleDismount/shutdown` (passenger seats). Also spawn-limit / world-border / space validation. |
| `KamazModel` | static geometry | Ports the source `TruckModel`: builds `List<Part>` with `Part.block(group, v(x,y,z), v(w,h,l), material, [rot])`; the old `Role` enum → resolved `Material` from settings; wheel `rollsWith/steersWith` flags → the `Part.group` channel. Exposes `WIDTH/HEIGHT/LENGTH`, `HITBOX_COUNT`, `CENTER_HITBOX_INDEX`, `model(settings)→VehicleModel`. |
| `KamazController` | drive math | Ported `DriveController.drive(vehicle, driver, settings)` verbatim (throttle, steer, terrain snap, run-over/fling/crush). Called from `driveTick`. |
| `KamazSettings` | config snapshot | Immutable `public final` fields; ctor reads `ModuleConfig` key-for-key from the source `KamazConfig`. `maxHealth = creeperDamage * creepersToDestroy`. |
| `KamazItem` | placer item | `create(items)` via `ItemFactory.build(MATERIAL, name, color, Text.lore(...), ModelData.KAMAZ, Keys.of("kamaz","item"))`; `isKamazItem(items, stack)`. |
| `KamazListener` | `InteractSink, Listener` | Interact (place/enter) via InteractSink; dismount (`EntityDismountEvent`) / swing (`PlayerAnimationEvent`) / quit via `@EventHandler`. |
| `KamazCommands` | subcommands | `all()` → `List<SubCommand>` using the private `record Sub(...) implements SubCommand` adapter; maps give/spawn/place/remove/list. |

**Then wire it:** add `new KamazModule()` to `MilitaryCraftPlugin`'s module list; add the `kamaz:` section to
`src/main/resources/config.yml`; add `militarycraft.kamaz.*` to `plugin.yml`; add `KAMAZ` to `ModelData`.

---

# PART G — HOW TO ADD A NEW VEHICLE MODULE (checklist)
1. `mkdir src/main/java/me/bibo/militarycraft/vehicles/<id>/`. Read the source plugin fully + read `vehicles/kamaz` as the template.
2. `XSettings` ← port every key from the source `<X>Config` into an immutable snapshot from `config.section("<id>")`.
3. `XModel` ← port the source model geometry onto `Part`/`VehicleModel`; map articulation flags to `Part.group` channels.
4. `X extends DisplayVehicle` ← own the articulation angle fields; implement the 6 seams; override hooks as needed.
5. `XController` ← port the source drive/flight math; called from `driveTick`.
6. `XManager extends VehicleManager<X>` ← implement create/rehydrate/moduleId/driveTick; override onVehicleTick /
   onDriverAttacked / enter / handleDismount as the source needs. Add weapons here (shells/rockets/bombs) via `core.combat`.
7. `XItem` ← placer via `ItemFactory` + new `ModelData.X`.
8. `XListener` ← place/enter (InteractSink) + dismount/swing/quit (@EventHandler).
9. `XCommands` ← give/spawn/place/remove/list (+ any vehicle-specific like `fire`) via the `Sub` record adapter.
10. `XModule` ← the enable/disable/reload wiring (copy `KamazModule`).
11. Wire: add to `MilitaryCraftPlugin` list; add `<id>:` config section (port source `config.yml` values +
    `camera-scale`); add `militarycraft.<id>.*` perms to `plugin.yml`; add `ModelData.X`.
12. `mvn -o -q compile` → GREEN. Tick the box in `STATUS.md`, update `PLAN.md` §5.

---

# PART H — PER-MODULE SPEC FOR THE REMAINING WORK

> For each: the source folder, target package, the base seams/hooks it needs, behaviours to PRESERVE, and any
> NEW core piece to build first. Always read the source plugin fully before porting; write user-facing
> text in English unless the token is a preserved proper name.

## H1. CP3b — Tank  (`TankCraft` → `vehicles/tank`)  ✅ done — the weapon/turret reference
- **Articulation:** three channels — HULL (hull yaw), TURRET (turret yaw), BARREL (barrel pitch). `transformFor`
  must reproduce the source `Transforms.forPart` (turret pivots about the ring, barrel elevates about the trunnion
  then rides the turret). **Override `partNeedsRetransform`** to restore the per-group skip (HULL parts skip when
  only the turret moves, etc.).
- **`onVehicleTick`:** water/drown (`refreshSubmerged`/`tickWater`), enemy-projectile sweep into the body,
  and countdown of `reload`/`weaponLock`/overheat — all regardless of driver.
- **`onDriverAttacked`:** route arrow/fireball/melee to tank HP at the config percentages.
- **Weapons:** port `combat/ShellManager` + `Shell` (ballistic shell, block-breaking explosion) → put shell
  logic under `vehicles/tank/combat/`, using `core.combat.Explosions`/`Projectiles`. Fire on left-click while seated.
- **`creeperDamageUnit()`** ← `settings.creeperDamage` (durability = 4 creepers by default).
- **Camera scale** ~ 3.0. Subcommands: give/spawn/place/remove/list.

## H2. CP3c — Fighters/fliers  (`JetCraft`,`HeliCraft`,`AirshipCraft`,`DroneCraft`)  ✅ done
Common: flight controllers instead of ground drive; `facingYaw()` = heading; weapons = rockets (`core.combat.Projectiles`/
`Explosions`) on left-click, bombs on right-click; pilot protection identical; camera pulled way back (scale 4–6).
- **Jet:** camera-steer flight (bank/pitch/roll), fast; port `control/FlightController` + `combat/WeaponSystem`.
  `transformFor` uses yaw+pitch+roll of the whole body.
- **Helicopter:** hover + low-speed; **rotor spins continuously on hover** (main rotor about Y, tail about X) — animate
  in `onVehicleTick`; forward rockets + bombs. Fork of airship movement + jet weapons.
- **Airship:** lighter-than-air; controls = heading + altitude (rise/sink), slow; bombs with area blast + smoke.
  Was the one hardcoding the `antiaircraft_entity` tag — that path now comes through `applyAntiAirHit()` automatically
  (nothing to special-case).
- **Drone:** piloted "from inside" (the player sees through it); one-shot rockets; kamikaze on impact/ram; a `fire`
  subcommand. Small/agile.

## H3. CP3d — Moto, Pickup, Train
✅ Implemented and green. The notes below describe the intended shape that is now present in the codebase.

- **Moto** (`MotoCraft`): keep the source's **`DriveMath`** (pure, unit-tested) — move it to `vehicles/moto` (or
  `core.util`) WITH its JUnit test (`DriveMathTest`). Sidecar passenger via `extraSeats`/`extraSeatLocation`.
  Uses a durable index → **build `core.persistence.EntityIndex`** (port `MotorcycleIndex`: tombstoned YAML, spawn
  cooldowns, atomic write) as a reusable optional service, WITH its test (`MotorcycleIndexTest`). Highest-quality source — mirror its rigor.
- **Pickup** (`PickupCraft`): driver + passenger + an **independent gunner seat** — model the gunner as a persisted
  extra seat (`seatCount()≥2`, `extraSeatLocation(1)`, its own hitbox); gunner fires a machine gun (`combat/GunManager`),
  HUD (`control/Hud`). **Drop** the legacy `jeepcraft_entity` migration entirely (clean slate).
- **Train** (`TrainCraft`): **outlier** — locomotive + 3 cars over vanilla rails. Keep the `rail/` package
  (RailTracer/RailEdge/RailCursor/TrainPath). Each car is a `DisplayVehicle`; a `Train` composes cars over a
  `RailPath` and overrides movement (rail-following, not free drive). Reuse only seat/model/persist from the base.
  Keep the `ProtectionListener` (standing on the tracks in front is lethal). Do this LAST.

## H4. CP4 — Placeables: AntiAir + TCKBus  (build `core.placeable` first)
`core.placeable` now exists with focused tests. The remaining CP4 work is to wire AntiAir and TCKBus onto it.

- **`core.placeable`** (NEW): `PlaceableRig` (a stationary display cluster: model + hitbox + PDC state, NO seat/
  movement; reuse `ModelBuilder`, persist/rehydrate/damage like `DisplayVehicle` but without ride/drive) and
  `PlaceableManager` (registry + adopt/forget via `EntityLifecycleSink` + purge). Generalise from AntiAir's `Turret`/`TurretManager`.
- **AntiAir** (`AntiAirCraft` → `weapons/antiair`): a `PlaceableRig` turret. Port the **GUI** (`TurretMenu` implements
  `InventoryHolder` + a local GUI `Listener` for clicks), the **fuel** system (`FuelTable`, furnace-style burn),
  **targeting** (`TargetingSystem`: hitscan bullets, modes Normies/SVO). Replace the throwaway-explosion vehicle
  hit with `core.combat().antiAirHit(vehiclePart)`; find rider vehicles via `core.vehicles().riddenBy(player)`.
  Config `vehicleTags` list is gone — use `VehicleService` instead. GUI/right-click to open the panel.
- **TCKBus** (`TCKBus` → `weapons/tckbus`): a `PlaceableRig` van + **Pillager snatch AI** (`SnatchManager`: two
  "TCK" pillagers walk up, stun, drag the victim into the bus) + custom drop store (`DropStore`, `/mc tckbus setdrop`
  captures the sender's hotbar). Skins tck/tzahal. Break the bus (explosion/hand) → drops the custom loot.

## H5. CP5 — Aerial ordnance: Airstrike + Nuke  (build `core.airsupport` first)
- **`core.airsupport`** (NEW): `OrdnanceRun` (abstract `BukkitRunnable`): a bomber display model flies a straight
  path, keeps a sliding plugin chunk-ticket window around the jet + target, warns nearby players (action bar),
  drops ordnance on a schedule, dramatic finish, teardown. `BomberPath` (heading/geometry). `StrikeRegistry`
  (per-player cooldown + max-active cap + area separation). Generalise from `AirstrikePlugin`'s `AirstrikeSequence`+`AirstrikeManager`.
- **Airstrike** (`AirstrikePlugin`/`ru.airstrike` → `weapons/airstrike`): Su-57 model (`Su57Model`), carpet of `TNTPrimed`
  along the run. A briefcase item + `/mc airstrike call|give`. Convert its Adventure `message()` idiom to `Text`.
- **Nuke** (`NukeStrike`/`ru.nukestrike` → `weapons/nuke`): sibling — a single slow-falling Fat-Man `BlockDisplay`
  (`NukeBombModel`) + crater + mushroom (`NukeCloudModel`) + blinding + `RadiationManager` (lingering magic-damage
  effect on survivors; keep it, it ticks independently of the run). Shares `core.airsupport`.

## H6. CP6 — Gear: WarKit  (`WarKit` → `gear/warkit`)
Mostly **lift-and-shift** (31 classes). Keep its internal structure: `Weapons` registry (`registerAll()`), `WarItems`
(item factory by id), the `*Service`/`*Manager` set (Gun/Grenade/Spray/Deployable/Sentry/Trench/Explosives/Gadget…),
`ChannelManager` (charge/channel bars via per-viewer particles — NOT ProtocolLib), `PainkillerManager`, `CamoManager`,
`MarkerManager`. **Rewire only:** its `Txt`/`ItemTools` → delegate to core `Text`/`ItemFactory`; every `NamespacedKey`
→ `Keys.of("warkit", …)`; register its many listeners via `core.registerListener`; its `Ticker` via `core.scheduler()`;
command → `/mc warkit <give|giveall|list>`; perms → `militarycraft.warkit.*`. It does NOT touch the vehicle core.

## H7. CP7 — Final assembly
Final `plugin.yml` (all module permissions), full `config.yml` (every module section + `modules:` toggles +
`camera:` scales), confirm every vehicle registered its camera scale, `mvn -o -q -DskipTests package` → jar,
wire + pass the two unit tests (`DriveMathTest`, `MotorcycleIndexTest`), write a `KNOWN_ISSUES` list of anything
compile-only-verified that the human must test on the server.

---

# PART I — CODING STANDARDS (match the existing code)
- **Ponytail / minimalism:** the smallest code that works. Reuse the base — never re-implement persistence,
  rehydration, `refreshModel`, ray/melee/sweep, pilot protection, cloak, or blast routing in a module. No speculative
  abstractions, no interface with one impl beyond the spec's sinks/module/subcommand contracts, no God-classes.
- **Immutability for settings:** `<X>Settings` is all `public final`, built once per enable/reload. Never read
  `getConfig()` in hot paths — read the snapshot.
- **State on the entity:** persist articulation angles + owner via `writeExtraState`/`readExtraState` on the seat PDC;
  never invent a side DB (except the optional `EntityIndex` where the source used one — Moto only).
- **Dirty-checking:** the base already throttles persist (20 ticks) and skips unchanged transforms; don't fight it.
- **Adventure text only**, English user strings with preserved proper names. Match the existing comment density and Javadoc style
- **Adventure text only**, English user strings with preserved proper names. Match the existing comment density and Javadoc style
  (short "why", not "what"); every non-trivial class gets a header Javadoc like the ones in `core`.
- **Any change under `core/**` is shared** — make it minimal, keep an overridable default, and note it in `STATUS.md`
  (a module needing a new base seam is usually a sign to override an existing hook instead).

# PART J — GOTCHAS (learned from the source plugins; will bite you if ignored)
- **BlockDisplay yaw handedness:** rotate a part's position AND orientation with **one** matrix/quaternion
  (compose offset-rotation and articulation together), or the model shears apart under non-zero yaw. See `Kamaz.transformFor`.
- **Display smoothness:** for moving parts, `teleport` duration must equal `interpolationDuration`, call
  `setInterpolationDelay(0)` before `setTransformation`, and set a large view-range for the far F5 camera. The base
  `refreshModel` + `DisplayConfig` already do this — don't undo it.
- **Camera clearance:** raise (and often push back) the driver seat so a big model doesn't bury the F5 camera inside
  itself (`seatLocation()` override; Kamaz raises + offsets rearward).
- **Transient vs persisted entities:** transient helper entities (e.g. Kamaz passenger seats) get `EntityTag.tag(...)`
  (so a purge sweeps orphans) but **NOT** a `<module>_id` PDC — otherwise the chunk-load adopter tries to fold them
  into a model and corrupts rehydration. Persisted parts get the full tag set (via `ModelBuilder`).
- **Single ExplosionSink:** `VehicleCombatServiceImpl` is the ONE `ExplosionSink` that fans a blast across all vehicle
  types; a `VehicleManager` must NOT also register as an `ExplosionSink` (it would apply the blast N times).
- **Internal-explosion guard:** vehicle self-destruct / shells use `Explosions.createExplosion(...)` which sets an
  internal flag so the resulting `EntityExplodeEvent` doesn't re-damage the very vehicle that fired it.
- **Pilot protection is unconditional:** a seated driver (and Kamaz passengers) take **0** direct damage — that's the
  base rule; routing damage to the vehicle HP is the opt-in (`onDriverAttacked`). Never let the rider take damage directly.
- **World-copy flush** (only if any feature copies a loaded world, e.g. a future arena): `setAutoSave(false)` + save +
  flush + wait before copying, or you get torn `.mca` chunks. (Not needed by the current 15, noted for completeness.)

# PART K — HOW TO KNOW A STEP IS DONE
`cd F:\program\MilitaryCraft && mvn -o -q compile` → **EXIT 0** means everything so far compiles. A checkpoint is
"done" only when its files exist, the compile is green, and its box is ticked in `STATUS.md`. A half-finished
checkpoint usually fails to compile — if so, delete that module's folder and revert its edits to
`MilitaryCraftPlugin.java` / `config.yml` / `plugin.yml` / `ModelData.java` to return to the last green state.

---

## Current state (see STATUS.md for the live version)
Done & green: **CP1** (core framework), **CP2** (model/vehicle/combat cores + camera), **CP3a** (Kamaz — the reference),
**CP3b** (Tank — weapon/turret/water reference), **CP3c** (Jet, Helicopter, Airship, Drone),
**CP3d** (Moto, Pickup, Train), **CP4** (AntiAir + TCKBus on `core.placeable`), **CP5** (Airstrike + Nuke),
**CP6** (WarKit), **CP7** (resources/tests/package readiness), plus Artillery "Belochka".
Current green baseline: 224 main Java files, `mvn -o test` = green (75 tests).
