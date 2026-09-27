package fun.endcore.escoins.command;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.clearlag.CleanupResult;
import fun.endcore.escoins.clearlag.ClearLagManager;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Command executor and tab completer for /clearlag and /clearentities.
 */
public class ClearLagCommand implements CommandExecutor, TabCompleter {
    private final ESCoins plugin;

    public ClearLagCommand(ESCoins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!sender.hasPermission("escoins.clearlag")) {
            mm.sendMessage(sender, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
            return true;
        }

        ClearLagManager clm = plugin.getClearLagManager();
        if (clm == null || !clm.isEnabled()) {
            mm.sendMessage(sender, "clearlag.disabled", "{PREFIX}&cESCore ClearLag system is currently disabled in config.yml.");
            return true;
        }

        if (args.length == 0) {
            handleStatus(sender, clm);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "clear", "now" -> {
                mm.sendMessage(sender, "clearlag.manual-start", "{PREFIX} &eExecuting entity cleanup...");
                clm.runCleanup(sender);
            }
            case "status" -> handleStatus(sender, clm);
            case "reload" -> {
                clm.reloadConfig();
                mm.sendMessage(sender, "clearlag.reload", "{PREFIX} &aClearLag configuration reloaded.");
            }
            default -> {
                mm.sendMessage(sender, "clearlag.help.header", "&8&m----------------&r &bClearLag &7Commands &8&m----------------");
                mm.sendMessage(sender, "clearlag.help.clear", "&e/clearlag clear &7- Manually trigger entity removal");
                mm.sendMessage(sender, "clearlag.help.status", "&e/clearlag status &7- View cleanup timer and statistics");
                mm.sendMessage(sender, "clearlag.help.reload", "&e/clearlag reload &7- Reload ClearLag settings");
                mm.sendMessage(sender, "clearlag.help.footer", "&8&m----------------------------------------");
            }
        }

        return true;
    }

    private void handleStatus(CommandSender sender, ClearLagManager clm) {
        MessageManager mm = plugin.getMessageManager();
        if (!clm.isEnabled()) {
            mm.sendMessage(sender, "clearlag.status.status-disabled", "&cClearLag system is currently disabled.");
            return;
        }

        CleanupResult last = clm.getLastResult();
        mm.sendMessage(sender, "clearlag.status.header", "&8&m----------------&r &bClearLag &7Status &8&m----------------");
        mm.sendMessage(sender, "clearlag.status.next-cleanup", "&7Next cleanup in: &e{TIME} seconds", "{TIME}", String.valueOf(clm.getSecondsRemaining()));
        mm.sendMessage(sender, "clearlag.status.interval", "&7Interval: &e{INTERVAL} seconds", "{INTERVAL}", String.valueOf(clm.getInterval()));
        mm.sendMessage(sender, "clearlag.status.last-removed", "&7Last cleanup removed: &e{TOTAL} &7entities (Items: &e{ITEMS}&7, Mobs: &e{MOBS}&7, Proj: &e{PROJECTILES}&7, XP: &e{XP}&7)",
                "{TOTAL}", String.valueOf(last.totalRemoved()),
                "{ITEMS}", String.valueOf(last.itemsRemoved()),
                "{MOBS}", String.valueOf(last.mobsRemoved()),
                "{PROJECTILES}", String.valueOf(last.projectilesRemoved()),
                "{XP}", String.valueOf(last.xpRemoved())
        );
        mm.sendMessage(sender, "clearlag.status.footer", "&8&m----------------------------------------");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        ClearLagManager clm = plugin.getClearLagManager();
        if (clm == null || !clm.isEnabled() || !sender.hasPermission("escoins.clearlag")) {
            return List.of();
        }

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> options = List.of("clear", "status", "reload");
            List<String> matches = new ArrayList<>();
            for (String opt : options) {
                if (opt.startsWith(current)) {
                    matches.add(opt);
                }
            }
            return matches;
        }

        return List.of();
    }
}
