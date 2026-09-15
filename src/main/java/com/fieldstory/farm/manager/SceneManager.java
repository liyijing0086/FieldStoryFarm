package com.fieldstory.farm.manager;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;

import java.util.EnumMap;
import java.util.Map;

/**
 * 场景组装管理器（E 场景组装；脚手架 §七 manager：GameManager/SceneManager）。
 *
 * <p>职责：搭建主场景（统一 BorderPane 五区布局），并把各模块组件“装”进游戏：
 * 土地模块(A)、玩家 UI/商店(B)、状态栏(D) 等视图接入后，通过 {@link #mount}
 * 挂载到对应槽位即可完成组装，各模块之间不互相硬引用。
 *
 * <p>P0 骨架阶段仅挂载主菜单（main-view.fxml）到 CENTER；其他模块视图交付后由
 * 各自作者调用 {@code SceneManager.getInstance().mount(...)} 挂载。
 */
public class SceneManager {

    /** 主场景可挂载区域（BorderPane 五区） */
    public enum Slot {
        /** 顶部（D 状态栏等） */
        TOP,
        /** 中央（A 土地模块、主菜单等） */
        CENTER,
        /** 左侧 */
        LEFT,
        /** 右侧（B 商店等） */
        RIGHT,
        /** 底部 */
        BOTTOM
    }

    /** 场景组装单例 */
    private static volatile SceneManager instance;

    private final Map<Slot, Node> mounted = new EnumMap<>(Slot.class);
    private BorderPane root;

    private SceneManager() {
        // 单例
    }

    /** 全局唯一场景组装器。 */
    public static SceneManager getInstance() {
        if (instance == null) {
            synchronized (SceneManager.class) {
                if (instance == null) {
                    instance = new SceneManager();
                }
            }
        }
        return instance;
    }

    /**
     * 组装主场景：把主界面内容挂到中央并生成 {@link Scene}。
     *
     * @param centerNode 中央内容（P0 为 main-view.fxml 加载的主菜单）
     * @param width      窗口宽度
     * @param height     窗口高度
     * @return 组装完成的主场景
     */
    public Scene assemble(Node centerNode, double width, double height) {
        root = new BorderPane();
        root.getStyleClass().add("app-root");
        mounted.clear();
        mount(Slot.CENTER, centerNode);
        return new Scene(root, width, height);
    }

    /**
     * 将组件挂载到指定槽位（同槽位已有组件将被替换）。
     * A/B/C/D 模块视图接入后调用本方法完成场景组装。
     *
     * @param slot 槽位
     * @param node 组件节点
     */
    public void mount(Slot slot, Node node) {
        mounted.put(slot, node);
        apply(slot);
    }

    /**
     * 移除槽位上的组件。
     *
     * @return 被移除的组件；槽位为空时返回 {@code null}
     */
    public Node unmount(Slot slot) {
        Node removed = mounted.remove(slot);
        apply(slot);
        return removed;
    }

    /** 当前根布局；未组装时返回 {@code null}。 */
    public BorderPane root() {
        return root;
    }

    private void apply(Slot slot) {
        if (root == null) {
            return;
        }
        switch (slot) {
            case TOP -> root.setTop(mounted.get(Slot.TOP));
            case CENTER -> root.setCenter(mounted.get(Slot.CENTER));
            case LEFT -> root.setLeft(mounted.get(Slot.LEFT));
            case RIGHT -> root.setRight(mounted.get(Slot.RIGHT));
            case BOTTOM -> root.setBottom(mounted.get(Slot.BOTTOM));
        }
    }
}
