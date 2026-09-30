package fun.endcore.escoins.command;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Command executor and tab completer for /escore, /escoins, and /core.
 */
public class ESCoreCommand implements CommandExecutor, TabCompleter {
    private final ESCoins plugin;

    private final fun.endcore.escoins.cosmetics.CosmeticCommandHandler cosmeticHandler;

    public ESCoreCommand(ESCoins plugin) {
        this.plugin = plugin;
        this.cosmeticHandler = new fun.endcore.escoins.cosmetics.CosmeticCommandHandler(plugin);
    }

    public fun.endcore.escoins.cosmetics.CosmeticCommandHandler getCosmeticHandler() {
        return cosmeticHandler;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("reload")) {
                if (!sender.hasPermission("escoins.reload") && !sender.hasPermission("escoins.admin") && !sender.isOp()) {
                    mm.sendMessage(sender, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
                    return true;
                }
                plugin.reloadPlugin();
                mm.sendMessage(sender, "reload-success", "{PREFIX} &aAll configurations and systems reloaded successfully.");
                return true;
            }

            if (sub.equals("give")) {
                return cosmeticHandler.handleGive(sender, args, null);
            }

            if (sub.equals("remove") || sub.equals("take") || sub.equals("clear")) {
                return cosmeticHandler.handleRemove(sender, args, null);
            }

            if (sub.equals("update") || sub.equals("updates")) {
                return handleUpdateCommand(sender, args);
            }
        }

        // Default: Show plugin information and commands
        mm.sendMessage(sender, "core.header", "&8&m----------------&r &b&lESCore &8&m----------------");
        mm.sendMessage(sender, "core.version", "&7Version: &ev1.0");
        mm.sendMessage(sender, "core.author", "&7Author: &eAKHILPLAYZYT");
        fun.endcore.escoins.clearlag.ClearLagManager clm = plugin.getClearLagManager();
        boolean clmEnabled = clm != null && clm.isEnabled();
        String clmFeature = clmEnabled ? "&fClearLag" : "&7ClearLag (Disabled)";
        mm.sendMessage(sender, "core.features", "&7Features: &fCoins &8| " + clmFeature + " &8| &fSpawn &8| &fCosmetics &8| &fTags");
        if (sender.hasPermission("escoins.admin") || sender.isOp()) {
            mm.sendMessage(sender, "core.cmd-reload", "&e/escore reload &7- Reload all configurations");
            mm.sendMessage(sender, "core.cmd-give", "&e/escore give <player> <glow|chatcolor|tag> <value> <perm|temp [duration]>");
            mm.sendMessage(sender, "core.cmd-remove", "&e/escore remove <player> <glow|chatcolor|tag>");
            mm.sendMessage(sender, "core.cmd-update", "&e/escore update <check|status> &7- BuiltByBit update checker");
        }
        mm.sendMessage(sender, "core.cmd-glow", "&e/glow <on|off> &7- Toggle player glow");
        mm.sendMessage(sender, "core.cmd-chatcolor", "&e/chatcolor <on|off> &7- Toggle chat color");
        mm.sendMessage(sender, "core.cmd-tag", "&e/tag <on|off|toggle|list|set> &7- Toggle and select player tags");
        mm.sendMessage(sender, "core.cmd-cc", "&e/cc &7- Chat color cosmetic shortcuts");
        mm.sendMessage(sender, "core.cmd-coins", "&e/coins &7- Manage and view premium coins");
        if (clmEnabled) {
            mm.sendMessage(sender, "core.cmd-clearlag", "&e/clearlag &7- Manage entity cleanup system");
        }
        mm.sendMessage(sender, "core.cmd-spawn", "&e/spawn &7- Teleport to server spawn");
        if (sender.hasPermission("escoins.setspawn") || sender.isOp()) {
            mm.sendMessage(sender, "core.cmd-setspawn", "&e/setspawn &7- Set server spawn location");
        }
        mm.sendMessage(sender, "core.footer", "&8&m----------------------------------------");
        return true;
    }

    private boolean handleUpdateCommand(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        fun.endcore.escoins.update.UpdateChecker uc = plugin.getUpdateChecker();
        if (uc == null) {
            mm.sendMessage(sender, "update.not-available", "{PREFIX}&cUpdate checker is not available.");
            return true;
        }

        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            if (!sender.hasPermission("escore.update.admin") && !sender.hasPermission("escoins.admin") && !sender.isOp()) {
                mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
                return true;
            }
            uc.sendStatus(sender);
            return true;
        }

        if (args[1].equalsIgnoreCase("check")) {
            if (!sender.hasPermission("escore.update.check") && !sender.hasPermission("escore.update.admin") && !sender.hasPermission("escoins.admin") && !sender.isOp()) {
                mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
                return true;
            }
            uc.forceCheck(sender);
            return true;
        }

        // Default: Show status
        uc.sendStatus(sender);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 1) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("remove") || sub.equals("take") || sub.equals("clear")) {
                return cosmeticHandler.onTabComplete(sender, args, null);
            }

            if (sub.equals("update") || sub.equals("updates")) {
                if (args.length == 2) {
                    List<String> options = new ArrayList<>();
                    if (sender.hasPermission("escore.update.admin") || sender.hasPermission("escoins.admin") || sender.isOp()) {
                        options.add("status");
                    }
                    if (sender.hasPermission("escore.update.check") || sender.hasPermission("escore.update.admin") || sender.hasPermission("escoins.admin") || sender.isOp()) {
                        options.add("check");
                    }
                    String current = args[1].toLowerCase();
                    return options.stream().filter(s -> s.startsWith(current)).toList();
                }
                return List.of();
            }
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("escoins.admin") || sender.hasPermission("escore.cosmetics.admin") || sender.isOp()) {
                options.add("reload");
                options.add("give");
                options.add("remove");
            }
            if (sender.hasPermission("escore.update.admin") || sender.hasPermission("escore.update.check") || sender.hasPermission("escoins.admin") || sender.isOp()) {
                options.add("update");
            }
            options.add("version");
            options.add("help");

            String current = args[0].toLowerCase();
            return options.stream().filter(o -> o.startsWith(current)).toList();
        }
        return List.of();
    }
}
