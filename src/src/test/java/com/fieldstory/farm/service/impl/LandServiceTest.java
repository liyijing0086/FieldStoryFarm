package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicSoil;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.service.ReclaimResult;
import com.fieldstory.farm.testutil.TestEconomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BasicLandService} 测试（验收规范 §十五、§十六、§三十三；
 * A 模块设计文档 §8.1；决策 D09）。
 *
 * <p>依赖经 {@link TestEconomyService} 测试桩注入（D 交付前 GameClock 未用，
 * 开垦不依赖时间）；不使用系统时间、不使用随机数。
 */
class LandServiceTest {

    private TestEconomyService economyService;
    private LandService landService;

    @BeforeEach
    void setUp() {
        economyService = new TestEconomyService();
        landService = new BasicLandService(economyService);
    }

    /** 辅助：以指定状态构造土地（(2,2) 为中心 8×8 种植区格）。 */
    private Soil soil(SoilState state) {
        Soil soil = new BasicSoil(2, 2);
        soil.setState(state);
        return soil;
    }

    /**
     * 开垦成功：EMPTY + 金币充足 → 先扣 5 金币、再置 TILLED
     * （验收规范 §十五；规则文档 §12.1 开垦消耗 5 金币）。
     */
    @Test
    void reclaimSuccessDeducts5Gold() {
        economyService.setGold(10);
        Soil soil = soil(SoilState.EMPTY);

        assertEquals(ReclaimResult.SUCCESS, landService.reclaim(soil));
        assertEquals(5, economyService.getGold());
        assertEquals(SoilState.TILLED, soil.getState());
    }

    /**
     * 金币不足 → NO_GOLD：不扣钱、不改地（验收规范 §十五"金币不足"分支）。
     */
    @Test
    void reclaimNoGoldLeavesGoldAndSoilUnchanged() {
        economyService.setGold(4);
        Soil soil = soil(SoilState.EMPTY);

        assertEquals(ReclaimResult.NO_GOLD, landService.reclaim(soil));
        assertEquals(4, economyService.getGold());
        assertEquals(SoilState.EMPTY, soil.getState());
    }

    /**
     * 非 EMPTY 开垦 → NOT_EMPTY 且金币与土地均不变
     * （验收规范 §十六：LOCKED 开垦等非法土地行为拦截）。
     */
    @Test
    void reclaimOnNonEmptyRejected() {
        economyService.setGold(10);
        Soil tilled = soil(SoilState.TILLED);

        assertEquals(ReclaimResult.NOT_EMPTY, landService.reclaim(tilled));
        assertEquals(10, economyService.getGold());
        assertEquals(SoilState.TILLED, tilled.getState());
    }

    /**
     * canReclaim 三分支：EMPTY+足金 → true；EMPTY+不足 → false；
     * 非 EMPTY → false（A 模块设计文档 §8.1 前置校验）。
     */
    @Test
    void canReclaimThreeBranches() {
        economyService.setGold(10);
        Soil empty = soil(SoilState.EMPTY);
        assertTrue(landService.canReclaim(empty));

        economyService.setGold(4);
        assertFalse(landService.canReclaim(empty));

        assertFalse(landService.canReclaim(soil(SoilState.TILLED)));
    }

    /**
     * removeCropAndSetTilled：清 crop 并置 TILLED（验收规范 §三十三；
     * 决策 D09：C 收获后调用，土地状态机唯一入口保持在 A）。
     */
    @Test
    void removeCropAndSetTilledClearsCrop() {
        Soil planted = soil(SoilState.PLANTED);
        // 手动构造作物（测试禁用 CropFactory，避免随机 UUID）
        Crop crop = new BasicCrop();
        crop.setCropType(CropType.WHEAT);
        planted.setCrop(crop);

        landService.removeCropAndSetTilled(planted);

        assertNull(planted.getCrop());
        assertEquals(SoilState.TILLED, planted.getState());
    }
}
