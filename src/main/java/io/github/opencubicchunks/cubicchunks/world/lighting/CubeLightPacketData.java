package io.github.opencubicchunks.cubicchunks.world.lighting;

import java.util.Arrays;

import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Packed {@link CubeLightData} carried by {@code CCClientboundLevelCubeWithLightPacket}. */
public final class CubeLightPacketData {
    public static final int MAX_PACKED_BYTES = 2097152;

    public static final StreamCodec<FriendlyByteBuf, CubeLightPacketData> STREAM_CODEC = StreamCodec.of(CubeLightPacketData::encode,
            CubeLightPacketData::decode);

    private final byte[] packed;

    public CubeLightPacketData(byte[] packed) {
        this.packed = packed;
    }

    public static CubeLightPacketData from(CubeAccess cube) {
        return new CubeLightPacketData(cube.cc_lightData().writePacked());
    }

    public void apply(CubeAccess cube) {
        cube.cc_lightData().readPacked(this.packed);
        cube.setLightCorrect(true);
    }

    private static void encode(FriendlyByteBuf buffer, CubeLightPacketData data) {
        buffer.writeByteArray(data.packed);
    }

    private static CubeLightPacketData decode(FriendlyByteBuf buffer) {
        return new CubeLightPacketData(buffer.readByteArray(MAX_PACKED_BYTES));
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof CubeLightPacketData that)) {
            return false;
        }
        return Arrays.equals(this.packed, that.packed);
    }

    @Override public int hashCode() {
        return Arrays.hashCode(this.packed);
    }
}
