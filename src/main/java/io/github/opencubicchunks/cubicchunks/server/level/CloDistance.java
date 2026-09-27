package io.github.opencubicchunks.cubicchunks.server.level;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import net.minecraft.world.phys.Vec3;

/**
 * Distance from a player to a column or a cube, for send priority.
 * <p>
 * Column positions use the cube column that contains the chunk. Cube positions use all three axes. An earlier helper
 * passed the cube X into the Y and Z center, so a cube above the player sorted as if it were on the same Y.
 */
public final class CloDistance {
    private CloDistance() {}

    /**
     * Squared distance from {@code point} to the center of {@code cloPos} when it is a cube.
     * Callers that still have a column should keep the vanilla column distance.
     */
    public static double squaredToCubeCenter(CloPos cloPos, Vec3 point) {
        if (!cloPos.isCube()) {
            throw new IllegalArgumentException("squaredToCubeCenter requires a cube position");
        }
        double dx = Coords.cubeToCenterBlock(cloPos.getX()) - point.x;
        double dy = Coords.cubeToCenterBlock(cloPos.getY()) - point.y;
        double dz = Coords.cubeToCenterBlock(cloPos.getZ()) - point.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Chebyshev distance in cubes from {@code center}, which must be a cube. A column contributes only its XZ cube column
     * (distance on Y is zero), matching a player standing in that column.
     */
    public static int chebyshevCubes(CloPos center, CloPos pos) {
        if (!center.isCube()) {
            throw new IllegalArgumentException("center must be a cube");
        }
        int dx;
        int dy;
        int dz;
        if (pos.isChunk()) {
            dx = Math.abs(Coords.sectionToCube(pos.getX()) - center.getX());
            dy = 0;
            dz = Math.abs(Coords.sectionToCube(pos.getZ()) - center.getZ());
        } else {
            dx = Math.abs(pos.getX() - center.getX());
            dy = Math.abs(pos.getY() - center.getY());
            dz = Math.abs(pos.getZ() - center.getZ());
        }
        return Math.max(dx, Math.max(dy, dz));
    }
}
