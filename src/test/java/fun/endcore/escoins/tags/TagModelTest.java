package fun.endcore.escoins.tags;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TagModelTest {

    @Test
    void testTagDefinitionNormalization() {
        TagDefinition def = new TagDefinition("warlord", "&c[WARLORD]");
        assertEquals("WARLORD", def.id());
        assertEquals("&c[WARLORD]", def.display());

        TagDefinition fallback = new TagDefinition("slayer", null);
        assertEquals("SLAYER", fallback.id());
        assertEquals("[SLAYER]", fallback.display());

        assertThrows(IllegalArgumentException.class, () -> new TagDefinition(null, "&c[TEST]"));
        assertThrows(IllegalArgumentException.class, () -> new TagDefinition("   ", "&c[TEST]"));
    }

    @Test
    void testPlayerTagDataOperations() {
        UUID uuid = UUID.randomUUID();
        PlayerTagData data = new PlayerTagData(uuid);

        assertEquals(uuid, data.getUuid());
        assertTrue(data.getOwnedTags().isEmpty());
        assertNull(data.getActiveTag());

        // Add tags
        data.addTag("warlord");
        data.addTag("SLAYER");
        data.addTag("warlord"); // Duplicate

        assertEquals(2, data.getOwnedTags().size());
        assertTrue(data.hasTag("WARLORD"));
        assertTrue(data.hasTag("warlord"));
        assertTrue(data.hasTag("slayer"));
        assertFalse(data.hasTag("GLADIATOR"));

        // Active tag
        data.setActiveTag("warlord");
        assertEquals("WARLORD", data.getActiveTag());

        // Remove non-active tag
        data.removeTag("slayer");
        assertFalse(data.hasTag("SLAYER"));
        assertEquals("WARLORD", data.getActiveTag());

        // Remove active tag should unset activeTag
        data.removeTag("warlord");
        assertFalse(data.hasTag("WARLORD"));
        assertNull(data.getActiveTag());

        // Constructor with initial list
        PlayerTagData data2 = new PlayerTagData(uuid, List.of("PHANTOM", "TYCOON", "NEMESIS"), "PHANTOM");
        assertEquals(3, data2.getOwnedTags().size());
        assertEquals("PHANTOM", data2.getActiveTag());
        assertTrue(data2.hasTag("tycoon"));

        // Clear tags
        data2.clearTags();
        assertTrue(data2.getOwnedTags().isEmpty());
        assertNull(data2.getActiveTag());

        // Verify unmodifiable view
        Set<String> owned = data2.getOwnedTags();
        assertThrows(UnsupportedOperationException.class, () -> owned.add("NEW_TAG"));
    }

    @Test
    void testTagEntryExpirationAndAutoPurge() {
        UUID uuid = UUID.randomUUID();
        PlayerTagData data = new PlayerTagData(uuid);

        // 1. Permanent tag
        TagEntry perm = new TagEntry("WARLORD", fun.endcore.escoins.cosmetics.OwnershipType.PERMANENT, null);
        assertFalse(perm.isExpired());
        assertEquals(Long.MAX_VALUE, perm.getRemainingMillis());
        data.addTag(perm);

        // 2. Temporary active tag expiring in the future
        TagEntry tempFuture = new TagEntry("PHANTOM", fun.endcore.escoins.cosmetics.OwnershipType.TEMPORARY, System.currentTimeMillis() + 60000L);
        assertFalse(tempFuture.isExpired());
        assertTrue(tempFuture.getRemainingMillis() > 0);
        data.addTag(tempFuture);

        data.setActiveTag("PHANTOM");
        assertEquals("PHANTOM", data.getActiveTag());
        assertTrue(data.hasTag("PHANTOM"));
        assertEquals(2, data.getOwnedTags().size());

        // 3. Expired temporary tag
        TagEntry tempExpired = new TagEntry("NEMESIS", fun.endcore.escoins.cosmetics.OwnershipType.TEMPORARY, System.currentTimeMillis() - 1000L);
        assertTrue(tempExpired.isExpired());
        assertEquals(0L, tempExpired.getRemainingMillis());
        data.addTag(tempExpired);

        // Expired tag should not be considered owned
        assertFalse(data.hasTag("NEMESIS"));
        assertNull(data.getTagEntry("NEMESIS"));

        // If an active tag expires, getActiveTag() must return null automatically
        TagEntry expiringActive = new TagEntry("GLADIATOR", fun.endcore.escoins.cosmetics.OwnershipType.TEMPORARY, System.currentTimeMillis() + 10L);
        data.addTag(expiringActive);
        data.setActiveTag("GLADIATOR");
        assertEquals("GLADIATOR", data.getActiveTag());

        try {
            Thread.sleep(25L);
        } catch (InterruptedException ignored) {}

        // Now expired - active tag must be automatically cleared
        assertNull(data.getActiveTag());
        assertFalse(data.hasTag("GLADIATOR"));
    }
}
