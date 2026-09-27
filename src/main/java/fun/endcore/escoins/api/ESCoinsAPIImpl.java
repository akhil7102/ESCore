package fun.endcore.escoins.api;

import fun.endcore.escoins.economy.CoinManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Concrete implementation of the ESCoins public API.
 */
public class ESCoinsAPIImpl implements ESCoinsAPI {
    private final CoinManager coinManager;

    public ESCoinsAPIImpl(CoinManager coinManager) {
        this.coinManager = coinManager;
    }

    @Override
    public long getBalance(UUID uuid) {
        return coinManager.getBalance(uuid);
    }

    @Override
    public CompletableFuture<Long> getBalanceAsync(UUID uuid) {
        return coinManager.getBalanceAsync(uuid);
    }

    @Override
    public boolean hasBalance(UUID uuid, long amount) {
        return coinManager.hasBalance(uuid, amount);
    }

    @Override
    public boolean giveCoins(UUID uuid, long amount) {
        return coinManager.giveCoins(uuid, amount, "API");
    }

    @Override
    public boolean giveCoins(UUID uuid, long amount, String source) {
        return coinManager.giveCoins(uuid, amount, source);
    }

    @Override
    public CompletableFuture<Boolean> giveCoinsAsync(UUID uuid, long amount) {
        return coinManager.giveCoinsAsync(uuid, amount, "API");
    }

    @Override
    public CompletableFuture<Boolean> giveCoinsAsync(UUID uuid, long amount, String source) {
        return coinManager.giveCoinsAsync(uuid, amount, source);
    }

    @Override
    public boolean takeCoins(UUID uuid, long amount) {
        return coinManager.takeCoins(uuid, amount, "API");
    }

    @Override
    public boolean takeCoins(UUID uuid, long amount, String source) {
        return coinManager.takeCoins(uuid, amount, source);
    }

    @Override
    public CompletableFuture<Boolean> takeCoinsAsync(UUID uuid, long amount) {
        return coinManager.takeCoinsAsync(uuid, amount, "API");
    }

    @Override
    public CompletableFuture<Boolean> takeCoinsAsync(UUID uuid, long amount, String source) {
        return coinManager.takeCoinsAsync(uuid, amount, source);
    }

    @Override
    public boolean setBalance(UUID uuid, long amount) {
        return coinManager.setBalance(uuid, amount, "API");
    }

    @Override
    public boolean setBalance(UUID uuid, long amount, String source) {
        return coinManager.setBalance(uuid, amount, source);
    }

    @Override
    public CompletableFuture<Boolean> setBalanceAsync(UUID uuid, long amount) {
        return coinManager.setBalanceAsync(uuid, amount, "API");
    }

    @Override
    public CompletableFuture<Boolean> setBalanceAsync(UUID uuid, long amount, String source) {
        return coinManager.setBalanceAsync(uuid, amount, source);
    }

    @Override
    public boolean transferCoins(UUID sender, UUID receiver, long amount) {
        return coinManager.transferCoins(sender, receiver, amount);
    }

    @Override
    public CompletableFuture<Boolean> transferCoinsAsync(UUID sender, UUID receiver, long amount) {
        return coinManager.transferCoinsAsync(sender, receiver, amount);
    }

    @Override
    public List<LeaderboardEntry> getTopLeaderboard(int limit) {
        return coinManager.getTopLeaderboard(limit);
    }

    @Override
    public int getLeaderboardPosition(UUID uuid) {
        return coinManager.getLeaderboardPosition(uuid);
    }

    @Override
    public int getGlobalRank(UUID uuid) {
        return coinManager.getGlobalRank(uuid);
    }

    @Override
    public CompletableFuture<Boolean> givePermanentCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type, String color) {
        fun.endcore.escoins.cosmetics.CosmeticManager cm = fun.endcore.escoins.ESCoins.getInstance().getCosmeticManager();
        if (cm == null) return CompletableFuture.completedFuture(false);
        return cm.givePermanentCosmetic(uuid, type, color);
    }

    @Override
    public CompletableFuture<Boolean> giveTemporaryCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type, String color, long durationMillis) {
        fun.endcore.escoins.cosmetics.CosmeticManager cm = fun.endcore.escoins.ESCoins.getInstance().getCosmeticManager();
        if (cm == null) return CompletableFuture.completedFuture(false);
        return cm.giveTemporaryCosmetic(uuid, type, color, durationMillis);
    }

    @Override
    public CompletableFuture<Boolean> removeCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type) {
        fun.endcore.escoins.cosmetics.CosmeticManager cm = fun.endcore.escoins.ESCoins.getInstance().getCosmeticManager();
        if (cm == null) return CompletableFuture.completedFuture(false);
        return cm.removeCosmetic(uuid, type);
    }

    @Override
    public java.util.Optional<fun.endcore.escoins.cosmetics.CosmeticEntry> getActiveCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type) {
        fun.endcore.escoins.cosmetics.CosmeticManager cm = fun.endcore.escoins.ESCoins.getInstance().getCosmeticManager();
        if (cm == null) return java.util.Optional.empty();
        return cm.getActiveCosmetic(uuid, type);
    }

    @Override
    public java.util.Optional<fun.endcore.escoins.cosmetics.CosmeticEntry> getCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type) {
        fun.endcore.escoins.cosmetics.CosmeticManager cm = fun.endcore.escoins.ESCoins.getInstance().getCosmeticManager();
        if (cm == null) return java.util.Optional.empty();
        return cm.getCosmetic(uuid, type);
    }

    @Override
    public boolean hasCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type) {
        fun.endcore.escoins.cosmetics.CosmeticManager cm = fun.endcore.escoins.ESCoins.getInstance().getCosmeticManager();
        return cm != null && cm.hasCosmetic(uuid, type);
    }

    @Override
    public boolean isCosmeticActive(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type) {
        fun.endcore.escoins.cosmetics.CosmeticManager cm = fun.endcore.escoins.ESCoins.getInstance().getCosmeticManager();
        return cm != null && cm.isCosmeticActive(uuid, type);
    }

    @Override
    public CompletableFuture<Boolean> setCosmeticActive(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type, boolean active) {
        fun.endcore.escoins.cosmetics.CosmeticManager cm = fun.endcore.escoins.ESCoins.getInstance().getCosmeticManager();
        if (cm == null) return CompletableFuture.completedFuture(false);
        return cm.setCosmeticActive(uuid, type, active);
    }

    @Override
    public String getActiveTag(UUID uuid) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        return tm != null ? tm.getActiveTag(uuid) : null;
    }

    @Override
    public String getActiveTagDisplay(UUID uuid) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        return tm != null ? tm.getActiveTagDisplay(uuid) : "";
    }

    @Override
    public fun.endcore.escoins.tags.TagDefinition getTag(String tagId) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        return tm != null ? tm.getTag(tagId) : null;
    }

    @Override
    public java.util.Collection<fun.endcore.escoins.tags.TagDefinition> getAllTags() {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        return tm != null ? tm.getAllTags() : java.util.Collections.emptyList();
    }
}
