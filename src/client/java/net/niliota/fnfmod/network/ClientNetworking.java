package net.niliota.fnfmod.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.niliota.fnfmod.Audio.AudioCache;
import net.niliota.fnfmod.Audio.AudioManager;
import net.niliota.fnfmod.Audio.AudioTrack;
import net.niliota.fnfmod.Charting.Chart;
import net.niliota.fnfmod.ClientSongCatalog;
import net.niliota.fnfmod.Gameplay;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static net.niliota.fnfmod.FNFmod.LOGGER;

// The client side of playing songs from a server (or from the built-in server in singleplayer).
// Receives the catalog, the chart and the audio, and starts the song once every audio file is there.
public class ClientNetworking {
    // the song we got from the server but can't start yet because audio files are still downloading
    private static SongDataPayload pendingSong;
    private static int filesToDownload = 0;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SongCatalogPayload.TYPE, (payload, context) -> ClientSongCatalog.set(payload.packs()));
        ClientPlayNetworking.registerGlobalReceiver(SongDataPayload.TYPE, (payload, context) -> onSongData(payload));
        ClientPlayNetworking.registerGlobalReceiver(AudioChunkPayload.TYPE, (payload, context) -> onAudioChunk(payload));

        // forget everything about the server when leaving it
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientSongCatalog.clear();
            AudioCache.cancelAll();
            pendingSong = null;
        });

        // "Downloading song..." text while waiting for audio files
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (pendingSong == null) return;

            int downloaded = filesToDownload - missingFiles(pendingSong).size();
            String text = "Downloading " + pendingSong.songName() + "... file " + Math.min(downloaded + 1, filesToDownload)
                    + "/" + filesToDownload + " (" + (int)(AudioCache.progress() * 100) + "%)";
            context.drawString(Minecraft.getInstance().font, text, 10, 10, 0xFFFFFFFF, true);
        });
    }

    // Called by SongOptionsScreen's "Start playing" button.
    public static void requestSong(RequestSongPayload request) {
        if (!ClientPlayNetworking.canSend(RequestSongPayload.TYPE)) return;
        ClientPlayNetworking.send(request);
    }

    private static void onSongData(SongDataPayload song) {
        // stop whatever was playing before
        AudioManager.stopAllTracks();
        Gameplay.stopGameplay();

        List<String> missing = missingFiles(song);
        if (missing.isEmpty()) {
            pendingSong = null;
            startSong(song);
            return;
        }

        // Ask the server for the files we don't have. The song starts in onAudioChunk() once they're all here.
        pendingSong = song;
        filesToDownload = missing.size();
        List<String> notDownloadingYet = new ArrayList<>();
        for (String hash : missing) {
            if (!AudioCache.isDownloading(hash)) notDownloadingYet.add(hash);
        }
        ClientPlayNetworking.send(new AudioRequestPayload(notDownloadingYet));
    }

    private static void onAudioChunk(AudioChunkPayload chunk) {
        AudioCache.acceptChunk(chunk);

        if (pendingSong != null && missingFiles(pendingSong).isEmpty()) {
            SongDataPayload song = pendingSong;
            pendingSong = null;
            startSong(song);
        }
    }

    // the hashes of the song's audio files that we don't have yet
    private static List<String> missingFiles(SongDataPayload song) {
        List<String> missing = new ArrayList<>();
        for (String hash : song.audioHashes()) {
            if (AudioCache.find(hash) == null && !missing.contains(hash)) missing.add(hash);
        }
        return missing;
    }

    // Everything is here, load the audio and start playing.
    private static void startSong(SongDataPayload song) {
        // close the menu, otherwise the arrow keys go to the menu instead of the game
        Minecraft.getInstance().setScreen(null);

        List<AudioTrack> allTracks = new ArrayList<>();
        for (String hash : song.audioHashes()) {
            Path file = AudioCache.find(hash);
            AudioTrack track = new AudioTrack();
            AudioManager.addTrack(track);
            track.load(file);

            // Add all tracks into a list that will then be given to AudioManager to be played at the same instant.
            allTracks.add(track);
        }

        // Play all selected audio files
        AudioManager.playAll(allTracks);

        Chart chart = new Chart(song.scrollSpeed(), song.notes(), song.bpm());
        chart.bpmChanges = song.bpmChanges();
        LOGGER.info("Starting {} ({} notes, bpm {}, speed {})", song.songName(), song.notes().size(), song.bpm(), song.scrollSpeed());
        Gameplay.startGameplay(chart);
    }
}
