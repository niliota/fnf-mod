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
import net.niliota.fnfmod.Judging.PlayStats;
import net.niliota.fnfmod.Screens.FnfOptionsScreen;
import net.niliota.fnfmod.Screens.PackSelectScreen;
import net.niliota.fnfmod.Screens.SongOptionsScreen;
import net.niliota.fnfmod.Screens.SongSelectScreen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

// The client side of a VS match (the server side is VsManager).
// Keeps track of the match, holds the loaded song until both players are ready, and sends the live score.
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

    public static boolean inMatch() {
        return state != NONE;
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(VsMatchPayload.TYPE, (payload, context) -> onMatch(payload));
        ClientPlayNetworking.registerGlobalReceiver(VsRivalScorePayload.TYPE, (payload, context) -> rivalScore = payload.score());

        // live score, 4 times a second
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

    public static void sendScore(int status) {
        if (Gameplay.judge == null || !ClientPlayNetworking.canSend(VsScorePayload.TYPE)) return;
        PlayStats stats = Gameplay.judge.stats;
        ClientPlayNetworking.send(new VsScorePayload(new VsScore(stats.score, stats.misses, stats.accuracy(), stats.maxCombo, status)));
    }

    private static void reset() {
        state = NONE;
        rivalScore = null;
        preparedChart = null;
        preparedTracks = null;
    }
}
