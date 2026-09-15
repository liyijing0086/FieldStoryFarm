package com.fieldstory.farm.persistence;

import com.fieldstory.farm.model.GameState;

/**
 * 存档位摘要（E 模块 P2：主菜单展示"这一档存到哪了"）。
 *
 * <p>主菜单需要在进入游戏<b>之前</b>让玩家看到每个存档位的状态（空档 / 第几天 / 金币多少），
 * 否则玩家无法判断该读哪一档。本记录是{@link SaveSlotManager#describe(SaveSlot)} 的返回值，
 * 只承载展示用信息，不含任何游戏计算。
 *
 * @param slot     存档位
 * @param occupied 是否已有存档（false = 空档）
 * @param gameDay  存档中的游戏天数（从 0 起的已结算天数；空档为 0）
 * @param gold     存档中的玩家金币（空档为 0）
 * @param savedAt  存档文件最后写入时间（ISO-8601 文本；空档为 null）
 */
public record SaveSlotInfo(SaveSlot slot, boolean occupied, long gameDay, int gold, String savedAt) {

    /** 空档摘要。 */
    public static SaveSlotInfo empty(SaveSlot slot) {
        return new SaveSlotInfo(slot, false, 0L, 0, null);
    }

    /**
     * 由存档聚合生成摘要。
     *
     * @param slot    存档位
     * @param state   读出的存档状态（不得为 null；null 表示空档，调用方应改用 {@link #empty}）
     * @param savedAt 存档文件最后写入时间（可为 null）
     * @return 摘要
     */
    public static SaveSlotInfo of(SaveSlot slot, GameState state, String savedAt) {
        if (state == null) {
            return empty(slot);
        }
        int gold = state.getPlayer() == null ? 0 : state.getPlayer().getGold();
        return new SaveSlotInfo(slot, true, state.getGameDay(), gold, savedAt);
    }

    /**
     * 展示文本：空档显示「空档」，有档显示「第 N 天 · 金币 G」。
     *
     * <p>天数按玩家口径显示（{@code gameDay} 是从 0 起的已结算天数，第 1 天对应 0），
     * 与 {@code GameClock.getGameDay()} 一致。
     */
    public String describe() {
        if (!occupied) {
            return "空档";
        }
        return "第 " + (gameDay + 1) + " 天 · 金币 " + gold;
    }
}
