package com.fieldstory.farm.view;

import com.fieldstory.farm.controller.ShopController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

/** P1 完整商店弹窗：种子 + 装饰。 */
public final class ShopPopupView extends Popup {

    private final SeedShopView seedShopView;
    private final DecorationShopView decorationShopView;
    private final StackPane contentPane;

    public ShopPopupView(ShopController shopController) {
        seedShopView = new SeedShopView(shopController);
        decorationShopView = new DecorationShopView(shopController);
        contentPane = new StackPane(seedShopView);

        Button seedTab = tab("种子商店");
        Button decorationTab = tab("装饰品商店");
        seedTab.setOnAction(e -> showSeedShop());
        decorationTab.setOnAction(e -> showDecorationShop());

        HBox tabs = new HBox(8, seedTab, decorationTab);
        tabs.setAlignment(Pos.CENTER);
        tabs.setPadding(new Insets(8));

        VBox root = new VBox(8, tabs, contentPane);
        root.setPadding(new Insets(12));
        root.setPrefWidth(360);
        root.setPrefHeight(480);
        root.getStyleClass().addAll("popup-panel", "shop-popup");
        UiTheme.apply(root);

        getContent().add(root);
        setAutoHide(true);
    }

    private Button tab(String text) {
        Button button = new Button(text);
        button.setPrefSize(120, 36);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private void showSeedShop() {
        seedShopView.refresh();
        contentPane.getChildren().setAll(seedShopView);
    }

    private void showDecorationShop() {
        decorationShopView.refresh();
        contentPane.getChildren().setAll(decorationShopView);
    }

    public void toggleBelow(Node owner) {
        if (isShowing()) {
            hide();
            return;
        }

        // 每次重新打开都刷新金币、种子库存和装饰价格/状态，避免关闭期间发生
        // 收获、播种或其他经济变化后仍显示旧数据。
        seedShopView.refresh();
        decorationShopView.refresh();

        var bounds = owner.localToScreen(owner.getBoundsInLocal());
        if (bounds != null) {
            show(owner, bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }
}
