package com.fieldstory.farm.view;

import com.fieldstory.farm.controller.DecorationController;
import com.fieldstory.farm.controller.ShopController;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.util.Objects;

/**
 * B 模块 P1 顶栏经营入口。
 *
 * <p>由 E 装配进现有 TOP HBox，避免直接修改 D 的 StatusView。
 */
public final class BusinessToolbarView extends HBox {

    private static final String WAREHOUSE_ICON =
            "/assets/icon/warehouse_button.png";

    private static final String SHOP_ICON =
            "/assets/icon/shop_button.png";

    private final Button warehouseButton =
            createIconButton(
                    WAREHOUSE_ICON,
                    "仓库"
            );

    private final Button shopButton =
            createIconButton(
                    SHOP_ICON,
                    "商店"
            );

    public BusinessToolbarView(
            ShopController shopController,
            DecorationController decorationController,
            DecorationOverlayView overlayView) {

        Objects.requireNonNull(
                shopController,
                "shopController"
        );

        Objects.requireNonNull(
                decorationController,
                "decorationController"
        );

        Objects.requireNonNull(
                overlayView,
                "overlayView"
        );

        WarehousePopupView warehouse =
                new WarehousePopupView(
                        decorationController,
                        overlayView::selectForPlacement
                );

        ShopPopupView shop =
                new ShopPopupView(
                        shopController
                );

        setSpacing(6);
        setAlignment(Pos.CENTER_RIGHT);
        getStyleClass().add("business-toolbar");

        getChildren().addAll(
                warehouseButton,
                shopButton
        );

        warehouseButton.setOnAction(
                e -> warehouse.toggleBelow(
                        warehouseButton
                )
        );

        shopButton.setOnAction(
                e -> shop.toggleBelow(
                        shopButton
                )
        );

        shopController.addOnDecorationPurchased(
                () -> {
                    if (warehouse.isShowing()) {
                        warehouse.refresh();
                    }
                }
        );

        decorationController.addOnChanged(
                () -> {
                    overlayView.refresh();

                    if (warehouse.isShowing()) {
                        warehouse.refresh();
                    }
                }
        );
    }

    private static Button createIconButton(
            String path,
            String fallbackText) {

        Button button =
                new Button();

        button.setPrefSize(44, 44);
        button.setMinSize(44, 44);
        button.setMaxSize(44, 44);

        button.setAccessibleText(
                fallbackText
        );

        button.setTooltip(
                new javafx.scene.control.Tooltip(
                        fallbackText
                )
        );

        button.getStyleClass().addAll("primary-button", "icon-button");

        ImageView icon =
                BImageAssets.view(
                        path,
                        30,
                        30
                );

        if (icon != null) {
            button.setGraphic(icon);
        } else {
            button.setText(
                    fallbackText
            );

            button.getStyleClass().add("hint-text");
        }

        return button;
    }
}