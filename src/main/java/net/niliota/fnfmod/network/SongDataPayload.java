package net.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.niliota.fnfmod.Charting.ChartNote;

import java.util.ArrayList;
import java.util.List;

// Server -> client, the answer to RequestSongPayload.
// The chart is already parsed by the server, so the client only gets the notes (both sides) and the song's info.
// The audio files are only named by their hash, the client then checks if it has them or needs to download them.
public record SongDataPayload(String songName,
                              int bpm,
                              double scrollSpeed,
                              List<ChartNote> notes,
                              List<String> audioHashes
) implements CustomPacketPayload {

    // A single packet can't be bigger than 1 MB. One note takes ~19 bytes, so this keeps us well under that.
    public static final int MAX_NOTES = 40000;

    public static final CustomPacketPayload.Type<SongDataPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "song_data"));

    public static final StreamCodec<FriendlyByteBuf, SongDataPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.songName());
                buf.writeVarInt(payload.bpm());
                buf.writeDouble(payload.scrollSpeed());

                buf.writeVarInt(payload.notes().size());
                for (ChartNote note : payload.notes()) {
                    buf.writeDouble(note.time);
                    buf.writeByte(note.lane);
                    buf.writeDouble(note.length);
                    buf.writeBoolean(note.isPlayer);
                }

                buf.writeCollection(payload.audioHashes(), FriendlyByteBuf::writeUtf);
            },
            buf -> {
                String songName = buf.readUtf();
                int bpm = buf.readVarInt();
                double scrollSpeed = buf.readDouble();

                List<ChartNote> notes = new ArrayList<>();
                int noteCount = buf.readVarInt();
                for (int i = 0; i < noteCount; i++) {
                    ChartNote note = new ChartNote();
                    note.time     = buf.readDouble();
                    note.lane     = buf.readByte();
                    note.length   = buf.readDouble();
                    note.isPlayer = buf.readBoolean();
                    notes.add(note);
                }

                List<String> audioHashes = buf.readList(FriendlyByteBuf::readUtf);
                return new SongDataPayload(songName, bpm, scrollSpeed, notes, audioHashes);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
