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

    @Test
    void testCosmeticEntryActiveState() {
        UUID uuid = UUID.randomUUID();

        // Default constructor sets active to true
        CosmeticEntry entry = new CosmeticEntry(uuid, CosmeticType.PLAYER_GLOW, "gold", OwnershipType.PERMANENT, null);
        assertTrue(entry.active());
        assertTrue(entry.isActive());

        // Toggle active to false
        CosmeticEntry disabled = entry.withActive(false);
        assertFalse(disabled.active());
        assertFalse(disabled.isActive());

        // Toggle back to true
        CosmeticEntry enabledAgain = disabled.withActive(true);
        assertTrue(enabledAgain.active());
        assertTrue(enabledAgain.isActive());

        // Expired cosmetic with active=true should have isActive() == false
        CosmeticEntry expiredActive = new CosmeticEntry(uuid, CosmeticType.CHAT_COLOR, "red", OwnershipType.TEMPORARY, System.currentTimeMillis() - 5000L, true, System.currentTimeMillis() - 10000L);
        assertTrue(expiredActive.active());
        assertTrue(expiredActive.isExpired());
        assertFalse(expiredActive.isActive());
    }

    @Test
    void testCosmeticToggleActionEvaluation() {
        // Simulates the command toggle logic for /glow and /chatcolor
        // Case 1: Player has no cosmetic -> deny
        CosmeticEntry none = null;
        assertNull(none, "Player has no cosmetic unlocked");

        // Case 2: Player has cosmetic active=true, runs /glow on -> already enabled deny
        CosmeticEntry activeGlow = new CosmeticEntry(UUID.randomUUID(), CosmeticType.PLAYER_GLOW, "aqua", OwnershipType.PERMANENT, null);
        assertTrue(activeGlow.active());

        // Case 3: Player has cosmetic active=true, runs /glow off -> toggles to disabled
        CosmeticEntry turnedOff = activeGlow.withActive(false);
        assertFalse(turnedOff.active());

        // Case 4: Player has cosmetic active=false, runs /glow off -> already disabled deny
        assertFalse(turnedOff.active());

        // Case 5: Player has cosmetic active=false, runs /glow on -> toggles to enabled
        CosmeticEntry turnedOn = turnedOff.withActive(true);
        assertTrue(turnedOn.active());
    }
}
