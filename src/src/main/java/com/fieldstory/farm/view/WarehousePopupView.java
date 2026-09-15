package com.fieldstory.farm.view;

import com.fieldstory.farm.controller.DecorationController;
import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.economy.DecorationPlacementResult;
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
import javafx.stage.Popup;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * B 模块 P1 装饰仓库弹窗。
 */
public final class WarehousePopupView
        extends Popup {

    private final DecorationController controller;

    private final Consumer<Decoration> onSelect;

    private final VBox listBox =
            new VBox(6);

    private final Label messageLabel =
            new Label();

    public WarehousePopupView(
            DecorationController controller,
            Consumer<Decoration> onSelect) {

        this.controller =
                Objects.requireNonNull(
                        controller,
                        "controller"
                );

        this.onSelect =
                Objects.requireNonNull(
                        onSelect,
                        "onSelect"
                );

        Label title =
                new Label(
                        "我的装饰"
                );

        title.getStyleClass().add("section-title");
        messageLabel.getStyleClass().add("hint-text");

        messageLabel.setWrapText(
                true
        );

        ScrollPane scroll =
                new ScrollPane(
                        listBox
                );

        scroll.setFitToWidth(
                true
        );

        scroll.setPrefHeight(
                360
        );

        scroll.getStyleClass().add("transparent-scroll");

        VBox root =
                new VBox(
                        8,
                        title,
                        scroll,
                        messageLabel
                );

        root.setPadding(
                new Insets(12)
        );

        root.setPrefWidth(
                340
        );

        root.setPrefHeight(
                440
        );

        root.getStyleClass().addAll("popup-panel", "warehouse-panel");
        UiTheme.apply(root);

        getContent().add(
                root
        );

        setAutoHide(
                true
        );

        refresh();
    }

    public void refresh() {

        listBox.getChildren()
                .clear();

        if (
                controller
                        .getOwnedDecorations()
                        .isEmpty()
        ) {

            Label empty =
                    new Label(
                            "（仓库暂无装饰）"
                    );

            empty.getStyleClass().add("hint-text");

            listBox.getChildren()
                    .add(empty);

            return;
        }

        for (
                Decoration decoration :
                controller.getOwnedDecorations()
        ) {

            listBox.getChildren()
                    .add(
                            row(decoration)
                    );
        }
    }

    private HBox row(
            Decoration decoration) {

        DecorationType type =
                decoration
                        .getDecorationType();

        Node icon =
                icon(type);

        Label name =
                new Label(
                        type.getDisplayName()
                                + (
                                decoration.isPlaced()
                                        ? "（已放置）"
                                        : "（仓库）"
                        )
                );

        name.getStyleClass().add("normal-text");

        HBox.setHgrow(
                name,
                Priority.ALWAYS
        );

        Button action =
                new Button(
                        decoration.isPlaced()
                                ? "收回"
                                : "选择"
                );

        action.setPrefSize(
                60,
                28
        );

        action.getStyleClass().addAll("primary-button", "small-button");

        action.setOnAction(
                e -> {

                    if (
                            decoration.isPlaced()
                    ) {

                        DecorationPlacementResult result =
                                controller.removeFromFarm(
                                        decoration
                                );

                        messageLabel.setText(
                                DecorationController.messageFor(
                                        result
                                )
                        );

                        refresh();

                    } else {

                        onSelect.accept(
                                decoration
                        );

                        messageLabel.setText(
                                "已选择"
                                        + type.getDisplayName()
                                        + "，请点击外围装饰区放置"
                        );

                        hide();
                    }
                }
        );

        HBox row =
                new HBox(
                        8,
                        icon,
                        name,
                        action
                );

        row.setAlignment(
                Pos.CENTER_LEFT
        );

        row.setPadding(
                new Insets(3)
        );
        row.getStyleClass().add("shop-row");

        return row;
    }

    private Node icon(
            DecorationType type) {

        String path =
                "/assets/decoration/"
                        + type.getAssetFileName();

        ImageView view =
                BImageAssets.view(
                        path,
                        40,
                        40
                );

        if (view != null) {
            return view;
        }

        Label placeholder =
                new Label("?");

        placeholder.setPrefSize(
                40,
                40
        );

        placeholder.setAlignment(
                Pos.CENTER
        );

        placeholder.getStyleClass().add("icon-placeholder");

        return placeholder;
    }

    public void toggleBelow(
            Node owner) {

        if (isShowing()) {
            hide();
            return;
        }

        refresh();

        var bounds =
                owner.localToScreen(
                        owner.getBoundsInLocal()
                );

        if (bounds != null) {

            show(
                    owner,
                    bounds.getMinX() - 240,
                    bounds.getMaxY() + 4
            );
        }
    }
}