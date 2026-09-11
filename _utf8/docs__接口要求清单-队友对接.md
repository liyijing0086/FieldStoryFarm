# 队友接口要求清单（FieldStoryFarm 场景合并）

> 作者：E 模块（存档与引擎 + 场景组装）
> 面向：A 土地 / B 玩家·商店 / C 剧情 / D 状态栏·时钟 各模块作者
> 依据：dev 分支当前源码逐一核对（非凭记忆），签名/常量均取自源码。
> 用法：合并前逐条勾选核对，打 ✅ 才算通过；任何一条不满足都可能导致编译失败、运行报错或验收不通过。
> 配套文档：`docs/接口约定-场景合并.md`（总览版，讲背景与思路）；本文档为**可核对的要求清单**（讲每条具体要求和验收）。

---

## A. 一句话分工总账

| 资产 | 持有者 | 队友的用法 |
|---|---|---|
| `manager/GameManager`（单例 + 状态机 + 存档触发） | E | 只调用，**禁止改**；场景合并阶段不 fork |
| `manager/SceneManager`（BorderPane 五区组装） | E | 只调用 `mount/unmount`，**禁止改** |
| `model/GameState`、`model/PlotState` | E（骨架） | 读写字段/列表；字段名不得改 |
| `model/Player` | B（模型，骨架已提供） | 经 `state.getPlayer()` 读写，**禁止 new 开新档** |
| `service/SaveService`、`persistence/JsonSaveService` | E | **禁止 import 到业务层**；只需了解事实 |
| `module-info.java` | 共用 | 合并时**只增不减**（见 §F） |
| `pom.xml`、`Launcher`、`AppConfig`、`FxmlUtil` | E | 统一约定，队友遵循即可 |

---

## B. 场景组装要求（对 A/B/C/D 视图交付）

> 根布局由 E 统一搭建：`MainApplication.start()` 已把主菜单挂到 **CENTER** 并生成 `Scene(960×640)`。
> 你的视图加载好后，**只需调用一次 mount**，游戏即可组装完成。

### B1. 挂载只走 `SceneManager.mount`
- 要求：交付的每个主视图，用下面方式挂载到指定槽位：
```java
SceneManager sm = SceneManager.getInstance();
sm.mount(SceneManager.Slot.CENTER, myFarmViewNode); // A 土地主视图（替换主菜单）
sm.mount(SceneManager.Slot.TOP,    myStatusBarNode); // D 状态栏
sm.mount(SceneManager.Slot.RIGHT,  myShopNode);      // B 商店
sm.mount(SceneManager.Slot.LEFT,   myPanelNode);     // 预留槽，归属先找 E 确认
sm.mount(SceneManager.Slot.BOTTOM, myPanelNode);     // 预留槽，归属先找 E 确认
```
- 验收：`mvnw javafx:run` 启动后，你的节点出现在对应区域。

### B2. 槽位归属表（谁挂哪，别抢）
| Slot | 归属 | 说明 |
|---|---|---|
| `CENTER` | A 土地主视图 / 主菜单 | P0 已被 main-view 占用；A 交付后由 A mount 覆盖 |
| `TOP` | D 状态栏 / 时钟 | 各模块状态面板也放这里需先协商 |
| `RIGHT` | B 商店 / 玩家 UI | |
| `LEFT` / `BOTTOM` | 预留 | 使用前先找 E 确认归属，避免两个模块抢一个槽 |

### B3. 同槽位重复 mount = 覆盖（禁止抢挂时机）
- 规则：`mount` 同一 Slot 会替换旧组件（天然支持“主菜单 → 游戏视图”切换）。
- 要求：**不要在多个模块里对同一槽位反复 mount 抢地盘**；每个槽位一个模块负责、挂一次。
- 时机建议：各自 view 初始化完成后调用一次；需要“从存档恢复后刷新”时在恢复逻辑里再 mount 一次覆盖即可。

### B4. 禁止在 controller 里动窗口与根布局
- 要求：不得 `new Scene` / `new Stage` / 直接 `setCenter/setRoot/setTop(...)`。
- 原因：根 `BorderPane` 是 `SceneManager` 的私有资产，只能经 `mount/unmount` 修改，否则组件会被直接绕过管理器导致不一致。
- `SceneManager.root()` 只读返回根（未组装时为 null），**不要持有后长期缓存**。

### B5. 移除组件用 `unmount`
- 签名：`Node unmount(Slot slot)`，返回被移除节点；空槽返回 `null`。

### B6. 不要改 `SceneManager` 本身
- 需要新槽位/新行为（如“全屏遮罩”“对话框层”）→ 先找 E 加好再合并，禁止在分支里私自扩展后与其他模块撞车。

---

## C. GameManager / 状态机要求（对 B/D 及各模块 UI 入口）

### C1. 唯一入口是 `GameManager.getInstance()`（业务层禁止 new）
- 签名：`public static GameManager getInstance()`（单例，默认装配 `JsonSaveService`）。
- 例外：`public GameManager(SaveService)` 仅测试注入用，**业务代码不得调用**（会造成多实例、多状态源）。
- 验收：全仓库业务代码搜索 `new GameManager(` 应为 0 处（测试类除外）。

### C2. 阶段切换必须经 GameManager 方法
- 状态机：`MAIN_MENU → PLAYING → PAUSED → EXITING`，由 E 的 `GameManager` 管理。
- 可用方法：`start()`（有档恢复/无档新建/档损坏自动降级新档）、`startNewGame()`（强制新会话，不读旧档不立即写盘）、`saveNow()`（手动/关键节点保存，不改阶段）、`saveAndExit()`（保存后进入 EXITING）、`pause()`（仅 PLAYING 可暂停，否则抛 `IllegalStateException`）、`resume()`（仅 PAUSED 可恢复）。
- 要求：B 商店打开需暂停 → `pause()`；关闭 → `resume()`。**禁止**模块内自定义“假暂停/假阶段”。

### C3. 新会话初始金币统一 500，禁止 `new Player()` 开新档
- 常量：`GameManager.INITIAL_GOLD = 500`；默认玩家名 `农夫`。
- 原因：`Player` 无参构造默认是 100/农夫（占位值）；直接 `new Player()` 会造成新档金币 100，与其他模块不一致。
- 要求：需要“全新状态”一律用 `GameManager.getInstance().start()/startNewGame()` 或静态 `GameManager.newGame()`。
- 验收：新游戏界面金币必须显示 500；存档 JSON 的 `player.gold` 首存为 500。

### C4. 关键节点存档调 `saveNow()`
- 场景：D 每日结算后、B 大额交易后等需要落盘的节点，调 `gm.saveNow()`。
- 注意：`saveNow()` 在未 `start()` 前抛 `IllegalStateException`，调用方自行处理（参考 `MainController.onSaveButtonClick` 的 try/catch 写法）。

### C5. 退出自动存档已接好，**不要重复拦退出事件**
- `MainApplication.stop()` 已调 `GameManager.getInstance().saveAndExit()`（验收 §四十二：保存 → 记录世界时间 → 退出）。
- 要求：各模块不要再在窗口关闭/退出路径上加第二套“保存+退出”逻辑；你要做的只是保证**数据已写进 GameState**，保存时机由 E 统一触发。
- 例外：游戏内“返回主菜单”这类**非退出**切换由你自行控制视图与状态（mount 覆盖 CENTER），不触发 saveAndExit。

### C6. `currentState()` 未启动会抛异常
- 签名：`public GameState currentState()`，未调 `start()` 时抛 `IllegalStateException`。
- 要求：UI 读状态前先确保已 start，或自行判空（可用 `hasSavedGame()` 先探测）。

---

## D. 状态读写要求（存档字段分工）

### D1. `GameState` 是唯一会话状态，只存“现在是什么”，不含计算
```java
public class GameState {
    Player          player;             // B 模块 Player 模型
    long            gameDay;            // 游戏天数 = D GameClock.getGameDay()
    String          currentWorldTime;   // ISO-8601 世界时间，时钟接入前可为 null
    Set<String>     unlocked;           // 已解锁内容标识，P0 默认空
    List<PlotState> plots;              // 全部地块快照，A 适配层生成
}
```
- 均有 getter/setter；`getUnlocked()`/`getPlots()` 返回**可变集合**，直接 `add/clear` 即可。
- 要求：**字段名不得改**（存档 JSON 与序列化逻辑已按此实现）；谁都不许另起炉灶存“第二份状态”。

### D2. D 时钟：结算/存档前写天数与时间
- 要求：每次“进入下一天/每日结算”以及任何需要落盘的时刻，把值写入会话状态：
```java
state.setGameDay(gm.currentState() 对应的天数);        // 或 state.setGameDay(...)
state.setCurrentWorldTime(ISO8601 字符串);             // 时钟记录的世界时间
```
- 说明：写盘动作本身由 `saveNow()/saveAndExit()` 触发；D 只需保证 **GameState 里的数据是新的**。
- 验收：连续玩两天 → 退出 → 重启，`gameDay` 恢复为第二天，且存档 JSON 里 `gameDay`、`currentWorldTime` 与结算一致。

### D3. A 土地：`plots ↔ Farm` 双向映射在 A 侧完成
- 要求：
  1. 保存前把 `Farm/Soil/Crop` 映射为 `List<PlotState>` 填入 `state.getPlots()`；
  2. 从存档恢复时反向映射回 Farm（A 自建适配层，E 不持有 A 的类型）；
  3. **枚举 ↔ 字符串映射在 A 侧做**，且建议字符串值 = 你枚举的 `name()`（如 `"EMPTY"/"TILLED"/"GROWING"`、`"WHEAT"`），映射零成本。
- 验收：种一块地 → 退出 → 重启，地块状态/作物/成长进度与退出瞬间一致。

### D4. B 经济/商店：gold 只读写 `state.getPlayer()`
- 要求：金币唯一数据源是 `GameState.player.gold`；**禁止在 B 侧维护第二份金币**（UI 缓存展示除外，落账一律走 player）。
- 改钱：`state.getPlayer().setGold(newGold)`；需要落盘再调 `saveNow()`。
- 验收：商店购买 → 金币变化 → 退出重启，金币为购买后数值且未重复扣减。

### D5. `PlotState` 字段必须与存档 §四十一 一一对应
```java
String plotId;               // 格唯一标识；为空时序列化为 "row,column"
int    row, column;          // 0-based 坐标
String state;                // 土地状态名（建议用 A 枚举名）
String cropUuid;             // 无作物时整棵 crop 为 null（hasCrop() 判断 cropUuid != null）
String cropType;             // "WHEAT"/"CORN"/"CARROT"...
String growthStage;          // 对应未来 GrowthStage 枚举名
double growthProgress;       // 0.0~1.0
String plantWorldTime;       // 播种时刻 ISO-8601
int    manualWaterCount;
String lastManualWaterGameDay;
```
- 要求：以上字段名是 JSON 键名，**不许改名/改含义**；A 适配层填值时逐一对齐。
- 反序列化细节：`plotId` 为空会自动补成 `"row,column"`；`crop` 缺失时按无作物处理。
- **D14（时间类型与浇水哨兵）**：A 侧 `plantWorldTime` = `long`（游戏小时）、`lastManualWaterGameDay` = `long`（游戏日，**无记录 = `-1`**）、`BasicCrop` 默认 `-1` 哨兵；E 端存储仍为字符串，换算与“无记录 → `-1`”映射由适配层负责。D 的 `GameClock` 无需新增 `getWorldTime()`。

---

## E. 存档服务要求

### E1. 业务层不感知存档文件
- 要求：Controller/Manager 不得感知 `data/save.json` 路径（验收 §三十九）。
- 正确姿势：要存档/读档，只调 `GameManager` 的方法；**不要 import** `persistence.JsonSaveService` 到业务层。

### E2. 了解这些事实即可（不要依赖其内部）
- 默认文件 `data/save.json`；`data/` 已在 `.gitignore`，**本地存档不进仓库**。
- 根节点保留 `version = 1` 与 `schema = "P0-json"`；写盘 UTF-8、自动建目录。
- 文件损坏或 version 不匹配 → `load()` 抛 `IllegalStateException` → `GameManager.start()` 捕获后自动降级新档，不崩。

### E3. 升级存档结构必须走版本递增
- 要求：任何模块要扩展 `GameState/PlotState` 字段 → 这是存档结构变更 → 必须同步：递增 `JsonSaveService.SAVE_VERSION`、`load()` 做兼容处理、**禁止原地改旧档结构**，并先找 E 对齐（P1 将迁 SQLite 并一次性导入旧 JSON）。
- 验收：变更后旧存档要么能正常读（兼容），要么明确提示版本不符降级新档，不允许静默错读。

---

## F. module-info / 视图加载 / 打包要求

### F1. 新增含 FXML controller 的包必须登记 opens（最容易翻车，最高优先级）
- 现状（基线）：
```java
module com.fieldstory.farm {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fasterxml.jackson.databind;
    opens com.fieldstory.farm to javafx.fxml;
    exports com.fieldstory.farm;
    opens com.fieldstory.farm.controller to javafx.fxml;   // controller 包
    opens com.fieldstory.farm.view to javafx.fxml;          // view 包
    exports com.fieldstory.farm.view to javafx.graphics;
}
```
- 要求：你们若新增了**带 FXML + controller 的新包**（例如 `farm.xxx.controller`），必须在 module-info 补一行：
```java
opens 你的新包 to javafx.fxml;
```
- 否则 JavaFX 反射实例化 controller 抛 `InaccessibleObjectException`，表现是“FXML 加载成功但运行时报错”。
- **合并纪律：module-info 是多路并发高冲突文件，合并时保留所有人的 opens/exports 行，只增不减、不互相覆盖。**

### F2. FXML 加载统一走 FxmlUtil
- 签名：`public static FXMLLoader load(Class<?> controllerClass, String fxmlName) throws IOException`
- 要求：controller 与 fxml **同包放置**，加载用 `FxmlUtil.load(YourController.class, "your-view.fxml").load()`。
- 不要在业务类里 `new FXMLLoader(new File(...))`（路径不可靠、破坏模块化）。

### F3. 窗口/标题常量统一取 AppConfig，禁止硬编码
- `AppConfig.APP_TITLE = "FieldStoryFarm"`、`WINDOW_WIDTH = 960`、`WINDOW_HEIGHT = 640`、`MAIN_VIEW_FXML = "main-view.fxml"`。
- 要求：新建子窗口（若有）尺寸也从这里取或新增常量，不写死 `960/640`。

### F4. 程序入口是 Launcher，运行命令固定
- `Launcher.main` → `Application.launch(MainApplication.class, args)`；
- pom 已配 `mainClass=com.fieldstory.farm/com.fieldstory.farm.Launcher`；
- 运行：`mvnw javafx:run`；**不要另起 `main`**，不要另配启动类。

---

## G. 编码 / 构建 / 提交纪律

### G1. 全源码 UTF-8
- pom 已固定 `project.build.sourceEncoding=UTF-8`。
- **禁止提交 GBK 文件**；仓库根目录那几份 GBK 中文计划/文档不入库（已确认未跟踪），转码后放 `docs/` 再入库。

### G2. JDK 17 + 测试全绿才 push
- 本机 JDK 17；JUnit 5（surefire 3.5.2）；当前基线测试 **16 个全绿**（GameManagerTest 8 + JsonSaveServiceTest 7 + 基础 1，以实际为准）。
- **push 前必须 `mvnw test` 全绿**，否则合并时第一步就会把 dev 打断、定位到人。

### G3. 不提交运行时/IDE 产物
- 已被 .gitignore 忽略：`data/`、`target/`、`.idea/`、`*.db`、`logs/`、`.rikki/` 等。
- 提交前 `git status` 自查，别 `git add -A` 一把梭把 save.json 带进去。

### G4. 一次提交一个模块
- commit message 写清 feat/fix 前缀与模块名；**不要大杂烩提交**——场景合并不顺时 E 需要能 revert/拣选单个模块。

---

## H. 合并到 dev 的协作要求

1. 各自在**自己的分支**完成，从 dev（`22640b6` 之后的最新版）拉新再开发，push 后**在群里喊一声**（附分支名）。
2. E 按 `A/C-D → B` 顺序逐个合入 dev，**每合一步跑一次 `mvnw test`**，失败即定位到人回修。
3. 预计冲突文件与处置：
   - `module-info.java`：合并所有 opens/exports 行（见 F1）
   - `pom.xml`：增量合并，保留双方新增依赖
   - `main-view.fxml` / `MainController`：多人改主菜单时以最新版为准，**保留 GameManager 的开始/继续/保存钩子**
   - `GameManager` / `SceneManager` / `model/*`：E 持有；除非事先协商，否则不应出现冲突（出现即说明有人动了我方资产）
4. 收尾由 E 统一全量验证：`mvnw test` → `mvnw javafx:run` 冒烟（启动 → 开始/读档 → 各模块 mount → 退出自动存档 → 重启恢复）。

---

## I. 合入前自检清单（每人 push 前逐条过）

### I-1 通用
- [ ] 源码文件均为 UTF-8，无 GBK 混入
- [ ] `mvnw test` 全绿（且没把别人打断）
- [ ] `git status` 无 `data/`、`target/`、`.idea/` 等产物
- [ ] 未修改 `GameManager`/`SceneManager`/`GameState`/`PlotState`/`JsonSaveService`/`module-info`（除非事先协商）
- [ ] 新增了含 FXML 的包 → 已在 `module-info.java` 补 `opens ... to javafx.fxml`（只增不减）
- [ ] 用了 `AppConfig` 常量、`FxmlUtil` 加载、没 `new Scene/Stage`

### I-2 A 土地模块
- [ ] 视图经 `SceneManager.mount(Slot.CENTER, ...)` 挂载（替换主菜单即覆盖主菜单的 CENTER）
- [ ] 适配层完成 `plots ↔ Farm` 双向映射，字段名与 §D5 完全一致
- [ ] 字符串值 = 你枚举 `name()`；新档（无存档）时 `plots` 是否已有默认地块快照？无则保持空列表亦可（GameManager.newGame 默认空）

### I-3 B 玩家/商店模块
- [ ] 金币只读写 `state.getPlayer()`，无第二份金币
- [ ] 无直接 `new Player()` 开新档；新档金币显示 500
- [ ] 商店打开/关闭若暂停：用 `GameManager.pause()/resume()`，不自定义假暂停

### I-4 C 剧情模块
- [ ] 解锁内容写 `state.getUnlocked()`（P0 默认空，P3 使用）；字段已预留，不新造状态容器
- [ ] 剧情面板若占用槽位，先与 E 确认归属（LEFT/BOTTOM 预留中）

### I-5 D 状态栏/时钟模块
- [ ] 结算/存档前已写 `state.setGameDay(...)` 与 `state.setCurrentWorldTime(ISO-8601)`
- [ ] 状态栏经 `SceneManager.mount(Slot.TOP, ...)` 挂载
- [ ] 未重复拦退出事件做二次保存（退出自动存档已在 `MainApplication.stop()`）

---

> 有疑问先找 E（本模块作者）对清楚再动手；宁可多问一句，不要两个人改同一个类后合并冲突返工。
