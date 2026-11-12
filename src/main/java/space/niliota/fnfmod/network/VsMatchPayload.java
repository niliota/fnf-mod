package space.niliota.fnfmod.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// Server -> client: what state the player's VS match is in now.
//  PICKING - the challenge was accepted, both players can pick a song (first one decides)
//  LOADING - a song was picked, both players load it (the song itself comes as a normal SongDataPayload right after)
//  GO      - both are ready, start the countdown
//  ENDED   - the match is over or was cancelled (the result is in the chat)
public record VsMatchPayload(int state, String rivalName, UUID rivalId, boolean youAreBoyfriend, String songName) implements CustomPacketPayload {
    public static final int PICKING = 0, LOADING = 1, GO = 2, ENDED = 3;

    public static final CustomPacketPayload.Type<VsMatchPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "vs_match"));

    public static final StreamCodec<FriendlyByteBuf, VsMatchPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.state());
                buf.writeUtf(payload.rivalName());
                UUIDUtil.STREAM_CODEC.encode(buf, payload.rivalId());
                buf.writeBoolean(payload.youAreBoyfriend());
                buf.writeUtf(payload.songName());
            },
            buf -> new VsMatchPayload(buf.readVarInt(), buf.readUtf(), UUIDUtil.STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
