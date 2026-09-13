package com.fieldstory.farm.util;

/**
 * 《田野物语 · 三韵集》全局游戏常量。
 *
 * <p>
 * 本类用于集中维护全项目共享且在当前版本中固定不变的数值，
 * 避免 Controller、Service、Model 中散落魔法数字。
 * </p>
 *
 * <p>
 * P0阶段主要包括：
 * 1. 地图与布局常量
 * 2. 世界时间常量
 * 3. 玩家与经济常量
 * 4. P0固定倍率
 * </p>
 *
 * <p>
 * 注意：
 * 本类只保存真正的全局固定常量。
 * 作物种子价格和基础售价不放在这里，
 * 由 CropType 作为唯一数据源负责。
 * </p>
 */
public final class GameConstants {

    // =========================================================
    // 地图与布局
    // =========================================================

    /**
     * 农场地图总行数。
     *
     * P0地图固定为12×12。
     */
    public static final int MAP_ROWS = 12;

    /**
     * 农场地图总列数。
     */
    public static final int MAP_COLS = 12;

    /**
     * 中心8×8农田区域起始行。
     *
     * 使用0-based坐标：
     * 中心区域为第2~9行。
     */
    public static final int CENTER_START_ROW = 2;

    /**
     * 中心8×8农田区域结束行。
     */
    public static final int CENTER_END_ROW = 9;

    /**
     * 中心8×8农田区域起始列。
     */
    public static final int CENTER_START_COL = 2;

    /**
     * 中心8×8农田区域结束列。
     */
    public static final int CENTER_END_COL = 9;

    /**
     * 单个地图格子的默认像素尺寸。
     *
     * 属于当前P0界面基础布局参数。
     * 后续统一UI时如果调整，应由全组统一修改。
     */
    public static final int TILE_SIZE = 64;


    // =========================================================
    // 世界时间
    // =========================================================

    /**
     * 每次基础游戏时钟tick推进的游戏分钟数。
     *
     * 当前D模块P0设计约定：
     * 每次tick推进10游戏分钟。
     */
    public static final int MINUTES_PER_TICK = 10;

    /**
     * 一个游戏日包含的游戏分钟数。
     *
     * 24 × 60 = 1440。
     */
    public static final int MINUTES_PER_DAY = 1440;

    /**
     * 默认游戏日开始时间。
     *
     * 360分钟 = 06:00。
     */
    public static final int DAY_START = 360;

    /**
     * 昼夜显示中的白天结束时间。
     *
     * 1080分钟 = 18:00。
     */
    public static final int DAY_END = 1080;

    /**
     * 每次基础游戏时钟tick推进的游戏天数。
     *
     * 由 {@link #MINUTES_PER_TICK} ÷ {@link #MINUTES_PER_DAY} 折算：
     * 10 ÷ 1440，供主循环协调 GrowthService 时计算经过游戏天数
     * （验收规范 §二十五：必须支持非整日成长）。
     */
    public static final double GAME_DAYS_PER_TICK =
            (double) MINUTES_PER_TICK / MINUTES_PER_DAY;


    // =========================================================
    // 玩家与经济
    // =========================================================

    /**
     * 新游戏初始金币。
     *
     * 正式规则：
     * 玩家新建游戏时拥有500金币。
     *
     * GameManager创建新游戏时应引用此常量，
     * 不应再次单独硬编码500。
     */
    public static final int INITIAL_GOLD = 500;

    /**
     * 开垦一格EMPTY土地所需金币。
     *
     * A模块开垦土地时通过EconomyService扣除该费用。
     */
    public static final int TILL_COST = 5;


    // =========================================================
    // P0固定倍率
    // =========================================================

    /**
     * P0天气倍率。
     *
     * P0尚未正式启用天气系统，
     * 因此固定为1.0。
     *
     * P1开始由WeatherService提供实际倍率。
     */
    public static final double WEATHER_RATE_P0 = 1.0;

    /**
     * P0装饰倍率。
     *
     * P0尚未启用装饰Buff，
     * 因此固定为1.0。
     */
    public static final double DECORATION_RATE_P0 = 1.0;

    /**
     * P0随机事件倍率。
     *
     * P0尚未启用随机事件，
     * 因此固定为1.0。
     *
     * P2由EventService正式提供事件效果。
     */
    public static final double EVENT_RATE_P0 = 1.0;


    // =========================================================
    // P1天气概率（规则文档 §十九）
    // =========================================================

    /**
     * 晴天出现概率（百分比）。
     *
     * 规则文档 §十九：40%。
     */
    public static final int WEATHER_PROB_SUNNY = 40;

    /**
     * 雨天出现概率（百分比）。
     *
     * 规则文档 §十九：25%。
     */
    public static final int WEATHER_PROB_RAIN = 25;

    /**
     * 干旱出现概率（百分比）。
     *
     * 规则文档 §十九：20%。
     */
    public static final int WEATHER_PROB_DROUGHT = 20;

    /**
     * 绿雨出现概率（百分比）。
     *
     * 规则文档 §十九：15%。
     */
    public static final int WEATHER_PROB_GREEN_RAIN = 15;


    // =========================================================
    // P1天气成长倍率（规则文档 §十九）
    // =========================================================

    /**
     * 晴天成长倍率。
     *
     * 规则文档 §十九：×1.0。
     */
    public static final double WEATHER_RATE_SUNNY = 1.0;

    /**
     * 雨天成长倍率。
     *
     * 规则文档 §十九：×1.5。
     */
    public static final double WEATHER_RATE_RAIN = 1.5;

    /**
     * 干旱成长倍率。
     *
     * 规则文档 §十九：×0.5。
     */
    public static final double WEATHER_RATE_DROUGHT = 0.5;

    /**
     * 绿雨成长倍率。
     *
     * 规则文档 §十九：×2.0。
     */
    public static final double WEATHER_RATE_GREEN_RAIN = 2.0;


    // =========================================================
    // P1天气品质分（规则文档 §三十五）
    // =========================================================

    /**
     * 雨天品质分/次。
     *
     * 规则文档 §三十五：+5。
     */
    public static final int WEATHER_QUALITY_RAIN = 5;

    /**
     * 雨天品质分上限。
     *
     * 规则文档 §三十五：+20。
     */
    public static final int WEATHER_QUALITY_RAIN_CAP = 20;

    /**
     * 干旱品质分/次。
     *
     * 规则文档 §三十五：+8。
     */
    public static final int WEATHER_QUALITY_DROUGHT = 8;

    /**
     * 干旱品质分上限。
     *
     * 规则文档 §三十五：+24。
     */
    public static final int WEATHER_QUALITY_DROUGHT_CAP = 24;

    /**
     * 绿雨品质分/次。
     *
     * 规则文档 §三十五：+15。
     */
    public static final int WEATHER_QUALITY_GREEN_RAIN = 15;

    /**
     * 绿雨品质分上限。
     *
     * 规则文档 §三十五：+45。
     */
    public static final int WEATHER_QUALITY_GREEN_RAIN_CAP = 45;


    // =========================================================
    // P2随机事件概率（规则文档 §四十七）
    // =========================================================

    /**
     * 无事件概率（百分比）。
     *
     * 规则文档 §四十七：74%。
     */
    public static final int EVENT_PROB_NONE = 74;

    /**
     * 流星夜概率（百分比）。
     *
     * 规则文档 §四十七：5%。
     */
    public static final int EVENT_PROB_METEOR_SHOWER = 5;

    /**
     * 神秘商人概率（百分比）。
     *
     * 规则文档 §四十七：8%。
     */
    public static final int EVENT_PROB_MYSTERY_MERCHANT = 8;

    /**
     * 小动物来访概率（百分比）。
     *
     * 规则文档 §四十七：10%。
     */
    public static final int EVENT_PROB_ANIMAL_VISIT = 10;

    /**
     * 彩虹日概率（百分比）。
     *
     * 规则文档 §四十七：3%。
     */
    public static final int EVENT_PROB_RAINBOW_DAY = 3;


    // =========================================================
    // P2随机事件持续时间（游戏小时，规则文档 §四十八~§五十一）
    // =========================================================

    /**
     * 流星夜持续时间（游戏小时）。
     *
     * 规则文档 §四十八：24 游戏小时。
     */
    public static final int EVENT_DURATION_METEOR_SHOWER = 24;

    /**
     * 神秘商人持续时间（游戏小时）。
     *
     * 规则文档 §四十九：12 游戏小时。
     */
    public static final int EVENT_DURATION_MYSTERY_MERCHANT = 12;

    /**
     * 彩虹日持续时间（游戏小时）。
     *
     * 规则文档 §五十一：24 游戏小时。
     */
    public static final int EVENT_DURATION_RAINBOW_DAY = 24;


    // =========================================================
    // P2随机事件效果（规则文档 §四十八/§五十一）
    // =========================================================

    /**
     * 流星夜品质分加成。
     *
     * 规则文档 §四十八：品质评分 +20。
     */
    public static final int EVENT_QUALITY_METEOR_SHOWER = 20;

    /**
     * 彩虹日品质分加成。
     *
     * 规则文档 §五十一：品质评分 +15。
     */
    public static final int EVENT_QUALITY_RAINBOW_DAY = 15;

    /**
     * 流星夜传说突破概率加成（百分比）。
     *
     * 规则文档 §四十八：传说突破概率 +10%。
     */
    public static final int EVENT_METEOR_LEGENDARY_BONUS = 10;

    /**
     * 彩虹日成长倍率（EventRate）。
     *
     * 规则文档 §五十一：EventRate ×2。
     */
    public static final double EVENT_RAINBOW_EVENT_RATE = 2.0;


    /**
     * 工具类禁止实例化。
     */
    private GameConstants() {
    }
}