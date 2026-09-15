package com.fieldstory.farm.view;

import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.model.impl.BasicWeatherState;
import com.fieldstory.farm.service.impl.BasicWeatherService;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 StatusView 测试（D 模块 P0 文档 §10.3）。
 *
 * <p>覆盖：update() 不抛异常、时间/游戏日文本正确、天气占位固定。
 *
 * <p>说明：StatusView 继承 JavaFX {@code HBox}，构造 Label 需要 JavaFX 工具包。
 * 本测试在 {@link BeforeAll} 中通过 {@link Platform#startup(Runnable)} 初始化工具包，
 * 并在 JavaFX 应用线程上构造/读取控件，以适配无图形界面的 CI 环境。
 */
class StatusViewTest {

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
    void updateDoesNotThrowAndShowsDayAndTime() throws InterruptedException {
        String[] texts = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            StatusView view = assertDoesNotThrow(() -> new StatusView(model));
            assertNotNull(view.getDayText());
            return new String[] {view.getDayText(), view.getTimeText(), view.getWeatherText()};
        });
        assertEquals("第 1 天", texts[0]);
        assertEquals("\u2600 06:00", texts[1]);
        assertEquals("晴天", texts[2]);
    }

    @Test
    void updateReflectsClockAdvance() throws InterruptedException {
        String time = onFxThread(() -> {
            GameClock clock = new BasicGameClock();
            FarmGameModel model = new FarmGameModel(clock);
            StatusView view = new StatusView(model);
            clock.setTotalMinutes(1080);
            view.update();
            return view.getTimeText();
        });
        assertEquals("\uD83C\uDF19 18:00", time);
    }

    @Test
    void goldLabelShowsPlaceholderWithoutPlayer() throws InterruptedException {
        String gold = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            StatusView view = new StatusView(model);
            return view.getGoldText();
        });
        assertEquals("金币 --", gold);
    }

    @Test
    void goldLabelShowsPlayerGold() throws InterruptedException {
        String gold = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            Player player = new Player("测试", 500);
            StatusView view = new StatusView(model, player);
            return view.getGoldText();
        });
        assertEquals("金币 500", gold);
    }

    @Test
    void goldLabelReflectsPlayerGoldChange() throws InterruptedException {
        String gold = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            Player player = new Player("测试", 500);
            StatusView view = new StatusView(model, player);
            player.setGold(123);
            view.update();
            return view.getGoldText();
        });
        assertEquals("金币 123", gold);
    }

    @Test
    void goldLabelShowsPlaceholderWhenPlayerNull() throws InterruptedException {
        String gold = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            StatusView view = new StatusView(model, (Player) null);
            return view.getGoldText();
        });
        assertEquals("金币 --", gold);
    }

    @Test
    void weatherShowsIconAndDisplayName() throws InterruptedException {
        String weather = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            BasicWeatherState state = new BasicWeatherState(WeatherType.RAIN, 3);
            BasicWeatherService service = new BasicWeatherService(state);
            StatusView view = new StatusView(model, service, state);
            return view.getWeatherText();
        });
        assertEquals("\uD83C\uDF27 雨天", weather);
    }

    @Test
    void weatherFallsBackToSunnyWhenStateNull() throws InterruptedException {
        String weather = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            StatusView view = new StatusView(model, null, null);
            return view.getWeatherText();
        });
        assertEquals("晴天", weather);
    }

    @Test
    void weatherFallsBackToSunnyWhenTypeNull() throws InterruptedException {
        String weather = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            BasicWeatherState state = new BasicWeatherState();
            state.setWeatherType(null);
            BasicWeatherService service = new BasicWeatherService(state);
            StatusView view = new StatusView(model, service, state);
            return view.getWeatherText();
        });
        assertEquals("晴天", weather);
    }

    @Test
    void eventShowsIconAndDisplayName() throws InterruptedException {
        String event = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            model.getEventState().setEventType(EventType.METEOR_SHOWER);
            StatusView view = new StatusView(model);
            return view.getEventText();
        });
        assertEquals("\uD83C\uDF20 流星夜", event);
    }

    @Test
    void eventShowsNoneWhenNoEvent() throws InterruptedException {
        String event = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            StatusView view = new StatusView(model);
            return view.getEventText();
        });
        assertEquals("无事件", event);
    }

    @Test
    void eventFallsBackToNoneWhenTypeNull() throws InterruptedException {
        String event = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            model.getEventState().setEventType(null);
            StatusView view = new StatusView(model);
            return view.getEventText();
        });
        assertEquals("无事件", event);
    }

    @Test
    void eventReflectsStateChangeOnUpdate() throws InterruptedException {
        String event = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            StatusView view = new StatusView(model);
            model.getEventState().setEventType(EventType.RAINBOW_DAY);
            view.update();
            return view.getEventText();
        });
        assertEquals("\uD83C\uDF08 彩虹日", event);
    }

    /** 可抛异常的取值函数。 */
    @FunctionalInterface
    private interface FxSupplier<T> {
        T get() throws Exception;
    }
}
