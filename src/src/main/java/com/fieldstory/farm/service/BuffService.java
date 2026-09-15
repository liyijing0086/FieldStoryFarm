package com.fieldstory.farm.service;

import com.fieldstory.farm.model.BuffSnapshot;
import com.fieldstory.farm.model.CropType;

/** B 模块 P1 装饰 Buff 唯一查询入口。 */
public interface BuffService {

    BuffSnapshot getSnapshot(int row, int column, CropType cropType);

    default double getGrowthRate(int row, int column, CropType cropType) {
        return getSnapshot(row, column, cropType).growthRate();
    }

    default int getQualityScoreBonus(int row, int column, CropType cropType) {
        return getSnapshot(row, column, cropType).qualityScoreBonus();
    }

    default double getPriceRate(int row, int column, CropType cropType) {
        return getSnapshot(row, column, cropType).priceRate();
    }

    default double getWitherProbabilityMultiplier(int row, int column, CropType cropType) {
        return getSnapshot(row, column, cropType).witherProbabilityMultiplier();
    }

    default double getWateringGrowthMultiplier(int row, int column, CropType cropType) {
        return getSnapshot(row, column, cropType).wateringGrowthMultiplier();
    }

    default double getFertilizerGrowthMultiplier(int row, int column, CropType cropType) {
        return getSnapshot(row, column, cropType).fertilizerGrowthMultiplier();
    }
}
