package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Client -> server: I started (playing = true) or stopped playing a song. countdownMs = how long until the music starts.
// The server uses it to show the game to players standing nearby (SpectateManager).
public record PlayingPayload(boolean playing, int countdownMs) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayingPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "playing"));

    public static final StreamCodec<FriendlyByteBuf, PlayingPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.playing());
                buf.writeVarInt(payload.countdownMs());
            },
            buf -> new PlayingPayload(buf.readBoolean(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
