package fun.endcore.escoins.arena;

import fun.endcore.escoins.ESCoins;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages arena region definitions, selections, wand items, snapshots, and regenerations.
 */
public class ArenaManager {
    private final ESCoins plugin;
    private final NamespacedKey wandKey;
    private final File arenasFile;
    private final File snapshotsFolder;

    private final Map<String, ArenaRegion> arenas = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<UUID, PlayerSelection> selections = new ConcurrentHashMap<>();

    public ArenaManager(ESCoins plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "arena_wand");

        File arenasDir = new File(plugin.getDataFolder(), "arenas");
        if (!arenasDir.exists()) {
            arenasDir.mkdirs();
        }
        this.arenasFile = new File(arenasDir, "arenas.yml");
        this.snapshotsFolder = new File(arenasDir, "snapshots");
        if (!snapshotsFolder.exists()) {
            snapshotsFolder.mkdirs();
        }

        loadAll();
    }

    public synchronized void loadAll() {
        arenas.clear();
        if (!arenasFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(arenasFile);
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection arenaSec = section.getConfigurationSection(key);
            if (arenaSec != null) {
                String name = arenaSec.getString("name", key);
                String world = arenaSec.getString("world", "world");
                int minX = arenaSec.getInt("min-x");
                int minY = arenaSec.getInt("min-y");
                int minZ = arenaSec.getInt("min-z");
                int maxX = arenaSec.getInt("max-x");
                int maxY = arenaSec.getInt("max-y");
                int maxZ = arenaSec.getInt("max-z");
                long createdAt = arenaSec.getLong("created-at", System.currentTimeMillis());

                ArenaRegion region = new ArenaRegion(name, world, minX, minY, minZ, maxX, maxY, maxZ, createdAt);
                arenas.put(name.toLowerCase(), region);
            }
        }
    }

    public synchronized void saveAll() {
        YamlConfiguration config = new YamlConfiguration();
        for (ArenaRegion region : arenas.values()) {
            String path = "arenas." + region.getName().toLowerCase();
            config.set(path + ".name", region.getName());
            config.set(path + ".world", region.getWorldName());
            config.set(path + ".min-x", region.getMinX());
            config.set(path + ".min-y", region.getMinY());
            config.set(path + ".min-z", region.getMinZ());
            config.set(path + ".max-x", region.getMaxX());
            config.set(path + ".max-y", region.getMaxY());
            config.set(path + ".max-z", region.getMaxZ());
            config.set(path + ".created-at", region.getCreatedAt());
        }

        try {
            config.save(arenasFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save arenas.yml", e);
        }
    }

    public void reload() {
        loadAll();
    }

    public Collection<ArenaRegion> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public ArenaRegion getArena(String name) {
        if (name == null) return null;
        return arenas.get(name.toLowerCase());
    }

    public boolean arenaExists(String name) {
        if (name == null) return false;
        return arenas.containsKey(name.toLowerCase());
    }

    public File getSnapshotFile(String arenaName) {
        return new File(snapshotsFolder, arenaName.toLowerCase() + ".snapshot");
    }

    public boolean hasSnapshot(String arenaName) {
        return getSnapshotFile(arenaName).exists();
    }

    public synchronized ArenaRegion createArena(String name, Player player) throws ArenaException {
        if (name == null || name.trim().isEmpty()) {
            throw new ArenaException("Arena name cannot be empty.");
        }
        name = name.trim();
        if (!name.matches("^[a-zA-Z0-9_-]{1,32}$")) {
            throw new ArenaException("Invalid arena name. Use alphanumeric characters, underscores, and hyphens (1-32 chars).");
        }
        if (arenaExists(name)) {
            throw new ArenaException("Arena '" + name + "' already exists.");
        }

        PlayerSelection sel = selections.get(player.getUniqueId());
        if (sel == null || !sel.isComplete()) {
            throw new ArenaException("You must select both Position 1 and Position 2 with the selection wand first.");
        }
        if (!sel.isSameWorld()) {
            throw new ArenaException("Position 1 and Position 2 must be in the same world.");
        }

        int maxBlocks = getMaxBlocks();
        if (maxBlocks > 0 && sel.getTotalBlocks() > maxBlocks) {
            throw new ArenaException("Arena volume (" + sel.getTotalBlocks() + " blocks) exceeds maximum allowed size (" + maxBlocks + " blocks).");
        }

        ArenaRegion region = new ArenaRegion(
                name,
                sel.getWorldName(),
                sel.getMinX(), sel.getMinY(), sel.getMinZ(),
                sel.getMaxX(), sel.getMaxY(), sel.getMaxZ()
        );

        arenas.put(name.toLowerCase(), region);
        saveAll();
        return region;
    }

    public synchronized void deleteArena(String name) throws ArenaException {
        if (name == null) {
            throw new ArenaException("Arena name cannot be null.");
        }
        ArenaRegion removed = arenas.remove(name.toLowerCase());
        if (removed == null) {
            throw new ArenaException("Arena '" + name + "' does not exist.");
        }

        File snapshot = getSnapshotFile(name);
        if (snapshot.exists()) {
            snapshot.delete();
        }
        saveAll();
    }

    public long saveSnapshot(String name) throws ArenaException, IOException {
        ArenaRegion region = getArena(name);
        if (region == null) {
            throw new ArenaException("Arena '" + name + "' does not exist.");
        }
        return ArenaSnapshot.saveSnapshot(region, getSnapshotFile(name));
    }

    public RestoreResult regenerateArena(String name) throws ArenaException, IOException {
        ArenaRegion region = getArena(name);
        if (region == null) {
            throw new ArenaException("Arena '" + name + "' does not exist.");
        }
        File snapshot = getSnapshotFile(name);
        if (!snapshot.exists()) {
            throw new ArenaException("Arena '" + name + "' has no saved snapshot. Use /arena regen " + name + " save first.");
        }
        return ArenaSnapshot.restoreSnapshot(region, snapshot, isClearEntities());
    }

    // ========================================================
    // Selection Management
    // ========================================================

    public PlayerSelection getSelection(UUID uuid) {
        return selections.computeIfAbsent(uuid, k -> new PlayerSelection());
    }

    public void setPos1(UUID uuid, Location loc) {
        getSelection(uuid).setPos1(loc);
    }

    public void setPos2(UUID uuid, Location loc) {
        getSelection(uuid).setPos2(loc);
    }

    public void clearSelection(UUID uuid) {
        selections.remove(uuid);
    }

    // ========================================================
    // Selection Wand
    // ========================================================

    public ItemStack createWand() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String matName = config.getString("arena-regen.wand.material", "WOODEN_AXE");
        Material material = Material.matchMaterial(matName != null ? matName : "WOODEN_AXE");
        if (material == null) {
            material = Material.WOODEN_AXE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString("arena-regen.wand.name", "&#00D3FF&lArena Selection Wand");
            meta.displayName(plugin.getMessageManager().parse(name));

            List<String> rawLore = config.getStringList("arena-regen.wand.lore");
            if (rawLore.isEmpty()) {
                rawLore = List.of(
                        "&7Left-Click: &bSet Position 1",
                        "&7Right-Click: &bSet Position 2",
                        "&8Use &e/arena regen create <name> &8to finish."
                );
            }
            List<Component> lore = new ArrayList<>();
            for (String line : rawLore) {
                lore.add(plugin.getMessageManager().parse(line));
            }
            meta.lore(lore);

            // PersistentDataContainer identification tag
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // Primary: check PDC tag
        if (meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) {
            return true;
        }

        // Fallback: check configured material and custom name
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String matName = config.getString("arena-regen.wand.material", "WOODEN_AXE");
        Material material = Material.matchMaterial(matName != null ? matName : "WOODEN_AXE");
        if (material != null && item.getType() == material) {
            if (meta.hasDisplayName()) {
                String name = config.getString("arena-regen.wand.name", "&#00D3FF&lArena Selection Wand");
                Component expected = plugin.getMessageManager().parse(name);
                return expected.equals(meta.displayName());
            }
        }
        return false;
    }

    public int getMaxBlocks() {
        return plugin.getConfigManager().getConfig().getInt("arena-regen.max-blocks", 1000000);
    }

    public boolean isClearEntities() {
        return plugin.getConfigManager().getConfig().getBoolean("arena-regen.clear-entities", true);
    }
}
