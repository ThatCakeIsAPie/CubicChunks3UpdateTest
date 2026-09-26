package io.github.opencubicchunks.cubicchunks.test.world.level.cube.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.world.level.cube.ProtoCube;
import io.github.opencubicchunks.cubicchunks.world.level.cube.status.CubeStatusTasks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Answers;

/**
 * Locks the temporary sinusoidal fill in {@link CubeStatusTasks#generateNoise}.
 * <p>
 * Stone where {@code y + round(amplitude * (sin((x)/8 + (z)/21) + cos(z/13)) / 2) <= SUPERFLAT_HEIGHT}, with
 * {@code amplitude = 20} and {@code SUPERFLAT_HEIGHT = 5}, and only for {@code y} in
 * {@code [minCubeY, min(maxCubeY, SUPERFLAT_HEIGHT + amplitude)]}. {@code x} and {@code z} are world block coordinates
 * (the generator adds cube-local x/z to {@code minCubeX}/{@code minCubeZ}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestCubeStatusTasks extends BaseTest {
    /** Matches the hardcoded amplitude in {@link CubeStatusTasks#generateNoise}. */
    private static final int AMPLITUDE = 20;

    private static final BlockState SMOOTH_STONE = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private ProtoCube makeProtoCube(CubePos cubePos) {
        LevelHeightAccessor heightAccessor = mock(Answers.RETURNS_DEEP_STUBS);
        when(heightAccessor.getMinY()).thenReturn(-(1 << 24));
        when(heightAccessor.getMaxY()).thenReturn(1 << 24);
        when(heightAccessor.getHeight()).thenReturn(1 << 25);
        when(heightAccessor.isOutsideBuildHeight(any())).thenReturn(false);
        when(heightAccessor.isOutsideBuildHeight(any(int.class))).thenReturn(false);
        return new ProtoCube(cubePos, mock(Answers.RETURNS_DEEP_STUBS), heightAccessor, mock(Answers.RETURNS_DEEP_STUBS),
                mock(Answers.RETURNS_DEEP_STUBS));
    }

    private ProtoCube fill(CubePos cubePos) {
        ProtoCube cube = makeProtoCube(cubePos);
        var filled = CubeStatusTasks.generateNoise(null, null, null, cube).join();
        assertSame(cube, filled);
        return cube;
    }

    /**
     * @return true when {@link CubeStatusTasks#generateNoise} writes smooth stone at this world position
     */
    private static boolean expectsSmoothStone(int worldX, int worldY, int worldZ, CubePos cubePos) {
        int minY = cubePos.minCubeY();
        int maxFilledY = Math.min(cubePos.maxCubeY(), CubicChunks.SUPERFLAT_HEIGHT + AMPLITUDE);
        if (worldY < minY || worldY > maxFilledY) {
            return false;
        }
        long offset = Math.round((AMPLITUDE * (Math.sin(worldX / 8.0 + worldZ / 21.0) + Math.cos(worldZ / 13.0))) / 2.0);
        return worldY + offset <= CubicChunks.SUPERFLAT_HEIGHT;
    }

    private static BlockState expectedState(int worldX, int worldY, int worldZ, CubePos cubePos) {
        return expectsSmoothStone(worldX, worldY, worldZ, cubePos) ? SMOOTH_STONE : AIR;
    }

    private void assertAllBlocks(ProtoCube cube, CubePos cubePos) {
        int diameter = CubicConstants.DIAMETER_IN_BLOCKS;
        for (int localX = 0; localX < diameter; localX++) {
            for (int localY = 0; localY < diameter; localY++) {
                for (int localZ = 0; localZ < diameter; localZ++) {
                    BlockPos pos = cubePos.asBlockPos(localX, localY, localZ);
                    assertEquals(expectedState(pos.getX(), pos.getY(), pos.getZ(), cubePos), cube.getBlockState(pos), pos::toShortString);
                }
            }
        }
    }

    @Test
    public void generateNoise_originCube_matchesSinusoid() {
        // Samples below assume the default 32-block cube. (0,0) surfaces at y=-5; (31,31) surfaces at y=20.
        assertEquals(32, CubicConstants.DIAMETER_IN_BLOCKS);
        CubePos origin = CubePos.of(0, 0, 0);
        ProtoCube cube = fill(origin);

        assertEquals(AIR, cube.getBlockState(new BlockPos(0, 0, 0)));
        assertEquals(SMOOTH_STONE, cube.getBlockState(new BlockPos(31, 20, 31)));
        assertEquals(AIR, cube.getBlockState(new BlockPos(31, 21, 31)));
        // Cube extends to y=31, but the fill stops at SUPERFLAT_HEIGHT + amplitude (25).
        assertEquals(AIR, cube.getBlockState(new BlockPos(31, 26, 31)));
        assertEquals(SMOOTH_STONE, cube.getBlockState(new BlockPos(31, 0, 31)));

        assertAllBlocks(cube, origin);
    }

    @Test
    public void generateNoise_shiftedCube_usesWorldXZ() {
        // Cube (2, 0, -1) covers x[64,95], y[0,31], z[-32,-1]. Surfaces differ from the same local column in cube 0.
        assertEquals(32, CubicConstants.DIAMETER_IN_BLOCKS);
        CubePos shifted = CubePos.of(2, 0, -1);
        ProtoCube cube = fill(shifted);

        assertEquals(SMOOTH_STONE, cube.getBlockState(new BlockPos(64, 11, -32)));
        assertEquals(AIR, cube.getBlockState(new BlockPos(64, 12, -32)));
        assertEquals(SMOOTH_STONE, cube.getBlockState(new BlockPos(95, 18, -25)));
        assertEquals(AIR, cube.getBlockState(new BlockPos(95, 19, -25)));

        assertAllBlocks(cube, shifted);
    }

    @Test
    public void generateNoise_cubeAboveSurface_staysEmpty() {
        // First cube whose min Y is above SUPERFLAT_HEIGHT + amplitude.
        assertEquals(32, CubicConstants.DIAMETER_IN_BLOCKS);
        CubePos above = CubePos.of(0, 1, 0);
        ProtoCube cube = fill(above);

        assertEquals(AIR, cube.getBlockState(above.asBlockPos()));
        assertEquals(AIR, cube.getBlockState(above.asBlockPos(31, 31, 31)));
        assertAllBlocks(cube, above);
    }

    @Test
    public void generateNoise_cubeEntirelyBelowSurface_isSolid() {
        // Cube (0, -2, 0) is y[-64,-33]. The lowest sinusoid surface is y=-15, so every block is stone.
        assertEquals(32, CubicConstants.DIAMETER_IN_BLOCKS);
        CubePos below = CubePos.of(0, -2, 0);
        ProtoCube cube = fill(below);

        assertEquals(SMOOTH_STONE, cube.getBlockState(below.asBlockPos()));
        assertEquals(SMOOTH_STONE, cube.getBlockState(below.asBlockPos(31, 31, 31)));
        assertAllBlocks(cube, below);
    }
}
