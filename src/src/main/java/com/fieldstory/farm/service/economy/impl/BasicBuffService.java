package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.BuffSnapshot;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.service.BuffService;
import com.fieldstory.farm.service.DecorationService;
import com.fieldstory.farm.service.SetService;

import java.util.List;
import java.util.Objects;

/**
 * B 模块装饰 / 套装 Buff 默认实现。
 *
 * <p>P1 负责装饰 Buff；P3 在同一成长倍率通道中加入自然之息 SetBonus。
 * 最终成长倍率：
 * <pre>
 * 1
 * + AdjacentBonus
 * + GlobalBonus
 * + CropSpecificBonus
 * + SetBonus
 * </pre>
 * 并继续遵守成长倍率最大 1.50 的上限。
 *
 * <p>丰收之魂的售价倍率和传奇之光的传奇概率加成不塞进
 * {@link BuffSnapshot}：它们分别属于 SetPriceRate 与 LegendarySetBonus，
 * 调用方应通过 {@link SetService#getPriceSetRate()} 和
 * {@link SetService#getLegendarySetBonusPercent()} 获取。
 */
public class BasicBuffService implements BuffService {

    private static final double MAX_GROWTH_RATE = 1.50;

    private final DecorationService decorationService;
    private final SetService setService;

    /**
     * P1/P2 兼容构造：没有 P3 SetService 时 SetBonus 视为 0。
     */
    public BasicBuffService(DecorationService decorationService) {
        this(decorationService, null);
    }

    /**
     * P3 构造：同时消费装饰 Buff 与当前激活的套装 Buff。
     */
    public BasicBuffService(
            DecorationService decorationService,
            SetService setService) {

        this.decorationService = Objects.requireNonNull(
                decorationService,
                "decorationService"
        );
        this.setService = setService;
    }

    @Override
    public BuffSnapshot getSnapshot(int row, int column, CropType cropType) {
        List<Decoration> placed = decorationService.getPlacedDecorations();

        double adjacentBonus = sunflowerBonus(placed, row, column);
        double globalBonus = 0.0;
        double cropSpecificBonus = 0.0;
        double setBonus = setService == null
                ? 0.0
                : setService.getGrowthSetBonus();

        int qualityBonus = 0;
        double priceBonus = 0.0;
        double witherMultiplier = 1.0;
        double wateringMultiplier = 1.0;
        double fertilizerMultiplier = 1.0;

        // 文档没有规定同类型 D05~D14 的重复叠加算法。
        // 为避免擅自制造无限叠加，本实现对这些类型按“是否至少放置一个”生效一次；
        // D01 按规则明确的 +5%/个、最多 +15% 单独处理。
        if (contains(placed, DecorationType.BIG_TREE)) {
            globalBonus += 0.03;
        }
        if (contains(placed, DecorationType.GOLDEN_FOUNTAIN)) {
            globalBonus += 0.05;
        }
        if (cropType == CropType.WHEAT
                && contains(placed, DecorationType.WHEAT_WATCHER)) {
            cropSpecificBonus += 0.10;
        }
        if (cropType == CropType.CORN
                && contains(placed, DecorationType.CORN_HARVEST)) {
            cropSpecificBonus += 0.10;
        }
        if (cropType == CropType.CARROT
                && contains(placed, DecorationType.CARROT_FIELD)) {
            cropSpecificBonus += 0.10;
        }

        if (contains(placed, DecorationType.RAINBOW_FOUNTAIN)) {
            qualityBonus += 10;
        }
        if (contains(placed, DecorationType.HARVEST_GODDESS)) {
            qualityBonus += 5;
            priceBonus += 0.15;
        }
        if (contains(placed, DecorationType.GOLDEN_THRONE)) {
            priceBonus += 0.10;
        }
        if (contains(placed, DecorationType.STONE_LANTERN)) {
            witherMultiplier = 0.70;
        }
        if (contains(placed, DecorationType.ROSE_BED)) {
            wateringMultiplier = 1.10;
        }
        if (contains(placed, DecorationType.SMALL_FOUNTAIN)) {
            fertilizerMultiplier = 1.20;
        }

        double growthRate = Math.min(
                MAX_GROWTH_RATE,
                1.0
                        + adjacentBonus
                        + globalBonus
                        + cropSpecificBonus
                        + setBonus
        );

        // 此处只表达 DecorationPriceRate。
        // 丰收之魂的 SetPriceRate 必须作为独立乘区由 SetService 提供。
        double priceRate = 1.0 + priceBonus;

        return new BuffSnapshot(
                growthRate,
                qualityBonus,
                priceRate,
                witherMultiplier,
                wateringMultiplier,
                fertilizerMultiplier
        );
    }

    private double sunflowerBonus(
            List<Decoration> placed,
            int row,
            int column) {

        int adjacentSunflowers = 0;

        for (Decoration decoration : placed) {
            if (decoration.getDecorationType()
                    != DecorationType.SUNFLOWER) {
                continue;
            }

            int dr = Math.abs(
                    decoration.getRow() - row
            );
            int dc = Math.abs(
                    decoration.getColumn() - column
            );

            if (dr <= 1
                    && dc <= 1
                    && !(dr == 0 && dc == 0)) {
                adjacentSunflowers++;
            }
        }

        return Math.min(
                adjacentSunflowers * 0.05,
                0.15
        );
    }

    private boolean contains(
            List<Decoration> placed,
            DecorationType type) {

        return placed.stream()
                .anyMatch(
                        decoration ->
                                decoration.getDecorationType() == type
                );
    }
}
