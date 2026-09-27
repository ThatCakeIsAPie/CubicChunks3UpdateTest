package io.github.opencubicchunks.cubicchunks.levelgen;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Walkable overworld fill for one cube.
 * <p>
 * This is not a {@code NoiseBasedChunkGenerator}. Vanilla {@code fillFromNoise} builds a column from the level's minimum
 * Y to its maximum Y, and {@code buildSurface} places bedrock on that floor. A cubic world has no floor, and sampling
 * the vanilla noise router needs a {@code NoiseChunk} plus neighboring columns (blending), which the cube status graph
 * does not provide. {@code CubeAccess.getOrCreateNoiseChunk} still throws. Calling into that from a cube task can wait
 * on a neighbor that is waiting on this cube.
 * <p>
 * Instead, each column gets a height from 2D value noise and a density from that height plus a 3D warp. Caves are a
 * second 3D sample below the crust. The surface is grass, sand, or snow over dirt or sand, then stone or deepslate
 * with a few ores. Sea level fills air with water. No Y, including the vanilla bedrock layer and the bottom of a cube,
 * is forced to bedrock. The task does not read the neighbor cache and returns a completed future.
 * <p>
 * Still stubbed, on purpose: structure starts, carvers, feature placement, aquifers, and vanilla density functions.
 * Biomes are a 2D plains/desert/snowy choice painted into the section palette when that palette can store them.
 */
public final class CubicOverworldGenerator {
    /** Used when generation has no level, including unit tests that pass a null context. */
    public static final long DEFAULT_SEED = 0L;

    /** Vanilla overworld sea level. Blocks strictly below this can be water. */
    public static final int SEA_LEVEL = 63;

    /** Stone at and above this Y, deepslate below. Not a world floor. */
    public static final int DEEPSLATE_Y = 0;

    private static final int BASE_HEIGHT = 68;
    private static final int CONTINENTAL_BLOCKS = 36;
    private static final int HILL_BLOCKS = 16;
    private static final int DETAIL_BLOCKS = 4;
    /** 3D warp added to the heightmap, in blocks. Also the ocean-flood band under the heightmap. */
    private static final int WARP_BLOCKS = 8;
    /** Highest Y that density can still be positive, for skipping empty sky cubes. */
    private static final int MAX_TERRAIN_Y = BASE_HEIGHT + CONTINENTAL_BLOCKS + HILL_BLOCKS + DETAIL_BLOCKS + WARP_BLOCKS;
    /** Blocks of crust that caves may not remove, counting down from the unwarped surface. */
    private static final int CAVE_MARGIN = 4;
    /** Surface block plus this many checks upward. Distance 2..this is dirt or sand. */
    private static final int DIRT_DEPTH = 4;
    /** Third block under the surface block. 1 and 2 are not magic-number literals; 3 is. */
    private static final int CRUST_OFFSET_3 = 3;

    private static final int CONTINENTAL_PERIOD = 220;
    private static final int HILL_PERIOD = 64;
    private static final int DETAIL_PERIOD = 24;
    private static final int WARP_PERIOD = 32;
    private static final int CAVE_PERIOD = 22;
    private static final int CHEESE_PERIOD = 48;
    private static final int BIOME_PERIOD = 180;

    private static final int FBM_OCTAVES = 3;
    private static final double OCTAVE_GAIN = 0.5;
    private static final long OCTAVE_SALT = 0x9E3779B97F4A7C15L;

    private static final double CAVE_THRESHOLD = 0.42;
    private static final double CHEESE_THRESHOLD = 0.1;
    private static final double TEMP_THRESHOLD = 0.35;
    private static final double HUMID_THRESHOLD = -0.1;
    private static final double COLD_THRESHOLD = -0.35;

    private static final int ORE_MODULUS = 1024;
    private static final int DIAMOND_CUTOFF = 3;
    private static final int GOLD_CUTOFF = 8;
    private static final int IRON_CUTOFF = 20;
    private static final int COPPER_CUTOFF = 34;
    private static final int COAL_CUTOFF = 56;
    private static final int DIAMOND_MAX_Y = 16;
    private static final int GOLD_MAX_Y = 32;
    private static final int IRON_MAX_Y = 64;
    /** Y ceiling that still allows an ore, for rolls that are not depth-limited. */
    private static final int ANY_ORE_Y = Integer.MAX_VALUE;

    private static final int HASH_SHIFT = 40;
    private static final int HASH_BITS = 24;
    private static final int HASH_UNIT_BITS = 23;
    private static final long HASH_MASK = (1L << HASH_BITS) - 1L;
    private static final double HASH_TO_UNIT = 1.0 / (double) (1 << HASH_UNIT_BITS);
    private static final int MIX_SHIFT_1 = 30;
    private static final int MIX_SHIFT_2 = 27;
    private static final int MIX_SHIFT_3 = 31;
    private static final long MIX_1 = 0xBF58476D1CE4E5B9L;
    private static final long MIX_2 = 0x94D049BB133111EBL;
    private static final long X_SALT = 0x9E3779B97F4A7C15L;
    private static final long Y_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final long Z_SALT = 0x165667B19E3779F9L;
    private static final double SMOOTH_A = 3.0;
    private static final double SMOOTH_B = 2.0;

    private static final long CONTINENT_SALT = 0x517CC0_0DL;
    private static final long HILL_SALT = 0xA24B1EEDL;
    private static final long DETAIL_SALT = 0xC0FFEE01L;
    private static final long WARP_SALT = 0x5A171E5L;
    private static final long CAVE_SALT = 0xCA7E0005L;
    private static final long CHEESE_SALT = 0xC1E5E005L;
    private static final long TEMP_SALT = 0x7E3B0001L;
    private static final long HUMID_SALT = 0x11D10001L;
    private static final long ORE_SALT = 0x0E500E5L;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState COAL = Blocks.COAL_ORE.defaultBlockState();
    private static final BlockState DEEPSLATE_COAL = Blocks.DEEPSLATE_COAL_ORE.defaultBlockState();
    private static final BlockState COPPER = Blocks.COPPER_ORE.defaultBlockState();
    private static final BlockState DEEPSLATE_COPPER = Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState();
    private static final BlockState IRON = Blocks.IRON_ORE.defaultBlockState();
    private static final BlockState DEEPSLATE_IRON = Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
    private static final BlockState GOLD = Blocks.GOLD_ORE.defaultBlockState();
    private static final BlockState DEEPSLATE_GOLD = Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
    private static final BlockState DIAMOND = Blocks.DIAMOND_ORE.defaultBlockState();
    private static final BlockState DEEPSLATE_DIAMOND = Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();

    private CubicOverworldGenerator() {}

    /**
     * @return plains, desert, or snowy from two 2D noises. The same choice is used at every Y in the column.
     */
    public static SurfaceBiome biomeAt(long seed, int x, int z) {
        double temperature = fbm2(x / (double) BIOME_PERIOD, z / (double) BIOME_PERIOD, seed ^ TEMP_SALT);
        double humidity = fbm2(x / (double) BIOME_PERIOD, z / (double) BIOME_PERIOD, seed ^ HUMID_SALT);
        if (temperature > TEMP_THRESHOLD && humidity < HUMID_THRESHOLD) {
            return SurfaceBiome.DESERT;
        }
        if (temperature < COLD_THRESHOLD) {
            return SurfaceBiome.SNOWY;
        }
        return SurfaceBiome.PLAINS;
    }

    /**
     * Unwarped column height. Density may still place solid a few blocks above this, or air a few blocks below.
     */
    public static int surfaceY(long seed, int x, int z) {
        double continental = fbm2(x / (double) CONTINENTAL_PERIOD, z / (double) CONTINENTAL_PERIOD, seed ^ CONTINENT_SALT);
        double hills = fbm2(x / (double) HILL_PERIOD, z / (double) HILL_PERIOD, seed ^ HILL_SALT);
        double detail = noise2(x / (double) DETAIL_PERIOD, z / (double) DETAIL_PERIOD, seed ^ DETAIL_SALT);
        double height = BASE_HEIGHT + continental * CONTINENTAL_BLOCKS + hills * HILL_BLOCKS + detail * DETAIL_BLOCKS;
        return Mth.floor(height);
    }

    /** Block that {@link #fillCube} writes at this world position. */
    public static BlockState blockState(long seed, int x, int y, int z) {
        int surface = surfaceY(seed, x, z);
        return stateFromFlags(isSolid(seed, x, y, z, surface), isSolid(seed, x, y + 1, z, surface), isSolid(seed, x, y + 2, z, surface),
                isSolid(seed, x, y + CRUST_OFFSET_3, z, surface), isSolid(seed, x, y + DIRT_DEPTH, z, surface), y, surface, biomeAt(seed, x, z), seed,
                x, z);
    }

    /**
     * Writes terrain into {@code cube}. Air is left as the section default. Does not place bedrock.
     */
    public static void fillCube(CubeAccess cube, long seed) {
        CubePos cubePos = cube.cc_getCubePos();
        int minY = cubePos.minCubeY();
        if (minY >= SEA_LEVEL && minY >= MAX_TERRAIN_Y) {
            return;
        }
        int minX = cubePos.minCubeX();
        int minZ = cubePos.minCubeZ();
        int diameter = CubicConstants.DIAMETER_IN_BLOCKS;
        boolean[] solid = new boolean[diameter + DIRT_DEPTH];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x < minX + diameter; x++) {
            for (int z = minZ; z < minZ + diameter; z++) {
                fillColumn(cube, cursor, solid, seed, x, z, minY, diameter);
            }
        }
    }

    /**
     * Paints the 2D biome into each section. {@code biomes} is the level registry ({@code Registries.BIOME}); there is no
     * builtin biome registry in 1.21.6. A null or unusable registry leaves the section's default biome in place so a
     * mocked chunk cache still finishes generation.
     */
    public static void fillBiomes(CubeAccess cube, long seed, @Nullable Registry<Biome> biomes) {
        if (biomes == null) {
            return;
        }
        Holder<Biome> plains = holder(biomes, Biomes.PLAINS);
        Holder<Biome> desert = holder(biomes, Biomes.DESERT);
        Holder<Biome> snowy = holder(biomes, Biomes.SNOWY_PLAINS);
        if (plains == null || desert == null || snowy == null) {
            return;
        }
        try {
            paintCube(cube, seed, plains, desert, snowy);
        } catch (RuntimeException ex) {
            // A mocked holder id-map cannot store these holders. Blocks are already chosen from the same 2D biome.
        }
    }

    private static void fillColumn(
            CubeAccess cube, BlockPos.MutableBlockPos cursor, boolean[] solid, long seed, int x, int z, int minY, int diameter
    ) {
        int surface = surfaceY(seed, x, z);
        if (minY >= SEA_LEVEL && minY >= surface + WARP_BLOCKS) {
            return;
        }
        SurfaceBiome biome = biomeAt(seed, x, z);
        int span = diameter + DIRT_DEPTH;
        for (int i = 0; i < span; i++) {
            solid[i] = isSolid(seed, x, minY + i, z, surface);
        }
        for (int i = 0; i < diameter; i++) {
            BlockState state = stateFromFlags(solid[i], solid[i + 1], solid[i + 2], solid[i + CRUST_OFFSET_3], solid[i + DIRT_DEPTH], minY + i,
                    surface, biome, seed, x, z);
            if (!state.isAir()) {
                cube.setBlockState(cursor.set(x, minY + i, z), state);
            }
        }
    }

    private static BlockState stateFromFlags(
            boolean here, boolean up1, boolean up2, boolean up3, boolean up4, int y, int surface, SurfaceBiome biome, long seed, int x, int z
    ) {
        BlockState chosen;
        if (!here) {
            chosen = airOrWater(y, surface);
        } else if (!up1) {
            chosen = cover(biome, surface);
        } else if (!up2 || !up3 || !up4) {
            chosen = under(biome, surface);
        } else {
            chosen = oreOrRock(seed, x, y, z);
        }
        return forbidBedrock(chosen);
    }

    private static BlockState airOrWater(int y, int surface) {
        if (y < SEA_LEVEL && surface < SEA_LEVEL && y > surface - WARP_BLOCKS) {
            return WATER;
        }
        return AIR;
    }

    private static BlockState cover(SurfaceBiome biome, int surface) {
        if (surface < SEA_LEVEL) {
            return SAND;
        }
        if (biome == SurfaceBiome.DESERT) {
            return SAND;
        }
        if (biome == SurfaceBiome.SNOWY) {
            return SNOW;
        }
        return GRASS;
    }

    private static BlockState under(SurfaceBiome biome, int surface) {
        if (surface < SEA_LEVEL || biome == SurfaceBiome.DESERT) {
            return SAND;
        }
        return DIRT;
    }

    private static BlockState oreOrRock(long seed, int x, int y, int z) {
        boolean deepslate = y < DEEPSLATE_Y;
        int roll = oreRoll(seed, x, y, z);
        if (oreHits(y, DIAMOND_MAX_Y, roll, DIAMOND_CUTOFF)) {
            return pick(deepslate, DIAMOND, DEEPSLATE_DIAMOND);
        }
        if (oreHits(y, GOLD_MAX_Y, roll, GOLD_CUTOFF)) {
            return pick(deepslate, GOLD, DEEPSLATE_GOLD);
        }
        if (oreHits(y, IRON_MAX_Y, roll, IRON_CUTOFF)) {
            return pick(deepslate, IRON, DEEPSLATE_IRON);
        }
        if (oreHits(y, ANY_ORE_Y, roll, COPPER_CUTOFF)) {
            return pick(deepslate, COPPER, DEEPSLATE_COPPER);
        }
        if (oreHits(y, ANY_ORE_Y, roll, COAL_CUTOFF)) {
            return pick(deepslate, COAL, DEEPSLATE_COAL);
        }
        return pick(deepslate, STONE, DEEPSLATE);
    }

    private static BlockState pick(boolean deepslate, BlockState stoneState, BlockState deepState) {
        if (deepslate) {
            return deepState;
        }
        return stoneState;
    }

    private static boolean oreHits(int y, int maxY, int roll, int cutoff) {
        if (y >= maxY) {
            return false;
        }
        return roll < cutoff;
    }

    /** Cubic worlds are not a column sitting on bedrock. Deepslate is the substitute if a state ever is. */
    private static BlockState forbidBedrock(BlockState state) {
        if (state.is(Blocks.BEDROCK)) {
            return DEEPSLATE;
        }
        return state;
    }

    private static boolean isSolid(long seed, int x, int y, int z, int surface) {
        if (density(seed, x, y, z, surface) <= 0) {
            return false;
        }
        if (surface - y <= CAVE_MARGIN) {
            return true;
        }
        return !carved(seed, x, y, z);
    }

    private static double density(long seed, int x, int y, int z, int surface) {
        double warp = noise3(x / (double) WARP_PERIOD, y / (double) WARP_PERIOD, z / (double) WARP_PERIOD, seed ^ WARP_SALT);
        return (surface - y) + warp * WARP_BLOCKS;
    }

    private static boolean carved(long seed, int x, int y, int z) {
        double cave = noise3(x / (double) CAVE_PERIOD, y / (double) CAVE_PERIOD, z / (double) CAVE_PERIOD, seed ^ CAVE_SALT);
        double cheese = noise3(x / (double) CHEESE_PERIOD, y / (double) CHEESE_PERIOD, z / (double) CHEESE_PERIOD, seed ^ CHEESE_SALT);
        return cave > CAVE_THRESHOLD && cheese > CHEESE_THRESHOLD;
    }

    private static int oreRoll(long seed, int x, int y, int z) {
        long mixed = mix(seed ^ ORE_SALT ^ (x * X_SALT) ^ (y * Y_SALT) ^ (z * Z_SALT));
        return (int) Long.remainderUnsigned(mixed, ORE_MODULUS);
    }

    private static void paintCube(CubeAccess cube, long seed, Holder<Biome> plains, Holder<Biome> desert, Holder<Biome> snowy) {
        CubePos cubePos = cube.cc_getCubePos();
        int perAxis = CubicConstants.DIAMETER_IN_SECTIONS;
        int minX = cubePos.minCubeX();
        int minY = cubePos.minCubeY();
        int minZ = cubePos.minCubeZ();
        for (int sx = 0; sx < perAxis; sx++) {
            for (int sz = 0; sz < perAxis; sz++) {
                for (int sy = 0; sy < perAxis; sy++) {
                    int originX = minX + sx * CubicConstants.SECTION_DIAMETER;
                    int originY = minY + sy * CubicConstants.SECTION_DIAMETER;
                    int originZ = minZ + sz * CubicConstants.SECTION_DIAMETER;
                    paintSection(cube.getSection(Coords.blockToIndex(originX, originY, originZ)), seed, originX, originY, originZ, plains, desert,
                            snowy);
                }
            }
        }
    }

    private static void paintSection(
            LevelChunkSection section, long seed, int originX, int originY, int originZ, Holder<Biome> plains, Holder<Biome> desert,
            Holder<Biome> snowy
    ) {
        section.fillBiomesFromNoise(
                (quartX, quartY, quartZ, sampler) -> choose(biomeAt(seed, QuartPos.toBlock(quartX), QuartPos.toBlock(quartZ)), plains, desert, snowy),
                null, QuartPos.fromBlock(originX), QuartPos.fromBlock(originY), QuartPos.fromBlock(originZ));
    }

    private static Holder<Biome> choose(SurfaceBiome biome, Holder<Biome> plains, Holder<Biome> desert, Holder<Biome> snowy) {
        if (biome == SurfaceBiome.DESERT) {
            return desert;
        }
        if (biome == SurfaceBiome.SNOWY) {
            return snowy;
        }
        return plains;
    }

    private static @Nullable Holder<Biome> holder(Registry<Biome> biomes, ResourceKey<Biome> key) {
        try {
            return biomes.getOrThrow(key);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static double fbm2(double x, double z, long seed) {
        double sum = 0;
        double amplitude = 1;
        double frequency = 1;
        double total = 0;
        for (int octave = 0; octave < FBM_OCTAVES; octave++) {
            sum += amplitude * noise2(x * frequency, z * frequency, seed + octave * OCTAVE_SALT);
            total += amplitude;
            amplitude *= OCTAVE_GAIN;
            frequency *= 2;
        }
        return sum / total;
    }

    private static double noise2(double x, double z, long seed) {
        int x0 = Mth.floor(x);
        int z0 = Mth.floor(z);
        double tx = fade(x - x0);
        double tz = fade(z - z0);
        double x00 = hashSigned(x0, 0, z0, seed);
        double x10 = hashSigned(x0 + 1, 0, z0, seed);
        double x01 = hashSigned(x0, 0, z0 + 1, seed);
        double x11 = hashSigned(x0 + 1, 0, z0 + 1, seed);
        return lerp(tz, lerp(tx, x00, x10), lerp(tx, x01, x11));
    }

    private static double noise3(double x, double y, double z, long seed) {
        int x0 = Mth.floor(x);
        int y0 = Mth.floor(y);
        int z0 = Mth.floor(z);
        double tx = fade(x - x0);
        double ty = fade(y - y0);
        double tz = fade(z - z0);
        double y00 = lerp(tx, hashSigned(x0, y0, z0, seed), hashSigned(x0 + 1, y0, z0, seed));
        double y10 = lerp(tx, hashSigned(x0, y0 + 1, z0, seed), hashSigned(x0 + 1, y0 + 1, z0, seed));
        double y01 = lerp(tx, hashSigned(x0, y0, z0 + 1, seed), hashSigned(x0 + 1, y0, z0 + 1, seed));
        double y11 = lerp(tx, hashSigned(x0, y0 + 1, z0 + 1, seed), hashSigned(x0 + 1, y0 + 1, z0 + 1, seed));
        return lerp(tz, lerp(ty, y00, y10), lerp(ty, y01, y11));
    }

    private static double fade(double t) {
        return t * t * (SMOOTH_A - SMOOTH_B * t);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double hashSigned(int x, int y, int z, long seed) {
        long mixed = mix(seed ^ (x * X_SALT) ^ (y * Y_SALT) ^ (z * Z_SALT));
        long bits = (mixed >>> HASH_SHIFT) & HASH_MASK;
        return bits * HASH_TO_UNIT - 1;
    }

    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> MIX_SHIFT_1)) * MIX_1;
        z = (z ^ (z >>> MIX_SHIFT_2)) * MIX_2;
        return z ^ (z >>> MIX_SHIFT_3);
    }

    /** Column climate used to pick the surface block and the section biome. */
    public enum SurfaceBiome {
        PLAINS, DESERT, SNOWY
    }
}
