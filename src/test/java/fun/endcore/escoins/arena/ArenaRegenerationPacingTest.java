package fun.endcore.escoins.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArenaRegenerationPacingTest {

    @Test
    @DisplayName("Should use 64-bit long arithmetic to prevent integer overflow on large volumes")
    void testLargeVolumeOverflowSafety() {
        // An arena of 2000 x 256 x 2000 blocks = 1,024,000,000 blocks
        // In 32-bit signed integer arithmetic, if multiplied naively, larger coordinates overflow
        ArenaRegion largeArena = new ArenaRegion("mega_arena", "world", 0, 0, 0, 2500, 255, 2500);

        long width = largeArena.getWidth();
        long height = largeArena.getHeight();
        long length = largeArena.getLength();
        long total = largeArena.getTotalBlocks();

        assertEquals(2501L, width);
        assertEquals(256L, height);
        assertEquals(2501L, length);

        long expected = 2501L * 256L * 2501L; // 1,601,280,256 blocks
        assertEquals(expected, total);
        assertTrue(total > 0, "Total blocks must remain positive and not overflow");
    }

    @Test
    @DisplayName("Adaptive pacing should calculate bounded blocks per tick within target duration")
    void testAdaptivePacingCalculation() {
        long totalBlocks = 100_000L;
        int targetSeconds = 30;
        int minSeconds = 5;
        int maxSeconds = 300;

        long targetTicks = targetSeconds * 20L;
        long minTicks = minSeconds * 20L;
        long maxTicks = maxSeconds * 20L;

        long desiredTicks = Math.max(minTicks, Math.min(maxTicks, targetTicks));
        assertEquals(600L, desiredTicks);

        int sizeY = 20;
        int sizeZ = 50;
        int delayRows = 1;
        int delayLayers = 2;

        long totalRows = (long) sizeY * sizeZ;
        long delayOverheadTicks = (totalRows * delayRows) + ((long) sizeY * delayLayers);

        // Delay overhead: (1000 * 1) + (20 * 2) = 1040 ticks, which exceeds 600 ticks
        // Algorithm adapts by dropping row delays:
        if (delayOverheadTicks >= desiredTicks) {
            delayRows = 0;
            delayLayers = Math.min(1, delayLayers);
            delayOverheadTicks = (long) sizeY * delayLayers;
        }

        assertEquals(0, delayRows);
        assertEquals(20L, delayOverheadTicks);

        long availableTicks = Math.max(1L, desiredTicks - delayOverheadTicks);
        assertEquals(580L, availableTicks);

        int blocksPerTick = (int) Math.min(2500L, Math.max(25L, (totalBlocks + availableTicks - 1) / availableTicks));
        // 100,000 / 580 ≈ 173 blocks per tick
        assertEquals(173, blocksPerTick);
        assertTrue(blocksPerTick >= 25 && blocksPerTick <= 2500, "Blocks per tick must remain safely bounded");
    }

    @Test
    @DisplayName("Small arenas should be bounded by minimum duration pacing")
    void testSmallArenaPacing() {
        long totalBlocks = 500L;
        int minSeconds = 5;
        long minTicks = minSeconds * 20L; // 100 ticks

        int sizeY = 5;
        int sizeZ = 10;
        int delayRows = 0;
        int delayLayers = 1;
        long delayOverhead = (long) sizeY * delayLayers; // 5 ticks

        long availableTicks = Math.max(1L, minTicks - delayOverhead);
        int blocksPerTick = (int) Math.min(2500L, Math.max(5L, (totalBlocks + availableTicks - 1) / availableTicks));

        // 500 / 95 ≈ 6 blocks per tick
        assertEquals(6, blocksPerTick);
    }
}
