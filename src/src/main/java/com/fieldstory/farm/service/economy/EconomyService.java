package com.fieldstory.farm.service.economy;


import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.economy.PurchaseResult;



/**
 * 经济系统统一接口。
 */
public interface EconomyService {



    /**
     * 获取金币。
     */
    int getGold();



    /**
     * 判断金币是否足够。
     */
    boolean canAfford(
            int amount
    );



    /**
     * 消耗金币。
     */
    void spendGold(
            int amount
    );



    /**
     * 增加金币。
     */
    void addGold(
            int amount
    );



    /**
     * 购买种子。
     */
    PurchaseResult buySeed(
            CropType type,
            int quantity
    );



    /**
     * 获取种子数量。
     */
    int getSeedCount(
            CropType type
    );



    /**
     * 判断是否拥有指定数量种子。
     */
    boolean hasSeed(
            CropType type,
            int quantity
    );



    /**
     * 消耗种子。
     */
    boolean consumeSeed(
            CropType type,
            int quantity
    );



    /**
     * 获取基础出售价格。
     */
    int calculateBaseSellPrice(
            CropType type
    );

}