package com.fieldstory.farm.persistence;

import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.service.PlantingService;
import com.fieldstory.farm.service.ReclaimResult;
import com.fieldstory.farm.service.PlantingResult;
import com.fieldstory.farm.service.economy.EconomyService;
import com.fieldstory.farm.service.economy.impl.EconomyServiceImpl;
import com.fieldstory.farm.service.impl.BasicLandService;
import com.fieldstory.farm.service.impl.BasicPlantingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验收标准 4 端到端测试：「游戏退出自动保存 → 进入从数据库读取」对农场同样生效。
 *
 * <p>按 {@code MainController} 的真实装配方式驱动：读档还原 → 操作农场 → 注册存档前回填钩子
 * → {@code saveAndExit()} 落盘 → 用同一数据库文件重启 → 再次还原，校验地块与作物无损。
 */
class FarmPersistenceIntegrationTest {

    @TempDir
    Path tempDir;

    /** 每次「重启」都新建管理器，但指向同一个数据库文件（模拟关掉程序再打开）。 */
    private GameManager managerOn(String dbName) {
        DatabaseService database = new DatabaseService(tempDir.resolve(dbName));
        return new GameManager(new SqliteSaveService(database, null));
    }

    @Test
    void exitAutoSaveThenRestartRestoresFarmPlotsAndCrops() {
        GameManager manager = managerOn("farm.db");
        GameState state = manager.start();

        Farm farm = new BasicFarm();
        FarmStateAdapter.restore(state, farm); // 新档：全部 EMPTY

        // 开垦一块地，并在另一块地种下作物
        farm.getSoil(2, 2).setState(SoilState.TILLED);

        Soil planted = farm.getSoil(3, 4);
        BasicCrop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(CropType.WHEAT);
        crop.setGrowthStage(GrowthStage.SPROUT);
        crop.setGrowthProgress(35.0);
        crop.setPlantWorldTime(3L * 24 + 8);
        crop.setManualWaterCount(1);
        crop.setLastManualWaterGameDay(2);
        planted.setCrop(crop);
        planted.setState(SoilState.PLANTED);

        // 装配层注册的回填钩子：落盘前把运行态写回 GameState
        manager.setBeforeSaveHook(() -> {
            FarmStateAdapter.capture(state, farm);
            state.setGameDay(5L);
        });

        manager.saveAndExit();

        // ---------- 重启 ----------
        GameManager restarted = managerOn("farm.db");
        assertTrue(restarted.hasSavedGame());
        GameState loaded = restarted.start();
        assertEquals(5L, loaded.getGameDay(), "游戏天数应随存档恢复");

        Farm reloaded = new BasicFarm();
        FarmStateAdapter.restore(loaded, reloaded);

        assertEquals(SoilState.TILLED, reloaded.getSoil(2, 2).getState());
        assertNull(reloaded.getSoil(2, 2).getCrop());

        Soil restored = reloaded.getSoil(3, 4);
        assertEquals(SoilState.PLANTED, restored.getState());
        Crop restoredCrop = restored.getCrop();
        assertNotNull(restoredCrop);
        assertEquals(crop.getCropUuid(), restoredCrop.getCropUuid());
        assertEquals(CropType.WHEAT, restoredCrop.getCropType());
        assertEquals(GrowthStage.SPROUT, restoredCrop.getGrowthStage());
        assertEquals(35.0, restoredCrop.getGrowthProgress());
        assertEquals(80L, restoredCrop.getPlantWorldTime());
        assertEquals(1, restoredCrop.getManualWaterCount());
        assertEquals(2L, restoredCrop.getLastManualWaterGameDay());

        // 未操作的地块保持初始状态
        assertEquals(SoilState.EMPTY, reloaded.getSoil(2, 3).getState());
        assertNull(reloaded.getSoil(2, 3).getCrop());
    }

    /**
     * 用真实 A/B 服务驱动（开垦 → 播种），再走一遍「退出保存 → 重启还原」，
     * 复现玩家在界面上的实际操作路径。
     */
    @Test
    void gameplayThroughRealServicesSurvivesRestart() {
        GameManager manager = managerOn("gameplay.db");
        GameState state = manager.start();

        Farm farm = new BasicFarm();
        FarmStateAdapter.restore(state, farm);

        BasicGameClock clock = new BasicGameClock(); // 第 1 天 06:00 → plantWorldTime = 30
        EconomyService economy = new EconomyServiceImpl(state.getPlayer());
        economy.buySeed(CropType.CARROT, 2);
        LandService land = new BasicLandService(economy);
        PlantingService planting = new BasicPlantingService(economy, clock);

        Soil soil = farm.getSoil(5, 6);
        assertEquals(ReclaimResult.SUCCESS, land.reclaim(soil));
        assertEquals(SoilState.TILLED, soil.getState());
        assertEquals(PlantingResult.SUCCESS, planting.plant(soil, CropType.CARROT));
        assertEquals(SoilState.PLANTED, soil.getState());

        manager.setBeforeSaveHook(() -> FarmStateAdapter.capture(state, farm));
        manager.saveAndExit();

        // ---------- 重启 ----------
        GameManager restarted = managerOn("gameplay.db");
        GameState loaded = restarted.start();
        Farm reloaded = new BasicFarm();
        FarmStateAdapter.restore(loaded, reloaded);

        Soil restored = reloaded.getSoil(5, 6);
        assertEquals(SoilState.PLANTED, restored.getState());
        Crop crop = restored.getCrop();
        assertNotNull(crop);
        assertEquals(CropType.CARROT, crop.getCropType());
        assertEquals(GrowthStage.SEED, crop.getGrowthStage());
        assertEquals(30L, crop.getPlantWorldTime(), "播种时刻应随存档恢复");
        assertEquals(-1L, crop.getLastManualWaterGameDay(), "从未浇水的哨兵值应保持");

        // 种子库存（B 模块）也随存档恢复：买 2 颗、播种消耗 1 颗
        assertEquals(1, loaded.getPlayer().getSeedInventory().get(CropType.CARROT));
    }
}
