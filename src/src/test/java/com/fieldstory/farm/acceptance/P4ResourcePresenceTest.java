package com.fieldstory.farm.acceptance;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4 发布资源守卫：避免 CSS/BGM/SFX 漏拷导致“代码能编译、发布包无资源”。 */
class P4ResourcePresenceTest {

    private static final List<String> REQUIRED_RESOURCES = List.of(
            "/css/style.css",
            "/assets/tiles/ground_01_16x16.png",
            "/assets/crops/legendary/golden_wheat.png",
            "/assets/crops/legendary/rainbow_corn.png",
            "/assets/crops/legendary/dragon_carrot.png",
            "/audio/bgm/menu-theme.wav",
            "/audio/bgm/farm-theme.wav",
            "/audio/bgm/event-theme.wav",
            "/audio/sfx/click.wav",
            "/audio/sfx/purchase.wav",
            "/audio/sfx/plant.wav",
            "/audio/sfx/water.wav",
            "/audio/sfx/fertilize.wav",
            "/audio/sfx/harvest.wav",
            "/audio/sfx/rare.wav",
            "/audio/sfx/legendary.wav",
            "/audio/sfx/rank-up.wav",
            "/audio/sfx/graduation.wav"
    );

    @Test
    void allP4RuntimeResourcesAreOnClasspath() {
        for (String path : REQUIRED_RESOURCES) {
            assertNotNull(P4ResourcePresenceTest.class.getResource(path),
                    "P4 运行资源缺失或放置路径错误：" + path);
        }
    }

    @Test
    void unifiedCssContainsCoreP4Selectors() throws Exception {
        URL css = P4ResourcePresenceTest.class.getResource("/css/style.css");
        assertNotNull(css);
        String text = new String(css.openStream().readAllBytes(), StandardCharsets.UTF_8);
        for (String selector : List.of(
                ".menu-card", ".top-bar", ".farm-action-menu",
                ".collection-root", ".showcase-root", ".graduation-panel")) {
            assertTrue(text.contains(selector), "统一 CSS 缺少核心选择器：" + selector);
        }
    }
}
