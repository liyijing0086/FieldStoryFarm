package com.fieldstory.farm.config;

/**
 * 应用全局配置。
 */
public final class AppConfig {

    /** 应用标题 */
    public static final String APP_TITLE = "FieldStoryFarm";

    /** 主窗口宽度 */
    public static final double WINDOW_WIDTH = 960;

    /** 主窗口高度 */
    public static final double WINDOW_HEIGHT = 640;

    /** 主界面 FXML 资源路径（相对 view 包） */
    public static final String MAIN_VIEW_FXML = "main-view.fxml";

    private AppConfig() {
        // 工具类，禁止实例化
    }
}
