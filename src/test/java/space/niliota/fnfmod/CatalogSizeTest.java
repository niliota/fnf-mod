package space.niliota.fnfmod;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import space.niliota.fnfmod.network.SongCatalogPayload;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Builds the song catalog out of the real packs in run/fnfpacks (if they exist, they aren't in the repo)
// and checks it still fits into one packet (1 MB).
class CatalogSizeTest {

    @Test
    void catalogFitsInOnePacket() throws IOException {
        Path packsDir = Path.of("run", "fnfpacks");
        Assumptions.assumeTrue(Files.isDirectory(packsDir), "run/fnfpacks not found, skipping");

        List<FNFPack> packs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(packsDir)) {
            int i = 0;
            for (Path path : stream) {
                if (Files.isDirectory(path)) packs.add(new FNFPack(++i, path.getFileName().toString(), path));
            }
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SongCatalogPayload.CODEC.encode(buf, SongCatalogPayload.fromPacks(packs));

        int songs = packs.stream().mapToInt(p -> p.songs.size()).sum();
        System.out.println("Catalog: " + packs.size() + " packs, " + songs + " songs, " + buf.readableBytes() + " bytes");
        assertTrue(buf.readableBytes() < 1024 * 1024, "SongCatalogPayload was " + buf.readableBytes() + " bytes");
    }
}
