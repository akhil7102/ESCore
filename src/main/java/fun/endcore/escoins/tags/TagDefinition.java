package fun.endcore.escoins.tags;

import fun.endcore.escoins.util.ColorUtil;

/**
 * Represents a registered player tag definition loaded from tags.yml.
 */
public record TagDefinition(
        String id,
        int order,
        String tag,
        String description,
        String permission
) {
    /**
     * Gets the fully translated tag string with hex and Minecraft color codes applied.
     */
    public String getFormatted() {
        return ColorUtil.translateHexAndLegacy(tag);
    }

    /**
     * Gets the fully translated description string.
     */
    public String getDescriptionFormatted() {
        return ColorUtil.translateHexAndLegacy(description);
    }
}
