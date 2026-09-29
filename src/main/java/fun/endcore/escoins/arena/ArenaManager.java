package fun.endcore.escoins.arena;

import fun.endcore.escoins.ESCoins;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
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

    // Active regenerations protection and task tracking
    private final Set<String> activeRegenerations = ConcurrentHashMap.newKeySet();
    private final Map<String, BukkitTask> activeRegenTasks = new ConcurrentHashMap<>();

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

        long maxBlocks = getMaxBlocks();
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

    public boolean isRegenerating(String arenaName) {
        return arenaName != null && activeRegenerations.contains(arenaName.toLowerCase());
    }

    /**
     * Regenerates an arena asynchronously loading snapshot data,
     * and restoring blocks layer-by-layer and row-by-row with adaptive pacing.
     * Players remain in place freely without being teleported.
     *
     * @param name     The arena name
     * @param callback Callback executed on completion or failure
     * @throws ArenaException If preconditions fail (arena doesn't exist, no snapshot, or already regenerating)
     */
    public void regenerateArena(String name, BiConsumer<RestoreResult, Throwable> callback) throws ArenaException {
        ArenaRegion region = getArena(name);
        if (region == null) {
            throw new ArenaException("Arena '" + name + "' does not exist.");
        }
        File snapshot = getSnapshotFile(name);
        if (!snapshot.exists()) {
            throw new ArenaException("Arena '" + name + "' has no saved snapshot. Use /arena regen " + name + " save first.");
        }

        String lower = name.toLowerCase();
        if (activeRegenerations.contains(lower)) {
            throw new ArenaException("Arena '" + name + "' is already currently being regenerated!");
        }

        World world = region.getWorld();
        if (world == null) {
            throw new ArenaException("World '" + region.getWorldName() + "' is not loaded or does not exist.");
        }

        activeRegenerations.add(lower);

        // Load snapshot asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ArenaSnapshot.LoadedSnapshot loaded;
            try {
                loaded = ArenaSnapshot.loadSnapshot(snapshot, region);
            } catch (Throwable t) {
                activeRegenerations.remove(lower);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null, t));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    startPacedRegeneration(region, loaded, callback);
                } catch (Throwable t) {
                    activeRegenerations.remove(lower);
                    callback.accept(null, t);
                }
            });
        });
    }

    private void startPacedRegeneration(ArenaRegion region, ArenaSnapshot.LoadedSnapshot snapshot, BiConsumer<RestoreResult, Throwable> callback) {
        String lower = region.getName().toLowerCase();
        World world = region.getWorld();
        if (world == null || !plugin.isEnabled()) {
            activeRegenerations.remove(lower);
            callback.accept(null, new IllegalStateException("World is unloaded or plugin is disabled."));
            return;
        }

        int minX = region.getMinX();
        int minY = region.getMinY();
        int minZ = region.getMinZ();
        int maxX = region.getMaxX();
        int maxZ = region.getMaxZ();

        ArenaSnapshot.ensureChunksLoaded(world, minX, maxX, minZ, maxZ);

        FileConfiguration config = plugin.getConfigManager().getConfig();
        int baseBlocksPerTick = Math.max(1, config.getInt("arena-regen.blocks-per-tick", 250));
        int delayRows = Math.max(0, config.getInt("arena-regen.delay-between-rows-ticks", 1));
        int delayLayers = Math.max(0, config.getInt("arena-regen.delay-between-layers-ticks", 2));
        boolean adaptive = config.getBoolean("arena-regen.adaptive-pacing", true);
        int targetSec = Math.max(1, config.getInt("arena-regen.target-duration-seconds", 30));
        int maxSec = Math.max(targetSec, config.getInt("arena-regen.maximum-duration-seconds", 300));
        int minSec = Math.max(1, config.getInt("arena-regen.minimum-duration-seconds", 5));

        long totalBlocks = snapshot.getTotalBlocks();
        int blocksPerTick = baseBlocksPerTick;

        if (adaptive) {
            long targetTicks = (long) targetSec * 20L;
            long minTicks = (long) minSec * 20L;
            long maxTicks = (long) maxSec * 20L;
            long desiredTicks = Math.max(minTicks, Math.min(maxTicks, targetTicks));

            long totalRows = (long) snapshot.sizeY() * snapshot.sizeZ();
            long delayOverheadTicks = (totalRows * delayRows) + ((long) snapshot.sizeY() * delayLayers);

            if (delayOverheadTicks >= desiredTicks) {
                delayRows = 0;
                delayLayers = Math.min(1, delayLayers);
                delayOverheadTicks = (long) snapshot.sizeY() * delayLayers;
            }

            long availableTicks = Math.max(1L, desiredTicks - delayOverheadTicks);
            blocksPerTick = (int) Math.min(2500L, Math.max(25L, (totalBlocks + availableTicks - 1) / availableTicks));
        }

        final int effectiveBlocksPerTick = blocksPerTick;
        final int effectiveDelayRows = delayRows;
        final int effectiveDelayLayers = delayLayers;
        final long startTime = System.currentTimeMillis();
        final int sizeX = snapshot.sizeX();
        final int sizeY = snapshot.sizeY();
        final int sizeZ = snapshot.sizeZ();
        final org.bukkit.block.data.BlockData[] palette = snapshot.palette();
        final short[] blockIndices = snapshot.blockIndices();

        BukkitRunnable runnable = new BukkitRunnable() {
            private int currentY = 0;
            private int currentZ = 0;
            private int currentX = 0;
            private int delayTicksRemaining = 0;
            private int modifiedCount = 0;

            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    cancel();
                    activeRegenTasks.remove(lower);
                    activeRegenerations.remove(lower);
                    return;
                }

                if (delayTicksRemaining > 0) {
                    delayTicksRemaining--;
                    return;
                }

                int placedThisTick = 0;

                while (placedThisTick < effectiveBlocksPerTick && currentY < sizeY) {
                    int idx = currentY * (sizeZ * sizeX) + currentZ * sizeX + currentX;
                    org.bukkit.block.data.BlockData targetData = palette[blockIndices[idx]];
                    int worldX = minX + currentX;
                    int worldY = minY + currentY;
                    int worldZ = minZ + currentZ;

                    Block block = world.getBlockAt(worldX, worldY, worldZ);
                    if (!block.getBlockData().matches(targetData)) {
                        block.setBlockData(targetData, false);
                        modifiedCount++;
                    }

                    placedThisTick++;
                    currentX++;

                    if (currentX >= sizeX) {
                        currentX = 0;
                        currentZ++;

                        if (currentZ >= sizeZ) {
                            currentZ = 0;
                            currentY++;

                            if (currentY >= sizeY) {
                                break;
                            }

                            if (effectiveDelayLayers > 0) {
                                delayTicksRemaining = effectiveDelayLayers;
                                return;
                            }
                        } else if (effectiveDelayRows > 0) {
                            delayTicksRemaining = effectiveDelayRows;
                            return;
                        }
                    }
                }

                if (currentY >= sizeY) {
                    // Complete!
                    cancel();
                    activeRegenTasks.remove(lower);
                    activeRegenerations.remove(lower);

                    // Apply tile entities (containers, signs)
                    for (ArenaSnapshot.TileData td : snapshot.tileDataList()) {
                        td.apply(world, minX, minY, minZ);
                    }

                    // Clear loose entities if enabled
                    if (isClearEntities()) {
                        org.bukkit.util.BoundingBox box = new org.bukkit.util.BoundingBox(minX, minY, minZ, region.getMaxX() + 1.0, region.getMaxY() + 1.0, region.getMaxZ() + 1.0);
                        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(box)) {
                            if (entity instanceof Player || entity.hasMetadata("NPC")) {
                                continue;
                            }
                            entity.remove();
                        }
                    }

                    long elapsed = System.currentTimeMillis() - startTime;
                    RestoreResult result = new RestoreResult(modifiedCount, totalBlocks, elapsed);
                    callback.accept(result, null);
                }
            }
        };

        BukkitTask task = runnable.runTaskTimer(plugin, 1L, 1L);
        activeRegenTasks.put(lower, task);
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
        if (activeRegenerations.contains(name.toLowerCase())) {
            throw new ArenaException("Arena '" + name + "' is already currently being regenerated!");
        }
        return ArenaSnapshot.restoreSnapshot(region, snapshot, isClearEntities());
    }

    /**
     * Stops and cancels all currently active regeneration tasks on plugin disable/reload.
     */
    public void stop() {
        for (BukkitTask task : activeRegenTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        activeRegenTasks.clear();
        activeRegenerations.clear();
        saveAll();
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

    public long getMaxBlocks() {
        return plugin.getConfigManager().getConfig().getLong("arena-regen.max-blocks", 1000000L);
    }

    public boolean isClearEntities() {
        return plugin.getConfigManager().getConfig().getBoolean("arena-regen.clear-entities", true);
    }
}
