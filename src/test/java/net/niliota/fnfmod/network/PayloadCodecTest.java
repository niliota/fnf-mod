package net.niliota.fnfmod.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.niliota.fnfmod.Charting.BpmChange;
import net.niliota.fnfmod.Charting.ChartNote;
import net.niliota.fnfmod.Charting.ScrollSpeeds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Writes every packet into bytes and reads it back, to make sure the reading side matches the writing side.
class PayloadCodecTest {

    // write the payload with its codec, then read it back out
    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, payload);
        T result = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "everything that was written should also be read");
        return result;
    }

    @Test
    void songCatalog() {
        SongCatalogPayload catalog = new SongCatalogPayload(List.of(
                new SongCatalogPayload.PackEntry("Pack A", List.of(
                        new SongCatalogPayload.SongEntry("bopeebo", List.of("Inst.ogg"), List.of("Voices.ogg", "Voices-bf.ogg"), List.of("bopeebo-hard.json"), List.of()),
                        new SongCatalogPayload.SongEntry("fresh", List.of(), List.of(), List.of("fresh.json"), List.of("meta.json"))
                )),
                new SongCatalogPayload.PackEntry("Empty pack", List.of())
        ));

        assertEquals(catalog, roundTrip(SongCatalogPayload.CODEC, catalog));
    }

    @Test
    void requestSong() {
        RequestSongPayload request = new RequestSongPayload(2, 15, List.of(0), List.of(0, 1), 3, -1);
        assertEquals(request, roundTrip(RequestSongPayload.CODEC, request));
    }

    @Test
    void songData() {
        ChartNote note = new ChartNote();
        note.time = 1234.5;
        note.lane = 3;
        note.length = 250;
        note.isPlayer = true;

        SongDataPayload song = new SongDataPayload("roses", 120, 1.8, List.of(note),
                List.of(new BpmChange(0, 120), new BpmChange(5000, 180)),
                List.of(new ScrollSpeeds.Change(8000, 2.5, 400, "quadInOut", ScrollSpeeds.PLAYER, false)),
                List.of("a".repeat(40)));
        SongDataPayload result = roundTrip(SongDataPayload.CODEC, song);

        assertEquals("roses", result.songName());
        assertEquals(120, result.bpm());
        assertEquals(1.8, result.scrollSpeed());
        assertEquals(song.audioHashes(), result.audioHashes());
        assertEquals(song.bpmChanges(), result.bpmChanges());
        assertEquals(song.scrollChanges(), result.scrollChanges());

        ChartNote resultNote = result.notes().get(0);
        assertEquals(1234.5, resultNote.time);
        assertEquals(3, resultNote.lane);
        assertEquals(250, resultNote.length);
        assertTrue(resultNote.isPlayer);
    }

    @Test
    void songDataStaysUnderPacketLimit() {
        // the biggest allowed chart should still fit into one packet (1 MB)
        List<ChartNote> notes = new java.util.ArrayList<>();
        for (int i = 0; i < SongDataPayload.MAX_NOTES; i++) notes.add(new ChartNote());

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SongDataPayload.CODEC.encode(buf, new SongDataPayload("big", 100, 2, notes, List.of(), List.of(), List.of()));
        assertTrue(buf.readableBytes() < 1024 * 1024, "SongDataPayload was " + buf.readableBytes() + " bytes");
    }

    @Test
    void audioChunk() {
        byte[] data = new byte[AudioChunkPayload.CHUNK_SIZE];
        data[0] = 7;
        data[data.length - 1] = 9;

        AudioChunkPayload chunk = new AudioChunkPayload("b".repeat(40), 512, 4_000_000, data);
        AudioChunkPayload result = roundTrip(AudioChunkPayload.CODEC, chunk);

        assertEquals(chunk.hash(), result.hash());
        assertEquals(512, result.offset());
        assertEquals(4_000_000, result.totalSize());
        assertArrayEquals(data, result.data());
    }
}
