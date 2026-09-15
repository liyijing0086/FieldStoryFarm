package com.fieldstory.farm.util;

import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * FXML 加载工具类。
 */
public final class FxmlUtil {

    private FxmlUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 加载与指定 controller 类同目录下的 FXML 文件。
     *
     * @param controllerClass controller 类，用于定位资源路径
     * @param fxmlName        FXML 文件名
     * @return FXML 加载结果，可通过 {@link FXMLLoader#load()} 完成加载
     * @throws IOException 资源不存在或加载失败时抛出
     */
    public static FXMLLoader load(Class<?> controllerClass, String fxmlName) throws IOException {
        URL location = controllerClass.getResource(fxmlName);
        if (location == null) {
            throw new IOException("未找到 FXML 资源: " + fxmlName);
        }
        return new FXMLLoader(location);
    }
}
