package space.niliota.fnfmod.network;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import space.niliota.fnfmod.Audio.AudioCache;
import space.niliota.fnfmod.Audio.AudioManager;
import space.niliota.fnfmod.Audio.AudioTrack;
import space.niliota.fnfmod.Charting.Chart;
import space.niliota.fnfmod.Charting.ScrollSpeeds;
import space.niliota.fnfmod.FnfOptions;
import space.niliota.fnfmod.Gameplay;
import space.niliota.fnfmod.Judging.JudgedNote;
import space.niliota.fnfmod.Judging.NoteJudge;
import space.niliota.fnfmod.Judging.Rating;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// Games being played near me (the server decides which, see SpectateManager). Their arrows are drawn floating above
// the players' heads (WorldNotes), and the closest one's music plays, quieter the further away it is.
// Each game keeps its own clock (from where the server said its music was) and follows the players' presses/hits/misses.
//
// The music is decoded in the background (a whole song takes a while, the game would freeze), and kept for a minute
// after it's not needed anymore, so walking out of range and back in continues it right away.
public class ClientSpectate {
    private static final double FULL_VOLUME_RANGE = 6, SILENT_RANGE = 24; // blocks
    private static final long HISTORY_NANOS = 500_000_000L; // events in the first half second are the ones from before I came
    private static final long KEEP_NANOS = 60_000_000_000L; // how long the music stays loaded after it's not needed

    // One game being watched. The boyfriend side is a player, the opponent side a player too in VS (null in a solo game).
    public static class Watched {
        public final SpectateStartPayload start;
        public final Chart chart;
        public final NoteJudge boyfriend, opponent;
        public final boolean[] boyfriendKeys = new boolean[4], opponentKeys = new boolean[4];
        public final double[] boyfriendLastHit = {-1e9, -1e9, -1e9, -1e9}, opponentLastHit = {-1e9, -1e9, -1e9, -1e9};
        public Rating boyfriendRating, opponentRating; // the last rating popups
        public long boyfriendRatingAt, opponentRatingAt;
        private final long musicZeroNanos;  // System.nanoTime() when the music was at 0
        private final long startedNanos;

        Watched(SpectateStartPayload start) {
            this.start = start;
            SongDataPayload song = start.song();
            chart = new Chart(song.scrollSpeed(), song.notes(), song.bpm());
            chart.bpmChanges = song.bpmChanges();
            chart.scrollSpeeds = new ScrollSpeeds(song.scrollSpeed() > 0 ? song.scrollSpeed() : 1, new ArrayList<>(song.scrollChanges()));
            boyfriend = new NoteJudge(chart.notes, true, chart.bpm, chart.bpmChanges);
            opponent = start.againstComputer() ? null : new NoteJudge(chart.notes, false, chart.bpm, chart.bpmChanges);
            startedNanos = System.nanoTime();
            musicZeroNanos = startedNanos - start.musicPosition() * 1_000_000L;
        }

        // where the song is now (ms), like Gameplay.songPosition
        public double songPosition() {
            return (System.nanoTime() - musicZeroNanos) / 1_000_000.0 - FnfOptions.audioOffset;
        }

        double musicPosition() {
            return (System.nanoTime() - musicZeroNanos) / 1_000_000.0;
        }
    }

    private static final Map<Integer, Watched> games = new HashMap<>();

    // the music of the closest game
    private static List<String> audioHashes = null; // the song the tracks are (or are being) loaded for
    private static int audioGame = -1;              // the game the music is playing for
    private static boolean waitingForAudio = false; // still downloading
    private static CompletableFuture<List<AudioTrack.Decoded>> decoding = null; // decoding in the background
    private static List<AudioTrack> tracks = new ArrayList<>();
    private static boolean loaded = false;          // tracks are ready to play
    private static boolean audioPlaying = false;
    private static long unusedSince = -1;           // System.nanoTime() since nothing needs the music (-1 = in use)

    public static Collection<Watched> games() {
        return games.values();
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SpectateStartPayload.TYPE, (payload, context) -> {
            if (!FnfOptions.watchNearby) return;
            games.put(payload.gameId(), new Watched(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(SpectateEventsPayload.TYPE, (payload, context) -> onEvents(payload));
        // the music pauses on the next tick (and stays loaded for a bit, the player might come back)
        ClientPlayNetworking.registerGlobalReceiver(SpectateStopPayload.TYPE, (payload, context) -> games.remove(payload.gameId()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientSpectate::stop));
        ClientTickEvents.END_CLIENT_TICK.register(ClientSpectate::tickAudio);
    }

    // a player pressed/hit/missed: their side follows it
    private static void onEvents(SpectateEventsPayload events) {
        Watched game = games.get(events.gameId());
        if (game == null) return;
        NoteJudge side = events.boyfriendSide() ? game.boyfriend : game.opponent;
        if (side == null) return;
        boolean[] keys = events.boyfriendSide() ? game.boyfriendKeys : game.opponentKeys;
        double[] lastHit = events.boyfriendSide() ? game.boyfriendLastHit : game.opponentLastHit;
        boolean popups = System.nanoTime() - game.startedNanos > HISTORY_NANOS; // no popups for what happened before I came

        for (VsEvent event : events.events()) {
            switch (event.type()) {
                case VsEvent.KEY_DOWN, VsEvent.KEY_UP -> {
                    if (event.value() >= 0 && event.value() < 4) keys[event.value()] = event.type() == VsEvent.KEY_DOWN;
                }
                case VsEvent.HIT -> {
                    if (event.extra() < 0 || event.extra() >= Rating.values().length) continue;
                    JudgedNote hit = side.applyHit(event.value(), Rating.values()[event.extra()]);
                    if (hit == null || !popups) continue;
                    lastHit[hit.note.lane] = game.songPosition(); // the strum glows
                    if (events.boyfriendSide()) {
                        game.boyfriendRating = hit.rating;
                        game.boyfriendRatingAt = System.currentTimeMillis();
                    } else {
                        game.opponentRating = hit.rating;
                        game.opponentRatingAt = System.currentTimeMillis();
                    }
                }
                case VsEvent.MISS -> side.applyMiss(event.value());
                case VsEvent.HOLD_DROPPED -> side.applyHoldDropped(event.value());
            }
        }
    }

    // ---- the music of the closest game ----

    private static void tickAudio(Minecraft client) {
        if (client.player == null || client.level == null) return;
        if (Gameplay.running) { // my own song plays: free the music
            forgetAudio();
            return;
        }

        // the closest game, and how far away its closest player is
        Watched closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Watched game : games.values()) {
            for (UUID id : new UUID[] {game.start.boyfriendId(), game.start.opponentId()}) {
                Player player = client.level.getPlayerByUUID(id);
                if (player == null) continue;
                double distance = player.distanceTo(client.player);
                if (distance < closestDistance) {
                    closest = game;
                    closestDistance = distance;
                }
            }
        }
        if (closest == null || closestDistance > SILENT_RANGE) {
            // nothing to hear: pause, and free the music if nothing needed it for a while
            pauseAudio();
            if (unusedSince != -1 && System.nanoTime() - unusedSince > KEEP_NANOS) forgetAudio();
            return;
        }

        List<String> hashes = closest.start.song().audioHashes();
        if (!hashes.equals(audioHashes)) { // a different song: load it
            forgetAudio();
            audioHashes = hashes;
            requestAudio(closest);
        }
        if (closest.start.gameId() != audioGame) { // same song, but maybe another game (or the same one again): sync up
            pauseAudio();
            audioGame = closest.start.gameId();
        }
        unusedSince = -1;
        if (waitingForAudio || !loadTracks()) return;

        // start (again) where the game's music is now
        if (!audioPlaying && closest.musicPosition() >= 0) {
            for (AudioTrack track : tracks) track.setPositionMs((long) closest.musicPosition());
            AudioManager.playAll(tracks);
            audioPlaying = true;
        }

        double volume = 1 - (closestDistance - FULL_VOLUME_RANGE) / (SILENT_RANGE - FULL_VOLUME_RANGE);
        for (AudioTrack track : tracks) track.setGain((float) (Math.max(0, Math.min(1, volume)) * FnfOptions.songVolume));
    }

    private static void requestAudio(Watched game) {
        List<String> missing = new ArrayList<>();
        for (String hash : game.start.song().audioHashes()) {
            if (AudioCache.find(hash) == null && !missing.contains(hash) && !AudioCache.isDownloading(hash)) missing.add(hash);
        }
        waitingForAudio = !allAudioHere();
        if (!missing.isEmpty()) ClientPlayNetworking.send(new AudioRequestPayload(missing));
    }

    // ClientNetworking: an audio chunk came in, maybe the music is all here now
    public static void onAudioChunk() {
        if (waitingForAudio && allAudioHere()) waitingForAudio = false;
    }

    private static boolean allAudioHere() {
        if (audioHashes == null) return false;
        for (String hash : audioHashes) {
            if (AudioCache.find(hash) == null) return false;
        }
        return true;
    }

    // true once the tracks are ready. Decoding happens in the background, only handing it to OpenAL happens here.
    private static boolean loadTracks() {
        if (loaded) return true;
        if (decoding == null) {
            List<Path> files = new ArrayList<>();
            for (String hash : audioHashes) files.add(AudioCache.find(hash));
            decoding = CompletableFuture.supplyAsync(() -> {
                List<AudioTrack.Decoded> decoded = new ArrayList<>();
                for (Path file : files) decoded.add(file == null ? null : AudioTrack.decode(file));
                return decoded;
            }, Util.backgroundExecutor()).exceptionally(error -> List.of()); // something went wrong: no music, but no crash
            return false;
        }
        if (!decoding.isDone()) return false;

        List<AudioTrack.Decoded> decoded = decoding.join();
        decoding = null;
        for (AudioTrack.Decoded sound : decoded) {
            AudioTrack track = new AudioTrack();
            track.upload(sound);
            if (track.isLoaded()) tracks.add(track); // files that couldn't be decoded are left out
        }
        loaded = true;
        audioPlaying = false;
        return true;
    }

    private static void pauseAudio() {
        if (audioPlaying) for (AudioTrack track : tracks) track.pause();
        audioPlaying = false;
        audioGame = -1;
        if (unusedSince == -1 && audioHashes != null) unusedSince = System.nanoTime();
    }

    // free the music completely
    private static void forgetAudio() {
        for (AudioTrack track : tracks) track.destroy();
        tracks = new ArrayList<>();
        // still decoding: free it once it's done, nobody needs it anymore
        if (decoding != null) decoding.thenAccept(decoded -> decoded.forEach(sound -> { if (sound != null) sound.free(); }));
        decoding = null;
        audioHashes = null;
        audioGame = -1;
        waitingForAudio = false;
        loaded = false;
        audioPlaying = false;
        unusedSince = -1;
    }

    // forget every game (leaving the server, or the option was turned off)
    public static void stop() {
        forgetAudio();
        games.clear();
    }
}
