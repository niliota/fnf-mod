package net.niliota.fnfmod;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.niliota.fnfmod.Audio.AudioManager;
import net.niliota.fnfmod.Charting.Chart;
import net.niliota.fnfmod.Charting.ChartNote;

public class Gameplay {
    public static boolean running = false;
    public static Chart currentChart;
    // Main spritesheet
    private static final ResourceLocation noteSpriteAtlas = ResourceLocation.fromNamespaceAndPath("fnfmod", "note_assets.png");

    public static void init() {
        int noteSize = 32;
        int noteSpacing = 0;
        int offsetFromRight = 5; // what was this
        int hitZoneOffsetFromBottom = 50;

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            // Stop running the main cycle if set to not running
            if (!running) return;
            if (AudioManager.tracks.isEmpty()) return; // nothing to sync the notes to

            int screenWidth  = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

            double PIXELS_PER_MS = (screenHeight / 720.0) * 0.45;

            int hitZoneY = hitZoneOffsetFromBottom;

            // rightmost note (lane 3) starts at offsetFromRight, others go left from there
            int lane3X = screenWidth - noteSize - offsetFromRight;
            int lane2X = lane3X - noteSize - noteSpacing;
            int lane1X = lane2X - noteSize - noteSpacing;
            int lane0X = lane1X - noteSize - noteSpacing;


            long currentMs = AudioManager.tracks.get(0).getPositionMs(); // Used to sync the note time with the song's

            for (ChartNote note : currentChart.notes) {
                if (!note.isPlayer) continue; // only the player's side is drawn for now

                double timeUntilHit = note.time - currentMs;

                if (timeUntilHit > 2000 || timeUntilHit < -500) continue;

                int noteY = (int)(hitZoneY + (timeUntilHit * PIXELS_PER_MS * currentChart.scrollSpeed));

                int noteX = switch (note.lane) {
                    case 0 -> lane0X;
                    case 1 -> lane1X;
                    case 2 -> lane2X;
                    case 3 -> lane3X;
                    default -> 0;
                };

                Arrow arrow = switch (note.lane) {
                    case 0 -> Arrow.LEFT;
                    case 1 -> Arrow.DOWN;
                    case 2 -> Arrow.UP;
                    case 3 -> Arrow.RIGHT;
                    default -> Arrow.LEFT;
                };

                arrow.draw(context, noteSpriteAtlas, noteX, noteY);
            }
        });
    }

    public static void startGameplay(Chart chart) {
        RenderNotes.visible = true;
        Gameplay.running = true;
        currentChart = chart;
    }

    public static void stopGameplay() {
        RenderNotes.visible = false;
        Gameplay.running = false;
        currentChart = null;
    }
}
