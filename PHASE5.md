# Cubic Chunks 3 — Phase 5

Working copy: [ThatCakeIsAPie/CubicChunks3UpdateTest](https://github.com/ThatCakeIsAPie/CubicChunks3UpdateTest), branched from `dev` @ `add40208` (Phase 4).

Phase 5 scope: cube streaming when two loaders edit or receive the same cube, including the dedicated-server packet path. **Not** vanilla multiplayer parity, anticheat, Distant Horizons, a lighting rewrite, worldgen past Phase 4, or a 26.x / NeoForge upgrade.

There is no NeoForge `integratedServer` two-player harness. The contract is covered by unit tests plus one server-cache integration test. A live two-client session is still required; see below.

## What works

### Send priority

`CloDistance.squaredToCubeCenter` uses the cube center on X, Y, and Z. `MixinChunkMap.cc_euclideanDistanceSquared` used to pass the cube X into the Y and Z centers, so a cube above the player sorted as if it shared the player's Y.

`PlayerChunkSender` collected pending CLOs with vanilla chessboard distance on a `ChunkPos`. Cube longs are not chunk positions. Both comparators in `cc_collectChunksToSend` now use `CloDistance.chebyshevCubes` from the player's cube. A column contributes only its XZ cube column (Y distance 0). The send loop still writes every column in the batch before any cube. The sort is by distance, so a nearby cube is not held behind every column.

### Dig packets

`ChunkHolder.blockChanged` for a cube records the section-local short, as before. `changedBlocksPerSection` is allocated to the column section count. A cube section index can exceed that (diameter 4+, or a short dimension). `cc_blockChanged` grows the array to at least `SECTION_COUNT` before the dasm body indexes it. Default diameter 2 (indices 0–7) already fits an overworld column of 24 sections; the grow is the guard.

`cc_broadcastCubeChanges` no longer emits `ClientboundSectionBlocksUpdatePacket`. That packet is addressed as a column section and does not carry cube light. Tracking players get `CCClientboundCubeBlockChangesPacket` (`cubicchunks:cube_block_changes`): cube position, the changed block states, and `CubeLightPacketData` for the edited cube. Capture reads sections and light under `CubeAccess.cc_blockMonitor`, so a concurrent editor cannot tear the palette away from the light snapshot. The handler applies the packet only when `ClientCubeCache.cc_getCube(..., false)` already has that cube. A delta that arrives first is dropped; the full cube packet is built at send time and already contains the edit.

`sectionLightChanged` on a cube holder still returns false, so there is still no incremental vanilla light packet. The delta packet is what carries the edited cube's nibbles. Neighbor cubes are not included. Sky light stays the Phase 3 heuristic.

Cancelling the dasm broadcast also skips the vanilla block-entity update. Cubes still do not persist block entities. The old `SectionPos` redirects on `cc_broadcastCubeChanges` remain and do not run.

### Client storage

Vanilla `ClientChunkCache.drop` ignores a position outside the view. The center packet is sent before the forgets, so a far teleport marks the cube out of range before the forget is handled, and the stale cube stays in the modulus slot. Storage radius is `calculateStorageRange(viewDistance)` (`max(2, viewDistance) + 3`), which is larger than server cube tracking (`max(3, ceil(viewDistance / DIAMETER))`), so a small step still lets the forget land. A jump past that margin does not.

`cc_drop` calls `Storage.dropAt`, which removes the cube at that slot when the stored coordinates match, including after the center has moved. An alias that hashes to the same slot (position plus `viewRange`) is left in place. `cc_updateViewCenter` writes the new center, drops every stored cube that fails `inRange`, then updates the column center. A lost forget cannot resurrect a cube the player has already left.

### One cube, two editors

`LevelCube.setBlockState` and `ProtoCube.setBlockState` take `cc_blockMonitor` for the section write and, on `LevelCube`, the light update of that cube. `CubeSerializer.write` takes the same lock when the argument is an `ImposterProtoCube` wrapped around a `CubeAccess`, and `CCClientboundLevelCubePacketData` snapshots section bytes under it.

The dedicated server runs cube loads on `ServerChunkCache.mainThreadProcessor`, so two players on one server do not enter `setBlockState` together. The lock is for the test harness (`Runnable::run`, which does not serialize) and for any future executor that does overlap a save or a packet snapshot with an edit. `getBlockState` is not locked; the tests join the editors before they read.

A block's `onPlace` that edits a different cube while holding this cube's lock can deadlock if the other thread does the reverse. Diamond, gold, and stone do not do that. The dedicated-server path is single-threaded.

## Tests

`TestCubeStreaming`:

- `cubeDistanceUsesEachAxis` — a point at the cube center is distance 0 on all three axes (the old X-for-Y-and-Z formula is not), and chebyshev uses Y for cubes and ignores Y for columns.
- `forgetAfterCenterMoveDropsTheSameSlot` — an out-of-range cube is removed by `dropOutsideRange`; a forget for a coordinate that only shares the slot does not remove the cube that is actually stored; after the center jumps, `dropAt` still removes the stored cubes.
- `twoClientsMatchAfterTwoEditsAndPacketRoundTrip` — two client copies of one cube receive the same delta (diamond and gold, plus block light 11) after a codec round-trip. An unedited cell stays air.
- `twoThreadsEditingOneCubeDoNotCorruptSections` — four threads write a diamond row, a gold row, and a shared cell. After join, each row is intact, the shared cell is one of the two states, and a full-packet copy matches.

`IntegrationTestServerCubeCache.twoEditorsOfOneCubeStayConsistentAcrossClientsAndReload` loads cube `(0,0,0)` to `FULL` twice and gets the same instance, copies it to two clients, digs two generated solid blocks, round-trips the delta, saves, and reloads on a new cache. The reloaded cube and a second full-packet client both have diamond and gold.

`./gradlew test` on JDK 21: **PASS**. **136 tests, 0 failures, 0 errors, 9 skipped** (131 before this phase). `checkstyleMain`, `checkstyleTest`, and `spotlessCheck` passed. `CubicChunksCore:test` is still skipped by design. No new `@Disabled` tests were added. The nine pre-existing skips stay: `TestClientCubeCache` (3), packet handler tests (3), `TestMinecraftServer` vanilla prepare/spawn (2), `TestCubicServerLevel.testVanillaSetChunkForced`.

`TestClientCubeCache` and the packet handler tests stay disabled because they need client mixins. `Storage` is tested by constructing it directly.

## Live two-client QA still required

Not exercised:

- A dedicated server process with two connected clients.
- Real delivery of `CCClientboundLevelCubeWithLightPacket`, `CCClientboundCubeBlockChangesPacket`, `CCClientboundForgetLevelCloPacket`, and `CCClientboundSetCubeCacheCenterPacket` on a connection, including view-center before forget.
- `CubeHolder.PlayerProvider` / `ChunkMap` actually listing both players for one cube. A null `cc_playerProvider` skips the delta send.
- Meshing after the delta. The packet writes the client `LevelCube`; it does not schedule a render section rebuild.
- Ordering against vanilla column packets that a mixed client might still receive.

What to watch in that session: both clients show the same dig; a player who walks out past the storage margin and back does not see the pre-forget cube; a player who joins after the dig receives the dug blocks on the full cube packet.

## Known races

- A delta that arrives before the cube is in `ClientCubeCache` is ignored. The server builds the full packet at send time, so an in-order full packet already has the edit. A full packet captured earlier, delivered after the delta, would overwrite the dig.
- Neighbor light is not in the delta. Only the edited cube's nibbles move. `sectionLightChanged` still returns false.
- Unload versus a new ticket is the vanilla `pendingUnloads` / `saveSyncFuture` pattern in `MixinChunkMap.cc_onScheduleUnload`. If the unload still owns the holder it saves and `setLoaded(false)`. A newer save-sync future reschedules and skips that save. A region write overlapping a new load can still race the way vanilla chunk IO does.
- Forced-cube tickets still do not survive restart (Phase 2).
- Heightmaps still throw, so the full cube packet still sends an empty heightmap map. Biomes are still not in `CubeSerializer`.
- The block lock does not cover `getBlockState`. A reader during an edit can observe a half-updated section. Production edits run on the server thread; the tests read after join.

## How to run

```bash
git submodule update --init --recursive
# JDK 21
./gradlew test
```
