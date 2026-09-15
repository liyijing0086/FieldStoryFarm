package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.economy.PurchaseResult;
import com.fieldstory.farm.service.economy.EconomyService;

import java.util.Objects;

/**
 * B模块 P0 种子快捷购买控制器。
 *
 * 职责：
 * 1. 接收快捷购买UI的用户意图
 * 2. 调用 EconomyService
 * 3. 向View提供金币、种子库存等只读数据
 * 4. 将 PurchaseResult 转换为用户提示
 *
 * 不负责：
 * 1. 修改Player
 * 2. 自己计算/扣除金币
 * 3. 自己维护种子价格
 * 4. 保存JSON
 * 5. 完整Shop业务
 */
public final class SeedQuickBuyController {

    /**
     * P0快捷购买固定一次购买1颗。
     *
     * P1若加入数量选择，应将数量作为用户输入传入，
     * 但仍继续调用 EconomyService.buySeed(...)。
     */
    private static final int P0_BUY_QUANTITY = 1;

    private final EconomyService economyService;

    public SeedQuickBuyController(
            EconomyService economyService) {

        this.economyService =
                Objects.requireNonNull(
                        economyService,
                        "economyService cannot be null"
                );
    }

    /**
     * 购买1颗指定种子。
     *
     * Controller不判断金币是否足够，
     * 业务判断统一交给EconomyService。
     */
    public PurchaseResult buyOneSeed(
            CropType type) {

        Objects.requireNonNull(
                type,
                "crop type cannot be null"
        );

        return economyService.buySeed(
                type,
                P0_BUY_QUANTITY
        );
    }

    /**
     * 当前金币。
     */
    public int getGold() {
        return economyService.getGold();
    }

    /**
     * 当前指定种子库存。
     */
    public int getSeedCount(
            CropType type) {

        Objects.requireNonNull(
                type,
                "crop type cannot be null"
        );

        return economyService.getSeedCount(type);
    }

    /**
     * PurchaseResult -> UI提示文案。
     *
     * Result枚举只表达业务结果，
     * 文案转换留在Controller/UI层。
     */
    public static String messageFor(
            PurchaseResult result,
            CropType type) {

        Objects.requireNonNull(
                result,
                "purchase result cannot be null"
        );

        switch (result) {
            case SUCCESS:
                return type == null
                        ? "购买成功"
                        : type.getDisplayName()
                        + "种子购买成功";

            case INSUFFICIENT_GOLD:
                return "金币不足";

            case INVALID_QUANTITY:
                return "购买数量无效";

            default:
                return "购买失败";
        }
    }
}