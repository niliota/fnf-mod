package space.niliota.fnfmod.Audio;

import net.fabricmc.loader.api.FabricLoader;
import space.niliota.fnfmod.AudioHashes;
import space.niliota.fnfmod.network.AudioChunkPayload;

import space.niliota.fnfmod.FnfOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// Finds audio files by their hash, and puts together the ones being downloaded from a server.
// Downloaded files are saved as <game folder>/fnfcache/<hash>.ogg, so every song only has to be downloaded once.
//
// The cache has a size limit (Options, 1 GB by default). When it's over, the songs played the longest ago get deleted
// first (a file's "last modified" time is bumped every time it's played, see markUsed). Songs used in the last
// KEEP_RECENT minutes are never deleted, so the one playing, being watched or still downloading stays.
// A deleted song simply downloads again the next time it's played.
public class AudioCache {
    private static final Path CACHE_DIR = FabricLoader.getInstance().getGameDir().resolve("fnfcache");
    private static final int MAX_FILE_SIZE = 64 * 1024 * 1024; // 64 MB, way bigger than any song should be
    private static final long KEEP_RECENT = 10 * 60_000L;      // ms

    // files that are still being downloaded
    private static final Map<String, Download> downloads = new HashMap<>();

    private static class Download {
        byte[] data;
        int received = 0;

        Download(int size) {
            data = new byte[size];
        }
    }

    // Returns a playable file for this hash, or null if we don't have it.
    // Checks our own fnfpacks/ first (that's how singleplayer works), then the download cache.
    public static Path find(String hash) {
        if (!AudioHashes.isValidHash(hash)) return null;

        Path local = AudioHashes.findFile(hash);
        if (local != null && Files.exists(local)) return local;

        Path cached = CACHE_DIR.resolve(hash + ".ogg");
        if (Files.exists(cached)) return cached;

        return null;
    }

    public static boolean isDownloading(String hash) {
        return downloads.containsKey(hash);
    }

    // Called for every chunk the server sends.
    public static void acceptChunk(AudioChunkPayload chunk) {
        // the hash ends up in a file name, so make sure it really is just a hash
        if (!AudioHashes.isValidHash(chunk.hash())) return;
        if (chunk.totalSize() <= 0 || chunk.totalSize() > MAX_FILE_SIZE) return;

        Download download = downloads.computeIfAbsent(chunk.hash(), hash -> new Download(chunk.totalSize()));

        // ignore chunks that don't fit into the file
        if (chunk.offset() < 0 || chunk.offset() + chunk.data().length > download.data.length) return;

        System.arraycopy(chunk.data(), 0, download.data, chunk.offset(), chunk.data().length);
        download.received += chunk.data().length;

        if (download.received >= download.data.length) {
            downloads.remove(chunk.hash());
            save(chunk.hash(), download.data);
        }
    }

    // Writes a finished download into the cache folder, but only if it's actually the file we asked for.
    private static void save(String hash, byte[] data) {
        if (!AudioHashes.sha1(data).equals(hash)) {
            LOGGER.warn("Downloaded audio file {} was broken, throwing it away", hash);
            return;
        }

        try {
            Files.createDirectories(CACHE_DIR);
            Files.write(CACHE_DIR.resolve(hash + ".ogg"), data);
            LOGGER.info("Downloaded audio file {} ({} KB)", hash, data.length / 1024);
        } catch (IOException e) {
            LOGGER.error("Couldn't save downloaded audio file {}", hash, e);
        }
        trim(); // maybe the cache is too big now
    }

    // A song is being played (or watched): its downloaded files count as "used now", so they're deleted last.
    public static void markUsed(Iterable<String> hashes) {
        for (String hash : hashes) {
            if (!AudioHashes.isValidHash(hash)) continue;
            Path cached = CACHE_DIR.resolve(hash + ".ogg");
            try {
                if (Files.exists(cached)) Files.setLastModifiedTime(cached, FileTime.fromMillis(System.currentTimeMillis()));
            } catch (IOException e) {
                LOGGER.warn("Couldn't update downloaded audio file {}", hash, e);
            }
        }
    }

    // Deletes the songs played the longest ago until the cache fits into the limit (FnfOptions.cacheLimitMB, 0 = no limit).
    public static void trim() {
        long limit = FnfOptions.cacheLimitMB * 1024L * 1024L;
        if (limit <= 0 || !Files.isDirectory(CACHE_DIR)) return;

        record CachedFile(Path path, long size, long lastUsed) {}
        List<CachedFile> files = new ArrayList<>();
        long total = 0;
        try (var list = Files.list(CACHE_DIR)) {
            for (Path file : (Iterable<Path>) list::iterator) {
                if (!isOurFile(file)) continue;
                CachedFile cached = new CachedFile(file, Files.size(file), Files.getLastModifiedTime(file).toMillis());
                files.add(cached);
                total += cached.size();
            }
        } catch (IOException e) {
            LOGGER.warn("Couldn't check the song cache", e);
            return;
        }
        if (total <= limit) return;

        files.sort(Comparator.comparingLong(CachedFile::lastUsed)); // played the longest ago first
        long now = System.currentTimeMillis();
        int deleted = 0;
        for (CachedFile file : files) {
            if (total <= limit) break;
            if (now - file.lastUsed() < KEEP_RECENT) break; // everything after this was used even more recently
            try {
                Files.delete(file.path());
                total -= file.size();
                deleted++;
            } catch (IOException e) {
                LOGGER.warn("Couldn't delete {} from the song cache", file.path().getFileName(), e);
            }
        }
        if (deleted > 0) LOGGER.info("Song cache was over its limit, deleted {} songs played the longest ago", deleted);
    }

    // only our own files (<hash>.ogg), nothing else that might have ended up in the folder
    private static boolean isOurFile(Path file) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file) && name.endsWith(".ogg") && AudioHashes.isValidHash(name.substring(0, name.length() - 4));
    }

    // How far the downloads are, from 0 to 1. Used for the "Downloading song..." text.
    public static float progress() {
        long received = 0;
        long total = 0;
        for (Download download : downloads.values()) {
            received += download.received;
            total += download.data.length;
        }
        return total == 0 ? 0 : (float) received / total;
    }

    // forget unfinished downloads (when leaving a server)
    public static void cancelAll() {
        downloads.clear();
    }

    // How much space the downloaded songs take, in bytes (for the options screen)
    public static long cacheSize() {
        if (!Files.isDirectory(CACHE_DIR)) return 0;
        long size = 0;
        try (var files = Files.list(CACHE_DIR)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (Files.isRegularFile(file)) size += Files.size(file);
            }
        } catch (IOException e) {
            LOGGER.warn("Couldn't measure the song cache", e);
        }
        return size;
    }

    // Deletes every downloaded song. Songs that are playing keep playing, they're already loaded into memory.
    // Returns how many files were deleted.
    public static int clear() {
        if (!Files.isDirectory(CACHE_DIR)) return 0;
        int deleted = 0;
        try (var files = Files.list(CACHE_DIR)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (isOurFile(file)) {
                    Files.delete(file);
                    deleted++;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Couldn't clear the song cache", e);
        }
        return deleted;
    }
}
