package space.niliota.fnfmod;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// The icon of an FNF mod, for the menus. Looked for in this order:
//  1. the root of the pack: icon.ico (the game's .exe icon), any other .ico, icon.png, pack.png, then any other image
//  2. the pack's mod folders (mods/<mod>/, content/<mod>/): pack.png (Psych's mod icon) or icon.png.
//     Only those names here: Psych's mods/ folder can also hold loose content folders full of random sprites.
// The picture is fitted into SIZE x SIZE and turned into a PNG, which gets sent to players with the song list.
// Packs without any of these just have no icon.
//
// .ico files hold several sizes of the same picture, each stored either as a PNG or as a raw bitmap (BMP).
// Java can't read .ico files by itself, so this picks the entries out by hand.
public class PackIcons {
    public static final int SIZE = 64;
    private static final Map<Path, byte[]> cache = new HashMap<>(); // pack folder -> PNG (empty = no icon)

    // PNG bytes of the pack's icon, or an empty array if it has none. modPaths = the pack's mod folders (FNFPack.modPaths)
    public static synchronized byte[] iconFor(Path packPath, List<Path> modPaths) {
        return cache.computeIfAbsent(packPath, path -> load(path, modPaths));
    }

    private static byte[] load(Path packPath, List<Path> modPaths) {
        for (Path file : candidates(packPath, modPaths)) {
            try {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                BufferedImage image = name.endsWith(".ico") ? readIco(Files.readAllBytes(file)) : ImageIO.read(file.toFile());
                if (image != null) return toPng(fit(image));
            } catch (Exception e) {
                LOGGER.warn("Couldn't read {} as the icon of pack {}", file.getFileName(), packPath.getFileName(), e);
            }
            // couldn't read this one, try the next
        }
        return new byte[0];
    }

    // every file that could be the icon, best first
    static List<Path> candidates(Path packPath, List<Path> modPaths) {
        List<Path> files = new ArrayList<>();
        List<Path> rootImages = imagesIn(packPath);

        // the root of the pack
        addIfExists(files, packPath.resolve("icon.ico"));
        for (Path file : rootImages) if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ico")) files.add(file);
        addIfExists(files, packPath.resolve("icon.png"));
        addIfExists(files, packPath.resolve("pack.png"));
        files.addAll(rootImages); // anything else that's a picture

        // the mod folders, only their pack.png / icon.png
        List<Path> mods = new ArrayList<>(modPaths);
        mods.sort(null); // same order every time
        for (Path mod : mods) {
            if (mod.equals(packPath)) continue; // already looked at
            addIfExists(files, mod.resolve("pack.png"));
            addIfExists(files, mod.resolve("icon.png"));
        }

        return files.stream().distinct().toList();
    }

    private static void addIfExists(List<Path> files, Path file) {
        if (Files.isRegularFile(file)) files.add(file);
    }

    // the pictures at the top of a folder, sorted by name
    private static List<Path> imagesIn(Path folder) {
        List<Path> images = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.{ico,ICO,png,PNG,jpg,JPG,jpeg,JPEG,bmp,BMP,gif,GIF}")) {
            for (Path file : files) {
                if (Files.isRegularFile(file)) images.add(file);
            }
        } catch (IOException ignored) {}
        images.sort(null);
        return images;
    }

    // The biggest picture in an .ico file
    static BufferedImage readIco(byte[] data) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 6 || buf.getShort(2) != 1) return null; // type 1 = icon
        int count = buf.getShort(4) & 0xFFFF;

        int bestSize = -1, bestOffset = 0, bestLength = 0;
        for (int i = 0; i < count; i++) {
            int entry = 6 + i * 16;
            if (entry + 16 > data.length) break;
            int width = data[entry] & 0xFF;
            if (width == 0) width = 256; // 0 means 256
            int length = buf.getInt(entry + 8);
            int offset = buf.getInt(entry + 12);
            if (offset < 0 || length <= 0 || offset + length > data.length) continue;
            if (width > bestSize) {
                bestSize = width;
                bestOffset = offset;
                bestLength = length;
            }
        }
        if (bestSize < 0) return null;

        // PNG entries start with the PNG signature, Java can read those
        if ((data[bestOffset] & 0xFF) == 0x89 && data[bestOffset + 1] == 'P' && data[bestOffset + 2] == 'N' && data[bestOffset + 3] == 'G') {
            return ImageIO.read(new ByteArrayInputStream(data, bestOffset, bestLength));
        }
        return readBmpEntry(buf, bestOffset);
    }

    // A bitmap entry: a BITMAPINFOHEADER, then the pixels bottom row first. Only 32-bit (BGRA) ones, that's what FNF builds use.
    private static BufferedImage readBmpEntry(ByteBuffer buf, int offset) {
        int headerSize = buf.getInt(offset);
        int width = buf.getInt(offset + 4);
        int height = buf.getInt(offset + 8) / 2; // the height counts the transparency mask too
        int bitsPerPixel = buf.getShort(offset + 14);
        if (bitsPerPixel != 32 || width <= 0 || height <= 0 || width > 1024 || height > 1024) return null;

        int pixels = offset + headerSize;
        if (pixels + width * height * 4 > buf.capacity()) return null;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int p = pixels + ((height - 1 - y) * width + x) * 4;
                int b = buf.get(p) & 0xFF, g = buf.get(p + 1) & 0xFF, r = buf.get(p + 2) & 0xFF, a = buf.get(p + 3) & 0xFF;
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    // Fits the picture into SIZE x SIZE without stretching it (wide or tall pictures get see-through space around them)
    static BufferedImage fit(BufferedImage image) {
        double scale = Math.min((double) SIZE / image.getWidth(), (double) SIZE / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));

        BufferedImage small = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        var graphics = small.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(image, (SIZE - width) / 2, (SIZE - height) / 2, width, height, null);
        graphics.dispose();
        return small;
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
