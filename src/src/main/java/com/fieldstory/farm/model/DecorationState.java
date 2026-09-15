package com.fieldstory.farm.model;

/**
 * 装饰快照（E 存档聚合的一部分，P1 起随存档持久化）。
 *
 * <p>与 {@link PlotState} 同类：这是 E 自己的“存档映射用”数据对象，
 * 只保存“放了什么装饰、放在哪一格”，不含任何装饰效果/Buff 计算。
 * {@code decorationType} 以字符串保存，E 不直接持有 B 模块装饰枚举，
 * 待 B 模块装饰模型交付后由适配层在“枚举 ↔ 字符串”之间映射。
 *
 * <p>P1 的 {@code decoration} 表（验收规范 §七十二）对应的正是本类型；
 * P0 阶段本列表为空，P1 装饰系统接入后由 B 侧写入。
 */
public class DecorationState {

    /** 装饰唯一 id；0 表示尚未分配（插入数据库时由自增主键分配）。 */
    private long id;

    /** 装饰类型名（对应未来 B 模块装饰枚举名，如 STONE_LANTERN）。 */
    private String decorationType;

    /** 所在行坐标（全局地图 0-based）。 */
    private int row;

    /** 所在列坐标（全局地图 0-based）。 */
    private int column;

    public DecorationState() {
        // 空构造供反序列化使用
    }

    public DecorationState(String decorationType, int row, int column) {
        this.decorationType = decorationType;
        this.row = row;
        this.column = column;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDecorationType() {
        return decorationType;
    }

    public void setDecorationType(String decorationType) {
        this.decorationType = decorationType;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }
}
