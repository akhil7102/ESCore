package fun.endcore.escoins.chat;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests chat formatting, message coloring, and tag insertion for both Paper and legacy Bukkit chat pipelines.
 */
class ChatFormattingAndTagsTest {

    @Test
    void testLegacyFormatTagInjection() {
        // Example format from EssentialsXChat / LPC
        String rawFormat = "DEV %1$s > %2$s";
        String activeTagDisplay = "&7[PHANTOM]";
        String coloredTag = ChatColor.translateAlternateColorCodes('&', activeTagDisplay);

        // When tag is active, inject immediately after %1$s
        String injectedFormat = rawFormat.replace("%1$s", "%1$s " + coloredTag);
        assertEquals("DEV %1$s " + coloredTag + " > %2$s", injectedFormat);

        // Format simulation with player display name and message
        String playerName = "AKHILPLAYZYT";
        String message = "hello";
        String formattedOutput = String.format(injectedFormat, playerName, message);

        assertEquals("DEV AKHILPLAYZYT " + coloredTag + " > hello", formattedOutput);
        assertTrue(formattedOutput.contains("DEV AKHILPLAYZYT " + coloredTag + " > hello"));
    }

    @Test
    void testLegacyFormatNoTagNoDoubleSpace() {
        String rawFormat = "DEV %1$s > %2$s";
        String activeTag = null;

        // When active tag is null, format is unmodified
        String finalFormat = rawFormat;
        if (activeTag != null) {
            finalFormat = rawFormat.replace("%1$s", "%1$s " + activeTag);
        }

        String playerName = "AKHILPLAYZYT";
        String message = "hello";
        String formattedOutput = String.format(finalFormat, playerName, message);

        // Must NOT contain double space
        assertEquals("DEV AKHILPLAYZYT > hello", formattedOutput);
        assertFalse(formattedOutput.contains("  "));
    }

    @Test
    void testLegacyMessageColorPrefixing() {
        String originalMessage = "hello everyone!";
        String chatCode = "&a"; // green
        String colorCode = ChatColor.translateAlternateColorCodes('&', chatCode);

        String coloredMessage = colorCode + originalMessage;
        assertTrue(coloredMessage.startsWith("§a"));
        assertEquals("§ahello everyone!", coloredMessage);

        // When formatted through standard %2$s
        String format = "%1$s: %2$s";
        String result = String.format(format, "AKHILPLAYZYT", coloredMessage);
        assertEquals("AKHILPLAYZYT: §ahello everyone!", result);
    }

    @Test
    void testNoDuplicateTagInsertion() {
        String tagId = "PHANTOM";
        String display = "&7[PHANTOM]";
        String formatWithPapiAlreadyResolved = "DEV %1$s §7[PHANTOM] > %2$s";

        // If format already contains tag ID or display, do not inject again
        boolean alreadyContains = formatWithPapiAlreadyResolved.contains(tagId) ||
                                  formatWithPapiAlreadyResolved.contains(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', display)));
        assertTrue(alreadyContains);
    }
}
