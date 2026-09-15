package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.economy.LandUnlockResult;
import com.fieldstory.farm.service.LandUnlockPriceProvider;
import com.fieldstory.farm.service.LandUnlockService;
import com.fieldstory.farm.service.economy.EconomyService;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * {@link LandUnlockService} 默认实现（B 模块 P3）。
 *
 * <p>正式流程：LOCKED → 读取 balance-config 价格 → 检查金币 → 扣钱 → EMPTY。
 * 本实现不写 SQL，不自动开垦，也不自创正式解锁价格。
 */
public final class BasicLandUnlockService implements LandUnlockService {

    private final EconomyService economyService;
    private final LandUnlockPriceProvider priceProvider;

    public BasicLandUnlockService(
            EconomyService economyService,
            LandUnlockPriceProvider priceProvider) {

        this.economyService = Objects.requireNonNull(
                economyService,
                "economyService"
        );
        this.priceProvider = Objects.requireNonNull(
                priceProvider,
                "priceProvider"
        );
    }

    @Override
    public OptionalInt getUnlockPrice(Soil soil) {
        if (soil == null
                || soil.getState() != SoilState.LOCKED) {
            return OptionalInt.empty();
        }

        OptionalInt configured = priceProvider.findUnlockPrice(
                soil.getRow(),
                soil.getColumn()
        );

        if (configured.isPresent()
                && configured.getAsInt() < 0) {
            throw new IllegalStateException(
                    "land unlock price must not be negative: "
                            + configured.getAsInt()
            );
        }

        return configured;
    }

    @Override
    public boolean canUnlock(Soil soil) {
        OptionalInt price = getUnlockPrice(soil);

        return price.isPresent()
                && economyService.canAfford(
                        price.getAsInt()
                );
    }

    @Override
    public LandUnlockResult unlock(Soil soil) {
        if (soil == null
                || soil.getState() != SoilState.LOCKED) {
            return LandUnlockResult.NOT_LOCKED;
        }

        OptionalInt price = getUnlockPrice(soil);

        if (price.isEmpty()) {
            return LandUnlockResult.PRICE_NOT_CONFIGURED;
        }

        int amount = price.getAsInt();

        if (!economyService.canAfford(amount)) {
            return LandUnlockResult.NO_GOLD;
        }

        // 文档顺序：检查金币 → 扣钱 → LOCKED → EMPTY。
        economyService.spendGold(amount);
        soil.setState(SoilState.EMPTY);

        return LandUnlockResult.SUCCESS;
    }
}
