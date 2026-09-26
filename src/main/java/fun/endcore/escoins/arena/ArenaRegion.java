package fun.endcore.escoins.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a cuboid arena region defined by two corner coordinates in a world.
 */
public class ArenaRegion {
    private final String name;
    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final long createdAt;

    public ArenaRegion(String name, String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this(name, worldName, x1, y1, z1, x2, y2, z2, System.currentTimeMillis());
    }

    public ArenaRegion(String name, String worldName, int x1, int y1, int z1, int x2, int y2, int z2, long createdAt) {
        this.name = name;
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public int getWidth() {
        return maxX - minX + 1;
    }

    public int getHeight() {
        return maxY - minY + 1;
    }

    public int getLength() {
        return maxZ - minZ + 1;
    }

    public long getTotalBlocks() {
        return (long) getWidth() * getHeight() * getLength();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        return contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("world", worldName);
        map.put("min-x", minX);
        map.put("min-y", minY);
        map.put("min-z", minZ);
        map.put("max-x", maxX);
        map.put("max-y", maxY);
        map.put("max-z", maxZ);
        map.put("created-at", createdAt);
        return map;
    }

    public static ArenaRegion deserialize(Map<String, Object> map) {
        String name = (String) map.get("name");
        String world = (String) map.get("world");
        int minX = ((Number) map.get("min-x")).intValue();
        int minY = ((Number) map.get("min-y")).intValue();
        int minZ = ((Number) map.get("min-z")).intValue();
        int maxX = ((Number) map.get("max-x")).intValue();
        int maxY = ((Number) map.get("max-y")).intValue();
        int maxZ = ((Number) map.get("max-z")).intValue();
        long createdAt = map.containsKey("created-at") ? ((Number) map.get("created-at")).longValue() : System.currentTimeMillis();

        return new ArenaRegion(name, world, minX, minY, minZ, maxX, maxY, maxZ, createdAt);
    }
}
