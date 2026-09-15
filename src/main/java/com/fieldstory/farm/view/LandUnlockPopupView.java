package com.fieldstory.farm.view;

import com.fieldstory.farm.controller.LandUnlockController;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.economy.LandUnlockResult;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * B 模块 P3 土地解锁确认弹窗。
 *
 * <p>职责严格停留在 View 层：
 * <ul>
 *   <li>显示 LOCKED 土地坐标与由 {@link LandUnlockController} 提供的配置价格；</li>
 *   <li>让玩家执行“确认购买”；</li>
 *   <li>把确认动作委托给 Controller；</li>
 *   <li>显示结果文案。</li>
 * </ul>
 *
 * <p>本 View 不读取 {@code balance-config.json}、不直接扣金币、不修改 Soil、
 * 不调用 DAO/SaveService。解锁成功后的刷新与保存由 E 装配层通过
 * {@link LandUnlockController#addOnUnlockSucceeded(Runnable)} 注入。
 *
 * <p>颜色只使用 UI 规范主色表及按钮禁用色：#FFF3DD、#8B5E3C、#493526、
 * #A97850、#C28B5A、#CCCCCC。
 */
public final class LandUnlockPopupView extends Popup {

    private final LandUnlockController controller;

    private final Label positionLabel = new Label();
    private final Label priceLabel = new Label();
    private final Label statusLabel = new Label();
    private final Button confirmButton = new Button("确认解锁");
    private final Button cancelButton = new Button("取消");

    private Soil currentSoil;

    public LandUnlockPopupView(LandUnlockController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");

        Label title = new Label("土地扩张");
        title.getStyleClass().add("section-title");
        positionLabel.getStyleClass().add("normal-text");
        priceLabel.getStyleClass().add("normal-text");
        statusLabel.getStyleClass().add("hint-text");
        statusLabel.setWrapText(true);

        styleButton(confirmButton);
        styleButton(cancelButton);

        confirmButton.setOnAction(event -> confirmUnlock());
        cancelButton.setOnAction(event -> hide());

        HBox actions = new HBox(8, confirmButton, cancelButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(
                10,
                title,
                positionLabel,
                priceLabel,
                statusLabel,
                actions
        );
        root.setPadding(new Insets(14));
        root.setPrefWidth(300);
        root.getStyleClass().addAll("popup-panel", "land-unlock-panel");
        UiTheme.apply(root);

        getContent().add(root);
        setAutoHide(true);
    }

    /**
     * 在指定节点下方显示某块 LOCKED 土地的购买确认。
     *
     * @param owner 用于定位 Popup 的 JavaFX 节点
     * @param soil  玩家刚点击的土地
     * @return 成功显示返回 true；节点尚未进入 Scene 时返回 false
     */
    public boolean showFor(Node owner, Soil soil) {
        Objects.requireNonNull(owner, "owner");

        currentSoil = soil;
        refreshPresentation();

        var bounds = owner.localToScreen(owner.getBoundsInLocal());
        if (bounds == null) {
            return false;
        }

        if (isShowing()) {
            hide();
        }

        show(owner, bounds.getMinX(), bounds.getMaxY() + 4);
        return true;
    }

    /** 当前弹窗绑定的土地；仅供装配/调试读取。 */
    public Soil getCurrentSoil() {
        return currentSoil;
    }

    /**
     * 纯函数：价格展示文案。测试无需启动 JavaFX Application Thread。
     */
    public static String priceText(OptionalInt price) {
        if (price == null || price.isEmpty()) {
            return "解锁价格：暂未配置";
        }
        return "解锁价格：" + price.getAsInt() + " 金币";
    }

    /**
     * 纯函数：当前土地是否允许出现“可确认”的业务状态。
     * 真正金币判断仍由 Controller/Service 完成。
     */
    public static boolean isLockedSoil(Soil soil) {
        return soil != null && soil.getState() == SoilState.LOCKED;
    }

    private void confirmUnlock() {
        LandUnlockResult result = controller.unlock(currentSoil);
        statusLabel.setText(LandUnlockController.messageFor(result));

        if (result == LandUnlockResult.SUCCESS) {
            hide();
            return;
        }

        updateConfirmButtonState();
    }

    private void refreshPresentation() {
        if (currentSoil == null) {
            positionLabel.setText("土地：未选择");
            priceLabel.setText(priceText(OptionalInt.empty()));
            statusLabel.setText("请选择需要扩张的 LOCKED 土地。");
            setConfirmDisabled(true);
            return;
        }

        positionLabel.setText(
                "土地：第 " + (currentSoil.getRow() + 1)
                        + " 行，第 " + (currentSoil.getColumn() + 1) + " 列"
        );

        OptionalInt price = controller.getUnlockPrice(currentSoil);
        priceLabel.setText(priceText(price));

        if (!isLockedSoil(currentSoil)) {
            statusLabel.setText("该土地当前不是 LOCKED 状态，无需购买解锁。");
            setConfirmDisabled(true);
            return;
        }

        if (price.isEmpty()) {
            statusLabel.setText("该土地尚未配置解锁价格，不能擅自使用临时正式数值。");
            setConfirmDisabled(true);
            return;
        }

        if (!controller.canUnlock(currentSoil)) {
            statusLabel.setText("金币不足，当前无法解锁这块土地。");
            setConfirmDisabled(true);
            return;
        }

        statusLabel.setText("确认后将扣除金币，并把土地从 LOCKED 变为 EMPTY。");
        setConfirmDisabled(false);
    }

    private void updateConfirmButtonState() {
        boolean enabled = isLockedSoil(currentSoil)
                && controller.getUnlockPrice(currentSoil).isPresent()
                && controller.canUnlock(currentSoil);
        setConfirmDisabled(!enabled);
    }

    private void setConfirmDisabled(boolean disabled) {
        confirmButton.setDisable(disabled);
    }

    private static void styleButton(Button button) {
        button.setMinHeight(36);
        button.getStyleClass().add("primary-button");
    }
}
