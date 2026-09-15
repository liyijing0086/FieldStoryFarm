package com.fieldstory.farm.model;

/**
 * 毕业状态（E 模块 P3；验收规范 §一百二十九、规则文档 §七十七）。
 *
 * <p>毕业唯一条件：{@code FarmScore == 147}（满收集）。第一次达到 147 时写入本状态并播放
 * 毕业动画；之后再进入游戏保持「永恒花园」状态（验收规范 §一百二十九）。
 *
 * <p>只保存状态，判定逻辑属 {@code service.GraduationService}（统一 Model 原则）。
 * {@link #getGraduationWorldTime()} / {@link #getGraduationGameDay()} 用 -1 哨兵表示未毕业。
 */
public class GraduationState {

    /** 是否已毕业（首次达到 147 后永久为 true）。 */
    private boolean graduated;

    /** 首次毕业的时刻世界时间（游戏小时）；-1 = 未毕业。 */
    private long graduationWorldTime = -1L;

    /** 首次毕业所在游戏日；-1 = 未毕业。 */
    private long graduationGameDay = -1L;

    public boolean isGraduated() {
        return graduated;
    }

    public void setGraduated(boolean graduated) {
        this.graduated = graduated;
    }

    public long getGraduationWorldTime() {
        return graduationWorldTime;
    }

    public void setGraduationWorldTime(long graduationWorldTime) {
        this.graduationWorldTime = graduationWorldTime;
    }

    public long getGraduationGameDay() {
        return graduationGameDay;
    }

    public void setGraduationGameDay(long graduationGameDay) {
        this.graduationGameDay = graduationGameDay;
    }
}
