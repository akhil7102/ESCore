package fun.endcore.escoins.api;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for ESCoins premium currency system.
 * Allows other plugins and external systems to query and modify player coin balances.
 */
public interface ESCoinsAPI {

    /**
     * Gets the current coin balance of a player.
     * If the player is online or cached, returns instantly without database query.
     *
     * @param uuid the player's unique identifier
     * @return the current balance as a long
     */
    long getBalance(UUID uuid);

    /**
     * Asynchronously retrieves the coin balance of a player.
     *
     * @param uuid the player's unique identifier
     * @return a CompletableFuture yielding the balance
     */
    CompletableFuture<Long> getBalanceAsync(UUID uuid);

    /**
     * Checks if a player has at least the specified coin balance.
     *
     * @param uuid the player's unique identifier
     * @param amount the required coin balance
     * @return true if the player has at least the amount, false otherwise
     */
    boolean hasBalance(UUID uuid, long amount);

    /**
     * Adds coins to a player's balance.
     *
     * @param uuid the player's unique identifier
     * @param amount the amount of coins to add (must be > 0)
     * @return true if successful, false if cancelled or error
     */
    boolean giveCoins(UUID uuid, long amount);

    /**
     * Adds coins to a player's balance with a specific audit source.
     *
     * @param uuid the player's unique identifier
     * @param amount the amount of coins to add
     * @param source the audit source (e.g. "Store", "Console", "PluginName")
     * @return true if successful
     */
    boolean giveCoins(UUID uuid, long amount, String source);

    /**
     * Asynchronously adds coins to a player's balance.
     *
     * @param uuid the player's unique identifier
     * @param amount the amount of coins to add
     * @return a CompletableFuture yielding true if successful
     */
    CompletableFuture<Boolean> giveCoinsAsync(UUID uuid, long amount);

    /**
     * Asynchronously adds coins to a player's balance with an audit source.
     *
     * @param uuid the player's unique identifier
     * @param amount the amount of coins to add
     * @param source the audit source
     * @return a CompletableFuture yielding true if successful
     */
    CompletableFuture<Boolean> giveCoinsAsync(UUID uuid, long amount, String source);

    /**
     * Deducts coins from a player's balance.
     * Never allows balance to become negative.
     *
     * @param uuid the player's unique identifier
     * @param amount the amount of coins to deduct
     * @return true if successfully deducted, false if insufficient balance or cancelled
     */
    boolean takeCoins(UUID uuid, long amount);

    /**
     * Deducts coins from a player's balance with an audit source.
     *
     * @param uuid the player's unique identifier
     * @param amount the amount of coins to deduct
     * @param source the audit source (e.g. "CoinShop", "Console")
     * @return true if successfully deducted, false if insufficient or cancelled
     */
    boolean takeCoins(UUID uuid, long amount, String source);

    /**
     * Asynchronously deducts coins from a player's balance.
     *
     * @param uuid the player's unique identifier
     * @param amount the amount of coins to deduct
     * @return a CompletableFuture yielding true if deducted
     */
    CompletableFuture<Boolean> takeCoinsAsync(UUID uuid, long amount);

    /**
     * Asynchronously deducts coins from a player's balance with an audit source.
     *
     * @param uuid the player's unique identifier
     * @param amount the amount of coins to deduct
     * @param source the audit source
     * @return a CompletableFuture yielding true if deducted
     */
    CompletableFuture<Boolean> takeCoinsAsync(UUID uuid, long amount, String source);

    /**
     * Sets a player's coin balance to an exact amount.
     *
     * @param uuid the player's unique identifier
     * @param amount the new coin balance (must be >= 0)
     * @return true if successful, false otherwise
     */
    boolean setBalance(UUID uuid, long amount);

    /**
     * Sets a player's coin balance with an audit source.
     *
     * @param uuid the player's unique identifier
     * @param amount the new coin balance
     * @param source the audit source
     * @return true if successful
     */
    boolean setBalance(UUID uuid, long amount, String source);

    /**
     * Asynchronously sets a player's coin balance.
     *
     * @param uuid the player's unique identifier
     * @param amount the new coin balance
     * @return a CompletableFuture yielding true if successful
     */
    CompletableFuture<Boolean> setBalanceAsync(UUID uuid, long amount);

    /**
     * Asynchronously sets a player's coin balance with an audit source.
     *
     * @param uuid the player's unique identifier
     * @param amount the new coin balance
     * @param source the audit source
     * @return a CompletableFuture yielding true if successful
     */
    CompletableFuture<Boolean> setBalanceAsync(UUID uuid, long amount, String source);

    /**
     * Transfers coins atomically between two players.
     *
     * @param sender the player sending the coins
     * @param receiver the player receiving the coins
     * @param amount the amount of coins to transfer
     * @return true if transfer was successful, false if sender has insufficient coins or cancelled
     */
    boolean transferCoins(UUID sender, UUID receiver, long amount);

    /**
     * Asynchronously transfers coins atomically between two players.
     *
     * @param sender the player sending the coins
     * @param receiver the player receiving the coins
     * @param amount the amount of coins to transfer
     * @return a CompletableFuture yielding true if successful
     */
    CompletableFuture<Boolean> transferCoinsAsync(UUID sender, UUID receiver, long amount);

    /**
     * Retrieves the top coin balances from the high-performance in-memory cache.
     *
     * @param limit maximum entries to return
     * @return immutable list of LeaderboardEntry
     */
    List<LeaderboardEntry> getTopLeaderboard(int limit);

    /**
     * Gets the leaderboard position (rank) of a player.
     *
     * @param uuid the player's unique identifier
     * @return 1-indexed position, or -1 if not in top leaderboard
     */
    int getLeaderboardPosition(UUID uuid);

    /**
     * Gets the server-wide global rank position of a player (1-indexed), or -1 if unranked.
     *
     * @param uuid the player's unique identifier
     * @return 1-indexed global rank position (e.g. 1, 2, 15), or -1 if unranked
     */
    int getGlobalRank(UUID uuid);

    // ========================================================
    // Cosmetic Entitlements API (Chat Colors & Player Glow)
    // ========================================================

    /**
     * Grants a permanent cosmetic perk to a player.
     *
     * @param uuid the player's unique identifier
     * @param type the cosmetic type (CHAT_COLOR or PLAYER_GLOW)
     * @param color the color name
     * @return a CompletableFuture yielding true if successful
     */
    CompletableFuture<Boolean> givePermanentCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type, String color);

    /**
     * Grants a temporary cosmetic perk to a player.
     *
     * @param uuid the player's unique identifier
     * @param type the cosmetic type
     * @param color the color name
     * @param durationMillis duration in milliseconds
     * @return a CompletableFuture yielding true if successful
     */
    CompletableFuture<Boolean> giveTemporaryCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type, String color, long durationMillis);

    /**
     * Removes an active cosmetic perk from a player.
     *
     * @param uuid the player's unique identifier
     * @param type the cosmetic type
     * @return a CompletableFuture yielding true if successful
     */
    CompletableFuture<Boolean> removeCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type);

    /**
     * Gets a player's active cosmetic entry if active and not expired.
     *
     * @param uuid the player's unique identifier
     * @param type the cosmetic type
     * @return Optional containing the active cosmetic entry, or empty
     */
    java.util.Optional<fun.endcore.escoins.cosmetics.CosmeticEntry> getActiveCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type);

    /**
     * Gets a player's cosmetic entitlement record (whether active or toggled off, but not expired).
     *
     * @param uuid the player's unique identifier
     * @param type the cosmetic type
     * @return Optional containing the cosmetic entry, or empty
     */
    java.util.Optional<fun.endcore.escoins.cosmetics.CosmeticEntry> getCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type);

    /**
     * Checks if a player has unlocked/owns this cosmetic perk (regardless of toggle state).
     *
     * @param uuid the player's unique identifier
     * @param type the cosmetic type
     * @return true if player owns this cosmetic
     */
    boolean hasCosmetic(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type);

    /**
     * Checks if a player's cosmetic perk is currently active and enabled.
     *
     * @param uuid the player's unique identifier
     * @param type the cosmetic type
     * @return true if player owns this cosmetic and it is currently enabled
     */
    boolean isCosmeticActive(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type);

    /**
     * Sets or toggles a player's cosmetic active status (on or off).
     *
     * @param uuid the player's unique identifier
     * @param type the cosmetic type
     * @param active true to enable, false to disable
     * @return a CompletableFuture yielding true if successful, false if player does not have the cosmetic
     */
    CompletableFuture<Boolean> setCosmeticActive(UUID uuid, fun.endcore.escoins.cosmetics.CosmeticType type, boolean active);

    // ========================================================
    // Player Tags API
    // ========================================================

    /**
     * Gets the active tag ID of a player, or null if none active or expired.
     *
     * @param uuid the player's unique identifier
     * @return the tag ID or null
     */
    String getActiveTag(UUID uuid);

    /**
     * Gets the formatted active tag string with colors translated,
     * or empty string "" if none active.
     *
     * @param uuid the player's unique identifier
     * @return formatted tag string or ""
     */
    String getActiveTagDisplay(UUID uuid);

    /**
     * Gets a tag definition by ID.
     *
     * @param tagId the tag ID
     * @return TagDefinition or null
     */
    fun.endcore.escoins.tags.TagDefinition getTag(String tagId);

    /**
     * Gets all registered tag definitions.
     *
     * @return collection of all tag definitions
     */
    java.util.Collection<fun.endcore.escoins.tags.TagDefinition> getAllTags();
}
