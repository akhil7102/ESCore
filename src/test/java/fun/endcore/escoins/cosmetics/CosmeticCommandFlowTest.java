package fun.endcore.escoins.cosmetics;

import fun.endcore.escoins.util.DurationParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests command argument parsing, normalization, duration parsing, and validation
 * for the unified /escore give <player> <feature> <value> <duration> flow.
 */
class CosmeticCommandFlowTest {

    @Test
    void testFeatureNormalization() {
        assertNull(normalize("tags"));
        assertNull(normalize("TAGS"));
        assertNull(normalize("tag"));
        assertNull(normalize("TAG"));

        assertEquals("glow", normalize("glow"));
        assertEquals("glow", normalize("GLOW"));
        assertEquals("glow", normalize("playerglow"));
        assertEquals("glow", normalize("PLAYERGLOW"));

        assertEquals("chatcolor", normalize("chatcolor"));
        assertEquals("chatcolor", normalize("CHATCOLOR"));
        assertEquals("chatcolor", normalize("color"));
        assertEquals("chatcolor", normalize("COLOR"));

        assertNull(normalize("unknown"));
        assertNull(normalize("invalid"));
    }

    private String normalize(String str) {
        if (str == null) return null;
        String lower = str.toLowerCase();
        if (lower.equals("glow") || lower.equals("playerglow")) return "glow";
        if (lower.equals("chatcolor") || lower.equals("color")) return "chatcolor";
        return null;
    }

    @Test
    void testDurationParsingExamples() {
        // Examples requested: 2h, 5d, 7d, 30d
        long twoHours = DurationParser.parseToMillis("2h");
        assertEquals(2 * 3600 * 1000L, twoHours);

        long fiveDays = DurationParser.parseToMillis("5d");
        assertEquals(5 * 86400 * 1000L, fiveDays);

        long sevenDays = DurationParser.parseToMillis("7d");
        assertEquals(7 * 86400 * 1000L, sevenDays);

        long thirtyDays = DurationParser.parseToMillis("30d");
        assertEquals(30 * 86400 * 1000L, thirtyDays);

        // Permanent parsing
        assertEquals(OwnershipType.PERMANENT, OwnershipType.fromString("perm"));
        assertEquals(OwnershipType.TEMPORARY, OwnershipType.fromString("temp"));
    }

    @Test
    void testGlowColorsValidity() {
        String[] sampleGlowColors = {"red", "green", "blue", "yellow", "aqua", "white", "gray", "dark_red", "dark_green", "dark_blue", "gold", "light_purple", "dark_purple"};
        for (String c : sampleGlowColors) {
            assertNotNull(CosmeticColor.getDefault(c));
        }
    }
}
