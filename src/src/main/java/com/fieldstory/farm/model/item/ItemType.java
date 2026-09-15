package com.fieldstory.farm.model.item;

/**
 * 物品类型枚举（C 物品·背包·商店模块维护）。
 *
 * <p>跨模块约定（《C任务跨模块开发约束文档》§二/§七）：
 * 本枚举由 C 模块统一维护，A/B/D 组员只引用、禁止各自复制；
 * 作物收获产物统一使用 {@link #HARVEST_PRODUCE}。
 *
 * <p>数值依据：《完整游戏规则设计文档》§六十四 种子系统：
 * 小麦种子 10、玉米种子 15、胡萝卜种子 20 金币。
 */
public enum ItemType {

    /** 小麦种子：商店购买价 10 金币 */
    WHEAT_SEED("小麦种子", 10),

    /** 玉米种子：商店购买价 15 金币 */
    CORN_SEED("玉米种子", 15),

    /** 胡萝卜种子：商店购买价 20 金币 */
    CARROT_SEED("胡萝卜种子", 20),

    /** 收获产物：作物收获所得；P0 收获自动出售（验收规范 §三十一），
     *  P1 品质结算后经 {@code Inventory.addItem()} 进背包，单价由收获方传入 */
    HARVEST_PRODUCE("收获产物", 0),

    /** 肥料：P1 启用（P0 禁止实现肥料系统，见验收规范 §十；C 只负责存放与交易，
     *  施肥业务计算归 B 模块，见约束文档 §三） */
    FERTILIZER("肥料", 0);

    /** 显示名称（UI 展示用） */
    private final String displayName;

    /** 默认单价（金币）：种子 = 商店购买价；产物/肥料 = 0（需由创建方显式传入价格） */
    private final int defaultPrice;

    ItemType(String displayName, int defaultPrice) {
        this.displayName = displayName;
        this.defaultPrice = defaultPrice;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultPrice() {
        return defaultPrice;
    }

    /** 是否为种子（可播种）。 */
    public boolean isSeed() {
        return this == WHEAT_SEED || this == CORN_SEED || this == CARROT_SEED;
    }
}
