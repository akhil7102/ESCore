package fun.endcore.escoins.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArenaRegionTest {

    @Test
    @DisplayName("Should correctly normalize inverted coordinates and compute dimensions")
    void testDimensionsAndNormalization() {
        ArenaRegion region = new ArenaRegion("test_arena", "world", 100, 70, 50, 80, 60, 100);

        assertEquals("test_arena", region.getName());
        assertEquals("world", region.getWorldName());

        // Min and Max normalization
        assertEquals(80, region.getMinX());
        assertEquals(100, region.getMaxX());
        assertEquals(60, region.getMinY());
        assertEquals(70, region.getMaxY());
        assertEquals(50, region.getMinZ());
        assertEquals(100, region.getMaxZ());

        // Dimensions (inclusive)
        assertEquals(21, region.getWidth());
        assertEquals(11, region.getHeight());
        assertEquals(51, region.getLength());
        assertEquals(21L * 11L * 51L, region.getTotalBlocks());
    }

    @Test
    @DisplayName("Should check if coordinates are within the region bounds")
    void testContainsCoordinates() {
        ArenaRegion region = new ArenaRegion("box", "world", 0, 10, 0, 10, 20, 10);

        assertTrue(region.contains(0, 10, 0));
        assertTrue(region.contains(10, 20, 10));
        assertTrue(region.contains(5, 15, 5));

        assertFalse(region.contains(-1, 15, 5));
        assertFalse(region.contains(5, 9, 5));
        assertFalse(region.contains(5, 21, 5));
        assertFalse(region.contains(5, 15, 11));
    }

    @Test
    @DisplayName("Should correctly serialize and deserialize arena data")
    void testSerializationRoundTrip() {
        long timestamp = 1700000000000L;
        ArenaRegion original = new ArenaRegion("sumo", "arenas_world", -50, 64, -50, 50, 80, 50, timestamp);

        Map<String, Object> map = original.serialize();
        ArenaRegion deserialized = ArenaRegion.deserialize(map);

        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getWorldName(), deserialized.getWorldName());
        assertEquals(original.getMinX(), deserialized.getMinX());
        assertEquals(original.getMaxX(), deserialized.getMaxX());
        assertEquals(original.getMinY(), deserialized.getMinY());
        assertEquals(original.getMaxY(), deserialized.getMaxY());
        assertEquals(original.getMinZ(), deserialized.getMinZ());
        assertEquals(original.getMaxZ(), deserialized.getMaxZ());
        assertEquals(original.getTotalBlocks(), deserialized.getTotalBlocks());
        assertEquals(original.getCreatedAt(), deserialized.getCreatedAt());
    }
}
