package io.github.opencubicchunks.cubicchunks.test.world.level.cube.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.levelgen.CubicOverworldGenerator;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.world.level.cube.ProtoCube;
import io.github.opencubicchunks.cubicchunks.world.level.cube.status.CubeStatusTasks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Answers;

/**
 * {@link CubeStatusTasks#generateNoise} fills a cube from {@link CubicOverworldGenerator} and does not wait on neighbors.
 * A null world-gen context uses {@link CubicOverworldGenerator#DEFAULT_SEED}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestCubeStatusTasks extends BaseTest {
    @Test
    public void generateNoise_matchesOverworldGenerator() {
        assertEquals(32, CubicConstants.DIAMETER_IN_BLOCKS);
        CubePos origin = CubePos.of(0, 0, 0);
        ProtoCube cube = fill(origin);
        int minX = origin.minCubeX();
        int minY = origin.minCubeY();
        int minZ = origin.minCubeZ();
        int diameter = CubicConstants.DIAMETER_IN_BLOCKS;
        for (int x = 0; x < diameter; x++) {
            for (int y = 0; y < diameter; y++) {
                for (int z = 0; z < diameter; z++) {
                    BlockPos pos = new BlockPos(minX + x, minY + y, minZ + z);
                    assertEquals(CubicOverworldGenerator.blockState(CubicOverworldGenerator.DEFAULT_SEED, pos.getX(), pos.getY(), pos.getZ()),
                            cube.getBlockState(pos), pos::toShortString);
                }
            }
        }
        assertEquals(0, count(cube, Blocks.SMOOTH_STONE.defaultBlockState()));
        assertEquals(0, count(cube, Blocks.BEDROCK.defaultBlockState()));
    }

    @Test
    public void generateBiomes_nullContext_returnsTheCube() {
        CubePos pos = CubePos.of(0, 1, 0);
        LevelHeightAccessor heightAccessor = org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(heightAccessor.getMinY()).thenReturn(-(1 << 24));
        org.mockito.Mockito.when(heightAccessor.getMaxY()).thenReturn(1 << 24);
        org.mockito.Mockito.when(heightAccessor.getHeight()).thenReturn(1 << 25);
        org.mockito.Mockito.when(heightAccessor.isOutsideBuildHeight(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        org.mockito.Mockito.when(heightAccessor.isOutsideBuildHeight(org.mockito.ArgumentMatchers.any(int.class))).thenReturn(false);
        ProtoCube cube = new ProtoCube(pos, org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS), heightAccessor,
                org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS), org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS));
        var filled = CubeStatusTasks.generateBiomes(null, null, null, cube).join();
        assertSame(cube, filled);
    }

    @Test
    public void generateNoise_nullContext_usesDefaultSeed() {
        CubePos pos = CubePos.of(1, -1, 3);
        ProtoCube cube = fill(pos);
        BlockPos sample = pos.asBlockPos(4, 8, 12);
        assertEquals(CubicOverworldGenerator.blockState(CubicOverworldGenerator.DEFAULT_SEED, sample.getX(), sample.getY(), sample.getZ()),
                cube.getBlockState(sample));
    }

    private ProtoCube fill(CubePos cubePos) {
        LevelHeightAccessor heightAccessor = org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(heightAccessor.getMinY()).thenReturn(-(1 << 24));
        org.mockito.Mockito.when(heightAccessor.getMaxY()).thenReturn(1 << 24);
        org.mockito.Mockito.when(heightAccessor.getHeight()).thenReturn(1 << 25);
        org.mockito.Mockito.when(heightAccessor.isOutsideBuildHeight(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        org.mockito.Mockito.when(heightAccessor.isOutsideBuildHeight(org.mockito.ArgumentMatchers.any(int.class))).thenReturn(false);
        ProtoCube cube = new ProtoCube(cubePos, org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS), heightAccessor,
                org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS), org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS));
        var filled = CubeStatusTasks.generateNoise(null, null, null, cube).join();
        assertSame(cube, filled);
        return cube;
    }

    private static int count(ProtoCube cube, net.minecraft.world.level.block.state.BlockState state) {
        CubePos pos = cube.cc_getCubePos();
        int found = 0;
        int diameter = CubicConstants.DIAMETER_IN_BLOCKS;
        for (int x = 0; x < diameter; x++) {
            for (int y = 0; y < diameter; y++) {
                for (int z = 0; z < diameter; z++) {
                    if (cube.getBlockState(pos.asBlockPos(x, y, z)).equals(state)) {
                        found++;
                    }
                }
            }
        }
        return found;
    }
}
