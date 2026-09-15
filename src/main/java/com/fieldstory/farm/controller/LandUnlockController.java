package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.economy.LandUnlockResult;
import com.fieldstory.farm.service.LandUnlockService;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * B 模块 P3 土地解锁 Controller。
 *
 * <p>只做 View → Service 的适配和成功回调；不读取配置文件、不扣金币算法、
 * 不写数据库。E 可通过成功回调接入 refresh + saveNow()。
 */
public final class LandUnlockController {

    private final LandUnlockService landUnlockService;
    private Runnable onUnlockSucceeded = () -> { };

    public LandUnlockController(
            LandUnlockService landUnlockService) {

        this.landUnlockService = Objects.requireNonNull(
                landUnlockService,
                "landUnlockService"
        );
    }

    public OptionalInt getUnlockPrice(Soil soil) {
        return landUnlockService.getUnlockPrice(soil);
    }

    public boolean canUnlock(Soil soil) {
        return landUnlockService.canUnlock(soil);
    }

    public LandUnlockResult unlock(Soil soil) {
        LandUnlockResult result = landUnlockService.unlock(soil);

        if (result == LandUnlockResult.SUCCESS) {
            onUnlockSucceeded.run();
        }

        return result;
    }

    /** E 装配层可注入“刷新 + 保存”回调。 */
    public void setOnUnlockSucceeded(Runnable callback) {
        this.onUnlockSucceeded = callback == null
                ? () -> { }
                : callback;
    }

    /** 保留已有回调并追加新的成功回调。 */
    public void addOnUnlockSucceeded(Runnable callback) {
        if (callback == null) {
            return;
        }

        Runnable previous = this.onUnlockSucceeded;
        this.onUnlockSucceeded = () -> {
            previous.run();
            callback.run();
        };
    }

    /** 仅供 UI 显示，不参与业务判断。 */
    public static String messageFor(LandUnlockResult result) {
        if (result == null) {
            return "未选择土地";
        }

        return switch (result) {
            case SUCCESS -> "土地解锁成功";
            case NOT_LOCKED -> "该土地无需解锁";
            case PRICE_NOT_CONFIGURED -> "该土地暂未配置解锁价格";
            case NO_GOLD -> "金币不足";
        };
    }
}
