## 1. 文档信息

|项|内容|
|---|---|
|模块名称|A——土地与作物模块|
|负责角色|lyj|
|开发阶段|P0 / v0.1.0-core|
|文档版本|v1.0|
|日期|2026-09-09|
|状态|待团队评审|
|包名|`com.fieldstory.farm`|

**P0 负责内容**：土地状态机、开垦、播种、作物成长、浇水、收获后的土地回退。

**主要协作模块**：B（玩家与经营，hsy）、C（品质与传说，hy）、D（世界环境，zsl）、E（存档与收集，hyt）。

**依赖文档**：《FSF游戏规则设计文档》（唯一数值事实源）、《FSF_P0-P4功能实现与验收规范》、《FSF项目需求分析与开发计划书》、《模块分工》、《脚手架》。

---

## 2. 设计目标与 P0 范围

### 2.1 P0 经营闭环

```
开垦 → 买种 → 播种 → 浇水 → 成长 → 收获 → 出售 → 再种
```

本模块负责其中：**开垦（土地部分）、播种、成长、浇水、收获（土地回退部分）**。

### 2.2 P0 实现清单

|序号|内容|
|---|---|
|1|12×12 地图网格 + FarmPlot 四类格|
|2|SoilState 状态机（EMPTY/TILLED/PLANTED，LOCKED 仅占位）|
|3|开垦（EMPTY→TILLED，经 EconomyService 扣 5 金币）|
|4|播种（TILLED→PLANTED，经 EconomyService 消耗 1 种子）|
|5|作物逐日成长（P0 公式 + 阶段阈值 + 封顶 100）|
|6|主动浇水（阶段/当日/5 次上限三重校验 + 成长加成）|
|7|收获后土地回退（LandService.removeCropAndSetTilled，供 C 调用）|

### 2.3 P0 禁止实现（红线）

❌ 随机天气、品质、肥料、随机事件、传说、装饰 Buff、套装、图鉴、FarmScore、离线推进、枯萎系统、SQLite 正式存档、ShopService。

`WITHERED`、`LOCKED` 仅为枚举占位，P0 不产生相关行为（验收规范十四、二十一）。

---

## 3. 设计决策依据

本设计吸收决策记录 D01~D13 中与本模块相关者：

|决策|内容|对本文档的影响|
|---|---|---|
|D04|FarmPlot 四值枚举（含 SHOP/SHOWCASE 占位）|4.1 节|
|D05|地图 12×12 为唯一数据事实|7.1 节|
|D07|新增 LandService，入 P0 Service 清单|8.1 节|
|D08|EconomyService 增加 getSeedCount/hasSeed/consumeSeed|13.1 节|
|D09|收获后置 TILLED 由 C 调 A：removeCropAndSetTilled；P1 枯萎铲除复用|8.1 节、14.5 节|
|D10|中心 8×8（0-based (2,2)~(9,9)）FARM_PLOT 持 Soil；外围 DECORATION_AREA 占位；SHOP/SHOWCASE 具体格 P1/P3 定|7.1 节|
|D11|manualWaterCount≥5 后 canWater 直接 false；第 5 次仍有效、加成维持 +20% 封顶|8.4 节|
|D12|CropType 枚举硬编码数值字段；B 的售价/种子价查询改调本枚举（单一数据源）|4.4 节|
|D13|接口在包根；实现类 Basic 前缀入 impl 子包；枚举不拆分|5 节全文|

---

## 4. 功能需求分析

### 4.1 土地需求

|编号|需求|溯源|
|---|---|---|
|FR-T01|建立 12×12 网格 Farm 模型，共 144 格|规则文档10.1；验收规范十一|
|FR-T02|FarmPlot 四类格：FARM_PLOT/DECORATION_AREA/SHOP/SHOWCASE|规则文档十一；验收规范十二；D04|
|FR-T03|SoilState 四态状态机，P0 只走 EMPTY→TILLED→PLANTED→TILLED|规则文档十二；验收规范十四|
|FR-T04|开垦：EMPTY 且 FARM_PLOT，先经 EconomyService 扣 5 金币，成功才置 TILLED；金币不足土地不得变化|规则文档12.1；验收规范十五|
|FR-T05|收获/铲除后土地回退 TILLED（removeCropAndSetTilled）|验收规范三十三；D09|
|FR-T06|六条非法行为拦截：①对非 EMPTY 开垦 ②对 LOCKED 种植 ③对非 TILLED 种植 ④对非 PLANTED 浇水/收获/铲除 ⑤非 FarmPlot 种植 ⑥SEED 阶段浇水|验收规范十六|
|FR-T07|土地/作物字段可被 E 序列化（存档字段清单）|验收规范四十一|
|FR-T08|P0 数值硬编码于 CropType，字段结构与 crop-config.json 对齐|验收规范十七；D12|

### 4.2 作物需求

|编号|需求|溯源|
|---|---|---|
|FR-C01|播种：TILLED→PLANTED，同时创建 Crop|验收规范十八|
|FR-C02|播种消耗 1 颗种子，不得再次扣金币|验收规范十八、十九|
|FR-C03|Crop 完整字段：uuid/type/stage/progress/plantWorldTime/manualWaterCount/lastManualWaterGameDay；plantWorldTime 为播种时刻的游戏世界时间|验收规范二十|
|FR-C04|P0 成长公式：`BaseDailyProgress × ElapsedGameDays × OperationRate`（三 Rate 固定 1.0）|验收规范二十三、二十四|
|FR-C05|阶段阈值：0-20% SEED、20-50% SPROUT、50-100% GROWING、≥100% MATURE|验收规范二十二|
|FR-C06|成长封顶 100%，不可溢出|验收规范三十|
|FR-C07|浇水仅限 SPROUT/GROWING/MATURE 阶段|验收规范二十六|
|FR-C08|每日最多主动浇水 1 次|验收规范二十七、二十九|
|FR-C09|单株最多 5 次；第 5 次有效，第 6 次拒绝（D11）|验收规范二十八；D11|
|FR-C10|成长加成 +5%/次，封顶 +20%，公式 `min(count×0.05, 0.20)`|规则文档二十七|
|FR-C11|浇水加成进入 OperationRate 参与成长公式|验收规范二十四|
|FR-C12|MATURE 为可收获标准，收获校验由 C 执行|验收规范三十二|

---

## 5. 包结构设计（A 模块新增 20 个类型）

```
com.fieldstory.farm├── model                              # 状态模型（只保存状态，无业务逻辑）│   ├── Farm.java            接口│   ├── Soil.java            接口│   ├── Crop.java            接口│   ├── FarmPlot.java        枚举│   ├── SoilState.java       枚举│   ├── GrowthStage.java     枚举│   ├── CropType.java        枚举（含数值字段）│   └── impl│       ├── BasicFarm.java│       ├── BasicSoil.java│       └── BasicCrop.java├── service                             # 业务规则│   ├── LandService.java      接口│   ├── PlantingService.java  接口│   ├── GrowthService.java    接口│   ├── WateringService.java  接口│   ├── ReclaimResult.java    结果枚举│   ├── PlantingResult.java   结果枚举│   ├── WateringResult.java   结果枚举│   └── impl│       ├── BasicLandService.java│       ├── BasicPlantingService.java│       ├── BasicGrowthService.java│       └── BasicWateringService.java└── factory                             # 对象创建    └── CropFactory.java
```

约定说明（D13）：接口在包根，实现类 Basic 前缀入 `impl` 子包，枚举不拆分；结果枚举随 Service 契约放 `service` 包根；A 不建 dao 包（P0 存档走 E 的 SaveService）。

---

## 6. 核心枚举设计

### 6.1 FarmPlot

```
public enum FarmPlot {
    FARM_PLOT,        // 种植格（仅此格持有 Soil）
    DECORATION_AREA,  // P0 外围 2 圈占位，P1 放装饰
    SHOP,             // 占位，P1/P3 确定具体格
    SHOWCASE          // 占位，P1/P3 确定具体格
}
```

### 6.2 SoilState

```
public enum SoilState {
    EMPTY,   // 可开垦
    TILLED,  // 可播种
    PLANTED, // 可浇水/收获/铲除
    LOCKED   // 占位，P3 土地扩张解锁
}
```

### 6.3 GrowthStage

```
public enum GrowthStage {
    SEED,     // [0, 20)   不可浇水、不可收获
    SPROUT,   // [20, 50)
    GROWING,  // [50, 100)
    MATURE,   // >=100 且封顶；可浇水、可收获
    WITHERED  // 占位，P1 枯萎系统
}
```

### 6.4 CropType（D12：数值唯一来源，含硬编码数值）

```
public enum CropType {
    WHEAT(2, 10, 50),
    CORN(3, 15, 70),
    CARROT(4, 20, 60);

    private final int baseGrowthDays;   // 规则文档十三
    private final int seedPrice;        // 规则文档十三
    private final int baseSellPrice;    // 规则文档十三

    CropType(int baseGrowthDays, int seedPrice, int baseSellPrice) {
        this.baseGrowthDays = baseGrowthDays;
        this.seedPrice = seedPrice;
        this.baseSellPrice = baseSellPrice;
    }

    public int getBaseGrowthDays() { return baseGrowthDays; }
    public int getSeedPrice() { return seedPrice; }
    public int getBaseSellPrice() { return baseSellPrice; }

    /** 基础日成长进度 = 100 ÷ 基础生长天数（验收规范二十三） */
    public double getBaseDailyProgress() { return 100.0 / baseGrowthDays; }
}
```

> 说明：B 的 EconomyServiceImpl 应调用 `getSeedPrice()/getBaseSellPrice()`，删除其自有的 switch 映射（B 文档第 22 节），保证全项目单一数值源。P1 商店上线后由 E 提供 JsonConfigLoader 切换为 crop-config.json 加载。

---

## 7. 模型接口设计（状态层，只有 getter/setter）

### 7.1 Farm / BasicFarm

```
public interface Farm {
    int getWidth();                          // 12
    int getHeight();                         // 12
    FarmPlot getPlot(int row, int col);      // 任意格
    Soil getSoil(int row, int col);          // 仅 FARM_PLOT 返回 Soil，其余返回 null
    boolean isFarmPlot(int row, int col);
}
```

`BasicFarm` 实现要点（D05/D10）：

- 常量：`MAP_SIZE=12`、`FARM_AREA_SIZE=8`、`FARM_AREA_ORIGIN=2`；
- 构造时全图初始化：中心 8×8（0-based 行/列 (2,2)~(9,9)）置 `FARM_PLOT` 并各持一个 `BasicSoil`（EMPTY），其余 80 格置 `DECORATION_AREA`；
- **结构保障**：Soil 只存在于 FARM_PLOT 格 → `getSoil` 对非种植格返回 null → "非 FarmPlot 种植"（验收规范十六）在结构上天然不可能，Service 层无需重复校验。

### 7.2 Soil / BasicSoil

```
public interface Soil {
    long getId();
    int getRow();
    int getColumn();
    SoilState getState();
    void setState(SoilState state);
    Crop getCrop();                 // 仅 PLANTED 时非 null
    void setCrop(Crop crop);
}
```

### 7.3 Crop / BasicCrop

```
public interface Crop {
    UUID getCropUuid();
    void setCropUuid(UUID cropUuid);
    CropType getCropType();
    void setCropType(CropType cropType);
    GrowthStage getGrowthStage();
    void setGrowthStage(GrowthStage growthStage);
    double getGrowthProgress();     // 0.0 ~ 100.0
    void setGrowthProgress(double growthProgress);
    long getPlantWorldTime();       // 游戏小时（决策 D14）
    void setPlantWorldTime(long plantWorldTime);
    int getManualWaterCount();      // 0 ~ 5
    void setManualWaterCount(int manualWaterCount);
    long getLastManualWaterGameDay();   // 游戏日；-1 表示无浇水记录（决策 D14）
    void setLastManualWaterGameDay(long lastManualWaterGameDay);
}
```

设计原则（验收规范三、四）：Model 只表达"现在是什么状态"，全部 setter 供 Service 与 E 反序列化使用，**不含任何业务判断**。

---

## 8. Service 接口设计（业务层）

### 8.1 LandService（D07）

```
public interface LandService {

    /** 开垦前置校验：EMPTY 且 EconomyService.canAfford(5)（验收规范十五） */
    boolean canReclaim(Soil soil);

    /** 开垦：扣 5 金币成功后才 EMPTY→TILLED；失败不动地不扣钱 */
    ReclaimResult reclaim(Soil soil);

    /** 收获/铲除后的土地回退：crop=null + TILLED（验收规范三十三；D09） */
    void removeCropAndSetTilled(Soil soil);
}
```

`BasicLandService`（依赖：`EconomyService`）实现要点：

```
public ReclaimResult reclaim(Soil soil) {
    if (soil.getState() != SoilState.EMPTY) return ReclaimResult.NOT_EMPTY;
    if (!economyService.canAfford(RECLAIM_COST)) return ReclaimResult.NO_GOLD;
    economyService.spendGold(RECLAIM_COST);
    soil.setState(SoilState.TILLED);
    return ReclaimResult.SUCCESS;
}

public void removeCropAndSetTilled(Soil soil) {
    soil.setCrop(null);
    soil.setState(SoilState.TILLED);
}
```

常量 `RECLAIM_COST = 5`（规则文档12.1）。`removeCropAndSetTilled` 由 C 的 BasicHarvestService 调用（D09），P1 枯萎铲除复用同一方法，土地状态机唯一入口保持在 A。

### 8.2 PlantingService

```
public interface PlantingService {

    /** 播种前置校验：TILLED 且 EconomyService.hasSeed(type, 1)（验收规范十八；D08） */
    boolean canPlant(Soil soil, CropType type);

    /** 播种：消耗 1 种子→创建 Crop→TILLED→PLANTED；播种不扣金币 */
    PlantingResult plant(Soil soil, CropType type);
}
```

`BasicPlantingService`（依赖：`EconomyService`、`CropFactory`、`GameClock`）实现要点：

```
public PlantingResult plant(Soil soil, CropType type) {
    if (soil.getState() != SoilState.TILLED) return PlantingResult.NOT_TILLED;
    if (!economyService.consumeSeed(type, 1)) return PlantingResult.NO_SEED;  // 失败土地不动
    long plantWorldTime = gameClock.getGameDay() * 24L + gameClock.getGameHour(); // 决策 D14
    Crop crop = cropFactory.create(type, plantWorldTime);
    soil.setCrop(crop);
    soil.setState(SoilState.PLANTED);
    return PlantingResult.SUCCESS;
}
```

### 8.3 GrowthService

```
public interface GrowthService {

    /** P0 成长公式：BaseDailyProgress × ElapsedGameDays × (1 + 浇水加成)（验收规范二十四） */
    double calculateGrowthDelta(Crop crop, double elapsedGameDays);

    /** 累加成长值→封顶 100→按阈值更新阶段（验收规范二十二、三十） */
    void applyGrowth(Crop crop, double elapsedGameDays);
}
```

`BasicGrowthService`（依赖：`WateringService` 同层调用取浇水加成，避免公式重复）实现要点：

```
public double calculateGrowthDelta(Crop crop, double elapsedGameDays) {
    double base = crop.getCropType().getBaseDailyProgress();
    double operationRate = 1.0 + wateringService.calculateWaterGrowthBonus(crop);
    return base * elapsedGameDays * operationRate;   // 天气/装饰/事件 Rate P0 固定 1.0，省略
}

public void applyGrowth(Crop crop, double elapsedGameDays) {
    double newProgress = Math.min(100.0, crop.getGrowthProgress()
            + calculateGrowthDelta(crop, elapsedGameDays));
    crop.setGrowthProgress(newProgress);
    crop.setGrowthStage(stageOf(newProgress));
}

private GrowthStage stageOf(double progress) {
    if (progress >= 100.0) return GrowthStage.MATURE;
    if (progress >= 50.0)  return GrowthStage.GROWING;
    if (progress >= 20.0)  return GrowthStage.SPROUT;
    return GrowthStage.SEED;
}
```

`elapsedGameDays` 由调用方按"经过游戏小时 ÷ 24"折算传入（验收规范二十五），本服务为纯函数、不依赖 GameClock，便于测试。

### 8.4 WateringService

```
public interface WateringService {

    /** 三重校验：阶段∈{SPROUT,GROWING,MATURE} 且 当日未浇 且 count<5（D11） */
    boolean canWater(Crop crop, long currentGameDay);

    /** 浇水：count+1（≤5）、记录当日；返回具体拒绝原因 */
    WateringResult water(Crop crop, long currentGameDay);

    /** 成长加成：min(count×0.05, 0.20)（规则文档二十七） */
    double calculateWaterGrowthBonus(Crop crop);
}
```

`BasicWateringService` 实现要点：

```
public WateringResult water(Crop crop, long currentGameDay) {
    if (crop.getGrowthStage() == GrowthStage.SEED) return WateringResult.SEED_STAGE;
    if (crop.getManualWaterCount() >= MAX_MANUAL_WATER_COUNT) return WateringResult.WATER_LIMIT_REACHED;
    if (currentGameDay == crop.getLastManualWaterGameDay()) return WateringResult.ALREADY_WATERED_TODAY; // 决策 D14：long 比较
    crop.setManualWaterCount(crop.getManualWaterCount() + 1);
    crop.setLastManualWaterGameDay(currentGameDay);
    return WateringResult.SUCCESS;
}

public double calculateWaterGrowthBonus(Crop crop) {
    return Math.min(crop.getManualWaterCount() * WATER_BONUS_PER_TIME, MAX_WATER_BONUS);
}
```

常量：`MAX_MANUAL_WATER_COUNT=5`（验收规范二十八、D11）、`WATER_BONUS_PER_TIME=0.05`、`MAX_WATER_BONUS=0.20`（规则文档二十七）。D11 行为约定：第 5 次浇水有效，第 6 次起 `canWater` 直接返回 false，加成维持 +20% 封顶；UI 建议提示"这株作物已经不需要浇水了"。

### 8.5 结果枚举（命名待团队确认，建议对齐 B 的 PurchaseResult 风格）

```
public enum ReclaimResult {
    SUCCESS, NOT_EMPTY, NO_GOLD
}

public enum PlantingResult {
    SUCCESS, NOT_TILLED, NO_SEED
}

public enum WateringResult {
    SUCCESS, SEED_STAGE, ALREADY_WATERED_TODAY, WATER_LIMIT_REACHED
}
```

---

## 9. 工厂设计

```
public class CropFactory {

    /** 创建初始 Crop：SEED、progress=0、manualWaterCount=0、lastManualWaterGameDay=-1（决策 D14 哨兵） */
    public Crop create(CropType type, long plantWorldTime) {
        Crop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(type);
        crop.setGrowthStage(GrowthStage.SEED);
        crop.setGrowthProgress(0.0);
        crop.setPlantWorldTime(plantWorldTime);
        crop.setManualWaterCount(0);
        crop.setLastManualWaterGameDay(-1L); // 决策 D14：-1 哨兵表示无浇水记录
        return crop;
    }
}
```

---

## 10. 状态机设计

### 10.1 SoilState 状态机

```
@startuml
hide empty description
[*] --> EMPTY
EMPTY --> TILLED : 开垦\n先 EconomyService 扣 5 金币\n不足则拒绝且土地不变
TILLED --> PLANTED : 播种\n先 consumeSeed 扣 1 种子\n失败则土地不变
PLANTED --> TILLED : 收获(C 调用)/P1 枯萎铲除\nremoveCropAndSetTilled
@enduml
```

|转移|触发|校验|溯源|
|---|---|---|---|
|EMPTY→TILLED|开垦|state==EMPTY 且 canAfford(5)|验收规范十五|
|TILLED→PLANTED|播种|state==TILLED 且 hasSeed≥1|验收规范十八|
|PLANTED→TILLED|收获/铲除|C 校验 MATURE 后调用|验收规范三十三、D09|

### 10.2 GrowthStage 状态机

```
@startuml
hide empty description
[*] --> SEED
SEED --> SPROUT : progress >= 20
SPROUT --> GROWING : progress >= 50
GROWING --> MATURE : progress >= 100\n(封顶 100，不溢出)
@enduml
```

阈值边界（验收规范二十二）：`[0,20)` SEED、`[20,50)` SPROUT、`[50,100)` GROWING、`≥100` MATURE。SEED 不可浇水（验收规范二十六）、不可收获；MATURE 可浇水可收获；P0 不产生 WITHERED。

---

## 11. 类图总览

```
@startuml
skinparam classAttributeIconSize 0

package "model" {
    interface Farm {
        +getWidth() : int
        +getHeight() : int
        +getPlot(row, col) : FarmPlot
        +getSoil(row, col) : Soil
        +isFarmPlot(row, col) : boolean
    }
    interface Soil {
        +getId() : long
        +getRow() : int
        +getColumn() : int
        +getState() : SoilState
        +setState(state : SoilState) : void
        +getCrop() : Crop
        +setCrop(crop : Crop) : void
    }
    interface Crop {
        +getCropUuid() : UUID
        +setCropUuid(cropUuid : UUID) : void
        +getCropType() : CropType
        +setCropType(cropType : CropType) : void
        +getGrowthStage() : GrowthStage
        +setGrowthStage(stage : GrowthStage) : void
        +getGrowthProgress() : double
        +setGrowthProgress(progress : double) : void
        +getPlantWorldTime() : long
        +setPlantWorldTime(time : long) : void
        +getManualWaterCount() : int
        +setManualWaterCount(count : int) : void
        +getLastManualWaterGameDay() : long
        +setLastManualWaterGameDay(day : long) : void
    }
    enum FarmPlot {
        FARM_PLOT
        DECORATION_AREA
        SHOP
        SHOWCASE
    }
    enum SoilState {
        EMPTY
        TILLED
        PLANTED
        LOCKED
    }
    enum GrowthStage {
        SEED
        SPROUT
        GROWING
        MATURE
        WITHERED
    }
    enum CropType {
        WHEAT = 2日/10/50
        CORN = 3日/15/70
        CARROT = 4日/20/60
        +getBaseGrowthDays() : int
        +getSeedPrice() : int
        +getBaseSellPrice() : int
        +getBaseDailyProgress() : double
    }
}

package "model.impl" {
    class BasicFarm
    class BasicSoil
    class BasicCrop
}

package "service" {
    interface LandService {
        +canReclaim(soil : Soil) : boolean
        +reclaim(soil : Soil) : ReclaimResult
        +removeCropAndSetTilled(soil : Soil) : void
    }
    interface PlantingService {
        +canPlant(soil : Soil, type : CropType) : boolean
        +plant(soil : Soil, type : CropType) : PlantingResult
    }
    interface GrowthService {
        +calculateGrowthDelta(crop : Crop, elapsedGameDays : double) : double
        +applyGrowth(crop : Crop, elapsedGameDays : double) : void
    }
    interface WateringService {
        +canWater(crop : Crop, currentGameDay : long) : boolean
        +water(crop : Crop, currentGameDay : long) : WateringResult
        +calculateWaterGrowthBonus(crop : Crop) : double
    }
    enum ReclaimResult {
        SUCCESS
        NOT_EMPTY
        NO_GOLD
    }
    enum PlantingResult {
        SUCCESS
        NOT_TILLED
        NO_SEED
    }
    enum WateringResult {
        SUCCESS
        SEED_STAGE
        ALREADY_WATERED_TODAY
        WATER_LIMIT_REACHED
    }
}

package "service.impl" {
    class BasicLandService
    class BasicPlantingService
    class BasicGrowthService
    class BasicWateringService
}

package "factory" {
    class CropFactory {
        +create(type : CropType, plantWorldTime : long) : Crop
    }
}

package "外部 B模块" {
    interface EconomyService {
        +getGold() : int
        +canAfford(amount : int) : boolean
        +spendGold(amount : int) : void
        +addGold(amount : int) : void
        +buySeed(type : CropType, quantity : int) : PurchaseResult
        +getSeedCount(type : CropType) : int
        +hasSeed(type : CropType, quantity : int) : boolean
        +consumeSeed(type : CropType, quantity : int) : boolean
        +calculateBaseSellPrice(type : CropType) : int
    }
}

package "外部 C/D/E 模块" {
    class BasicHarvestService
    interface GameClock
    interface SaveService
}

Farm <|.. BasicFarm
Soil <|.. BasicSoil
Crop <|.. BasicCrop
LandService <|.. BasicLandService
PlantingService <|.. BasicPlantingService
GrowthService <|.. BasicGrowthService
WateringService <|.. BasicWateringService

BasicFarm "1" *-- "64" BasicSoil : 中心8×8(0-based 2~9)
BasicFarm --> FarmPlot
BasicSoil --> SoilState
BasicSoil "1" *-- "0..1" BasicCrop
BasicCrop --> CropType
BasicCrop --> GrowthStage

BasicLandService ..> EconomyService : canAfford(5)/spendGold(5)
BasicPlantingService ..> EconomyService : hasSeed/consumeSeed
BasicPlantingService ..> CropFactory
BasicPlantingService ..> GameClock : getGameDay()/getGameHour()
BasicGrowthService ..> WateringService : calculateWaterGrowthBonus()
BasicHarvestService ..> LandService : removeCropAndSetTilled()
BasicHarvestService ..> EconomyService : 售价/入账
SaveService ..> Farm
SaveService ..> Soil
SaveService ..> Crop
@enduml
```

---

## 12. 外部协作接口契约

### 12.1 与 B（玩家与经营）——接口已确认，包结构待 B 对齐 D13

|我方消费|用途|状态|
|---|---|---|
|`canAfford(5)` / `spendGold(5)`|开垦扣金币|✅ B 文档已含|
|`hasSeed(type, 1)`|播种前置校验|✅ D08|
|`consumeSeed(type, 1)`|播种消耗种子（失败返回 false，土地不动）|✅ D08|

我方提供：`CropType.getSeedPrice()/getBaseSellPrice()` 为全项目价格单一数据源（B 删除自有 switch）。

### 12.2 与 C（品质与传说）——按 D09

C 的 `BasicHarvestService` 收获流程调用我方 `LandService.removeCropAndSetTilled(Soil)`；土地状态变化统一走 A。⚠️ B 文档第 15 节旧表述"C 自己负责移除 Crop、置 TILLED"与 D09 冲突，待 B 修订。

### 12.3 与 D（世界环境）

|消费|用途|
|---|---|
|`GameClock.getGameDay()`/`getGameHour()`|播种时间戳 plantWorldTime（决策 D14）|
|`GameClock.getGameDay()`|浇水当日判断（由调用方传入 water 方法）|

D 需提供 `TestGameClock` 供我方单测（规则文档八）。

### 12.4 与 E（存档与收集）

- 我方保证 Farm/Soil/Crop 字段可序列化，存档字段清单按验收规范四十一：地图全量 144 格 FarmPlot、Soil 的 id/坐标/state、Crop 的 uuid/type/stage/progress/plantWorldTime/manualWaterCount/lastManualWaterGameDay；
- D13 提醒：E 需在 module-info 为 `model.impl` 增加对 Jackson 的 opens（多态反序列化）。

---

## 13. P0 五大用例时序

### 13.1 开垦

```
Controller → farm.getSoil(row, col)          // null → 非 FarmPlot，UI 直接拦截（验收规范十六）          → landService.canReclaim(soil)     // EMPTY 且 EconomyService.canAfford(5)          → landService.reclaim(soil)              → economyService.spendGold(5)  // 不足 → NO_GOLD，土地不变              → soil.setState(TILLED)
```

### 13.2 播种

```
Controller → plantingService.canPlant(soil, type)   // TILLED 且 hasSeed(type, 1)          → plantingService.plant(soil, type)              → economyService.consumeSeed(type, 1) // 失败 → NO_SEED，土地不动              → cropFactory.create(type, gameClock.getGameDay()*24L+gameClock.getGameHour()) → soil.setCrop(crop); soil.setState(PLANTED)
```

### 13.3 成长

```
场景推进（D 的 GameClock 变化）→ elapsed = 经过游戏小时 ÷ 24（验收规范二十五）→ growthService.applyGrowth(crop, elapsed)    → delta = baseDailyProgress × elapsed × (1 + 浇水加成)    → progress = min(100, progress + delta)（验收规范三十）    → 阈值更新 stage（验收规范二十二）
```

### 13.4 浇水

```
Controller → wateringService.water(crop, (long) gameClock.getGameDay()) → SEED → SEED_STAGE（验收规范二十六）    → count ≥ 5 → WATER_LIMIT_REACHED（验收规范二十八、D11）    → 当日已浇 → ALREADY_WATERED_TODAY（验收规范二十七、二十九）    → 通过 → count+1、记录当日；加成 = min(count×5%, 20%)
```

### 13.5 收获（C 主导，A 提供土地回退）

```
C.BasicHarvestService    → 校验 crop.getGrowthStage() == MATURE（验收规范三十二）    → economyService.calculateBaseSellPrice(type) + addGold(price)    → a.landService.removeCropAndSetTilled(soil)   // D09        → soil.setCrop(null); soil.setState(TILLED)
```

---

## 14. 数值与规则溯源总表

|数值/规则|值|来源|
|---|---|---|
|地图尺寸|12×12 = 144 格|规则文档10.1；验收规范十一|
|种植区|中心 8×8 = 64 格，0-based (2,2)~(9,9)|规则文档10.1；验收规范十二；D05/D10|
|开垦费用|5 金币/格|规则文档12.1；验收规范十五|
|初始金币|500（B 负责）|验收规范三十五|
|种子价格|10 / 15 / 20|规则文档十三；验收规范十七|
|基础售价|50 / 70 / 60|规则文档十三；验收规范十七|
|生长天数|2 / 3 / 4 游戏日|规则文档十三；验收规范十七|
|基础日进度|50 / 33.333… / 25|验收规范二十三|
|成长公式|`Base × Days × (1+浇水加成)`，三 Rate=1.0|验收规范二十四|
|非整日折算|经过小时 ÷ 24|验收规范二十五|
|阶段阈值|20% / 50% / 100%|验收规范二十二|
|成长封顶|100%|验收规范三十|
|浇水阶段|SPROUT / GROWING / MATURE|验收规范二十六|
|浇水频次|每日 1 次|验收规范二十七、二十九|
|浇水上限|5 次（第 5 次有效，第 6 次拒绝）|验收规范二十八；D11|
|浇水加成|+5%/次，封顶 +20%|规则文档二十七|
|收获前置|MATURE|验收规范三十二|
|收获后置|TILLED|验收规范三十三；D09|
|播种消耗|1 种子，不扣金币|验收规范十八、十九|

---

## 15. 单元测试规划

|测试类|覆盖点|
|---|---|
|`FarmTest`|144 格总数、中心 8×8 有 Soil、外围 80 格 getSoil 返回 null、isFarmPlot 正确|
|`SoilStateTest`|状态机合法转移 + 六条非法行为拦截（验收规范十六）|
|`LandServiceTest`|canReclaim/reclaim：EMPTY+足金成功、非 EMPTY 拒绝、金币不足 NO_GOLD 且土地金币均不变；removeCropAndSetTilled（桩 EconomyService）|
|`PlantingServiceTest`|canPlant/plant：TILLED+有种子成功、非 TILLED 拒绝、NO_SEED 时土地不变（桩 EconomyService + TestGameClock）|
|`GrowthServiceTest`|公式（0.5 天=25% 示例）、浇水加成参与、封顶 100、阈值边界 20/50/100|
|`WateringServiceTest`|阶段/当日/5 次三重拒绝、第 5 次有效、bonus 封顶 0.20|

---

## 16. 风险与待确认事项

|#|事项|影响|建议|
|---|---|---|---|
|1|B 文档包结构（economy 子包 + Impl 后缀）未对齐 D13|团队合并类图出现两套风格|请 B 按 D13 调整为 `service.EconomyService` + `service.impl.BasicEconomyService`、`model.PurchaseResult`|
|2|B 文档第 15 节与 D09 冲突|C 可能绕过 A 的状态机|请 B 修订为"C 调 A 的 removeCropAndSetTilled"|
|3|B 文档第 22 节价格 switch 双数据源|数值漂移风险|请 B 改调 CropType 字段|
|4|D14：Player 是否拆接口+实现|不影响本模块|B 与团队拍板后入决策记录|
|5|结果枚举命名（ReclaimResult 等）|仅枚举名变化|建议对齐 PurchaseResult 的 XxxResult 风格|
|6|E 的 Jackson 多态序列化（D13）|存档依赖|请 E 关注 module-info opens|

---

## 17. 变更记录

|版本|日期|说明|
|---|---|---|
|v1.0|2026-09-09|初稿：含决策 D07~D13、B 模块接口确认部分、PlantUML 类图与状态机、单元测试规划|

---

**A 模块 P0 接口最终对外承诺（给 B/C/D/E）：**

```
// A 提供
boolean canReclaim(Soil soil);
ReclaimResult reclaim(Soil soil);
void removeCropAndSetTilled(Soil soil);          // C 收获 / P1 枯萎铲除调用
boolean canPlant(Soil soil, CropType type);
PlantingResult plant(Soil soil, CropType type);
double calculateGrowthDelta(Crop crop, double elapsedGameDays);
void applyGrowth(Crop crop, double elapsedGameDays);
boolean canWater(Crop crop, long currentGameDay);
WateringResult water(Crop crop, long currentGameDay);
double calculateWaterGrowthBonus(Crop crop);
Crop create(CropType type, long plantWorldTime);

// A 消费（B 提供，已确认）
boolean canAfford(int amount);
void spendGold(int amount);
boolean hasSeed(CropType type, int quantity);
boolean consumeSeed(CropType type, int quantity);

// A 消费（D 提供）
int getGameDay();
int getGameHour();
```

接口确定后，A 模块内部实现（Basic 前缀实现类）不应随意修改上述方法签名；P1/P2 扩展（天气倍率、装饰 Buff、枯萎）通过实现类内部演进与枚举占位承接，不推翻 P0 接口。