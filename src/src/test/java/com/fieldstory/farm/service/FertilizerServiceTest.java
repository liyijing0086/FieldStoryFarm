package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.item.Inventory;
import com.fieldstory.farm.model.item.Item;
import com.fieldstory.farm.model.item.ItemType;
import com.fieldstory.farm.service.impl.BasicFertilizerService;
import com.fieldstory.farm.service.impl.BasicMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link FertilizerService} 施肥规则测试
 * （验收规范 §六十三；规则文档 §二十六）。
 *
 * <p>规则：SPROUT/GROWING 可施、每株每天最多 1 次、生命周期最多 3 次、
 * 每次消耗 1 肥料、成长 +15%/次（上限 +45%）、品质 +8/次（由品质评分
 * 在收获时按记忆次数计分）。
 */
class FertilizerServiceTest {

    private MemoryService memoryService;
    private FertilizerService fertilizerService;

    @BeforeEach
    void setUp() {
        memoryService = new BasicMemoryService();
        fertilizerService = new BasicFertilizerService(memoryService);
    }

    /** 指定阶段的作物。 */
    private Crop cropAt(GrowthStage stage) {
        Crop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(CropType.WHEAT);
        crop.setGrowthStage(stage);
        return crop;
    }

    /** 新建记忆档案（播种时刻 48 小时 = 第 2 天）。 */
    private CropMemory memoryFor(Crop crop) {
        return memoryService.createMemory(crop);
    }

    /** 含 n 个肥料的背包。 */
    private Inventory inventoryWithFertilizer(int n) {
        Inventory inventory = new Inventory();
        inventory.addItem(new Item(ItemType.FERTILIZER, n));
        return inventory;
    }

    /** SPROUT 阶段施肥成功：扣 1 肥料、次数 +1、记录施肥日。 */
    @Test
    void fertilizeSproutSucceedsAndRecordsDay() {
        Crop crop = cropAt(GrowthStage.SPROUT);
        CropMemory memory = memoryFor(crop);
        Inventory inventory = inventoryWithFertilizer(3);

        FertilizeResult result = fertilizerService.fertilize(
                crop, memory, inventory, 3);

        assertEquals(FertilizeResult.SUCCESS, result);
        assertEquals(2, inventory.getQuantity(ItemType.FERTILIZER));
        assertEquals(1, memory.getFertilizerCount());
        assertEquals(3, memory.getLastFertilizeGameDay());
        assertEquals(1, crop.getFertilizerCount(), "正式成长链读取 Crop 运行态，成功后必须同步");
        assertEquals(3, crop.getLastFertilizedGameDay());
    }

    /** GROWING 阶段同样允许施肥。 */
    @Test
    void fertilizeGrowingSucceeds() {
        Crop crop = cropAt(GrowthStage.GROWING);
        CropMemory memory = memoryFor(crop);

        FertilizeResult result = fertilizerService.fertilize(
                crop, memory, inventoryWithFertilizer(1), 5);

        assertEquals(FertilizeResult.SUCCESS, result);
        assertEquals(1, memory.getFertilizerCount());
        assertEquals(5, memory.getLastFertilizeGameDay());
        assertEquals(1, crop.getFertilizerCount());
        assertEquals(5, crop.getLastFertilizedGameDay());
    }

    /** SEED 与 MATURE 阶段拒绝施肥（规则文档 §二十六）。 */
    @Test
    void fertilizeRejectedOnSeedAndMature() {
        Crop seed = cropAt(GrowthStage.SEED);
        Crop mature = cropAt(GrowthStage.MATURE);
        Inventory inventory = inventoryWithFertilizer(2);

        assertEquals(FertilizeResult.NOT_ALLOWED_STAGE,
                fertilizerService.fertilize(seed, memoryFor(seed), inventory, 3));
        assertEquals(FertilizeResult.NOT_ALLOWED_STAGE,
                fertilizerService.fertilize(mature, memoryFor(mature), inventory, 3));
        assertEquals(2, inventory.getQuantity(ItemType.FERTILIZER));
    }

    /** 同一天第二次施肥被拒绝，且不扣库存（每日最多 1 次）。 */
    @Test
    void fertilizeTwiceSameDayRejected() {
        Crop crop = cropAt(GrowthStage.SPROUT);
        CropMemory memory = memoryFor(crop);
        Inventory inventory = inventoryWithFertilizer(3);

        assertEquals(FertilizeResult.SUCCESS,
                fertilizerService.fertilize(crop, memory, inventory, 3));
        assertEquals(FertilizeResult.ALREADY_FERTILIZED_TODAY,
                fertilizerService.fertilize(crop, memory, inventory, 3));

        assertEquals(2, inventory.getQuantity(ItemType.FERTILIZER));
        assertEquals(1, memory.getFertilizerCount());
        assertEquals(1, crop.getFertilizerCount());
        assertEquals(3, crop.getLastFertilizedGameDay());
    }

    /** 生命周期第 4 次被拒绝（最多 3 次），且不扣库存。 */
    @Test
    void fertilizeFourthTimeOverLifeRejected() {
        Crop crop = cropAt(GrowthStage.SPROUT);
        CropMemory memory = memoryFor(crop);
        Inventory inventory = inventoryWithFertilizer(5);

        assertEquals(FertilizeResult.SUCCESS,
                fertilizerService.fertilize(crop, memory, inventory, 1));
        assertEquals(FertilizeResult.SUCCESS,
                fertilizerService.fertilize(crop, memory, inventory, 2));
        assertEquals(FertilizeResult.SUCCESS,
                fertilizerService.fertilize(crop, memory, inventory, 3));
        assertEquals(FertilizeResult.MAX_TIMES_PER_LIFE,
                fertilizerService.fertilize(crop, memory, inventory, 4));

        assertEquals(2, inventory.getQuantity(ItemType.FERTILIZER));
        assertEquals(3, memory.getFertilizerCount());
        assertEquals(3, crop.getFertilizerCount());
        assertEquals(3, crop.getLastFertilizedGameDay());
    }

    /** 库存不足拒绝施肥，不落档（consume 失败本身不扣减）。 */
    @Test
    void fertilizeWithoutFertilizerRejected() {
        Crop crop = cropAt(GrowthStage.SPROUT);
        CropMemory memory = memoryFor(crop);

        FertilizeResult result = fertilizerService.fertilize(
                crop, memory, new Inventory(), 3);

        assertEquals(FertilizeResult.NOT_ENOUGH_FERTILIZER, result);
        assertEquals(0, memory.getFertilizerCount());
        assertEquals(-1, memory.getLastFertilizeGameDay());
        assertEquals(0, crop.getFertilizerCount(), "扣库存失败时 Crop 运行态也不能提前提交");
        assertEquals(-1, crop.getLastFertilizedGameDay());
    }

    /** 库存仅 1 个：成功后次日库存不足被拒，次数不重复累加。 */
    @Test
    void fertilizeAfterStockExhaustedRejectedNextDay() {
        Crop crop = cropAt(GrowthStage.GROWING);
        CropMemory memory = memoryFor(crop);
        Inventory inventory = inventoryWithFertilizer(1);

        assertEquals(FertilizeResult.SUCCESS,
                fertilizerService.fertilize(crop, memory, inventory, 1));
        assertEquals(FertilizeResult.NOT_ENOUGH_FERTILIZER,
                fertilizerService.fertilize(crop, memory, inventory, 2));

        assertEquals(1, memory.getFertilizerCount());
        assertEquals(1, memory.getLastFertilizeGameDay());
        assertEquals(1, crop.getFertilizerCount());
        assertEquals(1, crop.getLastFertilizedGameDay());
    }

    /** 作物或档案缺失时拒绝。 */
    @Test
    void fertilizeWithoutCropOrMemoryRejected() {
        Crop crop = cropAt(GrowthStage.SPROUT);
        Inventory inventory = inventoryWithFertilizer(1);

        assertEquals(FertilizeResult.NO_CROP_OR_MEMORY,
                fertilizerService.fertilize(null, memoryFor(crop), inventory, 3));
        assertEquals(FertilizeResult.NO_CROP_OR_MEMORY,
                fertilizerService.fertilize(crop, null, inventory, 3));
    }

    /** 正式成长链以 Crop 运行态为准：退出重进恢复后无需依赖 Memory 查询即可计算。 */
    @Test
    void runtimeCropGrowthRateCapsAtThreeTimes() {
        Crop crop = cropAt(GrowthStage.GROWING);

        assertEquals(0.0, fertilizerService.fertilizerGrowthRate(crop), 1e-9);
        crop.setFertilizerCount(1);
        assertEquals(0.15, fertilizerService.fertilizerGrowthRate(crop), 1e-9);
        crop.setFertilizerCount(3);
        assertEquals(0.45, fertilizerService.fertilizerGrowthRate(crop), 1e-9);
        crop.setFertilizerCount(99);
        assertEquals(0.45, fertilizerService.fertilizerGrowthRate(crop), 1e-9);
    }

    /** 兼容旧档：Memory 已有次数时，下一次成功施肥会先对齐再递增，不会从 0 重算。 */
    @Test
    void legacyMemoryCountIsReconciledIntoRuntimeCrop() {
        Crop crop = cropAt(GrowthStage.GROWING);
        CropMemory memory = memoryFor(crop);
        memory.setFertilizerCount(2);
        memory.setLastFertilizeGameDay(2);

        FertilizeResult result = fertilizerService.fertilize(
                crop, memory, inventoryWithFertilizer(1), 3);

        assertEquals(FertilizeResult.SUCCESS, result);
        assertEquals(3, crop.getFertilizerCount());
        assertEquals(3, memory.getFertilizerCount());
        assertEquals(3, crop.getLastFertilizedGameDay());
        assertEquals(3, memory.getLastFertilizeGameDay());
    }

    /** 成长加成：0 次 0%、每次 +15%、3 次封顶 +45%。 */
    @Test
    void fertilizerGrowthRateCapsAtThreeTimes() {
        Crop crop = cropAt(GrowthStage.SPROUT);
        CropMemory memory = memoryFor(crop);

        assertEquals(0.0, fertilizerService.fertilizerGrowthRate(memory), 1e-9);

        memoryService.recordFertilizer(memory);
        assertEquals(0.15, fertilizerService.fertilizerGrowthRate(memory), 1e-9);

        memoryService.recordFertilizer(memory);
        memoryService.recordFertilizer(memory);
        assertEquals(0.45, fertilizerService.fertilizerGrowthRate(memory), 1e-9);

        // 脏数据防御：次数超过 3 仍封顶 45%
        memoryService.recordFertilizer(memory);
        assertEquals(0.45, fertilizerService.fertilizerGrowthRate(memory), 1e-9);
    }
}
