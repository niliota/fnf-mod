package space.niliota.fnfmod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.niliota.fnfmod.Charting.ChartNote;
import space.niliota.fnfmod.Charting.ChartParser;
import space.niliota.fnfmod.Charting.ChartTiming;
import space.niliota.fnfmod.Charting.ScrollSpeeds;
import space.niliota.fnfmod.network.AudioChunkPayload;
import space.niliota.fnfmod.network.AudioRequestPayload;
import space.niliota.fnfmod.network.RequestSongPayload;
import space.niliota.fnfmod.network.ServerSettingsPayload;
import space.niliota.fnfmod.network.SongCatalogPayload;
import space.niliota.fnfmod.network.SongDataPayload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// The server side of playing songs. (In singleplayer this runs on the built-in server, so it's used there too.)
//  * sends the song catalog to players when they join
//  * answers song requests with the parsed chart
//  * sends audio files to players that don't have them, a bit every tick so the connection doesn't get flooded
//
// Sending audio is limited (ServerConfig), so a public server doesn't run out of upload or memory:
//  * everyone downloading shares uploadLimitMBps, split evenly every tick
//  * files are read piece by piece while they're sent, never held in memory whole
//  * a player can only have MAX_QUEUED_FILES waiting, and download playerLimitMB per playerLimitMinutes
public class SongSender {
    private static final int MAX_FILES_PER_REQUEST = 16; // nobody needs more audio files than this for one song
    private static final int MAX_QUEUED_FILES = 32;      // a song or two (and the one being watched), per player
    private static final int MIN_PIECE = 16 * 1024;      // even with lots of players downloading, pieces aren't smaller than this

    // audio files that are still being sent, per player
    private static final Map<UUID, Deque<Transfer>> transfers = new HashMap<>();
    // how much each player downloaded since limitStart (for playerLimitMB)
    private static final Map<UUID, Usage> usage = new HashMap<>();

    private static class Transfer {
        String hash;
        Path file;
        int size;
        int offset = 0; // how much has been sent already

        Transfer(String hash, Path file, int size) {
            this.hash = hash;
            this.file = file;
            this.size = size;
        }
    }

    private static class Usage {
        long since = System.currentTimeMillis();
        long bytes = 0;
        boolean warned = false; // the player was told they hit the limit
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendCatalog(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> transfers.remove(handler.getPlayer().getUUID()));
        // usage is kept after leaving on purpose, so rejoining doesn't reset the download limit

        ServerPlayNetworking.registerGlobalReceiver(RequestSongPayload.TYPE, (payload, context) -> onSongRequest(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(AudioRequestPayload.TYPE, (payload, context) -> onAudioRequest(context.player(), payload));

        ServerTickEvents.END_SERVER_TICK.register(SongSender::sendChunks);
    }

    public static void sendCatalog(ServerPlayer player) {
        // players without the mod can't receive this, so don't even try
        if (!ServerPlayNetworking.canSend(player, SongCatalogPayload.TYPE)) return;

        ServerPlayNetworking.send(player, SongCatalogPayload.fromPacks(PackManager.packs));
        // older versions of the mod don't know this one
        if (ServerPlayNetworking.canSend(player, ServerSettingsPayload.TYPE)) {
            ServerPlayNetworking.send(player, new ServerSettingsPayload(ServerConfig.requireMic));
        }
    }

    private static void onSongRequest(ServerPlayer player, RequestSongPayload request) {
        if (!ServerConfig.canPlay(player)) return; // checked here too, the client could skip its own check
        SongDataPayload songData = buildSongData(player, request);
        if (songData != null) {
            ServerPlayNetworking.send(player, songData);
            SpectateManager.songSent(player, songData); // if they play it, players nearby can watch
        }
    }

    // Checks the request and loads the song (chart, timing, speed changes, audio hashes).
    // Returns null if the request was invalid or the song couldn't be loaded (the player gets a message for the latter).
    // Also used by VsManager, which sends the result to both players of a match.
    public static SongDataPayload buildSongData(ServerPlayer player, RequestSongPayload request) {
        return buildSongData(request, message -> player.sendSystemMessage(Component.literal(message)));
    }

    // Same, but problems go to "tell". The client uses this one on servers without the mod, with its own fnfpacks/.
    public static SongDataPayload buildSongData(RequestSongPayload request, Consumer<String> tell) {
        // Check everything the client sent, it could be anything.
        if (!inRange(request.packId(), PackManager.packs.size())) return null;
        FNFPack pack = PackManager.packs.get(request.packId());

        if (!inRange(request.songId(), pack.songs.size())) return null;
        FNFSong song = pack.songs.get(request.songId());

        if (!inRange(request.chartId(), song.chartFiles.size())) return null;
        if (request.metadataId() != -1 && !inRange(request.metadataId(), song.metadataFiles.size())) return null;
        if (request.instIds().size() + request.voicesIds().size() > MAX_FILES_PER_REQUEST) return null;

        List<Path> audioFiles = new ArrayList<>();
        for (int id : request.instIds()) {
            if (!inRange(id, song.audioInstFiles.size())) return null;
            audioFiles.add(song.audioInstFiles.get(id));
        }
        for (int id : request.voicesIds()) {
            if (!inRange(id, song.audioVoicesFiles.size())) return null;
            audioFiles.add(song.audioVoicesFiles.get(id));
        }

        Path chartFile = song.chartFiles.get(request.chartId());
        Path metadataFile = request.metadataId() == -1 ? null : song.metadataFiles.get(request.metadataId());

        try {
            List<ChartNote> notes = ChartParser.parseNotes(chartFile, song.engineType);
            if (notes.size() > SongDataPayload.MAX_NOTES) {
                tell.accept("This chart has too many notes (" + notes.size() + ").");
                return null;
            }

            // bpm and bpm changes, from wherever this chart's engine keeps them.
            // No metadata picked = use the one that belongs to this chart (V-Slice variations, Codename meta.json)
            ChartTiming timing = ChartTiming.read(chartFile, metadataFile);

            // speed can be in either the chart or the metadata file
            List<Path> dataFiles = new ArrayList<>();
            dataFiles.add(chartFile);
            dataFiles.add(timing.metadataFile);
            double scrollSpeed = ChartParser.getScrollSpeed(dataFiles);
            // speed changes during the song, from the chart's events (and events.json)
            ScrollSpeeds scrollSpeeds = ScrollSpeeds.read(chartFile, scrollSpeed, timing.changes);

            List<String> audioHashes = new ArrayList<>();
            for (Path file : audioFiles) {
                audioHashes.add(AudioHashes.hashOf(file));
            }

            return new SongDataPayload(song.name, pack.name, chartFile.getFileName().toString(), timing.startBpm, scrollSpeed, notes, timing.changes, scrollSpeeds.changes, audioHashes);
        } catch (Exception e) {
            // the full error stays in the server log, the player only gets a short message (errors can contain server paths)
            LOGGER.error("Couldn't load song {} from pack {}", song.name, pack.name, e);
            tell.accept("Couldn't load " + song.name + ", the chart might be in a format that isn't supported yet.");
            return null;
        }
    }

    private static void onAudioRequest(ServerPlayer player, AudioRequestPayload request) {
        if (request.hashes().size() > MAX_FILES_PER_REQUEST) return;

        Deque<Transfer> queue = transfers.computeIfAbsent(player.getUUID(), uuid -> new ArrayDeque<>());
        Usage used = usage.computeIfAbsent(player.getUUID(), uuid -> new Usage());
        long now = System.currentTimeMillis();
        if (now - used.since > ServerConfig.playerLimitMillis()) { // a new period, start counting from 0
            used.since = now;
            used.bytes = 0;
            used.warned = false;
        }

        for (String hash : request.hashes()) {
            if (queue.size() >= MAX_QUEUED_FILES) return;

            // only files that got hashed by a song request can be sent, so only files from fnfpacks/
            Path file = AudioHashes.findFile(hash);
            if (file == null) continue;

            // don't send the same file twice if it's already on its way
            boolean alreadyQueued = queue.stream().anyMatch(t -> t.hash.equals(hash));
            if (alreadyQueued) continue;

            try {
                long size = Files.size(file);
                if (size > Integer.MAX_VALUE) continue;
                if (used.bytes + size > ServerConfig.playerLimitBytes()) {
                    if (!used.warned) {
                        long minutesLeft = Math.max(1, (ServerConfig.playerLimitMillis() - (now - used.since) + 59_999) / 60_000);
                        player.sendSystemMessage(Component.literal("You've downloaded a lot of songs from this server, try again in "
                                + minutesLeft + (minutesLeft == 1 ? " minute." : " minutes.")));
                        used.warned = true;
                    }
                    return;
                }
                used.bytes += size;
                queue.add(new Transfer(hash, file, (int) size));
            } catch (IOException e) {
                LOGGER.error("Couldn't read audio file for {}", player.getName().getString(), e);
            }
        }
    }

    // Runs every server tick: everyone who's downloading something gets an even share of this tick's upload.
    private static void sendChunks(MinecraftServer server) {
        transfers.entrySet().removeIf(entry -> entry.getValue().isEmpty() || server.getPlayerList().getPlayer(entry.getKey()) == null);
        if (transfers.isEmpty()) return;

        long share = Math.max(MIN_PIECE, ServerConfig.uploadBytesPerTick() / transfers.size());
        for (Map.Entry<UUID, Deque<Transfer>> entry : transfers.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Deque<Transfer> queue = entry.getValue();

            long left = share;
            while (left > 0 && !queue.isEmpty()) {
                Transfer transfer = queue.peek();
                int length = (int) Math.min(Math.min(left, AudioChunkPayload.CHUNK_SIZE), transfer.size - transfer.offset);
                byte[] chunk;
                try {
                    chunk = readPiece(transfer.file, transfer.offset, length);
                } catch (IOException e) {
                    LOGGER.error("Couldn't read audio file for {}", player.getName().getString(), e);
                    queue.poll(); // skip this file
                    continue;
                }

                ServerPlayNetworking.send(player, new AudioChunkPayload(transfer.hash, transfer.offset, transfer.size, chunk));

                transfer.offset += length;
                left -= length;
                if (transfer.offset >= transfer.size) queue.poll(); // this file is done
            }
        }
    }

    // one piece of a file, read straight from the disk
    private static byte[] readPiece(Path file, int offset, int length) throws IOException {
        byte[] piece = new byte[length];
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.wrap(piece);
            long position = offset;
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, position);
                if (read < 0) throw new IOException("The file got shorter while it was being sent");
                position += read;
            }
        }
        return piece;
    }

    private static boolean inRange(int index, int size) {
        return index >= 0 && index < size;
    }
}
