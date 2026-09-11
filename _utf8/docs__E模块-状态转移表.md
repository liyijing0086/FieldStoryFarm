# E 模块状态转移表（P0）

> 本文**逐行核对源码**生成，非设计设想图。依据：
> `manager/GameManager.java`、`manager/GamePhase.java`、`manager/SceneManager.java`、
> `persistence/JsonSaveService.java`、`model/PlotState.java`、`model/GameState.java`、
> `model/Player.java`、`view/MainApplication.java`、`controller/MainController.java`、
> `src/main/resources/.../main-view.fxml`，以及**全部测试用例**。
>
> | 项目 | 值 |
> |---|---|
> | 模块 | E —— 存档与引擎（P0） |
> | 真状态机数量 | **1 个**（`GamePhase` + `GameManager`）；其余为资源 / 流程状态 |
> | 测试基线 | `GameManagerTest` **8** + `JsonSaveServiceTest` **13** = **21** 个用例 |
> | 推断规则 | 表中每一行的"守卫/副作用/目标"都能指回具体源码行，未列设计意图 |

---

## 0. 代码中"有状态"的位置一览

| # | 状态载体 | 状态集合 | E 是否有转移逻辑 | 触发者 |
|---|---|---|---|---|
| ① | `GamePhase`（`manager/GamePhase.java`） | `MAIN_MENU` / `PLAYING` / `PAUSED` / `EXITING` | **有**（唯一真状态机，带守卫） | `GameManager`：`start` / `startNewGame` / `pause` / `resume` / `saveAndExit` |
| ② | 存档文件（`JsonSaveService`） | 不存在 / v1 旧档 / v2 当前 / 损坏 / 版本不兼容 | **有**（版本判定 + 损坏降级） | `save()` / `load()` |
| ③ | 场景装配（`SceneManager` + `Slot`） | 未装配 / 已装配（5 槽挂载态） | 弱（挂载 / 替换 / 卸载，**无守卫**） | `assemble()` / `mount()` / `unmount()` |
| ④ | 土地与作物（`PlotState.state` / `growthStage`） | **自由字符串**，E 不定义枚举 | **无**（只原样存/取） | A 模块适配层（未来接入） |

> **一句话结论**：E 模块真正的状态机**只有 ①**。②③ 是资源与流程状态，④ 是纯数据快照——**不要在 E 里为土地状态写转移逻辑**，那是 A 的职责。

---

## 1. 应用生命周期状态机（`GamePhase` + `GameManager`）

### 1.1 状态集合

| 状态 | 含义 | 进入方式 |
|---|---|---|
| `MAIN_MENU` | 主菜单，尚未开始 | 构造 `GameManager` 时初始化（`GameManager.java:50`） |
| `PLAYING` | 游戏中 | `start()` / `startNewGame()` / `resume()` |
| `PAUSED` | 暂停 | `pause()` |
| `EXITING` | 已退出（已保存并关闭） | `saveAndExit()` |

> `phase` **不落盘**：重启后新管理器必然回到 `MAIN_MENU`（测试 `saveAndExitPersistsAndRestartRestores` 断言）。这是"退出后不推进世界"的实现方式，不是缺陷。

### 1.2 合法状态转移表

| # | 源状态 | 触发 | 守卫（代码强制） | 副作用 | 目标 |
|---|---|---|---|---|---|
| T1 | —（新建） | `new GameManager(saveService)` | `saveService != null`，否则 NPE | `phase = MAIN_MENU` | `MAIN_MENU` |
| T2 | `MAIN_MENU` | `start()` | 无阶段守卫；仅当 `state == null` 才读档/新档 | 有档→`load()`；无档或 `load()` 抛出→`newGame()`（金币 500 / 天数 0）；`phase = PLAYING` | `PLAYING` |
| T3 | `PLAYING` | `start()` | 无。对 `state` **幂等** | `state != null` → 不再读档；仅 `phase = PLAYING`（不变） | `PLAYING` |
| T4 | `PAUSED` | `start()` | 无。对 `state` **幂等** | 仅 `phase = PLAYING`——**绕过 `resume()` 的守卫** | `PLAYING` |
| T5 | `EXITING` | `start()` | 无。**`EXITING` 不是代码强制的终态** | 仅 `phase = PLAYING` | `PLAYING` |
| T6 | 任意 | `startNewGame()` | 无 | 强制 `state = newGame()`，不读档、不写盘；`phase = PLAYING` | `PLAYING` |
| T7 | `PLAYING` | `pause()` | `requirePhase(PLAYING)` | `phase = PAUSED` | `PAUSED` |
| T8 | `PAUSED` | `resume()` | `requirePhase(PAUSED)` | `phase = PLAYING` | `PLAYING` |
| T9 | `PLAYING` | `saveAndExit()` | `state != null && phase ∈ {PLAYING, PAUSED}` | `saveService.save(state)` → `phase = EXITING` | `EXITING` |
| T10 | `PAUSED` | `saveAndExit()` | 同上 | 同上 | `EXITING` |
| T11 | `MAIN_MENU` | `saveAndExit()` | `state == null` → **跳过写盘** | 仅 `phase = EXITING`（**不产生存档**） | `EXITING` |
| T12 | `EXITING` | `saveAndExit()` | phase 不在 {PLAYING, PAUSED} → 跳过写盘 | 幂等，阶段不变 | `EXITING` |
| T13 | 任意 | `saveNow()` | `state != null`，否则抛异常 | `saveService.save(state)`；**不改阶段**（自环） | 不变 |

**只读方法（不产生转移）**：`currentPhase()`、`hasSavedGame()`、`currentState()`（`state == null` 时抛异常）。

### 1.3 非法转移表（抛 `IllegalStateException`，**阶段保持不变**）

| 方法 | 允许的源状态 | 在其他状态的行为 |
|---|---|---|
| `pause()` | 仅 `PLAYING` | 抛 `仅游戏中可暂停（当前阶段: X）` |
| `resume()` | 仅 `PAUSED` | 抛 `仅暂停中可恢复（当前阶段: X）` |
| `currentState()` | 需 `state != null`（已 `start`） | 抛 `游戏尚未启动，请先调用 start()` |
| `saveNow()` | 需 `state != null` | 抛 `游戏尚未启动，无法保存` |

> 注意 `saveAndExit()` **不在**本表——它**没有守卫**，任何阶段调用都合法（见 T11 / T12）。

### 1.4 状态 × 事件 完整矩阵

行 = 当前状态，列 = 触发方法；格内为**结果状态**，`X` = 抛异常。

| 当前 \ 触发 | `start()` | `startNewGame()` | `pause()` | `resume()` | `saveNow()` | `saveAndExit()` |
|---|---|---|---|---|---|---|
| `MAIN_MENU` | `PLAYING` | `PLAYING` | **X** | **X** | `MAIN_MENU`¹ | `EXITING`² |
| `PLAYING` | `PLAYING` | `PLAYING` | `PAUSED` | **X** | `PLAYING` | `EXITING`³ |
| `PAUSED` | `PLAYING` | `PLAYING` | **X** | `PLAYING` | `PAUSED` | `EXITING`³ |
| `EXITING` | `PLAYING` | `PLAYING` | **X** | **X** | `EXITING` | `EXITING`⁴ |

脚注：

1. `state == null` 时 `saveNow()` 抛 `IllegalStateException`；若已 `start()` 过则落盘但阶段不变。
2. **不写盘**（`state == null`），仅置 `EXITING`。
3. **先 `save(state)` 再置 `EXITING`**。
4. **不写盘**，幂等。

### 1.5 三个必须知道的关键结论

1. **`start()` 没有阶段守卫** → `EXITING` 可被重新拉回 `PLAYING`（T5）；`PAUSED → start()` 也成立（T4），绕过 `resume()` 守卫。若要让 `EXITING` 成为严格终态，需给 `start()` 加 `requirePhase(MAIN_MENU)`。
2. **`start()` 对 `state` 幂等，对阶段不幂等** → 只要 `state != null` 就不再读档。**同进程内"回主菜单再读档"不生效**；需多档位/多周目时，得重启进程或新增显式重置方法（如 `resetForNewSession()`）。
3. **`PAUSED` 目前无 UI 入口** → `main-view.fxml` 只有两个按钮：「开始游戏」`#onStartButtonClick`、「保存进度」`#onSaveButtonClick`。`pause()` / `resume()` 只有测试能触发（`pauseResumeFollowStateMachine`）。状态机本身完整，接 UI 即可用。

---

## 2. 存档文件状态（`JsonSaveService`）

### 2.1 状态集合

| 状态 | 判定条件（代码） |
|---|---|
| `NOT_EXIST` | `Files.isRegularFile(saveFile) == false` → `hasSave()` 为 false |
| `V1_LEGACY` | 文件可解析且 `version == 1`（`MIN_SUPPORTED_VERSION`） |
| `V2_CURRENT` | 文件可解析且 `version == 2`（`SAVE_VERSION`） |
| `CORRUPT` | JSON 解析失败 / IO 失败 / 根不是 JSON 对象 |
| `INCOMPATIBLE` | `version < 1` 或 `version > 2` 或缺失（`asInt(-1)` → -1） |

### 2.2 转移表

| # | 源状态 | 操作 | 条件 | 副作用 / 结果 | 目标 |
|---|---|---|---|---|---|
| S1 | `NOT_EXIST` | `load()` | — | 返回 `null`（**不抛异常**） | `NOT_EXIST` |
| S2 | `NOT_EXIST` | `save(state)` | `state != null` | `createDirectories` → 写 UTF-8 美化 JSON（`version = 2`） | `V2_CURRENT` |
| S3 | `NOT_EXIST` | `save(null)` | — | 抛 `IllegalArgumentException("GameState 不能为空")` | `NOT_EXIST` |
| S4 | `V2_CURRENT` | `load()` | `version ∈ [1, 2]` | 返回 `GameState`（**只读，不改文件**） | `V2_CURRENT` |
| S5 | `V2_CURRENT` | `save(state)` | — | 覆写 | `V2_CURRENT`（自环） |
| S6 | `V1_LEGACY` | `load()` | `version == 1` | 返回 `GameState`；**`seedInventory` 按空库存处理**（v1 无该字段） | `V1_LEGACY` |
| S7 | `V1_LEGACY` | `save(state)` | — | 覆写为 `version = 2`（读旧写新，**原文件不被就地改结构**） | `V2_CURRENT` |
| S8 | `CORRUPT` | `load()` | JSON 解析 / IO 失败 | 抛 `IllegalStateException("存档文件损坏或无法读取: <绝对路径>")` | `CORRUPT` |
| S9 | `CORRUPT` | `load()` | 解析成功但根非 JSON 对象 | 抛 `IllegalStateException("存档文件为空或不是 JSON 对象")` | `CORRUPT` |
| S10 | `INCOMPATIBLE` | `load()` | `version < 1` 或 `> 2` 或缺失 | 抛 `IllegalStateException("存档版本不兼容: 支持 version=1~2，实际 version=X")` | `INCOMPATIBLE` |
| S11 | `CORRUPT` / `INCOMPATIBLE` | `save(state)` | — | 覆写为新档（**故障自愈**） | `V2_CURRENT` |
| S12 | `V2_CURRENT` | 外部破坏 / IO 失败 | — | 文件被改坏 | `CORRUPT` |
| S13 | `V2_CURRENT` | `version` 被改为 <1 或 >2 | — | 版本越界 | `INCOMPATIBLE` |

### 2.3 版本常量（升级结构的唯一入口）

| 常量 | 值 | 含义 |
|---|---|---|
| `JsonSaveService.SAVE_VERSION` | **2** | 当前结构版本；**升级结构必须递增并同时写兼容分支** |
| `JsonSaveService.MIN_SUPPORTED_VERSION` | **1** | 低于此版本的旧档直接拒绝 |
| `JsonSaveService.SCHEMA` | `P0-json` | 格式标识，区分 P1 的 SQLite 正式存档 |
| `JsonSaveService.DEFAULT_SAVE_FILE` | `data/save.json` | 默认路径（`data/` 已被 `.gitignore` 忽略） |

### 2.4 降级路径（`GameManager.start()` 内，加载失败**绝不让启动崩溃**）

| 情形 | 行为 | 是否覆写坏档 |
|---|---|---|
| `hasSave() == false` | `state = newGame()` | — |
| `load()` 抛 `IllegalStateException`（损坏/版本不符） | `System.err` 告警 → `state = null` → `newGame()` | **否**（坏档保留在磁盘） |
| `load()` 成功 | 恢复到退出瞬间状态，**不做任何离线成长计算** | 否 |

> 降级要点：只有下一次 `saveNow()` / `saveAndExit()` 才会覆盖旧档。

---

## 3. 场景装配状态（`SceneManager` + `Slot`）

### 3.1 状态集合

| 状态 | 判定 |
|---|---|
| `UNASSEMBLED` | `root == null`（尚未 `assemble`） |
| `ASSEMBLED` | `root != null`，5 个槽位可挂载 |

### 3.2 转移表

| # | 源状态 | 触发 | 守卫 | 副作用 | 目标 |
|---|---|---|---|---|---|
| C1 | —（新建） | `getInstance()` | — | `root = null`，`mounted` 为空 | `UNASSEMBLED` |
| C2 | `UNASSEMBLED` | `assemble(centerNode, w, h)` | — | `root = new BorderPane()`；`mounted.clear()`；`mount(CENTER, centerNode)` | `ASSEMBLED` |
| C3 | `UNASSEMBLED` | `mount(slot, node)` | **无** | 只更新 `mounted` 映射，**不上屏**（`apply()` 因 `root == null` 直接返回） | `UNASSEMBLED` |
| C4 | `UNASSEMBLED` | `unmount(slot)` | **无** | 从 `mounted` 移除，返回旧节点；不上屏 | `UNASSEMBLED` |
| C5 | `ASSEMBLED` | `mount(slot, node)` | 无 | 同槽位**替换**，`setTop/Center/Left/Right/Bottom` | `ASSEMBLED` |
| C6 | `ASSEMBLED` | `unmount(slot)` | 无 | 该槽位置空，返回被移除节点 | `ASSEMBLED` |
| C7 | `ASSEMBLED` | 再次 `assemble(...)` | 无 | 重建 `root` 并**清空全部已挂槽位** | `ASSEMBLED` |

**槽位归属（P0 现状）**

| Slot | 代码注释约定 | P0 现状 |
|---|---|---|
| `TOP` | D 状态栏等 | 空 |
| `CENTER` | A 土地模块、主菜单等 | **已挂 `main-view.fxml`（主菜单）** |
| `LEFT` | —（未指定） | 空 |
| `RIGHT` | B 商店等 | 空 |
| `BOTTOM` | —（未指定） | 空 |

> **装配顺序陷阱（C3）**：`root == null` 时 `mount/unmount` **不报错**，只改映射不上屏；随后 `assemble()` 会先 `mounted.clear()` → **装配前挂的组件被静默丢弃**。请一律「先 `assemble`，再 `mount`」。

---

## 4. 土地 / 作物状态：E 只存不转（无转移表）

E 模块**不定义也不转移**土地状态。`PlotState.state` 与 `crop.growthStage` 都是**自由字符串**，E 只做原样序列化/反序列化（`JsonSaveService.toPlotNode` / `toPlotState`）。

| 字段 | 载体 | E 的行为 | 状态取值（源码中出现的） |
|---|---|---|---|
| `plots[].state` | `PlotState.state`（String） | 原样存/取，**不校验** | `EMPTY`（测试 `roundTripPreservesPlayerDayUnlockedAndPlots`）、`TILLED`（`saveAndExitPersistsAndRestartRestores`）、`GROWING`（同前） |
| `plots[].crop.growthStage` | `PlotState.growthStage`（String） | 原样存/取，**不校验** | `SPROUT`（同前） |
| `plots[].crop.growthProgress` | `double` | 原样存/取 | `0.0 ~ 1.0`（契约区间） |

> **口径未冻结**：状态是自由字符串，E 不持有 A 的枚举（避免跨模块类型依赖）。A 交付枚举后，**必须由适配层固定「枚举 ↔ 字符串」映射**，否则存档会出现同义不同字（如 `EMPTY` / `空闲`）。**这是接入期最需要先对齐的一项。**

---

## 5. 状态 × 测试覆盖（共 21 个用例）

### 5.1 生命周期状态机（`GameManagerTest`，8）

| 测试 | 覆盖的转移 |
|---|---|
| `getInstanceReturnsSameSingleton` | 单例 |
| `freshManagerStaysInMainMenuUntilStarted` | T1；未 `start` 时 `currentState()` 抛异常 |
| `startWithoutSaveCreatesNewGameWithInitialGold` | T2（无档分支） |
| `staticNewGameProvidesInitialState` | `newGame()` 初始值 |
| `pauseResumeFollowStateMachine` | T7 / T8 / T10；非法转移（`MAIN_MENU` 暂停、重复暂停、`PLAYING` 恢复） |
| `saveAndExitPersistsAndRestartRestores` | T9；重启回 `MAIN_MENU`；恢复到退出瞬间（金币 321 / 天数 9 / 1 块地 `TILLED`） |
| `corruptSaveFallsBackToNewGameOnStart` | §2.4 损坏降级路径 |
| `saveNowBeforeStartThrows` | T13 守卫 |

### 5.2 存档文件状态（`JsonSaveServiceTest`，13）

| 测试 | 覆盖的状态/转移 |
|---|---|
| `loadReturnsNullWhenNoSaveExists` | S1（`NOT_EXIST` + `load()`） |
| `saveCreatesFileWithVersionAndSchema` | S2；落盘 `version=2` / `schema` / `gameDay` / `player` / `seedInventory` |
| `saveCreatesParentDirectories` | S2 的自动建目录 |
| `roundTripPreservesPlayerDayUnlockedAndPlots` | S5 + 全字段往返 + `plotId` 空值补 `row,column` |
| `roundTripOfEmptyState` | S5 空快照往返 |
| `saveRejectsNullState` | S3 参数守卫 |
| `seedInventoryRoundTripPreservesCounts` | S5（种子库存往返） |
| `playerWithoutSeedsRoundTripsAsEmptyInventory` | S5（无种子 → 空库存） |
| `loadLegacyVersion1WithoutSeedInventoryStillLoads` | **S6**（`V1_LEGACY` 兼容读入） |
| `loadEarlyV2WithRootLevelSeedInventoryFallsBack` | 读入回退（根节点 `seedInventory` → `player.seedInventory`） |
| `loadSkipsUnknownCropTypeName` | 读入时未知作物名跳过，不崩溃（前向兼容） |
| `loadCorruptFileThrows` | **S8**（`CORRUPT` 抛异常） |
| `loadUnsupportedVersionThrows` | **S10**（`INCOMPATIBLE`，`version=3`） |

### 5.3 尚未被测试覆盖的转移（诚实列出）

| 转移 | 说明 |
|---|---|
| T3 / T4 / T5（`start()` 从播放/暂停/退出态） | 逻辑可达，但**无用例**直接断言"`EXITING` 后可被 `start()` 拉回" |
| T6（`startNewGame()` 覆盖已有会话） | 无独立用例 |
| T11 / T12（`saveAndExit()` 在 `MAIN_MENU` / `EXITING`） | 用例覆盖到 `PLAYING`/`PAUSED` 分支（T9/T10），`MAIN_MENU` 跳过写盘未直接断言 |
| C1~C7（`SceneManager` 全部） | **无任何用例**（JavaFX 未纳入单测） |

---

## 附录：常量与类对照

| 符号 | 位置 | 说明 |
|---|---|---|
| `GamePhase` | `manager/GamePhase.java` | `MAIN_MENU` / `PLAYING` / `PAUSED` / `EXITING` |
| `GameManager` | `manager/GameManager.java` | 单例；`INITIAL_GOLD = 500`（`= GameConstants.INITIAL_GOLD`）、默认玩家名「农夫」 |
| `SceneManager` | `manager/SceneManager.java` | 单例；`Slot` 五区；`assemble` / `mount` / `unmount` / `root` |
| `JsonSaveService` | `persistence/JsonSaveService.java` | `SAVE_VERSION=2`、`MIN_SUPPORTED_VERSION=1`、`SCHEMA="P0-json"` |
| `PlotState` | `model/PlotState.java` | `plotId` / `row` / `column` / `state` + 作物字段（全为字符串快照） |
| `MainApplication` | `view/MainApplication.java` | `stop()` → `GameManager.getInstance().saveAndExit()`（退出自动存档） |
| `MainController` | `controller/MainController.java` | 「开始游戏」`onStartButtonClick` → `start()`；「保存进度」`onSaveButtonClick` → `saveNow()` |
