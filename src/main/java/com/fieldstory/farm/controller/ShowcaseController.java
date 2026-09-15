package com.fieldstory.farm.controller;

import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.service.ShowcaseService;
import com.fieldstory.farm.view.ShowcaseView;

import java.util.Objects;

/**
 * 展示台控制器（C 模块 品质与传说域，P3）。
 *
 * <p>职责（脚手架 §七.4）：装配 {@link ShowcaseView} 与
 * {@link ShowcaseService}，把展示台面板挂载到场景，并在收获新传说后
 * 触发视图刷新。所有业务筛选与文本组装都在 Service 层完成，本类只做
 * 视图装配与刷新转发。
 *
 * <p>跨模块纪律（接口要求清单 §B 场景组装要求，B1：挂载只走
 * {@link SceneManager#mount}，E 持有）：不修改 E 的
 * SceneManager/MainController/MainApplication 文件；
 * 挂载动作由本方法显式触发，不在构造器中抢占槽位，避免多模块抢挂。
 */
public class ShowcaseController {

    /** 展示台视图 */
    private final ShowcaseView showcaseView;

    /**
     * 注入展示台服务并构建视图。
     *
     * @param showcaseService 展示台服务
     */
    public ShowcaseController(ShowcaseService showcaseService) {
        Objects.requireNonNull(showcaseService, "展示台服务不能为空");
        this.showcaseView = new ShowcaseView(showcaseService);
    }

    /** 展示台视图。 */
    public ShowcaseView getView() {
        return showcaseView;
    }

    /**
     * 挂载到场景底部槽位（调用 E 的 SceneManager 完成组装）。
     *
     * <p>运行时约束：须在 {@link SceneManager#assemble} 之后调用（即
     * 「开始游戏」流程中）——assemble 会清空已挂载组件，在此之前挂载
     * 会被静默清除。本方法须运行在 JavaFX Application Thread。
     *
     * <p>BOTTOM 为预留槽位（接口要求清单 §B2：使用前先与 E 确认归属）；
     * C 的展示台面板默认挂底部，如与 E 的预留规划冲突，由装配方决定
     * 更换槽位或改弹窗展示，本类不持有槽位之外的任何场景资产。
     */
    public void mountToScene() {
        SceneManager.getInstance().mount(SceneManager.Slot.BOTTOM, showcaseView);
    }

    /**
     * 刷新展示台（收获新传说后由装配方/收获流程调用；验收规范
     * §一百三十二 ⑥：三种传说均可展示故事——新增档案可随时重拉）。
     * 须在 JavaFX Application Thread 调用（收获按钮事件天然满足）。
     */
    public void refresh() {
        showcaseView.refresh();
    }
}
