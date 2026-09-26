package fun.endcore.escoins.arena;

import org.bukkit.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSelectionTest {

    @Test
    @DisplayName("Should detect incomplete selection when one or both points are missing")
    void testIncompleteSelection() {
        PlayerSelection sel = new PlayerSelection();
        assertFalse(sel.isComplete());
        assertEquals(0, sel.getTotalBlocks());

        sel.setPos1(new Location(null, 10, 64, 10));
        assertFalse(sel.isComplete());
        assertEquals(0, sel.getTotalBlocks());

        sel.setPos2(new Location(null, 20, 70, 20));
        assertTrue(sel.isComplete());
    }

    @Test
    @DisplayName("Should compute cuboid bounds and volume correctly from two points")
    void testSelectionDimensions() {
        PlayerSelection sel = new PlayerSelection();
        sel.setPos1(new Location(null, 20, 80, 5));
        sel.setPos2(new Location(null, 10, 60, 25));

        assertTrue(sel.isComplete());
        assertEquals(10, sel.getMinX());
        assertEquals(20, sel.getMaxX());
        assertEquals(60, sel.getMinY());
        assertEquals(80, sel.getMaxY());
        assertEquals(5, sel.getMinZ());
        assertEquals(25, sel.getMaxZ());

        assertEquals(11, sel.getWidth());
        assertEquals(21, sel.getHeight());
        assertEquals(21, sel.getLength());
        assertEquals(11L * 21L * 21L, sel.getTotalBlocks());
    }
}
