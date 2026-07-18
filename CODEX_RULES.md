# CODEX_RULES — hard guardrails so you follow the plan exactly

Read `DEVELOPMENT_GUIDE.md` first. This file is the **do-not-go-off-track** companion: strict rules, the
**verified** core API cheat-sheet (so you never invent a method), the common wrong turns with the correct
move, and the per-module "definition of done". When this file and your instinct disagree, this file wins.

---

## 0. Prime directive
You are **continuing** an in-progress refactor with an established base. Your job each checkpoint is to
**port one source plugin onto the existing `core` base**, matching its behaviour, reusing everything the base
already provides. You are NOT redesigning, NOT "improving" the architecture, NOT rewriting gameplay. The
reference for "how a module looks" is `vehicles/kamaz/` — copy its structure.

## 1. The 20 hard rules
1. **One checkpoint at a time.** Do exactly the checkpoint in `STATUS.md` ("Resume at …"). Do not start the next one.
2. **Compile constantly.** Run `mvn -o -q compile` (from `F:\program\MilitaryCraft`) after each file and before you
   claim done. It must end **EXIT 0**. A red build is not done.
3. **Never edit the 15 source plugins** under `F:\program\<Name>`. They are read-only reference. Only write under
   `F:\program\MilitaryCraft`.
4. **Never add a dependency.** Only `paper-api` (provided) + Adventure (bundled). No ProtocolLib/Vault/PAPI/WorldGuard/Guava-extras.
5. **Never invent a core API.** If you need a method, open the actual file under `core/` and use the real signature
   (see the cheat-sheet in §2). If it genuinely doesn't exist, prefer overriding an existing hook; only as a last
   resort add a minimal method to core with a safe default, and record it in `STATUS.md`.
6. **Reuse the base — do not re-implement** persistence, rehydration, `refreshModel`, ray/melee/projectile-sweep,
   pilot protection, cloak, blast routing, or the registry/tick loop in a module. Those live in `DisplayVehicle`/`VehicleManager`.
7. **`Keys.of("<module>","name")` is the ONLY way to make a `NamespacedKey`.** Never `new NamespacedKey(...)`.
8. **All user text via `Text`** (Adventure). Use English user-facing strings after the localization pass,
   transliterating proper names such as "Pushinka", "Desert Express" and "Belochka". No `ChatColor`/`§` literals.
9. **Settings are an immutable snapshot** (`<X>Settings`, all `public final`), built from `config.section("<id>")`.
   Never call `getConfig()` in a tick/hot path.
10. **Port config keys exactly** from the source `<X>Config` — same paths, same defaults, same clamps. Do not rename,
    drop, or "simplify" tunables. Gameplay numbers (speeds, damage %, durability, cooldowns) must match the source.
11. **Commands only via `/mc <id> <sub>`** using `SubCommand` + `core.commands().register("<id>", list)`. No per-plugin
    commands, no aliases. `reload`/`cleanup` are global (`/mc reload`, `/mc cleanup`) — a module does NOT add its own.
12. **Permissions are `militarycraft.<id>.<action>`**, declared in `plugin.yml`. `militarycraft.admin` implies all.
13. **Register with the base:** a `VehicleManager` subclass must call `manager.attach(core)` in the module's `enable`
    (it registers with `VehicleService` + `EventBus`), then `adoptExisting()` then `start()`.
14. **Do not register a `VehicleManager` (or vehicle) as an `ExplosionSink`.** `VehicleCombatServiceImpl` is the single
    ExplosionSink that fans a blast to all vehicles; a second one double-applies damage.
15. **Transient helper entities** (extra passenger seats, debris, effects) get `EntityTag.tag(e, "<id>")` but **must NOT**
    get a `<id>_id` PDC (or the chunk-load adopter tries to fold them into a model). Persisted model parts are spawned
    only via `ModelBuilder` (which tags them fully).
16. **Articulate a part with ONE composed rotation** (offset-rotation ∘ articulation), then `Transforms.build(...)`.
    Do not rotate position and orientation separately, or the model shears under yaw (see `Kamaz.transformFor`).
17. **Persist your own state** (articulation angles, owner) via `writeExtraState`/`readExtraState` on the seat PDC.
    No side database — the only exception is Moto's durable index → `core.persistence.EntityIndex` (build it in CP3d).
18. **Any change under `core/**` is shared and must be minimal + flagged** in `STATUS.md`. Prefer overriding a hook.
    If you find yourself editing `DisplayVehicle`/`VehicleManager`, stop and check for an existing overridable hook first.
19. **Wire every new module** in four places: add `new XModule()` to `MilitaryCraftPlugin`'s list; add the `<id>:`
    section to `config.yml`; add `militarycraft.<id>.*` to `plugin.yml`; add `ModelData.X` for its item.
20. **When unsure, match the source plugin's behaviour** and leave a `// TODO(spec): <question>` — never guess-invent
    new gameplay. Report every TODO in your checkpoint summary.

## 2. Verified core API cheat-sheet (confirmed from the actual source — use these exact signatures)
> Anything not listed here: OPEN THE FILE under `core/` and read it. Do not guess.

**`Core` (`core/Core.java`)** — `plugin()`, `config()`→`ModuleConfig`, `config().section(String)`→`ModuleConfig`,
`events()`→`EventBus`, `commands()`→`RootCommand`, `items()`→`ItemFactory`, `models()`→`ModelBuilder`,
`vehicles()`→`VehicleService`, `combat()`→`VehicleCombatService`, `camera()`→`CameraService`, `logger()`,
`registerListener(Listener)`, `runSync(Runnable)`, `runAsync(Runnable)`, `scheduler()`.

**`EventBus`** — `register(Object sink)`, `unregister(Object sink)`. Sinks: `ExplosionSink`, `EntityLifecycleSink`,
`InteractSink`, `DamageSink` (see `core/event/`).

**`RootCommand`** — `register(String moduleId, List<SubCommand> subs)`, `unregister(String moduleId)`.
**`SubCommand`** — `String name()`, `String permission()` (nullable), `void execute(CommandSender, String[])`,
`List<String> tabComplete(CommandSender, String[])` (default `List.of()`).
**`CommandArgs`** (static) — `player(CommandSender)`→`Player|null`, `resolvePlayer(String)`→`Player|null`,
`coord(String token, double base, boolean allowRelative)`→`Double|null`, `giveItem(Player, ItemStack)`.

**`ModuleConfig`** (from `KamazSettings` — confirm the full getter set in `core/config/ModuleConfig.java`) —
`getDouble(path, def)`, `getDouble(path, def, min, max)`, `getInt(path, def)`, `getBoolean(path, def)`,
`getString(path, def)`, `block(path, Material fallback)`→`Material`, `section(String)`→`ModuleConfig`.

**`ItemFactory`** — `build(Material, String name, NamedTextColor, List<Component> lore, int modelData, NamespacedKey tagKey)`→`ItemStack`,
`isTagged(ItemStack, NamespacedKey)`→`boolean`.
**`Text`** (static) — `of(String legacy)`→`Component`, `msg(CommandSender, String legacy)`, `lore(String...)`→`List<Component>`.
**`Keys`** (static) — `init(Plugin)` (called once in plugin main), `of(String module, String name)`→`NamespacedKey`.
**`Pdc`** (static) — `setString/getString(pdc,key,def)`, `setDouble/getDouble(pdc,key,def)`, `setInt/getInt(pdc,key,def)`,
`getUuid(pdc,key,def)` (confirm byte/other in `core/key/Pdc.java`).
**`EntityTag`** (static) — `tag(Entity, String moduleId)`, `moduleOf(Entity)`→`String`, `TAG` (the scoreboard tag string).

**`ModelBuilder`** (`core.models()`) — `spawnSeat(Location, String moduleId, UUID id)`→`ArmorStand`;
`spawnHitbox(Location, float w, float h, int index, String moduleId, UUID id)`→`Interaction`;
`spawnBlockDisplay(Location, Part, int index, String moduleId, UUID id, DisplayConfig)`→`BlockDisplay`;
`spawnTextDisplay(Location, Part, int index, String moduleId, UUID id, DisplayConfig)`→`TextDisplay`.
**`Part`** — fields `int group; Vector3f offset, scale; float pitch, yaw, roll; Material material; String text`;
factories `Part.block(group, offset, scale, material)`, `Part.block(group, offset, scale, material, pitch, yaw, roll)`,
`Part.text(group, offset, text)`; `isText()`. **`PartGroup.STATIC == 0`.**
**`VehicleModel`** (record) — `(List<Part> parts, float width, float height, float length, double seatHeight, float[] hitboxZOffsets, int centerHitboxIndex)`.
**`Transforms`** (static) — `yawQuat(double)`, `pitchQuat(double)`, `rollQuat(double)`→`Quaternionf`;
`rotateAbout(Vector3f point, Vector3f pivot, Quaternionf)`; `localPointToWorld(Vector3f local, double hullYaw)`→`Vector3f`;
`build(Part, Vector3f worldOffset, Quaternionf worldRot)`→`Transformation`.
**`DisplayConfig.STANDARD`** — pass to the display spawn helpers.

**`DisplayVehicle`** — abstract: `moduleId()`, `model()`, `maxHealth()`, `transformFor(Part)`, `hitboxLocation(int)`,
`writeExtraState(PDC)`, `readExtraState(PDC)`. Overridable: `onSpawnEffects()`, `onDestroyEffects()`,
`partNeedsRetransform(Part, boolean moved, boolean anythingChanged)`, `seatLocation()`, `extraSeatLocation(int)`,
`facingYaw()`, `seatCount()`, `seatHeight()`, `creeperDamageUnit()`. Provided (call these): `spawnCluster(ModelBuilder)`,
`static group(List<Entity>, int partCount, int hitboxCount)`→`Groups`, `adopt(Groups, List<Part>, ModelBuilder)`,
`static readHealth(PDC, double)`, `persistState()`, `tickPersist()`, `markStateDirty()`, `refreshModel()`,
`damage(double)`→`boolean destroyed`, `applyAntiAirHit()`, `applyExplosion(Location, double power)`, `tickDamageEffects()`,
`destroy(boolean effects)`, `removeEntities()`, `mount(Player)`→`boolean`, `eject()`, `clearDriver()`, `isActive()`,
`isSpawned()`, `isOccupied()`, `world()`, `anchor()`, `seat()`, `health()`, `driver()`. Protected fields:
`id, world, anchor, health, driver, seat, hitboxes, displays, partDefs, extraSeats`.

**`VehicleManager<V>`** — abstract: `moduleId()`, `create(Location, double yaw)`→`V`, `rehydrate(UUID, List<Entity>)`→`V`,
`driveTick(V, Player)`. Overridable: `onVehicleTick(V)`, `onDriverAttacked(V, Player, EntityDamageByEntityEvent)`,
`enter(V, Player)`→`boolean`, `handleDismount(Player)`, `onEntityDamage(EntityDamageEvent)`, `shutdown()`. Provided:
`attach(Core)`, `start()`, `stop()`, `adoptExisting()`, `spawn(Location, double)`→`V`, `byId(UUID)`, `byDriver(UUID)`,
`byEntity(Entity)`, `all()`, `count()`, `remove(V, boolean effects)`, `purgeAll()`→`int[]{tracked, strays}`,
`rayTraceFrom(Location eye, double reach)`→`V`, `findMeleeTarget(Player, double reach, double pad)`→`MeleeHit`,
`projectilesInBody(V, double pad)`→`List<Projectile>`. `MeleeHit` = record `(DisplayVehicle vehicle, Location point, double distance)`.
Protected fields: `core, registry, driverToVehicle`.

**`VehicleService`** — `vehicleOf(Entity)`→`VehicleHandle`, `riddenBy(Player)`→`VehicleHandle`, `typeOf(Entity)`→`String`, `all()`.
**`VehicleCombatService`** — `antiAirHit(Entity vehiclePart)`→`boolean`, `explosionDamage(Location, double power)`.
**`CameraService`** — `registerScale(String type, double scale)`.
**`Explosions`** (static) — `createExplosion(World, Location, float power, boolean setFire, boolean breakBlocks)`, `impactFx(Location)`.
**`Projectiles`** (static) — `isWeaponProjectile(Projectile)`→`boolean`.

## 3. Common wrong turns → the correct move
- ❌ Writing your own `spawnEntities()` / `rehydrate()` / `persistState()` in the vehicle.
  ✅ Call `spawnCluster(models)` in the static `create`; follow the `group`/`adopt`/`readHealth` recipe in the static `rehydrate`.
- ❌ Holding hull/turret angles in `DisplayVehicle` or reading them via a base method.
  ✅ The base has NO angles. Your subclass owns its angle fields and returns them from `transformFor`/`facingYaw`; persist them in `writeExtraState`.
- ❌ `new NamespacedKey(plugin, "tank_id")`.  ✅ `Keys.of("tank", "id")`.
- ❌ `player.sendMessage(ChatColor.RED + "…")` or `"§c…"`.  ✅ `Text.msg(player, "&c…")`.
- ❌ Reading `plugin.getConfig().getDouble(...)` inside a drive/tick loop.  ✅ Read the immutable `XSettings` snapshot.
- ❌ Adding `/tank` command or an alias.  ✅ Only `/mc tank …` via `SubCommand` + `commands().register("tank", …)`.
- ❌ Editing `DisplayVehicle` to add per-vehicle behaviour.  ✅ Override a hook (`onVehicleTick`, `onDriverAttacked`,
  `partNeedsRetransform`, `seatLocation`, `creeperDamageUnit`, `onDestroyEffects`, …). Only touch core if no hook fits — then flag it.
- ❌ AntiAir spawning a tagged explosion to hurt a vehicle (the old hack).  ✅ `core.combat().antiAirHit(vehiclePart)`; find rider vehicles via `core.vehicles().riddenBy(player)`.
- ❌ Registering `XManager` as an `ExplosionSink` to catch blasts.  ✅ Don't — the combat service already routes blasts to all vehicles.
- ❌ Doing Tank + Jet + Heli in one pass "to save time".  ✅ One checkpoint; compile green; report; stop.
- ❌ "Cleaning up" gameplay math or dropping a config option you think is unused.  ✅ Port it faithfully; parity first.

## 4. Definition of Done (per module — all must be true before you say done)
- [ ] Package `…/<layer>/<id>/` created; classes follow the Kamaz shape (Module/Vehicle/Manager/Model/Controller/Settings/Item/Listener/Commands).
- [ ] `X extends DisplayVehicle` / `XManager extends VehicleManager<X>` (vehicles) — base reused, no duplicated lifecycle.
- [ ] All source config keys ported into `XSettings` with identical defaults/clamps; gameplay numbers match the source.
- [ ] Module wired in all 4 spots: `MilitaryCraftPlugin` list, `config.yml` `<id>:` section, `plugin.yml` perms, `ModelData.X`.
- [ ] `enable` registers manager (`attach`+`adoptExisting`+`start`), listeners, `/mc <id>` subcommands, and `camera().registerScale`.
- [ ] `disable` cleanly shuts down (eject/persist/cancel/unregister) WITHOUT deleting entities; `reload` rebuilds settings.
- [ ] Only `Keys.of` for keys; only `Text` for user strings; English text with preserved proper names; no new deps.
- [ ] Any `core/**` edit is minimal and listed in `STATUS.md`; ideally there are none.
- [ ] `mvn -o -q compile` → EXIT 0.
- [ ] `STATUS.md` box ticked + `PLAN.md` §5 updated + any behaviour change appended to `PLAN.md` §8 / `BUILD_SPEC.md` §11.

## 5. Per-checkpoint work protocol (do in this order)
1. Read `STATUS.md` → the "Resume at …" line. Read that checkpoint's row in `PLAN.md` §6 and its detailed spec in
   `DEVELOPMENT_GUIDE.md` PART H.
2. Read the **source plugin** fully (every class + its `config.yml`). Read `vehicles/kamaz/` again as the template.
3. If the checkpoint needs a NEW core piece first (`core.placeable` for CP4, `core.airsupport` for CP5,
   `core.persistence.EntityIndex` for CP3d), build and compile that FIRST, then the module(s).
4. Write the module class by class in this order: `Settings` → `Model` → `Vehicle` → `Controller` → `Manager` →
   `Item` → `Listener` → `Commands` → `Module`. Compile after each couple of files.
5. Wire the 4 spots. Compile green.
6. Self-review against §1 and the §4 checklist. Update the docs.
7. Report: files created/modified, any `core/**` change + why, behaviour deviations + TODOs, and the compile result.

## 6. If you get stuck / uncertain
- Missing API? Read the core file; don't invent. Still missing? Override a hook; if impossible, add a minimal core
  method with a default and flag it.
- Source behaviour ambiguous? Match the source literally and leave `// TODO(spec)`.
- Something looks over-engineered in the source? Port it faithfully anyway (parity first); note the concern, don't act on it.
- Never expand scope beyond the current checkpoint to "finish faster". Small, green, reported steps only.
