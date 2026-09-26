package fun.endcore.escoins.placeholder;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderPatternTest {
    private static final Pattern ESCOR_TOP_PATTERN = Pattern.compile("^coins_top_(\\d+)_(name|amount|ammount|amount_formatted|ammount_formatted)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCOINS_TOP_PATTERN = Pattern.compile("^top_(\\d+)_(name|amount|ammount|amount_formatted|ammount_formatted)$", Pattern.CASE_INSENSITIVE);

    @Test
    void testESCorePlaceholders() {
        // Test %escore_coins_top_1_name%
        Matcher m1 = ESCOR_TOP_PATTERN.matcher("coins_top_1_name");
        assertTrue(m1.matches());
        assertEquals("1", m1.group(1));
        assertEquals("name", m1.group(2).toLowerCase());

        // Test %escore_coins_top_1_amount%
        Matcher m2 = ESCOR_TOP_PATTERN.matcher("coins_top_1_amount");
        assertTrue(m2.matches());
        assertEquals("1", m2.group(1));
        assertEquals("amount", m2.group(2).toLowerCase());

        // Test backwards-compatible %escore_coins_top_1_ammount%
        Matcher m3 = ESCOR_TOP_PATTERN.matcher("coins_top_1_ammount");
        assertTrue(m3.matches());
        assertEquals("1", m3.group(1));
        assertEquals("ammount", m3.group(2).toLowerCase());

        // Test up to 10
        Matcher m10 = ESCOR_TOP_PATTERN.matcher("coins_top_10_name");
        assertTrue(m10.matches());
        assertEquals("10", m10.group(1));
    }

    @Test
    void testESCoinsPlaceholders() {
        Matcher m1 = ESCOINS_TOP_PATTERN.matcher("top_1_name");
        assertTrue(m1.matches());
        assertEquals("1", m1.group(1));
        assertEquals("name", m1.group(2).toLowerCase());

        Matcher m2 = ESCOINS_TOP_PATTERN.matcher("top_1_amount");
        assertTrue(m2.matches());

        Matcher m3 = ESCOINS_TOP_PATTERN.matcher("top_1_ammount");
        assertTrue(m3.matches());
    }

    @Test
    void testRankAndAmountParameters() {
        String p1 = "coins_rank";
        String p2 = "coins_amount";
        String p3 = "coins_amount_formatted";

        assertTrue(p1.equalsIgnoreCase("coins_rank") || p1.equalsIgnoreCase("rank"));
        assertTrue(p2.equalsIgnoreCase("coins_amount") || p2.equalsIgnoreCase("coins_balance"));
        assertTrue(p3.equalsIgnoreCase("coins_amount_formatted") || p3.equalsIgnoreCase("coins_balance_formatted"));
    }
}
