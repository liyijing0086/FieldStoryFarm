package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.WeatherState;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicEventState;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.model.impl.BasicWeatherState;
import com.fieldstory.farm.service.DailySimulationResult;
import com.fieldstory.farm.service.DaySettlementInput;
import com.fieldstory.farm.service.EventService;
import com.fieldstory.farm.service.GrowthRates;
import com.fieldstory.farm.service.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fieldstory.farm.util.GameConstants.WEATHER_RATE_DROUGHT;
import static com.fieldstory.farm.util.GameConstants.WEATHER_RATE_GREEN_RAIN;
import static com.fieldstory.farm.util.GameConstants.WEATHER_RATE_RAIN;
import static com.fieldstory.farm.util.GameConstants.WEATHER_RATE_SUNNY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 离线循环端到端集成测试（A 模块 P2；模拟 B 模块 OfflineSimulationService 驱动，
 * 验收规范 §八十八/§八十九；《模块规范/A 模块 P2 接口与决策记录.md》§4.1 伪代码）。
 *
 * <p>装配真实 A 服务：{@link BasicWorldTimeService} + {@link BasicWorldSimulationService}
 * （注入 {@link BasicGrowthService}(WateringService)、{@link BasicWitherService}）；
 * D 的 WeatherService / EventService 用局部假类替换——真实实现随机不可控
 * （决策 D18/D19 精神）：rollDailyWeather 按预设序列返回、rollDailyEvent 恒 NONE。
 *
 * <p>场景：2 株小麦（(2,2) SEED 进度 0 / (2,3) SPROUT 进度 50），起点第 3 天 10 点
 * （世界小时 82，决策 D14 口径），离线 576 现实分钟经 D28 封顶为 72 分钟
 * → 期末世界小时 154（第 6 天 10 点）；天气预设：第 3 天 SUNNY、第 4 天 RAIN、
 * 第 5 天 DROUGHT、第 6 天 SUNNY（第 3 天为初始当前天气，第 4/5/6 天由日末 ⑪ 掷出）。
 *
 * <p>禁止项（决策 D18/D19）：不使用 System.currentTimeMillis、不使用
 * new Random / RandomProvider；天气/事件走假类预设序列，枯萎掷骰直接传值。
 */
class WorldSimulationIntegrationTest {

    /** 起点：第 3 天 10 点（决策 D14：3×24+10 = 82）。 */
    private static final long START_WORLD_HOUR = 82L;

    /** D28 封顶后的期末：82 + 72 = 154（第 6 天 10 点）。 */
    private static final long END_WORLD_HOUR = 154L;

    /** 日末结算切点：第 3 天日末（= 第 4 天 00:00）。 */
    private static final long DAY3_END = 96L;

    /** 日末结算切点：第 4 天日末（= 第 5 天 00:00）。 */
    private static final long DAY4_END = 120L;

    /** 日末结算切点：第 5 天日末（= 第 6 天 00:00）。 */
    private static final long DAY5_END = 144L;

    /** 原始离线时长 72×8 = 576 现实分钟（走 D28 封顶路径：min(raw, 72)）。 */
    private static final long RAW_OFFLINE_REAL_MINUTES = 576L;

    /** 小麦每日基础成长进度：100 ÷ 2 = 50（CropType.WHEAT，规则文档 §14.1 成长最快）。 */
    private static final double WHEAT_DAILY_PROGRESS = CropType.WHEAT.getBaseDailyProgress();

    /**
     * 枯萎掷骰（决策 D19 直接传值）：按农场遍历顺序消费，全 0.0 为最易触发值——
     * 任何无风险日误判枯萎立即暴露；取尽视为 1.0（必不枯萎）。
     */
    private static final List<Double> WORST_CASE_ROLLS = List.of(0.0, 0.0);

    private BasicWorldTimeService worldTimeService;

    /** 天气假类（预设序列受控，替代 D 真实现）。 */
    private StubWeatherService weather;

    /** 事件假类（恒 NONE，替代 D 真实现）。 */
    private StubEventService eventService;

    private BasicWorldSimulationService simulation;

    @BeforeEach
    void setUp() {
        worldTimeService = new BasicWorldTimeService();
        // 天气预设：初始当前天气 = 第 3 天 SUNNY；⑪ 依次掷出第 4 天 RAIN / 第 5 天 DROUGHT / 第 6 天 SUNNY
        weather = new StubWeatherService(WeatherType.SUNNY, 3);
        weather.preset(4, WeatherType.RAIN);
        weather.preset(5, WeatherType.DROUGHT);
        weather.preset(6, WeatherType.SUNNY);
        eventService = new StubEventService();
        // 装配真实 A 服务（BasicGrowthService 注入 WateringService 的构造先例）
        simulation = new BasicWorldSimulationService(
                new BasicGrowthService(new BasicWateringService()),
                new BasicWitherService(), weather, eventService);
    }

    // ===== 1. 切点含日边界（验收 §八十八；决策 D25）=====

    /**
     * 封顶 72 现实分钟后区间 82→154：切点 = 起止点 + 日边界 96/120/144
     * （= 第 4/5/6 天 00:00），升序去重（决策 D25）。
     */
    @Test
    void offline3DaysCutPointsIncludeDayBoundaries() {
        List<Long> cutPoints = worldTimeService.segmentCutPoints(
                START_WORLD_HOUR, END_WORLD_HOUR, null, List.of());

        assertEquals(List.of(82L, 96L, 120L, 144L, 154L), cutPoints,
                "切点含日边界 96/120/144（第 3/4/5 天日末 = 第 4/5/6 天 00:00）");
    }

    // ===== 2. 首段非整日成长（验收 §二十五/§八十八）=====

    /**
     * 段 1（82→96 = 14 游戏小时，第 3 天 SUNNY ×1.0）：两株按
     * 段时长 ÷ 24 折算天数成长（验收 §二十五：禁止 offlineHours ÷ 24 粗暴处理）。
     */
    @Test
    void offlineFirstSegmentGrowsByElapsedHoursFraction() {
        Farm farm = new BasicFarm();
        Crop seedCrop = plantWheat(farm, 2, 2, GrowthStage.SEED, 0.0);
        Crop sproutCrop = plantWheat(farm, 2, 3, GrowthStage.SPROUT, 50.0);

        simulation.growSegment(farm, DAY3_END - START_WORLD_HOUR, currentRates());

        double expectedGain = WHEAT_DAILY_PROGRESS * 14.0 / 24.0;
        assertEquals(expectedGain, seedCrop.getGrowthProgress(), 1e-9,
                "SEED 株增益 = 50 × 14/24（段时长折算，验收 §二十五）");
        assertEquals(50.0 + expectedGain, sproutCrop.getGrowthProgress(), 1e-9,
                "SPROUT 株同段同增益");
        assertEquals(GrowthStage.SPROUT, seedCrop.getGrowthStage(),
                "29.17 ≥ 20 → SPROUT（验收 §二十二）");
        assertEquals(GrowthStage.GROWING, sproutCrop.getGrowthStage(),
                "79.17 ≥ 50 → GROWING（验收 §二十二）");
    }

    // ===== 3. 雨日计数与 D30（断言 2）=====

    /**
     * 照 §4.1 伪代码内联驱动：第 3 天日末 ⑪ 掷出第 4 天 RAIN 后，⑫ 只写
     * 补水时刻不计数（决策 D30）；第 4 天（雨日）结算 ⑤⑥ recordDailyWeather
     * 每雨日只计一次（验收 §五十 累计雨日数口径），两株 rainCount 各 +1；
     * 第 5/6 天无雨不再累计。
     */
    @Test
    void offline3DaysRainCountsOncePerRainyDay() {
        Farm farm = new BasicFarm();
        Crop seedCrop = plantWheat(farm, 2, 2, GrowthStage.SEED, 0.0);
        Crop sproutCrop = plantWheat(farm, 2, 3, GrowthStage.SPROUT, 50.0);
        List<Long> cutPoints = worldTimeService.segmentCutPoints(
                START_WORLD_HOUR, END_WORLD_HOUR, null, List.of());

        // 段 1（82→96，第 3 天 SUNNY）
        simulation.growSegment(farm, cutPoints.get(1) - cutPoints.get(0), currentRates());
        // 第 3 天日末结算：⑪ 掷出第 4 天 RAIN → ⑫ 对两株只写补水时刻（D30 不计数）
        simulation.settleDay(farm, settlementInput(3L, DAY3_END, WORST_CASE_ROLLS));

        assertEquals(0, seedCrop.getRainCount(), "D30：⑫ 雨天自动补水不计数");
        assertEquals(0, sproutCrop.getRainCount(), "D30：两株一致");
        assertEquals(DAY3_END, seedCrop.getLastHydratedWorldTime(),
                "D30：⑫ 只写补水时刻（96 = 第 4 天 00:00）");
        assertEquals(DAY3_END, sproutCrop.getLastHydratedWorldTime());

        // 段 2（96→120，第 4 天 RAIN ×1.5）
        simulation.growSegment(farm, cutPoints.get(2) - cutPoints.get(1), currentRates());
        // 第 4 天日末结算：⑤⑥ 每雨日只计一次
        simulation.settleDay(farm, settlementInput(4L, DAY4_END, WORST_CASE_ROLLS));

        assertEquals(1, seedCrop.getRainCount(), "雨日每株只计一次（D30 与验收 §五十口径）");
        assertEquals(1, sproutCrop.getRainCount(), "两株 rainCount 各 +1");
        assertEquals(DAY4_END, seedCrop.getLastHydratedWorldTime(), "⑤⑥ 更新补水时刻为 120");
        assertEquals(0, seedCrop.getDroughtStreak(), "雨天补水重置 streak（规则 §二十一）");

        // 段 3/4（120→144→154，第 5/6 天）：无雨日不再累计
        simulation.growSegment(farm, cutPoints.get(3) - cutPoints.get(2), currentRates());
        simulation.settleDay(farm, settlementInput(5L, DAY5_END, WORST_CASE_ROLLS));
        simulation.growSegment(farm, cutPoints.get(4) - cutPoints.get(3), currentRates());

        assertEquals(1, seedCrop.getRainCount(), "第 5/6 天无雨：rainCount 维持 1");
        assertEquals(1, sproutCrop.getRainCount());
    }

    // ===== 4. 旱日 streak 与 roll=0.0 不枯萎（断言 3）=====

    /**
     * 循环终态（验收 §八十九 ⑤⑥⑦；规则 §二十八~§三十）：第 5 天（DROUGHT）
     * 结算后两株均无有效补水 → droughtStreak 各为 1（≥1，未达风险区间 2/3）；
     * 全程传最易触发值 roll=0.0 也不枯萎；离线期间无主动浇水（验收 §八十六）。
     * <p>SEED 豁免路径（规则 §16.1）见
     * {@link #droughtSettlementRollTriggersWitherButSeedExempt()} 专项用例。
     */
    @Test
    void offline3DaysDroughtStreakOneAndRollZeroSurvives() {
        Farm farm = new BasicFarm();
        Crop seedCrop = plantWheat(farm, 2, 2, GrowthStage.SEED, 0.0);
        Crop sproutCrop = plantWheat(farm, 2, 3, GrowthStage.SPROUT, 50.0);

        runOfflineLoop(farm, START_WORLD_HOUR, RAW_OFFLINE_REAL_MINUTES, WORST_CASE_ROLLS);

        assertEquals(1, seedCrop.getDroughtStreak(), "第 5 天旱日无有效补水：streak 0→1");
        assertEquals(1, sproutCrop.getDroughtStreak(), "SPROUT 株无有效补水 streak≥1（实际 1）");
        assertEquals(1, seedCrop.getDroughtCount(), "每旱日 droughtCount +1（验收 §五十）");
        assertEquals(1, sproutCrop.getDroughtCount());
        assertEquals(GrowthStage.MATURE, sproutCrop.getGrowthStage(),
                "roll=0.0 最易触发值仍不枯萎（streak=1 未达风险区间）");
        assertEquals(GrowthStage.MATURE, seedCrop.getGrowthStage());
        assertEquals(0, sproutCrop.getManualWaterCount(), "离线期间无主动浇水（验收 §八十六）");
        assertEquals(-1L, sproutCrop.getLastManualWaterGameDay());
    }

    // ===== 5. 成熟推进与留田（断言 4）=====

    /**
     * 72 分钟封顶全程（3 游戏日）推进：SEED 株与 SPROUT 株最终均 MATURE、
     * 进度封顶 100（段 2 雨天 ×1.5 是跨 100 的关键段，验收 §三十/§四十九）；
     * 成熟不自动出售、不收获，两株仍留在 PLANTED 地块（验收 §八十七）。
     */
    @Test
    void offline3DaysGrowthReachesMatureAndStaysOnPlot() {
        Farm farm = new BasicFarm();
        Crop seedCrop = plantWheat(farm, 2, 2, GrowthStage.SEED, 0.0);
        Crop sproutCrop = plantWheat(farm, 2, 3, GrowthStage.SPROUT, 50.0);

        runOfflineLoop(farm, START_WORLD_HOUR, RAW_OFFLINE_REAL_MINUTES, WORST_CASE_ROLLS);

        assertEquals(100.0, seedCrop.getGrowthProgress(), 1e-9, "SEED 株封顶 100（验收 §三十）");
        assertEquals(GrowthStage.MATURE, seedCrop.getGrowthStage());
        assertEquals(100.0, sproutCrop.getGrowthProgress(), 1e-9);
        assertEquals(GrowthStage.MATURE, sproutCrop.getGrowthStage(),
                "SPROUT 株最终 MATURE（roll 全程未触发枯萎）");

        assertEquals(SoilState.PLANTED, farm.getSoil(2, 2).getState());
        assertEquals(seedCrop, farm.getSoil(2, 2).getCrop(),
                "成熟株仍留在地块（验收 §八十七：不自动出售）");
        assertEquals(SoilState.PLANTED, farm.getSoil(2, 3).getState());
        assertEquals(sproutCrop, farm.getSoil(2, 3).getCrop());
    }

    // ===== 6. 摘要字段（断言 5；验收 §八十九 ⑨ DailyLog 数据体）=====

    /**
     * 3 个日末切点（96/120/144）各产 1 份摘要，gameDay / weather / event /
     * maturedCount / witheredCount / rainHydratedCount 全对（决策 D24/D29）。
     */
    @Test
    void offline3DaysSettlementSummariesMatch() {
        Farm farm = new BasicFarm();
        plantWheat(farm, 2, 2, GrowthStage.SEED, 0.0);
        plantWheat(farm, 2, 3, GrowthStage.SPROUT, 50.0);

        List<DailySimulationResult> results = runOfflineLoop(
                farm, START_WORLD_HOUR, RAW_OFFLINE_REAL_MINUTES, WORST_CASE_ROLLS);

        assertEquals(3, results.size(), "72 游戏小时含 3 个日末切点（96/120/144）");

        DailySimulationResult day3 = results.get(0);
        assertEquals(3L, day3.gameDay());
        assertEquals(WeatherType.SUNNY, day3.weather());
        assertEquals(EventType.NONE, day3.event(), "D29：event = 结算前读的当日生效事件");
        assertEquals(0, day3.maturedCount(), "第 3 天结算时两株均未成熟");
        assertEquals(0, day3.witheredCount());
        assertEquals(0, day3.rainHydratedCount(), "SUNNY 不补水");

        DailySimulationResult day4 = results.get(1);
        assertEquals(4L, day4.gameDay());
        assertEquals(WeatherType.RAIN, day4.weather());
        assertEquals(EventType.NONE, day4.event());
        assertEquals(2, day4.maturedCount(), "段 2 雨天加速后两株均跨 100");
        assertEquals(0, day4.witheredCount());
        assertEquals(2, day4.rainHydratedCount(), "雨日全部 PLANTED 作物补水（规则 §二十一）");

        DailySimulationResult day5 = results.get(2);
        assertEquals(5L, day5.gameDay());
        assertEquals(WeatherType.DROUGHT, day5.weather());
        assertEquals(EventType.NONE, day5.event());
        assertEquals(2, day5.maturedCount());
        assertEquals(0, day5.witheredCount(), "streak=1 未达风险区间 + roll=0.0 不触发");
        assertEquals(0, day5.rainHydratedCount());
    }

    // ===== 7. 封顶生效（断言 6；决策 D28）=====

    /**
     * 72×8 = 576 现实分钟封顶为 72（验收 §八十三：超出部分不模拟、不补偿、
     * 不结转）；封顶后期末世界小时 = 82 + 72 = 154（第 6 天 10 点）。
     */
    @Test
    void offlineCapClamps576RealMinutesTo72() {
        assertEquals(72L, worldTimeService.capOfflineRealMinutes(72L * 8L),
                "D28：min(raw, 72)，超出部分直接丢弃");
        assertEquals(END_WORLD_HOUR,
                START_WORLD_HOUR + worldTimeService.capOfflineRealMinutes(RAW_OFFLINE_REAL_MINUTES),
                "封顶后期末 = 第 6 天 10 点（世界小时 154）");
    }

    // ===== 8. 专项：roll 触发枯萎与 SEED 豁免（断言 3 后半）=====

    /**
     * 第 5 天（DROUGHT）日末结算（规则 §16.1/§二十八；验收 §五十三）：
     * 遍历序 0 号株预设 streak=2（模拟此前已连续两旱日），⑤⑥ 后 streak=3
     * 进入耐性档风险区间（30%），传 rolls[0]=0.0 触发枯萎；1 号株为 SEED，
     * 同一结算传 rolls[1]=0.0 仍 SEED 豁免不参与枯萎判定（但 ⑤⑥ 照常记账）。
     */
    @Test
    void droughtSettlementRollTriggersWitherButSeedExempt() {
        Farm farm = new BasicFarm();
        Crop riskySprout = plantWheat(farm, 2, 2, GrowthStage.SPROUT, 50.0);
        riskySprout.setDroughtStreak(2);
        Crop seedCrop = plantWheat(farm, 2, 3, GrowthStage.SEED, 0.0);

        // 模拟第 4 天日末 ⑪ 掷出第 5 天天气 DROUGHT（§八十九：settleDay 内部
        // 只掷次日天气，当日天气由调用方在上一个日末驱动，见 §4.1 驱动循环）
        weather.rollDailyWeather(5);

        DailySimulationResult result = simulation.settleDay(farm, settlementInput(
                5L, DAY5_END, WORST_CASE_ROLLS));

        assertEquals(5L, result.gameDay());
        assertEquals(WeatherType.DROUGHT, result.weather());
        assertEquals(1, result.witheredCount(),
                "0 号掷骰 roll=0.0 < 0.30（耐性档 streak=3）触发枯萎");
        assertEquals(GrowthStage.WITHERED, riskySprout.getGrowthStage(),
                "⑥ 无有效补水：streak 2→3 后触发");
        assertEquals(3, riskySprout.getDroughtStreak(), "⑤⑥ 记账：2→3");
        assertEquals(GrowthStage.SEED, seedCrop.getGrowthStage(),
                "SEED 豁免：roll=0.0 也不参与枯萎判定（规则 §16.1）");
        assertEquals(1, seedCrop.getDroughtStreak(), "⑤⑥ 对 SEED 株仍记账：0→1");
    }

    // ===== 私有辅助（模拟 B 的 §4.1 驱动循环）=====

    /**
     * 模拟 B 模块 OfflineSimulationService 的驱动循环（文档 §4.1 伪代码）：
     * capOfflineRealMinutes 封顶 → segmentCutPoints 切段 → 逐段 growSegment
     * （rates 按段开始时当日天气组装，decorationRate/eventRate = 1.0）→
     * 日末切点组装 DaySettlementInput（D29：settleDay 前读 EventState）→ settleDay
     * （D24：只收摘要，不落库）。
     *
     * @param farm              农场
     * @param startWorldHour    起点世界小时（D14 口径）
     * @param rawOfflineMinutes 原始离线现实分钟（内部经 D28 封顶）
     * @param witherRolls       每日结算的枯萎掷骰（按 Farm.getSoils() 遍历顺序消费）
     * @return 逐日结算摘要（按日序，A 不推进时钟，由调用方 gameDay+1 驱动）
     */
    private List<DailySimulationResult> runOfflineLoop(Farm farm, long startWorldHour,
            long rawOfflineMinutes, List<Double> witherRolls) {
        long endWorldHour = startWorldHour
                + worldTimeService.capOfflineRealMinutes(rawOfflineMinutes);
        List<Long> cutPoints = worldTimeService.segmentCutPoints(
                startWorldHour, endWorldHour, null, List.of());
        List<DailySimulationResult> results = new ArrayList<>();
        long settleGameDay = startWorldHour / 24;
        for (int i = 0; i + 1 < cutPoints.size(); i++) {
            // 逐段成长：段内只成长、不判枯萎、不换天气（验收 §八十八 ⑭）
            simulation.growSegment(farm, cutPoints.get(i + 1) - cutPoints.get(i), currentRates());
            if (cutPoints.get(i + 1) % 24 == 0) {
                // 日末切点：结算当日 → 下一段开始前按 ⑪ 掷出的新天气重组 rates
                results.add(settleAt(farm, settleGameDay, cutPoints.get(i + 1), witherRolls));
                settleGameDay++;
            }
        }
        return results;
    }

    /**
     * 组装 DaySettlementInput 并调用 settleDay（决策 D29：结算前读当日生效
     * 事件；抗性倍率 P1 恒 1.0，规则 §三十一）。
     */
    private DailySimulationResult settleAt(Farm farm, long gameDay, long worldTimeAtSettle,
            List<Double> witherRolls) {
        return simulation.settleDay(farm,
                settlementInput(gameDay, worldTimeAtSettle, witherRolls));
    }

    /**
     * 按当日天气组装三件套（文档 §4.1：weatherRate 读当日天气；
     * decorationRate/eventRate 恒 1.0）。
     */
    private GrowthRates currentRates() {
        return new GrowthRates(weather.getGrowthRate(weather.currentWeather()), 1.0, 1.0);
    }

    /** 组装一次日结的全部入参（调用方侧，照 §4.1 伪代码）。 */
    private DaySettlementInput settlementInput(long gameDay, long worldTimeAtSettle,
            List<Double> witherRolls) {
        return new DaySettlementInput(gameDay, worldTimeAtSettle, weather.currentWeather(),
                eventService.currentEvent(), currentRates(), 1.0, witherRolls);
    }

    /** 在 (row, col) 按给定阶段/进度种一株小麦（从未主动浇水：哨兵 -1，决策 D14）。 */
    private Crop plantWheat(Farm farm, int row, int col, GrowthStage stage, double progress) {
        Soil soil = farm.getSoil(row, col);
        soil.setState(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropType(CropType.WHEAT);
        crop.setGrowthStage(stage);
        crop.setGrowthProgress(progress);
        crop.setManualWaterCount(0);
        crop.setLastManualWaterGameDay(-1);
        soil.setCrop(crop);
        return crop;
    }

    // ===== 测试内假类（决策 D18/D19：随机注入受控，禁止 RandomProvider）=====

    /** 天气假类：rollDailyWeather 按预设游戏日固定返回并写状态，取尽兜底 SUNNY。 */
    private static final class StubWeatherService implements WeatherService {

        private final WeatherState state;
        private final Map<Integer, WeatherType> presets = new HashMap<>();

        StubWeatherService(WeatherType initialWeather, int initialDayIndex) {
            this.state = new BasicWeatherState(initialWeather, initialDayIndex);
        }

        /** 预设指定游戏日 ⑪ 掷出的天气（写入状态供调用方读取）。 */
        void preset(int dayIndex, WeatherType type) {
            presets.put(dayIndex, type);
        }

        /** 当前天气（= 调用方读 D 的 WeatherState，D29 时机由调用方保证）。 */
        WeatherType currentWeather() {
            return state.getWeatherType();
        }

        @Override
        public WeatherType rollDailyWeather(int dayIndex) {
            WeatherType type = presets.getOrDefault(dayIndex, WeatherType.SUNNY);
            state.setWeatherType(type);
            state.setDayIndex(dayIndex);
            return type;
        }

        @Override
        public double getGrowthRate(WeatherType weatherType) {
            // 规则 §十九：晴 1.0 / 雨 1.5 / 旱 0.5 / 绿雨 2.0（常量单一数据源）
            switch (weatherType) {
                case RAIN:
                    return WEATHER_RATE_RAIN;
                case DROUGHT:
                    return WEATHER_RATE_DROUGHT;
                case GREEN_RAIN:
                    return WEATHER_RATE_GREEN_RAIN;
                case SUNNY:
                default:
                    return WEATHER_RATE_SUNNY;
            }
        }

        @Override
        public int getQualityScore(WeatherType weatherType) {
            return 0;
        }

        @Override
        public int getQualityScoreCap(WeatherType weatherType) {
            return 0;
        }

        @Override
        public boolean isRain(WeatherType weatherType) {
            return weatherType == WeatherType.RAIN;
        }

        @Override
        public boolean isDrought(WeatherType weatherType) {
            return weatherType == WeatherType.DROUGHT;
        }

        @Override
        public boolean isGreenRain(WeatherType weatherType) {
            return weatherType == WeatherType.GREEN_RAIN;
        }

        @Override
        public String getDisplayName(WeatherType weatherType) {
            return "";
        }

        @Override
        public String getIcon(WeatherType weatherType) {
            return "";
        }
    }

    /** 事件假类：rollDailyEvent 恒 NONE（预设无事件），事件状态保持 NONE。 */
    private static final class StubEventService implements EventService {

        private final EventState state = new BasicEventState();

        /** 当前生效事件（= 调用方在 settleDay 前读 D 的 EventState，决策 D29）。 */
        EventType currentEvent() {
            return state.getEventType();
        }

        @Override
        public EventType rollDailyEvent(int dayIndex) {
            state.setEventType(EventType.NONE);
            return EventType.NONE;
        }

        @Override
        public boolean isEventActive(long currentWorldTime) {
            return false;
        }

        @Override
        public void expireIfNeeded(long currentWorldTime) {
            // no-op：本用例事件恒 NONE，无过期状态
        }

        @Override
        public String getDisplayName(EventType type) {
            return "";
        }

        @Override
        public String getIcon(EventType type) {
            return "";
        }

        @Override
        public boolean isMeteorShower(EventType type) {
            return type == EventType.METEOR_SHOWER;
        }

        @Override
        public boolean isMysteryMerchant(EventType type) {
            return type == EventType.MYSTERY_MERCHANT;
        }

        @Override
        public boolean isRainbowDay(EventType type) {
            return type == EventType.RAINBOW_DAY;
        }
    }
}
