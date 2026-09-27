package fun.endcore.escoins.placeholder;

import fun.endcore.escoins.ESCoins;
import fun.endcore.escoins.api.LeaderboardEntry;
import fun.endcore.escoins.util.NumberFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PlaceholderAPI expansion for %escore_coins_*% placeholders.
 */
public class ESCoreCoinsExpansion extends PlaceholderExpansion {
    private final ESCoins plugin;
    private static final Pattern TOP_PATTERN = Pattern.compile("^coins_top_(\\d+)_(name|amount|ammount|amount_formatted|ammount_formatted)$", Pattern.CASE_INSENSITIVE);

    public ESCoreCoinsExpansion(ESCoins plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "escore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AKHILPLAYZYT";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String lower = params.toLowerCase();

        // %escore_coins_rank% (server-wide position like #1, #2, etc.)
        if (lower.equals("coins_rank") || lower.equals("rank")) {
            if (player == null) return "---";
            int rank = plugin.getCoinManager().getGlobalRank(player.getUniqueId());
            return rank > 0 ? "#" + rank : "---";
        }

        // %escore_coins_amount% or %escore_coins_balance%
        if (lower.equals("coins_amount") || lower.equals("coins_balance") || lower.equals("amount") || lower.equals("balance")) {
            if (player == null) return "0";
            return String.valueOf(plugin.getCoinManager().getBalance(player.getUniqueId()));
        }

        // %escore_coins_amount_formatted% or %escore_coins_balance_formatted%
        if (lower.equals("coins_amount_formatted") || lower.equals("coins_balance_formatted") || lower.equals("amount_formatted") || lower.equals("balance_formatted")) {
            if (player == null) return "0";
            long bal = plugin.getCoinManager().getBalance(player.getUniqueId());
            return NumberFormatter.format(bal);
        }

        // %escore_coins_top_<n>_<field>%
        Matcher matcher = TOP_PATTERN.matcher(params);
        if (matcher.matches()) {
            int rank;
            try {
                rank = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }

            LeaderboardEntry entry = plugin.getLeaderboard().getEntry(rank);
            if (entry == null) {
                return "---";
            }

            String field = matcher.group(2).toLowerCase();
            return switch (field) {
                case "name" -> entry.username();
                case "amount", "ammount" -> String.valueOf(entry.balance());
                case "amount_formatted", "ammount_formatted" -> NumberFormatter.format(entry.balance());
                default -> null;
            };
        }

        // %escore_chatcolor%
        if (lower.equals("chatcolor") || lower.equals("chat_color")) {
            if (player == null || plugin.getCosmeticManager() == null) return "none";
            return plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR)
                    .map(fun.endcore.escoins.cosmetics.CosmeticEntry::color)
                    .orElse("none");
        }

        // %escore_chatcolor_display%
        if (lower.equals("chatcolor_display") || lower.equals("chat_color_display")) {
            if (player == null || plugin.getCosmeticManager() == null) return "None";
            return plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR)
                    .map(entry -> {
                        fun.endcore.escoins.cosmetics.CosmeticColor cc = plugin.getCosmeticManager().getChatColor(entry.color());
                        return cc != null ? cc.displayName() : entry.color();
                    })
                    .orElse("None");
        }

        // %escore_chatcolor_time%
        if (lower.equals("chatcolor_time") || lower.equals("chat_color_time")) {
            if (player == null || plugin.getCosmeticManager() == null) return "None";
            return plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR)
                    .map(entry -> {
                        if (entry.ownershipType() == fun.endcore.escoins.cosmetics.OwnershipType.PERMANENT) return "Permanent";
                        return fun.endcore.escoins.util.DurationParser.formatRemaining(entry.getRemainingMillis());
                    })
                    .orElse("None");
        }

        // %escore_playerglow%
        if (lower.equals("playerglow") || lower.equals("player_glow") || lower.equals("glow")) {
            if (player == null || plugin.getCosmeticManager() == null) return "none";
            return plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.PLAYER_GLOW)
                    .map(fun.endcore.escoins.cosmetics.CosmeticEntry::color)
                    .orElse("none");
        }

        // %escore_playerglow_display%
        if (lower.equals("playerglow_display") || lower.equals("player_glow_display") || lower.equals("glow_display")) {
            if (player == null || plugin.getCosmeticManager() == null) return "None";
            return plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.PLAYER_GLOW)
                    .map(entry -> {
                        fun.endcore.escoins.cosmetics.CosmeticColor cc = plugin.getCosmeticManager().getGlowColor(entry.color());
                        return cc != null ? cc.displayName() : entry.color();
                    })
                    .orElse("None");
        }

        // %escore_playerglow_time%
        if (lower.equals("playerglow_time") || lower.equals("player_glow_time") || lower.equals("glow_time")) {
            if (player == null || plugin.getCosmeticManager() == null) return "None";
            return plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.PLAYER_GLOW)
                    .map(entry -> {
                        if (entry.ownershipType() == fun.endcore.escoins.cosmetics.OwnershipType.PERMANENT) return "Permanent";
                        return fun.endcore.escoins.util.DurationParser.formatRemaining(entry.getRemainingMillis());
                    })
                    .orElse("None");
        }

        // %escore_chatcolor_status% / %escore_chatcolor_enabled%
        if (lower.equals("chatcolor_status") || lower.equals("chat_color_status")) {
            if (player == null || plugin.getCosmeticManager() == null) return "None";
            return plugin.getCosmeticManager().getCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR)
                    .map(e -> e.active() ? "Enabled" : "Disabled")
                    .orElse("None");
        }
        if (lower.equals("chatcolor_enabled") || lower.equals("chat_color_enabled")) {
            if (player == null || plugin.getCosmeticManager() == null) return "false";
            return String.valueOf(plugin.getCosmeticManager().isCosmeticActive(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR));
        }

        // %escore_chatcolor_code% / %escore_chatcolor_code_raw%
        if (lower.equals("chatcolor_code") || lower.equals("chat_color_code")) {
            if (player == null || plugin.getCosmeticManager() == null) return "";
            return plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR)
                    .map(entry -> {
                        fun.endcore.escoins.cosmetics.CosmeticColor cc = plugin.getCosmeticManager().getChatColor(entry.color());
                        return cc != null ? cc.chatCode() : "";
                    })
                    .orElse("");
        }
        if (lower.equals("chatcolor_code_raw") || lower.equals("chat_color_code_raw")) {
            if (player == null || plugin.getCosmeticManager() == null) return "";
            return plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.CHAT_COLOR)
                    .map(entry -> {
                        fun.endcore.escoins.cosmetics.CosmeticColor cc = plugin.getCosmeticManager().getChatColor(entry.color());
                        return cc != null ? cc.chatCode().replace('&', '§') : "";
                    })
                    .orElse("");
        }

        // %escore_playerglow_status% / %escore_glow_status% / %escore_glow_enabled%
        if (lower.equals("playerglow_status") || lower.equals("player_glow_status") || lower.equals("glow_status")) {
            if (player == null || plugin.getCosmeticManager() == null) return "None";
            return plugin.getCosmeticManager().getCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.PLAYER_GLOW)
                    .map(e -> e.active() ? "Enabled" : "Disabled")
                    .orElse("None");
        }
        if (lower.equals("playerglow_enabled") || lower.equals("player_glow_enabled") || lower.equals("glow_enabled")) {
            if (player == null || plugin.getCosmeticManager() == null) return "false";
            return String.valueOf(plugin.getCosmeticManager().isCosmeticActive(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.PLAYER_GLOW));
        }

        // %escore_glow_code% / %escore_playerglow_code% / %escore_glowcolor_code%
        // Specifically used by TAB (groups.yml tagprefix: '%luckperms-prefix%%escore_glow_code%')
        if (lower.equals("glow_code") || lower.equals("playerglow_code") || lower.equals("player_glow_code") || lower.equals("glowcolor_code") || lower.equals("glow_color_code")) {
            if (player == null || plugin.getCosmeticManager() == null) return "";
            return plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.PLAYER_GLOW)
                    .map(entry -> {
                        fun.endcore.escoins.cosmetics.CosmeticColor cc = plugin.getCosmeticManager().getGlowColor(entry.color());
                        return cc != null ? cc.chatCode() : "";
                    })
                    .orElse("");
        }

        // %escore_glow_code_raw% / %escore_playerglow_code_raw% (with § section sign)
        if (lower.equals("glow_code_raw") || lower.equals("playerglow_code_raw") || lower.equals("player_glow_code_raw")) {
            if (player == null || plugin.getCosmeticManager() == null) return "";
            return plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.PLAYER_GLOW)
                    .map(entry -> {
                        fun.endcore.escoins.cosmetics.CosmeticColor cc = plugin.getCosmeticManager().getGlowColor(entry.color());
                        return cc != null ? cc.chatCode().replace('&', '§') : "";
                    })
                    .orElse("");
        }

        // %escore_glow_color% / %escore_playerglow_color%
        if (lower.equals("glow_color") || lower.equals("playerglow_color") || lower.equals("player_glow_color")) {
            if (player == null || plugin.getCosmeticManager() == null) return "none";
            return plugin.getCosmeticManager().getActiveCosmetic(player.getUniqueId(), fun.endcore.escoins.cosmetics.CosmeticType.PLAYER_GLOW)
                    .map(fun.endcore.escoins.cosmetics.CosmeticEntry::color)
                    .orElse("none");
        }

        return null;
    }
}
