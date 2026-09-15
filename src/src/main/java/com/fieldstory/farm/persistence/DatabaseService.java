package com.fieldstory.farm.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 数据库服务（E 模块 P1；脚手架 §5.1 persistence 层、验收规范 §七十一/§一百四十四）。
 *
 * <p>职责：数据库位置、连接获取、连接级 PRAGMA、结构迁移入口、E 内部 {@code meta} 键值读写。
 * <b>不含任何游戏业务</b>——业务数据由各 DAO 读写，Service 不得直接写 SQL（验收规范 §七十五）。
 *
 * <ul>
 *   <li>默认路径 {@code data/farm.db}（相对运行目录），禁止放入 {@code src/main/resources}
 *       （验收规范 §一百四十四）；{@code data/} 与 {@code *.db} 已在 .gitignore 忽略；</li>
 *   <li>{@link #openConnection()} 会建父目录、开启外键、设置 WAL 与忙等超时，
 *       并调用 {@link SchemaMigrator} 把库升级到最新结构版本（旧档原地升级，不丢数据）；</li>
 *   <li>连接由调用方用 try-with-resources 关闭；一次存档/读档用一条连接，事务边界由上层控制。</li>
 * </ul>
 */
public class DatabaseService {

    /** 默认数据库文件（相对工程运行目录），验收规范 §一百四十四。 */
    public static final String DEFAULT_DB_FILE = "data/farm.db";

    private final Path databaseFile;

    public DatabaseService() {
        this(Paths.get(DEFAULT_DB_FILE));
    }

    /**
     * 指定数据库文件构造（测试用临时路径）。
     *
     * @param databaseFile 数据库文件路径，不得为 null
     */
    public DatabaseService(Path databaseFile) {
        if (databaseFile == null) {
            throw new IllegalArgumentException("databaseFile 不能为空");
        }
        this.databaseFile = databaseFile;
    }

    /** 数据库文件位置（供测试与诊断使用）。 */
    public Path getDatabaseFile() {
        return databaseFile;
    }

    /** 数据库文件是否已存在（不代表已有玩家数据，是否“有档”由 {@code SqliteSaveService} 判定）。 */
    public boolean exists() {
        return Files.isRegularFile(databaseFile);
    }

    /**
     * 打开一条已初始化（父目录、PRAGMA、结构迁移就绪）的连接。
     *
     * @return 可用的 JDBC 连接，调用方负责关闭
     * @throws IllegalStateException 目录无法创建或数据库无法打开/迁移失败
     */
    public Connection openConnection() {
        Path absolute = databaseFile.toAbsolutePath();
        try {
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法创建数据库目录: " + absolute.getParent(), e);
        }
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
            boolean ready = false;
            try {
                applyConnectionPragmas(connection);
                SchemaMigrator.migrate(connection);
                ready = true;
                return connection;
            } finally {
                if (!ready) {
                    // 初始化/迁移失败时关闭连接，避免文件句柄泄漏（Windows 下会锁住 db 文件）
                    try {
                        connection.close();
                    } catch (SQLException ignored) {
                        // 关闭失败无需处理：原始异常更重要
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("无法打开 SQLite 数据库: " + absolute, e);
        }
    }

    /** 连接级 PRAGMA：外键约束、忙等超时、WAL 日志模式（崩溃恢复更稳）。 */
    private static void applyConnectionPragmas(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
        }
    }

    // ------------------------------------------------------------------
    // meta：E 持久化内部键值（JSON 迁移标记等）
    // ------------------------------------------------------------------

    /** 读取 {@code meta} 值；不存在返回 {@code null}。 */
    public String getMeta(Connection connection, String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT meta_value FROM meta WHERE meta_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** 写入/覆盖 {@code meta} 键值。 */
    public void putMeta(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO meta(meta_key, meta_value) VALUES(?, ?) "
                        + "ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
