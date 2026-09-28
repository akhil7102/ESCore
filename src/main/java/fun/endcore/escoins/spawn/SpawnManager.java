package fun.endcore.escoins.spawn;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages server spawn location, first join teleportation, returning player location restoration,
 * and /spawn countdowns with movement cancellation.
 */
public class SpawnManager {
    private final ESCoins plugin;

    private Location spawnLocation;
    private int teleportDelay = 3; // seconds
    private boolean cancelOnMove = true;
    private Sound teleportSound;

    // Track active teleport countdowns
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();

    private record PendingTeleport(BukkitTask task, Location initialLocation) {}

    public SpawnManager(ESCoins plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        boolean isSet = config.getBoolean("spawn.set", false);
        String worldName = config.getString("spawn.world", "world");
        double x = config.getDouble("spawn.x", 0.5);
        double y = config.getDouble("spawn.y", 64.0);
        double z = config.getDouble("spawn.z", 0.5);
        float yaw = (float) config.getDouble("spawn.yaw", 0.0);
        float pitch = (float) config.getDouble("spawn.pitch", 0.0);

        if (isSet && worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                this.spawnLocation = new Location(world, x, y, z, yaw, pitch);
            }
        }

        this.teleportDelay = Math.max(0, config.getInt("spawn.teleport-delay", 3));
        this.cancelOnMove = config.getBoolean("spawn.cancel-on-move", true);

        String soundName = config.getString("spawn.sound", "ENTITY_ENDERMAN_TELEPORT");
        try {
            this.teleportSound = (soundName != null && !soundName.isEmpty()) ? Sound.valueOf(soundName.toUpperCase()) : null;
        } catch (IllegalArgumentException e) {
            this.teleportSound = Sound.ENTITY_ENDERMAN_TELEPORT;
        }
    }

    public boolean hasSpawn() {
        return spawnLocation != null && spawnLocation.getWorld() != null;
    }

    public Location getSpawnLocation() {
        return spawnLocation != null ? spawnLocation.clone() : null;
    }

    /**
     * Sets and persists the spawn location.
     */
    public void setSpawn(Location loc) {
        if (loc == null || loc.getWorld() == null) return;

        this.spawnLocation = loc.clone();

        FileConfiguration config = plugin.getConfigManager().getConfig();
        config.set("spawn.set", true);
        config.set("spawn.world", loc.getWorld().getName());
        config.set("spawn.x", loc.getX());
        config.set("spawn.y", loc.getY());
        config.set("spawn.z", loc.getZ());
        config.set("spawn.yaw", loc.getYaw());
        config.set("spawn.pitch", loc.getPitch());
        plugin.getConfigManager().saveConfig();
    }

    /**
     * Handles /spawn command execution for a player.
     */
    public void requestSpawnTeleport(Player player) {
        MessageManager mm = plugin.getMessageManager();

        if (!hasSpawn()) {
            mm.sendMessage(player, "spawn.not-set", "{PREFIX} &cServer spawn has not been set yet.");
            return;
        }

        UUID uuid = player.getUniqueId();
        if (pendingTeleports.containsKey(uuid)) {
            mm.sendMessage(player, "spawn.already-teleporting", "{PREFIX} &cYou already have a pending teleport in progress!");
            return;
        }

        // Instant teleport for 0 delay or bypass permission
        if (teleportDelay <= 0 || player.hasPermission("escoins.spawn.bypass") || player.isOp()) {
            executeTeleport(player);
            return;
        }

        // Countdown teleport
        mm.sendMessage(player, "spawn.teleport-countdown", "{PREFIX} &7Teleporting in &e{TIME}&7 seconds... Don't move!",
                "{TIME}", String.valueOf(teleportDelay)
        );

        Location startLoc = player.getLocation().clone();

        BukkitRunnable runnable = new BukkitRunnable() {
            private int remaining = teleportDelay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    pendingTeleports.remove(uuid);
                    return;
                }

                remaining--;

                if (remaining > 0) {
                    mm.sendMessage(player, "spawn.teleport-countdown", "{PREFIX} &7Teleporting in &e{TIME}&7 seconds... Don't move!",
                            "{TIME}", String.valueOf(remaining)
                    );
                } else {
                    cancel();
                    pendingTeleports.remove(uuid);
                    executeTeleport(player);
                }
            }
        };

        BukkitTask task = runnable.runTaskTimer(plugin, 20L, 20L);
        pendingTeleports.put(uuid, new PendingTeleport(task, startLoc));
    }

    private void executeTeleport(Player player) {
        MessageManager mm = plugin.getMessageManager();
        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            mm.sendMessage(player, "spawn.not-set", "{PREFIX} &cServer spawn has not been set yet.");
            return;
        }

        player.teleport(spawnLocation);

        if (teleportSound != null) {
            player.playSound(player.getLocation(), teleportSound, 1.0f, 1.0f);
        }

        mm.sendMessage(player, "spawn.teleport-success", "{PREFIX} &aTeleported to spawn!");
    }

    public void cancelTeleport(UUID uuid, boolean notifyPlayer) {
        PendingTeleport pt = pendingTeleports.remove(uuid);
        if (pt != null) {
            pt.task().cancel();
            if (notifyPlayer) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    plugin.getMessageManager().sendMessage(player, "spawn.teleport-cancelled", "{PREFIX} &cTeleport cancelled because you moved!");
                }
            }
        }
    }

    public boolean hasPendingTeleports() {
        return !pendingTeleports.isEmpty();
    }

    /**
     * Cancels all pending teleports on plugin disable/reload.
     */
    public void stop() {
        for (PendingTeleport pt : pendingTeleports.values()) {
            if (pt != null && pt.task() != null) {
                pt.task().cancel();
            }
        }
        pendingTeleports.clear();
    }

    /**
     * Checks if player moved significantly and cancels pending teleport if cancel-on-move is enabled.
     */
    public void onPlayerMove(Player player, Location from, Location to) {
        if (!cancelOnMove) return;

        PendingTeleport pt = pendingTeleports.get(player.getUniqueId());
        if (pt == null) return;

        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            cancelTeleport(player.getUniqueId(), true);
        }
    }

    /**
     * Handles player join logic for first join vs returning players.
     */
    public void handlePlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if player has joined before asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean joinedBefore = plugin.getDatabaseManager().hasJoinedBefore(uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                if (!joinedBefore) {
                    // FIRST JOIN: Teleport to spawn and record first join
                    if (hasSpawn()) {
                        player.teleport(spawnLocation);
                    }
                    Location initial = player.getLocation();
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.getDatabaseManager().markFirstJoin(
                                uuid,
                                initial.getWorld().getName(),
                                initial.getX(),
                                initial.getY(),
                                initial.getZ(),
                                initial.getYaw(),
                                initial.getPitch()
                        );
                        if (plugin.getConfigManager().getConfig().getBoolean("first-join-message.enabled", true)) {
                            int count = plugin.getDatabaseManager().getUniquePlayerCount();
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                plugin.getMessageManager().broadcastMessage(
                                        "first-join",
                                        "&c❤ &fWelcome &c{PLAYER} &fto &#FF3366&lSERVER&f! &7[&#FF3366#{COUNT}&7]",
                                        "{PLAYER}", player.getName(),
                                        "{COUNT}", fun.endcore.escoins.util.NumberFormatter.format(count)
                                );
                            });
                        }
                    });
                } else {
                    // RETURNING PLAYER: Restore saved location if safe, otherwise fallback to spawn
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        Optional<PlayerLocationData> opt = plugin.getDatabaseManager().loadPlayerLocation(uuid);

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;

                            if (opt.isPresent()) {
                                Location saved = opt.get().toLocation();
                                if (saved != null && isLocationSafe(saved)) {
                                    player.teleport(saved);
                                    return;
                                }
                            }

                            // Fallback to spawn if saved location is missing or unsafe
                            if (hasSpawn()) {
                                player.teleport(spawnLocation);
                            }
                        });
                    });
                }
            });
        });
    }

    /**
     * Handles player logout: saves last safe playable location to database.
     */
    public void handlePlayerQuit(Player player) {
        cancelTeleport(player.getUniqueId(), false);

        // Do not save death location as safe logout location
        if (player.isDead() || player.getHealth() <= 0) {
            return;
        }

        Location loc = player.getLocation();
        if (isLocationSafe(loc)) {
            UUID uuid = player.getUniqueId();
            String world = loc.getWorld().getName();
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();
            float yaw = loc.getYaw();
            float pitch = loc.getPitch();

            if (!plugin.isEnabled()) {
                if (plugin.getDatabaseManager() != null) {
                    plugin.getDatabaseManager().savePlayerLocation(uuid, world, x, y, z, yaw, pitch);
                }
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    if (plugin.getDatabaseManager() != null) {
                        plugin.getDatabaseManager().savePlayerLocation(uuid, world, x, y, z, yaw, pitch);
                    }
                });
            }
        }
    }

    /**
     * Validates whether a location is safe for a player to be restored into.
     * Prevents restoring into lava, void, suffocation, or unloaded worlds.
     */
    public boolean isLocationSafe(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }

        World world = loc.getWorld();
        double y = loc.getY();

        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return false;
        }

        Block feet = loc.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);

        // Check lava
        if (feet.getType() == Material.LAVA || head.getType() == Material.LAVA || ground.getType() == Material.LAVA) {
            return false;
        }

        // Check fire
        if (feet.getType() == Material.FIRE || feet.getType() == Material.SOUL_FIRE || head.getType() == Material.FIRE) {
            return false;
        }

        // Check suffocation in solid blocks
        if (feet.getType().isSolid() && head.getType().isSolid()) {
            return false;
        }

        // Check void falling
        if (y < 0 && ground.getType() == Material.AIR && feet.getType() == Material.AIR) {
            return false;
        }

        return true;
    }
}
