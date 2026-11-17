package space.niliota.fnfmod.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import space.niliota.fnfmod.Audio.AudioCache;
import space.niliota.fnfmod.Audio.AudioManager;
import space.niliota.fnfmod.Audio.AudioTrack;
import space.niliota.fnfmod.Charting.Chart;
import space.niliota.fnfmod.Charting.ScrollSpeeds;
import space.niliota.fnfmod.ClientSongCatalog;
import space.niliota.fnfmod.Gameplay;
import space.niliota.fnfmod.SongSender;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// The client side of playing songs from a server (or from the built-in server in singleplayer).
// Receives the catalog, the chart and the audio, and starts the song once every audio file is there.
//
// On a server without the mod, nobody answers. Then my own fnfpacks/ is used instead: the menus list my packs
// (ClientSongCatalog.setLocal) and songs get loaded right here, the same way the server would (SongSender.buildSongData).
// The same happens on servers with the mod when "My songs" is picked in the Mic menu.
// Only I see those: nothing about them is sent to the server (no watching, no VS).
public class ClientNetworking {
    // the song we got from the server but can't start yet because audio files are still downloading
    private static SongDataPayload pendingSong;
    private static int filesToDownload = 0;
    private static RequestSongPayload lastRequest; // for Retry
    private static boolean lastRequestOwn = false; // ...and whether it was one of my own songs
    private static boolean playingOwnSong = false; // the song playing is from my own fnfpacks/ (nothing goes to the server)
    public static boolean requireMic = false;      // the server wants a Mic in the inventory to play (ServerSettingsPayload)

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SongCatalogPayload.TYPE, (payload, context) -> ClientSongCatalog.set(payload.packs()));
        ClientPlayNetworking.registerGlobalReceiver(ServerSettingsPayload.TYPE, (payload, context) -> requireMic = payload.requireMic());
        ClientPlayNetworking.registerGlobalReceiver(SongDataPayload.TYPE, (payload, context) -> onSongData(payload, false));
        ClientPlayNetworking.registerGlobalReceiver(AudioChunkPayload.TYPE, (payload, context) -> onAudioChunk(payload));

        // a server without the mod: use my own packs. (Fabric knows by now which packets the server understands.)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            if (!ClientPlayNetworking.canSend(RequestSongPayload.TYPE)) ClientSongCatalog.setLocal();
        }));

        // forget everything about the server when leaving it (or a singleplayer world), and stop the song.
        // OpenAL has to be used from the game's main thread, so the stopping is handed over to it.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            AudioManager.stopAllTracks();
            Gameplay.stopGameplay(); // also puts the FNF camera back
            ClientSongCatalog.clear();
            AudioCache.cancelAll();
            pendingSong = null;
            lastRequest = null; // that server's songs are gone
            playingOwnSong = false;
            requireMic = false;
        }));

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
        requestSong(request, ClientSongCatalog.showingOwn());
    }

    private static void requestSong(RequestSongPayload request, boolean own) {
        if (own) {
            // my own songs: load it from my own fnfpacks/, the server isn't asked
            lastRequest = request;
            lastRequestOwn = true;
            SongDataPayload song = SongSender.buildSongData(request,
                    message -> Minecraft.getInstance().player.displayClientMessage(Component.literal(message), false));
            if (song != null) onSongData(song, true);
        } else if (ClientPlayNetworking.canSend(RequestSongPayload.TYPE)) {
            lastRequest = request;
            lastRequestOwn = false;
            ClientPlayNetworking.send(request);
        }
    }

    // the song playing is from my own fnfpacks/ (singleplayer counts as the server's, it's the same list there)
    public static boolean playingOwnSong() {
        return playingOwnSong;
    }

    // tell the server I started/stopped playing a song (spectating, see SpectateManager)
    public static void sendPlaying(boolean playing, int countdownMs) {
        if (Minecraft.getInstance().getConnection() == null) return; // already left the server (cleaning up after leaving)
        if (playingOwnSong) return; // my own songs stay private
        if (ClientPlayNetworking.canSend(PlayingPayload.TYPE)) ClientPlayNetworking.send(new PlayingPayload(playing, countdownMs));
    }

    // Retry on the results screen: the same song with the same files again
    public static boolean canRetry() {
        return lastRequest != null && (lastRequestOwn || ClientPlayNetworking.canSend(RequestSongPayload.TYPE));
    }

    public static void retryLastSong() {
        if (canRetry()) requestSong(lastRequest, lastRequestOwn); // from the same place as last time
    }

    private static void onSongData(SongDataPayload song, boolean own) {
        playingOwnSong = own;
        // stop whatever was playing before (and forget the games played nearby, I'm playing myself now)
        ClientSpectate.stop();
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
        ClientSpectate.onAudioChunk(); // maybe the game being watched has all its audio now

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
        AudioCache.markUsed(song.audioHashes()); // played just now, so it's the last to go when the cache is full
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

        Chart chart = new Chart(song.scrollSpeed(), song.notes(), song.bpm());
        chart.bpmChanges = song.bpmChanges();
        chart.songName = song.songName();
        chart.packName = song.packName();
        chart.chartFile = song.chartFile();
        // charts without a speed get 1, so the notes still move
        chart.scrollSpeeds = new ScrollSpeeds(song.scrollSpeed() > 0 ? song.scrollSpeed() : 1, new ArrayList<>(song.scrollChanges()));
        LOGGER.info("Starting {} ({} notes, bpm {}, speed {})", song.songName(), song.notes().size(), song.bpm(), song.scrollSpeed());
        // the tracks start playing when the countdown ends (see Gameplay).
        // In VS the song waits until the rival has it loaded too, the server says when to start (ClientVs).
        if (ClientVs.state == VsMatchPayload.LOADING) ClientVs.songLoaded(chart, allTracks);
        else Gameplay.startGameplay(chart, allTracks);
    }
}
