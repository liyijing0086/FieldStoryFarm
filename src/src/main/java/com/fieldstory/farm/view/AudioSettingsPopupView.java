package com.fieldstory.farm.view;

import com.fieldstory.farm.service.AudioService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.Objects;

/** P4 最小音频设置：总音量 + 音效开关。 */
public final class AudioSettingsPopupView extends Popup {

    private final AudioService audioService;
    private final Slider volumeSlider;
    private final CheckBox sfxToggle;

    public AudioSettingsPopupView(AudioService audioService) {
        this.audioService = Objects.requireNonNull(audioService, "audioService");

        Label title = new Label("声音设置");
        title.getStyleClass().add("section-title");

        Label volumeLabel = new Label("总音量");
        volumeLabel.getStyleClass().add("normal-text");
        volumeSlider = new Slider(0, 100, audioService.getMasterVolume() * 100.0);
        volumeSlider.setPrefWidth(180);
        volumeSlider.valueProperty().addListener((obs, oldValue, value) ->
                audioService.setMasterVolume(value.doubleValue() / 100.0));

        HBox volumeRow = new HBox(10, volumeLabel, volumeSlider);
        volumeRow.setAlignment(Pos.CENTER_LEFT);

        sfxToggle = new CheckBox("启用音效");
        sfxToggle.setSelected(audioService.isSfxEnabled());
        sfxToggle.getStyleClass().add("normal-text");
        sfxToggle.selectedProperty().addListener((obs, oldValue, selected) ->
                audioService.setSfxEnabled(selected));

        VBox root = new VBox(12, title, volumeRow, sfxToggle);
        root.setPadding(new Insets(16));
        root.setPrefWidth(300);
        root.getStyleClass().addAll("panel", "settings-panel");
        UiTheme.apply(root);

        getContent().add(root);
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
        volumeSlider.setValue(audioService.getMasterVolume() * 100.0);
        sfxToggle.setSelected(audioService.isSfxEnabled());
        show(owner, bounds.getMinX(), bounds.getMaxY() + 6);
        return true;
    }
}
