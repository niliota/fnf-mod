package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Server -> client: stop watching this game (it ended, or you walked away).
public record SpectateStopPayload(int gameId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpectateStopPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "spectate_stop"));

    public static final StreamCodec<FriendlyByteBuf, SpectateStopPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.gameId()),
            buf -> new SpectateStopPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
