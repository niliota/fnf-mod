package space.niliota.fnfmod;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Reading pack icons out of .ico files (the two ways an .ico can store a picture), and the real packs' icons.
class PackIconsTest {

    // an .ico with one entry: the header, one directory entry, then the picture data
    private static byte[] ico(int size, byte[] picture) {
        ByteBuffer buf = ByteBuffer.allocate(6 + 16 + picture.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0).putShort((short) 1).putShort((short) 1);   // reserved, type 1 = icon, 1 entry
        buf.put((byte) (size == 256 ? 0 : size)).put((byte) (size == 256 ? 0 : size)).put((byte) 0).put((byte) 0);
        buf.putShort((short) 1).putShort((short) 32).putInt(picture.length).putInt(22);
        buf.put(picture);
        return buf.array();
    }

    @Test
    void pngEntry() throws IOException {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(10, 20, 0xFF00FF00);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);

        BufferedImage read = PackIcons.readIco(ico(256, png.toByteArray()));
        assertEquals(256, read.getWidth());
        assertEquals(0xFF00FF00, read.getRGB(10, 20));
    }

    @Test
    void bitmapEntryIsNotUpsideDown() throws IOException {
        // 2x2, 32-bit, rows stored bottom first. The top-left pixel is red.
        int width = 2, height = 2;
        ByteBuffer bmp = ByteBuffer.allocate(40 + width * height * 4 + 8).order(ByteOrder.LITTLE_ENDIAN);
        bmp.putInt(40).putInt(width).putInt(height * 2).putShort((short) 1).putShort((short) 32);
        bmp.position(40);
        bmp.put(new byte[] {0, 0, 0, (byte) 255, 0, 0, 0, (byte) 255});            // bottom row: black, black
        bmp.put(new byte[] {0, 0, (byte) 255, (byte) 255, 0, 0, 0, (byte) 255});   // top row: red (B,G,R,A), black

        BufferedImage read = PackIcons.readIco(ico(2, bmp.array()));
        assertEquals(0xFFFF0000, read.getRGB(0, 0));
        assertEquals(0xFF000000, read.getRGB(0, 1));
    }

    @Test
    void notAnIco() throws IOException {
        assertNull(PackIcons.readIco(new byte[] {1, 2, 3, 4, 5, 6, 7}));
    }

    @Test
    void widePicturesArentStretched() {
        BufferedImage wide = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 200; x++) for (int y = 0; y < 100; y++) wide.setRGB(x, y, 0xFFFF0000);

        BufferedImage icon = PackIcons.fit(wide);
        assertEquals(PackIcons.SIZE, icon.getWidth());
        assertEquals(0, icon.getRGB(32, 2) >>> 24, "see-through above the picture");
        assertEquals(0xFF, icon.getRGB(32, 32) >>> 24, "the picture in the middle");
    }

    // The packs in run/fnfpacks (not in the repo, skipped on GitHub): every pack that has an image should get a 64x64 icon.
    // Prints where each icon came from.
    @Test
    void realPacks() throws IOException {
        Path packsDir = Path.of("run", "fnfpacks");
        Assumptions.assumeTrue(Files.isDirectory(packsDir), "run/fnfpacks not found, skipping");

        try (DirectoryStream<Path> packs = Files.newDirectoryStream(packsDir)) {
            for (Path packPath : packs) {
                if (!Files.isDirectory(packPath)) continue;
                FNFPack pack = new FNFPack(0, packPath.getFileName().toString(), packPath);
                List<Path> candidates = PackIcons.candidates(pack.packPath, pack.modPaths);
                byte[] png = PackIcons.iconFor(pack.packPath, pack.modPaths);

                if (png.length > 0) {
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
                    assertEquals(PackIcons.SIZE, image.getWidth(), pack.name + " icon size");
                    System.out.println("Pack icon: " + pack.name + " <- " + packPath.relativize(candidates.get(0)));
                } else {
                    assertTrue(candidates.isEmpty(), pack.name + " has images but none could be read: " + candidates);
                    System.out.println("Pack icon: " + pack.name + " <- none");
                }
            }
        }
    }
}
