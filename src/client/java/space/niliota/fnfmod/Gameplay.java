package space.niliota.fnfmod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import space.niliota.fnfmod.Audio.AudioManager;
import space.niliota.fnfmod.Audio.AudioTrack;
import space.niliota.fnfmod.Audio.SongClock;
import space.niliota.fnfmod.Charting.Chart;
import space.niliota.fnfmod.Judging.JudgedNote;
import space.niliota.fnfmod.Judging.NoteJudge;
import space.niliota.fnfmod.network.ClientVs;
import space.niliota.fnfmod.network.VsEvent;
import space.niliota.fnfmod.network.VsMatchPayload;
import space.niliota.fnfmod.network.VsScore;

import java.util.List;

// Runs a song while it's playing: reads the arrow keys, lets NoteJudge decide hits and misses,
// and draws the notes and their hold tails scrolling up towards the strums (drawn by RenderNotes).
// The opponent's side plays itself (like in FNF) and can be shown next to the player's.
// The score, health bar and ratings are drawn by GameplayHud.
public class Gameplay {
    public static boolean running = false;
    public static Chart currentChart;
    public static NoteJudge judge;          // the side I play
    public static NoteJudge opponentJudge;  // the other side, plays by itself
    public static boolean vs = false;       // this song is a VS match (see ClientVs)
    public static boolean mySide = true;    // which chart side I play: true = boyfriend's notes, false = the opponent's (VS)
    public static double songPosition = 0;

    // Countdown before the music starts
    public static final int COUNTDOWN_BEATS = 5;   // the song's time starts this many beats before 0 (Psych: 5)
    public static boolean musicStarted = false;    // false during the countdown
    private static List<AudioTrack> tracksToStart; // loaded, waiting for the countdown to end
    private static long countdownStartNanos;
    private static int lastCountdownTick;

    // Singleplayer pause menu: the song pauses with the game
    public static boolean paused = false;
    private static long pausedAtNanos;

    // Main spritesheet
    private static final ResourceLocation noteSpriteAtlas = ResourceLocation.fromNamespaceAndPath("fnfmod", "note_assets.png");

    // Layout, shared with RenderNotes so the notes land exactly on the strums.
    // Always anchored to top-right (or top-middle with middle scroll), regardless of GUI scale. Bottom with downscroll.
    public static final int NOTE_SIZE = 32;
    public static final int OFFSET_FROM_RIGHT = 10;
    public static final int OFFSET_FROM_TOP = 5;
    public static final int OFFSET_FROM_BOTTOM = 52; // downscroll: stays above the hotbar, hearts and armor
    public static final int GAP_BETWEEN_SIDES = 16; // space between the player's and the opponent's strums

    // hold tails are thinner than notes, same proportions as on the spritesheet (hold piece is 52px wide, a note is 160px)
    private static final int HOLD_WIDTH = Math.round(NOTE_SIZE * 52f / 160f);
    private static final int HOLD_END_HEIGHT = Math.round(HOLD_WIDTH * 64f / 52f);

    // per lane: song time of the last hit, and if a hold is being held (both make the strum glow)
    public static double[] lastHitTime = new double[4];
    public static boolean[] holding = new boolean[4];
    public static double[] opponentLastHitTime = new double[4];
    public static boolean[] opponentHolding = new boolean[4];
    private static boolean[] lastHeld = new boolean[4]; // my keys last frame (VS sends when they change)

    // x of one of the player's lanes (0=left, 1=down, 2=up, 3=right)
    public static int laneX(int lane) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        if (FnfOptions.middleScroll) return screenWidth / 2 - 2 * NOTE_SIZE + lane * NOTE_SIZE;

        // With the opponent shown: centered in the right half of the screen, like Psych (its strums sit in the middle of each half)
        if (FnfOptions.showOpponent) return screenWidth * 3 / 4 - 2 * NOTE_SIZE + lane * NOTE_SIZE;

        // rightmost note (lane 3) starts at offsetFromRight, others go left from there
        return screenWidth - NOTE_SIZE - OFFSET_FROM_RIGHT - (3 - lane) * NOTE_SIZE;
    }

    // x of one of the opponent's lanes
    public static int opponentLaneX(int lane) {
        if (FnfOptions.middleScroll) {
            // split around the player's strums, 2 on each side (like Psych's middle scroll)
            if (lane < 2) return laneX(0) - GAP_BETWEEN_SIDES - (2 - lane) * NOTE_SIZE;
            return laneX(3) + NOTE_SIZE + GAP_BETWEEN_SIDES + (lane - 2) * NOTE_SIZE;
        }
        // centered in the left half of the screen, mirroring the player's strums in the right half (like Psych)
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return screenWidth / 4 - 2 * NOTE_SIZE + lane * NOTE_SIZE;
    }

    // with middle scroll the opponent's side is faded out so it doesn't get in the way (Psych does the same)
    public static float opponentAlpha() {
        return FnfOptions.middleScroll ? 0.35f : 1f;
    }

    public static int strumY() {
        if (FnfOptions.downscroll) {
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            return screenHeight - OFFSET_FROM_BOTTOM - NOTE_SIZE;
        }
        return OFFSET_FROM_TOP;
    }

    public static void init() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            // Stop running the main cycle if set to not running
            if (!running || judge == null) return;
            if (AudioManager.tracks.isEmpty()) return; // nothing to sync the notes to

            checkPause();

            // while paused everything stays where it is (the notes are still drawn)
            if (!paused) {
                // Used to sync the note time with the song's (counts down before the music starts)
                updateSongPosition();

                handleInput();
                playOpponent();

                if (judge.stats.isDead() && !FnfOptions.noFail && !vs) { // no game over in VS, it goes by score
                    gameOver();
                    return;
                }
            }

            // Fix transparency
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            if (FnfOptions.showOpponent) {
                opponentHolding = drawNotes(context, opponentJudge, true, opponentAlpha());
            }
            holding = drawNotes(context, judge, false, 1f);

            context.setColor(1f, 1f, 1f, 1f);
            // Unfix transparency
            RenderSystem.disableBlend();
        });
    }

    // Presses go to the judge one by one (consumeClick counts every press, even if two happen in one frame),
    // holding is checked every frame.
    private static void handleInput() {
        KeyMapping[] keys = laneKeys();
        boolean[] held = new boolean[4];

        for (int lane = 0; lane < 4; lane++) {
            while (keys[lane].consumeClick()) {
                JudgedNote hit = judge.press(lane, songPosition);
                if (hit != null) {
                    lastHitTime[lane] = songPosition;
                    GameplayHud.showRating(hit.rating, judge.stats.combo);
                    ClientVs.queue(new VsEvent(VsEvent.HIT, hit.index, hit.rating.ordinal())); // VS: the rival sees it
                }
            }
            held[lane] = keys[lane].isDown();

            // VS: the rival sees my strums pressed
            if (held[lane] != lastHeld[lane]) ClientVs.queue(new VsEvent(held[lane] ? VsEvent.KEY_DOWN : VsEvent.KEY_UP, lane, 0));
        }
        lastHeld = held;

        judge.update(songPosition, held); // misses and dropped holds go to the rival through judge.listener
    }

    // The opponent always plays, even when it isn't shown, so turning it on later doesn't show a pile of old notes.
    // In VS it's the rival: their hits and misses come from their game (ClientVs), here only their holds keep going.
    private static void playOpponent() {
        if (vs) {
            opponentJudge.remoteUpdate(songPosition);
            return;
        }
        for (JudgedNote hit : opponentJudge.autoPlay(songPosition)) {
            opponentLastHitTime[hit.note.lane] = songPosition;
        }
    }

    // Draws one side's notes. Returns which lanes have a hold being held right now.
    private static boolean[] drawNotes(GuiGraphics context, NoteJudge side, boolean isOpponent, float sideAlpha) {
        boolean[] holdingLanes = new boolean[4];

        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        // the speed right now (it can change during the song), every note on this side moves at it, like in Psych and Codename
        boolean boyfriendNotes = isOpponent != mySide; // which side of the chart these notes are
        double speed = currentChart.scrollSpeeds != null ? currentChart.scrollSpeeds.speedAt(songPosition, boyfriendNotes)
                     : currentChart.scrollSpeed > 0 ? currentChart.scrollSpeed : 1;
        // same as Psych: 0.45 pixels per ms at 720p, times the scroll speed
        double pixelsPerMs = (screenHeight / 720.0) * 0.45 * speed;
        int strumY = strumY();
        int strumMiddle = strumY + NOTE_SIZE / 2;
        int direction = FnfOptions.downscroll ? -1 : 1; // upscroll: notes come up from below, downscroll: down from above
        int roomBehindStrum = FnfOptions.downscroll ? screenHeight - strumMiddle : strumMiddle; // space past the strum, where notes leave the screen

        for (JudgedNote n : side.notes) {
            double distance = (n.note.time - songPosition) * pixelsPerMs; // how far the note still is from the strum
            if (distance > screenHeight) break; // notes are sorted, everything after this is off screen too
            int noteY = strumY + (int)(direction * distance);

            int noteX = isOpponent ? opponentLaneX(n.note.lane) : laneX(n.note.lane);
            if (n.isBeingHeld()) holdingLanes[n.note.lane] = true;

            // missed notes keep scrolling, but faded out
            float alpha = sideAlpha * (n.missed ? 0.35f : 1f);

            // Hold tail
            if (n.isHold() && !(n.hit && n.isDone() && !n.missed)) {
                // The hold is over once its last piece is hit (one step before the chart's end time, like in Psych),
                // so the tail ends there: the hold end's bottom reaches the middle of the strum right when the hold is done.
                // (as distances from the middle of the strum, in the direction the notes come from)
                double lastPieceTime = n.pieceTime(n.pieceCount - 1);
                double tailEnd = (lastPieceTime - songPosition) * pixelsPerMs + HOLD_END_HEIGHT;

                double tailStart;
                if (!n.hit) tailStart = distance;                       // not hit (yet), the whole tail
                else if (!n.missed) tailStart = Math.max(distance, 0);  // being held, eaten up at the strum
                else tailStart = (n.pieceTime(n.piecesHit) - songPosition) * pixelsPerMs; // dropped, what's left

                if (tailEnd > -roomBehindStrum) { // some of it is still on screen
                    context.setColor(1f, 1f, 1f, alpha * 0.6f); // Psych draws holds a bit see-through
                    drawHoldTail(context, n.note.lane, noteX + (NOTE_SIZE - HOLD_WIDTH) / 2,
                            strumMiddle + (int)(direction * tailStart), strumMiddle + (int)(direction * tailEnd), FnfOptions.downscroll);
                }
            }

            // The note itself, gone once it's hit
            if (!n.hit && noteY > -NOTE_SIZE && noteY < screenHeight) {
                context.setColor(1f, 1f, 1f, alpha);
                Arrow.note(n.note.lane).draw(context, noteSpriteAtlas, noteX, noteY);
            }
        }

        return holdingLanes;
    }

    // Stretched hold pieces from startY to the hold end at endY.
    // Upscroll: the tail goes down from the note. Downscroll (flipped): it goes up, with the hold end upside down (like Psych).
    private static void drawHoldTail(GuiGraphics context, int lane, int x, int startY, int endY, boolean flipped) {
        if (!flipped) {
            int length = endY - startY;
            if (length <= 0) return;

            if (length <= HOLD_END_HEIGHT) { // almost done, only the end is left
                Arrow.holdEnd(lane).draw(context, noteSpriteAtlas, x, startY, HOLD_WIDTH, length);
                return;
            }
            Arrow.hold(lane).draw(context, noteSpriteAtlas, x, startY, HOLD_WIDTH, length - HOLD_END_HEIGHT);
            Arrow.holdEnd(lane).draw(context, noteSpriteAtlas, x, endY - HOLD_END_HEIGHT, HOLD_WIDTH, HOLD_END_HEIGHT);
        } else {
            int length = startY - endY;
            if (length <= 0) return;

            if (length <= HOLD_END_HEIGHT) {
                Arrow.holdEnd(lane).drawFlipped(context, noteSpriteAtlas, x, endY, HOLD_WIDTH, length);
                return;
            }
            Arrow.hold(lane).draw(context, noteSpriteAtlas, x, endY + HOLD_END_HEIGHT, HOLD_WIDTH, length - HOLD_END_HEIGHT);
            Arrow.holdEnd(lane).drawFlipped(context, noteSpriteAtlas, x, endY, HOLD_WIDTH, HOLD_END_HEIGHT);
        }
    }

    private static KeyMapping[] laneKeys() {
        return new KeyMapping[] {FNFmodClient.leftKey, FNFmodClient.downKey, FNFmodClient.upKey, FNFmodClient.rightKey};
    }

    // Before the music starts there's a countdown, like Psych's startCountdown(): the song's time starts
    // COUNTDOWN_BEATS beats before 0 and counts up by itself, with "3, 2, 1, GO!" one beat apart.
    // At 0 the music starts, and from then on the time follows the audio (SongClock).
    // The audio offset option shifts the notes: the game's time = the music's time - offset (Psych: strumTime + noteOffset).
    private static void updateSongPosition() {
        if (musicStarted) {
            songPosition = SongClock.update(AudioManager.tracks.get(0)) - FnfOptions.audioOffset;
            return;
        }

        double crochet = crochet();
        songPosition = -crochet * COUNTDOWN_BEATS + (System.nanoTime() - countdownStartNanos) / 1_000_000.0;

        // one countdown tick per beat, starting one beat in: 0 = "3", 1 = "2", 2 = "1", 3 = "GO!"
        int tick = (int) Math.floor((songPosition + crochet * COUNTDOWN_BEATS) / crochet) - 1;
        if (tick > lastCountdownTick && tick <= 3) {
            lastCountdownTick = tick;
            GameplayHud.countdownTick(tick, crochet);
        }

        if (songPosition >= -FnfOptions.audioOffset) { // the music's time 0
            // Play all selected audio files at the same instant
            AudioManager.playAll(tracksToStart);
            musicStarted = true;
            SongClock.reset();
            songPosition = SongClock.update(AudioManager.tracks.get(0)) - FnfOptions.audioOffset;
        }
    }

    // Singleplayer: when the game pauses (Esc menu), the song pauses with it, countdown included.
    // On servers the game never pauses, so neither does the song (same as Minecraft itself).
    public static void checkPause() {
        if (!running) return;
        boolean gamePaused = Minecraft.getInstance().isPaused();

        if (gamePaused && !paused) {
            paused = true;
            pausedAtNanos = System.nanoTime();
            if (musicStarted) AudioManager.pauseAll();
        } else if (!gamePaused && paused) {
            paused = false;
            long pausedFor = System.nanoTime() - pausedAtNanos;
            countdownStartNanos += pausedFor;               // the countdown continues where it stopped
            GameplayHud.shiftTimers(pausedFor / 1_000_000); // so do the popups
            if (musicStarted) {
                AudioManager.resumeAll();
                SongClock.reset();
            }
            // presses from while the game was paused don't count
            for (KeyMapping key : laneKeys()) {
                while (key.consumeClick()) {}
            }
        }
    }

    // length of one beat in ms, at the song's starting bpm (Psych's Conductor.crochet)
    private static double crochet() {
        double bpm = currentChart.bpm > 0 ? currentChart.bpm : 100;
        return 60000.0 / bpm;
    }

    // tracks = the loaded audio, they start playing when the countdown ends
    public static void startGameplay(Chart chart, List<AudioTrack> tracks) {
        currentChart = chart;
        tracksToStart = tracks;
        musicStarted = false;
        countdownStartNanos = System.nanoTime();
        lastCountdownTick = -1;
        // In VS I might play the opponent's notes. They're still drawn where my strums are, the other side where the opponent's are.
        vs = ClientVs.state == VsMatchPayload.GO;
        mySide = !vs || ClientVs.iAmBoyfriend;
        judge = new NoteJudge(chart.notes, mySide, chart.bpm, chart.bpmChanges);
        opponentJudge = new NoteJudge(chart.notes, !mySide, chart.bpm, chart.bpmChanges);
        lastHeld = new boolean[4];
        if (vs) {
            // my misses and dropped holds go to the rival too
            judge.listener = new NoteJudge.Listener() {
                @Override public void onMiss(JudgedNote note) { ClientVs.queue(new VsEvent(VsEvent.MISS, note.index, 0)); }
                @Override public void onHoldDropped(JudgedNote note) { ClientVs.queue(new VsEvent(VsEvent.HOLD_DROPPED, note.index, 0)); }
            };
            ClientVs.rivalKeysDown = new boolean[4];
        }
        SongClock.reset();
        lastHitTime = new double[] {-1000, -1000, -1000, -1000};
        opponentLastHitTime = new double[] {-1000, -1000, -1000, -1000};
        holding = new boolean[4];
        opponentHolding = new boolean[4];

        // throw away key presses from before the song started
        for (KeyMapping key : laneKeys()) {
            while (key.consumeClick()) {}
        }

        RenderNotes.visible = true;
        Gameplay.running = true;

        if (FnfOptions.fnfCamera) FnfCamera.songStarted();
    }

    // The music ended by itself, show how it went. In VS the server also gets the final score and decides the winner.
    public static void songFinished() {
        if (judge != null) {
            if (vs) {
                ClientVs.sendScore(VsScore.FINISHED);
                GameplayHud.showResults("Song Finished! Waiting for " + ClientVs.rivalName + "'s result...", judge.stats);
            } else {
                GameplayHud.showResults("Song Cleared!", judge.stats);
            }
        }
        stopGameplay();
    }

    // The player pressed "Stop playing". In VS that's giving up.
    public static void stopByPlayer() {
        if (running && vs) ClientVs.sendScore(VsScore.GAVE_UP);
        AudioManager.stopAllTracks();
        stopGameplay();
    }

    // Health ran out.
    public static void gameOver() {
        AudioManager.stopAllTracks();
        GameplayHud.showResults("Game Over!", judge.stats);
        stopGameplay();
    }

    public static void stopGameplay() {
        RenderNotes.visible = false;
        Gameplay.running = false;
        currentChart = null;
        judge = null;
        opponentJudge = null;
        musicStarted = false;
        tracksToStart = null;
        paused = false;
        vs = false;
        mySide = true;

        FnfCamera.songEnded(); // back to first person if the FNF camera was on
    }
}
