package net.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// Client -> server, the hashes of the audio files the client doesn't have yet and wants the server to send.
public record AudioRequestPayload(List<String> hashes) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AudioRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "audio_request"));

    public static final StreamCodec<FriendlyByteBuf, AudioRequestPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeCollection(payload.hashes(), FriendlyByteBuf::writeUtf),
            buf -> new AudioRequestPayload(buf.readList(FriendlyByteBuf::readUtf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
