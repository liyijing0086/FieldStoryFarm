package com.fieldstory.farm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldstory.farm.service.LandUnlockPriceProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * 从静态 balance-config.json 读取土地解锁价格。
 *
 * <p>具体价格只存在配置文件，不写死在 Controller、Soil 或 LandUnlockService。
 * P3 允许测试配置验证流程；P4 再替换为最终平衡值。
 */
public final class JsonLandUnlockPriceProvider implements LandUnlockPriceProvider {

    public static final String DEFAULT_RESOURCE = "/config/balance-config.json";

    private final Map<String, Integer> prices;

    public JsonLandUnlockPriceProvider(Map<String, Integer> prices) {
        this.prices = prices == null ? Map.of() : Map.copyOf(prices);
    }

    public static JsonLandUnlockPriceProvider fromClasspath() {
        return fromClasspath(DEFAULT_RESOURCE);
    }

    public static JsonLandUnlockPriceProvider fromClasspath(String resourcePath) {
        String path = resourcePath == null || resourcePath.isBlank()
                ? DEFAULT_RESOURCE
                : resourcePath;
        try (InputStream input = JsonLandUnlockPriceProvider.class.getResourceAsStream(path)) {
            if (input == null) {
                return new JsonLandUnlockPriceProvider(Map.of());
            }
            JsonNode root = new ObjectMapper().readTree(input);
            JsonNode plots = root.path("landUnlock").path("plots");
            Map<String, Integer> parsed = new HashMap<>();
            if (plots.isArray()) {
                for (JsonNode plot : plots) {
                    if (!plot.has("row") || !plot.has("column") || !plot.has("price")) {
                        continue;
                    }
                    int row = plot.path("row").asInt(Integer.MIN_VALUE);
                    int column = plot.path("column").asInt(Integer.MIN_VALUE);
                    int price = plot.path("price").asInt(-1);
                    if (row >= 0 && column >= 0 && price >= 0) {
                        parsed.put(key(row, column), price);
                    }
                }
            }
            return new JsonLandUnlockPriceProvider(parsed);
        } catch (IOException malformed) {
            throw new IllegalStateException("无法读取土地解锁配置: " + path, malformed);
        }
    }

    @Override
    public OptionalInt findUnlockPrice(int row, int column) {
        Integer price = prices.get(key(row, column));
        return price == null ? OptionalInt.empty() : OptionalInt.of(price);
    }

    private static String key(int row, int column) {
        return row + "," + column;
    }
}
