package com.fieldstory.farm.view;

import com.fieldstory.farm.model.GraduationState;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.Duration;

/** P4 毕业弹窗：保持 P3 触发条件不变，只增加可见动画。 */
public final class GraduationPopupView extends Popup {

    private final Label detail = new Label();
    private final VBox root;

    public GraduationPopupView() {
        Label title = new Label("永恒花园");
        title.getStyleClass().add("game-title");
        Label message = new Label("FarmScore 已达到 147/147\n完整收集完成，农场正式毕业！");
        message.setWrapText(true);
        message.getStyleClass().add("normal-text");
        detail.getStyleClass().add("hint-text");

        Button close = new Button("继续经营");
        close.getStyleClass().add("primary-button");
        close.setOnAction(event -> hide());

        root = new VBox(12, title, message, detail, close);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.setPrefWidth(380);
        root.getStyleClass().addAll("panel", "graduation-panel");
        UiTheme.apply(root);
        getContent().add(root);
        setAutoHide(false);
    }

    public boolean showFor(Node owner, GraduationState state) {
        if (owner == null || state == null) {
            return false;
        }
        detail.setText("首次毕业：第 " + state.getGraduationGameDay() + " 游戏日");
        var bounds = owner.localToScreen(owner.getBoundsInLocal());
        if (bounds == null) {
            return false;
        }
        if (isShowing()) {
            hide();
        }
        show(owner,
                bounds.getMinX() + Math.max(0, (bounds.getWidth() - 380) / 2.0),
                bounds.getMinY() + 80);

        root.setOpacity(0);
        root.setScaleX(0.88);
        root.setScaleY(0.88);
        FadeTransition fade = new FadeTransition(Duration.millis(360), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(360), root);
        scale.setFromX(0.88);
        scale.setFromY(0.88);
        scale.setToX(1.0);
        scale.setToY(1.0);
        new ParallelTransition(fade, scale).play();
        return true;
    }
}
