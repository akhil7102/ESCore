package fun.endcore.escoins.economy;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.api.LeaderboardEntry;
import fun.endcore.escoins.api.event.CoinBalanceChangeEvent;
import fun.endcore.escoins.api.event.CoinGiveEvent;
import fun.endcore.escoins.api.event.CoinTakeEvent;
import fun.endcore.escoins.api.event.CoinTransferEvent;
import fun.endcore.escoins.database.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player coin balances, memory caching, thread-safe transactions,
 * audit history, and event dispatches.
 */
public class CoinManager {
    private final ESCoins plugin;
    private final Map<UUID, Long> balanceCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> usernameCache = new ConcurrentHashMap<>();

    private record CachedRank(int rank, long timestamp) {}
    private final Map<UUID, CachedRank> rankCache = new ConcurrentHashMap<>();

    public CoinManager(ESCoins plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a player joins the server. Asynchronously loads or creates their database record.
     */
    public CompletableFuture<Void> onPlayerJoin(UUID uuid, String username) {
        usernameCache.put(uuid, username);
        return CompletableFuture.runAsync(() -> {
            try {
                Optional<PlayerData> dataOpt = plugin.getDatabaseManager().loadPlayer(uuid);
                long balance = 0;
                if (dataOpt.isPresent()) {
                    PlayerData data = dataOpt.get();
                    balance = data.balance();
                    // Update username in DB if player changed names
                    if (!username.equalsIgnoreCase(data.username())) {
                        plugin.getDatabaseManager().createOrUpdatePlayer(uuid, username, balance);
                    }
                } else {
                    // New player record
                    plugin.getDatabaseManager().createOrUpdatePlayer(uuid, username, 0L);
                }

                balanceCache.put(uuid, balance);
                plugin.getLeaderboard().updatePlayer(uuid, username, balance);

                // Pre-warm rank cache asynchronously
                int rank = plugin.getDatabaseManager().getPlayerGlobalRank(balance);
                if (rank > 0) {
                    rankCache.put(uuid, new CachedRank(rank, System.currentTimeMillis()));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player data on join for " + username + " (" + uuid + ")", e);
            }
        });
    }

    /**
     * Called when a player disconnects from the server.
     */
    public void onPlayerQuit(UUID uuid) {
        rankCache.remove(uuid);
    }

    /**
     * Retrieves the username of a player (from cache, online player, or database).
     */
    public String getUsername(UUID uuid) {
        String cached = usernameCache.get(uuid);
        if (cached != null) return cached;

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            usernameCache.put(uuid, player.getName());
            return player.getName();
        }

        Optional<PlayerData> data = plugin.getDatabaseManager().loadPlayer(uuid);
        if (data.isPresent()) {
            usernameCache.put(uuid, data.get().username());
            return data.get().username();
        }

        return "Unknown";
    }

    /**
     * Resolves UUID and username for a player name (online or offline).
     */
    public CompletableFuture<Optional<PlayerData>> lookupPlayer(String username) {
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) {
            long bal = getBalance(online.getUniqueId());
            return CompletableFuture.completedFuture(Optional.of(new PlayerData(online.getUniqueId(), online.getName(), bal, null, null)));
        }

        return CompletableFuture.supplyAsync(() -> plugin.getDatabaseManager().loadPlayerByName(username));
    }

    /**
     * Gets the current balance of a player.
     * Instant O(1) in-memory return for online/cached players.
     */
    public long getBalance(UUID uuid) {
        Long cached = balanceCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        Optional<PlayerData> data = plugin.getDatabaseManager().loadPlayer(uuid);
        if (data.isPresent()) {
            long balance = data.get().balance();
            balanceCache.put(uuid, balance);
            usernameCache.put(uuid, data.get().username());
            return balance;
        }
        return 0L;
    }

    /**
     * Gets the current balance asynchronously.
     */
    public CompletableFuture<Long> getBalanceAsync(UUID uuid) {
        Long cached = balanceCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> getBalance(uuid));
    }

    /**
     * Checks if a player has at least amount coins.
     */
    public boolean hasBalance(UUID uuid, long amount) {
        return getBalance(uuid) >= amount;
    }

    /**
     * Adds coins to a player's balance.
     */
    public boolean giveCoins(UUID uuid, long amount, String source) {
        if (amount <= 0) return false;

        String username = getUsername(uuid);
        boolean async = !Bukkit.isPrimaryThread();

        CoinGiveEvent event = new CoinGiveEvent(uuid, username, amount, source, async);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        long actualAmount = event.getAmount();
        long before = getBalance(uuid);

        // Ensure record exists and update DB atomically
        plugin.getDatabaseManager().createOrUpdatePlayer(uuid, username, 0L);
        boolean success = plugin.getDatabaseManager().addBalance(uuid, actualAmount);

        if (success) {
            long after = before + actualAmount;
            balanceCache.put(uuid, after);
            rankCache.remove(uuid);
            plugin.getLeaderboard().updatePlayer(uuid, username, after);

            // Record transaction audit
            plugin.getDatabaseManager().recordTransaction(
                    new TransactionRecord(uuid, TransactionType.GIVE, actualAmount, before, after, source, null)
            );

            // Call balance change event
            Bukkit.getPluginManager().callEvent(
                    new CoinBalanceChangeEvent(uuid, username, before, after, TransactionType.GIVE, async)
            );
            return true;
        }

        return false;
    }

    /**
     * Deducts coins from a player's balance atomically.
     * Prevents double spending and negative balances at database level.
     */
    public boolean takeCoins(UUID uuid, long amount, String source) {
        if (amount <= 0) return false;

        String username = getUsername(uuid);
        boolean async = !Bukkit.isPrimaryThread();

        CoinTakeEvent event = new CoinTakeEvent(uuid, username, amount, source, async);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        long actualAmount = event.getAmount();
        long before = getBalance(uuid);

        if (before < actualAmount) {
            return false;
        }

        boolean success = plugin.getDatabaseManager().removeBalance(uuid, actualAmount);
        if (success) {
            long after = Math.max(0, before - actualAmount);
            balanceCache.put(uuid, after);
            rankCache.remove(uuid);
            plugin.getLeaderboard().updatePlayer(uuid, username, after);

            // Record transaction audit
            plugin.getDatabaseManager().recordTransaction(
                    new TransactionRecord(uuid, TransactionType.TAKE, actualAmount, before, after, source, null)
            );

            // Call balance change event
            Bukkit.getPluginManager().callEvent(
                    new CoinBalanceChangeEvent(uuid, username, before, after, TransactionType.TAKE, async)
            );
            return true;
        }

        return false;
    }

    /**
     * Sets a player's balance to an exact amount.
     */
    public boolean setBalance(UUID uuid, long amount, String source) {
        if (amount < 0) return false;

        String username = getUsername(uuid);
        long before = getBalance(uuid);
        boolean async = !Bukkit.isPrimaryThread();

        plugin.getDatabaseManager().createOrUpdatePlayer(uuid, username, amount);
        boolean success = plugin.getDatabaseManager().setBalance(uuid, amount);

        if (success) {
            balanceCache.put(uuid, amount);
            rankCache.remove(uuid);
            plugin.getLeaderboard().updatePlayer(uuid, username, amount);

            // Record transaction audit
            plugin.getDatabaseManager().recordTransaction(
                    new TransactionRecord(uuid, TransactionType.SET, amount, before, amount, source, null)
            );

            // Call balance change event
            Bukkit.getPluginManager().callEvent(
                    new CoinBalanceChangeEvent(uuid, username, before, amount, TransactionType.SET, async)
            );
            return true;
        }

        return false;
    }

    /**
     * Executes an atomic transfer between two players.
     */
    public boolean transferCoins(UUID sender, UUID receiver, long amount) {
        if (amount <= 0 || sender.equals(receiver)) {
            return false;
        }

        String senderName = getUsername(sender);
        String receiverName = getUsername(receiver);
        boolean async = !Bukkit.isPrimaryThread();

        CoinTransferEvent event = new CoinTransferEvent(sender, senderName, receiver, receiverName, amount, async);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        long actualAmount = event.getAmount();
        long senderBefore = getBalance(sender);
        if (senderBefore < actualAmount) {
            return false;
        }

        boolean success = plugin.getDatabaseManager().transferBalance(sender, senderName, receiver, receiverName, actualAmount);
        if (success) {
            long senderAfter = senderBefore - actualAmount;
            long receiverBefore = getBalance(receiver);
            long receiverAfter = receiverBefore + actualAmount;

            balanceCache.put(sender, senderAfter);
            balanceCache.put(receiver, receiverAfter);
            rankCache.remove(sender);
            rankCache.remove(receiver);

            plugin.getLeaderboard().updatePlayer(sender, senderName, senderAfter);
            plugin.getLeaderboard().updatePlayer(receiver, receiverName, receiverAfter);

            // Call change events
            Bukkit.getPluginManager().callEvent(
                    new CoinBalanceChangeEvent(sender, senderName, senderBefore, senderAfter, TransactionType.TRANSFER_SENT, async)
            );
            Bukkit.getPluginManager().callEvent(
                    new CoinBalanceChangeEvent(receiver, receiverName, receiverBefore, receiverAfter, TransactionType.TRANSFER_RECEIVED, async)
            );
            return true;
        }

        return false;
    }

    // Asynchronous API methods
    public CompletableFuture<Boolean> giveCoinsAsync(UUID uuid, long amount, String source) {
        return CompletableFuture.supplyAsync(() -> giveCoins(uuid, amount, source));
    }

    public CompletableFuture<Boolean> takeCoinsAsync(UUID uuid, long amount, String source) {
        return CompletableFuture.supplyAsync(() -> takeCoins(uuid, amount, source));
    }

    public CompletableFuture<Boolean> setBalanceAsync(UUID uuid, long amount, String source) {
        return CompletableFuture.supplyAsync(() -> setBalance(uuid, amount, source));
    }

    public CompletableFuture<Boolean> transferCoinsAsync(UUID sender, UUID receiver, long amount) {
        return CompletableFuture.supplyAsync(() -> transferCoins(sender, receiver, amount));
    }

    public List<LeaderboardEntry> getTopLeaderboard(int limit) {
        return plugin.getLeaderboard().getTop(limit);
    }

    public int getLeaderboardPosition(UUID uuid) {
        return plugin.getLeaderboard().getPlayerRank(uuid);
    }

    /**
     * Gets the server-wide global rank position of a player (1-indexed), or -1 if unranked.
     * Uses in-memory top cache, 15s TTL rank cache, and fallbacks to indexed database count.
     */
    public int getGlobalRank(UUID uuid) {
        if (uuid == null) return -1;

        // 1. Check in-memory top leaderboard first (instant O(1))
        int topRank = plugin.getLeaderboard().getPlayerRank(uuid);
        if (topRank > 0) {
            return topRank;
        }

        // 2. Check rank cache (15s TTL)
        CachedRank cached = rankCache.get(uuid);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.timestamp()) < 15_000L) {
            return cached.rank();
        }

        // 3. Check if player has record
        if (!balanceCache.containsKey(uuid) && !plugin.getDatabaseManager().hasPlayer(uuid)) {
            return -1;
        }

        // 4. Query indexed database count
        long balance = getBalance(uuid);
        int rank = plugin.getDatabaseManager().getPlayerGlobalRank(balance);
        if (rank > 0) {
            rankCache.put(uuid, new CachedRank(rank, now));
        }
        return rank;
    }
}
