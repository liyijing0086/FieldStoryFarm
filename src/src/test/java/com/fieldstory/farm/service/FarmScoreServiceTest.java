package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.FarmScoreBreakdown;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.impl.BasicCollectionService;
import com.fieldstory.farm.service.impl.BasicFarmScoreService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FarmScore 服务测试（E 模块 P3；验收规范 §一百一十九~§一百二十、§一百三十一）。
 *
 * <p>核心断言：构造 14 装饰 + 15 作物图鉴 + 3 传说 + 3 套装 → 恰好 147；
 * 去掉任意一项必要收藏则 &lt;147；重复收藏不重复加分。
 */
class FarmScoreServiceTest {

    /** 3 套装 id（套装成员判定属 B 模块，这里只验证 FarmScore 计数）。 */
    private static final String[] SET_IDS = {"NATURAL_BREATH", "HARVEST_SOUL", "LEGEND_LIGHT"};

    /** 14 装饰 id（D01~D14，验收规范 §一百一十三）。 */
    private static final int DECORATION_COUNT = 14;

    private static GameState emptyState() {
        return new GameState(new Player("农夫", 500), 0L);
    }

    /** 把状态填成满收集：14 装饰 + 15 作物图鉴 + 3 传说 + 3 套装。 */
    private static void fillFullCollection(GameState state) {
        CollectionService collection = new BasicCollectionService(state);
        for (int i = 1; i <= DECORATION_COUNT; i++) {
            collection.collectDecoration(String.format("D%02d", i));
        }
        for (CropType cropType : CropType.values()) {
            for (Quality quality : Quality.values()) {
                collection.collectCrop(cropType, quality);
            }
        }
        for (CropType cropType : CropType.values()) {
            collection.collectLegendary(cropType);
        }
        for (String setId : SET_IDS) {
            state.getSetCollection().getCollected().add(setId);
        }
    }

    @Test
    void fullCollectionScoresExactly147() {
        GameState state = emptyState();
        fillFullCollection(state);
        FarmScoreService service = new BasicFarmScoreService(state);

        FarmScoreBreakdown breakdown = service.breakdown();
        assertEquals(42, breakdown.decorationScore(), "14 × 3 = 42");
        assertEquals(30, breakdown.collectionScore(), "15 × 2 = 30");
        assertEquals(30, breakdown.legendaryScore(), "3 × 10 = 30");
        assertEquals(45, breakdown.setScore(), "3 × 15 = 45");
        assertEquals(147, service.totalScore(), "满分 147（§一百一十九）");
        assertTrue(service.isMaxScore());
    }

    @Test
    void emptyCollectionScoresZero() {
        FarmScoreService service = new BasicFarmScoreService(emptyState());
        assertEquals(0, service.totalScore());
    }

    @Test
    void removingAnyRequiredCollectionDropsBelow147() {
        GameState state = emptyState();
        fillFullCollection(state);
        // 去掉一个作物图鉴项：15 → 14，分数 -2
        state.getCollection().getCrops().remove(
                new com.fieldstory.farm.model.CropQualityKey(CropType.WHEAT, Quality.COMMON));
        FarmScoreService service = new BasicFarmScoreService(state);
        assertEquals(145, service.totalScore());
        assertTrue(service.totalScore() < 147, "缺一项必要收藏必须 <147（§一百三十一）");
    }

    @Test
    void duplicateDecorationAndLegendaryDoNotDoubleCount() {
        GameState state = emptyState();
        CollectionService collection = new BasicCollectionService(state);
        // 购买 5 株向日葵 → 装饰仍只算 1 种
        for (int i = 0; i < 5; i++) {
            collection.collectDecoration("D01");
        }
        // 收获 10 株金色麦穗 → 传说仍只算 1 种
        for (int i = 0; i < 10; i++) {
            collection.collectLegendary(CropType.WHEAT);
        }
        FarmScoreService service = new BasicFarmScoreService(state);
        assertEquals(3, service.breakdown().decorationScore(), "向日葵装饰分仍只有 3（§一百二十）");
        assertEquals(10, service.breakdown().legendaryScore(), "金色麦穗传说分仍只有 10（§一百二十）");
        assertEquals(13, service.totalScore());
    }

    @Test
    void setScoreKeepsAfterMembersRemovedButActiveCleared() {
        // 完成套装后收起成员：collected 保留（FarmScore 留分）、active 清空（Buff 停止，§一百一十八）
        GameState state = emptyState();
        state.getSetCollection().getCollected().add("NATURAL_BREATH");
        state.getSetCollection().getActive().add("NATURAL_BREATH");
        FarmScoreService service = new BasicFarmScoreService(state);
        assertEquals(15, service.breakdown().setScore());

        state.getSetCollection().getActive().clear();
        assertEquals(15, service.breakdown().setScore(), "setCollected 不变，FarmScore 保留套装分");
        assertTrue(state.getSetCollection().getActive().isEmpty(), "setActive 独立，Buff 停止");
    }
}
