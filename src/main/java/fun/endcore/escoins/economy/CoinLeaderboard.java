package fun.endcore.escoins.economy;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.api.LeaderboardEntry;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * High-performance in-memory cache for coin leaderboard.
 * Satisfies O(1) reads for PlaceholderAPI without any database queries.
 */
public class CoinLeaderboard {
    private final ESCoins plugin;
    private final List<LeaderboardEntry> cachedTop = new CopyOnWriteArrayList<>();
    private final Map<UUID, Integer> rankLookup = new ConcurrentHashMap<>();
    private BukkitTask periodicTask;

    private boolean enabled = true;
    private int size = 10;
    private int refreshInterval = 30; // seconds

    public CoinLeaderboard(ESCoins plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        this.enabled = plugin.getConfigManager().getConfig().getBoolean("leaderboard.enabled", true);
        this.size = Math.max(1, plugin.getConfigManager().getConfig().getInt("leaderboard.size", 10));
        this.refreshInterval = Math.max(5, plugin.getConfigManager().getConfig().getInt("leaderboard.refresh-interval", 30));

        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }

        if (enabled) {
            startPeriodicRefresh();
        }
    }

    private void startPeriodicRefresh() {
        // Initial async refresh
        refreshAsync();

        // Periodic async task (refreshInterval * 20 ticks)
        long intervalTicks = refreshInterval * 20L;
        periodicTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshFromDatabase, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
    }

    /**
     * Triggers an asynchronous refresh of the top leaderboard from the database.
     */
    public void refreshAsync() {
        if (!enabled) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshFromDatabase);
    }

    private void refreshFromDatabase() {
        if (plugin.getDatabaseManager() == null) return;
        List<LeaderboardEntry> freshTop = plugin.getDatabaseManager().getTopPlayers(size);
        applyNewTop(freshTop);
    }

    private synchronized void applyNewTop(List<LeaderboardEntry> newEntries) {
        cachedTop.clear();
        rankLookup.clear();
        for (int i = 0; i < newEntries.size(); i++) {
            LeaderboardEntry entry = newEntries.get(i).withRank(i + 1);
            cachedTop.add(entry);
            rankLookup.put(entry.uuid(), entry.rank());
        }
    }

    /**
     * Efficiently updates the in-memory leaderboard when a player's balance changes.
     * Prevents unnecessary full database queries.
     */
    public synchronized void updatePlayer(UUID uuid, String username, long newBalance) {
        if (!enabled || username == null) return;

        Integer currentRank = rankLookup.get(uuid);
        List<LeaderboardEntry> list = new ArrayList<>(cachedTop);

        if (currentRank != null) {
            // Player is already in top leaderboard - update balance
            list.removeIf(e -> e.uuid().equals(uuid));
            list.add(new LeaderboardEntry(0, uuid, username, newBalance));
        } else {
            // Player is not currently in top leaderboard
            long minBalanceInTop = list.isEmpty() ? 0 : list.getLast().balance();
            if (list.size() < size || newBalance > minBalanceInTop) {
                list.add(new LeaderboardEntry(0, uuid, username, newBalance));
            } else {
                // Not high enough to enter top cache
                return;
            }
        }

        // Sort descending by balance
        list.sort(Comparator.comparingLong(LeaderboardEntry::balance).reversed());

        // Trim to max size
        if (list.size() > size) {
            list = list.subList(0, size);
        }

        applyNewTop(list);
    }

    /**
     * Gets leaderboard entry at rank position (1-indexed).
     */
    public LeaderboardEntry getEntry(int rank) {
        int index = rank - 1;
        if (index >= 0 && index < cachedTop.size()) {
            return cachedTop.get(index);
        }
        return null;
    }

    /**
     * Gets username at rank position (1-indexed). Returns default if unoccupied.
     */
    public String getNameAt(int rank, String def) {
        LeaderboardEntry entry = getEntry(rank);
        return entry != null ? entry.username() : def;
    }

    /**
     * Gets coin amount at rank position (1-indexed). Returns default if unoccupied.
     */
    public long getAmountAt(int rank, long def) {
        LeaderboardEntry entry = getEntry(rank);
        return entry != null ? entry.balance() : def;
    }

    /**
     * Gets rank of a player (1-indexed), or -1 if not in cached leaderboard.
     */
    public int getPlayerRank(UUID uuid) {
        return rankLookup.getOrDefault(uuid, -1);
    }

    /**
     * Returns an unmodifiable copy of the current cached top entries.
     */
    public List<LeaderboardEntry> getTop(int limit) {
        if (limit <= 0) return Collections.emptyList();
        int safeLimit = Math.min(limit, cachedTop.size());
        return Collections.unmodifiableList(cachedTop.subList(0, safeLimit));
    }

    public int getSize() {
        return size;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
