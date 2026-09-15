package com.fieldstory.farm.view;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A 模块地面贴图切片加载器（P1 地面 Tile 美化渲染接入卡；UI规范 §6 地图、
 * §7 Tile 组合策略）。
 *
 * <p>按地面贴图变体从地面图集（{@link GroundSpriteSheet}）切出单帧贴图，
 * 统一 2× 整数倍放大（决策 D-G1：16×16 → 32×32）、最近邻采样
 * setSmooth(false) 保持像素风；渲染接入时由 FarmView 在 44×44 格子内
 * 居中显示，格子底色保留（本类不处理定位）。
 *
 * <p>图片按 classpath 缓存于 {@link ConcurrentHashMap}（key=classpath），
 * 避免同一图集被重复解码（同 AImageAssets 模式）；单帧切取依赖
 * {@link GroundSpriteSheet#frameX} / {@link GroundSpriteSheet#frameY}
 * 的帧坐标 → viewport 像素换算（帧坐标为人工对照原图确认的勘察数据，
 * 禁止自行更改）。
 *
 * <p>坏数据兜底：变体无法识别、图集缺失或加载失败，一律返回 null，
 * 不抛异常、不弹错误框；ImageView 创建仅允许在 FX 线程发生
 * （本类只被 FarmView 调用，由 FarmView 保证 FX 线程）。
 */
final class ATileAssets {

    /** 统一 2× 整数倍放大（决策 D-G1：16×16 → 32×32） */
    public static final int SCALE = 2;

    /** 按 classpath 缓存的全尺寸图集图片 */
    private static final Map<String, Image> CACHE =
            new ConcurrentHashMap<>();

    private ATileAssets() {
    }

    /**
     * 按地面贴图变体创建切片贴图视图。
     *
     * <p>流程：变体 → 帧坐标 → 缓存取图 → viewport 切片 → 2× 放大。
     * 任一步骤遇坏数据（变体为 null/NONE/未知、图集缺失或加载失败）
     * 返回 null。
     *
     * @param variant 地面贴图变体（可为 null）
     * @return 切片贴图视图；NONE 或无法识别或加载失败时为 null
     */
    static ImageView viewFor(GroundVariant variant) {
        if (variant == null || variant == GroundVariant.NONE) {
            // null 与 NONE 均为「不铺贴图」哨兵（决策 D-G3：不抛 NPE）
            return null;
        }

        GroundSpriteSheet sheet = GroundSpriteSheet.GROUND;
        int col;
        int row;
        switch (variant) {
            case GRASS:
                col = sheet.getGrassCol();
                row = sheet.getGrassRow();
                break;
            case TILLED:
                col = sheet.getTilledCol();
                row = sheet.getTilledRow();
                break;
            case WET:
                col = sheet.getWetCol();
                row = sheet.getWetRow();
                break;
            default:
                // 未知变体（坏数据）：不铺贴图
                return null;
        }

        Image image = CACHE.computeIfAbsent(
                sheet.getClasspath(),
                ATileAssets::loadUncached
        );
        if (image == null) {
            return null;
        }

        ImageView view = new ImageView(image);
        view.setViewport(new Rectangle2D(
                GroundSpriteSheet.frameX(col),
                GroundSpriteSheet.frameY(row),
                GroundSpriteSheet.FRAME_SIZE,
                GroundSpriteSheet.FRAME_SIZE
        ));
        view.setFitWidth(GroundSpriteSheet.FRAME_SIZE * SCALE);
        view.setFitHeight(GroundSpriteSheet.FRAME_SIZE * SCALE);
        view.setPreserveRatio(false);
        view.setSmooth(false);          // 像素风：最近邻不平滑（决策 D-G1）
        view.setMouseTransparent(true); // 鼠标穿透：悬停/点击作用于地块

        return view;
    }

    /**
     * P4 生产地图用满格地面贴图。旧 {@link #viewFor} 继续保留 32×32 与旧 viewport，
     * 以免破坏 P1 的历史测试；正式 FarmView 使用本方法把贴图铺满 44×44 Tile。
     *
     * <p>免费素材图集中的农地帧本身带有细微边缘/色差。按行列做确定性选择，
     * 可以打散“一整块纯色矩形”的观感，同时不引入随机状态。
     */
    static ImageView viewForTile(GroundVariant variant, int row, int column, double tileSize) {
        if (variant == null || variant == GroundVariant.NONE || tileSize <= 0) {
            return null;
        }
        GroundSpriteSheet sheet = GroundSpriteSheet.GROUND;
        int[][] frames;
        switch (variant) {
            case GRASS -> frames = new int[][]{
                    {2, 7}, {10, 1}, {4, 9}, {3, 6}
            };
            case TILLED -> frames = new int[][]{
                    // 免费图集中的干净土壤内格。P4 不再使用圆角/条状 autotile，
                    // 避免 8×8 农田拼成“砖墙/皮肤病”纹理。
                    {5, 1}
            };
            case WET -> frames = new int[][]{
                    // 湿地仍复用同一土壤帧，颜色差异由 FarmView 的表现层 ColorAdjust 给出。
                    {5, 1}
            };
            default -> { return null; }
        }
        int index = Math.floorMod(row * 7 + column * 3, frames.length);
        int col = frames[index][0];
        int frameRow = frames[index][1];

        Image image = CACHE.computeIfAbsent(sheet.getClasspath(), ATileAssets::loadUncached);
        if (image == null) {
            return null;
        }
        ImageView view = new ImageView(image);
        view.setViewport(new Rectangle2D(
                GroundSpriteSheet.frameX(col),
                GroundSpriteSheet.frameY(frameRow),
                GroundSpriteSheet.FRAME_SIZE,
                GroundSpriteSheet.FRAME_SIZE));
        view.setFitWidth(tileSize);
        view.setFitHeight(tileSize);
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
        URL url = ATileAssets.class.getResource(classpath);

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
