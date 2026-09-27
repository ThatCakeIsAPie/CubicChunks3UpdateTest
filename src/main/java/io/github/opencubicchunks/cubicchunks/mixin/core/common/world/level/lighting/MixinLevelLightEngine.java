package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.lighting;

import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLayerLightListener;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelLightEngine.class)
public class MixinLevelLightEngine {
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;
    @Unique
    private LayerLightEventListener cc_blockListener;
    @Unique
    private LayerLightEventListener cc_skyListener;

    @Inject(method = "lightOnInColumn", at = @At("HEAD"), cancellable = true)
    private void cc_onLightOnInColumn(long columnPos, CallbackInfoReturnable<Boolean> cir) {
        if (this.cc_isCubicWorld()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getRawBrightness", at = @At("HEAD"), cancellable = true)
    private void cc_onGetRawBrightness(BlockPos blockPos, int amount, CallbackInfoReturnable<Integer> cir) {
        if (!this.cc_isCubicWorld() || !(this.levelHeightAccessor instanceof Level level)) {
            return;
        }
        int sky = CubicLightQuery.skyLight(level, blockPos) - amount;
        int block = CubicLightQuery.blockLight(level, blockPos);
        cir.setReturnValue(Math.max(block, sky));
    }

    @Inject(method = "getLayerListener", at = @At("HEAD"), cancellable = true)
    private void cc_onGetLayerListener(LightLayer lightLayer, CallbackInfoReturnable<LayerLightEventListener> cir) {
        if (!this.cc_isCubicWorld() || !(this.levelHeightAccessor instanceof Level level)) {
            return;
        }
        if (lightLayer == LightLayer.BLOCK) {
            if (this.cc_blockListener == null) {
                this.cc_blockListener = new CubicLayerLightListener(level, false);
            }
            cir.setReturnValue(this.cc_blockListener);
            return;
        }
        if (this.cc_skyListener == null) {
            this.cc_skyListener = new CubicLayerLightListener(level, true);
        }
        cir.setReturnValue(this.cc_skyListener);
    }

    @Unique
    private boolean cc_isCubicWorld() {
        return this.levelHeightAccessor instanceof CanBeCubic cubic && cubic.cc_isCubic();
    }
}
