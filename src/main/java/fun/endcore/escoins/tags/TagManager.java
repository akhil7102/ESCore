package fun.endcore.escoins.tags;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.cosmetics.CosmeticEntry;
import fun.endcore.escoins.cosmetics.CosmeticType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages player tags loaded from tags.yml and integrates with CosmeticManager.
 */
public class TagManager {
    private final ESCoins plugin;
    private final File tagsFile;
    private final Map<String, TagDefinition> registeredTags = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public TagManager(ESCoins plugin) {
        this.plugin = plugin;
        this.tagsFile = new File(plugin.getDataFolder(), "tags.yml");
        loadConfig();
    }

    public synchronized void loadConfig() {
        if (!tagsFile.exists()) {
            try {
                plugin.saveResource("tags.yml", false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not save default tags.yml", e);
            }
        }

        registeredTags.clear();
        if (tagsFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(tagsFile);
            ConfigurationSection section = config.getConfigurationSection("tags");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    int order = section.getInt(key + ".order", 999);
                    String tag = section.getString(key + ".tag", "[" + key + "]");
                    String desc = section.getString(key + ".description", "");
                    String perm = section.getString(key + ".permission", "tags.use." + key.toLowerCase());
                    registeredTags.put(key.toLowerCase(), new TagDefinition(key.toLowerCase(), order, tag, desc, perm));
                }
            }
        }

        plugin.getLogger().info("Loaded " + registeredTags.size() + " player tags from tags.yml.");
    }

    public void reloadConfig() {
        loadConfig();
    }

    public void stop() {
        registeredTags.clear();
    }

    public TagDefinition getTag(String tagId) {
        if (tagId == null) return null;
        return registeredTags.get(tagId.trim().toLowerCase());
    }

    public boolean isValidTag(String tagId) {
        return getTag(tagId) != null;
    }

    public Collection<TagDefinition> getAllTags() {
        return Collections.unmodifiableCollection(registeredTags.values());
    }

    public List<TagDefinition> getTagsSorted() {
        List<TagDefinition> list = new ArrayList<>(registeredTags.values());
        list.sort(Comparator.comparingInt(TagDefinition::order));
        return list;
    }

    public Set<String> getAllTagIds() {
        return Collections.unmodifiableSet(registeredTags.keySet());
    }

    /**
     * Gets the player's active tag ID (lowercase), or null if none is active or expired.
     */
    public String getActiveTag(UUID uuid) {
        if (plugin.getCosmeticManager() == null) return null;
        return plugin.getCosmeticManager().getActiveCosmetic(uuid, CosmeticType.TAG)
                .map(CosmeticEntry::color)
                .orElse(null);
    }

    /**
     * Gets the formatted display string of the active tag, or empty string "" if none.
     */
    public String getActiveTagDisplay(UUID uuid) {
        String active = getActiveTag(uuid);
        if (active == null) return "";
        TagDefinition def = getTag(active);
        return def != null ? def.getFormatted() : "";
    }

    /**
     * Checks if a player has access to equip a tag (either via active cosmetic ownership or permission).
     */
    public boolean hasTagAccess(Player player, String tagId) {
        if (player == null || tagId == null) return false;
        String clean = tagId.trim().toLowerCase();
        TagDefinition def = getTag(clean);
        if (def == null) return false;

        // 1. Permission check
        if (player.hasPermission(def.permission()) ||
            player.hasPermission("tags.use.*") ||
            player.hasPermission("escore.tags.use." + clean) ||
            player.hasPermission("escore.tags.*") ||
            player.hasPermission("escoins.admin") ||
            player.isOp()) {
            return true;
        }

        // 2. Cosmetic ownership check
        if (plugin.getCosmeticManager() != null) {
            Optional<CosmeticEntry> opt = plugin.getCosmeticManager().getCosmetic(player.getUniqueId(), CosmeticType.TAG);
            if (opt.isPresent() && opt.get().color().equalsIgnoreCase(clean) && !opt.get().isExpired()) {
                return true;
            }
        }

        return false;
    }
}
