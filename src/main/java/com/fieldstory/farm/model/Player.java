package com.fieldstory.farm.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * 玩家数据对象。
 *
 * P0阶段负责保存：
 * 1. 玩家名称
 * 2. 金币
 * 3. 种子库存
 *
 * Player只保存状态，不处理经济业务逻辑。
 */
public class Player {

    /**
     * 玩家名称。
     */
    private String name;

    /**
     * 玩家金币。
     */
    private int gold;

    /**
     * 玩家种子库存。
     */
    private Map<CropType, Integer> seedInventory;

    /**
     * 无参构造器。
     *
     * 主要用于JSON反序列化、框架创建对象。
     * 正常新游戏请通过GameManager创建。
     */
    public Player() {
        this.name = "";
        this.gold = 0;
        this.seedInventory = createEmptySeedInventory();
    }

    /**
     * 创建指定名称和初始金币的玩家。
     *
     * GameManager.newGame()使用此构造器。
     *
     * @param name 玩家名称
     * @param gold 初始金币
     */
    public Player(String name, int gold) {

        if (gold < 0) {
            throw new IllegalArgumentException(
                    "gold must not be negative"
            );
        }

        this.name = name;
        this.gold = gold;
        this.seedInventory = createEmptySeedInventory();
    }

    /**
     * 创建三种作物的空种子库存。
     */
    private Map<CropType, Integer> createEmptySeedInventory() {

        Map<CropType, Integer> inventory =
                new EnumMap<>(CropType.class);

        for (CropType type : CropType.values()) {
            inventory.put(type, 0);
        }

        return inventory;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getGold() {
        return gold;
    }

    /**
     * 主要供状态恢复使用。
     *
     * 正常游戏中的金币增加和减少
     * 应通过EconomyService完成。
     */
    public void setGold(int gold) {

        if (gold < 0) {
            throw new IllegalArgumentException(
                    "gold must not be negative"
            );
        }

        this.gold = gold;
    }

    public Map<CropType, Integer> getSeedInventory() {
        return seedInventory;
    }

    /**
     * 主要供JSON存档恢复使用。
     *
     * 正常游戏中的种子增减
     * 应通过EconomyService完成。
     */
    public void setSeedInventory(
            Map<CropType, Integer> seedInventory) {

        if (seedInventory == null) {
            this.seedInventory =
                    createEmptySeedInventory();
            return;
        }

        this.seedInventory =
                new EnumMap<>(CropType.class);

        for (CropType type : CropType.values()) {
            this.seedInventory.put(
                    type,
                    seedInventory.getOrDefault(type, 0)
            );
        }
    }
}