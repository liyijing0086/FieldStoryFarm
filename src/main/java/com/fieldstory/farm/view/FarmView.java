package com.fieldstory.farm.view;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmPlot;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * 农场主画布（A 模块 P0 视图层；UI规范 §6 农场地图模块规范）。
 *
 * <p>渲染 12×12、每格 44×44、总 528×528 的地图画布（UI规范 §6.1）：
 * 中心 8×8 为 FARM_PLOT（底色随 Soil 状态变化），外围 2 格宽为
 * DECORATION_AREA 草地占位（UI规范 §6.3、§9）。
 *
 * <p>P0 为程序化占位渲染（正式像素素材 P1 替换，UI规范 §7 Tile 组合策略）；
 * 只允许使用 UI规范 §14 主色表 7 色与 §13 按钮三态色；P1 枯萎色
 * {@link #COLOR_WITHERED} 为 D17 候选色 A（待团队确认，如有变更仅改该常量）。
 *
 * <p>交互：悬停 Tooltip（UI规范 §10）、点击选中 3px 高亮描边（UI规范 §11）、
 * 隐藏式操作菜单并自动避让地图边界（UI规范 §12）。
 *
 * <p>纯静态函数 {@link #tileColorFor} / {@link #cropBlockSizeFor} /
 * {@link #tooltipTextFor} 只返回颜色/尺寸/字符串，不创建 JavaFX 节点，
 * 可在无 GUI 线程下单测（任务约束：JavaFX 节点创建不放纯函数里）。
 * 颜色常量用 {@link Color#rgb} 数值构造，同样不依赖 GUI 线程。
 */
public class FarmView extends Pane {

    // ==================== 布局常量（UI规范 §6.1） ====================

    /** 地图边长格数：12×12（UI规范 §6.1） */
    public static final int MAP_SIZE = 12;

    /** 单格像素：44×44（UI规范 §6.1） */
    public static final int TILE_SIZE = 44;

    /** 画布总像素：12 × 44 = 528（UI规范 §6.1） */
    public static final int MAP_PX = MAP_SIZE * TILE_SIZE;

    // ==================== 主色表 7 色（UI规范 §14） ====================

    /** 草地 #7FAE55（UI规范 §14）：装饰区底、P0 作物占位块 */
    public static final Color COLOR_GRASS = Color.rgb(0x7F, 0xAE, 0x55);

    /** 土地 #A97850（UI规范 §14）：TILLED/PLANTED 底、按钮 Normal（§13） */
    public static final Color COLOR_SOIL = Color.rgb(0xA9, 0x78, 0x50);

    /** UI背景 #FFF3DD（UI规范 §14）：菜单面板背景、按钮文字 */
    public static final Color COLOR_UI_BG = Color.rgb(0xFF, 0xF3, 0xDD);

    /** 木色 #8B5E3C（UI规范 §14）：EMPTY 未开垦格 */
    public static final Color COLOR_WOOD = Color.rgb(0x8B, 0x5E, 0x3C);

    /** 文字 #493526（UI规范 §14）：格间 1px 分隔线 */
    public static final Color COLOR_TEXT = Color.rgb(0x49, 0x35, 0x26);

    /** 高亮 #E8C45C（UI规范 §14）：MATURE 待收获格、选中描边（§11） */
    public static final Color COLOR_HIGHLIGHT = Color.rgb(0xE8, 0xC4, 0x5C);

    // ==================== 枯萎色（P1：D17 候选色 A，待团队确认后如有变更仅改此常量） ====================

    /** 枯萎 #857766（D17 候选色 A；UI规范 §14 补充条目待团队确认）：WITHERED 格整格底色 */
    public static final Color COLOR_WITHERED = Color.rgb(0x85, 0x77, 0x66);

    // ==================== 按钮三态（UI规范 §13） ====================

    /** 按钮 Hover #C28B5A（UI规范 §13） */
    public static final Color COLOR_BTN_HOVER = Color.rgb(0xC2, 0x8B, 0x5A);

    /** 按钮 Disabled #CCCCCC（UI规范 §13） */
    public static final Color COLOR_BTN_DISABLED = Color.rgb(0xCC, 0xCC, 0xCC);

    // ==================== 按钮/菜单尺寸（UI规范 §12、§13） ====================

    /** 操作按钮宽 120（UI规范 §13） */
    private static final double BUTTON_WIDTH = 120;

    /** 操作按钮高 36（UI规范 §13） */
    private static final double BUTTON_HEIGHT = 36;

    /** 按钮圆角 10（UI规范 §13） */
    private static final double BUTTON_RADIUS = 10;

    /** 按钮文字字号 16（UI规范 §15 按钮字号） */
    private static final double BUTTON_FONT_SIZE = 16;

    /** 菜单面板圆角 12（UI规范 §16 Panel 圆角） */
    private static final double MENU_RADIUS = 12;

    /** 菜单与目标格间距（避让边界用） */
    private static final double MENU_GAP = 6;

    /** 菜单 VBox 内边距（UI规范 §16 Panel 风格） */
    private static final Insets MENU_PADDING = new Insets(8);

    // ==================== 按钮样式（颜色取自 §13/§14，见上方常量） ====================

    private static final String STYLE_BTN_NORMAL = "-fx-background-color: #A97850;"
            + "-fx-background-radius: 10;"
            + "-fx-text-fill: #FFF3DD;"
            + "-fx-font-size: 16;";

    private static final String STYLE_BTN_HOVER = "-fx-background-color: #C28B5A;"
            + "-fx-background-radius: 10;"
            + "-fx-text-fill: #FFF3DD;"
            + "-fx-font-size: 16;";

    private static final String STYLE_BTN_DISABLED = "-fx-background-color: #CCCCCC;"
            + "-fx-background-radius: 10;"
            + "-fx-text-fill: #FFF3DD;"
            + "-fx-font-size: 16;";

    /** 数据源：12×12 地图（非 FARM_PLOT 格 getSoil 返回 null） */
    private final Farm farm;

    /**
     * 当前游戏日：Tooltip"今日已浇/未浇"判定基准
     * （由 Controller 经 {@link #setCurrentGameDay} 同步，来自 D 的
     * GameClock.getGameDay()，决策 D14 时间口径 long）。
     */
    private long currentGameDay = 0L;

    /** 每格底色矩形 */
    private final Rectangle[][] tiles = new Rectangle[MAP_SIZE][MAP_SIZE];

    /** 每格 P0 作物占位块（PLANTED 且未成熟时可见） */
    private final Rectangle[][] cropBlocks = new Rectangle[MAP_SIZE][MAP_SIZE];

    /** 每格 Tooltip（存引用以便刷新文案） */
    private final Tooltip[][] tooltips = new Tooltip[MAP_SIZE][MAP_SIZE];

    /** 选中描边：高亮 3px（UI规范 §11） */
    private final Rectangle selectionRect = new Rectangle(TILE_SIZE, TILE_SIZE);

    /** 隐藏式操作菜单（UI规范 §12）：默认隐藏 */
    private final VBox menuBox = new VBox(4);

    /** 点击 FARM_PLOT 格的回调（由 Controller 注册） */
    private Consumer<Soil> onTileSelected;

    /**
     * 以农场模型构造画布并完成首轮渲染。
     *
     * @param farm 农场模型（12×12，中心 8×8 为 FARM_PLOT）
     */
    public FarmView(Farm farm) {
        this.farm = farm;
        setPrefSize(MAP_PX, MAP_PX);
        setMinSize(MAP_PX, MAP_PX);
        setMaxSize(MAP_PX, MAP_PX);
        buildTiles();
        buildMenu();
        selectionRect.setFill(Color.TRANSPARENT);
        selectionRect.setStroke(COLOR_HIGHLIGHT);
        selectionRect.setStrokeWidth(3);
        getChildren().add(selectionRect);
        getChildren().add(menuBox);
    }

    // ==================== 纯静态函数（可无 GUI 线程单测） ====================

    /**
     * 更新当前游戏日（Tooltip"今日已浇/未浇"判定基准）。
     * 由 Controller 在选中格/动作完成后、刷新视图前同步，
     * 值为 D 的 GameClock.getGameDay()。
     *
     * @param currentGameDay 当前游戏日
     */
    public void setCurrentGameDay(long currentGameDay) {
        this.currentGameDay = currentGameDay;
    }

    /**
     * 纯函数：按格类型与土壤状态推导底色。
     *
     * <p>五态（验收规范 §三十七）：EMPTY 木色 / TILLED 土地 / PLANTED 土地
     * （MATURE 高亮、WITHERED 枯萎色例外见下）/ LOCKED 木色占位；
     * 装饰区草地（UI规范 §14）。
     *
     * @param plotType 格类型（非 FARM_PLOT 时 soil 为 null）
     * @param soil     该格土地（装饰区为 null）
     * @return 底色（仅来自 UI规范 §14 主色表）
     */
    public static Color tileColorFor(FarmPlot plotType, Soil soil) {
        if (plotType != FarmPlot.FARM_PLOT) {
            // DECORATION_AREA 草地；SHOP/SHOWCASE P0 布局不出现，占位同草地
            return COLOR_GRASS;
        }
        if (soil == null) {
            return COLOR_GRASS;
        }
        switch (soil.getState()) {
            case EMPTY:
                return COLOR_WOOD;
            case TILLED:
                return COLOR_SOIL;
            case PLANTED:
                Crop crop = soil.getCrop();
                if (crop != null && crop.getGrowthStage() == GrowthStage.WITHERED) {
                    // P1：枯萎色优先于 MATURE 高亮（D17 候选色，验收 §五十四）
                    return COLOR_WITHERED;
                }
                if (crop != null && crop.getGrowthStage() == GrowthStage.MATURE) {
                    return COLOR_HIGHLIGHT;
                }
                return COLOR_SOIL;
            case LOCKED:
            default:
                // P0 不产生 LOCKED（验收规范 §十四）；未解锁格以木色占位（同 EMPTY，不新增颜色）
                return COLOR_WOOD;
        }
    }

    /**
     * 纯函数：P0 作物占位块边长（任务指定：SEED 8 / SPROUT 16 / GROWING 24）。
     *
     * <p>MATURE 整格高亮显示、不画作物块，返回 0；WITHERED 为 P1 占位，返回 0。
     *
     * @param stage 作物成长阶段
     * @return 占位块边长（像素）
     */
    public static int cropBlockSizeFor(GrowthStage stage) {
        // 坏数据兜底：growth_stage 无法识别时适配层降级为 null（FarmStateAdapter.parseEnum），
        // 此处不得抛 NPE；未知阶段不绘制占位块（返回 0）。
        if (stage == null) {
            return 0;
        }
        switch (stage) {
            case SEED:
                return 8;
            case SPROUT:
                return 16;
            case GROWING:
                return 24;
            case MATURE:
            case WITHERED:
            default:
                return 0;
        }
    }

    /**
     * 坏数据兜底：crop_type 无法识别时为 null（存档允许 {@code crop_type=NULL}，
     * P1 设计文档 §3），返回占位名而非抛 NPE。
     *
     * @param crop 作物快照
     * @return 展示名；{@code crop.getCropType()} 为 null 时返回占位名
     */
    private static String cropTypeNameFor(Crop crop) {
        return crop.getCropType() == null ? "未知作物" : crop.getCropType().getDisplayName();
    }

    /**
     * 纯函数：悬停提示文案（UI规范 §10）。
     *
     * <p>六种文案：null=装饰区占位、EMPTY=未开垦、TILLED=已开垦可播种、
     * PLANTED=作物名+成长x%+今日已浇/未浇、MATURE=已成熟可收获、
     * WITHERED=已枯萎，请铲除（P1，规则 §16.5）。
     *
     * @param soil           该格土地（装饰区为 null）
     * @param currentGameDay 当前游戏日（来自 D 的 GameClock.getGameDay）
     * @return Tooltip 文案
     */
    public static String tooltipTextFor(Soil soil, long currentGameDay) {
        if (soil == null) {
            return "装饰区（P0 占位）";
        }
        switch (soil.getState()) {
            case EMPTY:
                return "未开垦";
            case TILLED:
                return "已开垦，可播种";
            case PLANTED:
                Crop crop = soil.getCrop();
                if (crop != null && crop.getGrowthStage() == GrowthStage.WITHERED) {
                    // P1：优先于成熟文案（规则 §16.5 必须玩家主动铲除）
                    return "已枯萎，请铲除";
                }
                if (crop != null && crop.getGrowthStage() == GrowthStage.MATURE) {
                    return "已成熟，可收获";
                }
                if (crop == null) {
                    return "已播种";
                }
                // "今日已浇"判定：lastManualWaterGameDay == 当前游戏日（决策 D14 long 用 ==）
                boolean wateredToday = crop.getLastManualWaterGameDay() == currentGameDay;
                return cropTypeNameFor(crop) + " 成长"
                        + (int) crop.getGrowthProgress() + "% 今日"
                        + (wateredToday ? "已浇" : "未浇");
            case LOCKED:
            default:
                return "未解锁";
        }
    }

    // ==================== 渲染 ====================

    /** 首轮渲染：144 格底色 + 分隔线 + Tooltip + 作物占位块。 */
    private void buildTiles() {
        for (int row = 0; row < MAP_SIZE; row++) {
            for (int column = 0; column < MAP_SIZE; column++) {
                FarmPlot plotType = farm.getPlotType(row, column);
                Soil soil = farm.getSoil(row, column);

                Rectangle tile = new Rectangle(column * TILE_SIZE, row * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                tile.setFill(tileColorFor(plotType, soil));
                tile.setStroke(COLOR_TEXT);
                tile.setStrokeWidth(1);
                Tooltip tooltip = new Tooltip(tooltipTextFor(soil, currentGameDay));
                tooltip.setShowDelay(Duration.millis(100));     // 默认 1000ms 太慢
                tooltip.setShowDuration(Duration.seconds(20));   // 长文案给足停留时间
                tooltip.setHideDelay(Duration.millis(100));     // 移开后 100ms 收起，不突兀
                Tooltip.install(tile, tooltip);
                int clickedRow = row;
                int clickedColumn = column;
                tile.setOnMouseClicked(event -> handleTileClick(clickedRow, clickedColumn));
                getChildren().add(tile);
                tiles[row][column] = tile;
                tooltips[row][column] = tooltip;

                Rectangle cropBlock = new Rectangle();
                cropBlock.setFill(COLOR_GRASS);
                cropBlock.setMouseTransparent(true);   // 鼠标穿透：悬停/点击作物等同作用于地块
                cropBlock.setVisible(false);
                cropBlocks[row][column] = cropBlock;
                getChildren().add(cropBlock);
                updateCropBlock(row, column, plotType, soil);
            }
        }
    }

    /** 按格当前状态刷新作物占位块（位置居中于格内）。 */
    private void updateCropBlock(int row, int column, FarmPlot plotType, Soil soil) {
        Rectangle cropBlock = cropBlocks[row][column];
        Crop crop = soil == null ? null : soil.getCrop();
        boolean visible = plotType == FarmPlot.FARM_PLOT
                && soil != null
                && soil.getState() == SoilState.PLANTED
                && crop != null
                && crop.getGrowthStage() != GrowthStage.MATURE;
        if (!visible) {
            cropBlock.setVisible(false);
            return;
        }
        int size = cropBlockSizeFor(crop.getGrowthStage());
        cropBlock.setWidth(size);
        cropBlock.setHeight(size);
        cropBlock.setX(column * TILE_SIZE + (TILE_SIZE - size) / 2.0);
        cropBlock.setY(row * TILE_SIZE + (TILE_SIZE - size) / 2.0);
        cropBlock.setVisible(true);
    }

    /** 动作完成后刷新对应格：底色 + 作物块 + Tooltip 文案。 */
    public void refreshTile(Soil soil) {
        if (soil == null) {
            return;
        }
        int row = soil.getRow();
        int column = soil.getColumn();
        FarmPlot plotType = farm.getPlotType(row, column);
        tiles[row][column].setFill(tileColorFor(plotType, soil));
        updateCropBlock(row, column, plotType, soil);
        refreshTooltip(soil);
    }

    /**
     * 整图刷新：遍历农场全部 Soil，逐格刷新底色、作物块与 Tooltip
     * （refreshTile 已含 refreshTooltip，此处再显式刷新一次以覆盖
     * 直接改文案的场景）。供 E 的跨天成长回调调用：跨天后
     * "今日已浇/未浇"判定随 {@link #currentGameDay} 更新。
     */
    public void refreshAll() {
        for (Soil soil : farm.getSoils()) {
            refreshTile(soil);
            refreshTooltip(soil);
        }
    }

    // ==================== 选中与菜单（UI规范 §11、§12） ====================

    /**
     * 选中格：显示 3px 高亮描边，并把 Tooltip 恢复为标准文案
     * （覆盖上一次动作失败提示）。
     */
    public void selectTile(Soil soil) {
        refreshTooltip(soil);
        if (soil == null) {
            selectionRect.setVisible(false);
            return;
        }
        selectionRect.setX(soil.getColumn() * TILE_SIZE);
        selectionRect.setY(soil.getRow() * TILE_SIZE);
        selectionRect.setVisible(true);
    }

    /** 注册 FARM_PLOT 格点击回调（装饰区点击传入 null）。 */
    public void setOnTileSelected(Consumer<Soil> onTileSelected) {
        this.onTileSelected = onTileSelected;
    }

    /**
     * 在目标格旁弹出操作菜单并自动避让地图边界（UI规范 §12）。
     * 默认出现在目标格右侧；越界时翻到左侧；垂直方向贴下边界。
     *
     * @param soil    目标格
     * @param buttons 菜单按钮（120×36，UI规范 §13）
     */
    public void showMenuFor(Soil soil, List<Node> buttons) {
        menuBox.getChildren().setAll(buttons);
        double menuWidth = BUTTON_WIDTH + MENU_PADDING.getLeft() + MENU_PADDING.getRight();
        double menuHeight = MENU_PADDING.getTop() + MENU_PADDING.getBottom()
                + buttons.size() * BUTTON_HEIGHT
                + Math.max(0, buttons.size() - 1) * menuBox.getSpacing();

        double x = (soil.getColumn() + 1) * TILE_SIZE + MENU_GAP;
        double y = soil.getRow() * TILE_SIZE;
        if (x + menuWidth > MAP_PX) {
            x = soil.getColumn() * TILE_SIZE - menuWidth - MENU_GAP;
        }
        if (y + menuHeight > MAP_PX) {
            y = MAP_PX - menuHeight;
        }
        menuBox.setLayoutX(Math.max(0, x));
        menuBox.setLayoutY(Math.max(0, y));
        menuBox.setVisible(true);
    }

    /** 收起操作菜单（UI规范 §12：默认隐藏）。 */
    public void hideMenu() {
        menuBox.setVisible(false);
    }

    /**
     * 动作失败提示：把该格 Tooltip 文案替换为结果文案并立即显示
     * （任务约束：失败结果经 Tooltip 提示用户）。
     */
    public void showTip(Soil soil, String message) {
        if (soil == null) {
            return;
        }
        int row = soil.getRow();
        int column = soil.getColumn();
        tooltips[row][column].setText(message);
        tooltips[row][column].show(tiles[row][column], TILE_SIZE / 2.0, TILE_SIZE / 2.0);
    }

    // ==================== 按钮（UI规范 §13） ====================

    /**
     * 创建操作按钮：120×36、圆角 10（UI规范 §13）；
     * Normal #A97850 / Hover #C28B5A / Disabled #CCCCCC（UI规范 §13）。
     *
     * @param text 按钮文字
     * @return 样式化按钮
     */
    public Button createMenuButton(String text) {
        Button button = new Button(text);
        button.setPrefSize(BUTTON_WIDTH, BUTTON_HEIGHT);
        button.setOnMouseEntered(event -> {
            if (!button.isDisabled()) {
                button.setStyle(STYLE_BTN_HOVER);
            }
        });
        button.setOnMouseExited(event -> applyButtonStyle(button, button.isDisabled()));
        button.disabledProperty().addListener((observable, oldValue, disabled) ->
                applyButtonStyle(button, disabled));
        applyButtonStyle(button, false);
        return button;
    }

    // ==================== 内部辅助 ====================

    private void handleTileClick(int row, int column) {
        Soil soil = farm.getSoil(row, column);
        if (onTileSelected != null) {
            onTileSelected.accept(soil);
        }
    }

    private void refreshTooltip(Soil soil) {
        if (soil == null) {
            return;
        }
        tooltips[soil.getRow()][soil.getColumn()].setText(tooltipTextFor(soil, currentGameDay));
    }

    private void buildMenu() {
        menuBox.setPadding(MENU_PADDING);
        menuBox.setStyle("-fx-background-color: #FFF3DD;"
                + "-fx-background-radius: " + MENU_RADIUS + ";");
        menuBox.setVisible(false);
    }

    private void applyButtonStyle(Button button, boolean disabled) {
        button.setStyle(disabled ? STYLE_BTN_DISABLED : STYLE_BTN_NORMAL);
    }
}
