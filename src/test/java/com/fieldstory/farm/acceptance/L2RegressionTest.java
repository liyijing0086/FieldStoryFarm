package com.fieldstory.farm.acceptance;

import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.WeatherState;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.model.impl.BasicEventState;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.model.impl.BasicWeatherState;
import com.fieldstory.farm.service.EventService;
import com.fieldstory.farm.service.WeatherService;
import com.fieldstory.farm.service.impl.BasicEventService;
import com.fieldstory.farm.service.impl.BasicWeatherService;
import com.fieldstory.farm.util.RandomProvider;
import com.fieldstory.farm.view.StatusView;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D 模块 L2 回归测试（P0~P2 世界环境：天气显示 / 事件状态 / 固定种子复现）。
 *
 * <p>依据《D模块 P3 跨模块接口约定文档》L2 验收项：
 * <ul>
 *   <li>L2.2 天气显示：状态栏天气「图标 + 显示名」正确；</li>
 *   <li>L2.3 事件状态：事件抽取、持续、到期关闭正确；</li>
 *   <li>L2.4 固定种子复现：天气序列、事件序列可复现。</li>
 * </ul>
 *
 * <p>本测试把上述「手工回归」固化为可重复执行的自动化回归：既断言服务层规则，
 * 又断言 {@link StatusView} 实际渲染出的文本，避免仅测纯函数而漏掉显示层回归。
 *
 * <p>说明：{@link StatusView} 继承 JavaFX {@code HBox}，构造 Label 需 JavaFX 工具包；
 * 本测试在 {@link BeforeAll} 中初始化工具包，并在 JavaFX 应用线程上构造/读取控件，
 * 以适配无图形界面的 CI 环境（与 {@code StatusViewTest} 同一约束）。
 */
class L2RegressionTest {

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

    // ------------------------------------------------------------------
    // L2.2 天气显示：状态栏天气「图标 + 显示名」正确
    // ------------------------------------------------------------------

    @Test
    void l2_2_weatherDisplayShowsIconAndNameForEveryType() throws InterruptedException {
        for (WeatherType type : WeatherType.values()) {
            String text = onFxThread(() -> {
                FarmGameModel model = new FarmGameModel();
                BasicWeatherState state = new BasicWeatherState(type, 1);
                BasicWeatherService service = new BasicWeatherService(state);
                StatusView view = new StatusView(model, service, state);
                return view.getWeatherText();
            });
            WeatherService service = new BasicWeatherService(new BasicWeatherState());
            assertEquals(service.getIcon(type) + " " + service.getDisplayName(type), text,
                    "天气显示应为「图标 + 显示名」：" + type);
        }
    }

    @Test
    void l2_2_weatherDisplayRefreshesAfterRoll() throws InterruptedException {
        String text = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            WeatherState state = model.getWeatherState();
            WeatherService service = model.getWeatherService();
            StatusView view = new StatusView(model, service, state);
            // 固定种子后滚动天气，再刷新状态栏，显示应随状态变化
            RandomProvider.setSeed(20240601L);
            WeatherType rolled = service.rollDailyWeather(2);
            view.update();
            return view.getWeatherText() + "|" + rolled;
        });
        String[] parts = text.split("\\|");
        WeatherService service = new BasicWeatherService(new BasicWeatherState());
        WeatherType rolled = WeatherType.valueOf(parts[1]);
        assertEquals(service.getIcon(rolled) + " " + service.getDisplayName(rolled), parts[0],
                "滚动天气后状态栏应显示新天气");
    }

    // ------------------------------------------------------------------
    // L2.3 事件状态：抽取、持续、到期关闭
    // ------------------------------------------------------------------

    @Test
    void l2_3_eventRollWritesStateAndDisplays() throws InterruptedException {
        String text = onFxThread(() -> {
            FarmGameModel model = new FarmGameModel();
            EventState state = model.getEventState();
            EventService service = model.getEventService();
            RandomProvider.setSeed(20240601L);
            EventType rolled = service.rollDailyEvent(1);
            StatusView view = new StatusView(model);
            return view.getEventText() + "|" + rolled;
        });
        String[] parts = text.split("\\|");
        EventType rolled = EventType.valueOf(parts[1]);
        EventService service = new BasicEventService(new BasicEventState(), new BasicGameClock());
        String expected = rolled == EventType.NONE
                ? "无事件"
                : service.getIcon(rolled) + " " + service.getDisplayName(rolled);
        assertEquals(expected, parts[0], "抽取事件后状态栏应显示对应事件");
    }

    @Test
    void l2_3_eventLastsThenExpires() {
        BasicEventState state = new BasicEventState();
        BasicEventService service = new BasicEventService(state, new BasicGameClock());
        // 流星夜持续 24 游戏小时
        state.setEventType(EventType.METEOR_SHOWER);
        state.setStartWorldTime(100L);
        state.setEndWorldTime(100L + EventType.METEOR_SHOWER.getDurationHours());

        assertTrue(service.isEventActive(100L), "起始时刻应处于活动期");
        assertTrue(service.isEventActive(123L), "到期前应处于活动期");
        assertFalse(service.isEventActive(124L), "到期时刻应不再活动");

        service.expireIfNeeded(123L);
        assertEquals(EventType.METEOR_SHOWER, state.getEventType(), "未到期不应关闭");
        service.expireIfNeeded(124L);
        assertEquals(EventType.NONE, state.getEventType(), "到期应关闭事件");
        assertEquals(0L, state.getStartWorldTime());
        assertEquals(0L, state.getEndWorldTime());
    }

    @Test
    void l2_3_instantEventDoesNotPersist() {
        BasicEventState state = new BasicEventState();
        BasicEventService service = new BasicEventService(state, new BasicGameClock());
        state.setEventType(EventType.ANIMAL_VISIT);
        state.setStartWorldTime(10L);
        state.setEndWorldTime(10L);
        assertFalse(service.isEventActive(10L), "即时事件不持续（规则文档 §五十）");
    }

    // ------------------------------------------------------------------
    // L2.4 固定种子复现：天气序列、事件序列可复现
    // ------------------------------------------------------------------

    @Test
    void l2_4_weatherSequenceReproducibleWithSameSeed() {
        WeatherType[] first = rollWeatherSequence(20240601L, 30);
        WeatherType[] second = rollWeatherSequence(20240601L, 30);
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], second[i], "相同种子应产生相同天气序列（规则文档 §九十）");
        }
    }

    @Test
    void l2_4_eventSequenceReproducibleWithSameSeed() {
        EventType[] first = rollEventSequence(12345L, 30);
        EventType[] second = rollEventSequence(12345L, 30);
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], second[i], "相同种子应产生相同事件序列（规则文档 §九十）");
        }
    }

    @Test
    void l2_4_differentSeedsProduceDifferentSequences() {
        WeatherType[] a = rollWeatherSequence(1L, 30);
        WeatherType[] b = rollWeatherSequence(2L, 30);
        boolean differs = false;
        for (int i = 0; i < a.length && !differs; i++) {
            if (a[i] != b[i]) {
                differs = true;
            }
        }
        assertTrue(differs, "不同种子应产生不同天气序列（否则复现验证无意义）");
        assertNotEquals(a[0], null);
    }

    private static WeatherType[] rollWeatherSequence(long seed, int length) {
        RandomProvider.setSeed(seed);
        BasicWeatherService service = new BasicWeatherService(new BasicWeatherState());
        WeatherType[] seq = new WeatherType[length];
        for (int i = 0; i < length; i++) {
            seq[i] = service.rollDailyWeather(i + 1);
        }
        return seq;
    }

    private static EventType[] rollEventSequence(long seed, int length) {
        RandomProvider.setSeed(seed);
        BasicEventService service = new BasicEventService(new BasicEventState(), new BasicGameClock());
        EventType[] seq = new EventType[length];
        for (int i = 0; i < length; i++) {
            seq[i] = service.rollDailyEvent(i + 1);
        }
        return seq;
    }

    /** 可抛异常的取值函数。 */
    @FunctionalInterface
    private interface FxSupplier<T> {
        T get() throws Exception;
    }
}
