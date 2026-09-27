package io.github.opencubicchunks.cubicchunks.network;

import static io.github.opencubicchunks.cubicchunks.network.MiscStreamCodecs.CUBE_POS_STREAM_CODEC;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.client.renderer.CubicLevelRenderer;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubeLightPacketData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

public record CCClientboundLevelCubeWithLightPacket(CubePos pos, CCClientboundLevelCubePacketData cubeData, CubeLightPacketData light)
        implements CustomPacketPayload {

    public static final Type<CCClientboundLevelCubeWithLightPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CubicChunks.MODID, "level_cube_with_light"));

    public static final StreamCodec<FriendlyByteBuf, CCClientboundLevelCubeWithLightPacket> STREAM_CODEC = StreamCodec.composite(
            CUBE_POS_STREAM_CODEC, CCClientboundLevelCubeWithLightPacket::pos, CCClientboundLevelCubePacketData.STREAM_CODEC,
            CCClientboundLevelCubeWithLightPacket::cubeData, CubeLightPacketData.STREAM_CODEC, CCClientboundLevelCubeWithLightPacket::light,
            CCClientboundLevelCubeWithLightPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public CCClientboundLevelCubeWithLightPacket(LevelCube cube) {
        this(cube.cc_getCloPos().cubePos(), new CCClientboundLevelCubePacketData(cube), CubeLightPacketData.from(cube));
    }

    public static class Handler implements IPayloadHandler<CCClientboundLevelCubeWithLightPacket> {
        @Override public void handle(CCClientboundLevelCubeWithLightPacket payload, IPayloadContext context) {
            int x = payload.pos.getX();
            int y = payload.pos.getY();
            int z = payload.pos.getZ();
            this.updateLevelCube(context.player().level(), x, y, z, payload);
        }

        private void updateLevelCube(Level level, int x, int y, int z, CCClientboundLevelCubeWithLightPacket payload) {
            // TODO P2 :: The empty map should contain heightmap data
            Map<Heightmap.Types, long[]> heightmaps = new HashMap<>();

            // TODO P2 :: No block entity tags consumer
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> entityTagConsumer = (a) -> {};

            LevelCube levelCube = ((ClientCubeCache) (level.getChunkSource())).cc_replaceWithPacketData(x, y, z, payload.cubeData.getReadBuffer(),
                    heightmaps, entityTagConsumer);
            if (levelCube != null) {
                payload.light().apply(levelCube);
            }

            ((ClientLevel) level).queueLightUpdate(() -> {
                LevelCube ready = ((CubeSource) level.getChunkSource()).cc_getCube(x, y, z, false);
                if (ready != null) {
                    ((CubicLevelRenderer) Minecraft.getInstance().levelRenderer).cc_onCubeReadyToRender(payload.pos);
                }
            });
        }
    }
}
