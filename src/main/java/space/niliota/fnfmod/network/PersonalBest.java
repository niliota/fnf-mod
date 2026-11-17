package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;

// One personal best, for one chart (pack + song + chart file). Sent between the client and a server that keeps
// its players' bests for its own songs (ServerBests).
public record PersonalBest(String pack, String song, String chart, int score, double accuracy, int misses, int maxCombo,
                           String rating, String fullCombo) {
    public static final int MAX_TEXT = 256; // pack/song/chart names are never longer than this

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(pack, MAX_TEXT);
        buf.writeUtf(song, MAX_TEXT);
        buf.writeUtf(chart, MAX_TEXT);
        buf.writeVarInt(score);
        buf.writeDouble(accuracy);
        buf.writeVarInt(misses);
        buf.writeVarInt(maxCombo);
        buf.writeUtf(rating, 32);
        buf.writeUtf(fullCombo, 32);
    }

    public static PersonalBest read(FriendlyByteBuf buf) {
        return new PersonalBest(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readVarInt(),
                buf.readDouble(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32), buf.readUtf(32));
    }
}
