package fun.endcore.escoins.tags;

import fun.endcore.escoins.cosmetics.OwnershipType;

/**
 * Represents a player's tag ownership record with support for permanent or temporary duration.
 */
public record TagEntry(
        String tagId,
        OwnershipType ownershipType,
        Long expiresAt
) {
    public TagEntry(String tagId, OwnershipType ownershipType, Long expiresAt) {
        this.tagId = tagId != null ? tagId.trim().toUpperCase() : "";
        this.ownershipType = ownershipType != null ? ownershipType : OwnershipType.PERMANENT;
        this.expiresAt = expiresAt;
    }

    public TagEntry(String tagId) {
        this(tagId, OwnershipType.PERMANENT, null);
    }

    /**
     * Checks if this tag entitlement has expired.
     */
    public boolean isExpired() {
        if (ownershipType == OwnershipType.PERMANENT || expiresAt == null) {
            return false;
        }
        return System.currentTimeMillis() >= expiresAt;
    }

    /**
     * Gets remaining time in milliseconds. Returns Long.MAX_VALUE if permanent, 0 if expired.
     */
    public long getRemainingMillis() {
        if (ownershipType == OwnershipType.PERMANENT || expiresAt == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }
}
