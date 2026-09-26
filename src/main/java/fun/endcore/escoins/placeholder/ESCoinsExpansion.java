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
 * PlaceholderAPI expansion for %escoins_*% placeholders.
 */
public class ESCoinsExpansion extends PlaceholderExpansion {
    private final ESCoins plugin;
    private static final Pattern TOP_PATTERN = Pattern.compile("^top_(\\d+)_(name|amount|ammount|amount_formatted|ammount_formatted)$", Pattern.CASE_INSENSITIVE);

    public ESCoinsExpansion(ESCoins plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "escoins";
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

        // %escoins_rank% or %escoins_coins_rank% (server-wide position like #1, #2, etc.)
        if (lower.equals("rank") || lower.equals("coins_rank")) {
            if (player == null) return "---";
            int rank = plugin.getCoinManager().getGlobalRank(player.getUniqueId());
            return rank > 0 ? "#" + rank : "---";
        }

        // %escoins_amount% or %escoins_balance%
        if (lower.equals("amount") || lower.equals("balance") || lower.equals("coins_amount") || lower.equals("coins_balance")) {
            if (player == null) return "0";
            return String.valueOf(plugin.getCoinManager().getBalance(player.getUniqueId()));
        }

        // %escoins_amount_formatted% or %escoins_balance_formatted%
        if (lower.equals("amount_formatted") || lower.equals("balance_formatted") || lower.equals("coins_amount_formatted") || lower.equals("coins_balance_formatted")) {
            if (player == null) return "0";
            long bal = plugin.getCoinManager().getBalance(player.getUniqueId());
            return NumberFormatter.format(bal);
        }

        // %escoins_top_<n>_<field>%
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

        return null;
    }
}
