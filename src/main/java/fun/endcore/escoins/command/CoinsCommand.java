package fun.endcore.escoins.command;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.api.LeaderboardEntry;
import fun.endcore.escoins.database.PlayerData;
import fun.endcore.escoins.util.MessageManager;
import fun.endcore.escoins.util.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Command handler and tab completer for /coins and /coin.
 */
public class CoinsCommand implements CommandExecutor, TabCompleter {
    private final ESCoins plugin;

    public CoinsCommand(ESCoins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        MessageManager mm = plugin.getMessageManager();

        // 1. /coins (no args) -> View own balance
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                mm.sendMessage(sender, "player-only", "{PREFIX} &cThis command can only be executed by players.");
                return true;
            }
            if (!sender.hasPermission("escoins.balance")) {
                mm.sendMessage(sender, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
                return true;
            }
            long balance = plugin.getCoinManager().getBalance(player.getUniqueId());
            mm.sendMessage(player, "coins.balance", "{PREFIX} &7You have &e{BALANCE} Coins&7.",
                    "{BALANCE}", NumberFormatter.format(balance),
                    "{PLAYER}", player.getName()
            );
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "balance", "bal" -> handleBalance(sender, args);
            case "pay", "send" -> handlePay(sender, args);
            case "top", "leaderboard" -> handleTop(sender);
            case "give", "add" -> handleGive(sender, args);
            case "take", "remove" -> handleTake(sender, args);
            case "set" -> handleSet(sender, args);
            case "reload" -> handleReload(sender);
            default -> handleHelp(sender);
        }

        return true;
    }

    private void handleBalance(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        // /coins balance (own)
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                mm.sendMessage(sender, "player-only", "{PREFIX} &cThis command can only be executed by players.");
                return;
            }
            if (!sender.hasPermission("escoins.balance")) {
                mm.sendMessage(sender, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
                return;
            }
            long balance = plugin.getCoinManager().getBalance(player.getUniqueId());
            mm.sendMessage(player, "coins.balance", "{PREFIX} &7You have &e{BALANCE} Coins&7.",
                    "{BALANCE}", NumberFormatter.format(balance),
                    "{PLAYER}", player.getName()
            );
            return;
        }

        // /coins balance <player>
        if (!sender.hasPermission("escoins.balance.others") && !sender.hasPermission("escoins.admin")) {
            mm.sendMessage(sender, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
            return;
        }

        String targetName = args[1];
        plugin.getCoinManager().lookupPlayer(targetName).thenAccept(optData -> {
            if (optData.isEmpty()) {
                mm.sendMessage(sender, "player-not-found", "{PREFIX} &cPlayer &e{PLAYER} &cnot found.", "{PLAYER}", targetName);
                return;
            }
            PlayerData data = optData.get();
            long balance = plugin.getCoinManager().getBalance(data.uuid());
            mm.sendMessage(sender, "coins.balance-other", "{PREFIX} &e{PLAYER} &7has &e{BALANCE} Coins&7.",
                    "{PLAYER}", data.username(),
                    "{BALANCE}", NumberFormatter.format(balance)
            );
        });
    }

    private void handlePay(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!(sender instanceof Player player)) {
            mm.sendMessage(sender, "player-only", "{PREFIX} &cThis command can only be executed by players.");
            return;
        }

        if (!player.hasPermission("escoins.pay")) {
            mm.sendMessage(player, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
            return;
        }

        if (args.length < 3) {
            mm.sendMessage(player, "coins.help.pay", "&e/coins pay <player> <amount> &7- Send coins to a player");
            return;
        }

        String targetName = args[1];
        if (targetName.equalsIgnoreCase(player.getName())) {
            mm.sendMessage(player, "coins.self-payment", "{PREFIX} &cYou cannot send Coins to yourself.");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount <= 0) {
                mm.sendMessage(player, "coins.invalid-amount", "{PREFIX} &cEnter a valid amount.");
                return;
            }
        } catch (NumberFormatException e) {
            mm.sendMessage(player, "coins.invalid-amount", "{PREFIX} &cEnter a valid amount.");
            return;
        }

        long senderBalance = plugin.getCoinManager().getBalance(player.getUniqueId());
        if (senderBalance < amount) {
            mm.sendMessage(player, "coins.insufficient", "{PREFIX} &cYou don't have enough Coins.");
            return;
        }

        // Asynchronously lookup receiver and execute atomic transfer
        plugin.getCoinManager().lookupPlayer(targetName).thenAccept(optData -> {
            if (optData.isEmpty()) {
                mm.sendMessage(player, "player-not-found", "{PREFIX} &cPlayer &e{PLAYER} &cnot found.", "{PLAYER}", targetName);
                return;
            }

            PlayerData receiverData = optData.get();
            if (receiverData.uuid().equals(player.getUniqueId())) {
                mm.sendMessage(player, "coins.self-payment", "{PREFIX} &cYou cannot send Coins to yourself.");
                return;
            }

            boolean success = plugin.getCoinManager().transferCoins(player.getUniqueId(), receiverData.uuid(), amount);
            if (success) {
                String formattedAmount = NumberFormatter.format(amount);
                mm.sendMessage(player, "coins.pay.sender", "{PREFIX} &7You sent &e{AMOUNT} Coins &7to &e{PLAYER}&7.",
                        "{AMOUNT}", formattedAmount,
                        "{PLAYER}", receiverData.username()
                );

                // Notify receiver if online
                Player receiverOnline = Bukkit.getPlayer(receiverData.uuid());
                if (receiverOnline != null && receiverOnline.isOnline()) {
                    mm.sendMessage(receiverOnline, "coins.pay.receiver", "{PREFIX} &7You received &e{AMOUNT} Coins &7from &e{PLAYER}&7.",
                            "{AMOUNT}", formattedAmount,
                            "{PLAYER}", player.getName()
                    );
                }
            } else {
                mm.sendMessage(player, "coins.insufficient", "{PREFIX} &cYou don't have enough Coins.");
            }
        });
    }

    private void handleTop(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();

        if (!sender.hasPermission("escoins.top")) {
            mm.sendMessage(sender, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
            return;
        }

        List<LeaderboardEntry> top = plugin.getCoinManager().getTopLeaderboard(plugin.getLeaderboard().getSize());

        mm.sendMessage(sender, "coins.top.header", "&8&m----------------&r &bES&fCoins &eTop &8&m----------------");
        if (top.isEmpty()) {
            mm.sendMessage(sender, "coins.top.empty", "{PREFIX} &7No player data found for top coins.");
        } else {
            for (LeaderboardEntry entry : top) {
                mm.sendMessage(sender, "coins.top.entry", "&6#{POSITION} &e{PLAYER} &7- &a{AMOUNT} Coins",
                        "{POSITION}", String.valueOf(entry.rank()),
                        "{PLAYER}", entry.username(),
                        "{AMOUNT}", NumberFormatter.format(entry.balance())
                );
            }
        }
        mm.sendMessage(sender, "coins.top.footer", "&8&m----------------------------------------");
    }

    private void handleGive(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!sender.hasPermission("escoins.give") && !sender.hasPermission("escoins.admin")) {
            mm.sendMessage(sender, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
            return;
        }

        if (args.length < 3) {
            mm.sendMessage(sender, "coins.help.give", "&e/coins give <player> <amount> &7- Give coins to a player");
            return;
        }

        String targetName = args[1];
        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount <= 0) {
                mm.sendMessage(sender, "coins.invalid-amount", "{PREFIX} &cEnter a valid amount.");
                return;
            }
        } catch (NumberFormatException e) {
            mm.sendMessage(sender, "coins.invalid-amount", "{PREFIX} &cEnter a valid amount.");
            return;
        }

        plugin.getCoinManager().lookupPlayer(targetName).thenAccept(optData -> {
            UUID targetUuid;
            String resolvedName;

            if (optData.isPresent()) {
                targetUuid = optData.get().uuid();
                resolvedName = optData.get().username();
            } else {
                Player online = Bukkit.getPlayerExact(targetName);
                if (online != null) {
                    targetUuid = online.getUniqueId();
                    resolvedName = online.getName();
                } else {
                    targetUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes());
                    resolvedName = targetName;
                }
            }

            boolean success = plugin.getCoinManager().giveCoins(targetUuid, amount, sender.getName());
            if (success) {
                mm.sendMessage(sender, "coins.give", "{PREFIX} &aAdded &e{AMOUNT} Coins &ato &e{PLAYER}&a.",
                        "{AMOUNT}", NumberFormatter.format(amount),
                        "{PLAYER}", resolvedName
                );
            } else {
                mm.sendMessage(sender, "coins.invalid-amount", "{PREFIX} &cFailed to give coins.");
            }
        });
    }

    private void handleTake(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!sender.hasPermission("escoins.take") && !sender.hasPermission("escoins.admin")) {
            mm.sendMessage(sender, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
            return;
        }

        if (args.length < 3) {
            mm.sendMessage(sender, "coins.help.take", "&e/coins take <player> <amount> &7- Take coins from a player");
            return;
        }

        String targetName = args[1];
        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount <= 0) {
                mm.sendMessage(sender, "coins.invalid-amount", "{PREFIX} &cEnter a valid amount.");
                return;
            }
        } catch (NumberFormatException e) {
            mm.sendMessage(sender, "coins.invalid-amount", "{PREFIX} &cEnter a valid amount.");
            return;
        }

        plugin.getCoinManager().lookupPlayer(targetName).thenAccept(optData -> {
            if (optData.isEmpty()) {
                mm.sendMessage(sender, "player-not-found", "{PREFIX} &cPlayer &e{PLAYER} &cnot found.", "{PLAYER}", targetName);
                return;
            }

            PlayerData data = optData.get();
            long currentBalance = plugin.getCoinManager().getBalance(data.uuid());
            if (currentBalance < amount) {
                mm.sendMessage(sender, "coins.insufficient", "{PREFIX} &cPlayer only has &e" + NumberFormatter.format(currentBalance) + " &cCoins. Cannot reduce below 0.");
                return;
            }

            boolean success = plugin.getCoinManager().takeCoins(data.uuid(), amount, sender.getName());
            if (success) {
                mm.sendMessage(sender, "coins.take", "{PREFIX} &cRemoved &e{AMOUNT} Coins &cfrom &e{PLAYER}&c.",
                        "{AMOUNT}", NumberFormatter.format(amount),
                        "{PLAYER}", data.username()
                );
            } else {
                mm.sendMessage(sender, "coins.insufficient", "{PREFIX} &cFailed to remove coins (insufficient balance).");
            }
        });
    }

    private void handleSet(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!sender.hasPermission("escoins.set") && !sender.hasPermission("escoins.admin")) {
            mm.sendMessage(sender, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
            return;
        }

        if (args.length < 3) {
            mm.sendMessage(sender, "coins.help.set", "&e/coins set <player> <amount> &7- Set player's coin balance");
            return;
        }

        String targetName = args[1];
        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount < 0) {
                mm.sendMessage(sender, "coins.invalid-amount", "{PREFIX} &cEnter a valid amount.");
                return;
            }
        } catch (NumberFormatException e) {
            mm.sendMessage(sender, "coins.invalid-amount", "{PREFIX} &cEnter a valid amount.");
            return;
        }

        plugin.getCoinManager().lookupPlayer(targetName).thenAccept(optData -> {
            UUID targetUuid;
            String resolvedName;

            if (optData.isPresent()) {
                targetUuid = optData.get().uuid();
                resolvedName = optData.get().username();
            } else {
                Player online = Bukkit.getPlayerExact(targetName);
                if (online != null) {
                    targetUuid = online.getUniqueId();
                    resolvedName = online.getName();
                } else {
                    targetUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes());
                    resolvedName = targetName;
                }
            }

            boolean success = plugin.getCoinManager().setBalance(targetUuid, amount, sender.getName());
            if (success) {
                mm.sendMessage(sender, "coins.set", "{PREFIX} &aSet &e{PLAYER}'s &abalance to &e{AMOUNT} Coins&a.",
                        "{AMOUNT}", NumberFormatter.format(amount),
                        "{PLAYER}", resolvedName
                );
            } else {
                mm.sendMessage(sender, "coins.invalid-amount", "{PREFIX} &cFailed to set coin balance.");
            }
        });
    }

    private void handleReload(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();

        if (!sender.hasPermission("escoins.reload") && !sender.hasPermission("escoins.admin")) {
            mm.sendMessage(sender, "no-permission", "{PREFIX} &cYou do not have permission to execute this command.");
            return;
        }

        plugin.reloadPlugin();
        mm.sendMessage(sender, "reload-success", "{PREFIX} &aConfiguration and messages reloaded successfully.");
    }

    private void handleHelp(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendMessage(sender, "coins.help.header", "&8&m----------------&r &bES&fCoins &7Commands &8&m----------------");
        if (sender.hasPermission("escoins.balance")) {
            mm.sendMessage(sender, "coins.help.balance", "&e/coins balance [player] &7- View coin balance");
        }
        if (sender.hasPermission("escoins.pay")) {
            mm.sendMessage(sender, "coins.help.pay", "&e/coins pay <player> <amount> &7- Send coins to a player");
        }
        if (sender.hasPermission("escoins.top")) {
            mm.sendMessage(sender, "coins.help.top", "&e/coins top &7- View coin leaderboard");
        }
        if (sender.hasPermission("escoins.give") || sender.hasPermission("escoins.admin")) {
            mm.sendMessage(sender, "coins.help.give", "&e/coins give <player> <amount> &7- Give coins to a player");
        }
        if (sender.hasPermission("escoins.take") || sender.hasPermission("escoins.admin")) {
            mm.sendMessage(sender, "coins.help.take", "&e/coins take <player> <amount> &7- Take coins from a player");
        }
        if (sender.hasPermission("escoins.set") || sender.hasPermission("escoins.admin")) {
            mm.sendMessage(sender, "coins.help.set", "&e/coins set <player> <amount> &7- Set player's coin balance");
        }
        if (sender.hasPermission("escoins.reload") || sender.hasPermission("escoins.admin")) {
            mm.sendMessage(sender, "coins.help.reload", "&e/coins reload &7- Reload all configurations");
        }
        mm.sendMessage(sender, "coins.help.footer", "&8&m----------------------------------------");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("escoins.balance")) options.add("balance");
            if (sender.hasPermission("escoins.pay")) options.add("pay");
            if (sender.hasPermission("escoins.top")) options.add("top");
            if (sender.hasPermission("escoins.give") || sender.hasPermission("escoins.admin")) options.add("give");
            if (sender.hasPermission("escoins.take") || sender.hasPermission("escoins.admin")) options.add("take");
            if (sender.hasPermission("escoins.set") || sender.hasPermission("escoins.admin")) options.add("set");
            if (sender.hasPermission("escoins.reload") || sender.hasPermission("escoins.admin")) options.add("reload");

            for (String opt : options) {
                if (opt.startsWith(current)) {
                    completions.add(opt);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("balance") || sub.equals("pay") || sub.equals("give") || sub.equals("take") || sub.equals("set")) {
                String current = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(current)) {
                        completions.add(p.getName());
                    }
                }
                return completions;
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("pay") || sub.equals("give") || sub.equals("take") || sub.equals("set")) {
                List<String> amounts = List.of("10", "50", "100", "500", "1000", "5000", "10000");
                String current = args[2];
                for (String amt : amounts) {
                    if (amt.startsWith(current)) {
                        completions.add(amt);
                    }
                }
                return completions;
            }
        }

        return completions;
    }
}
