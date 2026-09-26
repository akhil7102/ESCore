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
    public boolean hasCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type) {
        fun.endcore.escoins.cosmetics.CosmeticManager cm = fun.endcore.escoins.ESCoins.getInstance().getCosmeticManager();
        return cm != null && cm.hasCosmetic(uuid, type);
    }

    // ========================================================
    // Player Tags System API
    // ========================================================

    @Override
    public CompletableFuture<Boolean> giveTag(UUID uuid, String tagId) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        if (tm == null) return CompletableFuture.completedFuture(false);
        return tm.giveTag(uuid, tagId);
    }

    @Override
    public CompletableFuture<Boolean> removeTag(UUID uuid, String tagId) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        if (tm == null) return CompletableFuture.completedFuture(false);
        return tm.removeTag(uuid, tagId);
    }

    @Override
    public CompletableFuture<Boolean> clearTags(UUID uuid) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        if (tm == null) return CompletableFuture.completedFuture(false);
        return tm.clearTags(uuid);
    }

    @Override
    public CompletableFuture<Boolean> setActiveTag(UUID uuid, String tagId) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        if (tm == null) return CompletableFuture.completedFuture(false);
        return tm.setActiveTag(uuid, tagId);
    }

    @Override
    public java.util.Set<String> getOwnedTags(UUID uuid) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        if (tm == null) return java.util.Collections.emptySet();
        return tm.getPlayerTags(uuid).getOwnedTags();
    }

    @Override
    public String getActiveTag(UUID uuid) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        if (tm == null) return null;
        return tm.getActiveTag(uuid);
    }

    @Override
    public String getActiveTagDisplay(UUID uuid) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        if (tm == null) return "";
        return tm.getActiveTagDisplay(uuid);
    }

    @Override
    public boolean hasTag(UUID uuid, String tagId) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        return tm != null && tm.hasTag(uuid, tagId);
    }

    @Override
    public boolean isValidTag(String tagId) {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        return tm != null && tm.isValidTag(tagId);
    }

    @Override
    public java.util.Map<String, fun.endcore.escoins.tags.TagDefinition> getRegisteredTags() {
        fun.endcore.escoins.tags.TagManager tm = fun.endcore.escoins.ESCoins.getInstance().getTagManager();
        if (tm == null) return java.util.Collections.emptyMap();
        return tm.getRegisteredTags();
    }
}
