package io.github.opencubicchunks.cubicchunks.test.server.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundCubeBlockChangesPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelCubeWithLightPacket;
import io.github.opencubicchunks.cubicchunks.server.level.CloDistance;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Dual-access contract for one cube: two editors, a packet round-trip onto two client copies, and the client storage
 * rules that keep a forget valid after the view center moves. No integrated server.
 */
public class TestCubeStreaming extends BaseTest {
    @Test
    public void cubeDistanceUsesEachAxis() {
        Vec3 atCube = new Vec3(Coords.cubeToCenterBlock(0), Coords.cubeToCenterBlock(4), Coords.cubeToCenterBlock(-2));
        assertEquals(0.0, CloDistance.squaredToCubeCenter(CloPos.cube(0, 4, -2), atCube), 0.0);

        // The old helper passed cube X into the Y and Z centers, so this point was not at distance 0.
        double wrongY = Coords.cubeToCenterBlock(0) - atCube.y;
        double wrongZ = Coords.cubeToCenterBlock(0) - atCube.z;
        assertNotEquals(0.0, wrongY * wrongY + wrongZ * wrongZ);

        CloPos origin = CloPos.cube(0, 0, 0);
        assertEquals(0, CloDistance.chebyshevCubes(origin, origin));
        assertEquals(3, CloDistance.chebyshevCubes(origin, CloPos.cube(1, -3, 0)));
        assertEquals(2, CloDistance.chebyshevCubes(origin, CloPos.chunk(Coords.cubeToSection(2, 0), Coords.cubeToSection(0, 0))));
        assertTrue(CloDistance.chebyshevCubes(origin, CloPos.cube(0, 5, 0)) < CloDistance.chebyshevCubes(origin, CloPos.cube(9, 0, 0)));
    }

    @Test
    public void forgetAfterCenterMoveDropsTheSameSlot() {
        ClientLevel level = clientLevel();
        ClientCubeCache.Storage storage = new ClientCubeCache.Storage(2, level);
        CubePos origin = CubePos.of(0, 0, 0);
        CubePos edge = CubePos.of(2, 0, 0);
        CubePos outside = CubePos.of(8, 0, 0);
        LevelCube originCube = new LevelCube(level, origin);
        LevelCube edgeCube = new LevelCube(level, edge);
        LevelCube outsideCube = new LevelCube(level, outside);
        storage.replace(storage.getIndex(origin.getX(), origin.getY(), origin.getZ()), originCube);
        storage.replace(storage.getIndex(edge.getX(), edge.getY(), edge.getZ()), edgeCube);
        storage.replace(storage.getIndex(outside.getX(), outside.getY(), outside.getZ()), outsideCube);

        assertEquals(1, storage.dropOutsideRange());
        assertFalse(storage.dropAt(outside.getX(), outside.getY(), outside.getZ()));

        // A cube that hashes onto the origin slot must not be removed by a forget for the origin.
        CubePos alias = CubePos.of(storage.viewRange, 0, 0);
        assertEquals(storage.getIndex(origin.getX(), origin.getY(), origin.getZ()), storage.getIndex(alias.getX(), alias.getY(), alias.getZ()));
        LevelCube aliasCube = new LevelCube(level, alias);
        storage.replace(storage.getIndex(alias.getX(), alias.getY(), alias.getZ()), aliasCube);
        assertFalse(storage.dropAt(origin.getX(), origin.getY(), origin.getZ()));
        assertSame(aliasCube, storage.chunks.get(storage.getIndex(alias.getX(), alias.getY(), alias.getZ())));

        storage.viewCenterX = 20;
        storage.viewCenterY = 0;
        storage.viewCenterZ = 0;
        assertTrue(storage.dropAt(alias.getX(), alias.getY(), alias.getZ()));
        assertTrue(storage.dropAt(edge.getX(), edge.getY(), edge.getZ()));
        assertEquals(0, storage.chunkCount);
    }

    @Test
    public void twoClientsMatchAfterTwoEditsAndPacketRoundTrip() {
        ClientLevel level = clientLevel();
        CubePos cubePos = CubePos.of(3, -1, 4);
        LevelCube server = new LevelCube(level, cubePos);
        BlockPos digA = cubePos.asBlockPos(1, 2, 3);
        BlockPos digB = cubePos.asBlockPos(4, 2, 3);
        server.setBlockState(digA, Blocks.STONE.defaultBlockState(), 0);
        server.setBlockState(digB, Blocks.STONE.defaultBlockState(), 0);

        CCClientboundLevelCubeWithLightPacket full = roundTripFull(new CCClientboundLevelCubeWithLightPacket(server));
        LevelCube clientA = clientCopy(level, full);
        LevelCube clientB = clientCopy(level, full);
        assertEquals(Blocks.STONE.defaultBlockState(), clientA.getBlockState(digA));
        assertEquals(Blocks.STONE.defaultBlockState(), clientB.getBlockState(digB));

        BlockState diamond = Blocks.DIAMOND_BLOCK.defaultBlockState();
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        server.setBlockState(digA, diamond, 0);
        server.setBlockState(digB, gold, 0);
        server.cc_lightData().setBlockLight(digA.getX(), digA.getY(), digA.getZ(), 11);

        CCClientboundCubeBlockChangesPacket delta = roundTripDelta(CCClientboundCubeBlockChangesPacket.capture(server, changed(digA, digB)));
        assertEquals(cubePos, delta.pos());
        assertEquals(2, delta.changes().size());
        delta.apply(clientA);
        delta.apply(clientB);

        assertEquals(diamond, server.getBlockState(digA));
        assertEquals(gold, server.getBlockState(digB));
        assertEquals(diamond, clientA.getBlockState(digA));
        assertEquals(gold, clientA.getBlockState(digB));
        assertEquals(diamond, clientB.getBlockState(digA));
        assertEquals(gold, clientB.getBlockState(digB));
        assertEquals(11, clientA.cc_lightData().blockLight(digA.getX(), digA.getY(), digA.getZ()));
        assertEquals(11, clientB.cc_lightData().blockLight(digA.getX(), digA.getY(), digA.getZ()));
        assertEquals(Blocks.AIR.defaultBlockState(), clientA.getBlockState(cubePos.asBlockPos(0, 0, 0)));
    }

    @Test
    public void twoThreadsEditingOneCubeDoNotCorruptSections() throws Exception {
        ClientLevel level = clientLevel();
        CubePos cubePos = CubePos.of(0, 1, 0);
        LevelCube cube = new LevelCube(level, cubePos);
        int diameter = CubicConstants.DIAMETER_IN_BLOCKS;
        BlockState diamond = Blocks.DIAMOND_BLOCK.defaultBlockState();
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> editorA = pool.submit(() -> {
                start.await();
                for (int x = 0; x < diameter; x++) {
                    cube.setBlockState(cubePos.asBlockPos(x, 0, 0), diamond, 0);
                }
                return null;
            });
            Future<?> editorB = pool.submit(() -> {
                start.await();
                for (int x = 0; x < diameter; x++) {
                    cube.setBlockState(cubePos.asBlockPos(x, 1, 0), gold, 0);
                }
                return null;
            });
            BlockPos shared = cubePos.asBlockPos(0, 2, 0);
            Future<?> hammerA = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 100; i++) {
                    cube.setBlockState(shared, diamond, 0);
                }
                return null;
            });
            Future<?> hammerB = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 100; i++) {
                    cube.setBlockState(shared, gold, 0);
                }
                return null;
            });
            start.countDown();
            editorA.get(30, TimeUnit.SECONDS);
            editorB.get(30, TimeUnit.SECONDS);
            hammerA.get(30, TimeUnit.SECONDS);
            hammerB.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        for (int x = 0; x < diameter; x++) {
            assertEquals(diamond, cube.getBlockState(cubePos.asBlockPos(x, 0, 0)), "diamond column x=" + x);
            assertEquals(gold, cube.getBlockState(cubePos.asBlockPos(x, 1, 0)), "gold column x=" + x);
        }
        BlockState sharedState = cube.getBlockState(cubePos.asBlockPos(0, 2, 0));
        assertTrue(sharedState.equals(diamond) || sharedState.equals(gold));

        CCClientboundLevelCubeWithLightPacket full = roundTripFull(new CCClientboundLevelCubeWithLightPacket(cube));
        LevelCube copy = clientCopy(level, full);
        for (int x = 0; x < diameter; x++) {
            assertEquals(diamond, copy.getBlockState(cubePos.asBlockPos(x, 0, 0)));
            assertEquals(gold, copy.getBlockState(cubePos.asBlockPos(x, 1, 0)));
        }
    }

    private static ClientLevel clientLevel() {
        ClientLevel level = mock(Mockito.RETURNS_DEEP_STUBS);
        when(((CanBeCubic) level).cc_isCubic()).thenReturn(true);
        when(level.getHeight()).thenReturn(384);
        when(level.getSectionsCount()).thenReturn(24);
        return level;
    }

    private static LevelCube clientCopy(ClientLevel level, CCClientboundLevelCubeWithLightPacket packet) {
        LevelCube copy = new LevelCube(level, packet.pos());
        copy.replaceWithPacketData(packet.cubeData().getReadBuffer(), Map.of(), tag -> {});
        packet.light().apply(copy);
        return copy;
    }

    private static CCClientboundLevelCubeWithLightPacket roundTripFull(CCClientboundLevelCubeWithLightPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CCClientboundLevelCubeWithLightPacket.STREAM_CODEC.encode(buf, packet);
        return CCClientboundLevelCubeWithLightPacket.STREAM_CODEC.decode(buf);
    }

    private static CCClientboundCubeBlockChangesPacket roundTripDelta(CCClientboundCubeBlockChangesPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CCClientboundCubeBlockChangesPacket.STREAM_CODEC.encode(buf, packet);
        CCClientboundCubeBlockChangesPacket decoded = CCClientboundCubeBlockChangesPacket.STREAM_CODEC.decode(buf);
        assertEquals(packet.pos(), decoded.pos());
        assertEquals(packet.changes(), decoded.changes());
        assertEquals(packet.light(), decoded.light());
        return decoded;
    }

    private static ShortSet[] changed(BlockPos... positions) {
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
