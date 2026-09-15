package com.fieldstory.farm.view;

import javafx.scene.Parent;
import javafx.scene.Scene;

import java.net.URL;

/**
 * P4 统一样式入口。
 *
 * <p>只负责把 {@code /css/style.css} 安装到 Scene / Popup 根节点，不承载业务状态。
 * 资源缺失时静默降级，避免样式文件问题阻断游戏规则和存档流程。
 */
public final class UiTheme {

    public static final String STYLESHEET = "/css/style.css";

    private UiTheme() {
    }

    public static void apply(Scene scene) {
        if (scene == null) {
            return;
        }
        URL url = UiTheme.class.getResource(STYLESHEET);
        if (url != null && !scene.getStylesheets().contains(url.toExternalForm())) {
            scene.getStylesheets().add(url.toExternalForm());
        }
    }

    public static void apply(Parent parent) {
        if (parent == null) {
            return;
        }
        URL url = UiTheme.class.getResource(STYLESHEET);
        if (url != null && !parent.getStylesheets().contains(url.toExternalForm())) {
            parent.getStylesheets().add(url.toExternalForm());
        }
    }
}
