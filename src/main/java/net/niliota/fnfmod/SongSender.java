package net.niliota.fnfmod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.niliota.fnfmod.Charting.ChartNote;
import net.niliota.fnfmod.Charting.ChartParser;
import net.niliota.fnfmod.Charting.ChartTiming;
import net.niliota.fnfmod.network.AudioChunkPayload;
import net.niliota.fnfmod.network.AudioRequestPayload;
import net.niliota.fnfmod.network.RequestSongPayload;
import net.niliota.fnfmod.network.SongCatalogPayload;
import net.niliota.fnfmod.network.SongDataPayload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.niliota.fnfmod.FNFmod.LOGGER;

// The server side of playing songs. (In singleplayer this runs on the built-in server, so it's used there too.)
//  * sends the song catalog to players when they join
//  * answers song requests with the parsed chart
//  * sends audio files to players that don't have them, a few chunks every tick so the connection doesn't get flooded
public class SongSender {
    public static final int CHUNKS_PER_TICK = 2; // per player, 2 * 256 KB * 20 ticks = ~10 MB/s
    private static final int MAX_FILES_PER_REQUEST = 16; // nobody needs more audio files than this for one song

    // audio files that are still being sent, per player
    private static final Map<UUID, Deque<Transfer>> transfers = new HashMap<>();

    private static class Transfer {
        String hash;
        byte[] data;
        int offset = 0; // how much has been sent already

        Transfer(String hash, byte[] data) {
            this.hash = hash;
            this.data = data;
        }
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendCatalog(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> transfers.remove(handler.getPlayer().getUUID()));

        ServerPlayNetworking.registerGlobalReceiver(RequestSongPayload.TYPE, (payload, context) -> onSongRequest(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(AudioRequestPayload.TYPE, (payload, context) -> onAudioRequest(context.player(), payload));

        ServerTickEvents.END_SERVER_TICK.register(SongSender::sendChunks);
    }

    public static void sendCatalog(ServerPlayer player) {
        // players without the mod can't receive this, so don't even try
        if (!ServerPlayNetworking.canSend(player, SongCatalogPayload.TYPE)) return;

        ServerPlayNetworking.send(player, SongCatalogPayload.fromPacks(PackManager.packs));
    }

    private static void onSongRequest(ServerPlayer player, RequestSongPayload request) {
        // Check everything the client sent, it could be anything.
        if (!inRange(request.packId(), PackManager.packs.size())) return;
        FNFPack pack = PackManager.packs.get(request.packId());

        if (!inRange(request.songId(), pack.songs.size())) return;
        FNFSong song = pack.songs.get(request.songId());

        if (!inRange(request.chartId(), song.chartFiles.size())) return;
        if (request.metadataId() != -1 && !inRange(request.metadataId(), song.metadataFiles.size())) return;
        if (request.instIds().size() + request.voicesIds().size() > MAX_FILES_PER_REQUEST) return;

        List<Path> audioFiles = new ArrayList<>();
        for (int id : request.instIds()) {
            if (!inRange(id, song.audioInstFiles.size())) return;
            audioFiles.add(song.audioInstFiles.get(id));
        }
        for (int id : request.voicesIds()) {
            if (!inRange(id, song.audioVoicesFiles.size())) return;
            audioFiles.add(song.audioVoicesFiles.get(id));
        }

        Path chartFile = song.chartFiles.get(request.chartId());
        Path metadataFile = request.metadataId() == -1 ? null : song.metadataFiles.get(request.metadataId());

        try {
            List<ChartNote> notes = ChartParser.parseNotes(chartFile, song.engineType);
            if (notes.size() > SongDataPayload.MAX_NOTES) {
                player.sendSystemMessage(Component.literal("This chart has too many notes (" + notes.size() + ")."));
                return;
            }

            // bpm and bpm changes, from wherever this chart's engine keeps them.
            // No metadata picked = use the one that belongs to this chart (V-Slice variations, Codename meta.json)
            ChartTiming timing = ChartTiming.read(chartFile, metadataFile);

            // speed can be in either the chart or the metadata file
            List<Path> dataFiles = new ArrayList<>();
            dataFiles.add(chartFile);
            dataFiles.add(timing.metadataFile);
            double scrollSpeed = ChartParser.getScrollSpeed(dataFiles);

            List<String> audioHashes = new ArrayList<>();
            for (Path file : audioFiles) {
                audioHashes.add(AudioHashes.hashOf(file));
            }

            ServerPlayNetworking.send(player, new SongDataPayload(song.name, timing.startBpm, scrollSpeed, notes, timing.changes, audioHashes));
        } catch (Exception e) {
            // the full error stays in the server log, the player only gets a short message (errors can contain server paths)
            LOGGER.error("Couldn't load song {} from pack {}", song.name, pack.name, e);
            player.sendSystemMessage(Component.literal("Couldn't load " + song.name + ", the chart might be in a format that isn't supported yet."));
        }
    }

    private static void onAudioRequest(ServerPlayer player, AudioRequestPayload request) {
        if (request.hashes().size() > MAX_FILES_PER_REQUEST) return;

        Deque<Transfer> queue = transfers.computeIfAbsent(player.getUUID(), uuid -> new ArrayDeque<>());
        for (String hash : request.hashes()) {
            // only files that got hashed by a song request can be sent, so only files from fnfpacks/
            Path file = AudioHashes.findFile(hash);
            if (file == null) continue;

            // don't send the same file twice if it's already on its way
            boolean alreadyQueued = queue.stream().anyMatch(t -> t.hash.equals(hash));
            if (alreadyQueued) continue;

            try {
                queue.add(new Transfer(hash, Files.readAllBytes(file)));
            } catch (IOException e) {
                LOGGER.error("Couldn't read audio file for {}", player.getName().getString(), e);
            }
        }
    }

    // Runs every server tick, sends a few chunks to everyone who's downloading something.
    private static void sendChunks(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Deque<Transfer>>> iterator = transfers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Deque<Transfer>> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Deque<Transfer> queue = entry.getValue();

            if (player == null || queue.isEmpty()) {
                iterator.remove();
                continue;
            }

            for (int i = 0; i < CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
                Transfer transfer = queue.peek();
                int end = Math.min(transfer.offset + AudioChunkPayload.CHUNK_SIZE, transfer.data.length);
                byte[] chunk = Arrays.copyOfRange(transfer.data, transfer.offset, end);

                ServerPlayNetworking.send(player, new AudioChunkPayload(transfer.hash, transfer.offset, transfer.data.length, chunk));

                transfer.offset = end;
                if (transfer.offset >= transfer.data.length) queue.poll(); // this file is done
            }
        }
    }

    private static boolean inRange(int index, int size) {
        return index >= 0 && index < size;
    }
}
