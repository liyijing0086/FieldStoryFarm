package com.fieldstory.farm.model;

import com.fieldstory.farm.service.FarmRankService;
import com.fieldstory.farm.service.impl.BasicFarmRankService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 农场评价测试（E 模块 P3；验收规范 §一百二十一，规则文档 §七十四/§七十六）。
 *
 * <p>验证 8 级阈值与边界：0~9 新手农场、≥10 田园小筑、≥30 花园农场、≥55 美丽庄园、
 * ≥80 繁花似锦、≥105 自然天堂、≥125 传奇庄园、147 永恒花园。
 */
class FarmRankTest {

    private final FarmRankService service = new BasicFarmRankService();

    @Test
    void eightRanksAreDefined() {
        assertEquals(8, FarmRank.values().length, "共 8 级评价（§一百二十一）");
    }

    @Test
    void boundariesMapToExpectedRanks() {
        assertEquals(FarmRank.NOVICE, service.rankOf(0));
        assertEquals(FarmRank.NOVICE, service.rankOf(9), "0~9 新手农场");
        assertEquals(FarmRank.COTTAGE, service.rankOf(10), "≥10 田园小筑");
        assertEquals(FarmRank.COTTAGE, service.rankOf(29));
        assertEquals(FarmRank.GARDEN, service.rankOf(30), "≥30 花园农场");
        assertEquals(FarmRank.GARDEN, service.rankOf(54));
        assertEquals(FarmRank.MANOR, service.rankOf(55), "≥55 美丽庄园");
        assertEquals(FarmRank.MANOR, service.rankOf(79));
        assertEquals(FarmRank.BLOOMING, service.rankOf(80), "≥80 繁花似锦");
        assertEquals(FarmRank.BLOOMING, service.rankOf(104));
        assertEquals(FarmRank.NATURE_HEAVEN, service.rankOf(105), "≥105 自然天堂");
        assertEquals(FarmRank.NATURE_HEAVEN, service.rankOf(124));
        assertEquals(FarmRank.LEGEND_MANOR, service.rankOf(125), "≥125 传奇庄园");
        assertEquals(FarmRank.LEGEND_MANOR, service.rankOf(146), "146 仍是传奇庄园，不是永恒花园");
        assertEquals(FarmRank.ETERNAL_GARDEN, service.rankOf(147), "147 永恒花园（唯一毕业评价）");
    }

    @Test
    void onlyFullScoreIsGraduationRank() {
        assertFalse(service.rankOf(146).isGraduationRank(), "146 绝不能触发毕业（§一百三十二 ⑨）");
        assertTrue(service.rankOf(147).isGraduationRank());
    }

    @Test
    void negativeScoreClampsToNovice() {
        assertEquals(FarmRank.NOVICE, service.rankOf(-5));
    }

    @Test
    void displayNameComesFromRank() {
        assertEquals("永恒花园", service.displayNameOf(147));
        assertEquals("新手农场", service.displayNameOf(3));
    }
}
