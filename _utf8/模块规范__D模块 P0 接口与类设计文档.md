一、GameClock 接口
1.1 接口定义
接口全名：com.fieldstory.farm.model.GameClock

实现类位置：com.fieldstory.farm.model.impl.BasicGameClock

依据：决策记录 D13（接口在包根，实现类以 Basic 前缀放 impl 子包）

1.2 方法签名
java
package com.fieldstory.farm.model;

public interface GameClock {

    int getTotalMinutes();

    int getGameDay();

    int getGameHour();

    int getGameMinute();

    String getTimeString();

    boolean isDaytime();

    void tick();

    void setTotalMinutes(int totalMinutes);

}
1.3 行为约束
约束项	约束内容	来源
时间比例	1 现实分钟 = 1 游戏小时；24 现实分钟 = 1 游戏日	规则文档 §5.1；验收规范 §5
初始时间	BasicGameClock 默认构造器将 totalMinutes 初始化为 360（第1天 06:00）	规则文档 §5.1
跨日处理	tick() 只负责累加总分钟数，不处理日结逻辑（日结由 Service 层负责）	验收规范 §81
存档恢复	必须提供 setTotalMinutes(int) 方法供 E 模块恢复存档	验收规范 §41
禁止新增方法	P0 阶段不得添加天气、离线、事件相关方法	验收规范 §10
1.4 序列化字段
JSON 存档中必须保存世界时间。D 模块通过 FarmGameModel.getWorldTimeTotalMinutes() 提供 int 值（对应 GameClock.getTotalMinutes()）。

存档字段名以 E 模块 GameState 为准：currentWorldTime（String，ISO-8601）。D 模块不直接读写 JSON 字段，仅提供/接收 int 值。

依据：验收规范 §41、§42；决策记录（方案 A：以 E 模块 GameState.currentWorldTime 为唯一存档字段）

二、BasicGameClock 实现类
2.1 类定义
java
package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.GameClock;
import static com.fieldstory.farm.util.GameConstants.*;

public class BasicGameClock implements GameClock {

    private int totalMinutes;

    public BasicGameClock() {
        this.totalMinutes = DAY_START;  // 360，第1天 06:00
    }

    public BasicGameClock(int totalMinutes) {
        this.totalMinutes = totalMinutes;
    }

    // 实现所有接口方法...
}
2.2 构造器约束
约束项	约束内容
无参构造器	初始化为 DAY_START（360 分钟，对应第 1 天 06:00）
单参构造器	接受 int totalMinutes，用于存档恢复
禁止其他构造器	不得提供带 LocalDateTime 等参数的构造器，避免时间源不统一
依据：规则文档 §8（禁止散落时间源）

三、FarmGameModel 修改
3.1 新增字段
java
private GameClock gameClock;
3.2 新增方法
java
public void tick() {
    gameClock.tick();
}

public GameClock getGameClock() {
    return gameClock;
}

public int getWorldTimeTotalMinutes() {
    return gameClock.getTotalMinutes();
}

public void restoreWorldTime(int totalMinutes) {
    gameClock.setTotalMinutes(totalMinutes);
}
3.3 行为约束
约束项	约束内容	来源
初始化位置	在 FarmGameModel 构造器中初始化 gameClock = new BasicGameClock()	验收规范 §5
tick() 职责	只调用 gameClock.tick()，不含天气/事件/离线逻辑	验收规范 §10
存档恢复	提供 restoreWorldTime() 供 E 模块调用	验收规范 §41
生长更新	FarmGameModel 不直接驱动作物生长，由 Controller 协调 A 模块的 GrowthService	验收规范 §3.1
四、RandomProvider 工具类
4.1 类定义
java
package com.fieldstory.farm.util;

import java.util.Random;

public final class RandomProvider {

    private static final Random random = new Random();

    private RandomProvider() {}

    public static int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public static double nextDouble() {
        return random.nextDouble();
    }

    public static boolean nextBoolean() {
        return random.nextBoolean();
    }

    public static void setSeed(long seed) {
        random.setSeed(seed);
    }

}
4.2 行为约束
约束项	约束内容	来源
类类型	final class，私有构造器，所有方法 static	验收规范 §7
种子管理	必须提供 setSeed(long) 方法	验收规范 §90、§143
P0 使用范围	P0 阶段仅在单元测试中调用，业务代码不实际使用（P0 无随机需求）	验收规范 §7
禁止各自随机	任何 Service 不得自行 new Random()	验收规范 §7
五、StatusView 视图类
5.1 类定义
java
package com.fieldstory.farm.view;

import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.GameClock;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class StatusView extends HBox {

    private final FarmGameModel model;
    private final Label dayLabel;
    private final Label timeLabel;
    private final Label goldLabel;
    private final Label weatherLabel;  // P1 预留

    public StatusView(FarmGameModel model) {
        this.model = model;
        // 初始化 UI 组件...
        update();
    }

    public void update() {
        // 读取 Model 数据刷新 UI
    }

    private String getDaytimeIcon() {
        // 根据 isDaytime() 返回 "☀️" 或 "🌙"
    }

}
5.2 行为约束
约束项	约束内容	来源
只读原则	只能通过 Getter 读取数据，不得调用任何 Service 写方法	验收规范 §3.1
显示内容	必须包含：金币、游戏日、当前时间（含昼夜标识）	验收规范 §36
天气占位	weatherLabel P0 固定显示 "☀️ 晴天"，为 P1 预留	验收规范 §76
刷新频率	由 FarmController 定时器触发，每秒调用一次	规则文档 §5.1
NPE 保护	若 getPlayer() 返回 null，显示 "💰 --" 而不抛出异常	非功能需求 §2.2
六、GameConstants 常量类
6.1 完整常量定义
java
package com.fieldstory.farm.util;

public final class GameConstants {

    private GameConstants() {}

    // ===== 地图与布局 =====
    public static final int MAP_ROWS = 12;
    public static final int MAP_COLS = 12;
    public static final int CENTER_START_ROW = 2;
    public static final int CENTER_END_ROW = 9;
    public static final int CENTER_START_COL = 2;
    public static final int CENTER_END_COL = 9;
    public static final int TILE_SIZE = 64;

    // ===== 时间系统 =====
    public static final int MINUTES_PER_TICK = 10;
    public static final int MINUTES_PER_DAY = 1440;
    public static final int DAY_START = 360;    // 06:00
    public static final int DAY_END = 1080;     // 18:00

    // ===== 经济系统 =====
    public static final int INITIAL_GOLD = 500;
    public static final int TILL_COST = 5;

    // ===== P0 固定倍率 =====
    public static final double WEATHER_RATE_P0 = 1.0;
    public static final double DECORATION_RATE_P0 = 1.0;
    public static final double EVENT_RATE_P0 = 1.0;

}
6.2 行为约束
约束项	约束内容	来源
地图尺寸	12×12，中心 8×8 种植区（坐标 (2,2)~(9,9)，0-based）	规则文档 §10.1；验收规范 §11；决策 D10
时间换算	MINUTES_PER_DAY = 1440（1 日 = 24 小时 × 60 分钟）	规则文档 §5.1
昼夜边界	DAY_START = 360（06:00），DAY_END = 1080（18:00）	规则文档 §5.1
初始金币	INITIAL_GOLD = 500	规则文档 §63；验收规范 §35
开垦消耗	TILL_COST = 5	规则文档 §12.1；验收规范 §15
P0 倍率	必须带 _P0 后缀，标识为 P0 固定占位值	验收规范 §10
禁止硬编码	Controller/Service 中不得出现 1440、360、1080 等魔法数字	规则文档 §8
七、FarmController 修改
7.1 新增字段与方法
java
package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.view.StatusView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class FarmController {

    private final FarmGameModel model;
    private final StatusView statusView;
    private final Timeline gameLoopTimeline;

    public FarmController(FarmGameModel model, StatusView statusView) {
        this.model = model;
        this.statusView = statusView;
        this.gameLoopTimeline = initGameLoop();
    }

    private Timeline initGameLoop() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            model.tick();
            statusView.update();
            // 注意：此处由 Controller 协调调用 A 模块的生长更新
            // 具体调用方式需与 A 模块协商
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        return timeline;
    }

    public void startGameLoop() {
        gameLoopTimeline.play();
    }

    public void stopGameLoop() {
        gameLoopTimeline.pause();
    }

}
7.2 行为约束
约束项	约束内容	来源
定时器类型	必须使用 JavaFX Timeline + KeyFrame，禁止 java.util.Timer	JavaFX 线程安全要求
定时器生命周期	提供 startGameLoop() / stopGameLoop()，退出时停止	非功能需求 §2.3
Controller 职责	只负责调度（调用 Model 和 Service），不写业务逻辑	验收规范 §3.1
生长更新协调	在定时器回调中协调调用 A 模块的 GrowthService，具体方式与 A 模块协商	验收规范 §3.1
八、枚举类型（P1/P2 预留）
8.1 WeatherType（P1 预留）
java
package com.fieldstory.farm.model;

public enum WeatherType {
    SUNNY, RAIN, DROUGHT, GREEN_RAIN
}
约束：P0 阶段只允许定义枚举常量，禁止在 P0 代码中引用。

依据：验收规范 §48

8.2 EventType（P2 预留）
java
package com.fieldstory.farm.model;

public enum EventType {
    METEOR_SHOWER, MYSTERY_MERCHANT, ANIMAL_VISIT, RAINBOW_DAY, NONE
}
约束：P0 阶段只允许定义枚举常量，禁止在 P0 代码中引用。

依据：验收规范 §90

九、存档对接（与 E 模块协作）
9.1 提供的存档字段
成员D向 E 模块提供以下值，由 E 模块在 GameState（或 SaveData）中保存：

java
// D 模块提供（int，对应 GameClock.getTotalMinutes()）
public int getWorldTimeTotalMinutes()

// E 模块 GameState 中的存档字段（方案 A：以 E 模块为准）
private String currentWorldTime;  // ISO-8601，由 E 模块从 int 值转换后保存
9.2 存档恢复流程
E 模块读取 JSON/SQLite 中的 currentWorldTime（ISO-8601），转换为 int 总分钟数

E 模块调用 farmGameModel.restoreWorldTime(int totalMinutes)

然后再初始化其他组件

9.3 行为约束
约束项	约束内容	来源
P0 存档格式	JSON 临时存档，保存 currentWorldTime（E 模块 GameState 字段，ISO-8601）	验收规范 §41
P0 退出行为	退出时保存当前世界时间，不推进离线	验收规范 §42
P1 迁移兼容	totalMinutes 字段与 SQLite world_state.current_world_time 对齐	验收规范 §72、§73
十、单元测试要求
10.1 BasicGameClockTest
测试场景	预期结果	来源
初始 totalMinutes=360	getGameDay()=1，getTimeString()="06:00"，isDaytime()=true	规则文档 §5.1
连续 tick 24 次（每次 10 分钟）	totalMinutes=600，时间变为 10:00	验收规范 §25
tick 144 次（跨一天）	totalMinutes=1800，getGameDay()=2，时间为 06:00	验收规范 §25
setTotalMinutes(0)	getTimeString()="00:00"，isDaytime()=false	—
10.2 RandomProviderTest
测试场景	预期结果	来源
setSeed(12345L) 后固定序列	两次调用 nextInt(100) 返回相同结果	验收规范 §143
nextInt(bound)	返回值在 [0, bound) 范围内	—
10.3 StatusViewTest
测试场景	预期结果	来源
update() 调用	不抛出任何异常	非功能需求 §2.2
getPlayer() 返回 null	显示 "💰 --"，不抛出 NPE	非功能需求 §2.2
十一、跨模块接口约定
11.1 与 A 模块（土地与作物）
约定项	内容
时间获取方式	A 模块通过 FarmGameModel.getGameClock() 获取当前时间
生长更新触发	由 Controller 定时器统一调度，同时调用 model.tick() 和 A 模块的生长更新方法
接口稳定性	GameClock 接口在 P0 确定后，后续阶段只新增不修改现有方法
11.2 与 E 模块（存档）
约定项	内容
存档字段	E 模块 GameState 保存 currentWorldTime（String，ISO-8601）；D 模块提供 int 值（getWorldTimeTotalMinutes()）
恢复方法	E 模块调用 FarmGameModel.restoreWorldTime(int)
存档时机	购买、播种、收获等行为后自动保存，退出时强制保存
十二、约束来源索引
约束内容	文档来源	章节
时间比例（1分钟=1小时）	规则文档	§5.1
昼夜边界（06:00~18:00）	规则文档	§5.1
GameClock 为唯一时间源	规则文档	§8
地图尺寸 12×12	规则文档 / 验收规范	§10.1 / §11
初始金币 500	规则文档	§63
P0 禁止实现清单	验收规范	§10
P0 必须实现内容	验收规范	§36、§41
P0 UI 最低要求	验收规范	§36
分层架构固定	验收规范	§3.1
随机系统统一	验收规范	§7、§90
固定种子测试	验收规范	§143
接口在包根，实现类在 impl	决策记录	D13
附录：编码前自检清单
检查项	状态
GameClock 接口放 model 包，BasicGameClock 放 model.impl	☐
BasicGameClock 默认从 360 分钟（06:00）开始	☐
tick() 只累加分钟，不含日结逻辑	☐
FarmGameModel.tick() 只调用 gameClock.tick()	☐
FarmGameModel 提供 restoreWorldTime(int) 方法	☐
RandomProvider 为 final class，私有构造器，静态方法	☐
RandomProvider 提供 setSeed(long)	☐
StatusView 只读，无任何写方法调用	☐
FarmController 使用 Timeline 而非 Timer	☐
所有常量通过 GameConstants 引用，无魔法数字	☐
P0 倍率常量带 _P0 后缀	☐
P0 代码中无 WeatherType / EventType 的业务引用	☐
GameState 包含 currentWorldTime 字段（方案 A；D 模块提供 int 值）	☐
单元测试覆盖固定种子和时间边界	☐
跨模块接口通过 FarmGameModel 暴露	☐
包名统一为 com.fieldstory.farm.*	☐