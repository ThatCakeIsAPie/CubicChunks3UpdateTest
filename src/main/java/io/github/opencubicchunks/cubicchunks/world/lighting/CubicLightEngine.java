package io.github.opencubicchunks.cubicchunks.world.lighting;

import java.util.ArrayDeque;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Cubic block light, plus a sky heuristic that does not try to be vanilla's infinite column.
 * <p>
 * Direct sun is full brightness in non-occluding blocks with a clear path upward. The search above a cube is
 * {@link #SKY_SEARCH_BLOCKS} blocks. Unloaded space on that path counts as open sky, so a ceiling that is not loaded
 * (or is farther than the search) does not cast a shadow. Horizontal sky from a neighbor is copied from that neighbor's
 * stored values when the neighbor has already been lit. Block light is a normal 15-level flood and is occluded by
 * {@link BlockState#getLightBlock()} of 15.
 * <p>
 * Generation lights each cube once. A block change relights that cube and any already-lit cube within
 * {@link #MAX_LEVEL} blocks (plus the column below, within the sky search, when opacity changes). It does not enqueue
 * vanilla column light tasks.
 */
public final class CubicLightEngine {
    public static final int MAX_LEVEL = 15;
    /** How far above a cube an opaque block can still block direct sun. Farther, or unloaded, is treated as sky. */
    public static final int SKY_SEARCH_BLOCKS = 128;

    private static final int MIN_SPREAD = 1;

    private CubicLightEngine() {}

    /**
     * Initial light for a cube that has just reached the light status, then a one-shot refresh of neighbors that were
     * already lit and therefore could not see this cube.
     */
    public static void lightCube(CubeAccess cube, CubicLightView view) {
        relight(cube, view);
        cube.setLightCorrect(true);
        refreshAlreadyLitNeighbors(cube, view);
    }

    /** Recompute {@code cube} from {@code view}. Does not walk neighbors. */
    public static void relight(CubeAccess cube, CubicLightView view) {
        if (tryFastOpaqueCube(cube) || tryFastClearCube(cube, view)) {
            return;
        }
        LightVolume volume = new LightVolume(cube.cc_getCubePos());
        ArrayDeque<Long> blockQueue = new ArrayDeque<>();
        seedBlockLight(volume, view, blockQueue);
        propagate(volume, view, blockQueue, false);
        fillSky(volume, view);
        ArrayDeque<Long> skyQueue = new ArrayDeque<>();
        enqueueSkySources(volume, view, skyQueue);
        propagate(volume, view, skyQueue, true);
        writeCube(volume, cube);
    }

    /**
     * Gameplay update. Skips cubes that are not light-correct yet so worldgen can finish its own {@link #lightCube} pass.
     */
    public static void onBlockChanged(Level level, BlockPos pos, BlockState previous, BlockState current) {
        if (!(level instanceof CanBeCubic cubic) || !cubic.cc_isCubic()) {
            return;
        }
        if (!(level.getChunkSource() instanceof CubeSource source)) {
            return;
        }
        CubicLightView view = new SourceCubicLightView(source);
        CubePos editedPos = new CubePos(pos);
        CubeAccess edited = view.cubeAt(editedPos.getX(), editedPos.getY(), editedPos.getZ());
        if (edited == null) {
            return;
        }
        relight(edited, view);
        edited.setLightCorrect(true);
        LongOpenHashSet others = cubesToRelight(pos, previous, current);
        LongIterator iterator = others.iterator();
        while (iterator.hasNext()) {
            CubePos cubePos = new CubePos(iterator.nextLong());
            if (cubePos.equals(editedPos)) {
                continue;
            }
            refreshIfLit(view, cubePos.getX(), cubePos.getY(), cubePos.getZ());
        }
    }

    private static void refreshAlreadyLitNeighbors(CubeAccess cube, CubicLightView view) {
        CubePos pos = cube.cc_getCubePos();
        refreshIfLit(view, pos.getX() + 1, pos.getY(), pos.getZ());
        refreshIfLit(view, pos.getX() - 1, pos.getY(), pos.getZ());
        refreshIfLit(view, pos.getX(), pos.getY(), pos.getZ() + 1);
        refreshIfLit(view, pos.getX(), pos.getY(), pos.getZ() - 1);
        int minY = pos.getY() - skyDependentCubes();
        for (int y = pos.getY() - 1; y >= minY; y--) {
            refreshIfLit(view, pos.getX(), y, pos.getZ());
        }
    }

    private static void refreshIfLit(CubicLightView view, int cubeX, int cubeY, int cubeZ) {
        CubeAccess cube = view.cubeAt(cubeX, cubeY, cubeZ);
        if (cube != null && cube.isLightCorrect()) {
            relight(cube, view);
        }
    }

    private static int skyDependentCubes() {
        return Math.max(1, SKY_SEARCH_BLOCKS / CubicConstants.DIAMETER_IN_BLOCKS);
    }

    private static LongOpenHashSet cubesToRelight(BlockPos pos, BlockState previous, BlockState current) {
        LongOpenHashSet cubes = new LongOpenHashSet();
        addCubeRange(cubes, pos, MAX_LEVEL);
        if (lightBlock(previous) != lightBlock(current)) {
            addSkyColumn(cubes, pos);
        }
        return cubes;
    }

    private static void addCubeRange(LongOpenHashSet cubes, BlockPos pos, int reach) {
        int minX = Coords.blockToCube(pos.getX() - reach);
        int maxX = Coords.blockToCube(pos.getX() + reach);
        int minY = Coords.blockToCube(pos.getY() - reach);
        int maxY = Coords.blockToCube(pos.getY() + reach);
        int minZ = Coords.blockToCube(pos.getZ() - reach);
        int maxZ = Coords.blockToCube(pos.getZ() + reach);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cubes.add(CubePos.asLong(x, y, z));
                }
            }
        }
    }

    private static void addSkyColumn(LongOpenHashSet cubes, BlockPos pos) {
        int cubeX = Coords.blockToCube(pos.getX());
        int cubeZ = Coords.blockToCube(pos.getZ());
        int cubeY = Coords.blockToCube(pos.getY());
        int minY = Coords.blockToCube(pos.getY() - SKY_SEARCH_BLOCKS);
        for (int y = cubeY - 1; y >= minY; y--) {
            cubes.add(CubePos.asLong(cubeX, y, cubeZ));
        }
    }

    /**
     * A cube of full occluders with no emitters stores zeros. Sky and neighbor torches cannot enter.
     */
    private static boolean tryFastOpaqueCube(CubeAccess cube) {
        if (!isFullyOpaque(cube)) {
            return false;
        }
        cube.cc_lightData().clear();
        return true;
    }

    private static boolean isFullyOpaque(CubeAccess cube) {
        for (LevelChunkSection section : cube.getSections()) {
            if (section.hasOnlyAir() || section.maybeHas(CubicLightEngine::admitsLight)) {
                return false;
            }
        }
        return true;
    }

    private static boolean admitsLight(BlockState state) {
        return lightBlock(state) < MAX_LEVEL || emitsLight(state);
    }

    /**
     * An all-air cube with a clear (or unloaded) sky and no nearby emitter is uniform sky. Building the flood volume for
     * every such cube is what made a spawn-radius load stall.
     */
    private static boolean tryFastClearCube(CubeAccess cube, CubicLightView view) {
        if (!isAllAir(cube) || hasNearbyEmitter(cube, view) || !skyAboveIsClear(cube, view)) {
            return false;
        }
        cube.cc_lightData().fillSky(MAX_LEVEL);
        return true;
    }

    private static boolean isAllAir(CubeAccess cube) {
        for (LevelChunkSection section : cube.getSections()) {
            if (!section.hasOnlyAir()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNearbyEmitter(CubeAccess cube, CubicLightView view) {
        CubePos pos = cube.cc_getCubePos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (neighborEmits(view, pos, dx, dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean neighborEmits(CubicLightView view, CubePos pos, int dx, int dy) {
        for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) {
                continue;
            }
            CubeAccess neighbor = view.cubeAt(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
            if (neighbor != null && emitsAnywhere(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean emitsAnywhere(CubeAccess cube) {
        for (LevelChunkSection section : cube.getSections()) {
            if (section.maybeHas(CubicLightEngine::emitsLight)) {
                return true;
            }
        }
        return false;
    }

    private static boolean skyAboveIsClear(CubeAccess cube, CubicLightView view) {
        CubePos pos = cube.cc_getCubePos();
        int y = pos.maxCubeY() + 1;
        int end = y + SKY_SEARCH_BLOCKS - 1;
        while (y <= end) {
            CubeAccess above = view.cubeAt(pos.getX(), Coords.blockToCube(y), pos.getZ());
            if (above == null) {
                return true;
            }
            if (!isAllAir(above)) {
                return false;
            }
            y = above.cc_getCubePos().maxCubeY() + 1;
        }
        return true;
    }

    private static void seedBlockLight(LightVolume volume, CubicLightView view, ArrayDeque<Long> queue) {
        int minCubeX = Coords.blockToCube(volume.minX);
        int maxCubeX = Coords.blockToCube(volume.minX + volume.size - 1);
        int minCubeY = Coords.blockToCube(volume.minY());
        int maxCubeY = Coords.blockToCube(volume.minY() + volume.size - 1);
        int minCubeZ = Coords.blockToCube(volume.minZ);
        int maxCubeZ = Coords.blockToCube(volume.minZ + volume.size - 1);
        for (int x = minCubeX; x <= maxCubeX; x++) {
            for (int y = minCubeY; y <= maxCubeY; y++) {
                for (int z = minCubeZ; z <= maxCubeZ; z++) {
                    CubeAccess cube = view.cubeAt(x, y, z);
                    if (cube != null) {
                        seedCubeEmitters(volume, cube, queue);
                    }
                }
            }
        }
    }

    private static void seedCubeEmitters(LightVolume volume, CubeAccess cube, ArrayDeque<Long> queue) {
        LevelChunkSection[] sections = cube.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.maybeHas(CubicLightEngine::emitsLight)) {
                seedSectionEmitters(volume, cube, index, section, queue);
            }
        }
    }

    private static void seedSectionEmitters(LightVolume volume, CubeAccess cube, int index, LevelChunkSection section, ArrayDeque<Long> queue) {
        SectionPos sectionPos = Coords.sectionPosByIndex(cube.cc_getCubePos(), index);
        int originX = Coords.sectionToMinBlock(sectionPos.getX());
        int originY = Coords.sectionToMinBlock(sectionPos.getY());
        int originZ = Coords.sectionToMinBlock(sectionPos.getZ());
        int edge = CubicConstants.SECTION_DIAMETER;
        for (int localY = 0; localY < edge; localY++) {
            for (int localZ = 0; localZ < edge; localZ++) {
                for (int localX = 0; localX < edge; localX++) {
                    seedEmitter(volume, section, queue, originX + localX, originY + localY, originZ + localZ, localX, localY, localZ);
                }
            }
        }
    }

    private static void seedEmitter(
            LightVolume volume, LevelChunkSection section, ArrayDeque<Long> queue, int x, int y, int z, int localX, int localY, int localZ
    ) {
        if (!volume.contains(x, y, z)) {
            return;
        }
        int emission = lightEmission(section.getBlockState(localX, localY, localZ));
        if (emission <= 0) {
            return;
        }
        volume.setBlock(x, y, z, emission);
        queue.add(volume.pack(x, y, z));
    }

    private static void fillSky(LightVolume volume, CubicLightView view) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = volume.minX; x <= volume.minX + volume.size - 1; x++) {
            for (int z = volume.minZ; z <= volume.minZ + volume.size - 1; z++) {
                fillSkyColumn(volume, view, pos, x, z);
            }
        }
    }

    private static void fillSkyColumn(LightVolume volume, CubicLightView view, BlockPos.MutableBlockPos pos, int x, int z) {
        boolean ownColumn = volume.inTargetColumn(x, z);
        // Margin columns do not invent sun from unloaded space; they import a neighbor's stored sky instead.
        boolean sun = ownColumn && seesSky(view, pos, x, volume.maxY(), z);
        for (int y = volume.maxY(); y >= volume.minY(); y--) {
            sun = applySkyCell(volume, view, pos, x, y, z, ownColumn, sun);
        }
    }

    private static boolean applySkyCell(
            LightVolume volume, CubicLightView view, BlockPos.MutableBlockPos pos, int x, int y, int z, boolean ownColumn, boolean sun
    ) {
        BlockState state = stateAt(view, pos, x, y, z);
        if (state == null) {
            return ownColumn && sun;
        }
        if (sun && lightBlock(state) == 0) {
            volume.setSky(x, y, z, MAX_LEVEL);
            return true;
        }
        seedStoredSky(volume, view, x, y, z, ownColumn);
        return false;
    }

    private static void seedStoredSky(LightVolume volume, CubicLightView view, int x, int y, int z, boolean ownColumn) {
        if (ownColumn) {
            return;
        }
        int stored = storedSky(volume, view, x, y, z);
        if (stored <= 0) {
            return;
        }
        volume.setSky(x, y, z, stored);
    }

    /** Queue only cells that can still raise a neighbor. A fully lit open cube then does not flood every block. */
    private static void enqueueSkySources(LightVolume volume, CubicLightView view, ArrayDeque<Long> queue) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int maxX = volume.minX + volume.size - 1;
        int maxY = volume.maxY();
        int maxZ = volume.minZ + volume.size - 1;
        for (int x = volume.minX; x <= maxX; x++) {
            for (int y = volume.minY(); y <= maxY; y++) {
                enqueueSkyRow(volume, view, pos, queue, x, y, maxZ);
            }
        }
    }

    private static void enqueueSkyRow(
            LightVolume volume, CubicLightView view, BlockPos.MutableBlockPos pos, ArrayDeque<Long> queue, int x, int y, int maxZ
    ) {
        for (int z = volume.minZ; z <= maxZ; z++) {
            int level = volume.getSky(x, y, z);
            if (level > MIN_SPREAD && canSpreadSky(volume, view, pos, x, y, z, level)) {
                queue.add(volume.pack(x, y, z));
            }
        }
    }

    private static boolean canSpreadSky(LightVolume volume, CubicLightView view, BlockPos.MutableBlockPos pos, int x, int y, int z, int level) {
        return spreadsSkyInto(volume, view, pos, x + 1, y, z, level) || spreadsSkyInto(volume, view, pos, x - 1, y, z, level)
                || spreadsSkyInto(volume, view, pos, x, y + 1, z, level) || spreadsSkyInto(volume, view, pos, x, y - 1, z, level)
                || spreadsSkyInto(volume, view, pos, x, y, z + 1, level) || spreadsSkyInto(volume, view, pos, x, y, z - 1, level);
    }

    private static boolean spreadsSkyInto(LightVolume volume, CubicLightView view, BlockPos.MutableBlockPos pos, int x, int y, int z, int level) {
        if (!volume.contains(x, y, z) || volume.getSky(x, y, z) >= level - MIN_SPREAD) {
            return false;
        }
        BlockState state = stateAt(view, pos, x, y, z);
        if (state == null) {
            return false;
        }
        return level - Math.max(MIN_SPREAD, lightBlock(state)) > volume.getSky(x, y, z);
    }

    private static boolean seesSky(CubicLightView view, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        int top = y + SKY_SEARCH_BLOCKS;
        for (int scanY = y + 1; scanY <= top; scanY++) {
            BlockState state = stateAt(view, pos, x, scanY, z);
            if (state == null) {
                return true;
            }
            if (lightBlock(state) > 0) {
                return false;
            }
        }
        return true;
    }

    private static int storedSky(LightVolume volume, CubicLightView view, int x, int y, int z) {
        if (volume.inTarget(x, y, z)) {
            return 0;
        }
        CubeAccess cube = view.cubeAt(Coords.blockToCube(x), Coords.blockToCube(y), Coords.blockToCube(z));
        if (cube == null || !cube.isLightCorrect()) {
            return 0;
        }
        return cube.cc_lightData().skyLight(x, y, z);
    }

    private static void propagate(LightVolume volume, CubicLightView view, ArrayDeque<Long> queue, boolean sky) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            long packed = queue.removeFirst();
            spreadFrom(volume, view, pos, queue, volume.unpackX(packed), volume.unpackY(packed), volume.unpackZ(packed), sky);
        }
    }

    private static void spreadFrom(
            LightVolume volume, CubicLightView view, BlockPos.MutableBlockPos pos, ArrayDeque<Long> queue, int x, int y, int z, boolean sky
    ) {
        int level = sky ? volume.getSky(x, y, z) : volume.getBlock(x, y, z);
        if (level <= MIN_SPREAD) {
            return;
        }
        spreadOne(volume, view, pos, queue, x + 1, y, z, level, sky);
        spreadOne(volume, view, pos, queue, x - 1, y, z, level, sky);
        spreadOne(volume, view, pos, queue, x, y + 1, z, level, sky);
        spreadOne(volume, view, pos, queue, x, y - 1, z, level, sky);
        spreadOne(volume, view, pos, queue, x, y, z + 1, level, sky);
        spreadOne(volume, view, pos, queue, x, y, z - 1, level, sky);
    }

    private static void spreadOne(
            LightVolume volume, CubicLightView view, BlockPos.MutableBlockPos pos, ArrayDeque<Long> queue, int x, int y, int z, int level, boolean sky
    ) {
        if (!volume.contains(x, y, z)) {
            return;
        }
        BlockState state = stateAt(view, pos, x, y, z);
        if (state == null) {
            return;
        }
        int spread = level - Math.max(MIN_SPREAD, lightBlock(state));
        int current = sky ? volume.getSky(x, y, z) : volume.getBlock(x, y, z);
        if (spread <= current) {
            return;
        }
        if (sky) {
            volume.setSky(x, y, z, spread);
        } else {
            volume.setBlock(x, y, z, spread);
        }
        queue.add(volume.pack(x, y, z));
    }

    private static void writeCube(LightVolume volume, CubeAccess cube) {
        CubeLightData data = cube.cc_lightData();
        data.clear();
        CubePos pos = cube.cc_getCubePos();
        for (int x = pos.minCubeX(); x <= pos.maxCubeX(); x++) {
            for (int y = pos.minCubeY(); y <= pos.maxCubeY(); y++) {
                writeColumn(volume, data, x, y, pos.minCubeZ(), pos.maxCubeZ());
            }
        }
    }

    private static void writeColumn(LightVolume volume, CubeLightData data, int x, int y, int minZ, int maxZ) {
        for (int z = minZ; z <= maxZ; z++) {
            int block = volume.getBlock(x, y, z);
            int sky = volume.getSky(x, y, z);
            if (block != 0) {
                data.setBlockLight(x, y, z, block);
            }
            if (sky != 0) {
                data.setSkyLight(x, y, z, sky);
            }
        }
    }

    private static @Nullable BlockState stateAt(CubicLightView view, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        CubeAccess cube = view.cubeAt(Coords.blockToCube(x), Coords.blockToCube(y), Coords.blockToCube(z));
        if (cube == null) {
            return null;
        }
        return cube.getBlockState(pos.set(x, y, z));
    }

    private static boolean emitsLight(BlockState state) {
        return lightEmission(state) > 0;
    }

    static int lightEmission(BlockState state) {
        return state.getLightEmission();
    }

    static int lightBlock(BlockState state) {
        return state.getLightBlock();
    }
}
