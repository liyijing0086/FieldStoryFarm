package com.fieldstory.farm.view;

import com.fieldstory.farm.service.ShowcaseEntry;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.Objects;

/** 传说作物生命记忆卡；P4 只统一 CSS，不改展示字段。 */
public class MemoryCardView extends VBox {

    public MemoryCardView(ShowcaseEntry entry) {
        Objects.requireNonNull(entry, "展示条目不能为空");
        getStyleClass().add("memory-card");
        UiTheme.apply(this);
        setSpacing(6);
        setPadding(new Insets(12));

        Label nameLabel = label(entry.getLegendaryName(), "legendary-title");
        Label typeQualityLabel = label(infoLine("作物", entry.getCropType().getDisplayName())
                + "　" + infoLine("品质", entry.getQuality().getDisplayName()), "normal-text");
        Label timeLabel = label(infoLine("种植时间", entry.getPlantTimeText())
                + "　" + infoLine("收获时间", entry.getHarvestTimeText()), "normal-text");
        Label weatherLabel = label(infoLine("关键天气", entry.getWeatherSummary()), "normal-text");
        Label eventLabel = label(infoLine("关键事件", entry.getEventSummary()), "normal-text");
        Label actionLabel = label(infoLine("玩家操作", entry.getActionSummary()), "normal-text");
        Label storyHeading = label("生命故事", "subsection-title");
        Label storyLabel = label(entry.getFullStory(), "story-text");
        storyLabel.setWrapText(true);

        getChildren().addAll(nameLabel, typeQualityLabel, timeLabel,
                weatherLabel, eventLabel, actionLabel, storyHeading, storyLabel);
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    public static String infoLine(String label, String value) {
        return label + "：" + value;
    }
}
