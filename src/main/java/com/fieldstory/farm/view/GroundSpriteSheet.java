package com.fieldstory.farm.view;

/**
 * 地面贴图图集元数据（A 模块 P1 地面 Tile 美化；UI规范 §6 地图、§7 Tile 组合策略）。
 *
 * <p>地面贴图来自 {@code /assets/tiles/ground_01_16x16.png}（256×256，
 * 16×16 帧网格，单帧 16×16）。帧坐标已人工对照原图确认（勘察原始数据见
 * {@code .qoder-temp/ground_survey.txt}），禁止自行更改：
 * GRASS (col=3,row=6)、TILLED (col=3,row=1)、WET (col=8,row=10)。
 *
 * <p>本枚举只记录纯数据，不加载图片、不创建 JavaFX 节点（任务约束：
 * 纯函数/纯数据层）。帧坐标经 {@link #frameX} / {@link #frameY} 换算为
 * viewport 像素坐标，供加载器切取单帧复用。
 *
 * <p>显示策略见《A模块-P1地面Tile美化决策记录.md》：
 * D-G1 贴图 2× 缩放（32×32）居中显示、保留格子底色；
 * D-G2 湿判定 = 今日已浇或今日天气 ∈ {RAIN, GREEN_RAIN}；
 * D-G3 MATURE/WITHERED/EMPTY/LOCKED 不铺贴图；
 * D-G5 移除 tile 1px 描边、选中高亮保留。
 */
public enum GroundSpriteSheet {

    /** 地面图集（唯一）：256×256，16×16 帧网格 */
    GROUND("/assets/tiles/ground_01_16x16.png", 3, 6, 3, 1, 8, 10);

    /** 单帧尺寸：16×16（16 帧/行 × 16 帧/列 = 256×256 整图） */
    public static final int FRAME_SIZE = 16;

    /** classpath 资源路径（相对 resources 根，以 /assets/ 开头） */
    private final String classpath;

    /** 草地帧列/行（人工对照原图确认，禁止自行更改） */
    private final int grassCol;
    private final int grassRow;

    /** 耕地干帧列/行（人工对照原图确认，禁止自行更改） */
    private final int tilledCol;
    private final int tilledRow;

    /** 湿地深色帧列/行（人工对照原图确认，禁止自行更改） */
    private final int wetCol;
    private final int wetRow;

    GroundSpriteSheet(String classpath, int grassCol, int grassRow,
                      int tilledCol, int tilledRow, int wetCol, int wetRow) {
        this.classpath = classpath;
        this.grassCol = grassCol;
        this.grassRow = grassRow;
        this.tilledCol = tilledCol;
        this.tilledRow = tilledRow;
        this.wetCol = wetCol;
        this.wetRow = wetRow;
    }

    public String getClasspath() {
        return classpath;
    }

    public int getGrassCol() {
        return grassCol;
    }

    public int getGrassRow() {
        return grassRow;
    }

    public int getTilledCol() {
        return tilledCol;
    }

    public int getTilledRow() {
        return tilledRow;
    }

    public int getWetCol() {
        return wetCol;
    }

    public int getWetRow() {
        return wetRow;
    }

    /**
     * 帧列 → viewport x 像素（col × FRAME_SIZE），供加载器切取单帧复用。
     *
     * @param col 帧列（0-based）
     * @return viewport x 坐标（像素）
     */
    public static int frameX(int col) {
        return col * FRAME_SIZE;
    }

    /**
     * 帧行 → viewport y 像素（row × FRAME_SIZE），供加载器切取单帧复用。
     *
     * @param row 帧行（0-based）
     * @return viewport y 坐标（像素）
     */
    public static int frameY(int row) {
        return row * FRAME_SIZE;
    }
}
