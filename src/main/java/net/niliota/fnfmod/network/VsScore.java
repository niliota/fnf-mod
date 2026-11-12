package net.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;

// A player's final result in a VS match, sent once at the end (the live score comes from VsEvents instead).
// status: FINISHED (the song ended) or GAVE_UP (stopped the song or left). PLAYING = no result yet.
public record VsScore(int score, int misses, double accuracy, int maxCombo, int status) {
    public static final int PLAYING = 0, FINISHED = 1, GAVE_UP = 2;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(score);
        buf.writeVarInt(misses);
        buf.writeDouble(accuracy);
        buf.writeVarInt(maxCombo);
        buf.writeVarInt(status);
    }

    public static VsScore read(FriendlyByteBuf buf) {
        return new VsScore(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readVarInt());
    }

    public boolean isDone() {
        return status != PLAYING;
    }

    // Who won: 1 if a won, -1 if b won, 0 for a draw.
    // Giving up always loses, then the higher score wins, then the better accuracy (Funky Friday goes by score too).
    public static int compare(VsScore a, VsScore b) {
        boolean aGaveUp = a == null || a.status == GAVE_UP;
        boolean bGaveUp = b == null || b.status == GAVE_UP;
        if (aGaveUp != bGaveUp) return aGaveUp ? -1 : 1;
        if (aGaveUp) return 0;

        if (a.score != b.score) return a.score > b.score ? 1 : -1;
        return Double.compare(a.accuracy, b.accuracy);
    }
}
