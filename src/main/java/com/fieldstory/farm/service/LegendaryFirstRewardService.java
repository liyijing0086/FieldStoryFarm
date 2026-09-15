package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropType;

/**
 * 首次传说奖励服务接口（C 模块 品质与传说域，P2 收获事务第⑫步）。
 *
 * <p>规则文档 §六十七：第一次获得某一种传说作物发放 +500 金币，
 * 三种传说分别只能领取一次首次奖励。
 *
 * <p>本服务只负责「首次判定与领取记录」，金币入账由收获事务经
 * B 的 {@code EconomyService#addGold} 完成（金币唯一入口）。
 */
public interface LegendaryFirstRewardService {

    /** 首次传说奖励金额：+500 金币（规则文档 §六十七） */
    int FIRST_REWARD_GOLD = 500;

    /**
     * 领取指定传说作物的首次奖励。
     *
     * <p>首次领取返回 {@link #FIRST_REWARD_GOLD} 并登记该作物类型；
     * 再次领取返回 0（同一传说只领一次，规则文档 §六十七）。
     *
     * @param cropType 传说作物类型
     * @return 本次应发放的金币（500 或 0）
     */
    int claimFirstReward(CropType cropType);

    /**
     * 该传说作物的首次奖励是否已被领取。
     *
     * @param cropType 传说作物类型
     * @return true = 已领取过
     */
    boolean hasClaimed(CropType cropType);
}
