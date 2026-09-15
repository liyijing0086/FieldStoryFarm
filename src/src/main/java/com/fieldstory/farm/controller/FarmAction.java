package com.fieldstory.farm.controller;

/**
 * 农场地块操作动作枚举。
 *
 * <p>A 模块只负责把土地/作物当前状态映射为“玩家可以发起什么请求”。
 * 真正的业务仍由所属模块 Service 执行：开垦/播种/浇水/铲除归 A，
 * 收获归 C，土地解锁归 B。
 */
public enum FarmAction {

    /** 开垦：EMPTY → TILLED。 */
    RECLAIM,

    /** 播种：TILLED → PLANTED。 */
    PLANT,

    /** 浇水：作物阶段 ∈ {SPROUT, GROWING, MATURE}。 */
    WATER,

    /** 施肥：仅 SPROUT / GROWING，由 C.FertilizerService 执行。 */
    FERTILIZE,

    /**
     * 请求解锁 LOCKED 地块。
     *
     * <p>注意：这只是 A 提供给外部的点击事件出口；A 不读取解锁价格、
     * 不扣金币、也不执行 LOCKED → EMPTY。真正解锁由 B 的
     * LandUnlockController / LandUnlockService 完成。
     */
    REQUEST_UNLOCK,

    /** 收获入口；收获事务归 C 模块。 */
    HARVEST,

    /** 铲除枯萎：WITHERED → TILLED。 */
    CLEAR_WITHERED
}
