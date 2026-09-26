package fun.endcore.escoins.cosmetics;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticSystemTest {

    @Test
    void testCosmeticTypeParsing() {
        assertEquals(CosmeticType.CHAT_COLOR, CosmeticType.fromString("chatcolor"));
        assertEquals(CosmeticType.CHAT_COLOR, CosmeticType.fromString("chat_color"));
        assertEquals(CosmeticType.CHAT_COLOR, CosmeticType.fromString("cc"));
        assertEquals(CosmeticType.CHAT_COLOR, CosmeticType.fromString("color"));

        assertEquals(CosmeticType.PLAYER_GLOW, CosmeticType.fromString("playerglow"));
        assertEquals(CosmeticType.PLAYER_GLOW, CosmeticType.fromString("player_glow"));
        assertEquals(CosmeticType.PLAYER_GLOW, CosmeticType.fromString("glow"));
        assertEquals(CosmeticType.PLAYER_GLOW, CosmeticType.fromString("pg"));

        assertNull(CosmeticType.fromString("unknown"));
    }

    @Test
    void testOwnershipTypeParsing() {
        assertEquals(OwnershipType.PERMANENT, OwnershipType.fromString("perm"));
        assertEquals(OwnershipType.PERMANENT, OwnershipType.fromString("permanent"));
        assertEquals(OwnershipType.PERMANENT, OwnershipType.fromString("p"));

        assertEquals(OwnershipType.TEMPORARY, OwnershipType.fromString("temp"));
        assertEquals(OwnershipType.TEMPORARY, OwnershipType.fromString("temporary"));
        assertEquals(OwnershipType.TEMPORARY, OwnershipType.fromString("t"));

        assertNull(OwnershipType.fromString("invalid"));
    }

    @Test
    void testCosmeticColorsDefault() {
        String[] requiredColors = {
                "red", "green", "blue", "yellow", "aqua", "white", "gray",
                "dark_red", "dark_green", "dark_blue", "gold", "light_purple", "dark_purple"
        };

        for (String c : requiredColors) {
            CosmeticColor color = CosmeticColor.getDefault(c);
            assertNotNull(color, "Missing default color: " + c);
            assertNotNull(color.namedTextColor(), "Missing NamedTextColor for: " + c);
            assertNotNull(color.bukkitColor(), "Missing Bukkit ChatColor for: " + c);
            assertNotNull(color.chatCode(), "Missing chat code for: " + c);
        }
    }

    @Test
    void testCosmeticEntryExpiration() {
        UUID uuid = UUID.randomUUID();

        // Permanent never expires
        CosmeticEntry perm = new CosmeticEntry(uuid, CosmeticType.CHAT_COLOR, "green", OwnershipType.PERMANENT, null);
        assertFalse(perm.isExpired());
        assertEquals(Long.MAX_VALUE, perm.getRemainingMillis());

        // Future temporary not expired
        CosmeticEntry future = new CosmeticEntry(uuid, CosmeticType.PLAYER_GLOW, "blue", OwnershipType.TEMPORARY, System.currentTimeMillis() + 60000L);
        assertFalse(future.isExpired());
        assertTrue(future.getRemainingMillis() > 0);

        // Past temporary is expired
        CosmeticEntry expired = new CosmeticEntry(uuid, CosmeticType.PLAYER_GLOW, "blue", OwnershipType.TEMPORARY, System.currentTimeMillis() - 1000L);
        assertTrue(expired.isExpired());
        assertEquals(0L, expired.getRemainingMillis());
    }
}
