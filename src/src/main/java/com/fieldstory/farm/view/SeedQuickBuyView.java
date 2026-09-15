package com.fieldstory.farm.view;

import com.fieldstory.farm.controller.SeedQuickBuyController;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.economy.PurchaseResult;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * B模块 P0 种子快捷购买视图。
 *
 * 仅提供：
 * - 当前金币
 * - 三种种子库存
 * - 每种种子购买1颗入口
 * - 购买结果提示
 *
 * 本类不是P1完整ShopView。
 */
public final class SeedQuickBuyView extends VBox {

    private static final double PANEL_WIDTH = 180.0;
    private static final double BUTTON_WIDTH = 120.0;
    private static final double BUTTON_HEIGHT = 36.0;

    private final SeedQuickBuyController controller;

    private final Label goldLabel =
            new Label();

    private final Label messageLabel =
            new Label();

    private final Map<CropType, Label>
            seedCountLabels =
            new EnumMap<>(CropType.class);

    public SeedQuickBuyView(
            SeedQuickBuyController controller) {

        this.controller =
                Objects.requireNonNull(
                        controller,
                        "controller cannot be null"
                );

        configurePanel();
        buildContent();
        refresh();
    }

    private void configurePanel() {

        setSpacing(10);
        setPadding(new Insets(12));
        setAlignment(Pos.TOP_CENTER);

        setPrefWidth(PANEL_WIDTH);
        setMinWidth(PANEL_WIDTH);

        getStyleClass().addAll("panel", "seed-quick-buy");
        UiTheme.apply(this);
    }

    private void buildContent() {

        Label title =
                new Label("种子快捷购买");
        title.getStyleClass().add("section-title");

        goldLabel.getStyleClass().add("normal-text");
        messageLabel.getStyleClass().add("hint-text");
        messageLabel.setWrapText(true);

        getChildren().add(title);
        getChildren().add(goldLabel);

        for (CropType type :
                CropType.values()) {

            getChildren().add(
                    createSeedSection(type)
            );
        }

        getChildren().add(messageLabel);
    }

    private VBox createSeedSection(
            CropType type) {

        Label nameLabel =
                new Label(
                        type.getDisplayName()
                                + "种子"
                );

        nameLabel.getStyleClass().add("normal-text");

        Label countLabel =
                new Label();

        countLabel.getStyleClass().add("normal-text");

        seedCountLabels.put(
                type,
                countLabel
        );

        /*
         * 价格直接读取CropType。
         * 不在View维护第二份10/15/20价格表。
         */
        Button buyButton =
                new Button(
                        "购买 "
                                + type.getSeedPrice()
                                + "金币"
                );

        configureButton(buyButton);

        buyButton.setOnAction(
                event -> buy(type)
        );

        VBox box =
                new VBox(
                        4,
                        nameLabel,
                        countLabel,
                        buyButton
                );

        box.setAlignment(Pos.CENTER);

        return box;
    }

    private void configureButton(
            Button button) {

        button.setPrefSize(
                BUTTON_WIDTH,
                BUTTON_HEIGHT
        );

        button.setMinSize(
                BUTTON_WIDTH,
                BUTTON_HEIGHT
        );

        button.getStyleClass().add("primary-button");
    }

    private void buy(CropType type) {

        PurchaseResult result =
                controller.buyOneSeed(type);

        messageLabel.setText(
                SeedQuickBuyController
                        .messageFor(
                                result,
                                type
                        )
        );

        /*
         * 无论成功或失败都从Service重新读取状态，
         * View不自行推算金币/库存。
         */
        refresh();
    }

    /**
     * 从Controller重新读取当前真实经济状态。
     */
    public void refresh() {

        goldLabel.setText(
                "金币："
                        + controller.getGold()
        );

        for (CropType type :
                CropType.values()) {

            Label label =
                    seedCountLabels.get(type);

            if (label != null) {
                label.setText(
                        "库存："
                                + controller
                                .getSeedCount(type)
                );
            }
        }
    }

    public String getGoldText() {
        return goldLabel.getText();
    }

    public String getMessageText() {
        return messageLabel.getText();
    }
}