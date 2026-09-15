package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;

/**
 * {@link EventState} 的默认实现（D 模块 P2：世界环境 · 随机事件系统）。
 *
 * <p>依据《D模块 P2 接口与类设计文档》§4.3、决策 D13（接口在包根，实现类以 Basic 前缀放 impl 子包）。
 *
 * <p>与 {@code BasicWeatherState} 对称：只保存状态，不含业务逻辑。
 */
public class BasicEventState implements EventState {

    /** 当前事件类型。 */
    private EventType eventType;

    /** 事件开始世界时间（游戏小时）。 */
    private long startWorldTime;

    /** 事件结束世界时间（游戏小时）。 */
    private long endWorldTime;

    /** 神秘商人指定作物（非神秘商人时为 null）。 */
    private CropType targetCropType;

    /** 事件附加数据。 */
    private String payload;

    /**
     * 默认构造：{@code NONE}，起止世界时间均为 0
     * （P0/P1 无事件语义延续，规则文档 §四十七）。
     */
    public BasicEventState() {
        this(EventType.NONE, 0L, 0L);
    }

    /**
     * 指定事件类型与起止世界时间构造，用于存档恢复（验收规范 §九十一）。
     *
     * @param eventType      事件类型
     * @param startWorldTime 开始世界时间（游戏小时）
     * @param endWorldTime   结束世界时间（游戏小时）
     */
    public BasicEventState(EventType eventType, long startWorldTime, long endWorldTime) {
        this.eventType = eventType;
        this.startWorldTime = startWorldTime;
        this.endWorldTime = endWorldTime;
    }

    @Override
    public EventType getEventType() {
        return eventType;
    }

    @Override
    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    @Override
    public long getStartWorldTime() {
        return startWorldTime;
    }

    @Override
    public void setStartWorldTime(long startWorldTime) {
        this.startWorldTime = startWorldTime;
    }

    @Override
    public long getEndWorldTime() {
        return endWorldTime;
    }

    @Override
    public void setEndWorldTime(long endWorldTime) {
        this.endWorldTime = endWorldTime;
    }

    @Override
    public CropType getTargetCropType() {
        return targetCropType;
    }

    @Override
    public void setTargetCropType(CropType targetCropType) {
        this.targetCropType = targetCropType;
    }

    @Override
    public String getPayload() {
        return payload;
    }

    @Override
    public void setPayload(String payload) {
        this.payload = payload;
    }
}
