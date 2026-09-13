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
import static com.fieldstory.farm.util.GameConstants.EVENT_PROB_RAINBOW_DAY;

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
 * <p><b>世界时间口径（决策 D14）：</b>世界时间 = {@code getGameDay() * 24 + getGameHour()}
 * （游戏小时），与 A 模块 {@code plantWorldTime} 同一适配口径。D 侧不新增第二时钟、
 * 不新增 {@code getWorldTime()}。
 *
 * <p><b>阶段边界：</b>本实现只提供事件规则与状态，<b>不执行</b>离线模拟
 * （离线模拟由 B 模块复用本服务规则执行，验收规范 §八十九）。
 */
public class BasicEventService implements EventService {

    /** 事件状态（持有，1 对 1）。 */
    private final EventState eventState;

    /** 游戏时钟（读取世界时间，规则文档 §八）。 */
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

        long now = currentWorldTime();
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
     * 当前世界时间（游戏小时，决策 D14 适配口径）。
     *
     * @return 世界时间（游戏小时）
     */
    private long currentWorldTime() {
        return (long) gameClock.getGameDay() * 24 + gameClock.getGameHour();
    }
}
