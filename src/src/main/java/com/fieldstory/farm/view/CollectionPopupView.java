package com.fieldstory.farm.view;

import com.fieldstory.farm.controller.CollectionController;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.stage.Popup;
import javafx.stage.Screen;

import java.util.Objects;

/** P4 图鉴浮层：每次打开刷新，并自动避让屏幕右边界。 */
public final class CollectionPopupView extends Popup {

    private final CollectionView content;

    public CollectionPopupView(CollectionController controller) {
        content = new CollectionView(Objects.requireNonNull(controller, "controller 不能为空"));
        getContent().add(content);
        setAutoHide(true);
    }

    public void toggleBelow(Node owner) {
        if (isShowing()) {
            hide();
            return;
        }
        content.refresh();
        var bounds = owner.localToScreen(owner.getBoundsInLocal());
        if (bounds == null) {
            return;
        }
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = 560;
        double x = Math.max(screen.getMinX() + 12,
                Math.min(bounds.getMinX(), screen.getMaxX() - width - 12));
        show(owner, x, bounds.getMaxY() + 6);
    }

    public CollectionView getCollectionView() { return content; }
}
