# D 模块 P1 存档字段映射对接文档（世界环境模块）

> 作者：D 模块（zsl）｜ 阶段：P1（v0.2.0-playable）｜ 编码：UTF-8
> 上位规则：《FSF游戏规则设计文档.md》V4.0（唯一数值事实源）
> 阶段红线：《FSF_P0-P4功能实现与验收规范.md》V4.0
> 对接方：E 模块（存档与持久化）

---

## 一、文档定位

本文档定义 **D 模块（世界环境）** 与 **E 模块（存档）** 在 P1 阶段的**存档字段映射**，
用于 P0 JSON 存档（`worldTimeTotalMinutes`）向 P1 SQLite（`world_state` 表）的迁移对接。

D 模块**不直接读写**任何存档文件 / SQL，仅通过 `FarmGameModel` 暴露 int / 枚举值，
由 E 模块负责序列化与落库（验收规范 §七十五：Service 不得直接写 SQL）。

---

## 二、字段映射总表

| # | P0 JSON 字段 | P1 SQLite 字段 | 类型 | D 模块提供方 | 说明 |
|---|---|---|---|---|---|
| 1 | `worldTimeTotalMinutes` | `world_state.current_world_time` | `INTEGER` | `FarmGameModel.getWorldTimeTotalMinutes()` | 从第 1 天 00:00 起累计游戏分钟数 |
| 2 | — | `world_state.current_weather` | `TEXT` | `FarmGameModel.getWeatherState().getWeatherType().name()` | 天气枚举名（`SUNNY`/`RAIN`/`DROUGHT`/`GREEN_RAIN`） |
| 3 | — | `world_state.current_day_index` | `INTEGER` | `FarmGameModel.getWeatherState().getDayIndex()` | 当前天气所属游戏日索引（从 1 开始） |
| 4 | — | `world_state.random_seed` | `INTEGER` | `RandomProvider`（E 侧保存种子） | 供 `RandomProvider.setSeed` 复现天气序列 |

> 依据：验收规范 §四十一（P0 世界时间存档）、§七十三（P1 `world_state` 字段）、规则文档 §九十（随机种子复现）。

---

## 三、字段语义与约束

### 3.1 `current_world_time`（世界时间）

- **来源**：`GameClock.getTotalMinutes()`（经 `FarmGameModel.getWorldTimeTotalMinutes()`）。
- **语义**：从第 1 天 00:00 起累计的游戏分钟数；`getGameDay() = totalMinutes / 1440 + 1`。
- **恢复**：E 模块读取后调用 `FarmGameModel.restoreWorldTime(int)` → `GameClock.setTotalMinutes(int)`。
- **校验**：`setTotalMinutes` 对 `totalMinutes < 0` 抛 `IllegalArgumentException`（P1 新增范围校验）。
- **依据**：验收规范 §四十一；规则文档 §5.1。

### 3.2 `current_weather`（当前天气）

- **来源**：`WeatherState.getWeatherType()`，以枚举 `name()` 存取。
- **取值**：`SUNNY` / `RAIN` / `DROUGHT` / `GREEN_RAIN`（**枚举名即存档字符串，禁止改动**）。
- **恢复**：E 模块读取后 `new BasicWeatherState(WeatherType.valueOf(str), dayIndex)`。
- **依据**：验收规范 §七十三；D 模块 P1 文档 §4.1（枚举常量集不改动，存档兼容）。

### 3.3 `current_day_index`（天气日索引）

- **来源**：`WeatherState.getDayIndex()`。
- **语义**：当前天气所属游戏日索引（从 1 开始）。
- **依据**：验收规范 §七十三；D 模块 P1 文档 §4.2。

### 3.4 `random_seed`（随机种子）

- **来源**：E 模块保存的种子值，供 `RandomProvider.setSeed(long)` 复现天气序列。
- **语义**：固定种子下 `rollDailyWeather` 序列可复现（规则文档 §九十）。
- **依据**：验收规范 §七十三；规则文档 §九十。

---

## 四、对接时序（P1）

```text
[保存]
  E 模块 SaveService
    → FarmGameModel.getWorldTimeTotalMinutes()  → world_state.current_world_time
    → FarmGameModel.getWeatherState().getWeatherType().name() → world_state.current_weather
    → FarmGameModel.getWeatherState().getDayIndex()           → world_state.current_day_index
    → RandomProvider 当前种子                                  → world_state.random_seed

[读取]
  E 模块 SaveService
    → FarmGameModel.restoreWorldTime(current_world_time)
    → new BasicWeatherState(WeatherType.valueOf(current_weather), current_day_index)
    → RandomProvider.setSeed(random_seed)
```

---

## 五、D 模块不负责项（边界）

| 不负责项 | 归属 | 依据 |
|---|---|---|
| SQLite 建表 / DAO / 迁移脚本 | E 模块 | 验收规范 §七十五 |
| JSON / SQL 序列化实现 | E 模块 | 验收规范 §七十五 |
| `world_state` 表结构定义 | E 模块 | 验收规范 §七十三 |
| 存档文件路径 / 自动保存时机 | E 模块 | 规则文档 §八十七、§八十八 |

---

## 六、溯源说明

| 关键项 | 来源文档 | 章节 |
|---|---|---|
| P0 世界时间存档字段 | 验收规范 | §四十一 |
| P1 `world_state` 字段（current_weather / current_day_index / random_seed） | 验收规范 | §七十三 |
| 随机种子复现 | 规则文档 | §九十 |
| 时间换算（1 现实分钟 = 1 游戏小时） | 规则文档 | §5.1 |
| Service 不得直接写 SQL | 验收规范 | §七十五 |
| 枚举名即存档字符串 | D 模块 P1 文档 | §4.1 |
