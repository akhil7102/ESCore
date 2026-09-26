package fun.endcore.escoins.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Encapsulates a player's saved persistent logout coordinates.
 */
public record PlayerLocationData(
        UUID uuid,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean hasJoinedBefore
) {
    public Location toLocation() {
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }
}
