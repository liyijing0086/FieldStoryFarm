# D 模块 P2 详细设计说明书（世界环境模块 · 随机事件系统）

> 版本：P2 · v0.3.0-feature
> 负责人：D（zsl）· 世界环境模块
> 依据：《游戏规则设计文档》§四十七~§五十一、《FSF_P0-P4功能实现与验收规范》§八十九/§九十/§九十一/§九十二、《模块分工.md》第 7 行

---

## 〇、文档定位与范围

本文档为 D 模块 P2「随机事件系统」的详细设计说明书，覆盖需求分析、包结构、类设计、跨模块接口、测试要求、自检清单与溯源。

**阶段边界**：P2 只实现随机事件系统；**禁止**提前实现 P3（图鉴、套装、FarmScore、展示台、土地解锁、永恒花园）；**禁止** D 模块执行离线模拟（由 B 模块执行，D 只供规则）。

---

## 一、P2 需求分析（D 模块）

### 1.1 需求来源

| 需求 | 来源 |
|---|---|
| 随机事件系统 | 模块分工第 7 行（D 模块 P2 = 随机事件） |
| 每日最多 1 个事件 | 规则 §四十七 |
| 事件池 74/5/8/10/3 | 规则 §四十七 |
| 事件效果 | 规则 §四十八~§五十一、验收 §九十二 |
| active_event 表 | 验收 §九十一 |
| 离线复用在线规则 | 验收 §八十九 |

### 1.2 事件数值（**唯一事实源：规则文档 §四十七~§五十一**）

| 事件 | 概率 | 持续（游戏小时） | 效果 |
|---|---:|---:|---|
| 无事件 NONE | 74% | — | — |
| 流星夜 METEOR_SHOWER | 5% | 24 | 新种作物品质 +20；传说突破 +10% |
| 神秘商人 MYSTERY_MERCHANT | 8% | 12 | 随机 1 种作物售价 ×2 |
| 小动物来访 ANIMAL_VISIT | 10% | 即时 | 种子/肥料/50~200 金币 |
| 彩虹日 RAINBOW_DAY | 3% | 24 | EventRate ×2；品质 +15 |

### 1.3 事件生成时机（**唯一事实源：规则文档 §八十一**）

日结流程第 ⑤ 步「关闭到期事件」、第 ⑩ 步「从事件池抽取今日事件」、第 ⑪ 步「创建今日 EventState」。D 只提供 `expireIfNeeded()` 与 `rollDailyEvent()` 作为可调用单元，由上层协调器在跨日时调用。

### 1.4 D 模块 P2 明确不做（阶段边界）

- 不实现完整日结循环（跨模块协调，见矛盾 2）。
- 不执行离线模拟（B 模块执行，验收 §八十九）。
- 不实现品质/售价/成长计算（C/A 模块）。
- 不实现 P3 系统。
- 不直接写 SQL（E 模块落库，验收 §七十五）。

---

## 二、包结构设计

遵循决策 D13（接口在包根，实现类以 Basic 前缀放 impl 子包）：

```
com.fieldstory.farm
├── model
│   ├── EventType.java          （枚举，P0 已存在，P2 启用字段）
│   ├── EventState.java         （接口，P2 新增）
│   └── impl
│       └── BasicEventState.java（实现类，P2 新增）
├── service
│   ├── EventService.java       （接口，P2 新增）
│   └── impl
│       └── BasicEventService.java（实现类，P2 新增）
└── util
    └── GameConstants.java      （P2 追加事件常量）
```

---

## 三、类设计

### 3.1 EventType（枚举）

| 常量 | displayName | icon | durationHours | instant |
|---|---|---|---:|---|
| METEOR_SHOWER | 流星夜 | 🌠 | 24 | false |
| MYSTERY_MERCHANT | 神秘商人 | 🧙 | 12 | false |
| ANIMAL_VISIT | 小动物来访 | 🐿 | 0 | true |
| RAINBOW_DAY | 彩虹日 | 🌈 | 24 | false |
| NONE | 无事件 | （空） | 0 | true |

> 枚举 `name()` 即存档字符串，常量名不得改动（验收 §九十一）。

### 3.2 EventState（接口）

字段：`eventType`、`startWorldTime`、`endWorldTime`、`targetCropType`、`payload`；全部 getter/setter。只保存状态，不含业务逻辑。

### 3.3 BasicEventState（实现类）

- 无参构造：`NONE`，起止世界时间均为 0。
- 三参构造：`BasicEventState(EventType, long, long)` 用于存档恢复。

### 3.4 EventService（接口）

`rollDailyEvent(int)`、`isEventActive(long)`、`expireIfNeeded(long)`、`getDisplayName/getIcon`、`isMeteorShower/isMysteryMerchant/isRainbowDay`。

### 3.5 BasicEventService（实现类）

依赖：`EventState`、`GameClock`、`RandomProvider`。

**rollDailyEvent 伪代码**：

```
roll = RandomProvider.nextInt(100)          // [0,100)
if roll < 74            → NONE
else if roll < 79       → METEOR_SHOWER     // [74,79)
else if roll < 87       → MYSTERY_MERCHANT  // [79,87)
else if roll < 97       → ANIMAL_VISIT      // [87,97)
else                    → RAINBOW_DAY       // [97,100)
now = clock.getGameDay()*24 + clock.getGameHour()
NONE                      → start=end=0
即时（ANIMAL_VISIT）      → start=end=now
METEOR_SHOWER/RAINBOW_DAY → end = now + 24
MYSTERY_MERCHANT          → end = now + 12；targetCropType 随机 WHEAT/CORN/CARROT
```

**expireIfNeeded**：`now >= endWorldTime` 时重置为 `NONE`。

### 3.6 GameConstants（P2 追加常量）

| 常量 | 值 | 来源 |
|---|---:|---|
| EVENT_PROB_NONE | 74 | 规则 §四十七 |
| EVENT_PROB_METEOR_SHOWER | 5 | 规则 §四十七 |
| EVENT_PROB_MYSTERY_MERCHANT | 8 | 规则 §四十七 |
| EVENT_PROB_ANIMAL_VISIT | 10 | 规则 §四十七 |
| EVENT_PROB_RAINBOW_DAY | 3 | 规则 §四十七 |
| EVENT_DURATION_METEOR_SHOWER | 24 | 规则 §四十八 |
| EVENT_DURATION_MYSTERY_MERCHANT | 12 | 规则 §四十九 |
| EVENT_DURATION_RAINBOW_DAY | 24 | 规则 §五十一 |
| EVENT_QUALITY_METEOR_SHOWER | 20 | 规则 §四十八 |
| EVENT_QUALITY_RAINBOW_DAY | 15 | 规则 §五十一 |
| EVENT_METEOR_LEGENDARY_BONUS | 10 | 规则 §四十八 |
| EVENT_RAINBOW_EVENT_RATE | 2.0 | 规则 §五十一 |

保留 P0 的 `EVENT_RATE_P0 = 1.0`（P0 固定占位）。

### 3.7 FarmGameModel（聚合）

新增字段 `eventState`、`eventService`；新增方法 `getEventService()`、`getEventState()`。

**初始化顺序**：先建 `gameClock`，再建天气（不依赖时钟），最后建事件（`BasicEventService` 依赖 `gameClock` 读取世界时间）。

`tick()` 仍只调用 `gameClock.tick()`，不加入事件生成逻辑。

### 3.8 StatusView（事件显示）

新增 `eventLabel`，显示「图标 + 显示名」；无事件显示「无事件」；对 null 做 NPE 保护；文字色统一 `#493526`（7 色主色表）。

---

## 四、状态机

状态：`NONE`、`METEOR_SHOWER`、`MYSTERY_MERCHANT`、`ANIMAL_VISIT`、`RAINBOW_DAY`。
事件：`rollDailyEvent(dayIndex)`、`expireIfNeeded(currentWorldTime)`。
转移概率：74/5/8/10/3。

详见 `docs/D模块-P2事件状态机图.drawio.xml` 与 `docs/D模块-P2事件状态转移表.drawio.xml`。

---

## 五、单元测试要求（P2）

| 测试类 | 用例数 | 覆盖 |
|---|---:|---|
| `EventServiceTest` | 12 | 固定种子复现、概率分布、持续时长、到期关闭、即时事件、写入状态、显示名/图标、判定、目标作物 |
| `BasicEventStateTest` | 3 | 无参构造、三参构造、setter/getter 往返 |
| `EventTypeTest` | 6 | 常量集、name() 稳定、显示名/图标、时长、即时标志 |
| `FarmGameModelTest` | 13 | 事件聚合、默认 NONE、写入聚合状态、注入时钟聚合 |

只测纯函数，不实例化 JavaFX 控件。

---

## 六、编码前自检清单

| 检查项 | 状态 |
|---|---|
| `EventState` 接口放 `model`，`BasicEventState` 放 `model.impl`（决策 D13） | ✅ |
| `EventService` 接口放 `service`，`BasicEventService` 放 `service.impl` | ✅ |
| 事件概率 74/5/8/10/3 与规则 §四十七 一致 | ✅ |
| 事件时长 24/12/24 与规则 §四十八~§五十一 一致 | ✅ |
| 随机必须经 `RandomProvider`，无 `new Random()` | ✅ |
| 所有数值经 `GameConstants` 引用，无魔法数字 | ✅ |
| `EventType` 枚举常量集不改动（存档兼容） | ✅ |
| D 模块不实现收获/品质/成长/离线模拟 | ✅ |
| D 模块不直接写 SQL | ✅ |
| `StatusView` 只读，不调用 `rollDailyEvent` | ✅ |
| 包名统一为 `com.fieldstory.farm.*` | ✅ |
| 单元测试覆盖固定种子与概率分布 | ✅ |
| 文档矛盾已报告，未自行裁定 | ✅ |

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
| 接口在包根，实现类在 impl | 《FSF项目需求分析与开发计划书.md》 | 决策 D13 |
| 时间字段统一 long | 《FSF项目需求分析与开发计划书.md》 | 决策 D14 |
| D 模块 P2 = 随机事件 | 《模块分工.md》 | 第 7 行 |
| 分层架构固定 | 《FSF_P0-P4功能实现与验收规范.md》 | §3.1 |
| Service 不得直接写 SQL | 《FSF_P0-P4功能实现与验收规范.md》 | §七十五 |
