# MilitaryCraft — Build Specification (authoritative)

This document is the **single source of truth** for merging 15 standalone Paper 1.21.4
plugins into one modular plugin, `MilitaryCraft`. Sonnet subagents implement **strictly
against this spec** — do not redesign. If something is genuinely ambiguous, prefer the
existing behaviour of the source plugin and leave a `// TODO(spec):` note.

Source plugins (all under `F:\program\<Name>`):
Vehicles: TankCraft, KamazCraft, PickupCraft, JetCraft, HeliCraft, AirshipCraft, DroneCraft, MotoCraft, TrainCraft
Weapons: AntiAirCraft, AirstrikePlugin, NukeStrike
Gear: WarKit • Trap: TCKBus • Support: VehicleCamera

---

## 0. Non-negotiable facts (verified in Stage 0)

- Build: **Maven, Java 21, `paper-api 1.21.4-R0.1-SNAPSHOT` (scope provided)**. No shaded libs.
  No ProtocolLib / Vault / PlaceholderAPI / WorldGuard anywhere. Adventure is used (bundled in Paper).
- Offline build works: `paper-api 1.21.4` is cached in `~/.m2`. Build with:
  `mvn -o -q -DskipTests package` (use `-o` offline; `mvn -o -q compile` for fast checks).
- Storage model everywhere: **state on the entity via PersistentDataContainer** (written on the
  seat/anchor entity), settings in YAML. No SQL. MotoCraft additionally keeps a durable YAML index
  (tombstones + spawn cooldowns) — this becomes an **optional** core service, not mandatory.
- Cross-plugin coupling today is ONLY:
  1. Transport ↔ VehicleCamera: implicit, via scoreboard tags. Camera reads tags, applies `scale` attribute.
  2. AntiAir ↔ vehicles: AntiAir spawns a throwaway ArmorStand tagged `antiaircraft_entity` at the
     vehicle and fires a sourced `createExplosion`; each vehicle's DamageListener hardcodes a check for
     that tag and converts it to exactly **1 creeper of HP damage, no knockback, no block break**.
  No `getPlugin()` / ServicesManager usage between the 15.

## 1. Approved decisions (from the user — do not revisit)

- **One Maven project, one jar, one plugin.yml, one JavaPlugin.**
- Root package: **`me.bibo.militarycraft`**. The two `ru.*` plugins move here too.
- **Permissions**: unified scheme **`militarycraft.<module>.<action>`** (+ `militarycraft.admin`).
  Old nodes (tankcraft.*, nuke.*, …) are dropped.
- **Config**: a single `config.yml` with one top-level section per module + a `modules:` toggle section.
- **Commands**: **only `/mc <module> <action> [args]`** (alias `/militarycraft`). The old per-plugin
  commands (`/tank`, `/jet`, `/pvo`, …) and their aliases are removed. Admin: `/mc reload`,
  `/mc modules`, `/mc cleanup`.
- **Data**: clean slate. No migration of existing in-world entities. `/mc cleanup` sweeps orphans.

## 2. Project layout

```
F:\program\MilitaryCraft\
  pom.xml
  BUILD_SPEC.md               (this file)
  src/main/resources/
    plugin.yml
    config.yml                (assembled: modules + per-module sections)
  src/main/java/me/bibo/militarycraft/
    MilitaryCraftPlugin.java  (the only JavaPlugin)
    core/
      Core.java               (facade handed to every module)
      module/ MilitaryModule.java, ModuleManager.java
      config/ ConfigSupport.java, ModuleConfig.java
      key/    Keys.java, Pdc.java, EntityTag.java, ModelData.java
      text/   Text.java
      event/  EventBus.java + sink interfaces (ExplosionSink, InteractSink, EntityLifecycleSink, ...)
      command/ RootCommand.java, SubCommand.java, CommandArgs.java
      item/   ItemFactory.java
      model/  Part.java, PartGroup.java, Transforms.java, ModelBuilder.java, DisplayConfig.java
      util/   MathUtil.java, Fx.java
      vehicle/ DisplayVehicle.java, VehicleManager.java, VehicleService.java,
               VehicleHandle.java, PilotProtection.java, PilotCloak.java, Seat.java
      combat/ Explosions.java, Projectiles.java, VehicleCombatService.java
      placeable/ PlaceableRig.java, PlaceableManager.java
      airsupport/ OrdnanceRun.java, BomberPath.java, StrikeRegistry.java
      persistence/ EntityIndex.java   (optional durable index; Moto-style)
    camera/  CameraModule.java, CameraService.java
    vehicles/
      tank/  jet/  helicopter/  airship/  drone/  kamaz/  pickup/  moto/  train/
             each: <X>Module.java, <X>.java (extends DisplayVehicle), <X>Manager.java,
                   <X>Model.java, <X>Controller.java, <X>Settings.java, <X>Item.java, combat/...
    weapons/
      antiair/  airstrike/  nuke/
    gear/
      warkit/    (WarKit lifted here; rewired to core text/item/model)
    trap/
      tckbus/
  src/test/java/...            (MotoCraft DriveMath + MotorcycleIndex tests → core.util + core.persistence)
```

Modules are registered in a hardcoded list in `MilitaryCraftPlugin` (no reflection/classpath scan).
Each is gated by `modules.<id>.enabled` (default true).

## 3. Core conventions (ALL modules obey)

### 3.1 Keys / PDC / tags
- `Keys.of(String moduleId, String name)` → `new NamespacedKey(plugin, moduleId + "_" + name)`.
  This is the ONLY way to make a NamespacedKey. Prevents cross-module key collisions after the
  namespace collapses to `militarycraft`.
- Global scoreboard tag on every entity we spawn: `EntityTag.TAG = "militarycraft"`.
- Every spawned entity also gets PDC `Keys.of("core","module")` = module id (e.g. "tank"), so a
  world sweep can group/clean by module. Managers filter their own entities by this value.
- `Pdc` helper: typed get/set for String/UUID/Double/Int/Byte with defaults.
- `ModelData`: central registry of `CustomModelData` int constants for all placer/gear items
  (Tank placer was 7341). One constant per item; never reuse a value.

### 3.2 Text
- `Text` (Adventure). `Text.of("&a...")` parses legacy ampersand (incl hex) → Component.
  `Text.msg(sender, "&a...")`, `Text.item(name,color)`, `Text.lore(...)`. Replaces §-strings,
  Airstrike/Nuke `message()`, and WarKit `Txt`/`ItemTools` (fold those in / delegate to `Text`).
- Prefix for chat feedback: `&8[&bMC&8]&r `. User-facing strings are English after the 2026-07-15
  localization pass, while proper names inside object names are transliterated (for example "Pushinka",
  "Desert Express", "Belochka").

### 3.3 Config
- One `config.yml`. Each module reads `core.config().section("<id>")` → a `ModuleConfig` wrapper
  around `ConfigurationSection` with clamped typed getters (`getDoubleMin`, `material`, `block`, …).
- Reuse the TankConfig pattern: each module builds an immutable typed `<X>Settings` snapshot on
  enable and on reload. `ConfigSupport.block(section, path, fallback)` dedupes the copied material parser.

### 3.4 Commands (`/mc` only)
- `RootCommand` (TabExecutor) is the single command. It dispatches `/mc <module> <sub> [args]` to the
  module's registered `SubCommand` group, and handles `/mc reload|modules|cleanup` itself.
- `CommandArgs` helpers: `player(sender)`, `resolvePlayer(arg)`, `coord(token, base, allowRelative)`
  (the `~`-relative parser), `giveItem(player, stack)` (with inventory-overflow drop).
- Permission checks via `militarycraft.<module>.<action>`; `militarycraft.admin` implies all.

### 3.5 Events (dedupe Bukkit listeners)
- `EventBus` registers a SMALL set of core Bukkit listeners for the hot, shared events and fans out to
  module-registered sinks:
  - `ExplosionSink` ← EntityExplodeEvent + BlockExplodeEvent (vehicles, placeables consume).
  - `EntityLifecycleSink` ← EntitiesLoadEvent + EntitiesUnloadEvent (adopt/forget).
  - `InteractSink` ← PlayerInteractEvent + PlayerInteractEntityEvent (placers, mounting).
  - `VehicleDamageRouter` ← EntityDamageEvent/EntityDamageByEntityEvent (pilot protection + hit routing).
- Modules with genuinely unique events (WarKit's many listeners, TCK snatch AI, Train protection,
  AntiAir GUI clicks) register their own `Listener` directly via `core.registerListener(...)`.
  The rule: if 2+ modules need the same event, it goes through EventBus; otherwise a local listener is fine.

## 4. Core service contracts (interfaces the subagents implement)

### 4.1 `Core` (facade)
Exposes: `plugin()`, `config()`, `keys()`, `text()`, `events()`, `commands()`, `models()` (ModelBuilder),
`vehicles()` (VehicleService), `combat()` (VehicleCombatService), `camera()` (CameraService),
`logger()`, `registerListener(Listener)`, `sync/asyncTask` helpers, `scheduler()`.

### 4.2 `MilitaryModule`
```
public interface MilitaryModule {
    String id();                       // "tank", "antiair", "airstrike", "warkit", ...
    void enable(Core core);            // build settings, register manager/listeners/subcommands/perms
    void disable();                    // eject riders, persist, cancel tasks (no entity deletion on reload)
    default void reload(Core core) {}  // rebuild settings snapshot, push to live objects
}
```

### 4.3 `VehicleService` (core.vehicle)
The unified query surface other modules use instead of scoreboard-tag strings.
```
VehicleHandle vehicleOf(Entity anyPart);      // null if not one of ours
VehicleHandle riddenBy(Player player);        // vehicle the player is seated in (walks vehicle stack)
String typeOf(Entity anyPart);                // module id or null
Collection<VehicleHandle> all();
```
`VehicleHandle` exposes: `id()`, `type()`, `coreEntity()`, `location()`, `applyAntiAirHit()`
(flat 1-creeper, no knockback/block-break — see §6), `applyExplosion(loc,power)`, `isActive()`.

### 4.4 `VehicleCombatService` (core.combat)
Formalizes the AntiAir→vehicle contract as a direct call (replaces the tagged-explosion hack):
```
boolean antiAirHit(Entity vehiclePart);        // -> handle.applyAntiAirHit(); true if it hit a vehicle
void explosionDamage(Location loc, double power); // routes a blast to every nearby vehicle/placeable
```
Behaviour MUST match the old semantics exactly (1 creeper flat within ~8 blocks, no knockback,
no block break). This is the one deliberate behaviour-equivalent refactor — log it in §11.

### 4.5 `CameraService` (camera module)
```
void registerScale(String vehicleType, double scale);  // vehicle modules call on enable
```
Impl: reconcile loop (every N ticks) over online players; `type = vehicles.typeOf(riddenCore)`;
apply `minecraft:scale` attribute modifier keyed `militarycraft:zoom` = configured scale; remove on
dismount. Port VehicleCameraPlugin's attribute logic verbatim; only the "how do we know the vehicle"
changes from tag-scan to `VehicleService`. Scales come from `config.yml` `camera:` section per type.

## 5. Vehicle framework (the big dedupe) — core.vehicle

Model the shared lifecycle seen in Tank/Kamaz/Pickup/Jet/Heli/Airship/Drone/Moto.

### 5.1 `DisplayVehicle` (abstract)
Owns: `UUID id`, `World world`, `Location anchor`, `ArmorStand seat`, `List<Interaction> hitboxes`,
`List<Display> displays`, `List<Part> partDefs`, `double health`, `UUID driver`, dirty/persist flags.
Provides (concrete, shared):
- `create(...)` template: build seat + hitboxes + displays from `model()`, tag all, persist.
- `rehydrate(...)` template: regroup entities by role/index PDC, restore state from seat PDC.
- `persistState()/tickPersist()` (20-tick throttle), `markStateDirty()`.
- `refreshModel()` with the dirty-check + per-group transform skip (Tank's algorithm generalised).
- `damage(amount)->destroyedBool`, `destroy(effects)`, `removeEntities()`, `mount/eject/clearDriver`.
- `applyAntiAirHit()` (flat), `tickDamageEffects`, water/drown hook (opt-in).
Abstract hooks the concrete vehicle supplies:
- `VehicleModel model()` (parts + dims + pivots), `articulate(Part, angles)` via core.model.Transforms,
- `controlTick(player, settings)` (delegated to `<X>Controller`), `Settings settings()`,
- `spawnEffects()/destroyEffects()`, `seatHeight()`, `maxHealth()`.

### 5.2 `VehicleManager<V extends DisplayVehicle>` (abstract)
Owns: `Map<UUID,V> registry`, `Map<UUID,UUID> driverToVehicle`, tick task, cloak/protection state.
Provides: `start/stop/adoptExisting/shutdown/purgeAll`, `onEntitiesLoad/Unload` (via EntityLifecycleSink),
`tick()` loop (drive each occupied vehicle, dead-cleanup, persist, cloak reconcile), `enter/handleDismount`,
`byId/byDriver/byEntity/all/count`, ray-trace/melee/projectile-sweep helpers (generalise Tank's).
Concrete manager supplies: `create(loc,yaw)->V`, `rehydrate(id,entities)->V`, `moduleId()`.

### 5.3 `PilotProtection` + `PilotCloak`
Shared implementation of `[[vehicle-pilot-protection]]`: a seated driver takes 0 direct damage (routed to
the vehicle HP), invisibility + armour hidden via `PilotCloak.hide/show` (`sendEquipmentChange`, no
ProtocolLib). One copy used by every vehicle manager through the base class.

### 5.5 DisplayVehicle — exact seams (implement precisely; CP3 vehicles rely on this)
Articulation differs per vehicle (tank: hullYaw/turretYaw/barrelPitch; kamaz: hullYaw only;
jet: yaw/pitch/roll; moto: yaw+handlebar; airship: heading+altitude). So **the base holds NO
articulation angles** — each subclass owns its own angle fields. `core.model.Transforms` provides
only primitives (yawQuat/pitchQuat/rollQuat/rotateAbout/localPointToWorld/compose); each vehicle's
own articulation logic lives in its package and uses those primitives.

`DisplayVehicle` (abstract) holds: `id`, `world`, `anchor` (x/y/z), `health`, `driver`,
`ArmorStand seat`, `List<Interaction> hitboxes`, `List<Display> displays`, `List<Part> partDefs`,
`List<ArmorStand> extraSeats` (for multi-seat like Pickup gunner; default empty), dirty/persist flags.

Abstract methods (subclass supplies):
- `String moduleId()`
- `VehicleModel model()` — parts + dims (WIDTH/HEIGHT/LENGTH) + seatHeight + hitbox layout.
- `double maxHealth()`
- `Transformation transformFor(Part part)` — uses core Transforms + this vehicle's current angles.
- `Location hitboxLocation(int index)` — articulated hitbox placement.
- `void writeExtraState(PersistentDataContainer pdc)` / `void readExtraState(PersistentDataContainer pdc)`
  — persist/restore the vehicle's own angles + extra fields on the seat.

Overridable hooks (sane defaults in base):
- `void onSpawnEffects()` {} / `void onDestroyEffects()` { standard internal explosion + smoke + sound }
- `boolean partNeedsRetransform(Part part, boolean anchorMoved, boolean anythingChanged)` — default
  `return true` (recompute all parts that could have moved). Tank overrides to restore its per-group
  skip optimisation (HULL vs TURRET vs BARREL). Do NOT force that micro-opt into the base.
- `int seatCount()` default 1; `double seatHeight()` from model.

Concrete/shared machinery in base (generalise TankCraft `Tank`/`TankManager`):
- `spawnCluster()`: spawn seat ArmorStand (invisible/gravityless/marker=false/small/persistent),
  N Interaction hitboxes, Display parts from `model()`; tag every entity via `EntityTag` +
  `Keys.of("core","role")` (seat/hitbox/part) + `Keys.of("core","index")` + `Keys.of(moduleId,"id")`.
- `rehydrate(id, entities)`: regroup by role/index PDC, restore anchor from center hitbox, call
  `readExtraState`, rebuild missing hitboxes if incomplete, return null if displays incomplete.
- `refreshModel()`: dirty-check anchor; if moved teleport seat (RETAIN_PASSENGERS) + hitboxes +
  displays to base; for each display, if `partNeedsRetransform(...)` call `setInterpolationDelay(0)` +
  `setInterpolationDuration(2)` + `setTransformation(transformFor(part))`. (Bake in the smoothness
  gotcha: teleport-duration == interpolation-duration; view-range set at spawn via DisplayConfig.)
- `persistState()`/`tickPersist()` (20-tick throttle) writing health + `writeExtraState` on the seat.
- `damage(amount)->destroyedBool`, `destroy(effects)`, `removeEntities()`, `mount/eject/clearDriver`,
  `applyAntiAirHit()` (flat 1-creeper), `applyExplosion(loc,power)` (falloff), `tickDamageEffects`.

`VehicleManager<V extends DisplayVehicle>` (abstract) supplies the registry+loop+adopt/forget+cloak+
purge+ray/melee/sweep, generalising `TankManager`. Subclass provides `create(loc,yaw)->V`,
`rehydrate(id,entities)->V`, `moduleId()`, `settings()`. It registers itself with `VehicleService`
and implements `EntityLifecycleSink`. Pilot protection (0 direct dmg → vehicle, cloak) lives in the
base manager via `PilotProtection`, used by all.

### 5.4 Special cases
- **Train**: multi-car + rail-follow. Each car is a `DisplayVehicle`; a `Train` composes cars over a
  `RailPath`. It reuses seat/hitbox/model/persist but overrides movement (RailTracer). Least-fitting;
  keep its rail package (`rail/`) intact under `vehicles/train/`.
- **Moto**: keep `DriveMath` (pure, tested) → move to `core.util` or `vehicles/moto` with its test.
  Uses the optional `EntityIndex` for spawn cooldowns/counts.
- **Pickup**: independent gunner seat → an extra `Seat` on the base (base supports N seats; default 1).
- **Drone**: piloted "from inside" (spectator-ish) — controller differs, lifecycle same.

## 6. Combat & effects — core.combat
- `Explosions`: the shared blast→vehicle routing + `createExplosion` with an internal-explosion guard
  flag (dedupe the 5 copies). Vehicle self-destruct and shell impacts go through here.
- `Projectiles`: weapon-projectile classification (arrow/fireball/firework) + sweep helpers.
- `Fx`: tracer line, rocket trail, muzzle flash, hit sparks, smoke (dedupe AntiAir/Jet/Heli/Tank copies).
- AntiAir rocket → `VehicleCombatService.antiAirHit(vehiclePart)` (direct, no throwaway ArmorStand).
  Keep the "no knockback, no block break, 1 creeper flat" semantics inside `applyAntiAirHit()`.

## 7. Placeable framework — core.placeable
`PlaceableRig` (stationary display cluster: model + hitbox + PDC state, no seat/movement) and
`PlaceableManager` (registry + adopt/forget + purge). AntiAir turret and TCK bus extend these.
AntiAir adds: GUI (`InventoryHolder` menu + a local GUI listener), fuel (furnace-style), targeting
(`TargetingSystem` → uses `VehicleService` to find rider vehicles), modes. TCK adds: Pillager snatch AI,
custom drop store.

## 8. Aerial ordnance — core.airsupport
`OrdnanceRun` (abstract BukkitRunnable): bomber display model flies a straight path, sliding
plugin chunk-ticket window around jet + target, warns nearby players (action bar), drops ordnance on
schedule, dramatic finish, teardown. `StrikeRegistry` (per-player cooldown + max-active cap + area
separation). Airstrike = carpet of TNT (Su-57 model). Nuke = single slow Fat-Man BlockDisplay +
`RadiationManager` (lingering magic-damage effect; keep in nuke module). Both read their `config.yml`
sections; messages via `Text`.

## 9. Gear — gear/warkit
Largely lift-and-shift (31 classes). Keep its internal structure (Weapons registry, WarItems, the
`*Service`/`*Manager` set, ChannelManager). Rewire: `Txt`/`ItemTools` → delegate to core `Text`/`ItemFactory`;
item PDC tags → `Keys.of("warkit", …)`; register its listeners via `core.registerListener`; its command
becomes `/mc warkit ...`; perms → `militarycraft.warkit.*`. No dependency on the vehicle core.

## 10. Checkpoints (each ends with `mvn -o -q compile` green + a short report)

- **CP1 — Skeleton + core framework.** pom, plugin.yml, config.yml stub, `MilitaryCraftPlugin`,
  module system, `Core`, config/keys/pdc/text/event/command/item/util(MathUtil,Fx). Compiles with zero modules.
- **CP2 — Model + vehicle + combat core + camera.** core.model, core.vehicle (+PilotProtection),
  core.combat, and the `camera` module (simplest; validates Core wiring). Compiles.
- **CP3 — Vehicles.** Port in order: Kamaz (drive-only, validates base) → Tank (drive+shoot, canonical)
  → Jet, Heli, Airship, Drone (shared flight/weapons) → Moto (index), Pickup (gunner) → Train (rail, last).
  One report per vehicle/group; note any non-mechanical change.
- **CP4 — Placeable.** core.placeable + AntiAir (GUI/fuel/targeting via VehicleCombatService) + TCKBus.
- **CP5 — Airsupport.** core.airsupport + Airstrike + Nuke.
- **CP6 — Gear.** WarKit → gear module.
- **CP7 — Assembly.** Final plugin.yml (perms + single command), full config.yml, `mvn -o -DskipTests package`,
  known-issues list. Tests (`DriveMathTest`, `MotorcycleIndexTest`) wired and passing.

## 11. Behaviour-change log (keep updated as modules land)
- AntiAir→vehicle damage now a direct `VehicleCombatService` call instead of a tagged throwaway-explosion.
  Semantics preserved (1 creeper flat, no knockback, no block break). No more throwaway ArmorStand.
- Commands consolidated under `/mc`; old commands/aliases removed (user-approved).
- Permissions renamed to `militarycraft.<module>.<action>` (user-approved).
- Scoreboard tags unified to `militarycraft` + module PDC; old in-world entities not adopted (clean slate).
- Tank shell explosions are guarded internal Bukkit explosions, then manually routed through
  `VehicleHandle.applyExplosion` for all MilitaryCraft vehicles except the firing tank.
- CP3c aircraft use a shared `vehicles/aircraft` layer for orientation math and point-marched
  rockets/bombs. Drone direct-hit rocket damage to living targets is an explicit `AirMunition` option.
- Drone's source `PlayerScale` behaviour is represented through the existing shared `CameraService`
  (`drone.camera-scale`), not a second scale attribute modifier.
- CP3d is implemented: Moto uses the durable `EntityIndex`, Pickup has an independent gunner seat,
  and Train keeps rail-following multi-car composition with chunk tickets.
- Artillery "Belochka" is integrated under `/mc artillery`: firing uses real X/Z coordinates, launches
  exactly three charges per salvo, and computes distance-based dispersion from the selected installation.
  Its operator camera opens top-down above the artillery the player entered.
- The former flying-carpet utility module has been removed by user request and is no longer shipped.
- (append here whenever an implementation changes original behaviour)
