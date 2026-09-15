package com.fieldstory.farm.service;

import com.fieldstory.farm.model.EventType;

/**
 * P4 轻量音频服务。
 *
 * <p>音频只是表现层：播放失败/资源缺失不得改变世界时间、随机、经济或存档状态。
 */
public interface AudioService {

    enum Bgm {
        MENU,
        FARM,
        EVENT
    }

    enum Sfx {
        CLICK,
        PURCHASE,
        PLANT,
        WATER,
        FERTILIZE,
        HARVEST,
        RARE,
        LEGENDARY,
        RANK_UP,
        GRADUATION
    }

    void playBgm(Bgm bgm);

    /** 特殊事件持续期间使用事件主题；无事件恢复普通农场主题。 */
    void updateForEvent(EventType eventType);

    void playSfx(Sfx sfx);

    double getMasterVolume();

    void setMasterVolume(double volume);

    boolean isSfxEnabled();

    void setSfxEnabled(boolean enabled);

    void shutdown();
}
