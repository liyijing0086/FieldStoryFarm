package com.fieldstory.farm.factory;

import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.model.impl.DemoGameClock;

import java.util.Locale;

/**
 * 运行时 GameClock 装配工厂。
 *
 * <p>正式默认永远返回 {@link BasicGameClock}，从而保持
 * “1 现实分钟 = 1 游戏小时”。只有显式开启答辩/开发演示模式时
 * 才返回 {@link DemoGameClock}（×12）。</p>
 *
 * <p>开启 Demo 的推荐方式：</p>
 * <pre>
 * VM options: -Dfsf.clock.mode=demo
 * </pre>
 * 也支持环境变量 {@code FSF_CLOCK_MODE=demo}。
 * 未设置、空值或无法识别的值一律安全降级为正式模式。
 */
public final class GameClockFactory {

    public static final String CLOCK_MODE_PROPERTY = "fsf.clock.mode";
    public static final String CLOCK_MODE_ENV = "FSF_CLOCK_MODE";

    private GameClockFactory() {
    }

    /** 根据 JVM 属性 / 环境变量创建当前运行模式的时钟。 */
    public static GameClock createConfiguredClock() {
        String configured = System.getProperty(CLOCK_MODE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(CLOCK_MODE_ENV);
        }
        return create(configured);
    }

    /**
     * 按模式字符串创建时钟，主要供装配层与测试复用。
     * 只有 {@code demo} / {@code x12} / {@code 12} 会进入演示模式。
     */
    public static GameClock create(String mode) {
        return isDemoMode(mode) ? new DemoGameClock() : new BasicGameClock();
    }

    /** 是否是显式的 ×12 演示模式。 */
    public static boolean isDemoMode(String mode) {
        if (mode == null) {
            return false;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("demo")
                || normalized.equals("x12")
                || normalized.equals("12");
    }
}
