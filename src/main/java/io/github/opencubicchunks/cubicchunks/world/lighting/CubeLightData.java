package io.github.opencubicchunks.cubicchunks.world.lighting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import net.minecraft.core.BlockPos;

/**
 * Block and sky nibbles for one cube, one 2048-byte layer per section. Null layers are all zero.
 * <p>
 * Nibble order matches vanilla {@code DataLayer}: index {@code (y << 8) | (z << 4) | x} inside the section, low nibble first.
 */
public final class CubeLightData {
    public static final int SECTION_VOLUME = CubicConstants.SECTION_DIAMETER * CubicConstants.SECTION_DIAMETER * CubicConstants.SECTION_DIAMETER;
    public static final int BYTES_PER_SECTION = SECTION_VOLUME / 2;

    private static final int SECTION_MASK = CubicConstants.SECTION_DIAMETER - 1;
    private static final int Y_SHIFT = 8;
    private static final int Z_SHIFT = 4;
    private static final int NIBBLE_SHIFT = 2;
    private static final int HIGH_NIBBLE_SHIFT = NIBBLE_SHIFT * 2;
    private static final int NIBBLE_MASK = 15;
    private static final int UNSIGNED_BYTE = 0xFF;

    private final byte[][] block = new byte[CubicConstants.SECTION_COUNT][];
    private final byte[][] sky = new byte[CubicConstants.SECTION_COUNT][];

    public int blockLight(BlockPos pos) {
        return this.blockLight(pos.getX(), pos.getY(), pos.getZ());
    }

    public int blockLight(int x, int y, int z) {
        return this.get(this.block, x, y, z);
    }

    public int skyLight(BlockPos pos) {
        return this.skyLight(pos.getX(), pos.getY(), pos.getZ());
    }

    public int skyLight(int x, int y, int z) {
        return this.get(this.sky, x, y, z);
    }

    public void setBlockLight(int x, int y, int z, int value) {
        this.set(this.block, x, y, z, value);
    }

    public void setSkyLight(int x, int y, int z, int value) {
        this.set(this.sky, x, y, z, value);
    }

    public @Nullable byte[] blockSection(int sectionIndex) {
        return this.block[sectionIndex];
    }

    public @Nullable byte[] skySection(int sectionIndex) {
        return this.sky[sectionIndex];
    }

    public void clear() {
        for (int i = 0; i < this.block.length; i++) {
            this.block[i] = null;
            this.sky[i] = null;
        }
    }

    /** Every block in the cube gets this sky level, and block light is cleared. */
    public void fillSky(int level) {
        this.clear();
        if (level == 0) {
            return;
        }
        byte packed = (byte) ((level << HIGH_NIBBLE_SHIFT) | level);
        byte[] layer = new byte[BYTES_PER_SECTION];
        Arrays.fill(layer, packed);
        for (int i = 0; i < this.sky.length; i++) {
            this.sky[i] = layer.clone();
        }
    }

    public void copyFrom(CubeLightData other) {
        for (int i = 0; i < this.block.length; i++) {
            this.block[i] = copyLayer(other.block[i]);
            this.sky[i] = copyLayer(other.sky[i]);
        }
    }

    public byte[] writePacked() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            DataOutputStream data = new DataOutputStream(bytes);
            data.writeInt(this.block.length);
            for (int i = 0; i < this.block.length; i++) {
                writeLayer(data, this.block[i]);
                writeLayer(data, this.sky[i]);
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return bytes.toByteArray();
    }

    public void readPacked(byte[] packed) {
        this.clear();
        try {
            DataInputStream data = new DataInputStream(new ByteArrayInputStream(packed));
            int count = data.readInt();
            int limit = Math.min(count, this.block.length);
            for (int i = 0; i < limit; i++) {
                this.block[i] = readLayer(data);
                this.sky[i] = readLayer(data);
            }
        } catch (IOException ex) {
            this.clear();
            throw new IllegalArgumentException("Truncated cube light", ex);
        }
    }

    private int get(byte[][] layers, int x, int y, int z) {
        byte[] data = layers[Coords.blockToIndex(x, y, z)];
        if (data == null) {
            return 0;
        }
        return nibble(data, localIndex(x, y, z));
    }

    private void set(byte[][] layers, int x, int y, int z, int value) {
        int section = Coords.blockToIndex(x, y, z);
        byte[] data = layers[section];
        if (data == null) {
            if (value == 0) {
                return;
            }
            data = new byte[BYTES_PER_SECTION];
            layers[section] = data;
        }
        setNibble(data, localIndex(x, y, z), value);
    }

    private static int localIndex(int x, int y, int z) {
        return (y & SECTION_MASK) << Y_SHIFT | (z & SECTION_MASK) << Z_SHIFT | (x & SECTION_MASK);
    }

    private static int nibble(byte[] data, int local) {
        int shift = (local & 1) << NIBBLE_SHIFT;
        return (data[local >>> 1] >> shift) & NIBBLE_MASK;
    }

    private static void setNibble(byte[] data, int local, int value) {
        int byteIndex = local >>> 1;
        int shift = (local & 1) << NIBBLE_SHIFT;
        int mask = NIBBLE_MASK << shift;
        int current = data[byteIndex] & UNSIGNED_BYTE;
        data[byteIndex] = (byte) ((current & ~mask) | ((value & NIBBLE_MASK) << shift));
    }

    private static void writeLayer(DataOutputStream data, @Nullable byte[] layer) throws IOException {
        if (layer == null) {
            data.writeInt(0);
            return;
        }
        data.writeInt(layer.length);
        data.write(layer);
    }

    private static @Nullable byte[] readLayer(DataInputStream data) throws IOException {
        int length = data.readInt();
        if (length == 0) {
            return null;
        }
        if (length != BYTES_PER_SECTION) {
            throw new IOException("Unexpected light layer length " + length);
        }
        byte[] layer = new byte[length];
        data.readFully(layer);
        return layer;
    }

    private static @Nullable byte[] copyLayer(@Nullable byte[] layer) {
        return layer == null ? null : layer.clone();
    }
}
