package com.fieldstory.farm.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.PlotState;
import com.fieldstory.farm.service.SaveService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * P0 JSON 临时存档实现（验收规范 §四十~§四十二；脚手架 §九 运行数据 data/）。
 *
 * <ul>
 *   <li>默认文件：{@code data/save.json}（.gitignore 已忽略 data/，本地存档不进仓库）；</li>
 *   <li>根节点保留 {@code version}（=2）与 {@code schema} 标识：P1 迁 SQLite 时
 *       按版本增量迁移、读旧 JSON 一次性导入，禁止原地改结构；
 *       v1 旧档仍可读入（缺 {@code player.seedInventory} 时按空库存处理），见
 *       {@link #MIN_SUPPORTED_VERSION}；</li>
 *   <li>保存内容：Player 经济（name/gold）与其<b>唯一种子库存</b>
 *       {@code player.seedInventory}（验收 §十八/§四十一；B 模块 §6.2 禁止第二份库存）、
 *       gameDay（对应 GameClock.getGameDay）、unlocked（已解锁内容）、
 *       plots（每块土地/作物状态，验收 §四十一）；</li>
 *   <li>种子库存键为 {@link CropType} 枚举名（WHEAT/CORN/CARROT），土地状态/作物类型
 *       仍以字符串保存，不依赖 A/D 尚未交付的其余枚举类型；</li>
 *   <li>反序列化只恢复退出瞬间状态，不执行任何离线成长计算（离线模拟属 P2）。</li>
 * </ul>
 */
public class JsonSaveService implements SaveService {

    /** 当前存档结构版本；升级结构时必须递增并在加载时做兼容处理（v2：新增 player.seedInventory） */
    public static final int SAVE_VERSION = 2;

    /** 仍可读入的最低存档版本；低于此版本的旧档直接拒绝 */
    public static final int MIN_SUPPORTED_VERSION = 1;

    /** 存档格式标识（区分未来 SQLite 正式存档） */
    public static final String SCHEMA = "P0-json";

    /** 默认存档文件（相对工程运行目录） */
    public static final String DEFAULT_SAVE_FILE = "data/save.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path saveFile;

    public JsonSaveService() {
        this(Paths.get(DEFAULT_SAVE_FILE));
    }

    public JsonSaveService(Path saveFile) {
        if (saveFile == null) {
            throw new IllegalArgumentException("saveFile 不能为空");
        }
        this.saveFile = saveFile;
    }

    /** 当前存档文件位置（供测试与 P1 迁移使用）。 */
    public Path getSaveFile() {
        return saveFile;
    }

    @Override
    public boolean hasSave() {
        return Files.isRegularFile(saveFile);
    }

    @Override
    public void save(GameState state) {
        if (state == null) {
            throw new IllegalArgumentException("GameState 不能为空");
        }
        try {
            if (saveFile.getParent() != null) {
                Files.createDirectories(saveFile.getParent());
            }
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toRoot(state));
            Files.writeString(saveFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("存档写入失败: " + saveFile.toAbsolutePath(), e);
        }
    }

    @Override
    public GameState load() {
        if (!hasSave()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(Files.readString(saveFile, StandardCharsets.UTF_8));
            return toGameState(root);
        } catch (IOException e) {
            throw new IllegalStateException("存档文件损坏或无法读取: " + saveFile.toAbsolutePath(), e);
        }
    }

    // ------------------------------------------------------------------
    // 序列化：GameState -> JSON
    // ------------------------------------------------------------------

    private ObjectNode toRoot(GameState state) {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", SAVE_VERSION);
        root.put("schema", SCHEMA);
        root.put("savedAt", LocalDateTime.now().toString());
        root.put("gameDay", state.getGameDay());

        String worldTime = state.getCurrentWorldTime();
        if (worldTime == null) {
            root.putNull("currentWorldTime");
        } else {
            root.put("currentWorldTime", worldTime);
        }
        String lastRealTime = state.getLastRealTime();
        if (lastRealTime == null) {
            root.putNull("lastRealTime");
        } else {
            root.put("lastRealTime", lastRealTime);
        }

        Player player = state.getPlayer();
        if (player != null) {
            ObjectNode playerNode = root.putObject("player");
            playerNode.put("name", player.getName());
            playerNode.put("gold", player.getGold());
            playerNode.set("seedInventory", toSeedInventoryNode(player.getSeedInventory()));
        } else {
            root.putNull("player");
        }

        ArrayNode unlocked = root.putArray("unlocked");
        for (String key : state.getUnlocked()) {
            if (key != null) {
                unlocked.add(key);
            }
        }

        ArrayNode plots = root.putArray("plots");
        for (PlotState plot : state.getPlots()) {
            if (plot != null) {
                plots.add(toPlotNode(plot));
            }
        }
        return root;
    }

    /** 种子库存（唯一种子库存，来自 {@link Player}）→ JSON 对象：枚举名 → 数量。 */
    private ObjectNode toSeedInventoryNode(Map<CropType, Integer> inventory) {
        ObjectNode node = mapper.createObjectNode();
        if (inventory != null) {
            for (Map.Entry<CropType, Integer> entry : inventory.entrySet()) {
                if (entry.getKey() != null) {
                    node.put(entry.getKey().name(), entry.getValue() == null ? 0 : entry.getValue());
                }
            }
        }
        return node;
    }

    private ObjectNode toPlotNode(PlotState plot) {
        ObjectNode node = mapper.createObjectNode();
        String plotId = (plot.getPlotId() == null || plot.getPlotId().isBlank())
                ? plot.getRow() + "," + plot.getColumn()
                : plot.getPlotId();
        node.put("plotId", plotId);
        node.put("row", plot.getRow());
        node.put("column", plot.getColumn());
        node.put("state", plot.getState());

        if (plot.hasCrop()) {
            ObjectNode crop = node.putObject("crop");
            crop.put("cropUuid", plot.getCropUuid());
            crop.put("cropType", plot.getCropType());
            crop.put("growthStage", plot.getGrowthStage());
            crop.put("growthProgress", plot.getGrowthProgress());
            crop.put("plantWorldTime", plot.getPlantWorldTime());
            crop.put("manualWaterCount", plot.getManualWaterCount());
            crop.put("lastManualWaterGameDay", plot.getLastManualWaterGameDay());
            crop.put("fertilizerCount", plot.getFertilizerCount());
            crop.put("lastFertilizedGameDay", plot.getLastFertilizedGameDay());
            crop.put("droughtCount", plot.getDroughtCount());
            crop.put("rainCount", plot.getRainCount());
            crop.put("greenRainCount", plot.getGreenRainCount());
            crop.put("lastHydratedWorldTime", plot.getLastHydratedWorldTime());
            crop.put("droughtStreak", plot.getDroughtStreak());
            crop.put("eventCount", plot.getEventCount());
        } else {
            node.putNull("crop");
        }
        return node;
    }

    // ------------------------------------------------------------------
    // 反序列化：JSON -> GameState（仅恢复状态，不做任何成长计算）
    // ------------------------------------------------------------------

    private GameState toGameState(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("存档文件为空或不是 JSON 对象");
        }
        int version = root.path("version").asInt(-1);
        if (version < MIN_SUPPORTED_VERSION || version > SAVE_VERSION) {
            throw new IllegalStateException("存档版本不兼容: 支持 version="
                    + MIN_SUPPORTED_VERSION + "~" + SAVE_VERSION + "，实际 version=" + version);
        }

        GameState state = new GameState();
        JsonNode playerNode = root.get("player");
        if (playerNode != null && playerNode.isObject()) {
            String name = playerNode.path("name").asText(null);
            int gold = playerNode.path("gold").asInt(0);
            Player player = new Player(name, gold);
            player.setSeedInventory(readSeedInventory(playerNode, root));
            state.setPlayer(player);
        }
        state.setGameDay(root.path("gameDay").asLong(0L));

        JsonNode worldTime = root.get("currentWorldTime");
        if (worldTime != null && worldTime.isTextual()) {
            state.setCurrentWorldTime(worldTime.asText());
        }
        JsonNode lastRealTime = root.get("lastRealTime");
        if (lastRealTime != null && lastRealTime.isTextual()) {
            state.setLastRealTime(lastRealTime.asText());
        }

        JsonNode unlocked = root.get("unlocked");
        if (unlocked != null && unlocked.isArray()) {
            for (JsonNode key : unlocked) {
                if (key != null && key.isTextual()) {
                    state.getUnlocked().add(key.asText());
                }
            }
        }

        JsonNode plots = root.get("plots");
        if (plots != null && plots.isArray()) {
            for (JsonNode plotNode : plots) {
                if (plotNode != null && plotNode.isObject()) {
                    state.getPlots().add(toPlotState(plotNode));
                }
            }
        }
        return state;
    }

    /**
     * 读入种子库存。
     *
     * <p>优先取 {@code player.seedInventory}（v2 正式位置）；若缺失则回退根节点
     * {@code seedInventory}，用于兼容早期 v2 快照。v1 旧档两处都没有 → 按空库存处理。
     * 未知作物名（未来扩展）跳过，保证旧程序可向前读入新档而不崩溃。
     */
    private Map<CropType, Integer> readSeedInventory(JsonNode playerNode, JsonNode root) {
        JsonNode seedNode = (playerNode == null) ? null : playerNode.get("seedInventory");
        if (seedNode == null || !seedNode.isObject()) {
            seedNode = root.get("seedInventory");
        }

        Map<CropType, Integer> inventory = new EnumMap<>(CropType.class);
        if (seedNode != null && seedNode.isObject()) {
            for (Map.Entry<String, JsonNode> entry : seedNode.properties()) {
                CropType type = parseCropType(entry.getKey());
                if (type != null) {
                    inventory.put(type, entry.getValue().asInt(0));
                }
            }
        }
        return inventory;
    }

    /** 枚举名 → {@link CropType}；未知/空白名返回 null（调用方跳过）。 */
    private CropType parseCropType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return CropType.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private PlotState toPlotState(JsonNode node) {
        PlotState plot = new PlotState();
        int row = node.path("row").asInt(0);
        int column = node.path("column").asInt(0);
        String plotId = node.path("plotId").asText(null);
        plot.setPlotId((plotId == null || plotId.isBlank()) ? row + "," + column : plotId);
        plot.setRow(row);
        plot.setColumn(column);
        plot.setState(node.path("state").asText(null));

        JsonNode crop = node.get("crop");
        if (crop != null && crop.isObject()) {
            plot.setCropUuid(crop.path("cropUuid").asText(null));
            plot.setCropType(crop.path("cropType").asText(null));
            plot.setGrowthStage(crop.path("growthStage").asText(null));
            plot.setGrowthProgress(crop.path("growthProgress").asDouble(0.0));
            plot.setPlantWorldTime(crop.path("plantWorldTime").asText(null));
            plot.setManualWaterCount(crop.path("manualWaterCount").asInt(0));
            plot.setLastManualWaterGameDay(crop.path("lastManualWaterGameDay").asText(null));
            plot.setFertilizerCount(crop.path("fertilizerCount").asInt(0));
            plot.setLastFertilizedGameDay(crop.path("lastFertilizedGameDay").asText("-1"));
            plot.setDroughtCount(crop.path("droughtCount").asInt(0));
            plot.setRainCount(crop.path("rainCount").asInt(0));
            plot.setGreenRainCount(crop.path("greenRainCount").asInt(0));
            plot.setLastHydratedWorldTime(crop.path("lastHydratedWorldTime").asText("-1"));
            plot.setDroughtStreak(crop.path("droughtStreak").asInt(0));
            plot.setEventCount(crop.path("eventCount").asInt(0));
        }
        return plot;
    }
}
