package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Client -> server: in a VS match, the player picked this song and this side (boyfriend or opponent).
// The song is chosen the same way as a normal song request.
public record VsPickPayload(RequestSongPayload request, boolean boyfriend) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VsPickPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "vs_pick"));

    public static final StreamCodec<FriendlyByteBuf, VsPickPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                RequestSongPayload.CODEC.encode(buf, payload.request());
                buf.writeBoolean(payload.boyfriend());
            },
            buf -> new VsPickPayload(RequestSongPayload.CODEC.decode(buf), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
