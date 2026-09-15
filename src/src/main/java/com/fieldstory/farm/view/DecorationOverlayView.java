package com.fieldstory.farm.view;

import com.fieldstory.farm.controller.DecorationController;
import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmPlot;
import com.fieldstory.farm.model.economy.DecorationPlacementResult;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * B 模块 P1 装饰覆盖层。
 *
 * <p>不修改 A 的 FarmView：
 * FarmView 为底层，装饰图片为透明覆盖层。
 */
public final class DecorationOverlayView
        extends StackPane {

    private final Farm farm;

    private final FarmView farmView;

    private final DecorationController controller;

    private final Pane decorationLayer =
            new Pane();

    /** P4 交互浮层：始终高于装饰，只承载 FarmView 的动态操作菜单。 */
    private final Pane interactionLayer =
            new Pane();

    private Decoration pendingDecoration;

    private Consumer<String> messageSink =
            message -> {
            };

    public DecorationOverlayView(
            Farm farm,
            FarmView farmView,
            DecorationController controller) {

        this.farm =
                Objects.requireNonNull(
                        farm,
                        "farm"
                );

        this.farmView =
                Objects.requireNonNull(
                        farmView,
                        "farmView"
                );

        this.controller =
                Objects.requireNonNull(
                        controller,
                        "controller"
                );

        setPrefSize(
                FarmView.MAP_PX,
                FarmView.MAP_PX
        );

        setMinSize(
                FarmView.MAP_PX,
                FarmView.MAP_PX
        );

        setMaxSize(
                FarmView.MAP_PX,
                FarmView.MAP_PX
        );

        decorationLayer.setMouseTransparent(
                true
        );

        decorationLayer.setPickOnBounds(
                false
        );

        decorationLayer.setPrefSize(
                FarmView.MAP_PX,
                FarmView.MAP_PX
        );

        interactionLayer.setPrefSize(FarmView.MAP_PX, FarmView.MAP_PX);
        interactionLayer.setPickOnBounds(false);

        getChildren().addAll(
                farmView,
                decorationLayer,
                interactionLayer
        );

        // 解决“播种/浇水菜单被装饰压住”：只提升菜单，不改变 FarmView 业务或坐标系。
        farmView.promoteMenuTo(interactionLayer);

        farmView.addEventFilter(
                MouseEvent.MOUSE_PRESSED,
                this::handleFarmClick
        );

        refresh();
    }

    public void setMessageSink(
            Consumer<String> sink) {

        messageSink =
                sink == null
                        ? message -> {
                }
                        : sink;
    }

    public void selectForPlacement(
            Decoration decoration) {

        pendingDecoration =
                decoration;

        if (
                decoration != null
                        && decoration
                        .getDecorationType()
                        != null
        ) {

            messageSink.accept(
                    "已选择"
                            + decoration
                            .getDecorationType()
                            .getDisplayName()
                            + "，请点击外围装饰区放置"
            );
        }
    }

    public void clearSelection() {

        pendingDecoration =
                null;
    }

    public void refresh() {

        decorationLayer
                .getChildren()
                .clear();

        for (
                Decoration decoration :
                controller
                        .getPlacedDecorations()
        ) {

            render(decoration);
        }
    }

    private void handleFarmClick(
            MouseEvent event) {

        if (
                pendingDecoration == null
        ) {
            return;
        }

        int column =
                (int) (
                        event.getX()
                                / FarmView.TILE_SIZE
                );

        int row =
                (int) (
                        event.getY()
                                / FarmView.TILE_SIZE
                );

        if (
                row < 0
                        || row >= FarmView.MAP_SIZE
                        || column < 0
                        || column >= FarmView.MAP_SIZE
        ) {
            return;
        }

        if (
                farm.getPlotType(
                        row,
                        column
                )
                        != FarmPlot.DECORATION_AREA
        ) {
            return;
        }

        DecorationPlacementResult result =
                controller.place(
                        pendingDecoration,
                        row,
                        column
                );

        messageSink.accept(
                DecorationController.messageFor(
                        result
                )
        );

        if (
                result
                        == DecorationPlacementResult.SUCCESS
        ) {

            pendingDecoration =
                    null;

            refresh();
        }

        event.consume();
    }

    private void render(
            Decoration decoration) {

        DecorationType type =
                decoration.getDecorationType();

        if (type == null) {
            return;
        }

        double baseWidth = type.getWidth() * FarmView.TILE_SIZE;
        double baseHeight = type.getHeight() * FarmView.TILE_SIZE;
        double maxWidth = Math.max(8, baseWidth - 6);
        double maxHeight = Math.max(8, baseHeight - 6);

        String path = "/assets/decoration/" + type.getAssetFileName();
        ImageView view = BImageAssets.view(path, maxWidth, maxHeight);

        double x = decoration.getColumn() * FarmView.TILE_SIZE;
        double y = decoration.getRow() * FarmView.TILE_SIZE;
        double width = maxWidth;
        double height = maxHeight;

        if (view != null && view.getImage() != null) {
            // P4：保持素材原始宽高比、脚底贴格底、水平居中；不再强行拉伸 1.08 倍。
            double imageWidth = Math.max(1, view.getImage().getWidth());
            double imageHeight = Math.max(1, view.getImage().getHeight());
            double scale = Math.min(maxWidth / imageWidth, maxHeight / imageHeight);
            width = Math.max(1, Math.floor(imageWidth * scale));
            height = Math.max(1, Math.floor(imageHeight * scale));
            view.setFitWidth(width);
            view.setFitHeight(height);
            view.setPreserveRatio(false);
            view.setSmooth(false);
            view.setLayoutX(x + (baseWidth - width) / 2.0);
            view.setLayoutY(y + baseHeight - height - 2);
            decorationLayer.getChildren().add(view);
            return;
        }

        /*
         * 图片资源缺失时使用已有土地色进行 fallback，
         * 避免 UI 完全看不到装饰。
         */
        Rectangle fallback =
                new Rectangle(
                        x,
                        y,
                        width,
                        height
                );

        fallback.setFill(
                Color.rgb(
                        0xA9,
                        0x78,
                        0x50,
                        0.55
                )
        );

        fallback.setMouseTransparent(
                true
        );

        decorationLayer
                .getChildren()
                .add(
                        fallback
                );
    }
}