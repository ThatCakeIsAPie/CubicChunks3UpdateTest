# Cubic Chunks 3 — Phase 3

Working copy: [ThatCakeIsAPie/CubicChunks3UpdateTest](https://github.com/ThatCakeIsAPie/CubicChunks3UpdateTest), branched from `dev` @ `7f078aba` (Phase 2).

Phase 3 scope: a cubic lighting MVP so caves are not a fullbright cheat. **Not** vanilla-parity sky for infinite Y, a real `NoiseBasedChunkGenerator`, heightmaps / `SurfaceTracker`, client mesh polish beyond waiting for light, a 26.x port, or Paper.

## What works

Block light is a 15-level flood stored on the cube. The flood queue is packed relative to the cube, because vanilla `BlockPos.asLong` keeps only 12 bits of Y and wraps any cube outside that range. A torch in stone casts light; a block with `getLightBlock() == 15` stops it, including across a cube face when the neighbor is loaded. Spread subtracts `max(1, dest.getLightBlock())`. Emitters are seeded from `BlockState.getLightEmission()`. Empty sections are skipped via `LevelChunkSection.maybeHas`.

Sky light is a documented heuristic (below), stored in the same nibble arrays. `getRawBrightness` is `max(block, sky - amount)`. `getLayerListener` returns those nibbles as vanilla `DataLayer`s (index `(y << 8) | (z << 4) | x`, low nibble first). `lightOnInColumn` stays true so vanilla does not treat the column as unlit.

Where it is hooked:

- `CubeStatusTasks.light` calls `CubicLightEngine.lightCube`. `initializeLight` is still `passThrough`.
- `LevelCube.setBlockState` relights the edited cube when light properties change, plus already-lit cubes within 15 blocks, and the downward column within the sky search when opacity changes. It does not enqueue vanilla column light tasks. `ProtoCube.setBlockState` (noise) does not relight.
- `CCClientboundLevelCubeWithLightPacket` carries packed nibbles (`CubeLightPacketData`). The handler applies them and sets `isLightCorrect` before the render callback. Client section existence requires the cube and `isLightCorrect()`, so an unlit cube is not meshed.
- `CubeSerializer` writes `cc_light`. A full cube loaded without that tag is relit from itself (unloaded space is open sky). A proto only restores the tag; its light status still runs.
- Promotion `LevelCube(ServerLevel, ProtoCube)` copies the nibbles, unwrapping `ImposterProtoCube`.

Neighbor refresh at generation is one shot and non-recursive: the four orthogonal faces, and the column below for `max(1, 128 / cubeDiameter)` cubes. Only neighbors that are already `isLightCorrect` are refreshed, so a cube that has not reached the light status yet keeps its own pass.

## Sky compromise

Vanilla sky light walks an infinite column and a surface tracker. This MVP does not.

Direct sun is level 15 in blocks whose `getLightBlock()` is 0 when the path upward is clear. Cells that are already at that level are not flooded again, so an open cube does not walk every neighbor of every block. An all-air cube with a clear column above and no emitter in the neighboring cubes skips the flood and stores sky 15 directly. A cube whose every block is a full occluder stores zeros without a flood. The search is `CubicLightEngine.SKY_SEARCH_BLOCKS` (128). Unloaded space **on the cube's own columns** counts as open sky, so a ceiling that is not loaded, or is more than 128 blocks away, does not cast a shadow. Partial occluders (`getLightBlock()` 1–14, leaves and water) stop direct sun; the flood fills the rest. Only full opacity (≥ 15) blocks block light completely.

Margin columns (the 15-block halo used so a torch on a cube face reaches the neighbor) do **not** invent sun from unloaded space. They copy a neighbor's stored sky only when that neighbor is already `isLightCorrect()`.

Consequences:

- A solid cube with nothing loaded above is full sky in its air, which is what the sinusoid surface wants.
- Loading a stone cube above an already-lit air cube refreshes that air cube once and turns the sky off.
- Diagonal neighbors are not refreshed at generation time. A later block edit or a later orthogonal light pass can correct them.
- A block edit darkens sky only in the edited XZ column (down 128 blocks), not a 3×3 of columns.
- There is no incremental vanilla light packet. `sectionLightChanged` still returns false for cubes. Light travels in the cube packet, and a `LevelCube.setBlockState` on the side that receives the change updates that side's nibbles locally.

## Tests

`TestCubicLightEngine` (no world): torch in a solid cube, torch and stone across a cube border, open column is sky 15, a roof with a hole leaves sky under the hole and 0 in the far corner, a loaded stone cube above refreshes the cube below, packed nibbles round-trip on the packet codec.

`IntegrationTestServerCubeCache.fullCubeSinusoidalSurface` checks sky 0 in the stone floor and sky 15 in the air above it. `torchInSolidCubeCastsLightAndSurvivesReload` places a torch in the sinusoid stone, checks occlusion, saves, and reloads the block light. The cube-with-light packet serde asserts a non-zero nibble round-trip.

`./gradlew test` result is recorded after the full run in the PR body. No new `@Disabled` tests were added. The nine pre-existing skips stay: `TestClientCubeCache` (3), packet handler tests (3), `TestMinecraftServer` vanilla prepare/spawn (2), `TestCubicServerLevel.testVanillaSetChunkForced`.

## What still blocks a playable look

- **Sky is not vanilla.** Infinite-Y occlusion, skylight from a real surface tracker, and diagonal generation-time sky are out of scope. A dark cave under an unloaded ceiling is fullbright until that ceiling loads (and only the cubes this pass refreshes go dark).
- **Heightmaps still throw.** The cube packet still sends an empty heightmap map. No `SurfaceTracker`.
- **Terrain is still the Phase 1 sinusoid.** No `NoiseBasedChunkGenerator`. Column chunks stay empty.
- **Client mesh is still the Phase 0 path.** It now waits for `isLightCorrect`, and the packet finally contains light, but there is no visual pass in CI. `TestClientCubeCache` is still skipped.
- **NBT is still blocks + light.** Biomes, block entities, ticks, structures, and POI are absent. Forced-cube tickets still do not survive restart (Phase 2).

## How to run

```bash
git submodule update --init --recursive
# JDK 21
./gradlew test
```
