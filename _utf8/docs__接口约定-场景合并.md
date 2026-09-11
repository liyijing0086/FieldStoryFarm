# 场景合并 · 队友接口约定

> 作者：E 模块（存档与引擎 + 场景组装） ｜ 面向：A 土地 / B 玩家·商店 / C 剧情 / D 状态栏·时钟 模块作者
> 基线提交：`22640b6`（dev）｜ 本文件为 UTF-8，请在 UTF-8 编辑器（VS Code / IDEA 默认）中打开。
> 目的：大家各自 push 后合并到 dev 完成“场景合并任务”时，按本约定对接，避免接口对不齐与无谓冲突。

---

## 0. 一句话总览

- **游戏唯一入口**：`GameManager.getInstance()`（单例，负责读档/新档/保存/状态机）
- **场景唯一组装口**：`SceneManager.getInstance().mount(槽位, 节点)`（你们只挂载，不碰窗口与根布局）
- **存档只走接口**：`SaveService`（不要感知 `data/save.json` 路径）
- **新界面含 FXML controller 的包必须在 `module-info.java` 登记 `opens`**（最容易翻车，见 §5）

---

## 1. 场景组装（你们交付视图后怎么“装”进去）

根布局由 E 统一搭建：`MainApplication.start()` 已把主菜单 `main-view.fxml` 挂到 **CENTER** 并生成 `Scene(960×640)`。

你们的视图类/面板加载好后，**只调用一次挂载**：

```java
SceneManager sm = SceneManager.getInstance();
sm.mount(SceneManager.Slot.TOP,     myStatusBarNode);   // 例：D 状态栏
sm.mount(SceneManager.Slot.CENTER,  myFarmViewNode);    // 例：A 土地主视图（替换主菜单）
sm.mount(SceneManager.Slot.RIGHT,   myShopNode);        // 例：B 商店
```

约定：

| Slot | 归属建议 | 说明 |
|---|---|---|
| `CENTER` | A 土地主视图 / 主菜单 | P0 已被 main-view 占用；A 交付后由 A 挂载替换 |
| `TOP` | D 状态栏 / 时钟 | 各模块状态面板 |
| `RIGHT` | B 商店 / 玩家 UI | |
| `LEFT` / `BOTTOM` | 预留 | 需要时先找 E 确认归属 |

规则：

1. **同槽位重复 `mount` 会替换旧组件**（天然支持“主菜单 → 游戏视图”切换）。
2. 不要在 controller 里 `new Scene` / `new Stage` / 直接 `setCenter/setRoot`——根布局是 `SceneManager` 的私有 `BorderPane`，只能通过 `mount` 改。
3. `SceneManager.root()` 只读返回根（未组装时为 `null`），不要持有后长期缓存。
4. 需要移除组件用 `unmount(Slot)`（返回被移除节点，空槽返回 `null`）。
5. **你们别改 `SceneManager` 本身**（E 持有）；确实需要新槽位/新行为，先找 E 加好再合。

API 速查（源码原文）：

```java
// manager/SceneManager.java
public enum Slot { TOP, CENTER, LEFT, RIGHT, BOTTOM }
public static SceneManager getInstance();
public Scene assemble(Node centerNode, double width, double height); // E 内部/入口使用
public void mount(Slot slot, Node node);   // 同槽替换
public Node unmount(Slot slot);            // 返回被移除节点；空槽返回 null
public javafx.scene.layout.BorderPane root();
```

---

## 2. 游戏生命周期与状态机（GameManager）

状态机阶段：`MAIN_MENU → PLAYING → PAUSED → EXITING`，切换**必须**经 GameManager 方法，禁止模块内自行改阶段语义。

```java
// manager/GameManager.java
public static final int INITIAL_GOLD = 500;   // 新档初始金币，统一走这里

public static GameManager getInstance();          // 全局唯一入口（默认 JsonSaveService）
public GameManager(SaveService saveService);      // 测试注入用，业务层不要用
public GamePhase currentPhase();
public GameState currentState();                  // 未 start() 时抛 IllegalStateException
public boolean hasSavedGame();
public GameState start();                         // 有档→恢复；无档→新档；档损坏→自动降级新档
public GameState startNewGame();                  // 强制开新会话（不读旧档、不立即写盘）
public static GameState newGame();                // 构造全新状态（金币 500、游戏天数 0）
public void saveNow();                            // 手动/关键节点保存，不改变阶段
public void saveAndExit();                        // 退出存档（保存→退出）
public void pause();                              // 仅 PLAYING 可暂停
public void resume();                             // 仅 PAUSED 可恢复
```

对队友的硬性约定：

1. **开局金币统一 500**：新游戏会话用 `GameManager.getInstance().start()/startNewGame()/newGame()` 创建，**不要直接 `new Player()`**——`Player` 无参构造默认是 100/农夫，直接 new 会造成新档金币与其他模块不一致。
2. **退出即存档已经接好**：`MainApplication.stop()`（关窗）已调用 `saveAndExit()`。**你们不要**再各自拦截 `WindowEvent.WINDOW_CLOSE_REQUEST` 重复存档，否则会双写。
3. **关键节点存档**（如 D 每天结算后）：调 `gameManager.saveNow()`。
4. 暂停/恢复：统一走 `pause()/resume()`，例如 B 商店打开时若需要暂停，调 `pause()`，关闭时 `resume()`。

---

## 3. 状态读写（存档层，你们负责填字段）

### 3.1 读写单位 `GameState`（E 持有；只存“现在是什么”，不含任何计算）

```java
// model/GameState.java
Player   player;                       // B 模块 Player 模型
long     gameDay;                      // 游戏天数 = D GameClock.getGameDay()
String   currentWorldTime;             // 存档时刻世界时间 ISO-8601 字符串（时钟接入前可为 null）
Set<String>  unlocked;                 // 已解锁内容标识（P0 默认空）
List<PlotState> plots;                 // 全部地块快照（A 适配层生成）
// 以上均有对应 getter/setter（getUnlocked()/getPlots() 返回可变集合，直接 add/set 即可）
```

### 3.2 各模块写入职责

- **D 时钟**：每日结算/存档前写入 `state.setGameDay(long)` 与 `state.setCurrentWorldTime("ISO-8601")`。存档时机由 E 的 `saveNow()/saveAndExit()` 触发，D 只需保证**数据先写进 GameState**。
- **A 土地**：由 A 侧做一层适配，把 `Farm/Soil/Crop` 映射为 `List<PlotState>` 后填 `state.getPlots()`；从存档恢复时反向映射回 Farm。**枚举 ↔ 字符串映射在 A 侧做**，E 不持有 A 的枚举（避免在 A 交付前产生跨模块类型依赖）。
- **B 玩家/商店**：金币读写一律走 `state.getPlayer().getGold()/setGold(int)`；改钱后如需落盘调 `saveNow()`。

### 3.3 单块地块快照 `PlotState`（字段与 §四十一 一一对应）

```java
// model/PlotState.java
String plotId;                 // 格唯一标识；为空时序列化为 "row,column"
int    row, column;            // 0-based 坐标
String state;                  // 土地状态名，建议直接用 A 枚举名，如 "EMPTY"/"TILLED"/…
String cropUuid;               // 作物唯一标识（无作物时整棵 crop 为 null）
String cropType;               // 作物类型名，如 "WHEAT"/"CORN"/"CARROT"
String growthStage;            // 成长阶段名（对应未来 GrowthStage 枚举名）
double growthProgress;         // 0.0~1.0
String plantWorldTime;         // 播种时刻（ISO-8601）
int    manualWaterCount;
String lastManualWaterGameDay;
// 均有 getter/setter；无作物判断用 hasCrop()（cropUuid != null）
```

> 注：`state/cropType/growthStage` 以**字符串**存取，A 交付后由 A 适配层做“A 枚举名 ↔ 字符串”双向映射——字符串值建议就等于 A 的枚举 `name()`，映射零成本。

---

## 4. 存档服务（Controller 不得感知文件位置，§三十九）

```java
// service/SaveService.java（P0 由 JsonSaveService 实现；P1 换 Sqlite 时业务调用不变）
boolean hasSave();
void save(GameState state);
GameState load();   // 无档返回 null；文件损坏/版本不符抛 IllegalStateException（GameManager 捕获后降级新档）
```

`JsonSaveService` 事实约定（队友只需了解，不必 import）：

- 默认文件 `data/save.json`（**`data/` 已在 .gitignore，本地存档不进仓库**）
- 写入 UTF-8、自动建目录；根节点保留 `version = 1` 与 `schema = "P0-json"` 供 P1 迁移
- **升级存档结构时**：必须递增 `SAVE_VERSION` 并在 `load()` 做兼容处理，**禁止原地改旧档结构**；同时同步通知 E
- 测试想用临时文件：`new JsonSaveService(Path)` 可注入路径

---

## 5. module-info.java —— 本次合并最易翻车点（高优先级）

当前内容（基线上）：

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

**你们若新增了包且里面有 FXML + controller，必须补一行**：

```java
opens 你的新包 to javafx.fxml;
```

否则 JavaFX 反射实例化 controller 会抛 `InaccessibleObjectException`，表现为“FXML 加载成功但运行时报错”。场景合并时 `module-info.java` 是**多路并发修改高冲突文件**，合并原则：**保留所有人的 opens 行，只增不减，不要互相覆盖**。

---

## 6. FXML / 工具类与入口

- FXML 加载统一走 `com.fieldstory.farm.util.FxmlUtil.load(你的Controller.class, "xxx.fxml").load()`（controller 与 fxml 同包放置）。
- 窗口/标题常量在 `config/AppConfig`：`WINDOW_WIDTH=960`、`WINDOW_HEIGHT=640`、`MAIN_VIEW_FXML="main-view.fxml"`、`APP_TITLE="FieldStoryFarm"`——别硬编码 960/640。
- 程序入口 `Launcher`（`com.fieldstory.farm` 包，maven `mainClass=com.fieldstory.farm/com.fieldstory.farm.Launcher`），运行 `mvnw javafx:run`，不要另建 `main`。

---

## 7. 编码 / 构建 / 提交纪律

1. **所有源文件 UTF-8**（pom 已固定 `project.build.sourceEncoding=UTF-8`）；**不要提交 GBK 文件**——仓库根目录那几份中文计划/文档（GBK）不入库，已确认未跟踪。
2. 本机 JDK **17**；测试框架 JUnit 5（surefire 3.5.2）。
3. **push 前必须 `mvnw test` 全绿**；别把 `data/save.json`、`target/`、`.idea/` 提交（已忽略）。
4. 每个模块一次提交、写清 feat/fix 前缀，**不要大杂烩提交**——场景合并不顺时需要 revert/拣选单个模块。
5. 本次我的 P0 已在 dev（`22640b6`），大家从 dev 拉新再开发，避免基于旧骨架返工。

---

## 8. 合并到 dev 的执行方案（大家 push 后由 E 执行）

1. 各自 push 自己分支并**在群里喊一声**。
2. E 执行 `git fetch --all` → 按 **A/C-D → B** 顺序逐个合入 dev，**每合一步跑一次 `mvnw test`**，失败即定位到人。
3. 预计冲突文件与处置：
   - `module-info.java`：合并所有 opens/exports 行（见 §5）
   - `pom.xml`：增量合并，保留双方新增依赖
   - `main-view.fxml` / `MainController`：若多人改主菜单，以最新版为准，**保留 GameManager 的开始/继续/保存钩子**
   - `GameManager` / `SceneManager` / `model/*`：E 持有，除非已事先协商，否则不应冲突
4. 合并收尾由 E 统一跑全量验证：`mvnw test` → `mvnw javafx:run` 冒烟（启动 → 开始/读档 → 各模块 mount → 退出自动存档 → 重启恢复）。

---

## 9. 合入前若对不齐，先看这三点

1. **D 时钟**：确认 `GameState.setGameDay(long)` / `setCurrentWorldTime(String)` 在你每次结算/存档前已写入。
2. **A 土地**：确认 `plots` 快照 ↔ Farm 双向映射在 A 侧完成且字段名与 §3.3 完全一致。
3. **B 商店**：gold 只读写 `state.getPlayer()`，不自己维护第二份金币；商店打开/关闭若涉及暂停用 `GameManager.pause()/resume()`。

有任何接口疑问，**先找 E（本模块）对齐再动手**，避免两个人同时改同一个类造成合并冲突。
