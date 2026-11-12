package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Client -> server: in a VS match, this player has the song downloaded and loaded, ready to start.
public record VsReadyPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VsReadyPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "vs_ready"));

    public static final StreamCodec<FriendlyByteBuf, VsReadyPayload> CODEC = StreamCodec.unit(new VsReadyPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
