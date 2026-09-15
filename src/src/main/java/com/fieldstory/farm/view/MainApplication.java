package com.fieldstory.farm.view;

import com.fieldstory.farm.config.AppConfig;
import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.util.FxmlUtil;
import com.fieldstory.farm.service.AudioService;
import com.fieldstory.farm.service.impl.BasicAudioService;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * 应用入口：负责加载主界面并显示窗口。
 *
 * <p>场景由 {@link SceneManager} 统一组装（E 场景组装）；关闭窗口时自动存档
 * （验收规范 §四十二：保存当前状态 → 记录世界时间 → 退出）。
 */
public class MainApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        Node menu = FxmlUtil.load(this.getClass(), AppConfig.MAIN_VIEW_FXML).load();
        Scene scene = SceneManager.getInstance().assemble(menu,
                AppConfig.WINDOW_WIDTH, AppConfig.WINDOW_HEIGHT);
        UiTheme.apply(scene);

        AudioService audio = BasicAudioService.shared();
        audio.playBgm(AudioService.Bgm.MENU);
        // 全局按钮点击音效：避免逐个 View 重复接线；具体业务音效仍由业务成功回调触发。
        scene.addEventFilter(ActionEvent.ACTION, event -> {
            if (event.getTarget() instanceof ButtonBase) {
                audio.playSfx(AudioService.Sfx.CLICK);
            }
        });

        stage.setTitle(AppConfig.APP_TITLE);
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    /** 退出/关闭窗口自动存档（验收标准 2：退出即保存，重启恢复到退出瞬间）。 */
    @Override
    public void stop() {
        try {
            GameManager.getInstance().saveAndExit();
        } finally {
            BasicAudioService.shared().shutdown();
        }
    }
}
