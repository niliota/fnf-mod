package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Client -> server: my score in the VS match (a few times a second while playing, and once at the end).
public record VsScorePayload(VsScore score) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VsScorePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "vs_score"));

    public static final StreamCodec<FriendlyByteBuf, VsScorePayload> CODEC = StreamCodec.of(
            (buf, payload) -> payload.score().write(buf),
            buf -> new VsScorePayload(VsScore.read(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
