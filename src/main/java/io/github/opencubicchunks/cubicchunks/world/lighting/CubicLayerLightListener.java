package io.github.opencubicchunks.cubicchunks.world.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;

/** Block or sky values stored on cubes, exposed as the listener vanilla rendering and gameplay already call. */
public final class CubicLayerLightListener implements LayerLightEventListener {
    private final Level level;
    private final boolean sky;

    public CubicLayerLightListener(Level level, boolean sky) {
        this.level = level;
        this.sky = sky;
    }

    @Override public int getLightValue(BlockPos pos) {
        return this.sky ? CubicLightQuery.skyLight(this.level, pos) : CubicLightQuery.blockLight(this.level, pos);
    }

    @Override public DataLayer getDataLayerData(SectionPos sectionPos) {
        return CubicLightQuery.dataLayer(this.level, sectionPos, this.sky);
    }

    @Override public void checkBlock(BlockPos pos) {
        // Cube updates are applied in LevelCube.setBlockState. Vanilla's queue would relight columns.
    }

    @Override public boolean hasLightWork() {
        return false;
    }

    @Override public int runLightUpdates() {
        return 0;
    }

    @Override public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        // Section emptiness is a vanilla column-light concern. Cube light is stored on the cube.
    }

    @Override public void setLightEnabled(ChunkPos pos, boolean enabled) {
        // Cubic light is not toggled per column.
    }

    @Override public void propagateLightSources(ChunkPos pos) {
        // Emission is seeded when the cube is lit or a block changes.
    }
}
