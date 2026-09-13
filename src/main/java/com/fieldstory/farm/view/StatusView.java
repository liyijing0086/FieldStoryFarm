package com.fieldstory.farm.view;

import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.WeatherState;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.service.EventService;
import com.fieldstory.farm.service.WeatherService;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

/**
 * 顶部状态栏视图（D 模块 P0：世界环境；P1 升级天气显示）。
 *
 * <p>依据《D模块 P0 接口与类设计文档》§五、《D模块 P1 接口与类设计文档》§4.7、
 * 《P0-P4功能实现与验收规范》§36/§76。
 *
 * <p>显示：游戏日、游戏时间、金币、天气（P1 升级为「图标 + 显示名」）。
 * 只读原则：只能通过 Getter 读取数据，不得调用任何 Service 写方法（验收 §3.1）；
 * 天气只调用 {@link WeatherService} 的查询方法，不得调用 {@code rollDailyWeather}。
 */
public class StatusView extends HBox {

    /** 数据来源（只读）。 */
    private final FarmGameModel model;

    /** 天气服务（只读查询，可为 null）。 */
    private final WeatherService weatherService;

    /** 天气状态（只读，可为 null）。 */
    private final WeatherState weatherState;

    /** 事件服务（只读查询，可为 null）。 */
    private final EventService eventService;

    /** 事件状态（只读，可为 null）。 */
    private final EventState eventState;

    /** 玩家（只读，可为 null；用于显示金币，B 模块数据）。 */
    private final Player player;

    private final Label dayLabel;
    private final Label timeLabel;
    private final Label goldLabel;
    private final Label weatherLabel;
    private final Label eventLabel;

    /**
     * 注入模型，初始化 UI 组件并调用 {@link #update()}。
     *
     * <p>P0 兼容构造：无天气服务时天气显示固定「晴天」（P0 固定晴天语义，验收规范 §七十六）。
     *
     * @param model 游戏模型
     */
    public StatusView(FarmGameModel model) {
        this(model, null, null);
    }

    /**
     * 注入模型与玩家，初始化 UI 组件并调用 {@link #update()}。
     *
     * <p>用于显示金币（B 模块 {@link Player} 数据）；天气显示固定「晴天」。
     *
     * @param model  游戏模型
     * @param player 玩家（可为 null，此时金币显示占位「金币 --」）
     */
    public StatusView(FarmGameModel model, Player player) {
        this(model, null, null, player);
    }

    /**
     * 注入模型与天气服务，初始化 UI 组件并调用 {@link #update()}。
     *
     * @param model          游戏模型
     * @param weatherService 天气服务（只读查询，可为 null）
     * @param weatherState   天气状态（可为 null）
     */
    public StatusView(FarmGameModel model, WeatherService weatherService, WeatherState weatherState) {
        this(model, weatherService, weatherState, null);
    }

    /**
     * 注入模型、天气服务与玩家，初始化 UI 组件并调用 {@link #update()}。
     *
     * @param model          游戏模型
     * @param weatherService 天气服务（只读查询，可为 null）
     * @param weatherState   天气状态（可为 null）
     * @param player         玩家（可为 null，此时金币显示占位「金币 --」）
     */
    public StatusView(FarmGameModel model, WeatherService weatherService,
                      WeatherState weatherState, Player player) {
        this.model = model;
        this.weatherService = weatherService;
        this.weatherState = weatherState;
        this.player = player;
        // P2：事件服务/状态从模型只读获取（P0/P1 公开构造签名保持不变）
        this.eventService = model == null ? null : model.getEventService();
        this.eventState = model == null ? null : model.getEventState();
        this.dayLabel = new Label();
        this.timeLabel = new Label();
        this.goldLabel = new Label();
        this.weatherLabel = new Label();
        this.eventLabel = new Label();
        this.setSpacing(16);
        this.getChildren().addAll(dayLabel, timeLabel, goldLabel, weatherLabel, eventLabel);
        // 文字色统一使用 7 色主色表 #493526（UI 规范，禁止自造色）
        Color textColor = Color.web("#493526");
        dayLabel.setTextFill(textColor);
        timeLabel.setTextFill(textColor);
        goldLabel.setTextFill(textColor);
        weatherLabel.setTextFill(textColor);
        eventLabel.setTextFill(textColor);
        update();
    }

    /**
     * 刷新显示（由 Controller 定时调用，每秒一次，规则 §5.1）。
     */
    public void update() {
        GameClock clock = model.getGameClock();
        dayLabel.setText("第 " + clock.getGameDay() + " 天");
        timeLabel.setText(getDaytimeIcon() + " " + clock.getTimeString());
        goldLabel.setText(player == null ? "金币 --" : "金币 " + player.getGold());
        weatherLabel.setText(buildWeatherText());
        eventLabel.setText(buildEventText());
    }

    /**
     * 构造天气显示文本「图标 + 显示名」。
     *
     * <p>NPE 保护：{@code weatherService} 或 {@code weatherState} 为 null 时显示占位，
     * 不抛异常（非功能需求 §2.2）。
     *
     * @return 天气显示文本
     */
    private String buildWeatherText() {
        if (weatherService == null || weatherState == null) {
            return "晴天";
        }
        WeatherType type = weatherState.getWeatherType();
        if (type == null) {
            return "晴天";
        }
        return weatherService.getIcon(type) + " " + weatherService.getDisplayName(type);
    }

    /**
     * 构造事件显示文本「图标 + 显示名」。
     *
     * <p>NPE 保护：{@code eventService} 或 {@code eventState} 为 null 时显示占位，
     * 不抛异常（非功能需求 §2.2）。无事件（{@code NONE}）时显示「无事件」。
     *
     * @return 事件显示文本
     */
    private String buildEventText() {
        if (eventService == null || eventState == null) {
            return "无事件";
        }
        EventType type = eventState.getEventType();
        if (type == null || type == EventType.NONE) {
            return "无事件";
        }
        return eventService.getIcon(type) + " " + eventService.getDisplayName(type);
    }

    /**
     * 根据 {@code isDaytime()} 返回白天/夜晚图标。
     *
     * @return 白天返回太阳图标，否则返回月亮图标
     */
    private String getDaytimeIcon() {
        return model.getGameClock().isDaytime() ? "\u2600" : "\uD83C\uDF19";
    }

    /** 供测试读取游戏日文本。 */
    public String getDayText() {
        return dayLabel.getText();
    }

    /** 供测试读取时间文本。 */
    public String getTimeText() {
        return timeLabel.getText();
    }

    /** 供测试读取金币文本。 */
    public String getGoldText() {
        return goldLabel.getText();
    }

    /** 供测试读取天气文本。 */
    public String getWeatherText() {
        return weatherLabel.getText();
    }

    /** 供测试读取事件文本。 */
    public String getEventText() {
        return eventLabel.getText();
    }
}
