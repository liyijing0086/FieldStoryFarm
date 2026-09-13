# D 模块 P2 跨模块接口约定文档（随机事件系统）

> 版本：P2 · v0.3.0-feature
> 负责人：D（zsl）· 世界环境模块
> 依据：《游戏规则设计文档》§四十七~§五十一、《FSF_P0-P4功能实现与验收规范》§八十九/§九十/§九十一/§九十二、《模块分工.md》第 7 行
> 约束：D 模块**不修改** A/C/B/E 模块的类，本文件仅提出接口约定，需其他模块修改的部分以本文档形式提出。

---

## 一、D 模块 P2 对外接口清单（只读）

| 接口 | 方法 | 说明 |
|---|---|---|
| `EventService` | `EventType rollDailyEvent(int dayIndex)` | 每日 00:00 抽取事件（写状态） |
| `EventService` | `boolean isEventActive(long currentWorldTime)` | 事件是否持续中 |
| `EventService` | `void expireIfNeeded(long currentWorldTime)` | 到期关闭事件 |
| `EventService` | `String getDisplayName(EventType)` / `String getIcon(EventType)` | 显示信息 |
| `EventService` | `boolean isMeteorShower/isMysteryMerchant/isRainbowDay(EventType)` | 事件判定 |
| `EventState` | `getEventType/getStartWorldTime/getEndWorldTime/getTargetCropType/getPayload` | 事件状态读取 |
| `FarmGameModel` | `getEventService()` / `getEventState()` | 聚合只读入口 |

**世界时间口径（决策 D14）**：世界时间 = `getGameDay() * 24 + getGameHour()`（游戏小时），与 A 模块 `plantWorldTime` 同一适配口径。D 侧不新增第二时钟、不新增 `getWorldTime()`。

---

## 二、与 A 模块（土地与作物）接口约定

| 约定项 | 内容 | 来源 |
|---|---|---|
| 成长倍率接入 | A 模块 `GrowthService` 成长公式接入 `× EventRate`；彩虹日 `EventRate = 2.0`，其余为 `1.0` | 规则 §五十一、验收 §四十九 |
| 判定调用点 | A 模块调用 `EventService.isRainbowDay(type)` 判定是否彩虹日；`isEventActive(now)` 判定事件是否持续 | 规则 §五十一 |
| 常量引用 | `GameConstants.EVENT_RAINBOW_EVENT_RATE = 2.0`、`EVENT_RATE_P0 = 1.0`（P0 占位保留） | 规则 §五十一 |
| 事件记录字段 | 事件影响记录属 **A 模块 Crop**（如 `eventCount`），D 不持有 | 验收 §五十 |
| 时间字段矛盾 | `lastHydratedTime` 类型待 A 裁定（见 §五 矛盾 1） | 决策 D14 vs 验收 §五十 |

> **需 A 模块修改的部分（以文档提出，D 不直接改）**：`GrowthService` 成长公式增加 `× EventRate` 因子；`EventRate` 由 `EventService.isRainbowDay()` 决定（2.0 或 1.0）。

---

## 三、与 C 模块（品质与传说）接口约定

| 约定项 | 内容 | 来源 |
|---|---|---|
| 事件品质分 | C 模块 `QualityService` 调用 `isMeteorShower(type)` → `+20`、`isRainbowDay(type)` → `+15` | 规则 §四十八/§五十一 |
| 传说突破加成 | C 模块传说突破计算调用 `isMeteorShower(type)` → `+10%` | 规则 §四十八 |
| 常量引用 | `EVENT_QUALITY_METEOR_SHOWER = 20`、`EVENT_QUALITY_RAINBOW_DAY = 15`、`EVENT_METEOR_LEGENDARY_BONUS = 10` | 规则 §四十八/§五十一 |
| 神秘商人售价 | C 模块收获结算调用 `isMysteryMerchant(type)` + `EventState.getTargetCropType()` → 目标作物售价 `×2` | 规则 §四十九 |
| 事件状态读取 | C 模块通过 `FarmGameModel.getEventState()` 读取当前事件 | 验收 §九十一 |

> **需 C 模块修改的部分（以文档提出，D 不直接改）**：`QualityService` 增加事件品质分与传说突破加成；收获结算增加神秘商人售价 `×2`。

---

## 四、与 E 模块（存档）接口约定

| 约定项 | 内容 | 来源 |
|---|---|---|
| 新建表 | P2 新建 `active_event` 表 | 验收 §九十一 |
| 表字段 | `event_type`（枚举 `name()`）、`start_world_time`、`end_world_time`、`target_crop_type`、`payload` | 验收 §九十一 |
| 落库职责 | 由 E 模块 `ActiveEventDao` 落库，D 不直接写 SQL | 验收 §七十五 |
| 恢复语义 | 游戏在事件持续期间退出，回来后事件不能凭空消失（读回 `active_event` 恢复 `EventState`） | 验收 §九十一 |
| 日志表 | P2 增加 `event_log`（事件日志） | 验收 §一百零四 |

> **需 E 模块修改的部分（以文档提出，D 不直接改）**：新建 `active_event` 表与 `ActiveEventDao`；`event_log` 表；存档装配时把 `active_event` 读回 `BasicEventState`。

---

## 五、与 B 模块（玩家与经营）接口约定

| 约定项 | 内容 | 来源 |
|---|---|---|
| 离线模拟复用 | B 模块 `OfflineSimulationService` **必须复用** D 模块 `EventService` 规则，不得另写一套事件算法 | 验收 §八十九 |
| 调用方式 | B 模块离线每日循环调用 `EventService.rollDailyEvent(dayIndex)` 抽取、`expireIfNeeded(now)` 关闭 | 验收 §八十九/§九十 |
| 供规则不执行 | D 模块 P2 **只供规则**，**不执行**离线模拟（离线模拟由 B 执行） | 验收 §八十九、模块分工第 5 行 |
| 统一入口 | 在线与离线共用同一套世界模拟规则（建议 `WorldSimulationService`） | 验收 §八十九 |

> **需 B 模块修改的部分（以文档提出，D 不直接改）**：`OfflineSimulationService` 调用 D 的 `EventService`，不拥有独立事件公式。

---

## 六、跨模块矛盾与待裁定项（强制报告）

> 依据会话约束「发现文档间矛盾时立即停止并报告，禁止自行选择其中一种」。

### 矛盾 1：`lastHydratedTime` 字段类型（P1 遗留，未裁定）

| 文档 | 表述 |
|---|---|
| 验收规范 §五十 | `LocalDateTime lastHydratedTime;` |
| 规则文档 §二十五 | `LocalDateTime lastHydratedTime;` |
| 决策记录 D14 | 「A 侧时间字段统一 `long`」 |

- **影响范围**：A 模块 `Crop` 模型（非 D 模块）。
- **D 处理**：D 不持有该字段，**不自行裁定**；建议 A 模块（lyj）按 D14 统一为 `long`（游戏小时）。
- **待裁定人**：A 模块（lyj）。**状态：未裁定**。

### 矛盾 2：天气/事件生成时机的阶段归属（P1 遗留，未裁定）

| 文档 | 表述 |
|---|---|
| 规则文档 §八十一 | 完整 12 步日结流程（含天气生成第 ⑧ 步、事件抽取第 ⑩ 步） |
| 验收规范 §八十五 | 离线模拟属 P2 |

- **D 处理**：D 只提供 `rollDailyWeather()` / `rollDailyEvent()` 作为可调用单元，由上层协调器在跨日时调用；D **不**自行实现完整日结循环。
- **待裁定人**：团队（跨模块协调）。**状态：未裁定**。

### 矛盾 3：天气品质分上限的「次」口径（P1 遗留，未裁定）

- **D 处理**：D 只提供单次分值与上限常量，计次与封顶逻辑由 C 模块 `QualityService` 实现。
- **待裁定人**：C 模块（hy）。**状态：未裁定**。

### 矛盾 4：`EventService` 世界时间来源（P2 新增，本次发现）

| 文档 | 表述 |
|---|---|
| 任务书 | `BasicEventService` 依赖 `EventState`、`GameClock`、`RandomProvider` |
| 决策记录 D14 | D 侧 `GameClock` 不提供 `getWorldTime()`，A 侧时间由 `getGameDay()*24 + getGameHour()` 适配 |

- **分析**：`GameClock` 无「世界时间小时」直接读取方法，而事件起止时间需以游戏小时表示。
- **D 处理**：`BasicEventService` 通过 `getGameDay()*24 + getGameHour()` 适配计算世界小时（与 A 模块 `plantWorldTime` 同一口径），**不新增第二时钟、不新增 `getWorldTime()`**。
- **待裁定人**：团队（跨模块协调）。**状态：待确认**。

---

## 七、溯源说明

| 关键数值/规则 | 来源文档 | 章节 |
|---|---|---|
| 事件概率 74/5/8/10/3 | 《FSF游戏规则设计文档.md》 | §四十七 |
| 流星夜持续 24h、品质+20、传说+10% | 《FSF游戏规则设计文档.md》 | §四十八 |
| 神秘商人持续 12h、售价×2 | 《FSF游戏规则设计文档.md》 | §四十九 |
| 小动物即时、50~200 金币 | 《FSF游戏规则设计文档.md》 | §五十 |
| 彩虹日持续 24h、EventRate×2、品质+15 | 《FSF游戏规则设计文档.md》 | §五十一 |
| 每日最多 1 事件、一次抽取 | 《FSF游戏规则设计文档.md》 | §四十七 |
| 随机统一使用 RandomProvider | 《FSF游戏规则设计文档.md》 | §九十 |
| active_event 表字段 | 《FSF_P0-P4功能实现与验收规范.md》 | §九十一 |
| 离线模拟复用在线规则 | 《FSF_P0-P4功能实现与验收规范.md》 | §八十九 |
| 事件效果 | 《FSF_P0-P4功能实现与验收规范.md》 | §九十二 |
| 时间字段统一 long | 《FSF项目需求分析与开发计划书.md》 | 决策 D14 |
| D 模块 P2 = 随机事件 | 《模块分工.md》 | 第 7 行 |
