package fun.endcore.escoins.arena;

/**
 * Result details from an arena regeneration operation.
 */
public record RestoreResult(int modifiedBlocks, long totalBlocks, long elapsedMs) {
}
