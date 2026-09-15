package com.fieldstory.farm.view;

import com.fieldstory.farm.util.GameConstants;
import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmPlot;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    public static final int TILE_SIZE = GameConstants.TILE_SIZE;

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

    /** 每格 P1 作物贴图层（决策 D1/D2/D3：2× 放大、底对齐、MATURE 末帧；贴图缺失时隐藏回退方块） */
    private final ImageView[][] cropSprites = new ImageView[MAP_SIZE][MAP_SIZE];

    /** 每格 P1 地面贴图层（决策 D-G1/D-G2/D-G3：2× 放大、格内居中；贴图缺失时隐藏保持纯色底） */
    private final ImageView[][] groundTextures = new ImageView[MAP_SIZE][MAP_SIZE];

    /**
     * 今日是否湿天（天气 ∈ {RAIN, GREEN_RAIN}，由调用方换算，决策 D-G2）；
     * 经 {@link #setWetToday} 同步（D-G4 跨模块接线预留）。
     */
    private boolean wetToday = false;

    /** 每格 Tooltip（存引用以便刷新文案） */
    private final Tooltip[][] tooltips = new Tooltip[MAP_SIZE][MAP_SIZE];

    /** 选中描边：高亮 3px（UI规范 §11） */
    private final Rectangle selectionRect = new Rectangle(TILE_SIZE, TILE_SIZE);

    /** 隐藏式操作菜单（UI规范 §12）：默认隐藏 */
    private final VBox menuBox = new VBox(4);

    /**
     * 操作菜单宿主。FarmView 单独使用时仍为自身（兼容既有测试）；正式装配进
     * DecorationOverlayView 后会被提升到最上层 interactionLayer，解决菜单被装饰遮挡。
     */
    private Pane menuHost = this;

    /** 上一次渲染的阶段，仅用于 P4 视觉反馈；不参与任何成长判定。 */
    private final Map<UUID, GrowthStage> lastRenderedStages = new HashMap<>();

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
        selectionRect.setMouseTransparent(true);
        selectionRect.setVisible(false);
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
     * 更新今日湿天标记（地面贴图湿判定输入；决策 D-G2）。
     * 由 Controller 跨天时把天气换算为布尔（∈ {RAIN, GREEN_RAIN} → true）后、
     * 刷新视图前同步；D-G4 跨模块接线不在本卡范围，本卡只提供存储与使用。
     *
     * @param wetToday 今日是否湿天
     */
    public void setWetToday(boolean wetToday) {
        this.wetToday = wetToday;
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
     * 纯函数：成长阶段 → 图集帧索引（A 模块 P1 作物贴图接入；决策文档 D2/D3；
     * UI规范 §7 Tile组合策略）。
     *
     * <p>映射：SEED→0、SPROUT→2、GROWING→4、MATURE→末帧（totalFrames-1）、
     * WITHERED→-1（不显示贴图哨兵）。帧数不足时钳制到 totalFrames-1；
     * stage 为 null 或 totalFrames≤0 返回 -1（坏数据兜底，
     * AImageAssets 遇 -1 不创建贴图视图）。
     *
     * @param stage      成长阶段（可为 null）
     * @param frameCount 图集总帧数
     * @return 帧索引；-1 表示不显示贴图
     */
    public static int cropFrameIndexFor(GrowthStage stage, int frameCount) {
        if (stage == null || frameCount <= 0 || stage == GrowthStage.WITHERED) {
            return -1;
        }
        int frame;
        switch (stage) {
            case SEED:
                frame = 0;
                break;
            case SPROUT:
                frame = 2;
                break;
            case GROWING:
                frame = 4;
                break;
            case MATURE:
                frame = frameCount - 1;
                break;
            default:
                return -1;
        }
        return Math.min(frame, frameCount - 1);
    }

    /**
     * 纯函数：地面贴图变体判定（A 模块 P1 地面 Tile 美化；决策 D-G2/D-G3；
     * UI规范 §6 地图、§7 Tile 组合策略）。
     *
     * <p>变体映射：DECORATION_AREA→GRASS（SHOP/SHOWCASE 占位同草地）；
     * soil 为 null→GRASS；EMPTY/LOCKED→NONE（D-G3 不铺贴图，保持纯色语义）；
     * TILLED→wetToday?WET:TILLED（无作物，湿判定仅依赖天气）；
     * PLANTED 中 MATURE/WITHERED→NONE（D-G3），其余按湿判定：
     * 今日已浇（lastManualWaterGameDay == currentGameDay，决策 D14 long 用 ==）
     * 或今日湿天（wetToday，天气 ∈ {RAIN, GREEN_RAIN} 由调用方换算，决策 D-G2）
     * →WET，否则 TILLED。
     *
     * <p>坏数据兜底：任何入参为 null 不得抛 NPE（口径同 cropFrameIndexFor）；
     * plotType 为 null 视同非种植格（草地）；state 为 null 或未知→NONE。
     *
     * @param plotType       格类型（可为 null）
     * @param soil           该格土地（装饰区为 null）
     * @param currentGameDay 当前游戏日（来自 D 的 GameClock.getGameDay）
     * @param wetToday       今日是否湿天（天气 ∈ {RAIN, GREEN_RAIN}，由调用方换算，决策 D-G2）
     * @return 地面贴图变体；NONE 表示不铺贴图
     */
    public static GroundVariant groundVariantFor(FarmPlot plotType, Soil soil,
                                                 long currentGameDay, boolean wetToday) {
        if (plotType != FarmPlot.FARM_PLOT) {
            // DECORATION_AREA 草地；SHOP/SHOWCASE P0 布局不出现，占位同草地；
            // plotType 为 null 视同非种植格（坏数据兜底，不抛 NPE）
            return GroundVariant.GRASS;
        }
        if (soil == null) {
            return GroundVariant.GRASS;
        }
        SoilState state = soil.getState();
        if (state == null) {
            // 坏数据兜底：state 可能被适配层降级为 null，不铺贴图
            return GroundVariant.NONE;
        }
        switch (state) {
            case EMPTY:
            case LOCKED:
                // D-G3：不铺贴图，保持纯色语义
                return GroundVariant.NONE;
            case TILLED:
                // TILLED 无作物：湿判定只依赖天气（决策 D-G2）
                return wetToday ? GroundVariant.WET : GroundVariant.TILLED;
            case PLANTED:
                Crop crop = soil.getCrop();
                if (crop != null && crop.getGrowthStage() == GrowthStage.MATURE) {
                    // D-G3：成熟格保留整格高亮底色，不铺地面贴图
                    return GroundVariant.NONE;
                }
                if (crop != null && crop.getGrowthStage() == GrowthStage.WITHERED) {
                    // D-G3：枯萎格保留整格枯萎色，不铺地面贴图
                    return GroundVariant.NONE;
                }
                // 湿判定（决策 D-G2）：今日已浇（决策 D14 long 用 ==）或今日湿天；
                // crop 为 null 时按仅天气判定（坏数据兜底，不抛 NPE）
                boolean wateredToday = crop != null
                        && crop.getLastManualWaterGameDay() == currentGameDay;
                return (wateredToday || wetToday) ? GroundVariant.WET : GroundVariant.TILLED;
            default:
                // 未知状态（坏数据）：不铺贴图
                return GroundVariant.NONE;
        }
    }

    /**
     * 纯函数：悬停提示文案（UI规范 §10）。
     *
     * <p>六种文案：null=装饰区可放置装饰、EMPTY=未开垦、TILLED=已开垦可播种、
     * PLANTED=作物名+成长x%+今日已浇/未浇、MATURE=已成熟可收获、
     * WITHERED=已枯萎，请铲除（P1，规则 §16.5）。
     *
     * @param soil           该格土地（装饰区为 null）
     * @param currentGameDay 当前游戏日（来自 D 的 GameClock.getGameDay）
     * @return Tooltip 文案
     */
    public static String tooltipTextFor(Soil soil, long currentGameDay) {
        if (soil == null) {
            return "装饰区，可放置装饰";
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
                Tooltip tooltip = new Tooltip(tooltipTextFor(soil, currentGameDay));
                tooltip.setShowDelay(Duration.millis(100));     // 默认 1000ms 太慢
                tooltip.setShowDuration(Duration.INDEFINITE);   // 悬停常显：鼠标移开才消失
                tooltip.setHideDelay(Duration.millis(100));     // 移开后 100ms 收起，不突兀
                Tooltip.install(tile, tooltip);
                int clickedRow = row;
                int clickedColumn = column;
                tile.setOnMouseClicked(event -> handleTileClick(clickedRow, clickedColumn));
                getChildren().add(tile);
                tiles[row][column] = tile;
                tooltips[row][column] = tooltip;

                // P1 地面贴图层（z 序：tile → groundTexture → cropBlock → cropSprite）
                ImageView groundTexture = new ImageView();
                groundTexture.setVisible(false);
                groundTexture.setMouseTransparent(true);  // 鼠标穿透：悬停/点击作用于地块
                groundTexture.setSmooth(false);           // 像素风：最近邻不平滑（决策 D-G1）
                groundTexture.setPreserveRatio(false);
                groundTextures[row][column] = groundTexture;
                getChildren().add(groundTexture);
                updateGroundTexture(row, column, plotType, soil);

                Rectangle cropBlock = new Rectangle();
                cropBlock.setFill(COLOR_GRASS);
                cropBlock.setMouseTransparent(true);   // 鼠标穿透：悬停/点击作物等同作用于地块
                cropBlock.setVisible(false);
                cropBlocks[row][column] = cropBlock;
                getChildren().add(cropBlock);

                // P1 作物贴图层（z 序：tile → cropBlock → cropSprite，保持行优先）
                ImageView cropSprite = new ImageView();
                cropSprite.setVisible(false);
                cropSprite.setMouseTransparent(true);  // 鼠标穿透：悬停/点击作用于地块
                cropSprite.setUserData("cropSprite");  // 👈 加上这行专属记号
                cropSprites[row][column] = cropSprite;
                getChildren().add(cropSprite);

                updateCropBlock(row, column, plotType, soil);
                if (soil != null && soil.getCrop() != null
                        && soil.getCrop().getCropUuid() != null
                        && soil.getCrop().getGrowthStage() != null) {
                    lastRenderedStages.put(soil.getCrop().getCropUuid(), soil.getCrop().getGrowthStage());
                }
            }
        }
    }

    /**
     * P1 地面贴图层刷新（UI规范 §6 地图、§7 Tile 组合策略；决策 D-G1/D-G2/D-G3）。
     *
     * <p>变体判定见 {@link #groundVariantFor}：NONE（MATURE/WITHERED/EMPTY/LOCKED，
     * 决策 D-G3）或贴图加载失败（{@link ATileAssets#viewFor} 返回 null）时
     * 隐藏贴图层，纯色底语义与改造前一致；其余按变体切取图集帧，
     * 2× 放大（32×32）后于 44×44 格内居中（决策 D-G1，四周留 6px 底色边）。
     * 复用构造期占位视图（原地更新 image/viewport/fit），不替换节点，
     * 保证 Z 序稳定（tile → groundTexture → cropBlock → cropSprite）。
     *
     * @param row      全局行坐标
     * @param column   全局列坐标
     * @param plotType 格类型
     * @param soil     该格土地（装饰区为 null）
     */
    private void updateGroundTexture(int row, int column, FarmPlot plotType, Soil soil) {
        ImageView target = groundTextures[row][column];
        GroundVariant variant = groundVariantFor(plotType, soil, currentGameDay, wetToday);

        // P4 视觉层只补底图，不改变状态机语义：未开垦/LOCKED 仍是原状态，
        // 但视觉上使用草地贴图；成熟/枯萎仍保留其状态色，只叠半透明耕地纹理。
        boolean statusTint = false;
        if (plotType == FarmPlot.FARM_PLOT && soil != null) {
            SoilState state = soil.getState();
            if (state == SoilState.EMPTY || state == SoilState.LOCKED) {
                variant = GroundVariant.GRASS;
            } else if (state == SoilState.PLANTED && soil.getCrop() != null
                    && (soil.getCrop().getGrowthStage() == GrowthStage.MATURE
                    || soil.getCrop().getGrowthStage() == GrowthStage.WITHERED)) {
                variant = GroundVariant.TILLED;
                statusTint = true;
            }
        }

        if (variant == GroundVariant.NONE) {
            target.setVisible(false);
            target.setImage(null);
            target.setEffect(null);
            return;
        }

        ImageView view = ATileAssets.viewForTile(variant, row, column, TILE_SIZE);
        if (view == null) {
            target.setVisible(false);
            target.setImage(null);
            target.setEffect(null);
            return;
        }
        target.setImage(view.getImage());
        target.setViewport(view.getViewport());

        boolean soilTexture = variant == GroundVariant.TILLED || variant == GroundVariant.WET;
        // 草地铺满 44×44；耕地缩进 1px，让底层 #A97850 自然形成极细格界。
        // 这是纯表现层，不添加 SoilState，也不会改变 12×12/44×44 的点击坐标。
        double inset = soilTexture ? 1.0 : 0.0;
        double visualSize = TILE_SIZE - inset * 2.0;
        target.setFitWidth(visualSize);
        target.setFitHeight(visualSize);
        target.setX(column * TILE_SIZE + inset);
        target.setY(row * TILE_SIZE + inset);

        boolean locked = plotType == FarmPlot.FARM_PLOT
                && soil != null
                && soil.getState() == SoilState.LOCKED;
        target.setOpacity(statusTint ? 0.46 : (locked ? 0.70 : 1.0));

        // 每格只有极轻微、确定性的明暗差，避免重新变成一整块纯色矩形；
        // 不使用随机数，因此不会污染 RandomProvider/存档复现。
        double[] brightnessOffsets = {-0.025, -0.008, 0.010, 0.022};
        double subtle = brightnessOffsets[Math.floorMod(row * 5 + column * 3, brightnessOffsets.length)];

        if (variant == GroundVariant.WET) {
            ColorAdjust wetAdjust = new ColorAdjust();
            wetAdjust.setBrightness(-0.12 + subtle);
            wetAdjust.setSaturation(0.08);
            wetAdjust.setHue(-0.04);
            target.setEffect(wetAdjust);
        } else if (variant == GroundVariant.TILLED) {
            ColorAdjust soilAdjust = new ColorAdjust();
            soilAdjust.setBrightness(subtle);
            target.setEffect(soilAdjust);
        } else if (locked) {
            ColorAdjust lockedAdjust = new ColorAdjust();
            lockedAdjust.setBrightness(-0.12);
            lockedAdjust.setSaturation(-0.20);
            target.setEffect(lockedAdjust);
        } else {
            target.setEffect(null);
        }
        target.setVisible(true);
    }

    /**
     * 按格当前状态刷新作物占位块（位置居中于格内），并联动刷新贴图层（P1）。
     */
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
        } else {
            int size = cropBlockSizeFor(crop.getGrowthStage());
            cropBlock.setWidth(size);
            cropBlock.setHeight(size);
            cropBlock.setX(column * TILE_SIZE + (TILE_SIZE - size) / 2.0);
            cropBlock.setY(row * TILE_SIZE + (TILE_SIZE - size) / 2.0);
            cropBlock.setVisible(true);
        }
        updateCropSprite(row, column, plotType, soil);
    }

    /**
     * P1 作物贴图层刷新（UI规范 §7 Tile 组合策略；决策 D1/D2/D3）。
     *
     * <p>显示条件：FARM_PLOT + PLANTED + 作物非空 + 阶段非空且非 WITHERED；
     * MATURE 显示末帧贴图并保留整格高亮底色（决策 D2）。贴图经
     * {@link AImageAssets#viewFor} 切取，2× 放大后底对齐水平居中，
     * 高于格子上边界时向上越界不裁切（决策 D1）。
     *
     * <p>坏数据兜底：crop_type/stage 为 null、图集缺失或加载失败时
     * {@code viewFor} 返回 null，此时仅隐藏贴图层，方块逻辑保持原样
     * （含 MATURE 整格高亮），外观与行为与改造前完全一致。
     *
     * @param row      全局行坐标
     * @param column   全局列坐标
     * @param plotType 格类型
     * @param soil     该格土地（装饰区为 null）
     */
    private void updateCropSprite(int row, int column, FarmPlot plotType, Soil soil) {
        ImageView cropSprite = cropSprites[row][column];
        Crop crop = soil == null ? null : soil.getCrop();
        boolean showSprite = plotType == FarmPlot.FARM_PLOT
                && soil != null
                && soil.getState() == SoilState.PLANTED
                && crop != null
                && crop.getCropType() != null
                && crop.getGrowthStage() != null
                && crop.getGrowthStage() != GrowthStage.WITHERED;
        if (!showSprite) {
            // 非种植格/无作物/阶段坏数据/WITHERED（决策 D3）：不显示贴图
            cropSprite.setVisible(false);
            cropSprite.setImage(null); // 👈 加上了这行，清空图片
            return;
        }
        ImageView view = AImageAssets.viewForTile(crop.getCropType(), crop.getGrowthStage(), 40);
        if (view == null) {
            // 贴图缺失或枚举坏数据：隐藏贴图，方块逻辑原样（含 MATURE 整格高亮）
            cropSprite.setVisible(false);
            cropSprite.setImage(null); // 👈 加上了这行，清空图片
            return;
        }
        cropSprite.setImage(view.getImage());
        cropSprite.setViewport(view.getViewport());
        cropSprite.setFitWidth(view.getFitWidth());
        cropSprite.setFitHeight(view.getFitHeight());
        cropSprite.setPreserveRatio(view.isPreserveRatio());
        cropSprite.setSmooth(view.isSmooth());
        // P4：最大 40×40、底对齐水平居中，统一视觉锚点，避免作物悬空/越格
        cropSprite.setX(column * TILE_SIZE + (TILE_SIZE - cropSprite.getFitWidth()) / 2.0);
        cropSprite.setY((row + 1) * TILE_SIZE - cropSprite.getFitHeight());
        cropSprite.setVisible(true);
        // 避免贴图与 P0 占位块双显示（MATURE 方块本已隐藏，此处幂等）
        cropBlocks[row][column].setVisible(false);
    }

    /** 动作完成后刷新对应格：底色 + 作物块 + Tooltip 文案。 */
    public void refreshTile(Soil soil) {
        if (soil == null) {
            return;
        }
        int row = soil.getRow();
        int column = soil.getColumn();
        FarmPlot plotType = farm.getPlotType(row, column);

        Crop crop = soil.getCrop();
        UUID cropUuid = crop == null ? null : crop.getCropUuid();
        GrowthStage previousStage = cropUuid == null ? null : lastRenderedStages.get(cropUuid);
        GrowthStage currentStage = crop == null ? null : crop.getGrowthStage();

        tiles[row][column].setFill(tileColorFor(plotType, soil));
        updateGroundTexture(row, column, plotType, soil);
        updateCropBlock(row, column, plotType, soil);
        refreshTooltip(soil);

        if (cropUuid != null && currentStage != null) {
            lastRenderedStages.put(cropUuid, currentStage);
            if (previousStage != null && previousStage != currentStage) {
                animateGrowthStageChange(soil, currentStage == GrowthStage.MATURE);
            }
        }
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
        // 点击会隐藏 Tooltip（JavaFX 默认）；选中后立刻重开，
        // 鼠标不离开格子也能持续看到状态。
        // 守卫：无窗口环境（单元测试）跳过 .show()，避免
        // "The owner node needs to be associated with a window"
        Rectangle tile = tiles[soil.getRow()][soil.getColumn()];
        if (tile.getScene() != null && tile.getScene().getWindow() != null) {
            tooltips[soil.getRow()][soil.getColumn()]
                    .show(tile, TILE_SIZE / 2.0, TILE_SIZE / 2.0);
        }
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
        menuBox.toFront();
    }

    /**
     * 把操作菜单提升到与地图同尺寸的上层 Pane。正式 DecorationOverlayView 调用本方法，
     * 让播种/浇水/施肥卡片永远高于装饰；FarmView 单独测试时仍保持原父节点。
     */
    public void promoteMenuTo(Pane host) {
        if (host == null || host == menuHost) {
            return;
        }
        if (menuBox.getParent() instanceof Pane oldHost) {
            oldHost.getChildren().remove(menuBox);
        }
        host.getChildren().add(menuBox);
        menuHost = host;
        if (menuBox.isVisible()) {
            menuBox.toFront();
        }
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
        // 守卫：无窗口环境（单元测试）跳过 .show()，避免
        // "The owner node needs to be associated with a window"
        Rectangle tile = tiles[row][column];
        if (tile.getScene() != null && tile.getScene().getWindow() != null) {
            tooltips[row][column].show(tile, TILE_SIZE / 2.0, TILE_SIZE / 2.0);
        }
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
        button.getStyleClass().addAll("primary-button", "farm-action-button");
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

    /** P4：开垦反馈。 */
    public void animateReclaim(Soil soil) {
        animateTileFlash(soil, COLOR_HIGHLIGHT, 0.42);
    }

    /** P4：播种反馈，先闪格再轻微弹出作物。 */
    public void animatePlant(Soil soil) {
        animateTileFlash(soil, COLOR_GRASS, 0.30);
        animateGrowthStageChange(soil, false);
    }

    /** P4：浇水反馈。 */
    public void animateWater(Soil soil) {
        animateTileFlash(soil, Color.rgb(0x75, 0xB7, 0xD9), 0.40);
    }

    /** P4：施肥反馈。 */
    public void animateFertilize(Soil soil) {
        animateTileFlash(soil, COLOR_HIGHLIGHT, 0.36);
        animateGrowthStageChange(soil, false);
    }

    /** P4：收获反馈。 */
    public void animateHarvest(Soil soil) {
        animateTileFlash(soil, COLOR_HIGHLIGHT, 0.44);
    }

    private void animateGrowthStageChange(Soil soil, boolean mature) {
        if (soil == null) {
            return;
        }
        ImageView sprite = cropSprites[soil.getRow()][soil.getColumn()];
        if (sprite == null || !sprite.isVisible()) {
            return;
        }
        ScaleTransition scale = new ScaleTransition(Duration.millis(mature ? 360 : 220), sprite);
        scale.setFromX(mature ? 0.78 : 0.90);
        scale.setFromY(mature ? 0.78 : 0.90);
        scale.setToX(mature ? 1.10 : 1.0);
        scale.setToY(mature ? 1.10 : 1.0);
        scale.setAutoReverse(mature);
        scale.setCycleCount(mature ? 2 : 1);
        scale.play();
    }

    private void animateTileFlash(Soil soil, Color color, double opacity) {
        if (soil == null) {
            return;
        }
        Rectangle flash = new Rectangle(
                soil.getColumn() * TILE_SIZE,
                soil.getRow() * TILE_SIZE,
                TILE_SIZE,
                TILE_SIZE);
        flash.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), opacity));
        flash.setMouseTransparent(true);
        getChildren().add(flash);
        flash.toFront();
        selectionRect.toFront();
        if (menuHost == this) {
            menuBox.toFront();
        }

        FadeTransition fade = new FadeTransition(Duration.millis(320), flash);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setOnFinished(event -> getChildren().remove(flash));
        fade.play();
    }

    private void buildMenu() {
        menuBox.setPadding(MENU_PADDING);
        menuBox.getStyleClass().add("farm-action-menu");
        UiTheme.apply(menuBox);
        menuBox.setVisible(false);
    }

    private void applyButtonStyle(Button button, boolean disabled) {
        // 保留方法签名供历史代码兼容；P4 以后四态统一由 CSS :hover/:pressed/:disabled 管理。
        if (!button.getStyleClass().contains("primary-button")) {
            button.getStyleClass().add("primary-button");
        }
    }
}
