package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.service.LegendaryCheck;
import com.fieldstory.farm.service.LegendaryService;
import com.fieldstory.farm.util.RandomProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link LegendaryService} 基础实现（C 模块 品质与传说域，P2）。
 *
 * <p>三种传说作物的突破条件全部取自规则文档：
 * <ul>
 *   <li>金色麦穗（§四十二）：WHEAT，干旱≥1、干旱当天主动浇水救援、
 *       主动浇水≥2、Score≥110，基础概率 30%；</li>
 *   <li>彩虹玉米（§四十三）：CORN，绿雨≥1、施肥≥1、Score≥115，基础概率 40%；</li>
 *   <li>巨龙胡萝卜（§四十四）：CARROT，绿雨≥1、主动浇水≥2、Score≥120，基础概率 35%。</li>
 * </ul>
 *
 * <p>最终突破概率（规则文档 §四十五）：
 * BaseChance + 绿雨加成(+5%/次、最大+15%) + 流星夜加成(+10%) + 传奇之光套装加成(默认 0)，
 * 总上限 {@link LegendaryService#MAX_CHANCE} = 80%（验收规范 §一百零一）。
 *
 * <p>传说失败不损失作物，只不发生突破（规则文档 §四十六）；
 * 随机统一经 {@link RandomProvider}（规则文档 §三十九）。
 */
public class BasicLegendaryService implements LegendaryService {

    /** 绿雨突破加成：+5%/次（规则文档 §四十五） */
    private static final int GREEN_RAIN_BONUS = 5;

    /** 绿雨突破加成上限：+15%（规则文档 §四十五） */
    private static final int GREEN_RAIN_BONUS_CAP = 15;

    /** 流星夜突破加成：+10%（规则文档 §四十五） */
    private static final int METEOR_BONUS = 10;

    /** 突破概率与随机数换算的分母：nextDouble()×100 与百分比比较 */
    private static final double PERCENT_BASE = 100.0;

    /** 传奇之光套装加成（%），P2 默认 0（验收规范 §一百：P3 套装系统接入） */
    private int legendarySetBonus;

    @Override
    public LegendaryCheck checkEligibility(Crop crop, CropMemory memory, int qualityScore) {
        Objects.requireNonNull(crop, "作物不能为空");
        Objects.requireNonNull(memory, "生命记忆不能为空");

        CropType cropType = crop.getCropType();
        List<String> unmet = new ArrayList<>();
        boolean eligible = switch (cropType) {
            case WHEAT -> checkWheat(memory, qualityScore, unmet);
            case CORN -> checkCorn(memory, qualityScore, unmet);
            case CARROT -> checkCarrot(memory, qualityScore, unmet);
        };
        return new LegendaryCheck(eligible, LegendaryService.legendaryName(cropType), eligible ? List.of() : unmet);
    }

    /** 金色麦穗条件（规则文档 §四十二）。 */
    private boolean checkWheat(CropMemory memory, int qualityScore, List<String> unmet) {
        if (memory.getDroughtCount() < 1) {
            unmet.add("需至少经历 1 次干旱（当前 " + memory.getDroughtCount() + " 次）");
        }
        if (!memory.isWaterRescueOnDroughtDay()) {
            unmet.add("需在干旱当天主动浇水救援");
        }
        if (memory.getManualWaterCount() < 2) {
            unmet.add("需主动浇水至少 2 次（当前 " + memory.getManualWaterCount() + " 次）");
        }
        if (qualityScore < 110) {
            unmet.add("品质评分需 ≥110（当前 " + qualityScore + "）");
        }
        return unmet.isEmpty();
    }

    /** 彩虹玉米条件（规则文档 §四十三）。 */
    private boolean checkCorn(CropMemory memory, int qualityScore, List<String> unmet) {
        if (memory.getGreenRainCount() < 1) {
            unmet.add("需至少经历 1 场绿雨（当前 " + memory.getGreenRainCount() + " 场）");
        }
        if (memory.getFertilizerCount() < 1) {
            unmet.add("需至少施肥 1 次（当前 " + memory.getFertilizerCount() + " 次）");
        }
        if (qualityScore < 115) {
            unmet.add("品质评分需 ≥115（当前 " + qualityScore + "）");
        }
        return unmet.isEmpty();
    }

    /** 巨龙胡萝卜条件（规则文档 §四十四）。 */
    private boolean checkCarrot(CropMemory memory, int qualityScore, List<String> unmet) {
        if (memory.getGreenRainCount() < 1) {
            unmet.add("需至少经历 1 场绿雨（当前 " + memory.getGreenRainCount() + " 场）");
        }
        if (memory.getManualWaterCount() < 2) {
            unmet.add("需主动浇水至少 2 次（当前 " + memory.getManualWaterCount() + " 次）");
        }
        if (qualityScore < 120) {
            unmet.add("品质评分需 ≥120（当前 " + qualityScore + "）");
        }
        return unmet.isEmpty();
    }

    @Override
    public int baseChance(CropType cropType) {
        Objects.requireNonNull(cropType, "作物类型不能为空");
        return switch (cropType) {
            case WHEAT -> 30;
            case CORN -> 40;
            case CARROT -> 35;
        };
    }

    @Override
    public int legendaryChance(Crop crop, CropMemory memory, int qualityScore) {
        Objects.requireNonNull(crop, "作物不能为空");
        Objects.requireNonNull(memory, "生命记忆不能为空");

        int chance = baseChance(crop.getCropType());
        // 绿雨加成：+5%/次、最大 +15%（规则文档 §四十五）
        chance += Math.min(memory.getGreenRainCount() * GREEN_RAIN_BONUS, GREEN_RAIN_BONUS_CAP);
        // 流星夜加成：+10%（规则文档 §四十五）
        if (memory.getEvents().contains(EventType.METEOR_SHOWER)) {
            chance += METEOR_BONUS;
        }
        // 传奇之光套装加成（P2 默认 0，验收规范 §一百）
        chance += legendarySetBonus;
        return Math.min(chance, MAX_CHANCE);
    }

    @Override
    public boolean rollBreakthrough(Crop crop, CropMemory memory, int qualityScore) {
        if (!checkEligibility(crop, memory, qualityScore).isEligible()) {
            // 条件不满足：不掷骰，直接不突破（验收规范 §九十六）
            return false;
        }
        int chance = legendaryChance(crop, memory, qualityScore);
        return RandomProvider.nextDouble() * PERCENT_BASE < chance;
    }

    @Override
    public void setLegendarySetBonus(int bonusPercent) {
        if (bonusPercent < 0) {
            throw new IllegalArgumentException("套装加成不能为负：" + bonusPercent);
        }
        this.legendarySetBonus = bonusPercent;
    }
}
