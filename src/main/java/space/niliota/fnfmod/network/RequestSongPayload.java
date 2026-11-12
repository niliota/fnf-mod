package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// Client -> server, sent when the player presses "Start playing".
// Everything is a position in the lists from SongCatalogPayload, never a file path,
// so a client can't ask the server for any file that isn't part of a pack.
public record RequestSongPayload(int packId,
                                 int songId,
                                 List<Integer> instIds,
                                 List<Integer> voicesIds,
                                 int chartId,
                                 int metadataId // -1 if no metadata file is selected
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestSongPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "request_song"));

    public static final StreamCodec<FriendlyByteBuf, RequestSongPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.packId());
                buf.writeVarInt(payload.songId());
                buf.writeCollection(payload.instIds(), FriendlyByteBuf::writeVarInt);
                buf.writeCollection(payload.voicesIds(), FriendlyByteBuf::writeVarInt);
                buf.writeVarInt(payload.chartId());
                buf.writeVarInt(payload.metadataId());
            },
            buf -> new RequestSongPayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readList(FriendlyByteBuf::readVarInt),
                    buf.readList(FriendlyByteBuf::readVarInt),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
