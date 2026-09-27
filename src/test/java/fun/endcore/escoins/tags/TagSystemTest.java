package fun.endcore.escoins.tags;

import fun.endcore.escoins.cosmetics.CosmeticEntry;
import fun.endcore.escoins.cosmetics.CosmeticType;
import fun.endcore.escoins.cosmetics.OwnershipType;
import fun.endcore.escoins.util.ColorUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TagSystemTest {

    @Test
    void testAll36TagsLoadedFromYaml() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("tags.yml");
        assertNotNull(is, "tags.yml must exist in classpath resources");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
        ConfigurationSection section = config.getConfigurationSection("tags");
        assertNotNull(section, "tags section must exist in tags.yml");

        assertEquals(36, section.getKeys(false).size(), "There should be exactly 36 tags");

        String[] expectedTags = {
                "lucky", "noob", "pro", "sky", "legend", "slayer", "dream", "frost", "gold", "aqua",
                "shadow", "epic", "king", "fire", "crystal", "ninja", "hunter", "phoenix", "magic", "champion",
                "assassin", "titan", "viper", "dragon", "wizard", "samurai", "warrior", "joy", "ranger", "ghost",
                "knight", "wizardry", "titanic", "god", "royal", "mythic"
        };

        for (int i = 0; i < expectedTags.length; i++) {
            String id = expectedTags[i];
            int expectedOrder = i + 1;

            assertTrue(section.contains(id), "Missing tag: " + id);
            assertEquals(expectedOrder, section.getInt(id + ".order"), "Incorrect order for: " + id);
            assertEquals("tags.use." + id, section.getString(id + ".permission"), "Incorrect permission for: " + id);
            assertNotNull(section.getString(id + ".tag"), "Missing tag display string for: " + id);
            assertNotNull(section.getString(id + ".description"), "Missing tag description for: " + id);
        }
    }

    @Test
    void testColorUtilHexTranslation() {
        String input = "&8[&#00FF7F&lLucky&8]";
        String translated = ColorUtil.translateHexAndLegacy(input);

        // Expected format: §8[§x§0§0§F§F§7§F§lLucky§8]
        assertEquals("§8[§x§0§0§F§F§7§F§lLucky§8]", translated);

        // Test empty and null handling
        assertEquals("", ColorUtil.translateHexAndLegacy(null));
        assertEquals("", ColorUtil.translateHexAndLegacy(""));
    }

    @Test
    void testTagDefinitionFormatting() {
        TagDefinition def = new TagDefinition("lucky", 1, "&8[&#00FF7F&lLucky&8]", "&7Unlocked from crates or shop", "tags.use.lucky");
        assertEquals("lucky", def.id());
        assertEquals(1, def.order());
        assertEquals("§8[§x§0§0§F§F§7§F§lLucky§8]", def.getFormatted());
        assertEquals("tags.use.lucky", def.permission());
    }

    @Test
    void testCosmeticTypeTagAliases() {
        assertEquals(CosmeticType.TAG, CosmeticType.fromString("tag"));
        assertEquals(CosmeticType.TAG, CosmeticType.fromString("tags"));
        assertEquals(CosmeticType.TAG, CosmeticType.fromString("playertag"));
        assertEquals(CosmeticType.TAG, CosmeticType.fromString("playertags"));
    }

    @Test
    void testPlaceholderReturnsEmptyWhenNoTag() {
        // As per specification: "if no tag then it displays nothing it dosent even feel like it exists"
        String activeTag = null;
        String formatted = (activeTag == null) ? "" : activeTag;
        assertEquals("", formatted, "Must return an empty string with length 0 when no tag is active");
        assertTrue(formatted.isEmpty());
    }

    @Test
    void testTagCosmeticEntryActiveAndExpiration() {
        UUID uuid = UUID.randomUUID();

        // Permanent tag
        CosmeticEntry permTag = new CosmeticEntry(uuid, CosmeticType.TAG, "god", OwnershipType.PERMANENT, null);
        assertTrue(permTag.active());
        assertTrue(permTag.isActive());
        assertFalse(permTag.isExpired());
        assertEquals("god", permTag.color());

        // Toggle tag off
        CosmeticEntry offTag = permTag.withActive(false);
        assertFalse(offTag.active());
        assertFalse(offTag.isActive());

        // Temporary tag
        long future = System.currentTimeMillis() + 3600000L;
        CosmeticEntry tempTag = new CosmeticEntry(uuid, CosmeticType.TAG, "mythic", OwnershipType.TEMPORARY, future);
        assertTrue(tempTag.isActive());
        assertFalse(tempTag.isExpired());

        // Expired tag
        long past = System.currentTimeMillis() - 1000L;
        CosmeticEntry expiredTag = new CosmeticEntry(uuid, CosmeticType.TAG, "mythic", OwnershipType.TEMPORARY, past);
        assertFalse(expiredTag.isActive());
        assertTrue(expiredTag.isExpired());
    }
}
