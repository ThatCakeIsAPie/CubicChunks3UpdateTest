package io.github.opencubicchunks.cubicchunks.world.lighting;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import net.minecraft.world.level.BlockGetter;

/** Cubes already loaded in a {@link CubeSource}. Does not generate missing cubes. */
public final class SourceCubicLightView implements CubicLightView {
    private final CubeSource source;

    public SourceCubicLightView(CubeSource source) {
        this.source = source;
    }

    @Override public @Nullable CubeAccess cubeAt(int cubeX, int cubeY, int cubeZ) {
        BlockGetter getter = this.source.cc_getCubeForLighting(cubeX, cubeY, cubeZ);
        if (getter instanceof CubeAccess cube) {
            return cube;
        }
        return null;
    }
}
