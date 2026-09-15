package com.fieldstory.farm.view;

import com.fieldstory.farm.controller.OfflineLogController;
import com.fieldstory.farm.model.DailyOfflineLog;
import com.fieldstory.farm.model.OfflineLog;
import com.fieldstory.farm.model.OfflineSimulationResult;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.Objects;
import java.util.Optional;

/**
 * B 模块 P2 离线日志弹窗。
 *
 * <p>只负责展示 {@link OfflineLogController} 生成的玩家可读日志，不执行世界模拟、
 * 不访问 DAO、不修改 Crop/Player。P2 规则要求 EffectiveOfflineDuration &gt; 0 时
 * 在回到游戏后显示一次“离开期间农场发生了什么”，并按游戏日分组。
 */
public final class OfflineLogPopupView extends Popup {

    private final OfflineLogController controller;
    private final VBox daysBox = new VBox(10);
    private boolean shownForCurrentStartup;

    public OfflineLogPopupView(OfflineLogController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");

        Label title = new Label("离开期间农场发生了什么");
        title.getStyleClass().add("section-title");

        ScrollPane scroll = new ScrollPane(daysBox);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);
        scroll.getStyleClass().add("transparent-scroll");

        VBox root = new VBox(12, title, scroll);
        root.setPadding(new Insets(14));
        root.setPrefWidth(420);
        root.setPrefHeight(460);
        root.getStyleClass().addAll("popup-panel", "offline-panel");
        UiTheme.apply(root);

        getContent().add(root);
        setAutoHide(true);
    }

    /**
     * 按 P2 规则尝试显示一次离线日志。
     *
     * @param owner  用于定位弹窗的 JavaFX 节点
     * @param result 本次离线模拟结果
     * @return 本次确实显示了弹窗时返回 true；无有效离线时间或本启动已显示则返回 false
     */
    public boolean showOnce(Node owner, OfflineSimulationResult result) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(result, "result");

        if (shownForCurrentStartup) {
            return false;
        }

        Optional<OfflineLog> log = controller.buildLog(result);
        if (log.isEmpty()) {
            return false;
        }

        render(log.orElseThrow());

        var bounds = owner.localToScreen(owner.getBoundsInLocal());
        if (bounds == null) {
            return false;
        }

        show(owner, bounds.getMinX(), bounds.getMaxY() + 4);
        shownForCurrentStartup = true;
        return true;
    }

    /** 当前应用启动周期内是否已经展示过离线日志。 */
    public boolean hasShownForCurrentStartup() {
        return shownForCurrentStartup;
    }

    /**
     * 仅供重新装载存档/测试场景开启新启动周期时重置“一次展示”状态。
     * 普通关闭弹窗不会重置，从而避免同一次启动重复弹出。
     */
    public void resetForNextStartup() {
        hide();
        shownForCurrentStartup = false;
        daysBox.getChildren().clear();
    }

    private void render(OfflineLog log) {
        daysBox.getChildren().clear();

        if (log.days().isEmpty()) {
            Label empty = new Label("离开期间没有需要特别记录的变化。");
            empty.setWrapText(true);
            empty.getStyleClass().add("hint-text");
            daysBox.getChildren().add(empty);
            return;
        }

        for (DailyOfflineLog day : log.days()) {
            Label dayTitle = new Label("第" + day.gameDay() + "日");
            dayTitle.getStyleClass().add("subsection-title");

            VBox lines = new VBox(4);
            for (String text : day.lines()) {
                Label line = new Label(text);
                line.setWrapText(true);
                line.getStyleClass().add("normal-text");
                lines.getChildren().add(line);
            }

            VBox group = new VBox(5, dayTitle, lines);
            group.setPadding(new Insets(8));
            group.getStyleClass().add("offline-day-card");
            daysBox.getChildren().add(group);
        }
    }
}
