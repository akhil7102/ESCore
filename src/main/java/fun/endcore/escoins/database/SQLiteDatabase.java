package fun.endcore.escoins.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fun.endcore.escoins.ESCoins;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * SQLite database implementation using HikariCP and WAL mode.
 */
public class SQLiteDatabase extends DatabaseManager {
    private HikariDataSource dataSource;
    private final String fileName;

    public SQLiteDatabase(ESCoins plugin, String fileName) {
        super(plugin);
        this.fileName = (fileName == null || fileName.isEmpty()) ? "coins.db" : fileName;
    }

    @Override
    public void initialize() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), fileName);
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("ESCoins-SQLite-Pool");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        // For SQLite, a single connection pool with WAL mode prevents database lock contention
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        // Connection PRAGMAs for optimal performance and concurrency
        config.setConnectionInitSql("PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL; PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 10000;");

        this.dataSource = new HikariDataSource(config);

        // Execute table migrations
        createTables();
        plugin.getLogger().info("SQLite database connected successfully (" + dbFile.getName() + ").");
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("SQLite DataSource is closed or uninitialized");
        }
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
                plugin.getLogger().info("SQLite database connection pool closed.");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error closing SQLite connection pool", e);
            }
        }
    }

    @Override
    protected String getUpsertPlayerSql() {
        return "INSERT INTO escoins_players (uuid, username, balance, created_at, updated_at) " +
               "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
               "ON CONFLICT(uuid) DO UPDATE SET " +
               "username = excluded.username, " +
               "updated_at = CURRENT_TIMESTAMP;";
    }

    @Override
    protected String getCreatePlayersTableSql() {
        return "CREATE TABLE IF NOT EXISTS escoins_players (" +
               "uuid VARCHAR(36) PRIMARY KEY, " +
               "username VARCHAR(16) NOT NULL, " +
               "balance BIGINT NOT NULL DEFAULT 0, " +
               "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
               "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
               ");";
    }

    @Override
    protected String getCreateTransactionsTableSql() {
        return "CREATE TABLE IF NOT EXISTS escoins_transactions (" +
               "transaction_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
               "player_uuid VARCHAR(36) NOT NULL, " +
               "type VARCHAR(32) NOT NULL, " +
               "amount BIGINT NOT NULL, " +
               "balance_before BIGINT NOT NULL, " +
               "balance_after BIGINT NOT NULL, " +
               "source VARCHAR(64) NOT NULL, " +
               "target_uuid VARCHAR(36), " +
               "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
               ");";
    }

    @Override
    protected String getCreateLocationsTableSql() {
        return "CREATE TABLE IF NOT EXISTS escore_player_locations (" +
               "uuid VARCHAR(36) PRIMARY KEY, " +
               "world VARCHAR(64) NOT NULL, " +
               "x DOUBLE NOT NULL, " +
               "y DOUBLE NOT NULL, " +
               "z DOUBLE NOT NULL, " +
               "yaw FLOAT NOT NULL, " +
               "pitch FLOAT NOT NULL, " +
               "has_joined_before INTEGER NOT NULL DEFAULT 1, " +
               "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
               ");";
    }

    @Override
    protected String getUpsertLocationSql() {
        return "INSERT INTO escore_player_locations (uuid, world, x, y, z, yaw, pitch, has_joined_before, updated_at) " +
               "VALUES (?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP) " +
               "ON CONFLICT(uuid) DO UPDATE SET " +
               "world = excluded.world, " +
               "x = excluded.x, " +
               "y = excluded.y, " +
               "z = excluded.z, " +
               "yaw = excluded.yaw, " +
               "pitch = excluded.pitch, " +
               "has_joined_before = 1, " +
               "updated_at = CURRENT_TIMESTAMP;";
    }

    @Override
    protected String getCreateCosmeticsTableSql() {
        return "CREATE TABLE IF NOT EXISTS escore_player_cosmetics (" +
               "uuid VARCHAR(36) NOT NULL, " +
               "cosmetic_type VARCHAR(32) NOT NULL, " +
               "color VARCHAR(32) NOT NULL, " +
               "ownership_type VARCHAR(16) NOT NULL, " +
               "expires_at BIGINT, " +
               "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
               "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
               "PRIMARY KEY (uuid, cosmetic_type)" +
               ");";
    }

    @Override
    protected String getUpsertCosmeticSql() {
        return "INSERT INTO escore_player_cosmetics (uuid, cosmetic_type, color, ownership_type, expires_at, updated_at) " +
               "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
               "ON CONFLICT(uuid, cosmetic_type) DO UPDATE SET " +
               "color = excluded.color, " +
               "ownership_type = excluded.ownership_type, " +
               "expires_at = excluded.expires_at, " +
               "updated_at = CURRENT_TIMESTAMP;";
    }

    @Override
    protected String getCreateTagsTableSql() {
        return "CREATE TABLE IF NOT EXISTS escore_player_tags (" +
               "uuid VARCHAR(36) NOT NULL, " +
               "tag_id VARCHAR(64) NOT NULL, " +
               "is_active INTEGER NOT NULL DEFAULT 0, " +
               "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
               "PRIMARY KEY (uuid, tag_id)" +
               ");";
    }

    @Override
    protected String getInsertTagSql() {
        return "INSERT INTO escore_player_tags (uuid, tag_id, is_active) VALUES (?, ?, 0) " +
               "ON CONFLICT(uuid, tag_id) DO NOTHING;";
    }
}
