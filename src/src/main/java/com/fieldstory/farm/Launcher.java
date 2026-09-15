package com.fieldstory.farm;

import com.fieldstory.farm.factory.GameClockFactory;
import com.fieldstory.farm.view.MainApplication;
import javafx.application.Application;

/**
 * 启动器：与 Application 分离，保证模块化与非模块化环境下均可直接运行。
 *
 * <p>正式默认使用 ×1 时钟。答辩/开发时可显式传入 {@code --demo}，
 * 启用 {@code DemoGameClock ×12}；{@code --formal} 可显式恢复正式模式。</p>
 */
public class Launcher {

    public static void main(String[] args) {
        applyClockModeArgument(args);
        Application.launch(MainApplication.class, args);
    }

    /**
     * 只负责把命令行的运行模式转换成统一系统属性，不创建任何游戏对象。
     * 正式模式仍是默认值；未知参数不会改变时钟模式。
     */
    static void applyClockModeArgument(String[] args) {
        if (args == null) {
            return;
        }
        for (String arg : args) {
            if ("--demo".equalsIgnoreCase(arg)) {
                System.setProperty(GameClockFactory.CLOCK_MODE_PROPERTY, "demo");
                return;
            }
            if ("--formal".equalsIgnoreCase(arg)) {
                System.setProperty(GameClockFactory.CLOCK_MODE_PROPERTY, "formal");
                return;
            }
        }
    }
}
