package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.service.DailySimulationResult;
import com.fieldstory.farm.service.DaySettlementInput;
import com.fieldstory.farm.service.GrowthRates;
import com.fieldstory.farm.service.WorldSimulationService;
import com.fieldstory.farm.view.StatusView;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.fieldstory.farm.util.GameConstants.MINUTES_PER_DAY;
import static com.fieldstory.farm.util.GameConstants.MINUTES_PER_TICK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一轮“生产世界主链”回归：正式 FarmController 必须把在线成长和跨日日结
 * 都委托给同一个 WorldSimulationService，并把“新成熟 / 日结摘要”通过薄回调暴露给外层。
 */
class FarmControllerWorldChainTest {

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

    private static <T> T onFxThread(FxSupplier<T> supplier) throws InterruptedException {
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

    @Test
    void productionPathUsesWorldSimulationAndPublishesFacts() throws InterruptedException {
        Snapshot snapshot = onFxThread(() -> {
            // 让下一次 1 分钟正式 tick 恰好跨过第 1 天 00:00 边界。
            BasicGameClock clock = new BasicGameClock(MINUTES_PER_DAY - MINUTES_PER_TICK);
            FarmGameModel model = new FarmGameModel(clock);
            model.setFarm(new BasicFarm());

            RecordingWorldSimulationService world = new RecordingWorldSimulationService();
            FarmController controller = new FarmController(
                    model,
                    new StatusView(model),
                    world,
                    (row, column, type) -> 1.0,
                    (row, column, type) -> 1.0);

            AtomicInteger maturedFacts = new AtomicInteger();
            AtomicInteger settlementFacts = new AtomicInteger();
            AtomicInteger refreshes = new AtomicInteger();
            controller.setOnCropsMatured(crops -> maturedFacts.addAndGet(crops.size()));
            controller.setOnDaySettled(result -> settlementFacts.incrementAndGet());
            controller.setOnWorldAdvanced(refreshes::incrementAndGet);

            controller.handleTick();
            return new Snapshot(
                    world.growCalls,
                    world.settleCalls,
                    maturedFacts.get(),
                    settlementFacts.get(),
                    refreshes.get());
        });

        assertEquals(1, snapshot.growCalls(), "每个在线 tick 只能委托一次分段成长");
        assertEquals(1, snapshot.settleCalls(), "跨日时必须委托同一个世界引擎完成日结");
        assertEquals(1, snapshot.maturedFacts(), "新成熟结果必须通过薄回调向外暴露");
        assertEquals(1, snapshot.settlementFacts(), "日结摘要必须通过薄回调向外暴露");
        assertEquals(1, snapshot.refreshes(), "每次世界推进后只触发一次统一刷新出口");
    }

    @Test
    void sameDayTickGrowsButDoesNotSettle() throws InterruptedException {
        Snapshot snapshot = onFxThread(() -> {
            BasicGameClock clock = new BasicGameClock();
            FarmGameModel model = new FarmGameModel(clock);
            model.setFarm(new BasicFarm());

            RecordingWorldSimulationService world = new RecordingWorldSimulationService();
            FarmController controller = new FarmController(
                    model,
                    new StatusView(model),
                    world,
                    null,
                    null);

            AtomicInteger settlementFacts = new AtomicInteger();
            controller.setOnDaySettled(result -> settlementFacts.incrementAndGet());
            controller.handleTick();

            return new Snapshot(
                    world.growCalls,
                    world.settleCalls,
                    0,
                    settlementFacts.get(),
                    0);
        });

        assertEquals(1, snapshot.growCalls(), "同日 tick 仍要按时间差推进成长");
        assertEquals(0, snapshot.settleCalls(), "未跨 00:00 不能提前执行日结");
        assertEquals(0, snapshot.settlementFacts(), "未日结时不能伪造日结摘要");
    }

    private static final class RecordingWorldSimulationService implements WorldSimulationService {
        private int growCalls;
        private int settleCalls;

        @Override
        public List<Crop> growSegment(Farm farm, double gameHours, GrowthRates rates) {
            growCalls++;
            return List.of(new BasicCrop());
        }

        @Override
        public DailySimulationResult settleDay(Farm farm, DaySettlementInput input) {
            settleCalls++;
            return new DailySimulationResult(
                    input.gameDay(),
                    input.weather() == null ? WeatherType.SUNNY : input.weather(),
                    input.eventInEffect() == null ? EventType.NONE : input.eventInEffect(),
                    0,
                    0,
                    0);
        }
    }

    private record Snapshot(int growCalls, int settleCalls, int maturedFacts,
                            int settlementFacts, int refreshes) {
    }

    @FunctionalInterface
    private interface FxSupplier<T> {
        T get() throws Exception;
    }
}
