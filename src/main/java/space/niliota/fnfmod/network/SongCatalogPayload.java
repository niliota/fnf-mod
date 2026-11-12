package space.niliota.fnfmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import space.niliota.fnfmod.FNFPack;
import space.niliota.fnfmod.FNFSong;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Server -> client, sent when a player joins.
// The list of every pack and song on the server, so the client can show the menus without having the packs itself.
// Only names are sent, never the actual paths on the server. The client later asks for files by their position in these lists.
public record SongCatalogPayload(List<PackEntry> packs) implements CustomPacketPayload {

    public record PackEntry(String name, List<SongEntry> songs) {}

    public record SongEntry(String name,
                            List<String> instFiles,
                            List<String> voicesFiles,
                            List<String> chartFiles,
                            List<String> metadataFiles) {}

    public static final CustomPacketPayload.Type<SongCatalogPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("fnfmod", "song_catalog"));

    public static final StreamCodec<FriendlyByteBuf, SongCatalogPayload> CODEC = StreamCodec.of(
            // WRITE (Server -> ByteBuf)
            (buf, payload) -> {
                buf.writeVarInt(payload.packs().size());
                for (PackEntry pack : payload.packs()) {
                    buf.writeUtf(pack.name());
                    buf.writeVarInt(pack.songs().size());
                    for (SongEntry song : pack.songs()) {
                        buf.writeUtf(song.name());
                        buf.writeCollection(song.instFiles(), FriendlyByteBuf::writeUtf);
                        buf.writeCollection(song.voicesFiles(), FriendlyByteBuf::writeUtf);
                        buf.writeCollection(song.chartFiles(), FriendlyByteBuf::writeUtf);
                        buf.writeCollection(song.metadataFiles(), FriendlyByteBuf::writeUtf);
                    }
                }
            },
            // READ (ByteBuf -> Client), in the exact same order as it was written
            buf -> {
                List<PackEntry> packs = new ArrayList<>();
                int packCount = buf.readVarInt();
                for (int p = 0; p < packCount; p++) {
                    String packName = buf.readUtf();
                    List<SongEntry> songs = new ArrayList<>();
                    int songCount = buf.readVarInt();
                    for (int s = 0; s < songCount; s++) {
                        songs.add(new SongEntry(
                                buf.readUtf(),
                                buf.readList(FriendlyByteBuf::readUtf),
                                buf.readList(FriendlyByteBuf::readUtf),
                                buf.readList(FriendlyByteBuf::readUtf),
                                buf.readList(FriendlyByteBuf::readUtf)
                        ));
                    }
                    packs.add(new PackEntry(packName, songs));
                }
                return new SongCatalogPayload(packs);
            }
    );

    // Builds the catalog out of the packs the server found in fnfpacks/.
    public static SongCatalogPayload fromPacks(List<FNFPack> packs) {
        List<PackEntry> packEntries = new ArrayList<>();
        for (FNFPack pack : packs) {
            List<SongEntry> songEntries = new ArrayList<>();
            for (FNFSong song : pack.songs) {
                songEntries.add(new SongEntry(
                        song.name,
                        fileNames(song.audioInstFiles),
                        fileNames(song.audioVoicesFiles),
                        fileNames(song.chartFiles),
                        fileNames(song.metadataFiles)
                ));
            }
            packEntries.add(new PackEntry(pack.name, songEntries));
        }
        return new SongCatalogPayload(packEntries);
    }

    // only the file names, like "Voices-bf.ogg", the folders they're in stay on the server
    private static List<String> fileNames(List<Path> files) {
        List<String> names = new ArrayList<>();
        for (Path file : files) {
            names.add(file.getFileName().toString());
        }
        return names;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
