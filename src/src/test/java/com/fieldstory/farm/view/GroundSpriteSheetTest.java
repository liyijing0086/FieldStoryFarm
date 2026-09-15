package com.fieldstory.farm.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GroundSpriteSheet} 纯数据测试（UI规范 §6 地图、§7 Tile 组合策略）。
 *
 * <p>帧坐标为人工对照原图确认的勘察数据（.qoder-temp/ground_survey.txt），
 * 与决策文档《A模块-P1地面Tile美化决策记录.md》帧坐标表一致，禁止自行更改。
 */
class GroundSpriteSheetTest {

    @Test
    void classpathIsGroundSheet() {
        assertEquals("/assets/tiles/ground_01_16x16.png",
                GroundSpriteSheet.GROUND.getClasspath());
    }

    @Test
    void frameSizeIs16() {
        assertEquals(16, GroundSpriteSheet.FRAME_SIZE);
    }

    /** GRASS（草地）：col=3, row=6（人工对照原图确认） */
    @Test
    void grassFrameIsCol3Row6() {
        assertEquals(3, GroundSpriteSheet.GROUND.getGrassCol());
        assertEquals(6, GroundSpriteSheet.GROUND.getGrassRow());
    }

    /** TILLED（耕地干）：col=3, row=1（人工对照原图确认） */
    @Test
    void tilledFrameIsCol3Row1() {
        assertEquals(3, GroundSpriteSheet.GROUND.getTilledCol());
        assertEquals(1, GroundSpriteSheet.GROUND.getTilledRow());
    }

    /** WET（湿地深色）：col=8, row=10（人工对照原图确认） */
    @Test
    void wetFrameIsCol8Row10() {
        assertEquals(8, GroundSpriteSheet.GROUND.getWetCol());
        assertEquals(10, GroundSpriteSheet.GROUND.getWetRow());
    }

    @Test
    void frameXMultipliesColBy16() {
        assertEquals(0, GroundSpriteSheet.frameX(0));
        assertEquals(48, GroundSpriteSheet.frameX(3));
        assertEquals(128, GroundSpriteSheet.frameX(8));
        assertEquals(240, GroundSpriteSheet.frameX(15));
    }

    @Test
    void frameYMultipliesRowBy16() {
        assertEquals(0, GroundSpriteSheet.frameY(0));
        assertEquals(16, GroundSpriteSheet.frameY(1));
        assertEquals(96, GroundSpriteSheet.frameY(6));
        assertEquals(160, GroundSpriteSheet.frameY(10));
    }
}
