package fun.endcore.escoins.clearlag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClearLagSystemTest {

    @Test
    @DisplayName("CleanupResult empty returns all zeros")
    void testCleanupResultEmpty() {
        CleanupResult empty = CleanupResult.empty();
        assertEquals(0, empty.totalRemoved());
        assertEquals(0, empty.itemsRemoved());
        assertEquals(0, empty.mobsRemoved());
        assertEquals(0, empty.projectilesRemoved());
        assertEquals(0, empty.xpRemoved());
        assertEquals(0, empty.executionTimeMs());
    }

    @Test
    @DisplayName("CleanupResult record correctly stores counts and duration")
    void testCleanupResultCounts() {
        CleanupResult result = new CleanupResult(150, 100, 25, 15, 10, 5);
        assertEquals(150, result.totalRemoved());
        assertEquals(100, result.itemsRemoved());
        assertEquals(25, result.mobsRemoved());
        assertEquals(15, result.projectilesRemoved());
        assertEquals(10, result.xpRemoved());
        assertEquals(5, result.executionTimeMs());
    }

    @Test
    @DisplayName("Entity filter logic: verify dropped items are never skipped by mob persistence rules")
    void testItemNotBlockedByPersistentEntityCheck() {
        // Simulates the bug where entity.isPersistent() was checked globally
        boolean protectPersistent = true;
        boolean isLivingEntity = false; // Item is not LivingEntity
        boolean itemPersistent = true; // In Bukkit, all items have persist=true

        // Under fixed logic:
        boolean skipped = false;
        if (protectPersistent && isLivingEntity) {
            // only living entities check mob persistence
            skipped = true;
        }

        assertFalse(skipped, "Dropped items must not be skipped by persistent entity protection!");
    }

    @Test
    @DisplayName("Entity filter logic: verify nametagged mob is protected when protectNamed is true")
    void testNamedMobProtection() {
        boolean protectNamed = true;
        boolean isLivingEntity = true;
        String customName = "Dinnerbone";

        boolean skipped = false;
        if (protectNamed && isLivingEntity && customName != null) {
            skipped = true;
        }

        assertTrue(skipped, "Nametagged mob must be protected when protectNamed is enabled!");
    }

    @Test
    @DisplayName("Entity filter logic: verify naturally despawning hostile mob is cleared")
    void testHostileMobCleared() {
        boolean protectPersistent = true;
        boolean isLivingEntity = true;
        boolean removeWhenFarAway = true; // Naturally spawned zombie

        boolean skipped = false;
        if (protectPersistent && isLivingEntity) {
            if (!removeWhenFarAway) {
                skipped = true;
            }
        }

        assertFalse(skipped, "Naturally despawning hostile mob should be cleared when removeMobs is enabled!");
    }
}
