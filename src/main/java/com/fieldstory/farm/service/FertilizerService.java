package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.item.Inventory;

/**
 * 施肥服务接口（C 模块 品质与传说域，P1 施肥系统；验收规范 §六十三）。
 *
 * <p>规则（规则文档 §二十六）：
 * <ul>
 *   <li>允许阶段：SPROUT / GROWING；</li>
 *   <li>每株每天最多 1 次；</li>
 *   <li>每株生命周期最多 3 次；</li>
 *   <li>每次消耗 1 肥料（经 B 的 {@link Inventory#consume} 扣减）；</li>
 *   <li>效果：成长速度 +15%/次（每株最多 +45%）、品质评分 +8/次
 *       （最多 +24，经 C 的 QualityService 施肥分生效）。</li>
 * </ul>
 *
 * <p>P4 收口口径：{@link Crop} 是“当前活着的作物运行态”唯一来源，必须保存
 * {@code fertilizerCount / lastFertilizedGameDay}；{@link CropMemory} 同步保存同一事实，
 * 用于收获后的品质、传奇条件与生命故事。两者不是两套规则：Crop 驱动成长与当日限制，
 * Memory 负责永久档案。
 */
public interface FertilizerService {

    /** 每株生命周期施肥上限：3 次（规则文档 §二十六） */
    int MAX_FERTILIZE_PER_LIFE = 3;

    /** 每次消耗肥料数量：1（规则文档 §二十六） */
    int FERTILIZER_COST = 1;

    /**
     * 对指定作物执行一次施肥。校验顺序：阶段 → 每日限制 → 生命周期限制 → 库存；
     * 全部通过才扣库存，并同时更新 Crop 运行态与 CropMemory 永久事实。
     */
    FertilizeResult fertilize(Crop crop, CropMemory memory, Inventory inventory, long gameDay);

    /**
     * 正式成长链使用的施肥成长 bonus：{@code crop.fertilizerCount × 0.15}，最多 0.45。
     */
    double fertilizerGrowthRate(Crop crop);

    /**
     * 兼容品质/旧测试的 Memory 查询入口；正式在线/离线成长链不再依赖它。
     */
    double fertilizerGrowthRate(CropMemory memory);
}
