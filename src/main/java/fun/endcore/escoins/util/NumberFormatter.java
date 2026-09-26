package fun.endcore.escoins.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Utility for formatting coin numbers with configurable grouping separators.
 */
public final class NumberFormatter {
    private static volatile String thousandsSeparator = ",";
    private static volatile int groupingSize = 3;

    private NumberFormatter() {}

    public static void configure(String separator, int size) {
        thousandsSeparator = (separator == null || separator.isEmpty()) ? "," : separator;
        groupingSize = size <= 0 ? 3 : size;
    }

    /**
     * Formats a long value with thousands separators.
     * Fast and thread-safe.
     *
     * @param value the long value
     * @return formatted string, e.g. "1,000,000"
     */
    public static String format(long value) {
        if (value == 0) {
            return "0";
        }
        boolean negative = value < 0;
        String raw = String.valueOf(negative ? -value : value);
        int len = raw.length();
        if (len <= groupingSize) {
            return negative ? "-" + raw : raw;
        }

        StringBuilder sb = new StringBuilder(len + (len / groupingSize) + (negative ? 1 : 0));
        if (negative) {
            sb.append('-');
        }

        int remainder = len % groupingSize;
        if (remainder > 0) {
            sb.append(raw, 0, remainder);
            if (len > remainder) {
                sb.append(thousandsSeparator);
            }
        }

        for (int i = remainder; i < len; i += groupingSize) {
            sb.append(raw, i, i + groupingSize);
            if (i + groupingSize < len) {
                sb.append(thousandsSeparator);
            }
        }

        return sb.toString();
    }
}
