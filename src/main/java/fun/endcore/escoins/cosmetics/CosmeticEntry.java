package fun.endcore.escoins.cosmetics;

import java.util.UUID;

/**
 * Represents a player's cosmetic entitlement record (permanent or temporary).
 */
public record CosmeticEntry(
        UUID playerUuid,
        CosmeticType type,
        String color,
        OwnershipType ownershipType,
        Long expiresAt,
        long createdAt
) {
    public CosmeticEntry(UUID playerUuid, CosmeticType type, String color, OwnershipType ownershipType, Long expiresAt) {
        this(playerUuid, type, color, ownershipType, expiresAt, System.currentTimeMillis());
    }

    /**
     * Checks if this entitlement has expired.
     */
    public boolean isExpired() {
        if (ownershipType == OwnershipType.PERMANENT || expiresAt == null) {
            return false;
        }
        return System.currentTimeMillis() >= expiresAt;
    }

    /**
     * Gets the remaining time in milliseconds. Returns Long.MAX_VALUE if permanent, or 0 if expired.
     */
    public long getRemainingMillis() {
        if (ownershipType == OwnershipType.PERMANENT || expiresAt == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }
}
