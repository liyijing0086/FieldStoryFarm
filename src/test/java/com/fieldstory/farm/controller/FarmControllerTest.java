package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.model.impl.BasicSoil;
import com.fieldstory.farm.service.GrowthService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.fieldstory.farm.util.GameConstants.GAME_DAYS_PER_TICK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FarmController} 主循环成长协调测试（D 模块 P0；验收规范 §3.1）。
 *
 * <p>只测纯函数 {@link FarmController#advanceCrops(Farm, GrowthService, double)}：
 * D 负责"什么时候推进"，A 负责"怎么成长"。
 * 不实例化 FarmController（构造会创建 JavaFX Timeline，需 GUI 线程，
 * 与 {@code FarmViewControllerTest} 同一约束）。
 */
class FarmControllerTest {

    /** 记录调用参数的成长服务桩：验证 D 只做遍历与转发，不重复成长公式。 */
    private static final class RecordingGrowthService implements GrowthService {

        final List<Crop> grown = new ArrayList<>();
        final List<Double> elapsed = new ArrayList<>();
        final List<Double> weatherRates = new ArrayList<>();

        @Override
        public double calculateGrowthDelta(Crop crop, double elapsedGameDays) {
            return 0;
        }

        @Override
        public void applyGrowth(Crop crop, double elapsedGameDays) {
            grown.add(crop);
            elapsed.add(elapsedGameDays);
        }

        @Override
        public void applyGrowth(Crop crop, double elapsedGameDays, double weatherRate) {
            grown.add(crop);
            elapsed.add(elapsedGameDays);
            weatherRates.add(weatherRate);
        }
    }

    /** 辅助：在指定坐标放一块已播种（PLANTED）土地。 */
    private static Soil plantedSoil(Farm farm, int row, int column) {
        Soil soil = farm.getSoil(row, column);
        soil.setState(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropType(CropType.WHEAT);
        soil.setCrop(crop);
        return soil;
    }

    @Test
    void advanceCropsGrowsEveryPlantedCrop() {
        Farm farm = new BasicFarm();
        Soil a = plantedSoil(farm, 2, 2);
        Soil b = plantedSoil(farm, 3, 5);
        RecordingGrowthService growth = new RecordingGrowthService();

        FarmController.advanceCrops(farm, growth, GAME_DAYS_PER_TICK);

        assertEquals(2, growth.grown.size());
        assertTrue(growth.grown.contains(a.getCrop()));
        assertTrue(growth.grown.contains(b.getCrop()));
    }

    @Test
    void advanceCropsSkipsEmptySoils() {
        Farm farm = new BasicFarm();
        plantedSoil(farm, 2, 2);
        RecordingGrowthService growth = new RecordingGrowthService();

        FarmController.advanceCrops(farm, growth, GAME_DAYS_PER_TICK);

        // 64 块土地中只有 1 块播种，其余 EMPTY（crop == null）必须跳过
        assertEquals(1, growth.grown.size());
    }

    @Test
    void advanceCropsForwardsElapsedGameDaysUnchanged() {
        Farm farm = new BasicFarm();
        plantedSoil(farm, 2, 2);
        RecordingGrowthService growth = new RecordingGrowthService();

        FarmController.advanceCrops(farm, growth, 0.5);

        assertEquals(1, growth.elapsed.size());
        assertEquals(0.5, growth.elapsed.get(0));
    }

    @Test
    void advanceCropsWithNullFarmIsNoOp() {
        RecordingGrowthService growth = new RecordingGrowthService();

        FarmController.advanceCrops(null, growth, GAME_DAYS_PER_TICK);

        assertTrue(growth.grown.isEmpty());
    }

    @Test
    void advanceCropsWithNullGrowthServiceIsNoOp() {
        Farm farm = new BasicFarm();
        plantedSoil(farm, 2, 2);

        // 不抛异常即通过（P0 早期装配前 GrowthService 未注入）
        FarmController.advanceCrops(farm, null, GAME_DAYS_PER_TICK);
    }

    @Test
    void advanceCropsWithNullSoilsIsNoOp() {
        Farm emptyFarm = new Farm() {
            @Override
            public com.fieldstory.farm.model.FarmPlot getPlotType(int row, int column) {
                return null;
            }

            @Override
            public Soil getSoil(int row, int column) {
                return null;
            }

            @Override
            public List<Soil> getSoils() {
                return null;
            }
        };
        RecordingGrowthService growth = new RecordingGrowthService();

        FarmController.advanceCrops(emptyFarm, growth, GAME_DAYS_PER_TICK);

        assertTrue(growth.grown.isEmpty());
    }

    @Test
    void advanceCropsSkipsNullSoilEntries() {
        List<Soil> soils = new ArrayList<>();
        soils.add(null);
        Soil planted = new BasicSoil(2, 2);
        planted.setState(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropType(CropType.WHEAT);
        planted.setCrop(crop);
        soils.add(planted);

        Farm farm = new Farm() {
            @Override
            public com.fieldstory.farm.model.FarmPlot getPlotType(int row, int column) {
                return null;
            }

            @Override
            public Soil getSoil(int row, int column) {
                return null;
            }

            @Override
            public List<Soil> getSoils() {
                return soils;
            }
        };
        RecordingGrowthService growth = new RecordingGrowthService();

        FarmController.advanceCrops(farm, growth, GAME_DAYS_PER_TICK);

        assertEquals(1, growth.grown.size());
        assertEquals(crop, growth.grown.get(0));
    }

    // ==================== P1：WeatherRate 传参（验收规范 §四十九） ====================

    @Test
    void advanceCropsForwardsWeatherRateToGrowthService() {
        Farm farm = new BasicFarm();
        plantedSoil(farm, 2, 2);
        RecordingGrowthService growth = new RecordingGrowthService();

        FarmController.advanceCrops(farm, growth, GAME_DAYS_PER_TICK, 1.5);

        assertEquals(1, growth.weatherRates.size());
        assertEquals(1.5, growth.weatherRates.get(0));
    }

    @Test
    void advanceCropsForwardsWeatherRateForEveryCrop() {
        Farm farm = new BasicFarm();
        plantedSoil(farm, 2, 2);
        plantedSoil(farm, 3, 5);
        RecordingGrowthService growth = new RecordingGrowthService();

        FarmController.advanceCrops(farm, growth, GAME_DAYS_PER_TICK, 2.0);

        assertEquals(2, growth.weatherRates.size());
        assertEquals(2.0, growth.weatherRates.get(0));
        assertEquals(2.0, growth.weatherRates.get(1));
    }

    @Test
    void advanceCropsTwoArgOverloadDefaultsWeatherRateToP0() {
        Farm farm = new BasicFarm();
        plantedSoil(farm, 2, 2);
        RecordingGrowthService growth = new RecordingGrowthService();

        // 2 参重载（P0 兼容）应默认传 WEATHER_RATE_P0 = 1.0
        FarmController.advanceCrops(farm, growth, GAME_DAYS_PER_TICK);

        assertEquals(1, growth.weatherRates.size());
        assertEquals(1.0, growth.weatherRates.get(0));
    }

    @Test
    void advanceCropsWithNullFarmAndWeatherRateIsNoOp() {
        RecordingGrowthService growth = new RecordingGrowthService();

        FarmController.advanceCrops(null, growth, GAME_DAYS_PER_TICK, 2.0);

        assertTrue(growth.grown.isEmpty());
    }
}
