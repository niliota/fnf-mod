package space.niliota.fnfmod.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// Server -> client: you're near someone playing, here's their game (its arrows float above the players, ClientSpectate).
// song = the same song data a player gets. The boyfriend side is a player; the opponent side is a player in VS,
// or the computer in a solo game (opponentName "", opponentId = NOBODY).
// musicPosition = where the music is right now in ms (negative = still counting down).
public record SpectateStartPayload(int gameId, SongDataPayload song,
                                   String boyfriendName, UUID boyfriendId,
                                   String opponentName, UUID opponentId,
                                   long musicPosition) implements CustomPacketPayload {

    public static final UUID NOBODY = new UUID(0, 0);

    public static final CustomPacketPayload.Type<SpectateStartPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "spectate_start"));

    public static final StreamCodec<FriendlyByteBuf, SpectateStartPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.gameId());
                SongDataPayload.CODEC.encode(buf, payload.song());
                buf.writeUtf(payload.boyfriendName());
                UUIDUtil.STREAM_CODEC.encode(buf, payload.boyfriendId());
                buf.writeUtf(payload.opponentName());
                UUIDUtil.STREAM_CODEC.encode(buf, payload.opponentId());
                buf.writeLong(payload.musicPosition());
            },
            buf -> new SpectateStartPayload(buf.readVarInt(), SongDataPayload.CODEC.decode(buf),
                    buf.readUtf(), UUIDUtil.STREAM_CODEC.decode(buf),
                    buf.readUtf(), UUIDUtil.STREAM_CODEC.decode(buf),
                    buf.readLong())
    );

    public boolean againstComputer() {
        return opponentId.equals(NOBODY);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
