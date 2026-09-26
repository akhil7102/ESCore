package fun.endcore.escoins.listener;

import fun.endcore.escoins.ESCoins;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens to player lifecycle events: memory caches, spawn restoration, and movement checks.
 */
public class PlayerConnectionListener implements Listener {
    private final ESCoins plugin;

    public PlayerConnectionListener(ESCoins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getCoinManager().onPlayerJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        plugin.getSpawnManager().handlePlayerJoin(event.getPlayer());
        if (plugin.getCosmeticManager() != null) {
            plugin.getCosmeticManager().onPlayerJoin(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getCoinManager().onPlayerQuit(event.getPlayer().getUniqueId());
        plugin.getSpawnManager().handlePlayerQuit(event.getPlayer());
        if (plugin.getCosmeticManager() != null) {
            plugin.getCosmeticManager().onPlayerQuit(event.getPlayer());
        }
        if (plugin.getArenaManager() != null) {
            plugin.getArenaManager().clearSelection(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (plugin.getSpawnManager().hasPendingTeleports() && event.hasChangedBlock()) {
            plugin.getSpawnManager().onPlayerMove(event.getPlayer(), event.getFrom(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        if (plugin.getCosmeticManager() != null) {
            plugin.getCosmeticManager().onPlayerWorldChange(event.getPlayer());
        }
    }
}
