package fun.endcore.escoins.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing human-readable duration strings (e.g. 30s, 15m, 1h, 5d, 2w)
 * into milliseconds and formatting milliseconds into friendly remaining time.
 */
public final class DurationParser {
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)");

    private DurationParser() {}

    /**
     * Parses a duration string into milliseconds.
     *
     * @param input duration string, e.g. "5d", "24h", "30m", "10s", "2w"
     * @return duration in milliseconds
     * @throws IllegalArgumentException if the input is invalid or <= 0
     */
    public static long parseToMillis(String input) throws IllegalArgumentException {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration string cannot be empty.");
        }

        String cleaned = input.trim().toLowerCase();
        Matcher matcher = DURATION_PATTERN.matcher(cleaned);
        long totalMillis = 0;
        int matchedEnd = 0;

        while (matcher.find()) {
            // Ensure no invalid characters between segments
            if (matcher.start() != matchedEnd) {
                throw new IllegalArgumentException("Invalid duration format: " + input);
            }
            matchedEnd = matcher.end();

            long value;
            try {
                value = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Duration number too large: " + matcher.group(1));
            }

            String unit = matcher.group(2).toLowerCase();
            long unitMultiplier = switch (unit) {
                case "s", "sec", "second", "seconds" -> 1000L;
                case "m", "min", "minute", "minutes" -> 60L * 1000L;
                case "h", "hr", "hrs", "hour", "hours" -> 3600L * 1000L;
                case "d", "day", "days" -> 86400L * 1000L;
                case "w", "wk", "wks", "week", "weeks" -> 7L * 86400L * 1000L;
                default -> throw new IllegalArgumentException("Unknown time unit: '" + unit + "'. Use s, m, h, d, or w.");
            };

            totalMillis = Math.addExact(totalMillis, Math.multiplyExact(value, unitMultiplier));
        }

        if (matchedEnd != cleaned.length() || totalMillis <= 0) {
            throw new IllegalArgumentException("Invalid duration format: '" + input + "'. Examples: 30m, 12h, 5d, 2w");
        }

        return totalMillis;
    }

    /**
     * Formats remaining milliseconds into a human-readable string.
     * Examples: "5d 12h", "4h 30m", "25m 10s", "45s".
     *
     * @param remainingMillis remaining time in milliseconds
     * @return formatted duration string
     */
    public static String formatRemaining(long remainingMillis) {
        if (remainingMillis <= 0) {
            return "Expired";
        }

        long seconds = remainingMillis / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;

        if (days > 0) {
            if (hours > 0) {
                return days + "d " + hours + "h";
            }
            return days + "d";
        }

        if (hours > 0) {
            if (minutes > 0) {
                return hours + "h " + minutes + "m";
            }
            return hours + "h";
        }

        if (minutes > 0) {
            if (secs > 0) {
                return minutes + "m " + secs + "s";
            }
            return minutes + "m";
        }

        return secs + "s";
    }
}
