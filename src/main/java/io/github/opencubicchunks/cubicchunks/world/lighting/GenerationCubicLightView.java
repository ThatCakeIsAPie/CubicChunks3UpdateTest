package io.github.opencubicchunks.cubicchunks.world.lighting;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.server.level.GenerationCloHolder;
import io.github.opencubicchunks.cubicchunks.util.StaticCache3D;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.server.level.GenerationChunkHolder;

/**
 * The cube being lit, plus whatever neighbors the generation cache has already produced.
 */
public final class GenerationCubicLightView implements CubicLightView {
    private final CubeAccess self;
    private final @Nullable StaticCache3D<GenerationChunkHolder> cache;

    public GenerationCubicLightView(CubeAccess self, @Nullable StaticCache3D<GenerationChunkHolder> cache) {
        this.self = self;
        this.cache = cache;
    }

    @Override public @Nullable CubeAccess cubeAt(int cubeX, int cubeY, int cubeZ) {
        var selfPos = this.self.cc_getCubePos();
        if (selfPos.getX() == cubeX && selfPos.getY() == cubeY && selfPos.getZ() == cubeZ) {
            return this.self;
        }
        if (this.cache == null || !this.cache.contains(cubeX, cubeY, cubeZ)) {
            return null;
        }
        GenerationChunkHolder holder = this.cache.get(cubeX, cubeY, cubeZ);
        if (holder == null) {
            return null;
        }
        CloAccess clo = ((GenerationCloHolder) (Object) holder).cc_getLatestClo();
        if (clo instanceof CubeAccess cube) {
            return cube;
        }
        return null;
    }
}
