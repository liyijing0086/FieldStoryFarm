package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.model.impl.BasicSoil;
import com.fieldstory.farm.service.HarvestResult;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.service.PlantingResult;
import com.fieldstory.farm.service.ReclaimResult;
import com.fieldstory.farm.service.WateringResult;
import com.fieldstory.farm.service.impl.BasicHarvestService;
import com.fieldstory.farm.service.impl.BasicLandService;
import com.fieldstory.farm.service.impl.BasicPlantingService;
import com.fieldstory.farm.service.impl.BasicWateringService;
import com.fieldstory.farm.testutil.TestEconomyService;
import com.fieldstory.farm.view.FarmView;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FarmViewController} 测试（UI规范 §12；验收规范 §十五~§二十八、§五十四）。
 *
 * <p>多数用例只测 {@link FarmViewController#actionsFor} 与
 * {@link FarmViewController#actionMessageFor} 两个静态纯函数（不创建控件）；
 * P1 铲除执行用例需实例化 FarmViewController/FarmView，在 JavaFX 线程运行
 * （{@code Platform.startup} 模式，同 {@code FarmViewRestoreIntegrationTest}）。
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

    /** P1：枯萎格只给铲除（不给浇水/收获，验收 §五十四）。 */
    @Test
    void actionsForWitheredIsClearWithered() {
        assertEquals(List.of(FarmAction.CLEAR_WITHERED),
                FarmViewController.actionsFor(plantedSoil(GrowthStage.WITHERED)));
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

    // ==================== 铲除执行（FX 线程：WITHERED→TILLED、crop=null） ====================

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX 工具包初始化超时");
    }

    /** 在 JavaFX 应用线程上执行并返回结果。 */
    private static <T> T onFxThread(Supplier<T> supplier) throws InterruptedException {
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ref.set(supplier.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX 任务执行超时");
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return ref.get();
    }

    /**
     * P1：点击枯萎格 → 菜单只含"铲除"按钮 → 点击后 Soil=TILLED、crop=null，
     * 且铲除免费不扣金币（验收 §五十四；规则 §16.5）。
     */
    @Test
    void clearWitheredButtonTillsSoilAndRemovesCrop() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = new BasicFarm();
            Soil soil = farm.getSoil(2, 2);
            soil.setState(SoilState.PLANTED);
            Crop crop = new BasicCrop();
            crop.setCropType(CropType.WHEAT);
            crop.setGrowthStage(GrowthStage.WITHERED);
            soil.setCrop(crop);

            final TestEconomyService economy = new TestEconomyService();
            LandService landService = new BasicLandService(economy);
            GameClock clock = new BasicGameClock();
            FarmViewController controller = new FarmViewController(
                    farm,
                    landService,
                    new BasicPlantingService(economy, clock),
                    new BasicWateringService(),
                    new BasicHarvestService(economy, landService),
                    clock);
            FarmView view = controller.getView();

            // 点击 (2,2) 格 → 选中并弹出操作菜单
            Rectangle tile = view.getChildren().stream()
                    .filter(Rectangle.class::isInstance)
                    .map(Rectangle.class::cast)
                    .filter(r -> r.getX() == 2 * FarmView.TILE_SIZE && r.getY() == 2 * FarmView.TILE_SIZE)
                    .findFirst()
                    .orElseThrow();
            tile.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, true, false, false, null));

            // 菜单只含"铲除"一个按钮（不给浇水/收获）
            List<Button> menuButtons = view.getChildren().stream()
                    .filter(VBox.class::isInstance)
                    .map(VBox.class::cast)
                    .flatMap(menu -> menu.getChildren().stream())
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .collect(Collectors.toList());
            assertEquals(1, menuButtons.size());
            assertEquals("铲除", menuButtons.get(0).getText());
            menuButtons.get(0).fire();

            assertEquals(SoilState.TILLED, soil.getState());
            assertNull(soil.getCrop());
            assertEquals(0, economy.getGold());   // 铲除免费（规则 §16.5），不扣金币
            return null;
        });
    }
}
