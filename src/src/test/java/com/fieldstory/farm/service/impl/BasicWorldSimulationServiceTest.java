package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.service.DailySimulationResult;
import com.fieldstory.farm.service.DaySettlementInput;
import com.fieldstory.farm.service.EventService;
import com.fieldstory.farm.service.GrowthRates;
import com.fieldstory.farm.service.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BasicWorldSimulationService} 测试（A 模块 P2；验收规范 §八十九；
 * 决策 D29：result.event = 当日生效事件镜像入参；
 * 决策 D30：§八十九⑫ 雨天自动补水只写 lastHydratedWorldTime 不计数）。
 *
 * <p>随机注入 stub 控制（不用 RandomProvider、不用系统时间，决策 D18/D19）；
 * 作物状态手动构造（不用 CropFactory）；枯萎/成长/浇水用真实现（纯函数）。
 */
class BasicWorldSimulationServiceTest {

    private BasicWorldSimulationService simulation;

    /** 天气 stub：按调用顺序返回预设序列，取尽停最后一值 */
    private StubWeatherService weather;

    @BeforeEach
    void setUp() {
        weather = new StubWeatherService();
        // StubEventService 固定返回 NONE，与 eventInEffect 区分（D29 语义验证）
        simulation = new BasicWorldSimulationService(
                new BasicGrowthService(new BasicWateringService()),
                new BasicWitherService(), weather, new StubEventService(EventType.NONE));
    }

    /** 在全局坐标 (row, col) 种一株玉米（从未主动浇水，lastManualWaterGameDay=-1 哨兵 D14） */
    private Crop plantOn(Farm farm, int row, int col, GrowthStage stage, double progress) {
        Soil soil = farm.getSoil(row, col);
        soil.setState(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(CropType.CORN);
        crop.setGrowthStage(stage);
        crop.setGrowthProgress(progress);
        crop.setManualWaterCount(0);
        crop.setLastManualWaterGameDay(-1);
        soil.setCrop(crop);
        return crop;
    }

    // ===== 1. D29：result.event = 当日生效事件（镜像入参）=====

    /**
     * 决策 D29：摘要 event 镜像 {@link DaySettlementInput#eventInEffect()}，
     * 而非第 ⑬ 步 rollDailyEvent 的返回值（stub 固定 NONE 用于区分）。
     */
    @Test
    void settleDayEventMirrorsInputInEffect() {
        Farm farm = new BasicFarm();
        plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);

        DailySimulationResult result = simulation.settleDay(farm, new DaySettlementInput(
                1, 24, WeatherType.SUNNY, EventType.METEOR_SHOWER,
                GrowthRates.P0, 1.0, List.of()));

        assertEquals(EventType.METEOR_SHOWER, result.event(),
                "决策 D29：摘要记录当日生效事件（镜像入参），非第 ⑬ 步抽取结果");
        assertEquals(1L, result.gameDay());
        assertEquals(WeatherType.SUNNY, result.weather());
    }

    // ===== 2. D30：⑫ 只补水不计数 =====

    /**
     * 决策 D30 核心：连续两日（第 1 天旱、第 2 天雨），
     * 第 2 天结算后 rainCount == 1——第 1 天日末 ⑫ 对"新一天"的雨天
     * 只写补水时间戳不计数，rainCount 只在次日日结 ⑤⑥ 计一次
     * （验收 §五十"累计雨日数"每雨日 +1）。
     */
    @Test
    void consecutiveSettleRainCountsOncePerRainyDay() {
        Farm farm = new BasicFarm();
        Crop crop = plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);

        // 第 1 天：当日 DROUGHT；⑪ 掷出新天气 RAIN（⑫ 执行）
        weather.setSequence(WeatherType.RAIN, WeatherType.SUNNY);
        simulation.settleDay(farm, new DaySettlementInput(
                1, 24, WeatherType.DROUGHT, EventType.NONE, GrowthRates.P0, 1.0, List.of()));
        assertEquals(0, crop.getRainCount(),
                "决策 D30：第 1 天日末 ⑫ 只补水不计数，rainCount 仍 0");
        assertEquals(24L, crop.getLastHydratedWorldTime(),
                "决策 D30：⑫ 让作物在雨日 00:00 拿到补水时间戳");

        // 第 2 天：当日 RAIN，⑤⑥ recordDailyWeather 计一次
        simulation.settleDay(farm, new DaySettlementInput(
                2, 48, WeatherType.RAIN, EventType.NONE, GrowthRates.P0, 1.0, List.of()));
        assertEquals(1, crop.getRainCount(),
                "决策 D30：同一雨日只计一次（验收 §五十 累计雨日数口径）");
        assertEquals(48L, crop.getLastHydratedWorldTime());
        assertEquals(0, crop.getDroughtStreak());
    }

    /**
     * 决策 D30：⑫ 之后作物 lastHydratedWorldTime == 雨日 00:00（worldTimeAtSettle），
     * 且 rainCount / droughtCount / greenRainCount / manualWaterCount 全部不变。
     */
    @Test
    void rainStepWritesHydratedTimeOnly() {
        Farm farm = new BasicFarm();
        Crop crop = plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);
        crop.setDroughtStreak(2);

        // 当日 SUNNY（⑤⑥ 归零 streak）；⑪ 掷出新天气 RAIN → ⑫ 执行
        weather.setSequence(WeatherType.RAIN);
        simulation.settleDay(farm, new DaySettlementInput(
                1, 24, WeatherType.SUNNY, EventType.NONE, GrowthRates.P0, 1.0, List.of()));

        assertEquals(24L, crop.getLastHydratedWorldTime(),
                "决策 D30：⑫ 只写 lastHydratedWorldTime（雨日 00:00）");
        assertEquals(0, crop.getRainCount(), "决策 D30：⑫ 不动 rainCount");
        assertEquals(0, crop.getDroughtCount());
        assertEquals(0, crop.getGreenRainCount());
        assertEquals(0, crop.getDroughtStreak());
        assertEquals(0, crop.getManualWaterCount(), "补水不改主动浇水计数（验收 §五十一）");
    }

    // ===== 3. 结算摘要其余字段 =====

    /**
     * 标记成熟：progress ≥100 计数，不收获、不自动出售（验收 §八十七）；
     * 作物仍在土地上、阶段仍 MATURE。
     */
    @Test
    void settleDayCountsMatureWithoutHarvest() {
        Farm farm = new BasicFarm();
        Crop mature = plantOn(farm, 2, 2, GrowthStage.MATURE, 100.0);
        plantOn(farm, 2, 3, GrowthStage.SPROUT, 30.0);

        DailySimulationResult result = simulation.settleDay(farm, new DaySettlementInput(
                1, 24, WeatherType.SUNNY, EventType.NONE, GrowthRates.P0, 1.0, List.of()));

        assertEquals(1, result.maturedCount());
        assertEquals(0, result.witheredCount());
        assertEquals(GrowthStage.MATURE, mature.getGrowthStage());
        assertEquals(SoilState.PLANTED, farm.getSoil(2, 2).getState());
        assertEquals(mature, farm.getSoil(2, 2).getCrop(), "不收获（验收 §八十七）");
    }

    /**
     * 当日 RAIN：rainHydratedCount == 全部 PLANTED 作物数（规则 §二十一）；
     * 每株 rainCount+1、streak 归零。
     */
    @Test
    void settleDayRainHydratesAllPlantedCounts() {
        Farm farm = new BasicFarm();
        Crop first = plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);
        Crop second = plantOn(farm, 2, 3, GrowthStage.SPROUT, 30.0);
        first.setDroughtStreak(3);
        second.setDroughtStreak(3);

        DailySimulationResult result = simulation.settleDay(farm, new DaySettlementInput(
                1, 24, WeatherType.RAIN, EventType.NONE, GrowthRates.P0, 1.0, List.of()));

        assertEquals(2, result.rainHydratedCount(),
                "当日 RAIN：全部 PLANTED 作物获补水（规则 §二十一）");
        assertEquals(1, first.getRainCount());
        assertEquals(0, first.getDroughtStreak());
        assertEquals(1, second.getRainCount());
    }

    // ===== 4. 枯萎判定：掷骰顺序消费 =====

    /**
     * witherRolls 按 {@link Farm#getSoils()} 遍历顺序消费（(2,2) → (2,3)）：
     * ⑤⑥ DROUGHT 使 streak 1→2（概率 0.30），roll=0.29 触发、roll=0.30 不触发（开区间）。
     */
    @Test
    void witherRollsConsumedInTraversalOrder() {
        Farm farm = new BasicFarm();
        Crop first = plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);
        Crop second = plantOn(farm, 2, 3, GrowthStage.SPROUT, 30.0);
        first.setDroughtStreak(1);
        second.setDroughtStreak(1);

        DailySimulationResult result = simulation.settleDay(farm, new DaySettlementInput(
                1, 24, WeatherType.DROUGHT, EventType.NONE, GrowthRates.P0, 1.0,
                List.of(0.29, 0.30)));

        assertEquals(1, result.witheredCount());
        assertEquals(GrowthStage.WITHERED, first.getGrowthStage(), "roll=0.29 < 0.30 触发");
        assertEquals(GrowthStage.SPROUT, second.getGrowthStage(), "roll=0.30 不触发（开区间）");
    }

    /**
     * 第三轮事实桥：真正进入非零枯萎概率区间的作物 UUID 必须由世界引擎上报，
     * Memory 层不能再复制 streak / 作物耐性 / Buff 概率公式。
     */
    @Test
    void settlementReportsExactlyCropsThatEnteredWitherRisk() {
        Farm farm = new BasicFarm();
        Crop risky = plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);
        Crop safe = plantOn(farm, 2, 3, GrowthStage.SPROUT, 30.0);
        risky.setDroughtStreak(1); // 日结记录 DROUGHT 后变 2，普通玉米进入 30% 风险
        safe.setDroughtStreak(0);  // 日结后仅 1，无风险

        DailySimulationResult result = simulation.settleDay(farm, new DaySettlementInput(
                1, 24, WeatherType.DROUGHT, EventType.NONE,
                10L, 20L, GrowthRates.P0, 1.0, List.of(0.99, 0.99)));

        assertEquals(List.of(risky.getCropUuid()), result.witherRiskCropUuids());
        assertEquals(10L, result.eventStartWorldTime());
        assertEquals(20L, result.eventEndWorldTime());
    }

    /**
     * 掷骰列表取尽视为 1.0：⑤⑥ 后 streak=4（概率 1.0），rolls 为空仍必不枯萎
     * （异常输入不破坏状态）。
     */
    @Test
    void witherRollsExhaustedMeansSurvive() {
        Farm farm = new BasicFarm();
        Crop crop = plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);
        crop.setDroughtStreak(3);

        DailySimulationResult result = simulation.settleDay(farm, new DaySettlementInput(
                1, 24, WeatherType.DROUGHT, EventType.NONE, GrowthRates.P0, 1.0, List.of()));

        assertEquals(0, result.witheredCount(), "取尽视为 1.0，必不枯萎");
        assertEquals(GrowthStage.SPROUT, crop.getGrowthStage());
    }

    /**
     * A/B 集成：同样的基础枯萎概率与 roll，下标 (2,2) 使用 1.0 会枯萎，
     * (2,3) 使用 B 的 witherProbabilityMultiplier=0.7 后概率从 30% 降到 21%，
     * roll=0.25 应存活。A 不识别“石灯笼”，只消费最终倍率。
     */
    @Test
    void settleDayConsumesPerCropWitherProbabilityMultiplier() {
        Farm farm = new BasicFarm();
        Crop plain = plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);
        Crop protectedCrop = plantOn(farm, 2, 3, GrowthStage.SPROUT, 30.0);
        plain.setDroughtStreak(1);
        protectedCrop.setDroughtStreak(1);

        DailySimulationResult result = simulation.settleDay(
                farm,
                new DaySettlementInput(
                        1, 24, WeatherType.DROUGHT, EventType.NONE,
                        GrowthRates.P0, 1.0, List.of(0.25, 0.25)),
                (row, column, cropType) -> column == 3 ? 0.7 : 1.0);

        assertEquals(1, result.witheredCount());
        assertEquals(GrowthStage.WITHERED, plain.getGrowthStage(),
                "30% 基础概率下 0.25 应触发枯萎");
        assertEquals(GrowthStage.SPROUT, protectedCrop.getGrowthStage(),
                "0.30 × 0.7 = 0.21，0.25 不应枯萎");
    }

    // ===== 5. 分段成长（验收 §八十八）=====

    /**
     * 进度 90 经 12 小时成长跨过 100：返回本段新成熟列表、进度封顶 100（验收 §三十）。
     */
    @Test
    void growSegmentReturnsNewlyMatured() {
        Farm farm = new BasicFarm();
        Crop crop = plantOn(farm, 2, 2, GrowthStage.GROWING, 90.0);

        List<Crop> matured = simulation.growSegment(farm, 12.0, GrowthRates.P0);

        assertEquals(1, matured.size(), "本段新成熟（进度跨过 100）");
        assertTrue(matured.contains(crop));
        assertEquals(100.0, crop.getGrowthProgress(), 1e-9, "封顶 100（验收 §三十）");
        assertEquals(GrowthStage.MATURE, crop.getGrowthStage());
    }

    /**
     * 非整日成长（验收 §二十五）：12 游戏小时 = 0.5 天，
     * 玉米日进度 100/3 → 增量 100/6，未成熟返回空列表。
     */
    @Test
    void growSegmentSupportsFractionalDays() {
        Farm farm = new BasicFarm();
        Crop crop = plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);

        List<Crop> matured = simulation.growSegment(farm, 12.0, GrowthRates.P0);

        assertTrue(matured.isEmpty());
        assertEquals(30.0 + 100.0 / 3.0 * 0.5, crop.getGrowthProgress(), 1e-6,
                "经过游戏小时 ÷ 24 折算天数（验收 §二十五）");
    }

    /**
     * A/B P2：四参 growSegment 必须按地块逐 Crop 解析 DecorationRate，
     * 不能把 B 的位置/作物专属 Buff 压成一个全局值。
     */
    @Test
    void growSegmentSupportsPerCropDecorationRateResolver() {
        Farm farm = new BasicFarm();
        Crop normal = plantOn(farm, 2, 2, GrowthStage.SEED, 0.0);
        Crop boosted = plantOn(farm, 2, 3, GrowthStage.SEED, 0.0);

        simulation.growSegment(farm, 24.0, GrowthRates.P0,
                (row, column, cropType) -> row == 2 && column == 3 ? 1.5 : 1.0);

        assertEquals(100.0 / 3.0, normal.getGrowthProgress(), 1e-6);
        assertEquals(50.0, boosted.getGrowthProgress(), 1e-6,
                "逐 Crop resolver 应覆盖 GrowthRates 中的单一 decorationRate");
    }

    /**
     * A/B 集成：resolver 返回的 growthRate 已包含 Decoration + Set Growth Buff；
     * A 只消费最终倍率，不解析装饰或套装。自然之息单独贡献 +8% 时，
     * 24 小时玉米应按 1.08 倍成长。
     */
    @Test
    void growSegmentConsumesDecorationAndSetGrowthRate() {
        Farm farm = new BasicFarm();
        Crop crop = plantOn(farm, 2, 2, GrowthStage.SEED, 0.0);

        simulation.growSegment(
                farm,
                24.0,
                GrowthRates.P0,
                (row, column, cropType) -> 1.08);

        assertEquals(100.0 / 3.0 * 1.08, crop.getGrowthProgress(), 1e-6,
                "A 必须直接消费 B 汇总后的 Decoration/Set growthRate");
    }

    /**
     * WITHERED 跳过不成长（A P1 设计 §6.3）：进度与阶段均不变。
     */
    @Test
    void growSegmentSkipsWithered() {
        Farm farm = new BasicFarm();
        Crop crop = plantOn(farm, 2, 2, GrowthStage.WITHERED, 50.0);

        List<Crop> matured = simulation.growSegment(farm, 24.0, GrowthRates.P0);

        assertTrue(matured.isEmpty());
        assertEquals(50.0, crop.getGrowthProgress(), 1e-9, "枯萎作物不再成长");
        assertEquals(GrowthStage.WITHERED, crop.getGrowthStage());
    }

    // ===== 6. 决策 D31：逐 Crop 装饰倍率解析 =====

    /**
     * 决策 D31：resolver 逐株返回不同 DecorationRate（规则 §五十五：
     * D01 邻格 / D08~D10 作物专属使同段内各株倍率不同）——
     * (2,2) 1.5、(2,3) 1.0，增量按各自倍率计算。
     */
    @Test
    void growSegmentResolvesPerCropDecorationRate() {
        Farm farm = new BasicFarm();
        Crop boosted = plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);
        Crop plain = plantOn(farm, 2, 3, GrowthStage.SPROUT, 30.0);

        List<Crop> matured = simulation.growSegment(farm, 12.0, GrowthRates.P0,
                (row, column, cropType) -> row == 2 && column == 2 ? 1.5 : 1.0);

        assertTrue(matured.isEmpty());
        assertEquals(30.0 + 100.0 / 3.0 * 0.5 * 1.5, boosted.getGrowthProgress(), 1e-6,
                "决策 D31：(2,2) 按解析出的 1.5 成长");
        assertEquals(30.0 + 100.0 / 3.0 * 0.5, plain.getGrowthProgress(), 1e-6,
                "决策 D31：(2,3) 按解析出的 1.0 成长");
    }

    /**
     * 决策 D31：resolver 为 null 回退 3 参行为——全部作物统一用
     * rates.decorationRate()（与 3 参调用语义完全一致）。
     */
    @Test
    void growSegmentNullResolverFallsBackToUniformRate() {
        Farm farm = new BasicFarm();
        Crop first = plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);
        Crop second = plantOn(farm, 2, 3, GrowthStage.SPROUT, 30.0);

        GrowthRates rates = new GrowthRates(1.0, 1.5, 1.0);
        simulation.growSegment(farm, 12.0, rates, null);

        double expected = 30.0 + 100.0 / 3.0 * 0.5 * 1.5;
        assertEquals(expected, first.getGrowthProgress(), 1e-6,
                "决策 D31：null resolver 回退三件套统一 decorationRate=1.5");
        assertEquals(expected, second.getGrowthProgress(), 1e-6);
    }

    /**
     * 决策 D31：resolver 返回非法值（NaN / 负数）经 GrowthRates 钳制为 0，
     * 该株成长暂停、其余株不受影响（异常输入不破坏状态）。
     */
    @Test
    void growSegmentClampsIllegalResolverValues() {
        Farm farm = new BasicFarm();
        Crop nanCrop = plantOn(farm, 2, 2, GrowthStage.SPROUT, 30.0);
        Crop negativeCrop = plantOn(farm, 2, 3, GrowthStage.SPROUT, 30.0);
        Crop normalCrop = plantOn(farm, 2, 4, GrowthStage.SPROUT, 30.0);

        simulation.growSegment(farm, 12.0, GrowthRates.P0,
                (row, column, cropType) -> {
                    if (column == 2) {
                        return Double.NaN;
                    }
                    if (column == 3) {
                        return -0.5;
                    }
                    return 1.0;
                });

        assertEquals(30.0, nanCrop.getGrowthProgress(), 1e-9,
                "NaN 钳制为 0：成长暂停");
        assertEquals(30.0, negativeCrop.getGrowthProgress(), 1e-9,
                "负数钳制为 0：成长暂停");
        assertEquals(30.0 + 100.0 / 3.0 * 0.5, normalCrop.getGrowthProgress(), 1e-6,
                "其余株不受影响");
    }

    // ===== 测试内 stub（随机注入控制，决策 D18/D19）=====

    /** 天气 stub：按调用顺序返回预设序列，取尽停最后一值。 */
    private static final class StubWeatherService implements WeatherService {

        private List<WeatherType> sequence = List.of(WeatherType.SUNNY);
        private int index = 0;

        void setSequence(WeatherType... types) {
            this.sequence = List.of(types);
            this.index = 0;
        }

        @Override
        public WeatherType rollDailyWeather(int dayIndex) {
            return sequence.get(Math.min(index++, sequence.size() - 1));
        }

        @Override
        public double getGrowthRate(WeatherType weatherType) {
            return 1.0;
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

    /** 事件 stub：rollDailyEvent 固定返回构造值（D29 与 eventInEffect 区分用）。 */
    private static final class StubEventService implements EventService {

        private final EventType type;

        StubEventService(EventType type) {
            this.type = type;
        }

        @Override
        public EventType rollDailyEvent(int dayIndex) {
            return type;
        }

        @Override
        public boolean isEventActive(long currentWorldTime) {
            return false;
        }

        @Override
        public void expireIfNeeded(long currentWorldTime) {
            // no-op
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
