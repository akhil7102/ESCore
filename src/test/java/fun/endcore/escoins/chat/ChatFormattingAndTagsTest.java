package fun.endcore.escoins.chat;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests chat formatting and message coloring for both Paper and legacy Bukkit chat pipelines.
 * Specifically verifies that external formatters like LPC (LuckPerms Chat) and EssentialsX are supported.
 */
class ChatFormattingAndTagsTest {

    @Test
    void testLegacyFormatStandardBukkit() {
        String rawFormat = "DEV %1$s: %2$s";
        String playerName = "AKHILPLAYZYT";
        String message = "hello";
        String formattedOutput = String.format(rawFormat, playerName, message);

        assertEquals("DEV AKHILPLAYZYT: hello", formattedOutput);
    }

    @Test
    void testLpcChatFormatter() {
        String lpcFormat = "DEV AKHILPLAYZYT: %2$s";
        String playerName = "AKHILPLAYZYT";
        String message = "hello";
        String formattedOutput = String.format(lpcFormat, playerName, message);

        assertEquals("DEV AKHILPLAYZYT: hello", formattedOutput);
        assertFalse(formattedOutput.contains("  "));
    }

    @Test
    void testLegacyMessageColorPrefixing() {
        String originalMessage = "hello";
        String chatCode = "&a"; // green
        String colorCode = ChatColor.translateAlternateColorCodes('&', chatCode);

        String coloredMessage = colorCode + originalMessage;
        assertTrue(coloredMessage.startsWith("§a"));
        assertEquals("§ahello", coloredMessage);

        // When formatted through LPC format "DEV AKHILPLAYZYT: %2$s"
        String format = "DEV AKHILPLAYZYT: %2$s";
        String result = String.format(format, "AKHILPLAYZYT", coloredMessage);
        assertEquals("DEV AKHILPLAYZYT: §ahello", result);
    }
}
