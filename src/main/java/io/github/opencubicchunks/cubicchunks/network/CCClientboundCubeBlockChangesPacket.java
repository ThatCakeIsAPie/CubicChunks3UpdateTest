package io.github.opencubicchunks.cubicchunks.network;

import static io.github.opencubicchunks.cubicchunks.network.MiscStreamCodecs.CUBE_POS_STREAM_CODEC;

import java.util.ArrayList;
import java.util.List;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubeLightPacketData;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/**
 * One dig (or a batch of them) for a cube that tracking clients already have.
 * <p>
 * The dasm copy of {@code ChunkHolder.broadcastChanges} still built {@code ClientboundSectionBlocksUpdatePacket}. That
 * packet is addressed as a column section and does not carry the cube's server light nibbles. This packet names the
 * cube, the block states, and the light snapshot after the edit. Clients that do not have the cube yet ignore it; the
 * full cube packet is built at send time and already contains the edit.
 */
public record CCClientboundCubeBlockChangesPacket(CubePos pos, List<Change> changes, CubeLightPacketData light) implements CustomPacketPayload {

    public static final Type<CCClientboundCubeBlockChangesPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CubicChunks.MODID, "cube_block_changes"));

    public static final StreamCodec<FriendlyByteBuf, CCClientboundCubeBlockChangesPacket> STREAM_CODEC = StreamCodec
            .of(CCClientboundCubeBlockChangesPacket::encode, CCClientboundCubeBlockChangesPacket::decode);

    /**
     * Reads the sections {@code blockChanged} marked on {@code cube}. Block states and light are sampled together so a
     * concurrent editor cannot tear the palette between the two.
     */
    public static CCClientboundCubeBlockChangesPacket capture(LevelCube cube, ShortSet[] changedBlocksPerSection) {
        return cube.cc_callWithBlockLock(() -> {
            List<Change> changes = new ArrayList<>();
            int limit = Math.min(changedBlocksPerSection.length, CubicConstants.SECTION_COUNT);
            CubePos cubePos = cube.cc_getCubePos();
            for (int sectionIndex = 0; sectionIndex < limit; sectionIndex++) {
                ShortSet locals = changedBlocksPerSection[sectionIndex];
                if (locals == null || locals.isEmpty()) {
                    continue;
                }
                SectionPos sectionPos = Coords.sectionPosByIndex(cubePos, sectionIndex);
                ShortIterator iterator = locals.iterator();
                while (iterator.hasNext()) {
                    BlockPos blockPos = sectionPos.relativeToBlockPos(iterator.nextShort());
                    changes.add(new Change(blockPos, cube.getBlockState(blockPos)));
                }
            }
            return new CCClientboundCubeBlockChangesPacket(cubePos, List.copyOf(changes), CubeLightPacketData.from(cube));
        });
    }

    /**
     * Applies this batch onto {@code cube}. The same block state is a no-op inside {@link LevelCube#setBlockState}.
     * Server light replaces any local relight so the copy matches the cube the packet was captured from.
     */
    public void apply(LevelCube cube) {
        if (!cube.cc_getCubePos().equals(this.pos)) {
            return;
        }
        for (Change change : this.changes) {
            cube.setBlockState(change.pos, change.state, Block.UPDATE_SKIP_ON_PLACE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        }
        this.light.apply(cube);
    }

    private static void encode(FriendlyByteBuf buffer, CCClientboundCubeBlockChangesPacket packet) {
        CUBE_POS_STREAM_CODEC.encode(buffer, packet.pos);
        buffer.writeVarInt(packet.changes.size());
        for (Change change : packet.changes) {
            buffer.writeBlockPos(change.pos);
            buffer.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(change.state));
        }
        CubeLightPacketData.STREAM_CODEC.encode(buffer, packet.light);
    }

    private static CCClientboundCubeBlockChangesPacket decode(FriendlyByteBuf buffer) {
        CubePos pos = CUBE_POS_STREAM_CODEC.decode(buffer);
        int count = buffer.readVarInt();
        if (count < 0) {
            throw new IllegalArgumentException("Negative cube block change count");
        }
        List<Change> changes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos blockPos = buffer.readBlockPos();
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(buffer.readVarInt());
            changes.add(new Change(blockPos, state));
        }
        CubeLightPacketData light = CubeLightPacketData.STREAM_CODEC.decode(buffer);
        return new CCClientboundCubeBlockChangesPacket(pos, List.copyOf(changes), light);
    }

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Change(BlockPos pos, BlockState state) {}

    public static class Handler implements IPayloadHandler<CCClientboundCubeBlockChangesPacket> {
        @Override public void handle(CCClientboundCubeBlockChangesPacket payload, IPayloadContext context) {
            var cache = (ClientCubeCache) context.player().level().getChunkSource();
            CubePos pos = payload.pos;
            LevelCube cube = cache.cc_getCube(pos.getX(), pos.getY(), pos.getZ(), false);
            if (cube != null) {
                payload.apply(cube);
            }
        }
    }
}
