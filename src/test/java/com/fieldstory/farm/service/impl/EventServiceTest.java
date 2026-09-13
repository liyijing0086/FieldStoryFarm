package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.impl.BasicEventState;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.util.RandomProvider;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2 EventService 测试（D 模块 P2 文档 §6.1）。
 *
 * <p>覆盖：固定种子可复现、概率分布、事件持续时间、到期关闭、即时事件不设持续、
 * 写入 EventState、显示名与图标、判定方法。只测纯函数，不实例化 JavaFX 控件。
 */
class EventServiceTest {

    /** 概率分布抽样次数（规则文档 §四十七）。 */
    private static final int SAMPLE_SIZE = 10000;

    /** 概率容差（±3%，D 模块 P2 文档 §6.1）。 */
    private static final double TOLERANCE = 0.03;

    private BasicEventService newService() {
        return new BasicEventService(new BasicEventState(), new BasicGameClock());
    }

    @Test
    void fixedSeedProducesReproducibleSequence() {
        RandomProvider.setSeed(12345L);
        BasicEventService first = newService();
        EventType[] firstSeq = new EventType[30];
        for (int i = 0; i < firstSeq.length; i++) {
            firstSeq[i] = first.rollDailyEvent(i + 1);
        }

        RandomProvider.setSeed(12345L);
        BasicEventService second = newService();
        for (int i = 0; i < firstSeq.length; i++) {
            assertEquals(firstSeq[i], second.rollDailyEvent(i + 1),
                    "相同种子应产生相同事件序列（规则文档 §九十）");
        }
    }

    @Test
    void probabilityDistributionMatchesRules() {
        RandomProvider.setSeed(987654321L);
        BasicEventService service = newService();
        Map<EventType, Integer> counts = new EnumMap<>(EventType.class);
        for (EventType type : EventType.values()) {
            counts.put(type, 0);
        }
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            EventType type = service.rollDailyEvent(i + 1);
            counts.put(type, counts.get(type) + 1);
        }

        assertProbability(counts, EventType.NONE, 0.74);
        assertProbability(counts, EventType.METEOR_SHOWER, 0.05);
        assertProbability(counts, EventType.MYSTERY_MERCHANT, 0.08);
        assertProbability(counts, EventType.ANIMAL_VISIT, 0.10);
        assertProbability(counts, EventType.RAINBOW_DAY, 0.03);
    }

    private void assertProbability(Map<EventType, Integer> counts, EventType type, double expected) {
        double actual = counts.get(type) / (double) SAMPLE_SIZE;
        assertTrue(Math.abs(actual - expected) <= TOLERANCE,
                type + " 概率应约 " + expected + "，实际 " + actual);
    }

    @Test
    void rollDailyEventWritesState() {
        BasicEventState state = new BasicEventState();
        BasicEventService service = new BasicEventService(state, new BasicGameClock());
        RandomProvider.setSeed(20240601L);
        EventType rolled = service.rollDailyEvent(5);
        assertEquals(rolled, state.getEventType(),
                "rollDailyEvent 应写入 EventState");
    }

    @Test
    void meteorShowerLasts24Hours() {
        BasicEventState state = new BasicEventState();
        BasicEventService service = new BasicEventService(state, new BasicGameClock());
        // 直接构造流星夜状态验证持续时长（避免依赖随机）
        state.setEventType(EventType.METEOR_SHOWER);
        state.setStartWorldTime(100L);
        state.setEndWorldTime(100L + EventType.METEOR_SHOWER.getDurationHours());
        assertEquals(24, state.getEndWorldTime() - state.getStartWorldTime());
        assertTrue(service.isEventActive(110L));
        assertFalse(service.isEventActive(124L));
    }

    @Test
    void mysteryMerchantLasts12Hours() {
        BasicEventState state = new BasicEventState();
        BasicEventService service = new BasicEventService(state, new BasicGameClock());
        state.setEventType(EventType.MYSTERY_MERCHANT);
        state.setStartWorldTime(50L);
        state.setEndWorldTime(50L + EventType.MYSTERY_MERCHANT.getDurationHours());
        assertEquals(12, state.getEndWorldTime() - state.getStartWorldTime());
        assertTrue(service.isEventActive(60L));
        assertFalse(service.isEventActive(62L));
    }

    @Test
    void rainbowDayLasts24Hours() {
        BasicEventState state = new BasicEventState();
        BasicEventService service = new BasicEventService(state, new BasicGameClock());
        state.setEventType(EventType.RAINBOW_DAY);
        state.setStartWorldTime(0L);
        state.setEndWorldTime(24L);
        assertEquals(24, state.getEndWorldTime() - state.getStartWorldTime());
        assertTrue(service.isEventActive(23L));
        assertFalse(service.isEventActive(24L));
    }

    @Test
    void instantEventIsNotActive() {
        BasicEventState state = new BasicEventState();
        BasicEventService service = new BasicEventService(state, new BasicGameClock());
        state.setEventType(EventType.ANIMAL_VISIT);
        state.setStartWorldTime(10L);
        state.setEndWorldTime(10L);
        assertFalse(service.isEventActive(10L), "即时事件不持续（规则文档 §五十）");
    }

    @Test
    void expireIfNeededClosesExpiredEvent() {
        BasicEventState state = new BasicEventState();
        BasicEventService service = new BasicEventService(state, new BasicGameClock());
        state.setEventType(EventType.METEOR_SHOWER);
        state.setStartWorldTime(0L);
        state.setEndWorldTime(24L);

        service.expireIfNeeded(23L);
        assertEquals(EventType.METEOR_SHOWER, state.getEventType(), "未到期不应关闭");

        service.expireIfNeeded(24L);
        assertEquals(EventType.NONE, state.getEventType(), "到期应关闭事件");
        assertEquals(0L, state.getStartWorldTime());
        assertEquals(0L, state.getEndWorldTime());
    }

    @Test
    void displayNameAndIconAreNonEmptyForAllTypes() {
        BasicEventService service = newService();
        for (EventType type : EventType.values()) {
            assertNotNull(service.getDisplayName(type));
            assertNotNull(service.getIcon(type));
        }
        assertEquals("流星夜", service.getDisplayName(EventType.METEOR_SHOWER));
        assertEquals("彩虹日", service.getDisplayName(EventType.RAINBOW_DAY));
    }

    @Test
    void predicateMethodsMatchOnlyTheirType() {
        BasicEventService service = newService();
        assertTrue(service.isMeteorShower(EventType.METEOR_SHOWER));
        assertFalse(service.isMeteorShower(EventType.RAINBOW_DAY));
        assertTrue(service.isMysteryMerchant(EventType.MYSTERY_MERCHANT));
        assertFalse(service.isMysteryMerchant(EventType.METEOR_SHOWER));
        assertTrue(service.isRainbowDay(EventType.RAINBOW_DAY));
        assertFalse(service.isRainbowDay(EventType.ANIMAL_VISIT));
    }

    @Test
    void mysteryMerchantAssignsTargetCrop() {
        BasicEventState state = new BasicEventState();
        BasicEventService service = new BasicEventService(state, new BasicGameClock());
        RandomProvider.setSeed(42L);
        // 反复抽取直到命中神秘商人，验证 targetCropType 被赋值
        boolean found = false;
        for (int i = 0; i < 2000 && !found; i++) {
            EventType type = service.rollDailyEvent(i + 1);
            if (type == EventType.MYSTERY_MERCHANT) {
                CropType target = state.getTargetCropType();
                assertNotNull(target, "神秘商人应指定目标作物（规则文档 §四十九）");
                assertTrue(target == CropType.WHEAT || target == CropType.CORN
                        || target == CropType.CARROT);
                found = true;
            }
        }
        assertTrue(found, "2000 次抽取应至少命中一次神秘商人");
    }

    @Test
    void nonMysteryEventClearsTargetCrop() {
        BasicEventState state = new BasicEventState();
        BasicEventService service = new BasicEventService(state, new BasicGameClock());
        state.setTargetCropType(CropType.WHEAT);
        RandomProvider.setSeed(1L);
        // 抽取直到非神秘商人
        for (int i = 0; i < 2000; i++) {
            EventType type = service.rollDailyEvent(i + 1);
            if (type != EventType.MYSTERY_MERCHANT) {
                assertNull(state.getTargetCropType(), "非神秘商人应清空目标作物");
                return;
            }
        }
    }
}
