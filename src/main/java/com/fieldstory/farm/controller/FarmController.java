package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.service.GrowthService;
import com.fieldstory.farm.view.StatusView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.List;

import static com.fieldstory.farm.util.GameConstants.GAME_DAYS_PER_TICK;
import static com.fieldstory.farm.util.GameConstants.WEATHER_RATE_P0;

/**
 * 农场主控制器（D 模块 P0：世界环境）。
 *
 * <p>依据《D模块 P0 接口与类设计文档》§七、《P0-P4功能实现与验收规范》§3.1。
 *
 * <p>职责：只负责调度（调用 Model 和 Service），不写业务逻辑。
 * 定时器必须使用 JavaFX {@link Timeline} + {@link KeyFrame}，禁止 {@code java.util.Timer}。
 *
 * <p><b>成长协调（验收规范 §3.1）：</b>D 负责"什么时候推进"，
 * A 负责"怎么成长"。本类在每次 tick 后按经过游戏天数遍历当前 Farm 的作物，
 * 调用 A 模块 {@link GrowthService#applyGrowth(Crop, double, double)}，
 * 并传入 D 模块提供的当前天气倍率 {@code WeatherRate}（验收规范 §四十九）；
 * 成长公式不在本类重复实现。
 *
 * <p><b>跨天回调（A 模块 GrowthService 接入点）：</b>主循环检测到游戏日递增时，
 * 触发 {@link #setOnDayChanged(Runnable)} 注入的回调，供装配层协调 A 模块按「天」推进成长
 * （验收规范 §3.1「生长由 Controller 协调」的落实）。
 */
public class FarmController {

    private final FarmGameModel model;
    private final StatusView statusView;

    /**
     * 成长服务（A 模块）；未注入时为 null，此时主循环只推进时间、不协调成长
     * （P0 早期装配前保持向后兼容）。
     */
    private final GrowthService growthService;

    private final Timeline gameLoopTimeline;

    /**
     * 跨天回调（A 模块 GrowthService 的接入点，验收规范 §3.1）。
     *
     * <p>默认空实现；由装配层通过 {@link #setOnDayChanged(Runnable)} 注入。
     * 每次游戏日递增时触发一次，用于协调 A 模块按「天」推进作物成长。
     */
    private Runnable onDayChanged = () -> {};

    /** 上一次观察到的游戏日；-1 表示尚未初始化（首个 tick 只记录、不触发回调）。 */
    private int lastGameDay = -1;

    /**
     * 注入模型和视图，初始化定时器（不协调成长，向后兼容）。
     *
     * @param model      游戏模型
     * @param statusView 状态栏视图
     */
    public FarmController(FarmGameModel model, StatusView statusView) {
        this(model, statusView, (GrowthService) null);
    }

    /**
     * 注入模型、视图与跨天回调，初始化定时器。
     *
     * @param model         游戏模型
     * @param statusView    状态栏视图
     * @param onDayChanged  跨天回调（A 模块 GrowthService 接入点），可为 null（忽略）
     */
    public FarmController(FarmGameModel model, StatusView statusView,
                          Runnable onDayChanged) {
        this(model, statusView, (GrowthService) null);
        setOnDayChanged(onDayChanged);
    }

    /**
     * 注入模型、视图与成长服务，初始化定时器。
     *
     * @param model         游戏模型
     * @param statusView    状态栏视图
     * @param growthService 成长服务（A 模块），可为 null（不协调成长）
     */
    public FarmController(FarmGameModel model, StatusView statusView,
                          GrowthService growthService) {
        this.model = model;
        this.statusView = statusView;
        this.growthService = growthService;
        this.gameLoopTimeline = initGameLoop();
    }

    /**
     * 创建 Timeline 和 KeyFrame，每秒触发一次：
     * {@code model.tick()} → 协调 {@link GrowthService} 推进作物 → {@code statusView.update()}。
     *
     * <p>成长协调：遍历当前 Farm 全部 Soil，对已播种（crop 非 null）的作物
     * 按 {@link com.fieldstory.farm.util.GameConstants#GAME_DAYS_PER_TICK}
     * 与当前天气倍率调用 {@code growthService.applyGrowth(crop, elapsedGameDays, weatherRate)}。
     * Farm 未装配或 GrowthService 未注入时跳过（P0 早期装配前）。
     *
     * @return 已配置的定时器
     */
    private Timeline initGameLoop() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> handleTick()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        return timeline;
    }

    /**
     * 执行一次主循环 tick：推进时间 → 检测跨天并触发回调 → 协调成长 → 刷新视图。
     *
     * <p>抽为包级方法便于单元测试（Timeline 的 KeyFrame 处理器直接调用本方法）。
     * 首个 tick 只记录当前游戏日（{@code lastGameDay < 0}），不触发回调；
     * 之后仅当游戏日严格递增时触发一次 {@link #setOnDayChanged(Runnable)} 回调。
     */
    void handleTick() {
        model.tick();
        int day = model.getGameClock().getGameDay();
        if (lastGameDay < 0) {
            lastGameDay = day;
        } else if (day > lastGameDay) {
            lastGameDay = day;
            onDayChanged.run();
        }
        advanceCrops();
        statusView.update();
    }

    /**
     * 协调 A 模块成长服务推进当前 Farm 全部作物（D 只负责推进时机）。
     *
     * <p>Farm 未装配或 GrowthService 未注入时不做任何事。
     */
    private void advanceCrops() {
        advanceCrops(model.getFarm(), growthService, GAME_DAYS_PER_TICK,
                currentWeatherRate());
    }

    /**
     * 读取当前天气的成长倍率 {@code WeatherRate}（D 模块提供，验收规范 §四十九）。
     *
     * <p>P1 成长公式 {@code BaseDailyProgress × ElapsedGameDays × WeatherRate × OperationRate}
     * 中的 {@code WeatherRate} 由 D 模块 {@link com.fieldstory.farm.service.WeatherService#getGrowthRate}
     * 提供；D 只负责取值并传给 A 模块 {@link GrowthService}，不参与公式组装（D 模块 P1 文档 §1.4）。
     *
     * <p>天气服务/状态未装配时返回 {@link com.fieldstory.farm.util.GameConstants#WEATHER_RATE_P0}
     * （1.0），保持 P0 行为，避免早期装配前 NPE。
     *
     * @return 当前天气成长倍率
     */
    private double currentWeatherRate() {
        if (model.getWeatherService() == null || model.getWeatherState() == null) {
            return WEATHER_RATE_P0;
        }
        return model.getWeatherService().getGrowthRate(model.getWeatherState().getWeatherType());
    }

    /**
     * 纯函数：遍历 Farm 全部 Soil，对已播种作物按经过游戏天数调用成长服务。
     *
     * <p>D 只负责"什么时候推进"，成长公式由 A 模块 {@link GrowthService} 实现，
     * 本方法不重复任何成长规则（验收规范 §3.1）。
     *
     * <p>空安全：farm / growthService / soils / soil 任一为 null 时安全跳过，
     * 便于 P0 早期装配前调用与单元测试。
     *
     * @param farm             农田地图，可为 null
     * @param growthService    成长服务，可为 null
     * @param elapsedGameDays  本次经过的游戏天数
     */
    static void advanceCrops(Farm farm, GrowthService growthService,
                             double elapsedGameDays) {
        advanceCrops(farm, growthService, elapsedGameDays, WEATHER_RATE_P0);
    }

    /**
     * 纯函数：遍历 Farm 全部 Soil，对已播种作物按经过游戏天数与天气倍率调用成长服务。
     *
     * <p>D 只负责"什么时候推进"与"当前天气倍率是多少"，成长公式由 A 模块
     * {@link GrowthService} 实现，本方法不重复任何成长规则（验收规范 §3.1）。
     *
     * <p>P1 升级（验收规范 §四十九）：调用
     * {@link GrowthService#applyGrowth(Crop, double, double)} 传入 {@code weatherRate}，
     * 使天气倍率进入成长公式。
     *
     * <p>空安全：farm / growthService / soils / soil 任一为 null 时安全跳过，
     * 便于 P0 早期装配前调用与单元测试。
     *
     * @param farm             农田地图，可为 null
     * @param growthService    成长服务，可为 null
     * @param elapsedGameDays  本次经过的游戏天数
     * @param weatherRate      当前天气成长倍率（D 模块提供）
     */
    static void advanceCrops(Farm farm, GrowthService growthService,
                             double elapsedGameDays, double weatherRate) {
        if (farm == null || growthService == null) {
            return;
        }
        List<Soil> soils = farm.getSoils();
        if (soils == null) {
            return;
        }
        for (Soil soil : soils) {
            if (soil == null) {
                continue;
            }
            Crop crop = soil.getCrop();
            if (crop != null) {
                growthService.applyGrowth(crop, elapsedGameDays, weatherRate);
            }
        }
    }

    /**
     * 启动定时器。
     */
    public void startGameLoop() {
        gameLoopTimeline.play();
    }

    /**
     * 停止定时器（退出时停止，非功能需求 §2.3）。
     */
    public void stopGameLoop() {
        gameLoopTimeline.pause();
    }

    /**
     * 设置跨天回调（A 模块 GrowthService 的接入点，验收规范 §3.1）。
     *
     * <p>每次游戏日递增时触发一次。参数为 null 时忽略（保持原回调不变，防御式）。
     *
     * @param onDayChanged 跨天回调，可为 null（忽略）
     */
    public void setOnDayChanged(Runnable onDayChanged) {
        if (onDayChanged == null) {
            return;
        }
        this.onDayChanged = onDayChanged;
    }
}
