package fun.endcore.escoins.arena;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Listens for wand interactions to set cuboid selection points.
 */
public class ArenaWandListener implements Listener {
    private final ESCoins plugin;

    public ArenaWandListener(ESCoins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        ArenaManager arenaManager = plugin.getArenaManager();
        if (!arenaManager.isWand(item)) {
            return;
        }

        if (!player.hasPermission("escoins.arena.admin") && !player.hasPermission("escoins.admin") && !player.isOp()) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block == null) return;

            event.setCancelled(true);
            Location loc = block.getLocation();
            arenaManager.setPos1(player.getUniqueId(), loc);

            MessageManager mm = plugin.getMessageManager();
            PlayerSelection sel = arenaManager.getSelection(player.getUniqueId());
            mm.sendMessage(player, "arena.pos1-set", "{PREFIX}&aPosition 1 set to &e{X}, {Y}, {Z} &a({WORLD}).",
                    "{X}", String.valueOf(loc.getBlockX()),
                    "{Y}", String.valueOf(loc.getBlockY()),
                    "{Z}", String.valueOf(loc.getBlockZ()),
                    "{WORLD}", loc.getWorld().getName());

            if (sel.isComplete() && sel.isSameWorld()) {
                mm.sendMessage(player, "arena.selection-info", "{PREFIX}&7Cuboid size: &e{SIZE_X}x{SIZE_Y}x{SIZE_Z} &7(&e{BLOCKS} &7blocks).",
                        "{SIZE_X}", String.valueOf(sel.getWidth()),
                        "{SIZE_Y}", String.valueOf(sel.getHeight()),
                        "{SIZE_Z}", String.valueOf(sel.getLength()),
                        "{BLOCKS}", String.valueOf(sel.getTotalBlocks()));
            }
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block == null) return;

            event.setCancelled(true);
            Location loc = block.getLocation();
            arenaManager.setPos2(player.getUniqueId(), loc);

            MessageManager mm = plugin.getMessageManager();
            PlayerSelection sel = arenaManager.getSelection(player.getUniqueId());
            mm.sendMessage(player, "arena.pos2-set", "{PREFIX}&aPosition 2 set to &e{X}, {Y}, {Z} &a({WORLD}).",
                    "{X}", String.valueOf(loc.getBlockX()),
                    "{Y}", String.valueOf(loc.getBlockY()),
                    "{Z}", String.valueOf(loc.getBlockZ()),
                    "{WORLD}", loc.getWorld().getName());

            if (sel.isComplete() && sel.isSameWorld()) {
                mm.sendMessage(player, "arena.selection-info", "{PREFIX}&7Cuboid size: &e{SIZE_X}x{SIZE_Y}x{SIZE_Z} &7(&e{BLOCKS} &7blocks).",
                        "{SIZE_X}", String.valueOf(sel.getWidth()),
                        "{SIZE_Y}", String.valueOf(sel.getHeight()),
                        "{SIZE_Z}", String.valueOf(sel.getLength()),
                        "{BLOCKS}", String.valueOf(sel.getTotalBlocks()));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getArenaManager().clearSelection(event.getPlayer().getUniqueId());
    }
}
