package fun.endcore.escoins.update;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Immutable data structure representing a detected BuiltByBit resource update.
 */
public record UpdateInfo(
        String id,
        String title,
        String date,
        String url,
        String changelog,
        String changelogHash,
        long detectedAt
) {
    public UpdateInfo {
        if (title == null || title.isBlank()) {
            title = "Core SMP Update";
        } else {
            title = title.trim();
        }
        if (date == null || date.isBlank()) {
            date = "Unknown Date";
        } else {
            date = date.trim();
        }
        if (url == null || url.isBlank()) {
            url = "https://builtbybit.com/resources/core-smp.125982/updates";
        } else {
            url = url.trim();
        }
        if (changelog == null) {
            changelog = "";
        } else {
            changelog = changelog.trim();
        }
        if (id == null || id.isBlank()) {
            id = generateFallbackId(title, date, changelog);
        } else {
            id = id.trim();
        }
        if (changelogHash == null || changelogHash.isBlank()) {
            changelogHash = hashString(changelog.isEmpty() ? title + "_" + date : changelog);
        } else {
            changelogHash = changelogHash.trim();
        }
        if (detectedAt <= 0) {
            detectedAt = System.currentTimeMillis();
        }
    }

    public static String hashString(String input) {
        if (input == null) input = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public static String generateFallbackId(String title, String date, String changelog) {
        if (title != null && !title.isBlank() && date != null && !date.isBlank()) {
            String slug = (title + "_" + date).toLowerCase()
                    .replaceAll("[^a-z0-9_]+", "-")
                    .replaceAll("-+", "-")
                    .replaceAll("^-|-$", "");
            if (!slug.isBlank()) {
                return slug;
            }
        }
        return hashString(changelog != null && !changelog.isBlank() ? changelog : String.valueOf(System.currentTimeMillis()));
    }
}
