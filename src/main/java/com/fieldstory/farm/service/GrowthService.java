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
     * 应用成长（P1 升级：含天气倍率 WeatherRate）。
     *
     * <p><b>跨模块协商点（D ↔ A，验收规范 §四十九）：</b>
     * P1 成长公式升级为
     * {@code BaseDailyProgress × ElapsedGameDays × WeatherRate × OperationRate}，
     * 其中 {@code WeatherRate} 由 D 模块 {@link com.fieldstory.farm.service.WeatherService#getGrowthRate}
     * 提供，公式组装由 A 模块本接口实现（D 不越界）。
     *
     * <p>本方法为<b>向后兼容的过渡重载</b>：默认实现忽略 {@code weatherRate}、
     * 委托给 {@link #applyGrowth(Crop, double)}，保证 A 模块现有实现无需改动即可编译。
     * A 模块（lyj）确认签名后应 override 本方法，将 {@code weatherRate} 纳入公式；
     * 届时 D 侧 {@code FarmController} 已按本签名传参，无需再改。
     *
     * @param crop            目标作物
     * @param elapsedGameDays 经过的游戏天数
     * @param weatherRate     天气成长倍率（D 模块提供：晴 1.0 / 雨 1.5 / 旱 0.5 / 绿雨 2.0）
     */
    default void applyGrowth(Crop crop, double elapsedGameDays, double weatherRate) {
        applyGrowth(crop, elapsedGameDays);
    }
}
