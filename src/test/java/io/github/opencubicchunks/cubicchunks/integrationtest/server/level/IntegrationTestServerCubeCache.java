package io.github.opencubicchunks.cubicchunks.integrationtest.server.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.levelgen.CubicOverworldGenerator;
import io.github.opencubicchunks.cubicchunks.levelgen.CubicOverworldGenerator.SurfaceBiome;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundCubeBlockChangesPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelCubeWithLightPacket;
import io.github.opencubicchunks.cubicchunks.server.level.CubeLevel;
import io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.testutils.CloseableReference;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.level.cube.ProtoCube;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightEngine;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ProcessorChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mockito;

/**
 * Integration tests for getting chunks and cubes from {@link ServerChunkCache}.
 * <p>
 * This test is strongly dependent on {@link DistanceManager} and {@link ChunkMap} as well; errors here should probably be ignored unless
 * {@link IntegrationTestCubicChunkMap} passes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IntegrationTestServerCubeCache extends BaseTest {
    private Stream<ChunkStatus> chunkStatuses() {
        return ChunkStatus.getStatusList().stream();
    }

    private CloseableReference<ServerChunkCache> createServerChunkCache(boolean vanillaTest)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        return createServerChunkCache(vanillaTest, Files.createTempDirectory("cc_test"));
    }

    private CloseableReference<ServerChunkCache> createServerChunkCache(boolean vanillaTest, Path dimensionPath)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        // Worldgen internals
        var randomStateMockedStatic = Mockito.mockStatic(RandomState.class, withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS));
        NoiseBasedChunkGenerator noiseBasedChunkGeneratorMock = mock();
        when(noiseBasedChunkGeneratorMock.generatorSettings()).thenReturn(mock());
        if (vanillaTest) {
            // These methods are currently only called when running vanilla tests
            when(noiseBasedChunkGeneratorMock.createBiomes(any(), any(), any(), any()))
                    .thenAnswer(i -> CompletableFuture.completedFuture(i.getArguments()[3]));
            when(noiseBasedChunkGeneratorMock.fillFromNoise(any(), any(), any(), any()))
                    .thenAnswer(i -> CompletableFuture.completedFuture(i.getArguments()[3]));
        }

        ServerLevel serverLevelMock;
        try (var ignored = Mockito.mockConstruction(ServerChunkCache.class, withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS))) {
            // Server level
            serverLevelMock = mock(withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS).extraInterfaces(CanBeCubic.class));
        }
        if (!vanillaTest) {
            var f = serverLevelMock.getClass().getSuperclass().getSuperclass().getDeclaredField("cc_isCubic");
            f.setAccessible(true);
            f.set(serverLevelMock, true);
            when(((CanBeCubic) serverLevelMock).cc_isCubic()).thenReturn(true);
        }
        when(serverLevelMock.getSeed()).thenReturn(CubicOverworldGenerator.DEFAULT_SEED);
        when(serverLevelMock.getHeight()).thenReturn(384);
        when(serverLevelMock.getSectionsCount()).thenReturn(24);
        // We seem to need an actual directory, not a mock
        LevelStorageSource.LevelStorageAccess levelStorageAccessMock = mock(Mockito.RETURNS_DEEP_STUBS);
        when(levelStorageAccessMock.getDimensionPath(any())).thenReturn(dimensionPath);
        var serverChunkCache = new ServerChunkCache(serverLevelMock, levelStorageAccessMock, mock(Mockito.RETURNS_DEEP_STUBS),
                mock(Mockito.RETURNS_DEEP_STUBS),
                // We run everything on the main thread as Mockito has race conditions when multiple threads call into it
                // (which occurs when using RETURNS_DEEP_STUBS)
                Runnable::run, noiseBasedChunkGeneratorMock, 10, // server view distance
                10, // simulation distance
                false, // sync - not relevant for tests; false should be faster
                // Need to mock an implementation of the interface, so that it also implements CloProgressListener
                Mockito.<ProcessorChunkProgressListener>mock(Mockito.RETURNS_DEEP_STUBS), mock(Mockito.RETURNS_DEEP_STUBS),
                mock(Mockito.RETURNS_DEEP_STUBS));
        var f = serverLevelMock.getClass().getSuperclass().getDeclaredField("chunkSource");
        f.setAccessible(true);
        f.set(serverLevelMock, serverChunkCache);
        when(serverLevelMock.getChunkSource()).thenReturn(serverChunkCache);
        return new CloseableReference<>(serverChunkCache, randomStateMockedStatic);
    }

    /**
     * Get a single chunk in a non-cubic ServerChunkCache
     */
    public void singleGetChunkVanilla(ChunkStatus status) throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(true)) {
            var serverChunkCache = serverChunkCacheRef.value();
            var chunkAccess = serverChunkCache.getChunk(0, 0, status, true);
            assertNotNull(chunkAccess);
            assertTrue(chunkAccess.getPersistedStatus().isOrAfter(status));
            if (status.isOrAfter(ChunkStatus.FULL)) {
                assertInstanceOf(LevelChunk.class, chunkAccess);
            } else {
                assertInstanceOf(ProtoChunk.class, chunkAccess);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("chunkStatuses")
    public void getChunkVanilla(ChunkStatus status) throws Exception {
        singleGetChunkVanilla(status);
    }

    @Test
    public void getChunkNowVanilla() throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(true)) {
            var serverChunkCache = serverChunkCacheRef.value();
            // Present chunk
            ChunkPos pos = new ChunkPos(5, -123);
            serverChunkCache.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
            var chunkAccess = serverChunkCache.getChunkNow(pos.x, pos.z);
            assertNotNull(chunkAccess);
            assertSame(ChunkStatus.FULL, chunkAccess.getPersistedStatus());
            assertInstanceOf(LevelChunk.class, chunkAccess);

            // Neighbor chunk
            chunkAccess = serverChunkCache.getChunkNow(pos.x - 1, pos.z);
            assertNull(chunkAccess); // Expected to be null as getChunkNow requests at FULL

            // Non-present chunk
            chunkAccess = serverChunkCache.getChunkNow(0, 0);
            assertNull(chunkAccess);
        }
    }

    @Test
    public void hasChunkVanilla() throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(true)) {
            var serverChunkCache = serverChunkCacheRef.value();
            // Non-present chunk
            ChunkPos pos = new ChunkPos(-12, 65);
            assertFalse(serverChunkCache.hasChunk(pos.x, pos.z));

            // Load a chunk
            serverChunkCache.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
            // Retest chunk
            assertTrue(serverChunkCache.hasChunk(pos.x, pos.z));

            // Neighbor chunk, expected to be false as hasChunk checks for FULL
            assertFalse(serverChunkCache.hasChunk(pos.x - 1, pos.z)); // Ex
        }
    }

    // TODO (P2) test these methods:
    // isPositionTicking
    // tick
    // blockChanged
    // onLightUpdate

    /**
     * Get a single chunk in a cubic ServerChunkCache
     */
    public void singleGetChunk(ChunkStatus status) throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(false)) {
            var serverChunkCache = serverChunkCacheRef.value();
            var chunkAccess = serverChunkCache.getChunk(0, 0, status, true);
            assertNotNull(chunkAccess);
            assertTrue(chunkAccess.getPersistedStatus().isOrAfter(status));
            if (status.isOrAfter(ChunkStatus.FULL)) {
                assertInstanceOf(LevelChunk.class, chunkAccess);
            } else {
                assertInstanceOf(ProtoChunk.class, chunkAccess);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("chunkStatuses")
    public void getChunk(ChunkStatus status) throws Exception {
        singleGetChunk(status);
    }

    /**
     * getNow a single chunk in a cubic ServerChunkCache
     */
    @Test
    public void getChunkNow() throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(false)) {
            var serverChunkCache = serverChunkCacheRef.value();
            // Present chunk
            serverChunkCache.getChunk(5, -123, ChunkStatus.FULL, true);
            var chunkAccess = serverChunkCache.getChunkNow(5, -123);
            assertNotNull(chunkAccess);
            assertSame(ChunkStatus.FULL, chunkAccess.getPersistedStatus());
            assertInstanceOf(LevelChunk.class, chunkAccess);

            // Neighbor chunk
            chunkAccess = serverChunkCache.getChunkNow(4, -123);
            assertNull(chunkAccess); // Expected to be null as getChunkNow requests at FULL

            // Non-present chunk
            chunkAccess = serverChunkCache.getChunkNow(0, 0);
            assertNull(chunkAccess);
        }
    }

    /**
     * test hasChunk a for single chunk in a cubic ServerChunkCache
     */
    @Test
    public void hasChunk() throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(false)) {
            var serverChunkCache = serverChunkCacheRef.value();
            ChunkPos pos = new ChunkPos(-12, 65);
            // Non-present chunk
            assertFalse(serverChunkCache.hasChunk(pos.x, pos.z));

            // Load a chunk
            serverChunkCache.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
            // Retest chunk
            assertTrue(serverChunkCache.hasChunk(pos.x, pos.z));

            // Neighbor chunk, expected to be false as hasChunk checks for FULL
            assertFalse(serverChunkCache.hasChunk(pos.x - 1, pos.z));
        }
    }

    /**
     * Get a single cube in a cubic ServerChunkCache
     */
    public void singleGetCube(ChunkStatus status) throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(false)) {
            var serverChunkCache = ((ServerCubeCache) serverChunkCacheRef.value());
            var chunkAccess = serverChunkCache.cc_getCube(0, 0, 0, status, true);
            assertNotNull(chunkAccess);
            assertTrue(chunkAccess.getPersistedStatus().isOrAfter(status));
            if (status.isOrAfter(ChunkStatus.FULL)) {
                assertInstanceOf(LevelCube.class, chunkAccess);
            } else {
                assertInstanceOf(ProtoCube.class, chunkAccess);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("chunkStatuses")
    public void getCube(ChunkStatus status) throws Exception {
        singleGetCube(status);
    }

    /**
     * getNow a single cube in a cubic ServerChunkCache
     */
    @Test
    public void getCubeNow() throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(false)) {
            var serverChunkCache = serverChunkCacheRef.value();
            var cubicServerChunkCache = ((ServerCubeCache) serverChunkCache);

            // Present chunk
            CubePos cubePos = CubePos.of(5, 1273, -123);
            cubicServerChunkCache.cc_getCube(cubePos.getX(), cubePos.getY(), cubePos.getZ(), ChunkStatus.FULL, true);
            var cubeAccess = cubicServerChunkCache.cc_getCubeNow(cubePos.getX(), cubePos.getY(), cubePos.getZ());
            assertNotNull(cubeAccess);
            assertSame(ChunkStatus.FULL, cubeAccess.getPersistedStatus());
            assertInstanceOf(LevelCube.class, cubeAccess);
            // check its chunks
            for (int localChunkX = 0; localChunkX < CubicConstants.DIAMETER_IN_SECTIONS; localChunkX++) {
                for (int localChunkZ = 0; localChunkZ < CubicConstants.DIAMETER_IN_SECTIONS; localChunkZ++) {
                    var chunkPos = cubePos.asChunkPos(localChunkX, localChunkZ);
                    var chunkAccess = serverChunkCache.getChunkNow(chunkPos.x, chunkPos.z);
                    assertNotNull(chunkAccess);
                    assertSame(ChunkStatus.FULL, chunkAccess.getPersistedStatus());
                    assertInstanceOf(LevelChunk.class, chunkAccess);
                }
            }

            // Neighbor cube
            cubePos = CubePos.of(cubePos.getX() - 1, cubePos.getY(), cubePos.getZ());
            cubeAccess = cubicServerChunkCache.cc_getCubeNow(cubePos.getX(), cubePos.getY(), cubePos.getZ());
            assertNull(cubeAccess); // Expected to be null as getCubeNow requests at FULL
            // check its chunks
            for (int localChunkX = 0; localChunkX < CubicConstants.DIAMETER_IN_SECTIONS; localChunkX++) {
                for (int localChunkZ = 0; localChunkZ < CubicConstants.DIAMETER_IN_SECTIONS; localChunkZ++) {
                    var chunkPos = cubePos.asChunkPos(localChunkX, localChunkZ);
                    var chunkAccess = serverChunkCache.getChunkNow(chunkPos.x, chunkPos.z);
                    assertNull(chunkAccess);
                }
            }

            // Non-present cube
            cubePos = CubePos.of(0, 0, 0);
            cubeAccess = cubicServerChunkCache.cc_getCubeNow(cubePos.getX(), cubePos.getY(), cubePos.getZ());
            assertNull(cubeAccess);
            // check its chunks
            for (int localChunkX = 0; localChunkX < CubicConstants.DIAMETER_IN_SECTIONS; localChunkX++) {
                for (int localChunkZ = 0; localChunkZ < CubicConstants.DIAMETER_IN_SECTIONS; localChunkZ++) {
                    var chunkPos = cubePos.asChunkPos(localChunkX, localChunkZ);
                    var chunkAccess = serverChunkCache.getChunkNow(chunkPos.x, chunkPos.z);
                    assertNull(chunkAccess);
                }
            }
        }
    }

    /**
     * test hasCube a for single cube in a cubic ServerChunkCache
     */
    @Test
    public void hasCube() throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(false)) {
            ServerChunkCache serverChunkCache = serverChunkCacheRef.value();
            var serverCubeCache = ((ServerCubeCache) serverChunkCache);
            // Non-present cube
            CubePos cubePos = CubePos.of(-12, 98, 65);
            assertFalse(serverCubeCache.cc_hasCube(cubePos.getX(), cubePos.getY(), cubePos.getZ()));
            // check its chunks
            for (int localChunkX = 0; localChunkX < CubicConstants.DIAMETER_IN_SECTIONS; localChunkX++) {
                for (int localChunkZ = 0; localChunkZ < CubicConstants.DIAMETER_IN_SECTIONS; localChunkZ++) {
                    var chunkPos = cubePos.asChunkPos(localChunkX, localChunkZ);
                    var has = serverChunkCache.hasChunk(chunkPos.x, chunkPos.z);
                    assertFalse(has);
                }
            }

            // Load a cube
            serverCubeCache.cc_getCube(cubePos.getX(), cubePos.getY(), cubePos.getZ(), ChunkStatus.FULL, true);

            // Retest cube
            assertTrue(serverCubeCache.cc_hasCube(cubePos.getX(), cubePos.getY(), cubePos.getZ()));
            // check its chunks
            for (int localChunkX = 0; localChunkX < CubicConstants.DIAMETER_IN_SECTIONS; localChunkX++) {
                for (int localChunkZ = 0; localChunkZ < CubicConstants.DIAMETER_IN_SECTIONS; localChunkZ++) {
                    var chunkPos = cubePos.asChunkPos(localChunkX, localChunkZ);
                    var has = serverChunkCache.hasChunk(chunkPos.x, chunkPos.z);
                    assertTrue(has);
                }
            }

            // Neighbor cube, expected to be false as hasChunk checks for FULL
            cubePos = CubePos.of(cubePos.getX() - 1, cubePos.getY(), cubePos.getZ());
            assertFalse(serverCubeCache.cc_hasCube(cubePos.getX(), cubePos.getY(), cubePos.getZ())); // Expected false as hasCube checks for full
                                                                                                     // status
            // check its chunks
            for (int localChunkX = 0; localChunkX < CubicConstants.DIAMETER_IN_SECTIONS; localChunkX++) {
                for (int localChunkZ = 0; localChunkZ < CubicConstants.DIAMETER_IN_SECTIONS; localChunkZ++) {
                    var chunkPos = cubePos.asChunkPos(localChunkX, localChunkZ);
                    var has = serverChunkCache.hasChunk(chunkPos.x, chunkPos.z);
                    assertFalse(has);
                }
            }
        }
    }

    /**
     * Get a cube and nearby cubes and chunks in a cubic ServerChunkCache
     */
    @Test
    public void getCubeAndNeighboringCubesAndChunks() throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(false)) {
            var serverChunkCache = serverChunkCacheRef.value();
            var cubicServerChunkCache = ((ServerCubeCache) serverChunkCache);
            var cubeAccess = cubicServerChunkCache.cc_getCube(0, 0, 0, ChunkStatus.FULL, true);
            assertNotNull(cubeAccess);
            assertTrue(cubeAccess.getPersistedStatus().isOrAfter(ChunkStatus.FULL));
            assertInstanceOf(LevelCube.class, cubeAccess);
            for (int i = 0; i < CubeLevel.RADIUS_AROUND_FULL_CUBE; i++) {
                var expectedStatus = CubeLevel.getStatusAroundFullCube(i);
                cubeAccess = cubicServerChunkCache.cc_getCube(i, -i, 0, expectedStatus, false);
                assertNotNull(cubeAccess);
                assertTrue(cubeAccess.getPersistedStatus().isOrAfter(expectedStatus));
                for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
                    for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                        int x = -i * CubicConstants.DIAMETER_IN_SECTIONS + dx;
                        int z = i * CubicConstants.DIAMETER_IN_SECTIONS + dz;
                        if (expectedStatus.isOrAfter(ChunkStatus.FULL)) {
                            assertTrue(serverChunkCache.hasChunk(x, z));
                        }
                        var chunkAccess = serverChunkCache.getChunk(x, z, expectedStatus, false);
                        assertNotNull(chunkAccess);
                        assertTrue(chunkAccess.getPersistedStatus().isOrAfter(expectedStatus));
                    }
                }
            }
        }
    }

    /**
     * A cube loaded to {@link ChunkStatus#FULL} keeps the overworld grass/dirt fill, and phase-3 sky light still treats
     * buried ground as dark and open air as sky.
     */
    @Test
    public void fullCubeOverworldSurfaceAndLight() throws Exception {
        assertEquals(32, CubicConstants.DIAMETER_IN_BLOCKS);
        BlockPos grass = findOpenGrass(CubicOverworldGenerator.DEFAULT_SEED);
        BlockPos above = grass.above();
        BlockPos buried = grass.below(4);
        try (var serverChunkCacheRef = createServerChunkCache(false)) {
            var chunkCache = serverChunkCacheRef.value();
            var serverCubeCache = (ServerCubeCache) chunkCache;
            var cubeAccess = serverCubeCache.cc_getCube(Coords.blockToCube(grass.getX()), Coords.blockToCube(grass.getY()),
                    Coords.blockToCube(grass.getZ()), ChunkStatus.FULL, true);
            assertInstanceOf(LevelCube.class, cubeAccess);

            assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), cubeAccess.getBlockState(grass));
            assertEquals(Blocks.DIRT.defaultBlockState(), cubeAccess.getBlockState(grass.below()));
            assertEquals(Blocks.AIR.defaultBlockState(), cubeAccess.getBlockState(above));
            assertEquals(15, cubeAccess.getBlockState(buried).getLightBlock());
            assertEquals(0, countBedrock((LevelCube) cubeAccess));

            var lightEngine = chunkCache.getLightEngine();
            assertEquals(0, lightEngine.getRawBrightness(buried, 0), "solid ground is not fullbright");
            assertEquals(CubicLightEngine.MAX_LEVEL, lightEngine.getRawBrightness(above, 0), "air above the surface keeps sky light");
            assertEquals(0, ((LevelCube) cubeAccess).cc_lightData().skyLight(buried));
            assertEquals(CubicLightEngine.MAX_LEVEL, ((LevelCube) cubeAccess).cc_lightData().skyLight(above));
        }
    }

    /**
     * A torch carved into generated stone lights the air beside it, a solid block stops that light, and both values survive save/reload.
     */
    @Test
    public void torchInSolidCubeCastsLightAndSurvivesReload() throws Exception {
        Path dimensionPath = Files.createTempDirectory("cc_cube_light");
        BlockPos torch = findSolidRun(CubicOverworldGenerator.DEFAULT_SEED);
        BlockPos beside = torch.offset(1, 0, 0);
        BlockPos wall = torch.offset(2, 0, 0);
        BlockPos behind = torch.offset(3, 0, 0);
        int emission = Blocks.TORCH.defaultBlockState().getLightEmission();
        try (var firstRef = createServerChunkCache(false, dimensionPath)) {
            ServerChunkCache cache = firstRef.value();
            try {
                var cube = ((ServerCubeCache) cache).cc_getCube(0, 0, 0, ChunkStatus.FULL, true);
                assertInstanceOf(LevelCube.class, cube);
                assertEquals(15, cube.getBlockState(torch).getLightBlock());
                assertEquals(15, cube.getBlockState(wall).getLightBlock());
                assertEquals(15, cube.getBlockState(behind).getLightBlock());
                cube.setBlockState(torch, Blocks.AIR.defaultBlockState(), 0);
                cube.setBlockState(beside, Blocks.AIR.defaultBlockState(), 0);
                cube.setBlockState(behind, Blocks.AIR.defaultBlockState(), 0);
                cube.setBlockState(torch, Blocks.TORCH.defaultBlockState(), 0);

                assertEquals(emission, cube.cc_lightData().blockLight(torch));
                assertEquals(emission - 1, cube.cc_lightData().blockLight(beside));
                assertEquals(0, cube.cc_lightData().blockLight(wall));
                assertEquals(0, cube.cc_lightData().blockLight(behind));
                assertEquals(emission - 1, cache.getLightEngine().getRawBrightness(beside, 0));
                assertEquals(0, cache.getLightEngine().getRawBrightness(behind, 0));
                cache.save(true);
            } finally {
                cache.chunkMap.close();
            }
        }
        try (var secondRef = createServerChunkCache(false, dimensionPath)) {
            ServerChunkCache cache = secondRef.value();
            try {
                var cube = ((ServerCubeCache) cache).cc_getCube(0, 0, 0, ChunkStatus.FULL, true);
                assertInstanceOf(LevelCube.class, cube);
                assertEquals(Blocks.TORCH.defaultBlockState(), cube.getBlockState(torch));
                assertEquals(emission, cube.cc_lightData().blockLight(torch));
                assertEquals(emission - 1, cube.cc_lightData().blockLight(beside));
                assertEquals(0, cube.cc_lightData().blockLight(behind));
            } finally {
                cache.chunkMap.close();
            }
        }
    }

    /**
     * A block edited after generation must still be there after {@link ServerChunkCache#save} and a new cache on the same directory.
     * The neighboring generated block must be unchanged, so the reload is the saved cube and not a fresh fill of an empty file.
     */
    @Test
    public void cubeBlocksSurviveSaveAndReload() throws Exception {
        Path dimensionPath = Files.createTempDirectory("cc_cube_save");
        BlockPos placed = findSolidRun(CubicOverworldGenerator.DEFAULT_SEED);
        BlockPos generated = placed.offset(1, 0, 0);
        assertEquals(placed.getX() >> 4, generated.getX() >> 4);
        assertEquals(placed.getY() >> 4, generated.getY() >> 4);
        assertEquals(placed.getZ() >> 4, generated.getZ() >> 4);
        try (var firstRef = createServerChunkCache(false, dimensionPath)) {
            ServerChunkCache cache = firstRef.value();
            try {
                var cube = ((ServerCubeCache) cache).cc_getCube(0, 0, 0, ChunkStatus.FULL, true);
                assertInstanceOf(LevelCube.class, cube);
                BlockState kept = cube.getBlockState(generated);
                assertEquals(CubicOverworldGenerator.blockState(CubicOverworldGenerator.DEFAULT_SEED, generated.getX(), generated.getY(),
                        generated.getZ()), kept);
                assertEquals(15, cube.getBlockState(placed).getLightBlock());
                assertEquals(15, kept.getLightBlock());
                cube.setBlockState(placed, Blocks.DIAMOND_BLOCK.defaultBlockState(), 0);
                assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(), cube.getBlockState(placed));
                cache.save(true);
            } finally {
                cache.chunkMap.close();
            }
        }
        try (var secondRef = createServerChunkCache(false, dimensionPath)) {
            ServerChunkCache cache = secondRef.value();
            try {
                var cube = ((ServerCubeCache) cache).cc_getCube(0, 0, 0, ChunkStatus.FULL, true);
                assertInstanceOf(LevelCube.class, cube);
                assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(), cube.getBlockState(placed));
                assertEquals(CubicOverworldGenerator.blockState(CubicOverworldGenerator.DEFAULT_SEED, generated.getX(), generated.getY(),
                        generated.getZ()), cube.getBlockState(generated));
            } finally {
                cache.chunkMap.close();
            }
        }
    }

    private static BlockPos findOpenGrass(long seed) {
        for (int x = -128; x <= 128; x++) {
            for (int z = -128; z <= 128; z++) {
                BlockPos grass = openGrassAt(seed, x, z);
                if (grass != null) {
                    return grass;
                }
            }
        }
        throw new AssertionError("no open grass column");
    }

    private static BlockPos openGrassAt(long seed, int x, int z) {
        if (CubicOverworldGenerator.biomeAt(seed, x, z) != SurfaceBiome.PLAINS) {
            return null;
        }
        int surface = CubicOverworldGenerator.surfaceY(seed, x, z);
        if (surface < CubicOverworldGenerator.SEA_LEVEL) {
            return null;
        }
        for (int y = surface - 2; y <= surface + 12; y++) {
            if (!CubicOverworldGenerator.blockState(seed, x, y, z).is(Blocks.GRASS_BLOCK)) {
                continue;
            }
            CubePos cube = CubePos.from(x, y, z);
            if (y - 4 < cube.minCubeY() || y + 1 > cube.maxCubeY()) {
                continue;
            }
            if (!openToCubeTop(seed, x, z, y + 1, cube.maxCubeY())) {
                continue;
            }
            if (!CubicOverworldGenerator.blockState(seed, x, y - 1, z).is(Blocks.DIRT)) {
                continue;
            }
            if (CubicOverworldGenerator.blockState(seed, x, y - 4, z).getLightBlock() != 15) {
                continue;
            }
            return new BlockPos(x, y, z);
        }
        return null;
    }

    private static boolean openToCubeTop(long seed, int x, int z, int fromY, int maxY) {
        for (int y = fromY; y <= maxY; y++) {
            if (!CubicOverworldGenerator.blockState(seed, x, y, z).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** Four adjacent full blocks inside cube {@code (0,0,0)}, in one section, so a torch test can carve a gap. */
    private static BlockPos findSolidRun(long seed) {
        CubePos origin = CubePos.of(0, 0, 0);
        int diameter = CubicConstants.DIAMETER_IN_BLOCKS;
        for (int y = 0; y < diameter; y++) {
            for (int z = 0; z < diameter; z++) {
                for (int x = 0; x < diameter - 3; x++) {
                    BlockPos start = origin.asBlockPos(x, y, z);
                    if (((start.getX() + 3) >> 4) != (start.getX() >> 4)) {
                        continue;
                    }
                    if (solidRun(seed, start)) {
                        return start;
                    }
                }
            }
        }
        throw new AssertionError("no solid run in cube (0,0,0)");
    }

    private static boolean solidRun(long seed, BlockPos start) {
        for (int dx = 0; dx < 4; dx++) {
            BlockState state = CubicOverworldGenerator.blockState(seed, start.getX() + dx, start.getY(), start.getZ());
            if (state.getLightBlock() != 15) {
                return false;
            }
        }
        return true;
    }

    private static int countBedrock(LevelCube cube) {
        CubePos pos = cube.cc_getCubePos();
        int found = 0;
        int diameter = CubicConstants.DIAMETER_IN_BLOCKS;
        for (int x = 0; x < diameter; x++) {
            for (int y = 0; y < diameter; y++) {
                for (int z = 0; z < diameter; z++) {
                    if (cube.getBlockState(pos.asBlockPos(x, y, z)).is(Blocks.BEDROCK)) {
                        found++;
                    }
                }
            }
        }
        return found;
    }

    @Test
    public void testAddCubicTicketWithRadius() throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(false)) {
            var serverChunkCache = serverChunkCacheRef.value();
            var cubicServerChunkCache = ((ServerCubeCache) serverChunkCache);
            int spawnRadius = Coords.sectionToCube(11);
            cubicServerChunkCache.cc_addTicketWithRadius(TicketType.START, CloPos.cube(0, 0, 0), spawnRadius);
            serverChunkCache.tick(() -> true, false);
            var cubeAccess = cubicServerChunkCache.cc_getCube(0, 0, 0, ChunkStatus.FULL, true);
            assertNotNull(cubeAccess);
            assertTrue(cubeAccess.getPersistedStatus().isOrAfter(ChunkStatus.FULL));
            assertInstanceOf(LevelCube.class, cubeAccess);
        }
    }

    /**
     * Two editors of one loaded cube, then two client copies fed by the full packet and the dig packet. Save/reload must
     * still show both edits. Loads go through the cache's main-thread executor, which is what a dedicated server uses.
     */
    @Test
    public void twoEditorsOfOneCubeStayConsistentAcrossClientsAndReload() throws Exception {
        Path dimensionPath = Files.createTempDirectory("cc_cube_mp");
        BlockPos digA = findSolidRun(CubicOverworldGenerator.DEFAULT_SEED);
        BlockPos digB = digA.offset(1, 0, 0);
        BlockState diamond = Blocks.DIAMOND_BLOCK.defaultBlockState();
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState generatedA;
        BlockState generatedB;
        try (var firstRef = createServerChunkCache(false, dimensionPath)) {
            ServerChunkCache cache = firstRef.value();
            try {
                CubeAccess first = ((ServerCubeCache) cache).cc_getCube(0, 0, 0, ChunkStatus.FULL, true);
                CubeAccess second = ((ServerCubeCache) cache).cc_getCube(0, 0, 0, ChunkStatus.FULL, true);
                assertSame(first, second);
                assertInstanceOf(LevelCube.class, first);
                LevelCube serverCube = (LevelCube) first;
                generatedA = serverCube.getBlockState(digA);
                generatedB = serverCube.getBlockState(digB);
                assertEquals(15, generatedA.getLightBlock());
                assertEquals(15, generatedB.getLightBlock());

                CCClientboundLevelCubeWithLightPacket full = roundTrip(new CCClientboundLevelCubeWithLightPacket(serverCube));
                LevelCube clientA = clientCopy(cache.level, full);
                LevelCube clientB = clientCopy(cache.level, full);
                assertEquals(generatedA, clientA.getBlockState(digA));
                assertEquals(generatedB, clientB.getBlockState(digB));

                serverCube.setBlockState(digA, diamond, 0);
                serverCube.setBlockState(digB, gold, 0);
                CCClientboundCubeBlockChangesPacket delta = roundTrip(
                        CCClientboundCubeBlockChangesPacket.capture(serverCube, changedSections(digA, digB)));
                delta.apply(clientA);
                delta.apply(clientB);

                assertEquals(diamond, serverCube.getBlockState(digA));
                assertEquals(gold, serverCube.getBlockState(digB));
                assertEquals(diamond, clientA.getBlockState(digA));
                assertEquals(gold, clientA.getBlockState(digB));
                assertEquals(diamond, clientB.getBlockState(digA));
                assertEquals(gold, clientB.getBlockState(digB));
                cache.save(true);
            } finally {
                cache.chunkMap.close();
            }
        }
        try (var secondRef = createServerChunkCache(false, dimensionPath)) {
            ServerChunkCache cache = secondRef.value();
            try {
                var reloaded = ((ServerCubeCache) cache).cc_getCube(0, 0, 0, ChunkStatus.FULL, true);
                assertInstanceOf(LevelCube.class, reloaded);
                assertEquals(diamond, reloaded.getBlockState(digA));
                assertEquals(gold, reloaded.getBlockState(digB));
                CCClientboundLevelCubeWithLightPacket full = roundTrip(new CCClientboundLevelCubeWithLightPacket((LevelCube) reloaded));
                LevelCube client = clientCopy(cache.level, full);
                assertEquals(diamond, client.getBlockState(digA));
                assertEquals(gold, client.getBlockState(digB));
            } finally {
                cache.chunkMap.close();
            }
        }
    }

    private static LevelCube clientCopy(Level level, CCClientboundLevelCubeWithLightPacket packet) {
        LevelCube copy = new LevelCube(level, packet.pos());
        copy.replaceWithPacketData(packet.cubeData().getReadBuffer(), Map.of(), tag -> {});
        packet.light().apply(copy);
        return copy;
    }

    private static CCClientboundLevelCubeWithLightPacket roundTrip(CCClientboundLevelCubeWithLightPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CCClientboundLevelCubeWithLightPacket.STREAM_CODEC.encode(buf, packet);
        return CCClientboundLevelCubeWithLightPacket.STREAM_CODEC.decode(buf);
    }

    private static CCClientboundCubeBlockChangesPacket roundTrip(CCClientboundCubeBlockChangesPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CCClientboundCubeBlockChangesPacket.STREAM_CODEC.encode(buf, packet);
        return CCClientboundCubeBlockChangesPacket.STREAM_CODEC.decode(buf);
    }

    private static ShortSet[] changedSections(BlockPos... positions) {
        ShortSet[] sets = new ShortSet[CubicConstants.SECTION_COUNT];
        for (BlockPos pos : positions) {
            int index = Coords.blockToIndex(pos);
            if (sets[index] == null) {
                sets[index] = new ShortOpenHashSet();
            }
            sets[index].add(SectionPos.sectionRelativePos(pos));
        }
        return sets;
    }
}
