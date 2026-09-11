package com.fieldstory.farm.model.item;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Player;

import java.util.Objects;

/**
 * 商店模型（C 商店模块）：物品买卖与金币校验。
 *
 * <p>对外接口（《C任务跨模块开发约束文档》§九，签名已锁定，不得随意改动）：
 * <ul>
 *   <li>{@link #buyItem(Item, Player)}：购买（扣金币，成功后由调用方入包）；</li>
 *   <li>{@link #sellItem(Item, Player)}：出售（加金币，成功后由调用方移除物品）。</li>
 * </ul>
 *
 * <p>金币唯一数据源：{@link Player#getGold()} / {@link Player#setGold(int)}
 * （《接口要求清单》§D4：改钱一律走 player，禁止维护第二份金币）。
 *
 * <p>P0 范围：只售三种种子（验收规范 §十九「最小购买入口」，P1 扩展完整商店）。
 *
 * <p>P2 事件倍率（规则文档 §四十九 神秘商人 ×2）：出售价格读取
 * {@link EventPriceRateProvider} 提供的公开事件倍率（倍率变量存放于 D 模块，
 * C 不写事件逻辑，约束文档 §四.1）。默认 {@link EventPriceRateProvider#NONE}
 * 恒为 1.0，未注入 D 实现时出售行为与 P0 完全一致。
 */
public class ShopModel {

    /** 购买失败提示（金币不足），UI 层展示用。 */
    public static final String MSG_GOLD_NOT_ENOUGH = "金币不足";

    /** 事件售价倍率提供者（D 模块实现）；默认无事件倍率 1.0 */
    private EventPriceRateProvider eventPriceRateProvider = EventPriceRateProvider.NONE;

    /**
     * 注入事件售价倍率提供者（P2 神秘商人对接；约束文档 §四.1）。
     *
     * <p>D 模块 EventService 接入时注入其实现；传 null 恢复默认
     * {@link EventPriceRateProvider#NONE}（倍率 1.0）。
     */
    public void setEventPriceRateProvider(EventPriceRateProvider provider) {
        this.eventPriceRateProvider = provider == null ? EventPriceRateProvider.NONE : provider;
    }

    /**
     * 购买物品：校验金币后扣除。
     *
     * <p>对外接口；签名已锁定。背包入库由调用方在返回 true 后执行
     * {@link Inventory#addItem(Item)}。
     *
     * @param item   待购物品（单价 = item.getUnitPrice()）
     * @param player 玩家（金币唯一数据源）
     * @return 金币足够且扣款成功返回 true；金币不足或参数非法返回 false
     */
    public boolean buyItem(Item item, Player player) {
        if (!validate(item, player)) {
            return false;
        }
        int total = item.totalPrice();
        if (!canAfford(player, total)) {
            return false;
        }
        player.setGold(player.getGold() - total);
        return true;
    }

    /**
     * 出售物品：按单件价格结算金币（锁定签名，与作物类型无关，事件倍率按
     * {@code cropType = null} 读取，默认 1.0）。
     *
     * <p>对外接口；签名已锁定。物品移除由调用方在返回 true 后执行
     * {@link Inventory#removeItem(int)}。
     *
     * @param item   待售物品
     * @param player 玩家（金币唯一数据源）
     * @return 结算成功返回 true；参数非法返回 false
     */
    public boolean sellItem(Item item, Player player) {
        return sellItem(item, player, null);
    }

    /**
     * 出售物品（P2 事件倍率版）：按单件价格 × 事件倍率结算金币。
     *
     * <p>神秘商人事件期间出售其目标作物时，D 注入的提供者对对应
     * {@link CropType} 返回 2.0，最终售价 ×2（规则文档 §四十九）。
     *
     * @param item     待售物品
     * @param player   玩家（金币唯一数据源）
     * @param cropType 出售作物类型（用于查询事件倍率）；null 时倍率为 1.0
     * @return 结算成功返回 true；参数非法返回 false
     */
    public boolean sellItem(Item item, Player player, CropType cropType) {
        if (!validate(item, player)) {
            return false;
        }
        player.setGold(player.getGold() + sellPriceFor(item, cropType));
        return true;
    }

    /**
     * 出售结算价：总价 × 事件倍率（作物类型无关，倍率按 null 读取）。
     *
     * <p>公式：FinalPrice = BasePrice × EventPriceRate（规则文档 §六十五
     * 售价公式的 P2 子集：品质/装饰/套装倍率由 B 模块与 P3 接入）。
     */
    public int sellPriceFor(Item item) {
        return sellPriceFor(item, null);
    }

    /**
     * 出售结算价：总价 × 指定作物类型的事件倍率。
     *
     * <p>神秘商人目标作物 ×2（规则文档 §四十九）；无事件时倍率 1.0。
     * 四舍五入取整，保证倍率 1.5 / 2.0 下结果仍为整数金币。
     *
     * @param item     待售物品
     * @param cropType 出售作物类型；null 时倍率为 1.0
     * @return 结算金币数；参数非法返回 0
     */
    public int sellPriceFor(Item item, CropType cropType) {
        if (item == null) {
            return 0;
        }
        double rate = eventPriceRateProvider.priceRateFor(cropType);
        return (int) Math.round(item.totalPrice() * rate);
    }

    /** 玩家金币是否足以支付 amount。 */
    public boolean canAfford(Player player, int amount) {
        return player.getGold() >= amount;
    }

    /** 购买数量为 n 的总价（n × 单件价格）。 */
    public int priceFor(ItemType type, int quantity) {
        Objects.requireNonNull(type, "物品类型不能为空");
        if (quantity <= 0) {
            throw new IllegalArgumentException("购买数量必须为正数: " + quantity);
        }
        return type.getDefaultPrice() * quantity;
    }

    private boolean validate(Item item, Player player) {
        return item != null && player != null && item.getUnitPrice() > 0;
    }
}
