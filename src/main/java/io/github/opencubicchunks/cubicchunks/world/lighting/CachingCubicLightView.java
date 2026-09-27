package io.github.opencubicchunks.cubicchunks.world.lighting;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * One relight's cube and section lookups. A flood touches the same sections thousands of times.
 */
final class CachingCubicLightView implements CubicLightView {
    private static final int SECTION_MASK = CubicConstants.SECTION_DIAMETER - 1;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final CubicLightView delegate;
    private final Long2ObjectOpenHashMap<CubeAccess> cubes = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet missing = new LongOpenHashSet();
    private final Long2ObjectOpenHashMap<SectionFacts[]> facts = new Long2ObjectOpenHashMap<>();

    CachingCubicLightView(CubicLightView delegate) {
        this.delegate = delegate;
    }

    @Override public @Nullable CubeAccess cubeAt(int cubeX, int cubeY, int cubeZ) {
        long key = CubePos.asLong(cubeX, cubeY, cubeZ);
        if (this.missing.contains(key)) {
            return null;
        }
        CubeAccess cached = this.cubes.get(key);
        if (cached != null) {
            return cached;
        }
        CubeAccess cube = this.delegate.cubeAt(cubeX, cubeY, cubeZ);
        if (cube == null) {
            this.missing.add(key);
            return null;
        }
        this.cubes.put(key, cube);
        return cube;
    }

    @Nullable BlockState stateAt(int x, int y, int z) {
        CubeAccess cube = this.cubeAt(Coords.blockToCube(x), Coords.blockToCube(y), Coords.blockToCube(z));
        if (cube == null) {
            return null;
        }
        SectionFacts fact = this.factsFor(cube)[Coords.blockToIndex(x, y, z)];
        if (fact.uniform != null) {
            return fact.uniform;
        }
        LevelChunkSection section = cube.getSection(Coords.blockToIndex(x, y, z));
        return section.getBlockState(x & SECTION_MASK, y & SECTION_MASK, z & SECTION_MASK);
    }

    private SectionFacts[] factsFor(CubeAccess cube) {
        long key = cube.cc_getCubePos().asLong();
        SectionFacts[] cached = this.facts.get(key);
        if (cached != null) {
            return cached;
        }
        LevelChunkSection[] sections = cube.getSections();
        SectionFacts[] built = new SectionFacts[sections.length];
        for (int i = 0; i < sections.length; i++) {
            built[i] = SectionFacts.of(sections[i]);
        }
        this.facts.put(key, built);
        return built;
    }

    private static final class SectionFacts {
        private final @Nullable BlockState uniform;

        private SectionFacts(@Nullable BlockState uniform) {
            this.uniform = uniform;
        }

        private static SectionFacts of(LevelChunkSection section) {
            if (section.hasOnlyAir()) {
                return new SectionFacts(AIR);
            }
            BlockState first = section.getBlockState(0, 0, 0);
            if (!section.maybeHas(state -> state != first)) {
                return new SectionFacts(first);
            }
            return new SectionFacts(null);
        }
    }
}
