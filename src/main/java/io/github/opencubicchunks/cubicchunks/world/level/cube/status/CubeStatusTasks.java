package io.github.opencubicchunks.cubicchunks.world.level.cube.status;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddFieldToSets;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddMethodToSets;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddTransformToSets;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.notstirred.dasm.api.annotations.transform.TransformFromMethod;
import io.github.opencubicchunks.cubicchunks.levelgen.CubicOverworldGenerator;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCubeSet;
import io.github.opencubicchunks.cubicchunks.util.StaticCache3D;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.status.CCChunkStatusTasks;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.GenerationCubicLightView;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.storage.ValueInput;
import org.slf4j.Logger;

/**
 * Equivalent of {@link ChunkStatusTasks} for cube generation.
 * <p/>
 * See also: {@link CCChunkStatusTasks} for chunk generation tasks in cubic worlds.
 */
@Dasm(ChunkToCubeSet.class)
public class CubeStatusTasks {
    private CubeStatusTasks() {}

    @AddFieldToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, field = "LOGGER:Lorg/slf4j/Logger;")
    private static final Logger LOGGER = LogUtils.getLogger();

    @AddTransformToSets(ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class)
    @TransformFromMethod(owner = @Ref(ChunkStatusTasks.class), value = "isLighted(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z")
    private static native boolean isLighted(CubeAccess cube);

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "passThrough(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> passThrough(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        return CompletableFuture.completedFuture(cube);
    }

    // Structure starts stay pass-through. Vanilla jigsaw placement is 2D, asks neighboring chunks for pieces, and would wait on
    // cubes that are waiting on this one. Y-aware structures are not part of this fill.
    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "generateStructureStarts(Lnet/minecraft/world/level/chunk/status/WorldGenContext;"
            + "Lnet/minecraft/world/level/chunk/status/ChunkStep;Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)"
            + "Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> generateStructureStarts(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        return passThrough(worldGenContext, step, cache, cube);
    }

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "loadStructureStarts(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> loadStructureStarts(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        return passThrough(worldGenContext, step, cache, cube);
    }

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "generateStructureReferences(Lnet/minecraft/world/level/chunk/status/WorldGenContext;"
            + "Lnet/minecraft/world/level/chunk/status/ChunkStep;Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)"
            + "Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> generateStructureReferences(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        return passThrough(worldGenContext, step, cache, cube);
    }

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "generateBiomes(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> generateBiomes(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        CubicOverworldGenerator.fillBiomes(cube, worldSeed(worldGenContext), biomeRegistry(worldGenContext));
        return CompletableFuture.completedFuture(cube);
    }

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "generateNoise(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> generateNoise(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        // One cube, no neighbor lookup. The cache is unused so this task cannot wait on another cube.
        CubicOverworldGenerator.fillCube(cube, worldSeed(worldGenContext));
        return CompletableFuture.completedFuture(cube);
    }

    private static long worldSeed(WorldGenContext worldGenContext) {
        if (worldGenContext == null) {
            return CubicOverworldGenerator.DEFAULT_SEED;
        }
        return worldGenContext.level().getSeed();
    }

    private static @Nullable Registry<Biome> biomeRegistry(WorldGenContext worldGenContext) {
        if (worldGenContext == null) {
            return null;
        }
        try {
            return worldGenContext.level().registryAccess().lookupOrThrow(Registries.BIOME);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "generateSurface(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> generateSurface(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        // Grass, dirt, sand, snow, and the "no bedrock" rule are applied in generateNoise. A separate surface pass would
        // be the vanilla place that writes a bedrock floor at min build height.
        return passThrough(worldGenContext, step, cache, cube);
    }

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "generateCarvers(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> generateCarvers(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        return passThrough(worldGenContext, step, cache, cube);
    }

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "generateFeatures(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> generateFeatures(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        return passThrough(worldGenContext, step, cache, cube);
    }

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "initializeLight(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> initializeLight(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        return passThrough(worldGenContext, step, cache, cube);
    }

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "light(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> light(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        CubicLightEngine.lightCube(cube, new GenerationCubicLightView(cube, cache));
        return CompletableFuture.completedFuture(cube);
    }

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class, method = "generateSpawn(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;")
    public static CompletableFuture<CubeAccess> generateSpawn(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    ) {
        return passThrough(worldGenContext, step, cache, cube);
    }

    // Upgrade ProtoCube to LevelCube
    // dasm + mixin
    @AddTransformToSets(ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class)
    @TransformFromMethod(owner = @Ref(ChunkStatusTasks.class), value = "full(Lnet/minecraft/world/level/chunk/status/WorldGenContext;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;")
    public static native CompletableFuture<CubeAccess> full(
            WorldGenContext worldGenContext, CubeStep step, StaticCache3D<GenerationChunkHolder> cache, CubeAccess cube
    );

    @AddTransformToSets(ChunkToCubeSet.ChunkStatusTasks_to_CubeStatusTasks_redirects.class)
    @TransformFromMethod(owner = @Ref(ChunkStatusTasks.class), value = "postLoadProtoChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/storage/ValueInput$ValueInputList;)V")
    private static native void postLoadProtoCube(ServerLevel level, ValueInput.ValueInputList entityTags);
}
