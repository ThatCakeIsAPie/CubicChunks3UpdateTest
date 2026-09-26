# Cubic Chunks 3 — Phase 1

Working copy: [ThatCakeIsAPie/CubicChunks3UpdateTest](https://github.com/ThatCakeIsAPie/CubicChunks3UpdateTest), branched from `dev` @ `8c79d587` (Phase 0).

Phase 1 scope: lock the existing sinusoidal placeholder in `CubeStatusTasks.generateNoise`. **Not** a `NoiseBasedChunkGenerator`, lighting, heightmap wiring, save/load, a 26.x port, or Paper.

## What was locked

`CubeStatusTasks.generateNoise` is still the only cube fill. It writes `Blocks.SMOOTH_STONE` where

```
y + round(20 * (sin(x / 8 + z / 21) + cos(z / 13)) / 2) <= CubicChunks.SUPERFLAT_HEIGHT
```

`SUPERFLAT_HEIGHT` is 5 and the amplitude is 20. `x` and `z` are world block coordinates (the loop adds cube-local x/z to `minCubeX` / `minCubeZ`). `y` is world Y, and the loop only visits `y` in `[minCubeY, min(maxCubeY, 25)]`. Every other block in the cube stays air.

Default cube diameter is 32 (`CUBE_DIAMETER_IN_SECTIONS = 2`). The rounded offset is in [-20, 20], so the surface lies between y = -15 and y = 25.

| Test | Cube | Lock |
| --- | --- | --- |
| `TestCubeStatusTasks.generateNoise_originCube_matchesSinusoid` | `(0,0,0)` y `[0,31]` | Every block matches the formula. Column `(31,31)` is stone through y = 20 and air at y = 21. y = 26 stays air (above the y = 25 cap). Column `(0,0)` surfaces at y = -5, so it is air in this cube. |
| `TestCubeStatusTasks.generateNoise_shiftedCube_usesWorldXZ` | `(2,0,-1)` x `[64,95]`, z `[-32,-1]` | Phase uses world X/Z. `(64,11,-32)` and `(95,18,-25)` are stone; one block above each is air. |
| `TestCubeStatusTasks.generateNoise_cubeAboveSurface_staysEmpty` | `(0,1,0)` y `[32,63]` | Whole cube stays air (min Y is above 25). |
| `TestCubeStatusTasks.generateNoise_cubeEntirelyBelowSurface_isSolid` | `(0,-2,0)` y `[-64,-33]` | Whole cube is smooth stone (max Y is below -15). |
| `IntegrationTestServerCubeCache.fullCubeSinusoidalSurface` | `ServerCubeCache` cube `(0,0,0)` at `ChunkStatus.FULL` | The promoted `LevelCube` still has stone at `(31,20,31)` and `(31,0,31)`, and air at `(31,21,31)` and `(0,0,0)`. |

The integration harness is the existing one: a cubic `ServerChunkCache` with `cc_isCubic` set. That is the flag `generateNewWorldsAsCC` turns on in `Level`. No client is started.

`generateNoise` does not read `WorldGenContext`, the step, or the neighbor cache. The unit test passes null for those arguments.

## Test run

`./gradlew test` on JDK 21: **PASS**. **115 tests, 0 failures, 0 errors, 9 skipped** (110 before this phase, plus these 5). `checkstyleMain` and `checkstyleTest` passed. `CubicChunksCore:test` is still skipped by design.

The nine pre-existing skips are unchanged: `TestClientCubeCache` (3), packet handler tests (3), `TestMinecraftServer` vanilla prepare/spawn (2), `TestCubicServerLevel.testVanillaSetChunkForced`.

## What still fails for “see terrain in-game”

These tests prove block storage after `generateNoise` and after a cube is promoted to `FULL`. They do not open a world or draw a frame.

Still in the way of seeing that stone on screen (all pre-existing; not changed here):

- **No world was opened.** Phase 0’s client smoke stopped at the title screen and resource reload. Nothing in this phase creates a singleplayer world.
- **Stone only.** Biomes, surface, carvers, features, light, and spawn cube tasks are still `passThrough`. `src_old`’s `CubicNoiseBasedChunkGenerator` is not on the compile path.
- **Column chunks stay empty.** `CCChunkStatusTasks.generateNoise` is `passThrough`. The stone is on the cube. Client section rendering has cube redirects, but `TestClientCubeCache` is still skipped, and cube packets / meshing are not exercised here.
- **Light and heightmaps do not follow the sinusoid.** `…WithLight` cube packets carry no light data. Cube heightmaps return `SUPERFLAT_HEIGHT` or throw, so spawn and culling do not track the surface.
- **Cubes are not saved.** `ChunkMap.cc_save` returns false.

## How to run

```bash
git submodule update --init --recursive
# JDK 21
./gradlew test
```
