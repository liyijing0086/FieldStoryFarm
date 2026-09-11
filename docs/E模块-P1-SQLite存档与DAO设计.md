# E 模块 P1 设计文档 · SQLite 正式存档 + 6 DAO + 迁移

> 归属：E（存档与收集模块） P1 任务「SQLite + 6 DAO + 迁移」
> 依据：《FSF_P0-P4 功能实现与验收规范》§七十一~§七十五、§七十八、§七十九、§一百四十四、§一百五十一；《脚手架》§3.1/§5.1/§7；《模块分工》E 行 P1。

## 1. 目标

1. 把存档从 P0 的「单纯 JSON 文件」升级为 **SQLite 本地数据库**（`data/farm.db`），P1 起 **SQLite 是唯一正式运行存档**（§七十一）。
2. 交付 **6 个 DAO** 封装数据库增删改查，业务层不得直接写 SQL（§七十五）。
3. 实现 **两条迁移线**：旧 P0 JSON → SQLite 的**数据迁移**（§七十四），以及基于 `PRAGMA user_version` 的**结构版本迁移**，保证后续版本升级时旧存档可原地升级、**不丢玩家农场数据**。

## 2. 架构与文件

```
View → Controller → Service → DAO → Persistence
```

| 文件 | 层 | 职责 |
|---|---|---|
| `persistence/DatabaseService.java` | Persistence | 数据库位置、连接获取、连接级 PRAGMA、meta 键值 |
| `persistence/SchemaMigrator.java` | Persistence | 按 `user_version` 的版本化结构迁移 |
| `persistence/SqliteSaveService.java` | Persistence | 实现 `SaveService`：GameState ↔ 表 双向映射 + JSON 一次性迁移 |
| `persistence/FarmStateAdapter.java` | Persistence | `GameState.plots` ↔ A 模块 `Farm/Soil/Crop` 双向同步（D3：退出保存 / 进入读档的农场闭环） |
| `persistence/dao/PlayerDao.java` | DAO | player / player_seed / unlocked |
| `persistence/dao/FarmDao.java` | DAO | farm（地图尺寸） |
| `persistence/dao/SoilDao.java` | DAO | soil（土地坐标与状态） |
| `persistence/dao/CropDao.java` | DAO | crop（作物成长数据） |
| `persistence/dao/DecorationDao.java` | DAO | decoration |
| `persistence/dao/WorldStateDao.java` | DAO | world_state |
| `model/DecorationState.java` | Model | E 侧装饰存档快照（与 `PlotState` 同类，字符串、无业务） |

对外接口不变：业务层只依赖 `service/SaveService`（`hasSave/save/load`）。`GameManager.getInstance()` 默认实现由 `JsonSaveService` 切换为 `SqliteSaveService`；`JsonSaveService` 保留，仅作迁移来源。

依赖：`org.xerial:sqlite-jdbc:3.46.1.3`（JPMS 模块名 `org.xerial.sqlitejdbc`，其 `requires org.slf4j` 故显式引入 `org.slf4j:slf4j-api:1.7.36`）；`module-info.java` 增加 `requires java.sql; requires org.xerial.sqlitejdbc;`。

## 3. 数据库表

单人存档：单行表固定 `id = 1`。

| 表 | 关键列 | 来源（§） |
|---|---|---|
| `player` | `id, name, gold` | §七十二 player |
| `player_seed` | `crop_type, quantity` | 种子库存（B §6.2 唯一种子库存） |
| `unlocked` | `unlocked_key` | 已解锁内容 |
| `farm` | `map_rows, map_cols` | §七十二 farm |
| `soil` | `soil_id, plot_id, row_index, col_index, state` | §七十二 soil |
| `crop` | `crop_uuid, soil_id→soil, crop_type, growth_stage, growth_progress, plant_world_time, manual_water_count, last_manual_water_game_day` | §七十二 crop |
| `decoration` | `id, decoration_type, row_index, col_index` | §七十二 decoration |
| `world_state` | `current_world_time, last_real_time, current_weather, current_day_index, random_seed` | §七十二/§七十三 |
| `meta` | `meta_key, meta_value` | E 内部（迁移标记等） |

约束：`crop.soil_id` 外键引用 `soil(soil_id) ON DELETE CASCADE`；`soil_id = 行 × 12 + 列`（与 A 模块 `BasicSoil` 口径一致）。为兼容 JSON 中可能缺失的字段，`player.name`、`soil.state`、`crop.crop_type` 允许 NULL。

`world_state` 五列按 §七十三 **提前建好**，P1 仅写 `current_world_time`/`current_day_index`，其余写 NULL，P2 离线/天气接入无需改结构。

## 4. DAO 接口（CRUD）

DAO 构造注入 `java.sql.Connection`，**事务边界由 `SqliteSaveService` 控制**（一次存档 = 一个事务）。

| DAO | 方法 |
|---|---|
| `PlayerDao` | `insert/update/find/exists/deleteAll`；`replaceSeeds/findSeeds/deleteAllSeeds`；`replaceUnlocked/findUnlocked/deleteAllUnlocked` |
| `FarmDao` | `insert/update/findMapSize/deleteAll` |
| `SoilDao` | `insert/updateState/findById/findAll/count/deleteAll` |
| `CropDao` | `insert/update/findById/findBySoilId/findAll/count/deleteAll` |
| `DecorationDao` | `insert/update/findById/findAll/count/deleteAll`（未指定 id 时回填自增主键） |
| `WorldStateDao` | `insert/update/find/deleteAll` |

## 5. GameState ↔ 表 映射

- **保存**：单事务内「清空相关表 → 全量写入」，失败整体回滚（不留半截存档）。
  `player/player_seed/unlocked` ← `Player` + `GameState.unlocked`；`farm` ← 12×12；
  `soil` ← 每个 `PlotState` 的坐标与状态；`crop` ← 有作物的 `PlotState`；
  `decoration` ← `GameState.decorations`；`world_state` ← `gameDay`/`currentWorldTime`。
- **读取**：`soil` 左连 `crop` 还原 `GameState.plots`（按 `soil_id` 升序，顺序稳定）；
  无 `player` 行返回 `null`（视为无档）；只恢复退出瞬间状态，**不做任何离线成长**（离线属 P2）。

## 6. 迁移

### 6.1 结构版本迁移（`SchemaMigrator`）

- 以 `PRAGMA user_version` 记录结构版本；`SchemaMigrator.SCHEMA_VERSION = 1`。
- 启动打开连接时逐级执行「版本 > 当前」的迁移步骤（每步为增量 DDL + 版本号递增），
  已是最新则空操作，重复调用幂等；**不重建已有表**，数据保留。
- 库版本高于程序支持版本时抛错拒绝打开，避免旧程序写坏新结构。
- **后续版本扩结构**：新增 `MigrationStep`（如 v1→v2 `ALTER TABLE ... ADD COLUMN`）并把 `SCHEMA_VERSION` +1 即可，旧档自动升级。

### 6.2 数据迁移（JSON → SQLite，§七十四）

首次读写时执行一次（`SqliteSaveService`）：

```
库无玩家数据 且 旧 data/save.json 存在 → 读 JSON → 一次性导入 SQLite → 校验 → 写 meta.json_migration_done 标记
之后 → 只读 SQLite
```

- 库已有玩家数据 → 只补标记，不覆盖；
- 无旧 JSON → 写 `json_migration_done=none`，避免每次启动重复探测；
- 旧 JSON 损坏 → **不写标记**，保留修复后重试的机会，且不阻断启动（降级为无档 → 新游戏）；
- **禁止 JSON + SQLite 双写**：迁移后不再写 JSON（`data/save.json` 时间戳不变），避免出现两个“真存档”。

## 7. 测试

| 测试 | 覆盖 |
|---|---|
| `persistence/SQLiteDaoTest`（12） | 结构迁移建表、6 个 DAO 的 CRUD、外键级联、`SqliteSaveService` 整档往返、「关闭重开数据正确」、全量替换、可空字段 |
| `persistence/MigrationTest`（6） | 旧 JSON 一次性导入 + 标记、不覆盖已有 SQLite、无/损坏 JSON 处理、低版本结构升级不丢数据、拒绝未来版本 |
| `persistence/FarmStateAdapterTest`（7） | 整场快照生成、地块/作物采集↔还原无损往返、还原幂等与整体重置、坏数据（未知枚举/非法 UUID/越界坐标）不丢作物不抛错 |
| `persistence/FarmPersistenceIntegrationTest`（2） | 验收标准 4 端到端：退出自动保存 → 用同一库重启 → 还原农场；含用真实 A/B 服务（开垦/播种）驱动的流程 |
| `manager/GameManagerTest`（+3） | 存档前回填钩子在 `saveNow`/`saveAndExit` 均触发、无档不触发、`hasSave` 抛错时降级新游戏 |

全量：`mvnw -B test`（218 项，含 P0/P1 既有 206 项）。

## 8. 运行与验证

- 数据库文件：`data/farm.db`（相对运行目录）；`data/`、`*.db` 已在 `.gitignore` 忽略，禁止放入 `resources`（§一百四十四）。
- 实机验证：删除 `data/farm.db` 后 `mvnw javafx:run` → 点「开始游戏」→ 自动迁移（日志 `已从旧 JSON 存档迁移玩家数据`）→ `farm.db` 生成，内容与 `save.json` 一致；关闭窗口自动存档，`save.json` 时间戳不变。
- 实机验证（验收标准 4 农场闭环）：点「开始游戏」后关闭窗口 → `soil` 表 64 行、
  `world_state.current_day_index` 为当前游戏日；再用一份含「已开垦/已播种地块 + 作物」的库重开应用，
  关闭后该地块与作物字段仍在（证明进入读档已还原到运行农场，退出回填又原样写回）。

## 9. 已知限制 / 后续

- `decoration` 表与 `DecorationDao` 已就绪并有 CRUD，但 P1 装饰系统（B 模块）尚未交付，`GameState.decorations` 暂为空；B 侧接入后由适配层填充。
- `world_state.current_weather/random_seed/last_real_time` 待 D 模块天气/时钟接入后写入（列已就位）。
- **`GameState.plots` 与 A 模块 `Farm` 的同步（D3）已落地**：`persistence/FarmStateAdapter`
  负责双向映射（枚举 ↔ 名称、`long` 时间 ↔ 十进制字符串，坏数据降级不丢作物）。
  `MainController` 开始游戏时 `restore`，并通过 `GameManager.setBeforeSaveHook` 在
  `saveNow`/`saveAndExit` 落盘前 `capture`，因此「退出自动保存 / 进入读档」对地块与作物同样生效。
  游戏天数一并由该钩子写回 `world_state.current_day_index`，读档时经
  `FarmGameModel.restoreWorldTime` 还原；P1 不持久化当天时刻，恢复后按当日 06:00 起算（离线属 P2）。
- 日志：sqlite-jdbc 在无 SLF4J 绑定时会打印一行 NOP 提示（不影响功能）。
