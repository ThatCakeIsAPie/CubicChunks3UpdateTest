package io.github.opencubicchunks.cubicchunks.world.lighting;

import io.github.opencubicchunks.cc_core.api.CubePos;

/**
 * Temporary block and sky levels for a cube plus a {@link CubicLightEngine#MAX_LEVEL}-block margin.
 * Values outside the target cube are discarded after the flood.
 */
final class LightVolume {
    /** Local offsets inside a light volume fit in this many bits. A cube plus a 15-block margin is far smaller. */
    private static final int COORD_SHIFT = 21;
    private static final long COORD_MASK = (1L << COORD_SHIFT) - 1;

    final int minX;
    final int minY;
    final int minZ;
    final int size;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final int targetMinX;
    private final int targetMinY;
    private final int targetMinZ;
    private final int targetMaxX;
    private final int targetMaxY;
    private final int targetMaxZ;
    private final byte[] block;
    private final byte[] sky;

    LightVolume(CubePos cubePos) {
        this.targetMinX = cubePos.minCubeX();
        this.targetMinY = cubePos.minCubeY();
        this.targetMinZ = cubePos.minCubeZ();
        this.targetMaxX = cubePos.maxCubeX();
        this.targetMaxY = cubePos.maxCubeY();
        this.targetMaxZ = cubePos.maxCubeZ();
        this.minX = this.targetMinX - CubicLightEngine.MAX_LEVEL;
        this.minY = this.targetMinY - CubicLightEngine.MAX_LEVEL;
        this.minZ = this.targetMinZ - CubicLightEngine.MAX_LEVEL;
        this.size = (this.targetMaxX - this.targetMinX + 1) + CubicLightEngine.MAX_LEVEL * 2;
        this.maxX = this.minX + this.size - 1;
        this.maxY = this.minY + this.size - 1;
        this.maxZ = this.minZ + this.size - 1;
        int volume = this.size * this.size * this.size;
        this.block = new byte[volume];
        this.sky = new byte[volume];
    }

    int maxY() {
        return this.maxY;
    }

    int minY() {
        return this.minY;
    }

    boolean contains(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    boolean inTarget(int x, int y, int z) {
        return x >= this.targetMinX && x <= this.targetMaxX && y >= this.targetMinY && y <= this.targetMaxY && z >= this.targetMinZ
                && z <= this.targetMaxZ;
    }

    boolean inTargetColumn(int x, int z) {
        return x >= this.targetMinX && x <= this.targetMaxX && z >= this.targetMinZ && z <= this.targetMaxZ;
    }

    int getBlock(int x, int y, int z) {
        return this.block[this.index(x, y, z)];
    }

    int getSky(int x, int y, int z) {
        return this.sky[this.index(x, y, z)];
    }

    void setBlock(int x, int y, int z, int value) {
        this.block[this.index(x, y, z)] = (byte) value;
    }

    void setSky(int x, int y, int z, int value) {
        this.sky[this.index(x, y, z)] = (byte) value;
    }

    /**
     * Queue key for a block inside this volume. Vanilla {@code BlockPos.asLong} keeps only 12 bits of Y, which wraps
     * cubic worlds and indexes this array out of bounds.
     */
    long pack(int x, int y, int z) {
        return ((long) (x - this.minX) << COORD_SHIFT * 2) | ((long) (y - this.minY) << COORD_SHIFT) | (z - this.minZ);
    }

    int unpackX(long packed) {
        return this.minX + (int) (packed >>> COORD_SHIFT * 2);
    }

    int unpackY(long packed) {
        return this.minY + (int) ((packed >>> COORD_SHIFT) & COORD_MASK);
    }

    int unpackZ(long packed) {
        return this.minZ + (int) (packed & COORD_MASK);
    }

    private int index(int x, int y, int z) {
        return ((x - this.minX) * this.size + (y - this.minY)) * this.size + (z - this.minZ);
    }
}
