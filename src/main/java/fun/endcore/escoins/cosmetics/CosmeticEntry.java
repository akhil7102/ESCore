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
        boolean active,
        long createdAt
) {
    public CosmeticEntry(UUID playerUuid, CosmeticType type, String color, OwnershipType ownershipType, Long expiresAt) {
        this(playerUuid, type, color, ownershipType, expiresAt, true, System.currentTimeMillis());
    }

    public CosmeticEntry(UUID playerUuid, CosmeticType type, String color, OwnershipType ownershipType, Long expiresAt, long createdAt) {
        this(playerUuid, type, color, ownershipType, expiresAt, true, createdAt);
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
     * Checks if this cosmetic is currently active (enabled by the player and not expired).
     */
    public boolean isActive() {
        return active && !isExpired();
    }

    /**
     * Returns a copy of this entry with the specified active status.
     */
    public CosmeticEntry withActive(boolean newActive) {
        return new CosmeticEntry(playerUuid, type, color, ownershipType, expiresAt, newActive, createdAt);
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
