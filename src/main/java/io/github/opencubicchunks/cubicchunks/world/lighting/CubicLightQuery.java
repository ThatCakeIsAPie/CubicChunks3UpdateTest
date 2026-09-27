package io.github.opencubicchunks.cubicchunks.world.lighting;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.DataLayer;

/** Reads cube light for the vanilla light-engine hooks. */
public final class CubicLightQuery {
    private CubicLightQuery() {}

    public static int blockLight(Level level, BlockPos pos) {
        return light(level, pos, false);
    }

    public static int skyLight(Level level, BlockPos pos) {
        return light(level, pos, true);
    }

    public static DataLayer dataLayer(Level level, SectionPos sectionPos, boolean sky) {
        CubeAccess cube = cube(level, sectionPos.origin());
        if (cube == null) {
            return new DataLayer();
        }
        int index = Coords.blockToIndex(sectionPos.origin());
        byte[] layer = sky ? cube.cc_lightData().skySection(index) : cube.cc_lightData().blockSection(index);
        if (layer == null) {
            return new DataLayer();
        }
        return new DataLayer(layer.clone());
    }

    private static int light(Level level, BlockPos pos, boolean sky) {
        CubeAccess cube = cube(level, pos);
        if (cube == null) {
            return 0;
        }
        return sky ? cube.cc_lightData().skyLight(pos) : cube.cc_lightData().blockLight(pos);
    }

    private static @Nullable CubeAccess cube(Level level, BlockPos pos) {
        if (!(level.getChunkSource() instanceof CubeSource source)) {
            return null;
        }
        var getter = source.cc_getCubeForLighting(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ()));
        if (getter instanceof CubeAccess cube) {
            return cube;
        }
        return null;
    }
}
