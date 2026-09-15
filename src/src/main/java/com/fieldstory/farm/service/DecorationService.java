package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.economy.DecorationPlacementResult;

import java.util.List;

/**
 * B 模块 P1 装饰库存与放置业务入口。
 *
 * <p>拥有状态与放置状态均通过同一服务维护；外部模块不得直接改坐标。
 */
public interface DecorationService {

    List<Decoration> getOwnedDecorations();

    List<Decoration> getPlacedDecorations();

    int getOwnedCount(DecorationType type);

    int getPlacedCount(DecorationType type);

    List<Decoration> addPurchasedDecoration(DecorationType type, int quantity);

    boolean canPlace(Decoration decoration, int row, int column);

    DecorationPlacementResult place(Decoration decoration, int row, int column);

    DecorationPlacementResult move(Decoration decoration, int row, int column);

    DecorationPlacementResult removeFromFarm(Decoration decoration);

    boolean isDecorationArea(int row, int column);

    boolean isOccupied(int row, int column, Decoration ignore);
}
