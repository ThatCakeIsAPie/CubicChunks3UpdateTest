package io.github.opencubicchunks.cubicchunks.client.gui.render;

import io.github.opencubicchunks.cubicchunks.client.gui.render.pip.WorldLoadingCubeStatusesRenderer;
import io.github.opencubicchunks.cubicchunks.client.gui.render.state.pip.WorldLoadingCubeStatusesRenderState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

@EventBusSubscriber
public class CCNeoForgeRenderingHooks {
    private CCNeoForgeRenderingHooks() {}

    @SubscribeEvent
    public static void registerPipRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(WorldLoadingCubeStatusesRenderState.class, WorldLoadingCubeStatusesRenderer::new);
    }
}
