package com.fieldstory.farm.view;

import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.stage.Popup;
import javafx.stage.Screen;

import java.util.Objects;

/** P4 展示台改为独立浮层，不再占用 BorderPane.BOTTOM。 */
public final class ShowcasePopupView extends Popup {

    private final ShowcaseView view;

    public ShowcasePopupView(ShowcaseView view) {
        this.view = Objects.requireNonNull(view, "view");
        UiTheme.apply(view);
        getContent().add(view);
        setAutoHide(true);
    }

    public boolean toggleBelow(Node owner) {
        if (isShowing()) {
            hide();
            return false;
        }
        if (owner == null) {
            return false;
        }
        var bounds = owner.localToScreen(owner.getBoundsInLocal());
        if (bounds == null) {
            return false;
        }
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = view.prefWidth(-1) > 0 ? view.prefWidth(-1) : 560;
        double x = Math.min(bounds.getMinX(), screen.getMaxX() - width - 12);
        x = Math.max(screen.getMinX() + 12, x);
        show(owner, x, bounds.getMaxY() + 6);
        return true;
    }
}
