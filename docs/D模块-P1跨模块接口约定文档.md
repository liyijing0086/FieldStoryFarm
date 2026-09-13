# D 模块 P1 跨模块接口约定文档（世界环境模块 · 天气系统）

> 作者：D 模块（zsl）｜ 阶段：P1（v0.2.0-playable）｜ 编码：UTF-8
> 上位规则：《FSF游戏规则设计文档.md》V4.0（唯一数值事实源）
> 阶段红线：《FSF_P0-P4功能实现与验收规范.md》V4.0
> 对接方：A 模块（土地与作物）、C 模块（品质与传说）、E 模块（存档）、B 模块（玩家与经营）

---

## 一、文档定位

本文档定义 **D 模块（世界环境）** 在 P1 阶段对外提供的**天气接口**，以及各模块的**调用点**与**待裁定项**。

D 模块**只提供天气数据与倍率**，不实现 A/C/B 模块的业务规则（统一 Model / Service 归属原则，验收规范 §3.1、§一百五十）。

> **约束**：D 模块**不修改** A/C/B 模块的类；需要 A/C/B 修改的部分以本文档形式提出。

---

## 二、D 模块对外接口清单（P1）

| 接口 | 方法 | 返回 | 用途 |
|---|---|---|---|
| `WeatherService` | `rollDailyWeather(int dayIndex)` | `WeatherType` | 生成每日天气（每天 00:00 调用一次） |
| `WeatherService` | `getGrowthRate(WeatherType)` | `double` | 天气成长倍率 |
| `WeatherService` | `getQualityScore(WeatherType)` | `int` | 天气品质分/次 |
| `WeatherService` | `getQualityScoreCap(WeatherType)` | `int` | 天气品质分上限 |
| `WeatherService` | `isRain(WeatherType)` | `boolean` | 是否雨天 |
| `WeatherService` | `isDrought(WeatherType)` | `boolean` | 是否干旱 |
| `WeatherService` | `isGreenRain(WeatherType)` | `boolean` | 是否绿雨 |
| `WeatherService` | `getDisplayName(WeatherType)` | `String` | 天气显示名（UI） |
| `WeatherService` | `getIcon(WeatherType)` | `String` | 天气图标（UI） |
| `WeatherState` | `getWeatherType()` / `getDayIndex()` | `WeatherType` / `int` | 天气状态读取 |
| `FarmGameModel` | `getWeatherService()` / `getWeatherState()` | — | 聚合入口 |

**数值表（唯一事实源：规则文档 §十九、§三十五）**

| 天气 | 概率 | 成长倍率 | 品质分/次 | 品质分上限 |
|---|---:|---:|---:|---:|
| SUNNY 晴天 | 40% | ×1.0 | +0 | — |
| RAIN 雨天 | 25% | ×1.5 | +5 | +20 |
| DROUGHT 干旱 | 20% | ×0.5 | +8 | +24 |
| GREEN_RAIN 绿雨 | 15% | ×2.0 | +15 | +45 |

---

## 三、与 A 模块（土地与作物）接口约定

| 约定项 | 内容 | 来源 |
|---|---|---|
| 成长倍率调用点 | A 模块 `GrowthService` 组装成长公式时调用 `WeatherService.getGrowthRate(type)` 获取 `WeatherRate` | 验收规范 §四十九 |
| 成长公式 | `GrowthDelta = BaseDailyProgress × ElapsedGameDays × WeatherRate × OperationRate`（`OperationRate` 由 A 模块 WateringService 提供） | 验收规范 §四十九 |
| 雨天自动补水 | A 模块用 `WeatherService.isRain(type)` 判定是否触发自动补水（**不增 `manualWaterCount`**） | 规则文档 §二十一 |
| 干旱判定 | A 模块用 `WeatherService.isDrought(type)` 更新 `droughtStreak` | 规则文档 §二十二、§二十九 |
| 天气记录字段 | `droughtCount / rainCount / greenRainCount / droughtStreak` 属 **A 模块 Crop**，D 不持有、不修改 | 验收规范 §五十 |
| 时间字段矛盾 | `lastHydratedTime` 类型待 A 裁定（见 §六 矛盾 1） | 决策 D14 vs 验收规范 §五十 |

> **D 模块不修改 A 模块的 `Crop` 类**；上述 Crop 字段扩展由 A 模块（lyj）自行实现。

---

## 四、与 C 模块（品质与传说）接口约定

| 约定项 | 内容 | 来源 |
|---|---|---|
| 天气品质分调用点 | C 模块 `QualityService` 调用 `getQualityScore(type)` / `getQualityScoreCap(type)` | 规则文档 §三十五 |
| 绿雨突破 | C 模块用 `isGreenRain(type)` 计算传说突破加成（**P2 启用**） | 规则文档 §二十三 |
| 封顶口径 | D 只提供**单次分值与上限常量**；计次与封顶逻辑由 C 模块 QualityService 实现（见 §六 矛盾 3） | 规则文档 §三十五 |

> **D 模块不修改 C 模块的 `QualityService`**。

---

## 五、与 E 模块（存档）接口约定

| 约定项 | 内容 | 来源 |
|---|---|---|
| 世界时间 | `world_state.current_world_time` ← `FarmGameModel.getWorldTimeTotalMinutes()` | 验收规范 §四十一、§七十三 |
| 当前天气 | `world_state.current_weather` ← `WeatherState.getWeatherType().name()` | 验收规范 §七十三 |
| 天气日索引 | `world_state.current_day_index` ← `WeatherState.getDayIndex()` | 验收规范 §七十三 |
| 随机种子 | `world_state.random_seed` ← 供 `RandomProvider.setSeed` 复现 | 验收规范 §七十三、规则文档 §九十 |
| 落库职责 | 由 E 模块 `WorldStateDao` 落库，D 不直接写 SQL | 验收规范 §七十五 |

> 详见《D模块-P1存档字段映射对接文档.md》。**D 模块不修改 E 的 DAO / SaveService**。

---

## 六、与 B 模块（玩家与经营）接口约定

| 约定项 | 内容 | 来源 |
|---|---|---|
| 装饰 Buff | 装饰对成长/品质的影响由 B 模块 `BuffService` 提供，D 不参与 | 模块分工第 5 行 |
| 无直接依赖 | D 与 B 在 P1 无直接接口依赖 | — |

---

## 七、跨模块矛盾与待裁定项（强制报告）

> 依据会话约束「发现文档间矛盾时立即停止并报告，禁止自行选择其中一种」。

### 矛盾 1：`lastHydratedTime` 字段类型

| 文档 | 表述 |
|---|---|
| 验收规范 §五十 | `LocalDateTime lastHydratedTime;` |
| 规则文档 §二十五 | `LocalDateTime lastHydratedTime;` |
| 决策记录 D14 | 「A 侧时间字段统一 `long`」 |

- **影响范围**：A 模块 `Crop` 模型（非 D 模块）。
- **D 模块处理**：D 不持有该字段，**不自行裁定**；建议 A 模块（lyj）按 D14 统一为 `long`（游戏小时）。
- **待裁定人**：A 模块（lyj）。

### 矛盾 2：天气生成时机的阶段归属

| 文档 | 表述 |
|---|---|
| 规则文档 §八十一 | 完整 12 步日结流程（含天气生成第 ⑧ 步） |
| 验收规范 §四十六 | P1 新增天气系统 |
| 验收规范 §八十五 | 离线模拟属 P2 |

- **D 模块处理**：P1 阶段 D 只提供 `rollDailyWeather()` 作为第 ⑧ 步的**可调用单元**，由上层协调器在跨日时调用；D **不**自行实现完整日结循环。
- **待裁定人**：团队（跨模块协调）。

### 矛盾 3：天气品质分上限的「次」口径

| 文档 | 表述 |
|---|---|
| 规则文档 §三十五 | 雨天 +5/次，上限 +20；干旱 +8/次，上限 +24；绿雨 +15/次，上限 +45 |
| 验收规范 §五十八 | 与规则文档一致 |

- **分析**：上限与单次分值的整除关系为 20/5=4、24/8=3、45/15=3，即「有效计次」分别为 4/3/3 次。
- **D 模块处理**：D 只提供**单次分值与上限常量**，**计次与封顶逻辑由 C 模块 QualityService 实现**。D 不裁定「第几次开始封顶」的边界语义。
- **待裁定人**：C 模块（hy）。

---

## 八、溯源说明

| 关键项 | 来源文档 | 章节 |
|---|---|---|
| 天气概率 40/25/20/15 | 规则文档 | §十九 |
| 天气成长倍率 1.0/1.5/0.5/2.0 | 规则文档 | §十九 |
| 天气品质分 0/5/8/15、上限 0/20/24/45 | 规则文档 | §三十五 |
| 雨天自动补水语义 | 规则文档 | §二十一 |
| 干旱 droughtStreak 语义 | 规则文档 | §二十二、§二十九 |
| 绿雨计数与突破加成 | 规则文档 | §二十三 |
| P1 成长公式含 WeatherRate | 验收规范 | §四十九 |
| 天气记录字段（Crop 侧） | 验收规范 | §五十 |
| 天气存档字段 | 验收规范 | §七十三 |
| 随机统一走 RandomProvider | 规则文档 | §九十 |
| 分层架构固定 | 验收规范 | §3.1 |
| Service 不得直接写 SQL | 验收规范 | §七十五 |
| 时间字段统一 long | 决策记录 | D14 |
