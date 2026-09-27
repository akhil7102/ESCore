package fun.endcore.escoins.cosmetics;

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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Command executor and tab completer for /cc and /chatcolor.
 * Allows players to toggle their chat color cosmetic on and off, provided they own a chat color.
 * Also supports administrative /chatcolor give and /chatcolor remove for convenience.
 */
public class ChatColorCommand implements CommandExecutor, TabCompleter {
    private final ESCoins plugin;
    private final CosmeticCommandHandler handler;

    public ChatColorCommand(ESCoins plugin, CosmeticCommandHandler handler) {
        this.plugin = plugin;
        this.handler = handler;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        MessageManager mm = plugin.getMessageManager();

        // 1. Check for administrative subcommands (give, remove, take, clear)
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give")) {
                if (!handler.hasPermission(sender)) {
                    mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
                    return true;
                }
                return handler.handleGive(sender, args, CosmeticType.CHAT_COLOR);
            } else if (sub.equals("remove") || sub.equals("take") || sub.equals("clear")) {
                if (!handler.hasPermission(sender)) {
                    mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
                    return true;
                }
                return handler.handleRemove(sender, args, CosmeticType.CHAT_COLOR);
            }
        }

        // 2. Ensure sender is a player for personal toggle operations
        if (!(sender instanceof Player player)) {
            if (handler.hasPermission(sender)) {
                sendHelp(sender);
            } else {
                mm.sendMessage(sender, "player-only", "{PREFIX}&cThis command can only be executed by players.");
            }
            return true;
        }

        // 3. If administrator runs bare /chatcolor or /cc without args, show administrative help
        if (args.length == 0 && handler.hasPermission(player)) {
            sendHelp(player);
            return true;
        }

        // 4. Permission check for toggling chat color (default is true for all players)
        if (!player.hasPermission("escore.chatcolor.toggle") && !player.hasPermission("escoins.admin") && !player.isOp()) {
            mm.sendMessage(player, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return true;
        }

        // 5. Server-wide feature enabled check
        CosmeticManager cm = plugin.getCosmeticManager();
        if (cm == null || !cm.isChatColorEnabled()) {
            mm.sendMessage(player, "cosmetics.chatcolor.disabled-server", "{PREFIX}&cChat colors are currently disabled on this server.");
            return true;
        }

        // 6. Verification: player must have an unlocked, non-expired chat color cosmetic
        Optional<CosmeticEntry> optColor = cm.getCosmetic(player.getUniqueId(), CosmeticType.CHAT_COLOR);
        if (optColor.isEmpty() || optColor.get().isExpired()) {
            mm.sendMessage(player, "cosmetics.chatcolor.no-chatcolor", "{PREFIX}&cYou do not have a chat color unlocked to toggle.");
            return true;
        }

        CosmeticEntry color = optColor.get();

        // 7. Handle toggle logic: /chatcolor (no args), /chatcolor on, /chatcolor off, /chatcolor toggle
        if (args.length == 0) {
            // Default toggle when no argument provided
            if (color.active()) {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.CHAT_COLOR, false);
                mm.sendMessage(player, "cosmetics.chatcolor.disabled", "{PREFIX}&cYour chat color has been &c&ldisabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.CHAT_COLOR, true);
                mm.sendMessage(player, "cosmetics.chatcolor.enabled", "{PREFIX}&aYour chat color has been &a&lenabled&a.");
            }
            return true;
        }

        String action = args[0].toLowerCase();
        if (action.equals("on") || action.equals("enable")) {
            if (color.active()) {
                mm.sendMessage(player, "cosmetics.chatcolor.already-enabled", "{PREFIX}&cYour chat color is already &e&lenabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.CHAT_COLOR, true);
                mm.sendMessage(player, "cosmetics.chatcolor.enabled", "{PREFIX}&aYour chat color has been &a&lenabled&a.");
            }
            return true;
        } else if (action.equals("off") || action.equals("disable")) {
            if (!color.active()) {
                mm.sendMessage(player, "cosmetics.chatcolor.already-disabled", "{PREFIX}&cYour chat color is already &e&ldisabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.CHAT_COLOR, false);
                mm.sendMessage(player, "cosmetics.chatcolor.disabled", "{PREFIX}&cYour chat color has been &c&ldisabled&c.");
            }
            return true;
        } else if (action.equals("toggle")) {
            if (color.active()) {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.CHAT_COLOR, false);
                mm.sendMessage(player, "cosmetics.chatcolor.disabled", "{PREFIX}&cYour chat color has been &c&ldisabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.CHAT_COLOR, true);
                mm.sendMessage(player, "cosmetics.chatcolor.enabled", "{PREFIX}&aYour chat color has been &a&lenabled&a.");
            }
            return true;
        }

        // Unknown argument: show usage (or admin help if admin)
        if (handler.hasPermission(player)) {
            sendHelp(player);
        } else {
            mm.sendMessage(player, "cosmetics.chatcolor.usage", "{PREFIX}&cUsage: /chatcolor <on|off>");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendMessage(sender, "cosmetics.cc-help.header", "&8&m----------------&r &b&lChat Color Management &8&m----------------");
        mm.sendMessage(sender, "cosmetics.cc-help.toggle", "&e/chatcolor <on|off> &7- Toggle your chat color cosmetic");
        mm.sendMessage(sender, "cosmetics.cc-help.give-perm", "&e/cc give <player> chatcolor <color> perm &7- Grant permanent chat color");
        mm.sendMessage(sender, "cosmetics.cc-help.give-temp", "&e/cc give <player> chatcolor <color> temp <duration> &7- Grant temporary chat color");
        mm.sendMessage(sender, "cosmetics.cc-help.remove", "&e/cc remove <player> chatcolor &7- Remove player's chat color");
        mm.sendMessage(sender, "cosmetics.cc-help.footer", "&8&m----------------------------------------------------");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("on");
            list.add("off");
            list.add("toggle");
            if (handler.hasPermission(sender)) {
                list.add("give");
                list.add("remove");
            }
            return filterPrefix(list, args[0]);
        }
        if (handler.hasPermission(sender) && args.length > 1) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("remove") || sub.equals("take") || sub.equals("clear")) {
                return handler.onTabComplete(sender, args, CosmeticType.CHAT_COLOR);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> res = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(lower)) res.add(c);
        }
        return res;
    }
}
