package com.fieldstory.farm.view;

import com.fieldstory.farm.controller.CollectionController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** P4 图鉴呈现：业务数据仍只读 CollectionController，仅重排视觉层级。 */
public final class CollectionView extends VBox {

    private final CollectionController controller;

    private final Label cropLabel = new Label();
    private final Label decorationLabel = new Label();
    private final Label legendaryLabel = new Label();
    private final Label setLabel = new Label();
    private final Label farmScoreLabel = new Label();
    private final Label rankLabel = new Label();
    private final VBox goalBox = new VBox(8);
    private final ProgressBar scoreProgress = new ProgressBar(0);

    public CollectionView(CollectionController controller) {
        this.controller = Objects.requireNonNull(controller, "controller 不能为空");
        getStyleClass().addAll("panel", "collection-root");
        UiTheme.apply(this);
        setSpacing(14);
        setPadding(new Insets(18));
        setPrefSize(560, 430);
        setMaxSize(560, 430);

        Label title = new Label("收集图鉴");
        title.getStyleClass().add("section-title");
        rankLabel.getStyleClass().add("rank-label");
        HBox header = new HBox(12, title, rankLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        farmScoreLabel.getStyleClass().add("score-text");
        scoreProgress.setMaxWidth(Double.MAX_VALUE);
        scoreProgress.getStyleClass().add("farm-score-progress");
        VBox scoreCard = new VBox(6, farmScoreLabel, scoreProgress);
        scoreCard.getStyleClass().add("score-strip");

        GridPane summaries = new GridPane();
        summaries.setHgap(10);
        summaries.setVgap(10);
        addSummaryCard(summaries, cropLabel, "作物", 0, 0);
        addSummaryCard(summaries, decorationLabel, "装饰", 1, 0);
        addSummaryCard(summaries, legendaryLabel, "传说", 0, 1);
        addSummaryCard(summaries, setLabel, "套装", 1, 1);

        Label goalTitle = new Label("下一步目标");
        goalTitle.getStyleClass().add("subsection-title");
        goalBox.getStyleClass().add("goal-list");
        ScrollPane goals = new ScrollPane(goalBox);
        goals.setFitToWidth(true);
        goals.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        goals.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(goals, Priority.ALWAYS);

        getChildren().addAll(header, scoreCard, summaries, goalTitle, goals);
        refresh();
    }

    private static void addSummaryCard(GridPane grid, Label valueLabel, String title, int column, int row) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("summary-title");
        valueLabel.getStyleClass().add("summary-value");
        VBox card = new VBox(3, titleLabel, valueLabel);
        card.getStyleClass().add("summary-card");
        card.setPrefWidth(245);
        grid.add(card, column, row);
    }

    public void refresh() {
        int crop = controller.cropCollected();
        int decoration = controller.decorationCollected();
        int legendary = controller.legendaryCollected();
        int sets = controller.setCollected();
        int score = controller.farmScore();
        int max = controller.maxFarmScore();

        // 保持原测试/调用依赖的精确文本格式。
        cropLabel.setText("作物图鉴  " + crop + "/" + controller.cropTarget());
        decorationLabel.setText("装饰图鉴  " + decoration + "/" + controller.decorationTarget());
        legendaryLabel.setText("传说  " + legendary + "/" + controller.legendaryTarget());
        setLabel.setText("套装  " + sets + "/" + controller.setTarget());
        farmScoreLabel.setText("FarmScore  " + score + "/" + max);
        rankLabel.setText("当前评价  " + controller.currentRankName());
        scoreProgress.setProgress(max <= 0 ? 0 : Math.min(1.0, score / (double) max));

        goalBox.getChildren().clear();
        for (String hint : controller.goalHints()) {
            Label item = new Label(hint);
            item.setWrapText(true);
            item.getStyleClass().add("goal-item");
            goalBox.getChildren().add(item);
        }
    }

    public String cropText() { return cropLabel.getText(); }
    public String decorationText() { return decorationLabel.getText(); }
    public String legendaryText() { return legendaryLabel.getText(); }
    public String setText() { return setLabel.getText(); }
    public String farmScoreText() { return farmScoreLabel.getText(); }
    public String rankText() { return rankLabel.getText(); }

    public List<String> goalTexts() {
        List<String> texts = new ArrayList<>();
        for (Node node : goalBox.getChildren()) {
            if (node instanceof Label label) {
                texts.add(label.getText());
            }
        }
        return texts;
    }
}
