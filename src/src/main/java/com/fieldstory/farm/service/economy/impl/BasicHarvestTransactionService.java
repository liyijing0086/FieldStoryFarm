package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.HarvestLog;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.item.EventPriceRateProvider;
import com.fieldstory.farm.model.item.Inventory;
import com.fieldstory.farm.model.item.Item;
import com.fieldstory.farm.model.item.ItemType;
import com.fieldstory.farm.service.BuffService;
import com.fieldstory.farm.service.CollectionService;
import com.fieldstory.farm.service.HarvestOutcome;
import com.fieldstory.farm.service.HarvestResult;
import com.fieldstory.farm.service.HarvestTransactionService;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.service.LegendaryFirstRewardService;
import com.fieldstory.farm.service.LegendaryService;
import com.fieldstory.farm.service.LogService;
import com.fieldstory.farm.service.MemoryService;
import com.fieldstory.farm.service.QualityScoreInput;
import com.fieldstory.farm.service.QualityService;
import com.fieldstory.farm.service.SetService;
import com.fieldstory.farm.service.economy.EconomyService;

import java.util.Objects;

/**
 * {@link HarvestTransactionService} 基础实现（C 模块 品质与传说域，P2）。
 *
 * <p>严格执行 V4.0 §六十五/§六十八：
 * <pre>
 * FinalPrice = BasePrice × QualityMultiplier × DecorationPriceRate
 *            × SetPriceRate × EventPriceRate
 * </pre>
 * 其中 DecorationPriceRate 只消费 B.BuffService，SetPriceRate 只消费
 * {@link SetService#getPriceSetRate()}，二者绝不合并，避免套装重复计价。
 *
 * <p>品质分中的 DecorationScore 同样只消费 B.BuffService；事件品质分根据已经
 * 写入 CropMemory 的真实事件经历计算。LEGENDARY 永远由 LegendaryService
 * 突破产生，QualityService 的普通档位最高 EPIC。
 */
public class BasicHarvestTransactionService implements HarvestTransactionService {

    private static final int HOURS_PER_DAY = 24;
    private static final int METEOR_QUALITY_BONUS = 20;
    private static final int RAINBOW_DAY_QUALITY_BONUS = 15;

    private final EconomyService economyService;
    private final LandService landService;
    private final QualityService qualityService;
    private final LegendaryService legendaryService;
    private final MemoryService memoryService;
    private final GameClock gameClock;
    private final EventPriceRateProvider eventPriceRateProvider;
    private final LegendaryFirstRewardService firstRewardService;
    private final LogService logService;

    /** B：装饰 Buff。null 时按旧行为使用中性值。 */
    private final BuffService buffService;

    /** B：套装服务。null 时按旧行为 SetPriceRate=1.0。 */
    private final SetService setService;

    /** E：图鉴。完整生产装配时由收获事务内部完成第 11 步。 */
    private final CollectionService collectionService;

    /** P2/旧测试兼容构造。 */
    public BasicHarvestTransactionService(EconomyService economyService,
                                          LandService landService,
                                          QualityService qualityService,
                                          LegendaryService legendaryService,
                                          MemoryService memoryService,
                                          GameClock gameClock) {
        this(economyService, landService, qualityService, legendaryService,
                memoryService, gameClock, EventPriceRateProvider.NONE);
    }

    /** P2/旧测试兼容构造。 */
    public BasicHarvestTransactionService(EconomyService economyService,
                                          LandService landService,
                                          QualityService qualityService,
                                          LegendaryService legendaryService,
                                          MemoryService memoryService,
                                          GameClock gameClock,
                                          EventPriceRateProvider eventPriceRateProvider) {
        this(economyService, landService, qualityService, legendaryService,
                memoryService, gameClock, eventPriceRateProvider, null, null);
    }

    /** 既有 9 参数完整构造，继续兼容原测试。 */
    public BasicHarvestTransactionService(EconomyService economyService,
                                          LandService landService,
                                          QualityService qualityService,
                                          LegendaryService legendaryService,
                                          MemoryService memoryService,
                                          GameClock gameClock,
                                          EventPriceRateProvider eventPriceRateProvider,
                                          LegendaryFirstRewardService firstRewardService,
                                          LogService logService) {
        this(economyService, landService, qualityService, legendaryService,
                memoryService, gameClock, eventPriceRateProvider,
                firstRewardService, logService, null, null, null);
    }

    /**
     * P3 正式生产构造：C 完整收获链消费 B Buff/Set 与 E Collection。
     */
    public BasicHarvestTransactionService(EconomyService economyService,
                                          LandService landService,
                                          QualityService qualityService,
                                          LegendaryService legendaryService,
                                          MemoryService memoryService,
                                          GameClock gameClock,
                                          EventPriceRateProvider eventPriceRateProvider,
                                          LegendaryFirstRewardService firstRewardService,
                                          LogService logService,
                                          BuffService buffService,
                                          SetService setService,
                                          CollectionService collectionService) {
        this.economyService = Objects.requireNonNull(economyService, "经济服务不能为空");
        this.landService = Objects.requireNonNull(landService, "土地服务不能为空");
        this.qualityService = Objects.requireNonNull(qualityService, "品质服务不能为空");
        this.legendaryService = Objects.requireNonNull(legendaryService, "传说服务不能为空");
        this.memoryService = Objects.requireNonNull(memoryService, "记忆服务不能为空");
        this.gameClock = Objects.requireNonNull(gameClock, "世界时钟不能为空");
        this.eventPriceRateProvider = eventPriceRateProvider == null
                ? EventPriceRateProvider.NONE
                : eventPriceRateProvider;
        this.firstRewardService = firstRewardService;
        this.logService = logService;
        this.buffService = buffService;
        this.setService = setService;
        this.collectionService = collectionService;
    }

    @Override
    public boolean canHarvest(Soil soil) {
        if (soil == null || soil.getState() != SoilState.PLANTED) {
            return false;
        }
        Crop crop = soil.getCrop();
        return crop != null && crop.getGrowthStage() == GrowthStage.MATURE;
    }

    @Override
    public HarvestOutcome harvest(Soil soil) {
        return harvest(soil, null);
    }

    @Override
    public HarvestOutcome harvest(Soil soil, Inventory inventory) {
        // ① 检查 MATURE：失败不改变任何状态。
        if (soil == null || soil.getState() != SoilState.PLANTED) {
            return HarvestOutcome.failure(HarvestResult.NOT_PLANTED);
        }
        Crop crop = soil.getCrop();
        if (crop == null) {
            return HarvestOutcome.failure(HarvestResult.NO_CROP);
        }
        if (crop.getGrowthStage() != GrowthStage.MATURE) {
            return HarvestOutcome.failure(HarvestResult.NOT_MATURE);
        }

        // ③ 读取/补建 CropMemory。
        CropMemory memory = memoryService.findMemory(crop.getCropUuid())
                .orElseGet(() -> memoryService.createMemory(crop));

        // ④ QualityScore：经历来自 Memory；品质装饰只消费 B Buff；事件来自真实 Memory 事件。
        int decorationScore = buffService == null
                ? 0
                : buffService.getQualityScoreBonus(
                        soil.getRow(), soil.getColumn(), crop.getCropType());
        int eventScore = eventQualityScore(memory);
        QualityScoreInput scoreInput = new QualityScoreInput(
                crop.getCropType(),
                memory.getManualWaterCount(),
                memory.getRainCount(),
                memory.getDroughtCount(),
                memory.getGreenRainCount(),
                memory.getFertilizerCount(),
                decorationScore,
                eventScore);
        int score = qualityService.calculateScore(scoreInput);

        // ⑤⑥ 传奇条件 + 概率 + 掷骰；正式 BasicLegendaryService 直接读 SetService。
        boolean legendary = legendaryService.rollBreakthrough(crop, memory, score);

        // ⑦ 最终品质。普通品质路径最高 EPIC。
        Quality quality = legendary ? Quality.LEGENDARY : qualityService.determineQuality(score);

        // ⑧ 最终售价：五个因子严格分离。
        int basePrice = economyService.calculateBaseSellPrice(crop.getCropType());
        double decorationPriceRate = buffService == null
                ? 1.0
                : buffService.getPriceRate(
                        soil.getRow(), soil.getColumn(), crop.getCropType());
        double setPriceRate = setService == null ? 1.0 : setService.getPriceSetRate();
        double eventPriceRate = eventPriceRateProvider.priceRateFor(crop.getCropType());
        int sellPrice = (int) Math.round(
                basePrice
                        * quality.getPriceMultiplier()
                        * decorationPriceRate
                        * setPriceRate
                        * eventPriceRate);
        int fertilizerReward = quality.getFertilizerReward();
        long harvestWorldTime = currentWorldTime();

        // ⑨ 金币入账。
        economyService.addGold(sellPrice);

        // ⑩ 品质肥料奖励。
        if (inventory != null && fertilizerReward > 0) {
            inventory.addItem(new Item(ItemType.FERTILIZER, fertilizerReward));
        }

        // ⑪ 图鉴：完整生产装配由事务内部更新，不再依赖 Controller 收获后补写。
        if (collectionService != null) {
            collectionService.collectCrop(crop.getCropType(), quality);
            if (legendary) {
                collectionService.collectLegendary(crop.getCropType());
            }
        }

        // ⑫ 首次某种传奇 +500。生产路径的服务会从持久化传奇图鉴恢复已领取状态。
        int firstRewardGold = 0;
        if (legendary && firstRewardService != null) {
            firstRewardGold = firstRewardService.claimFirstReward(crop.getCropType());
            if (firstRewardGold > 0) {
                economyService.addGold(firstRewardGold);
            }
        }

        // ⑬⑭ 生成故事并落历史 Memory。
        String story = memoryService.completeHarvest(memory, quality, legendary, harvestWorldTime);

        // ⑮ HarvestLog。
        if (logService != null) {
            logService.append(new HarvestLog(crop.getCropUuid(), crop.getCropType(),
                    quality, legendary, sellPrice, fertilizerReward, firstRewardGold,
                    story, harvestWorldTime));
        }

        // ⑯⑰ 清地。
        landService.removeCropAndSetTilled(soil);

        // ⑱ SQLite 的真正 commit/rollback 仍由 E Composition Root/持久化事务承担；
        // C 侧已经保证所有规则入口集中在本方法，不在 Controller 重复业务公式。
        return HarvestOutcome.success(quality, score, sellPrice, fertilizerReward,
                legendary, story, memory, firstRewardGold);
    }

    /**
     * 事件品质分（V4.0 §三十八）：流星夜 +20，彩虹日 +15。
     *
     * <p>CropMemory 的事件列表可能因持续事件跨日而出现重复记录；规则没有定义
     * 同一次持续事件按天重复叠加品质分，因此这里按“是否真实经历过该事件”各计一次，
     * 避免持续 24 小时事件被重复放大。
     */
    private int eventQualityScore(CropMemory memory) {
        int score = 0;
        if (memory.getEvents().contains(EventType.METEOR_SHOWER)) {
            score += METEOR_QUALITY_BONUS;
        }
        if (memory.getEvents().contains(EventType.RAINBOW_DAY)) {
            score += RAINBOW_DAY_QUALITY_BONUS;
        }
        return score;
    }

    /** 当前世界时间（游戏小时）：gameDay×24 + gameHour。 */
    private long currentWorldTime() {
        return (long) gameClock.getGameDay() * HOURS_PER_DAY + gameClock.getGameHour();
    }
}
