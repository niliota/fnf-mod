package space.niliota.fnfmod;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.json.JSONObject;
import space.niliota.fnfmod.Judging.PlayStats;
import space.niliota.fnfmod.network.PersonalBest;
import space.niliota.fnfmod.network.PersonalBestPayload;
import space.niliota.fnfmod.network.PersonalBestsPayload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// The player's personal bests, one per chart (pack + song + chart file).
// Every finished song counts (No fail and VS too), only a game over doesn't. The best is the highest score.
//
// There are two lists ("profiles"):
//  * my own: singleplayer (every world shares it) and "My songs" on servers. Saved in config/fnfmod-scores.json.
//  * the server's: songs from a server that keeps its players' bests (ServerBests). It sends them when joining,
//    and gets new ones from here. Servers that don't keep them (older versions, LAN) use my own list.
public class HighScores {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fnfmod-scores.json");

    public record Best(int score, double accuracy, int misses, int maxCombo, String rating, String fullCombo) {}

    private static final Map<String, Best> own = new HashMap<>();
    private static final Map<String, Best> server = new HashMap<>();
    private static boolean serverKeepsBests = false;

    public static void init() {
        load();
        ClientPlayNetworking.registerGlobalReceiver(PersonalBestsPayload.TYPE, (payload, context) -> {
            server.clear();
            for (PersonalBest b : payload.bests()) {
                server.put(key(b.pack(), b.song(), b.chart()), new Best(b.score(), b.accuracy(), b.misses(), b.maxCombo(), b.rating(), b.fullCombo()));
            }
            serverKeepsBests = true;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            server.clear();
            serverKeepsBests = false;
        }));
    }

    public static String key(String packName, String songName, String chartFile) {
        return packName + "|" + songName + "|" + chartFile;
    }

    // the list for songs from my own fnfpacks (ownSongs) or from the server
    private static Map<String, Best> profile(boolean ownSongs) {
        return ownSongs || !serverKeepsBests ? own : server;
    }

    // the list for what the menus show right now
    private static Map<String, Best> shown() {
        return profile(ClientSongCatalog.showingOwn());
    }

    // the best for one chart, or null if it was never finished
    public static Best best(String packName, String songName, String chartFile) {
        return shown().get(key(packName, songName, chartFile));
    }

    public static Best best(String packName, String songName, String chartFile, boolean ownSongs) {
        return profile(ownSongs).get(key(packName, songName, chartFile));
    }

    // the best of all of a song's charts (for the song list), or null
    public static Best bestOfSong(String packName, String songName) {
        String prefix = packName + "|" + songName + "|";
        Best best = null;
        for (Map.Entry<String, Best> entry : shown().entrySet()) {
            if (entry.getKey().startsWith(prefix) && (best == null || entry.getValue().score() > best.score())) best = entry.getValue();
        }
        return best;
    }

    // A song was finished. Saves it if it beat the best, returns true if it did.
    // ownSongs = it came from my own fnfpacks (singleplayer or "My songs"), otherwise from the server.
    public static boolean record(String packName, String songName, String chartFile, PlayStats stats, boolean ownSongs) {
        if (packName.isEmpty() && songName.isEmpty()) return false; // don't know which song it was
        Map<String, Best> profile = profile(ownSongs);
        String key = key(packName, songName, chartFile);
        Best old = profile.get(key);
        if (old != null && stats.score <= old.score()) return false;

        Best best = new Best(stats.score, stats.accuracy(), stats.misses, stats.maxCombo, stats.ratingName(), stats.fullComboLabel());
        profile.put(key, best);
        if (profile == own) {
            save();
        } else if (ClientPlayNetworking.canSend(PersonalBestPayload.TYPE)) {
            // the server keeps it
            ClientPlayNetworking.send(new PersonalBestPayload(new PersonalBest(packName, songName, chartFile,
                    best.score(), best.accuracy(), best.misses(), best.maxCombo(), best.rating(), best.fullCombo())));
        }
        return true;
    }

    private static void load() {
        if (!Files.exists(FILE)) return;
        try {
            JSONObject json = new JSONObject(Files.readString(FILE));
            for (String key : json.keySet()) {
                JSONObject b = json.optJSONObject(key);
                if (b == null) continue;
                own.put(key, new Best(b.optInt("score"), b.optDouble("accuracy", 0), b.optInt("misses"),
                        b.optInt("maxCombo"), b.optString("rating", "?"), b.optString("fullCombo", "")));
            }
        } catch (Exception e) {
            LOGGER.warn("Couldn't read FNFmod's high scores", e);
        }
    }

    private static void save() {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Best> entry : own.entrySet()) {
            Best b = entry.getValue();
            json.put(entry.getKey(), new JSONObject()
                    .put("score", b.score()).put("accuracy", b.accuracy()).put("misses", b.misses())
                    .put("maxCombo", b.maxCombo()).put("rating", b.rating()).put("fullCombo", b.fullCombo()));
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json.toString(2));
        } catch (IOException e) {
            LOGGER.warn("Couldn't save FNFmod's high scores", e);
        }
    }
}
