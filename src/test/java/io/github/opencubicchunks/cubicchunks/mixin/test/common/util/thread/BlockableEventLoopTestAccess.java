package io.github.opencubicchunks.cubicchunks.mixin.test.common.util.thread;

import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlockableEventLoop.class)
public interface BlockableEventLoopTestAccess {
    @Invoker("pollTask") boolean invokePollTask();
}
