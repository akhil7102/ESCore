package fun.endcore.escoins.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fun.endcore.escoins.ESCoins;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * MySQL and MariaDB database implementation using HikariCP.
 */
public class MySQLDatabase extends DatabaseManager {
    private HikariDataSource dataSource;

    public MySQLDatabase(ESCoins plugin) {
        super(plugin);
    }

    @Override
    public void initialize() throws SQLException {
        FileConfiguration dbConfig = plugin.getConfigManager().getDatabase();
        String host = dbConfig.getString("database.mysql.host", "localhost");
        int port = dbConfig.getInt("database.mysql.port", 3306);
        String database = dbConfig.getString("database.mysql.database", "escoins");
        String username = dbConfig.getString("database.mysql.username", "root");
        String password = dbConfig.getString("database.mysql.password", "");
        int poolSize = dbConfig.getInt("database.mysql.pool-size", 5);
        long connTimeout = dbConfig.getLong("database.mysql.connection-timeout", 30000);
        long maxLifetime = dbConfig.getLong("database.mysql.max-lifetime", 1800000);

        HikariConfig config = new HikariConfig();
        config.setPoolName("ESCoins-MySQL-Pool");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8mb4&serverTimezone=UTC");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(Math.max(2, poolSize));
        config.setConnectionTimeout(connTimeout);
        config.setMaxLifetime(maxLifetime);

        // Standard optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        this.dataSource = new HikariDataSource(config);

        // Execute table migrations
        createTables();
        plugin.getLogger().info("MySQL database connected successfully to " + host + ":" + port + "/" + database);
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("MySQL DataSource is closed or uninitialized");
        }
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
                plugin.getLogger().info("MySQL database connection pool closed.");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error closing MySQL connection pool", e);
            }
        }
    }

    @Override
    protected String getUpsertPlayerSql() {
        return "INSERT INTO escoins_players (uuid, username, balance, created_at, updated_at) " +
               "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
               "ON DUPLICATE KEY UPDATE " +
               "username = VALUES(username), " +
               "updated_at = CURRENT_TIMESTAMP;";
    }

    @Override
    protected String getCreatePlayersTableSql() {
        return "CREATE TABLE IF NOT EXISTS escoins_players (" +
               "uuid VARCHAR(36) NOT NULL, " +
               "username VARCHAR(16) NOT NULL, " +
               "balance BIGINT NOT NULL DEFAULT 0, " +
               "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
               "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
               "PRIMARY KEY (uuid)" +
               ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
    }

    @Override
    protected String getCreateTransactionsTableSql() {
        return "CREATE TABLE IF NOT EXISTS escoins_transactions (" +
               "transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
               "player_uuid VARCHAR(36) NOT NULL, " +
               "type VARCHAR(32) NOT NULL, " +
               "amount BIGINT NOT NULL, " +
               "balance_before BIGINT NOT NULL, " +
               "balance_after BIGINT NOT NULL, " +
               "source VARCHAR(64) NOT NULL, " +
               "target_uuid VARCHAR(36), " +
               "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
               ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
    }

    @Override
    protected String getCreateLocationsTableSql() {
        return "CREATE TABLE IF NOT EXISTS escore_player_locations (" +
               "uuid VARCHAR(36) NOT NULL, " +
               "world VARCHAR(64) NOT NULL, " +
               "x DOUBLE NOT NULL, " +
               "y DOUBLE NOT NULL, " +
               "z DOUBLE NOT NULL, " +
               "yaw FLOAT NOT NULL, " +
               "pitch FLOAT NOT NULL, " +
               "has_joined_before INT NOT NULL DEFAULT 1, " +
               "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
               "PRIMARY KEY (uuid)" +
               ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
    }

    @Override
    protected String getUpsertLocationSql() {
        return "INSERT INTO escore_player_locations (uuid, world, x, y, z, yaw, pitch, has_joined_before, updated_at) " +
               "VALUES (?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP) " +
               "ON DUPLICATE KEY UPDATE " +
               "world = VALUES(world), " +
               "x = VALUES(x), " +
               "y = VALUES(y), " +
               "z = VALUES(z), " +
               "yaw = VALUES(yaw), " +
               "pitch = VALUES(pitch), " +
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
               "is_active BOOLEAN NOT NULL DEFAULT 1, " +
               "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
               "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
               "PRIMARY KEY (uuid, cosmetic_type)" +
               ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
    }

    @Override
    protected String getUpsertCosmeticSql() {
        return "INSERT INTO escore_player_cosmetics (uuid, cosmetic_type, color, ownership_type, expires_at, is_active, updated_at) " +
               "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
               "ON DUPLICATE KEY UPDATE " +
               "color = VALUES(color), " +
               "ownership_type = VALUES(ownership_type), " +
               "expires_at = VALUES(expires_at), " +
               "is_active = VALUES(is_active), " +
               "updated_at = CURRENT_TIMESTAMP;";
    }
}
