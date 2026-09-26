package fun.endcore.escoins.cosmetics;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.util.DurationParser;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player cosmetic entitlements: Chat Colors and Player Glow.
 * Provides thread-safe caching, persistence, scoreboard glow teams, and expiration handling.
 */
public class CosmeticManager {
    public static final String GLOW_TEAM_PREFIX = "escore_glow_";

    private final ESCoins plugin;

    // Configuration caches
    private boolean chatColorEnabled = true;
    private String defaultChatColor = "white";
    private final Map<String, CosmeticColor> availableChatColors = new LinkedHashMap<>();

    private boolean playerGlowEnabled = true;
    private final Set<String> availableGlowColors = new LinkedHashSet<>();

    // Active in-memory cosmetic entitlements per player: UUID -> (CosmeticType -> CosmeticEntry)
    private final Map<UUID, Map<CosmeticType, CosmeticEntry>> activeCosmetics = new ConcurrentHashMap<>();

    // Periodic task for checking online player temporary expirations
    private BukkitTask expirationTask;

    public CosmeticManager(ESCoins plugin) {
        this.plugin = plugin;
        loadConfig();
        startExpirationTask();

        // On plugin load/reload, warm up cache for already online players
        for (Player online : Bukkit.getOnlinePlayers()) {
            onPlayerJoin(online);
        }
    }

    public synchronized void loadConfig() {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        // 1. Chat Colors
        this.chatColorEnabled = config.getBoolean("cosmetics.chat-color.enabled", true);
        this.defaultChatColor = config.getString("cosmetics.chat-color.default-color", "white").toLowerCase();
        this.availableChatColors.clear();

        ConfigurationSection ccSec = config.getConfigurationSection("cosmetics.chat-color.colors");
        if (ccSec != null) {
            for (String key : ccSec.getKeys(false)) {
                String display = ccSec.getString(key + ".display");
                String chatCode = ccSec.getString(key + ".chat-code");
                CosmeticColor def = CosmeticColor.getDefault(key);
                if (display == null && def != null) display = def.displayName();
                if (chatCode == null && def != null) chatCode = def.chatCode();
                NamedTextColor ntc = def != null ? def.namedTextColor() : NamedTextColor.WHITE;
                org.bukkit.ChatColor bc = def != null ? def.bukkitColor() : org.bukkit.ChatColor.WHITE;
                this.availableChatColors.put(key.toLowerCase(), new CosmeticColor(key.toLowerCase(), display, chatCode, ntc, bc));
            }
        }
        if (this.availableChatColors.isEmpty()) {
            this.availableChatColors.putAll(CosmeticColor.getDefaults());
        }

        // 2. Player Glow
        this.playerGlowEnabled = config.getBoolean("cosmetics.player-glow.enabled", true);
        this.availableGlowColors.clear();

        List<String> glowList = config.getStringList("cosmetics.player-glow.colors");
        if (glowList != null && !glowList.isEmpty()) {
            for (String c : glowList) {
                this.availableGlowColors.add(c.toLowerCase());
            }
        }
        if (this.availableGlowColors.isEmpty()) {
            this.availableGlowColors.addAll(CosmeticColor.getDefaults().keySet());
        }
    }

    public void reloadConfig() {
        loadConfig();
    }

    public void stop() {
        if (expirationTask != null) {
            expirationTask.cancel();
            expirationTask = null;
        }

        // Clean up glow on online players to prevent leftover glowing status
        for (Player online : Bukkit.getOnlinePlayers()) {
            removePlayerGlow(online);
        }
        activeCosmetics.clear();
    }

    // ========================================================
    // Lifecycle Listeners
    // ========================================================

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        CompletableFuture.runAsync(() -> {
            try {
                List<CosmeticEntry> entries = plugin.getDatabaseManager().loadCosmetics(uuid);
                Map<CosmeticType, CosmeticEntry> map = new ConcurrentHashMap<>();

                long now = System.currentTimeMillis();
                for (CosmeticEntry entry : entries) {
                    if (entry.isExpired()) {
                        plugin.getDatabaseManager().deleteCosmetic(uuid, entry.type());
                    } else {
                        map.put(entry.type(), entry);
                    }
                }

                activeCosmetics.put(uuid, map);

                // Apply player glow if active
                CosmeticEntry glow = map.get(CosmeticType.PLAYER_GLOW);
                if (glow != null && !glow.isExpired() && playerGlowEnabled) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            applyPlayerGlow(player, glow.color());
                        }
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load cosmetics for " + player.getName(), e);
            }
        });
    }

    public void onPlayerQuit(Player player) {
        removePlayerGlow(player);
        // We can keep the active cosmetics cached or remove them on quit
        activeCosmetics.remove(player.getUniqueId());
    }

    // ========================================================
    // Expiration Management (Lightweight)
    // ========================================================

    private void startExpirationTask() {
        // Runs every 200 ticks (10 seconds) on main thread - only checks online players with temporary cosmetics
        this.expirationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                Map<CosmeticType, CosmeticEntry> map = activeCosmetics.get(player.getUniqueId());
                if (map == null || map.isEmpty()) continue;

                for (CosmeticType type : CosmeticType.values()) {
                    CosmeticEntry entry = map.get(type);
                    if (entry != null && entry.ownershipType() == OwnershipType.TEMPORARY && entry.isExpired()) {
                        // Expired!
                        map.remove(type);
                        if (type == CosmeticType.PLAYER_GLOW) {
                            removePlayerGlow(player);
                        }

                        // Remove from DB asynchronously
                        CompletableFuture.runAsync(() ->
                                plugin.getDatabaseManager().deleteCosmetic(player.getUniqueId(), type)
                        );

                        // Notify player
                        plugin.getMessageManager().sendMessage(player, "cosmetics.expired",
                                "{PREFIX}&cYour &e{TYPE} &ccosmetic has expired.",
                                "{TYPE}", type.getDisplayName()
                        );
                    }
                }
            }
        }, 200L, 200L);
    }

    // ========================================================
    // Scoreboard Glowing Management
    // ========================================================

    /**
     * Applies the glowing color to the player via scoreboard teams.
     * Reuses existing teams and does not interfere with teams of other plugins.
     */
    public void applyPlayerGlow(Player player, String colorName) {
        if (!playerGlowEnabled || player == null || !player.isOnline()) {
            return;
        }

        Scoreboard scoreboard = getTargetScoreboard(player);
        if (scoreboard == null) return;

        // 1. Remove player from any previous ESCore glow team
        removePlayerFromGlowTeams(player, scoreboard);

        // 2. Resolve team for this color
        CosmeticColor cosmeticColor = getGlowColor(colorName);
        if (cosmeticColor == null) return;

        String teamName = GLOW_TEAM_PREFIX + cosmeticColor.name();
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            try {
                team = scoreboard.registerNewTeam(teamName);
            } catch (IllegalArgumentException e) {
                team = scoreboard.getTeam(teamName);
            }
        }

        if (team != null) {
            team.color(cosmeticColor.namedTextColor());
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        }

        // 3. Set glowing
        player.setGlowing(true);
    }

    /**
     * Disables glow and removes player from ESCore glow teams.
     */
    public void removePlayerGlow(Player player) {
        if (player == null) return;

        Scoreboard scoreboard = getTargetScoreboard(player);
        if (scoreboard != null) {
            removePlayerFromGlowTeams(player, scoreboard);
        }

        if (player.isOnline()) {
            player.setGlowing(false);
        }
    }

    private void removePlayerFromGlowTeams(Player player, Scoreboard scoreboard) {
        String name = player.getName();
        for (Team team : scoreboard.getTeams()) {
            // ONLY inspect and remove from teams belonging to ESCore glow system!
            if (team.getName().startsWith(GLOW_TEAM_PREFIX) && team.hasEntry(name)) {
                team.removeEntry(name);
            }
        }
    }

    private Scoreboard getTargetScoreboard(Player player) {
        Scoreboard sb = player.getScoreboard();
        if (sb != null) return sb;
        return Bukkit.getScoreboardManager() != null ? Bukkit.getScoreboardManager().getMainScoreboard() : null;
    }

    // ========================================================
    // Cosmetic Query Methods
    // ========================================================

    public Optional<CosmeticEntry> getActiveCosmetic(UUID uuid, CosmeticType type) {
        Map<CosmeticType, CosmeticEntry> map = activeCosmetics.get(uuid);
        if (map != null) {
            CosmeticEntry entry = map.get(type);
            if (entry != null) {
                if (entry.isExpired()) {
                    map.remove(type);
                    CompletableFuture.runAsync(() -> plugin.getDatabaseManager().deleteCosmetic(uuid, type));
                    return Optional.empty();
                }
                return Optional.of(entry);
            }
        }

        // If offline or not in cache, load from DB
        List<CosmeticEntry> list = plugin.getDatabaseManager().loadCosmetics(uuid);
        for (CosmeticEntry entry : list) {
            if (entry.type() == type) {
                if (entry.isExpired()) {
                    CompletableFuture.runAsync(() -> plugin.getDatabaseManager().deleteCosmetic(uuid, type));
                    return Optional.empty();
                }
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public boolean hasCosmetic(UUID uuid, CosmeticType type) {
        return getActiveCosmetic(uuid, type).isPresent();
    }

    public CosmeticColor getChatColor(String name) {
        if (name == null) return null;
        CosmeticColor color = availableChatColors.get(name.toLowerCase());
        if (color == null) {
            color = CosmeticColor.getDefault(name);
        }
        return color;
    }

    public CosmeticColor getGlowColor(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase();
        if (!availableGlowColors.contains(lower)) {
            // Check default registry fallback
            if (!CosmeticColor.getDefaults().containsKey(lower)) {
                return null;
            }
        }
        return CosmeticColor.getDefault(lower);
    }

    public Collection<CosmeticColor> getAvailableChatColors() {
        return Collections.unmodifiableCollection(availableChatColors.values());
    }

    public Set<String> getAvailableGlowColors() {
        return Collections.unmodifiableSet(availableGlowColors);
    }

    public boolean isValidColor(CosmeticType type, String colorName) {
        if (colorName == null) return false;
        if (type == CosmeticType.CHAT_COLOR) {
            return getChatColor(colorName) != null;
        } else if (type == CosmeticType.PLAYER_GLOW) {
            return getGlowColor(colorName) != null;
        }
        return false;
    }

    // ========================================================
    // Administrative Operations (Permanent & Temporary)
    // ========================================================

    public CompletableFuture<Boolean> givePermanentCosmetic(UUID uuid, CosmeticType type, String color) {
        String cleanColor = color.toLowerCase();
        CosmeticEntry entry = new CosmeticEntry(uuid, type, cleanColor, OwnershipType.PERMANENT, null);
        return saveAndApply(uuid, entry);
    }

    public CompletableFuture<Boolean> giveTemporaryCosmetic(UUID uuid, CosmeticType type, String color, long durationMillis) {
        String cleanColor = color.toLowerCase();
        long expiresAt = System.currentTimeMillis() + durationMillis;
        CosmeticEntry entry = new CosmeticEntry(uuid, type, cleanColor, OwnershipType.TEMPORARY, expiresAt);
        return saveAndApply(uuid, entry);
    }

    private CompletableFuture<Boolean> saveAndApply(UUID uuid, CosmeticEntry entry) {
        return CompletableFuture.supplyAsync(() -> plugin.getDatabaseManager().saveCosmetic(entry))
                .thenApply(success -> {
                    if (success) {
                        // Update in-memory cache
                        activeCosmetics.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(entry.type(), entry);

                        // If player is online, apply on main thread
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (entry.type() == CosmeticType.PLAYER_GLOW) {
                                    applyPlayerGlow(player, entry.color());
                                }
                            });
                        }
                    }
                    return success;
                });
    }

    public CompletableFuture<Boolean> removeCosmetic(UUID uuid, CosmeticType type) {
        return CompletableFuture.supplyAsync(() -> plugin.getDatabaseManager().deleteCosmetic(uuid, type))
                .thenApply(success -> {
                    Map<CosmeticType, CosmeticEntry> map = activeCosmetics.get(uuid);
                    if (map != null) {
                        map.remove(type);
                    }

                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (type == CosmeticType.PLAYER_GLOW) {
                                removePlayerGlow(player);
                            }
                        });
                    }
                    return success;
                });
    }

    public boolean isChatColorEnabled() {
        return chatColorEnabled;
    }

    public boolean isPlayerGlowEnabled() {
        return playerGlowEnabled;
    }

    public String getDefaultChatColor() {
        return defaultChatColor;
    }
}
