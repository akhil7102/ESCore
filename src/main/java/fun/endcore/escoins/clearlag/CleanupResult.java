package fun.endcore.escoins.clearlag;

/**
 * Result of a single entity cleanup run.
 */
public record CleanupResult(
        int totalRemoved,
        int itemsRemoved,
        int mobsRemoved,
        int projectilesRemoved,
        int xpRemoved,
        long executionTimeMs
) {
    public static CleanupResult empty() {
        return new CleanupResult(0, 0, 0, 0, 0, 0);
    }
}
