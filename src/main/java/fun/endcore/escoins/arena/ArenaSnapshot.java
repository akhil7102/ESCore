package fun.endcore.escoins.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * High-performance, lightweight palette-based binary arena snapshot serializer and restorer.
 */
public class ArenaSnapshot {
    private static final int MAGIC = 0x45534152; // "ESAR"
    private static final int VERSION = 1;

    private static final byte TILE_TYPE_CONTAINER = 1;
    private static final byte TILE_TYPE_SIGN = 2;

    /**
     * Captures and serializes the arena cuboid region into a compressed snapshot file.
     *
     * @param region       The arena region
     * @param snapshotFile The target file to write
     * @return Total blocks captured
     * @throws IOException If saving fails
     */
    public static long saveSnapshot(ArenaRegion region, File snapshotFile) throws IOException {
        World world = region.getWorld();
        if (world == null) {
            throw new IllegalStateException("World '" + region.getWorldName() + "' is not loaded or does not exist.");
        }

        // Ensure parent directory exists
        File parent = snapshotFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        int minX = region.getMinX();
        int minY = region.getMinY();
        int minZ = region.getMinZ();
        int maxX = region.getMaxX();
        int maxY = region.getMaxY();
        int maxZ = region.getMaxZ();

        int sizeX = region.getWidth();
        int sizeY = region.getHeight();
        int sizeZ = region.getLength();
        long totalBlocksLong = (long) sizeX * sizeY * sizeZ;
        if (totalBlocksLong > Integer.MAX_VALUE) {
            throw new IllegalStateException("Arena volume exceeds maximum 32-bit limit.");
        }
        int totalBlocks = (int) totalBlocksLong;

        // Ensure all chunks within region are loaded
        ensureChunksLoaded(world, minX, maxX, minZ, maxZ);

        // 1. Build palette & block indices
        List<String> palette = new ArrayList<>();
        Map<String, Integer> paletteIndexMap = new HashMap<>();
        short[] blockIndices = new short[totalBlocks];

        List<TileData> tileDataList = new ArrayList<>();

        int idx = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    Block block = world.getBlockAt(x, y, z);
                    String dataStr = block.getBlockData().getAsString();

                    Integer pIdx = paletteIndexMap.get(dataStr);
                    if (pIdx == null) {
                        pIdx = palette.size();
                        paletteIndexMap.put(dataStr, pIdx);
                        palette.add(dataStr);
                    }
                    blockIndices[idx++] = pIdx.shortValue();

                    // Check for tile entities
                    BlockState state = block.getState(false);
                    if (state instanceof InventoryHolder holder && state instanceof Container) {
                        ItemStack[] contents = holder.getInventory().getContents();
                        boolean hasItems = false;
                        for (ItemStack item : contents) {
                            if (item != null && !item.getType().isAir()) {
                                hasItems = true;
                                break;
                            }
                        }
                        if (hasItems) {
                            tileDataList.add(new ContainerTileData(x - minX, y - minY, z - minZ, contents));
                        }
                    } else if (state instanceof Sign sign) {
                        tileDataList.add(new SignTileData(x - minX, y - minY, z - minZ, sign));
                    }
                }
            }
        }

        // 2. Write out atomically using a temporary file
        File tempFile = new File(snapshotFile.getParentFile(), snapshotFile.getName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tempFile))))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(sizeX);
            out.writeInt(sizeY);
            out.writeInt(sizeZ);

            // Palette
            out.writeInt(palette.size());
            for (String str : palette) {
                out.writeUTF(str);
            }

            // Blocks
            for (short bIndex : blockIndices) {
                out.writeShort(bIndex);
            }

            // Tile entities
            out.writeInt(tileDataList.size());
            for (TileData td : tileDataList) {
                td.write(out);
            }
            out.flush();
        }

        // Replace snapshotFile
        if (snapshotFile.exists()) {
            snapshotFile.delete();
        }
        if (!tempFile.renameTo(snapshotFile)) {
            // Fallback copy if renameTo failed
            try (InputStream in = new FileInputStream(tempFile);
                 OutputStream out = new FileOutputStream(snapshotFile)) {
                in.transferTo(out);
            }
            tempFile.delete();
        }

        return totalBlocksLong;
    }

    /**
     * Restores an arena from its saved snapshot.
     *
     * @param region        The arena region
     * @param snapshotFile  The snapshot file
     * @param clearEntities Whether to remove loose items, projectiles, and mobs in the cuboid
     * @return RestoreResult with modified block count, total blocks, and elapsed time in ms
     * @throws IOException If restoring fails
     */
    public static RestoreResult restoreSnapshot(ArenaRegion region, File snapshotFile, boolean clearEntities) throws IOException {
        long startTime = System.currentTimeMillis();
        World world = region.getWorld();
        if (world == null) {
            throw new IllegalStateException("World '" + region.getWorldName() + "' is not loaded or does not exist.");
        }

        if (!snapshotFile.exists()) {
            throw new FileNotFoundException("Snapshot file does not exist: " + snapshotFile.getAbsolutePath());
        }

        int minX = region.getMinX();
        int minY = region.getMinY();
        int minZ = region.getMinZ();
        int maxX = region.getMaxX();
        int maxZ = region.getMaxZ();

        // Ensure chunks are loaded
        ensureChunksLoaded(world, minX, maxX, minZ, maxZ);

        BlockData[] palette;
        short[] blockIndices;
        List<TileData> tileDataList = new ArrayList<>();
        int sizeX, sizeY, sizeZ;

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(snapshotFile))))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid snapshot file format (magic mismatch).");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported snapshot version: " + version);
            }

            sizeX = in.readInt();
            sizeY = in.readInt();
            sizeZ = in.readInt();

            if (sizeX != region.getWidth() || sizeY != region.getHeight() || sizeZ != region.getLength()) {
                throw new IllegalStateException("Snapshot bounds (" + sizeX + "x" + sizeY + "x" + sizeZ +
                        ") do not match arena region bounds (" + region.getWidth() + "x" + region.getHeight() + "x" + region.getLength() + ")");
            }

            int paletteSize = in.readInt();
            palette = new BlockData[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                String str = in.readUTF();
                palette[i] = Bukkit.createBlockData(str);
            }

            int totalBlocks = sizeX * sizeY * sizeZ;
            blockIndices = new short[totalBlocks];
            for (int i = 0; i < totalBlocks; i++) {
                blockIndices[i] = in.readShort();
            }

            // Read tile entities if available
            if (in.available() > 0) {
                int tileCount = in.readInt();
                for (int i = 0; i < tileCount; i++) {
                    tileDataList.add(TileData.read(in));
                }
            }
        }

        // Apply block updates (physics = false to avoid lighting/block cascades and drops)
        int modifiedCount = 0;
        int idx = 0;
        for (int y = minY; y <= region.getMaxY(); y++) {
            for (int z = minZ; z <= region.getMaxZ(); z++) {
                for (int x = minX; x <= region.getMaxX(); x++) {
                    BlockData targetData = palette[blockIndices[idx++]];
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getBlockData().matches(targetData)) {
                        block.setBlockData(targetData, false);
                        modifiedCount++;
                    }
                }
            }
        }

        // Apply tile entities
        for (TileData td : tileDataList) {
            td.apply(world, minX, minY, minZ);
        }

        // Clear loose entities if enabled
        if (clearEntities) {
            BoundingBox box = new BoundingBox(minX, minY, minZ, region.getMaxX() + 1.0, region.getMaxY() + 1.0, region.getMaxZ() + 1.0);
            for (Entity entity : world.getNearbyEntities(box)) {
                if (entity instanceof Player) {
                    continue; // Never touch players
                }
                entity.remove();
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new RestoreResult(modifiedCount, (long) sizeX * sizeY * sizeZ, elapsed);
    }

    private static void ensureChunksLoaded(World world, int minX, int maxX, int minZ, int maxZ) {
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    world.loadChunk(cx, cz, true);
                }
            }
        }
    }

    // ========================================================
    // Tile Entity Data Abstractions
    // ========================================================

    private interface TileData {
        void write(DataOutputStream out) throws IOException;

        void apply(World world, int minX, int minY, int minZ);

        static TileData read(DataInputStream in) throws IOException {
            byte type = in.readByte();
            if (type == TILE_TYPE_CONTAINER) {
                return ContainerTileData.readContainer(in);
            } else if (type == TILE_TYPE_SIGN) {
                return SignTileData.readSign(in);
            }
            throw new IOException("Unknown tile entity type: " + type);
        }
    }

    private static class ContainerTileData implements TileData {
        private final int relX, relY, relZ;
        private final Map<Integer, byte[]> serializedSlots;

        public ContainerTileData(int relX, int relY, int relZ, ItemStack[] contents) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.serializedSlots = new HashMap<>();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null && !item.getType().isAir()) {
                    serializedSlots.put(i, item.serializeAsBytes());
                }
            }
        }

        private ContainerTileData(int relX, int relY, int relZ, Map<Integer, byte[]> serializedSlots) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.serializedSlots = serializedSlots;
        }

        @Override
        public void write(DataOutputStream out) throws IOException {
            out.writeByte(TILE_TYPE_CONTAINER);
            out.writeInt(relX);
            out.writeInt(relY);
            out.writeInt(relZ);
            out.writeInt(serializedSlots.size());
            for (Map.Entry<Integer, byte[]> entry : serializedSlots.entrySet()) {
                out.writeInt(entry.getKey());
                byte[] bytes = entry.getValue();
                out.writeInt(bytes.length);
                out.write(bytes);
            }
        }

        public static ContainerTileData readContainer(DataInputStream in) throws IOException {
            int relX = in.readInt();
            int relY = in.readInt();
            int relZ = in.readInt();
            int count = in.readInt();
            Map<Integer, byte[]> slots = new HashMap<>(count);
            for (int i = 0; i < count; i++) {
                int slot = in.readInt();
                int len = in.readInt();
                byte[] bytes = new byte[len];
                in.readFully(bytes);
                slots.put(slot, bytes);
            }
            return new ContainerTileData(relX, relY, relZ, slots);
        }

        @Override
        public void apply(World world, int minX, int minY, int minZ) {
            Block block = world.getBlockAt(minX + relX, minY + relY, minZ + relZ);
            BlockState state = block.getState(false);
            if (state instanceof Container container) {
                container.getInventory().clear();
                for (Map.Entry<Integer, byte[]> entry : serializedSlots.entrySet()) {
                    int slot = entry.getKey();
                    if (slot < container.getInventory().getSize()) {
                        try {
                            ItemStack item = ItemStack.deserializeBytes(entry.getValue());
                            container.getInventory().setItem(slot, item);
                        } catch (Exception ignored) {
                        }
                    }
                }
                container.update(true, false);
            }
        }
    }

    private static class SignTileData implements TileData {
        private final int relX, relY, relZ;
        private final String[] frontLines;
        private final String[] backLines;

        public SignTileData(int relX, int relY, int relZ, Sign sign) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;

            LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
            this.frontLines = new String[4];
            for (int i = 0; i < 4; i++) {
                Component c = sign.getSide(Side.FRONT).line(i);
                this.frontLines[i] = c != null ? serializer.serialize(c) : "";
            }

            this.backLines = new String[4];
            for (int i = 0; i < 4; i++) {
                Component c = sign.getSide(Side.BACK).line(i);
                this.backLines[i] = c != null ? serializer.serialize(c) : "";
            }
        }

        private SignTileData(int relX, int relY, int relZ, String[] frontLines, String[] backLines) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.frontLines = frontLines;
            this.backLines = backLines;
        }

        @Override
        public void write(DataOutputStream out) throws IOException {
            out.writeByte(TILE_TYPE_SIGN);
            out.writeInt(relX);
            out.writeInt(relY);
            out.writeInt(relZ);
            for (int i = 0; i < 4; i++) {
                out.writeUTF(frontLines[i]);
            }
            for (int i = 0; i < 4; i++) {
                out.writeUTF(backLines[i]);
            }
        }

        public static SignTileData readSign(DataInputStream in) throws IOException {
            int relX = in.readInt();
            int relY = in.readInt();
            int relZ = in.readInt();
            String[] front = new String[4];
            for (int i = 0; i < 4; i++) {
                front[i] = in.readUTF();
            }
            String[] back = new String[4];
            for (int i = 0; i < 4; i++) {
                back[i] = in.readUTF();
            }
            return new SignTileData(relX, relY, relZ, front, back);
        }

        @Override
        public void apply(World world, int minX, int minY, int minZ) {
            Block block = world.getBlockAt(minX + relX, minY + relY, minZ + relZ);
            BlockState state = block.getState(false);
            if (state instanceof Sign sign) {
                LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
                for (int i = 0; i < 4; i++) {
                    sign.getSide(Side.FRONT).line(i, serializer.deserialize(frontLines[i]));
                    sign.getSide(Side.BACK).line(i, serializer.deserialize(backLines[i]));
                }
                sign.update(true, false);
            }
        }
    }
}
