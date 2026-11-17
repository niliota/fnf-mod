package space.niliota.fnfmod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.json.JSONObject;
import space.niliota.fnfmod.item.ModItems;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// Settings for server owners, in <server folder>/config/fnfmod-server.json (made with the defaults on the first start).
// Most of them limit how much sending songs to players can use of the server's upload (see SongSender).
//  uploadLimitMBps       - how fast songs get sent, in MB per second, shared by everyone downloading at the same time
//  playerLimitMB         - how much one player can download per playerLimitMinutes (normal playing never gets close)
//  playerLimitMinutes
//  requireMic            - players need a Mic in their inventory to play: the menu key (O) doesn't work without one,
//                          for survival servers where the Mic should be crafted first. Off by default.
public class ServerConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fnfmod-server.json");

    public static double uploadLimitMBps = 4;     // a song (usually 3-10 MB) takes a second or two
    public static int playerLimitMB = 500;
    public static int playerLimitMinutes = 10;
    public static boolean requireMic = false;

    public static void load() {
        if (Files.exists(FILE)) {
            try {
                JSONObject json = new JSONObject(Files.readString(FILE));
                uploadLimitMBps    = Math.max(0.1, json.optDouble("uploadLimitMBps", uploadLimitMBps));
                playerLimitMB      = Math.max(1, json.optInt("playerLimitMB", playerLimitMB));
                playerLimitMinutes = Math.max(1, json.optInt("playerLimitMinutes", playerLimitMinutes));
                requireMic         = json.optBoolean("requireMic", requireMic);
            } catch (Exception e) {
                LOGGER.warn("Couldn't read {}, using the defaults", FILE.getFileName(), e);
            }
        }
        save(); // writes the file if it's missing, and adds settings that are new since it was made
    }

    private static void save() {
        JSONObject json = new JSONObject();
        json.put("uploadLimitMBps", uploadLimitMBps);
        json.put("playerLimitMB", playerLimitMB);
        json.put("playerLimitMinutes", playerLimitMinutes);
        json.put("requireMic", requireMic);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json.toString(2));
        } catch (IOException e) {
            LOGGER.warn("Couldn't save {}", FILE.getFileName(), e);
        }
    }

    // false if the server wants a Mic and the player doesn't have one (they get told why)
    public static boolean canPlay(ServerPlayer player) {
        if (!requireMic || player.getInventory().contains(stack -> stack.is(ModItems.MIC))) return true;
        player.sendSystemMessage(Component.literal("You need a Mic to play here. Craft one from a note block on top of a stick."));
        return false;
    }

    public static long uploadBytesPerTick() {
        return (long) (uploadLimitMBps * 1024 * 1024 / 20);
    }

    public static long playerLimitBytes() {
        return playerLimitMB * 1024L * 1024L;
    }

    public static long playerLimitMillis() {
        return playerLimitMinutes * 60_000L;
    }
}
