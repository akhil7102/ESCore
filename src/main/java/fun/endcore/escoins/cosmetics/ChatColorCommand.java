package fun.endcore.escoins.cosmetics;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Command executor and tab completer for /cc (and /chatcolor) command alias.
 * Delegates directly to the shared CosmeticCommandHandler.
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

        if (!handler.hasPermission(sender)) {
            mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("give")) {
            return handler.handleGive(sender, args, CosmeticType.CHAT_COLOR);
        } else if (sub.equals("remove") || sub.equals("take") || sub.equals("clear")) {
            return handler.handleRemove(sender, args, CosmeticType.CHAT_COLOR);
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendMessage(sender, "cosmetics.cc-help.header", "&8&m----------------&r &b&lChat Color Management &8&m----------------");
        mm.sendMessage(sender, "cosmetics.cc-help.give-perm", "&e/cc give <player> chatcolor <color> perm &7- Grant permanent chat color");
        mm.sendMessage(sender, "cosmetics.cc-help.give-temp", "&e/cc give <player> chatcolor <color> temp <duration> &7- Grant temporary chat color");
        mm.sendMessage(sender, "cosmetics.cc-help.remove", "&e/cc remove <player> chatcolor &7- Remove player's chat color");
        mm.sendMessage(sender, "cosmetics.cc-help.footer", "&8&m----------------------------------------------------");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return handler.onTabComplete(sender, args, CosmeticType.CHAT_COLOR);
    }
}
