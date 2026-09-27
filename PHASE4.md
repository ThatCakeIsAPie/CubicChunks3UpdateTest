# Cubic Chunks 3 — Phase 4

Working copy: [ThatCakeIsAPie/CubicChunks3UpdateTest](https://github.com/ThatCakeIsAPie/CubicChunks3UpdateTest), branched from `dev` @ `d8a3d882` (Phase 3).

Phase 4 scope: a walkable overworld-like cube fill so the world is more than a sinusoid of smooth stone. **Not** vanilla `NoiseBasedChunkGenerator` parity, jigsaw structures, carvers, features, aquifers, a lighting rewrite, a 26.x port, or Paper.

## What the generator does

`CubicOverworldGenerator` fills one cube from the level seed (`CubicOverworldGenerator.DEFAULT_SEED` when the context is null, including unit tests). It does not call `NoiseBasedChunkGenerator.fillFromNoise` or `buildSurface`.

Vanilla noise builds a column from the level's minimum Y to its maximum Y, and the surface step places bedrock on that floor. A cubic world has no floor. Sampling the vanilla density router also needs a `NoiseChunk` and neighboring columns for blending. `CubeAccess.getOrCreateNoiseChunk` still throws, and a cube task that waits on a neighbor can deadlock the status graph. `src_old`'s `CubicNoiseBasedChunkGenerator` was only `setCubic()`. This PR keeps the generator self-contained: no neighbor-cache reads, and every status task returns `CompletableFuture.completedFuture`.

Terrain:

- Column height is 2D value noise: base 68, plus continental (period 220, amplitude 36), hills (period 64, amplitude 16), and detail (period 24, amplitude 4).
- Density at a block is `(surfaceY - y) + warp * 8`, where warp is 3D value noise (period 32). Solid when density is positive, so the surface wobbles a few blocks around the heightmap.
- Caves open only more than 4 blocks under that heightmap, where a second 3D sample (period 22) is above 0.42 and a wider cheese sample (period 48) is above 0.1. The crust stays walkable.
- Air below sea level 63, and within 8 blocks under a heightmap that is itself below sea level, is water. Deep cave air stays dry. There are no aquifers.
- A cube whose minimum Y is at or above both sea level and `MAX_TERRAIN_Y` (132) is left air. A column whose cube starts above `surface + 8` and at or above sea level is skipped.

Materials (stone is not the only block):

- Top solid block on land: grass, or sand in a desert, or snow block in a snowy column. Underwater tops are sand.
- The next three solid blocks are dirt, or sand in a desert or under the sea.
- Deeper rock is stone at `y >= 0` and deepslate below. That Y is a material change, not a world floor. Cubes below the vanilla bedrock layer (`y = -64`) and further down stay deepslate and ore.
- Sparse ores from a position hash: diamond below y 16, gold below 32, iron below 64, copper and coal at any depth, with deepslate variants below y 0.
- `forbidBedrock` replaces a bedrock state with deepslate. The generator never selects bedrock, including at the bottom of a cube and at `y = -64`.

Biomes are 2D (same choice at every Y): temperature and humidity value noise, period 180. Hot and dry is desert, cold is snowy plains, otherwise plains. `generateBiomes` paints that into each section with `LevelChunkSection.fillBiomesFromNoise` when `level.registryAccess().lookupOrThrow(Registries.BIOME)` can resolve plains, desert, and snowy plains. A null context or a mocked registry that cannot store holders leaves the section default. Block choice does not depend on the palette.

Where it is hooked (`CubeStatusTasks`):

- `generateBiomes` paints the 2D biome.
- `generateNoise` calls `fillCube`. The neighbor cache is unused.
- `generateSurface` stays `passThrough`. Surface materials are written with the noise fill so this step cannot add a bedrock floor.
- `generateStructureStarts`, structure references, carvers, features, `initializeLight`, and spawn stay `passThrough`.
- `light` is unchanged (Phase 3).
- Column tasks (`CCChunkStatusTasks`) stay `passThrough` except `full`. Terrain lives on cubes.

## What remains stubbed

- **Structures.** No structure starts or references. Vanilla jigsaw placement is 2D and asks neighboring chunks for pieces. Wiring that into the cube graph is the deadlock this phase avoids. No Y-aware structure starts.
- **Carvers and features.** Vanilla carvers and placed features (trees, ores-as-features, lakes) are not run. Caves and ores here are the noise shortcuts above, not the datapack feature pipeline.
- **Aquifers, vanilla density functions, and `NoiseBasedChunkGenerator`.** No climate sampler, no `RandomState`, no noise router. `CubeAccess.getOrCreateNoiseChunk`, `getNoiseBiome`, and `fillBiomesFromNoise` on the cube still throw. Tests read biomes from the section palette.
- **Heightmaps / `SurfaceTracker`.** Still throw. The cube packet still sends an empty heightmap map.
- **Biome persistence.** `CubeSerializer` still writes blocks and light, not biomes. A reload keeps the blocks; the palette is whatever the section default is after load.
- **Column chunks** stay empty.

## Deadlock and performance

The fill does not read another cube and does not return an incomplete future, so `generateNoise` cannot wait on a neighbor that is waiting on it. That is the deadlock bar for this phase.

Cost is local. A `FULL` load still generates the vanilla accumulated-radius neighborhood (on the order of a thousand cubes). Each cube is one pass over its columns: height and biome once per column, a solid mask of `diameter + 4` samples, then non-air `setBlockState`. Sky cubes above the terrain cap return immediately. There is no neighbor blending and no vanilla `NoiseChunk`. The integration `FULL` tests are the canary: they run the real status pipeline, including Phase 3 lighting, and must finish.

Risks that remain:

- The neighborhood is still large. A slower fill would show up as a long `FULL` test, not as a status-graph wait.
- Cave and ore thresholds are tuned for "some of each in cube `(0,0,0)`", not for vanilla distribution. A seed other than 0 changes the surface.
- Sky light is still the Phase 3 heuristic. A cave under an unloaded ceiling is fullbright until that ceiling loads.
- Water and partial occluders stop direct sun. A grass surface with air above is sky 15 on the air and sky 0 inside the opaque crust.

## Tests

`TestCubicOverworldGenerator` (seed 0): a plains column is grass on dirt with an opaque block four down; desert is sand; snowy is snow on dirt; ocean air below sea level is water; cube `(0,0,0)` contains stone, at least one ore, and cave air, and contains neither bedrock nor smooth stone; cube `(0,-2,0)` includes `y = -64` with deepslate and no bedrock, and cube `(0,-8,0)` is still deepslate with no bedrock; cube `(0,16,0)` is air; a shifted cube matches `blockState` in world coordinates; a different seed moves the heightmap; `fillBiomes` paints the column biome into the section palette; a null registry leaves the cube air.

`TestCubeStatusTasks.generateNoise_matchesOverworldGenerator` compares every block of cube `(0,0,0)` to `blockState`. A null context uses the default seed. `generateBiomes` with a null context returns the cube.

`IntegrationTestServerCubeCache` stubs `ServerLevel.getSeed()` to the default seed. `fullCubeOverworldSurfaceAndLight` loads a grass column to `FULL`, checks grass, dirt, air above, no bedrock, and Phase 3 sky (0 in the buried opaque block, 15 in the air). `torchInSolidCubeCastsLightAndSurvivesReload` carves a torch into generated stone and checks occlusion plus save/reload. `cubeBlocksSurviveSaveAndReload` edits one block to diamond and checks that the neighbor generated block is still the generator state after a new cache on the same directory, so the reload is the saved cube and not a fresh fill.

Gradle result is recorded after `./gradlew test` on this revision.

## How to run

```bash
git submodule update --init --recursive
# JDK 21
./gradlew test
```
