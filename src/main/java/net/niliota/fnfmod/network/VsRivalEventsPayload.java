package net.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// Server -> client: what happened in the rival's game (passed on from their VsEventsPayload).
public record VsRivalEventsPayload(List<VsEvent> events) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VsRivalEventsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "vs_rival_events"));

    public static final StreamCodec<FriendlyByteBuf, VsRivalEventsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> VsEvent.writeList(buf, payload.events()),
            buf -> new VsRivalEventsPayload(VsEvent.readList(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
