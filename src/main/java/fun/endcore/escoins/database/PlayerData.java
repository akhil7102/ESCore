package fun.endcore.escoins.database;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Encapsulates persistent player database record.
 */
public record PlayerData(
        UUID uuid,
        String username,
        long balance,
        Timestamp createdAt,
        Timestamp updatedAt
) {}
