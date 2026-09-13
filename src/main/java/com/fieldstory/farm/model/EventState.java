package com.fieldstory.farm.model;

/**
 * 随机事件状态模型（D 模块 P2：世界环境 · 随机事件系统）。
 *
 * <p>依据《D模块 P2 接口与类设计文档》§4.2、《游戏规则设计文档》§四十七~§五十一、
 * 《P0-P4功能实现与验收规范》§九十一。
 *
 * <p>职责：只保存「当前事件是什么、何时开始、何时结束、目标作物、附加数据」，
 * 不含任何事件生成、概率计算、到期判定逻辑（统一 Model 原则，验收规范 §4）。
 *
 * <p>存档映射：对应 E 模块 {@code active_event} 表字段
 * {@code event_type}（枚举 {@code name()}）、{@code start_world_time}、
 * {@code end_world_time}、{@code target_crop_type}、{@code payload}（验收规范 §九十一）。
 */
public interface EventState {

    /**
     * 获取当前事件类型（规则文档 §四十七）。
     *
     * @return 当前事件类型
     */
    EventType getEventType();

    /**
     * 设置当前事件类型。
     *
     * @param eventType 事件类型
     */
    void setEventType(EventType eventType);

    /**
     * 获取事件开始世界时间（游戏小时，规则文档 §四十八~§五十一）。
     *
     * @return 开始世界时间（游戏小时）
     */
    long getStartWorldTime();

    /**
     * 设置事件开始世界时间。
     *
     * @param startWorldTime 开始世界时间（游戏小时）
     */
    void setStartWorldTime(long startWorldTime);

    /**
     * 获取事件结束世界时间（游戏小时，规则文档 §四十八~§五十一）。
     *
     * @return 结束世界时间（游戏小时）
     */
    long getEndWorldTime();

    /**
     * 设置事件结束世界时间。
     *
     * @param endWorldTime 结束世界时间（游戏小时）
     */
    void setEndWorldTime(long endWorldTime);

    /**
     * 获取神秘商人指定作物（规则文档 §四十九：随机指定 WHEAT/CORN/CARROT）。
     *
     * <p>非神秘商人事件时为 {@code null}。
     *
     * @return 目标作物类型，无则为 null
     */
    CropType getTargetCropType();

    /**
     * 设置神秘商人指定作物。
     *
     * @param targetCropType 目标作物类型
     */
    void setTargetCropType(CropType targetCropType);

    /**
     * 获取事件附加数据（验收规范 §九十一：{@code payload}）。
     *
     * <p>用于承载事件特有信息（如小动物奖励描述），无则为 {@code null}。
     *
     * @return 附加数据，无则为 null
     */
    String getPayload();

    /**
     * 设置事件附加数据。
     *
     * @param payload 附加数据
     */
    void setPayload(String payload);
}
