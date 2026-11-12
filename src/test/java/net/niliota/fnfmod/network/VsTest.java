package net.niliota.fnfmod.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// VS: who wins, and the VS packets survive being written and read back.
class VsTest {

    private static VsScore score(int points, double accuracy, int status) {
        return new VsScore(points, 0, accuracy, 0, status);
    }

    @Test
    void higherScoreWins() {
        assertEquals(1, VsScore.compare(score(5000, 0.9, VsScore.FINISHED), score(4000, 0.99, VsScore.FINISHED)));
        assertEquals(-1, VsScore.compare(score(4000, 0.99, VsScore.FINISHED), score(5000, 0.9, VsScore.FINISHED)));
    }

    @Test
    void accuracyBreaksTies() {
        assertEquals(1, VsScore.compare(score(5000, 0.95, VsScore.FINISHED), score(5000, 0.90, VsScore.FINISHED)));
        assertEquals(0, VsScore.compare(score(5000, 0.95, VsScore.FINISHED), score(5000, 0.95, VsScore.FINISHED)));
    }

    @Test
    void givingUpOrNoResultLoses() {
        assertEquals(1, VsScore.compare(score(100, 0.5, VsScore.FINISHED), score(99999, 1, VsScore.GAVE_UP)));
        assertEquals(-1, VsScore.compare(null, score(0, 0, VsScore.FINISHED)));
        assertEquals(0, VsScore.compare(score(0, 0, VsScore.GAVE_UP), null));
    }

    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, T> codec, T payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, payload);
        T result = codec.decode(buf);
        assertEquals(0, buf.readableBytes());
        return result;
    }

    @Test
    void packets() {
        VsMatchPayload match = new VsMatchPayload(VsMatchPayload.LOADING, "Rival", UUID.randomUUID(), false, "bopeebo");
        assertEquals(match, roundTrip(VsMatchPayload.CODEC, match));

        VsPickPayload pick = new VsPickPayload(new RequestSongPayload(1, 2, List.of(0), List.of(0, 1), 3, -1), false);
        assertEquals(pick, roundTrip(VsPickPayload.CODEC, pick));

        VsScorePayload myScore = new VsScorePayload(new VsScore(12345, 3, 0.9876, 210, VsScore.PLAYING));
        assertEquals(myScore, roundTrip(VsScorePayload.CODEC, myScore));

        VsRivalScorePayload rivalScore = new VsRivalScorePayload(new VsScore(1, 2, 0.5, 3, VsScore.GAVE_UP));
        assertEquals(rivalScore, roundTrip(VsRivalScorePayload.CODEC, rivalScore));

        assertNotNull(roundTrip(VsReadyPayload.CODEC, new VsReadyPayload()));
    }
}
