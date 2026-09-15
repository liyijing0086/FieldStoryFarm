package com.fieldstory.farm.view;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A 模块作物贴图切片加载器（P1 作物贴图接入渲染接入卡；UI规范 §7 Tile 组合策略、
 * §8 作物资源规范）。
 *
 * <p>按作物类型与生长阶段从横向帧图集（{@link CropSpriteSheet}）切出单帧贴图，
 * 统一 2× 整数倍放大（决策 D1）、最近邻采样 setSmooth(false) 保持像素风
 * （星露谷式底对齐、允许向上越界由渲染接入卡负责，本类不处理定位）。
 *
 * <p>图片按 classpath 缓存于 {@link ConcurrentHashMap}（key=classpath），
 * 避免同一图集被重复解码（同 BImageAssets 模式）；单帧切取依赖
 * {@link FarmView#cropFrameIndexFor} 的阶段→帧映射（SEED→0、SPROUT→2、
 * GROWING→4、MATURE→末帧，决策 D2/D3）。
 *
 * <p>坏数据兜底：作物类型/阶段无法识别、图集缺失或加载失败，一律返回 null，
 * 不抛异常、不弹错误框；ImageView 创建仅允许在 FX 线程发生（由渲染接入卡
 * 在 FarmView 内部调用本方法）。
 */
final class AImageAssets {

    /** 统一 2× 整数倍放大（决策 D1） */
    public static final int SCALE = 2;

    /** 按 classpath 缓存的全尺寸图集图片 */
    private static final Map<String, Image> CACHE =
            new ConcurrentHashMap<>();

    private AImageAssets() {
    }

    /**
     * 按作物类型与生长阶段创建切片贴图视图。
     *
     * <p>流程：图集元数据 → 帧索引 → 缓存取图 → viewport 切片 → 2× 放大。
     * 任一步骤遇坏数据（类型/阶段/图集缺失或加载失败）返回 null。
     *
     * @param type  作物类型（可为 null）
     * @param stage 成长阶段（可为 null）
     * @return 切片贴图视图；无法识别或加载失败时为 null
     */
    static ImageView viewFor(CropType type, GrowthStage stage) {
        if (type == null || stage == null) {
            return null;
        }
        CropSpriteSheet sheet = CropSpriteSheet.forCropType(type);
        if (sheet == null) {
            return null;
        }

        int frameIndex = FarmView.cropFrameIndexFor(stage, sheet.getFrameCount());
        if (frameIndex < 0) {
            // -1 为「不显示贴图」哨兵（决策 D3：WITHERED 不显示贴图）
            return null;
        }

        Image image = CACHE.computeIfAbsent(
                sheet.getClasspath(),
                AImageAssets::loadUncached
        );
        if (image == null) {
            return null;
        }

        ImageView view = new ImageView(image);
        view.setViewport(new Rectangle2D(
                (double) frameIndex * sheet.getFrameWidth(),
                0,
                sheet.getFrameWidth(),
                sheet.getFrameHeight()
        ));
        view.setFitWidth(sheet.getFrameWidth() * SCALE);
        view.setFitHeight(sheet.getFrameHeight() * SCALE);
        view.setPreserveRatio(false);
        view.setSmooth(false);          // 像素风：最近邻不平滑（决策 D1）
        view.setMouseTransparent(true); // 鼠标穿透：悬停/点击作用于地块

        return view;
    }

    /**
     * P4 生产地图用作物视图：保持原 viewport 逻辑，但把最终视觉尺寸限制在
     * {@code maxSize × maxSize} 内并保持宽高比，避免 18×32 图集按旧 2× 规则
     * 变成 36×64 后悬出 44px Tile。旧 {@link #viewFor} 保持不变，兼容既有测试。
     */
    static ImageView viewForTile(CropType type, GrowthStage stage, double maxSize) {
        ImageView legacy = viewFor(type, stage);
        if (legacy == null || legacy.getViewport() == null || maxSize <= 0) {
            return legacy;
        }
        double frameWidth = legacy.getViewport().getWidth();
        double frameHeight = legacy.getViewport().getHeight();
        double scale = Math.min(maxSize / frameWidth, maxSize / frameHeight);

        ImageView view = new ImageView(legacy.getImage());
        view.setViewport(legacy.getViewport());
        view.setFitWidth(Math.max(1, Math.floor(frameWidth * scale)));
        view.setFitHeight(Math.max(1, Math.floor(frameHeight * scale)));
        view.setPreserveRatio(false);
        view.setSmooth(false);
        view.setMouseTransparent(true);
        return view;
    }

    /**
     * 未命中缓存时按 classpath 同步加载全尺寸图集。
     *
     * <p>requestedWidth/Height 传 0 表示按原图实际尺寸解码（切片需要全尺寸图）；
     * backgroundLoading=false 同步加载，保证 {@link Image#isError()} 判定可靠。
     * getResource 为 null 或解码失败一律按缺失处理返回 null（不抛异常）。
     *
     * @param classpath 图集资源路径
     * @return 全尺寸图集图片；缺失或加载失败时为 null
     */
    private static Image loadUncached(String classpath) {
        URL url = AImageAssets.class.getResource(classpath);

        if (url == null) {
            return null;
        }

        Image image = new Image(
                url.toExternalForm(),
                0,
                0,
                true,
                false,
                false
        );

        return image.isError()
                ? null
                : image;
    }
}
