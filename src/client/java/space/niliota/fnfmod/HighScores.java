package space.niliota.fnfmod;

import net.fabricmc.loader.api.FabricLoader;
import org.json.JSONObject;
import space.niliota.fnfmod.Judging.PlayStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// The player's personal bests, one per chart (pack + song + chart file), saved in config/fnfmod-scores.json.
// Every finished song counts (No fail and VS too), only a game over doesn't. The best is the highest score.
public class HighScores {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fnfmod-scores.json");

    public record Best(int score, double accuracy, int misses, int maxCombo, String rating, String fullCombo) {}

    private static final Map<String, Best> bests = new HashMap<>();

    public static String key(String packName, String songName, String chartFile) {
        return packName + "|" + songName + "|" + chartFile;
    }

    // the best for one chart, or null if it was never finished
    public static Best best(String packName, String songName, String chartFile) {
        return bests.get(key(packName, songName, chartFile));
    }

    // the best of all of a song's charts (for the song list), or null
    public static Best bestOfSong(String packName, String songName) {
        String prefix = packName + "|" + songName + "|";
        Best best = null;
        for (Map.Entry<String, Best> entry : bests.entrySet()) {
            if (entry.getKey().startsWith(prefix) && (best == null || entry.getValue().score() > best.score())) best = entry.getValue();
        }
        return best;
    }

    // A song was finished. Saves it if it beat the best, returns true if it did.
    public static boolean record(String packName, String songName, String chartFile, PlayStats stats) {
        if (packName.isEmpty() && songName.isEmpty()) return false; // don't know which song it was
        String key = key(packName, songName, chartFile);
        Best old = bests.get(key);
        if (old != null && stats.score <= old.score()) return false;

        bests.put(key, new Best(stats.score, stats.accuracy(), stats.misses, stats.maxCombo, stats.ratingName(), stats.fullComboLabel()));
        save();
        return true;
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            JSONObject json = new JSONObject(Files.readString(FILE));
            for (String key : json.keySet()) {
                JSONObject b = json.optJSONObject(key);
                if (b == null) continue;
                bests.put(key, new Best(b.optInt("score"), b.optDouble("accuracy", 0), b.optInt("misses"),
                        b.optInt("maxCombo"), b.optString("rating", "?"), b.optString("fullCombo", "")));
            }
        } catch (Exception e) {
            LOGGER.warn("Couldn't read FNFmod's high scores", e);
        }
    }

    private static void save() {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Best> entry : bests.entrySet()) {
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
