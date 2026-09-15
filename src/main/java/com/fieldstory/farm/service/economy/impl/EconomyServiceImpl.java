package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.economy.PurchaseResult;
import com.fieldstory.farm.service.economy.EconomyService;

import java.util.Objects;

/**
 * B 模块 P0 经济服务默认实现。
 *
 * <p>职责：
 * <ol>
 *   <li>金币查询、收入与支出；</li>
 *   <li>种子购买；</li>
 *   <li>种子库存查询与消耗；</li>
 *   <li>P0 基础售价访问。</li>
 * </ol>
 *
 * <p>所有正式经济变更必须通过本服务，不允许 Controller/View 直接修改
 * {@link Player#getGold()} 或 {@link Player#getSeedInventory()}。
 *
 * <p>异常输入保护：购买数量、总价、库存累加均使用 {@code long} 做中间计算，
 * 防止 {@code int} 溢出导致负价格、负库存或“金币越买越多”等状态破坏。
 */
public class EconomyServiceImpl implements EconomyService {

    private final Player player;

    /**
     * 创建经济服务。
     *
     * @param player 当前游戏会话中的唯一 Player
     */
    public EconomyServiceImpl(Player player) {
        this.player = Objects.requireNonNull(
                player,
                "player cannot be null"
        );
    }

    @Override
    public int getGold() {
        return player.getGold();
    }

    @Override
    public boolean canAfford(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "amount must not be negative"
            );
        }
        return player.getGold() >= amount;
    }

    @Override
    public void spendGold(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "amount must not be negative"
            );
        }

        if (!canAfford(amount)) {
            throw new IllegalStateException(
                    "insufficient gold"
            );
        }

        player.setGold(player.getGold() - amount);
    }

    @Override
    public void addGold(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "amount must not be negative"
            );
        }

        long nextGold = (long) player.getGold() + amount;
        if (nextGold > Integer.MAX_VALUE) {
            throw new IllegalStateException("gold overflow");
        }

        player.setGold((int) nextGold);
    }

    @Override
    public PurchaseResult buySeed(CropType type, int quantity) {
        Objects.requireNonNull(
                type,
                "crop type cannot be null"
        );

        if (quantity <= 0) {
            return PurchaseResult.INVALID_QUANTITY;
        }

        /*
         * CropType 是价格唯一数据源。
         * B 模块禁止自己维护第二份 switch 价格表。
         */
        int unitPrice = type.getSeedPrice();

        // 使用 long 进行乘法，避免 int 溢出把巨大订单变成负价格。
        long totalPriceLong = (long) unitPrice * quantity;
        if (totalPriceLong > Integer.MAX_VALUE) {
            return PurchaseResult.INVALID_QUANTITY;
        }

        int currentSeedCount = getSeedCount(type);
        long nextSeedCountLong = (long) currentSeedCount + quantity;
        if (nextSeedCountLong > Integer.MAX_VALUE) {
            return PurchaseResult.INVALID_QUANTITY;
        }

        int totalPrice = (int) totalPriceLong;
        if (!canAfford(totalPrice)) {
            return PurchaseResult.INSUFFICIENT_GOLD;
        }

        /*
         * 一次购买业务：
         * 扣金币 + 增加库存。
         * 所有前置校验都在变更状态之前完成，失败时保持原状态。
         */
        spendGold(totalPrice);
        player.getSeedInventory().put(type, (int) nextSeedCountLong);

        return PurchaseResult.SUCCESS;
    }

    @Override
    public int getSeedCount(CropType type) {
        Objects.requireNonNull(
                type,
                "crop type cannot be null"
        );

        return player.getSeedInventory().getOrDefault(type, 0);
    }

    @Override
    public boolean hasSeed(CropType type, int quantity) {
        Objects.requireNonNull(
                type,
                "crop type cannot be null"
        );

        if (quantity <= 0) {
            return false;
        }

        return getSeedCount(type) >= quantity;
    }

    @Override
    public boolean consumeSeed(CropType type, int quantity) {
        Objects.requireNonNull(
                type,
                "crop type cannot be null"
        );

        if (quantity <= 0) {
            return false;
        }

        if (!hasSeed(type, quantity)) {
            return false;
        }

        int remaining = getSeedCount(type) - quantity;
        player.getSeedInventory().put(type, remaining);
        return true;
    }

    @Override
    public int calculateBaseSellPrice(CropType type) {
        Objects.requireNonNull(
                type,
                "crop type cannot be null"
        );

        /* CropType 是基础售价唯一数据源。 */
        return type.getBaseSellPrice();
    }
}
