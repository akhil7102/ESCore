package fun.endcore.escoins.database;

import fun.endcore.escoins.api.LeaderboardEntry;
import fun.endcore.escoins.economy.TransactionRecord;
import fun.endcore.escoins.economy.TransactionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {
    private File tempDb;
    private DatabaseManager dbManager;

    @BeforeEach
    void setup() throws Exception {
        tempDb = Files.createTempFile("test_coins_", ".db").toFile();
        String jdbcUrl = "jdbc:sqlite:" + tempDb.getAbsolutePath();

        dbManager = new DatabaseManager(null) {
            private Connection conn;

            @Override
            public void initialize() throws SQLException {
                conn = DriverManager.getConnection(jdbcUrl);
                createTables();
            }

            @Override
            public void close() {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException ignored) {}
            }

            @Override
            public Connection getConnection() throws SQLException {
                if (conn == null || conn.isClosed()) {
                    conn = DriverManager.getConnection(jdbcUrl);
                }
                return conn;
            }

            @Override
            protected String getUpsertPlayerSql() {
                return "INSERT INTO escoins_players (uuid, username, balance, created_at, updated_at) " +
                       "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                       "ON CONFLICT(uuid) DO UPDATE SET username = excluded.username, updated_at = CURRENT_TIMESTAMP;";
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
        };

        dbManager.initialize();
    }

    @AfterEach
    void cleanup() {
        if (dbManager != null) {
            dbManager.close();
        }
        if (tempDb != null && tempDb.exists()) {
            tempDb.delete();
        }
    }

    @Test
    void testCreateAndLoadPlayer() {
        UUID uuid = UUID.randomUUID();
        boolean created = dbManager.createOrUpdatePlayer(uuid, "Steve", 100L);
        assertTrue(created);

        Optional<PlayerData> opt = dbManager.loadPlayer(uuid);
        assertTrue(opt.isPresent());
        assertEquals("Steve", opt.get().username());
        assertEquals(100L, opt.get().balance());

        // Test name lookup
        Optional<PlayerData> optByName = dbManager.loadPlayerByName("steve");
        assertTrue(optByName.isPresent());
        assertEquals(uuid, optByName.get().uuid());
    }

    @Test
    void testAtomicAddAndDeductBalance() {
        UUID uuid = UUID.randomUUID();
        dbManager.createOrUpdatePlayer(uuid, "Alex", 500L);

        // Add 250
        assertTrue(dbManager.addBalance(uuid, 250L));
        assertEquals(750L, dbManager.loadPlayer(uuid).get().balance());

        // Deduct 200
        assertTrue(dbManager.removeBalance(uuid, 200L));
        assertEquals(550L, dbManager.loadPlayer(uuid).get().balance());

        // Attempt to deduct more than balance (600 > 550) -> should fail atomically!
        assertFalse(dbManager.removeBalance(uuid, 600L));
        assertEquals(550L, dbManager.loadPlayer(uuid).get().balance());
    }

    @Test
    void testAtomicTransfer() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        dbManager.createOrUpdatePlayer(sender, "SenderPlayer", 1000L);
        dbManager.createOrUpdatePlayer(receiver, "ReceiverPlayer", 200L);

        // Valid transfer of 400
        boolean transferSuccess = dbManager.transferBalance(sender, "SenderPlayer", receiver, "ReceiverPlayer", 400L);
        assertTrue(transferSuccess);

        assertEquals(600L, dbManager.loadPlayer(sender).get().balance());
        assertEquals(600L, dbManager.loadPlayer(receiver).get().balance());

        // Insufficient funds transfer (700 > 600) -> should rollback and return false
        boolean insufficient = dbManager.transferBalance(sender, "SenderPlayer", receiver, "ReceiverPlayer", 700L);
        assertFalse(insufficient);

        // Balances must remain unchanged
        assertEquals(600L, dbManager.loadPlayer(sender).get().balance());
        assertEquals(600L, dbManager.loadPlayer(receiver).get().balance());
    }

    @Test
    void testTransactionHistory() {
        UUID uuid = UUID.randomUUID();
        TransactionRecord record = new TransactionRecord(
                uuid,
                TransactionType.GIVE,
                5000L,
                0L,
                5000L,
                "Console",
                null
        );
        assertDoesNotThrow(() -> dbManager.recordTransaction(record));
    }

    @Test
    void testLeaderboardQuery() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        dbManager.createOrUpdatePlayer(p1, "LowPlayer", 100L);
        dbManager.createOrUpdatePlayer(p2, "TopPlayer", 9999L);
        dbManager.createOrUpdatePlayer(p3, "MidPlayer", 500L);

        List<LeaderboardEntry> top = dbManager.getTopPlayers(10);
        assertEquals(3, top.size());

        // 1st should be TopPlayer
        assertEquals("TopPlayer", top.get(0).username());
        assertEquals(9999L, top.get(0).balance());

        // 2nd should be MidPlayer
        assertEquals("MidPlayer", top.get(1).username());
        assertEquals(500L, top.get(1).balance());

        // 3rd should be LowPlayer
        assertEquals("LowPlayer", top.get(2).username());
        assertEquals(100L, top.get(2).balance());
    }

    @Test
    void testPlayerLocationPersistence() {
        UUID uuid = UUID.randomUUID();

        // Initially player has never joined
        assertFalse(dbManager.hasJoinedBefore(uuid));

        // Save location
        dbManager.savePlayerLocation(uuid, "world", 100.5, 64.0, -200.5, 90.0f, 0.0f);

        // Now has joined before
        assertTrue(dbManager.hasJoinedBefore(uuid));

        // Load location
        var optLoc = dbManager.loadPlayerLocation(uuid);
        assertTrue(optLoc.isPresent());
        var loc = optLoc.get();
        assertEquals("world", loc.worldName());
        assertEquals(100.5, loc.x(), 0.001);
        assertEquals(64.0, loc.y(), 0.001);
        assertEquals(-200.5, loc.z(), 0.001);
        assertEquals(90.0f, loc.yaw(), 0.001);
        assertEquals(0.0f, loc.pitch(), 0.001);
        assertTrue(loc.hasJoinedBefore());
    }

    @Test
    void testPlayerGlobalRankAndExistence() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        UUID pUnknown = UUID.randomUUID();

        dbManager.createOrUpdatePlayer(p1, "Rank1Player", 5000L);
        dbManager.createOrUpdatePlayer(p2, "Rank2Player", 2500L);
        dbManager.createOrUpdatePlayer(p3, "Rank3Player", 100L);

        assertTrue(dbManager.hasPlayer(p1));
        assertTrue(dbManager.hasPlayer(p2));
        assertTrue(dbManager.hasPlayer(p3));
        assertFalse(dbManager.hasPlayer(pUnknown));

        // p1 balance = 5000 -> rank 1
        assertEquals(1, dbManager.getPlayerGlobalRank(5000L));
        // p2 balance = 2500 -> rank 2
        assertEquals(2, dbManager.getPlayerGlobalRank(2500L));
        // p3 balance = 100 -> rank 3
        assertEquals(3, dbManager.getPlayerGlobalRank(100L));
        // A player with 0 balance -> rank 4 (3 players have > 0)
        assertEquals(4, dbManager.getPlayerGlobalRank(0L));
    }

    @Test
    void testCosmeticsPersistenceAndExpiration() {
        UUID uuid = UUID.randomUUID();

        // 1. Save permanent chat color
        fun.endcore.escoins.cosmetics.CosmeticEntry permChat = new fun.endcore.escoins.cosmetics.CosmeticEntry(
                uuid,
                fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR,
                "green",
                fun.endcore.escoins.cosmetics.OwnershipType.PERMANENT,
                null
        );
        assertTrue(dbManager.saveCosmetic(permChat));

        // 2. Save temporary player glow (5 seconds)
        long expiresAt = System.currentTimeMillis() + 5000L;
        fun.endcore.escoins.cosmetics.CosmeticEntry tempGlow = new fun.endcore.escoins.cosmetics.CosmeticEntry(
                uuid,
                fun.endcore.escoins.cosmetics.CosmeticType.PLAYER_GLOW,
                "blue",
                fun.endcore.escoins.cosmetics.OwnershipType.TEMPORARY,
                expiresAt
        );
        assertTrue(dbManager.saveCosmetic(tempGlow));

        // 3. Load cosmetics
        List<fun.endcore.escoins.cosmetics.CosmeticEntry> list = dbManager.loadCosmetics(uuid);
        assertEquals(2, list.size());

        fun.endcore.escoins.cosmetics.CosmeticEntry loadedChat = list.stream()
                .filter(e -> e.type() == fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR)
                .findFirst().orElseThrow();
        assertEquals("green", loadedChat.color());
        assertEquals(fun.endcore.escoins.cosmetics.OwnershipType.PERMANENT, loadedChat.ownershipType());
        assertNull(loadedChat.expiresAt());
        assertFalse(loadedChat.isExpired());

        fun.endcore.escoins.cosmetics.CosmeticEntry loadedGlow = list.stream()
                .filter(e -> e.type() == fun.endcore.escoins.cosmetics.CosmeticType.PLAYER_GLOW)
                .findFirst().orElseThrow();
        assertEquals("blue", loadedGlow.color());
        assertEquals(fun.endcore.escoins.cosmetics.OwnershipType.TEMPORARY, loadedGlow.ownershipType());
        assertNotNull(loadedGlow.expiresAt());
        assertFalse(loadedGlow.isExpired());

        // 4. Update permanent chat color to red
        fun.endcore.escoins.cosmetics.CosmeticEntry updatedChat = new fun.endcore.escoins.cosmetics.CosmeticEntry(
                uuid,
                fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR,
                "red",
                fun.endcore.escoins.cosmetics.OwnershipType.PERMANENT,
                null
        );
        assertTrue(dbManager.saveCosmetic(updatedChat));
        List<fun.endcore.escoins.cosmetics.CosmeticEntry> list2 = dbManager.loadCosmetics(uuid);
        assertEquals(2, list2.size());
        assertEquals("red", list2.stream().filter(e -> e.type() == fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR).findFirst().get().color());

        // 5. Delete chat color
        assertTrue(dbManager.deleteCosmetic(uuid, fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR));
        List<fun.endcore.escoins.cosmetics.CosmeticEntry> list3 = dbManager.loadCosmetics(uuid);
        assertEquals(1, list3.size());
        assertEquals(fun.endcore.escoins.cosmetics.CosmeticType.PLAYER_GLOW, list3.get(0).type());

        // 6. Delete expired cosmetics
        int deleted = dbManager.deleteExpiredCosmetics(System.currentTimeMillis() + 10000L);
        assertTrue(deleted >= 1);
        List<fun.endcore.escoins.cosmetics.CosmeticEntry> list4 = dbManager.loadCosmetics(uuid);
        assertTrue(list4.isEmpty());
    }
}
