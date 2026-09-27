package fun.endcore.escoins.cosmetics;

/**
 * Types of cosmetic entitlements supported by ESCore.
 */
public enum CosmeticType {
    CHAT_COLOR("chatcolor", "Chat Color"),
    PLAYER_GLOW("playerglow", "Player Glow"),
    TAG("tag", "Tag");

    private final String id;
    private final String displayName;

    CosmeticType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Resolves a CosmeticType from a string input (case-insensitive, allows hyphen/underscore variants).
     */
    public static CosmeticType fromString(String input) {
        if (input == null) return null;
        String clean = input.trim().toLowerCase().replace("-", "").replace("_", "");
        return switch (clean) {
            case "chatcolor", "chat", "cc", "color" -> CHAT_COLOR;
            case "playerglow", "glow", "pg" -> PLAYER_GLOW;
            case "tag", "tags", "playertag", "playertags" -> TAG;
            default -> null;
        };
    }
}
