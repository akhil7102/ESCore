package fun.endcore.escoins.economy;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Represents a single financial transaction record for audit trails and history.
 */
public record TransactionRecord(
        long transactionId,
        UUID playerUuid,
        TransactionType type,
        long amount,
        long balanceBefore,
        long balanceAfter,
        String source,
        UUID targetUuid,
        Timestamp timestamp
) {
    public TransactionRecord(UUID playerUuid, TransactionType type, long amount, long balanceBefore, long balanceAfter, String source, UUID targetUuid) {
        this(0, playerUuid, type, amount, balanceBefore, balanceAfter, source, targetUuid, new Timestamp(System.currentTimeMillis()));
    }
}
