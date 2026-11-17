package space.niliota.fnfmod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.json.JSONObject;
import space.niliota.fnfmod.network.PersonalBest;
import space.niliota.fnfmod.network.PersonalBestPayload;
import space.niliota.fnfmod.network.PersonalBestsPayload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// Personal bests for a server's songs, kept by the server (in the world folder, fnfmod-scores.json), one list per player.
// Players get theirs when joining, and send a new one when they beat it. Songs from their own fnfpacks ("My songs")
// and singleplayer stay on the player's computer instead (HighScores on the client).
//
// Only dedicated servers keep them. In singleplayer (and LAN) every world shares the player's own list.
// The scores come from the player's game, like everything about judging, so a modified client could send anything.
// The server at least checks the chart really is one of its songs.
public class ServerBests {
    private static final Map<UUID, Map<String, PersonalBest>> bests = new HashMap<>();
    private static Path file; // null = this server doesn't keep bests (not a dedicated server)

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(ServerBests::load);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            bests.clear();
            file = null;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (file == null || !ServerPlayNetworking.canSend(player, PersonalBestsPayload.TYPE)) return;
            Map<String, PersonalBest> mine = bests.getOrDefault(player.getUUID(), Map.of());
            ServerPlayNetworking.send(player, new PersonalBestsPayload(new ArrayList<>(mine.values())));
        });

        ServerPlayNetworking.registerGlobalReceiver(PersonalBestPayload.TYPE, (payload, context) -> onBest(context.player(), payload.best()));
    }

    private static void onBest(ServerPlayer player, PersonalBest best) {
        if (file == null || !isServerChart(best)) return;
        Map<String, PersonalBest> mine = bests.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>());

        String key = best.pack() + "|" + best.song() + "|" + best.chart();
        PersonalBest old = mine.get(key);
        if (old != null && best.score() <= old.score()) return;
        if (old == null && mine.size() >= PersonalBestsPayload.MAX_BESTS) return;

        mine.put(key, best);
        save();
    }

    // true if the pack, song and chart file exist on this server
    private static boolean isServerChart(PersonalBest best) {
        for (FNFPack pack : PackManager.packs) {
            if (!pack.name.equals(best.pack())) continue;
            for (FNFSong song : pack.songs) {
                if (!song.name.equals(best.song())) continue;
                for (Path chart : song.chartFiles) {
                    if (chart.getFileName().toString().equals(best.chart())) return true;
                }
            }
        }
        return false;
    }

    private static void load(MinecraftServer server) {
        bests.clear();
        if (!server.isDedicatedServer()) {
            file = null;
            return;
        }
        file = server.getWorldPath(LevelResource.ROOT).resolve("fnfmod-scores.json");
        if (!Files.exists(file)) return;

        try {
            JSONObject json = new JSONObject(Files.readString(file));
            for (String uuid : json.keySet()) {
                JSONObject player = json.optJSONObject(uuid);
                if (player == null) continue;
                Map<String, PersonalBest> mine = new HashMap<>();
                for (String key : player.keySet()) {
                    JSONObject b = player.optJSONObject(key);
                    if (b == null) continue;
                    mine.put(key, new PersonalBest(b.optString("pack"), b.optString("song"), b.optString("chart"),
                            b.optInt("score"), b.optDouble("accuracy", 0), b.optInt("misses"), b.optInt("maxCombo"),
                            b.optString("rating", "?"), b.optString("fullCombo", "")));
                }
                bests.put(UUID.fromString(uuid), mine);
            }
        } catch (Exception e) {
            LOGGER.warn("Couldn't read the players' personal bests ({})", file.getFileName(), e);
        }
    }

    private static void save() {
        if (file == null) return;
        JSONObject json = new JSONObject();
        for (Map.Entry<UUID, Map<String, PersonalBest>> player : bests.entrySet()) {
            JSONObject mine = new JSONObject();
            for (Map.Entry<String, PersonalBest> entry : player.getValue().entrySet()) {
                PersonalBest b = entry.getValue();
                mine.put(entry.getKey(), new JSONObject()
                        .put("pack", b.pack()).put("song", b.song()).put("chart", b.chart())
                        .put("score", b.score()).put("accuracy", b.accuracy()).put("misses", b.misses())
                        .put("maxCombo", b.maxCombo()).put("rating", b.rating()).put("fullCombo", b.fullCombo()));
            }
            json.put(player.getKey().toString(), mine);
        }
        try {
            Files.writeString(file, json.toString(1));
        } catch (IOException e) {
            LOGGER.warn("Couldn't save the players' personal bests", e);
        }
    }
}
