package com.fieldstory.farm.view;

import com.fieldstory.farm.model.CropType;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

/**
 * P4 传奇作物展示图资源映射。
 *
 * <p>这些图片只用于图鉴/展示台等较大的 UI 卡片，不参与 44x44 农田 Tile
 * 渲染。农田仍使用 {@link AImageAssets} 的阶段图集，避免高分辨率传奇插图
 * 被压缩到 40px 后失真，也避免把“是否传奇”变成地图渲染层的新状态来源。
 */
final class LegendaryImageAssets {

    private static final Map<CropType, String> PATHS = new EnumMap<>(CropType.class);
    private static final Map<CropType, Image> CACHE = new EnumMap<>(CropType.class);

    static {
        PATHS.put(CropType.WHEAT, "/assets/crops/legendary/golden_wheat.png");
        PATHS.put(CropType.CORN, "/assets/crops/legendary/rainbow_corn.png");
        PATHS.put(CropType.CARROT, "/assets/crops/legendary/dragon_carrot.png");
    }

    private LegendaryImageAssets() {
    }

    /**
     * 创建传奇展示图。资源缺失时返回 null，让 View 安全回退到普通成熟作物图。
     */
    static ImageView viewFor(CropType type, double maxSize, boolean unlocked) {
        if (type == null || maxSize <= 0) {
            return null;
        }
        Image image = imageFor(type);
        if (image == null) {
            return null;
        }

        ImageView view = new ImageView(image);
        view.setFitWidth(maxSize);
        view.setFitHeight(maxSize);
        view.setPreserveRatio(true);
        view.setSmooth(false);
        view.setMouseTransparent(true);

        if (!unlocked) {
            ColorAdjust lockedEffect = new ColorAdjust();
            lockedEffect.setSaturation(-1.0);
            lockedEffect.setBrightness(-0.30);
            view.setEffect(lockedEffect);
            view.setOpacity(0.30);
        }
        return view;
    }

    static String classpathFor(CropType type) {
        return type == null ? null : PATHS.get(type);
    }

    private static Image imageFor(CropType type) {
        if (CACHE.containsKey(type)) {
            return CACHE.get(type);
        }
        String path = PATHS.get(type);
        if (path == null) {
            return null;
        }
        URL url = LegendaryImageAssets.class.getResource(path);
        if (url == null) {
            return null;
        }
        Image image = new Image(url.toExternalForm(), 0, 0, true, false, false);
        if (image.isError()) {
            return null;
        }
        CACHE.put(type, image);
        return image;
    }
}
