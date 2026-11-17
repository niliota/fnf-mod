package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Server -> client, sent when joining: the server owner's settings the client needs to know about (ServerConfig).
//  requireMic - the menu key doesn't work without a Mic in the inventory, players have to craft one
public record ServerSettingsPayload(boolean requireMic) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ServerSettingsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "server_settings"));

    public static final StreamCodec<FriendlyByteBuf, ServerSettingsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.requireMic()),
            buf -> new ServerSettingsPayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
