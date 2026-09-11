package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicSoil;
import com.fieldstory.farm.service.HarvestResult;
import com.fieldstory.farm.service.PlantingResult;
import com.fieldstory.farm.service.ReclaimResult;
import com.fieldstory.farm.service.WateringResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link FarmViewController} 纯函数测试（UI规范 §12；验收规范 §十五~§二十八）。
 *
 * <p>只测 {@link FarmViewController#actionsFor} 与
 * {@link FarmViewController#actionMessageFor} 两个静态纯函数；
 * 不实例化 FarmViewController/FarmView（JavaFX 节点创建需 GUI 线程，
 * 测试中禁止创建控件，任务约束）。
 */
class FarmViewControllerTest {

    /** 辅助：指定状态的空土地（全局坐标 2,2，中心种植区）。 */
    private static Soil soil(SoilState state) {
        Soil soil = new BasicSoil(2, 2);
        soil.setState(state);
        return soil;
    }

    /** 辅助：PLANTED 土地 + 指定阶段作物（小麦）。 */
    private static Soil plantedSoil(GrowthStage stage) {
        Soil soil = soil(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropType(CropType.WHEAT);
        crop.setGrowthStage(stage);
        soil.setCrop(crop);
        return soil;
    }

    // ==================== actionsFor：EMPTY→开垦 / TILLED→播种 / PLANTED→浇水 / MATURE→收获 ====================

    @Test
    void actionsForEmptyIsReclaim() {
        assertEquals(List.of(FarmAction.RECLAIM),
                FarmViewController.actionsFor(soil(SoilState.EMPTY)));
    }

    @Test
    void actionsForTilledIsPlant() {
        assertEquals(List.of(FarmAction.PLANT),
                FarmViewController.actionsFor(soil(SoilState.TILLED)));
    }

    @Test
    void actionsForPlantedIsWater() {
        assertEquals(List.of(FarmAction.WATER),
                FarmViewController.actionsFor(plantedSoil(GrowthStage.SEED)));
    }

    @Test
    void actionsForMatureIsHarvest() {
        assertEquals(List.of(FarmAction.HARVEST),
                FarmViewController.actionsFor(plantedSoil(GrowthStage.MATURE)));
    }

    @Test
    void actionsForLockedIsEmpty() {
        // P0 不产生 LOCKED（验收规范 §十四），无可用动作
        assertEquals(List.of(), FarmViewController.actionsFor(soil(SoilState.LOCKED)));
    }

    @Test
    void actionsForDecorationAreaIsEmpty() {
        assertEquals(List.of(), FarmViewController.actionsFor(null));
    }

    // ==================== actionMessageFor：各结果枚举映射（设计文档 D13） ====================

    @Test
    void actionMessageForReclaimResults() {
        assertEquals("开垦成功", FarmViewController.actionMessageFor(ReclaimResult.SUCCESS));
        assertEquals("该格不是空地，无法开垦", FarmViewController.actionMessageFor(ReclaimResult.NOT_EMPTY));
        assertEquals("金币不足，开垦需要5金币", FarmViewController.actionMessageFor(ReclaimResult.NO_GOLD));
    }

    @Test
    void actionMessageForPlantingResults() {
        assertEquals("播种成功", FarmViewController.actionMessageFor(PlantingResult.SUCCESS));
        assertEquals("该格未开垦，无法播种", FarmViewController.actionMessageFor(PlantingResult.NOT_TILLED));
        assertEquals("种子不足，无法播种", FarmViewController.actionMessageFor(PlantingResult.NO_SEED));
    }

    @Test
    void actionMessageForWateringResults() {
        assertEquals("浇水成功", FarmViewController.actionMessageFor(WateringResult.SUCCESS));
        assertEquals("种子阶段还不能浇水", FarmViewController.actionMessageFor(WateringResult.SEED_STAGE));
        assertEquals("今天已经浇过水了", FarmViewController.actionMessageFor(WateringResult.ALREADY_WATERED_TODAY));
        assertEquals("这株作物已经不需要浇水了", FarmViewController.actionMessageFor(WateringResult.WATER_LIMIT_REACHED));
    }

    @Test
    void actionMessageForHarvestResults() {
        // C 模块 BasicHarvestService 结果码 → 文案（D13：枚举不挂文案）
        assertEquals("收获成功", FarmViewController.actionMessageFor(HarvestResult.SUCCESS));
        assertEquals("该格没有可收获的作物", FarmViewController.actionMessageFor(HarvestResult.NOT_PLANTED));
        assertEquals("该格没有可收获的作物", FarmViewController.actionMessageFor(HarvestResult.NO_CROP));
        assertEquals("作物还没成熟", FarmViewController.actionMessageFor(HarvestResult.NOT_MATURE));
    }
}
