package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.economy.DecorationPurchaseResult;
import com.fieldstory.farm.model.economy.PurchaseResult;
import com.fieldstory.farm.service.DecorationService;
import com.fieldstory.farm.service.ShopService;
import com.fieldstory.farm.service.economy.EconomyService;

import java.util.Objects;

/** B 模块 P1 ShopService 默认实现。 */
public class BasicShopService implements ShopService {

    private final EconomyService economyService;
    private final DecorationService decorationService;

    public BasicShopService(EconomyService economyService,
                            DecorationService decorationService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.decorationService = Objects.requireNonNull(decorationService, "decorationService");
    }

    @Override
    public PurchaseResult buySeed(CropType type, int quantity) {
        return economyService.buySeed(type, quantity);
    }

    @Override
    public DecorationPurchaseResult buyDecoration(DecorationType type, int quantity) {
        if (type == null || quantity <= 0) {
            return DecorationPurchaseResult.INVALID_QUANTITY;
        }

        long total = (long) type.getPrice() * quantity;
        if (total > Integer.MAX_VALUE) {
            return DecorationPurchaseResult.INVALID_QUANTITY;
        }

        int totalPrice = (int) total;
        if (!economyService.canAfford(totalPrice)) {
            return DecorationPurchaseResult.INSUFFICIENT_GOLD;
        }

        // addPurchasedDecoration 对合法 type/quantity 不应失败；先创建再扣款可以避免
        // 创建逻辑异常时留下“已扣款但未入库”的半成功状态。
        decorationService.addPurchasedDecoration(type, quantity);
        economyService.spendGold(totalPrice);
        return DecorationPurchaseResult.SUCCESS;
    }

    @Override
    public boolean canAffordDecoration(DecorationType type, int quantity) {
        if (type == null || quantity <= 0) {
            return false;
        }
        long total = (long) type.getPrice() * quantity;
        return total <= Integer.MAX_VALUE && economyService.canAfford((int) total);
    }

    @Override
    public int getGold() {
        return economyService.getGold();
    }

    @Override
    public int getSeedCount(CropType type) {
        return economyService.getSeedCount(type);
    }
}
