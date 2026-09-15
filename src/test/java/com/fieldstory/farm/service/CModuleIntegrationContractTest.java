package com.fieldstory.farm.service;

import com.fieldstory.farm.model.BuffSnapshot;
import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.DecorationSet;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.model.impl.BasicSoil;
import com.fieldstory.farm.model.item.Inventory;
import com.fieldstory.farm.service.impl.BasicCollectionService;
import com.fieldstory.farm.service.impl.BasicHarvestTransactionService;
import com.fieldstory.farm.service.impl.BasicLandService;
import com.fieldstory.farm.service.impl.BasicLegendaryFirstRewardService;
import com.fieldstory.farm.service.impl.BasicLegendaryService;
import com.fieldstory.farm.service.impl.BasicLogService;
import com.fieldstory.farm.service.impl.BasicMemoryService;
import com.fieldstory.farm.testutil.TestEconomyService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C 模块跨 B/E 契约测试：锁定 V4.0 中最容易重复/串线的三个规则。
 */
class CModuleIntegrationContractTest {

    @Test
    void finalPriceKeepsDecorationSetAndEventRatesSeparatedAndQualityConsumesBBuff() {
        TestEconomyService economy = new TestEconomyService();
        economy.setGold(100);
        MemoryService memories = new BasicMemoryService();
        CollectionService collection = new BasicCollectionService(new GameState());
        BasicLogService logs = new BasicLogService();

        Soil soil = matureSoil(CropType.CORN, 2, 3);
        CropMemory memory = memories.createMemory(soil.getCrop());
        memory.getEvents().add(EventType.METEOR_SHOWER); // +20
        memory.getEvents().add(EventType.RAINBOW_DAY);   // +15

        AtomicReference<QualityScoreInput> captured = new AtomicReference<>();
        QualityService quality = new QualityService() {
            @Override
            public int calculateScore(QualityScoreInput input) {
                captured.set(input);
                return 80;
            }

            @Override
            public QualityBreakdown explainScore(QualityScoreInput input) {
                return new QualityBreakdown(0, 0, 0, 0, 0, 0,
                        input.getDecorationScore(), input.getEventScore());
            }

            @Override
            public Quality determineQuality(int score) {
                return Quality.RARE; // ×2
            }
        };

        LegendaryService neverLegendary = new LegendaryService() {
            @Override public LegendaryCheck checkEligibility(Crop c, CropMemory m, int score) {
                return new LegendaryCheck(false, "", List.of("test"));
            }
            @Override public int baseChance(CropType type) { return 0; }
            @Override public int legendaryChance(Crop c, CropMemory m, int score) { return 0; }
            @Override public boolean rollBreakthrough(Crop c, CropMemory m, int score) { return false; }
            @Override public void setLegendarySetBonus(int bonusPercent) { }
        };

        BuffService buffs = (row, column, cropType) ->
                new BuffSnapshot(1.0, 15, 1.25, 1.0, 1.0, 1.0);
        SetService sets = fixedSetService(1.10, 10);

        HarvestTransactionService service = new BasicHarvestTransactionService(
                economy,
                new BasicLandService(economy),
                quality,
                neverLegendary,
                memories,
                new BasicGameClock(6480),
                cropType -> 2.0,
                null,
                logs,
                buffs,
                sets,
                collection
        );

        HarvestOutcome outcome = service.harvest(soil, new Inventory());

        // 70 × 2(RARE) × 1.25(Decoration) × 1.10(Set) × 2(Event) = 385
        assertTrue(outcome.isSuccess());
        assertEquals(385, outcome.getSellPrice());
        assertEquals(485, economy.getGold());

        assertEquals(15, captured.get().getDecorationScore(),
                "品质装饰分必须消费 B BuffService");
        assertEquals(35, captured.get().getEventScore(),
                "流星夜 +20、彩虹日 +15");
        assertEquals(Quality.RARE,
                collection.cropStatus(CropType.CORN, Quality.RARE).isCollected()
                        ? Quality.RARE : null);
        assertEquals(1, logs.listAll().size());
    }

    @Test
    void legendaryChanceConsumesSetServiceDirectly() {
        Crop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(CropType.CORN);
        crop.setGrowthStage(GrowthStage.MATURE);

        CropMemory memory = new CropMemory(crop.getCropUuid(), CropType.CORN, 0);
        memory.setGreenRainCount(1);   // +5
        memory.setFertilizerCount(1); // 满足彩虹玉米条件

        LegendaryService legendary = new BasicLegendaryService(fixedSetService(1.0, 10));

        // 40 base + 5 green rain + 10 legendary set = 55
        assertEquals(55, legendary.legendaryChance(crop, memory, 115));
    }

    @Test
    void firstLegendaryRewardRestoresClaimedStateFromPersistedCollection() {
        GameState state = new GameState();
        CollectionService collection = new BasicCollectionService(state);
        collection.collectLegendary(CropType.WHEAT); // 模拟 SQLite 读档已恢复的图鉴事实

        LegendaryFirstRewardService reward =
                new BasicLegendaryFirstRewardService(collection);

        assertTrue(reward.hasClaimed(CropType.WHEAT));
        assertEquals(0, reward.claimFirstReward(CropType.WHEAT),
                "重启后不得再次领取同一种传说的 +500");
        assertEquals(LegendaryFirstRewardService.FIRST_REWARD_GOLD,
                reward.claimFirstReward(CropType.CORN));
    }

    private static Soil matureSoil(CropType type, int row, int column) {
        Soil soil = new BasicSoil(row, column);
        soil.setState(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(type);
        crop.setGrowthStage(GrowthStage.MATURE);
        crop.setGrowthProgress(100);
        crop.setPlantWorldTime(0);
        soil.setCrop(crop);
        return soil;
    }

    private static SetService fixedSetService(double priceSetRate, int legendaryPercent) {
        return new SetService() {
            @Override public List<DecorationSet> allSets() { return List.of(); }
            @Override public boolean isCollected(DecorationSet set) { return false; }
            @Override public boolean isActive(DecorationSet set) { return false; }
            @Override public int collectedCount() { return 0; }
            @Override public int activeCount() { return 0; }
            @Override public Set<String> getCollectedSetIds() { return Set.of(); }
            @Override public Set<String> getActiveSetIds() { return Set.of(); }
            @Override public List<String> getActiveBuffDescriptions() { return List.of(); }
            @Override public double getPriceSetRate() { return priceSetRate; }
            @Override public int getLegendarySetBonusPercent() { return legendaryPercent; }
            @Override public void refresh() { }
        };
    }
}
