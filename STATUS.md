# MilitaryCraft — STATUS (what is done / where to resume)

**Last updated:** 2026-07-18 · **Build:** ✅ GREEN (`mvn -o test` → 50 tests, EXIT=0; `mvn -o -DskipTests package` → `target/MilitaryCraft-1.0.0.jar`) · **Java files:** 338

> ⚠️ **ARCHITECTURE NOTE (2026-07-18):** the generic `DisplayVehicle`/`VehicleManager`/`core.model`/
> `core.placeable`/`vehicles.aircraft` framework described in the older sections below **was removed** — it
> had no live consumer. Every shipping vehicle implements `VehicleHandle` directly and bridges to combat via
> `ManagedVehicleProvider`; that adapter is the real architecture. Sections that describe the old framework as
> live are historical. See **`CLAUDE_CODE_REVIEW.md`** and **`MILITARYCRAFT_CODE_REVIEW.md`** for the current
> audit + what changed, and **`ORIGINAL_PARITY_RESTORE_PLAN.md`** for the parity mandate (source of truth).

**Docs:** ⭐ **`CLAUDE_CODE_REVIEW.md` / `MILITARYCRAFT_CODE_REVIEW.md` — current audits + change log** ·
`ORIGINAL_PARITY_RESTORE_PLAN.md` (parity mandate) · `DEVELOPMENT_GUIDE.md`, `CODEX_RULES.md`, `PLAN.md`,
`BUILD_SPEC.md` (historical design docs — predate the 2026-07-18 framework removal).

---

## How to tell if a build step is finished
The **on‑disk truth**, independent of any UI or limits:
```
cd F:\program\MilitaryCraft
mvn -o -q compile        # EXIT=0  → everything done so far compiles cleanly
```
Then check this file's checklist below (§Progress). A checkpoint is "done" only when: its files exist,
`mvn -o -q compile` is green, and its box here is ticked. In the Claude Code UI a background Sonnet agent
also shows a task chip that flips from *running* to *completed* — but the compile command above is the
authority. If limits run out mid‑way, a half‑written checkpoint will usually **fail to compile**; if that
happens, delete that module's folder (e.g. `src/main/java/.../vehicles/<id>/`) and revert the edits it made
to `MilitaryCraftPlugin.java` / `config.yml` / `plugin.yml` / `ModelData.java`, and the project returns to
the last green state.

## Progress
- [x] **CP1 — core framework** (21 classes) — green
- [x] **CP2 — model + vehicle base + combat + camera** (20 classes) — green
- [x] **CP3a — Kamaz** (drive‑only; validates the base) — green
- [x] **CP3b — Tank** (weapon/turret/water reference) — green
- [x] **CP3c — Jet, Helicopter, Airship, Drone** — green
- [x] **CP3d — Moto, Pickup, Train** — green
- [x] **CP4 — core.placeable + AntiAir + TCKBus** — green
- [x] **CP5 — core.airsupport + Airstrike + Nuke** — green
- [x] **CP6 — WarKit → gear** — green
- [x] **CP7 — final plugin.yml + config.yml + camera scales + tests + known‑issues** — green
- [x] **Extra integration — SvoArtillery** — green

## What exists right now
### Core framework (`core/…`, done)
Module system (`MilitaryModule`, `ModuleManager`), `Core` facade, config (`ConfigSupport`, `ModuleConfig`),
keys/PDC (`Keys.of`, `Pdc`, `EntityTag`, `ModelData`), `Text` (Adventure, hex), `EventBus` + sinks
(`ExplosionSink`/`InteractSink`/`EntityLifecycleSink`/`DamageSink`), `/mc` command
(`RootCommand`/`SubCommand`/`CommandArgs`/`CommandMenu`), `ItemFactory`, `MathUtil`, `Fx`.

### Vehicle/model/combat cores (`core.model`, `core.vehicle`, `core.combat`, done)
- `DisplayVehicle` (abstract): spawn/rehydrate/refreshModel/persist/damage/destroy/ride + `VehicleHandle`.
  Abstract seams: `moduleId, model, maxHealth, transformFor, hitboxLocation, writeExtraState, readExtraState`.
  Overridable hooks: `onSpawnEffects, onDestroyEffects, partNeedsRetransform, seatLocation,
  extraSeatLocation, facingYaw, seatCount, seatHeight, creeperDamageUnit, onVehicleTick(*via manager*)`.
- `VehicleManager<V>` (abstract): registry + tick loop + adopt/forget + pilot protection/cloak + purge +
  ray/melee/projectile‑sweep. Hooks: `create, rehydrate, moduleId, driveTick, onVehicleTick, onDriverAttacked`.
- `VehicleService`(+Impl), `VehicleCombatService`(+Impl: `antiAirHit`, `directDamage`, `repair`, `rayTrace`,
  `vehicleNear`/`vehiclesNear`, `radiusDamage`, `explosionDamage`), `PilotProtection`,
  `PilotCloak`, `Explosions`, `Projectiles`, `core.model.{Part,PartGroup,Transforms,VehicleModel,ModelBuilder,DisplayConfig}`.

### camera module (`camera/…`, done)
`CameraService.registerScale(type, scale)` + reconcile loop applying the `scale` attribute via
`VehicleService` (no scoreboard‑tag scanning). Registered in the module list.

### Kamaz vehicle (`vehicles/kamaz/…`, done) — the module TEMPLATE
9 classes: `KamazModule, Kamaz(extends DisplayVehicle), KamazManager(extends VehicleManager<Kamaz>),
KamazModel, KamazController, KamazSettings, KamazItem, KamazListener, KamazCommands`.
Config `kamaz:` section (camera‑scale 2.5, maxHealth 150 = 3 creepers). Perms `militarycraft.kamaz.*`.
**No `core/**` changes were needed** — proves the base is sufficient. Use this module's structure as the
copy‑template for every other vehicle.

### Tank vehicle (`vehicles/tank/…`, done) — the weapon/turret reference
13 classes: `TankModule, Tank(extends DisplayVehicle), TankManager(extends VehicleManager<Tank>),
TankModel, TankTransforms, TankController, TankSettings, TankItem, TankListener, TankCommands,
TankPlacement, combat/ShellManager, combat/Shell`.
Config `tank:` section (camera-scale 3.0, maxHealth 200 = 4 creepers). Perms `militarycraft.tank.*`.
Preserves hull/turret/barrel articulation, per-group retransform skip, water/drown, reload/weaponLock/overheat,
projectile sweep, ramming, and ballistic shell logic. **Only core edit:** `ModelData.TANK`; no core API/lifecycle changes.

### Aircraft family (`vehicles/aircraft`, `vehicles/jet`, `vehicles/helicopter`, `vehicles/airship`, `vehicles/drone`, done)
42 Java files total for CP3c. Shared aircraft layer: `OrientedVehicle`, `AircraftTransforms`, `AirMunition`,
`AirMunitionSpec`, `AbstractAircraftManager`, `AircraftPlacement`. This keeps yaw/pitch/roll math, direct-hit
munition behaviour, projectile sweep, melee routing, and per-aircraft active munition ticking in one place.

- `jet`: camera-steer fixed-wing flight, afterburner/heat, stall/crash handling, rockets on left-click and bombs on right-click.
- `helicopter`: hover/trim flight, animated main/tail rotors, rockets+bombs.
- `airship`: slow heading/altitude controller, animated propellers, bombs with lingering smoke; AntiAir damage comes through `applyAntiAirHit`.
- `drone`: FPV-style piloted UAV, shared camera-scale operator view, one-shot rockets, direct-hit soft-target damage, kamikaze detonation, `/mc drone fire`.

Config sections and permissions for all four are present. `ModelData`: `JET=7343`, `HELICOPTER=7344`, `AIRSHIP=7345`,
`DRONE=7346` (Jet source value was remapped to avoid Kamaz's 7342).

### CP3d vehicles (`vehicles/moto`, `vehicles/pickup`, `vehicles/train`, done)
- `moto`: motorcycle + sidecar variant, durable `EntityIndex`, spawn limits/cooldowns, projectile/impact damage,
  `DriveMathTest` coverage.
- `pickup`: driver, passenger and independent rear gunner seat; mounted gun; placement validation and crew cleanup.
- `train`: locomotive + three cars on rails, `rail/` tracer package, chunk tickets, composed multi-car runtime,
  rail placement/removal and `TrainPath`/route tests.

### CP4 placeables (`weapons/antiair`, `weapons/tckbus`, done)
- `antiair`: placeable anti-air rig on `core.placeable`, fuel table, target modes, inventory GUI, direct
  `VehicleCombatService.antiAirHit` damage against MilitaryCraft aircraft, block-aware line of fire, persistence
  and cleanup.
- `tckbus`: placeable trap bus on `core.placeable`, TCK/Tzahal skins, worker mob snatch AI, custom persistent
  drop storage via `/mc tckbus setdrop|showdrop|cleardrop`, chunk-safe lifecycle and cleanup.

### CP5 air support (`weapons/airstrike`, `weapons/nuke`, done)
- `core.airsupport`: `ChunkWindow` sliding chunk-ticket helper shared by long-running ordnance sequences.
- `airstrike`: beacon item and `/mc airstrike strike <x> <z> [world]`, pathing aircraft run, warning radius,
  cooldown/max-active guards, rolling explosions and forced chunk window cleanup.
- `nuke`: briefcase item and `/mc nuke strike <x> <z> [world]`, slow visible bomb run, warning radius, crater
  builder, blast effects and managed radiation zones.

### CP6 gear (`gear/warkit`, done)
- `warkit`: native MilitaryCraft gear registry with consumables, marker, rifle/pistol hitscan weapons, grenades,
  combat stim, recon scanner, jump jet, armor pieces, gas mask, impact pads, camo cloak and visor helmet.

### Extra modules (`weapons/artillery`, done)
- `artillery`: Artillery "Belochka" with `/mc artillery fire <x> <z>`, exactly three shells per salvo,
  distance-based dispersion, top-down operating camera over the selected installation, persistent sessions/state,
  block protection and cleanup.
- The former flying-carpet utility module has been removed by user request and is no longer shipped.
  standing riders, slow-falling ejects and cleanup.

### Core additions now present
- `core.persistence.EntityIndex`: durable YAML index used by Moto for counts/cooldowns/tombstones.
- `core.placeable`: reusable stationary rig/manager foundation with tests, used by AntiAir and TCKBus.
- `core.airsupport`: chunk-window helper for long-running Airstrike/Nuke sequences.
- `core.command.CommandMenu`: `/mc menu` GUI over the shared command registry, with module/action pages,
  permission-aware visibility, clickable parameters, static/tab-complete choices and chat prompts.
- Resource QA: `ResourceYamlTest` parses `plugin.yml`/`config.yml`, checks module sections, duplicate permission
  nodes and every literal subcommand permission declared by `new Sub(...)`.

## How to resume
The original checkpoint plan is complete. Resume by doing focused hardening, runtime QA on the Paper 1.21.4
server, and small bug fixes only. A safe resume loop is: inspect the target module, run `mvn -o test`, make the
smallest patch, run `mvn -o test` again, then package with `mvn -o -DskipTests package`.

## Known issues / TODOs collected so far
- **Base TODO(spec) hooks** added in CP2 that later modules rely on: `facingYaw()` (default 0),
  `creeperDamageUnit()` (default maxHealth/4). Fine as‑is; each vehicle overrides where it has a config value.
- **Camera key**: uses `Keys.of("camera","zoom")` (`militarycraft:camera_zoom`), internal only.
- **Kamaz deviations (accepted):** projectile sweep now also counts Fireworks (base classifier, more
  correct); explosion contact radius uses the base 2.0 (not source 3.0) — per‑creeper scaling preserved;
  passenger seats kept transient (not via the persisted `extraSeats` seam); give/spawn/remove perms default `op`.
- **Tank integration note:** shell explosions are guarded internal Bukkit explosions like the source, then
  manually routed through `VehicleHandle.applyExplosion` for all MilitaryCraft vehicles except the firing tank.
  This preserves tank-vs-tank behaviour and makes shells interact with already-merged vehicles.
- **Combat interaction note:** vehicle HP is now the shared target surface for vanilla entity hits, WarKit
  hitscan weapons, pickup/AntiAir raycasts, tank shells, aircraft munitions, drone kamikaze impacts, nuke
  radius damage, self-destruction blasts, vehicle ramming, and vehicle-triggered tripwire/pressure plates.
  WarKit repair kits repair the ridden vehicle before falling back to off-hand item repair.
- **CP3c integration note:** aircraft rockets/bombs use the shared `AirMunition` point-marcher. Drone uses
  its direct living-hit path for one-shot rockets; Jet/Heli/Airship use direct vehicle damage plus blast routing.
  Drone operator scale is implemented through the existing shared `CameraService` (`drone.camera-scale: 0.45`)
  rather than a second module-local scale modifier, so scale modifiers do not stack.
- **CP4 integration note:** AntiAir and TCKBus both use `core.placeable`. AntiAir has GUI/fuel/modes and direct
  anti-air vehicle damage; TCKBus has worker snatch AI plus persistent custom drops.
- **CP5 integration note:** Airstrike and Nuke both use managed chunk windows and clean up their forced chunks on
  sequence end or module shutdown.
- **CP6 integration note:** WarKit is implemented as a native MilitaryCraft gear set instead of copying every
  source class one-for-one; item names/lore and commands are English and tied to the core item/key/text helpers.
- **Localization:** user-facing names/lore/messages are English, except preserved proper names inside names
  such as Kamaz "Pushinka", Train "Desert Express" and Artillery "Belochka".
- **Verification limit:** builds and unit/resource tests are green. Runtime behaviour (models rendering, driving,
  combat) must be tested by the user on the live Paper 1.21.4 server at `F:\program\server 1 21 4`.
