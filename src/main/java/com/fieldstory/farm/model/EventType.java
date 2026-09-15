package com.fieldstory.farm.model;

/**
 * 随机事件类型枚举（D 模块 P0：世界环境，P2 启用）。
 *
 * <p>依据《D模块 P0 接口与类设计文档》§8.2、《D模块 P2 接口与类设计文档》§4.1、
 * 《游戏规则设计文档》§四十七~§五十一、《P0-P4功能实现与验收规范》§九十/§九十二。
 *
 * <p><b>P0 约束：</b>P0 阶段只允许定义枚举常量，禁止在 P0 业务代码中引用
 * （验收规范 §10：P0 不实现随机事件，世界环境固定 {@code EventRate = 1.0}）。
 * 随机事件系统自 P2 起启用。
 *
 * <p><b>存档兼容：</b>枚举 {@code name()} 即存档字符串（E 模块 {@code active_event.event_type}
 * 以 {@code name()} 存取，验收规范 §九十一），常量名不得改动。
 *
 * <p><b>数值来源：</b>持续时间与即时性来自规则文档 §四十八~§五十一。
 */
public enum EventType {

    /** 流星夜（P2 启用，规则文档 §四十八）：持续 24 游戏小时。 */
    METEOR_SHOWER("流星夜", "\uD83C\uDF20", 24, false),

    /** 神秘商人（P2 启用，规则文档 §四十九）：持续 12 游戏小时。 */
    MYSTERY_MERCHANT("神秘商人", "\uD83E\uDDD9", 12, false),

    /** 小动物来访（P2 启用，规则文档 §五十）：即时事件，无持续时间。 */
    ANIMAL_VISIT("小动物来访", "\uD83D\uDC3F", 0, true),

    /** 彩虹日（P2 启用，规则文档 §五十一）：持续 24 游戏小时。 */
    RAINBOW_DAY("彩虹日", "\uD83C\uDF08", 24, false),

    /** 无事件（P0/P1 默认值，规则文档 §四十七：74% 概率）。 */
    NONE("无事件", "", 0, true);

    /** 中文显示名（UI，验收规范 §九十二）。 */
    private final String displayName;

    /** 图标字符（UI，验收规范 §九十二）。 */
    private final String icon;

    /** 持续时间（游戏小时）；即时事件为 0（规则文档 §四十八~§五十一）。 */
    private final int durationHours;

    /** 是否即时事件（规则文档 §五十：小动物来访为即时事件）。 */
    private final boolean instant;

    EventType(String displayName, String icon, int durationHours, boolean instant) {
        this.displayName = displayName;
        this.icon = icon;
        this.durationHours = durationHours;
        this.instant = instant;
    }

    /**
     * 获取中文显示名。
     *
     * @return 中文显示名
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取图标字符。
     *
     * @return 图标字符
     */
    public String getIcon() {
        return icon;
    }

    /**
     * 获取持续时间（游戏小时）。
     *
     * @return 持续时间；即时事件返回 0
     */
    public int getDurationHours() {
        return durationHours;
    }

    /**
     * 是否为即时事件。
     *
     * @return 即时事件返回 true
     */
    public boolean isInstant() {
        return instant;
    }
}
