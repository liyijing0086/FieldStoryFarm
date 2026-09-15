package com.fieldstory.farm.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CropMemory} 生命记忆模型测试（规则文档 §六十九；
 * 验收规范 §九十三~九十五）。
 */
class CropMemoryTest {

    /**
     * 构造后时间字段为 -1 哨兵（决策 D14：与第 0 小时/第 0 游戏日区分），
     * 品质未落档时 isCompleted 为 false。
     */
    @Test
    void sentinelTimesAndIncompleteByDefault() {
        CropMemory memory = new CropMemory(UUID.randomUUID(), CropType.WHEAT, 48);

        assertEquals(48, memory.getPlantWorldTime());
        assertEquals(-1, memory.getMatureWorldTime());
        assertEquals(-1, memory.getHarvestWorldTime());
        assertEquals(-1, memory.getLastDroughtGameDay());
        assertFalse(memory.isCompleted());
        assertNotNull(memory.getEvents());
        assertTrue(memory.getEvents().isEmpty());
    }

    /** 无参构造（持久化反序列化预留）：可后续补字段。 */
    @Test
    void noArgConstructorForPersistence() {
        CropMemory memory = new CropMemory();

        memory.setCropUuid(UUID.randomUUID());
        memory.setCropType(CropType.CORN);
        memory.setPlantWorldTime(24);

        assertEquals(CropType.CORN, memory.getCropType());
        assertEquals(24, memory.getPlantWorldTime());
    }

    /**
     * 品质落档后 isCompleted 为 true（验收规范 §九十四：收获完成
     * 即品质结果落档，收获时间写入）。
     */
    @Test
    void completedWhenQualityRecorded() {
        CropMemory memory = new CropMemory(UUID.randomUUID(), CropType.WHEAT, 48);
        memory.setQuality(Quality.EPIC);
        memory.setHarvestWorldTime(96);

        assertTrue(memory.isCompleted());
        assertEquals(Quality.EPIC, memory.getQuality());
        assertEquals(96, memory.getHarvestWorldTime());
    }

    /** 传说标志与最终故事独立落档（验收规范 §九十四 记录项）。 */
    @Test
    void legendaryFlagAndStoryAreIndependent() {
        CropMemory memory = new CropMemory(UUID.randomUUID(), CropType.WHEAT, 48);
        memory.setLegendary(true);
        memory.setFinalStory("化作金色麦穗");

        assertTrue(memory.isLegendary());
        assertEquals("化作金色麦穗", memory.getFinalStory());
    }

    /** 经历计数默认 0，可逐个累加（MemoryService 写入）。 */
    @Test
    void experienceCountersStartAtZero() {
        CropMemory memory = new CropMemory(UUID.randomUUID(), CropType.CARROT, 24);

        assertEquals(0, memory.getManualWaterCount());
        assertEquals(0, memory.getRainCount());
        assertEquals(0, memory.getDroughtCount());
        assertEquals(0, memory.getGreenRainCount());
        assertEquals(0, memory.getFertilizerCount());
        assertFalse(memory.isWitherRisk());
        assertFalse(memory.isWaterRescueOnDroughtDay());
    }

    /** 构造参数校验：uuid 与类型不得为空。 */
    @Test
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class,
                () -> new CropMemory(null, CropType.WHEAT, 48));
        assertThrows(NullPointerException.class,
                () -> new CropMemory(UUID.randomUUID(), null, 48));
    }
}
