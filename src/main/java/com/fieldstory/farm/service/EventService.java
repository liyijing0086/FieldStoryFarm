package com.fieldstory.farm.service;

import com.fieldstory.farm.model.EventType;

/**
 * 随机事件服务接口（D 模块 P2：世界环境 · 随机事件系统）。
 *
 * <p>依据《D模块 P2 接口与类设计文档》§4.4、《游戏规则设计文档》§四十七~§五十一、
 * 《P0-P4功能实现与验收规范》§九十/§九十二。
 *
 * <p>职责：每日抽取随机事件、维护事件持续状态、提供事件判定与显示信息。
 * 本接口只提供事件数据与判定，不参与品质/售价/成长计算（统一 Model / Service 归属原则，
 * 验收规范 §3.1）。
 *
 * <p>随机统一走 {@code RandomProvider}（规则文档 §九十），禁止 {@code new Random()}。
 *
 * <p><b>阶段边界：</b>D 模块只提供事件规则与状态，<b>不执行</b>离线模拟
 * （离线模拟由 B 模块 {@code OfflineSimulationService} 复用本服务规则执行，验收规范 §八十九）。
 */
public interface EventService {

    /**
     * 抽取指定游戏日的随机事件（每天 00:00 调用一次，规则文档 §四十七/§八十一）。
     *
     * <p>概率：无事件 74%、流星夜 5%、神秘商人 8%、小动物来访 10%、彩虹日 3%
     * （规则文档 §四十七）。一次随机抽取决定结果，禁止四个事件分别独立判断
     * （验收规范 §九十）。生成结果写入内部 {@code EventState}。
     *
     * @param dayIndex 游戏日索引（从 1 开始）
     * @return 抽取到的事件类型
     */
    EventType rollDailyEvent(int dayIndex);

    /**
     * 判断事件在指定世界时间是否持续中（规则文档 §四十八~§五十一）。
     *
     * <p>即时事件（小动物来访）不持续，返回 false。
     *
     * @param currentWorldTime 当前世界时间（游戏小时）
     * @return 事件持续中返回 true
     */
    boolean isEventActive(long currentWorldTime);

    /**
     * 到期关闭事件（规则文档 §八十一第 ⑤ 步：关闭到期事件）。
     *
     * <p>当 {@code currentWorldTime >= endWorldTime} 时，将事件重置为 {@code NONE}。
     * 无副作用于其他系统。
     *
     * @param currentWorldTime 当前世界时间（游戏小时）
     */
    void expireIfNeeded(long currentWorldTime);

    /**
     * 事件显示名（UI，验收规范 §九十二）。
     *
     * @param type 事件类型
     * @return 中文显示名
     */
    String getDisplayName(EventType type);

    /**
     * 事件图标（UI，验收规范 §九十二）。
     *
     * @param type 事件类型
     * @return 图标字符串
     */
    String getIcon(EventType type);

    /**
     * 是否流星夜（供 C 模块品质分/传说突破加成，规则文档 §四十八）。
     *
     * @param type 事件类型
     * @return 流星夜返回 true
     */
    boolean isMeteorShower(EventType type);

    /**
     * 是否神秘商人（供 C 模块售价加成，规则文档 §四十九）。
     *
     * @param type 事件类型
     * @return 神秘商人返回 true
     */
    boolean isMysteryMerchant(EventType type);

    /**
     * 是否彩虹日（供 C 模块品质分、A 模块 EventRate 加成，规则文档 §五十一）。
     *
     * @param type 事件类型
     * @return 彩虹日返回 true
     */
    boolean isRainbowDay(EventType type);
}
