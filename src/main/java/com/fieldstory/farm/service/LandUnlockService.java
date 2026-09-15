package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.economy.LandUnlockResult;

import java.util.OptionalInt;

/**
 * B 模块 P3 土地解锁服务。
 *
 * <p>职责严格限定为 LOCKED → EMPTY：读取配置价格、检查金币、扣除金币并解锁。
 * EMPTY → TILLED 仍属于 A 模块 LandService，本服务不得代替开垦逻辑。
 *
 * <p>本服务不负责确认弹窗、不执行 SQLite 保存；成功后的 UI 刷新与 saveNow()
 * 由 Controller/E 装配层处理。
 */
public interface LandUnlockService {

    /**
     * 读取当前 LOCKED 土地的配置价格。
     * 非 LOCKED、null 或未配置价格时返回 empty。
     */
    OptionalInt getUnlockPrice(Soil soil);

    /**
     * 当前土地是否满足“LOCKED + 有价格配置 + 金币足够”。
     */
    boolean canUnlock(Soil soil);

    /**
     * 执行一次 LOCKED → EMPTY 解锁事务。
     *
     * <p>成功顺序固定为：先扣金币，再将 SoilState 改为 EMPTY。
     */
    LandUnlockResult unlock(Soil soil);
}
