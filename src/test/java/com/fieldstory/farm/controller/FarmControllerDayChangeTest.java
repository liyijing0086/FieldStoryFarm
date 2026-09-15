package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.view.StatusView;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.fieldstory.farm.util.GameConstants.MINUTES_PER_DAY;
import static com.fieldstory.farm.util.GameConstants.MINUTES_PER_TICK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FarmController} 跨天回调测试（D 模块 P0；验收规范 §3.1）。
 *
 * <p>验证主循环在游戏日递增时恰好触发一次 {@code onDayChanged} 回调
 * （A 模块 GrowthService 的接入点）。
 *
 * <p>说明：FarmController 构造会创建 JavaFX {@code Timeline}，需 JavaFX 工具包。
 * 本测试在 {@link BeforeAll} 中通过 {@link Platform#startup(Runnable)} 初始化工具包，
 * 并在 JavaFX 应用线程上构造对象，以适配无图形界面的 CI 环境
 * （与 {@code StatusViewTest} 同一约束）。
 */
class FarmControllerDayChangeTest {

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
    void onDayChangedFiresExactlyOnceWhenCrossingOneDay() throws InterruptedException {
        int fires = onFxThread(() -> {
            BasicGameClock clock = new BasicGameClock();
            FarmGameModel model = new FarmGameModel(clock);
            StatusView view = new StatusView(model);
            FarmController controller = new FarmController(model, view);

            AtomicInteger count = new AtomicInteger();
            controller.setOnDayChanged(count::incrementAndGet);

            // 正式步长为 1 游戏分钟：把时钟放到日界前 1 分钟，下一 tick 精确跨日。
            clock.setTotalMinutes(MINUTES_PER_DAY - MINUTES_PER_TICK);
            controller.handleTick();
            return count.get();
        });
        assertEquals(1, fires, "跨过 1 个游戏日应恰好触发 1 次回调");
    }

    @Test
    void onDayChangedDoesNotFireWithinSameDay() throws InterruptedException {
        int fires = onFxThread(() -> {
            BasicGameClock clock = new BasicGameClock();
            FarmGameModel model = new FarmGameModel(clock);
            StatusView view = new StatusView(model);
            FarmController controller = new FarmController(model, view);

            AtomicInteger count = new AtomicInteger();
            controller.setOnDayChanged(count::incrementAndGet);

            // 日界前 2 分钟只推进 1 分钟，仍停留在同一天。
            clock.setTotalMinutes(MINUTES_PER_DAY - 2 * MINUTES_PER_TICK);
            controller.handleTick();
            return count.get();
        });
        assertEquals(0, fires, "未跨天不应触发回调");
    }

    @Test
    void onDayChangedFiresOncePerDayAcrossTwoDays() throws InterruptedException {
        int fires = onFxThread(() -> {
            BasicGameClock clock = new BasicGameClock();
            FarmGameModel model = new FarmGameModel(clock);
            StatusView view = new StatusView(model);
            FarmController controller = new FarmController(model, view);

            AtomicInteger count = new AtomicInteger();
            controller.setOnDayChanged(count::incrementAndGet);

            clock.setTotalMinutes(MINUTES_PER_DAY - MINUTES_PER_TICK);
            controller.handleTick();
            clock.setTotalMinutes(2 * MINUTES_PER_DAY - MINUTES_PER_TICK);
            controller.handleTick();
            return count.get();
        });
        assertEquals(2, fires, "跨过 2 个游戏日应触发 2 次回调");
    }

    @Test
    void setOnDayChangedIgnoresNull() throws InterruptedException {
        int fires = onFxThread(() -> {
            BasicGameClock clock = new BasicGameClock();
            FarmGameModel model = new FarmGameModel(clock);
            StatusView view = new StatusView(model);
            FarmController controller = new FarmController(model, view);

            AtomicInteger count = new AtomicInteger();
            controller.setOnDayChanged(count::incrementAndGet);
            controller.setOnDayChanged(null); // 应被忽略，保留原回调

            clock.setTotalMinutes(MINUTES_PER_DAY - MINUTES_PER_TICK);
            controller.handleTick();
            return count.get();
        });
        assertEquals(1, fires, "null 参数应被忽略，原回调仍生效");
    }

    @Test
    void constructorWithOnDayChangedWiresCallback() throws InterruptedException {
        int fires = onFxThread(() -> {
            BasicGameClock clock = new BasicGameClock();
            FarmGameModel model = new FarmGameModel(clock);
            StatusView view = new StatusView(model);

            AtomicInteger count = new AtomicInteger();
            FarmController controller = new FarmController(model, view, count::incrementAndGet);

            clock.setTotalMinutes(MINUTES_PER_DAY - MINUTES_PER_TICK);
            controller.handleTick();
            return count.get();
        });
        assertEquals(1, fires, "构造器注入的回调应在跨天时触发");
    }

    /** 可抛异常的取值函数。 */
    @FunctionalInterface
    private interface FxSupplier<T> {
        T get() throws Exception;
    }
}
