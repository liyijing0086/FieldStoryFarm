package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.economy.DecorationPurchaseResult;
import com.fieldstory.farm.model.economy.PurchaseResult;
import com.fieldstory.farm.service.ShopService;

import java.util.Objects;

/** B 模块 P1 商店控制器：只协调 View 与 ShopService。 */
public class ShopController {

    private final ShopService shopService;
    private Runnable onDecorationPurchased = () -> { };
    private Runnable onPurchaseSucceeded = () -> { };

    public ShopController(ShopService shopService) {
        this.shopService = Objects.requireNonNull(shopService, "shopService");
    }

    public void setOnDecorationPurchased(Runnable callback) {
        this.onDecorationPurchased = callback == null ? () -> { } : callback;
    }

    public void addOnDecorationPurchased(Runnable callback) {
        if (callback == null) {
            return;
        }
        Runnable previous = this.onDecorationPurchased;
        this.onDecorationPurchased = () -> {
            previous.run();
            callback.run();
        };
    }

    /** 可由 E 装配层注入自动存档回调。 */
    public void setOnPurchaseSucceeded(Runnable callback) {
        this.onPurchaseSucceeded = callback == null ? () -> { } : callback;
    }

    public void addOnPurchaseSucceeded(Runnable callback) {
        if (callback == null) {
            return;
        }
        Runnable previous = this.onPurchaseSucceeded;
        this.onPurchaseSucceeded = () -> {
            previous.run();
            callback.run();
        };
    }

    public PurchaseResult buySeed(CropType type, int quantity) {
        PurchaseResult result = shopService.buySeed(type, quantity);
        if (result == PurchaseResult.SUCCESS) {
            onPurchaseSucceeded.run();
        }
        return result;
    }

    public DecorationPurchaseResult buyDecoration(DecorationType type, int quantity) {
        DecorationPurchaseResult result = shopService.buyDecoration(type, quantity);
        if (result == DecorationPurchaseResult.SUCCESS) {
            onDecorationPurchased.run();
            onPurchaseSucceeded.run();
        }
        return result;
    }

    public boolean canAffordDecoration(DecorationType type, int quantity) {
        return shopService.canAffordDecoration(type, quantity);
    }

    public int getGold() {
        return shopService.getGold();
    }

    public int getSeedCount(CropType type) {
        return shopService.getSeedCount(type);
    }

    public static String messageFor(PurchaseResult result, CropType type) {
        if (result == null) {
            return "购买失败";
        }
        return switch (result) {
            case SUCCESS -> type == null ? "购买成功" : type.getDisplayName() + "种子购买成功";
            case INSUFFICIENT_GOLD -> "金币不足";
            case INVALID_QUANTITY -> "购买数量无效";
        };
    }

    public static String messageFor(DecorationPurchaseResult result, DecorationType type) {
        if (result == null) {
            return "购买失败";
        }
        return switch (result) {
            case SUCCESS -> type == null ? "购买成功" : type.getDisplayName() + "购买成功";
            case INSUFFICIENT_GOLD -> "金币不足";
            case INVALID_QUANTITY -> "购买数量无效";
        };
    }
}
