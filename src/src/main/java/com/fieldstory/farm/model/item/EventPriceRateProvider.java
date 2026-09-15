package com.fieldstory.farm.model.item;

import com.fieldstory.farm.model.CropType;

/**
 * 事件售价倍率契约（C 模块定义、D 模块实现）。
 *
 * <p>跨模块约束（《C任务跨模块开发约束文档》§四.1）：
 * 神秘商人事件（规则文档 §四十九：随机 1 种作物在活动期间收获出售
 * 最终售价 ×2，持续 12 游戏小时）由 D 模块的 EventService 触发，
 * <b>售价倍率变量存放在 D</b>；C 模块的商店与收获事务在计算出售价格时
 * 读取本接口，C 不自己写任何事件逻辑。
 *
 * <p>默认实现 {@link #NONE} 恒返回 1.0（无事件倍率），
 * D 模块接入后将自己的实现注入 {@code ShopModel} /
 * {@code HarvestTransactionServiceImpl}（P2 集成步骤）。
 */
@FunctionalInterface
public interface EventPriceRateProvider {

    /** 无事件时的默认倍率提供者：恒为 1.0（价格不变）。 */
    EventPriceRateProvider NONE = cropType -> 1.0;

    /**
     * 指定作物类型的当前事件售价倍率。
     *
     * @param cropType 出售作物类型；null 表示与作物类型无关的出售
     * @return 倍率（≥0）；无事件时应返回 1.0。神秘商人目标作物返回 2.0
     *         （规则文档 §四十九；验收规范 §九十二）
     */
    double priceRateFor(CropType cropType);
}
