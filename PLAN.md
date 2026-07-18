# MilitaryCraft — Full Construction Plan (Opus)

This is the **execution roadmap** for merging the 15 standalone Paper 1.21.4 plugins into one
modular `MilitaryCraft` plugin. It is the companion to two other docs in this folder:
- **`BUILD_SPEC.md`** — the authoritative architecture/design contract (interfaces, seams, conventions).
- **`STATUS.md`** — the live "what is done vs remaining" tracker (read that to know where to resume).

If you (or a future AI session) are resuming: read `STATUS.md` first, then this file's §6 (per‑module
mapping) and §7 (how to run the next checkpoint). `BUILD_SPEC.md` holds the deep design.

---

## 1. Goal & ground rules
- **One** Maven project, **one** jar, **one** `plugin.yml`, **one** `JavaPlugin` (`MilitaryCraftPlugin`).
- Root package `me.bibo.militarycraft`. Java 21, `paper-api 1.21.4` (provided), no external libs.
- Model split (user's requirement): **Opus** does inventory/architecture/spec/review; **Sonnet subagents
  write the Java.** Each checkpoint = one Sonnet subagent implementing, then Opus reviews + compiles.
- User's fixed decisions: unified perms `militarycraft.<module>.<action>`; single `config.yml` with
  per‑module sections; commands **only** `/mc <module> <action>`; clean slate (no data migration).

## 2. Build & verify (offline — deps are cached in ~/.m2)
```
cd F:\program\MilitaryCraft
mvn -o -q compile          # fast compile check (use after every change)
mvn -o -q -DskipTests package   # produce target/MilitaryCraft-1.0.0.jar
mvn -o -q test             # run unit tests (Moto DriveMath / MotorcycleIndex — added in CP7)
```
Java 21.0.9 and Maven 4.0.0-rc-5 are installed. A green build = `EXIT=0`.

## 3. Architecture in one screen (details in BUILD_SPEC.md)
```
MilitaryCraftPlugin (the only JavaPlugin)
  └─ Core (facade) ── config · events(EventBus) · commands(/mc) · items · models · vehicles · combat · camera
  └─ ModuleManager ── hardcoded list of MilitaryModule, gated by modules.<id>.enabled
Layers:  Bukkit glue (listeners/commands/GUI)  →  feature modules  →  core framework  →  Paper API
Storage: state on the entity via PDC (seat ArmorStand); settings in one config.yml. No SQL.
         Optional core.persistence.EntityIndex (Moto-style durable YAML) for cross-load counts/cooldowns.
Cross-module contracts are explicit services, NOT scoreboard-tag strings:
  - CameraService.registerScale(type, scale)      (vehicles register; camera applies scale attribute)
  - VehicleService.vehicleOf/riddenBy/typeOf       (anyone queries vehicles)
  - VehicleCombatService.antiAirHit(entity)        (AntiAir damages vehicles directly — replaces the
                                                    old tagged-throwaway-explosion hack)
```

## 4. Conventions cheat-sheet (every module obeys — full text in BUILD_SPEC §3)
- NamespacedKey: **only** `Keys.of("<module>", "name")` → `militarycraft:<module>_<name>`.
- Every spawned entity: scoreboard tag `EntityTag.TAG` (`"militarycraft"`) + PDC `core_module` = module id.
- Text: `Text.of("&a...")` (Adventure, legacy‑ampersand incl. hex). Chat prefix `&8[&bMC&8]&r `.
- Config: `core.config().section("<id>")` → `ModuleConfig` typed clamped getters; build an immutable
  `<X>Settings` snapshot on enable/reload. Material parsing via `ConfigSupport.block(...)`.
- Commands: register a `SubCommand` group under `/mc <module>`; perms `militarycraft.<module>.<action>`;
  `militarycraft.admin` implies all. Global admin: `/mc reload|modules|cleanup`.
- Events: shared hot events go through `EventBus` sinks (`ExplosionSink`, `InteractSink`,
  `EntityLifecycleSink`, `DamageSink`). Module‑unique events → local `Listener` via `core.registerListener`.
- Item CustomModelData: one constant per item in `core/key/ModelData.java` (never reuse a value).

## 5. Checkpoint roadmap
| CP | Scope | Status |
|----|-------|--------|
| CP1 | Skeleton + core framework (module/config/keys/pdc/text/eventbus/command/item/util) | ✅ done, green |
| CP2 | core.model + core.vehicle (DisplayVehicle/VehicleManager + PilotProtection) + core.combat + camera module | ✅ done, green |
| CP3a | **Kamaz** (drive‑only) — validates the base | ✅ done, green |
| CP3b | **Tank** (drive+shoot; reference for weapons/turret/water/onVehicleTick/onDriverAttacked) | ✅ done, green |
| CP3c | **Jet, Helicopter, Airship, Drone** (flight + weapons family) | ✅ done, green |
| CP3d | **Moto** (uses EntityIndex), **Pickup** (gunner extra‑seat), **Train** (rail — last, outlier) | ✅ done, green |
| CP4 | core.placeable + **AntiAir** (GUI/fuel/targeting via VehicleCombatService) + **TCKBus** (snatch AI + custom drops) | ✅ done, green |
| CP5 | core.airsupport (`ChunkWindow`) + **Airstrike** + **Nuke** (+RadiationManager) | ✅ done, green |
| CP6 | **WarKit** → `gear` (native MilitaryCraft gear registry) | ✅ done, green |
| CP7 | Final plugin.yml (all perms) + full config.yml + camera scales + tests + known‑issues | ✅ done, green |
| Extra | **SvoArtillery** integration requested after the original plan | ✅ done, green |

Update the Status column here and in `STATUS.md` as each lands.

## 6. Per‑module migration map (source → target)
Every vehicle module has the SAME shape (established by Kamaz — use it as the template):
`<X>Module` (MilitaryModule) · `<X>` (extends DisplayVehicle) · `<X>Manager` (extends VehicleManager<X>) ·
`<X>Model` · `<X>Controller` · `<X>Settings` · `<X>Item` · `<X>Listener` (place/interact/dismount) ·
`<X>Commands` (SubCommand group). Config section `<id>:`, perms `militarycraft.<id>.*`, camera scale registered.

### Vehicles (CP3)
| Target `vehicles/<id>` | Source | Key specifics to preserve | Base seams used |
|---|---|---|---|
| `tank` | `F:\program\TankCraft` | turret/barrel articulation (HULL/TURRET/BARREL groups → override `partNeedsRetransform` for per‑group skip); shells (ShellManager/Shell); water/drown + projectile‑sweep + reload countdown → **`onVehicleTick`**; weapon dmg to occupied vehicle → **`onDriverAttacked`**; `creeperDamageUnit` from config | transformFor, hitboxLocation, writeExtra/readExtra, onVehicleTick, onDriverAttacked, partNeedsRetransform, creeperDamageUnit |
| `jet` | `F:\program\JetCraft` | camera‑steer flight (FlightController); yaw/pitch/roll articulation; rockets+bombs (WeaponSystem/Projectile); `facingYaw`=heading | transformFor, facingYaw, onDriverAttacked |
| `helicopter` | `F:\program\HeliCraft` | hover flight; rotor always spinning on hover (main=Y, tail=X); forward rockets/bombs | transformFor (rotor anim), onVehicleTick (rotor) |
| `airship` | `F:\program\AirshipCraft` | lighter‑than‑air heading+altitude; bombs; takes anti-air hit → now via `applyAntiAirHit` (no tag check) | transformFor, onDriverAttacked |
| `drone` | `F:\program\DroneCraft` | piloted "from inside"; one‑shot rockets; kamikaze on impact; operator scale via shared CameraService | transformFor, onVehicleTick |
| `moto` | `F:\program\MotoCraft` | **keep `DriveMath` (pure/tested)** → `core.util` or module + its test; sidecar passenger (extraSeats); durable index → **`core.persistence.EntityIndex`** (build it here) for spawn cooldowns/counts | extraSeatLocation, onVehicleTick |
| `pickup` | `F:\program\PickupCraft` | independent **gunner seat** (extraSeats seam); HUD; GunManager; DROP the legacy `jeepcraft_entity` migration | extraSeatLocation, seatCount=2+, onDriverAttacked |
| `train` | `F:\program\TrainCraft` | **outlier**: locomotive + 3 cars over vanilla rails; keep `rail/` pkg (RailTracer/RailEdge/RailCursor/TrainPath); ProtectionListener (deadly to stand on tracks); each car a DisplayVehicle, `Train` composes over a RailPath | custom movement; reuse seat/model/persist only |

### Weapons/Trap (CP4/CP5)
| Target | Source | Specifics | New core piece |
|---|---|---|---|
| `weapons/antiair` | `F:\program\AntiAirCraft` | placeable turret; GUI, fuel table, targeting through `VehicleService`, modes, rocket/bullet fire through `VehicleCombatService.antiAirHit` | **core.placeable** (PlaceableRig/PlaceableManager) |
| `weapons/tckbus` | `F:\program\TCKBus` | placeable VW-T4 bus; worker mob snatch AI; persistent custom drops (`/mc tckbus setdrop|showdrop|cleardrop`); skins tck/tzahal | core.placeable (reuse) |
| `weapons/airstrike` | `F:\program\AirstrikePlugin` (`ru.airstrike`) | Su-57-style path, sliding chunk window, warning radius, cooldown/max-active, rolling explosion run; Adventure `message()` → `Text` | **core.airsupport** (`ChunkWindow`) |
| `weapons/nuke` | `F:\program\NukeStrike` (`ru.nukestrike`) | slow visible bomb sequence + crater builder + `RadiationManager` lingering magic damage | core.airsupport (reuse) |

### Gear (CP6)
| Target | Source | Specifics |
|---|---|---|
| `gear/warkit` | `F:\program\WarKit` | Native MilitaryCraft gear registry: consumables, marker, rifle/pistol, grenades, combat stim, recon scanner, jump jet, wearable armor, gas mask, impact pads, camo cloak and visor helmet. Uses core `Text`/`ItemFactory`, `Keys.of("warkit",...)`, `/mc warkit`, and `militarycraft.warkit.*`. No vehicle dependency. |

## 7. How to run a checkpoint (the recipe)
1. Launch a **Sonnet** subagent (`Agent` tool, `model: "sonnet"`, `run_in_background:false` for strict ordering).
2. The prompt MUST tell it to: read `BUILD_SPEC.md` + the relevant CP2 base files + the source plugin
   (read‑only), implement only that checkpoint on top of the base, wire the module into
   `MilitaryCraftPlugin`, add config section + perms + `/mc` subcommands + camera scale, and finish with
   `mvn -o -q compile` GREEN. Use the **Kamaz module as the structural template**.
3. It must **reuse the base** (no re‑implementing persistence/rehydrate/refreshModel/ray/cloak). Any change
   under `core/**` must be minimal and reported for Opus review.
4. After it returns: Opus reviews the load‑bearing files, runs `mvn -o -q compile`, updates `STATUS.md`
   + the §5 table, then releases the next checkpoint.

Ordering rule: **independent before dependent** — camera before vehicles (done); core.placeable before
AntiAir/TCK; core.airsupport before Airstrike/Nuke. Within CP3, Tank before the other weaponised vehicles
(it's the weapon reference).

## 8. Behaviour changes from the originals (keep appending; also in BUILD_SPEC §11)
- AntiAir→vehicle damage: direct `VehicleCombatService` call, not a tagged throwaway explosion (semantics
  preserved: 1 creeper flat, no knockback, no block break).
- AntiAir now uses a per-rig inventory GUI for mode, fuel and status; sneak-right-click still cycles mode and
  direct right-click with fuel still performs the quick-add path.
- TCKBus was integrated under `weapons/tckbus` (not `trap/tckbus`) because all destructive placeables are grouped
  with weapon modules in this codebase. It reuses `core.placeable` and has persistent custom drops.
- Airstrike and Nuke use `core.airsupport.ChunkWindow` for explicit forced chunk cleanup during long sequences.
- WarKit was implemented as a native MilitaryCraft gear registry rather than a raw class lift. The gameplay
  surface is preserved as functional items and commands while using the project's existing core services.
- Commands unified under `/mc`; old commands/aliases removed. Perms renamed `militarycraft.<module>.<action>`.
  `/mc menu` now opens a permission-aware GUI over the same command registry, with module/action pages,
  clickable parameter buttons, tab-complete choices and chat prompts for custom values.
- Scoreboard tags unified to `militarycraft` + module PDC; old in‑world entities not adopted (clean slate).
- `Text` uses a hex‑capable legacy serializer (the source plugins' `legacyAmpersand()` silently dropped hex).
- Tank shell explosions are guarded internal Bukkit explosions, then manually routed through
  `VehicleHandle.applyExplosion` for all MilitaryCraft vehicles except the firing tank.
- Shared vehicle combat now covers direct entity hits, WarKit and vehicle raycasts, AntiAir bullets/rockets,
  tank shells, aircraft/drone impacts, nuke radius damage, self-destruction blasts, ramming, repair kits,
  and vehicle-triggered tripwire/pressure plates.
- CP3c aircraft share `vehicles/aircraft` (`OrientedVehicle`, `AircraftTransforms`, `AirMunition`,
  `AbstractAircraftManager`) for yaw/pitch/roll flight, active munition ticking, projectile sweep, and pilot damage routing.
- Drone operator scaling uses the shared `CameraService` (`drone.camera-scale: 0.45`) instead of a second
  module-local `PlayerScale` modifier, avoiding stacked scale modifiers.
- CP3d is implemented: Moto has durable `EntityIndex`/spawn controls, Pickup has an independent gunner seat,
  and Train keeps rail-following composition with chunk tickets and route/path tests.
- SvoArtillery was integrated as `weapons/artillery`: firing is `/mc artillery fire <x> <z>`, not grid labels;
  every shot launches exactly three charges, and dispersion scales with distance from the selected installation.
  The operating camera opens top-down over the artillery the player entered.
- The former flying-carpet utility module has been removed by user request and is no longer shipped.
- User-facing text/lore has been localized to English while transliterating proper names such as "Pushinka",
  "Desert Express" and "Belochka".
