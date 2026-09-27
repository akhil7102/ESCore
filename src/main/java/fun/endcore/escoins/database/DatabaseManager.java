package fun.endcore.escoins.database;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.api.LeaderboardEntry;
import fun.endcore.escoins.cosmetics.CosmeticEntry;
import fun.endcore.escoins.cosmetics.CosmeticType;
import fun.endcore.escoins.cosmetics.OwnershipType;
import fun.endcore.escoins.economy.TransactionRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Base database manager defining data access methods and common operations.
 */
public abstract class DatabaseManager {
    protected final ESCoins plugin;

    public DatabaseManager(ESCoins plugin) {
        this.plugin = plugin;
    }

    public abstract void initialize() throws SQLException;

    public abstract void close();

    public abstract Connection getConnection() throws SQLException;

    protected abstract String getUpsertPlayerSql();

    protected abstract String getCreatePlayersTableSql();

    protected abstract String getCreateTransactionsTableSql();

    protected abstract String getCreateLocationsTableSql();

    protected abstract String getUpsertLocationSql();

    protected abstract String getCreateCosmeticsTableSql();

    protected abstract String getUpsertCosmeticSql();

    /**
     * Initializes tables and indices.
     */
    protected void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Create players table
            stmt.executeUpdate(getCreatePlayersTableSql());

            // Create transactions table
            stmt.executeUpdate(getCreateTransactionsTableSql());

            // Create locations table
            stmt.executeUpdate(getCreateLocationsTableSql());

            // Create cosmetics table
            stmt.executeUpdate(getCreateCosmeticsTableSql());
            try {
                stmt.executeUpdate("ALTER TABLE escore_player_cosmetics ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT 1;");
            } catch (SQLException ignored) {
                // Column already exists
            }

            // Create indices
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_escoins_players_balance ON escoins_players(balance DESC);");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_escoins_players_name ON escoins_players(username);");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_escoins_tx_player ON escoins_transactions(player_uuid);");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_escore_cosmetics_uuid ON escore_player_cosmetics(uuid);");
        }
    }

    /**
     * Loads a player from the database by UUID.
     */
    public Optional<PlayerData> loadPlayer(UUID uuid) {
        String sql = "SELECT username, balance, created_at, updated_at FROM escoins_players WHERE uuid = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String username = rs.getString("username");
                    long balance = rs.getLong("balance");
                    Timestamp created = rs.getTimestamp("created_at");
                    Timestamp updated = rs.getTimestamp("updated_at");
                    return Optional.of(new PlayerData(uuid, username, balance, created, updated));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player " + uuid + " from database", e);
        }
        return Optional.empty();
    }

    /**
     * Loads a player from the database by username.
     */
    public Optional<PlayerData> loadPlayerByName(String username) {
        String sql = "SELECT uuid, username, balance, created_at, updated_at FROM escoins_players WHERE LOWER(username) = LOWER(?);";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String actualName = rs.getString("username");
                    long balance = rs.getLong("balance");
                    Timestamp created = rs.getTimestamp("created_at");
                    Timestamp updated = rs.getTimestamp("updated_at");
                    return Optional.of(new PlayerData(uuid, actualName, balance, created, updated));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player by name " + username + " from database", e);
        }
        return Optional.empty();
    }

    /**
     * Upserts a player record (inserts if not present, updates username and timestamp if already present).
     */
    public boolean createOrUpdatePlayer(UUID uuid, String username, long initialBalance) {
        String sql = getUpsertPlayerSql();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setLong(3, initialBalance);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create/update player " + username + " (" + uuid + ")", e);
            return false;
        }
    }

    /**
     * Sets a player's balance to an exact amount.
     */
    public boolean setBalance(UUID uuid, long newBalance) {
        String sql = "UPDATE escoins_players SET balance = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, newBalance);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set balance for player " + uuid, e);
            return false;
        }
    }

    /**
     * Atomically adds balance to a player.
     */
    public boolean addBalance(UUID uuid, long amount) {
        String sql = "UPDATE escoins_players SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add balance for player " + uuid, e);
            return false;
        }
    }

    /**
     * Atomically removes balance from a player only if they have sufficient coins.
     * Prevents negative balances and double spending at the database engine level.
     */
    public boolean removeBalance(UUID uuid, long amount) {
        String sql = "UPDATE escoins_players SET balance = balance - ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ? AND balance >= ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setString(2, uuid.toString());
            ps.setLong(3, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove balance for player " + uuid, e);
            return false;
        }
    }

    /**
     * Executes an atomic transfer between two players inside an isolated SQL transaction.
     * Guarantees all-or-nothing execution, double-spending prevention, and audit recording.
     */
    public boolean transferBalance(UUID sender, String senderName, UUID receiver, String receiverName, long amount) {
        String deductSql = "UPDATE escoins_players SET balance = balance - ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ? AND balance >= ?;";
        String creditSql = "UPDATE escoins_players SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?;";
        String ensureReceiverSql = getUpsertPlayerSql();
        String recordTxSql = "INSERT INTO escoins_transactions (player_uuid, type, amount, balance_before, balance_after, source, target_uuid, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP);";
        String getBalanceSql = "SELECT balance FROM escoins_players WHERE uuid = ?;";

        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // Ensure receiver exists
                try (PreparedStatement psEnsure = conn.prepareStatement(ensureReceiverSql)) {
                    psEnsure.setString(1, receiver.toString());
                    psEnsure.setString(2, receiverName != null ? receiverName : "Unknown");
                    psEnsure.setLong(3, 0L);
                    psEnsure.executeUpdate();
                }

                // Query sender balance before
                long senderBefore = 0;
                try (PreparedStatement psBefore = conn.prepareStatement(getBalanceSql)) {
                    psBefore.setString(1, sender.toString());
                    try (ResultSet rs = psBefore.executeQuery()) {
                        if (rs.next()) {
                            senderBefore = rs.getLong("balance");
                        } else {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                // Deduct from sender atomically
                try (PreparedStatement psDeduct = conn.prepareStatement(deductSql)) {
                    psDeduct.setLong(1, amount);
                    psDeduct.setString(2, sender.toString());
                    psDeduct.setLong(3, amount);
                    if (psDeduct.executeUpdate() == 0) {
                        // Sender had insufficient balance
                        conn.rollback();
                        return false;
                    }
                }

                // Query receiver balance before
                long receiverBefore = 0;
                try (PreparedStatement psBefore = conn.prepareStatement(getBalanceSql)) {
                    psBefore.setString(1, receiver.toString());
                    try (ResultSet rs = psBefore.executeQuery()) {
                        if (rs.next()) {
                            receiverBefore = rs.getLong("balance");
                        }
                    }
                }

                // Credit to receiver
                try (PreparedStatement psCredit = conn.prepareStatement(creditSql)) {
                    psCredit.setLong(1, amount);
                    psCredit.setString(2, receiver.toString());
                    psCredit.executeUpdate();
                }

                // Insert sender transaction record (TRANSFER_SENT)
                try (PreparedStatement psTx = conn.prepareStatement(recordTxSql)) {
                    psTx.setString(1, sender.toString());
                    psTx.setString(2, "TRANSFER_SENT");
                    psTx.setLong(3, amount);
                    psTx.setLong(4, senderBefore);
                    psTx.setLong(5, senderBefore - amount);
                    psTx.setString(6, "Transfer to " + (receiverName != null ? receiverName : receiver));
                    psTx.setString(7, receiver.toString());
                    psTx.executeUpdate();
                }

                // Insert receiver transaction record (TRANSFER_RECEIVED)
                try (PreparedStatement psTx = conn.prepareStatement(recordTxSql)) {
                    psTx.setString(1, receiver.toString());
                    psTx.setString(2, "TRANSFER_RECEIVED");
                    psTx.setLong(3, amount);
                    psTx.setLong(4, receiverBefore);
                    psTx.setLong(5, receiverBefore + amount);
                    psTx.setString(6, "Transfer from " + (senderName != null ? senderName : sender));
                    psTx.setString(7, sender.toString());
                    psTx.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().log(Level.SEVERE, "Transaction error during coin transfer from " + sender + " to " + receiver, e);
                return false;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Connection error during transfer from " + sender + " to " + receiver, e);
            return false;
        }
    }

    /**
     * Records a single financial transaction.
     */
    public void recordTransaction(TransactionRecord tx) {
        String sql = "INSERT INTO escoins_transactions (player_uuid, type, amount, balance_before, balance_after, source, target_uuid, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tx.playerUuid().toString());
            ps.setString(2, tx.type().name());
            ps.setLong(3, tx.amount());
            ps.setLong(4, tx.balanceBefore());
            ps.setLong(5, tx.balanceAfter());
            ps.setString(6, tx.source() != null ? tx.source() : "System");
            ps.setString(7, tx.targetUuid() != null ? tx.targetUuid().toString() : null);
            ps.setTimestamp(8, tx.timestamp() != null ? tx.timestamp() : new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to record coin transaction for " + tx.playerUuid(), e);
        }
    }

    /**
     * Retrieves the top players ordered by balance descending using indexed query.
     */
    public List<LeaderboardEntry> getTopPlayers(int limit) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        String sql = "SELECT uuid, username, balance FROM escoins_players ORDER BY balance DESC LIMIT ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String username = rs.getString("username");
                    long balance = rs.getLong("balance");
                    entries.add(new LeaderboardEntry(rank++, uuid, username, balance));
                }
            }
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query top players from database", e);
            }
        }
        return entries;
    }

    /**
     * Saves a player's last safe logout location.
     */
    public void savePlayerLocation(UUID uuid, String world, double x, double y, double z, float yaw, float pitch) {
        String sql = getUpsertLocationSql();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, world);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.setFloat(6, yaw);
            ps.setFloat(7, pitch);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save location for player " + uuid, e);
            }
        }
    }

    /**
     * Loads a player's last saved location.
     */
    public Optional<fun.endcore.escoins.spawn.PlayerLocationData> loadPlayerLocation(UUID uuid) {
        String sql = "SELECT world, x, y, z, yaw, pitch, has_joined_before FROM escore_player_locations WHERE uuid = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String world = rs.getString("world");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");
                    boolean hasJoined = rs.getInt("has_joined_before") == 1;
                    return Optional.of(new fun.endcore.escoins.spawn.PlayerLocationData(uuid, world, x, y, z, yaw, pitch, hasJoined));
                }
            }
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player location for " + uuid, e);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if a player has ever completed their first join.
     */
    public boolean hasJoinedBefore(UUID uuid) {
        String sql = "SELECT 1 FROM escore_player_locations WHERE uuid = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check first join for " + uuid, e);
            }
            return false;
        }
    }

    /**
     * Marks that a player has completed their first join with their initial position.
     */
    public void markFirstJoin(UUID uuid, String world, double x, double y, double z, float yaw, float pitch) {
        savePlayerLocation(uuid, world, x, y, z, yaw, pitch);
    }

    /**
     * Gets the total count of unique players who have ever joined the server.
     */
    public int getUniquePlayerCount() {
        String sql = "SELECT COUNT(*) FROM escore_player_locations;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query unique player count", e);
            }
        }
        return 1;
    }

    /**
     * Checks if a player record exists in the database.
     */
    public boolean hasPlayer(UUID uuid) {
        String sql = "SELECT 1 FROM escoins_players WHERE uuid = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check if player exists: " + uuid, e);
            }
            return false;
        }
    }

    /**
     * Gets the 1-indexed global rank of a player across the server based on coin balance.
     * Evaluated using the balance index in logarithmic time.
     */
    public int getPlayerGlobalRank(long balance) {
        String sql = "SELECT COUNT(*) + 1 FROM escoins_players WHERE balance > ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, balance);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query global player rank", e);
            }
        }
        return -1;
    }

    /**
     * Loads all cosmetic entitlements for a player.
     */
    public List<CosmeticEntry> loadCosmetics(UUID uuid) {
        List<CosmeticEntry> list = new ArrayList<>();
        String sql = "SELECT cosmetic_type, color, ownership_type, expires_at, is_active, created_at FROM escore_player_cosmetics WHERE uuid = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String typeStr = rs.getString("cosmetic_type");
                    CosmeticType type = CosmeticType.fromString(typeStr);
                    if (type == null) continue;

                    String color = rs.getString("color");
                    String ownStr = rs.getString("ownership_type");
                    OwnershipType ownType = OwnershipType.fromString(ownStr);
                    if (ownType == null) ownType = OwnershipType.PERMANENT;

                    long expiresAtLong = rs.getLong("expires_at");
                    Long expiresAt = rs.wasNull() ? null : expiresAtLong;

                    boolean active = true;
                    try {
                        active = rs.getBoolean("is_active");
                        if (rs.wasNull()) {
                            active = true;
                        }
                    } catch (SQLException ignored) {
                        active = true;
                    }

                    Timestamp createdTs = rs.getTimestamp("created_at");
                    long createdAt = createdTs != null ? createdTs.getTime() : System.currentTimeMillis();

                    list.add(new CosmeticEntry(uuid, type, color, ownType, expiresAt, active, createdAt));
                }
            }
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load cosmetics for " + uuid, e);
            }
        }
        return list;
    }

    /**
     * Saves or updates a cosmetic entitlement for a player.
     */
    public boolean saveCosmetic(CosmeticEntry entry) {
        String sql = getUpsertCosmeticSql();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.playerUuid().toString());
            ps.setString(2, entry.type().getId());
            ps.setString(3, entry.color());
            ps.setString(4, entry.ownershipType().name());
            if (entry.expiresAt() != null) {
                ps.setLong(5, entry.expiresAt());
            } else {
                ps.setNull(5, Types.BIGINT);
            }
            ps.setBoolean(6, entry.active());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save cosmetic for " + entry.playerUuid(), e);
            }
            return false;
        }
    }

    /**
     * Updates the active toggle state of a cosmetic entitlement.
     */
    public boolean updateCosmeticActive(UUID uuid, CosmeticType type, boolean active) {
        String sql = "UPDATE escore_player_cosmetics SET is_active = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ? AND cosmetic_type = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setString(2, uuid.toString());
            ps.setString(3, type.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update cosmetic active state for " + uuid, e);
            }
            return false;
        }
    }

    /**
     * Deletes a cosmetic entitlement for a player.
     */
    public boolean deleteCosmetic(UUID uuid, CosmeticType type) {
        String sql = "DELETE FROM escore_player_cosmetics WHERE uuid = ? AND cosmetic_type = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete cosmetic " + type + " for " + uuid, e);
            }
            return false;
        }
    }

    /**
     * Deletes all expired temporary cosmetics.
     */
    public int deleteExpiredCosmetics(long currentMillis) {
        String sql = "DELETE FROM escore_player_cosmetics WHERE ownership_type = 'TEMPORARY' AND expires_at <= ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, currentMillis);
            return ps.executeUpdate();
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete expired cosmetics", e);
            }
            return 0;
        }
    }

}
