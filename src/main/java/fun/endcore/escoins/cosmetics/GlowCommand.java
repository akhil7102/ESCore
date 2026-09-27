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
 * Command executor and tab completer for /glow (and /playerglow).
 * Allows players to toggle their glowing status on and off, provided they own a glow cosmetic.
 * Also supports administrative /glow give and /glow remove for convenience.
 */
public class GlowCommand implements CommandExecutor, TabCompleter {
    private final ESCoins plugin;
    private final CosmeticCommandHandler handler;

    public GlowCommand(ESCoins plugin, CosmeticCommandHandler handler) {
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
                return handler.handleGive(sender, args, CosmeticType.PLAYER_GLOW);
            } else if (sub.equals("remove") || sub.equals("take") || sub.equals("clear")) {
                if (!handler.hasPermission(sender)) {
                    mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
                    return true;
                }
                return handler.handleRemove(sender, args, CosmeticType.PLAYER_GLOW);
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

        // 3. Permission check for toggling glow (default is true for all players)
        if (!player.hasPermission("escore.glow.toggle") && !player.hasPermission("escoins.admin") && !player.isOp()) {
            mm.sendMessage(player, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return true;
        }

        // 4. Server-wide feature enabled check
        CosmeticManager cm = plugin.getCosmeticManager();
        if (cm == null || !cm.isPlayerGlowEnabled()) {
            mm.sendMessage(player, "cosmetics.glow.disabled-server", "{PREFIX}&cPlayer glow is currently disabled on this server.");
            return true;
        }

        // 5. Verification: player must have an unlocked, non-expired glow cosmetic
        Optional<CosmeticEntry> optGlow = cm.getCosmetic(player.getUniqueId(), CosmeticType.PLAYER_GLOW);
        if (optGlow.isEmpty() || optGlow.get().isExpired()) {
            mm.sendMessage(player, "cosmetics.glow.no-glow", "{PREFIX}&cYou do not have a player glow unlocked to toggle.");
            return true;
        }

        CosmeticEntry glow = optGlow.get();

        // 6. Handle toggle logic: /glow (no args), /glow on, /glow off, /glow toggle
        if (args.length == 0) {
            // Default toggle when no argument provided
            if (glow.active()) {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.PLAYER_GLOW, false);
                mm.sendMessage(player, "cosmetics.glow.disabled", "{PREFIX}&cYour player glow has been &c&ldisabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.PLAYER_GLOW, true);
                mm.sendMessage(player, "cosmetics.glow.enabled", "{PREFIX}&aYour player glow has been &a&lenabled&a.");
            }
            return true;
        }

        String action = args[0].toLowerCase();
        if (action.equals("on") || action.equals("enable")) {
            if (glow.active()) {
                mm.sendMessage(player, "cosmetics.glow.already-enabled", "{PREFIX}&cYour player glow is already &e&lenabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.PLAYER_GLOW, true);
                mm.sendMessage(player, "cosmetics.glow.enabled", "{PREFIX}&aYour player glow has been &a&lenabled&a.");
            }
            return true;
        } else if (action.equals("off") || action.equals("disable")) {
            if (!glow.active()) {
                mm.sendMessage(player, "cosmetics.glow.already-disabled", "{PREFIX}&cYour player glow is already &e&ldisabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.PLAYER_GLOW, false);
                mm.sendMessage(player, "cosmetics.glow.disabled", "{PREFIX}&cYour player glow has been &c&ldisabled&c.");
            }
            return true;
        } else if (action.equals("toggle")) {
            if (glow.active()) {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.PLAYER_GLOW, false);
                mm.sendMessage(player, "cosmetics.glow.disabled", "{PREFIX}&cYour player glow has been &c&ldisabled&c.");
            } else {
                cm.setCosmeticActive(player.getUniqueId(), CosmeticType.PLAYER_GLOW, true);
                mm.sendMessage(player, "cosmetics.glow.enabled", "{PREFIX}&aYour player glow has been &a&lenabled&a.");
            }
            return true;
        }

        // Unknown argument: show usage (or admin help if admin)
        if (handler.hasPermission(player)) {
            sendHelp(player);
        } else {
            mm.sendMessage(player, "cosmetics.glow.usage", "{PREFIX}&cUsage: /glow <on|off>");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendMessage(sender, "cosmetics.glow-help.header", "&8&m----------------&r &b&lPlayer Glow Management &8&m----------------");
        mm.sendMessage(sender, "cosmetics.glow-help.toggle", "&e/glow <on|off> &7- Toggle your player glow cosmetic");
        mm.sendMessage(sender, "cosmetics.glow-help.give-perm", "&e/glow give <player> glow <color> perm &7- Grant permanent glow");
        mm.sendMessage(sender, "cosmetics.glow-help.give-temp", "&e/glow give <player> glow <color> temp <duration> &7- Grant temporary glow");
        mm.sendMessage(sender, "cosmetics.glow-help.remove", "&e/glow remove <player> glow &7- Remove player's glow");
        mm.sendMessage(sender, "cosmetics.glow-help.footer", "&8&m----------------------------------------------------");
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
                return handler.onTabComplete(sender, args, CosmeticType.PLAYER_GLOW);
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
