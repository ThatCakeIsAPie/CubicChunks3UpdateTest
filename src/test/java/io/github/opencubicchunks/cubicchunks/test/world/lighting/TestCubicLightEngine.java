package io.github.opencubicchunks.cubicchunks.test.world.lighting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.ProtoCube;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubeLightData;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubeLightPacketData;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Answers;

/**
 * Block light on cubes, and the sky heuristic: unloaded space above a column is open sky, and an opaque block in a loaded
 * cube within {@link CubicLightEngine#SKY_SEARCH_BLOCKS} blocks occludes it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestCubicLightEngine extends BaseTest {
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState TORCH = Blocks.TORCH.defaultBlockState();

    @Test
    public void torchInSolidCubeCastsLightAndStoneOccludes() {
        ProtoCube cube = stoneCube(CubePos.of(0, 0, 0));
        // Tunnel along X through solid stone: torch, air, stone wall, air.
        set(cube, 10, 16, 16, AIR);
        set(cube, 11, 16, 16, AIR);
        set(cube, 13, 16, 16, AIR);
        set(cube, 10, 16, 16, TORCH);

        CubicLightEngine.relight(cube, CubicLightView.only(cube));

        int emission = TORCH.getLightEmission();
        CubeLightData light = cube.cc_lightData();
        assertTrue(emission > 1, "torch should emit light");
        assertEquals(emission, light.blockLight(block(cube, 10, 16, 16)));
        assertEquals(emission - 1, light.blockLight(block(cube, 11, 16, 16)));
        assertEquals(0, light.blockLight(block(cube, 12, 16, 16)), "stone wall");
        assertEquals(0, light.blockLight(block(cube, 13, 16, 16)), "air behind the wall");
        assertEquals(0, light.skyLight(block(cube, 11, 16, 16)), "solid cube has no sky");
        assertEquals(0, light.blockLight(block(cube, 10, 15, 16)), "stone beside the torch");
    }

    @Test
    public void torchAcrossCubeBorderLightsNeighbor() {
        ProtoCube source = stoneCube(CubePos.of(0, 0, 0));
        ProtoCube neighbor = stoneCube(CubePos.of(1, 0, 0));
        int y = 16;
        int z = 16;
        set(source, CubicConstants.DIAMETER_IN_BLOCKS - 1, y, z, TORCH);
        set(neighbor, 0, y, z, AIR);
        set(neighbor, 1, y, z, AIR);
        Cubes view = new Cubes();
        view.add(source);
        view.add(neighbor);

        CubicLightEngine.relight(neighbor, view);

        int emission = TORCH.getLightEmission();
        assertEquals(emission - 1, neighbor.cc_lightData().blockLight(block(neighbor, 0, y, z)));
        assertEquals(emission - 2, neighbor.cc_lightData().blockLight(block(neighbor, 1, y, z)));
    }

    @Test
    public void stoneOnCubeBorderOccludesNeighbor() {
        ProtoCube source = stoneCube(CubePos.of(0, 0, 0));
        ProtoCube neighbor = stoneCube(CubePos.of(1, 0, 0));
        int y = 16;
        int z = 16;
        set(source, CubicConstants.DIAMETER_IN_BLOCKS - 1, y, z, TORCH);
        // Neighbor face stays stone; the block behind it is air.
        set(neighbor, 1, y, z, AIR);
        Cubes view = new Cubes();
        view.add(source);
        view.add(neighbor);

        CubicLightEngine.relight(neighbor, view);

        assertEquals(0, neighbor.cc_lightData().blockLight(block(neighbor, 0, y, z)));
        assertEquals(0, neighbor.cc_lightData().blockLight(block(neighbor, 1, y, z)));
    }

    @Test
    public void openColumnIsFullSkyAndARoofOccludes() {
        ProtoCube open = airCube(CubePos.of(0, 4, 0));
        CubicLightEngine.relight(open, CubicLightView.only(open));
        assertEquals(CubicLightEngine.MAX_LEVEL, open.cc_lightData().skyLight(block(open, 4, 4, 4)));

        ProtoCube roof = airCube(CubePos.of(0, 0, 0));
        int top = CubicConstants.DIAMETER_IN_BLOCKS - 1;
        for (int x = 0; x < CubicConstants.DIAMETER_IN_BLOCKS; x++) {
            for (int z = 0; z < CubicConstants.DIAMETER_IN_BLOCKS; z++) {
                set(roof, x, top, z, STONE);
            }
        }
        int holeX = 16;
        int holeZ = 16;
        set(roof, holeX, top, holeZ, AIR);
        CubicLightEngine.relight(roof, CubicLightView.only(roof));

        assertEquals(CubicLightEngine.MAX_LEVEL, roof.cc_lightData().skyLight(block(roof, holeX, top - 1, holeZ)), "column under the hole");
        assertEquals(CubicLightEngine.MAX_LEVEL - 2, roof.cc_lightData().skyLight(block(roof, holeX + 2, top - 1, holeZ)));
        assertEquals(0, roof.cc_lightData().skyLight(block(roof, 0, top - 1, 0)), "far under the roof");
    }

    @Test
    public void loadedStoneAboveBlocksSkyThatUnloadedSpaceWouldLeaveOpen() {
        ProtoCube below = airCube(CubePos.of(0, 0, 0));
        CubicLightEngine.relight(below, CubicLightView.only(below));
        below.setLightCorrect(true);
        assertEquals(CubicLightEngine.MAX_LEVEL, below.cc_lightData().skyLight(block(below, 8, 8, 8)));

        ProtoCube above = stoneCube(CubePos.of(0, 1, 0));
        Cubes view = new Cubes();
        view.add(below);
        view.add(above);
        CubicLightEngine.lightCube(above, view);

        assertEquals(0, below.cc_lightData().skyLight(block(below, 8, 8, 8)), "already-lit cube below is refreshed once the ceiling exists");
    }

    @Test
    public void packedLightRoundTrips() {
        ProtoCube cube = stoneCube(CubePos.of(3, -2, 1));
        set(cube, 4, 5, 6, AIR);
        set(cube, 4, 5, 6, TORCH);
        CubicLightEngine.relight(cube, CubicLightView.only(cube));

        CubeLightPacketData packet = CubeLightPacketData.from(cube);
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        CubeLightPacketData.STREAM_CODEC.encode(buf, packet);
        CubeLightPacketData decoded = CubeLightPacketData.STREAM_CODEC.decode(buf);
        assertEquals(packet, decoded);

        ProtoCube copy = airCube(cube.cc_getCubePos());
        decoded.apply(copy);
        BlockPos torch = block(cube, 4, 5, 6);
        assertEquals(cube.cc_lightData().blockLight(torch), copy.cc_lightData().blockLight(torch));
        assertEquals(cube.cc_lightData().skyLight(block(cube, 0, 0, 0)), copy.cc_lightData().skyLight(block(copy, 0, 0, 0)));
        assertTrue(copy.isLightCorrect());
    }

    private static ProtoCube stoneCube(CubePos pos) {
        ProtoCube cube = makeProtoCube(pos);
        fill(cube, STONE);
        return cube;
    }

    private static ProtoCube airCube(CubePos pos) {
        return makeProtoCube(pos);
    }

    private static void fill(CubeAccess cube, BlockState state) {
        CubePos pos = cube.cc_getCubePos();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int diameter = CubicConstants.DIAMETER_IN_BLOCKS;
        for (int x = 0; x < diameter; x++) {
            for (int y = 0; y < diameter; y++) {
                for (int z = 0; z < diameter; z++) {
                    cube.setBlockState(mutable.set(pos.blockX(x), pos.blockY(y), pos.blockZ(z)), state, 0);
                }
            }
        }
    }

    private static void set(CubeAccess cube, int localX, int localY, int localZ, BlockState state) {
        CubePos pos = cube.cc_getCubePos();
        cube.setBlockState(new BlockPos(pos.blockX(localX), pos.blockY(localY), pos.blockZ(localZ)), state, 0);
    }

    private static BlockPos block(CubeAccess cube, int localX, int localY, int localZ) {
        CubePos pos = cube.cc_getCubePos();
        return new BlockPos(pos.blockX(localX), pos.blockY(localY), pos.blockZ(localZ));
    }

    private static ProtoCube makeProtoCube(CubePos cubePos) {
        LevelHeightAccessor heightAccessor = org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(heightAccessor.getMinY()).thenReturn(-(1 << 24));
        org.mockito.Mockito.when(heightAccessor.getMaxY()).thenReturn(1 << 24);
        org.mockito.Mockito.when(heightAccessor.getHeight()).thenReturn(1 << 25);
        org.mockito.Mockito.when(heightAccessor.isOutsideBuildHeight(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        org.mockito.Mockito.when(heightAccessor.isOutsideBuildHeight(org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        return new ProtoCube(cubePos, org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS), heightAccessor,
                org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS), org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS));
    }

    private static final class Cubes implements CubicLightView {
        private final Map<Long, CubeAccess> cubes = new HashMap<>();

        void add(CubeAccess cube) {
            this.cubes.put(cube.cc_getCubePos().asLong(), cube);
        }

        @Override public @Nullable CubeAccess cubeAt(int cubeX, int cubeY, int cubeZ) {
            return this.cubes.get(CubePos.asLong(cubeX, cubeY, cubeZ));
        }
    }
}
