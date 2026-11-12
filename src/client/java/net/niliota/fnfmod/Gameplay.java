package net.niliota.fnfmod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.niliota.fnfmod.Audio.AudioManager;
import net.niliota.fnfmod.Audio.SongClock;
import net.niliota.fnfmod.Charting.Chart;
import net.niliota.fnfmod.Judging.JudgedNote;
import net.niliota.fnfmod.Judging.NoteJudge;

// Runs a song while it's playing: reads the arrow keys, lets NoteJudge decide hits and misses,
// and draws the notes and their hold tails scrolling up towards the strums (drawn by RenderNotes).
// The opponent's side plays itself (like in FNF) and can be shown next to the player's.
// The score, health bar and ratings are drawn by GameplayHud.
public class Gameplay {
    public static boolean running = false;
    public static Chart currentChart;
    public static NoteJudge judge;          // the player's side
    public static NoteJudge opponentJudge;  // the opponent's side, plays by itself
    public static double songPosition = 0;

    // Options from the song options screen (remembered until the game closes)
    public static boolean noFail = false;       // health running out doesn't end the song
    public static boolean middleScroll = false; // player's strums in the middle of the screen
    public static boolean showOpponent = false; // show the opponent's strums and notes too

    // Main spritesheet
    private static final ResourceLocation noteSpriteAtlas = ResourceLocation.fromNamespaceAndPath("fnfmod", "note_assets.png");

    // Layout, shared with RenderNotes so the notes land exactly on the strums.
    // Always anchored to top-right (or top-middle with middle scroll), regardless of GUI scale
    public static final int NOTE_SIZE = 32;
    public static final int OFFSET_FROM_RIGHT = 10;
    public static final int OFFSET_FROM_TOP = 5;
    public static final int GAP_BETWEEN_SIDES = 16; // space between the player's and the opponent's strums

    // hold tails are thinner than notes, same proportions as in FNF (hold piece is 51px wide, a note is 156px)
    private static final int HOLD_WIDTH = Math.round(NOTE_SIZE * 51f / 156f);
    private static final int HOLD_END_HEIGHT = Math.round(HOLD_WIDTH * 64f / 51f);

    // per lane: song time of the last hit, and if a hold is being held (both make the strum glow)
    public static double[] lastHitTime = new double[4];
    public static boolean[] holding = new boolean[4];
    public static double[] opponentLastHitTime = new double[4];
    public static boolean[] opponentHolding = new boolean[4];

    // x of one of the player's lanes (0=left, 1=down, 2=up, 3=right)
    public static int laneX(int lane) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        if (middleScroll) return screenWidth / 2 - 2 * NOTE_SIZE + lane * NOTE_SIZE;

        // rightmost note (lane 3) starts at offsetFromRight, others go left from there
        return screenWidth - NOTE_SIZE - OFFSET_FROM_RIGHT - (3 - lane) * NOTE_SIZE;
    }

    // x of one of the opponent's lanes
    public static int opponentLaneX(int lane) {
        if (middleScroll) {
            // split around the player's strums, 2 on each side (like Psych's middle scroll)
            if (lane < 2) return laneX(0) - GAP_BETWEEN_SIDES - (2 - lane) * NOTE_SIZE;
            return laneX(3) + NOTE_SIZE + GAP_BETWEEN_SIDES + (lane - 2) * NOTE_SIZE;
        }
        // on its own side, the left side of the screen, like in FNF (mirrors the player's distance from the right edge)
        return OFFSET_FROM_RIGHT + lane * NOTE_SIZE;
    }

    // with middle scroll the opponent's side is faded out so it doesn't get in the way (Psych does the same)
    public static float opponentAlpha() {
        return middleScroll ? 0.35f : 1f;
    }

    public static int strumY() {
        return OFFSET_FROM_TOP;
    }

    public static void init() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            // Stop running the main cycle if set to not running
            if (!running || judge == null) return;
            if (AudioManager.tracks.isEmpty()) return; // nothing to sync the notes to

            // Used to sync the note time with the song's
            songPosition = SongClock.update(AudioManager.tracks.get(0));

            handleInput();
            playOpponent();

            if (judge.stats.isDead() && !noFail) {
                gameOver();
                return;
            }

            // Fix transparency
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            if (showOpponent) {
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
                }
            }
            held[lane] = keys[lane].isDown();
        }

        judge.update(songPosition, held);
    }

    // The opponent always plays, even when it isn't shown, so turning it on later doesn't show a pile of old notes.
    private static void playOpponent() {
        for (JudgedNote hit : opponentJudge.autoPlay(songPosition)) {
            opponentLastHitTime[hit.note.lane] = songPosition;
        }
    }

    // Draws one side's notes. Returns which lanes have a hold being held right now.
    private static boolean[] drawNotes(GuiGraphics context, NoteJudge side, boolean isOpponent, float sideAlpha) {
        boolean[] holdingLanes = new boolean[4];

        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        double speed = currentChart.scrollSpeed > 0 ? currentChart.scrollSpeed : 1;
        // same as Psych: 0.45 pixels per ms at 720p, times the scroll speed
        double pixelsPerMs = (screenHeight / 720.0) * 0.45 * speed;
        int strumY = strumY();

        for (JudgedNote n : side.notes) {
            int noteY = (int)(strumY + (n.note.time - songPosition) * pixelsPerMs);
            if (noteY > screenHeight) break; // notes are sorted, everything after this is below the screen too

            int noteX = isOpponent ? opponentLaneX(n.note.lane) : laneX(n.note.lane);
            if (n.isBeingHeld()) holdingLanes[n.note.lane] = true;

            // missed notes keep scrolling, but faded out
            float alpha = sideAlpha * (n.missed ? 0.35f : 1f);

            // Hold tail
            if (n.isHold() && !(n.hit && n.isDone() && !n.missed)) {
                // The hold is over once its last piece is hit (one step before the chart's end time, like in Psych),
                // so the tail ends there: the hold end's bottom reaches the middle of the strum right when the hold is done.
                double lastPieceTime = n.pieceTime(n.pieceCount - 1);
                int tailEndY = (int)(strumY + (lastPieceTime - songPosition) * pixelsPerMs) + NOTE_SIZE / 2 + HOLD_END_HEIGHT;

                int tailStartY;
                if (!n.hit) tailStartY = noteY;                              // not hit (yet), the whole tail
                else if (!n.missed) tailStartY = Math.max(noteY, strumY);    // being held, eaten up at the strum
                else tailStartY = (int)(strumY + (n.pieceTime(n.piecesHit) - songPosition) * pixelsPerMs); // dropped, what's left
                tailStartY += NOTE_SIZE / 2;

                if (tailEndY > 0) {
                    context.setColor(1f, 1f, 1f, alpha * 0.6f); // Psych draws holds a bit see-through
                    drawHoldTail(context, n.note.lane, noteX + (NOTE_SIZE - HOLD_WIDTH) / 2, tailStartY, tailEndY);
                }
            }

            // The note itself, gone once it's hit
            if (!n.hit && noteY > -NOTE_SIZE) {
                context.setColor(1f, 1f, 1f, alpha);
                Arrow.note(n.note.lane).draw(context, noteSpriteAtlas, noteX, noteY);
            }
        }

        return holdingLanes;
    }

    // stretched hold pieces from startY down to the hold end at endY
    private static void drawHoldTail(GuiGraphics context, int lane, int x, int startY, int endY) {
        int length = endY - startY;
        if (length <= 0) return;

        if (length <= HOLD_END_HEIGHT) { // almost done, only the end is left
            Arrow.holdEnd(lane).draw(context, noteSpriteAtlas, x, startY, HOLD_WIDTH, length);
            return;
        }
        Arrow.hold(lane).draw(context, noteSpriteAtlas, x, startY, HOLD_WIDTH, length - HOLD_END_HEIGHT);
        Arrow.holdEnd(lane).draw(context, noteSpriteAtlas, x, endY - HOLD_END_HEIGHT, HOLD_WIDTH, HOLD_END_HEIGHT);
    }

    private static KeyMapping[] laneKeys() {
        return new KeyMapping[] {FNFmodClient.leftKey, FNFmodClient.downKey, FNFmodClient.upKey, FNFmodClient.rightKey};
    }

    public static void startGameplay(Chart chart) {
        currentChart = chart;
        judge = new NoteJudge(chart.notes, true, chart.bpm, chart.bpmChanges);
        opponentJudge = new NoteJudge(chart.notes, false, chart.bpm, chart.bpmChanges);
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
    }

    // The music ended by itself, show how it went.
    public static void songFinished() {
        if (judge != null) GameplayHud.showResults("Song Cleared!", judge.stats);
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
    }
}
