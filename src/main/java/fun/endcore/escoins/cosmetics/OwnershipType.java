package fun.endcore.escoins.cosmetics;

/**
 * Entitlement ownership duration type.
 */
public enum OwnershipType {
    PERMANENT("perm"),
    TEMPORARY("temp");

    private final String alias;

    OwnershipType(String alias) {
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }

    public static OwnershipType fromString(String input) {
        if (input == null) return null;
        String clean = input.trim().toLowerCase();
        return switch (clean) {
            case "perm", "permanent", "p" -> PERMANENT;
            case "temp", "temporary", "t" -> TEMPORARY;
            default -> null;
        };
    }
}
