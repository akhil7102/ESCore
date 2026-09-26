package fun.endcore.escoins.command;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command executor for /spawn.
 * Usable by all players with the escoins.spawn permission (no OP required).
 */
public class SpawnCommand implements CommandExecutor {
    private final ESCoins plugin;

    public SpawnCommand(ESCoins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!(sender instanceof Player player)) {
            mm.sendMessage(sender, "player-only", "{PREFIX} &cThis command can only be executed by players.");
            return true;
        }

        if (!player.hasPermission("escoins.spawn")) {
            mm.sendMessage(player, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
            return true;
        }

        plugin.getSpawnManager().requestSpawnTeleport(player);
        return true;
    }
}
