package fun.endcore.escoins.arena;

import org.bukkit.Location;

/**
 * Represents an in-memory two-point selection made by a player using the selection wand.
 */
public class PlayerSelection {
    private Location pos1;
    private Location pos2;

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null;
    }

    public boolean isSameWorld() {
        if (!isComplete()) {
            return false;
        }
        return pos1.getWorld() != null && pos2.getWorld() != null &&
               pos1.getWorld().getName().equals(pos2.getWorld().getName());
    }

    public String getWorldName() {
        return pos1 != null && pos1.getWorld() != null ? pos1.getWorld().getName() : null;
    }

    public int getMinX() {
        if (!isComplete()) return 0;
        return Math.min(pos1.getBlockX(), pos2.getBlockX());
    }

    public int getMinY() {
        if (!isComplete()) return 0;
        return Math.min(pos1.getBlockY(), pos2.getBlockY());
    }

    public int getMinZ() {
        if (!isComplete()) return 0;
        return Math.min(pos1.getBlockZ(), pos2.getBlockZ());
    }

    public int getMaxX() {
        if (!isComplete()) return 0;
        return Math.max(pos1.getBlockX(), pos2.getBlockX());
    }

    public int getMaxY() {
        if (!isComplete()) return 0;
        return Math.max(pos1.getBlockY(), pos2.getBlockY());
    }

    public int getMaxZ() {
        if (!isComplete()) return 0;
        return Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    public int getWidth() {
        if (!isComplete()) return 0;
        return getMaxX() - getMinX() + 1;
    }

    public int getHeight() {
        if (!isComplete()) return 0;
        return getMaxY() - getMinY() + 1;
    }

    public int getLength() {
        if (!isComplete()) return 0;
        return getMaxZ() - getMinZ() + 1;
    }

    public long getTotalBlocks() {
        if (!isComplete()) return 0;
        return (long) getWidth() * getHeight() * getLength();
    }
}
