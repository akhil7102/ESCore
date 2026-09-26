package fun.endcore.escoins.util;

import fun.endcore.escoins.ESCoins;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles all message formatting, color translations, Adventure components,
 * action bars, and title dispatches.
 */
public class MessageManager {
    private final ESCoins plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private String prefix = "&8[&bES&fCoins&8]";

    public MessageManager(ESCoins plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration messages = plugin.getConfigManager().getMessages();
        this.prefix = messages.getString("prefix", "&8[&bES&fCoins&8]");
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Gets a message string from messages.yml with {PREFIX} replaced.
     */
    public String getRawMessage(String path, String def) {
        FileConfiguration messages = plugin.getConfigManager().getMessages();
        String msg = messages.getString(path, def);
        if (msg == null || msg.isEmpty()) {
            return "";
        }
        return msg.replace("{PREFIX}", prefix);
    }

    /**
     * Gets a list of strings from messages.yml with {PREFIX} replaced.
     */
    public List<String> getRawList(String path) {
        FileConfiguration messages = plugin.getConfigManager().getMessages();
        List<String> list = messages.getStringList(path);
        if (list.isEmpty()) {
            String single = messages.getString(path);
            if (single != null && !single.isEmpty()) {
                return List.of(single.replace("{PREFIX}", prefix));
            }
            return Collections.emptyList();
        }
        return list.stream().map(s -> s.replace("{PREFIX}", prefix)).toList();
    }

    /**
     * Converts a text string (supporting legacy &, hex &#RRGGBB, and MiniMessage) to an Adventure Component.
     */
    public Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // Replace hex &#RRGGBB with legacy section representation
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sb);
        String processed = sb.toString();

        // If string contains MiniMessage tags (<...>) and doesn't contain legacy codes (&), parse with MiniMessage
        if (processed.contains("<") && processed.contains(">") && !processed.contains("&")) {
            try {
                return miniMessage.deserialize(processed);
            } catch (Exception ignored) {
                // Fallback to legacy
            }
        }

        return legacySerializer.deserialize(processed);
    }

    /**
     * Sends a message to a sender with replacement mappings.
     * Replacements must be key-value pairs, e.g. "{PLAYER}", "Steve", "{AMOUNT}", "100".
     */
    public void sendMessage(CommandSender sender, String path, String def, String... replacements) {
        String message = getRawMessage(path, def);
        if (message.isEmpty()) {
            return;
        }

        message = applyReplacements(message, replacements);
        sender.sendMessage(parse(message));
    }

    /**
     * Sends a list or single message from config path to sender.
     */
    public void sendMessageList(CommandSender sender, String path, List<String> def, String... replacements) {
        List<String> list = getRawList(path);
        if (list.isEmpty()) {
            list = def;
        }
        for (String line : list) {
            String msg = applyReplacements(line, replacements);
            sender.sendMessage(parse(msg));
        }
    }

    /**
     * Broadcasts a message to all online players and console.
     */
    public void broadcastMessage(String path, String def, String... replacements) {
        String message = getRawMessage(path, def);
        if (message.isEmpty()) {
            return;
        }
        message = applyReplacements(message, replacements);
        Component component = parse(message);
        Bukkit.broadcast(component);
    }

    /**
     * Sends an action bar message to all online players.
     */
    public void broadcastActionBar(String text, String... replacements) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String formatted = text.replace("{PREFIX}", prefix);
        formatted = applyReplacements(formatted, replacements);
        Component component = parse(formatted);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(component);
        }
    }

    /**
     * Sends a Title to all online players.
     */
    public void broadcastTitle(String titleText, String subtitleText, int fadeInMs, int stayMs, int fadeOutMs, String... replacements) {
        String formattedTitle = titleText != null ? titleText.replace("{PREFIX}", prefix) : "";
        String formattedSubtitle = subtitleText != null ? subtitleText.replace("{PREFIX}", prefix) : "";

        formattedTitle = applyReplacements(formattedTitle, replacements);
        formattedSubtitle = applyReplacements(formattedSubtitle, replacements);

        Component titleComponent = parse(formattedTitle);
        Component subtitleComponent = parse(formattedSubtitle);

        Times times = Times.times(
                Duration.ofMillis(fadeInMs),
                Duration.ofMillis(stayMs),
                Duration.ofMillis(fadeOutMs)
        );
        Title title = Title.title(titleComponent, subtitleComponent, times);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
    }

    public static String applyReplacements(String text, String... replacements) {
        if (text == null || replacements == null || replacements.length < 2) {
            return text == null ? "" : text;
        }
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String key = replacements[i];
            String value = replacements[i + 1];
            if (key != null && value != null) {
                text = text.replace(key, value);
            }
        }
        return text;
    }
}
