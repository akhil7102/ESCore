package fun.endcore.escoins.arena;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Command executor and tab completer for /arena and /arena regen commands.
 */
public class ArenaCommand implements CommandExecutor, TabCompleter {
    private final ESCoins plugin;

    public ArenaCommand(ESCoins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!sender.hasPermission("escoins.arena.admin") && !sender.hasPermission("escoins.admin") && !sender.isOp()) {
            mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // Handle "/arena regen ..." prefix
        int offset = 0;
        if (args[0].equalsIgnoreCase("regen")) {
            offset = 1;
            if (args.length == 1) {
                sendHelp(sender);
                return true;
            }
        }

        String sub = args[offset].toLowerCase();

        // 1. /arena regen wand
        if (sub.equals("wand")) {
            if (!(sender instanceof Player player)) {
                mm.sendMessage(sender, "player-only", "{PREFIX}&cThis command can only be executed by players.");
                return true;
            }
            ItemStack wand = plugin.getArenaManager().createWand();
            player.getInventory().addItem(wand);
            mm.sendMessage(player, "arena.wand-given", "{PREFIX}&aYou received the Arena Selection Wand. Left-click for pos 1, right-click for pos 2.");
            return true;
        }

        // 2. /arena regen create <arena>
        if (sub.equals("create")) {
            if (args.length <= offset + 1) {
                mm.sendMessage(sender, "arena.create-usage", "{PREFIX}&cUsage: /arena regen create <arena>");
                return true;
            }
            if (!(sender instanceof Player player)) {
                mm.sendMessage(sender, "player-only", "{PREFIX}&cThis command can only be executed by players.");
                return true;
            }
            String arenaName = args[offset + 1];
            try {
                ArenaRegion region = plugin.getArenaManager().createArena(arenaName, player);
                mm.sendMessage(sender, "arena.created", "{PREFIX}&aArena &e{ARENA} &acreated successfully! &7({BLOCKS} blocks)",
                        "{ARENA}", region.getName(),
                        "{BLOCKS}", String.valueOf(region.getTotalBlocks()));
            } catch (ArenaException e) {
                sender.sendMessage(mm.parse(mm.getPrefix() + "&c" + e.getMessage()));
            }
            return true;
        }

        // 3. /arena regen save <arena>
        if (sub.equals("save")) {
            if (args.length <= offset + 1) {
                mm.sendMessage(sender, "arena.save-usage", "{PREFIX}&cUsage: /arena regen <arena> save");
                return true;
            }
            handleSave(sender, args[offset + 1]);
            return true;
        }

        // 4. /arena regen regenerate <arena>
        if (sub.equals("regenerate") || sub.equals("restore")) {
            if (args.length <= offset + 1) {
                mm.sendMessage(sender, "arena.regen-usage", "{PREFIX}&cUsage: /arena regen regenerate <arena>");
                return true;
            }
            handleRegenerate(sender, args[offset + 1]);
            return true;
        }

        // 5. /arena regen list
        if (sub.equals("list")) {
            handleList(sender);
            return true;
        }

        // 6. /arena regen delete <arena>
        if (sub.equals("delete") || sub.equals("remove")) {
            if (args.length <= offset + 1) {
                mm.sendMessage(sender, "arena.delete-usage", "{PREFIX}&cUsage: /arena regen delete <arena>");
                return true;
            }
            handleDelete(sender, args[offset + 1]);
            return true;
        }

        // 7. Check for /arena regen <arena> save OR /arena regen <arena> regenerate
        if (args.length > offset + 1) {
            String arenaName = args[offset];
            String action = args[offset + 1].toLowerCase();

            if (action.equals("save")) {
                handleSave(sender, arenaName);
                return true;
            } else if (action.equals("regenerate") || action.equals("restore") || action.equals("regen")) {
                handleRegenerate(sender, arenaName);
                return true;
            }
        }

        sendHelp(sender);
        return true;
    }

    private void handleSave(CommandSender sender, String arenaName) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendMessage(sender, "arena.saving", "{PREFIX}&7Saving snapshot for arena &e{ARENA}&7...",
                "{ARENA}", arenaName);

        try {
            long totalBlocks = plugin.getArenaManager().saveSnapshot(arenaName);
            mm.sendMessage(sender, "arena.saved", "{PREFIX}&aSnapshot for arena &e{ARENA} &asaved successfully! &7({BLOCKS} blocks)",
                    "{ARENA}", arenaName,
                    "{BLOCKS}", String.valueOf(totalBlocks));
        } catch (ArenaException e) {
            sender.sendMessage(mm.parse(mm.getPrefix() + "&c" + e.getMessage()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save arena snapshot for " + arenaName, e);
            sender.sendMessage(mm.parse(mm.getPrefix() + "&cFailed to save snapshot: " + e.getMessage()));
        }
    }

    private void handleRegenerate(CommandSender sender, String arenaName) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendMessage(sender, "arena.regenerating", "{PREFIX}&7Regenerating arena &e{ARENA}&7...",
                "{ARENA}", arenaName);

        try {
            RestoreResult result = plugin.getArenaManager().regenerateArena(arenaName);
            mm.sendMessage(sender, "arena.regenerated", "{PREFIX}&aArena &e{ARENA} &aregenerated successfully! &7({MODIFIED} blocks changed in {TIME}ms)",
                    "{ARENA}", arenaName,
                    "{MODIFIED}", String.valueOf(result.modifiedBlocks()),
                    "{TIME}", String.valueOf(result.elapsedMs()));
        } catch (ArenaException e) {
            sender.sendMessage(mm.parse(mm.getPrefix() + "&c" + e.getMessage()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to regenerate arena " + arenaName, e);
            sender.sendMessage(mm.parse(mm.getPrefix() + "&cFailed to regenerate arena: " + e.getMessage()));
        }
    }

    private void handleList(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        Collection<ArenaRegion> arenas = plugin.getArenaManager().getArenas();
        if (arenas.isEmpty()) {
            mm.sendMessage(sender, "arena.list-empty", "{PREFIX}&7No arenas have been created yet.");
            return;
        }

        mm.sendMessage(sender, "arena.list-header", "&8&m----------------&r &b&lArenas &8&m----------------");
        for (ArenaRegion ar : arenas) {
            boolean hasSnap = plugin.getArenaManager().hasSnapshot(ar.getName());
            String status = hasSnap ? "&a[Saved]" : "&c[No Snapshot]";
            mm.sendMessage(sender, "arena.list-entry", "&e{ARENA} &7- &f{WORLD} &8(&7{SIZE_X}x{SIZE_Y}x{SIZE_Z}&8, &7{BLOCKS} blocks&8) {SNAPSHOT_STATUS}",
                    "{ARENA}", ar.getName(),
                    "{WORLD}", ar.getWorldName(),
                    "{SIZE_X}", String.valueOf(ar.getWidth()),
                    "{SIZE_Y}", String.valueOf(ar.getHeight()),
                    "{SIZE_Z}", String.valueOf(ar.getLength()),
                    "{BLOCKS}", String.valueOf(ar.getTotalBlocks()),
                    "{SNAPSHOT_STATUS}", status);
        }
        mm.sendMessage(sender, "arena.list-footer", "&8&m----------------------------------------");
    }

    private void handleDelete(CommandSender sender, String arenaName) {
        MessageManager mm = plugin.getMessageManager();
        try {
            plugin.getArenaManager().deleteArena(arenaName);
            mm.sendMessage(sender, "arena.deleted", "{PREFIX}&aArena &e{ARENA} &adeleted successfully.",
                    "{ARENA}", arenaName);
        } catch (ArenaException e) {
            sender.sendMessage(mm.parse(mm.getPrefix() + "&c" + e.getMessage()));
        }
    }

    private void sendHelp(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendMessage(sender, "arena.help.header", "&8&m----------------&r &b&lArena Regeneration &8&m----------------");
        mm.sendMessage(sender, "arena.help.wand", "&e/arena regen wand &7- Get the selection wand");
        mm.sendMessage(sender, "arena.help.create", "&e/arena regen create <arena> &7- Create arena from wand selection");
        mm.sendMessage(sender, "arena.help.save", "&e/arena regen <arena> save &7- Save current arena as snapshot");
        mm.sendMessage(sender, "arena.help.regenerate", "&e/arena regen regenerate <arena> &7- Restore arena from snapshot");
        mm.sendMessage(sender, "arena.help.list", "&e/arena regen list &7- List all arenas");
        mm.sendMessage(sender, "arena.help.delete", "&e/arena regen delete <arena> &7- Delete an arena");
        mm.sendMessage(sender, "arena.help.footer", "&8&m----------------------------------------");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("escoins.arena.admin") && !sender.hasPermission("escoins.admin") && !sender.isOp()) {
            return List.of();
        }

        // Tab for first argument
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("regen");
            options.add("wand");
            options.add("create");
            options.add("regenerate");
            options.add("list");
            options.add("delete");
            String current = args[0].toLowerCase();
            return options.stream().filter(o -> o.startsWith(current)).toList();
        }

        // If command started with "regen"
        if (args[0].equalsIgnoreCase("regen")) {
            if (args.length == 2) {
                List<String> options = new ArrayList<>();
                options.add("wand");
                options.add("create");
                options.add("regenerate");
                options.add("list");
                options.add("delete");
                // Also add existing arenas so "/arena regen <arena> save" tab completes!
                for (ArenaRegion ar : plugin.getArenaManager().getArenas()) {
                    options.add(ar.getName());
                }
                String current = args[1].toLowerCase();
                return options.stream().filter(o -> o.startsWith(current)).toList();
            }

            if (args.length == 3) {
                String sub = args[1].toLowerCase();
                if (sub.equals("regenerate") || sub.equals("delete") || sub.equals("save")) {
                    List<String> arenaNames = plugin.getArenaManager().getArenas().stream()
                            .map(ArenaRegion::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                    return arenaNames;
                }

                // If args[1] is an existing arena name: suggest "save" and "regenerate"
                if (plugin.getArenaManager().getArena(args[1]) != null) {
                    List<String> options = List.of("save", "regenerate");
                    String current = args[2].toLowerCase();
                    return options.stream().filter(o -> o.startsWith(current)).toList();
                }
            }
        } else {
            // Direct subcommand without "regen", e.g. "/arena regenerate <tab>"
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if (sub.equals("regenerate") || sub.equals("delete") || sub.equals("save")) {
                    return plugin.getArenaManager().getArenas().stream()
                            .map(ArenaRegion::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
                if (plugin.getArenaManager().getArena(args[0]) != null) {
                    List<String> options = List.of("save", "regenerate");
                    return options.stream().filter(o -> o.startsWith(args[1].toLowerCase())).toList();
                }
            }
        }

        return List.of();
    }
}
