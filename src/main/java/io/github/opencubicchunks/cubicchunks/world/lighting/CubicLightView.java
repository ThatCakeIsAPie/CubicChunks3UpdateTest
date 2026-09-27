package io.github.opencubicchunks.cubicchunks.world.lighting;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;

/**
 * Loaded cubes visible to one light computation. Missing cubes are unloaded: block light does not pass through them, and
 * direct sky light treats unloaded space above a cube's own columns as open sky.
 */
@FunctionalInterface
public interface CubicLightView {
    @Nullable CubeAccess cubeAt(int cubeX, int cubeY, int cubeZ);

    /**
     * @return a view that can see {@code cube} and nothing else
     */
    static CubicLightView only(CubeAccess cube) {
        var pos = cube.cc_getCubePos();
        return (cubeX, cubeY, cubeZ) -> pos.getX() == cubeX && pos.getY() == cubeY && pos.getZ() == cubeZ ? cube : null;
    }
}
