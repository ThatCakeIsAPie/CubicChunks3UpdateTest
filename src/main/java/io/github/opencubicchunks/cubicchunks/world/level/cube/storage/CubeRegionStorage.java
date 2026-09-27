package io.github.opencubicchunks.cubicchunks.world.level.cube.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.world.level.CloPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/**
 * Persists cubes in vanilla region files, one horizontal region directory per cube Y.
 * <p>
 * Cube X/Z do not fit in a column region file (distinct Y layers would share the same {@link ChunkPos} key), so each cube Y gets its own
 * {@code region3d/y&lt;cubeY&gt;/r.&lt;regionX&gt;.&lt;regionZ&gt;.mca}. Within a file the key is {@code ChunkPos(cubeX, cubeZ)}.
 * All region-file access happens on a single thread.
 */
public final class CubeRegionStorage implements AutoCloseable {
    /** Vanilla region files are 32 by 32 chunks. */
    private static final int REGION_BITS = 5;

    private final RegionStorageInfo info;
    private final Path folder;
    private final boolean sync;
    private final ExecutorService executor;
    private final Map<Key, RegionFile> regions = new HashMap<>();

    public CubeRegionStorage(RegionStorageInfo info, Path folder, boolean sync) {
        this.info = info;
        this.folder = folder;
        this.sync = sync;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Cube region IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<Optional<CompoundTag>> read(CloPos cloPos) {
        return this.submit(() -> this.readSync(cloPos));
    }

    public CompletableFuture<Void> write(CloPos cloPos, Supplier<CompoundTag> tagSupplier) {
        return this.submit(() -> {
            CompoundTag tag = tagSupplier.get();
            if (tag != null) {
                this.writeSync(cloPos, tag);
            }
            return null;
        });
    }

    /**
     * Waits for queued reads and writes, then forces region files to disk.
     */
    public void flush() {
        this.submit(() -> {
            for (RegionFile regionFile : this.regions.values()) {
                regionFile.flush();
            }
            return null;
        }).join();
    }

    @Override public void close() {
        try {
            this.submit(() -> {
                IOException failure = null;
                for (RegionFile regionFile : this.regions.values()) {
                    try {
                        regionFile.close();
                    } catch (IOException exception) {
                        failure = exception;
                    }
                }
                this.regions.clear();
                if (failure != null) {
                    throw failure;
                }
                return null;
            }).join();
        } finally {
            this.executor.shutdown();
        }
    }

    private Optional<CompoundTag> readSync(CloPos cloPos) throws IOException {
        int cubeX = cloPos.getX();
        int cubeZ = cloPos.getZ();
        RegionFile regionFile = this.getRegionFile(cubeX, cloPos.getY(), cubeZ);
        try (DataInputStream input = regionFile.getChunkDataInputStream(new ChunkPos(cubeX, cubeZ))) {
            if (input == null) {
                return Optional.empty();
            }
            return Optional.of(NbtIo.read(input));
        }
    }

    private void writeSync(CloPos cloPos, CompoundTag tag) throws IOException {
        int cubeX = cloPos.getX();
        int cubeZ = cloPos.getZ();
        RegionFile regionFile = this.getRegionFile(cubeX, cloPos.getY(), cubeZ);
        try (DataOutputStream output = regionFile.getChunkDataOutputStream(new ChunkPos(cubeX, cubeZ))) {
            NbtIo.write(tag, output);
        }
    }

    private RegionFile getRegionFile(int cubeX, int cubeY, int cubeZ) throws IOException {
        int regionX = cubeX >> REGION_BITS;
        int regionZ = cubeZ >> REGION_BITS;
        Key key = new Key(regionX, cubeY, regionZ);
        RegionFile existing = this.regions.get(key);
        if (existing != null) {
            return existing;
        }
        Path layerDirectory = this.folder.resolve("y" + cubeY);
        Path externalDirectory = layerDirectory.resolve("external");
        Files.createDirectories(externalDirectory);
        Path regionPath = layerDirectory.resolve("r." + regionX + "." + regionZ + ".mca");
        RegionFile regionFile = new RegionFile(this.info, regionPath, externalDirectory, this.sync);
        this.regions.put(key, regionFile);
        return regionFile;
    }

    private <T> CompletableFuture<T> submit(IoTask<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            this.executor.execute(() -> {
                try {
                    future.complete(task.run());
                } catch (Exception exception) {
                    future.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    @FunctionalInterface
    private interface IoTask<T> {
        @Nullable T run() throws IOException;
    }

    private record Key(int regionX, int cubeY, int regionZ) {}
}
