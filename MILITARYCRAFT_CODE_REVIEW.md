# MilitaryCraft — полный senior code review и план доведения до production-grade

**Дата аудита:** 2026-07-18  
**Режим:** только чтение исходников; код, конфигурация и ресурсы не изменялись  
**Цель:** не сохранить текущее состояние любой ценой, а определить путь к максимально безопасному, стабильному, производительному и сопровождаемому продукту

---

## 1. Краткий вердикт

MilitaryCraft компилируется, его 71 существующий тест проходит, а в коде есть немало сильных инженерных решений: явная модульная загрузка, PDC-идентификация объектов, восстановление сущностей из чанков, атомарная запись некоторых YAML-файлов и разумный адаптер между автономными legacy-модулями.

Однако выпускать текущую версию на публичный сервер без дополнительного hardening я бы не рекомендовал. Главные причины:

1. Предметы Airstrike и Nuke позволяют вызвать разрушительный удар без заявленного permission и после отмены события protection-плагином.
2. Многие виды техники, оружия и изменения ландшафта намеренно игнорируют отмену событий и напрямую меняют мир, поэтому способны обходить WorldGuard/claims и другие политики сервера.
3. Почти во всех модулях конфигурация пропускает NaN, Infinity и чрезмерные значения. Это даёт администратору или повреждённому YAML возможность создать entity/particle/TNT storm, OOM или watchdog timeout.
4. Chunk lifecycle и force-loading реализованы неодинаково и местами некорректно: удары снимают чужой force-loaded state, техника забывается при выгрузке одной детали, а некоторые контроллеры синхронно генерируют мир во время движения.
5. Reload и lifecycle не транзакционны: часть модулей может не перезагрузиться или не очиститься, но пользователь всё равно получает сообщение об успехе.
6. В проекте одновременно живут две архитектуры техники. Большой framework DisplayVehicle/VehicleManager/PlaceableRig фактически не используется, тогда как реальные модули работают через VehicleHandle/ManagedVehicleProvider.
7. 71 зелёный тест создаёт завышенное ощущение покрытия: значительная часть тестов относится к неиспользуемому framework, а критические runtime-сценарии почти не тестируются.

**Общий уровень:** функционально богатый late prototype / parity-port, но пока не production-grade platform.  
**Рекомендуемая стратегия:** сначала закрыть безопасность, целостность мира, bounded configuration и lifecycle; затем стабилизировать persistence/chunk model; только после этого чистить архитектуру и улучшать UX.

---

## 2. Объём и методика аудита

Проверено полностью:

- 352 production Java-файла, примерно 59,5 тыс. строк:
  - vehicles — 195 файлов / около 34 тыс. строк;
  - weapons — 71 / около 11,9 тыс.;
  - gear — 32 / около 6,6 тыс.;
  - core — 50 / около 6,5 тыс.;
  - camera — 3 / около 0,27 тыс.;
  - MilitaryCraftPlugin — bootstrap;
- 13 test-классов / 71 test method;
- pom.xml, plugin.yml и config.yml;
- все корневые проектные документы;
- resourcepack и WarKit-ResourcePack.zip;
- generated graphify graph и его соответствие текущему source tree;
- существующий JAR из target.

Дополнительно выполнена чистая проверка в отдельной временной копии pom.xml + src, чтобы Maven не изменял проект:

- compilation: успешно;
- tests: **71/71 passed**, 0 failures, 0 errors, 0 skipped;
- production classes compiled: 352;
- итог: BUILD SUCCESS.

Существующий target/MilitaryCraft-1.0.0.jar:

- размер 1 604 795 байт;
- SHA-256: AB18468B813546E35526B6B6FF1EB8FD3CA99C3BF4C23D3616D6C806B565FC3D;
- plugin.yml присутствует.

### Ограничения аудита

Это полный статический code review, а не live soak-test:

- Paper-сервер с реальными мирами не запускался;
- не измерялись MSPT, сетевые пакеты и heap под нагрузкой;
- не выполнялась интеграция с конкретным WorldGuard/claim/PvP/anti-cheat plugin;
- не проверялось поведение после реального crash процесса.

Поэтому функциональные дефекты ниже помечены как подтверждённые кодом, а численная тяжесть performance-сценариев — как прогноз, который следует подтвердить профилированием.

### Шкала приоритетов

- **P0 — release blocker:** прямой обход авторизации/защиты или массовое разрушение мира доступным игровым путём.
- **P1 — высокий риск:** потеря данных, зависание сервера, нарушение lifecycle/persistence или серьёзная gameplay/security ошибка.
- **P2 — существенный:** заметный риск поддержки, производительности, совместимости или UX.
- **P3 — улучшение качества:** гигиена, понятность, consistency, developer experience.

---

## 3. Что уже сделано хорошо

Это важно сохранить при переработке:

- MilitaryCraftPlugin.java:43–90 создаёт один JavaPlugin и явный детерминированный список модулей; reflection/classpath scan отсутствует.
- ModuleManager.java:43–51 выключает модули в обратном порядке; enable failure хотя бы инициирует cleanup.
- ManagedVehicleProvider — удачный тонкий мост: автономные legacy-менеджеры участвуют в общей боевой системе без опасного массового переписывания.
- EventBus изолирует RuntimeException отдельных sink и использует безопасные коллекции регистрации.
- Во всём просмотренном runtime Bukkit/entity/world mutations выполняются в main thread; небезопасных async Bukkit-вызовов не найдено. AsyncChatEvent в CommandMenu возвращается на main thread перед Bukkit-операциями.
- PDC используется как реальная identity предметов и сущностей, а не только lore/display name.
- Moto — лучший внутренний ориентир для vehicle persistence: canonical anchor, transient derived entities, finite/ranged config, footprint/world-border/loaded-chunk validation и проверка addPassenger.
- EntityIndex и MotorcycleIndex используют временный файл, fsync, atomic-move fallback и сохраняют malformed copy.
- ArtilleryYamlFiles и ArtilleryTaskTracker — хорошие заготовки атомарного persistence и task ownership.
- ArtilleryTargetValidator уже проверяет finite/range/border; этот подход нужно распространить на остальные команды.
- WarKitCommand корректно обрабатывает leftovers полного inventory.
- Airstrike/Nuke имеют active caps, cooldown и teardown-механику; проблема не в отсутствии защиты как идеи, а в неполных границах и ownership.
- AntiAir stagger-ит часть targeting scan, использует LOS/ray tracing и custom InventoryHolder.
- plugin.yml честно объявляет folia-supported: false.
- resourcepack source содержит 76 валидных JSON; ZIP не имеет duplicate/unsafe paths.

---

# 4. Release blockers

## MC-SEC-001 — [P0] Airstrike item обходит permission и protection cancellation

**Где:**  
src/main/java/me/bibo/militarycraft/weapons/airstrike/listener/AirstrikeListener.java:25–50  
src/main/resources/plugin.yml:535–537

**Что не так:** listener прямо сообщает в комментарии, что beacon должен работать даже при отмене raw interaction protection-плагином. Он не проверяет airstrike.use, хотя plugin.yml описывает этот permission как право на original command **и item**, default: op.

**Риск:** non-op игрок, получивший, укравший или подобравший beacon, может вызвать разрушающий удар в защищённом регионе. Command permission и command-access gate здесь не участвуют.

**Исправление:**

1. Для block interaction использовать ignoreCancelled=true и fail closed.
2. Перед targeting/consume/start проверить airstrike.use, game mode, world allowlist, border и ActionPolicy региона.
3. Сделать запуск транзакционным: authorization → target preflight → reserve cooldown/cap → spawn/schedule → consume item. При ошибке всё откатывать.
4. Добавить audit log: actor UUID, world, coordinates, source item, policy result.

**Готово, когда:** тест с отменённым PlayerInteractEvent и тест non-op с beacon не создают ни sequence, ни entities, ни cooldown и не расходуют item.

## MC-SEC-002 — [P0] Nuke item имеет тот же обход

**Где:**  
src/main/java/me/bibo/militarycraft/weapons/nuke/NukeListener.java:22–45  
src/main/resources/plugin.yml:548–550

**Что не так:** NukeListener не проверяет nuke.use и продолжает работу независимо от предыдущей отмены события.

**Риск:** игрок без op-permission может вызвать ядерный удар и необратимый crater, если предмет оказался у него.

**Исправление:** тот же единый authorization/start transaction, что и для Airstrike; запрет нельзя реализовывать только в command executor.

**Готово, когда:** все способы запуска — direct command, /mc, GUI и item — проходят одну и ту же policy и дают одинаковый результат.

## MC-WORLD-001 — [P0/P1] Прямые terrain mutations обходят защиту, аудит и rollback

**Где — подтверждённые примеры:**

- GrenadeService.java:680–713 — Molotov ставит FIRE напрямую;
- SprayService.java:156–182 — flamethrower ставит FIRE;
- TrenchService.java:99–147 — удаляет до 144 блоков через setType(AIR);
- ExplosivesManager.java:103–153,337–367,455–462 — tripwire/firing wall ставят и удаляют реальные блоки;
- NukeSequence.java:567–648 — crater стирает блоки/контейнеры, добавляет debris и scorch;
- ArtilleryManager.java:109–120 — создаёт carrier/model после interaction;
- vehicle и TCK placement-listeners, перечисленные ниже, также не уважают cancellation.

**Что не так:** plugin mutations не проходят через один проверяемый world-action слой. Protection plugins обычно отменяют player event, но код либо обрабатывает отменённое событие, либо позднее меняет блоки напрямую.

**Риск:** grief в claims, удаление контейнеров и построек, несовместимость с rollback/audit plugins, частично выполненные стены/траншеи/кратеры.

**Исправление:**

- ввести TerrainMutationPolicy/PlacementPolicy с actor, owner UUID, action type, world, footprint и reason;
- preflight проверяет **всю область**, а не один clicked block;
- реализовать adapters для используемых protection plugins и безопасный fallback;
- mutation выполняется batch-ами с hard budget;
- для крупных операций хранить журнал/undo plan;
- не начинать операцию, если preflight хотя бы одного блока запрещён;
- временно дать отдельные config flags для block-breaking nuke/fire/trench/wall и безопасные defaults.

**Готово, когда:** region-integration tests доказывают отсутствие частичных mutations; rollback восстанавливает прежний BlockData и container state там, где операция обещает обратимость.

---

# 5. Высокоприоритетные системные проблемы

## MC-CONFIG-001 — [P1] NaN/Infinity и огромные значения проходят почти во все модули

**Где:**  
AirstrikeSequence.java:73–99  
NukeSequence.java:119–139,545–632  
WeaponConfig.java:191–349  
AntiAirConfig.java:89–145  
TckBusSettings.java:176–249  
TrainConfig.java:50–79  
AirshipConfig, JetConfig, HelicopterConfig, DroneConfig.java:105–182, KamazConfig.java:95–164, TankConfig.java:104–184, PickupConfig.java:87–157  
CameraServiceImpl.java:53–69  
core/util/MathUtil.java:38–40

**Что не так:** Math.max/min не обезвреживают NaN. Во многих местах есть только lower bound, но нет upper bound, проверки finite, cross-field constraints или оценки стоимости.

**Последствия:**

- Nuke создаёт и сортирует O(radius²) crater queue; возможны OOM, integer overflow и watchdog.
- Airstrike может создать огромный TNT burst за один tick.
- WarKit может породить тысячи displays/particles/tasks или division by zero.
- TCK может восстановить absurd workerCount из PDC и создать entity storm.
- Train/vehicles получают NaN coordinates/speed/transforms и могут отменить общий scheduler task.
- Camera может создать invalid AttributeModifier и перестать reconcile.

**Исправление:**

1. Typed immutable ConfigSnapshot для каждого модуля.
2. Общий decoder: finiteDouble, rangedInt/Double, duration, boundedCount, material, sound.
3. Cross-field validation: min ≤ max, cost > 0, interval ≥ 1, radius × operationsWithinBudget.
4. Parse/validate **весь** новый config до commit; при одной ошибке оставить старый snapshot.
5. Runtime hard caps должны существовать даже после validation — защита от corrupt PDC и programming error.
6. Выводить все ошибки одним ValidationReport с path/value/allowed range.

**Готово, когда:** fuzz tests с NaN, ±Infinity, MIN/MAX_VALUE, negative, zero и huge values не меняют active snapshot и не создают сущности/задачи.

## MC-CHUNK-001 — [P1] Airstrike/Nuke портят глобальный force-loaded state

**Где:**  
AirstrikeSequence.java:232–267  
NukeSequence.java:669–705

**Что не так:** каждая sequence вызывает World.setChunkForceLoaded(true/false) и хранит только собственный Set. Нет plugin ownership и reference count.

**Риск:** два overlapping strike используют один chunk; завершение первого ставит false и ломает второй. Точно так же снимается force-load администратора или другого плагина. При crash штатный teardown не гарантирован.

**Исправление:** единый ChunkLeaseService:

- plugin chunk tickets вместо глобального setChunkForceLoaded;
- reference count по (world UUID, chunk X, chunk Z);
- lease/AutoCloseable на sequence;
- diff desired window, global cap и metrics;
- release только собственного ticket после обнуления refcount;
- аварийная cleanup всех leases на disable;
- никакого synchronous generation из hot tick.

**Готово, когда:** concurrency test с двумя leases на один chunk показывает, что release первого не выгружает chunk; чужой force-loaded state остаётся неизменным.

## MC-CHUNK-002 — [P1] Движение техники синхронно грузит/генерирует мир

**Где:**

- HelicopterController.java:240–245 — world.loadChunk(..., true) для трёх чанков во время движения;
- DroneController.java:99–119 и Drone.java:397–405 — ticket следующего чанка, result игнорируется;
- RailTracer.java:141–159 и Train.java:113–145,223–234 — getBlockAt по не загруженным chunks;
- TrainCommand.java:145–158,201–208 и восемь place-команд — arbitrary coordinates/getChunk().load();
- TckBusCommands.java:127–207,222–233;
- ArtilleryCommands.java:220–252;
- NukeCommands.java:114–155.

**Риск:** игрок или администратор может быстро генерировать дальний мир на main thread, вызвать TPS stall и раздувание диска. NaN/Infinity дополнительно отравляют Location/API.

**Исправление:**

- общий CommandCoordinates validator: finite, world border, min/max Y, maximum distance;
- policy: команды не генерируют новые chunks по умолчанию;
- async chunk preload там, где Paper API и продуктовая политика это допускают, затем sync continuation;
- per-player/module world-generation rate limit;
- vehicle movement останавливается у unloaded frontier либо использует заранее выданный bounded route lease;
- result addPluginChunkTicket/load проверяется.

**Готово, когда:** команды с NaN/Infinity/30M coordinates отклоняются до world access; profiler не показывает chunk generation внутри vehicle tick.

## MC-LIFE-001 — [P1] Reload частичный, но всегда сообщает успех

**Где:**  
MilitaryCraftPlugin.java:100–105  
ModuleManager.java:54–71  
RootCommand.java:76–83  
CameraModule.java:97–105

**Что не так:** ModuleManager логирует и подавляет RuntimeException каждого модуля. RootCommand и direct camera command безусловно отправляют success.

**Риск:** сервер оказывается в смеси старых и новых settings; часть task уже отменена, часть объектов читает новый snapshot, администратор считает reload успешным.

**Исправление:**

- глобальные и module config сначала parse/validate;
- prepare phase ничего не меняет;
- commit phase атомарно swap-ит snapshots;
- ReloadReport содержит SUCCESS/PARTIAL/FAILED по каждому модулю;
- при commit failure либо rollback, либо module переводится в явный FAILED/DEGRADED;
- пользователю возвращается краткий итог и путь к подробному log.

**Готово, когда:** fault-injection одного module reload не меняет остальные snapshots и команда не пишет green success.

## MC-LIFE-002 — [P1] Enable/disable cleanup не гарантирует освобождение всех ресурсов

**Где:**  
ModuleManager.java:74–97  
TankModule.java:69–97  
WarKitModule.java:64–87  
WarKitRuntime.java:75–89  
NukeManager.java:144–163  
AirstrikeManager.java:105–114

**Что не так:** cleanup steps выполняются последовательно в одном try. Первое исключение пропускает остальные unregister/cancel/remove, после чего finally обнуляет ссылки. Enable failure может оставить listener/task/provider, хотя id считается inactive; disable failure оставляет id active.

**Исправление:** LifecycleScope/CloseStack регистрирует каждую listener registration, command binding, provider, task, ticket и runtime resource сразу после acquire. close выполняет **все** actions в reverse order, агрегирует suppressed errors и остаётся retryable. Состояния: NEW → ENABLING → ACTIVE → DISABLING → DISABLED/FAILED.

**Готово, когда:** fault injection в каждом cleanup step не оставляет зарегистрированных listeners/tasks/providers/tickets и выдаёт aggregated report.

## MC-SPAWN-001 — [P1] Spawn/start многих объектов не транзакционен

**Где — примеры:**  
AirstrikeManager.java:49–71  
NukeManager.java:85–104  
TrainManager.java:72–95, Train.java:99–149, TrainCar.java:197–229  
DeployableManager.java:176–216  
SentryManager.java:109–162  
vehicle create/model spawn paths

**Что не так:** entities, tickets, cooldown и intermediate state создаются до публикации в registry; исключение посередине оставляет orphan entities или leases. В strike cooldown записывается до полного успешного schedule.

**Исправление:** единый шаблон preflight → staged resource scope → spawn/build → schedule → publish → commit. Любой Throwable до commit вызывает rollback всего scope. Registry становится видимым только после success.

**Готово, когда:** искусственная ошибка после каждого N-го spawn не оставляет ни одной entity/task/ticket/cooldown.

## MC-CLEAN-001 — [P1/P2] cleanup — фактически необратимый purge без preview

**Где:**  
RootCommand.java:101–109  
CommandMenu.java:64–70,215–222  
VehicleServiceImpl.java:110–125

**Что не так:** /mc cleanup и GUI в один шаг удаляют не только orphan entities, но и все tracked vehicles. Девять providers последовательно вызывают purge; большинство purgeAll отдельно сканирует все entities всех миров.

**Риск:** административная ошибка уничтожает технику; на entity-heavy сервере одна команда делает около девяти полных world scans на main thread.

**Исправление:**

- разделить repair-orphans, purge-tracked и purge-all;
- default cleanup только диагностирует/quarantine;
- dry-run, module/world scope, counts и confirm token с timeout;
- batch removal с progress и tick budget;
- один общий tagged-entity pass вместо N world scans;
- backup/export registry перед destructive purge.

**Готово, когда:** один GUI click не может выполнить destructive action; dry-run и actual run используют один immutable plan.

## MC-OBS-001 — [P2] Ошибка tick может заморозить систему или заспамить log

**Где:**  
TrainManager.java:53–63 — нет per-train/top-level barrier  
TckBusManager.java:76–106  
TurretManager.java:82–100  
TankManager.java:165–170  
JetManager.java:252–263  
HelicopterManager.java:165–176  
AirshipManager.java:166–177  
DroneManager.java:213–224  
PickupManager.java:99–103,172–199

**Что не так:** в Train одна exception отменяет единственный repeating task. В других менеджерах одна и та же ошибка может логироваться 20 раз/сек на объект, часто без полноценного stack trace/context.

**Исправление:** TaskSupervisor с top-level finally, per-object isolation, failure counter, exponential backoff/quarantine, rate-limited structured logging и module health metrics.

**Готово, когда:** сломанный объект не останавливает остальные и создаёт максимум одно подробное сообщение + периодический summary.

---

# 6. Vehicle и camera

## MC-VEH-001 — [P1] Placement-listeners обходят cancelled events

**Где:**  
airship/listeners/PlacementListener.java:27–39  
jet/listeners/PlacementListener.java:26–38  
helicopter/listeners/PlacementListener.java:26–38  
drone/listeners/PlacementListener.java:26–38  
kamaz/listeners/PlacementListener.java:26–38  
tank/listeners/PlacementListener.java:27–39  
train/listeners/PlacementListener.java:28–59  
weapons/tckbus/TckBusPlacementListener.java:26–68  
weapons/antiair/listeners/PlacementListener.java:28–65

**Проблема:** ignoreCancelled=false задан явно либо используется default false, затем техника создаётся после отмены WorldGuard/claim listener.

**Решение:** единый VehiclePlacementService с cancellation contract, permission, full 3D footprint/AABB, world/border/loaded-chunk, overlap, owner quotas, cooldown и transactional consume/spawn.

## MC-VEH-002 — [P1] Place-команды обходят validation обычного предмета

**Примеры:**

- KamazCommand.java:100–135 вызывает create напрямую, тогда как TruckManager.java:286–312 содержит validateCreate/recordCreate.
- TankCommand.java:94–97,145–149 и tank PlacementListener.java:50–61 не используют TankCollision.validatePlacement.
- AntiAirCommand.java:87–113,186–190 бесплатно создаёт turret, не проверяет maxLoaded/footprint/claim и пишет success независимо от результата.
- TCK/Train и остальные команды имеют собственные неполные coordinate/placement paths.

**Риск:** limits, spacing, collision, economy и region policy зависят от способа запуска.

**Решение:** command/item/GUI должны вызывать одну application operation; admin bypass — отдельная явная capability с audit, а не другой code path.

## MC-VEH-003 — [P1] Manager забывает multi-chunk объект при unload любой детали

**Где:**  
AirshipManager.java:273–293  
TruckManager.java:247–271  
TankManager.java:265–291  
TckBusManager.java:147–165  
аналогичный dormant defect: core/vehicle/VehicleManager.java:263–332

**Что не так:** модели длиннее одного chunk. Выгрузка peripheral hitbox/worker удаляет весь wrapper, хотя authoritative core остаётся загруженным. Оставшиеся entities теряют controller; поздний load может дать duplicate/frozen state.

**Исправление:**

- canonical durable core/anchor;
- entity UUID → object UUID/role index;
- forget только при core unload;
- peripheral unload = detach, late load = reconcile;
- object tracks current chunk set;
- corrupt/partial group помещается в quarantine, а не удаляется автоматически.

## MC-VEH-004 — [P1] Rehydrate Tank/Kamaz может сдвинуть всю модель

**Где:**  
Truck.java:157–205  
Tank.java:143–185  
для сравнения положительный вариант: Pickup.java:199–256

**Что не так:** если center отсутствует, anchor берётся из первого произвольного hitbox, который сам смещён относительно центра.

**Риск:** после chunk reload модель прыгает, сталкивается с блоками или дублируется.

**Исправление:** абсолютный anchor + schema version в PDC canonical core; восстановление только из него. Старые группы мигрировать по роли/offset с диагностикой.

## MC-VEH-005 — [P1] Driver/player state восстанавливается несимметрично

**Где:**  
Drone.java:155–165  
DroneManager.java:284–315  
drone/listeners/InteractionListener.java:142–151  
driver cloak/invisibility paths других aircraft

**Что не так:** Drone rehydrate не восстанавливает driver relation; join handler безусловно делает setInvisible(false), если текущий manager не знает игрока.

**Риск:** после crash/reload игрок остаётся passenger/изменённого масштаба либо теряет invisibility potion/состояние другого plugin.

**Исправление:** PlayerStateLease со snapshot только тех свойств, которые меняет MilitaryCraft: invisibility, scale modifier, gamemode/camera, velocity constraints. Reconcile на join/respawn/world change; восстанавливать только собственное значение и только если оно не было заменено извне.

## MC-VEH-006 — [P1/P2] Ammo, reload, heat и weapon lock не persist

**Где:**  
Airship.java:89–101,352–363  
Jet.java:79–92,267–279  
Helicopter.java:93–106,370–381  
Tank.java:69–72,292–304  
Pickup.java:105–108,349–363

**Что не так:** rehydrate получает полные боеприпасы или сбрасывает reload/overheat/lock.

**Риск:** chunk unload/restart становится бесплатной перезарядкой и снимает ограничения.

**Исправление:** persist gameplay-authoritative state и absolute deadlines; transient animation state не сохранять. Версионировать schema и тестировать spawn → use → unload → load → restart.

## MC-VEH-007 — [P1] Train ticket и tick lifecycle

**Где:**  
Train.java:416–447  
TrainManager.java:53–63  
TrainManager.java:72–95  
Train.java:99–149  
TrainCar.java:197–229

**Проблемы:**

- после reload keepChunksLoaded=false метод возвращается, но не снимает ранее выданные tickets;
- addPluginChunkTicket result игнорируется;
- refresh вызывается дважды за tick;
- exception одного train отменяет общий task;
- spawn около 241 display не транзакционен.

**Решение:** немедленный diff/release при config transition, один ChunkLeaseService call/tick, transactional spawn, per-train supervisor.

## MC-VEH-008 — [P1/P2] Train не имеет rail reservation и слишком тяжёл

**Где:**  
Train.java:113–149,187–200,223–234  
TrainManager.java:72–95  
TrainModel.java:102–300  
TrainCar.java:143–173  
RailCursor.java:48–68

**Что не так:** короткий путь экстраполируется прямо через стены; несколько поездов можно поставить на одни rails; уже закэшированный путь не revalidate после переключения/разрушения rails. Один поезд — около 241 display, до шести — около 1 446 display teleports каждый tick.

**Исправление:** rail-segment reservation/occupancy, full-length clearance, segment version/revalidation, entity budget, LOD/distance pause, animation 5–10 Hz, display interpolation/parent hierarchy и performance gate.

## MC-VEH-009 — [P1] Moto persistence выполняет sync YAML+fsync на main thread

**Где:**  
MotorcycleManager.java:175–177  
MotorcycleIndex.java:390–437,215–279

**Что не так:** каждые 100 ticks сериализуется и fsync-ится весь YAML. Tombstones и cooldowns не имеют retention/compaction.

**Риск:** файл неограниченно растёт, а регулярный flush создаёт TPS spikes.

**Исправление:** immutable snapshot на main thread → single-writer async serialization/fsync/atomic move; bounded queue, last-known-good, shutdown deadline, tombstone/cooldown retention и compaction metrics.

## MC-VEH-010 — [P1] Placement и collision допускают non-finite coordinates, неполный footprint и tunnelling

**Где:**  
pickup/commands/PickupCommand.java:171–214  
pickup/PickupCollision.java:32–80  
drone/control/DroneController.java:99–141  
moto/control/DriveController.java:104–160  
jet/control/FlightController.java  
airship/control/AirshipController.java  
helicopter/control/HelicopterController.java  
kamaz/control/DriveController.java  
tank/control/DriveController.java  
airship, jet, helicopter, drone, kamaz и tank command/listener placement paths

**Что не так:**

- Pickup `/place` принимает NaN/Infinity, после чего collision-код приводит координаты к `int` и обращается к `world.getBlockAt`; это способно отправить main thread к крайнему chunk.
- Большинство placement checks проверяет точку или один блок сверху, а не полный ориентированный объём длинной модели; Tank даже не использует уже существующий validator.
- Контроллеры в основном проверяют только конечную позицию. При разрешённой скорости машина может за один tick пройти сквозь тонкую стену. Даже Moto допускает перемещение до двух блоков/tick при endpoint terrain test.
- Pickup обещает support under all wheels, но PickupCollision.java:40–42,69–80 проверяет центральную колонку, поэтому машина может висеть над краем.

**Риск:** синхронная генерация далёкого мира, spawn внутри здания, проход техники через стены и рассинхронизация физики между модулями.

**Исправление:** на внешней границе каждой команды проверять `Double.isFinite`, world border, min/max height и loaded footprint. Один shared collision service должен выполнять full oriented footprint/entity clearance и adaptive swept OBB/voxel substeps с жёстким лимитом работы; для колёсной техники — проверка всех contact points и допустимого перепада высот.

## MC-VEH-011 — [P1] Composite entity update и rehydrate не являются атомарными

**Где:**  
Airship.java:119–227,396–463  
AirshipManager.java:248–256  
Jet.java:312–356  
Helicopter.java:416–490  
Drone.java:290–343  
Truck.java:377–443  
Pickup model refresh paths  
aircraft/Truck/Tank/Pickup passenger-mount paths

**Что не так:** Airship перестраивает только части, пришедшие в текущем `EntitiesLoadEvent`; после первой группы manager считает wrapper готовым, а поздно загруженные части того же ID остаются отдельно. Одновременно многие модели игнорируют результат `teleport`: logical position уже считается обновлённой, хотя seat/hitbox/display могли остаться в разных местах. Airship/Jet/Helicopter/Drone/Truck игнорируют и результат `addPassenger`, но успевают изменить rider maps или invisibility.

**Риск:** дубли, split-brain модель в нескольких chunks, невидимый игрок без машины, повреждённая collision geometry и ошибка, повторяющаяся каждый tick.

**Исправление:** canonical core + ожидаемый role/index manifest; собирать объект только после bounded reconciliation затронутого chunk-set. Move должен быть транзакцией: обновить authoritative anchor, проверить derived teleports/mount, при частичном отказе rollback/retry, после лимита — quarantine с диагностикой. Rider state менять только после успешного `addPassenger`.

## MC-VEH-012 — [P1/P2] Display/entity work не имеет runtime budget

**Где:**  
Airship.java:396–463  
Jet.java:312–356  
Helicopter.java:416–490  
Drone.java:290–343  
Truck/Tank/Pickup model refresh paths  
DroneManager.java:72,86–123  
PickupManager.java:143–148,249–291  
TrainModel.java:102–300

**Что не так:** десятки display/hitbox entities на машину телепортируются до 20 раз/сек, отдельно обновляются роторы и колёса. Drone каждые 100 ticks сканирует все загруженные entities всех worlds. Pickup при load выполняет `world.getEntities()` для каждого ID и каждый tick проверяет весь набор частей. Train доводит эту модель до примерно 241 display на состав.

**Риск:** стоимость растёт как `vehicles × parts × tick rate`, а recovery scans — как `groups × world entities`; на production-карте это превращается в MSPT и packet spikes.

**Исправление:** задать per-module/global entity, spawn, query, packet и millisecond budgets; индексировать lifecycle events по object UUID/role; проверять canonical core часто, derived parts — редко/event-driven; animation 5–10 Hz, interpolation, distance activation/LOD и reduced part models. Зафиксировать load-test gates на 1/10/50/100 объектов.

## MC-VEH-013 — [P2] Persisted state доверяется без schema validation, а quotas считают только loaded wrappers

**Где:**  
Airship.java:167–176  
Jet.java:153–164  
Helicopter.java:173–182  
Drone.java:155–165  
Truck.java:169–185  
Tank.java:155–165  
Pickup.java:199–205,231–249  
AirshipManager.java:305–315  
HelicopterManager.java:351–361  
TruckManager.java:315–323  
Train runtime/persistence

**Что не так:** legacy rehydrate принимает PDC coordinates/HP/anchor без finite/range/schema checks. Ограничение `max-per-player` у нескольких modules считает только загруженные wrappers, поэтому unload chunk обходит quota. Train вообще не сохраняет состав и маршрут: restart безусловно уничтожает активный gameplay state, но этот контракт не объяснён пользователю.

**Риск:** corrupt PDC заражает live tick NaN-значениями; лимиты обходятся park/unload; поведение restart неожиданно и ведёт к потере объектов.

**Исправление:** versioned validated decoder с required fields, ranges, checksum и quarantine; durable registry должен быть источником ownership/quota независимо от loaded state. Для Train либо добавить canonical persistent record и rehydrate, либо явно назвать его ephemeral, предупредить в UI/config и запретить выдавать как постоянный asset.

## MC-VEH-014 — [P2/P3] Накоплены локальные correctness- и maintainability-дефекты

**Примеры:**

- 21 legacy-файл Pickup содержат `Decompiled with CFR 0.152`; PickupPart.java:58–69, Transforms.java:58–78 и Pickup.java:435–450 сохраняют decompiler artifacts (`ordinal` switch, synthetic `MatchException`). Это не надёжный source-of-truth.
- `repair` у Airship.java:558–568, Jet.java:429–440, Helicopter.java:611–621, Drone.java:436–447, Truck.java:572–583, Tank.java:527–538 и Pickup.java:576–587 способен уменьшить HP и вернуть отрицательное лечение, если reload снизил max-health.
- Jet.java:442–480 при crash создаёт impact explosion, затем `destroy(true)` — вторую explosion возле той же точки.
- `/give` в AirshipCommand.java:82, JetCommand.java:82, HelicopterCommand.java:82, DroneCommand.java:103 и KamazCommand.java:69 игнорирует inventory leftovers и молча теряет предмет.
- Airship/Jet/Helicopter/Drone безусловно делают `setInvisible(false)` при выходе, стирая potion/состояние другого plugin; Kamaz/Tank snapshot не имеет полного offline/join recovery.
- Cooldown maps в AirshipManager.java:59, JetManager.java:58, HelicopterManager.java:62, DroneManager.java:62, TruckManager.java:53–55 и TankManager.java:57 не имеют общего TTL/retention cleanup.
- Drone rocket/debris и debris других modules запускают delayed tasks вне module task-scope; disable не гарантирует их отмену.
- PickupManager.migrateStale():620–672 группирует только loaded entities и восстанавливает HP до максимума; частичная legacy-группа теряет состояние или дублируется.
- Pickup.java:1028–1039 включает overheat только при `size() > limit`, то есть на один выстрел позже ожидаемого `>=`.
- TrainItem.java:38–44 принимает любое наличие PDC key, не проверяя значение/schema; TrainManager.java:121–123 отдаёт mutable values-view; комментарий InteractionListener.java:101–111 обещает удаление ударом, которого реализация не делает.

**Исправление:** прежде чем рефакторить decompiled Pickup, зафиксировать black-box regression tests и восстановить понятный исходный source/provenance. Нормализовать HP до нового maximum и возвращать `max(0, after-before)`; оставить один authoritative Jet blast; обрабатывать inventory leftovers; использовать общий reversible PlayerStateLease, bounded expiring maps и module-owned TaskScope. Мелкие расхождения превратить в parameterized contract tests для всех vehicle modules.

## MC-CAM-001 — [P1] Camera modifier persistent и может пережить crash

**Где:**  
CameraServiceImpl.java:115–129, особенно 128  
CameraModule.java:46–63,91–94

**Что не так:** используется addModifier, а не transient modifier. Quit/disable cleanup не выполняется при kill/crash.

**Риск:** camera:zoom сохраняется с player data и остаётся после удаления/поломки plugin.

**Исправление:** MilitaryCraft-owned NamespacedKey, addTransientModifier и одноразовая join-migration, удаляющая legacy camera:zoom.

## MC-CAM-002 — [P1] Camera defaults не работают до первого reload

**Где:**  
MilitaryCraftPlugin.java:71–88  
CameraModule.java:37–43  
CameraServiceImpl.java:43–69  
config.yml:91–96

**Что не так:** Camera включается первой и строит tagScales. Поздние registerScale меняют compatibilityScales, но не effective tagScales.

**Исправление:** defaults и overrides хранить отдельно, effective map пересчитывать при каждой регистрации; либо включать camera после providers. Добавить cold-start test.

## MC-CAM-003 — [P1/P2] Camera меняет физику и конфликтует с другими modifiers

**Где:**  
CameraServiceImpl.java:38,100–129,131–148  
config.yml:69–81

**Что не так:**

- SCALE меняет не только F5 distance, но и hitbox, collision/suffocation;
- amount = desired − base игнорирует чужие modifiers;
- global key camera:zoom может принадлежать другому plugin;
- порядок нескольких scoreboard tags недетерминирован.

**Исправление:** VehicleService.riddenBy как primary identity; owned transient modifier; определённая composition policy; безопасные ground/air caps и opt-out. Для идеального visual-only UX рассмотреть client-side/camera-entity решение, не меняющее hitbox.

## MC-CAM-004 — [P1] vehiclecamera.admin запускает глобальный reload

**Где:**  
CameraModule.java:97–105  
plugin.yml:561–563

**Что не так:** permission с описанием camera reload получает disruptive reload всех 16 modules.

**Исправление:** local camera reload; global reload только militarycraft.admin.

---

# 7. Weapons, deployables и artillery

## MC-COMBAT-001 — [P1] Нет единой attribution/team/PvP policy

**Подтверждённые примеры:**

- AirstrikeSequence.java:177–185 — TNT без caller source;
- NukeSequence.java:382–412 и RadiationManager.java:71–95 — generic damage без causing entity;
- ArtilleryBallistics.java:153–158 — shell explosion без operator;
- TargetingSystem.java:236–251 — offline AntiAir owner превращается в generic damage;
- DeployableManager.java:588–595, SentryManager.java:288–311, ExplosivesManager.java:139–148,264–272 — null owner означает allow/generic damage;
- Train.java:282–337 и vehicle impact — knockback применяется даже после отмены damage;
- TckBusSnatchManager.java:313–329 — setHealth(0) обходит damage pipeline.

**Риск:** friendly fire, обход PvP/claim rules, отсутствие kill credit/stats/audit, разные результаты для online/offline owner.

**Исправление:** CombatContext с actor UUID, owner/team snapshot, weapon/action id, source object, world и flags. CombatPolicy отдельно решает damage, knockback, status effects, terrain и target mode. Offline UUID не должен превращаться в null=allow.

## MC-ART-001 — [P1] Stale artillery record перезаписывает настоящий блок

**Где:**  
ArtilleryManager.java:380–392,422–467  
ArtilleryModelManager.java:81–93

**Что не так:** любой carrier, который не AIR/BARRIER, принудительно заменяется на BARRIER как repair.

**Риск:** stale/corrupt registry уничтожит постройку игрока.

**Исправление:** unknown block никогда не repair автоматически. Signed marker/schema; invalid entry → quarantine/doctor report; отдельная подтверждаемая migration.

## MC-ART-002 — [P1] Artillery persistence теряет stable identity

**Где:**  
ArtilleryStore.java:69–124,182–190  
ArtillerySessionManager.java:286–340

**Что не так:** save не пишет artillery id/world UUID, load синтезирует их; pending session save также не пишет world UUID. Незагруженный при старте world может получить неверную identity.

**Исправление:** versioned schema с required stable UUID, world UUID, coordinates, owner/ACL и revision; explicit one-time migration с backup и integrity report.

## MC-ART-003 — [P1] Artillery session restore может сбросить игрока с высоты

**Где:**  
ArtillerySessionManager.java:95–100,148–172,208–230

**Что не так:** gamemode восстанавливается до подтверждённого teleport, а session уже удаляется. При rejected/failed teleport игрок может оказаться survival на camera altitude.

**Исправление:** preload destination, проверить teleport result, до success держать safe state, session закрывать только после commit; persist retry/rollback.

## MC-ART-004 — [P1/P2] Persistence и camera делают sync hot work

**Где:**  
ArtilleryStore.java:107–124  
ArtilleryManager.java:162,217,223,248  
ArtillerySessionManager.java:208–230  
ArtilleryModelManager.java:262–275

**Что не так:** полный YAML rewrite на горячих действиях, player teleport каждый tick, глобальный scan BlockDisplay при refresh.

**Исправление:** dirty batching/journal + async snapshot; camera entity/teleport only on drift; chunk/spatial tagged index.

## MC-AA-001 — [P1] AntiAir не имеет полноценного owner/team ACL

**Где:**  
InteractionListener.java:24–40  
GuiListener.java:105–220  
AntiAirCommand.java:61–113

**Что не так:** любой antiaircraft.use открывает чужой turret, меняет mode и забирает/кладёт fuel. Command place/give имеет более слабую policy и обходит normal placement.

**Исправление:** persisted owner/team/access list; authorization повторяется при каждом GUI click; fuel mutation audit; separate admin override.

## MC-AA-002 — [P1] AntiAir config/PDC и fallback explosion небезопасны

**Где:**  
AntiAirConfig.java:89–145  
Turret.java:142–169,533–543  
TargetingSystem.java:268–327  
TurretManager.java:163–175  
TargetingSystem.java:187–193

**Что не так:** NaN/huge values делают immortal turret/entity storm; fallback rocket создаёт real explosion с breakBlocks=true; burst списывает fuel один раз, хотя setting называется consume per shot.

**Исправление:** bounds/versioned PDC/quarantine; visual + CombatService вместо throwaway real explosion; атомарный fuel accounting на projectile либо переименование semantics.

## MC-TCK-001 — [P1] Scripted capture bypasses combat и портит чужие effects

**Где:**  
TckBusSnatchManager.java:85–113,164–174,313–329,402–413

**Что не так:** eligibility не учитывает team/PvP/region; final setHealth(0) не является EntityDamageEvent. Release безусловно удаляет SLOWNESS/JUMP/DARKNESS, даже если эффект дал другой plugin.

**Исправление:** configurable/cancellable capture policy, attributed lethal damage; effect lease хранит точный предыдущий effect/duration/amplifier и восстанавливает только своё изменение.

## MC-WK-001 — [P1] WarKit active objects и tasks не принадлежат lifecycle

**Где:**  
WarKitRuntime.java:75–97  
SprayService.java:212–256  
GadgetService.java:76–88,175–205,253–259  
PainkillerManager.java:38–59  
GrenadeService.java:615–624

**Что не так:** reload смешивает старые и новые snapshots; chemical clouds не cleanup; stim/grapple/painkiller/impulse callbacks частично не tracked. Callback после logout/death/disable действует на неправильную жизнь или пропускает обязательный эффект.

**Исправление:** module TaskRegistry + generation tokens; active object держит immutable config revision; reload либо сохраняет его до завершения, либо controlled migration/restart.

## MC-WK-002 — [P1] Deployable/explosive owner semantics fail open

**Где:**  
TeamRules.java:27–29  
DeployableManager.java:387–407,588–595  
SentryManager.java:175–176,222–243,288–311  
ExplosivesManager.java:139–168,264–272,443–479

**Что не так:** offline owner = null = можно атаковать; mines/tripwire исключают максимум owner UUID и реагируют на teammates/animals/vehicles; чужой Maxim можно занять.

**Исправление:** immutable owner/team/ACL snapshot, единый target classifier на acquire и impact, explicit modes, offline-safe attribution.

## MC-WK-003 — [P1] Suicide vest reusable и имеет неясную enemy policy

**Где:**  
ExplosivesManager.java:278–330

**Что не так:** любой mob/animal считается enemy для arming; vest не расходуется до blast; p.damage может быть отменён/снижен/totem, после чего игрок выживает с тем же vest.

**Исправление:** state machine ARMED → CONSUMED → DETONATED; consume до effect с rollback только при start failure; documented death/totem/team policy.

## MC-WK-004 — [P2] Projectile/AoE/utility mechanics имеют consistency gaps

**Где — сгруппированные подтверждённые случаи:**

- GrenadeService.java:455–516,720–771 — дорогие clouds, AoE через стены, blindness без mode/LOS/team;
- GrenadeService.java:243–270,320–377 — endpoint collision и tunneling;
- GadgetService.java:115–168 — jump jet без cooldown; negative cost увеличивает fuel;
- DeployableManager.java:573–615 — неверная candidate AABB;
- ExplosivesManager.java:337–367 — partial firing wall расходует целый item;
- ExplosivesManager.java:45–59 — cleanup может удалить заново поставленный vanilla tripwire;
- TrenchService.java:33–147 и Weapons.java:345–351 — cooldown не используется, target меняется после preflight, lore 2x6/6x6 не совпадает с 2x8/6x8;
- GadgetService.java:235–274 — scanner O(active scanners × players) и не учитывает vanish/team.

**Исправление:** swept ray/AABB, explicit utility policy, global particle/projectile/entity budgets, transactional footprint, cooldown ownership и документация, совпадающая с реализацией.

---

# 8. Core architecture, commands и persistence

## MC-ARCH-001 — [P1/P2] Две конкурирующие vehicle/placeable архитектуры

**Факты:**

- реальные Tank, Truck, Jet, Helicopter, Airship, Drone, Motorcycle, Pickup и Train реализуют VehicleHandle напрямую;
- реальные modules регистрируют ManagedVehicleProvider;
- ни одного concrete subclass VehicleManager нет;
- DisplayVehicle наследует только OrientedVehicle, который никем не используется;
- VehicleManager наследует только AbstractAircraftManager, который также не используется;
- весь core/placeable не имеет runtime consumer;
- ModelBuilder создаётся в MilitaryCraftPlugin.java:53 и прокидывается через Core, но live modules его не используют;
- неиспользуемая foundation занимает примерно 4–5 тыс. строк и имеет собственные persistence/combat/lifecycle rules.

**Почему это проблема:** новый разработчик видит «красивую» foundation и строит неверную mental model. Баги исправляются в одном из двух стеков. Тесты dormant foundation создают ложное покрытие. WarKit проверяет DisplayVehicle-specific shape/height, хотя live vehicles ими не являются.

**Активное correctness-последствие:** SentryManager.java:91–94 и GrenadeService.java:77–80 используют model height только для DisplayVehicle; все live vehicles получают условную высоту 1.0, включая airship.

**Рекомендация:** принять ADR. Для текущего parity-продукта я рекомендую:

1. Сохранить живой контракт VehicleHandle + VehicleProvider.
2. Добавить в VehicleHandle bounds/aimPoint/collision shape/canonical anchor/state revision.
3. После reachability и regression tests удалить или вынести experimental DisplayVehicle/VehicleManager/placeable foundation.
4. Не мигрировать девять модулей на dormant inheritance только ради уменьшения LOC: это высокий gameplay/data risk.

Альтернатива — отдельный greenfield v2, где foundation действительно становится единственной архитектурой. Не смешивать этот проект с parity migration.

## MC-ARCH-002 — [P2] Entity lookup и event routing линейны и дублируются

**Где:**  
VehicleServiceImpl.java:45–66,90–125  
VehicleCombatServiceImpl.java:72–98  
отдельные WorldListener каждого vehicle module  
startup adoptExisting/purgeAll каждого manager

**Что не так:** lookup перебирает до девяти providers; all() строит новый map/list; block ray повторяется; каждый manager отдельно сканирует world entities на startup/cleanup.

**Исправление:** EntityRegistry:

- entity UUID → module/provider/object/role;
- object UUID → handle/bounds/chunks;
- PDC namespace dispatch для late adoption;
- один world/chunk event router;
- один tagged-entity scan с module buckets;
- spatial index по world/chunk для ray/radius queries.

## MC-CMD-001 — [P1/P2] GUI ломает optional positional arguments

**Где:**  
CommandMenu.java:371–398,543–598

**Что не так:** buildArgs удаляет пустые optional values. WarKit give item [player] [amount] превращает amount в player, если middle arg пуст. Hardcoded Moto workaround доказывает системность.

**Исправление:** GUI вызывает structured application command, а не собирает String[]. Defaults/placeholders задаются в typed ParamSpec.

## MC-CMD-002 — [P2] Command metadata живёт минимум в трёх местах

**Где:**  
CommandMenu.java:543–663  
CommandAccess.java:224–242  
plugin.yml:9–96  
реальные SubCommand/direct executors

**Риск:** aliases, permission, args, GUI и tab completion расходятся. Уже есть два permission namespace и разные результаты direct / legacy /mc / GUI.

**Исправление:** единый declarative CommandDescriptor: ids, aliases, permission, actor type, params, examples, icon, destructive flag, handler. Из него строить /mc, legacy bridge, GUI, help/tab и startup validation plugin.yml.

## MC-CMD-003 — [P2] CommandAccess имеет namespace и action inconsistencies

**Где:**  
CommandAccess.java:166–214,224–242  
config.yml:40–59

**Что не так:**

- plainRoot отбрасывает namespace; /otherplugin:tank может ошибочно считаться MilitaryCraft command и отменяться;
- direct command без subcommand становится __root; allowlist airstrike.call не разрешает direct /airstrike, хотя /mc airstrike call разрешается;
- allowedActions перечитывается и пересобирается на каждый check;
- plugin.yml legacy default permissions конфликтуют с глобальным op gate;
- militarycraft.admin описан как доступ ко всем modules, но не имеет children для legacy direct permissions.

**Исправление:** проверять реальный PluginCommand owner/qualified namespace; compile access snapshot на reload; построить explicit compatibility matrix и тестировать все surfaces.

## MC-CMD-004 — [P2] GUI stale state, chat prompt и destructive UX

**Где:**  
CommandMenu.java:101–105,180–198,246–290,330–340,467–475  
RootCommand.java:220–245

**Проблемы:**

- открытый menu переживает module unregister и может дать NPE;
- chat prompt бессрочно перехватывает следующее сообщение;
- tooltip обещает Shift/right-click clear, реализован только right-click;
- нет pagination, лишние entries молча скрываются;
- GUI имеет отдельный permission/execution path.

**Исправление:** registry revision в holder, invalidate/close on reload, prompt TTL/countdown/cancel, pagination, common application handler, confirmation screen для destructive operations.

## MC-DATA-001 — [P1/P2] Runtime data разбросаны по legacy folders

**Где — примеры:**  
TankRuntime.java:62–88  
PickupRuntime.java:62–90  
WarKitRuntime.java:198–229  
AirstrikeRuntime.java:75–101  
аналогичные Runtime классы остальных modules

**Что не так:** это не только fallback чтения. getDataFolder часто возвращает sibling plugins/TankCraft, WarKit, MotoCraft, TCKBus, SvoArtillery и т. д. Backup MilitaryCraft folder не покрывает состояние. Если standalone plugin установлен одновременно, оба процесса могут читать/писать одинаковые PDC/data/commands.

**Исправление:**

1. plugins/MilitaryCraft/modules/{id} — единственный active data root.
2. Versioned one-time importer ищет legacy folders, делает backup, dry-run/report и marker migration-complete.
3. После migration не выполнять тихий fallback.
4. На startup обнаруживать standalone plugin names/PDC conflicts; conflicting module не запускать без explicit override.
5. Документировать backup/restore и schema version.

## MC-DATA-002 — [P2] Recovery удаляет или делает unretryable persistent entities

**Где:**  
VehicleManager.java:263–304  
PlaceableManager.java:171–180,224–251,397–519  
PlaceableRig.java:625–660,868–875

**Что не так:** dormant framework удаляет entities при rehydrate error; cleanup подавляет failures, очищает references и забывает wrapper.

**Исправление:** quarantine вместо delete, failed-cleanup registry/retry queue, diagnostic export и только подтверждённый purge.

## MC-DATA-003 — [P2] PDC/YAML schema versioning непоследовательно

**Примеры:** artillery IDs/world UUID; TCK workerCount; vehicle ammo/driver state; legacy NamespacedKey в десятках modules; Train/AntiAir config transitions.

**Исправление:** у каждой persisted entity/item/file: schema version, stable object UUID, module id, role, authoritative anchor, checksum/required fields и migration table. Неизвестная future version не должна repair/delete автоматически.

---

# 9. Тесты, build и release engineering

## MC-TEST-001 — [P1/P2] Зелёные тесты почти не покрывают live lifecycle

**Факты:**

- 13 test classes, 71 methods, все проходят.
- 34 теста сосредоточены в Moto.
- 21 тест проверяет EntityIndex/placeable/aircraft foundation, которая не подключена к runtime.
- нет Paper/Bukkit runtime harness dependency.

**Критические пробелы:**

- plugin start/stop и module enable/disable fault injection;
- atomic/partial reload;
- cancelled interaction/protection policy;
- command surface/permissions/GUI parity;
- item authorization Airstrike/Nuke;
- multi-chunk unload/rehydrate;
- concurrent chunk leases;
- spawn rollback;
- corrupt PDC/YAML migration;
- offline owner/team;
- full inventory;
- Nuke/Airstrike/WarKit/AntiAir/TCK/Train budgets;
- crash-recovery player state;
- soak/performance.

**ResourceYamlTest.java:105–124** сканирует только Java-файлы с Command в имени и literal permissions regex. Он не видит listener/item permissions, dynamic policy, default semantics и executor wiring.

**Решение — test pyramid:**

1. Pure tests: config fuzz, math, serializers/migrations, permission policy.
2. Mock/integration: events cancelled, inventory, player lifecycle, command surfaces.
3. Real Paper harness: spawn → move/use → chunk unload/load → restart.
4. Fault injection: N-th spawn/write/teleport/task failure.
5. Soak: 50–100 players, max vehicles/turrets/clouds/strikes, assertions по MSPT/entities/tasks/tickets.

## MC-BUILD-001 — [P2] Нет CI, quality gates и воспроизводимого release

**Где:** pom.xml:15–85; в предоставленной директории отсутствуют .github/CI configs.

**Что есть:** Java 21, Paper API 1.21.4-R0.1-SNAPSHOT, JUnit, Xlint deprecation/unchecked. Текущая сборка проходит без таких warnings.

**Чего не хватает:**

- CI compile/test/package на clean checkout;
- Maven Enforcer/toolchain и dependency convergence;
- reproducible build outputTimestamp;
- dependency/security/license scan;
- static analysis/format gate;
- coverage по live packages;
- integration/soak stage;
- release notes, signed checksums и rollback artifact.

**Важно:** Paper SNAPSHOT допустим как server API target, но mutable dependency ухудшает воспроизводимость. Зафиксировать поддержку server versions и тестировать compatibility matrix, а не объявлять библиотеку «устаревшей» без продуктового решения.

## MC-RES-001 — [P2] Resource pack нельзя воспроизвести из source folder

**Факты:**

- resourcepack: 78 файлов, включая 76 валидных JSON;
- ZIP: 187 entries, 70 OGG, 15 PNG, 100 JSON, 2 mcmeta;
- source folder не содержит значительную часть sounds/textures/interface из ZIP;
- build script/manifest/hashes отсутствуют;
- LICENSE/CREDITS/NOTICE не найдены.

**Риск:** невозможно доказать, какой source создал ZIP, повторить release или проверить права на распространение сторонних sounds/textures.

**Исправление:** хранить все исходные assets или документированный fetch pipeline; deterministic pack build; manifest path/hash/license/author; validate model references; release SHA; server distribution instructions.

## MC-DOC-001 — [P2] Документы противоречат текущему проекту

**Примеры:**

- STATUS.md:3 говорит 227 Java-файлов и 75 тестов;
- DEVELOPMENT_GUIDE.md:460 говорит 224 main-файла и 75 тестов;
- BUILD_SPEC.md:116,307 описывает единственную /mc command;
- ORIGINAL_PARITY_RESTORE_PLAN.md:21,40–53 позже требует direct legacy commands и является более свежим источником;
- graphify-out/GRAPH_REPORT.md и graph.json всё ещё содержат удалённый MagicCarpet;
- существующий CLAUDE_CODE_REVIEW.md сам ограничивает глубокий scope gameplay modules и не заменяет этот аудит.

**Риск:** документация формирует неверную архитектурную картину и приводит к ошибочным изменениям.

**Исправление:** один current README + ADR index + generated status. Старые PLAN/BUILD_SPEC пометить superseded/archive. Graphify artifacts либо regeneratable+ignored, либо обновлять в CI. Source code остаётся authority.

## MC-LEGAL-001 — [P2] Нет явной лицензии и provenance

В предоставленной директории не найден LICENSE/COPYING/NOTICE, а проект объединяет код и assets множества прежних plugins.

**Решение:** определить лицензию собственного кода, зафиксировать авторство/источники каждого imported module и каждого asset, проверить условия redistribution. Это не утверждение о нарушении — это отсутствующий release-control.

---

# 10. Maintainability и UX

## MC-MAINT-001 — [P2] God classes и массовая копипаста

33 production-класса имеют не менее 500 строк, 15 — не менее 750. Крупнейшие:

- Helicopter — 1175;
- Airship — 1104;
- MotorcycleManager — 1065;
- Pickup — 1041;
- Motorcycle — 993;
- Truck — 904;
- TckBusRig — 868;
- Tank — 838;
- Drone/DroneManager — 833/822;
- GrenadeService — 786;
- NukeSequence — 755;
- CommandMenu — 739.

LOC сам по себе не баг. Проблема в смешении state, entity model, movement, persistence, combat, UX и cleanup. Повторяются Keys, Transforms, MathUtil, listeners, driver cloak, command coordinate parsing.

**Решение:** не начинать с абстрактного inheritance rewrite. Сначала выделять non-gameplay seams:

- typed config/validation;
- command coordinates;
- lifecycle/task/ticket scope;
- entity registry;
- player state lease;
- action/combat/terrain policies;
- persistence writer;
- model renderer отдельно от domain state.

После characterization tests разбивать каждый god object на state machine + controller + renderer + persistence mapper.

## MC-MAINT-002 — [P2/P3] Exception swallowing и неявные contracts

В production найдено 182 catch Exception/Throwable и 28 ignored catch. Многие нужны для fault isolation, но часть скрывает важный failure:

- crater silently пропускает любой block failure;
- cleanup sequence exceptions игнорируются;
- tick warning часто теряет stack;
- CommandArgs.giveItem имеет контринтуитивный boolean;
- mutable Vector3f/list views возвращаются из «immutable» models;
- static keys/state затрудняют test isolation.

**Решение:** typed Result/Report, structured context, rate limit, запрещённый empty catch quality gate, immutable value objects и ясные method names.

## MC-UX-001 — [P2/P3] Сообщения и интерфейс не являются единой системой

**Наблюдения:**

- hardcoded English strings и разные prefixes;
- legacy §/& formatting смешан с Adventure Components;
- direct и /mc команды имеют разные usage/permissions/feedback;
- полное inventory иногда теряет выдаваемый item, но сообщает success;
- cleanup/reload success не отражает реальный результат;
- cooldown/lore расходятся с кодом, например trench size/cooldown;
- крупные operations не показывают progress/cost;
- нет единого messages/locales слоя.

**Улучшение:**

- MiniMessage/Adventure-based MessageService;
- locales/{en,ru,...}.yml и fallback;
- consistent severity/icon/prefix;
- hover/click suggestions;
- action bar/bossbar progress для strike, migration, cleanup;
- точные reason codes: denied by region, unloaded chunk, quota, invalid config;
- full-inventory policy: add → drop safely → report;
- accessibility: не кодировать статус только цветом.

## MC-OPS-001 — [P2] Не хватает диагностики и budgets

Добавить /mc doctor и /mc status:

- module lifecycle state/config revision/last error;
- tracked objects vs tagged entities;
- orphan/quarantine counts;
- active tasks/projectiles/clouds/strikes;
- chunk leases;
- entity/display/particle budgets;
- persistence queue/last successful flush;
- per-module tick time p50/p95/max;
- config source: unified или legacy migration;
- detected conflicting standalone plugins;
- resource pack version/hash.

Metrics должны иметь low overhead и rate-limited logs; debug mode не должен быть постоянно включён.

---

# 11. Целевая архитектура

Рекомендуемая структура без переписывания gameplay «с нуля»:

1. **ModuleDescriptor** — id, dependencies, commands, permissions, config schema, migrations.
2. **LifecycleScope** — ownership listeners/tasks/providers/tickets/entities/callback tokens; close-all с aggregate errors.
3. **ValidatedConfigService** — immutable snapshots, prepare/commit/report, hard safety limits.
4. **ActionPolicy** — authorization + region/claim/world rules для command/item/GUI.
5. **TerrainMutationService** — preflight footprint, batching, audit и optional rollback.
6. **CombatService** — attributed exactly-once damage, team/PvP/effects/knockback policy.
7. **EntityRegistry** — entity UUID/object UUID/module/role/chunk/bounds/aim point.
8. **ChunkLeaseService** — plugin ticket refcount, budget, diff и diagnostics.
9. **PlayerStateLease** — invisibility/scale/gamemode/effects с ownership и crash reconciliation.
10. **PersistenceService** — versioned schemas, async single writer, atomic replace, migration/quarantine.
11. **CommandDescriptor/ApplicationCommand** — одна операция для CLI, legacy aliases и GUI.
12. **TaskSupervisor** — per-object isolation, backoff/quarantine, rate-limited errors и timings.

Это composition вокруг существующих legacy controllers. Gameplay constants/model geometry можно сохранить, постепенно заменяя только опасную infrastructure.

---

# 12. Приоритетный план работ

## Этап 0 — подготовка и safety net

1. Сделать backup миров, plugins data folders и текущего JAR.
2. Зафиксировать текущую parity-сессию набором characterization tests.
3. Создать test server и scripted scenarios для всех 16 modules.
4. Ввести release checklist и запрет production deploy при P0.

**Exit criteria:** воспроизводимая clean build, backup restore rehearsal, smoke test каждого module.

## Этап 1 — закрыть P0

1. Permission check Airstrike/Nuke item.
2. Уважать cancelled events во всех placement/weapon routes.
3. Ввести минимальный fail-closed Action/Placement/Terrain policy.
4. По умолчанию ограничить destructive terrain features, пока нет region adapters.
5. Добавить security regression tests.

**Exit criteria:** предмет/команда/GUI не обходят permission и protection; ни одной partial terrain mutation при deny.

## Этап 2 — bounded runtime и chunk safety

1. Общий finite/ranged config decoder и atomic reload.
2. Hard caps для entities, displays, particles, TNT, radius, workers, projectiles, active objects.
3. ChunkLeaseService; убрать setChunkForceLoaded из sequences.
4. Запрет sync chunk generation из movement/commands.
5. TaskSupervisor и log rate limiting.

**Exit criteria:** config fuzz green; concurrent lease tests green; max-load soak держит согласованный MSPT budget.

## Этап 3 — lifecycle и transactions

1. LifecycleScope для всех modules.
2. Transactional spawn/start/consume/cooldown.
3. Truthful ReloadReport/ModuleHealth.
4. Safe cleanup preview/confirm/batch/quarantine.
5. PlayerStateLease и task generation tokens.

**Exit criteria:** fault injection после каждого resource acquisition не оставляет leaks; partial reload невозможен или явно reported/rolled back.

## Этап 4 — persistence и multi-chunk correctness

1. Canonical anchor/entity registry для всех vehicle/placeable objects.
2. Fix unload/reconcile Tank/Kamaz/Airship/TCK/Drone/Train.
3. Persist ammo/heat/reload/driver authoritative state.
4. Versioned artillery/world UUID/owner schemas.
5. Async bounded persistence и compaction.
6. Legacy folder migration + conflict detection.

**Exit criteria:** restart/chunk churn tests не создают duplicates/orphans/free reload; corrupt data quarantined, но не удаляет world objects.

## Этап 5 — единая gameplay policy

1. CombatContext/owner/team/offline attribution.
2. ACL artillery/PVO/Maxim/sentry/mines/vehicles.
3. Damage/effect/knockback/terrain matrix.
4. Effect ownership для TCK/medkit/camo/stim.
5. Collision/LOS/swept projectile fixes.

**Exit criteria:** team/PvP/claim tests одинаковы для online/offline owner и всех weapon types.

## Этап 6 — architecture cleanup

1. ADR по двум vehicle architectures.
2. Расширить живой VehicleHandle bounds/aimPoint/anchor/revision.
3. Удалить dormant foundation и её tests либо вынести в отдельный experimental module.
4. EntityRegistry заменяет N scans/linear providers.
5. Typed command descriptor заменяет hardcoded maps/GUI schemas.
6. Разделить god classes по стабильным seams.

**Exit criteria:** один runtime path для lifecycle/combat/commands; documentation и tests описывают именно его.

## Этап 7 — performance, UX и release

1. Profile/LOD/display interpolation/entity budgets.
2. Train reservation и reduced animation cadence.
3. Localized MessageService, pagination, confirmation/progress.
4. /mc doctor, metrics и health reports.
5. Deterministic resource-pack pipeline + licenses/provenance.
6. CI, reproducible artifacts, checksums, changelog и rollback release.

**Exit criteria:** soak/SLO green, no unexplained orphan/ticket/task growth, release полностью воспроизводим.

---

# 13. Минимальная обязательная test matrix

| Область | Обязательные сценарии |
|---|---|
| Security | cancelled interaction; non-op с transferred Airstrike/Nuke item; claim deny; admin override audit |
| Config | NaN, Infinity, negative, zero, huge, overflow, cross-field invalid; old snapshot remains active |
| Lifecycle | failure каждого enable/disable/reload step; no leaked listeners/tasks/providers/tickets |
| Spawn | failure после каждого N-th entity; rollback entities/items/cooldown/tickets |
| Chunks | overlapping leases; unload peripheral/core; movement at unloaded frontier; restart |
| Persistence | corrupt YAML/PDC, future schema, missing world, interrupted write, compaction |
| Player state | crash/rejoin/death/world change during camera/drone/artillery/TCK/stim |
| Combat | online/offline owner, teammate, PvP deny, creative/spectator, wall occlusion, attribution |
| Commands | direct, alias, namespaced, /mc, GUI; identical permission/result; optional args |
| Inventory | full/partial inventory for every give/remove/drop path |
| Performance | max trains/turrets/vehicles/clouds/strikes; MSPT, packets, heap, entity/task/ticket counts |
| Resource pack | JSON/model reference validation, deterministic ZIP hash, client load smoke test |

---

# 14. Definition of “идеально”

MilitaryCraft можно считать production-grade, когда:

- ни одно действие не обходит permission/region/team policy из-за другого entry point;
- любой config value валидируется до применения и имеет hard runtime budget;
- каждый task/listener/entity/ticket имеет владельца и гарантированный cleanup;
- spawn/reload/migration либо завершается полностью, либо полностью откатывается;
- chunk unload/restart не меняет gameplay state и не создаёт duplicates;
- corrupt data quarantined и диагностируется, но не удаляет пользовательский мир автоматически;
- destructive команды имеют preview/confirmation/backup;
- live runtime architecture одна и отражена в документации;
- тесты покрывают реальные modules, а не преимущественно dormant foundation;
- performance имеет измеримые SLO и автоматические soak gates;
- JAR и resource pack воспроизводимы, имеют checksums, provenance и license records;
- администратор видит health, errors и migration source без чтения исходников.

---

## Итог

Самый опасный путь — начинать с косметического рефакторинга или массового переноса всех legacy modules на неиспользуемый framework. Это даст большой diff, но не закроет обход protection, NaN/config DoS, chunk ownership и lifecycle leaks.

Правильный порядок: **security → bounded configuration/chunks → transactional lifecycle → persistence/multi-chunk correctness → unified policy → architecture cleanup → performance/UX/release engineering**.

Этот документ — план работ. Никакие описанные исправления в код не вносились.
