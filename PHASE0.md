# Cubic Chunks 3 — Phase 0 recon

Working copy: [ThatCakeIsAPie/CubicChunks3UpdateTest](https://github.com/ThatCakeIsAPie/CubicChunks3UpdateTest) (`dev` @ `e23e0e42`, “Fix dasm in tests”).
This is a fork of [OpenCubicChunks/CubicChunks3](https://github.com/OpenCubicChunks/CubicChunks3) for Lyle Cole’s 1.21.6 update effort.

Phase 0 scope: submodule + toolchain recon, green compile/test/jar, smoke as far as CI allows, known-broken list, smallest Phase 1 slice. **Not** a 26.x port, lighting engine, real `NoiseBasedChunkGenerator`, or Paper.

## Toolchain (from repo, not guessed)

| Item | Value | Source |
| --- | --- | --- |
| Minecraft | **1.21.6** (`[1.21.6,1.21.7)`) | `gradle.properties` |
| NeoForge | **21.6.4-beta** (`[21.6,)`) | `gradle.properties` |
| Parchment | 1.21.5 / 2025.06.15 | `gradle.properties` |
| Gradle | **8.14.1** | `gradle/wrapper/gradle-wrapper.properties` |
| Java (mod) | **21** (`java.toolchain.languageVersion`) | `build.gradle` |
| Java (CubicChunksCore) | toolchain **17** | `CubicChunksCore/build.gradle` |
| Agent JDK used | OpenJDK **21.0.10** | `java -version` |
| DASM | `dasm` 3.2.0 API + `dasm-neoforge` 3.2.0 | `build.gradle` |
| Moddev plugin | `net.neoforged.moddev` 2.0.90 | `build.gradle` |

README still says “Not yet usable or functional” and targets NeoForge/MC 1.21.6. Default `generateNewWorldsAsCC = true`.

## Submodule

`CubicChunksCore` is required (`settings.gradle` `include ':CubicChunksCore'`, `compileJava.dependsOn("CubicChunksCore:assemble")`).

- `.gitmodules`: `https://github.com/OpenCubicChunks/CubicChunksCore` @ branch `dev`
- Recorded SHA: `75a1100c8a6d47f02bde8515d4a7de207f04be36` (“Use CubicChunksCore's checkstyle config…”)
- Checkout arrived empty; `git submodule update --init --recursive` populated it. Clone with `--recursive` or the init command above.

Core itself is version-independent (coords / `CubePos` / `CloPos` / early config). Its tests are disabled in the submodule (`enabled(false)`); they are not unzipped into the parent test classpath (commented “java modules” TODO).

## Green-build results (this agent, 2026-09-26)

| Command | Result | Notes |
| --- | --- | --- |
| `./gradlew compileJava` | **PASS** | 2m 43s first run (NeoForge/MC artifacts). Deprecation/unchecked notes only. |
| `./gradlew test` | **PASS** | 1m 59s. **110 tests, 0 failures, 0 errors, 9 skipped.** |
| `./gradlew jar` | **PASS** | `build/libs/cubicchunks-1.0.0.jar` (512K) + `CubicChunksCore/build/libs/CubicChunksCore.jar` (110K) |
| `./gradlew build -x test` | **PASS** | 38s. Includes `spotlessCheck`, `checkstyleMain`, `checkstyleTest`, `check`. |
| `./gradlew runServer` | **SMOKE PASS** | Dedicated `--nogui` reached `Done (1.758s)!`. Spawn prep 14% → 93% → elapsed 898 ms. Config wrote `generateNewWorldsAsCC = true`. |
| `./gradlew runClient` | **SMOKE PASS (title/resource reload)** | Software GL (`LIBGL_ALWAYS_SOFTWARE=1`). Reached render thread: `Setting user: Dev`, LWJGL 3.3.3, resource reload `vanilla, mod_resources, mod/cubicchunks, mod/neoforge`. OpenAL/ALSA failed (no sound card) — non-fatal, sounds off. Did **not** create a world or verify sinusoidal terrain on screen. GitHub Actions has no GPU; CI only runs `build -x test` then `check`. |

JUnit skipped (9): `TestClientCubeCache` (3), packet handler tests (3), `TestMinecraftServer` vanilla prepare/spawn (2), `TestCubicServerLevel.testVanillaSetChunkForced`. No `CubeStatusTasks.generateNoise` coverage.

`CubicChunksCore:test` is SKIPPED by design.

### runServer smoke details

- Launch target `neoforgeserverdev`, MC 1.21.6, NeoForge 21.6.4-beta, Java 21.
- Mod list: `cubicchunks 1.0.0`, `minecraft`, `neoforge`.
- World created under `run/world` (gitignored). Did **not** prove sinusoidal stone visually — only that the dedicated server finished “Preparing start region” and idled.
- Non-fatal: many Mixin “Overwrite prohibited for @Final … Skipping method” / DASM visibility warnings. These are existing DASM+Mixin noise, not a boot crash.
- Server-dist mixin miss (non-fatal): access mixin `SectionOcclusionGraph$GraphEventsAccess` cannot load on dedicated server (`RuntimeDistCleaner`). Client-only target in a common access config.
- Mixin warns `Compatibility level JAVA_21` vs mixin max `JAVA_13` (cosmetic; it still sets JAVA_21).
- Saving is stubbed (`cc_save` returns false; `stopServer` ignores chunk-map work). Worlds will not persist cubes.

### runClient smoke

DISPLAY `:1` + Mesa GLX/`libGL` exist on this agent. Started with `LIBGL_ALWAYS_SOFTWARE=1` / `GALLIUM_DRIVER=llvmpipe`.

- Render thread started: `Setting user: Dev`, `Backend library: LWJGL version 3.3.3+5`.
- Resource reload included `mod/cubicchunks`. CC client mixins applied (`MixinLevelRenderer`, `MixinSectionOcclusionGraph`, `MixinLevelLoadingScreen`, `MixinClientLevel`).
- Sound engine failed (`Failed to open OpenAL device` / no ALSA card) and disabled itself — expected on a headless VM, not a CC bug.
- Stopped after atlas load; no singleplayer world was created, so sinusoidal terrain was not visually confirmed.
- GitHub Actions has no GPU/display; CI only runs `build -x test` then `check`.

## Bitrot fixes in this PR

Local compile/test/jar were already green; no Java source changes were required.

CI was still on **JDK 17** + `actions/setup-java@v1` (`.github/workflows/gradleBuild.yml`, `gradleBuildPR.yml`, `testJob.yml`) while the mod toolchain is Java 21. Foojay resolver *might* download 21, but a 17-only runner is the wrong pin for NeoForge 1.21.6. Workflows now use Temurin **21** via `setup-java@v4`.

## Known-broken / not-yet (do not “fix” in Phase 0)

### Worldgen (Phase 1+ / Phase 3)

- `CubeStatusTasks.generateNoise` is the **only** real cube fill: hardcoded **sinusoidal SMOOTH_STONE** around `CubicChunks.SUPERFLAT_HEIGHT` (5), amplitude 20. Comment: “Temporary basic sinusoidal terrain, so we can generate a simple test world.”
- All other cube status tasks (structures, biomes, surface, carvers, features, light init, light, spawn) **passThrough**.
- Column chunks in a cubic world also passThrough (`CCChunkStatusTasks`) except `full` (ProtoChunk → LevelChunk).
- `CubeAccess.getOrCreateNoiseChunk` / `carverBiome` / `getNoiseBiome` / `fillBiomesFromNoise` throw `UnsupportedOperationException` (TODO P3).
- `src_old` still has `CubicNoiseBasedChunkGenerator` and levelgen mixins; they are **not** on the current compile path.
- `ModOverworldChunkGeneratorsandCC.md` is **1.17-era** guidance, not current 1.21.6 API.

### Lighting (Phase 2 — out of scope)

- `MixinLevelLightEngine`: cubic worlds force `lightOnInColumn=true` and `getRawBrightness=MAX`.
- Cube packets named `…WithLight` carry **no light data**.
- Heightmaps on cubes are stubs (`getHeight` returns `SUPERFLAT_HEIGHT`; several heightmap APIs throw).
- `initializeLightSources` / `getSkyLightSources` unimplemented.

### Save / load (Phase 2)

- `ChunkMap.cc_save` always `false`.
- `MixinChunkStorage` / `IOWorker` / `PoiManager` cubic paths incomplete.
- `ServerConfig` per-dimension world-style load/generate is **commented out**. Cubic-ness is a **global** `generateNewWorldsAsCC` flag applied in `Level.<init>`, not persisted per world/dimension. `MixinServerLevel` still has `TODO conditionally mark as cubic based on dimension, config, level data`.

### Runtime / gameplay

- Cube ticking empty; chunk ticking in cubic worlds skipped (Forge events?).
- Teleport / dimension change / entity CLO position incomplete (P2/P3).
- Forced cubes, POI, block entities on the wire, heightmap/light on send — incomplete.
- `src/main/java/.../movetoforgesourcesetlater` still lives on the main sourceset (DASM/Forge event shims).
- `jcenter()` remains in Core `build.gradle` (dead repo; unused for this green build).

### Tests / process

- README contributing rules require tests for every mixin/class; many mixins are only covered indirectly.
- `longRunTest` task disabled (MDG single test-task limit).
- Parent cannot yet consume Core’s `testsJar`.
- CI previously pinned JDK 17 (fixed here). PR workflow still tars the whole tree as `post-build-state` (heavy; not changed).

## Smallest Phase 1 slice (recommendation)

**Do not** start `NoiseBasedChunkGenerator`, biomes, lighting, or a 26.x bump.

**Do** lock the existing sinusoidal placeholder as the P1 contract:

1. **Unit test `CubeStatusTasks.generateNoise`** on a `ProtoCube`:
   - Fill uses `Blocks.SMOOTH_STONE`, `SUPERFLAT_HEIGHT = 5`, amplitude 20, the current `sin((x+cubeX)/8 + (z+cubeZ)/21) + cos((z+cubeZ)/13)` formula.
   - Assert stone vs air on a few in-cube and out-of-range Y values (including a cube entirely above the surface, which should stay empty).
2. **One integration assertion** (optional but small): after `ServerCubeCache` loads cube `(0,0,0)` to `FULL` with `generateNewWorldsAsCC=true`, `getBlockState` at the expected surface is stone. Reuse `IntegrationTestServerCubeCache` / `BaseTest` — do not stand up a real client.
3. **Do not** implement surface/carvers/features/light. Leave those as `passThrough`.
4. **Do not** revive `src_old` generators.

That slice is local to one method + tests, proves the only terrain that exists today, and is the right smoke before any later noise-generator work.

## How to rebuild

```bash
git submodule update --init --recursive
# JDK 21
./gradlew compileJava
./gradlew test
./gradlew build -x test   # CI-equivalent jar + spotless + checkstyle
# optional dedicated-server smoke (writes run/, gitignored):
echo eula=true > run/eula.txt
./gradlew runServer
```
