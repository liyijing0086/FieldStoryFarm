package com.fieldstory.farm.view;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B 模块 UI 图片加载器。
 *
 * <p>统一从 classpath 的 /assets 下读取图片，并按实际显示尺寸解码缓存，
 * 避免 2048x2048 原图在多个 View 中被重复解码占用大量内存。
 */
final class BImageAssets {

    private static final Map<String, Image> CACHE =
            new ConcurrentHashMap<>();

    private BImageAssets() {
    }

    static Image load(
            String classpath,
            double requestedWidth,
            double requestedHeight) {

        if (classpath == null || classpath.isBlank()) {
            return null;
        }

        String key =
                classpath
                        + "@"
                        + (int) requestedWidth
                        + "x"
                        + (int) requestedHeight;

        return CACHE.computeIfAbsent(
                key,
                ignored -> loadUncached(
                        classpath,
                        requestedWidth,
                        requestedHeight
                )
        );
    }

    private static Image loadUncached(
            String classpath,
            double requestedWidth,
            double requestedHeight) {

        URL url =
                BImageAssets.class.getResource(classpath);

        if (url == null) {
            return null;
        }

        Image image =
                new Image(
                        url.toExternalForm(),
                        requestedWidth,
                        requestedHeight,
                        true,
                        false,
                        false
                );

        return image.isError()
                ? null
                : image;
    }

    static ImageView view(
            String classpath,
            double width,
            double height) {

        Image image =
                load(
                        classpath,
                        width,
                        height
                );

        if (image == null) {
            return null;
        }

        ImageView view =
                new ImageView(image);

        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(true);
        view.setSmooth(false);
        view.setMouseTransparent(true);

        return view;
    }
}