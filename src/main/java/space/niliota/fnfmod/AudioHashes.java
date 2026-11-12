package space.niliota.fnfmod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

// Keeps track of audio files from fnfpacks/ by their SHA-1 hash (a "fingerprint" of the file's contents).
// Clients only ever see the hash, never where the file is on the server.
//  * The server uses it to know which files it's allowed to send (only ones that got hashed here, so only fnfpacks/ files).
//  * The client uses it to check if it already has the file locally (in singleplayer the client and the server
//    run in the same game, so they share this class and the song can be played straight from fnfpacks/).
// Files are hashed only when a song gets requested, hashing every pack at startup would take forever.
public class AudioHashes {
    private static final Map<Path, String> hashByPath = new HashMap<>();
    private static final Map<String, Path> pathByHash = new HashMap<>();

    // synchronized because in singleplayer the server thread writes and the client thread reads.
    public static synchronized String hashOf(Path file) throws IOException {
        String hash = hashByPath.get(file);
        if (hash != null) return hash;

        hash = sha1(file);
        hashByPath.put(file, hash);
        pathByHash.put(hash, file);
        return hash;
    }

    // Returns the file with this hash, or null if it never got hashed here.
    public static synchronized Path findFile(String hash) {
        return pathByHash.get(hash);
    }

    // A valid hash is exactly 40 hex characters. Anything else coming over the network gets ignored.
    public static boolean isValidHash(String hash) {
        return hash != null && hash.matches("[0-9a-f]{40}");
    }

    public static String sha1(byte[] data) {
        return HexFormat.of().formatHex(newDigest().digest(data));
    }

    private static String sha1(Path file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // every Java install has SHA-1, this won't happen
        }
    }
}
