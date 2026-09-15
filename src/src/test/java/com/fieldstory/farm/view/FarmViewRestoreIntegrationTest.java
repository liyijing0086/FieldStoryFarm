package com.fieldstory.farm.view;

import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.PlotState;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.persistence.FarmStateAdapter;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：读档→渲染整链路对「坏枚举」的健壮性。
 *
 * <p>P1 设计文档 §3 允许 {@code crop.crop_type} 为 NULL，§7 承诺"坏枚举不抛错"；
 * 但适配层 {@link FarmStateAdapter} 对无法识别的枚举只降级为 {@code null}，
 * 若下游 {@link FarmView} 不设防，会在 {@code new FarmView(farm)} 时抛 NPE，
 * 导致「开始游戏」直接失败（实测两条路径：未知 growth_stage / NULL crop_type）。
 *
 * <p>本测试在 JavaFX 线程执行，覆盖真实的「适配层还原 → FarmView 构造」链路。
 */
class FarmViewRestoreIntegrationTest {

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

    /** 构造单块土地快照；坐标为 0-based 全局坐标（中心种植区 2..9）。 */
    private static PlotState plot(int row, int column, String cropType, String growthStage) {
        PlotState plot = new PlotState();
        plot.setRow(row);
        plot.setColumn(column);
        plot.setState("PLANTED");
        plot.setCropUuid(UUID.randomUUID().toString());
        plot.setCropType(cropType);
        plot.setGrowthStage(growthStage);
        plot.setGrowthProgress(50);
        plot.setPlantWorldTime("0");
        plot.setManualWaterCount(0);
        plot.setLastManualWaterGameDay("-1");
        return plot;
    }

    /**
     * 适配层把未知 growth_stage / NULL crop_type 降级为 null 后，
     * FarmView 首轮渲染（buildTiles）不得抛 NPE。
     */
    @Test
    void buildTilesAfterRestoreWithDegradedEnumsDoesNotThrow() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = new BasicFarm();
            GameState state = new GameState(new Player("农夫", 100), 1L);
            state.getPlots().add(plot(2, 2, "WHEAT", "SEEDLING")); // 未知阶段 → 降级 null
            state.getPlots().add(plot(2, 3, null, "GROWING"));      // crop_type NULL（设计允许）

            FarmStateAdapter.restore(state, farm);

            assertDoesNotThrow(() -> new FarmView(farm));
            return null;
        });
    }
}
