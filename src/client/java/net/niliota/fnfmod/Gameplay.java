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
// The score, health bar and ratings are drawn by GameplayHud.
public class Gameplay {
    public static boolean running = false;
    public static Chart currentChart;
    public static NoteJudge judge;
    public static boolean noFail = false; // set on the song options screen, true = can't die
    public static double songPosition = 0;

    // Main spritesheet
    private static final ResourceLocation noteSpriteAtlas = ResourceLocation.fromNamespaceAndPath("fnfmod", "note_assets.png");

    // Layout, shared with RenderNotes so the notes land exactly on the strums.
    // Always anchored to top-right, regardless of GUI scale
    public static final int NOTE_SIZE = 32;
    public static final int OFFSET_FROM_RIGHT = 10;
    public static final int OFFSET_FROM_TOP = 5;

    // hold tails are thinner than notes, same proportions as in FNF (hold piece is 51px wide, a note is 156px)
    private static final int HOLD_WIDTH = Math.round(NOTE_SIZE * 51f / 156f);
    private static final int HOLD_END_HEIGHT = Math.round(HOLD_WIDTH * 64f / 51f);

    // per lane: song time of the last hit, and if a hold is being held (both make the strum glow)
    public static double[] lastHitTime = new double[4];
    public static boolean[] holding = new boolean[4];

    public static int laneX(int lane) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        // rightmost note (lane 3) starts at offsetFromRight, others go left from there
        return screenWidth - NOTE_SIZE - OFFSET_FROM_RIGHT - (3 - lane) * NOTE_SIZE;
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

            if (judge.stats.isDead() && !noFail) {
                gameOver();
                return;
            }

            drawNotes(context);
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

    private static void drawNotes(GuiGraphics context) {
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        double speed = currentChart.scrollSpeed > 0 ? currentChart.scrollSpeed : 1;
        // same as Psych: 0.45 pixels per ms at 720p, times the scroll speed
        double pixelsPerMs = (screenHeight / 720.0) * 0.45 * speed;

        // Fix transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        holding = new boolean[4];
        int strumY = strumY();

        for (JudgedNote n : judge.notes) {
            int noteY = (int)(strumY + (n.note.time - songPosition) * pixelsPerMs);
            if (noteY > screenHeight) break; // notes are sorted, everything after this is below the screen too

            int noteX = laneX(n.note.lane);
            if (n.isBeingHeld()) holding[n.note.lane] = true;

            // missed notes keep scrolling, but faded out
            float alpha = n.missed ? 0.35f : 1f;

            // Hold tail
            if (n.isHold() && !(n.hit && n.isDone() && !n.missed)) {
                int tailEndY = (int)(strumY + (n.note.time + n.note.length - songPosition) * pixelsPerMs) + NOTE_SIZE / 2;
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

        context.setColor(1f, 1f, 1f, 1f);
        // Unfix transparency
        RenderSystem.disableBlend();
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
        judge = new NoteJudge(chart.notes, true, chart.bpm);
        SongClock.reset();
        lastHitTime = new double[] {-1000, -1000, -1000, -1000};
        holding = new boolean[4];

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
    }
}
