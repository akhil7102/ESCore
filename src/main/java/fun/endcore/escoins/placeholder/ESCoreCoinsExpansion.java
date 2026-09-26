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

        // ========================================================
        // Player Tags Placeholders
        // ========================================================

        // %escore_tag% or %escore_tag_display%
        if (lower.equals("tag") || lower.equals("tag_display") || lower.equals("active_tag") || lower.equals("tag_active")) {
            if (player == null || plugin.getTagManager() == null) return "";
            String display = plugin.getTagManager().getActiveTagDisplay(player.getUniqueId());
            return display != null ? display : "";
        }

        // %escore_tag_raw% or %escore_tag_name% or %escore_tag_id%
        if (lower.equals("tag_raw") || lower.equals("tag_name") || lower.equals("tag_id")) {
            if (player == null || plugin.getTagManager() == null) return "";
            String active = plugin.getTagManager().getActiveTag(player.getUniqueId());
            return active != null ? active : "";
        }

        // %escore_tags_count% or %escore_tags_owned%
        if (lower.equals("tags_count") || lower.equals("tags_owned") || lower.equals("tag_count")) {
            if (player == null || plugin.getTagManager() == null) return "0";
            return String.valueOf(plugin.getTagManager().getPlayerTags(player.getUniqueId()).getOwnedTags().size());
        }

        return null;
    }
}
