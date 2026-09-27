package io.github.opencubicchunks.cubicchunks.world.level.cube;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface CubeSource {
    @Nullable CubeAccess cc_getCube(int x, int y, int z, ChunkStatus status, boolean forceLoad);

    @Nullable LevelCube cc_getCube(int x, int y, int z, boolean forceLoad);

    @Nullable LevelCube cc_getCubeNow(int x, int y, int z);

    /**
     * Cube used by the cubic light engine. Does not generate or load a missing cube.
     */
    default @Nullable BlockGetter cc_getCubeForLighting(int cubeX, int cubeY, int cubeZ) {
        return this.cc_getCube(cubeX, cubeY, cubeZ, ChunkStatus.EMPTY, false);
    }

    boolean cc_hasCube(int x, int y, int z);

    int cc_getLoadedCubeCount();

    boolean cc_updateCubeForced(CubePos cubePos, boolean forced);
}
