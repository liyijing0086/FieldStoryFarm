package com.fieldstory.farm.testutil;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.economy.PurchaseResult;
import com.fieldstory.farm.service.economy.EconomyService;

import java.util.EnumMap;
import java.util.Map;

/**
 * {@link EconomyService} 测试桩（B 文档 §14 九方法接口，A 模块设计文档 §12.1 消费约定）。
 *
 * <p>仅模拟 A 模块播种/开垦所需行为：金币与各作物种子数可 set；
 * canAfford/spendGold/hasSeed/consumeSeed 为桩内真实行为，
 * 供 {@code service.impl.BasicLandService} / {@code service.impl.BasicPlantingService}
 * 单测构造器注入；buySeed/addGold 等 A 用不到的方法返回默认值（桩占位）。
 */
public class TestEconomyService implements EconomyService {

    /** 当前金币（可 set） */
    private int gold;

    /** 各作物种子数（可 set） */
    private final Map<CropType, Integer> seedCounts = new EnumMap<>(CropType.class);

    /** 设置当前金币（测试用）。 */
    public void setGold(int gold) {
        this.gold = gold;
    }

    /** 设置某作物种子数（测试用）。 */
    public void setSeedCount(CropType type, int count) {
        seedCounts.put(type, count);
    }

    @Override
    public int getGold() {
        return gold;
    }

    @Override
    public boolean canAfford(int amount) {
        return gold >= amount;
    }

    @Override
    public void spendGold(int amount) {
        gold -= amount;
    }

    /**
     * 桩占位：A 模块 P0 用不到（收获入账由 C 调用，B 文档 §18），
     * 仅保持接口完整。
     */
    @Override
    public void addGold(int amount) {
        gold += amount;
    }

    /**
     * 桩占位：A 模块 P0 用不到（种子购买入口由 B 提供，B 文档 §16），
     * 仅保持接口完整，默认返回 SUCCESS。
     */
    @Override
    public PurchaseResult buySeed(CropType type, int quantity) {
        return PurchaseResult.SUCCESS;
    }

    @Override
    public int getSeedCount(CropType type) {
        return seedCounts.getOrDefault(type, 0);
    }

    @Override
    public boolean hasSeed(CropType type, int quantity) {
        return getSeedCount(type) >= quantity;
    }

    @Override
    public boolean consumeSeed(CropType type, int quantity) {
        int current = getSeedCount(type);
        if (current < quantity) {
            return false;
        }
        seedCounts.put(type, current - quantity);
        return true;
    }

    /**
     * 桩占位：A 模块 P0 用不到（基础售价由 C 收获调用，B 文档 §18），
     * 仅保持接口完整，直接委托 CropType 单一数据源（B 文档 §7、§8）。
     */
    @Override
    public int calculateBaseSellPrice(CropType type) {
        return type.getBaseSellPrice();
    }
}
