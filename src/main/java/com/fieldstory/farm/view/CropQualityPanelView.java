package com.fieldstory.farm.view;

import com.fieldstory.farm.service.QualityBreakdown;
import com.fieldstory.farm.service.QualityScoreInput;
import com.fieldstory.farm.service.QualityService;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * 品质解释面板（C 模块 品质与传说域，P1；验收规范 §七十七）。
 *
 * <p>玩家查看作物时展示品质评分来源：基础分、天气累计、主动浇水、
 * 施肥、装饰、事件与确定性合计；随机 0~9 按 §七十七 收获前不显示
 * 具体值，只提示「收获时确定」。
 *
 * <p>View 职责（脚手架 §七.5：只负责显示）：本视图只调用
 * {@link QualityService#explainScore} 的只读查询生成分项明细，
 * 不写任何 Model/Service 状态、不消耗随机源。颜色只用 UI规范 §14
 * 主色表 7 色，字号按 UI规范 §15，Panel 样式按 UI规范 §16。
 *
 * <p>独立组件：与 FarmView 解耦，由装配方在查看作物时调用
 * {@link #display(QualityScoreInput)} 挂载展示（与 P3 展示台同模式）。
 */
public class CropQualityPanelView extends VBox {

    /** 数据源（只读查询：分项明细） */
    private final QualityService qualityService;

    /** 分项行容器 */
    private final VBox rows = new VBox(2);

    /** 空态占位提示 */
    private final Label emptyLabel = new Label();

    /**
     * 注入品质服务，构建面板并显示空态。
     *
     * @param qualityService 品质服务（只读查询 explainScore）
     */
    public CropQualityPanelView(QualityService qualityService) {
        this.qualityService = Objects.requireNonNull(qualityService, "品质服务不能为空");
        setSpacing(6);
        setPadding(new Insets(10));
        getStyleClass().addAll("panel", "quality-panel");
        UiTheme.apply(this);

        Label title = new Label("品质解释");
        title.getStyleClass().add("section-title");

        emptyLabel.getStyleClass().add("hint-text");
        emptyLabel.setWrapText(true);
        emptyLabel.setText("选择一株作物，查看它的品质评分来源。");

        getChildren().addAll(title, rows, emptyLabel);
    }

    /**
     * 按评分输入渲染分项明细（玩家查看作物时调用）。
     *
     * <p>只读操作：调 explainScore 计算确定性分项，不消耗随机源；
     * 随机行恒为提示文案（验收规范 §七十七：收获前不显示随机分）。
     *
     * @param input 评分输入（作物类型 + 经历计数 + 装饰/事件分）
     */
    public void display(QualityScoreInput input) {
        Objects.requireNonNull(input, "评分输入不能为空");
        QualityBreakdown breakdown = qualityService.explainScore(input);

        rows.getChildren().clear();
        rows.getChildren().add(label(rowLabel("基础分", breakdown.getBaseScore())));
        rows.getChildren().add(label(weatherRowLabel(breakdown.getRainScore(),
                breakdown.getDroughtScore(), breakdown.getGreenRainScore())));
        rows.getChildren().add(label(detailRowLabel("主动浇水",
                input.getManualWaterCount() + " 次", breakdown.getWaterScore())));
        rows.getChildren().add(label(detailRowLabel("施肥",
                input.getFertilizerCount() + " 次", breakdown.getFertilizerScore())));
        rows.getChildren().add(label(rowLabel("装饰", breakdown.getDecorationScore())));
        rows.getChildren().add(label(rowLabel("事件", breakdown.getEventScore())));
        rows.getChildren().add(label(randomRowLabel()));
        rows.getChildren().add(label(totalRowLabel(breakdown.deterministicTotal())));

        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
    }

    /** 隐藏明细、恢复空态提示（当前无选中作物时调用）。 */
    public void clear() {
        rows.getChildren().clear();
        emptyLabel.setVisible(true);
        emptyLabel.setManaged(true);
    }

    /** 内部：按统一样式构建一行标签。 */
    private Label label(String text) {
        Label row = new Label(text);
        row.getStyleClass().add("normal-text");
        row.setWrapText(true);
        return row;
    }

    /**
     * 纯函数：普通分项行文案「名称 +分数」。
     *
     * @param name  分项名称
     * @param score 分项分数（非负）
     * @return 行文案，例如「基础分 +50」
     */
    public static String rowLabel(String name, int score) {
        Objects.requireNonNull(name, "分项名称不能为空");
        return name + " +" + Math.max(0, score);
    }

    /**
     * 纯函数：天气累计行文案（含雨/旱/绿雨分项明细）。
     *
     * @return 行文案，例如「天气累计 +31（雨 +15 · 旱 +16 · 绿雨 +0）」
     */
    public static String weatherRowLabel(int rainScore, int droughtScore, int greenRainScore) {
        int total = Math.max(0, rainScore) + Math.max(0, droughtScore) + Math.max(0, greenRainScore);
        return "天气累计 +" + total
                + "（雨 +" + Math.max(0, rainScore)
                + " · 旱 +" + Math.max(0, droughtScore)
                + " · 绿雨 +" + Math.max(0, greenRainScore) + "）";
    }

    /**
     * 纯函数：带次数明细的分项行文案「名称 +分数（次数明细）」。
     *
     * @return 行文案，例如「施肥 +8（3 次）」
     */
    public static String detailRowLabel(String name, String detail, int score) {
        Objects.requireNonNull(name, "分项名称不能为空");
        Objects.requireNonNull(detail, "次数明细不能为空");
        return name + " +" + Math.max(0, score) + "（" + detail + "）";
    }

    /**
     * 纯函数：随机行文案（验收规范 §七十七：收获前不显示随机分）。
     *
     * @return 行文案「随机 0~9（收获时确定）」
     */
    public static String randomRowLabel() {
        return "随机 0~9（收获时确定）";
    }

    /**
     * 纯函数：确定性合计行文案。
     *
     * @return 行文案，例如「确定性合计 120（收获时再加随机分）」
     */
    public static String totalRowLabel(int deterministicTotal) {
        return "确定性合计 " + Math.max(0, deterministicTotal) + "（收获时再加随机分）";
    }
}
