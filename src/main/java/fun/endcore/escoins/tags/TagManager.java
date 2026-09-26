package fun.endcore.escoins.tags;

import fun.endcore.escoins.ESCoins;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player tags: configuration from tags.yml, persistent ownership, active tag selection, and chat rendering.
 */
public class TagManager {
    private final ESCoins plugin;
    private final File tagsFile;

    // Registered tags from tags.yml: Tag ID (case-insensitive) -> TagDefinition
    private final Map<String, TagDefinition> registeredTags = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // Online player tag cache: UUID -> PlayerTagData
    private final Map<UUID, PlayerTagData> playerTagsCache = new ConcurrentHashMap<>();

    public TagManager(ESCoins plugin) {
        this.plugin = plugin;
        this.tagsFile = new File(plugin.getDataFolder(), "tags.yml");

        loadConfig();

        // Warm up cache for any online players if plugin reloaded
        for (Player player : Bukkit.getOnlinePlayers()) {
            onPlayerJoin(player);
        }
    }

    public synchronized void loadConfig() {
        if (!tagsFile.exists()) {
            plugin.saveResource("tags.yml", false);
        }

        registeredTags.clear();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(tagsFile);
        ConfigurationSection section = config.getConfigurationSection("tags");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String display = section.getString(key + ".display", "[" + key.toUpperCase() + "]");
                registeredTags.put(key.toUpperCase(), new TagDefinition(key, display));
            }
        }

        plugin.getLogger().info("Loaded " + registeredTags.size() + " player tags from tags.yml.");
    }

    public void reloadConfig() {
        loadConfig();
    }

    public void stop() {
        playerTagsCache.clear();
        registeredTags.clear();
    }

    // ========================================================
    // Lifecycle Management
    // ========================================================

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        CompletableFuture.runAsync(() -> {
            try {
                PlayerTagData data = plugin.getDatabaseManager().loadPlayerTags(uuid);
                playerTagsCache.put(uuid, data);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player tags for " + player.getName(), e);
            }
        });
    }

    public void onPlayerQuit(Player player) {
        playerTagsCache.remove(player.getUniqueId());
    }

    // ========================================================
    // Tag Queries
    // ========================================================

    public TagDefinition getTag(String tagId) {
        if (tagId == null) return null;
        return registeredTags.get(tagId.trim().toUpperCase());
    }

    public boolean isValidTag(String tagId) {
        return getTag(tagId) != null;
    }

    public Map<String, TagDefinition> getRegisteredTags() {
        return Collections.unmodifiableMap(registeredTags);
    }

    public PlayerTagData getPlayerTags(UUID uuid) {
        PlayerTagData cached = playerTagsCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        return plugin.getDatabaseManager().loadPlayerTags(uuid);
    }

    public CompletableFuture<PlayerTagData> getPlayerTagsAsync(UUID uuid) {
        PlayerTagData cached = playerTagsCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> plugin.getDatabaseManager().loadPlayerTags(uuid));
    }

    /**
     * Gets the player's active tag ID (e.g. "WARLORD"), or null if none is active.
     */
    public String getActiveTag(UUID uuid) {
        PlayerTagData data = playerTagsCache.get(uuid);
        if (data != null) {
            return data.getActiveTag();
        }
        return plugin.getDatabaseManager().loadPlayerTags(uuid).getActiveTag();
    }

    /**
     * Gets the formatted display component string of the player's active tag, or empty string if none.
     */
    public String getActiveTagDisplay(UUID uuid) {
        String active = getActiveTag(uuid);
        if (active == null) {
            return "";
        }
        TagDefinition def = getTag(active);
        return def != null ? def.display() : "";
    }

    public boolean hasTag(UUID uuid, String tagId) {
        if (tagId == null) return false;
        String upper = tagId.trim().toUpperCase();
        PlayerTagData data = playerTagsCache.get(uuid);
        if (data != null) {
            return data.hasTag(upper);
        }
        return plugin.getDatabaseManager().loadPlayerTags(uuid).hasTag(upper);
    }

    // ========================================================
    // Tag Modifications
    // ========================================================

    /**
     * Grants tag ownership to a player.
     */
    public CompletableFuture<Boolean> giveTag(UUID uuid, String tagId) {
        if (!isValidTag(tagId)) {
            return CompletableFuture.completedFuture(false);
        }
        String upper = tagId.trim().toUpperCase();
        return CompletableFuture.supplyAsync(() -> plugin.getDatabaseManager().addPlayerTag(uuid, upper))
                .thenApply(success -> {
                    if (success) {
                        PlayerTagData cached = playerTagsCache.get(uuid);
                        if (cached != null) {
                            cached.addTag(upper);
                        }
                    }
                    return success;
                });
    }

    /**
     * Removes tag ownership from a player. If active, also clears active tag.
     */
    public CompletableFuture<Boolean> removeTag(UUID uuid, String tagId) {
        String upper = tagId.trim().toUpperCase();
        return CompletableFuture.supplyAsync(() -> {
            boolean removed = plugin.getDatabaseManager().removePlayerTag(uuid, upper);
            // If active tag was removed, clear active tag
            PlayerTagData data = plugin.getDatabaseManager().loadPlayerTags(uuid);
            if (upper.equalsIgnoreCase(data.getActiveTag())) {
                plugin.getDatabaseManager().setPlayerActiveTag(uuid, null);
            }
            return removed;
        }).thenApply(success -> {
            PlayerTagData cached = playerTagsCache.get(uuid);
            if (cached != null) {
                cached.removeTag(upper);
            }
            return success;
        });
    }

    /**
     * Clears all tag ownership from a player.
     */
    public CompletableFuture<Boolean> clearTags(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> plugin.getDatabaseManager().clearPlayerTags(uuid))
                .thenApply(success -> {
                    PlayerTagData cached = playerTagsCache.get(uuid);
                    if (cached != null) {
                        cached.clearTags();
                    }
                    return success;
                });
    }

    /**
     * Sets the active tag for a player. Pass null to unselect active tag.
     */
    public CompletableFuture<Boolean> setActiveTag(UUID uuid, String tagId) {
        String upper = tagId != null && !tagId.trim().isEmpty() ? tagId.trim().toUpperCase() : null;
        if (upper != null && (!isValidTag(upper) || !hasTag(uuid, upper))) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> plugin.getDatabaseManager().setPlayerActiveTag(uuid, upper))
                .thenApply(success -> {
                    if (success) {
                        PlayerTagData cached = playerTagsCache.get(uuid);
                        if (cached != null) {
                            cached.setActiveTag(upper);
                        }
                    }
                    return success;
                });
    }
}
