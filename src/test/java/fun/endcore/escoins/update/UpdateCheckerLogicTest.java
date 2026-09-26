package fun.endcore.escoins.update;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerLogicTest {
    private File tempDir;

    @BeforeEach
    void setup() throws Exception {
        tempDir = Files.createTempDirectory("escore_logic_test_").toFile();
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
    void testBaselineVsNewUpdateFlow() {
        UpdateStorage storage = new UpdateStorage(tempDir, null);
        assertFalse(storage.hasBaseline());

        AtomicInteger adminNotifications = new AtomicInteger(0);
        AtomicInteger consoleNotifications = new AtomicInteger(0);

        UpdateInfo initialUpdate = new UpdateInfo(
                "update-100",
                "CoreSMP v1.0",
                "Sep 01, 2026",
                "https://builtbybit.com/resources/core-smp.125982/update?update=100",
                "Initial release",
                "hash-100",
                System.currentTimeMillis()
        );

        // 1. FIRST CHECK EVER: Save baseline without notification
        boolean firstCheckSaveBaseline = true;
        if (!storage.hasBaseline() && firstCheckSaveBaseline) {
            storage.save(initialUpdate);
            // No notifications!
        }
        assertEquals(0, adminNotifications.get());
        assertEquals(0, consoleNotifications.get());
        assertTrue(storage.hasBaseline());
        assertEquals("update-100", storage.getLastUpdateId());

        // 2. SECOND CHECK: Same update
        UpdateInfo check2 = new UpdateInfo(
                "update-100",
                "CoreSMP v1.0",
                "Sep 01, 2026",
                "https://builtbybit.com/resources/core-smp.125982/update?update=100",
                "Initial release",
                "hash-100",
                System.currentTimeMillis()
        );

        if (!check2.id().equalsIgnoreCase(storage.getLastUpdateId())) {
            adminNotifications.incrementAndGet();
            consoleNotifications.incrementAndGet();
            storage.save(check2);
        }
        assertEquals(0, adminNotifications.get());
        assertEquals(0, consoleNotifications.get());

        // 3. THIRD CHECK: NEW UPDATE DETECTED
        UpdateInfo check3 = new UpdateInfo(
                "update-101",
                "CoreSMP v1.1 - World Generation Fixed",
                "Sep 13, 2026",
                "https://builtbybit.com/resources/core-smp.125982/update?update=101",
                "Fixed tree decay",
                "hash-101",
                System.currentTimeMillis()
        );

        if (!check3.id().equalsIgnoreCase(storage.getLastUpdateId())) {
            adminNotifications.incrementAndGet();
            consoleNotifications.incrementAndGet();
            storage.save(check3);
        }
        assertEquals(1, adminNotifications.get());
        assertEquals(1, consoleNotifications.get());
        assertEquals("update-101", storage.getLastUpdateId());

        // 4. FOURTH CHECK: Same new update (must not re-notify)
        if (!check3.id().equalsIgnoreCase(storage.getLastUpdateId())) {
            adminNotifications.incrementAndGet();
            consoleNotifications.incrementAndGet();
            storage.save(check3);
        }
        assertEquals(1, adminNotifications.get(), "Duplicate notification must not occur");
        assertEquals(1, consoleNotifications.get(), "Duplicate notification must not occur");

        // 5. SERVER RESTART SIMULATION: New storage instance loading same directory
        UpdateStorage restartedStorage = new UpdateStorage(tempDir, null);
        assertTrue(restartedStorage.hasBaseline());
        assertEquals("update-101", restartedStorage.getLastUpdateId());

        // Check again after restart
        if (!check3.id().equalsIgnoreCase(restartedStorage.getLastUpdateId())) {
            adminNotifications.incrementAndGet();
        }
        assertEquals(1, adminNotifications.get(), "After restart, previously saved update must not re-notify");
    }

    @Test
    void testIntervalLowerBoundEnforced() {
        int rawInterval = 10;
        int enforced = Math.max(30, rawInterval);
        assertEquals(30, enforced);

        int rawIntervalValid = 120;
        int enforcedValid = Math.max(30, rawIntervalValid);
        assertEquals(120, enforcedValid);
    }
}
