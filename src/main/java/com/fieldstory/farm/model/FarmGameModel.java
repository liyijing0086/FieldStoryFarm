package com.fieldstory.farm.model;

import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.model.impl.BasicWeatherState;
import com.fieldstory.farm.service.WeatherService;
import com.fieldstory.farm.service.impl.BasicWeatherService;

/**
 * 游戏模型聚合（D 模块 P0：世界环境；P1 聚合天气系统）。
 *
 * <p>依据《D模块 P0 接口与类设计文档》§三、《D模块 P1 接口与类设计文档》§5.3、
 * 《FSF_P0-P4 分阶段实现与验收规范》§5/§41/§42。
 *
 * <p>职责：聚合 {@link GameClock}、{@link Farm} 与天气系统（{@link WeatherState} +
 * {@link WeatherService}），对外暴露统一时间读取、天气读取与存档恢复入口。
 * 本类不直接处理生长、事件等业务逻辑（由 Controller 协调 A 模块 GrowthService，验收规范 §3.1）；
 * 天气只做<b>聚合与只读暴露</b>，天气生成由 {@link WeatherService#rollDailyWeather(int)} 负责。
 *
 * <p><b>团队裁决落实（原"矛盾报告"）</b>：
 * <ul>
 *   <li>裁决 ①：土地字段统一为 A 模块的 {@link Farm} 接口（原 D 文档写作 {@code LandGrid}，
 *       该类型在全项目不存在，已按 A 模块 §7.1 的 {@code Farm} 接口统一）。</li>
 *   <li>裁决 ②：本类<b>不</b>持有 {@code Player}（{@code Player} 归 B 模块，D 不越层持有），
 *       玩家数据由 E 模块装配层（GameManager）注入到需要的组件。</li>
 * </ul>
 */
public class FarmGameModel {

    /** 游戏时钟（聚合，1 对 1）。 */
    private final GameClock gameClock;

    /** 农田地图（聚合，1 对 1）；由 E 模块装配层注入，可为 null（P0 早期未装配时）。 */
    private Farm farm;

    /** 天气状态（聚合，1 对 1）；P1 新增，默认 {@code SUNNY} / 第 1 天（验收规范 §七十六）。 */
    private final WeatherState weatherState;

    /** 天气服务（聚合，1 对 1）；P1 新增，供 A/C 模块只读调用（D 模块 P1 文档 §5.1/§5.2）。 */
    private final WeatherService weatherService;

    /**
     * 默认构造：初始化 {@code gameClock = new BasicGameClock()}（第 1 天 06:00，验收规范 §5），
     * 并初始化天气系统（{@code BasicWeatherState} + {@code BasicWeatherService}）。
     */
    public FarmGameModel() {
        this.gameClock = new BasicGameClock();
        this.weatherState = new BasicWeatherState();
        this.weatherService = new BasicWeatherService(this.weatherState);
    }

    /**
     * 注入时钟构造（测试可注入 TestGameClock，规则 §八）。
     *
     * <p>天气系统仍使用默认实现（{@code BasicWeatherState} + {@code BasicWeatherService}）。
     *
     * @param gameClock 游戏时钟
     */
    public FarmGameModel(GameClock gameClock) {
        this.gameClock = gameClock;
        this.weatherState = new BasicWeatherState();
        this.weatherService = new BasicWeatherService(this.weatherState);
    }

    /**
     * 推进时间：只调用 {@code gameClock.tick()}，不含天气/事件/日结逻辑（验收规范 §10）。
     */
    public void tick() {
        gameClock.tick();
    }

    /**
     * 获取时钟引用（供 A 模块读取时间，跨模块接口约定 §11.1）。
     *
     * @return 游戏时钟
     */
    public GameClock getGameClock() {
        return gameClock;
    }

    /**
     * 获取农田地图（裁决 ①：类型为 A 模块的 {@link Farm} 接口）。
     *
     * @return 农田地图，未装配时返回 null
     */
    public Farm getFarm() {
        return farm;
    }

    /**
     * 设置农田地图（由 E 模块装配层注入）。
     *
     * @param farm 农田地图
     */
    public void setFarm(Farm farm) {
        this.farm = farm;
    }

    /**
     * 获取天气服务（供 A 模块读取成长倍率、C 模块读取品质分，D 模块 P1 文档 §5.1/§5.2）。
     *
     * <p>只读暴露：调用方只应使用查询方法（{@code getGrowthRate} / {@code isRain} 等），
     * 天气生成由上层协调器在跨日时调用 {@link WeatherService#rollDailyWeather(int)}。
     *
     * @return 天气服务
     */
    public WeatherService getWeatherService() {
        return weatherService;
    }

    /**
     * 获取天气状态（供 E 模块存档读取 {@code current_weather}，D 模块 P1 文档 §5.3）。
     *
     * @return 天气状态
     */
    public WeatherState getWeatherState() {
        return weatherState;
    }

    /**
     * 获取当前总分钟数（存档用，验收规范 §41）。
     *
     * @return 总分钟数
     */
    public int getWorldTimeTotalMinutes() {
        return gameClock.getTotalMinutes();
    }

    /**
     * 恢复时间（存档用，验收规范 §41）。
     *
     * @param totalMinutes 总分钟数
     */
    public void restoreWorldTime(int totalMinutes) {
        gameClock.setTotalMinutes(totalMinutes);
    }
}
