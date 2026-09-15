package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CollectionStatus;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.impl.BasicCollectionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 收集图鉴服务测试（E 模块 P3；验收规范 §一百一十~§一百一十八、§一百三十二）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>三态推进（未发现 → 已发现 → 已收集）与「只前进不回退」；</li>
 *   <li>作物图鉴以「作物 × 品质」为粒度，收集稀有小麦不等于解锁普通小麦（§一百一十一）；</li>
 *   <li>装饰 / 传说去重（重复收集不新增），抑制重复计分（§一百二十）。</li>
 * </ul>
 */
class CollectionServiceTest {

    private static GameState newState() {
        return new GameState(new Player("农夫", 500), 0L);
    }

    @Test
    void cropStartsUndiscoveredAndAdvancesThroughThreeStates() {
        CollectionService service = new BasicCollectionService(newState());
        assertEquals(CollectionStatus.UNDISCOVERED, service.cropStatus(CropType.WHEAT, Quality.RARE));

        service.discoverCrop(CropType.WHEAT, Quality.RARE);
        assertEquals(CollectionStatus.DISCOVERED, service.cropStatus(CropType.WHEAT, Quality.RARE));
        assertEquals(0, service.collectedCropCount(), "已发现但未收集不计入收集数");

        service.collectCrop(CropType.WHEAT, Quality.RARE);
        assertEquals(CollectionStatus.COLLECTED, service.cropStatus(CropType.WHEAT, Quality.RARE));
        assertEquals(1, service.collectedCropCount());
    }

    @Test
    void collectIsPermanentAndNeverDowngrades() {
        CollectionService service = new BasicCollectionService(newState());
        service.collectCrop(CropType.WHEAT, Quality.RARE);
        // 之后再「发现」不应把已收集降级（图鉴永久保存，§一百一十二）
        service.discoverCrop(CropType.WHEAT, Quality.RARE);
        assertEquals(CollectionStatus.COLLECTED, service.cropStatus(CropType.WHEAT, Quality.RARE));
    }

    @Test
    void cropKeyIsCropTimesQualityNotAutoUnlockSiblings() {
        CollectionService service = new BasicCollectionService(newState());
        service.collectCrop(CropType.WHEAT, Quality.RARE);
        // 收集稀有小麦不会自动解锁普通 / 优秀小麦（§一百一十一）
        assertEquals(CollectionStatus.UNDISCOVERED, service.cropStatus(CropType.WHEAT, Quality.COMMON));
        assertEquals(CollectionStatus.UNDISCOVERED, service.cropStatus(CropType.WHEAT, Quality.EXCELLENT));
        assertEquals(1, service.collectedCropCount());
    }

    @Test
    void allFifteenCropTargetsCanBeCollected() {
        CollectionService service = new BasicCollectionService(newState());
        for (CropType cropType : CropType.values()) {
            for (Quality quality : Quality.values()) {
                service.collectCrop(cropType, quality);
            }
        }
        assertEquals(CollectionService.CROP_TARGET, service.collectedCropCount(),
                "3 作物 × 5 品质 = 15（§一百一十）");
    }

    @Test
    void decorationCollectionIsDeduplicated() {
        CollectionService service = new BasicCollectionService(newState());
        service.collectDecoration("D01");
        service.collectDecoration("D01");
        service.collectDecoration("D02");
        assertTrue(service.isDecorationCollected("D01"));
        assertFalse(service.isDecorationCollected("D03"));
        assertEquals(2, service.collectedDecorationCount(), "重复购买同一装饰不重复增加");
    }

    @Test
    void legendaryCollectionIsDeduplicated() {
        CollectionService service = new BasicCollectionService(newState());
        service.collectLegendary(CropType.WHEAT);
        service.collectLegendary(CropType.WHEAT);
        service.collectLegendary(CropType.CORN);
        assertTrue(service.isLegendaryCollected(CropType.WHEAT));
        assertFalse(service.isLegendaryCollected(CropType.CARROT));
        assertEquals(2, service.collectedLegendaryCount(), "同种传说收获多株只记一次（§一百二十）");
    }

    @Test
    void collectionIsStoredInGameStateSoItPersists() {
        GameState state = newState();
        CollectionService service = new BasicCollectionService(state);
        service.collectCrop(CropType.CORN, Quality.EPIC);
        // 直接写入 GameState.CollectionState，供存档全量覆盖
        assertTrue(state.getCollection().cropStatus(CropType.CORN, Quality.EPIC).isCollected());
    }

    @Test
    void nullCropOrQualityIsIgnoredWithoutThrowing() {
        GameState state = newState();
        CollectionService service = new BasicCollectionService(state);
        // 传 null 不得抛 NPE（与 cropStatus 的 null 语义一致），也不得把 null 键写进图鉴
        service.collectCrop(null, Quality.RARE);
        service.collectCrop(CropType.WHEAT, null);
        service.discoverCrop(null, null);
        assertEquals(0, service.collectedCropCount());
        assertTrue(state.getCollection().getCrops().isEmpty(), "null 键不得落库");
    }
}
