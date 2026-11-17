package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Client -> server: I beat my personal best on one of the server's songs, keep it (ServerBests).
public record PersonalBestPayload(PersonalBest best) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PersonalBestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "personal_best"));

    public static final StreamCodec<FriendlyByteBuf, PersonalBestPayload> CODEC = StreamCodec.of(
            (buf, payload) -> payload.best().write(buf),
            buf -> new PersonalBestPayload(PersonalBest.read(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
