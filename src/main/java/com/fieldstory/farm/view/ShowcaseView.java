package com.fieldstory.farm.view;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.service.ShowcaseEntry;
import com.fieldstory.farm.service.ShowcaseService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * P4 展示台：固定三种传奇槽位 + 详细故事区。
 * 数据仍完全来自 ShowcaseService/CropMemory，不增加任何收藏状态。
 */
public class ShowcaseView extends VBox {

    private final ShowcaseService showcaseService;
    private final ComboBox<String> selector = new ComboBox<>();
    private final Label countLabel = new Label();
    private final ScrollPane detailScroll = new ScrollPane();
    private final HBox slots = new HBox(10);
    private final List<ShowcaseEntry> entries = new ArrayList<>();
    private final Map<CropType, List<ShowcaseEntry>> entriesByType = new EnumMap<>(CropType.class);

    public ShowcaseView(ShowcaseService showcaseService) {
        this.showcaseService = Objects.requireNonNull(showcaseService, "展示台服务不能为空");
        getStyleClass().addAll("panel", "showcase-root");
        UiTheme.apply(this);
        setSpacing(12);
        setPadding(new Insets(18));
        setPrefSize(620, 470);
        setMaxSize(620, 470);

        Label title = new Label("传奇展示台");
        title.getStyleClass().add("section-title");
        countLabel.getStyleClass().add("normal-text");
        HBox header = new HBox(12, title, countLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        slots.setAlignment(Pos.CENTER);

        selector.setPrefWidth(260);
        selector.getStyleClass().add("showcase-selector");
        selector.setOnAction(event -> renderSelected());

        detailScroll.setFitToWidth(true);
        detailScroll.setPrefViewportHeight(190);
        detailScroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(detailScroll, Priority.ALWAYS);

        getChildren().addAll(header, slots, selector, detailScroll);
        refresh();
    }

    public void refresh() {
        entries.clear();
        entries.addAll(showcaseService.listLegendaryEntries());
        entriesByType.clear();
        for (CropType type : CropType.values()) {
            entriesByType.put(type, new ArrayList<>());
        }
        for (ShowcaseEntry entry : entries) {
            entriesByType.computeIfAbsent(entry.getCropType(), ignored -> new ArrayList<>()).add(entry);
        }

        countLabel.setText("已收录传说记忆：" + entries.size() + " 株");
        rebuildSlots();

        selector.getItems().clear();
        for (ShowcaseEntry entry : entries) {
            selector.getItems().add(selectorLabelFor(entry));
        }
        boolean hasEntries = !entries.isEmpty();
        selector.setManaged(hasEntries);
        selector.setVisible(hasEntries);
        if (hasEntries) {
            selector.getSelectionModel().selectFirst();
            renderSelected();
        } else {
            Label empty = new Label("三座传奇席位正在等待故事。\n收获金色麦穗、彩虹玉米或巨龙胡萝卜后，生命记忆会在这里永久陈列。");
            empty.setWrapText(true);
            empty.getStyleClass().add("showcase-empty");
            detailScroll.setContent(empty);
        }
    }

    private void rebuildSlots() {
        slots.getChildren().clear();
        for (CropType type : CropType.values()) {
            List<ShowcaseEntry> typeEntries = entriesByType.getOrDefault(type, List.of());
            boolean unlocked = !typeEntries.isEmpty();
            VBox card = new VBox(6);
            card.setAlignment(Pos.CENTER);
            card.getStyleClass().addAll("legendary-slot",
                    unlocked ? "legendary-slot-unlocked" : "legendary-slot-locked");
            card.setPrefWidth(180);
            card.setPrefHeight(205);

            // 展示台使用专属传奇插图；资源缺失时再安全回退到普通成熟作物图。
            ImageView crop = LegendaryImageAssets.viewFor(type, 104, unlocked);
            if (crop == null) {
                crop = AImageAssets.viewForTile(type, com.fieldstory.farm.model.GrowthStage.MATURE, 40);
            }
            if (crop != null) {
                card.getChildren().add(crop);
            }
            Label name = new Label(legendaryName(type));
            name.getStyleClass().add("legendary-slot-name");
            Label state = new Label(unlocked ? "已收录 " + typeEntries.size() + " 株" : "未收录");
            state.getStyleClass().add("hint-text");
            card.getChildren().addAll(name, state);

            if (unlocked) {
                Button open = new Button("查看故事");
                open.getStyleClass().add("secondary-button");
                open.setOnAction(event -> selectEntry(typeEntries.get(0)));
                card.getChildren().add(open);
            }
            slots.getChildren().add(card);
        }
    }

    private void selectEntry(ShowcaseEntry target) {
        int index = entries.indexOf(target);
        if (index >= 0) {
            selector.getSelectionModel().select(index);
            renderSelected();
        }
    }

    private void renderSelected() {
        int index = selector.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < entries.size()) {
            detailScroll.setContent(new MemoryCardView(entries.get(index)));
        }
    }

    private static String legendaryName(CropType type) {
        return switch (type) {
            case WHEAT -> "金色麦穗";
            case CORN -> "彩虹玉米";
            case CARROT -> "巨龙胡萝卜";
        };
    }

    public static String selectorLabelFor(ShowcaseEntry entry) {
        Objects.requireNonNull(entry, "展示条目不能为空");
        return entry.getLegendaryName() + " · " + entry.getCropType().getDisplayName();
    }
}
