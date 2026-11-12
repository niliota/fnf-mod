package net.niliota.fnfmod;

import net.fabricmc.loader.api.FabricLoader;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.niliota.fnfmod.FNFmod.LOGGER;

// The player's FNFmod settings, set once on the options screen (Mic menu -> Options) and used for every song.
// Saved in <game folder>/config/fnfmod.json, so they're still there after restarting the game.
public class FnfOptions {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fnfmod.json");

    public static final int MIN_AUDIO_OFFSET = -500, MAX_AUDIO_OFFSET = 500; // same range as Psych's "Note Offset"

    public static boolean noFail = false;       // health running out doesn't end the song
    public static boolean middleScroll = false; // player's strums in the middle of the screen
    public static boolean downscroll = false;   // strums at the bottom, notes come down from the top
    public static boolean showOpponent = false; // show the opponent's strums and notes too
    public static boolean fnfCamera = false;    // switch to the side view camera while a song plays
    public static double cameraDistance = 5.5;  // blocks, see FnfCamera
    public static int audioOffset = 0;          // ms, higher = notes come later (for when the audio reaches you late, like Psych)

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            JSONObject json = new JSONObject(Files.readString(FILE));
            noFail         = json.optBoolean("noFail", noFail);
            middleScroll   = json.optBoolean("middleScroll", middleScroll);
            downscroll     = json.optBoolean("downscroll", downscroll);
            showOpponent   = json.optBoolean("showOpponent", showOpponent);
            fnfCamera      = json.optBoolean("fnfCamera", fnfCamera);
            cameraDistance = clamp(json.optDouble("cameraDistance", cameraDistance), FnfCamera.MIN_DISTANCE, FnfCamera.MAX_DISTANCE);
            audioOffset    = (int) clamp(json.optInt("audioOffset", audioOffset), MIN_AUDIO_OFFSET, MAX_AUDIO_OFFSET);
        } catch (Exception e) {
            LOGGER.warn("Couldn't read FNFmod's options, using the defaults", e);
        }
    }

    public static void save() {
        JSONObject json = new JSONObject();
        json.put("noFail", noFail);
        json.put("middleScroll", middleScroll);
        json.put("downscroll", downscroll);
        json.put("showOpponent", showOpponent);
        json.put("fnfCamera", fnfCamera);
        json.put("cameraDistance", cameraDistance);
        json.put("audioOffset", audioOffset);
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
