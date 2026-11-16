package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// Server -> client: presses/hits/misses in the game being watched, from the boyfriend's or the opponent's side.
// Right after SpectateStartPayload the events from before you came come in too, so the scores are right.
public record SpectateEventsPayload(int gameId, boolean boyfriendSide, List<VsEvent> events) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpectateEventsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "spectate_events"));

    public static final StreamCodec<FriendlyByteBuf, SpectateEventsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.gameId());
                buf.writeBoolean(payload.boyfriendSide());
                VsEvent.writeList(buf, payload.events());
            },
            buf -> new SpectateEventsPayload(buf.readVarInt(), buf.readBoolean(), VsEvent.readList(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
