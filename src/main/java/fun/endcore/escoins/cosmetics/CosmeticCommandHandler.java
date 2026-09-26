package fun.endcore.escoins.cosmetics;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.database.PlayerData;
import fun.endcore.escoins.util.DurationParser;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Shared command execution and tab-completion logic for cosmetic administrative commands.
 * Used by both /escore (subcommands give/remove) and /cc.
 */
public class CosmeticCommandHandler {
    private final ESCoins plugin;

    public CosmeticCommandHandler(ESCoins plugin) {
        this.plugin = plugin;
    }

    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("escore.cosmetics.admin") ||
               sender.hasPermission("escoins.admin") ||
               sender.isOp();
    }

    /**
     * Handles /... give <player> [chatcolor|playerglow] <color> <perm|temp> [duration]
     */
    public boolean handleGive(CommandSender sender, String[] args, CosmeticType fallbackType) {
        MessageManager mm = plugin.getMessageManager();

        if (!hasPermission(sender)) {
            mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return true;
        }

        // Expected minimum arguments:
        // args[0] = "give"
        // args[1] = target
        // args[2] = [type or color]
        if (args.length < 4) {
            sendGiveUsage(sender, fallbackType);
            return true;
        }

        String targetName = args[1];
        CosmeticType type = CosmeticType.fromString(args[2]);
        String color;
        String ownershipStr;
        String durationStr = null;

        if (type != null) {
            // Full syntax: give <player> <type> <color> <perm|temp> [duration]
            if (args.length < 5) {
                sendGiveUsage(sender, type);
                return true;
            }
            color = args[3].toLowerCase();
            ownershipStr = args[4].toLowerCase();
            if (args.length >= 6) {
                durationStr = args[5];
            }
        } else {
            // Shorthand syntax (e.g. /cc give <player> <color> <perm|temp> [duration]):
            type = fallbackType != null ? fallbackType : CosmeticType.CHAT_COLOR;
            color = args[2].toLowerCase();
            ownershipStr = args[3].toLowerCase();
            if (args.length >= 5) {
                durationStr = args[4];
            }
        }

        CosmeticManager cm = plugin.getCosmeticManager();
        if (!cm.isValidColor(type, color)) {
            mm.sendMessage(sender, "cosmetics.invalid-color", "{PREFIX}&cInvalid color &e{COLOR}&c. Available: &7{COLORS}",
                    "{COLOR}", color,
                    "{COLORS}", getAvailableColorsString(type));
            return true;
        }

        OwnershipType ownership = OwnershipType.fromString(ownershipStr);
        if (ownership == null) {
            mm.sendMessage(sender, "cosmetics.invalid-ownership", "{PREFIX}&cInvalid ownership type &e{TYPE}&c. Use &eperm &cor &etemp <duration>&c.",
                    "{TYPE}", ownershipStr);
            return true;
        }

        long durationMillis = 0;
        if (ownership == OwnershipType.TEMPORARY) {
            if (durationStr == null || durationStr.trim().isEmpty()) {
                mm.sendMessage(sender, "cosmetics.missing-duration", "{PREFIX}&cYou must specify a duration for temporary cosmetics. Examples: 30m, 12h, 5d, 2w");
                return true;
            }
            try {
                durationMillis = DurationParser.parseToMillis(durationStr);
            } catch (IllegalArgumentException e) {
                mm.sendMessage(sender, "cosmetics.invalid-duration", "{PREFIX}&c{ERROR}",
                        "{ERROR}", e.getMessage());
                return true;
            }
        }

        final CosmeticType finalType = type;
        final String finalColor = color;
        final OwnershipType finalOwnership = ownership;
        final long finalDuration = durationMillis;
        final String finalDurationFormatted = ownership == OwnershipType.TEMPORARY ? DurationParser.formatRemaining(durationMillis) : "Permanent";

        // Resolve player (online or offline)
        resolvePlayerAsync(targetName).thenAccept(optPlayer -> {
            if (optPlayer.isEmpty()) {
                mm.sendMessage(sender, "player-not-found", "{PREFIX}&cPlayer &e{PLAYER} &cnot found.",
                        "{PLAYER}", targetName);
                return;
            }

            TargetProfile target = optPlayer.get();
            CompletableFuture<Boolean> future;
            if (finalOwnership == OwnershipType.PERMANENT) {
                future = cm.givePermanentCosmetic(target.uuid(), finalType, finalColor);
            } else {
                future = cm.giveTemporaryCosmetic(target.uuid(), finalType, finalColor, finalDuration);
            }

            future.thenAccept(success -> {
                if (success) {
                    // Feedback to sender
                    if (finalOwnership == OwnershipType.PERMANENT) {
                        mm.sendMessage(sender, "cosmetics.given-perm",
                                "{PREFIX}&aGranted permanent &e{COLOR} {TYPE} &ato &e{PLAYER}&a.",
                                "{COLOR}", finalColor,
                                "{TYPE}", finalType.getDisplayName(),
                                "{PLAYER}", target.name());
                    } else {
                        mm.sendMessage(sender, "cosmetics.given-temp",
                                "{PREFIX}&aGranted temporary &e{COLOR} {TYPE} &ato &e{PLAYER} &afor &e{DURATION}&a.",
                                "{COLOR}", finalColor,
                                "{TYPE}", finalType.getDisplayName(),
                                "{PLAYER}", target.name(),
                                "{DURATION}", finalDurationFormatted);
                    }

                    // Feedback to online player
                    Player online = Bukkit.getPlayer(target.uuid());
                    if (online != null && online.isOnline()) {
                        if (finalOwnership == OwnershipType.PERMANENT) {
                            mm.sendMessage(online, "cosmetics.received-perm",
                                    "{PREFIX}&aYou received permanent &e{COLOR} {TYPE}&a!",
                                    "{COLOR}", finalColor,
                                    "{TYPE}", finalType.getDisplayName());
                        } else {
                            mm.sendMessage(online, "cosmetics.received-temp",
                                    "{PREFIX}&aYou received temporary &e{COLOR} {TYPE} &afor &e{DURATION}&a!",
                                    "{COLOR}", finalColor,
                                    "{TYPE}", finalType.getDisplayName(),
                                    "{DURATION}", finalDurationFormatted);
                        }
                    }
                } else {
                    mm.sendMessage(sender, "cosmetics.error", "{PREFIX}&cFailed to save cosmetic entitlement.");
                }
            });
        });

        return true;
    }

    /**
     * Handles /... remove <player> [chatcolor|playerglow]
     */
    public boolean handleRemove(CommandSender sender, String[] args, CosmeticType fallbackType) {
        MessageManager mm = plugin.getMessageManager();

        if (!hasPermission(sender)) {
            mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return true;
        }

        // args[0] = "remove"
        // args[1] = target
        // args[2] = optional type
        if (args.length < 2) {
            sendRemoveUsage(sender, fallbackType);
            return true;
        }

        String targetName = args[1];
        CosmeticType type = fallbackType;
        if (args.length >= 3) {
            CosmeticType parsed = CosmeticType.fromString(args[2]);
            if (parsed != null) {
                type = parsed;
            }
        }
        if (type == null) {
            type = CosmeticType.CHAT_COLOR;
        }

        final CosmeticType finalType = type;
        CosmeticManager cm = plugin.getCosmeticManager();

        resolvePlayerAsync(targetName).thenAccept(optPlayer -> {
            if (optPlayer.isEmpty()) {
                mm.sendMessage(sender, "player-not-found", "{PREFIX}&cPlayer &e{PLAYER} &cnot found.",
                        "{PLAYER}", targetName);
                return;
            }

            TargetProfile target = optPlayer.get();
            cm.removeCosmetic(target.uuid(), finalType).thenAccept(success -> {
                mm.sendMessage(sender, "cosmetics.removed",
                        "{PREFIX}&aRemoved &e{TYPE} &afrom &e{PLAYER}&a.",
                        "{TYPE}", finalType.getDisplayName(),
                        "{PLAYER}", target.name());

                Player online = Bukkit.getPlayer(target.uuid());
                if (online != null && online.isOnline()) {
                    mm.sendMessage(online, "cosmetics.target-removed",
                            "{PREFIX}&cYour &e{TYPE} &ccosmetic has been removed.",
                            "{TYPE}", finalType.getDisplayName());
                }
            });
        });

        return true;
    }

    public void sendGiveUsage(CommandSender sender, CosmeticType type) {
        MessageManager mm = plugin.getMessageManager();
        if (type == CosmeticType.CHAT_COLOR) {
            mm.sendMessage(sender, "cosmetics.usage-chatcolor",
                    "{PREFIX}&cUsage: /escore give <player> chatcolor <color> <perm|temp [duration]>");
        } else if (type == CosmeticType.PLAYER_GLOW) {
            mm.sendMessage(sender, "cosmetics.usage-playerglow",
                    "{PREFIX}&cUsage: /escore give <player> playerglow <color> <perm|temp [duration]>");
        } else {
            mm.sendMessage(sender, "cosmetics.usage-give",
                    "{PREFIX}&cUsage: /escore give <player> <chatcolor|playerglow> <color> <perm|temp [duration]>");
        }
    }

    public void sendRemoveUsage(CommandSender sender, CosmeticType type) {
        MessageManager mm = plugin.getMessageManager();
        if (type == CosmeticType.CHAT_COLOR) {
            mm.sendMessage(sender, "cosmetics.usage-remove-chatcolor",
                    "{PREFIX}&cUsage: /escore remove <player> chatcolor");
        } else if (type == CosmeticType.PLAYER_GLOW) {
            mm.sendMessage(sender, "cosmetics.usage-remove-playerglow",
                    "{PREFIX}&cUsage: /escore remove <player> playerglow");
        } else {
            mm.sendMessage(sender, "cosmetics.usage-remove",
                    "{PREFIX}&cUsage: /escore remove <player> <chatcolor|playerglow>");
        }
    }

    private String getAvailableColorsString(CosmeticType type) {
        CosmeticManager cm = plugin.getCosmeticManager();
        if (type == CosmeticType.CHAT_COLOR) {
            return String.join(", ", cm.getAvailableChatColors().stream().map(CosmeticColor::name).toList());
        } else {
            return String.join(", ", cm.getAvailableGlowColors());
        }
    }

    /**
     * Resolves a player by username asynchronously across online players, database, and OfflinePlayer.
     */
    public CompletableFuture<Optional<TargetProfile>> resolvePlayerAsync(String username) {
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new TargetProfile(online.getUniqueId(), online.getName())));
        }

        return CompletableFuture.supplyAsync(() -> {
            Optional<PlayerData> optData = plugin.getDatabaseManager().loadPlayerByName(username);
            if (optData.isPresent()) {
                PlayerData pd = optData.get();
                return Optional.of(new TargetProfile(pd.uuid(), pd.username()));
            }

            OfflinePlayer off = Bukkit.getOfflinePlayer(username);
            if (off.getName() != null) {
                return Optional.of(new TargetProfile(off.getUniqueId(), off.getName()));
            }

            return Optional.empty();
        });
    }

    // ========================================================
    // Tab Completion Helpers
    // ========================================================

    public List<String> onTabComplete(CommandSender sender, String[] args, CosmeticType fixedType) {
        if (!hasPermission(sender)) {
            return List.of();
        }

        // Subcommand completion: give, remove
        if (args.length == 1) {
            return List.of("give", "remove").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("give")) {
            return completeGive(args, fixedType);
        } else if (sub.equals("remove")) {
            return completeRemove(args, fixedType);
        }

        return List.of();
    }

    private List<String> completeGive(String[] args, CosmeticType fixedType) {
        // args[1] = player
        if (args.length == 2) {
            String current = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(current))
                    .toList();
        }

        if (fixedType != null) {
            // Fixed type (e.g. /cc give <player> ...)
            // args[2] could be "chatcolor" or a color directly
            if (args.length == 3) {
                List<String> list = new ArrayList<>();
                list.add("chatcolor");
                list.addAll(getColorsForType(fixedType));
                String current = args[2].toLowerCase();
                return list.stream().filter(s -> s.startsWith(current)).toList();
            }

            if (args[2].equalsIgnoreCase("chatcolor")) {
                if (args.length == 4) {
                    String current = args[3].toLowerCase();
                    return getColorsForType(fixedType).stream().filter(s -> s.startsWith(current)).toList();
                }
                if (args.length == 5) {
                    return List.of("perm", "temp").stream().filter(s -> s.startsWith(args[4].toLowerCase())).toList();
                }
                if (args.length == 6 && args[4].equalsIgnoreCase("temp")) {
                    return List.of("30m", "1h", "12h", "1d", "5d", "7d", "2w").stream()
                            .filter(s -> s.startsWith(args[5].toLowerCase())).toList();
                }
            } else {
                if (args.length == 4) {
                    return List.of("perm", "temp").stream().filter(s -> s.startsWith(args[3].toLowerCase())).toList();
                }
                if (args.length == 5 && args[3].equalsIgnoreCase("temp")) {
                    return List.of("30m", "1h", "12h", "1d", "5d", "7d", "2w").stream()
                            .filter(s -> s.startsWith(args[4].toLowerCase())).toList();
                }
            }
        } else {
            // General type (/escore give <player> ...)
            // args[2] = chatcolor | playerglow
            if (args.length == 3) {
                return List.of("chatcolor", "playerglow").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase())).toList();
            }
            CosmeticType type = CosmeticType.fromString(args[2]);
            if (type != null) {
                if (args.length == 4) {
                    String current = args[3].toLowerCase();
                    return getColorsForType(type).stream().filter(s -> s.startsWith(current)).toList();
                }
                if (args.length == 5) {
                    return List.of("perm", "temp").stream().filter(s -> s.startsWith(args[4].toLowerCase())).toList();
                }
                if (args.length == 6 && args[4].equalsIgnoreCase("temp")) {
                    return List.of("30m", "1h", "12h", "1d", "5d", "7d", "2w").stream()
                            .filter(s -> s.startsWith(args[5].toLowerCase())).toList();
                }
            }
        }

        return List.of();
    }

    private List<String> completeRemove(String[] args, CosmeticType fixedType) {
        if (args.length == 2) {
            String current = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(current))
                    .toList();
        }
        if (args.length == 3) {
            if (fixedType != null) {
                return List.of("chatcolor").stream().filter(s -> s.startsWith(args[2].toLowerCase())).toList();
            }
            return List.of("chatcolor", "playerglow").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase())).toList();
        }
        return List.of();
    }

    private List<String> getColorsForType(CosmeticType type) {
        CosmeticManager cm = plugin.getCosmeticManager();
        if (type == CosmeticType.CHAT_COLOR) {
            return cm.getAvailableChatColors().stream().map(CosmeticColor::name).toList();
        } else {
            return new ArrayList<>(cm.getAvailableGlowColors());
        }
    }

    public record TargetProfile(UUID uuid, String name) {}
}
