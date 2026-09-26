package fun.endcore.escoins.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DurationParserTest {

    @Test
    void testBasicUnits() {
        assertEquals(30 * 1000L, DurationParser.parseToMillis("30s"));
        assertEquals(15 * 60 * 1000L, DurationParser.parseToMillis("15m"));
        assertEquals(24 * 3600 * 1000L, DurationParser.parseToMillis("24h"));
        assertEquals(5 * 86400 * 1000L, DurationParser.parseToMillis("5d"));
        assertEquals(2 * 7 * 86400 * 1000L, DurationParser.parseToMillis("2w"));
    }

    @Test
    void testCompoundUnits() {
        long expected = (86400 + 12 * 3600 + 30 * 60) * 1000L;
        assertEquals(expected, DurationParser.parseToMillis("1d12h30m"));
    }

    @Test
    void testInvalidFormats() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToMillis(""));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToMillis("invalid"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToMillis("5x"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToMillis("-5d"));
    }

    @Test
    void testFormatRemaining() {
        assertEquals("5d 12h", DurationParser.formatRemaining((5 * 86400 + 12 * 3600) * 1000L));
        assertEquals("2h 15m", DurationParser.formatRemaining((2 * 3600 + 15 * 60) * 1000L));
        assertEquals("30m 10s", DurationParser.formatRemaining((30 * 60 + 10) * 1000L));
        assertEquals("45s", DurationParser.formatRemaining(45 * 1000L));
        assertEquals("Expired", DurationParser.formatRemaining(0));
        assertEquals("Expired", DurationParser.formatRemaining(-1000L));
    }
}
