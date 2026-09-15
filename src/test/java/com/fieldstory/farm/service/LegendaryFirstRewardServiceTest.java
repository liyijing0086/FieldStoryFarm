package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.service.impl.BasicLegendaryFirstRewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LegendaryFirstRewardService} 首次传说奖励测试
 * （规则文档 §六十七：第一次获得某一种传说作物 +500 金币，
 * 三种传说分别只能领取一次）。
 */
class LegendaryFirstRewardServiceTest {

    private LegendaryFirstRewardService rewardService;

    @BeforeEach
    void setUp() {
        rewardService = new BasicLegendaryFirstRewardService();
    }

    /** 首次领取发放 +500。 */
    @Test
    void firstClaimGrantsFullReward() {
        assertEquals(500, rewardService.claimFirstReward(CropType.WHEAT));
        assertTrue(rewardService.hasClaimed(CropType.WHEAT));
    }

    /** 同一传说再次领取为 0。 */
    @Test
    void secondClaimSameTypeGivesZero() {
        rewardService.claimFirstReward(CropType.WHEAT);

        assertEquals(0, rewardService.claimFirstReward(CropType.WHEAT));
    }

    /** 三种传说分别独立领取一次。 */
    @Test
    void eachLegendaryTypeClaimsIndependently() {
        assertEquals(500, rewardService.claimFirstReward(CropType.WHEAT));
        assertEquals(500, rewardService.claimFirstReward(CropType.CORN));
        assertEquals(500, rewardService.claimFirstReward(CropType.CARROT));

        assertEquals(0, rewardService.claimFirstReward(CropType.WHEAT));
        assertEquals(0, rewardService.claimFirstReward(CropType.CORN));
        assertEquals(0, rewardService.claimFirstReward(CropType.CARROT));
    }

    /** 未领取过的类型 hasClaimed 为 false。 */
    @Test
    void hasClaimedFalseBeforeFirstClaim() {
        assertFalse(rewardService.hasClaimed(CropType.CARROT));
    }
}
