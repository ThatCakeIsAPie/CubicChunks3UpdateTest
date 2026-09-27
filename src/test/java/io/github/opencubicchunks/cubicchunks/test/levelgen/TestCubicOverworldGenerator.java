package io.github.opencubicchunks.cubicchunks.test.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.levelgen.CubicOverworldGenerator;
import io.github.opencubicchunks.cubicchunks.levelgen.CubicOverworldGenerator.SurfaceBiome;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.world.level.cube.ProtoCube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Answers;

/**
 * Locks the phase-4 overworld fill: more than one material, a walkable crust, caves, ores, water, and no bedrock.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestCubicOverworldGenerator extends BaseTest {
    private static final long SEED = CubicOverworldGenerator.DEFAULT_SEED;
    /** Desert on seed 0 starts past ±256. The scan has to reach it. */
    private static final int SEARCH = 640;
    private static final int SEARCH_STEP = 8;

    @Test
    public void columnMaterials_grassDirtStoneCaveOreWaterAndNoBedrock() {
        BlockPos grass = findWalkable(SurfaceBiome.PLAINS, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        assertEquals(Blocks.AIR.defaultBlockState(), CubicOverworldGenerator.blockState(SEED, grass.getX(), grass.getY() + 1, grass.getZ()));
        assertEquals(Blocks.DIRT.defaultBlockState(), CubicOverworldGenerator.blockState(SEED, grass.getX(), grass.getY() - 1, grass.getZ()));
        BlockState buried = CubicOverworldGenerator.blockState(SEED, grass.getX(), grass.getY() - 4, grass.getZ());
        assertTrue(buried.getLightDampening() == 15, grass::toShortString);
        assertNotEquals(Blocks.SMOOTH_STONE.defaultBlockState(), buried);
        assertNotEquals(Blocks.BEDROCK.defaultBlockState(), buried);

        BlockPos sand = findWalkable(SurfaceBiome.DESERT, Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState());
        assertEquals(Blocks.SAND.defaultBlockState(), CubicOverworldGenerator.blockState(SEED, sand.getX(), sand.getY() - 1, sand.getZ()));

        BlockPos snow = findWalkable(SurfaceBiome.SNOWY, Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        assertEquals(Blocks.DIRT.defaultBlockState(), CubicOverworldGenerator.blockState(SEED, snow.getX(), snow.getY() - 1, snow.getZ()));

        BlockPos water = findWater();
        assertEquals(Blocks.WATER.defaultBlockState(), CubicOverworldGenerator.blockState(SEED, water.getX(), water.getY(), water.getZ()));
        assertTrue(water.getY() < CubicOverworldGenerator.SEA_LEVEL);

        ProtoCube underground = fill(CubePos.of(0, 0, 0));
        assertTrue(count(underground, Blocks.STONE.defaultBlockState()) > 0);
        assertTrue(countOre(underground) > 0, "underground cube should contain an ore");
        assertEquals(0, count(underground, Blocks.BEDROCK.defaultBlockState()));
        assertEquals(0, count(underground, Blocks.SMOOTH_STONE.defaultBlockState()));
        assertTrue(countCave(findCaveCube()) > 0, "an underground cube should contain cave air");
    }

    @Test
    public void deepslateCube_hasNoBedrockFloor() {
        // Cube (0, -2, 0) is y[-64, -33], which includes the vanilla bedrock layer.
        CubePos deep = CubePos.of(0, -2, 0);
        assertTrue(deep.minCubeY() <= -64 && deep.maxCubeY() >= -64);
        ProtoCube cube = fill(deep);
        assertEquals(0, count(cube, Blocks.BEDROCK.defaultBlockState()));
        assertEquals(0, count(cube, Blocks.STONE.defaultBlockState()));
        assertTrue(count(cube, Blocks.DEEPSLATE.defaultBlockState()) > 0);
        BlockPos floor = new BlockPos(0, -64, 0);
        BlockState atVanillaFloor = cube.getBlockState(floor);
        assertFalse(atVanillaFloor.is(Blocks.BEDROCK));
        assertEquals(CubicOverworldGenerator.blockState(SEED, 0, -64, 0), atVanillaFloor);

        ProtoCube deeper = fill(CubePos.of(0, -8, 0));
        assertEquals(0, count(deeper, Blocks.BEDROCK.defaultBlockState()));
        assertTrue(count(deeper, Blocks.DEEPSLATE.defaultBlockState()) > 0);
    }

    @Test
    public void cubeAboveTerrain_isAir() {
        ProtoCube sky = fill(CubePos.of(0, 16, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), sky.getBlockState(sky.cc_getCubePos().asBlockPos()));
        assertEquals(Blocks.AIR.defaultBlockState(), sky.getBlockState(sky.cc_getCubePos().asBlockPos(31, 31, 31)));
        assertEquals(0, count(sky, Blocks.STONE.defaultBlockState()));
        assertEquals(0, count(sky, Blocks.WATER.defaultBlockState()));
    }

    @Test
    public void shiftedColumn_usesWorldCoordinates() {
        CubePos shifted = CubePos.of(2, 0, -1);
        ProtoCube cube = fill(shifted);
        int x = 64;
        int z = -32;
        assertEquals(shifted.minCubeX(), x);
        assertEquals(shifted.minCubeZ(), z);
        for (int y = shifted.minCubeY(); y <= shifted.maxCubeY(); y++) {
            assertEquals(CubicOverworldGenerator.blockState(SEED, x, y, z), cube.getBlockState(new BlockPos(x, y, z)), y + "");
        }
        assertTrue(surfaceDiffersFromOrigin(shifted), "a shifted cube must sample world XZ, not the origin column");
    }

    @Test
    public void seedChangesTheColumn() {
        boolean differed = false;
        for (int x = 0; x < 24; x++) {
            if (CubicOverworldGenerator.surfaceY(SEED, x, 0) != CubicOverworldGenerator.surfaceY(SEED + 99L, x, 0)) {
                differed = true;
                break;
            }
        }
        assertTrue(differed);
    }

    @Test
    public void fillBiomes_paintsTheColumnBiome() {
        Registry<Biome> biomes = biomeRegistry();
        ProtoCube cube = newProtoCube(CubePos.of(0, 2, 0), biomes);
        CubicOverworldGenerator.fillBiomes(cube, SEED, biomes);
        int x = 6;
        int y = cube.cc_getCubePos().minCubeY() + 4;
        int z = 10;
        assertBiome(biomes, cube, x, y, z);
        assertBiome(biomes, cube, x, y + 16, z);
        assertNotNull(findBiome(SurfaceBiome.DESERT));
        assertNotNull(findBiome(SurfaceBiome.SNOWY));
        assertNotNull(findBiome(SurfaceBiome.PLAINS));
    }

    @Test
    public void fillBiomes_nullRegistry_leavesTheCube() {
        ProtoCube cube = newProtoCube(CubePos.of(0, 0, 0), null);
        CubicOverworldGenerator.fillBiomes(cube, SEED, null);
        assertEquals(Blocks.AIR.defaultBlockState(), cube.getBlockState(cube.cc_getCubePos().asBlockPos()));
    }

    private static void assertBiome(Registry<Biome> biomes, ProtoCube cube, int x, int y, int z) {
        int cellX = QuartPos.toBlock(QuartPos.fromBlock(x));
        int cellZ = QuartPos.toBlock(QuartPos.fromBlock(z));
        SurfaceBiome expected = CubicOverworldGenerator.biomeAt(SEED, cellX, cellZ);
        LevelChunkSection section = cube.getSection(Coords.blockToIndex(x, y, z));
        assertEquals(biomes.getOrThrow(biomeKey(expected)),
                section.getNoiseBiome(SectionPos.sectionRelative(x) >> 2, SectionPos.sectionRelative(y) >> 2, SectionPos.sectionRelative(z) >> 2));
    }

    private static ResourceKey<Biome> biomeKey(SurfaceBiome biome) {
        if (biome == SurfaceBiome.DESERT) {
            return Biomes.DESERT;
        }
        if (biome == SurfaceBiome.SNOWY) {
            return Biomes.SNOWY_PLAINS;
        }
        return Biomes.PLAINS;
    }

    private static Registry<Biome> biomeRegistry() {
        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        registry.register(Biomes.PLAINS, org.mockito.Mockito.mock(Biome.class), RegistrationInfo.BUILT_IN);
        registry.register(Biomes.DESERT, org.mockito.Mockito.mock(Biome.class), RegistrationInfo.BUILT_IN);
        registry.register(Biomes.SNOWY_PLAINS, org.mockito.Mockito.mock(Biome.class), RegistrationInfo.BUILT_IN);
        return registry;
    }

    private BlockPos findBiome(SurfaceBiome biome) {
        for (int x = -SEARCH; x <= SEARCH; x += SEARCH_STEP) {
            for (int z = -SEARCH; z <= SEARCH; z += SEARCH_STEP) {
                if (CubicOverworldGenerator.biomeAt(SEED, x, z) == biome) {
                    return new BlockPos(x, 0, z);
                }
            }
        }
        return null;
    }

    private BlockPos findWalkable(SurfaceBiome biome, BlockState cover, BlockState under) {
        for (int x = -SEARCH; x <= SEARCH; x++) {
            for (int z = -SEARCH; z <= SEARCH; z++) {
                if (CubicOverworldGenerator.biomeAt(SEED, x, z) != biome) {
                    continue;
                }
                int surface = CubicOverworldGenerator.surfaceY(SEED, x, z);
                if (surface < CubicOverworldGenerator.SEA_LEVEL) {
                    continue;
                }
                BlockPos found = walkableAt(x, z, surface, cover, under);
                if (found != null) {
                    return found;
                }
            }
        }
        throw new AssertionError("no walkable " + biome + " column");
    }

    private BlockPos walkableAt(int x, int z, int surface, BlockState cover, BlockState under) {
        for (int y = surface - 2; y <= surface + 12; y++) {
            if (!CubicOverworldGenerator.blockState(SEED, x, y, z).equals(cover)) {
                continue;
            }
            if (!CubicOverworldGenerator.blockState(SEED, x, y + 1, z).isAir()) {
                continue;
            }
            if (!CubicOverworldGenerator.blockState(SEED, x, y - 1, z).equals(under)) {
                continue;
            }
            if (CubicOverworldGenerator.blockState(SEED, x, y - 4, z).getLightDampening() != 15) {
                continue;
            }
            return new BlockPos(x, y, z);
        }
        return null;
    }

    private boolean surfaceDiffersFromOrigin(CubePos shifted) {
        int diameter = CubicConstants.DIAMETER_IN_BLOCKS;
        for (int dx = 0; dx < diameter; dx++) {
            for (int dz = 0; dz < diameter; dz++) {
                int worldX = shifted.minCubeX() + dx;
                int worldZ = shifted.minCubeZ() + dz;
                if (CubicOverworldGenerator.surfaceY(SEED, worldX, worldZ) != CubicOverworldGenerator.surfaceY(SEED, dx, dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private BlockPos findWater() {
        for (int x = -SEARCH; x <= SEARCH; x += 2) {
            for (int z = -SEARCH; z <= SEARCH; z += 2) {
                int surface = CubicOverworldGenerator.surfaceY(SEED, x, z);
                if (surface >= CubicOverworldGenerator.SEA_LEVEL) {
                    continue;
                }
                int y = CubicOverworldGenerator.SEA_LEVEL - 1;
                if (CubicOverworldGenerator.blockState(SEED, x, y, z).is(Blocks.WATER)) {
                    return new BlockPos(x, y, z);
                }
            }
        }
        throw new AssertionError("no ocean water");
    }

    /** Cheese caves miss some cubes, including {@code (0,0,0)} on the default seed. A neighbor still opens. */
    private ProtoCube findCaveCube() {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                ProtoCube cube = fill(CubePos.of(x, 0, z));
                if (countCave(cube) > 0) {
                    return cube;
                }
            }
        }
        throw new AssertionError("no cave air in cubes y=0, x/z -2..2");
    }

    private ProtoCube fill(CubePos cubePos) {
        ProtoCube cube = newProtoCube(cubePos, null);
        CubicOverworldGenerator.fillCube(cube, SEED);
        return cube;
    }

    private ProtoCube newProtoCube(CubePos cubePos, Registry<Biome> biomes) {
        LevelHeightAccessor heightAccessor = org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(heightAccessor.getMinY()).thenReturn(-(1 << 24));
        org.mockito.Mockito.when(heightAccessor.getMaxY()).thenReturn(1 << 24);
        org.mockito.Mockito.when(heightAccessor.getHeight()).thenReturn(1 << 25);
        org.mockito.Mockito.when(heightAccessor.isOutsideBuildHeight(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        org.mockito.Mockito.when(heightAccessor.isOutsideBuildHeight(org.mockito.ArgumentMatchers.any(int.class))).thenReturn(false);
        Registry<Biome> registry = biomes == null ? org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS) : biomes;
        return new ProtoCube(cubePos, org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS), heightAccessor, registry,
                org.mockito.Mockito.mock(Answers.RETURNS_DEEP_STUBS));
    }

    private static int count(ProtoCube cube, BlockState state) {
        CubePos pos = cube.cc_getCubePos();
        int diameter = CubicConstants.DIAMETER_IN_BLOCKS;
        int found = 0;
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

    private static int countOre(ProtoCube cube) {
        return count(cube, Blocks.COAL_ORE.defaultBlockState()) + count(cube, Blocks.COPPER_ORE.defaultBlockState())
                + count(cube, Blocks.IRON_ORE.defaultBlockState()) + count(cube, Blocks.GOLD_ORE.defaultBlockState())
                + count(cube, Blocks.DIAMOND_ORE.defaultBlockState()) + count(cube, Blocks.DEEPSLATE_COAL_ORE.defaultBlockState())
                + count(cube, Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState()) + count(cube, Blocks.DEEPSLATE_IRON_ORE.defaultBlockState())
                + count(cube, Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState()) + count(cube, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState());
    }

    private static int countCave(ProtoCube cube) {
        CubePos pos = cube.cc_getCubePos();
        int diameter = CubicConstants.DIAMETER_IN_BLOCKS;
        int found = 0;
        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                if (columnHasCave(cube, pos, x, z, diameter)) {
                    found++;
                }
            }
        }
        return found;
    }

    private static boolean columnHasCave(ProtoCube cube, CubePos pos, int x, int z, int diameter) {
        boolean seenSolid = false;
        boolean seenAirAfterSolid = false;
        for (int y = 0; y < diameter; y++) {
            boolean air = cube.getBlockState(pos.asBlockPos(x, y, z)).isAir();
            if (!air) {
                if (seenAirAfterSolid) {
                    return true;
                }
                seenSolid = true;
                seenAirAfterSolid = false;
            } else if (seenSolid) {
                seenAirAfterSolid = true;
            }
        }
        return false;
    }
}
