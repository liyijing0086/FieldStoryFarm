package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P2 BasicEventState 测试（D 模块 P2 文档 §6.2）。
 *
 * <p>覆盖：无参构造默认值、双参构造字段赋值、setter/getter 往返一致。
 * 只测纯函数，不实例化 JavaFX 控件。
 */
class BasicEventStateTest {

    @Test
    void defaultConstructorIsNoneWithZeroTimes() {
        BasicEventState state = new BasicEventState();
        assertEquals(EventType.NONE, state.getEventType());
        assertEquals(0L, state.getStartWorldTime());
        assertEquals(0L, state.getEndWorldTime());
        assertNull(state.getTargetCropType());
        assertNull(state.getPayload());
    }

    @Test
    void parameterizedConstructorAssignsFields() {
        BasicEventState state = new BasicEventState(EventType.METEOR_SHOWER, 100L, 124L);
        assertEquals(EventType.METEOR_SHOWER, state.getEventType());
        assertEquals(100L, state.getStartWorldTime());
        assertEquals(124L, state.getEndWorldTime());
    }

    @Test
    void settersAndGettersRoundTrip() {
        BasicEventState state = new BasicEventState();
        state.setEventType(EventType.MYSTERY_MERCHANT);
        state.setStartWorldTime(50L);
        state.setEndWorldTime(62L);
        state.setTargetCropType(CropType.CORN);
        state.setPayload("reward");

        assertEquals(EventType.MYSTERY_MERCHANT, state.getEventType());
        assertEquals(50L, state.getStartWorldTime());
        assertEquals(62L, state.getEndWorldTime());
        assertEquals(CropType.CORN, state.getTargetCropType());
        assertEquals("reward", state.getPayload());
    }
}
