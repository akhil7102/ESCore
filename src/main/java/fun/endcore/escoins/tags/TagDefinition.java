package fun.endcore.escoins.tags;

/**
 * Represents a configured tag definition with an identifier and formatted display string.
 */
public record TagDefinition(String id, String display) {
    public TagDefinition {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Tag ID cannot be null or empty.");
        }
        id = id.trim().toUpperCase();
        if (display == null) {
            display = "[" + id + "]";
        }
    }
}
