package com.fieldstory.farm.model;

import java.time.LocalDateTime;

/**
 * 游戏时钟接口（D 模块 P0：世界环境）。
 *
 * <p>依据《D模块 P0 接口与类设计文档》§一、《游戏规则与数值设计文档》§5.1/§8/§九、
 * 《FSF_P0-P4 分阶段实现与验收规范》§5/§41/§42。实现位于
 * {@code com.fieldstory.farm.model.impl.BasicGameClock}（决策 D13：接口在包根，实现类以 Basic 前缀在 impl 子包）。
 *
 * <p><b>接口方法集（决策 ③：甲文档 = 规则文档 §八）：</b>
 * {@link #getRealTime()}、{@link #getGameDay()}、
 * {@link #getGameHour()}、{@link #advance()}、{@link #calculateOfflineDuration()}。
 * 其余方法（{@link #getTotalMinutes()}、{@link #getGameMinute()}、
 * {@link #getTimeString()}、{@link #isDaytime()}、{@link #tick()}、
 * {@link #setTotalMinutes(int)}）为 D 模块 P0 文档 §一 与验收规范 §41 所需的
 * 具体辅助方法，与甲文档不冲突（甲文档仅要求上述六个存在，不禁止扩展）。
 *
 * <p><b>决策 D14：</b>不提供 {@code getWorldTime()}。A 侧时间字段统一为 {@code long}，
 * 由 {@code getGameDay() * 24 + getGameHour()} 适配计算游戏小时（plantWorldTime），
 * 避免 D 侧暴露 {@code LocalDateTime} 造成跨模块时间类型耦合。
 *
 * <p>时间换算：1 现实分钟 = 1 游戏小时，24 现实分钟 = 1 游戏日（规则 §5.1）。
 * 本接口是全局唯一时间源，禁止在 Service 层直接使用
 * {@code System.currentTimeMillis()}（规则 §8）。
 */
public interface GameClock {

    // ===== 甲文档（规则 §八）规定的接口方法集 =====

    /**
     * 获取现实世界时间（用于离线时长计算，规则 §八/§九）。
     *
     * @return 当前现实时间
     */
    LocalDateTime getRealTime();

    /**
     * 获取当前游戏日（从 1 开始）。
     *
     * <p>决策 D14：A 侧 {@code plantWorldTime} 由
     * {@code getGameDay() * 24 + getGameHour()} 适配计算。
     *
     * @return 游戏日
     */
    int getGameDay();

    /**
     * 获取当前小时（0~23）。
     *
     * @return 小时
     */
    int getGameHour();

    /**
     * 推进一个正式在线时间单位（当前为 1 游戏分钟，规则 §八/§九）。
     *
     * <p>与 {@link #tick()} 等价，为甲文档规定的命名。
     */
    void advance();

    /**
     * 计算离线时长（现实世界分钟数，规则 §八/§九）。
     *
     * <p>无已保存现实时间基准时返回 0；正式运行由实现依据持久化的
     * {@code logoutRealTime / last_real_time} 与 {@link #getRealTime()} 计算整现实分钟。
     *
     * @return 离线现实分钟数
     */
    long calculateOfflineDuration();

    /**
     * 获取上次真实存档/退出时间。新游戏或旧档没有记录时返回 {@code null}。
     *
     * <p>该值只用于 {@link #calculateOfflineDuration()} 的离线时长计算；
     * 由 E 持久化层从 {@code world_state.last_real_time} 恢复，不参与游戏世界时间推进。
     */
    LocalDateTime getLastRealTime();

    /**
     * 恢复/更新上次真实存档时间。传 {@code null} 表示尚无离线计算基准。
     *
     * @param lastRealTime 上次真实存档/退出时间
     */
    void setLastRealTime(LocalDateTime lastRealTime);

    // ===== D 模块 P0 文档 §一 与验收规范 §41 所需的具体方法 =====

    /**
     * 获取从第 1 天 00:00 起累计的总分钟数。
     *
     * @return 累计总分钟数
     */
    int getTotalMinutes();

    /**
     * 获取当前分钟（0~59）。
     *
     * @return 分钟
     */
    int getGameMinute();

    /**
     * 返回格式化的时间，如 "06:30"。
     *
     * @return HH:mm 格式时间字符串
     */
    String getTimeString();

    /**
     * 判断是否为白天（06:00~18:00，规则 §5.1）。
     *
     * @return 白天返回 true
     */
    boolean isDaytime();

    /**
     * 推进一个正式在线时间单位（当前为 1 游戏分钟）。
     *
     * <p>只负责累计总分钟数，不触发跨天结算逻辑（跨天结算由 Service 层负责，验收规范 §81）。
     */
    void tick();

    /**
     * 设置总分钟数（存档恢复，验收规范 §41）。
     *
     * @param totalMinutes 总分钟数
     */
    void setTotalMinutes(int totalMinutes);
}
