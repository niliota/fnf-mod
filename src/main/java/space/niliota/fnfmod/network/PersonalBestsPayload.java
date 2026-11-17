package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

// Server -> client, sent when joining: my personal bests for this server's songs (the server keeps them, see ServerBests).
// Getting this also tells the client that this server keeps them, so finished songs get sent back (PersonalBestPayload).
public record PersonalBestsPayload(List<PersonalBest> bests) implements CustomPacketPayload {
    public static final int MAX_BESTS = 10000; // per player, way more charts than anyone plays

    public static final CustomPacketPayload.Type<PersonalBestsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "personal_bests"));

    public static final StreamCodec<FriendlyByteBuf, PersonalBestsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.bests().size());
                for (PersonalBest best : payload.bests()) best.write(buf);
            },
            buf -> {
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_BESTS) throw new IllegalArgumentException("Too many personal bests: " + count);
                List<PersonalBest> bests = new ArrayList<>();
                for (int i = 0; i < count; i++) bests.add(PersonalBest.read(buf));
                return new PersonalBestsPayload(bests);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
