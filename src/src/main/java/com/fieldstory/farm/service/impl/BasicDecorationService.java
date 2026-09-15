package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.DecorationState;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmPlot;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.economy.DecorationPlacementResult;
import com.fieldstory.farm.model.impl.BasicDecoration;
import com.fieldstory.farm.service.DecorationService;
import com.fieldstory.farm.util.GameConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * B 模块 P1 DecorationService 默认实现。
 *
 * <p>运行态使用 B 的 Decoration；持久化快照写回 E 的 GameState.decorations。
 * row/column = -1 表示“已拥有但在仓库”，因此购买后即使尚未放置也会进入 SQLite。
 */
public class BasicDecorationService implements DecorationService {

    private final Farm farm;
    private final GameState gameState;
    private final List<Decoration> ownedDecorations = new ArrayList<>();
    private long nextId = 1L;

    public BasicDecorationService(Farm farm, GameState gameState) {
        this.farm = Objects.requireNonNull(farm, "farm");
        this.gameState = Objects.requireNonNull(gameState, "gameState");
        restoreFromState();
    }

    @Override
    public List<Decoration> getOwnedDecorations() {
        return List.copyOf(ownedDecorations);
    }

    @Override
    public List<Decoration> getPlacedDecorations() {
        return ownedDecorations.stream()
                .filter(Decoration::isPlaced)
                .toList();
    }

    @Override
    public int getOwnedCount(DecorationType type) {
        if (type == null) {
            return 0;
        }
        return (int) ownedDecorations.stream()
                .filter(d -> d.getDecorationType() == type)
                .count();
    }

    @Override
    public int getPlacedCount(DecorationType type) {
        if (type == null) {
            return 0;
        }
        return (int) ownedDecorations.stream()
                .filter(Decoration::isPlaced)
                .filter(d -> d.getDecorationType() == type)
                .count();
    }

    @Override
    public List<Decoration> addPurchasedDecoration(DecorationType type, int quantity) {
        Objects.requireNonNull(type, "decorationType");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        List<Decoration> created = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            Decoration decoration = new BasicDecoration(nextId++, type);
            ownedDecorations.add(decoration);
            created.add(decoration);
        }
        syncState();
        return List.copyOf(created);
    }

    @Override
    public boolean canPlace(Decoration decoration, int row, int column) {
        return placementCheck(decoration, row, column, false)
                == DecorationPlacementResult.SUCCESS;
    }

    @Override
    public DecorationPlacementResult place(Decoration decoration, int row, int column) {
        DecorationPlacementResult result = placementCheck(decoration, row, column, false);
        if (result != DecorationPlacementResult.SUCCESS) {
            return result;
        }
        decoration.setRow(row);
        decoration.setColumn(column);
        syncState();
        return DecorationPlacementResult.SUCCESS;
    }

    @Override
    public DecorationPlacementResult move(Decoration decoration, int row, int column) {
        if (decoration == null || !ownedDecorations.contains(decoration)) {
            return DecorationPlacementResult.NOT_OWNED;
        }
        if (!decoration.isPlaced()) {
            return DecorationPlacementResult.NOT_PLACED;
        }

        int oldRow = decoration.getRow();
        int oldColumn = decoration.getColumn();

        // 校验时忽略自身占位；失败时完全不改原状态。
        DecorationPlacementResult result = placementCheck(decoration, row, column, true);
        if (result != DecorationPlacementResult.SUCCESS) {
            return result;
        }

        decoration.setRow(row);
        decoration.setColumn(column);
        try {
            syncState();
        } catch (RuntimeException ex) {
            decoration.setRow(oldRow);
            decoration.setColumn(oldColumn);
            throw ex;
        }
        return DecorationPlacementResult.SUCCESS;
    }

    @Override
    public DecorationPlacementResult removeFromFarm(Decoration decoration) {
        if (decoration == null || !ownedDecorations.contains(decoration)) {
            return DecorationPlacementResult.NOT_OWNED;
        }
        if (!decoration.isPlaced()) {
            return DecorationPlacementResult.NOT_PLACED;
        }
        decoration.setRow(-1);
        decoration.setColumn(-1);
        syncState();
        return DecorationPlacementResult.SUCCESS;
    }

    @Override
    public boolean isDecorationArea(int row, int column) {
        if (!insideMap(row, column)) {
            return false;
        }
        return farm.getPlotType(row, column) == FarmPlot.DECORATION_AREA;
    }

    @Override
    public boolean isOccupied(int row, int column, Decoration ignore) {
        for (Decoration other : ownedDecorations) {
            if (!other.isPlaced() || other == ignore) {
                continue;
            }
            DecorationType type = other.getDecorationType();
            if (type == null) {
                continue;
            }
            int height = type.getHeight();
            int width = type.getWidth();
            if (row >= other.getRow() && row < other.getRow() + height
                    && column >= other.getColumn() && column < other.getColumn() + width) {
                return true;
            }
        }
        return false;
    }

    private DecorationPlacementResult placementCheck(Decoration decoration,
                                                      int row,
                                                      int column,
                                                      boolean allowAlreadyPlaced) {
        if (decoration == null || !ownedDecorations.contains(decoration)) {
            return DecorationPlacementResult.NOT_OWNED;
        }
        if (decoration.isPlaced() && !allowAlreadyPlaced) {
            return DecorationPlacementResult.ALREADY_PLACED;
        }

        DecorationType type = decoration.getDecorationType();
        if (type == null || type.getWidth() <= 0 || type.getHeight() <= 0) {
            return DecorationPlacementResult.INVALID_SIZE;
        }

        int height = type.getHeight();
        int width = type.getWidth();
        if (row < 0 || column < 0
                || row + height > GameConstants.MAP_ROWS
                || column + width > GameConstants.MAP_COLS) {
            return DecorationPlacementResult.OUT_OF_BOUNDS;
        }

        for (int r = row; r < row + height; r++) {
            for (int c = column; c < column + width; c++) {
                if (!isDecorationArea(r, c)) {
                    return DecorationPlacementResult.NOT_DECORATION_AREA;
                }
                if (isOccupied(r, c, decoration)) {
                    return DecorationPlacementResult.CELL_OCCUPIED;
                }
            }
        }
        return DecorationPlacementResult.SUCCESS;
    }

    private boolean insideMap(int row, int column) {
        return row >= 0 && row < GameConstants.MAP_ROWS
                && column >= 0 && column < GameConstants.MAP_COLS;
    }

    private void restoreFromState() {
        long maxId = 0L;
        for (DecorationState state : gameState.getDecorations()) {
            if (state == null) {
                continue;
            }
            DecorationType type = parseType(state.getDecorationType());
            if (type == null) {
                continue;
            }
            long id = state.getId();
            if (id <= 0) {
                id = ++maxId;
            }
            maxId = Math.max(maxId, id);
            ownedDecorations.add(new BasicDecoration(
                    id, type, state.getRow(), state.getColumn()));
        }
        ownedDecorations.sort(Comparator.comparingLong(Decoration::getId));
        nextId = Math.max(1L, maxId + 1L);
        syncState();
    }

    /**
     * 把 B 的运行态装饰同步回 E 的 GameState；真正 SQLite 落盘仍由 E SaveService 负责。
     */
    private void syncState() {
        List<DecorationState> snapshots = gameState.getDecorations();
        snapshots.clear();
        for (Decoration decoration : ownedDecorations) {
            DecorationState state = new DecorationState(
                    decoration.getDecorationType().name(),
                    decoration.getRow(),
                    decoration.getColumn());
            state.setId(decoration.getId());
            snapshots.add(state);
        }
    }

    private DecorationType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return DecorationType.valueOf(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
