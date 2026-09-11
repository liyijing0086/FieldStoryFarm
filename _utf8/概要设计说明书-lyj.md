# 1 引言

## 1.1 编写目的

本文档用于明确《田野物语 · 三韵集》的整体软件结构、分层职责、模块边界、核心类关系、运行流程、数据持久化策略、阶段演进方式以及团队协作接口，为以下工作提供统一依据：

- 课程设计答辩与概要设计评审；
- P0‑P4 分阶段开发；
- 5 人团队并行协作与接口联调；
- 后续详细设计、数据库设计和测试设计；
- AI 辅助开发时的架构约束和溯源核对。

## 1.2 项目背景

《田野物语 · 三韵集》（Field Story Farm）是基于 Java 17 + JavaFX 17 开发的桌面端单机农场模拟经营游戏。项目围绕土地经营、作物培养、天气策略、品质控制、传说突破、装饰建设、生命记忆和收藏成长构成完整游戏循环，最终以“永恒花园”作为当前版本的全收集毕业目标。

系统采用 P0→P4 五阶段逐级交付：

- P0 建立完整核心经营闭环；
- P1 加入策略系统和 SQLite；
- P2 实现持续世界、离线模拟、事件、生命记忆和传说作物；
- P3 完成图鉴、套装、评价、展示与毕业；
- P4 负责 UI、动画、音效、平衡、全量测试和打包发布。

## 1.3 设计范围

本说明书覆盖：

- 桌面客户端内部的软件分层；
- 业务模块划分与依赖；
- 核心 Model / Service / DAO / Controller / View 的职责；
- 本地 SQLite 与静态 JSON 配置边界；
- GameClock、RandomProvider、世界模拟和事务一致性；
- P0‑P4 架构演进；
- 关键业务流程和状态机；
- 可测试性、可维护性和存档可靠性；
- AI 使用、文档核对和人工复核机制。

不在本文范围内：

- 具体 Java 源代码；
- SQL 建表语句和索引细节；
- FXML/CSS 逐控件设计；
- 动画资源、美术资源和音频素材制作细节；
- 未经规则文档确认的新玩法或新数值。

## 1.4 术语与缩写

|术语|含义|
|---|---|
|MVC|Model‑View‑Controller，项目在其基础上扩展 Service、DAO、Persistence 分层|
|Model|仅表达当前状态的数据对象，不负责复杂游戏规则|
|Service|负责规则计算、状态迁移、跨对象协作和事务编排|
|DAO|数据访问对象，负责查询、保存、更新、删除，不承载业务判断|
|Persistence|持久化实现；P0 为 JSON 临时存档，P1 起 SQLite 为唯一正式运行存档|
|GameClock|统一游戏时间源，所有游戏时间逻辑必须通过该抽象取得|
|RandomProvider|统一随机源，用于固定 seed、测试复现和在线/离线一致性|
|WorldSimulationService|P2 起在线和离线共同复用的世界规则执行入口|
|CropMemory|与 cropUuid 绑定的作物生命周期历史记录，作物离开当前土地后仍保留|
|FarmScore|由装饰、图鉴、传说、套装共同构成的农场总评分，满分 147|

---

# 2 设计目标与约束

## 2.1 总体设计目标

系统设计应同时满足**可玩、可维护、可复现、可验收**四个目标：

1. **可玩**：P0 即形成可重复经营的最小游戏闭环，而非演示壳。
2. **可维护**：规则集中在 Service，状态集中在 Model，数据库访问集中在 DAO，避免跨层污染。
3. **可复现**：时间统一由 GameClock 提供，随机统一由 RandomProvider 提供，测试可通过 TestGameClock 与固定 seed 重现。
4. **可验收**：每阶段功能必须真实改变数据，关闭重开后状态正确，异常输入不破坏状态，核心逻辑具备自动测试。

## 2.2 技术环境约束

|项目|约束|
|---|---|
|Java|17|
|JavaFX|17|
|Maven|3.9+|
|SQLite|3.x|
|SQLite JDBC|3.x|
|JSON|Jackson 2.17+|
|测试|JUnit 5|
|版本管理|Git|
|正式数据库路径|data/farm.db，不得放入 resources|

> 具体操作系统发布范围由 P4 打包验收确定，本文不自行扩展。

## 2.3 关键业务约束

- 正式时间比例：1 现实分钟 = 1 游戏小时，24 现实分钟 = 1 游戏日。
- DemoClock 用于开发/答辩，支持 ×12；TestGameClock 支持手动 advance。
- 单次离线模拟最多结算 72 游戏小时（3 游戏日）。
- P0 固定 WeatherRate = DecorationRate = EventRate = 1.0，后续加入系统不改变基础成长公式结构。
- 地图从 P0 起即使用最终 12×12 规格，中心 8×8 为种植区。
- P1 起 SQLite 为唯一正式运行存档；P0 JSON 仅为临时存档。
- P2 的离线模拟禁止维护第二套成长公式，必须复用在线世界规则。
- 传说品质不由普通品质分数直接产生，必须经过专门的 LegendaryService 判定流程。
- P3 毕业唯一判定为 FarmScore == 147；146 分不能毕业。

## 2.4 非功能目标

|质量属性|概要设计要求|
|---|---|
|稳定性|无效操作不得修改状态；关键事务失败不得形成半成功数据|
|存档可靠性|退出/重启状态一致；P2 离线模拟结果一次事务保存后再显示 UI|
|可测试性|时间与随机可注入/可控制；核心 Service 可脱离 JavaFX 测试|
|可维护性|单向分层依赖；Model 无复杂逻辑；DAO 无业务规则|
|可扩展性|阶段功能通过新增 Service/DAO 和配置扩展，不推翻 P0 基础模型和公式|
|可解释性|品质、传说、离线变化等重要结果应可由记录或 UI 解释|

---

# 3 总体架构设计

## 3.1 系统上下文

本项目是桌面端单机应用。运行时由一个 JavaFX 客户端进程、应用内部业务层、本地 SQLite 数据库以及 resources 下的静态配置/美术/音频资源共同构成，不依赖外部业务服务器。

玩家通过 JavaFX View 发起操作；Controller 接收事件并调用 Service；Service 根据 GameClock、RandomProvider、静态配置和当前 Model 执行业务规则；需要持久化时通过 DAO 访问 SQLite。P2 起，启动阶段会先加载存档并完成离线模拟，再打开 FarmView，以避免 UI 与后台状态短暂不一致。

## 3.2 分层架构
![[图3-1.drawio.svg]]<center>图3‑1 系统分层架构</center>

各层职责如下：

| 层/组件             | 职责                              | 禁止事项                |
| ---------------- | ------------------------------- | ------------------- |
| View             | JavaFX 界面显示、用户输入、状态呈现           | 不直接修改 Model；不计算游戏规则 |
| Controller       | 接收界面事件、参数整理、调用 Service、触发界面刷新   | 不写复杂规则；不写 SQL       |
| Service          | 游戏规则、状态迁移、业务编排、跨模块协作、事务边界       | 不把 UI 逻辑带入业务层       |
| DAO / Repository | 按实体访问 SQLite，完成 CRUD            | 不判断品质、成长、传说等业务规则    |
| Persistence      | SQLite 连接、事务、P0 JSON 临时存档等持久化实现 | 不成为第二套业务规则入口        |
| Model            | 保存当前状态与领域数据                     | 不自行决定成长、枯萎、传说、出售等行为 |

## 3.3 依赖方向

正式依赖方向保持： **View → Controller → Service → DAO/Repository → Persistence**

除此之外：

- Service 可依赖 Model、GameClock、RandomProvider 和 config 数据；
- DAO 可依赖 Model 的持久化映射，但不得反向调用 Service；
- View 不得持有 DAO；
- Model 不得依赖 JavaFX；
- OfflineSimulationService 只负责离线时间窗口编排，领域规则必须委托 WorldSimulationService 与其他领域 Service。

## 3.4 逻辑模块划分

项目业务按现有 5 人分工抽象为五个稳定领域：土地与作物、玩家与经营、品质与传说、世界环境、存档与收集。该划分来自现有需求和团队责任矩阵，便于并行开发，同时不改变统一分层架构。
![[图3-2.drawio.svg]]<center>图3‑2 五个业务领域及协作关系</center>

| 领域     | 核心职责                                  | 主要状态/服务                                                                                        | 首要协作者          |
| ------ | ------------------------------------- | ---------------------------------------------------------------------------------------------- | -------------- |
| 土地与作物域 | 地图、Soil 状态、播种、成长、浇水、枯萎                | Farm、Soil、Crop；LandService*、PlantingService、GrowthService、WateringService、WitherService        | 世界环境、玩家经营、品质传说 |
| 玩家与经营域 | 金币、种子库存、商店、装饰、Buff、套装、土地解锁            | Player；EconomyService、ShopService、DecorationService、BuffService、SetService、LandUnlockService   | 土地作物、存档收集      |
| 品质与传说域 | 品质、肥料、完整收获、传说突破、作物生命记忆、展示             | QualityService、FertilizerService、HarvestService、LegendaryService、MemoryService、ShowcaseService | 土地作物、玩家经营、存档收集 |
| 世界环境域  | 统一时间、天气、随机事件、在线/离线世界推进                | GameClock、RandomProvider、WeatherService、EventService、WorldTimeService、WorldSimulationService   | 全部业务域          |
| 存档与收集域 | JSON/SQLite、DAO、日志、图鉴、FarmScore、评价、毕业 | SaveService、LogService、CollectionService、FarmScoreService、FarmRankService、GraduationService    | 全部业务域          |

> LandService 来自项目决策记录 D07，是团队针对“开垦/土地回退”的实现层补充；R2 的“最终 Service 归属”表尚未同步列入，详见第 14 章。

## 3.5 包结构设计

逻辑包结构建议遵循脚手架的 `com.fieldstory.farm` 根包，并结合 README 中已经给出的 persistence/config 规划：

```
com.fieldstory.farm
├── model
│   ├── impl
│   └── enums
├── service
│   └── impl
├── dao
├── controller
├── view
├── persistence
├── config
├── factory
├── manager
└── util
```

物理规则：

- 核心实体与 Service 采用“接口 + 实现类”拆分；
- 接口放在 model / service 包根；
- 实现类以 Basic 前缀放在对应 impl 子包；
- 枚举不拆接口；
- DAO 命名为 `[实体]Dao`；
- Controller 与 View 只做协调和显示。

> 文档同步说明：README 的示例根路径使用 `com/fieldstory/`，而工程脚手架使用 `com.fieldstory.farm`。本说明书按“工程结构由脚手架负责”的原则采用 `com.fieldstory.farm`，README 需同步。

---

# 4 模块与 Service 概要设计

## 4.1 P0 Service

P0 的目标是完整经营闭环：开垦→买种→播种→成长→浇水→成熟→收获→出售→再种，并支持退出保存和继续游戏。

|Service|职责概要|主要输入/输出|关键约束|
|---|---|---|---|
|LandService*|开垦、收获/铲除后的土地回退|Soil、金币检查结果 → SoilState|D07 项目决策；不得与 P3 LandUnlockService 混同|
|GrowthService|根据经过的游戏时间推进 growthProgress / GrowthStage|Crop、时间窗口、倍率 → 新成长状态|P0 Weather/Decoration/Event 均固定 1.0；支持非整日成长|
|WateringService|判断是否允许主动浇水并记录有效次数|Crop、游戏日 → 浇水结果|每游戏日最多一次有效；成长加成有封顶|
|EconomyService|金币、种子库存、购买与基础售价|Player、CropType、数量|播种不直接扣金币；种子库存与金币分离|
|PlantingService|校验土地与种子并创建作物|TILLED Soil、CropType|不直接访问 Player 内部状态，跨域库存通过 EconomyService|
|BasicHarvestService|P0 基础收获与出售|MATURE Crop → 金币、TILLED Soil|P2 由 HarvestService 替代|
|SaveService|P0 JSON 临时保存/加载接口|当前玩家、农场、作物等状态|P0 退出后不进行离线推进|

## 4.2 P1 新增 Service

|Service|职责概要|关键协作|
|---|---|---|
|WeatherService|每游戏日生成并提供天气影响|GameClock、RandomProvider、GrowthService|
|WitherService|在线枯萎判定与状态变化|Weather、Crop、Buff|
|QualityService|收获时计算普通品质分/普通品质|Crop 经历、操作、天气、装饰、事件|
|FertilizerService|施肥操作、次数限制及对应成长/品质影响|Crop、Player 肥料资源|
|BuffService|汇总已放置装饰对目标作物的有效 Buff|Decoration、Farm 布局|
|ShopService|完整商店业务入口|EconomyService、DecorationService|
|DecorationService|装饰购买/放置/调整与合法性校验|Farm 布局、EconomyService、BuffService|

> P1 同时完成 SQLite 正式存档和 DAO 层落地，但不改变 P0 已有经营语义。

## 4.3 P2 新增/升级 Service

|Service|职责概要|关键约束|
|---|---|---|
|WorldTimeService|世界时间/游戏日边界协作|正式时间比例不变|
|WorldSimulationService|在线与离线共同使用的世界规则执行器|成长、天气、事件、补水、枯萎等共用一套领域逻辑|
|OfflineSimulationService|计算离线窗口并分段调用 WorldSimulationService|单次最多 72 游戏小时；不得拥有独立成长公式|
|EventService|每日随机事件生成、持续期与效果|随机统一使用 RandomProvider|
|LegendaryService|专属传说条件与突破概率判定|先特殊条件，再 Score，再概率|
|MemoryService|记录真实成长经历并生成可追溯生命故事|CropMemory 不能因当前 crop 删除而消失|
|HarvestService|完整收获事务编排，正式替代 BasicHarvestService|品质、传说、经济、记忆、日志、土地回退必须原子完成|
|LogService|HarvestLog / EventLog / OfflineLog 等日志写入与读取|支持离线解释与追溯|

## 4.4 P3 新增 Service

|Service|职责概要|关键约束|
|---|---|---|
|CollectionService|作物品质图鉴、装饰图鉴状态管理|作物品质 15 项；COLLECTED 永久保存|
|SetService|三套装饰套装的收集/激活状态判断|“拥有”和“当前激活”需分离|
|FarmScoreService|计算唯一 FarmScore|由装饰、图鉴、传说、套装构成，总分 147|
|FarmRankService|根据 FarmScore 映射 8 级农场评价|永恒花园仅在 147|
|ShowcaseService|展示已获得传说作物的历史 CropMemory|展示的是历史记录，不是当前活 Crop|
|LandUnlockService|P3 解锁 LOCKED 土地|价格从 balance‑config 读取；LOCKED→EMPTY|
|GraduationService|首次达到毕业条件时写入毕业状态并触发毕业流程|仅首次动画；后续保持永恒花园状态|

## 4.5 Controller 与 View

当前文档已明确的主要界面/控制器包括：

|Controller/View|概要职责|
|---|---|
|FarmController / FarmView|农场主场景、格子选择、土地/作物操作、时间与状态信息展示|
|ShopController / ShopView|种子、装饰等商店入口|
|CollectionController / CollectionView|作物/装饰图鉴、目标进度、FarmScore 等收集信息|
|ShowcaseView|传说作物历史记录与生命故事展示|
|OfflineLogView|P2 启动后展示离线期间发生的变化|

> 未在源文档中明确命名的 Controller 不在概要设计中自行新增，留给详细设计按界面实际需要确定。

## 4.6 Factory、Manager 与 Util

脚手架预留：

- **factory**：对象创建，如 CropFactory、DecorationFactory；
- **manager**：项目级组装与场景管理，如 GameManager、SceneManager；
- **util**：通用工具，如 JsonConfigLoader、AnimationUtil。

> 这些组件只解决对象装配、场景切换和通用技术问题，不允许变成绕开 Service 的第二业务入口。

---

# 5 核心类与静态关系设计

## 5.1 核心领域对象

|Model|主要状态|生命周期/职责|
|---|---|---|
|Player|gold、fertilizer、seedInventory 等|玩家资源与持有状态|
|Farm|score、level、地图/布局|农场整体聚合状态|
|Soil|row、column、SoilState、crop|仅 FARM_PLOT 拥有 Soil；承载土地状态|
|Crop|cropUuid、type、growthStage、growthProgress、时间、浇水/施肥/天气经历等|当前土地上的活作物状态|
|Decoration|类型、位置及配置引用|已放置装饰及其布局状态|
|WorldState|currentWorldTime、logoutRealTime、currentWeather、currentGameDay、randomSeed|持续世界与随机可复现的核心状态|
|ActiveEvent|eventType、start/endWorldTime、targetCropType、payload|当前持续事件，需跨退出保存|
|CropMemory|cropUuid 与真实经历记录|作物当前对象被移除后仍保留的历史档案|
|Collection|图鉴及收集状态|P3 长期收集进度|

## 5.2 核心枚举

|枚举|核心值/用途|
|---|---|
|FarmPlot|FARM_PLOT、DECORATION_AREA、SHOP、SHOWCASE|
|SoilState|EMPTY、TILLED、PLANTED、LOCKED|
|CropType|WHEAT、CORN、CARROT|
|GrowthStage|SEED、SPROUT、GROWING、MATURE、WITHERED|
|Weather|晴天、雨天、干旱、绿雨|
|Quality|COMMON、EXCELLENT、RARE、EPIC、LEGENDARY|
|EventType|流星夜、神秘商人、小动物来访、彩虹日等对应枚举值|
|CollectionState|UNDISCOVERED、DISCOVERED、COLLECTED|

## 5.3 类关系原则

依据课程材料中的 UML 关系定义，本项目概要类关系建议按以下语义理解：

- Farm 与核心 Soil 网格属于整体‑部分关系；
- Soil 长期持有当前 Crop 的 0..1 引用；
- Player 与 Farm 为长期业务关联；
- WorldState 持有当前世界时间/天气/随机状态，并与 ActiveEvent 协作；
- CropMemory 与当前 Crop 不同生命周期，历史记录不得随 Crop 从当前土地删除而丢失；
- Service 对 Model/DAO 多数属于依赖关系，而不是让 Model 反向依赖 Service；
- 接口与 Basic 实现类属于 realization（实现）关系。

---

# 6 状态机与核心规则对架构的约束

## 6.1 土地与作物状态机

### 土地状态

- **LOCKED**：不可开垦、播种、放置装饰；P3 解锁后转为 EMPTY。
- **EMPTY**：普通空地；开垦支付 5 金币后转为 TILLED。
- **TILLED**：可播种；不存在 Crop，因此不能浇水/施肥/收获。
- **PLANTED**：持有 Crop；操作能力由 Crop 的 GrowthStage 决定。
- 收获或主动铲除 WITHERED 作物后，土地回到 TILLED。

### 作物状态

- **SEED**：0%≤progress<20%，不主动参与枯萎判定；
- **SPROUT**：20%≤progress<50%，开始接受浇水、施肥、天气、装饰、事件和枯萎检测；
- **GROWING**：50%≤progress<100%；
- **MATURE**：progress≥100%，成长进度封顶 100，可收获，但长期极端干旱仍可能枯萎；
- **WITHERED**：不可收获、不可出售，需玩家免费铲除。

> 收获是玩家行为，不存在 HARVEST/HARVESTED 成长阶段。

## 6.2 时间与成长设计

GrowthService 不使用“每天 00:00 才成长”的离散实现，而按实际经过的游戏时间计算。统一公式结构为：

> GrowthDelta = BaseDailyProgress × 时间比例 × WeatherRate × DecorationRate × OperationRate × EventRate。

P0 保持 WeatherRate、DecorationRate、EventRate 均为 1.0；P1/P2 只替换对应倍率提供者，不重写基础公式。所有 Service 获取时间必须通过 GameClock，不得散落系统时间调用。

## 6.3 世界模拟一致性

P2 的关键架构约束是**“在线/离线一套规则”**。OfflineSimulationService 负责：

1. 计算实际离线时长；
2. 按 72 现实分钟上限裁剪；
3. 按时间窗口/游戏日边界分段；
4. 反复调用 WorldSimulationService；
5. 保存结果和离线日志。

> 天气生成、成长、自动雨水补水、droughtStreak、枯萎、事件开启/关闭等领域规则必须与在线流程复用相同逻辑，以避免“上线一种结果、离线另一种结果”。

## 6.4 品质与传说边界

普通品质由 QualityService 在收获时根据基础、天气、操作、装饰、事件和随机分综合计算；LEGENDARY 不能通过普通分数区间直接得到。 LegendaryService 的统一流程为：

> 特殊经历条件 → Score 门槛 → 突破概率 → 最终传奇结果。

> 因此 P1 的 QualityService 必须在架构上预留与 P2 LegendaryService 的组合点，但 P1 本身不得提前生成 LEGENDARY。

## 6.5 FarmScore 与毕业

FarmScore 统一由四部分组成：

- 14 种不同装饰：14×3=42；
- 15 项作物品质图鉴：15×2=30；
- 3 种传说作物：3×10=30；
- 3 套装饰套装：3×15=45；
- **总分：147**。

> GraduationService 只能在 FarmScoreService 确认 FarmScore == 147 后执行首次毕业流程。FarmRank 是展示/评价映射，不可替代毕业条件。

---

# 7 数据与持久化概要设计

## 7.1 持久化演进策略

|阶段|存档形式|设计目的|
|---|---|---|
|P0|JSON 临时存档|验证核心闭环“退出保存→重新打开继续”；退出后不做离线推进|
|P1|SQLite 正式存档 + JSON 迁移|SQLite 成为唯一正式运行存档，为 P2 持续世界提前保存 world_state|
|P2|SQLite 扩展事件、记忆、日志|支持离线模拟、事件持续、生命记录与解释日志|
|P3|SQLite 扩展收集/套装/展示|支持长期收集、评价、毕业与展示|
|P4|数据安全与回归|验证异常关闭恢复、完整回归和发布环境|

## 7.2 数据表规划

### P1 最低表

player、farm、soil、crop、decoration、world_state

> world_state 在 P1 即至少预留：current_world_time、last_real_time、current_weather、current_day_index、random_seed，为 P2 离线模拟避免推翻结构。

### P2 新增

active_event、event_log、offline_log、crop_memory

> active_event 至少需要 event_type、start_world_time、end_world_time、target_crop_type、payload；crop_memory 以 cropUuid 维持历史身份。

### P3 新增

collection、set_collection、showcase

> GraduationState 的具体落表方式在现有数据库设计规范中尚未给出，应在详细数据库设计阶段确认，不在本文臆造表名。

## 7.3 数据所有权与访问边界

|数据|业务所有者|持久化访问|
|---|---|---|
|Player 资源|玩家与经营域|PlayerDao|
|Farm/布局|土地与作物域 / 玩家经营域|FarmDao|
|Soil|土地与作物域|SoilDao|
|Crop|土地与作物域|CropDao|
|Decoration|玩家与经营域|DecorationDao|
|WorldState|世界环境域|WorldStateDao|
|ActiveEvent|世界环境域|ActiveEventDao|
|CropMemory|品质与传说域|CropMemoryDao|
|EventLog / OfflineLog|世界环境/存档收集域|EventLogDao / OfflineLogDao|
|Collection|存档与收集域|CollectionDao|
|SetCollection|存档与收集域|SetCollectionDao|
|Showcase|品质传说/存档收集域|ShowcaseDao|

## 7.4 静态配置设计

统一使用以下四个文件，位置为 `src/main/resources/config/`：

|配置文件|内容边界|
|---|---|
|crop‑config.json|作物基础成长时间、种子价格、基础售价、静态品质基础信息等|
|decoration‑config.json|装饰价格、尺寸、效果等静态数据|
|event‑config.json|随机事件相关静态配置|
|balance‑config.json|开垦费用、土地解锁价格、初始资源等平衡参数|

> 静态 JSON 只保存程序配置，不保存玩家运行存档。项目决策 D12 规定 P0 CropType 数值结构先与配置结构保持一致，真正配置加载不得晚于 P1 商店上线。

## 7.5 事务边界

以下流程必须按**“全成功或全失败”**处理：

- P2 完整收获事务；
- P2 离线模拟结果落库；
- P3 首次毕业状态写入及相关最终状态保存；
- 涉及金币扣除与土地/装饰/解锁状态同时变化的关键购买流程。

> 其中“购买流程事务化”的具体 DAO 事务实现由详细设计确定；本文只规定一致性目标，不新增数据库机制。

## 7.6 数据安全

正式数据库固定为 `data/farm.db`，不得放入 src/main/resources，也不得提交用户数据库、farm.db‑shm、farm.db‑wal 等运行数据到 Git。P4 回归必须覆盖启动、保存、退出和异常关闭恢复。

---

# 8 接口与协作设计

## 8.1 View → Controller

View 只传递用户意图和界面必要参数，例如“点击某格”“选择某种子”“执行浇水”“点击收获”。Controller 不应直接修改 Player、Crop 或 Soil，而应调用相应 Service，并根据返回结果刷新显示和提示。

## 8.2 Controller → Service

Controller 只承担：

- 参数合法性基础检查；
- 调用正确业务 Service；
- 将 Service 结果转换为界面状态；
- 不重复实现 Service 中已有规则。

## 8.3 Service → Service 的关键边界

现有项目决策已经确定若干跨模块接口：

- PlantingService 消耗种子通过 EconomyService 的库存查询/消耗接口完成，不直接访问 Player 内部字段；
- P0 BasicHarvestService 完成收获后，通过土地模块的回退能力将 Soil 恢复为 TILLED；P2 该职责由 HarvestService 编排；
- OfflineSimulationService 不实现成长/天气算法，只编排 WorldSimulationService；
- HarvestService 负责协调 QualityService、LegendaryService、EconomyService、MemoryService、LogService 及土地状态回退；
- FarmScoreService 负责分数真值，FarmRankService 和 GraduationService 都依赖它的结果，不各自重复算分。

## 8.4 Service → DAO

Service 只能通过 DAO/Repository 访问正式数据库。DAO 接口应围绕实体持久化设计，不出现 calculateQuality、canWither、isLegendary 等规则型方法。

## 8.5 配置、时间、随机基础接口

- **GameClock**：所有游戏时间的唯一来源；
- **RandomProvider**：所有随机行为的统一来源；
- **JsonConfigLoader / Config 对象**：静态配置读取入口；
- **DatabaseService**：SQLite 初始化与连接；
- **SaveService**：阶段化存档门面，P0 对应 JSON、P1 起对应 SQLite 正式存档语义。

---

# 9 关键动态流程设计

## 9.1 启动与离线模拟流程


关键保证：

- UI 打开时 Model 已是最终离线结算后的状态；
- 离线窗口最多 72 现实分钟对应的游戏时间；
- 模拟结果事务保存；
- 离线日志可解释世界变化；
- 固定 randomSeed 时，同样存档和离线时长应可复现同样的随机序列。

## 9.2 在线经营主流程

典型在线流程：

1. 玩家在 FarmView 选择土地；
2. FarmController 调用 LandService 完成开垦，或调用 PlantingService 完成播种；
3. GameClock 推进，GrowthService/WorldSimulationService 根据时间窗口更新作物；
4. 玩家可调用 WateringService、FertilizerService 等改变操作状态；
5. P1 起 WeatherService、BuffService、WitherService 共同影响成长与风险；
6. Crop 达到 MATURE 后进入可收获状态；
7. P0 由 BasicHarvestService 完成基础出售；P2 起统一进入完整 HarvestService 事务。

## 9.3 完整收获事务

流程必须保证：检查成熟、计算品质、判定传说、计算售价、发放金币/肥料、更新记忆/日志、清除当前 Crop、恢复 Soil=TILLED 在同一次业务事务中完成。任一步骤异常时不得出现“钱已增加但作物仍在”或“作物删除但记忆未保存”等半成功状态。

> P3 上线 CollectionService 后，图鉴/传说收集等长期收集状态也应接入完整收获结果，但不得破坏 P2 收获事务的一致性。

## 9.4 毕业流程

第一次达到 FarmScore=147：

1. FarmScoreService 确认分数；
2. FarmRank 更新为永恒花园；
3. GraduationService 写入毕业状态；
4. 播放毕业 UI；
5. 解锁完整统计；
6. 保存。

> 首次毕业动画只触发一次；之后重启仍保持永恒花园状态。

## 9.5 保存与恢复流程

- P0：玩家操作后按 SaveService 写 JSON；退出后世界不推进；
- P1：所有正式状态写 SQLite，JSON 仅用于迁移；
- P2：退出记录 logoutRealTime、currentWorldTime、当前天气/事件、作物及玩家资源；
- 下次启动计算真实离线时长，模拟完成后再写回并打开界面。

---

# 10 阶段化架构演进

## 10.1 阶段路线

|阶段|Tag|架构重点|主要交付|
|---|---|---|---|
|骨架|v0.0.1‑skeleton|Maven/JavaFX/目录/SQLite依赖/测试框架|工程可启动|
|P0|v0.1.0‑core|GameClock、基础 Model/Service、JSON 临存|核心经营闭环|
|P1|v0.2.0‑playable|Weather/Quality/Decoration 等策略 Service + DAO + SQLite|可策略经营|
|P2|v0.3.0‑feature|WorldSimulation、OfflineSimulation、Event、Memory、Legendary、完整收获事务|持续世界特色完整|
|P3|v0.4.0‑collect|Collection、Set、FarmScore、Showcase、LandUnlock、Graduation|长线收集与毕业|
|P4|v1.0.0‑release|UI/动画/音效/平衡/全量测试/打包|正式发布|

## 10.2 阶段禁止依赖

- P0：GrowthService 不得依赖尚未存在的 WeatherService；使用 WeatherRate=1。
- P1：QualityService 不得“偷偷生成” LEGENDARY。
- P2：OfflineSimulationService 不得拥有独立成长公式。
- P3：完成三套装不能直接将 Rank 设为永恒花园；唯一条件仍是 FarmScore==147。
- P4：不得为赶进度修改核心机制；只修 Bug、调配置、优化体验。大型规则修改必须回到规则文档评审。

## 10.3 向后兼容原则

每个阶段新增系统时应保留前一阶段的有效业务语义：

- P1 替换完整 ShopView，不重写 P0 EconomyService 的购买语义；
- P1 SQLite 替代 P0 JSON 作为正式存档，但不改变玩家资源和作物状态语义；
- P2 HarvestService 升级 P0 BasicHarvestService，但必须保持“成熟才能收获、收获后土地 TILLED”等基础行为；
- P2 世界模拟扩展成长环境，不改正式时间比例；
- P3 收藏/毕业只消费既有收获与装饰结果，不修改 P0‑P2 核心培养规则。

---

# 11 异常、日志与一致性设计

## 11.1 无效操作处理

无效操作必须**“失败但不改状态”**。典型场景：

- EMPTY 直接播种；
- TILLED 浇水/施肥/收获；
- PLANTED 再次播种；
- LOCKED 开垦；
- 非 FARM_PLOT 种植；
- 金币不足购买/开垦；
- 同游戏日重复有效浇水或施肥；
- 非 MATURE 作物收获。

> Controller 负责展示错误信息，Service 负责保证数据没有被部分修改。

## 11.2 日志设计

P2 起至少存在：

- **EventLog**：记录事件发生；
- **OfflineLog**：按离线过程/游戏日解释世界变化；
- **CropMemory**：长期生命经历；
- **HarvestLog**：完整收获结果记录（规则流程明确要求）。

> 日志的主要目标是可解释、可追溯和测试复现，而不是替代正式状态表。

## 11.3 随机一致性

所有随机行为通过 RandomProvider，WorldState 保存 randomSeed。固定 seed 的测试必须可以重现：天气序列、事件序列、品质随机分、枯萎结果和传奇突破。**禁止各 Service 独立 new Random() 形成无法复现的行为。**

---

# 12 可测试性与质量保证设计

## 12.1 测试分层

|层次|重点|
|---|---|
|单元测试|GrowthService、WateringService、QualityService、LegendaryService、FarmScoreService 等纯业务规则|
|DAO 测试|实体 CRUD、事务写入、迁移后数据一致性|
|集成测试|Controller→Service、Service 间协作、完整收获、离线模拟|
|里程碑验收|严格按 P0‑P4 逐级验收，不以前置 UI 壳替代真实逻辑|
|回归测试|P4 全量覆盖时间、土地、作物、操作、天气、品质、装饰、事件、收集、存档|

## 12.2 关键可测试性设计

- RealGameClock、DemoGameClock、TestGameClock 三种实现分离；
- TestGameClock 可手动推进，自动测试无需真实等待；
- RandomProvider 可注入固定 seed；
- Service 不依赖 JavaFX，因此可以独立 JUnit 测试；
- OfflineSimulationService 与在线 WorldSimulationService 共用逻辑，使一致性测试可直接对比；
- HarvestService 的事务可测试成功路径和异常回滚路径。

## 12.3 最低关键场景

必须覆盖：

- 6 游戏小时非整日成长；
- 同游戏日重复浇水不增加 manualWaterCount；
- 干旱→晴天→干旱不形成连续 droughtStreak；
- 离线 8 小时只推进 72 游戏小时；
- 传说条件满足但概率失败时落回 EPIC；
- 在线与离线在相同初始状态/seed/时间窗口下关键结果一致；
- 图鉴、套装、展示台状态持久化；
- FarmScore=146 不毕业、147 毕业；
- 异常关闭后 SQLite 状态可恢复。

---

# 13 团队协作与模块责任设计

现有 5 人分工如下，概要设计以其作为主要“模块领地”，同时要求跨模块只通过约定接口协作。

|成员|模块领地|P0|P1|P2|P3|P4|
|---|---|---|---|---|---|---|
|A: lyj|土地与作物|土地状态机、播种、成长、浇水|枯萎|持续世界引擎|—|播种/浇水/生长/成熟动画|
|B: hsy|玩家与经营|金币、买种子、经济|商店、装饰+Buff|离线模拟+离线日志|套装、土地解锁|数值平衡|
|C: hy|品质与传说|基础收获|品质、肥料|完整收获、传说、生命记忆|展示台|品质/传说动画|
|D: zsl|世界环境|GameClock、RandomProvider、状态栏|天气|随机事件|—|天气/事件动画、音效|
|E: hyt|存档与收集|JSON存档、GameManager、场景组装、Git|SQLite+6 DAO+迁移|记忆/事件持久化|图鉴、FarmScore/FarmRank、毕业|CSS统一、打包、回归测试|

## 13.1 协作规则

- 每个成员维护自己模块内的 Model/Service/测试，跨模块通过接口调用；
- 业务规则发生争议时先查 R1，再查 R2，不通过“代码先写再说”解决；
- DAO/数据库表由 E 负责集成，但数据语义由对应业务域共同确认；
- B 的离线模拟必须复用 A/D 的世界领域规则，不复制算法；
- C 的收获事务通过已定义接口调用 A/B/E 的能力，不直接修改其他模块内部字段；
- Git 合并前执行前阶段回归测试，避免阶段新增导致基础闭环回归。

---

# 14 AI 使用与核对说明

## 14.1 AI 在本项目中的定位

AI 是辅助分析与实现工具，不是规则权威，也不是最终设计决策者。

允许 AI 承担：

- 阅读和整理现有规则/需求；
- 生成实现计划、概要设计草稿、测试清单和文档结构；
- 在明确接口和规则后辅助编写代码；
- 对多个文档做一致性扫描；
- 根据已确定规则生成可复现的测试建议。

AI 不得：

- 修改规则文档中的数值；
- 自行发明未定义的玩法；
- 越过 P0→P4 阶段边界；
- 遇到文档冲突时静默选择“看起来更合理”的方案；
- 用通用游戏经验覆盖项目明确规定。

## 14.2 项目已有 AI 协作规则

《项目需求分析与开发计划书》规定：

- 组员新任务应新建 AI 对话；
- 使用工作区《ai首先阅读.md》V1.1 作为标准会话开头模板；
- AI 写代码前必须先输出“实现计划”；
- 任务完成后附“溯源说明”，标记关键规则来源；
- 发现文档矛盾必须停止并报告。

> 本次上传材料未包含《ai首先阅读.md》正文，因此本文只引用上述已提供规则，不重新编造该模板内容。

## 14.3 本说明书的 AI 使用范围

本说明书由 AI 辅助完成以下工作：

1. 对 R1‑R6 的项目资料进行结构化整理；
2. 以 R7/R8 为方法与格式参考，组织“模块—类—关系—流程—数据”概要设计结构；
3. 对 Service、DAO、数据表、阶段边界和团队分工进行交叉核对；
4. 绘制分层架构、模块关系、领域对象、状态机、启动/离线和收获流程图；
5. 形成冲突/同步事项清单。

> AI 没有修改 R1 中任何游戏数值，也没有新增新的玩法系统。

## 14.4 核对方法

采用“四层核对法”**：

|核对层|核对内容|主要来源|
|---|---|---|
|规则核对|时间、状态机、成长、品质、传说、FarmScore、毕业条件|R1|
|阶段核对|P0‑P4 上线边界、禁止依赖、Service/DAO 归属、验收顺序|R2|
|工程核对|Java/JavaFX/Maven/SQLite、分层、包结构、接口实现约定、资源路径|R4 + R5|
|项目协作核对|决策记录、团队分工、AI 会话规则|R3 + R6|

> 对每个关键设计结论至少执行以下检查之一：
> 
> - 在权威文档中找到直接定义；
> - 由两个文档相互印证；
> - 若只有低优先级文档支持且与高优先级文档未同步，则标记“需同步/待确认”。

## 14.5 人工复核清单

提交课程答辩或进入详细设计前，团队应至少人工确认：

- ☐ R1 仍为当前唯一规则事实源，版本号未变化；
- ☐ R2 的最终 Service/DAO 归属与本文一致；
- ☐ D01‑D13 的团队决策是否已正式拍板并同步到上游文档；
- ☐ com.fieldstory.farm 根包和 persistence/config 物理目录是否与实际仓库一致；
- ☐ SQLite 详细字段、主键、外键、索引、事务由后续数据库设计规范确认；
- ☐ GraduationState 的正式持久化位置已确定；
- ☐ 《ai首先阅读.md》V1.1 正文仍与项目 AI 协作规则一致；
- ☐ 5 人责任人和接口负责人未发生变化；
- ☐ 生成类图/流程图与实际代码结构同步更新。

## 14.6 文档冲突与同步事项

本次核对发现以下需要显式管理的文档差异：

**S01：LandService 未同步到 R2 最终 Service 归属表**

- R2“最终 Service 归属”P0 未列 LandService；
- R3 决策 D07 明确新增 LandService，并用于开垦和土地回退；
- 本说明书采用 D07 作为团队实现层决策，不视为新增玩法系统；
- 建议：在 R2 或工程开发规范中补充该服务归属，避免后续 AI/成员按旧表遗漏。

**S02：根包路径表达不一致**

- R4 使用 com.fieldstory.farm；
- R5 示例目录使用 com/fieldstory/；
- 本说明书采用 R4，并要求 README 同步。

**S03：物理 persistence/config 包在文档中的完整度不同**

- 固定逻辑架构包含 Persistence；
- R5 给出 persistence/ 和 Java config/ 示例；
- R4 主目录树未显式列出这两个 Java 包，但资源 config 已存在；
- 本说明书保留逻辑 Persistence 层，并将物理包作为当前建议；最终以实际仓库/工程开发规范确认。

**S04：README 静态配置清单缺少 event‑config.json**

- R3 决策 D01 与 R4 已统一为四个配置文件；
- README 的配置表仅列 crop‑config、decoration‑config、balance‑config；
- 本说明书采用 D01/R4 的四文件方案，README 需同步补充 event‑config.json。

**S05：数据库详细设计文档尚未建立**

- R4 明确“数据库设计规范待创建”。本文只规划表和数据边界，不自行补充未定义字段、外键、索引或迁移版本号。

**S06：AI 标准模板正文未随本批资料提供**

- R3 引用《ai首先阅读.md》V1.1，但本批材料未包含该文件。本文无法核对其模板正文，只能核对 R3 中明确写出的 AI 使用约束。

---

# 15 需求—设计追踪矩阵

|需求/目标|主要设计模块|关键 Service/组件|阶段|
|---|---|---|---|
|核心经营闭环|土地作物 + 玩家经营|Land/Planting/Growth/Watering/Economy/BasicHarvest|P0|
|天气与策略|世界环境 + 品质传说|Weather/Wither/Quality/Fertilizer/Buff|P1|
|装饰与商店|玩家经营|Shop/Decoration/Buff|P1|
|正式持久化|存档收集|Save + 6 DAO + SQLite|P1|
|持续世界|世界环境|GameClock/WorldTime/WorldSimulation|P2|
|离线模拟|世界环境 + 存档收集|OfflineSimulation/WorldSimulation/Log|P2|
|随机事件|世界环境|EventService/RandomProvider/ActiveEventDao|P2|
|生命记忆|品质传说 + 存档收集|MemoryService/CropMemoryDao|P2|
|传说突破|品质传说|LegendaryService/HarvestService|P2|
|图鉴与套装|存档收集 + 玩家经营|CollectionService/SetService|P3|
|展示台|品质传说|ShowcaseService/ShowcaseDao|P3|
|土地扩展|玩家经营|LandUnlockService|P3|
|永恒花园|存档收集|FarmScore/FarmRank/Graduation|P3|
|正式发布|全模块|UI/动画/音效/回归/打包|P4|

---

# 16 结论

本概要设计以“规则唯一、阶段明确、分层固定、状态与行为分离、在线离线共用规则、存档可恢复、随机可复现”**为核心架构原则。系统从 P0 即建立稳定的地图、作物、时间、经济和存档基础，P1‑P3 通过新增 Service、DAO 与配置逐级扩展，而不推翻早期核心模型；P4 只做发布级优化和回归验证。

该结构适合 5 人团队按领域并行开发，也便于课程答辩从“需求如何推导出模块和类、模块如何协作、系统如何保证一致性和可测试性”进行说明。进入详细设计前，应优先完成第 14.6 节文档同步事项及数据库详细设计确认。

---

# 附录 A 最终 Service 清单

## A.1 P0

- LandService*（项目决策 D07，待同步 R2）
- GrowthService
- WateringService
- EconomyService
- PlantingService
- BasicHarvestService
- SaveService

## A.2 P1 新增

- WeatherService
- WitherService
- QualityService
- FertilizerService
- BuffService
- ShopService
- DecorationService

## A.3 P2 新增/升级

- WorldTimeService
- WorldSimulationService
- OfflineSimulationService
- EventService
- LegendaryService
- MemoryService
- HarvestService（替代 BasicHarvestService）
- LogService

## A.4 P3 新增

- CollectionService
- SetService
- FarmScoreService
- FarmRankService
- ShowcaseService
- LandUnlockService
- GraduationService

# 附录 B 最终 DAO 清单

## B.1 P1

- PlayerDao
- FarmDao
- SoilDao
- CropDao
- DecorationDao
- WorldStateDao

## B.2 P2

- ActiveEventDao
- EventLogDao
- OfflineLogDao
- CropMemoryDao

## B.3 P3

- CollectionDao
- SetCollectionDao
- ShowcaseDao
