package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.service.CollectionService;
import com.fieldstory.farm.service.LegendaryFirstRewardService;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * {@link LegendaryFirstRewardService} 基础实现（C 模块 品质与传说域，P2）。
 *
 * <p>已领取首次奖励的传说作物类型集合（规则文档 §六十七：
 * 三种传说分别只能领取一次 +500 金币）。正式构造从 E 持久化的传说图鉴
 * 恢复该事实，避免重启后重复领取；无参构造仅保留旧测试兼容。
 */
public class BasicLegendaryFirstRewardService implements LegendaryFirstRewardService {

    /** 已领取首次奖励的传说作物类型 */
    private final Set<CropType> claimed = EnumSet.noneOf(CropType.class);

    /** 旧测试/早期装配兼容。 */
    public BasicLegendaryFirstRewardService() {
        // 空集合起步
    }

    /**
     * 正式构造：从 E 的持久化传说图鉴恢复“已领取”事实。
     *
     * <p>首次传奇奖励与“首次获得某种传说”是同一事实。图鉴随 SQLite
     * 持久化，因此重启后用它初始化 claimed，可避免重复领取 +500。
     */
    public BasicLegendaryFirstRewardService(CollectionService collectionService) {
        Objects.requireNonNull(collectionService, "图鉴服务不能为空");
        for (CropType type : CropType.values()) {
            if (collectionService.isLegendaryCollected(type)) {
                claimed.add(type);
            }
        }
    }

    @Override
    public int claimFirstReward(CropType cropType) {
        Objects.requireNonNull(cropType, "作物类型不能为空");
        if (!claimed.add(cropType)) {
            return 0; // 已领过：同一传说只领一次（规则文档 §六十七）
        }
        return FIRST_REWARD_GOLD;
    }

    @Override
    public boolean hasClaimed(CropType cropType) {
        Objects.requireNonNull(cropType, "作物类型不能为空");
        return claimed.contains(cropType);
    }
}
