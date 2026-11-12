package net.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Server -> client: the rival's score in the VS match, passed on from their VsScorePayload.
public record VsRivalScorePayload(VsScore score) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VsRivalScorePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "vs_rival_score"));

    public static final StreamCodec<FriendlyByteBuf, VsRivalScorePayload> CODEC = StreamCodec.of(
            (buf, payload) -> payload.score().write(buf),
            buf -> new VsRivalScorePayload(VsScore.read(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
