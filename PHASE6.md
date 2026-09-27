# Cubic Chunks 3 — Phase 6a (first NeoForge ladder rung)

Working copy: [ThatCakeIsAPie/CubicChunks3UpdateTest](https://github.com/ThatCakeIsAPie/CubicChunks3UpdateTest), branched from `dev` @ `16769e0a` (Phase 5).

Phase 6a scope: the first productive pin off Minecraft **1.21.6** / NeoForge **21.6.4-beta**. This is **not** a 26.2 or 26.3 port, and it does not rewrite lighting, worldgen, multiplayer, or the Vulkan renderer.

## Chosen rung

**Minecraft 26.1.2 + NeoForge 26.1.2.111 (stable, no `-beta`).**

Maven (`https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`, `lastUpdated` 2026-09-26) at the time of this pin:

| Line | Newest artifact | Status |
| --- | --- | --- |
| 26.1 | **26.1.2.111** | stable |
| 26.2 | 26.2.0.88 | stable |
| 26.3 | 26.3.0.23-beta | beta |

`neoforge-26.1.2.111.pom` depends on NeoForm **`26.1.2-1`** and FancyModLoader **11.0.15**. The four-component NeoForge version is `26.1.2` (Minecraft 26.1 hotfix 2) plus NeoForge build `111`.

### Why not a 1.21.x stepping stone

The [26.1 primer](https://docs.neoforged.net/primer/docs/26.1/) is written as **1.21.11 → 26.1**, and the [26.1 release notes](https://neoforged.net/news/26.1release/) tell porters coming from 1.21.1 to also read the 21.9 and 21.11 notes. This repo is already on **21.6 / 1.21.6**, so 21.7–21.11 exist as smaller diffs. They are not required by the toolchain:

- ModDevGradle **2.0.147** (the version shipped by [MDK-26.1-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-26.1-ModDevGradle)) loads NeoForge 26.1 directly.
- Gradle **9.2.1** is the wrapper that MDK pins (Gradle 9.1.0+ is the documented minimum).
- Java **25** is selected by the foojay toolchain resolver **1.0.0**. The Gradle JVM can stay on 21; compilation targets 25.

Jumping those 1.21.x notes into one 26.1 compile is the largest single bump the primers start from. 26.2 is a separate primer whose first sections are **Vulkan, blend factors, GPU formats, and vertex-format rewrites**. That is the human-owned render rung, so it is not this PR.

### Why not 26.2 or 26.3

26.2 is stable (`26.2.0.88`) but the [26.2 primer](https://docs.neoforged.net/primer/docs/26.2/) opens with the Vulkan backend swap. 26.3 is still beta (`26.3.0.23-beta`). Both are later rungs.

## Toolchain this PR pins

| Item | Was (`dev`) | Phase 6a |
| --- | --- | --- |
| Minecraft | 1.21.6 `[1.21.6,1.21.7)` | **26.1.2** `[26.1.2,26.2)` |
| NeoForge | 21.6.4-beta `[21.6,)` | **26.1.2.111** `[26.1,26.2)` |
| FML / loader range | `[4,)` | **`[11,)`** (FML 11.0.15) |
| Parchment | 1.21.5 / 2025.06.15 | **removed** — 26.1 ships Mojang names |
| ModDevGradle | 2.0.90 | **2.0.147** |
| Gradle wrapper | 8.14.1 | **9.2.1** (26.1 MDK pin) |
| Foojay resolver | 0.9.0 | **1.0.0** |
| Java toolchain | 21 | **25** |
| Mixin compat level | `JAVA_21` | **`JAVA_25`** |
| Game test property | `forge.enabledGameTestNamespaces` | **`neoforge.enabledGameTestNamespaces`** (26.1 MDK) |
| GitHub Actions JDK | 21 | **25** |
| DASM | dasm + dasm-neoforge **3.2.0** | unchanged (newest on NeoForge Maven, published 2025-12-14) |
| CubicChunksCore | submodule `75a1100`, Java 17 | unchanged |

`Jenkinsfile` still requests the Jenkins tool named `jdk17`. Phase 0 only moved GitHub Actions; this PR does the same for JDK 25. A Jenkins controller without a `jdk25` tool label would fail if that string changed.

## What 26.1 breaks (primer, not yet a source inventory)

From the 26.1 primer and release notes, the breaks that touch this repo’s surface:

- **`ChunkPos` is a record.** `new ChunkPos(BlockPos)` → `ChunkPos.containing`, `new ChunkPos(long)` → `ChunkPos.unpack`, `asLong` / `toLong` → `pack`. Cube code stores packed longs and constructs `ChunkPos` all over the chunk map, tickets, and networking.
- **GUI extract.** `GuiGraphics` → `GuiGraphicsExtractor`. `render*` / `draw*` on screens become `extract*`. Cubic Chunks’ loading-screen and heightmap debug renderer sit on the old render path.
- **Render pipeline (human-owned).** Block/fluid models, `QuadInstance`, `ChunkSectionLayer` (tripwire layer removed), Blaze3D backends. Do not rewrite this to chase 26.2 Vulkan.
- **Item stacks.** `ItemStack` / `FluidStack` construction needs registries; data files use `ItemStackTemplate` / `FluidStackTemplate`.
- **Saved data split** out of primary level data. Cube persistence (Phase 2) rides level save hooks that may have moved.
- **Loot / provider type unrolling** (`*Type` records removed, codecs registered directly). Only matters where CC references those vanilla types.
- **`Level#random` is protected.**

Lighting, cube generation, and multiplayer packet semantics from Phases 2–5 stay as they are wherever the 26.1 methods still exist. No worldgen expansion.

## Compile inventory

Filled in after `./gradlew compileJava` / `./gradlew test` on this pin. See the bottom of this file once that run is recorded.

## Next rung

- **6b** — finish whatever 26.1 compile/test failures this PR cannot close (chunk-map / ticket / packet / mixin / DASM targets). Still no Vulkan.
- **6c** — NeoForge **26.2.x** (newest stable at research time: `26.2.0.88`) using the 26.2 primer. Render and Vulkan changes stay human-owned; an agent should only bump the pin and apply mechanical renames the primer lists outside the renderer.
- **After 6c** — NeoForge **26.3.x** once it leaves beta (newest at research time: `26.3.0.23-beta`). Not a target of 6a.
