package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Server -> client, one piece of an audio file.
// Audio files are way bigger than what one packet can hold (1 MB), so they get cut into chunks.
// offset = where in the file this piece goes, totalSize = size of the whole file.
public record AudioChunkPayload(String hash, int offset, int totalSize, byte[] data) implements CustomPacketPayload {

    public static final int CHUNK_SIZE = 256 * 1024; // 256 KB per packet

    public static final CustomPacketPayload.Type<AudioChunkPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "audio_chunk"));

    public static final StreamCodec<FriendlyByteBuf, AudioChunkPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.hash());
                buf.writeVarInt(payload.offset());
                buf.writeVarInt(payload.totalSize());
                buf.writeByteArray(payload.data());
            },
            buf -> new AudioChunkPayload(
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readByteArray(CHUNK_SIZE)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
