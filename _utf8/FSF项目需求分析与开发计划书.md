# 《田野物语 · 三韵集》

# 项目需求分析与开发计划书

# 1. 项目概述

## 1.1 项目背景

《田野物语 · 三韵集》（Field Story Farm）是一款基于 Java 17 + JavaFX 17
开发的桌面端单机农场模拟经营游戏。

项目围绕土地经营、作物培养、天气策略、品质控制、传说突破、装饰建设、生命记忆与收藏成长展开，采用
P0→P4 五阶段逐级交付。

《完整游戏规则设计文档
V4.0》为唯一游戏规则事实源，其他需求、开发计划、测试说明均需以其为准。

## 1.2 目标用户

-   课程答辩评委：关注需求完整性、系统设计合理性、验收可验证性。
-   5人开发团队：关注模块边界、任务拆分和交付路线。

## 1.3 核心价值

-   持续世界时间系统；
-   作物培养与品质策略；
-   传说突破；
-   装饰建设；
-   生命记忆；
-   收藏成长。

## 1.4 最终目标「永恒花园」

玩家必须完成以下 9 项条件：

1.  解锁并经营农场。
2.  获得三种基础作物。
3.  收集三种作物的四种普通品质。
4.  培养三种专属传说作物。
5.  完成15个作物品质图鉴。
6.  购买全部14种装饰。
7.  完成3套装饰套装。
8.  收集3种传说作物。
9.  将 FarmScore 提升至147。

达到全部条件后触发「永恒花园」。

------------------------------------------------------------------------

# 2. 需求分析

## 2.1 功能需求

## P0：v0.1.0-core 核心经营闭环

目标：

开垦 → 买种 → 播种 → 浇水 → 成长 → 收获 → 出售 → 再种。

### 功能列表

  ----------------------------------------------------------------------------------------------------------
  功能              描述                           依赖系统                   验收标准
  ----------------- ------------------------------ -------------------------- ------------------------------
  土地经营          12×12地图，中心8×8种植区，FarmPlot支持开垦   Soil、EconomyService、LandService  EMPTY土地消耗5金币变为TILLED

  种子购买          购买三种基础作物种子           EconomyService             金币减少，种子库存增加

  播种              消耗种子创建Crop               PlantingService            TILLED土地可播种

  成长              根据时间推进成长               GrowthService、GameClock   成长进度正确变化

  浇水              每日最多一次有效主动浇水       WateringService            重复浇水不增加效果

  收获              成熟后出售获得金币             BasicHarvestService        MATURE作物可收获
  ----------------------------------------------------------------------------------------------------------

### P0禁止实现清单

P0禁止实现：

-   随机天气；
-   品质系统；
-   肥料；
-   随机事件；
-   传说作物；
-   装饰Buff；
-   套装；
-   图鉴；
-   FarmScore；
-   离线推进；
-   枯萎；
-   SQLite正式存档。

P0中的世界环境固定：

-   WeatherRate = 1.0
-   DecorationRate = 1.0
-   EventRate = 1.0

目的：保证后续新增系统不会修改基础成长公式。

## P1：v0.2.0-playable 策略系统完整

新增：

-   天气系统；
-   品质系统；
-   肥料系统；
-   完整商店；
-   14种装饰；
-   5类Buff；
-   在线枯萎；
-   SQLite正式存档。

验收：

-   同一作物因浇水、施肥、装饰策略不同产生不同品质结果。

## P2：v0.3.0-feature 持续世界与特色系统

新增：

-   持续世界时间；
-   离线模拟；
-   离线日志；
-   随机事件；
-   CropMemory；
-   三种传说作物；
-   HarvestService完整事务。

验收：

-   退出后重新进入，世界变化可解释；
-   在线与离线使用同一成长规则。

## P3：v0.4.0-collect 收藏与毕业

新增：

-   作物图鉴；
-   装饰图鉴；
-   套装；
-   FarmScore；
-   8级评价；
-   展示台；
-   土地扩张；
-   永恒花园。

验收：

-   FarmScore=146不能毕业；
-   FarmScore=147触发毕业。

## P4：v1.0.0-release

新增：

-   UI全面美化；
-   动画；
-   音效；
-   数值平衡；
-   全量测试；
-   打包发布。

------------------------------------------------------------------------

## 2.2 非功能需求

-   性能：成长计算、存档操作不能阻塞界面。
-   稳定性：异常输入不能破坏游戏状态。
-   存档可靠性：关闭重启后状态一致。
-   可维护性：严格遵守分层架构。

------------------------------------------------------------------------

# 3. 系统设计

## 3.1 分层架构

View

↓

Controller

↓

Service

↓

DAO / Repository

↓

Persistence

规则：

-   View负责显示和输入。
-   Controller负责调用Service。
-   Service负责业务规则。
-   DAO负责数据访问。
-   Persistence负责SQLite和存档实现。
-   Model只保存状态。

## 3.2 Service归属

### P0

-   LandService
-   GrowthService
-   WateringService
-   EconomyService
-   PlantingService
-   BasicHarvestService
-   SaveService

### P1新增

-   WeatherService
-   WitherService
-   QualityService
-   FertilizerService
-   BuffService
-   ShopService
-   DecorationService

### P2新增/升级

-   WorldTimeService
-   WorldSimulationService
-   OfflineSimulationService
-   EventService
-   LegendaryService
-   MemoryService
-   HarvestService
-   LogService

说明：

BasicHarvestService由P2阶段HarvestService正式替代。

### P3新增

-   CollectionService
-   SetService
-   FarmScoreService
-   FarmRankService
-   ShowcaseService
-   LandUnlockService
-   GraduationService

------------------------------------------------------------------------

# 4. 数据设计

## 4.1 SQLite规划

规划数据表：

-   Player
-   Farm
-   Soil
-   Crop
-   Decoration
-   WorldState
-   ActiveEvent
-   CropMemory
-   Collection

具体字段与分阶段表清单（出处：验收规范第七十二、七十三、九十一、九十三、一百零四节）：

-   P1：player、farm、soil、crop、decoration、world_state；
-   P1 world_state：current_world_time、last_real_time、current_weather、current_day_index、random_seed；
-   P2：active_event（event_type、start_world_time、end_world_time、target_crop_type、payload）；
-   P2：crop_memory（cropUuid）；
-   P2：offline_log、event_log。

## 4.2 JSON配置规划

最终决策：

统一使用 crop-config.json、decoration-config.json、event-config.json、balance-config.json，依据 README 与验收规范，脚手架.md 示例将同步修正。

------------------------------------------------------------------------

# 5. 核心数值规则摘要

  项目           规则
  -------------- ---------------------
  正式时间       1现实分钟=1游戏小时
  游戏日         24现实分钟
  离线最大结算   72游戏小时
  DemoClock      ×12
  地图           12×12
  种植区         8×8
  开垦           5金币
  初始金币       500

## 作物

  作物       成长时间   种子价格   基础售价
  -------- ---------- ---------- ----------
  小麦        2游戏日         10         50
  玉米        3游戏日         15         70
  胡萝卜      4游戏日         20         60

## 天气

  天气     概率   倍率
  ------ ------ ------
  晴天      40%   ×1.0
  雨天      25%   ×1.5
  干旱      20%   ×0.5
  绿雨      15%   ×2.0

## 成长公式

GrowthDelta = 基础每日成长进度 × 时间比例 × WeatherRate × DecorationRate
× OperationRate × EventRate。

基础每日成长进度：

100 ÷ 基础成长天数。

## FarmScore

毕业条件：

FarmScore = 147。

------------------------------------------------------------------------

# 6. 开发计划

  阶段   任务         依赖   Tag
  ------ ------------ ------ -----------------
  骨架   工程初始化   无     v0.0.1-skeleton
  P0     经营闭环     骨架   v0.1.0-core
  P1     策略与存档   P0     v0.2.0-playable
  P2     持续世界     P1     v0.3.0-feature
  P3     收藏毕业     P2     v0.4.0-collect
  P4     发布优化     P3     v1.0.0-release

## 责任矩阵

| 角色 | 责任人 | 认领模块 |
|---|---|---|
|  |  |  |

责任人、认领模块与工时数字由团队后续填入。

------------------------------------------------------------------------

# 7. 测试计划

## 单元测试

-   成长公式测试。
-   GameClock时间推进测试。
-   浇水次数限制测试。
-   经济扣除与奖励测试。
-   存档读写测试。
-   品质计算测试。
-   传说突破判定测试。
-   FarmScore计算测试。

## 集成验收测试

### P0

-   完整种植循环。
-   保存重启恢复。

### P1

-   天气影响。
-   品质差异。
-   肥料和装饰影响。

### P2

-   离线模拟测试。
-   离线日志测试。
-   在线成长公式与离线模拟一致性验证。
-   传说突破完整流程测试。

### P3

-   图鉴收集测试。
-   套装完成测试。
-   展示台展示测试。
-   FarmScore毕业测试。

### P4

-   全流程回归测试。

------------------------------------------------------------------------

# 8. 风险与应对

## 技术风险

-   多系统影响成长公式。
-   应对：统一Service计算入口。

## 进度风险

-   P3/P4内容较多。
-   应对：优先保证阶段验收。

## 协作风险

-   多人修改同一模块。
-   应对：固定模块责任人与接口。

------------------------------------------------------------------------

# 决策记录

| 决策ID | 事项 | 最终决策 | 依据 | 拍板日期 | 需同步修改的文档 |
|---|---|---|---|---|---|
| D01 | JSON配置文件命名 | 统一使用 crop-config.json、decoration-config.json、event-config.json、balance-config.json | README 与验收规范 |  | 脚手架.md |
| D02 | SQLite分阶段表清单 | 按验收规范第七十二、七十三、九十一、九十三、一百零四节执行，详见本计划书4.1节 | 验收规范 |  | 本计划书4.1节 |
| D03 | P0收获接口命名 | 按验收规范：P0使用 BasicHarvestService，P2升级为 HarvestService | 验收规范 |  | 本计划书2.1、3.2 |
| D04 | 土地格类型命名 | 统一叫 FarmPlot | 验收规范、本计划书2.1 |  | 规则文档十一节、验收规范十二节、README |
| D05 | P0地图规格 | 12×12地图，中心8×8种植区；A建Farm模型、D画界面均须体现8×8种植区 | 验收规范 |  | 本计划书2.1 |
| D06 | Git分支名 | 统一用 dev | 仓库实际分支 |  | 相关文档 |
| D07 | P0开垦服务命名 | 新增 LandService（负责开垦、收获/铲除后的土地回退），加入 P0 Service 清单；与 P3 LandUnlockService 不冲突 | 计划书2.1；验收规范十五、十六 |  | 本计划书2.1、3.2 |
| D08 | 播种消耗种子的跨模块接口 | EconomyService 增加 getSeedCount(CropType) 与 consumeSeed(CropType, int)；PlantingService 只调这两个方法，不直接访问 Player | 验收规范十八、三十四；规则文档六十四 |  | 验收规范三十四（可选） |
| D09 | 收获后置 TILLED 的执行方 | C 调 A：BasicHarvestService 在“移除Crop→置TILLED”步骤调用 A 的 LandService.removeCropAndSetTilled(Soil)；P1 枯萎铲除复用同一方法 | 验收规范三十一、三十三；规则文档16.5；模块分工 |  | 无 |
| D10 | 外围格 P0 占位策略 | Farm 维护 12×12 的 FarmPlot[][]；中心 8×8（0-based 坐标(2,2)~(9,9)）为 FARM_PLOT 且持有 Soil；外围统一 DECORATION_AREA 占位；SHOP/SHOWCASE 枚举保留，P1/P3 再定具体格；坐标约定 0-based | 验收规范十一、十二；规则文档10.1、十一；决策 D05 |  | 无 |
| D11 | 浇水第6次行为与提示 | manualWaterCount≥5 后 canWater 直接返回 false（第5次仍有效，成长加成维持 +20% 封顶）；UI 提示建议“这株作物已经不需要浇水了” | 验收规范二十七、二十八；规则文档二十四 |  | 无（UI 文案为团队约定） |
| D12 | CropType 数值来源 | P0 枚举硬编码（字段结构与 crop-config.json 对齐：cropType/baseGrowthDays/seedPrice/baseSellPrice）；配置加载时点后移，不晚于 P1 商店上线，由 E 提供 JsonConfigLoader 后切换 | 验收规范十七；决策 D01；脚手架第八节 |  | 决策记录；加载时点由 E 后续决策 |
| D13 | model 实现类包位置 | 接口在包根，实现类以 Basic 前缀放 impl 子包（model.impl 与 service.impl 对称）；枚举不拆接口；E 注意 module-info 为 model.impl 增加对 Jackson 的 opens | 脚手架第七节；决策 D03 |  | 脚手架第七节或工程开发规范（待创建） |
| D14 | 时间类型与浇水哨兵 | A 侧时间字段统一 long：plantWorldTime=long 游戏小时（由 GameClock.getGameDay()×24+getGameHour() 适配计算）；lastManualWaterGameDay=long 游戏日（int 拓宽）。D 的 GameClock 无需新增 getWorldTime()。BasicCrop 字段默认 -1 哨兵（模型层兜底）+ CropFactory 显式设置 + E 适配层反序列化"无浇水记录"必须映射 -1 | 验收规范§八、§十七、§十八；规则文档§六；A 设计文档§7.3/§8.4/§9 |  | A 设计文档§7.3/§8.4/§9/§10/§11；通知 D（zsl）、E（hyt） |
| D15 | 世界时间存档字段名与类型 | 以 E 模块 GameState.currentWorldTime（String，ISO-8601）为唯一存档字段；D 模块仅通过 FarmGameModel.getWorldTimeTotalMinutes() 提供 int 值、restoreWorldTime(int) 接收，不直接读写 JSON 字段 | 验收规范 §41；E 模块文档 §3.1/D1；D 模块文档 §9.1/§11.2 |  | D 模块文档 §1.4/§9.1/§9.2/§9.3/§11.2/附录清单 |

# 遗留问题

1.  责任矩阵认领与工时：
    -   责任人、认领模块与工时数字由团队后续填入。

------------------------------------------------------------------------

# 9. AI协作规范

## 9.1 标准会话开头模板

标准模板全文见工作区根目录《ai首先阅读.md》（V1.1），
该文件为唯一模板源，本文档不重复全文，避免两份模板漂移。

组员每次开新任务：
新建对话 → 复制《ai首先阅读.md》全文 → 填写【当前阶段】与【本次任务】。

## 9.2 会话强制要求

-   组员每次开新任务必须新建对话；
-   新建对话后必须先粘贴 9.1 节的标准会话开头模板，再提交任务描述。

## 9.3 输出规范

-   AI 写代码前必须先输出“实现计划”；
-   任务完成后必须附“溯源说明”，注明关键数值/规则来自哪份文档哪一节。

## 9.4 禁止事项

-   AI 不得修改规则文档中的数值；
-   不得越过阶段边界（P0→P4 顺序执行）；
-   发现文档矛盾必须停止执行并报告，不得自行选择其一继续。
