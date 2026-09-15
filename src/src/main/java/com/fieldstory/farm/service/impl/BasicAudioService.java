package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.service.AudioService;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

/**
 * JavaFX Media 的最小 P4 实现。
 *
 * <p>所有加载都按需进行；资源缺失、平台音频后端不可用时静默降级。
 * 这样测试环境/无声设备不会因为表现层导致核心功能失败。
 */
public final class BasicAudioService implements AudioService {

    private static final BasicAudioService INSTANCE = new BasicAudioService();

    private static final Map<Bgm, String> BGM_PATHS = new EnumMap<>(Bgm.class);
    private static final Map<Sfx, String> SFX_PATHS = new EnumMap<>(Sfx.class);

    static {
        BGM_PATHS.put(Bgm.MENU, "/audio/bgm/menu-theme.wav");
        BGM_PATHS.put(Bgm.FARM, "/audio/bgm/farm-theme.wav");
        BGM_PATHS.put(Bgm.EVENT, "/audio/bgm/event-theme.wav");

        SFX_PATHS.put(Sfx.CLICK, "/audio/sfx/click.wav");
        SFX_PATHS.put(Sfx.PURCHASE, "/audio/sfx/purchase.wav");
        SFX_PATHS.put(Sfx.PLANT, "/audio/sfx/plant.wav");
        SFX_PATHS.put(Sfx.WATER, "/audio/sfx/water.wav");
        SFX_PATHS.put(Sfx.FERTILIZE, "/audio/sfx/fertilize.wav");
        SFX_PATHS.put(Sfx.HARVEST, "/audio/sfx/harvest.wav");
        SFX_PATHS.put(Sfx.RARE, "/audio/sfx/rare.wav");
        SFX_PATHS.put(Sfx.LEGENDARY, "/audio/sfx/legendary.wav");
        SFX_PATHS.put(Sfx.RANK_UP, "/audio/sfx/rank-up.wav");
        SFX_PATHS.put(Sfx.GRADUATION, "/audio/sfx/graduation.wav");
    }

    private final Map<Sfx, AudioClip> clipCache = new EnumMap<>(Sfx.class);

    private MediaPlayer bgmPlayer;
    private Bgm currentBgm;
    private double masterVolume = 0.55;
    private boolean sfxEnabled = true;

    private BasicAudioService() {
    }

    public static BasicAudioService shared() {
        return INSTANCE;
    }

    @Override
    public synchronized void playBgm(Bgm bgm) {
        if (bgm == null || bgm == currentBgm) {
            return;
        }
        stopBgm();
        URL resource = BasicAudioService.class.getResource(BGM_PATHS.get(bgm));
        if (resource == null) {
            currentBgm = bgm;
            return;
        }
        try {
            Media media = new Media(resource.toExternalForm());
            MediaPlayer player = new MediaPlayer(media);
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setVolume(masterVolume);
            player.setOnError(this::stopBgm);
            player.play();
            bgmPlayer = player;
            currentBgm = bgm;
        } catch (RuntimeException ignored) {
            // 音频表现层不得阻断游戏启动。
            currentBgm = bgm;
            bgmPlayer = null;
        }
    }

    @Override
    public void updateForEvent(EventType eventType) {
        playBgm(eventType != null && eventType != EventType.NONE ? Bgm.EVENT : Bgm.FARM);
    }

    @Override
    public synchronized void playSfx(Sfx sfx) {
        if (!sfxEnabled || sfx == null) {
            return;
        }
        try {
            AudioClip clip = clipCache.computeIfAbsent(sfx, this::loadClip);
            if (clip != null) {
                clip.setVolume(masterVolume);
                clip.play();
            }
        } catch (RuntimeException ignored) {
            // 无声设备/Headless 环境安全降级。
        }
    }

    private AudioClip loadClip(Sfx sfx) {
        URL resource = BasicAudioService.class.getResource(SFX_PATHS.get(sfx));
        if (resource == null) {
            return null;
        }
        try {
            return new AudioClip(resource.toExternalForm());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public synchronized double getMasterVolume() {
        return masterVolume;
    }

    @Override
    public synchronized void setMasterVolume(double volume) {
        masterVolume = Math.max(0.0, Math.min(1.0, volume));
        if (bgmPlayer != null) {
            bgmPlayer.setVolume(masterVolume);
        }
    }

    @Override
    public synchronized boolean isSfxEnabled() {
        return sfxEnabled;
    }

    @Override
    public synchronized void setSfxEnabled(boolean enabled) {
        sfxEnabled = enabled;
    }

    @Override
    public synchronized void shutdown() {
        stopBgm();
        clipCache.clear();
        currentBgm = null;
    }

    private void stopBgm() {
        if (bgmPlayer != null) {
            try {
                bgmPlayer.stop();
                bgmPlayer.dispose();
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
        bgmPlayer = null;
        currentBgm = null;
    }
}
