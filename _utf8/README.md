# 《田野物语 · 三韵集》

---

## 一、项目简介

### 1.1 项目概述

《田野物语 · 三韵集》（英文名：**Field Story Farm**）是一款基于 **Java 17 + JavaFX 17** 开发的桌面端农场模拟经营游戏。

游戏以"每一株作物都有经历，每一次经营都有选择"为核心设计理念，将土地经营、作物培养、天气策略、品质控制、传说突破、装饰建设、生命记忆与收藏成长八大系统深度融合，为玩家提供从"新手农场"到"永恒花园"的完整毕业体验。

### 1.2 项目定位

| 维度 | 说明 |
|---|---|
| 项目类型 | 桌面端单机游戏 |
| 技术栈 | Java 17 + JavaFX 17 + SQLite |
| 构建工具 | Maven |
| 开发阶段 | 按 P0 → P4 五阶段逐级交付 |
| 目标体验时长 | 正常玩家 6～8 小时完成全收集 |
| 核心特色 | 持续世界时间系统、离线模拟、生命记忆、传说突破 |

### 1.3 项目状态

当前版本路线：

```text
v0.0.1-skeleton → v0.1.0-core(P0) → v0.2.0-playable(P1) → v0.3.0-feature(P2) → v0.4.0-collect(P3) → v1.0.0-release(P4)
```

---

## 二、核心游戏机制

### 2.1 游戏目标

玩家最终目标为 **「永恒花园」** ，需完成：

| 收集项 | 数量 | 说明 |
|---|---|---|
| 作物品质图鉴 | 15 | 3种作物 × 5种品质 |
| 装饰收集 | 14 | 全部14种装饰 |
| 传说作物 | 3 | 金色麦穗、彩虹玉米、巨龙胡萝卜 |
| 装饰套装 | 3 | 自然之息、丰收之魂、传奇之光 |

当 **FarmScore = 147** 时，触发「永恒花园」毕业。

### 2.2 核心循环

```text
启动游戏 → 加载存档 / 离线模拟 → 进入农场
    ↓
查看天气 / 事件 / 作物状态 → 制定经营策略
    ↓
开垦 / 播种 / 浇水 / 施肥 / 调整装饰
    ↓
作物持续成长（受天气、装饰、事件影响）
    ↓
作物成熟 → 收获 → 计算品质 / 判定传说突破
    ↓
获得金币 / 肥料 → 更新图鉴 → 生成生命记忆
    ↓
购买种子 / 装饰 → 扩大农场 → 提升 FarmScore
    ↓
达到 147 分 → 永恒花园毕业
```

### 2.3 时间系统

| 规则 | 说明 |
|---|---|
| 正式时间比例 | 1 现实分钟 = 1 游戏小时 |
| 1 游戏日 | 24 现实分钟 |
| 离线最大结算 | 单次最多 72 游戏小时（72 现实分钟 = 3 游戏日） |
| 开发/答辩模式 | 支持 ×12 倍速（DemoClock） |
| 测试模式 | 支持手动推进（TestClock） |

### 2.4 关键系统速览

| 系统 | 简要说明 |
|---|---|
| 土地系统 | 12×12 地图，8×8 种植区，4 种土地状态 |
| 作物系统 | 小麦、玉米、胡萝卜，5 个成长阶段，支持枯萎 |
| 天气系统 | 晴天(40%) / 雨天(25%) / 干旱(20%) / 绿雨(15%) |
| 品质系统 | 普通 / 优秀 / 稀有 / 史诗 / 传说，收获时结算 |
| 传说作物 | 3 种专属传说，需满足特殊经历 + 品质门槛 + 概率突破 |
| 装饰系统 | 14 种装饰，5 类 Buff，支持套装 |
| 事件系统 | 每日 1 次随机事件（流星夜/神秘商人/小动物/彩虹日） |
| 生命记忆 | 每株作物完整成长档案，生成可追溯故事 |
| 离线模拟 | 按游戏日切片推进，生成离线日志 |
| 图鉴系统 | 15 项作物图鉴 + 14 项装饰图鉴 |
| FarmScore | 147 分满分，8 级评价体系 |

---

## 三、技术架构

### 3.1 分层架构

```text
┌──────────────────────────────────────────────────────────┐
│                     View (JavaFX)                        │
├──────────────────────────────────────────────────────────┤
│                    Controller                            │
├──────────────────────────────────────────────────────────┤
│                     Service                              │
├──────────────────────────────────────────────────────────┤
│                 DAO / Repository                         │
├──────────────────────────────────────────────────────────┤
│                  Persistence (SQLite)                    │
└──────────────────────────────────────────────────────────┘
```

**核心原则：**
- **Model** 只保存状态，不包含业务逻辑
- **Service** 负责所有状态变更和游戏规则计算
- **Controller** 仅协调 View 与 Service，不包含业务逻辑
- **DAO** 负责数据库操作，Service 不得直接写 SQL

### 3.2 关键组件

| 组件 | 职责 | 首次上线阶段 |
|---|---|---|
| `GameClock` | 统一时间源，禁止直接调用 System.currentTimeMillis() | P0 |
| `RandomProvider` | 统一随机源，支持固定 seed 测试 | P0 |
| `SaveService` | 存档接口，P0 为 JSON，P1 起为 SQLite | P0 / P1 |
| `WorldSimulationService` | 在线/离线共用世界模拟规则 | P2 |

### 3.3 模块依赖阶段边界

```text
P0 禁止：GrowthService 依赖 WeatherService（Weather 不存在）
P1 禁止：QualityService 生成 LEGENDARY（传说属 P2）
P2 禁止：OfflineSimulationService 拥有独立成长公式（必须复用在线规则）
P3 禁止：套装完成直接等同永恒花园（必须 FarmScore == 147）
P4 禁止：为赶进度修改核心机制（仅修 Bug / 调配置 / 优化体验）
```

---

## 四、开发路线图

### 4.1 阶段总览

| 阶段 | 版本 Tag | 阶段目标 | 核心交付 |
|---|---|---|---|
| 骨架 | `v0.0.1-skeleton` | 工程可启动 | JavaFX 主界面 |
| P0 | `v0.1.0-core` | 核心经营闭环 | 开垦→播种→浇水→成长→收获→出售→再种 |
| P1 | `v0.2.0-playable` | 策略系统完整 | 天气、品质、肥料、装饰、商店、枯萎、SQLite |
| P2 | `v0.3.0-feature` | 核心特色完整 | 持续世界、离线模拟、事件、生命记忆、传说 |
| P3 | `v0.4.0-collect` | 长线内容完整 | 图鉴、套装、评价、展示台、土地扩展、永恒花园 |
| P4 | `v1.0.0-release` | 正式发布 | 完整 UI、动画、音效、测试、平衡、打包 |

### 4.2 P0 核心闭环（v0.1.0-core）

玩家必须能够完成：

```text
进入游戏 → 看到农场 → 开垦土地 → 购买种子 → 播种
    ↓
等待成长 → 主动浇水 → 成熟 → 收获 → 自动出售
    ↓
获得金币 → 重新购买种子 → 再次播种 → 退出保存 → 重新打开继续
```

**P0 明确不实现：** 天气、品质、肥料、事件、传说、装饰 Buff、套装、图鉴、FarmScore、离线推进、枯萎、SQLite

**验收标准：** 在 ×12 倍速下，10 分钟内完成一次完整种植循环。

### 4.3 P1 策略与持久化（v0.2.0-playable）

新增系统：
- 天气系统（4 种天气 + WeatherRate）
- 品质系统（5 档品质 + 收获时结算）
- 肥料系统（成长 + 品质）
- 完整商店（种子 + 装饰）
- 14 种装饰 + 5 类 Buff
- 在线枯萎系统
- SQLite 正式存档 + JSON 迁移

**验收标准：** 同一作物因玩家策略不同（浇水/施肥/装饰布局）获得明显不同品质结果。

### 4.4 P2 持续世界与特色（v0.3.0-feature）

新增系统：
- 持续世界时间（退出后时间继续推进）
- 离线模拟（最多 72 游戏小时，按日切片）
- 离线日志（按日分组展示离线变化）
- 4 种随机事件
- CropMemory 生命记忆系统
- 3 种传说作物（金色麦穗 / 彩虹玉米 / 巨龙胡萝卜）
- LegendaryService 完整事务

**验收标准：** 退出游戏后重新进入，世界变化可复现、可解释、有离线日志记录。

### 4.5 P3 收集与毕业（v0.4.0-collect）

新增系统：
- 15 项作物图鉴 + 14 项装饰图鉴
- 3 套装饰套装（自然之息 / 丰收之魂 / 传奇之光）
- FarmScore 147 分体系 + 8 级评价
- 展示台（传说作物展示 + 生命故事）
- LOCKED 土地解锁
- 永恒花园毕业触发

**验收标准：** 游戏存在从 0 到 147 的完整毕业路线，146 分绝对不能触发毕业。

### 4.6 P4 发布（v1.0.0-release）

内容：
- 完整 CSS 统一 UI
- 动画（播种/浇水/收获/品质揭晓/传奇突破/毕业）
- 音效（按钮/操作/品质/升级）
- 数值平衡（目标 6～8 小时毕业）
- 全量回归测试 + 固定随机种子测试
- Maven 打包 + 发布验收

**验收标准：** 全新环境启动，完整玩家路径从新建游戏到永恒花园，无阻断 Bug。

---

## 五、项目结构

### 5.1 源码目录结构（建议）

```text
src/main/java/com/fieldstory/
├── App.java                          # 程序入口
├── controller/
│   ├── FarmController.java
│   ├── ShopController.java
│   ├── CollectionController.java
│   └── ...
├── view/
│   ├── FarmView.java
│   ├── ShopView.java
│   ├── CollectionView.java
│   ├── ShowcaseView.java
│   ├── OfflineLogView.java
│   ├── components/                   # 可复用 UI 组件
│   └── css/                          # CSS 样式
├── model/
│   ├── Player.java
│   ├── Farm.java
│   ├── Soil.java
│   ├── Crop.java
│   ├── CropMemory.java
│   ├── Decoration.java
│   ├── WorldState.java
│   ├── ActiveEvent.java
│   ├── enums/
│   │   ├── FarmPlot.java
│   │   ├── SoilState.java
│   │   ├── CropType.java
│   │   ├── GrowthStage.java
│   │   ├── Weather.java
│   │   ├── Quality.java
│   │   ├── EventType.java
│   │   └── ...
│   └── ...
├── service/
│   ├── GameClock.java
│   ├── RealGameClock.java
│   ├── DemoGameClock.java
│   ├── TestGameClock.java
│   ├── RandomProvider.java
│   ├── GrowthService.java           # P0
│   ├── WateringService.java         # P0
│   ├── EconomyService.java          # P0
│   ├── PlantingService.java         # P0
│   ├── WeatherService.java          # P1
│   ├── QualityService.java          # P1
│   ├── FertilizerService.java       # P1
│   ├── BuffService.java             # P1
│   ├── WitherService.java           # P1
│   ├── DecorationService.java       # P1
│   ├── WorldSimulationService.java  # P2 (在线/离线共用)
│   ├── OfflineSimulationService.java# P2
│   ├── EventService.java            # P2
│   ├── LegendaryService.java        # P2
│   ├── MemoryService.java           # P2
│   ├── HarvestService.java          # P2 (替代 BasicHarvestService)
│   ├── CollectionService.java       # P3
│   ├── SetService.java              # P3
│   ├── FarmScoreService.java        # P3
│   ├── ShowcaseService.java         # P3
│   ├── LandUnlockService.java       # P3
│   └── ...
├── dao/
│   ├── PlayerDao.java               # P1
│   ├── FarmDao.java                 # P1
│   ├── SoilDao.java                 # P1
│   ├── CropDao.java                 # P1
│   ├── DecorationDao.java           # P1
│   ├── WorldStateDao.java           # P1
│   ├── ActiveEventDao.java          # P2
│   ├── CropMemoryDao.java           # P2
│   ├── CollectionDao.java           # P3
│   └── ...
├── persistence/
│   ├── SaveService.java             # 存档接口
│   ├── JsonSaveService.java         # P0 临时实现
│   ├── SqliteSaveService.java       # P1 正式实现
│   └── DatabaseService.java         # SQLite 初始化与连接
├── config/
│   ├── CropConfig.java              # 作物静态配置
│   ├── DecorationConfig.java        # 装饰静态配置
│   └── BalanceConfig.java           # 经济平衡配置
└── util/
    └── ...
```

### 5.2 资源目录

```text
src/main/resources/
├── css/
│   ├── main.css
│   ├── farm.css
│   ├── shop.css
│   └── ...
├── images/
│   ├── crops/
│   ├── decorations/
│   ├── weather/
│   ├── ui/
│   └── ...
├── sounds/
│   ├── button.wav
│   ├── plant.wav
│   ├── water.wav
│   ├── harvest.wav
│   ├── legendary.wav
│   └── ...
├── config/
│   ├── crop-config.json
│   ├── decoration-config.json
│   └── balance-config.json
└── fxml/
    ├── farm-view.fxml
    ├── shop-view.fxml
    └── ...
```

### 5.3 数据目录

```text
data/
└── farm.db                         # SQLite 数据库（运行时生成，Git 忽略）
```

---

## 六、快速开始

### 6.1 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 17+ |
| Maven | 3.8+ |
| JavaFX | 17+ |
| SQLite | 3.x（嵌入式，无需独立安装） |

### 6.2 构建与运行

```bash
# 克隆仓库
git clone [repository-url]

# 进入项目目录
cd field-story-farm

# 编译与测试
mvn clean test

# 打包
mvn clean package

# 运行
java -jar target/field-story-farm-1.0.0.jar
```

### 6.3 开发模式（×12 倍速）

用于开发调试和答辩演示，修改配置文件或启动参数启用 `DemoGameClock`。

---

## 七、测试策略

### 7.1 测试分级

| 层级 | 覆盖范围 | 示例 |
|---|---|---|
| 单元测试 | 各 Service 核心逻辑 | `GrowthServiceTest`, `QualityServiceTest` |
| 集成测试 | DAO + SQLite | `SqliteDaoTest`, `MigrationTest` |
| 场景测试 | 完整玩家流程 | P0 核心闭环验收, P3 147 分验收 |
| 回归测试 | 全阶段功能不被破坏 | P4 完整测试矩阵 |
| 固定随机测试 | 可重现结果 | `DeterministicRandomTest` |

### 7.2 测试覆盖率要求

- 核心 Service 逻辑：**≥ 80%**
- DAO 层：**≥ 70%**
- Controller 层：不强制单元测试（通过场景测试覆盖）

### 7.3 关键测试用例

| 测试场景 | 验证内容 |
|---|---|
| 非整日成长 | 经过 6 游戏小时，成长 = BaseDailyProgress × 0.25 |
| 重复浇水限制 | 同一游戏日第二次浇水不增加 manualWaterCount |
| 连续干旱定义 | 干旱→晴天→干旱，droughtStreak 不连续 |
| 离线 8 小时 | 最多推进 72 游戏小时 |
| 传说失败路径 | 满足条件但概率失败 → EPIC |
| 146 分不毕业 | 缺少任意必要条件，不能触发永恒花园 |

---

## 八、配置系统

### 8.1 静态配置（JSON）

| 配置文件 | 内容 | 位置 |
|---|---|---|
| `crop-config.json` | 作物基础时间、价格、品质分 | `src/main/resources/config/` |
| `decoration-config.json` | 装饰价格、效果、尺寸 | `src/main/resources/config/` |
| `balance-config.json` | 开垦费用、土地解锁价格、初始资源 | `src/main/resources/config/` |

### 8.2 开发环境配置

开发阶段可使用测试配置快速验证流程，正式发布价格由 P4 平衡测试定稿。

---

## 九、文档索引

| 文档 | 用途 |
|---|---|
| `《田野物语·三韵集》完整游戏规则设计文档 V4.0.md` | 唯一游戏规则事实源，所有设计以此为准 |
| `《田野物语·三韵集》P0-P4逐级功能实现与验收规范 V4.0.md` | 开发实施基准，定义各阶段具体实现与验收标准 |
| `README.md`（本文档） | 项目入口文档，快速了解项目全貌 |

**规则冲突处理原则：**

> 以《完整游戏规则设计文档 V4.0》为最终规则基准。

---

## 十、贡献与开发规范

### 10.1 开发原则

1. **架构固定**：View → Controller → Service → DAO → Persistence
2. **统一时间**：所有游戏时间通过 `GameClock`，禁止直接调用系统时间
3. **统一随机**：所有随机通过 `RandomProvider`，支持固定 seed 测试
4. **阶段边界**：严格按照 P0→P4 顺序开发，禁止跨阶段实现半套功能
5. **配置驱动**：数值平衡通过配置文件调整，禁止硬编码

### 10.2 代码规范

- 包名：`com.fieldstory.*`
- Service 命名：`[功能]Service.java`
- DAO 命名：`[实体]Dao.java`
- 枚举类命名：`[名称].java`（全大写枚举值）
- 禁止在 Controller / View / Model getter/setter 中编写复杂游戏规则

### 10.3 提交规范

```text
feat: 新增功能
fix: 修复 Bug
test: 新增或修改测试
refactor: 重构（不改变功能）
docs: 文档更新
style: 代码格式调整
chore: 构建/工具链变更
```

### 10.4 Git 忽略

```gitignore
data/farm.db
data/farm.db-shm
data/farm.db-wal
logs/
target/
.idea/
*.iml
```

---

## 十一、版本历史

| 版本 | 发布日期 | 主要变更 |
|---|---|---|
| v0.0.1-skeleton | TBD | 工程骨架，JavaFX 主界面 |
| v0.1.0-core | TBD | P0 核心经营闭环 |
| v0.2.0-playable | TBD | P1 策略系统 + SQLite |
| v0.3.0-feature | TBD | P2 持续世界 + 传说 |
| v0.4.0-collect | TBD | P3 收集 + 毕业系统 |
| v1.0.0-release | TBD | P4 正式发布 |

---

## 十二、许可证

[待定]

---

## 十三、联系与支持

[待补充]

---

**本文档最后更新：** 2026 年 9 月

**依据基准：** 《田野物语 · 三韵集》完整游戏规则设计文档 V4.0