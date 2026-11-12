package net.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// Client -> server: what happened in my game during the last tick of a VS match (presses, hits, misses).
public record VsEventsPayload(List<VsEvent> events) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VsEventsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "vs_events"));

    public static final StreamCodec<FriendlyByteBuf, VsEventsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> VsEvent.writeList(buf, payload.events()),
            buf -> new VsEventsPayload(VsEvent.readList(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
