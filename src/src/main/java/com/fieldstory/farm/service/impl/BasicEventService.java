package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.service.EventService;
import com.fieldstory.farm.util.RandomProvider;

import static com.fieldstory.farm.util.GameConstants.EVENT_DURATION_METEOR_SHOWER;
import static com.fieldstory.farm.util.GameConstants.EVENT_DURATION_MYSTERY_MERCHANT;
import static com.fieldstory.farm.util.GameConstants.EVENT_DURATION_RAINBOW_DAY;
import static com.fieldstory.farm.util.GameConstants.EVENT_PROB_ANIMAL_VISIT;
import static com.fieldstory.farm.util.GameConstants.EVENT_PROB_METEOR_SHOWER;
import static com.fieldstory.farm.util.GameConstants.EVENT_PROB_MYSTERY_MERCHANT;
import static com.fieldstory.farm.util.GameConstants.EVENT_PROB_NONE;

/**
 * {@link EventService} 基础实现（D 模块 P2：世界环境 · 随机事件系统）。
 *
 * <p>依据《D模块 P2 接口与类设计文档》§4.5、《游戏规则设计文档》§四十七~§五十一/§九十、
 * 《P0-P4功能实现与验收规范》§九十/§九十一/§九十二。
 *
 * <p>概率抽取：使用 {@link RandomProvider#nextInt(int)} 得到 {@code [0, 100)} 整数，
 * 区间划分 {@code [0,74)} 无事件、{@code [74,79)} 流星夜、{@code [79,87)} 神秘商人、
 * {@code [87,97)} 小动物来访、{@code [97,100)} 彩虹日，区间宽度严格等于 74/5/8/10/3
 * （合计 100）。一次随机抽取决定结果，禁止四个事件分别独立判断（验收规范 §九十）。
 * 随机必须经 {@code RandomProvider}，禁止 {@code new Random()}（规则文档 §九十）。
 *
 * <p><b>事件起点口径（D 方案 A）：</b>{@link #rollDailyEvent(int)} 只在每日 00:00
 * 世界结算边界创建“当天事件”，因此事件起始世界时间必须由传入的 {@code dayIndex}
 * 计算为 {@code dayIndex * 24L}。不能读取一个尚未同步到该日边界的旧 {@link GameClock}
 * 来决定新事件起点，否则在线跨日/离线逐日模拟都会产生时间漂移。
 *
 * <p><b>阶段边界：</b>本实现只提供事件规则与状态，<b>不执行</b>离线模拟
 * （离线模拟由 B 模块复用本服务规则执行，验收规范 §八十九）。
 */
public class BasicEventService implements EventService {

    /** 事件状态（持有，1 对 1）。 */
    private final EventState eventState;

    /**
     * 游戏时钟依赖。保留既有构造/装配契约；每日事件的开始时间不从这里读取，
     * 而由 rollDailyEvent(dayIndex) 的目标游戏日 00:00 决定。
     */
    private final GameClock gameClock;

    /**
     * 构造器注入事件状态与游戏时钟。
     *
     * @param eventState 事件状态
     * @param gameClock  游戏时钟
     */
    public BasicEventService(EventState eventState, GameClock gameClock) {
        this.eventState = eventState;
        this.gameClock = gameClock;
    }

    @Override
    public EventType rollDailyEvent(int dayIndex) {
        int roll = RandomProvider.nextInt(100);
        EventType type;
        if (roll < EVENT_PROB_NONE) {                                   // [0, 74) → 74%
            type = EventType.NONE;
        } else if (roll < EVENT_PROB_NONE + EVENT_PROB_METEOR_SHOWER) { // [74, 79) → 5%
            type = EventType.METEOR_SHOWER;
        } else if (roll < EVENT_PROB_NONE + EVENT_PROB_METEOR_SHOWER
                + EVENT_PROB_MYSTERY_MERCHANT) {                        // [79, 87) → 8%
            type = EventType.MYSTERY_MERCHANT;
        } else if (roll < EVENT_PROB_NONE + EVENT_PROB_METEOR_SHOWER
                + EVENT_PROB_MYSTERY_MERCHANT + EVENT_PROB_ANIMAL_VISIT) { // [87, 97) → 10%
            type = EventType.ANIMAL_VISIT;
        } else {                                                        // [97, 100) → 3%
            type = EventType.RAINBOW_DAY;
        }

        // D 方案 A：当天事件在 00:00 创建。dayIndex 是本次抽取的目标游戏日，
        // 因此事件起点必须锚定该日 00:00，而不是读取可能仍停留在旧时刻的 GameClock。
        long now = dayStartWorldTime(dayIndex);
        eventState.setEventType(type);
        eventState.setTargetCropType(null);
        eventState.setPayload(null);

        if (type == EventType.NONE) {
            eventState.setStartWorldTime(0L);
            eventState.setEndWorldTime(0L);
        } else if (type.isInstant()) {
            // 即时事件（小动物来访）：不设持续，起止均为当前时刻（规则文档 §五十）
            eventState.setStartWorldTime(now);
            eventState.setEndWorldTime(now);
        } else {
            eventState.setStartWorldTime(now);
            eventState.setEndWorldTime(now + durationOf(type));
        }

        if (type == EventType.MYSTERY_MERCHANT) {
            eventState.setTargetCropType(randomTargetCrop());
        }
        return type;
    }

    @Override
    public boolean isEventActive(long currentWorldTime) {
        EventType type = eventState.getEventType();
        if (type == null || type == EventType.NONE || type.isInstant()) {
            return false;
        }
        return currentWorldTime >= eventState.getStartWorldTime()
                && currentWorldTime < eventState.getEndWorldTime();
    }

    @Override
    public void expireIfNeeded(long currentWorldTime) {
        EventType type = eventState.getEventType();
        if (type == null || type == EventType.NONE) {
            return;
        }
        if (currentWorldTime >= eventState.getEndWorldTime()) {
            eventState.setEventType(EventType.NONE);
            eventState.setStartWorldTime(0L);
            eventState.setEndWorldTime(0L);
            eventState.setTargetCropType(null);
            eventState.setPayload(null);
        }
    }

    @Override
    public String getDisplayName(EventType type) {
        return type == null ? EventType.NONE.getDisplayName() : type.getDisplayName();
    }

    @Override
    public String getIcon(EventType type) {
        return type == null ? EventType.NONE.getIcon() : type.getIcon();
    }

    @Override
    public boolean isMeteorShower(EventType type) {
        return type == EventType.METEOR_SHOWER;
    }

    @Override
    public boolean isMysteryMerchant(EventType type) {
        return type == EventType.MYSTERY_MERCHANT;
    }

    @Override
    public boolean isRainbowDay(EventType type) {
        return type == EventType.RAINBOW_DAY;
    }

    /**
     * 计算事件持续时间（游戏小时，规则文档 §四十八~§五十一）。
     *
     * @param type 事件类型
     * @return 持续时间（游戏小时）
     */
    private int durationOf(EventType type) {
        switch (type) {
            case METEOR_SHOWER:
                return EVENT_DURATION_METEOR_SHOWER;
            case MYSTERY_MERCHANT:
                return EVENT_DURATION_MYSTERY_MERCHANT;
            case RAINBOW_DAY:
                return EVENT_DURATION_RAINBOW_DAY;
            default:
                return 0;
        }
    }

    /**
     * 随机指定神秘商人目标作物（规则文档 §四十九：WHEAT/CORN/CARROT）。
     *
     * @return 目标作物类型
     */
    private CropType randomTargetCrop() {
        CropType[] candidates = {CropType.WHEAT, CropType.CORN, CropType.CARROT};
        return candidates[RandomProvider.nextInt(candidates.length)];
    }

    /**
     * 计算指定游戏日 00:00 对应的世界时间。
     *
     * <p>项目当前世界时间适配口径为 {@code dayIndex * 24 + hour}；每日事件在
     * 00:00 抽取，因此 hour 固定为 0。使用传入 dayIndex 可以保证在线跨日与
     * 离线模拟调用得到完全一致的事件时间轴。
     *
     * @param dayIndex 游戏日索引（从 1 开始）
     * @return 当天 00:00 对应的世界时间（游戏小时）
     */
    private long dayStartWorldTime(int dayIndex) {
        return (long) dayIndex * 24L;
    }
}
