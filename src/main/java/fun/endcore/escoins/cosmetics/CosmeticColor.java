package fun.endcore.escoins.cosmetics;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;

import java.util.*;

/**
 * Represents a cosmetic color definition with Adventure NamedTextColor, Bukkit ChatColor, and formatting codes.
 */
public record CosmeticColor(
        String name,
        String displayName,
        String chatCode,
        NamedTextColor namedTextColor,
        ChatColor bukkitColor
) {
    private static final Map<String, CosmeticColor> DEFAULTS = new LinkedHashMap<>();

    static {
        registerDefault("red", "&cRed", "&c", NamedTextColor.RED, ChatColor.RED);
        registerDefault("green", "&aGreen", "&a", NamedTextColor.GREEN, ChatColor.GREEN);
        registerDefault("blue", "&9Blue", "&9", NamedTextColor.BLUE, ChatColor.BLUE);
        registerDefault("yellow", "&eYellow", "&e", NamedTextColor.YELLOW, ChatColor.YELLOW);
        registerDefault("aqua", "&bAqua", "&b", NamedTextColor.AQUA, ChatColor.AQUA);
        registerDefault("white", "&fWhite", "&f", NamedTextColor.WHITE, ChatColor.WHITE);
        registerDefault("gray", "&7Gray", "&7", NamedTextColor.GRAY, ChatColor.GRAY);
        registerDefault("dark_red", "&4Dark Red", "&4", NamedTextColor.DARK_RED, ChatColor.DARK_RED);
        registerDefault("dark_green", "&2Dark Green", "&2", NamedTextColor.DARK_GREEN, ChatColor.DARK_GREEN);
        registerDefault("dark_blue", "&1Dark Blue", "&1", NamedTextColor.DARK_BLUE, ChatColor.DARK_BLUE);
        registerDefault("gold", "&6Gold", "&6", NamedTextColor.GOLD, ChatColor.GOLD);
        registerDefault("light_purple", "&dLight Purple", "&d", NamedTextColor.LIGHT_PURPLE, ChatColor.LIGHT_PURPLE);
        registerDefault("dark_purple", "&5Dark Purple", "&5", NamedTextColor.DARK_PURPLE, ChatColor.DARK_PURPLE);
    }

    private static void registerDefault(String name, String display, String code, NamedTextColor named, ChatColor bukkit) {
        DEFAULTS.put(name.toLowerCase(), new CosmeticColor(name.toLowerCase(), display, code, named, bukkit));
    }

    public static Map<String, CosmeticColor> getDefaults() {
        return Collections.unmodifiableMap(DEFAULTS);
    }

    public static CosmeticColor getDefault(String name) {
        if (name == null) return null;
        return DEFAULTS.get(name.toLowerCase());
    }
}
