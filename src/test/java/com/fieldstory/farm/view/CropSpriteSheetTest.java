package com.fieldstory.farm.view;

import com.fieldstory.farm.model.CropType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link CropSpriteSheet} 图集元数据测试（A 模块 P1 作物贴图接入）。
 *
 * <p>元数据断言与素材文件名自描述一致：
 * wheat_18x32_8frames.png / corn_16x32_8frames.png / carrot_16x16_7frames.png；
 * 不加载图片、不创建 JavaFX 节点（纯数据层，无需 GUI 线程）。
 */
class CropSpriteSheetTest {

    @Test
    void wheatMetadataMatchesAssetFileName() {
        CropSpriteSheet sheet = CropSpriteSheet.WHEAT;
        assertEquals(CropType.WHEAT, sheet.getCropType());
        assertEquals("/assets/crops/wheat_18x32_8frames.png", sheet.getClasspath());
        assertEquals(18, sheet.getFrameWidth());
        assertEquals(32, sheet.getFrameHeight());
        assertEquals(8, sheet.getFrameCount());
    }

    @Test
    void cornMetadataMatchesAssetFileName() {
        CropSpriteSheet sheet = CropSpriteSheet.CORN;
        assertEquals(CropType.CORN, sheet.getCropType());
        assertEquals("/assets/crops/corn_16x32_8frames.png", sheet.getClasspath());
        assertEquals(16, sheet.getFrameWidth());
        assertEquals(32, sheet.getFrameHeight());
        assertEquals(8, sheet.getFrameCount());
    }

    @Test
    void carrotMetadataMatchesAssetFileName() {
        CropSpriteSheet sheet = CropSpriteSheet.CARROT;
        assertEquals(CropType.CARROT, sheet.getCropType());
        assertEquals("/assets/crops/carrot_16x16_7frames.png", sheet.getClasspath());
        assertEquals(16, sheet.getFrameWidth());
        assertEquals(16, sheet.getFrameHeight());
        assertEquals(7, sheet.getFrameCount());
    }

    /** 坏数据兜底：crop_type 允许 NULL（P1 设计文档 §3），forCropType(null) 返回 null 不抛异常。 */
    @Test
    void forCropTypeWithNullReturnsNull() {
        assertNull(CropSpriteSheet.forCropType(null));
    }
}
