# MilitaryCraft — новое delta-код-ревью и видение развития

**Дата:** 2026-07-18  
**Проверенная версия исходников:** `8e7883b` (`master`)  
**Формат:** только новые сведения и новый план работ; код не изменялся

> Этот документ намеренно не пересказывает `MILITARYCRAFT_CODE_REVIEW.md`, `CLAUDE_CODE_REVIEW.md` и переданную переписку с Claude. Если ниже снова упоминается уже знакомая подсистема, причина только одна: после прежнего ревью в ней появился новый код, а в его реализации обнаружилась новая регрессия либо прежнее исправление оказалось неполным по ранее не описанному сценарию.

---

## 1. Границы и методика этого ревью

Проверены текущее дерево проекта, изменения `32d7fc7..8e7883b`, 330 production- и 9 test-файлов `.java`, `pom.xml`, конфигурация, текущий JAR, source resource pack и `WarKit-ResourcePack.zip`. Старые отчёты и вся переписка Claude сначала были превращены в список исключений; уже описанные там security, lifecycle, persistence, LOD, localization, CombatPolicy, CI и прочие предложения сюда не переносились.

Аудит статический. Paper-сервер и Minecraft-клиент не запускались. Maven в рабочем проекте не запускался, чтобы не записать ничего в `target`; существующие файлы не менялись. Номера строк относятся к HEAD `8e7883b` и после будущих правок могут сместиться.

### Что особенно важно по новой дельте

1. Последний coordinate-hardening создаёт два новых некорректных пути: `NaN` приводит к NPE, а огромное конечное число всё ещё запускает далёкую синхронную генерацию.
2. Новый refcount учитывает только Airstrike/Nuke и конфликтует с Train/Drone, потому что Bukkit ticket принадлежит плагину, а не внутренней подсистеме.
3. Сохранённый `PilotProtection` больше не имеет write-side и поэтому не защищает текущие модули при crash.
4. Документированный deployment-JAR старее последних hardening-коммитов.
5. Визуальная система сейчас распадается на три несвязанных слоя: живой `warkit:item_model`, неиспользуемые legacy overrides и placer items без поддерживаемых моделей.
6. Actionbar является конкурентным глобальным каналом: важное предупреждение реально может быть затёрто обычным HUD через несколько тиков.

---

## 2. Сводка новых находок

| ID | Приоритет | Новая находка | Главный эффект |
|---|---:|---|---|
| DELTA-TECH-001 | P1 | World-border clamp всё ещё ведёт к далёкому `getChunk().load()` | freeze/main-thread generation |
| DELTA-TECH-002 | P2 | `NaN`/`Infinity` дают `null`, который разыменовывают семь команд | command exception; ложные координаты success |
| DELTA-TECH-003 | P1 | `ChunkWindow` конфликтует с Train/Drone tickets того же plugin owner | активная система теряет chunk ticket |
| DELTA-TECH-004 | P2 | `PilotProtection` читает маркеры, которые текущий код никогда не пишет | невидимость может пережить crash |
| DELTA-TECH-005 | P1/P2 | Сбойный Train вызывается снова 20 раз/сек и больше не логируется | скрытая постоянная нагрузка и poison state |
| DELTA-TECH-006 | P2 | Legacy persistent camera modifier не мигрирует при совпавшем amount | старый zoom продолжает сохраняться |
| DELTA-TECH-007 | P2/P3 | Airstrike cap не ограничивает burst за tick; `trail-density` ограничен наоборот | до 200 spawn за tick; cap повышает particle rate |
| DELTA-TECH-008 | P2/P3 | Разные clock policies дают fail-open и fail-closed при скачке времени | bypass либо искусственное продление cooldown |
| DELTA-REL-001 | P1 | `target/MilitaryCraft-1.0.0.jar` отстаёт от HEAD | деплой не содержит заявленные fixes |
| DELTA-API-001 | P2 условно | Публичные классы/методы удалены без смены версии `1.0.0` | возможны `NoSuchMethodError` у add-on |
| DELTA-VIS-001 | P2 | Optional resource-pack contract не реализован | missing model вместо обещанного vanilla fallback |
| DELTA-VIS-002 | P2 | Мёртвый `ModelData` расходится с live IDs; placer IDs сталкиваются | технику нельзя визуально различить по CMD |
| DELTA-VIS-003 | P2/P3 | ZIP содержит недостижимый legacy asset layer и не обслуживает placer items | vanilla look и конфликты с другими packs |
| DELTA-VIS-004 | P2/P3 | Wearable item models не имеют equipment assets | отсутствует custom equipped silhouette |
| DELTA-VIS-005 | P3 | Обычные корпуса почти всей техники принудительно fullbright | модели не принадлежат освещению мира |
| DELTA-VIS-006 | P3 | У WarKit нет своей texture language и hand-transform QA | несогласованный визуальный масштаб и материалы |
| DELTA-AUDIO-001 | P2/P3 | Sound events отсоединены от Java; три ссылки сломаны | bundled soundbank не работает в MilitaryCraft |
| DELTA-UX-001 | P2 | 209 прямых `sendActionBar` из 65 файлов не имеют арбитража | критические alerts и HUD взаимно стираются |
| DELTA-UX-002 | P3 | FPV textures существуют, но ничем не подключены | нет cockpit/reticle identity |
| DELTA-UX-003 | P2/P3 | Большинство проверенных enter paths не повторяет role controls | игрок не знает управление чужой техникой |
| DELTA-UX-004 | P3 | Ammo/fuel/reload меняются только в PDC, модель предмета статична | state не читается в hotbar/inventory |
| DELTA-UX-005 | P3 | Готовый detonator asset не используется; C4 управляется скрытым жестом | слабая discoverability и нет выбора charge group |
| DELTA-UX-006 | P2/P3 | Deployables не показывают IFF и рабочее состояние в мире | союзное/вражеское и ready/empty неразличимы |

---

# 3. Новые технические находки

## DELTA-TECH-001 — [P1] `safeLocation` превращает огромный ввод в далёкую синхронную генерацию

**Где:**

- `src/main/java/me/bibo/militarycraft/core/util/CommandCoords.java:57–71`
- `vehicles/tank/commands/TankCommand.java:145–146`
- `vehicles/kamaz/commands/KamazCommand.java:130–131`
- `vehicles/jet/commands/JetCommand.java:135–136`
- `vehicles/helicopter/commands/HelicopterCommand.java:146–147`
- `vehicles/airship/commands/AirshipCommand.java:146–147`
- `vehicles/drone/commands/DroneCommand.java:156–157`
- `weapons/tckbus/TckBusCommands.java:202–203`

**Что именно не так:** новое исправление не отклоняет координату вне практической рабочей зоны. Оно клампит X/Z внутрь текущего `WorldBorder`, после чего caller безусловно делает `at.getChunk().load()`. У стандартной границы край находится примерно у ±30 миллионов блоков. Поэтому `/tank place 1e100 70 0` не считается ошибкой: конечное `1e100` превращается в точку около края мира, и сервер синхронно загружает или генерирует этот чанк на main thread.

**Почему это новая информация:** прежний отчёт описывал отсутствие bounds. Здесь проблема уже в новом helper-е, который объявлен завершённым исправлением в `CLAUDE_CODE_REVIEW.md:26`, но выбрал опасную политику silent clamp.

**Риск:** это permission-gated operator path, а не обычный player input, но операторская опечатка всё равно способна остановить общий tick thread. Одновременно объект появляется совсем не там, куда его просили поставить.

**Как исправить именно новый gap:** заменить silent clamp в этом helper-е на `Rejected`, если безопасная effective point отличается от requested point; rejected result не должен доходить до `getChunk().load()`. Success печатается только после spawn в принятой фактической точке. Более широкая архитектура безопасной загрузки/генерации чанков уже описана в прежнем ревью и здесь намеренно не повторяется.

**Готово, когда:** `1e100`, граница мира и негенерированный дальний чанк дают контролируемый отказ без синхронной генерации; разрешённая близкая точка продолжает работать.

## DELTA-TECH-002 — [P2] Новый nullable contract даёт NPE на `NaN`/`Infinity`

**Где:**

- `CommandCoords.java:30–48` — finite-safe `parse`, у которого нет ни одного caller;
- `CommandCoords.java:57–59` — `safeLocation` возвращает `null`;
- `TankCommand.java:126–149,153–163` — локальный parser и немедленный `at.getChunk()`;
- тот же паттерн в Kamaz, Jet, Helicopter, Airship, Drone и TCKBus;
- Moto отдельно проверяет `Double.isFinite` в `MotoCommand.java:282–295` и потому не имеет этого NPE.

**Что именно не так:** `Double.parseDouble("NaN")` и `Double.parseDouble("Infinity")` не бросают исключение. Семь старых локальных `parseCoord` считают результат валидным. `safeLocation` корректно возвращает `null`, но новый nullable contract ни один из этих callers не проверяет.

**Минимальное воспроизведение:** `/tank place NaN 70 0` с нужным permission → `safeLocation == null` → NPE на `at.getChunk()`.

Для огромных конечных чисел возникает второй UX-дефект: сообщения, например `TankCommand.java:149` и `TckBusCommands.java:207`, печатают запрошенные `x/y/z`, а не фактический clamped `Location`. Оператор получает ложное подтверждение.

**Как исправить:** реально подключить общий parser и заменить `null` на typed result: `Accepted(Location)` либо `Rejected(reason, requested)`. Вызов spawn должен получать только `Accepted`; success должен печатать координаты созданного объекта. Удалить семь локальных копий после characterization tests.

**Готово, когда:** таблица `NaN`, `±Infinity`, malformed relative value, huge finite, out-of-height и valid relative coordinate даёт детерминированный результат без exception и без ложного success.

## DELTA-TECH-003 — [P1] Refcount `ChunkWindow` не является общим для всех владельцев ticket

**Где:**

- `core/airsupport/ChunkWindow.java:93–105,109–127`
- `vehicles/drone/drone/Drone.java:397–414`
- `vehicles/train/train/Train.java:432–443,495–502`

**Что именно не так:** Bukkit plugin chunk ticket идентифицируется парой «plugin + chunk». Внутри одного MilitaryCraft нельзя создать независимый Bukkit-ticket для Airstrike, Drone и Train. `ChunkWindow` ведёт refcount только между собственными instances и считает `addPluginChunkTicket == false` признаком чужого ownership. Drone и Train при этом напрямую добавляют и снимают ticket того же `MilitaryCraftPlugin` и ведут отдельные локальные sets.

**Сценарий A:** Train уже держит chunk; Airstrike получает `false` и считает ticket чужим; Train уезжает и снимает ticket; Airstrike продолжает работу без реального удержания chunk.

**Сценарий B:** Airstrike первым добавляет ticket; Drone входит в chunk и считает его своим; Airstrike завершает sequence и снимает ticket; Drone остаётся, но его локальный key всё ещё утверждает, что chunk закреплён.

**Риск:** unload посреди полёта/удара, split state, пропавшие displays и непредсказуемое поведение, зависящее от порядка событий.

**Как исправить именно новый interleaving gap:** сделать существующий `ChunkWindow.REFERENCES` единственной точкой add/remove для этих четырёх consumers, добавить в неё owner token и перевести конкретные прямые вызовы Train/Drone на этот путь. Критичное новое правило: `addPluginChunkTicket == false` нельзя трактовать как безопасное «чужое владение» внутри того же plugin instance. Общая архитектура chunk-lease уже описана в прежнем ревью и здесь не дублируется.

**Готово, когда:** все перестановки acquire/release четырёх типов владельцев подтверждают, что последний живой owner всегда удерживает ticket, а последний release снимает его ровно один раз.

## DELTA-TECH-004 — [P2] `PilotProtection` после удаления framework стал reader без writer

**Где:**

- `core/vehicle/PilotProtection.java:20–40`
- `core/event/EventBus.java:38,113–115`
- пример live write только в RAM: `vehicles/tank/tank/TankManager.java:347–376`
- аналоги: `TruckManager.java:466–515`, `AirshipManager.java:367–386`, `HelicopterManager.java:413–433`, `DroneManager.java:407–415,481–489` и Jet manager.

**Что именно не так:** `recoverStaleVisibility` ищет PDC keys `core:pilot_cloaked` и `core:pilot_was_invisible`, но текущий production-код не записывает ни один из них. Единственный writer `rememberVisibility` был удалён вместе с прежним `VehicleManager`. Наличие join-handler создаёт иллюзию защиты, однако для текущих поездок он гарантированно ничего не находит.

**Риск:** после hard crash игрок может сохранить `invisible=true`, тогда как in-memory map предыдущего состояния исчезнет. После входа reader не увидит marker и не восстановит visibility. Кроме того, модули, которые всегда делают `setInvisible(false)`, могут затереть законную невидимость из другой механики.

**Как исправить:** минимально вернуть write-side именно в живые mount paths: перед изменением visibility атомарно записывать прежнее значение, module ID, vehicle UUID и revision; при штатном dismount удалять marker только после успешного restore. При join восстанавливать один раз, лишь если игрок больше не занимает валидный seat этой revision. Старые markers обработать как отдельную одноразовую migration policy.

**Готово, когда:** kill -9 между mount и dismount, restart и join восстанавливают исходное значение как для изначально видимого, так и для изначально невидимого игрока.

## DELTA-TECH-005 — [P1/P2] Train tick isolation бесконечно повторяет poison object

**Где:** `vehicles/train/train/TrainManager.java:35–36,55–77,154–157`.

**Что именно не так:** exception ловится, логируется один раз и заканчивается `continue`. На следующем server tick тот же Train снова вызывается. Постоянно сломанный объект создаёт и выбрасывает exception 20 раз/сек, но после первого сообщения это полностью скрыто. Проверка `t.isRemoved()` недостижима в exception-path; ручное удаление также не чистит `loggedTickFailures`.

**Риск:** постоянная CPU/allocation нагрузка, повторное выполнение частично завершившегося state transition, удержание displays/tickets и слота `maxTrains`, отсутствие видимого сигнала оператору.

**Как исправить именно новый poison-loop:** exception-path должен до `continue` вывести Train из active registry, один раз попытаться выполнить guarded cleanup и очистить его `loggedTickFailures`; exception, UUID и результат cleanup можно оставить в отдельном диагностическом record. Возврат объекта в active registry допускается только явным recovery action. Общий retry/backoff/supervision design уже находится в прежнем ревью и здесь не повторяется.

**Готово, когда:** постоянно бросающий Train не вызывается 20 раз/сек, не мешает остальным, не удерживает ticket/entities после принятой policy и остаётся видимым в диагностике.

## DELTA-TECH-006 — [P2] Persistent camera modifier с тем же amount не мигрирует

**Где:** `src/main/java/me/bibo/militarycraft/camera/CameraServiceImpl.java:115–131`.

**Что именно не так:** новый код находит modifier по key, сравнивает amount и при равенстве возвращается на строках 123–124. Удаление старого modifier и `addTransientModifier` находятся ниже. Поэтому именно legacy persistent modifier с правильным числом считается уже корректным и остаётся persistent.

**Риск:** часть игроков, ради которых была нужна миграция, продолжает сохранять camera zoom в player data после crash. Комментарий на строках 128–130 обещает удаление при следующем apply, но этот edge case обещание нарушает.

**Как исправить именно equal-amount fast-path:** modifier, не помеченный как созданный в текущей process-session, сначала remove/re-add через `addTransientModifier`; проверять равный amount и возвращаться можно только для уже известной transient записи этой session. Общая one-time camera migration была предложена ранее и здесь не повторяется.

**Готово, когда:** player data со старым modifier и тем же amount после одного join/apply больше не содержит persistent запись, а zoom в текущей поездке остаётся правильным.

## DELTA-TECH-007 — [P2/P3] Airstrike safety cap ограничивает не те величины

**Где:**

- `weapons/airstrike/task/AirstrikeSequence.java:75–85`
- `AirstrikeSequence.java:104–118,146–151,162–165`
- `AirstrikeSequence.java:192–194`
- `src/main/resources/config.yml:1980–1981`

**Часть A — TNT burst:** `tnt-count` ограничен 200, но `jet-speed` не имеет finite/upper bound, а `dropDueBombs` использует неограниченный `while`. При огромной скорости самолёт за один tick пересекает весь run и синхронно создаёт до 200 `TNTPrimed`. Total cap не является per-tick work budget. При нескольких активных strikes spike складывается.

**Часть B — обратный `trail-density`:** параметр по факту является интервалом. `1` означает emit каждый tick, `10` — каждый десятый. Новый `Math.min(10, value)` не защищает от storm; он превращает прежнее `1_000_000` (почти выключенный trail) в `10` и тем самым повышает частоту.

**Как исправить:**

- finite + разумный upper bound для `jet-speed`;
- отдельный `max-bombs-per-tick`, оставшиеся scheduled bombs переносить на следующие ticks и создавать в их рассчитанных along-track positions;
- общий burst budget на все одновременные sequences;
- переименовать настройку в `trail-interval-ticks` и оставить lower bound `>=1`; либо действительно определить density как particles/tick и ограничивать её сверху.

**Готово, когда:** экстремальный config не создаёт больше заданного числа entities за tick, а увеличение `trail-interval-ticks` никогда не повышает нагрузку.

## DELTA-TECH-008 — [P2/P3] Wall-clock скачок обрабатывается противоречиво

**Где:**

- 68 вызовов `System.currentTimeMillis()` в 37 production-файлах; `System.nanoTime()` не используется;
- Moto spawn cooldown: `MotorcycleManager.java:377–382,399–402`;
- Kamaz spawn cooldown: `TruckManager.java:296–302,309–312`;
- Airstrike: `AirstrikeManager.java:95–102`;
- Nuke: `NukeManager.java:130–137`;
- persisted WarKit fuel timestamp: `Weapons.java:142–152`, `SprayService.java:55–61`.

**Что именно не так:** при откате системного времени Moto видит отрицательный elapsed и из-за `elapsed >= 0` пропускает cooldown полностью. Kamaz, Airstrike и Nuke трактуют тот же отрицательный elapsed как дополнительное ожидание. Sprayer может заморозить regeneration при rollback либо мгновенно заполниться при forward jump. Это особенно реально после VM snapshot, ручной коррекции часов или крупной NTP correction.

**Как исправить:** явно разделить семантику времени. Server ticks либо monotonic `nanoTime` — для process-local arming, cooldown и immunity. Epoch millis — только для того, что обязано пережить restart. Для persisted elapsed определить одну rollback policy. Если `savedAt > now`, одного `max(0, now - savedAt)` недостаточно: такой clamp оставит full cooldown/нулевую regeneration, пока часы не догонят будущее значение. Нужно явно rebase/reset timestamp, хранить bounded remaining duration либо выбрать и документировать другой fail-open/fail-closed вариант. Максимально засчитываемый offline interval также должен быть ограничен. Внедряемый Clock позволит тестировать forward/backward jump без изменения системных часов.

**Готово, когда:** один и тот же rollback сценарий не даёт ни обхода, ни бесконечного lockout, а persisted и process-local timers имеют документированно разные clock types.

## DELTA-REL-001 — [P1] Документированный JAR не соответствует текущему source

**Доказательства:**

- `STATUS.md:3` и `CLAUDE_CODE_REVIEW.md:13` утверждают, что package завершён и `target/MilitaryCraft-1.0.0.jar` актуален;
- существующий JAR: 1 494 025 bytes, время `2026-07-18 16:17:01 +03:00`, SHA-256 `A34FBB3D2ADD5B52AF425A0C87718870BEB560AF998E357699CB657990E66A68`;
- hardening commits оформлены позже: `cae213a` 16:18, `de97177` 16:33, `cbb1d2c` 16:45, HEAD 16:46; само время commit не доказывает состав JAR, потому что часть ещё не закоммиченного camera/permission кода уже была собрана в него;
- в JAR отсутствует `me/bibo/militarycraft/core/util/CommandCoords.class`;
- bytecode Airstrike/Nuke всё ещё содержит прежний force-load path;
- `STATUS.md` говорит о 338 Java-файлах, тогда как текущий source содержит 339 вместе с tests.

**Риск:** администратор следует документации и разворачивает артефакт, в котором доказанно нет новых coordinate/chunk/train/TNT изменений. Permission checks и transient camera modifier в bytecode JAR уже присутствуют, поэтому состав нельзя выводить только из timestamp. Тем не менее Git source и production artifact всё равно расходятся по критичным scale-hardening paths.

**Как исправить:** немедленно считать этот JAR stale, собрать release из точного commit и проверять содержимое артефакта, а не наличие зелёной записи в Markdown. В manifest либо отдельном `/mc version` должны быть Git SHA и build timestamp; release-проверка сравнивает их с ожидаемым commit и проверяет наличие ключевых новых классов.

**Готово, когда:** deployed JAR сообщает `8e7883b` или более новый утверждённый SHA, его checksum зафиксирован вместе с этим SHA, а clean checkout создаёт тот же проверяемый набор классов.

## DELTA-API-001 — [P2, если существуют add-ons] Breaking Java API остался версией `1.0.0`

**Где:**

- `pom.xml:7–10,44` — координаты `me.bibo:militarycraft:1.0.0` и то же имя JAR;
- `MilitaryCraftPlugin.java:105–110` — наружу всё ещё выдаются `Core` и `ModuleManager`;
- в baseline `32d7fc7` публичными были `Core.models()`, `VehicleService.registerManager/unregisterManager`, `core.model.*`, `core.placeable.*`, `DisplayVehicle`, `VehicleManager`, `EntityIndex`; rewrite их удалил.

**Что именно не так:** внутренняя сборка зелёная, но binary compatibility с внешним plugin/add-on не проверяется. Add-on, собранный против прежнего `1.0.0` и вызывающий `plugin.core().models()`, на новом JAR той же версии получит `NoSuchMethodError`; ссылка на удалённый класс даст `NoClassDefFoundError`.

**Оговорка:** это не утверждение, что такой add-on уже есть. Проблема в том, что проект не объявляет, какие public packages являются поддерживаемым API, поэтому риск невозможно оценить и version `1.0.0` не сообщает о разрыве.

**Как исправить:** определить API boundary. Если всё внутреннее — не рекламировать `Core` как integration surface и документировать отсутствие стабильного Java API. Если add-ons поддерживаются — отдельный API artifact, semantic version bump, migration notes и binary-compatibility check между releases.

---

# 4. Новые визуальные, ресурсные и UX-находки

## Сначала положительный факт, который стоит сохранить

Текущие 38 runtime IDs `warkit:<id>` имеют ровно 38 соответствующих item definitions в source pack; пропущенных IDs нет. PDC остаётся authoritative identity предметов. Это хорошая база: следующие улучшения можно делать через namespaced models, не меняя gameplay identity и не ломая старые PDC tags.

## DELTA-VIS-001 — [P2] Optional pack и vanilla fallback существуют только в инструкции

**Где:**

- `gear/warkit/WarItems.java:331–334`
- `gear/warkit/weapon/Weapons.java:554–590`
- `weapons/airstrike/command/AirstrikeCommand.java:61–64`
- `resourcepack/warkit/HOW_TO_ENABLE.txt:5–8,65–75`

**Что именно не так:** Java безусловно устанавливает `item_model=warkit:<id>`. В config нет обещанного `resource-pack.models`, а в коде нет обработки принятия/отказа клиента. HOWTO одновременно обещает vanilla appearance без pack и требует несуществующий toggle. Клиент без definitions для namespaced model не получает гарантированный vanilla fallback.

**Как исправить:** выбрать честный режим продукта.

- **Required pack:** сервер раздаёт URL/hash, требует pack и до `SUCCESSFULLY_LOADED` не выдаёт visually critical items.
- **Optional pack:** server policy реально управляет назначением `item_model`; предмет без pack получает полноценное vanilla представление. Статус принятия отражается в понятном сообщении, а существующие items имеют reissue/migration path.

HOWTO должен описывать монолитный MilitaryCraft, фактические 38 IDs и реальный runtime contract.

## DELTA-VIS-002 — [P2] Центральный `ModelData` не используется, а placer items коллидируют

**Где:**

- `core/key/ModelData.java:4–6,13–54` обещает уникальный registry;
- `JetItem.java:20–21,40`, `TruckItem.java:20–21,42`, `PickupItem.java:31–32,42` используют одну пару `NETHERITE_SCRAP + 7342`;
- `AirshipItem.java:20–21,40` и `MotorcycleItem.java:18–19,41` используют `NETHERITE_SCRAP + 7351`;
- `ModelData.*` не имеет production consumers.

Registry заявляет Jet=7343, Pickup=7348, Airship=7345, Moto=7347, но live factories выдают другие значения. Helicopter, AntiAir и TCK тоже расходятся со своими constants. Для legacy material+CMD pack физически не может различить три предмета первой группы и два второй.

**Как исправить:** назначить каждому placer namespaced key, например `militarycraft:placer/jet`, через `setItemModel`; PDC оставить identity, integer CMD — только migration alias. Asset-contract test должен проверять уникальность legacy pair, существование каждого runtime model и отсутствие декларативного registry без consumers.

## DELTA-VIS-003 — [P2/P3] Bundled ZIP не является единым MilitaryCraft pack

**Факты:**

- в ZIP нет mappings для `netherite_scrap`, `paper`, `furnace_minecart`, `heavy_core`, `barrier`; соответствующие vehicle/Train/Artillery/Nuke placer items остаются vanilla;
- ZIP переопределяет `minecraft/items/armor_stand`, `blaze_rod`, `diamond_hoe`, `echo_shard`, `iron_helmet`, `leather_helmet`, `spyglass`;
- selectors там ждут string custom-model values `m60`, `awp`, `blackbox`, `helmet` и другие, но Java нигде не вызывает `CustomModelDataComponent#setStrings`;
- audio использует слишком общий namespace `custom`, а живые item models — `warkit`;
- ZIP имеет дополнительную модель `detonator`, которой нет в source pack и Java.

**Риск:** часть архива недостижима из MilitaryCraft, placer items не получают идентичность, а `minecraft` overrides плохо объединяются с packs других плагинов: последний override заменяет selector целиком.

**Как исправить:** один namespace `militarycraft`, namespaced `item_model` для всех живых items и отсутствие vanilla overrides там, где API 1.21.4 позволяет работать без них. Legacy AWP/M60/blackbox layer либо оформить отдельным compatibility sub-pack/module с реальным producer, либо не включать в основной release.

## DELTA-VIS-004 — [P2/P3] Custom item model не превращается в custom equipped silhouette

**Где:**

- `WarItems.java:134–278` — vest, helmet, exosuit, gas mask, pads, visor;
- `Weapons.java:371–388` — suicide vest;
- ZIP не содержит `assets/warkit/equipment/**` либо custom armor/equipment textures.

Item model работает в руке, inventory и ground-render, но не превращает надетую vanilla armor в отдельный gas-mask/exosuit silhouette. На персонаже остаётся окрашенная leather armor или vanilla netherite часть.

**Как улучшить:** авторовать equipment assets/layers для 1.21.4: отдельный силуэт gas mask/visor, единый kevlar+vest set, механические ноги exosuit и реальные pads. Там, где предмет имеет durability, предусмотреть worn/damaged state. Проверять вид от первого и третьего лица, armor combinations и dyed/team variants.

## DELTA-VIS-005 — [P3] Fullbright лишает технику ночной атмосферы и объёма

**Где:** representative hull paths используют `new Display.Brightness(15, 15)`:

- Tank `Tank.java:274`
- Jet `Jet.java:245`
- Helicopter `Helicopter.java:349`
- Airship `Airship.java:334`
- Drone `Drone.java:227`
- Kamaz `Truck.java:309`
- Pickup `Pickup.java:331`
- Moto `Motorcycle.java:282`
- Train `TrainCar.java:276`
- AntiAir `Turret.java:250`
- Artillery `ArtilleryModelManager.java:198–218`
- TCK Bus `TckBusRig.java:258`

**Почему это проблема:** обычный металл, дерево, стекло и резина одинаково self-lit в полдень, ночью и в пещере. Модель выглядит как яркий макет поверх мира, теряет объём и не позволяет использовать свет как состояние.

**Как улучшить:** hull получает world lighting. Fullbright остаётся только у семантически emissive элементов: headlights, navigation lights, radar screen, armed/reload indicator, engine glow, краткий muzzle flash. Для ночной читаемости использовать несколько маленьких emissive деталей, а не освещать весь корпус.

## DELTA-VIS-006 — [P3] Геометрия WarKit есть, собственной material language нет

**Факты:** все 38 source-моделей `assets/warkit/models/item/*.json` используют только `minecraft:block/*`; `assets/warkit/textures/**` отсутствует. В ZIP 39 моделей с detonator, но общая проблема та же. У всех проверенных моделей left-hand transforms скопированы с right-hand transforms.

**Почему это проблема:** coal/iron/concrete/diamond block textures дают разную texel density и ощущение «предмета из кубиков». Оружие, медицина, ткань и электроника не принадлежат одному art direction. Для асимметричного rifle/pistol/launcher одинаковые left/right transforms не являются полноценной калибровкой.

**Как улучшить:** compact atlas/palette: painted metal, bare metal, rubber, wood, cloth; общий olive/drab + graphite base; отдельные hazard accents для explosives/chemical, medical и electronics. Создать render QA matrix: GUI, ground, fixed, first/third person, main/offhand, left-handed client option. Держать контролируемую texel density и физический масштаб между всеми items.

## DELTA-AUDIO-001 — [P2/P3] Soundbank сломан семантически и полностью отсоединён

**Факты по `WarKit-ResourcePack.zip`:**

- `sounds.json` содержит 69 sound events;
- три отсутствующие ссылки: `svist` ожидает `svist.ogg`, но файл `svist..ogg`; namespace `cstom` вместо `custom` у `geroi...`; event `8u-bit...` не совпадает с файлом `8-bit...`;
- четыре OGG в итоге не зарегистрированы корректно, включая `camolet_ypal.ogg`;
- Java/config имеют ноль ссылок `custom:*` и используют vanilla `Sound` enum;
- у events нет subtitles;
- большинство spatial-кандидатов записаны stereo, из-за чего их нельзя качественно позиционировать как mono SFX.

При этом архив уже содержит сирену и fighter sounds, а Airstrike имитирует налёт через golem/bell/ender-dragon/blaze (`AirstrikeSequence.java:133–142,221–223`), Nuke — через bell/firework/wither/dragon (`NukeSequence.java:222–251,293–343`).

**Как улучшить:** semantic audio IDs в namespace `militarycraft`, например `warning.airstrike_siren`, `vehicle.jet.flyby`, `vehicle.drone.motor`, `weapon.rifle.fire`, `explosion.nuke.distant`. Исправить manifest; spatial SFX сделать mono, stereo оставить UI/radio/music; нормализовать loudness; длинному audio задать корректный streaming contract; добавить subtitles и vanilla fallback.

## DELTA-UX-001 — [P2] Actionbar не имеет владельца, slots и приоритета

**Где:** в проекте 209 прямых вызовов `sendActionBar` в 65 production-файлах. Постоянный HUD обычно пишет каждые четыре тика:

- Jet `FlightController.java:225–227,479–513`
- Helicopter `HelicopterController.java:100–101,263–298`
- Airship `AirshipController.java:100–101,250–283`
- Drone `DroneController.java:67–68,196–230`
- Tank `DriveController.java:90–95,342–353`

В тот же единственный client channel пишут WarKit channel progress каждые два тика (`ChannelManager.java:56–76`), Nuke warning каждые восемь (`NukeSequence.java:299–302`), Airstrike warning (`AirstrikeSequence.java:137–142`), radiation, reload, altitude, overheat и десятки one-shot errors.

**Реальный сценарий:** водитель лечит/ремонтирует через channel action. Progress и vehicle telemetry по очереди заменяют друг друга. Если рядом падает Nuke, следующий обычный HUD packet может стереть критическое предупреждение через 0,2 секунды. Результат зависит от scheduler order.

**Как исправить:** один `PlayerHudCoordinator` принимает logical slots: `base telemetry`, `weapon state`, `timed interaction`, `critical vehicle alert`, `global danger`. Он хранит priority+TTL и собирает ровно один output. Рекомендуемый порядок: global life danger > critical vehicle > interaction/error > weapon state > base telemetry. Нельзя исправлять это добавлением ещё одного независимого bossbar/actionbar writer.

## DELTA-UX-002 — [P3] FPV/cockpit assets лежат мёртвым грузом

**Факты:** ZIP содержит валидный `assets/minecraft/textures/fpvui.png` 256×256 и второй `assets/minecraft/textures/misc/fpvui.png`, который имеет JPEG signature `FF D8 FF E0` несмотря на `.png`. Ни один JSON не ссылается на них. В pack нет font definitions; Jet и Drone выводят только variable-width text actionbar.

**Как улучшить:** создать `assets/militarycraft/font/hud.json` с bitmap glyphs и подключить его через coordinated HUD layer. Наборы:

- Drone: reticle, battery, signal, warhead/rocket state;
- Jet: artificial horizon, speed/altitude ladder, weapon pips, overheat;
- Helicopter/Airship: vertical speed, hover/climb, heading;
- Pickup gunner/AntiAir: reticle, heat/reload/lock arc.

Обязателен text-only fallback. Raw `fpvui` без JSON либо превратить в atlas/glyph source, либо убрать из release source, чтобы не выдавать его за работающий UI.

## DELTA-UX-003 — [P2/P3] Большинство проверенных vehicle enter paths не объясняет управление

**Где:** успешный enter у Tank (`listeners/InteractionListener.java:37–48`), Jet (`:37–59`), Helicopter (`:36–61`), Airship (`:38–60`) и Pickup roles (`:53–96`) не сообщает controls. Подсказка находится в lore placer item, который после placement часто расходован. Игрок, севший в чужую или старую технику, lore никогда не видел.

Moto показывает хороший локальный контраст: `moto/listeners/InteractionListener.java:44–54` после успешного enter выдаёт role-specific hint.

**Как улучшить:** first-enter tutorial по ключу `module + role` и повторяемая `/mc controls <module>`. Driver, gunner и passenger получают разные 2–3 коротких шага. Completion отмечается после наблюдаемого действия, а не после таймера; повторный вход не спамит. Пока tutorial не закончен, coordinator показывает маленький help affordance.

## DELTA-UX-004 — [P3] Состояние оружия невидимо в hotbar и inventory

**Где:** `Weapons.java:123–152,554–590` хранит ammo/fuel/last-use в PDC, но всегда назначает один статический `warkit:<id>`. `GunService.java:95–113` показывает ammo только кратким actionbar после выстрела; Spray/Gadget работают аналогично.

**Как улучшить:** stateful item models: loaded, low, empty, reloading, hot, depleted/charging, safe/armed. Дискретные состояния меняют namespaced model; непрерывный остаток может использовать bar/durability component. PDC остаётся истиной, а visual component обновляется в той же операции, чтобы состояние не расходилось. Это одновременно разгрузит actionbar.

## DELTA-UX-005 — [P3] C4 имеет готовый detonator asset, но использует скрытый double-jump gesture

**Где:** `Weapons.java:405–413` описывает Shift + double jump; `ExplosivesManager.java:199–225` детонирует этим жестом все собственные charges. ZIP уже содержит `assets/warkit/items/detonator.json` и соответствующую модель, но Java ID/factory и source-pack files отсутствуют.

**Как улучшить:** сделать detonator явным tool: показать количество charges, right-click — выбранная group, sneak/right-click — смена channel, отдельные ready/no-signal/armed states. Legacy gesture можно оставить opt-in binding. Это использует уже созданный asset и делает механику обнаруживаемой.

## DELTA-UX-006 — [P2/P3] В мире нет общего IFF/state vocabulary

**Где:** AntiAir хранит owner (`Turret.java:53,96–100,164–167`), но render на `:241–258` выбирает только fixed materials. Sentry/Maxim geometry не использует owner/team. Mines/C4 показывают общий красный/жёлтый dust (`ExplosivesManager.java:393–425`), одинаковый для всех владельцев.

**Риск:** игрок не может до interaction или выстрела отличить allied/hostile turret, active/disabled, ready/empty, tracking/overheated. В насыщенной сцене это не косметика, а combat readability.

**Как улучшить:** небольшой state panel на существующей детали, без тяжёлой новой модели. Allied/hostile/neutral различать цветом и формой/символом; arming, tracking, firing, empty, overheated, disabled — устойчивыми patterns. Owner detail показывать при близком наведении. Одна семантика должна работать для AntiAir, Sentry, Maxim, mines, Artillery и vehicles.

---

# 5. Новое видение сверх исправления дефектов

Ниже — направления, которых не было в прежних двух ревью. Они не обязательны для parity-режима; это продуктовый слой, который можно включать постепенно.

## 5.1. Визуальные damage stages вместо одного процента HP

Сейчас отдельные модули уже дают smoke trail на низком HP, например Jet и Pickup, но язык не согласован. Я бы зафиксировал визуальные состояния, не обязательно общий base class:

1. **Healthy:** чистый корпус, нормальный звук, все lamps.
2. **Damaged:** редкий дым из конкретной зоны, одна повреждённая panel, неровный engine loop.
3. **Critical:** густой дым, sparks, flickering warning light, асимметрия rotor/engine, отчётливый звук отказа.
4. **Disabled/wreck:** staged wreck на короткое время, затем контролируемый cleanup/salvage representation.

Состояние меняется только при переходе threshold, а не перестраивает модель каждый tick. Игрок должен оценить состояние чужой техники без HUD.

## 5.2. World-space telegraphing опасных действий

Nuke/Airstrike уже имеют aircraft models, sounds и effects, поэтому проблема не в полном отсутствии world feedback. Не хватает единого понятного контракта, из которого игрок считывает radius, direction и time-to-impact. Для честного боя я бы добавил:

- impact ring/marker, видимый с воздуха и земли;
- направление подлёта и escalating siren;
- отдельный near/far sound mix;
- lock-on reticle и tone для целей AntiAir/Patriot;
- короткий owner/team-only arming indicator для mines и C4.

Задача не в большем количестве частиц, а в предсказуемой последовательности: detect → understand direction/radius → time to react → impact.

## 5.3. Art bible для всего MilitaryCraft

Нужен короткий документ для художника и разработчика: scale reference, palette, texel density, material library, silhouette rules, emissive policy, team accents, damage states и UI icon grid. Для каждого класса техники задать роль силуэта: recon, logistics, armor, air superiority, support. Это сохранит различия исходных портов, но уберёт ощущение случайного набора блоков.

## 5.4. Два продуктовых режима вместо принудительной экономики

Текущий MilitaryCraft — admin-issued sandbox: почти всё приходит через `give/place`, recipes/Vault/economy layer отсутствуют. Это нормальный базовый режим, его нельзя ломать. Поверх него можно сделать default-off **Operations/Logistics profile**:

- depots и ограниченные точки resupply;
- ammo/fuel/repair crates;
- salvage с wrecks;
- team budgets и стоимость вызова support;
- supply routes для Kamaz/Train/Airship;
- сценарные objectives, которые дают роль каждому из 16 modules.

Таким образом sandbox сохраняет parity, а длительная campaign появляется как отдельная policy/config profile, не размазанная по каждому module.

## 5.5. Role matrix и balance evidence

До изменения чисел составить матрицу `module → battlefield role → counters → resource cost → ideal crew → unique value`. Например, Drone — recon/precision, Kamaz/Train — logistics, AntiAir — area denial, Jet — air superiority, Artillery/Airstrike — delayed area strike. Затем записывать только gameplay outcome events: usage, hit/kill attribution, survival time, ammo efficiency, objective contribution. Это не общий ops-monitoring из старого отчёта, а данные именно для balance decisions.

---

# 6. Приоритетный план только по новым пунктам

## Этап A — до следующего production deploy

1. Считать текущий `target/MilitaryCraft-1.0.0.jar` непригодным к релизу; собрать и идентифицировать артефакт точного HEAD.
2. Закрыть `NaN → null → NPE`, отказаться от silent clamp и печатать фактическую точку.
3. Объединить ownership plugin chunk tickets для Train/Drone/Airstrike/Nuke.
4. Вернуть write-side crash marker для live pilot mounts либо честно убрать заявление о recovery до реализации.

**Exit criteria:** новый JAR содержит `CommandCoords.class`; опасные coordinate cases отказаны; interleaving ticket test зелёный; hard-crash visibility scenario описан и воспроизводим.

## Этап B — следующий stability pass

1. Остановить бесконечный Train poison tick и очистить failure registry.
2. Выполнить idempotent migration legacy camera modifier до amount fast-path.
3. Ввести per-tick Airstrike bomb budget и исправить семантику trail interval.
4. Разделить monotonic и persisted clock semantics.
5. Принять решение о Java API boundary и версии после удаления публичных classes.

## Этап C — честный client/resource contract

1. Выбрать required либо optional resource-pack mode.
2. Перевести все placer items на namespaced models и убрать live CMD collisions.
3. Разделить основной MilitaryCraft pack и недостижимый legacy layer.
4. Исправить sound manifest и подключить небольшой первый semantic set: warning, jet, rifle, nuke.
5. Ввести `PlayerHudCoordinator`; только после него добавлять cockpit glyph HUD.

## Этап D — визуальная подпись продукта

1. Equipment assets для mask/visor/vest/exosuit.
2. Material palette, transform QA matrix и отключение fullbright у hull.
3. Stateful weapon models и явный detonator.
4. Damage stages, world-space telegraphs и IFF/state panels.
5. First-enter role tutorials и `/mc controls`.

## Этап E — опциональное развитие gameplay

1. Art bible и role matrix.
2. Один небольшой Operations scenario как vertical slice.
3. Только после проверки slice — depots, supply, salvage и team budgets.

---

# 7. Новые проверочные сценарии

| Область | Минимальный сценарий приёмки |
|---|---|
| Coordinates | `NaN`, `Infinity`, `1e100`, border edge, invalid relative, valid near coordinate; ни одного exception/дальней генерации |
| Chunk ownership | все порядки acquire/release Train + Drone + Airstrike + Nuke на одном chunk |
| Pilot recovery | hard stop после mount для initially visible и initially invisible player |
| Train isolation | постоянный exception не вызывает `tick()` 20 раз/сек и не скрывает health state |
| Camera migration | legacy persistent modifier с тем же amount становится transient после первого apply |
| Airstrike budget | экстремальные speed/count дают не больше configured entities per tick |
| Clock | rollback/forward jump для Moto, Kamaz, Airstrike, Nuke и persisted sprayer |
| Artifact | JAR manifest SHA совпадает с release commit; key classes и bytecode paths присутствуют |
| Resource contract | runtime item IDs ↔ pack definitions; no missing sound; no unreachable required assets |
| HUD arbitration | vehicle + channel + Nuke одновременно; danger не исчезает, telemetry не flicker-ит |
| Visual QA | day/night/cave, first/third person, main/offhand, equipped armor, pack accepted/declined |
| Onboarding | новый driver/gunner/passenger получает только свою подсказку; повторный вход не спамит |

---

# 8. Что сознательно не повторено

В этом файле нет повторного списка прежних проблем с permissions/protection, terrain rollback, generic config validation, lifecycle/reload, persistence, multi-chunk entity recovery, combat attribution, LOD/display budgets, localization, CI, license/provenance, god classes и старой целевой архитектуры. Они остаются в предыдущих документах. Этот файл следует использовать как **добавочный backlog после них**, а не как их замену.

**Итог:** ближайшая новая цель — не очередной широкий rewrite. Сначала нужно сделать правдивыми четыре контракта, которые сейчас расходятся с реальностью: coordinate safety, chunk-ticket ownership, crash recovery и release artifact. После этого наибольший рост воспринимаемого качества дадут не дополнительные части моделей, а согласованные lighting, stateful items, semantic sound, cockpit HUD, IFF и понятное обучение.
