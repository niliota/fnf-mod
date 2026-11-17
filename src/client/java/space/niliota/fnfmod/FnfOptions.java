package space.niliota.fnfmod;

import net.fabricmc.loader.api.FabricLoader;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// The player's FNFmod settings, set once on the options screen (Mic menu -> Options) and used for every song.
// Saved in <game folder>/config/fnfmod.json, so they're still there after restarting the game.
public class FnfOptions {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fnfmod.json");

    public static final int MIN_AUDIO_OFFSET = -500, MAX_AUDIO_OFFSET = 500; // same range as Psych's "Note Offset"

    public static boolean noFail = false;       // health running out doesn't end the song
    public static boolean ghostTapping = true;  // pressing with no note there is fine (off = it's a miss, like Psych's option)
    public static boolean middleScroll = false; // player's strums in the middle of the screen
    public static boolean downscroll = false;   // strums at the bottom, notes come down from the top
    public static boolean showOpponent = true;  // show the opponent's strums and notes too
    public static boolean watchNearby = true;   // arrows above players playing near me (and their music)
    public static boolean truePov = true;       // boyfriend always on the right: on the opponent side in VS, I'm on the left (strums, camera, health bar)
    public static boolean fnfCamera = true;     // switch to the side view camera while a song plays
    public static double cameraDistance = 5.5;  // blocks, see FnfCamera
    public static int audioOffset = 0;          // ms, higher = notes come later (for when the audio reaches you late, like Psych)
    public static int cacheLimitMB = 1024;      // songs downloaded from servers (fnfcache/) can take this much, 0 = no limit
    public static final int[] CACHE_LIMITS = {256, 512, 1024, 2048, 4096, 8192, 16384, 0}; // the slider's steps, the last one = no limit
    public static Set<String> ownSongsServers = new HashSet<>(); // servers where "My songs" is picked in the Mic menu (their addresses)
    public static double songVolume = 1;        // 0 to 1, every FNF song (mine and the ones nearby). Minecraft's Master slider counts too

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            JSONObject json = new JSONObject(Files.readString(FILE));
            noFail         = json.optBoolean("noFail", noFail);
            ghostTapping   = json.optBoolean("ghostTapping", ghostTapping);
            middleScroll   = json.optBoolean("middleScroll", middleScroll);
            downscroll     = json.optBoolean("downscroll", downscroll);
            showOpponent   = json.optBoolean("showOpponent", showOpponent);
            truePov        = json.optBoolean("truePov", truePov);
            watchNearby    = json.optBoolean("watchNearby", watchNearby);
            fnfCamera      = json.optBoolean("fnfCamera", fnfCamera);
            cameraDistance = clamp(json.optDouble("cameraDistance", cameraDistance), FnfCamera.MIN_DISTANCE, FnfCamera.MAX_DISTANCE);
            audioOffset    = (int) clamp(json.optInt("audioOffset", audioOffset), MIN_AUDIO_OFFSET, MAX_AUDIO_OFFSET);
            songVolume     = clamp(json.optDouble("songVolume", songVolume), 0, 1);
            cacheLimitMB   = Math.max(0, json.optInt("cacheLimitMB", cacheLimitMB));
            ownSongsServers.clear();
            JSONArray servers = json.optJSONArray("ownSongsServers");
            if (servers != null) for (int i = 0; i < servers.length(); i++) ownSongsServers.add(servers.optString(i));
        } catch (Exception e) {
            LOGGER.warn("Couldn't read FNFmod's options, using the defaults", e);
        }
    }

    public static void save() {
        JSONObject json = new JSONObject();
        json.put("noFail", noFail);
        json.put("ghostTapping", ghostTapping);
        json.put("middleScroll", middleScroll);
        json.put("downscroll", downscroll);
        json.put("showOpponent", showOpponent);
        json.put("truePov", truePov);
        json.put("watchNearby", watchNearby);
        json.put("fnfCamera", fnfCamera);
        json.put("cameraDistance", cameraDistance);
        json.put("audioOffset", audioOffset);
        json.put("songVolume", songVolume);
        json.put("cacheLimitMB", cacheLimitMB);
        json.put("ownSongsServers", new JSONArray(ownSongsServers));
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json.toString(2));
        } catch (IOException e) {
            LOGGER.warn("Couldn't save FNFmod's options", e);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
