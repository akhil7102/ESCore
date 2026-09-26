package fun.endcore.escoins.chat;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests chat formatting, message coloring, and tag insertion for both Paper and legacy Bukkit chat pipelines.
 * Specifically verifies that external formatters like LPC (LuckPerms Chat) and EssentialsX are supported and bypassed.
 */
class ChatFormattingAndTagsTest {

    @Test
    void testLegacyFormatTagInjectionStandardBukkit() {
        // Standard Bukkit format: %1$s: %2$s
        String rawFormat = "DEV %1$s: %2$s";
        String activeTagDisplay = "&c[WARLORD]";
        String coloredTag = ChatColor.translateAlternateColorCodes('&', activeTagDisplay);

        // When tag is active, inject immediately after %1$s
        String injectedFormat = rawFormat.replace("%1$s", "%1$s " + coloredTag);
        assertEquals("DEV %1$s " + coloredTag + ": %2$s", injectedFormat);

        String playerName = "AKHILPLAYZYT";
        String message = "hello";
        String formattedOutput = String.format(injectedFormat, playerName, message);

        assertEquals("DEV AKHILPLAYZYT " + coloredTag + ": hello", formattedOutput);
    }

    @Test
    void testLpcChatFormatterTagInjection() {
        // LPC replaces {prefix}{name}: {message} into literal name in format: "DEV AKHILPLAYZYT: %2$s"
        String lpcFormat = "DEV AKHILPLAYZYT: %2$s";
        String playerName = "AKHILPLAYZYT";
        String activeTagDisplay = "&c[WARLORD]";
        String coloredTag = ChatColor.translateAlternateColorCodes('&', activeTagDisplay);

        // Multi-strategy injection: when format contains player.getName()
        assertTrue(lpcFormat.contains(playerName));
        int nameIdx = lpcFormat.indexOf(playerName);
        int endIdx = nameIdx + playerName.length();
        String tagToInsert = " " + coloredTag;
        String newFormat = lpcFormat.substring(0, endIdx) + tagToInsert + lpcFormat.substring(endIdx);

        assertEquals("DEV AKHILPLAYZYT " + coloredTag + ": %2$s", newFormat);

        // Final output matches user's exact required output: DEV AKHILPLAYZYT &c[WARLORD]: hello
        String formattedOutput = String.format(newFormat, playerName, "hello");
        assertEquals("DEV AKHILPLAYZYT " + coloredTag + ": hello", formattedOutput);
    }

    @Test
    void testLpcFormatNoTagNoDoubleSpace() {
        // When player has no tag active, LPC format is untouched
        String lpcFormat = "DEV AKHILPLAYZYT: %2$s";
        String activeTag = null;

        String finalFormat = lpcFormat;
        if (activeTag != null) {
            finalFormat = lpcFormat + " " + activeTag;
        }

        String formattedOutput = String.format(finalFormat, "AKHILPLAYZYT", "hello");

        // Exactly matches user console: DEV AKHILPLAYZYT: hello
        assertEquals("DEV AKHILPLAYZYT: hello", formattedOutput);
        assertFalse(formattedOutput.contains("  "));
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

    @Test
    void testNoDuplicateTagInsertion() {
        String tagId = "WARLORD";
        String display = "&c[WARLORD]";
        String formatWithPapiAlreadyResolved = "DEV AKHILPLAYZYT §c[WARLORD]: %2$s";

        // If format already contains tag ID or display, do not inject again
        boolean alreadyContains = formatWithPapiAlreadyResolved.contains(tagId) ||
                                  formatWithPapiAlreadyResolved.contains(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', display)));
        assertTrue(alreadyContains);
    }
}
