package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.HarvestLog;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.model.impl.BasicSoil;
import com.fieldstory.farm.service.impl.BasicHarvestTransactionService;
import com.fieldstory.farm.service.impl.BasicLandService;
import com.fieldstory.farm.service.impl.BasicLegendaryFirstRewardService;
import com.fieldstory.farm.service.impl.BasicLogService;
import com.fieldstory.farm.service.impl.BasicMemoryService;
import com.fieldstory.farm.service.impl.BasicQualityService;
import com.fieldstory.farm.testutil.TestEconomyService;
import com.fieldstory.farm.util.RandomProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 收获事务 P2 新功能测试：首次传说奖励（规则文档 §六十七）与
 * HarvestLog 落档（规则文档 §六十八 ⑮；验收规范 §一百零三）。
 *
 * <p>传说判定注入固定桩隔离随机；验证 9 参完整构造器的
 * 18 步全链路，以及旧构造器（不装配奖励/日志）的向后兼容行为。
 */
class HarvestTransactionP2FeaturesTest {

    /** 第 5 天 12:00（总分钟 4×1440+720 = 6480） */
    private static final int WORLD_TOTAL_MINUTES = 6480;

    private TestEconomyService economyService;
    private MemoryService memoryService;
    private GameClock gameClock;
    private QualityService qualityService;
    private LegendaryFirstRewardService rewardService;
    private LogService logService;

    @BeforeEach
    void setUp() {
        economyService = new TestEconomyService();
        economyService.setGold(100);
        memoryService = new BasicMemoryService();
        gameClock = new BasicGameClock(WORLD_TOTAL_MINUTES);
        qualityService = new BasicQualityService();
        rewardService = new BasicLegendaryFirstRewardService();
        logService = new BasicLogService();
        RandomProvider.setSeed(42L);
    }

    /** 固定传说桩：rollBreakthrough 恒为指定值。 */
    private LegendaryService legendaryStub(boolean roll) {
        return new LegendaryService() {
            @Override
            public LegendaryCheck checkEligibility(Crop crop, CropMemory memory, int qualityScore) {
                return new LegendaryCheck(true,
                        LegendaryService.legendaryName(crop.getCropType()), List.of());
            }

            @Override
            public int baseChance(CropType cropType) {
                return 30;
            }

            @Override
            public int legendaryChance(Crop crop, CropMemory memory, int qualityScore) {
                return 80;
            }

            @Override
            public boolean rollBreakthrough(Crop crop, CropMemory memory, int qualityScore) {
                return roll;
            }

            @Override
            public void setLegendarySetBonus(int bonusPercent) {
                // 桩：无套装
            }
        };
    }

    /** 9 参完整构造（P2 全链路：首次奖励 + 日志）。 */
    private HarvestTransactionService fullService(LegendaryService legendary) {
        return new BasicHarvestTransactionService(economyService,
                new BasicLandService(economyService), qualityService,
                legendary, memoryService, gameClock, null,
                rewardService, logService);
    }

    /** 旧 7 参构造（不装配首次奖励与日志，向后兼容）。 */
    private HarvestTransactionService legacyService(LegendaryService legendary) {
        return new BasicHarvestTransactionService(economyService,
                new BasicLandService(economyService), qualityService,
                legendary, memoryService, gameClock, null);
    }

    /** 成熟作物土地。 */
    private Soil plantedMatureSoil(CropType cropType) {
        Soil soil = new BasicSoil(2, 2);
        soil.setState(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(cropType);
        crop.setGrowthStage(GrowthStage.MATURE);
        crop.setPlantWorldTime(80);
        soil.setCrop(crop);
        return soil;
    }

    /**
     * 首次传说收获：售价 250（§六十五）+ 首次奖励 500（§六十七），
     * 金币 = 100 + 250 + 500；HarvestLog 写入 1 条且字段完整。
     */
    @Test
    void firstLegendaryHarvestGrantsFirstRewardAndWritesLog() {
        Soil soil = plantedMatureSoil(CropType.WHEAT);
        UUID cropUuid = soil.getCrop().getCropUuid();

        HarvestOutcome outcome = fullService(legendaryStub(true)).harvest(soil, null);

        assertTrue(outcome.isSuccess());
        assertTrue(outcome.isLegendary());
        assertEquals(250, outcome.getSellPrice());
        assertEquals(500, outcome.getFirstRewardGold());
        assertEquals(100 + 250 + 500, economyService.getGold());
        assertTrue(rewardService.hasClaimed(CropType.WHEAT));

        List<HarvestLog> logs = logService.listAll();
        assertEquals(1, logs.size());
        HarvestLog log = logs.get(0);
        assertEquals(cropUuid, log.getCropUuid());
        assertEquals(CropType.WHEAT, log.getCropType());
        assertEquals(Quality.LEGENDARY, log.getQuality());
        assertTrue(log.isLegendary());
        assertEquals(250, log.getSellPrice());
        assertEquals(5, log.getFertilizerReward());
        assertEquals(500, log.getFirstRewardGold());
        assertEquals(outcome.getFinalStory(), log.getFinalStory());
        assertTrue(log.getHarvestWorldTime() > 0);
    }

    /** 同一传说第二次收获：不再发 500，但仍写日志（2 条）。 */
    @Test
    void secondLegendaryHarvestSameTypeSkipsFirstReward() {
        HarvestTransactionService service = fullService(legendaryStub(true));

        HarvestOutcome first = service.harvest(plantedMatureSoil(CropType.WHEAT), null);
        HarvestOutcome second = service.harvest(plantedMatureSoil(CropType.WHEAT), null);

        assertEquals(500, first.getFirstRewardGold());
        assertEquals(0, second.getFirstRewardGold());
        assertEquals(100 + 250 + 500 + 250, economyService.getGold());
        assertEquals(2, logService.listAll().size());
        assertEquals(0, logService.listAll().get(1).getFirstRewardGold());
    }

    /** 三种传说各领一次：3 株分别发放 500（售价小麦 250/玉米 350/胡萝卜 300）。 */
    @Test
    void threeLegendaryTypesEachGrantFirstReward() {
        HarvestTransactionService service = fullService(legendaryStub(true));

        service.harvest(plantedMatureSoil(CropType.WHEAT), null);
        service.harvest(plantedMatureSoil(CropType.CORN), null);
        service.harvest(plantedMatureSoil(CropType.CARROT), null);

        assertEquals(100 + 250 + 350 + 300 + 3 * 500, economyService.getGold());
        assertEquals(3, logService.listAll().size());
        assertTrue(rewardService.hasClaimed(CropType.WHEAT));
        assertTrue(rewardService.hasClaimed(CropType.CORN));
        assertTrue(rewardService.hasClaimed(CropType.CARROT));
    }

    /** 非传说收获：无首次奖励，但 HarvestLog 照常写入。 */
    @Test
    void nonLegendaryHarvestWritesLogWithoutFirstReward() {
        Soil soil = plantedMatureSoil(CropType.WHEAT);

        HarvestOutcome outcome = fullService(legendaryStub(false)).harvest(soil, null);

        assertTrue(outcome.isSuccess());
        assertFalse(outcome.isLegendary());
        assertEquals(0, outcome.getFirstRewardGold());
        assertEquals(100 + 50, economyService.getGold());

        List<HarvestLog> logs = logService.listAll();
        assertEquals(1, logs.size());
        assertEquals(Quality.COMMON, logs.get(0).getQuality());
        assertEquals(0, logs.get(0).getFirstRewardGold());
    }

    /** 旧构造器向后兼容：传说收获不发放首次奖励、不写日志。 */
    @Test
    void legacyConstructorSkipsFirstRewardAndLog() {
        Soil soil = plantedMatureSoil(CropType.WHEAT);

        HarvestOutcome outcome = legacyService(legendaryStub(true)).harvest(soil, null);

        assertTrue(outcome.isSuccess());
        assertTrue(outcome.isLegendary());
        assertEquals(0, outcome.getFirstRewardGold());
        assertEquals(100 + 250, economyService.getGold());
        assertTrue(logService.listAll().isEmpty());
    }
}
