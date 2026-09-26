package fun.endcore.escoins.update;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class UpdateStorageTest {
    private File tempDir;

    @BeforeEach
    void setup() throws Exception {
        tempDir = Files.createTempDirectory("escore_update_test_").toFile();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (tempDir != null && tempDir.exists()) {
            Files.walk(tempDir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    void testInitialEmptyStorage() {
        UpdateStorage storage = new UpdateStorage(tempDir, null);
        assertFalse(storage.hasBaseline());
        assertEquals("", storage.getLastUpdateId());
        assertNull(storage.getLastUpdate());
    }

    @Test
    void testSaveAndLoadUpdate() {
        UpdateStorage storage = new UpdateStorage(tempDir, null);

        UpdateInfo info = new UpdateInfo(
                "update-48921",
                "CoreSMP v1.1 - World Generation Fixed",
                "Sep 13, 2026",
                "https://builtbybit.com/resources/core-smp.125982/update?update=48921",
                "Fixed generation bugs.",
                "hash12345",
                System.currentTimeMillis()
        );

        storage.save(info);
        assertTrue(storage.hasBaseline());
        assertEquals("update-48921", storage.getLastUpdateId());

        // Create new storage instance pointing to same file
        UpdateStorage loaded = new UpdateStorage(tempDir, null);
        assertTrue(loaded.hasBaseline());
        assertEquals("update-48921", loaded.getLastUpdateId());

        UpdateInfo retrieved = loaded.getLastUpdate();
        assertNotNull(retrieved);
        assertEquals("CoreSMP v1.1 - World Generation Fixed", retrieved.title());
        assertEquals("Sep 13, 2026", retrieved.date());
        assertEquals("https://builtbybit.com/resources/core-smp.125982/update?update=48921", retrieved.url());
        assertEquals("hash12345", retrieved.changelogHash());
    }
}
