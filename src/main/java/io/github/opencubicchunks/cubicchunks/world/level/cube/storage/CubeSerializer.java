package io.github.opencubicchunks.cubicchunks.world.level.cube.storage;

import java.util.Optional;

import com.mojang.serialization.Codec;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.ImposterProtoCube;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.level.cube.ProtoCube;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightView;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;

/**
 * Block-state persistence for cubes.
 * <p>
 * This is intentionally narrower than {@code SerializableChunkData}: heightmaps, biomes, block entities, ticks, structures, and POI are not
 * written. Block and sky nibbles are stored under {@code cc_light} so a reload does not have to guess them. {@link CubeAccess#getHeightmaps()}
 * throws, so cubes must not go through the vanilla chunk serializer.
 * A full cube is returned as an {@link ImposterProtoCube} so the cube {@code full()} step can unwrap it the same way vanilla unwraps
 * {@code ImposterProtoChunk}.
 */
public final class CubeSerializer {
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY,
            BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());

    private CubeSerializer() {}

    public static CompoundTag write(CubeAccess cube) {
        CompoundTag tag = new CompoundTag();
        var cubePos = cube.cc_getCubePos();
        tag.putInt("xPos", cubePos.getX());
        tag.putInt("yPos", cubePos.getY());
        tag.putInt("zPos", cubePos.getZ());
        tag.putBoolean("cc_cube", true);
        ChunkStatus status = cube.getPersistedStatus();
        ResourceLocation statusKey = BuiltInRegistries.CHUNK_STATUS.getKey(status);
        if (statusKey == null) {
            throw new IllegalStateException("Unregistered chunk status " + status);
        }
        tag.putString("Status", statusKey.toString());
        tag.putLong("InhabitedTime", cube.getInhabitedTime());
        NbtUtils.addCurrentDataVersion(tag);

        ListTag sectionsTag = new ListTag();
        LevelChunkSection[] sections = cube.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            CompoundTag sectionTag = new CompoundTag();
            sectionTag.putInt("i", index);
            sectionTag.store("block_states", BLOCK_STATE_CODEC, section.getStates());
            sectionsTag.add(sectionTag);
        }
        tag.put("sections", sectionsTag);
        tag.putByteArray("cc_light", cube.cc_lightData().writePacked());
        return tag;
    }

    public static CloAccess read(Level level, CloPos cloPos, CompoundTag tag) {
        var cubePos = cloPos.cubePos();
        int fileX = tag.getIntOr("xPos", cubePos.getX());
        int fileY = tag.getIntOr("yPos", cubePos.getY());
        int fileZ = tag.getIntOr("zPos", cubePos.getZ());
        if (fileX != cubePos.getX() || fileY != cubePos.getY() || fileZ != cubePos.getZ()) {
            CubicChunks.LOGGER.warn("Cube file position ({}, {}, {}) does not match requested {}", fileX, fileY, fileZ, cubePos);
        }

        ChunkStatus status = tag.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY);
        long inhabitedTime = tag.getLongOr("InhabitedTime", 0L);
        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        LevelChunkSection[] sections = readSections(tag, biomeRegistry);

        if (status.getChunkType() == ChunkType.LEVELCHUNK) {
            LevelCube levelCube = new LevelCube(level, cubePos, UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), inhabitedTime,
                    sections, null, null);
            readLight(levelCube, tag, true);
            return new ImposterProtoCube(levelCube, false);
        }

        ProtoCube protoCube = new ProtoCube(cubePos, UpgradeData.EMPTY, sections, new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), level,
                biomeRegistry, null);
        protoCube.setInhabitedTime(inhabitedTime);
        protoCube.setPersistedStatus(status);
        readLight(protoCube, tag, false);
        return protoCube;
    }

    /**
     * Full cubes skip the light status on reload, so missing light is recomputed from this cube alone (unloaded neighbors count as open sky).
     */
    private static void readLight(CubeAccess cube, CompoundTag tag, boolean relightIfMissing) {
        tag.getByteArray("cc_light").ifPresent(bytes -> {
            if (bytes.length > 0) {
                cube.cc_lightData().readPacked(bytes);
                cube.setLightCorrect(true);
            }
        });
        if (relightIfMissing && !cube.isLightCorrect()) {
            CubicLightEngine.relight(cube, CubicLightView.only(cube));
            cube.setLightCorrect(true);
        }
    }

    private static LevelChunkSection[] readSections(CompoundTag tag, Registry<Biome> biomeRegistry) {
        LevelChunkSection[] sections = new LevelChunkSection[CubicConstants.SECTION_COUNT];
        ListTag sectionsTag = tag.getListOrEmpty("sections");
        for (int listIndex = 0; listIndex < sectionsTag.size(); listIndex++) {
            Optional<CompoundTag> sectionOptional = sectionsTag.getCompound(listIndex);
            if (sectionOptional.isEmpty()) {
                continue;
            }
            CompoundTag sectionTag = sectionOptional.get();
            int index = sectionTag.getIntOr("i", -1);
            if (index < 0 || index >= sections.length) {
                CubicChunks.LOGGER.warn("Skipping cube section with index {}", index);
                continue;
            }
            PalettedContainer<BlockState> states = sectionTag.read("block_states", BLOCK_STATE_CODEC)
                    .orElseGet(() -> new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(),
                            PalettedContainer.Strategy.SECTION_STATES));
            LevelChunkSection biomes = new LevelChunkSection(biomeRegistry);
            sections[index] = new LevelChunkSection(states, biomes.getBiomes());
        }
        return sections;
    }
}
