package com.fieldstory.farm.view;

import com.fieldstory.farm.controller.ShopController;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.economy.DecorationPurchaseResult;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * B 模块 P1 装饰商店内容视图。
 */
public final class DecorationShopView extends VBox {

    private final ShopController controller;

    private final Label goldLabel =
            new Label();

    private final Label messageLabel =
            new Label();

    private final VBox listBox =
            new VBox(6);

    public DecorationShopView(
            ShopController controller) {

        this.controller =
                Objects.requireNonNull(
                        controller,
                        "controller"
                );

        setSpacing(10);
        setPadding(
                new Insets(12)
        );

        setAlignment(
                Pos.TOP_LEFT
        );

        setPrefWidth(360);
        setMinWidth(360);

        getStyleClass().addAll("panel", "shop-root");
        UiTheme.apply(this);

        Label title =
                new Label(
                        "装饰品商店"
                );

        title.getStyleClass().add("section-title");

        goldLabel.getStyleClass().add("normal-text");

        messageLabel.getStyleClass().add("hint-text");

        messageLabel.setWrapText(
                true
        );

        for (DecorationType type :
                DecorationType.values()) {

            listBox.getChildren()
                    .add(
                            createRow(type)
                    );
        }

        ScrollPane scroll =
                new ScrollPane(
                        listBox
                );

        scroll.setFitToWidth(
                true
        );

        scroll.setPrefHeight(
                340
        );

        scroll.getStyleClass().add("transparent-scroll");

        VBox.setVgrow(
                scroll,
                Priority.ALWAYS
        );

        getChildren().addAll(
                title,
                goldLabel,
                scroll,
                messageLabel
        );

        refresh();
    }

    private HBox createRow(
            DecorationType type) {

        Node icon =
                decorationIcon(type);

        VBox info =
                new VBox(2);

        Label name =
                new Label(
                        type.getDisplayName()
                );

        name.getStyleClass().add("normal-text");

        Label price =
                new Label(
                        type.getPrice()
                                + " 金币"
                );

        price.getStyleClass().add("hint-text");

        info.getChildren().addAll(
                name,
                price
        );

        HBox.setHgrow(
                info,
                Priority.ALWAYS
        );

        Button buy =
                new Button("购买");

        buy.setPrefSize(
                72,
                36
        );

        buy.getStyleClass().add("primary-button");

        buy.setOnAction(
                e -> {

                    DecorationPurchaseResult result =
                            controller.buyDecoration(
                                    type,
                                    1
                            );

                    messageLabel.setText(
                            ShopController.messageFor(
                                    result,
                                    type
                            )
                    );

                    refresh();
                }
        );

        HBox row =
                new HBox(
                        8,
                        icon,
                        info,
                        buy
                );

        row.setAlignment(
                Pos.CENTER_LEFT
        );

        row.setPadding(
                new Insets(4)
        );
        row.getStyleClass().add("shop-row");

        return row;
    }

    private Node decorationIcon(
            DecorationType type) {

        String path =
                "/assets/decoration/"
                        + type.getAssetFileName();

        ImageView view =
                BImageAssets.view(
                        path,
                        48,
                        48
                );

        if (view != null) {
            return view;
        }

        Label placeholder =
                new Label("?");

        placeholder.setPrefSize(
                48,
                48
        );

        placeholder.setAlignment(
                Pos.CENTER
        );

        placeholder.getStyleClass().add("icon-placeholder");

        return placeholder;
    }

    public void refresh() {

        goldLabel.setText(
                "金币："
                        + controller.getGold()
        );
    }

    public String getMessageText() {

        return messageLabel.getText();
    }
}