# 《田野物语 · 三韵集》

# P0-P4逐级功能实现与验收规范 V4.0

**文档性质：开发实施基准**

**上位规则：《完整游戏规则设计文档 V4.0》**

---

# 一、文档目的

本文件只回答四件事：

1. 每个阶段具体实现什么；
2. 每个功能做到什么程度才算完成；
3. 每个阶段允许依赖哪些系统；
4. 如何验收，避免“功能看起来有了，实际上逻辑还是假的”。

本文件不重新定义游戏玩法数值。

任何：

- 时间比例；
- 天气概率；
- 作物成长时间；
- 品质阈值；
- 装饰效果；
- 传说条件；
- FarmScore；
- 毕业条件；

全部以《完整游戏规则设计文档 V4.0》为准。

---

# 二、版本路线总览

|阶段|版本Tag|阶段目标|玩家最终能体验到|
|---|---|---|---|
|骨架|`v0.0.1-skeleton`|工程可启动|看到JavaFX主界面|
|P0|`v0.1.0-core`|核心经营闭环|开垦→买种→播种→浇水→成长→收获→卖出→再种|
|P1|`v0.2.0-playable`|策略系统完整|天气、品质、肥料、装饰、商店、枯萎、SQLite|
|P2|`v0.3.0-feature`|核心特色完整|持续世界、离线模拟、事件、生命记忆、传说作物|
|P3|`v0.4.0-collect`|长线内容完整|图鉴、套装、评价、展示台、土地扩展、永恒花园|
|P4|`v1.0.0-release`|正式发布|完整UI、动画、音效、测试、平衡、打包|

---

# 三、所有阶段必须共同遵守的开发原则

## 3.1 架构固定

统一采用：

```text
View
↓
Controller
↓
Service
↓
DAO / Repository
↓
Persistence
```

Model只保存状态。

禁止将复杂游戏规则直接写在：

```text
Controller
View
Model getter/setter
```

例如：

错误：

```java
crop.water();
crop.calculateQuality();
farm.calculateScore();
```

正确：

```java
wateringService.water(cropId);
qualityService.calculate(crop);
farmScoreService.calculate(playerId);
```

---

# 四、统一Model原则

Model只表达：

> “现在是什么状态”。

Service负责：

> “如何从一个状态变到另一个状态”。

例如Crop允许保存：

```text
cropUuid
cropType
growthStage
growthProgress
plantWorldTime
manualWaterCount
fertilizerCount
droughtCount
...
```

但Crop自身不得决定：

```text
如何成长
如何枯萎
如何变传奇
如何出售
```

---

# 五、统一时间原则

从P0第一天就必须引入：

```java
GameClock
```

正式时间：

```text
1现实分钟 = 1游戏小时
24现实分钟 = 1游戏日
```

不得在Service中直接调用系统时间进行游戏计算。

---

# 六、测试时间原则

实现：

```java
RealGameClock
DemoGameClock
TestGameClock
```

其中：

### RealGameClock

正式游戏：

```text
×1
```

### DemoGameClock

开发/答辩：

```text
×12
```

### TestGameClock

测试：

```text
可手动advance()
```

因此自动测试不需要真的等待96分钟。

---

# 七、随机原则

从需要随机的第一个版本开始统一使用：

```java
RandomProvider
```

禁止每个Service：

```java
new Random()
```

各自随机。

这样才能固定seed进行测试和离线复现。

---

# 八、阶段完成原则

任何阶段只有同时满足以下条件才算完成：

```text
功能真实可操作
+
数据真实变化
+
关闭重开状态正确
+
异常输入不会破坏状态
+
前阶段功能没有回归
+
核心逻辑存在自动测试
```

只把按钮画出来：

> 不算实现。

---

# 九、P0总体目标

# P0 = 完整核心经营闭环

版本：

```text
v0.1.0-core
```

玩家必须能够完成：

```text
进入游戏
↓
看到农场
↓
开垦土地
↓
购买种子
↓
播种
↓
等待成长
↓
主动浇水
↓
成熟
↓
收获
↓
自动出售
↓
获得金币
↓
重新购买种子
↓
再次播种
↓
退出保存
↓
重新打开继续
```

P0必须已经是：

# 一个真正能玩的最小游戏

而不是后面几个系统的演示壳。

---

# 十、P0明确不实现

P0禁止实现：

- 随机天气；
- 品质系统；
- 肥料；
- 随机事件；
- 传说作物；
- 装饰Buff；
- 套装；
- 图鉴；
- FarmScore；
- 离线推进；
- 枯萎；
- SQLite正式存档。

P0中的世界环境固定视为：

```text
WeatherRate = 1.0

DecorationRate = 1.0

EventRate = 1.0
```

这样后续加入系统不会修改基础公式。

---

# 十一、P0.1 地图基础

## 地图尺寸

从P0开始直接使用最终地图：

```text
12 × 12
```

禁止先做10×10再重构。

中心：

```text
8 × 8 FarmPlot
```

外围：

```text
2格宽功能区域
```

P0外围区域只显示占位。

不开放装饰。

---

# 十二、P0 FarmPlot

建立：

```java
enum FarmPlot {

    FARM_PLOT,
    DECORATION_AREA,
    SHOP,
    SHOWCASE

}
```

P0实际可交互：

```text
FarmPlot
```

其余格子：

```text
可见
但不可操作
```

---

# 十三、P0 Soil

建立：

```java
class Soil {

    long id;

    int row;

    int column;

    SoilState state;

    Crop crop;

}
```

仅：

```text
FarmPlot
```

拥有Soil。

---

# 十四、P0 SoilState

直接建立最终枚举：

```java
EMPTY
TILLED
PLANTED
LOCKED
```

P0主要使用：

```text
EMPTY
TILLED
PLANTED
```

LOCKED已经存在数据定义，但土地解锁功能直到P3开启。

---

# 十五、P0土地操作

EMPTY点击：

```text
检查金币 >= 5
↓
扣除5金币
↓
state = TILLED
↓
保存
↓
刷新格子
```

金币不足：

```text
不扣钱
不改变土地
显示：
“金币不足”
```

---

# 十六、P0禁止错误土地行为

必须阻止：

```text
EMPTY直接播种

TILLED浇水

TILLED收获

PLANTED再次播种

LOCKED开垦

非FarmPlot进行种植
```

---

# 十七、P0.2 作物基础

建立：

```java
enum CropType {

    WHEAT,
    CORN,
    CARROT

}
```

数据：

|作物|成长时间|种子价格|基础售价|
|---|---:|---:|---:|
|小麦|2游戏日|10|50|
|玉米|3游戏日|15|70|
|胡萝卜|4游戏日|20|60|

---

# 十八、P0 SeedInventory

必须从P0就区分：

```text
金币
≠
种子
```

Player建立：

```java
Map<CropType, Integer> seedInventory;
```

购买：

```text
金币减少
↓
种子数量增加
```

播种：

```text
种子数量减少
```

不得播种时直接扣金币。

---

# 十九、P0最小种子购买入口

P0暂不建设完整ShopView。

FarmView提供：

```text
种子快捷购买区
```

包含：

```text
小麦种子 10金币
玉米种子 15金币
胡萝卜种子 20金币
```

点击购买：

```text
EconomyService.buySeed()
```

P1正式ShopView仍然调用同一个Service。

因此P1不是重写购买逻辑，而只是替换完整商店界面。

---

# 二十、P0 Crop模型

至少包含：

```java
UUID cropUuid;

CropType cropType;

GrowthStage growthStage;

double growthProgress;

LocalDateTime plantWorldTime;

int manualWaterCount;

LocalDate lastManualWaterGameDay;
```

P0暂时不用的字段可以后续数据库迁移加入。

---

# 二十一、P0 GrowthStage

统一：

```java
SEED
SPROUT
GROWING
MATURE
WITHERED
```

P0不会产生WITHERED。

但枚举必须已经存在。

---

# 二十二、P0成长阶段

```text
0% ≤ progress < 20%
SEED

20% ≤ progress < 50%
SPROUT

50% ≤ progress < 100%
GROWING

progress ≥ 100%
MATURE
```

---

# 二十三、P0成长计算

基础：

```text
BaseDailyProgress
=
100 / BaseGrowthDays
```

因此：

小麦：

```text
50% / 日
```

玉米：

```text
33.333% / 日
```

胡萝卜：

```text
25% / 日
```

---

# 二十四、P0成长公式

P0：

```text
GrowthDelta
=
BaseDailyProgress
×
ElapsedGameDays
×
OperationRate
```

因为：

```text
WeatherRate = 1
DecorationRate = 1
EventRate = 1
```

---

# 二十五、P0成长必须支持非整日

禁止写成：

```text
每天00:00才增加成长
```

必须根据实际游戏时间差计算。

例如小麦经过：

```text
12游戏小时
```

基础成长：

```text
50%
×
0.5天
=
25%
```

---

# 二十六、P0.3 浇水

允许：

```text
SPROUT
GROWING
MATURE
```

SEED：

```text
不可主动浇水
```

---

# 二十七、P0主动浇水限制

每个游戏日：

```text
最多1次有效主动浇水
```

单株最多记录：

```text
5次
```

---

# 二十八、P0浇水成长效果

每次：

```text
成长加成 +5%
```

成长加成最多：

```text
+20%
```

因此：

```text
WaterGrowthBonus
=
min(manualWaterCount × 0.05, 0.20)
```

P0：

```text
OperationRate
=
1 + WaterGrowthBonus
```

第五次浇水仍然记录：

```text
manualWaterCount = 5
```

但成长加成仍然：

```text
20%
```

第五次的品质价值直到P1才使用。

---

# 二十九、P0重复浇水

同一个游戏日第二次点击：

```text
不增加manualWaterCount
不增加成长Buff
```

UI提示：

```text
“今天已经浇过水了”
```

---

# 三十、P0.4 成熟与收获

当：

```text
growthProgress >= 100
```

系统：

```text
growthProgress = 100
growthStage = MATURE
```

必须保证不会：

```text
105%
123%
```

继续增长。

---

# 三十一、P0收获规则

玩家点击：

```text
MATURE
```

执行：

```text
检查成熟
↓
读取CropType
↓
读取基础售价
↓
增加金币
↓
移除Crop
↓
SoilState = TILLED
↓
保存
↓
刷新UI
```

---

# 三十二、P0售价

因为品质尚未实现：

```text
FinalPrice = BasePrice
```

即：

```text
小麦50
玉米70
胡萝卜60
```

P1加入品质后才扩展公式。

---

# 三十三、P0不得存在HARVESTED

收获后：

```text
Crop对象离开当前土地
```

不是：

```text
Crop.stage = HARVESTED
```

土地直接变：

```text
PLANTED
↓
TILLED
```

---

# 三十四、P0.5 EconomyService

至少实现：

```java
boolean canAfford(int amount);

void spendGold(int amount);

void addGold(int amount);

PurchaseResult buySeed(
    CropType type,
    int quantity
);

int calculateBaseSellPrice(
    CropType type
);
```

金币不得直接在Controller中：

```java
player.setGold(player.getGold() - 10);
```

---

# 三十五、P0初始玩家

正式初始金币：

```text
500
```

种子通过最小购买入口购买。

这样：

```text
P0
P1
P2
P3
```

整个游戏经济模型完全一致。

---

# 三十六、P0.6 UI最小要求

FarmView至少显示：

### 顶部

```text
金币
游戏日
游戏时间
```

### 中部

```text
12×12农场地图
```

### 右侧或底部

```text
当前选中格信息
种子库存
购买种子
播种操作
浇水操作
收获操作
```

---

# 三十七、P0土地视觉状态必须明确

至少用：

- 文本；
- CSS；
- 图标；

区分：

```text
EMPTY
TILLED
PLANTED
MATURE
LOCKED
```

玩家不能必须“点一下才知道这格是什么”。

---

# 三十八、P0作物信息

选中作物显示：

```text
作物：小麦

阶段：GROWING

成长：63%

主动浇水：2/5

预计成熟时间：约18游戏小时
```

---

# 三十九、P0.7 P0临时存档

P0允许：

```text
JSON
```

但必须通过：

```java
SaveService
```

接口调用。

Controller不得知道：

```text
JSON文件在哪里
```

---

# 四十、P0 SaveService

建议：

```java
interface SaveService {

    void save(GameState state);

    GameState load();

    boolean hasSave();

}
```

P0实现：

```text
JsonSaveService
```

P1替换：

```text
SqliteSaveService
```

业务层调用方式不变。

---

# 四十一、P0 JSON必须保存

至少：

```text
Player.gold
seedInventory

GameClock.currentWorldTime

所有FarmPlot状态

每株Crop：
cropUuid
cropType
growthStage
growthProgress
plantWorldTime
manualWaterCount
lastManualWaterGameDay
```

---

# 四十二、P0退出行为

关闭程序：

```text
保存当前状态
↓
记录当前世界时间
↓
退出
```

P0：

# 退出后不推进世界。

因为离线模拟属于P2。

重新进入：

```text
恢复退出瞬间状态
```

---

# 四十三、P0测试要求

至少：

```text
SoilStateTest

GrowthServiceTest

WateringServiceTest

EconomyServiceTest

JsonSaveServiceTest
```

---

# 四十四、P0核心验收流程

使用：

```text
DemoGameClock ×12
```

执行：

```text
① 新建游戏

② 验证金币=500

③ 开垦3块地

④ 分别购买：
小麦
玉米
胡萝卜

⑤ 三块地分别播种

⑥ 等待进入SPROUT

⑦ 主动浇水

⑧ 观察成长速度变化

⑨ 三种作物全部成熟

⑩ 全部收获

⑪ 金币正确增加

⑫ 再购买至少1颗种子

⑬ 在收获后的TILLED土地重新播种

⑭ 退出游戏

⑮ 重新启动

⑯ 玩家金币、种子、土地、作物状态全部恢复
```

整个演示：

```text
10分钟以内完成
```

---

# 四十五、P0完成定义

只有上述流程全部通过才打：

```text
v0.1.0-core
```

此时游戏已经能够：

# “玩一轮并且继续玩”。

---

# 四十六、P1总体目标

# P1 = 从能玩升级为有策略

版本：

```text
v0.2.0-playable
```

新增：

```text
天气
+
品质
+
肥料
+
完整商店
+
装饰
+
Buff
+
在线枯萎
+
SQLite
```

---

# 四十七、P1必须保持P0完全兼容

P1不得改变：

```text
地图尺寸
土地状态逻辑
基础成长时间
种子价格
基础售价
主动浇水基础效果
GameClock定义
```

只允许：

> 在原公式中加入新变量。

---

# 四十八、P1.1 Weather系统

建立：

```java
SUNNY
RAIN
DROUGHT
GREEN_RAIN
```

概率：

```text
晴40%
雨25%
干旱20%
绿雨15%
```

每天：

```text
00:00
```

生成一次天气。

---

# 四十九、P1 WeatherRate

```text
SUNNY        ×1.0

RAIN         ×1.5

DROUGHT      ×0.5

GREEN_RAIN   ×2.0
```

成长公式升级：

```text
GrowthDelta
=
BaseDailyProgress
×
ElapsedGameDays
×
WeatherRate
×
OperationRate
```

P1装饰上线后进一步加入：

```text
× DecorationRate
```

---

# 五十、P1天气记录

Crop增加：

```java
int droughtCount;
int rainCount;
int greenRainCount;
LocalDateTime lastHydratedTime;
int droughtStreak;
```

---

# 五十一、P1雨天

雨天：

```text
rainCount +1
lastHydratedTime更新
droughtStreak重置
```

不增加：

```text
manualWaterCount
```

不增加主动浇水成长Buff。

---

# 五十二、P1干旱

干旱：

```text
droughtCount +1
```

如果：

```text
当天没有有效补水
```

则：

```text
droughtStreak +1
```

否则：

```text
droughtStreak = 0
```

任何非干旱日：

```text
droughtStreak = 0
```

---

# 五十三、P1.2 WitherService

P1必须实现：

# 在线枯萎

这样D06石灯笼在P1就有真实作用。

普通作物：

|连续干旱|枯萎率|
|---:|---:|
|1|0%|
|2|30%|
|3|70%|
|≥4|100%|

小麦和成熟作物：

|连续干旱|枯萎率|
|---:|---:|
|1|0%|
|2|0%|
|3|30%|
|4|70%|
|≥5|100%|

SEED：

```text
不参加枯萎
```

---

# 五十四、P1枯萎结果

成功触发：

```text
stage = WITHERED
```

WITHERED：

```text
不能出售
不能获得肥料
```

玩家执行：

```text
清除
```

之后：

```text
Soil = TILLED
```

---

# 五十五、P1.3 Quality系统

建立：

```text
COMMON
EXCELLENT
RARE
EPIC
LEGENDARY
```

但P1：

```text
LEGENDARY暂时无法获得
```

因为传说系统属于P2。

---

# 五十六、P1品质基础分

小麦：

```text
50
```

玉米：

```text
50
```

胡萝卜：

```text
55
```

---

# 五十七、P1品质公式

收获时：

```text
QualityScore
=
BaseScore
+
WeatherScore
+
OperationScore
+
DecorationScore
+
RandomScore
```

P1：

```text
EventScore = 0
```

因为事件属于P2。

---

# 五十八、P1天气品质分

```text
晴天：
0

雨天：
+5/次，上限20

干旱：
+8/次，上限24

绿雨：
+15/次，上限45
```

---

# 五十九、P1操作品质分

主动浇水：

```text
+3/次
上限15
```

施肥：

```text
+8/次
上限24
```

---

# 六十、P1品质判定

```text
Score < 60
COMMON

60～79
EXCELLENT

80～99
RARE

Score ≥ 100
EPIC
```

P1绝对不得：

```text
Score≥某值
→
LEGENDARY
```

---

# 六十一、P1品质售价

```text
COMMON      ×1

EXCELLENT   ×1.5

RARE        ×2

EPIC        ×3
```

LEGENDARY倍率已经存在配置：

```text
×5
```

但直到P2才可能出现。

---

# 六十二、P1.4 肥料系统

品质收获奖励：

```text
COMMON      0

EXCELLENT   1

RARE        2

EPIC        3

LEGENDARY   5
```

P1实际只能出现前四档。

---

# 六十三、P1施肥

允许：

```text
SPROUT
GROWING
```

每天最多：

```text
1次
```

生命周期最多：

```text
3次
```

每次：

```text
消耗1肥料

成长+15%

品质+8
```

---

# 六十四、P1 Crop新增字段

```java
int fertilizerCount;

LocalDate lastFertilizedGameDay;
```

---

# 六十五、P1.5 完整ShopView

替换P0简易购买区。

包含：

```text
种子
装饰
```

特殊商品入口可预留，但P1不需要虚构内容。

---

# 六十六、P1种子商店

仍调用：

```java
EconomyService.buySeed()
```

P0逻辑原样保留。

P1只升级UI。

---

# 六十七、P1.6 Decoration系统

实现14种装饰。

Buff分类统一：

```text
GrowthBuff
QualityBuff
PriceBuff
WitherResistanceBuff
OperationModifierBuff
```

不是旧版4类。

---

# 六十八、P1装饰区域

只能：

```text
DECORATION_AREA
```

放置装饰。

普通：

```text
1×1
```

大型：

```text
2×2
```

---

# 六十九、P1装饰必须实现真实效果

D01：

```text
相邻8格成长+5%
最多+15%
```

D02：

```text
主动浇水成长效果×1.10
```

D03：

```text
纯装饰
```

D04：

```text
纯装饰
```

D05：

```text
全局成长+3%
```

D06：

```text
枯萎概率×0.7
```

D07：

```text
施肥成长效果×1.20
```

D08：

```text
小麦成长+10%
```

D09：

```text
玉米成长+10%
```

D10：

```text
胡萝卜成长+10%
```

D11：

```text
全局成长+5%
```

D12：

```text
品质+10
```

D13：

```text
售价+10%
```

D14：

```text
售价+15%
品质+5
```

---

# 七十、P1装饰成长倍率

```text
DecorationRate
=
1
+
AdjacentBonus
+
GlobalBonus
+
CropSpecificBonus
```

P1：

```text
SetBonus = 0
```

因为套装属于P3。

成长类装饰倍率：

```text
最大1.5
```

---

# 七十一、P1.7 SQLite正式落地

从P1开始：

# SQLite成为唯一正式运行存档。

JSON玩家存档停止继续使用。

---

# 七十二、P1数据库最低表

```text
player
farm
soil
crop
decoration
world_state
```

---

# 七十三、P1 world_state必须提前存在

虽然离线模拟在P2，

P1已经保存：

```text
current_world_time
last_real_time
current_weather
current_day_index
random_seed
```

这样P2不需要重新推翻存档结构。

---

# 七十四、P1 JSON迁移

首次运行P1：

如果检测：

```text
旧P0 save.json存在
且
数据库不存在正式玩家数据
```

执行：

```text
JSON
↓
一次性导入SQLite
↓
校验
↓
建立迁移完成标记
```

之后：

```text
只读SQLite
```

禁止：

```text
JSON + SQLite双写
```

否则迟早会出现两个“真存档”，这种东西通常只在Bug报告里很有生命力。

---

# 七十五、P1 DAO

至少：

```text
PlayerDao
FarmDao
SoilDao
CropDao
DecorationDao
WorldStateDao
```

Service不得直接写SQL。

---

# 七十六、P1 UI升级

新增：

```text
天气图标

天气说明

品质颜色

肥料数量

装饰商店

作物品质影响预览

枯萎提示
```

---

# 七十七、P1品质解释UI

玩家查看作物时显示：

```text
天气累计 +15

主动浇水 +9

施肥 +8

装饰 +10
```

随机0～9：

```text
收获前不显示
```

---

# 七十八、P1测试

新增：

```text
WeatherServiceTest

QualityServiceTest

FertilizerServiceTest

BuffServiceTest

WitherServiceTest

ShopServiceTest

SQLiteDaoTest

MigrationTest
```

并继续运行全部P0测试。

---

# 七十九、P1验收

必须完成至少：

```text
同一作物建立多个培养组

↓

使用不同：
天气
浇水
施肥
装饰

↓

最终得到：
COMMON
EXCELLENT
RARE
EPIC
中的多个档位

↓

每株结果能够解释来源
```

另外必须验证：

```text
雨天不会增加manualWaterCount

非连续干旱不会累积droughtStreak

石灯笼只改变枯萎概率

SQLite关闭重开数据正确

P0 JSON可成功迁移
```

完成：

```text
v0.2.0-playable
```

---

# 八十、P2总体目标

# P2 = 项目核心差异化

版本：

```text
v0.3.0-feature
```

玩家最重要的新体验：

> “我关闭游戏之后，这个农场真的继续经历了一段时间。”

新增：

```text
持续世界
离线模拟
离线日志
随机事件
生命记忆
传说作物
```

---

# 八十一、P2不得改变正式时间比例

仍然：

```text
1现实分钟 = 1游戏小时
```

“持续世界时间”：

不是：

```text
现实时间与游戏日期1:1
```

而是：

```text
真实经过1分钟
=
世界推进1游戏小时
```

---

# 八十二、P2.1 logout记录

退出游戏必须保存：

```text
lastRealTime
currentWorldTime
```

启动：

```text
nowRealTime
-
lastRealTime
=
RawOfflineDuration
```

---

# 八十三、P2离线上限

```text
EffectiveOfflineDuration
=
min(
RawOfflineDuration,
72现实分钟
)
```

即：

```text
最多推进3游戏日
```

超出时间：

```text
不模拟
不补偿
```

---

# 八十四、P2启动顺序

严格执行：

```text
启动程序

↓

初始化数据库

↓

读取Player

↓

读取Farm

↓

读取Soil/Crop

↓

读取WorldState

↓

读取ActiveEvent

↓

计算离线时长

↓

执行OfflineSimulationService

↓

事务保存模拟结果

↓

生成OfflineLog

↓

打开FarmView
```

绝对不能：

```text
先打开FarmView
↓
再后台偷偷改变作物
```

否则UI和数据会短时间不一致。

---

# 八十五、P2.2 离线模拟

离线期间允许：

```text
时间推进

天气变化

作物成长

自动雨水补水

装饰Buff

枯萎

成熟

事件发生

生命经历记录
```

---

# 八十六、P2离线禁止行为

离线期间绝对不存在：

```text
主动浇水

主动施肥

购买

移动装饰

主动收获
```

---

# 八十七、P2成熟作物

离线期间成熟：

```text
stage = MATURE
```

但：

```text
不自动出售
```

玩家返回后：

```text
亲自点击收获
```

品质也在此时最终结算。

---

# 八十八、P2离线模拟分段

模拟不能简单：

```text
offlineHours
÷
24
```

粗暴处理。

必须按：

```text
游戏日00:00边界

事件结束时间

作物成熟时间
```

切段。

---

# 八十九、P2每日离线顺序

每日：

```text
① 处理当前时间段成长

② 更新阶段

③ 标记成熟

④ 到达日结边界

⑤ 处理补水

⑥ 更新droughtStreak

⑦ 枯萎判定

⑧ 关闭过期Event

⑨ 保存DailyLog

⑩ GameDay+1

⑪ 生成新Weather

⑫ 雨天自动补水

⑬ 抽取当天Event

⑭ 继续下一段
```

在线每日结算也必须调用相同领域逻辑。

禁止开发：

```text
OnlineDailyService一套算法

OfflineSimulationService另一套算法
```

建议共用：

```text
WorldSimulationService
```

P2的OfflineSimulationService只是负责：

> 按时间窗口调用同一套世界模拟规则。

---

# 九十、P2.3 RandomEvent

每天最多：

```text
1个
```

事件池：

```text
无事件74%

流星夜5%

神秘商人8%

小动物来访10%

彩虹日3%
```

必须：

```text
一次随机抽取
```

而不是四个事件分别独立判断。

---

# 九十一、P2 active_event

数据库增加：

```text
active_event
```

至少保存：

```text
event_type
start_world_time
end_world_time
target_crop_type
payload
```

原因：

> 游戏在事件持续期间退出，回来后事件不能凭空消失。

---

# 九十二、P2事件效果

### 流星夜

```text
24游戏小时
新种作物品质+20
传说概率+10%
```

### 神秘商人

```text
12游戏小时
随机1种作物售价×2
```

### 小动物来访

即时奖励：

```text
种子 / 肥料 / 50～200金币
```

### 彩虹日

```text
24游戏小时
EventRate×2
品质+15
```

---

# 九十三、P2.4 CropMemory

数据库增加：

```text
crop_memory
```

每株Crop：

```text
cropUuid
```

生命周期唯一。

历史记录不能因为：

```text
当前crop表删除
```

而丢失。

---

# 九十四、P2 Memory记录

包括：

```text
种植时间
成熟时间
收获时间
天气
干旱
绿雨
主动浇水
雨水
施肥
事件
枯萎风险
品质
传说突破
最终故事
```

---

# 九十五、P2收获后Memory

收获完成：

```text
当前Crop清除
```

但：

```text
CropMemory永久保留
```

---

# 九十六、P2.5 LegendaryService

传说判断必须从QualityService拆出：

```java
LegendaryService
```

职责：

```text
检查特殊条件
计算突破概率
执行突破骰
返回突破结果
```

QualityService负责：

```text
QualityScore
普通品质档位
```

---

# 九十七、P2金色麦穗

条件：

```text
WHEAT

经历至少1次干旱

至少1次：
干旱当天主动浇水

manualWaterCount >= 2

QualityScore >= 110
```

基础：

```text
30%
```

---

# 九十八、P2彩虹玉米

条件：

```text
CORN

greenRainCount >= 1

fertilizerCount >= 1

QualityScore >= 115
```

基础：

```text
40%
```

---

# 九十九、P2巨龙胡萝卜

条件：

```text
CARROT

greenRainCount >= 1

manualWaterCount >= 2

QualityScore >= 120
```

基础：

```text
35%
```

---

# 一百、P2突破加成

P2已有：

```text
绿雨 +5%/次
最多+15%

流星夜 +10%
```

P3加入：

```text
传奇之光 +10%
```

因此LegendaryService从P2就预留：

```text
SetLegendaryBonus
```

P2默认：

```text
0
```

---

# 一百零一、P2传奇上限

```text
最终概率 <= 80%
```

---

# 一百零二、P2品质最终顺序

```text
计算Score

↓

检查传奇基础条件

↓

条件满足？
```

否：

```text
普通4档
```

是：

```text
执行传奇骰
```

成功：

```text
LEGENDARY
```

失败：

```text
按Score正常返回
通常为EPIC
```

---

# 一百零三、P2.6 收获事务升级

HarvestService必须正式建立。

流程：

```text
检查MATURE
↓
QualityService计算Score
↓
LegendaryService判定
↓
确定Quality
↓
EconomyService计算售价
↓
发金币
↓
发肥料
↓
MemoryService完成故事
↓
保存CropMemory
↓
记录HarvestLog
↓
清除土地Crop
↓
Soil=TILLED
↓
事务提交
```

---

# 一百零四、P2日志表

增加：

```text
offline_log
event_log
crop_memory
active_event
```

---

# 一百零五、P2离线日志UI

回到游戏：

如果：

```text
EffectiveOfflineDuration > 0
```

显示一次：

```text
离开期间农场发生了什么
```

按游戏日分组。

重点显示：

```text
天气

成熟

枯萎

特殊事件

传说机会

奖励
```

---

# 一百零六、P2测试

新增：

```text
OfflineSimulationServiceTest

WorldSimulationServiceTest

EventServiceTest

LegendaryServiceTest

MemoryServiceTest

HarvestServiceTest

OfflineCapTest

DeterministicRandomTest
```

---

# 一百零七、P2必须测试的关键边界

### 离开30分钟

```text
推进30游戏小时
```

### 离开8小时

```text
只推进72游戏小时
```

### 离线跨3个游戏日

必须生成：

```text
正确天气
正确事件
正确成长
正确droughtStreak
```

### 作物离线成熟

```text
MATURE
但未出售
```

### 雨天

```text
rainCount增加
manualWaterCount不增加
```

---

# 一百零八、P2验收演示

推荐使用：

```text
TestClock
```

而不是人工修改电脑系统时间。

演示：

```text
① 种植多株作物

② 保存并退出

③ TestClock向前推进1游戏日

④ 重新打开

⑤ 系统先完成离线模拟

⑥ 显示离线日志

⑦ 作物成长与天气记录正确

⑧ 查看CropMemory

⑨ 创建满足传奇条件的候选作物

⑩ 固定随机seed验证传奇成功/失败路径
```

完成：

```text
v0.3.0-feature
```

---

# 一百零九、P3总体目标

# P3 = 游戏从“循环玩法”升级为“有毕业目标”

版本：

```text
v0.4.0-collect
```

新增：

```text
15项作物图鉴

14项装饰图鉴

3套装

8级FarmRank

FarmScore

展示台

LOCKED土地解锁

永恒花园
```

---

# 一百一十、P3.1 Collection系统

作物目标：

```text
3作物 × 5品质 = 15
```

包括：

```text
COMMON
EXCELLENT
RARE
EPIC
LEGENDARY
```

---

# 一百一十一、P3图鉴解锁

真正收获：

```text
对应作物 + 对应品质
```

才永久完成。

例如：

```text
WHEAT + RARE
```

只解锁：

```text
稀有小麦
```

不会自动解锁：

```text
普通小麦
优秀小麦
```

---

# 一百一十二、P3三级状态

```text
UNDISCOVERED

DISCOVERED

COLLECTED
```

COLLECTED永久保存。

---

# 一百一十三、P3装饰图鉴

14种装饰：

```text
首次成功购买该类型
→
COLLECTED
```

重复购买：

```text
不重复增加FarmScore
```

---

# 一百一十四、P3.2 Set系统

三个套装：

```text
自然之息

丰收之魂

传奇之光
```

---

# 一百一十五、自然之息

要求：

```text
D01
D02
D05
D06
```

全部拥有并放置。

Buff：

```text
成长+8%
```

---

# 一百一十六、丰收之魂

要求：

```text
D07
D08
D09
D10
```

Buff：

```text
售价+10%
```

---

# 一百一十七、传奇之光

要求：

```text
D11
D12
D13
D14
```

Buff：

```text
传奇突破概率+10%
```

---

# 一百一十八、P3套装两个状态必须分开

必须保存：

```text
setCollected
```

和：

```text
setActive
```

Collected：

```text
曾经完整完成
永久记录
```

Active：

```text
当前全部成员仍然放置
```

因此：

玩家完成套装以后收起一个装饰：

```text
setCollected = true
setActive = false
```

FarmScore仍保留套装分。

Buff停止。

---

# 一百一十九、P3.3 FarmScore

统一：

```text
装饰：
14 × 3 = 42

作物品质图鉴：
15 × 2 = 30

三种传说：
3 × 10 = 30

三套装：
3 × 15 = 45
```

总分：

```text
147
```

---

# 一百二十、P3禁止重复计分

例如：

玩家收获10株金色麦穗：

```text
金色麦穗传说分仍然只有10
```

购买5个向日葵：

```text
向日葵装饰分仍然只有3
```

套装反复拆装：

```text
仍然只有15
```

---

# 一百二十一、P3 FarmRank

|等级|分数|
|---|---:|
|新手农场|0～9|
|田园小筑|≥10|
|花园农场|≥30|
|美丽庄园|≥55|
|繁花似锦|≥80|
|自然天堂|≥105|
|传奇庄园|≥125|
|永恒花园|147|

---

# 一百二十二、P3永恒花园唯一条件

```text
FarmScore == 147
```

不是：

```text
完成3套装即可毕业
```

不是：

```text
获得3个传说即可毕业
```

不是：

```text
Rank达到某个等级即可
```

只有：

# 全收集147分。

---

# 一百二十三、P3.4 Showcase

展示台FarmPlot：

```text
SHOWCASE
```

允许展示：

```text
已经获得过的传说作物历史记录
```

展示的不是当前活Crop。

而是：

```text
CropMemory
```

---

# 一百二十四、P3展示内容

至少显示：

```text
传说名称

作物类型

品质

种植时间

收获时间

关键天气

关键事件

玩家操作

完整生命故事
```

---

# 一百二十五、P3.5 土地解锁

P0已经存在：

```text
LOCKED
```

P3正式启用。

执行：

```text
玩家点击LOCKED

↓

读取balance-config中的解锁价格

↓

检查金币

↓

确认购买

↓

扣钱

↓

LOCKED → EMPTY

↓

保存
```

---

# 一百二十六、P3土地价格配置原则

具体价格：

```text
不得写死在Controller或Soil
```

必须读取：

```text
balance-config.json
```

规则文档当前未固定各格扩地具体价格，因此：

> 开发层不得自行发明正式平衡数值。

在P3开发测试中允许使用测试配置验证流程。

正式发布价格由P4平衡测试定稿。

---

# 一百二十七、P3.6 CollectionView

至少显示：

```text
作物图鉴  x/15

装饰图鉴  x/14

传说      x/3

套装      x/3

FarmScore x/147

当前评价
```

---

# 一百二十八、P3目标提示

玩家点击未完成目标：

例如：

```text
彩虹玉米
```

显示：

```text
需要：

✓ 玉米

□ 经历绿雨

□ 至少施肥1次

□ 品质评分达到115

突破基础概率40%
```

避免毕业系统依赖外部攻略。

---

# 一百二十九、P3毕业触发

第一次达到：

```text
147
```

执行：

```text
FarmScoreService确认

↓

更新Rank = ETERNAL_GARDEN

↓

写入GraduationState

↓

播放毕业UI

↓

解锁完整统计

↓

保存
```

只触发一次首次毕业动画。

之后进入游戏：

```text
保持永恒花园状态
```

---

# 一百三十、P3测试

新增：

```text
CollectionServiceTest

SetServiceTest

FarmScoreServiceTest

FarmRankTest

ShowcaseServiceTest

LandUnlockServiceTest

GraduationTest
```

---

# 一百三十一、P3必须测试147分

自动构造：

```text
14装饰
15作物图鉴
3传说
3套装
```

验证：

```text
FarmScore = 147
```

删除任意一个必要收藏：

应：

```text
<147
```

但已获得的历史收藏正式游戏中不得被普通操作删除。

---

# 一百三十二、P3验收

完整验收：

```text
① 图鉴进度实时更新

② 重复收藏不重复加分

③ 套装收集状态和激活状态独立

④ 拆除套装成员后Buff停止

⑤ FarmScore不丢失

⑥ 三种传说可展示故事

⑦ LOCKED土地可购买解锁

⑧ 147分触发永恒花园

⑨ 146分绝对不能触发毕业
```

完成：

```text
v0.4.0-collect
```

---

# 一百三十三、P4总体目标

# P4 = 正式发布而不是继续塞新玩法

版本：

```text
v1.0.0-release
```

P4原则：

> 不新增大型核心系统。

否则项目会以一种极其传统的人类软件方式，在“准备发布”阶段突然重新设计半个游戏。

---

# 一百三十四、P4.1 UI统一

完成统一CSS。

必须覆盖：

```text
主菜单
农场
商店
图鉴
展示台
离线日志
设置
弹窗
```

---

# 一百三十五、P4品质视觉

统一：

```text
COMMON
EXCELLENT
RARE
EPIC
LEGENDARY
```

视觉层级。

颜色必须仅作为辅助。

同时配：

```text
名称
图标
```

避免只靠颜色识别。

---

# 一百三十六、P4.2 动画

最低实现：

```text
播种反馈

浇水反馈

施肥反馈

成长阶段切换

成熟提示

收获动画

金币增加

品质揭晓

传奇突破

农场升级

永恒花园毕业
```

动画不得改变逻辑时间。

---

# 一百三十七、P4.3 音效

至少：

```text
按钮

购买

播种

浇水

收获

稀有品质

传说突破

评价升级
```

提供：

```text
总音量
音效开关
```

---

# 一百三十八、P4.4 数值平衡

目标毕业时间：

```text
熟练玩家：
4～5小时

普通首次：
6～8小时

慢节奏：
8～10小时
```

极端坏运气：

```text
不应长期超过12小时
```

---

# 一百三十九、P4重点观察指标

记录测试数据：

```text
首次收获时间

首次优秀时间

首次稀有时间

首次史诗时间

首次传说时间

三传说完成时间

14装饰完成时间

15图鉴完成时间

147分毕业时间
```

---

# 一百四十、P4不得通过破坏规则调整平衡

例如毕业太慢：

优先调整：

```text
配置数值
概率
经济曲线
土地价格
```

不能临时加入：

```text
“Score130自动传奇”
```

这种已经被正式规则删除的捷径。

---

# 一百四十一、P4.5 全量测试

P4不是第一次测试。

P0-P3已经逐级测试。

P4负责：

# 完整回归。

---

# 一百四十二、P4测试矩阵

至少覆盖：

### 时间

```text
在线推进
离线推进
跨日
72分钟上限
```

### 土地

```text
开垦
播种
LOCKED
解锁
```

### 作物

```text
3类型
5阶段
成熟
枯萎
```

### 操作

```text
浇水
重复浇水
施肥
重复施肥
```

### 天气

```text
4天气
```

### 品质

```text
4普通档
3传奇
```

### 装饰

```text
14类型
5Buff分类
```

### 事件

```text
4事件
无事件
```

### 收集

```text
15作物
14装饰
3套装
3传说
```

### 存档

```text
启动
保存
退出
异常关闭恢复
```

---

# 一百四十三、P4固定随机测试

测试环境：

```text
固定RandomSeed
```

必须可以重现：

```text
固定天气序列
固定事件序列
固定品质随机分
固定枯萎结果
固定传奇突破
```

---

# 一百四十四、P4.6 数据安全

SQLite数据库：

```text
data/farm.db
```

不得放：

```text
src/main/resources
```

---

# 一百四十五、Git忽略

至少：

```text
data/farm.db
data/farm.db-shm
data/farm.db-wal
logs/
```

静态JSON配置：

```text
需要提交Git
```

---

# 一百四十六、P4.7 打包

必须验证：

```bash
mvn clean test
```

通过后：

```bash
mvn clean package
```

---

# 一百四十七、发布验收环境

必须至少在：

```text
全新目录
无开发IDE
无已有save
```

环境启动一次。

验证：

```text
新建游戏

保存

退出

重启

继续游戏
```

---

# 一百四十八、P4最终验收

正式发布前必须完成一次完整玩家路径：

```text
新游戏
↓
P0核心经营
↓
P1策略培养
↓
P2离线世界与传说
↓
P3全收集
↓
FarmScore147
↓
永恒花园
```

任何过程中：

```text
不得使用数据库手改
不得使用Debug直接加分
不得使用跳过收集的开发按钮
```

作为正式毕业验收。

---

# 一百四十九、最终模块归属表

|模块|首次正式上线阶段|
|---|---|
|JavaFX基础架构|骨架|
|GameClock|P0|
|12×12地图|P0|
|8×8种植区|P0|
|土地状态|P0|
|3种作物|P0|
|成长|P0|
|主动浇水|P0|
|种子库存|P0|
|基础经济|P0|
|基础收获|P0|
|JSON临时存档|P0|
|天气|P1|
|在线枯萎|P1|
|品质|P1|
|肥料|P1|
|完整商店|P1|
|14装饰|P1|
|5类Buff|P1|
|SQLite|P1|
|DAO|P1|
|持续世界|P2|
|离线模拟|P2|
|离线日志|P2|
|随机事件|P2|
|CropMemory|P2|
|生命故事|P2|
|LegendaryService|P2|
|HarvestService完整事务|P2|
|作物图鉴|P3|
|装饰图鉴|P3|
|套装|P3|
|FarmScore|P3|
|8级评价|P3|
|展示台|P3|
|土地扩张|P3|
|永恒花园|P3|
|UI全面美化|P4|
|动画|P4|
|音效|P4|
|最终数值平衡|P4|
|完整回归测试|P4|
|打包发布|P4|

---

# 一百五十、最终Service归属

## P0

```text
GrowthService

WateringService

EconomyService

PlantingService

BasicHarvestService

SaveService
```

## P1新增

```text
WeatherService

WitherService

QualityService

FertilizerService

BuffService

ShopService

DecorationService
```

## P2新增/升级

```text
WorldTimeService

WorldSimulationService

OfflineSimulationService

EventService

LegendaryService

MemoryService

HarvestService

LogService
```

P2以后：

```text
BasicHarvestService
```

由：

```text
HarvestService
```

正式替代。

## P3新增

```text
CollectionService

SetService

FarmScoreService

FarmRankService

ShowcaseService

LandUnlockService

GraduationService
```

---

# 一百五十一、最终DAO归属

P1开始：

```text
PlayerDao

FarmDao

SoilDao

CropDao

DecorationDao

WorldStateDao
```

P2：

```text
ActiveEventDao

EventLogDao

OfflineLogDao

CropMemoryDao
```

P3：

```text
CollectionDao

SetCollectionDao

ShowcaseDao
```

---

# 一百五十二、阶段之间禁止出现的错误依赖

## P0禁止

```text
GrowthService依赖WeatherService
```

因为Weather尚不存在。

P0应使用：

```text
WeatherRate = 1
```

---

## P1禁止

```text
QualityService偷偷生成Legendary
```

传奇属于P2。

---

## P2禁止

```text
OfflineSimulationService拥有一套独立成长公式
```

必须复用在线世界规则。

---

## P3禁止

```text
完成3套装直接Rank=永恒花园
```

只能：

```text
FarmScore == 147
```

---

## P4禁止

```text
为了赶进度修改核心机制
```

P4只能：

```text
修Bug
调配置
优化体验
```

大型规则修改必须重新回到规则文档评审。

---

# 一百五十三、各阶段最终玩家体验

## P0

玩家感受到：

> “这是一个已经能玩的农场游戏。”

---

## P1

玩家感受到：

> “我怎么种，会影响结果。”

---

## P2

玩家感受到：

> “我的农场即使关闭以后，也继续拥有自己的故事。”

---

## P3

玩家感受到：

> “我知道自己为什么继续玩，也知道什么时候真正毕业。”

---

## P4

玩家感受到：

> “这是一个完整产品，而不是课程作业窗口里摆了几十个Button。”

---

# 一百五十四、唯一开发顺序

最终实现顺序固定为：

```text
v0.0.1-skeleton

↓

P0
核心闭环

↓

v0.1.0-core

↓

P1
策略与正式持久化

↓

v0.2.0-playable

↓

P2
持续世界与特色系统

↓

v0.3.0-feature

↓

P3
收集与毕业系统

↓

v0.4.0-collect

↓

P4
体验、平衡、回归、发布

↓

v1.0.0-release
```

任何新需求必须先判断属于哪个阶段。

不得为了“顺手”跨阶段实现半套功能。

---

# 一百五十五、最终阶段完成标准

## P0完成

```text
能连续经营
+
能保存
+
能重新进入
```

## P1完成

```text
同一种作物
因玩家策略不同
获得明显不同结果
```

## P2完成

```text
退出游戏后重新进入
世界变化可复现、可解释、有记录
```

## P3完成

```text
游戏存在从0到147的完整毕业路线
```

## P4完成

```text
全流程可发布
无阻断Bug
普通玩家约6～8小时完成主要全收集
```

至此，《田野物语 · 三韵集》的开发实现、功能依赖、版本边界和最终玩家效果形成一条唯一、连续、无重复定义的实施路线。