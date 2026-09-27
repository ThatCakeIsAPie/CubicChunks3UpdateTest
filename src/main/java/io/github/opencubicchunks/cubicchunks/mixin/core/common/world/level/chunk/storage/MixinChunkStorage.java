package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.chunk.storage;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.mojang.datafixers.DataFixer;
import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddMethodToSets;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.world.level.cube.storage.CubeRegionStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Dasm(value = ChunkToCloSet.class, target = @Ref(ChunkStorage.class))
@Mixin(ChunkStorage.class)
public abstract class MixinChunkStorage {
    @Shadow public abstract boolean isOldChunkAround(ChunkPos chunkPos, int radius);

    @Shadow public abstract CompletableFuture<Optional<CompoundTag>> read(ChunkPos chunkPos);

    @Shadow public abstract CompletableFuture<Void> write(ChunkPos chunkPos, Supplier<CompoundTag> tagSupplier);

    private RegionStorageInfo cc_storageInfo;
    private Path cc_regionFolder;
    private boolean cc_sync;
    private volatile @Nullable CubeRegionStorage cc_cubeStorage;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cc_initCubeStorage(RegionStorageInfo info, Path folder, DataFixer fixerUpper, boolean sync, CallbackInfo ci) {
        this.cc_storageInfo = info;
        this.cc_regionFolder = folder;
        this.cc_sync = sync;
    }

    @AddMethodToSets(containers = ChunkToCloSet.ChunkStorage_redirects.class, method = "isOldChunkAround(Lnet/minecraft/world/level/ChunkPos;I)Z")
    public boolean cc_isOldChunkAround(CloPos pos, int radius) {
        if (pos.isChunk()) {
            return this.isOldChunkAround(pos.chunkPos(), radius);
        }
        // Cubes have no legacy 2D region scan. A missing cube is generated, not upgraded.
        return false;
    }

    @AddMethodToSets(containers = ChunkToCloSet.ChunkStorage_redirects.class, method = "read(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;")
    public CompletableFuture<Optional<CompoundTag>> cc_read(CloPos cloPos) {
        if (cloPos.isChunk()) {
            return this.read(cloPos.chunkPos());
        }
        return this.cc_cubeStorage().read(cloPos);
    }

    @AddMethodToSets(containers = ChunkToCloSet.ChunkStorage_redirects.class, method = "write(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;")
    public CompletableFuture<Void> cc_write(CloPos cloPos, Supplier<CompoundTag> tagSupplier) {
        if (cloPos.isChunk()) {
            return this.write(cloPos.chunkPos(), tagSupplier);
        }
        return this.cc_cubeStorage().write(cloPos, tagSupplier);
    }

    @Inject(method = "flushWorker", at = @At("RETURN"))
    private void cc_flushCubeWorker(CallbackInfo ci) {
        CubeRegionStorage storage = this.cc_cubeStorage;
        if (storage != null) {
            storage.flush();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void cc_closeCubeStorage(CallbackInfo ci) {
        CubeRegionStorage storage = this.cc_cubeStorage;
        if (storage != null) {
            storage.close();
            this.cc_cubeStorage = null;
        }
    }

    private CubeRegionStorage cc_cubeStorage() {
        CubeRegionStorage storage = this.cc_cubeStorage;
        if (storage == null) {
            synchronized (this) {
                storage = this.cc_cubeStorage;
                if (storage == null) {
                    storage = new CubeRegionStorage(this.cc_storageInfo.withTypeSuffix("cubes"), this.cc_regionFolder.resolveSibling("region3d"),
                            this.cc_sync);
                    this.cc_cubeStorage = storage;
                }
            }
        }
        return storage;
    }
}
