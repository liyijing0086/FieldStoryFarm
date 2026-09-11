> 作者：A 模块（lyj）｜ 阶段：P1（v0.2.0-playable）｜ 编码：UTF-8
> 上位规则：《FSF游戏规则设计文档.md》（唯一数值事实源）
> 阶段红线：《FSF_P0-P4功能实现与验收规范.md》
> 工程结构：《脚手架.md》｜ 分工：《模块分工.md》第 4 行（A 模块 P1 = 枯萎系统）
> 前置文档：《模块规范/A模块 P0 接口与类设计文档.md》（本文档延续其结构与承诺）
> 决策记录：《FSF项目需求分析与开发计划书.md》§决策记录（P1 新决策自 D16 起）

---

## 1. 文档信息

| 项    | 内容                    |
| ---- | --------------------- |
| 模块名称 | A——土地与作物模块            |
| 负责角色 | lyj                   |
| 开发阶段 | P1 / v0.2.0-playable  |
| 文档版本 | v1.0-draft            |
| 日期   | 2026-09-11            |
| 状态   | 待师傅确认（D16~D20）后团队评审   |
| 包名   | `com.fieldstory.farm` |

**P1 负责内容**：在线枯萎系统（WitherService + Crop 天气记录字段 + GrowthService WeatherRate 扩展 + UI 枯萎显示与清除按钮）。

**主要协作模块**：B（装饰抗性，后续）、C（品质读取天气计数、收获跳过 WITHERED）、D（天气数据提供，已交付）、E（集成装配接线、存档 5 字段）。

**依赖文档**：《FSF游戏规则设计文档》§十五~§二十三、§二十八~§三十一、§八十一；《FSF_P0-P4功能实现与验收规范》§四十六~§五十四、§七十八、§一百五十；《模块规范/D模块 P1 接口与类设计文档.md》§1.5、§5.1；《模块规范/A模块 P0 接口与类设计文档.md》。

---

## 2. P1 目标与范围

### 2.1 P1 目标（验收规范 §四十六）

P1 = 从能玩升级为有策略（v0.2.0-playable），A 模块承担其中 **在线枯萎** 部分：

```
天气记录（Crop 5 字段）→ droughtStreak 更新 → 枯萎概率判定 → WITHERED → 玩家铲除 → TILLED
```

四个交付单元：

|#|单元|内容|
|---|---|---|
|1|模型扩展|Crop 新增 5 个天气记录字段（验收规范 §五十）|
|2|核心服务|`WitherService` + `BasicWitherService`（验收规范 §一百五十 P1 新增清单）|
|3|成长公式升级|GrowthDelta 加入 WeatherRate（验收规范 §四十九，复查报告已裁定 3 参重载）|
|4|UI|枯萎视觉显示 + 铲除按钮（验收规范 §五十四）|

### 2.2 P1 红线（验收规范 §四十七：P1 必须保持 P0 完全兼容）

P1 **不得改变**：地图尺寸、土地状态逻辑、基础成长时间、种子价格、基础售价、主动浇水基础效果、GameClock 定义。

**只允许**：在原成长公式中加入新变量（WeatherRate）。

落实检查：

|红线项|本设计是否触碰|结论|
|---|---|---|
|土地状态逻辑|枯萎只改 `Crop.stage`，土地状态仅经 `LandService.removeCropAndSetTilled`（P0 已有方法，D09/D20）|✅ 不触碰|
|基础成长时间|`CropType.getBaseDailyProgress()` 不改|✅ 不触碰|
|主动浇水基础效果|雨天补水不增加 `manualWaterCount`、不加浇水 Buff（验收规范 §五十一）|✅ 不触碰|
|成长公式|只乘入新变量 WeatherRate（验收规范 §四十九），DecorationRate P1 不加|✅ 合规|

### 2.3 P1 禁止实现（阶段边界）

- ❌ 石灯笼抗性生效逻辑（B 装饰 P1 未交付，仅预留参数 `witherMitigationRate`，P1 恒传 1.0）；
- ❌ 完整 12 步日结循环（规则文档 §八十一 属 P2 持续世界引擎，D P1 文档 §1.3 已确认 D 只提供第 ⑧ 步可调用单元）；
- ❌ 离线模拟枯萎、枯萎动画/音效（P2/P4）。

---

## 3. 规则与决策依据

### 3.1 规则引用索引

|规则|来源|要点|
|---|---|---|
|GrowthStage 五态（含 WITHERED）|规则 §十五|P0 已占位，P1 启用 WITHERED|
|SEED 免疫|规则 §16.1；验收 §五十三|`stage==SEED` 不参与枯萎判定|
|MATURE 仍可枯萎|规则 §16.4|成熟作物可因长期极端干旱枯萎|
|WITHERED 语义|规则 §16.5；验收 §五十四|无法收获/出售、无金币/肥料、必须玩家主动铲除、铲除免费、铲除后 `Soil=TILLED`、`Crop=null`|
|雨天自动补水|规则 §二十一；验收 §五十一|rainCount+1、lastHydratedTime 更新、streak 重置；**不**加 manualWaterCount、不加浇水 Buff；雨天补水 ≠ 玩家主动浇水|
|干旱|规则 §二十二；验收 §五十二|droughtCount+1；无有效补水 → streak+1，否则 0；任何非干旱日 streak=0|
|绿雨|规则 §二十三|greenRainCount+1|
|枯萎四条件|规则 §二十八|①非 SEED ②当日存在干旱风险 ③没有有效补水 ④streak 达风险区间|
|连续干旱定义|规则 §二十九|当日 DROUGHT 且无有效补水 → streak+1；晴天/雨天/绿雨/主动有效浇水均终止 streak|
|枯萎概率表|规则 §三十；验收 §五十三|普通 / 小麦 / 成熟三档（见 §5.3）|
|石灯笼抗性|规则 §三十一|最终枯萎概率 ×0.7（P1 预留，恒 1.0）|
|日结 12 步|规则 §八十一|③更新 streak ④枯萎判定 ⑧生成天气 ⑨雨天补水；P2 持续世界引擎实现|

### 3.2 决策记录（P1 新决策 D16~D20，**待师傅确认**）

|决策|内容|影响章节|
|---|---|---|
|D16|`lastHydratedTime` 不用 `LocalDateTime`，按 D14 统一 long 游戏小时口径 `lastHydratedWorldTime`（与 `plantWorldTime` 一致）；"当日已补水"判定 = `lastHydratedWorldTime >= 0 && lastHydratedWorldTime / 24 == currentGameDay`（前置 `>= 0` 防哨兵 -1：`currentGameDay == 0` 时 `-1 / 24 == 0` 会误判已补水）|§4、§5.2|
|D17|枯萎视觉先查《FSFUI布局与美术设计规范.md》有无枯萎色；已查证 **无**（主色表 7 色 + 按钮三态均无枯萎色），本档提出候选色值并提请 UI 规范补充条目，实现前团队确认|§7.1|
|D18|WeatherRate 由 D 的 `WeatherService.getGrowthRate` 提供；按复查报告裁定，A 的 GrowthService 只收 `double weatherRate` 参数，**A 不依赖 D 的 Service**，文档记录事实|§6、§8|
|D19|`RandomProvider` 为静态工具类，不可构造器注入——概率计算与掷骰判定拆成纯函数（roll 值入参）保证可测；随机数由集成层经 `RandomProvider.nextDouble()` 获取后传入|§5.4、§8、§9|
|D20|清除枯萎操作归 A：UI 铲除按钮 + 复用 `LandService.removeCropAndSetTilled` → `Soil=TILLED`（D09 方法复用）|§7.3|

**"有效补水"定义（写死）**：当日主动浇水成功（`crop.getLastManualWaterGameDay() == currentGameDay`）**或**当日天气为 RAIN；绿雨/晴天终止 streak 但**不属于**补水（规则 §二十九）。

---

## 4. 模型设计：Crop 天气记录 5 字段

### 4.1 字段清单与默认哨兵值（验收规范 §五十）

|字段|类型|默认哨兵|语义|来源|
|---|---|---|---|---|
|droughtCount|int|0|累计干旱日数|规则 §二十二|
|rainCount|int|0|累计雨日数|规则 §二十一|
|greenRainCount|int|0|累计绿雨日数|规则 §二十三|
|lastHydratedWorldTime|long|**-1**|最近一次补水时刻（游戏小时，D16 裁定口径）|验收 §五十 + D16|
|droughtStreak|int|0|连续干旱计数|规则 §二十九|

`lastHydratedWorldTime` 哨兵 -1 与 P0 的 `lastManualWaterGameDay = -1` 对称（D14 模式：BasicCrop 模型层兜底默认 -1 + CropFactory 显式设置 + E 反序列化缺省映射 -1）。

### 4.2 与 D 模块 P1 §1.5 的字段归属对照（强制对照项）

|D P1 §1.5 表述|本档结论|一致性|
|---|---|---|
|`droughtCount/rainCount/greenRainCount/droughtStreak` 属 **A 模块 Crop**，D 不持有|✅ 一致，A 全盘接收|无冲突|
|`lastHydratedTime` 类型矛盾已报告（验收 §五十/规则 §二十五 `LocalDateTime` vs D14 `long`），"由 A 模块最终裁定"|**A 裁决建议**：按 D16 统一为 `long lastHydratedWorldTime`（游戏小时），与 `plantWorldTime` 口径一致；判定"当日已补水"用 `lastHydratedWorldTime / 24 == currentGameDay`。**待师傅确认**后同步 D/E 文档|有冲突，本档给出裁决建议|

### 4.3 接口与实现变更

`model/Crop.java`（P1 追加 5 对方法）：

```java
/** 累计干旱日数（规则文档 §二十二；验收规范 §五十） */
int getDroughtCount();
void setDroughtCount(int droughtCount);

/** 累计雨日数（规则文档 §二十一；验收规范 §五十） */
int getRainCount();
void setRainCount(int rainCount);

/** 累计绿雨日数（规则文档 §二十三；验收规范 §五十） */
int getGreenRainCount();
void setGreenRainCount(int greenRainCount);

/** 最近一次补水时刻（游戏小时，-1 表示无记录；决策 D16） */
long getLastHydratedWorldTime();
void setLastHydratedWorldTime(long lastHydratedWorldTime);

/** 连续干旱计数（规则文档 §二十九；验收规范 §五十） */
int getDroughtStreak();
void setDroughtStreak(int droughtStreak);
```

`model/impl/BasicCrop.java`：新增 5 字段，模型层兜底默认 `0/0/0/-1/0`（D14 哨兵模式）。

`factory/CropFactory.java`：`create(type, plantWorldTime)` 显式设置 5 字段默认值（与 P0 一致：BasicCrop 兜底 + 工厂显式 + E 适配层兜底）。

---

## 5. 服务设计：WitherService

### 5.1 包结构与接口（D13：接口在包根，实现类 Basic 前缀入 impl）

```
com.fieldstory.farm
├── service
│   ├── WitherService.java        【P1 新增】枯萎服务接口
│   ├── WitherResult.java         【P1 新增】枯萎判定结果枚举（随契约放 service 包根）
│   └── impl
│       └── BasicWitherService.java 【P1 新增】实现类
```

```java
package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.WeatherType;

/**
 * 枯萎服务接口（A 模块 P1；验收规范 §一百五十）。
 * 只依赖 model 层共享类型（Crop/WeatherType），不依赖 D 的 Service（D18）。
 */
public interface WitherService {

    /**
     * 当日天气记录（跨天回调对每株 PLANTED 作物调用一次）：
     * RAIN→rainCount+1、lastHydratedWorldTime=currentWorldTime、streak=0（规则 §二十一）；
     * DROUGHT→droughtCount+1，无有效补水 streak+1 否则 0（规则 §二十九）；
     * GREEN_RAIN→greenRainCount+1、streak=0（规则 §二十三）；
     * SUNNY→streak=0（任何非干旱日，验收 §五十二）。
     * 均不修改 manualWaterCount（验收 §五十一）。
     */
    void recordDailyWeather(Crop crop, WeatherType weatherType,
                            long currentGameDay, long currentWorldTime);

    /** 有效补水判定（定义见 §3.2 写死条款）：当日主动浇水成功 或 当日天气 RAIN。 */
    boolean isEffectivelyHydrated(Crop crop, WeatherType weatherType, long currentGameDay);

    /** 纯函数：枯萎概率（规则 §三十）；SEED/WITHERED 返回 0；含抗性倍率。 */
    double calculateWitherProbability(Crop crop, double witherMitigationRate);

    /** 纯函数：掷骰判定，roll ∈ [0,1)，roll < probability 触发（D19）。 */
    boolean rollWither(Crop crop, double witherMitigationRate, double roll);

    /**
     * 枯萎判定（四条件，规则 §二十八）：
     * ①stage==SEED→SEED_EXEMPT ②当日非 DROUGHT→NO_DROUGHT_RISK
     * ③已有效补水→NO_DROUGHT_RISK ④streak 未达风险区间→NO_DROUGHT_RISK
     * 通过后概率掷骰：roll < probability → stage=WITHERED（验收 §五十四）。
     */
    WitherResult judgeWither(Crop crop, WeatherType weatherType, long currentGameDay,
                             double witherMitigationRate, double roll);
}
```

### 5.2 天气记录与 streak 更新规则（规则 §二十一~§二十三、§二十九；验收 §五十一/§五十二）

`recordDailyWeather` 分支表：

|当日天气|rainCount|greenRainCount|droughtCount|lastHydratedWorldTime|droughtStreak|manualWaterCount|
|---|---|---|---|---|---|---|
|RAIN|+1|—|—|= currentWorldTime|= 0|不变|
|GREEN_RAIN|—|+1|—|不变|= 0|不变|
|DROUGHT|—|—|+1|不变|无有效补水 → +1；有 → 0|不变|
|SUNNY|—|—|—|不变|= 0|不变|

- "有效补水"用 `isEffectivelyHydrated(crop, weatherType, currentGameDay)`：`weatherType == RAIN` 或 `lastManualWaterGameDay == currentGameDay`（long 比较，D14）；
- DROUGHT 日仅在玩家当日主动浇水成功时为"有效补水"（当日天气不可能是 RAIN）；
- 绿雨/晴天只终止 streak，**不**更新 lastHydratedWorldTime、**不**属于补水（任务卡写死条款）；
- 雨天补水 ≠ 玩家主动浇水：不增加 manualWaterCount、不提供浇水成长 Buff（验收 §五十一）；
- **边界语义**：SEED 期作物 streak 照常更新（规则 §二十九 无阶段例外），仅枯萎判定跳过 SEED（§16.1、验收 §五十三）。若师傅认为 SEED 期应冻结 streak，请追加决策。

### 5.3 枯萎概率表（规则 §三十；验收 §五十三）

|droughtStreak|普通生长期|小麦|成熟作物|
|---:|---:|---:|---:|
|1|0%|0%|0%|
|2|30%|0%|0%|
|3|70%|30%|30%|
|4|100%|70%|70%|
|≥5|100%|100%|100%|

映射逻辑（BasicWitherService）：

```java
boolean resistant = crop.getCropType() == CropType.WHEAT
        || crop.getGrowthStage() == GrowthStage.MATURE;
int s = crop.getDroughtStreak();
double base;
if (s < 2) {
    base = 0.0;                                    // streak=1 或 0
} else if (resistant) {
    base = (s == 2) ? 0.0 : (s == 3) ? 0.30 : (s == 4) ? 0.70 : 1.00;
} else {
    base = (s == 2) ? 0.30 : (s == 3) ? 0.70 : 1.00;   // s≥4→1.0
}
return base * witherMitigationRate;
```

- **SEED 不参与**（验收 §五十三）：`stage==SEED` 直接返回 0；
- **WITHERED 跳过**（验收 §五十三）：`stage==WITHERED` 直接返回 0，且 `judgeWither` 返回 `ALREADY_WITHERED`；
- 三档口径：普通生长期 = 非小麦且非 MATURE（即玉米/胡萝卜的 SPROUT/GROWING）；小麦任何阶段与任何作物 MATURE 走耐性档（验收 §五十三"小麦和成熟作物"）。

### 5.4 掷骰判定（D19 纯函数）

`rollWither(crop, witherMitigationRate, roll)` = `roll < calculateWitherProbability(...)`。概率 0 永不触发；概率 1.0（未配抗性时）任何 roll ∈ [0,1) 必触发。**服务内禁止调用 RandomProvider**；roll 由集成层经 `RandomProvider.nextDouble()` 获取传入（D19），保证纯函数可测。

### 5.5 完整判定流程（judgeWither，规则 §二十八 四条件）

```
① crop == null / 非 PLANTED          → NOT_PLANTED（防御）
② stage == WITHERED                  → ALREADY_WITHERED（§五十三 跳过）
③ stage == SEED                      → SEED_EXEMPT（§16.1）
④ weatherType != DROUGHT             → NO_DROUGHT_RISK（条件②"当日存在干旱风险"）
⑤ isEffectivelyHydrated == true      → NO_DROUGHT_RISK（条件③"没有有效补水"）
⑥ prob = calculateWitherProbability  → prob <= 0 → NO_DROUGHT_RISK（条件④未达风险区间）
⑦ roll < prob → setGrowthStage(WITHERED) → WITHERED（§五十四）
⑧ 否则                                → SURVIVED
```

集成层调用顺序（任务卡 §8）：`recordDailyWeather`（更新 streak）→ `judgeWither`（用更新后 streak 判定）→ 视图刷新。

### 5.6 WitherResult 结果枚举（对齐 ReclaimResult 风格，文案由 Controller 映射）

```java
public enum WitherResult {
    NOT_PLANTED,       // 无作物（防御分支）
    SEED_EXEMPT,       // SEED 阶段豁免（规则 §16.1；验收 §五十三）
    ALREADY_WITHERED,  // 已枯萎，跳过（验收 §五十三）
    NO_DROUGHT_RISK,   // 四条件②③④任一不满足（含"已有效补水"）
    SURVIVED,          // 概率判定未触发
    WITHERED           // 判定触发：stage=WITHERED（验收 §五十四）
}
```

### 5.7 石灯笼抗性接口预留（规则 §三十一；B 模块后续）

- 判定链已含 `witherMitigationRate` 参数：最终枯萎概率 = 基础概率 × `witherMitigationRate`；
- **P1 恒传 1.0**（B 装饰未交付），常量 `WITHER_MITIGATION_P1 = 1.0`；
- B 装饰上线后（P1 后续）由集成层按石灯笼 Buff 传 0.7：30%→21%、70%→49%、100%→70%（规则 §三十一示例）；
- 石灯笼只降枯萎概率，**不**改变干旱成长倍率 ×0.5（规则 §三十一，A 不越界）。

### 5.8 BasicWitherService 实现要点

- 无参构造，**零外部依赖**（不依赖 D 的 Service、不依赖 RandomProvider，D18/D19）；
- 常量入 **BasicWitherService 自身** 的 `public static final`（P0 先例 `BasicLandService.RECLAIM_COST`；不动 D 的 `util/GameConstants.java`；禁止魔法数字）：

```java
// ===== P1 枯萎概率（规则文档 §三十）=====
public static final double WITHER_PROB_STREAK2_NORMAL   = 0.30;
public static final double WITHER_PROB_STREAK3_NORMAL   = 0.70;
public static final double WITHER_PROB_STREAK3_RESISTANT = 0.30;
public static final double WITHER_PROB_STREAK4_RESISTANT = 0.70;
// ===== P1 枯萎抗性（规则文档 §三十一；B 石灯笼上线前恒 1.0）=====
public static final double WITHER_MITIGATION_P1 = 1.0;
```

---

## 6. 成长公式扩展：WeatherRate（复查报告已裁定，定稿）

### 6.1 裁定结论（不再写"待 D 确认"）

|#|结论|说明|
|---|---|---|
|1|`GrowthService` 接口升级：`applyGrowth` 与 `calculateGrowthDelta` 各加 3 参重载（第三参 `weatherRate`），默认委托 2 参版本（weatherRate=1.0）|P0 完全兼容|
|2|`BasicGrowthService` override 3 参版本，公式：`GrowthDelta = BaseDailyProgress × ElapsedGameDays × WeatherRate × OperationRate`|验收 §四十九|
|3|调用方：D 的 `FarmController.currentWeatherRate()` 经 `advanceCrops` 3 参传入；**A 不依赖 D 的 Service（只收 double）**|D18|
|4|DecorationRate：P1 不加，预留经同一通道扩展第 4 参（B 装饰交付时协商）|本档声明|
|5|测试：3 参版新增用例（rate=1.5/0.5/2.0 逐值断言），2 参版既有用例不动|§9|

### 6.2 接口变更（`service/GrowthService.java`）

**协作顺序**：先拉 dev 确认 D 已加 `default applyGrowth(3 参)`；A 补齐 `calculateGrowthDelta` 3 参 default + `BasicGrowthService` override（§6.3），避免与 D 的 default 重复声明冲突。

```java
/** 3 参重载：第三参 weatherRate（验收 §四十九）；默认委托 2 参版本，P0 完全兼容。
 *  applyGrowth 的 3 参 default 由 D 加（先拉 dev 确认），A 不重复声明。 */
default double calculateGrowthDelta(Crop crop, double elapsedGameDays, double weatherRate) {
    return calculateGrowthDelta(crop, elapsedGameDays);
}
```

### 6.3 实现变更（`service/impl/BasicGrowthService.java`）

```java
@Override
public double calculateGrowthDelta(Crop crop, double elapsedGameDays, double weatherRate) {
    double base = crop.getCropType().getBaseDailyProgress();
    double operationRate = 1.0 + wateringService.calculateWaterGrowthBonus(crop);
    return base * elapsedGameDays * weatherRate * operationRate;   // DecorationRate P1 不加（预留第 4 参）
}

@Override
public void applyGrowth(Crop crop, double elapsedGameDays, double weatherRate) {
    if (crop.getGrowthStage() == GrowthStage.WITHERED) {
        return;   // P1 守卫：枯萎作物不再成长（规则 §16.5），且防止 stageOf 把 WITHERED 重算回正常阶段
    }
    double newProgress = Math.min(100.0, crop.getGrowthProgress()
            + calculateGrowthDelta(crop, elapsedGameDays, weatherRate));
    crop.setGrowthProgress(newProgress);
    crop.setGrowthStage(stageOf(newProgress));
}
```

2 参 `applyGrowth` 实现上委托 `applyGrowth(crop, elapsedGameDays, 1.0)`，数值行为与 P0 完全一致，既有 2 参测试用例不动。

> **WITHERED 守卫是 P1 必要修改**：P0 的 `applyGrowth` 会无条件 `setGrowthStage(stageOf(newProgress))`，若枯萎作物继续被 D 的 `advanceCrops` 每 tick 推进，会被错误重算回 SPROUT/GROWING。守卫放在 A 的 Service 内（不改接口、不动 D 文件）。

### 6.4 DecorationRate 预留声明

P1 装饰 Buff 由 B 模块交付时，经同一通道扩展第 4 参（`decorationRate`），公式变为 `Base × Days × WeatherRate × DecorationRate × OperationRate`（验收 §四十九"P1 装饰上线后进一步加入"）。届时与 B 协商后升级接口，本档声明、P1 不实现。

---

## 7. UI 设计

### 7.1 枯萎视觉（D17）

- 已查《FSFUI布局与美术设计规范.md》：主色表 §14 七色 + 按钮三态 §13 均**无枯萎色**；
- **候选色值（提请 UI 规范补充条目，实现前团队确认）**：
  - 候选 A（推荐）：`#857766`（灰褐，与 MATURE 高亮 #E8C45C、EMPTY 木色 #8B5E3C 均有区分度）；
  - 候选 B：`#6F5B3E`（深枯褐，对比更强但接近木色）；
- 建议在 UI 规范 §14 主色表追加"枯萎 #857766"条目，随后 `FarmView` 以 `COLOR_WITHERED = Color.rgb(0x85, 0x77, 0x66)` 常量接入；
- 呈现方式：WITHERED 格**整格底色**换枯萎色、作物占位块隐藏（`cropBlockSizeFor(WITHERED)` 维持 P0 已占位的 0，不改）。

`view/FarmView.java` 变更点：

```java
public static Color tileColorFor(FarmPlot plotType, Soil soil) {
    // ... EMPTY/TILLED 分支不变 ...
    case PLANTED:
        Crop crop = soil.getCrop();
        if (crop != null && crop.getGrowthStage() == GrowthStage.WITHERED) {
            return COLOR_WITHERED;      // P1：枯萎色（D17，待团队确认候选色）
        }
        if (crop != null && crop.getGrowthStage() == GrowthStage.MATURE) {
            return COLOR_HIGHLIGHT;
        }
        return COLOR_SOIL;
    // ...
}
```

### 7.2 Tooltip 文案（`tooltipTextFor`）

```java
case PLANTED:
    Crop crop = soil.getCrop();
    if (crop != null && crop.getGrowthStage() == GrowthStage.WITHERED) {
        return "已枯萎，请铲除";        // 规则 §16.5：必须玩家主动铲除
    }
    if (crop != null && crop.getGrowthStage() == GrowthStage.MATURE) {
        return "已成熟，可收获";
    }
    // ... 未成熟文案不变 ...
```

### 7.3 清除按钮（D20：归 A，复用 removeCropAndSetTilled）

`controller/FarmAction.java` 新增：

```java
/** 铲除枯萎：WITHERED → TILLED（LandService.removeCropAndSetTilled；D20、验收 §五十四） */
CLEAR_WITHERED
```

`controller/FarmViewController.java` 变更点（**6 参构造器不变，无新依赖**——铲除走已有的 LandService）：

```java
public static List<FarmAction> actionsFor(Soil soil) {
    // ...
    case PLANTED:
        Crop crop = soil.getCrop();
        if (crop != null && crop.getGrowthStage() == GrowthStage.WITHERED) {
            return List.of(FarmAction.CLEAR_WITHERED);   // P1
        }
        if (crop != null && crop.getGrowthStage() == GrowthStage.MATURE) {
            return List.of(FarmAction.HARVEST);
        }
        return List.of(FarmAction.WATER);
    // ...
}

private static String labelFor(FarmAction action) {
    // ...
    case CLEAR_WITHERED:
        return "铲除";                  // 规则 §16.5：铲除免费
    // ...
}

private void clearWithered(Soil soil) {
    landService.removeCropAndSetTilled(soil);   // D20：PLANTED→TILLED、crop=null（验收 §五十四、规则 §16.5）
    farmView.hideMenu();
    farmView.setCurrentGameDay(gameClock.getGameDay());
    farmView.refreshTile(soil);
}
```

- 铲除**免费**（规则 §16.5），不扣金币、不发肥料、不计图鉴；
- 土地状态机唯一入口保持 A 的 `removeCropAndSetTilled`（P0 已交付，D09 收获复用、D20 枯萎复用）；
- `WITHERED` 作物 `actionsFor` 只给铲除，不出现浇水/收获按钮。

---

## 8. 跨模块协作项

### 8.1 与 D（世界环境）——已交付，P1 无需 D 新增接口 ✓

|D 已交付|A 消费方式|
|---|---|
|`WeatherService` 9 方法（rollDailyWeather/getGrowthRate/…/isRain/isDrought）|集成层取 `WeatherType` 传给 WitherService（A 不依赖 D Service，D18）|
|`WeatherState` + `FarmGameModel` 聚合（getWeatherService/getWeatherState）|集成层读取当日天气|
|`FarmController` 跨天回调 `onDayChanged` + `advanceCrops` 每 tick 推进|枯萎挂钩点（见 8.2）|
|`model/impl/TestGameClock`（advance/setGameDay/setGameHour）|A 单测时钟（§9）|
|`RandomProvider.nextDouble()`|集成层取 roll 传入（D19）|

**唯一 D 侧小改（复查报告已裁定，需 D 或 E 装配层执行）**：`FarmController.advanceCrops` 由 2 参改为 3 参调用——先经 `model.getWeatherService().getGrowthRate(model.getWeatherState().getWeatherType())` 取 rate，再 `growthService.applyGrowth(crop, elapsedGameDays, weatherRate)`。A 只提供 3 参方法、只收 double，不动 D 的 Service 归属。

### 8.2 与 E（集成装配）——onDayChanged 回调接线顺序（任务卡定稿）

```
rollDailyWeather(day)
  → 对 Farm 全部 PLANTED 作物：
      witherService.recordDailyWeather(crop, today, day, worldTime)
      → witherService.judgeWither(crop, today, day, WITHER_MITIGATION_P1,
                                   RandomProvider.nextDouble())
  → 视图刷新（farmView.refreshAll()）
（成长已由 D 的 advanceCrops 每 tick 推进，枯萎判定不重复成长逻辑）
```

- `worldTime = day × 24 + hour`（D14 口径），RAIN 时写入 lastHydratedWorldTime；
- 完整 12 步日结（规则 §八十一）属 P2 持续世界引擎，P1 只做上述简接线（D P1 §1.3 阶段边界一致）；
- **E 存档同步**：P1 起 Crop 序列化需含 5 新字段（验收 §五十），E 的 CropDao/JSON 适配层需同步字段清单，缺省映射哨兵值（D14 模式）。

### 8.3 与 B（玩家与经营）——石灯笼抗性（后续）

- P1 集成层恒传 `witherMitigationRate = 1.0`；B 装饰交付后传 0.7（规则 §三十一）；
- A 不实现任何装饰 Buff 逻辑（B 的 BuffService 职责，模块分工第 5 行）。

### 8.4 与 C（品质与传说）——提醒事项

- WITHERED 作物不能出售、不能获得肥料（验收 §五十四）——C 的 HarvestService/QualityService 需跳过 `stage==WITHERED`；
- 天气计数字段（rainCount 等）为 C 品质评分可读数据（C 只读，A 负责写），与 D P1 §5.1 约定一致。

---

## 9. 测试策略

### 9.1 可测性设计（D19）

- 概率计算（`calculateWitherProbability`）与掷骰判定（`rollWither`）为**纯函数**，roll 值入参，逐档断言概率边界；
- `recordDailyWeather`/`judgeWither` 均为无 I/O 纯逻辑，输入 crop/weatherType/day/worldTime/roll 全显式；
- 随机数仅由集成层经 `RandomProvider` 获取（不进入被测单元）；
- 测试时钟使用 **`model/impl/TestGameClock`（正式包）**——注意与 `testutil/TestGameClock`（test 包同名类）区分，避免 import 混淆：A P1 测试统一 import `com.fieldstory.farm.model.impl.TestGameClock`。

### 9.2 BasicWitherServiceTest（新，对应验收 §七十八 WitherServiceTest）

|组|用例|
|---|---|
|概率表|普通 streak 0/1→0、2→0.30、3→0.70、4→1.0、5→1.0；小麦 streak 2→0、3→0.30、4→0.70、5→1.0；MATURE（玉米）同小麦档；SEED→0；WITHERED→0|
|抗性|mitigation=0.7：0.30→0.21、0.70→0.49、1.0→0.70（规则 §三十一 示例逐值断言）|
|掷骰边界|prob=0.30：roll=0.29→true、roll=0.30→false；prob=0.70：roll=0.69→true、roll=0.70→false；prob=1.0：roll=0.9999→true；prob=0：任意 roll→false|
|天气记录|RAIN：rainCount+1、lastHydratedWorldTime=传入 worldTime、streak=0、manualWaterCount 不变；DROUGHT 无补水：droughtCount+1、streak+1；DROUGHT 当日主动浇水：droughtCount+1、streak=0；GREEN_RAIN：greenRainCount+1、streak=0、lastHydrated 不变；SUNNY：streak=0、三 count 不变|
|judgeWither 四条件|SEED→SEED_EXEMPT；WITHERED→ALREADY_WITHERED；非 DROUGHT→NO_DROUGHT_RISK；DROUGHT 但当日已浇水→NO_DROUGHT_RISK；streak=1→NO_DROUGHT_RISK；streak=2 普通 roll=0.29→WITHERED 且 stage 变 WITHERED；roll=0.30→SURVIVED|
|哨兵|CropFactory.create 后 5 字段 = 0/0/0/-1/0（BasicCropTest 扩展）|

### 9.3 GrowthServiceTest（扩展，3 参版）

- rate=1.5/0.5/2.0 逐值断言（如小麦 base=50、elapsed=1、无浇水加成 → 75/25/100）；封顶 100；浇水加成与 WeatherRate 连乘；
- 2 参版既有用例**不动**（default 委托保证 P0 行为不变）；
- WITHERED 守卫：枯萎作物 applyGrowth 后 progress/stage 不变。

### 9.4 UI 测试（扩展）

- `FarmViewControllerTest`：`actionsFor` 对 WITHERED 返回 `[CLEAR_WITHERED]`；铲除后 Soil=TILLED、crop=null；
- `FarmViewTest`：`tileColorFor` WITHERED→COLOR_WITHERED；`tooltipTextFor` WITHERED→"已枯萎，请铲除"。

---

## 10. 包结构变更清单（A 模块 P1）

```
com.fieldstory.farm
├── model
│   └── Crop.java                     【改】+5 对 getter/setter（§4.3）
├── model/impl
│   └── BasicCrop.java                【改】+5 字段，兜底哨兵 0/0/0/-1/0
├── factory
│   └── CropFactory.java              【改】create 显式设置 5 字段
├── service
│   ├── WitherService.java            【新】接口（§5.1）
│   ├── WitherResult.java             【新】结果枚举（§5.6）
│   ├── GrowthService.java            【改】+3 参 default 重载（§6.2）
│   └── impl
│       ├── BasicWitherService.java   【新】实现（§5.8）
│       └── BasicGrowthService.java   【改】override 3 参 + WITHERED 守卫（§6.3）
├── controller
│   ├── FarmAction.java               【改】+CLEAR_WITHERED（§7.3）
│   └── FarmViewController.java       【改】actionsFor/labelFor/clearWithered（§7.3）
└── view
    └── FarmView.java                 【改】tileColorFor/tooltipTextFor + COLOR_WITHERED（§7）
```

不新增 dao 包（P1 存档走 E 的 DAO）；不新增 A 对 D Service 的依赖。

---

## 11. 类图总览（文本版）

```text
<<enum>> WeatherType (model, D)          <<enum>> GrowthStage (model, A)
        │ 入参                                     │ WITHERED 启用
        ▼                                           ▼
┌───────────────────────────────┐        ┌───────────────────────────────┐
│ <<interface>> Crop (A, P1+5字段)│◄───┐   │ <<interface>> WitherService  │
│ +getDroughtCount()…            │    │   │ +recordDailyWeather()        │
│ +getLastHydratedWorldTime()    │    │   │ +isEffectivelyHydrated()     │
│ +getDroughtStreak()            │    │   │ +calculateWitherProbability()│
└──────────────┬────────────────┘    │   │ +rollWither()                 │
               │ 实现                 │   │ +judgeWither()                │
┌──────────────▼────────────────┐    │   └──────────────┬────────────────┘
│ BasicCrop (model.impl)        │    │                  │ 实现
└───────────────────────────────┘    │   ┌──────────────▼────────────────┐
                                     │   │ BasicWitherService (impl)     │
        ┌────────────────────────────┘   │ 无参构造，零外部依赖          │
        │ 读写                           └──────────────┬────────────────┘
        ▼                                                │ 读取常量
┌───────────────────────────────┐                       ▼
│ <<interface>> GrowthService   │              BasicWitherService 内置常量
│ +calculateGrowthDelta(2/3参)  │
│ +applyGrowth(2/3参)           │
└──────────────┬────────────────┘
               │ 实现
┌──────────────▼────────────────┐     集成层（E 装配）
│ BasicGrowthService (impl)     │◄─── rollDailyWeather(day)
│ 构造器注入 WateringService    │     → recordDailyWeather + judgeWither(roll)
│ 3 参公式：Base×Days×Weather   │     → RandomProvider.nextDouble() 取 roll
│   ×Operation；WITHERED 守卫   │     → farmView.refreshAll()
└───────────────────────────────┘
```

依赖方向：View → Controller → Service → Model → Util；A 的 WitherService 只依赖 model 共享类型（D18）。

---

## 12. 溯源说明（关键数值/规则来源）

|关键数值/规则|来源文档|章节|
|---|---|---|
|P1 版本 v0.2.0-playable、新增"在线枯萎"|验收规范|§四十六|
|P1 不得改变 P0 基础数值，只允许公式加新变量|验收规范|§四十七|
|天气四态概率 40/25/20/15|规则文档|§十九|
|WeatherRate 1.0/1.5/0.5/2.0|规则文档|§十九；验收 §四十九|
|成长公式 GrowthDelta = Base × Days × WeatherRate × OperationRate|验收规范|§四十九|
|Crop 新增 5 字段|验收规范|§五十|
|雨天：rainCount+1、lastHydratedTime 更新、streak 重置；不加 manualWaterCount|规则 §二十一；验收 §五十一|
|干旱：droughtCount+1；无有效补水 streak+1|规则 §二十二；验收 §五十二|
|绿雨：greenRainCount+1|规则文档|§二十三|
|枯萎四条件|规则文档|§二十八|
|连续干旱定义与中断项|规则文档|§二十九|
|枯萎概率表（普通/小麦/成熟）|规则文档|§三十；验收 §五十三|
|石灯笼 ×0.7（30→21/70→49/100→70）|规则文档|§三十一|
|SEED 不参与枯萎判定|规则 §16.1；验收 §五十三|
|MATURE 仍可枯萎|规则文档|§16.4|
|WITHERED 不可出售/无肥料；清除后 Soil=TILLED；铲除免费|规则 §16.5；验收 §五十四|
|WitherService 属 P1 新增 Service|验收规范|§一百五十|
|P1 新增 WitherServiceTest|验收规范|§七十八|
|日结 12 步（③streak ④枯萎 ⑧天气 ⑨补水）|规则文档|§八十一|
|时间字段 long 口径|决策记录|D14|
|铲除复用 removeCropAndSetTilled|决策记录|D09、D20|
|包结构（接口根 + Basic 前缀 impl）|决策记录|D13|

---

## 13. 待确认事项与风险

|#|事项|影响|建议|
|---|---|---|---|
|1|D16：lastHydratedWorldTime 用 long（vs 验收 §五十/规则 §二十五 的 LocalDateTime）|Crop 字段类型|请师傅确认后同步 D/E 文档|
|2|D17：枯萎候选色 #857766 / #6F5B3E|UI 规范 §14 无枯萎色|提请团队确认候选色并补充 UI 规范条目|
|3|SEED 期 streak 照常更新（规则 §二十九 字面无阶段例外）|玩法：种子期连旱+破土即可能枯萎|若师傅裁定 SEED 冻结 streak，需追加决策|
|4|D 的 FarmController.advanceCrops 3 参改造归属|复查报告已裁定，但动 D 文件|由 D 或 E 装配层执行，A 只提供 3 参方法|
|5|testutil/TestGameClock 与 model/impl/TestGameClock 同名并存|单测 import 混淆|A P1 测试统一用 model/impl 版本；建议 E 后续收敛|
|6|E 存档适配需同步 Crop 5 新字段与哨兵映射|存档兼容|提醒 E 在 CropDao/JSON 适配层处理（D14 模式）|

---

## 14. 变更记录

|版本|日期|说明|
|---|---|---|
|v1.0-draft|2026-09-11|P1 初稿：D16~D20 决策、Crop 5 字段、WitherService 契约、3 参 WeatherRate 定稿（复查报告）、UI 枯萎视觉与铲除、跨模块协作、测试策略；全部数值溯源规则文档/验收规范|
```

**交付说明**
- 本卡只产出设计文档，未改任何 .java/.fxml 文件 ✓
- 全部数值引用规则文档/验收规范原文，无自创数值；决策编号自 D16 连续 ✓
- 与 D P1 §1.5 的字段归属对照及 lastHydratedTime 类型冲突已按要求列出并给出裁决建议（§4.2）✓
- §6 按追加输入的复查报告裁定结论定稿（3 参重载 + A 只收 double + DecorationRate 预留声明），未再写"待 D 确认" ✓
- D17 已实际查证 UI 规范无枯萎色，提出两个候选色并提请规范补充 ✓
- 文末附完整溯源说明（§12）✓