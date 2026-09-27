# Cubic Chunks 3 — Phase 2

Working copy: [ThatCakeIsAPie/CubicChunks3UpdateTest](https://github.com/ThatCakeIsAPie/CubicChunks3UpdateTest), branched from `dev` @ `9d06fc86` (Phase 1).

Phase 2 scope: cube block states survive a save and a new `ServerChunkCache` on the same dimension directory. **Not** lighting, heightmaps, a real `NoiseBasedChunkGenerator`, block entities, ticks, structures, POI, forced-ticket NBT, a 26.x port, or Paper.

## What persists

`ChunkMap.cc_save` no longer returns false for every CLO.

- Column chunks still go through vanilla `ChunkMap.save`.
- Cubes skip POI flush and `SerializableChunkData.copyOf` (that calls `getHeightmaps()`, which throws on a cube). `CubeSerializer` writes `Status`, `DataVersion`, cube x/y/z, inhabited time, and non-empty section block-state palettes.
- Bytes go to vanilla `RegionFile`s under `region3d/y<cubeY>/r.<regionX>.<regionZ>.mca`, keyed by `ChunkPos(cubeX, cubeZ)`. One directory per cube Y so two cubes that share X/Z do not overwrite each other. `ChunkStorage.cc_read` / `cc_write` delegate columns to the vanilla worker and cubes to that storage. `flushWorker` and `close` flush and close it.

Load does not return a raw `LevelCube`. `cc_scheduleChunkLoad` parses the tag on the main thread and, for a full cube, returns `new ImposterProtoCube(levelCube, false)`. The existing cube `full()` step is a DASM of vanilla `ChunkStatusTasks.full` and unwraps that imposter. `Status` is written with `ChunkStatus.CODEC`, and the tag carries the current data version, so the already-transformed `cc_readChunk` / `cc_isExistingChunkFull` path does not datafix the cube or treat it as empty. A cube whose persisted status is `FULL` is not sent through `generateNoise` again.

`saveAllChunks` was already redirected for cubic worlds. `ServerChunkCache.save(true)` (and the server stop path, which still calls `saveAllChunks(false, true, false)`) therefore writes every cube that has been accessible since the last save. `flushWorker` joins the cube IO thread and forces the region files.

Cube unload no longer uses the empty `cc_scheduleUnload` replacement. Vanilla `scheduleUnload` is left in place for columns. For a cube long it saves the `LevelCube`, marks it unloaded, and does not call chunk-only POI, light, or `Level.unload`.

## Test

`IntegrationTestServerCubeCache.cubeBlocksSurviveSaveAndReload` loads cube `(0,0,0)` to `FULL`, checks the Phase 1 sinusoid (air at `(31,21,31)`, smooth stone at `(31,20,31)`), sets diamond at `(31,21,31)` (same section as the stone, so section emptiness does not change), calls `ServerChunkCache.save(true)`, closes the chunk map, and opens a second cache on the same directory. The reloaded cube must still be diamond at `(31,21,31)` and smooth stone at `(31,20,31)`.

`(31,21,31)` is air after `generateNoise`, so a reload that only regenerated the cube would fail the diamond assert.

## Test run

`./gradlew test` on JDK 21: see the PR / the latest run recorded below.

No new `@Disabled` tests were added. `CubicChunksCore:test` is still skipped by design.

The nine pre-existing skips are unchanged: `TestClientCubeCache` (3), packet handler tests (3), `TestMinecraftServer` vanilla prepare/spawn (2), `TestCubicServerLevel.testVanillaSetChunkForced`.

## What still blocks a playable session

Saving block states is not a world you can play.

- **Lighting is not saved or updated.** The cube unsaved listener is still a no-op (`MixinCubeStatusTasks`). Wiring it to `chunksToEagerlySave` would call the untransformed `saveChunksEagerly`, which does `getLatestChunk()` and then chunk-only saves. Exit and `saveAllChunks` are the path that actually writes cubes. Light tasks are still `passThrough`.
- **Heightmaps, biomes, block entities, ticks, structures, and POI are not in the cube tag.** Biome palettes on load are a fresh plains-filled section container. `getHeightmaps()` still throws. Surface tracker wiring is unchanged.
- **Forced-cube tickets do not survive a restart.** `MixinTicketStorage` still does not codec-save cubic tickets. Spawn tickets are created again on start, and those cubes load from `region3d` if they were saved. A cube that was only kept loaded by a forced ticket will not be requested again after restart.
- **`MinecraftServer.stopServer` still skips the `chunkMap.hasWork()` wait** (`MixinMinecraftServer` returns false from that `anyMatch`). The save call after that loop still runs. In-flight cube writes are finished by `flushWorker` inside `saveAllChunks(true)`, not by the skipped wait.
- **Terrain is still the Phase 1 sinusoid.** There is no `NoiseBasedChunkGenerator`. Column `generateNoise` is still `passThrough`, so the stone lives on the cube.
- **Nothing here opens a client or meshes a cube.** `TestClientCubeCache` is still skipped.

## How to run

```bash
git submodule update --init --recursive
# JDK 21
./gradlew test
```
