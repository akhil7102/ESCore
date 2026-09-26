package fun.endcore.escoins.cosmetics;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.database.PlayerData;
import fun.endcore.escoins.tags.TagDefinition;
import fun.endcore.escoins.tags.TagManager;
import fun.endcore.escoins.util.DurationParser;
import fun.endcore.escoins.util.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Handles administrative grant and remove command flows for:
 * - tags
 * - glow
 * - chatcolor
 *
 * Syntax: /escore give <player> <feature> <value> <duration>
 * Subcommand aliases supported for backwards compatibility.
 */
public class CosmeticCommandHandler {
    private final ESCoins plugin;

    public CosmeticCommandHandler(ESCoins plugin) {
        this.plugin = plugin;
    }

    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("escore.cosmetics.admin") ||
               sender.hasPermission("escore.tags.admin") ||
               sender.hasPermission("escoins.admin") ||
               sender.isOp();
    }

    /**
     * Handles /escore give <player> <feature> <value> <duration>
     */
    public boolean handleGive(CommandSender sender, String[] args, CosmeticType fallbackType) {
        MessageManager mm = plugin.getMessageManager();

        if (!hasPermission(sender)) {
            mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return true;
        }

        // Check argument progress and guide administrator at every step
        if (args.length < 2) {
            mm.sendMessage(sender, "escore.give.usage",
                    "{PREFIX}&cUsage: /escore give <player> <feature> <value> <duration>\n&7Next argument: &e<player> &7(Online or offline player name)");
            return true;
        }

        String targetName = args[1];

        // If shorthand command like /cc give <player> ... where feature is omitted
        String feature;
        int valueIndex;
        int durationTypeIndex;
        int durationValueIndex;

        if (fallbackType != null && args.length >= 3 && !isFeatureKeyword(args[2])) {
            // E.g. /cc give AKHILPLAYZYT green perm
            feature = fallbackType == CosmeticType.CHAT_COLOR ? "chatcolor" : "glow";
            valueIndex = 2;
            durationTypeIndex = 3;
            durationValueIndex = 4;
        } else {
            if (args.length < 3) {
                mm.sendMessage(sender, "escore.give.hint-feature",
                        "{PREFIX}&cUsage: /escore give " + targetName + " <feature> <value> <duration>\n&7Next argument: &e<feature> &7(Options: &atags&7, &aglow&7, &achatcolor&7)");
                return true;
            }
            feature = normalizeFeature(args[2]);
            valueIndex = 3;
            durationTypeIndex = 4;
            durationValueIndex = 5;
        }

        if (feature == null) {
            mm.sendMessage(sender, "escore.give.invalid-feature",
                    "{PREFIX}&cInvalid feature '&e{FEATURE}&c'. Valid features: &atags&c, &aglow&c, &achatcolor&c.",
                    "{FEATURE}", args[2]);
            return true;
        }

        // Validate value presence
        if (args.length <= valueIndex) {
            sendValueHint(sender, targetName, feature);
            return true;
        }

        String rawValue = args[valueIndex];

        // Validate value against feature options
        if (feature.equals("tags")) {
            TagManager tm = plugin.getTagManager();
            if (tm == null || !tm.isValidTag(rawValue)) {
                String available = tm != null ? String.join(", ", tm.getRegisteredTags().keySet()) : "None";
                mm.sendMessage(sender, "tags.not-found",
                        "{PREFIX}&cInvalid tag '&e{TAG}&c'. Available tags: &7{TAGS}",
                        "{TAG}", rawValue.toUpperCase(),
                        "{TAGS}", available);
                return true;
            }
        } else if (feature.equals("glow")) {
            CosmeticManager cm = plugin.getCosmeticManager();
            if (cm == null || !cm.isValidColor(CosmeticType.PLAYER_GLOW, rawValue)) {
                String available = cm != null ? String.join(", ", cm.getAvailableGlowColors()) : "None";
                mm.sendMessage(sender, "cosmetics.invalid-color",
                        "{PREFIX}&cInvalid glow color '&e{COLOR}&c'. Available: &7{COLORS}",
                        "{COLOR}", rawValue.toLowerCase(),
                        "{COLORS}", available);
                return true;
            }
        } else if (feature.equals("chatcolor")) {
            CosmeticManager cm = plugin.getCosmeticManager();
            if (cm == null || !cm.isValidColor(CosmeticType.CHAT_COLOR, rawValue)) {
                String available = cm != null ? String.join(", ", cm.getAvailableChatColors().stream().map(CosmeticColor::name).toList()) : "None";
                mm.sendMessage(sender, "cosmetics.invalid-color",
                        "{PREFIX}&cInvalid chat color '&e{COLOR}&c'. Available: &7{COLORS}",
                        "{COLOR}", rawValue.toLowerCase(),
                        "{COLORS}", available);
                return true;
            }
        }

        // Validate duration type presence
        if (args.length <= durationTypeIndex) {
            mm.sendMessage(sender, "escore.give.hint-duration-type",
                    "{PREFIX}&cUsage: /escore give " + targetName + " " + feature + " " + rawValue + " <duration>\n&7Next argument: &e<duration> &7(Options: &aperm &7or &atemp <duration>&7)");
            return true;
        }

        String durationTypeStr = args[durationTypeIndex].toLowerCase();
        OwnershipType ownership = OwnershipType.fromString(durationTypeStr);
        if (ownership == null) {
            mm.sendMessage(sender, "cosmetics.invalid-ownership",
                    "{PREFIX}&cInvalid duration type '&e{TYPE}&c'. Use &eperm &cor &etemp <duration>&c.",
                    "{TYPE}", durationTypeStr);
            return true;
        }

        long durationMillis = 0;
        Long expiresAt = null;
        if (ownership == OwnershipType.TEMPORARY) {
            if (args.length <= durationValueIndex) {
                mm.sendMessage(sender, "escore.give.hint-duration-value",
                        "{PREFIX}&cUsage: /escore give " + targetName + " " + feature + " " + rawValue + " temp <duration>\n&7Next argument: &e<duration> &7(Examples: &a1h&7, &a12h&7, &a1d&7, &a5d&7, &a7d&7, &a30d&7)");
                return true;
            }

            String durationStr = args[durationValueIndex];
            try {
                durationMillis = DurationParser.parseToMillis(durationStr);
                expiresAt = System.currentTimeMillis() + durationMillis;
            } catch (IllegalArgumentException e) {
                mm.sendMessage(sender, "cosmetics.invalid-duration",
                        "{PREFIX}&c{ERROR}",
                        "{ERROR}", e.getMessage());
                return true;
            }
        }

        final String finalFeature = feature;
        final String finalValue = feature.equals("tags") ? rawValue.toUpperCase() : rawValue.toLowerCase();
        final OwnershipType finalOwnership = ownership;
        final long finalDuration = durationMillis;
        final Long finalExpiresAt = expiresAt;
        final String finalDurationFormatted = ownership == OwnershipType.TEMPORARY ? DurationParser.formatRemaining(durationMillis) : "Permanent";

        if (plugin.isDebugCosmeticsEnabled()) {
            plugin.getLogger().info("[DEBUG Cosmetics] Parsed grant command: player=" + targetName +
                    ", feature=" + finalFeature + ", value=" + finalValue +
                    ", ownership=" + finalOwnership + ", duration=" + finalDurationFormatted);
        }

        // Resolve player asynchronously (supports online and offline players)
        resolvePlayerAsync(targetName).thenAccept(optPlayer -> {
            if (optPlayer.isEmpty()) {
                mm.sendMessage(sender, "player-not-found", "{PREFIX}&cPlayer &e{PLAYER} &cnot found.",
                        "{PLAYER}", targetName);
                return;
            }

            TargetProfile target = optPlayer.get();

            if (finalFeature.equals("tags")) {
                TagManager tm = plugin.getTagManager();
                TagDefinition def = tm.getTag(finalValue);
                String display = def != null ? def.display() : "[" + finalValue + "]";

                tm.giveTag(target.uuid(), finalValue, finalOwnership, finalExpiresAt).thenAccept(success -> {
                    if (success) {
                        if (finalOwnership == OwnershipType.PERMANENT) {
                            mm.sendMessage(sender, "tags.given-perm",
                                    "{PREFIX}&aGranted permanent tag &r{TAG_DISPLAY} &ato &e{PLAYER}&a.",
                                    "{TAG_DISPLAY}", display,
                                    "{TAG}", finalValue,
                                    "{PLAYER}", target.name());
                        } else {
                            mm.sendMessage(sender, "tags.given-temp",
                                    "{PREFIX}&aGranted temporary tag &r{TAG_DISPLAY} &ato &e{PLAYER} &afor &e{DURATION}&a.",
                                    "{TAG_DISPLAY}", display,
                                    "{TAG}", finalValue,
                                    "{PLAYER}", target.name(),
                                    "{DURATION}", finalDurationFormatted);
                        }

                        Player online = Bukkit.getPlayer(target.uuid());
                        if (online != null && online.isOnline()) {
                            if (finalOwnership == OwnershipType.PERMANENT) {
                                mm.sendMessage(online, "tags.received-perm",
                                        "{PREFIX}&aYou were granted permanent &r{TAG_DISPLAY} &atag! Use &e/tags select {TAG} &ato activate it.",
                                        "{TAG_DISPLAY}", display,
                                        "{TAG}", finalValue);
                            } else {
                                mm.sendMessage(online, "tags.received-temp",
                                        "{PREFIX}&aYou were granted temporary &r{TAG_DISPLAY} &atag for &e{DURATION}&a! Use &e/tags select {TAG} &ato activate it.",
                                        "{TAG_DISPLAY}", display,
                                        "{TAG}", finalValue,
                                        "{DURATION}", finalDurationFormatted);
                            }
                        }
                    } else {
                        mm.sendMessage(sender, "tags.error", "{PREFIX}&cFailed to grant tag.");
                    }
                });
            } else {
                CosmeticType cosmeticType = finalFeature.equals("glow") ? CosmeticType.PLAYER_GLOW : CosmeticType.CHAT_COLOR;
                CosmeticManager cm = plugin.getCosmeticManager();
                CompletableFuture<Boolean> future;

                if (finalOwnership == OwnershipType.PERMANENT) {
                    future = cm.givePermanentCosmetic(target.uuid(), cosmeticType, finalValue);
                } else {
                    future = cm.giveTemporaryCosmetic(target.uuid(), cosmeticType, finalValue, finalDuration);
                }

                future.thenAccept(success -> {
                    if (success) {
                        String featureLabel = cosmeticType.getDisplayName();
                        if (finalOwnership == OwnershipType.PERMANENT) {
                            mm.sendMessage(sender, "cosmetics.given-perm",
                                    "{PREFIX}&aGranted permanent &e{COLOR} {TYPE} &ato &e{PLAYER}&a.",
                                    "{COLOR}", finalValue,
                                    "{TYPE}", featureLabel,
                                    "{PLAYER}", target.name());
                        } else {
                            mm.sendMessage(sender, "cosmetics.given-temp",
                                    "{PREFIX}&aGranted temporary &e{COLOR} {TYPE} &ato &e{PLAYER} &afor &e{DURATION}&a.",
                                    "{COLOR}", finalValue,
                                    "{TYPE}", featureLabel,
                                    "{PLAYER}", target.name(),
                                    "{DURATION}", finalDurationFormatted);
                        }

                        Player online = Bukkit.getPlayer(target.uuid());
                        if (online != null && online.isOnline()) {
                            if (finalOwnership == OwnershipType.PERMANENT) {
                                mm.sendMessage(online, "cosmetics.received-perm",
                                        "{PREFIX}&aYou received permanent &e{COLOR} {TYPE}&a!",
                                        "{COLOR}", finalValue,
                                        "{TYPE}", featureLabel);
                            } else {
                                mm.sendMessage(online, "cosmetics.received-temp",
                                        "{PREFIX}&aYou received temporary &e{COLOR} {TYPE} &afor &e{DURATION}&a!",
                                        "{COLOR}", finalValue,
                                        "{TYPE}", featureLabel,
                                        "{DURATION}", finalDurationFormatted);
                            }
                        }
                    } else {
                        mm.sendMessage(sender, "cosmetics.error", "{PREFIX}&cFailed to save cosmetic entitlement.");
                    }
                });
            }
        });

        return true;
    }

    private void sendValueHint(CommandSender sender, String player, String feature) {
        MessageManager mm = plugin.getMessageManager();
        if (feature.equals("tags")) {
            String available = plugin.getTagManager() != null ? String.join(", ", plugin.getTagManager().getRegisteredTags().keySet()) : "None";
            mm.sendMessage(sender, "escore.give.hint-tag",
                    "{PREFIX}&cUsage: /escore give " + player + " tags <tag> <duration>\n&7Next argument: &e<tag> &7(Available tags: &f" + available + "&7)");
        } else if (feature.equals("glow")) {
            String available = plugin.getCosmeticManager() != null ? String.join(", ", plugin.getCosmeticManager().getAvailableGlowColors()) : "None";
            mm.sendMessage(sender, "escore.give.hint-glow",
                    "{PREFIX}&cUsage: /escore give " + player + " glow <color> <duration>\n&7Next argument: &e<color> &7(Available glow colors: &f" + available + "&7)");
        } else {
            String available = plugin.getCosmeticManager() != null ? String.join(", ", plugin.getCosmeticManager().getAvailableChatColors().stream().map(CosmeticColor::name).toList()) : "None";
            mm.sendMessage(sender, "escore.give.hint-chatcolor",
                    "{PREFIX}&cUsage: /escore give " + player + " chatcolor <color> <duration>\n&7Next argument: &e<color> &7(Available chat colors: &f" + available + "&7)");
        }
    }

    /**
     * Handles /escore remove <player> <feature> [value]
     */
    public boolean handleRemove(CommandSender sender, String[] args, CosmeticType fallbackType) {
        MessageManager mm = plugin.getMessageManager();

        if (!hasPermission(sender)) {
            mm.sendMessage(sender, "no-permission", "{PREFIX}&cYou do not have permission to execute this command.");
            return true;
        }

        if (args.length < 2) {
            mm.sendMessage(sender, "cosmetics.usage-remove",
                    "{PREFIX}&cUsage: /escore remove <player> <tags|glow|chatcolor> [tag]");
            return true;
        }

        String targetName = args[1];
        String feature;
        String optionalValue = null;

        if (fallbackType != null && (args.length < 3 || !isFeatureKeyword(args[2]))) {
            feature = fallbackType == CosmeticType.CHAT_COLOR ? "chatcolor" : "glow";
            if (args.length >= 3) {
                optionalValue = args[2];
            }
        } else {
            if (args.length < 3) {
                mm.sendMessage(sender, "cosmetics.usage-remove",
                        "{PREFIX}&cUsage: /escore remove " + targetName + " <tags|glow|chatcolor> [tag]");
                return true;
            }
            feature = normalizeFeature(args[2]);
            if (args.length >= 4) {
                optionalValue = args[3];
            }
        }

        if (feature == null) {
            mm.sendMessage(sender, "cosmetics.usage-remove",
                    "{PREFIX}&cUsage: /escore remove " + targetName + " <tags|glow|chatcolor> [tag]");
            return true;
        }

        final String finalFeature = feature;
        final String finalValue = optionalValue;

        resolvePlayerAsync(targetName).thenAccept(optPlayer -> {
            if (optPlayer.isEmpty()) {
                mm.sendMessage(sender, "player-not-found", "{PREFIX}&cPlayer &e{PLAYER} &cnot found.",
                        "{PLAYER}", targetName);
                return;
            }

            TargetProfile target = optPlayer.get();

            if (finalFeature.equals("tags")) {
                TagManager tm = plugin.getTagManager();
                if (finalValue != null && !finalValue.trim().isEmpty()) {
                    String tagId = finalValue.toUpperCase();
                    tm.removeTag(target.uuid(), tagId).thenAccept(success -> {
                        mm.sendMessage(sender, "tags.admin.removed",
                                "{PREFIX}&aSuccessfully removed tag &e{TAG} &afrom &e{PLAYER}&a.",
                                "{TAG_DISPLAY}", tagId,
                                "{TAG}", tagId,
                                "{PLAYER}", target.name());
                        Player online = Bukkit.getPlayer(target.uuid());
                        if (online != null && online.isOnline()) {
                            mm.sendMessage(online, "tags.revoked",
                                    "{PREFIX}&cThe tag &e{TAG} &cwas removed from your account.",
                                    "{TAG_DISPLAY}", tagId,
                                    "{TAG}", tagId);
                        }
                    });
                } else {
                    tm.clearTags(target.uuid()).thenAccept(success -> {
                        mm.sendMessage(sender, "tags.admin.cleared",
                                "{PREFIX}&aSuccessfully cleared all tags from &e{PLAYER}&a.",
                                "{PLAYER}", target.name());
                        Player online = Bukkit.getPlayer(target.uuid());
                        if (online != null && online.isOnline()) {
                            mm.sendMessage(online, "tags.all-cleared", "{PREFIX}&cAll your tags have been cleared.");
                        }
                    });
                }
            } else {
                CosmeticType cosmeticType = finalFeature.equals("glow") ? CosmeticType.PLAYER_GLOW : CosmeticType.CHAT_COLOR;
                CosmeticManager cm = plugin.getCosmeticManager();
                cm.removeCosmetic(target.uuid(), cosmeticType).thenAccept(success -> {
                    mm.sendMessage(sender, "cosmetics.removed",
                            "{PREFIX}&aRemoved &e{TYPE} &afrom &e{PLAYER}&a.",
                            "{TYPE}", cosmeticType.getDisplayName(),
                            "{PLAYER}", target.name());

                    Player online = Bukkit.getPlayer(target.uuid());
                    if (online != null && online.isOnline()) {
                        mm.sendMessage(online, "cosmetics.target-removed",
                                "{PREFIX}&cYour &e{TYPE} &ccosmetic has been removed.",
                                "{TYPE}", cosmeticType.getDisplayName());
                    }
                });
            }
        });

        return true;
    }

    private boolean isFeatureKeyword(String str) {
        if (str == null) return false;
        String lower = str.toLowerCase();
        return lower.equals("tags") || lower.equals("tag") ||
               lower.equals("glow") || lower.equals("playerglow") ||
               lower.equals("chatcolor") || lower.equals("color");
    }

    private String normalizeFeature(String str) {
        if (str == null) return null;
        String lower = str.toLowerCase();
        if (lower.equals("tags") || lower.equals("tag")) return "tags";
        if (lower.equals("glow") || lower.equals("playerglow")) return "glow";
        if (lower.equals("chatcolor") || lower.equals("color")) return "chatcolor";
        return null;
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

            // Fallback for offline player
            if (off.getUniqueId() != null) {
                return Optional.of(new TargetProfile(off.getUniqueId(), username));
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

        if (args.length == 1) {
            return List.of("give", "remove").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("give")) {
            return completeGive(args, fixedType);
        } else if (sub.equals("remove") || sub.equals("take") || sub.equals("clear")) {
            return completeRemove(args, fixedType);
        }

        return List.of();
    }

    private List<String> completeGive(String[] args, CosmeticType fixedType) {
        // 1. /escore give <TAB> -> Online player names
        if (args.length == 2) {
            String current = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(current))
                    .toList();
        }

        if (fixedType != null) {
            // E.g. /cc give <player> <TAB>
            if (args.length == 3) {
                List<String> list = new ArrayList<>();
                list.add("chatcolor");
                list.addAll(getColorsForFeature("chatcolor"));
                return list.stream().filter(s -> s.startsWith(args[2].toLowerCase())).toList();
            }
            if (args[2].equalsIgnoreCase("chatcolor")) {
                if (args.length == 4) {
                    return getColorsForFeature("chatcolor").stream().filter(s -> s.startsWith(args[3].toLowerCase())).toList();
                }
                if (args.length == 5) {
                    return List.of("perm", "temp").stream().filter(s -> s.startsWith(args[4].toLowerCase())).toList();
                }
                if (args.length == 6 && args[4].equalsIgnoreCase("temp")) {
                    return List.of("1h", "2h", "1d", "5d", "7d", "30d").stream().filter(s -> s.startsWith(args[5].toLowerCase())).toList();
                }
            } else {
                if (args.length == 4) {
                    return List.of("perm", "temp").stream().filter(s -> s.startsWith(args[3].toLowerCase())).toList();
                }
                if (args.length == 5 && args[3].equalsIgnoreCase("temp")) {
                    return List.of("1h", "2h", "1d", "5d", "7d", "30d").stream().filter(s -> s.startsWith(args[4].toLowerCase())).toList();
                }
            }
            return List.of();
        }

        // Standard /escore give <player> ...
        // 2. /escore give <player> <TAB> -> tags, glow, chatcolor
        if (args.length == 3) {
            return List.of("tags", "glow", "chatcolor").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .toList();
        }

        String feature = normalizeFeature(args[2]);
        if (feature != null) {
            // 3. /escore give <player> <feature> <TAB> -> Values (tag IDs or colors)
            if (args.length == 4) {
                String current = args[3].toLowerCase();
                return getColorsForFeature(feature).stream()
                        .filter(s -> s.toLowerCase().startsWith(current))
                        .toList();
            }

            // 4. /escore give <player> <feature> <value> <TAB> -> perm, temp
            if (args.length == 5) {
                return List.of("perm", "temp").stream()
                        .filter(s -> s.startsWith(args[4].toLowerCase()))
                        .toList();
            }

            // 5. /escore give <player> <feature> <value> temp <TAB> -> duration examples
            if (args.length == 6 && args[4].equalsIgnoreCase("temp")) {
                return List.of("1h", "2h", "1d", "5d", "7d", "30d").stream()
                        .filter(s -> s.startsWith(args[5].toLowerCase()))
                        .toList();
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
            return List.of("tags", "glow", "chatcolor").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .toList();
        }
        if (args.length == 4 && (args[2].equalsIgnoreCase("tags") || args[2].equalsIgnoreCase("tag"))) {
            Player target = Bukkit.getPlayerExact(args[1]);
            String current = args[3].toLowerCase();
            if (target != null && plugin.getTagManager() != null) {
                return plugin.getTagManager().getPlayerTags(target.getUniqueId()).getOwnedTags().stream()
                        .filter(k -> k.toLowerCase().startsWith(current))
                        .toList();
            }
            if (plugin.getTagManager() != null) {
                return plugin.getTagManager().getRegisteredTags().keySet().stream()
                        .filter(k -> k.toLowerCase().startsWith(current))
                        .toList();
            }
        }
        return List.of();
    }

    private List<String> getColorsForFeature(String feature) {
        if (feature.equals("tags")) {
            if (plugin.getTagManager() == null) return List.of();
            return new ArrayList<>(plugin.getTagManager().getRegisteredTags().keySet());
        } else if (feature.equals("glow")) {
            if (plugin.getCosmeticManager() == null) return List.of();
            return new ArrayList<>(plugin.getCosmeticManager().getAvailableGlowColors());
        } else if (feature.equals("chatcolor")) {
            if (plugin.getCosmeticManager() == null) return List.of();
            return plugin.getCosmeticManager().getAvailableChatColors().stream().map(CosmeticColor::name).toList();
        }
        return List.of();
    }

    public record TargetProfile(UUID uuid, String name) {}
}
