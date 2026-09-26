package fun.endcore.escoins.api;

import java.util.UUID;

/**
 * Represents a single ranking entry in the coin leaderboard.
 */
public record LeaderboardEntry(
        int rank,
        UUID uuid,
        String username,
        long balance
) {
    public LeaderboardEntry withRank(int newRank) {
        return new LeaderboardEntry(newRank, this.uuid, this.username, this.balance);
    }
}
