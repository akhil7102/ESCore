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

            if (sub.equals("tag") || sub.equals("tags")) {
                return handleTagCommand(sender, args);
            }

            if (sub.equals("update") || sub.equals("updates")) {
                return handleUpdateCommand(sender, args);
            }
        }

        // Default: Show plugin information and commands
        mm.sendMessage(sender, "core.header", "&8&m----------------&r &b&lESCore &8&m----------------");
        mm.sendMessage(sender, "core.version", "&7Version: &ev1.0");
        mm.sendMessage(sender, "core.author", "&7Author: &eAKHILPLAYZYT");
        mm.sendMessage(sender, "core.features", "&7Features: &fCoins &8| &fClearLag &8| &fSpawn &8| &fArena Regen &8| &fCosmetics &8| &fTags");
        if (sender.hasPermission("escoins.admin") || sender.isOp()) {
            mm.sendMessage(sender, "core.cmd-reload", "&e/escore reload &7- Reload all configurations");
            mm.sendMessage(sender, "core.cmd-give", "&e/escore give <player> <tags|glow|chatcolor> <value> <perm|temp [duration]>");
            mm.sendMessage(sender, "core.cmd-remove", "&e/escore remove <player> <tags|glow|chatcolor> [tag]");
            mm.sendMessage(sender, "core.cmd-tag", "&e/escore tag <give|remove|clear|list> &7- Manage player tags");
            mm.sendMessage(sender, "core.cmd-update", "&e/escore update <check|status> &7- BuiltByBit update checker");
        }
        mm.sendMessage(sender, "core.cmd-tags", "&e/tags &7- Player chat tags");
        mm.sendMessage(sender, "core.cmd-cc", "&e/cc &7- Chat color cosmetic shortcuts");
        mm.sendMessage(sender, "core.cmd-coins", "&e/coins &7- Manage and view premium coins");
        mm.sendMessage(sender, "core.cmd-clearlag", "&e/clearlag &7- Manage entity cleanup system");
        mm.sendMessage(sender, "core.cmd-spawn", "&e/spawn &7- Teleport to server spawn");
        if (sender.hasPermission("escoins.setspawn") || sender.isOp()) {
            mm.sendMessage(sender, "core.cmd-setspawn", "&e/setspawn &7- Set server spawn location");
        }
        if (sender.hasPermission("escoins.arena.admin") || sender.hasPermission("escoins.admin") || sender.isOp()) {
            mm.sendMessage(sender, "core.cmd-arena", "&e/arena regen &7- Arena regeneration system");
        }
        mm.sendMessage(sender, "core.footer", "&8&m----------------------------------------");
        return true;
    }

    private boolean handleTagCommand(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        fun.endcore.escoins.tags.TagManager tm = plugin.getTagManager();

        if (args.length == 1) {
            sendTagHelp(sender);
            return true;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "give" -> {
                if (!sender.hasPermission("escore.tags.give") && !sender.hasPermission("escore.tags.admin") && !sender.hasPermission("escoins.admin") && !sender.isOp()) {
                    mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 4) {
                    mm.sendMessage(sender, "tags.admin.give-usage", "{PREFIX}&cUsage: /escore tag give <player> <tag>");
                    return true;
                }
                String targetName = args[2];
                String tagId = args[3].toUpperCase();
                if (!tm.isValidTag(tagId)) {
                    mm.sendMessage(sender, "tags.not-found", "{PREFIX}&cTag '&e{TAG}&c' does not exist.", "{TAG}", tagId);
                    return true;
                }
                fun.endcore.escoins.tags.TagDefinition def = tm.getTag(tagId);
                cosmeticHandler.resolvePlayerAsync(targetName).thenAccept(opt -> {
                    if (opt.isEmpty()) {
                        mm.sendMessage(sender, "player-not-found", "{PREFIX}&cPlayer &e{PLAYER} &cnot found.", "{PLAYER}", targetName);
                        return;
                    }
                    var profile = opt.get();
                    if (tm.hasTag(profile.uuid(), tagId)) {
                        mm.sendMessage(sender, "tags.admin.already-owns", "{PREFIX}&e{PLAYER} &calready owns the '&e{TAG}&c' tag.",
                                "{PLAYER}", profile.name(), "{TAG}", tagId);
                        return;
                    }
                    tm.giveTag(profile.uuid(), tagId).thenAccept(success -> {
                        if (success) {
                            mm.sendMessage(sender, "tags.admin.given",
                                    "{PREFIX}&aSuccessfully granted tag &r{TAG_DISPLAY} &ato &e{PLAYER}&a.",
                                    "{TAG_DISPLAY}", def.display(),
                                    "{TAG}", tagId,
                                    "{PLAYER}", profile.name());
                            Player online = org.bukkit.Bukkit.getPlayer(profile.uuid());
                            if (online != null && online.isOnline()) {
                                mm.sendMessage(online, "tags.received",
                                        "{PREFIX}&aYou were granted the &r{TAG_DISPLAY} &atag! Use &e/tags select {TAG} &ato activate it.",
                                        "{TAG_DISPLAY}", def.display(),
                                        "{TAG}", tagId);
                            }
                        } else {
                            mm.sendMessage(sender, "tags.error", "{PREFIX}&cFailed to grant tag.");
                        }
                    });
                });
            }
            case "remove" -> {
                if (!sender.hasPermission("escore.tags.remove.others") && !sender.hasPermission("escore.tags.admin") && !sender.hasPermission("escoins.admin") && !sender.isOp()) {
                    mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 4) {
                    mm.sendMessage(sender, "tags.admin.remove-usage", "{PREFIX}&cUsage: /escore tag remove <player> <tag>");
                    return true;
                }
                String targetName = args[2];
                String tagId = args[3].toUpperCase();
                fun.endcore.escoins.tags.TagDefinition def = tm.getTag(tagId);
                String display = def != null ? def.display() : tagId;

                cosmeticHandler.resolvePlayerAsync(targetName).thenAccept(opt -> {
                    if (opt.isEmpty()) {
                        mm.sendMessage(sender, "player-not-found", "{PREFIX}&cPlayer &e{PLAYER} &cnot found.", "{PLAYER}", targetName);
                        return;
                    }
                    var profile = opt.get();
                    if (!tm.hasTag(profile.uuid(), tagId)) {
                        mm.sendMessage(sender, "tags.admin.not-owned", "{PREFIX}&e{PLAYER} &cdoes not own the '&e{TAG}&c' tag.",
                                "{PLAYER}", profile.name(), "{TAG}", tagId);
                        return;
                    }
                    tm.removeTag(profile.uuid(), tagId).thenAccept(success -> {
                        if (success) {
                            mm.sendMessage(sender, "tags.admin.removed",
                                    "{PREFIX}&aSuccessfully removed tag &r{TAG_DISPLAY} &afrom &e{PLAYER}&a.",
                                    "{TAG_DISPLAY}", display,
                                    "{TAG}", tagId,
                                    "{PLAYER}", profile.name());
                            Player online = org.bukkit.Bukkit.getPlayer(profile.uuid());
                            if (online != null && online.isOnline()) {
                                mm.sendMessage(online, "tags.revoked",
                                        "{PREFIX}&cThe &r{TAG_DISPLAY} &ctag was removed from your account.",
                                        "{TAG_DISPLAY}", display,
                                        "{TAG}", tagId);
                            }
                        } else {
                            mm.sendMessage(sender, "tags.error", "{PREFIX}&cFailed to remove tag.");
                        }
                    });
                });
            }
            case "clear" -> {
                if (!sender.hasPermission("escore.tags.clear") && !sender.hasPermission("escore.tags.admin") && !sender.hasPermission("escoins.admin") && !sender.isOp()) {
                    mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 3) {
                    mm.sendMessage(sender, "tags.admin.clear-usage", "{PREFIX}&cUsage: /escore tag clear <player>");
                    return true;
                }
                String targetName = args[2];
                cosmeticHandler.resolvePlayerAsync(targetName).thenAccept(opt -> {
                    if (opt.isEmpty()) {
                        mm.sendMessage(sender, "player-not-found", "{PREFIX}&cPlayer &e{PLAYER} &cnot found.", "{PLAYER}", targetName);
                        return;
                    }
                    var profile = opt.get();
                    tm.clearTags(profile.uuid()).thenAccept(success -> {
                        if (success) {
                            mm.sendMessage(sender, "tags.admin.cleared",
                                    "{PREFIX}&aSuccessfully cleared all tags from &e{PLAYER}&a.",
                                    "{PLAYER}", profile.name());
                            Player online = org.bukkit.Bukkit.getPlayer(profile.uuid());
                            if (online != null && online.isOnline()) {
                                mm.sendMessage(online, "tags.all-cleared", "{PREFIX}&cAll your tags have been cleared.");
                            }
                        } else {
                            mm.sendMessage(sender, "tags.error", "{PREFIX}&cFailed to clear tags.");
                        }
                    });
                });
            }
            case "list" -> {
                if (!sender.hasPermission("escore.tags.admin") && !sender.hasPermission("escoins.admin") && !sender.isOp()) {
                    mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
                    return true;
                }
                var allTags = tm.getRegisteredTags();
                if (allTags.isEmpty()) {
                    mm.sendMessage(sender, "tags.admin.list-empty", "{PREFIX}&7No tags configured in tags.yml.");
                    return true;
                }
                mm.sendMessage(sender, "tags.admin.list-header", "&8&m----------------&r &b&lAvailable Tags &8&m----------------");
                for (var def : allTags.values()) {
                    mm.sendMessage(sender, "tags.admin.list-entry", "&8- &r{TAG_DISPLAY} &7(&e{TAG_NAME}&7)",
                            "{TAG_DISPLAY}", def.display(),
                            "{TAG_NAME}", def.id());
                }
                mm.sendMessage(sender, "tags.admin.list-footer", "&8&m--------------------------------------------------");
            }
            default -> sendTagHelp(sender);
        }
        return true;
    }

    private void sendTagHelp(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendMessage(sender, "tags.admin.help-header", "&8&m----------------&r &b&lTag Admin Commands &8&m----------------");
        mm.sendMessage(sender, "tags.admin.help-give", "&e/escore tag give <player> <tag> &7- Grant tag to player");
        mm.sendMessage(sender, "tags.admin.help-remove", "&e/escore tag remove <player> <tag> &7- Remove tag from player");
        mm.sendMessage(sender, "tags.admin.help-clear", "&e/escore tag clear <player> &7- Clear all tags from player");
        mm.sendMessage(sender, "tags.admin.help-list", "&e/escore tag list &7- List all configured tags");
        mm.sendMessage(sender, "tags.admin.help-footer", "&8&m----------------------------------------------------");
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

            if (sub.equals("tag") || sub.equals("tags")) {
                return completeTagCommand(sender, args);
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
            if (sender.hasPermission("escore.tags.admin") || sender.hasPermission("escoins.admin") || sender.isOp()) {
                options.add("tag");
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

    private List<String> completeTagCommand(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return List.of("give", "remove", "clear", "list").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        String action = args[1].toLowerCase();
        if (args.length == 3 && (action.equals("give") || action.equals("remove") || action.equals("clear"))) {
            String current = args[2].toLowerCase();
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(current))
                    .toList();
        }

        if (args.length == 4 && action.equals("give")) {
            String current = args[3].toLowerCase();
            return plugin.getTagManager().getRegisteredTags().keySet().stream()
                    .filter(k -> k.toLowerCase().startsWith(current))
                    .toList();
        }

        if (args.length == 4 && action.equals("remove")) {
            String current = args[3].toLowerCase();
            Player target = org.bukkit.Bukkit.getPlayerExact(args[2]);
            if (target != null) {
                return plugin.getTagManager().getPlayerTags(target.getUniqueId()).getOwnedTags().stream()
                        .filter(k -> k.toLowerCase().startsWith(current))
                        .toList();
            }
            return plugin.getTagManager().getRegisteredTags().keySet().stream()
                    .filter(k -> k.toLowerCase().startsWith(current))
                    .toList();
        }

        return List.of();
    }
}
