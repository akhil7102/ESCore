package fun.endcore.escoins.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberFormatterTest {

    @BeforeEach
    void setup() {
        NumberFormatter.configure(",", 3);
    }

    @Test
    void testStandardFormatting() {
        assertEquals("0", NumberFormatter.format(0));
        assertEquals("500", NumberFormatter.format(500));
        assertEquals("1,000", NumberFormatter.format(1000));
        assertEquals("10,000", NumberFormatter.format(10000));
        assertEquals("1,000,000", NumberFormatter.format(1000000));
        assertEquals("1,234,567,890", NumberFormatter.format(1234567890L));
    }

    @Test
    void testLargeLongValues() {
        assertEquals("9,223,372,036,854,775,807", NumberFormatter.format(Long.MAX_VALUE));
    }

    @Test
    void testCustomSeparator() {
        NumberFormatter.configure(".", 3);
        assertEquals("1.000.000", NumberFormatter.format(1000000));

        NumberFormatter.configure(" ", 3);
        assertEquals("1 000 000", NumberFormatter.format(1000000));
    }
}
