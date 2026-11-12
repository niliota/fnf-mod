package net.niliota.fnfmod.network;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.niliota.fnfmod.Audio.AudioManager;
import net.niliota.fnfmod.Audio.AudioTrack;
import net.niliota.fnfmod.Charting.Chart;
import net.niliota.fnfmod.Gameplay;
import net.niliota.fnfmod.Judging.JudgedNote;
import net.niliota.fnfmod.Judging.NoteJudge;
import net.niliota.fnfmod.Judging.PlayStats;
import net.niliota.fnfmod.Judging.Rating;
import net.niliota.fnfmod.Screens.FnfOptionsScreen;
import net.niliota.fnfmod.Screens.PackSelectScreen;
import net.niliota.fnfmod.Screens.SongOptionsScreen;
import net.niliota.fnfmod.Screens.SongSelectScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// The client side of a VS match (the server side is VsManager).
// Keeps track of the match, holds the loaded song until both players are ready, sends my score and my presses/hits/misses,
// and applies the rival's ones to their side (Gameplay.opponentJudge), so their real game shows on the opponent strums.
public class ClientVs {
    public static final int NONE = -1;
    public static int state = NONE;          // NONE or one of VsMatchPayload's states
    public static String rivalName = "";
    public static UUID rivalId;
    public static boolean iAmBoyfriend = true;
    public static VsScore rivalScore;        // the rival's latest live score
    public static boolean pickBoyfriend = true; // the side picked on the song screen (when picking the song)

    // the song, loaded and waiting for the server's GO
    private static Chart preparedChart;
    private static List<AudioTrack> preparedTracks;

    private static int ticksSinceScore = 0;

    // my presses/hits/misses since the last tick, sent once per tick
    private static final List<VsEvent> outgoing = new ArrayList<>();
    // which of the rival's keys are down (their strums look pressed)
    public static boolean[] rivalKeysDown = new boolean[4];

    public static boolean inMatch() {
        return state != NONE;
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(VsMatchPayload.TYPE, (payload, context) -> onMatch(payload));
        ClientPlayNetworking.registerGlobalReceiver(VsRivalScorePayload.TYPE, (payload, context) -> rivalScore = payload.score());
        ClientPlayNetworking.registerGlobalReceiver(VsRivalEventsPayload.TYPE, (payload, context) -> onRivalEvents(payload.events()));

        // my events, once per tick (in pieces if there are a lot)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (outgoing.isEmpty()) return;
            if (ClientPlayNetworking.canSend(VsEventsPayload.TYPE)) {
                for (int start = 0; start < outgoing.size(); start += VsEvent.MAX_PER_PACKET) {
                    List<VsEvent> part = outgoing.subList(start, Math.min(outgoing.size(), start + VsEvent.MAX_PER_PACKET));
                    ClientPlayNetworking.send(new VsEventsPayload(new ArrayList<>(part)));
                }
            }
            outgoing.clear();
        });

        // score for the server's result, 4 times a second
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!Gameplay.running || !Gameplay.vs || !Gameplay.musicStarted) return;
            if (++ticksSinceScore >= 5) {
                ticksSinceScore = 0;
                sendScore(VsScore.PLAYING);
            }
        });

        // "waiting for the rival" while the song is loaded but the other player isn't ready yet
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (state != VsMatchPayload.LOADING) return;
            String text = preparedChart == null ? "VS: loading the song..." : "VS: waiting for " + rivalName + "...";
            context.drawCenteredString(Minecraft.getInstance().font, text,
                    Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2, 30, 0xFF55FFFF);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientVs::reset));
    }

    private static void onMatch(VsMatchPayload match) {
        Minecraft minecraft = Minecraft.getInstance();
        rivalName = match.rivalName();
        rivalId = match.rivalId();

        switch (match.state()) {
            case VsMatchPayload.PICKING -> {
                state = VsMatchPayload.PICKING;
                rivalScore = null;
                minecraft.setScreen(new PackSelectScreen(Component.empty()));
            }
            case VsMatchPayload.LOADING -> {
                state = VsMatchPayload.LOADING;
                iAmBoyfriend = match.youAreBoyfriend();
                preparedChart = null;
                preparedTracks = null;
                // the song was picked: close the song menus and stop anything that was playing
                if (minecraft.screen instanceof PackSelectScreen || minecraft.screen instanceof SongSelectScreen
                        || minecraft.screen instanceof SongOptionsScreen || minecraft.screen instanceof FnfOptionsScreen) {
                    minecraft.setScreen(null);
                }
                AudioManager.stopAllTracks();
                Gameplay.stopGameplay();
            }
            case VsMatchPayload.GO -> {
                state = VsMatchPayload.GO;
                iAmBoyfriend = match.youAreBoyfriend();
                if (preparedChart != null) {
                    Gameplay.startGameplay(preparedChart, preparedTracks);
                    preparedChart = null;
                    preparedTracks = null;
                }
            }
            case VsMatchPayload.ENDED -> {
                // if the song is still going (the rival left or the match was cancelled), stop it
                if (Gameplay.running && Gameplay.vs) {
                    AudioManager.stopAllTracks();
                    Gameplay.stopGameplay();
                }
                reset();
            }
        }
    }

    // The song is downloaded and loaded (ClientNetworking): wait for the rival instead of starting.
    public static void songLoaded(Chart chart, List<AudioTrack> tracks) {
        preparedChart = chart;
        preparedTracks = tracks;
        ClientPlayNetworking.send(new VsReadyPayload());
    }

    // something happened in my game (Gameplay), the rival should see it
    public static void queue(VsEvent event) {
        if (Gameplay.vs) outgoing.add(event);
    }

    // something happened in the rival's game: show it on their side
    private static void onRivalEvents(List<VsEvent> events) {
        NoteJudge rival = Gameplay.opponentJudge;
        if (!Gameplay.running || !Gameplay.vs || rival == null) return;

        for (VsEvent event : events) {
            switch (event.type()) {
                case VsEvent.KEY_DOWN, VsEvent.KEY_UP -> {
                    if (event.value() >= 0 && event.value() < 4) rivalKeysDown[event.value()] = event.type() == VsEvent.KEY_DOWN;
                }
                case VsEvent.HIT -> {
                    if (event.extra() < 0 || event.extra() >= Rating.values().length) continue;
                    JudgedNote hit = rival.applyHit(event.value(), Rating.values()[event.extra()]);
                    if (hit != null) Gameplay.opponentLastHitTime[hit.note.lane] = Gameplay.songPosition; // their strum glows
                }
                case VsEvent.MISS -> rival.applyMiss(event.value());
                case VsEvent.HOLD_DROPPED -> rival.applyHoldDropped(event.value());
            }
        }
    }

    public static void sendScore(int status) {
        if (Gameplay.judge == null || !ClientPlayNetworking.canSend(VsScorePayload.TYPE)) return;
        PlayStats stats = Gameplay.judge.stats;
        ClientPlayNetworking.send(new VsScorePayload(new VsScore(stats.score, stats.misses, stats.accuracy(), stats.maxCombo, status)));
    }

    private static void reset() {
        state = NONE;
        outgoing.clear();
        rivalKeysDown = new boolean[4];
        rivalScore = null;
        preparedChart = null;
        preparedTracks = null;
    }
}
