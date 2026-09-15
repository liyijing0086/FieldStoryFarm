package com.fieldstory.farm.view;

import com.fieldstory.farm.model.CropType;

/**
 * 作物生长图集元数据（A 模块 P1 作物贴图接入；UI规范 §8 作物资源规范）。
 *
 * <p>每项枚举对应一张横向帧图集（多帧拼在一张 PNG 内），
 * 元数据取自素材文件名自描述（如 {@code wheat_18x32_8frames.png}）：
 * 单帧宽 × 单帧高、总帧数。本枚举只记录纯数据，不加载图片、
 * 不创建 JavaFX 节点（任务约束：纯函数/纯数据层）。
 *
 * <p>P1 仅接入 P0 三种基础作物（验收规范 §十七）；其余作物（bamboo、
 * beetroot 等）的图集元数据待对应作物启用时再补录。
 *
 * <p>显示策略见《A模块-P1作物贴图接入决策记录.md》：
 * D1 统一 2× 整数倍放大、底对齐允许向上越界；
 * D2 MATURE 显示末帧贴图并保留整格高亮底色；
 * D3 WITHERED 不显示贴图（{@link FarmView#cropFrameIndexFor} 返回 -1）。
 */
public enum CropSpriteSheet {

    /** 小麦图集：单帧 18×32、8 帧（素材文件名自描述） */
    WHEAT(CropType.WHEAT, "/assets/crops/wheat_18x32_8frames.png", 18, 32, 8),

    /** 玉米图集：单帧 16×32、8 帧（素材文件名自描述） */
    CORN(CropType.CORN, "/assets/crops/corn_16x32_8frames.png", 16, 32, 8),

    /** 胡萝卜图集：单帧 16×16、7 帧（素材文件名自描述） */
    CARROT(CropType.CARROT, "/assets/crops/carrot_16x16_7frames.png", 16, 16, 7);

    /** 关联作物类型 */
    private final CropType cropType;

    /** classpath 资源路径（相对 resources 根，以 /assets/ 开头） */
    private final String classpath;

    /** 单帧宽度（像素） */
    private final int frameWidth;

    /** 单帧高度（像素） */
    private final int frameHeight;

    /** 总帧数（横向排列） */
    private final int frameCount;

    CropSpriteSheet(CropType cropType, String classpath,
                    int frameWidth, int frameHeight, int frameCount) {
        this.cropType = cropType;
        this.classpath = classpath;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frameCount = frameCount;
    }

    public CropType getCropType() {
        return cropType;
    }

    public String getClasspath() {
        return classpath;
    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }

    public int getFrameCount() {
        return frameCount;
    }

    /**
     * 按作物类型查图集元数据。
     *
     * <p>入参 null 或未收录的作物返回 null（不抛异常），
     * 与坏数据兜底口径一致（P1 设计文档 §3：crop_type 允许 NULL，
     * 适配层降级 null 后下游不得抛 NPE）。
     *
     * @param cropType 作物类型（可为 null）
     * @return 对应图集元数据；null 或未收录时返回 null
     */
    public static CropSpriteSheet forCropType(CropType cropType) {
        if (cropType == null) {
            return null;
        }
        for (CropSpriteSheet sheet : values()) {
            if (sheet.cropType == cropType) {
                return sheet;
            }
        }
        return null;
    }
}
