package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;

/**
 * 成长服务接口（A 模块设计文档 §8.3）。
 *
 * <p>P0 成长公式（验收规范 §二十四）：
 * {@code BaseDailyProgress × ElapsedGameDays × (1 + 浇水加成)}；
 * 天气/装饰/事件 Rate 在 P0 固定 1.0，公式中省略，
 * 对应系统仅为注释占位、禁止在 P0 引入（验收规范 §二十四）。
 *
 * <p>P1 升级（A 模块设计文档 §6）：{@code applyGrowth} 与
 * {@code calculateGrowthDelta} 各加 3 参重载（第三参 weatherRate，
 * 验收规范 §四十九），默认委托 2 参版本（weatherRate=1.0），P0 完全兼容；
 * DecorationRate P1 不加，预留第 4 参扩展（A 模块设计文档 §6.4）。
 *
 * <p>本接口为纯函数服务，不依赖 GameClock（接口层禁止 import GameClock）：
 * elapsedGameDays 由调用方按"经过游戏小时 ÷ 24"折算传入，
 * 必须支持非整日成长（验收规范 §二十五）。
 *
 * <p>浇水加成经 WateringService.calculateWaterGrowthBonus 同层调用获取，
 * 避免公式重复（A 模块设计文档 §8.3）。
 */
public interface GrowthService {

    /**
     * 计算单次成长增量（纯函数，不修改作物状态）。
     *
     * <p>公式（验收规范 §二十四；规则文档 §二十七）：
     * {@code getBaseDailyProgress() × elapsedGameDays
     * × (1 + WateringService.calculateWaterGrowthBonus(crop))}。
     *
     * @param crop            目标作物
     * @param elapsedGameDays 经过的游戏天数（经过游戏小时 ÷ 24，验收规范 §二十五）
     * @return 成长增量
     */
    double calculateGrowthDelta(Crop crop, double elapsedGameDays);

    /**
     * 应用成长：累加成长值 → Math.min 封顶 100 → 按阈值更新阶段。
     *
     * <p>封顶：{@code progress = min(100, progress + delta)}，
     * 禁止出现 105%、123% 等超值继续增长（验收规范 §三十）。
     *
     * <p>阶段阈值（验收规范 §二十二）：≥100 MATURE、≥50 GROWING、
     * ≥20 SPROUT、其余 SEED。
     *
     * @param crop            目标作物
     * @param elapsedGameDays 经过的游戏天数
     */
    void applyGrowth(Crop crop, double elapsedGameDays);

    /**
     * 计算单次成长增量（3 参重载，纯函数，不修改作物状态）。
     *
     * <p>P1 公式（验收规范 §四十九）：
     * {@code getBaseDailyProgress() × elapsedGameDays × weatherRate
     * × (1 + WateringService.calculateWaterGrowthBonus(crop))}。
     *
     * <p>默认委托 2 参版本（weatherRate=1.0），P0 完全兼容
     * （A 模块设计文档 §6.2）。
     *
     * @param crop            目标作物
     * @param elapsedGameDays 经过的游戏天数（经过游戏小时 ÷ 24，验收规范 §二十五）
     * @param weatherRate     天气成长倍率（验收规范 §四十九，P0 固定 1.0）
     * @return 成长增量
     */
    default double calculateGrowthDelta(Crop crop, double elapsedGameDays,
            double weatherRate) {
        return calculateGrowthDelta(crop, elapsedGameDays);
    }

    /**
     * 应用成长（3 参重载）：累加成长值 → Math.min 封顶 100 → 按阈值更新阶段。
     *
     * <p>P1 守卫（A 模块设计文档 §6.3）：WITHERED 阶段作物直接返回，
     * 不再成长，防止 stageOf 把枯萎作物重算回正常阶段。
     *
     * <p>默认委托 2 参版本（weatherRate=1.0），P0 完全兼容。
     *
     * @param crop            目标作物
     * @param elapsedGameDays 经过的游戏天数
     * @param weatherRate     天气成长倍率（验收规范 §四十九）
     */
    default void applyGrowth(Crop crop, double elapsedGameDays,
            double weatherRate) {
        applyGrowth(crop, elapsedGameDays);
    }
}
