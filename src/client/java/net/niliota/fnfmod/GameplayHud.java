package net.niliota.fnfmod;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.niliota.fnfmod.Judging.PlayStats;
import net.niliota.fnfmod.Judging.Rating;
import net.niliota.fnfmod.network.ClientVs;

import java.util.Locale;

// Everything on screen during a song besides the notes: health bar, score line, rating popups and the countdown.
// Also writes the results into the chat when a song ends.
public class GameplayHud {
    private static final int HEALTH_BAR_WIDTH = 150;
    private static final int HEALTH_BAR_HEIGHT = 6;
    private static final int BOTTOM_OFFSET = 66; // health bar's distance from the bottom, clears the hotbar, hearts and armor
    private static final long POPUP_TIME = 600; // ms a rating popup stays on screen

    // the last rating popup
    private static Rating lastRating;
    private static int lastCombo;
    private static long lastRatingTime = 0;

    // the countdown before the music starts
    private static String countdownText;
    private static int countdownColor = 0xFFFFFF;
    private static long countdownTime = 0;
    private static long countdownDuration = 0;

    public static void init() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!Gameplay.running || Gameplay.judge == null) return;

            PlayStats stats = Gameplay.judge.stats;
            Font font = Minecraft.getInstance().font;
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

            // Health bar, bottom middle above the hotbar and hearts (like FNF, the top is where the strums are).
            // With downscroll the strums are at the bottom, so it goes to the top instead (like Psych).
            // The opponent's red on the left, the player's green on the right.
            int barX = screenWidth / 2 - HEALTH_BAR_WIDTH / 2;
            int barY = FnfOptions.downscroll ? font.lineHeight + 7 : screenHeight - BOTTOM_OFFSET;
            int playerWidth = (int)(HEALTH_BAR_WIDTH * stats.health / PlayStats.MAX_HEALTH);
            context.fill(barX - 1, barY - 1, barX + HEALTH_BAR_WIDTH + 1, barY + HEALTH_BAR_HEIGHT + 1, 0xFF000000);
            context.fill(barX, barY, barX + HEALTH_BAR_WIDTH - playerWidth, barY + HEALTH_BAR_HEIGHT, 0xFFFF0000);
            context.fill(barX + HEALTH_BAR_WIDTH - playerWidth, barY, barX + HEALTH_BAR_WIDTH, barY + HEALTH_BAR_HEIGHT, 0xFF66FF33);

            // Score line above it, same text as Psych
            context.drawCenteredString(font, scoreLine(stats), screenWidth / 2, barY - font.lineHeight - 3, 0xFFFFFFFF);

            // VS: the rival's live score, above mine (below the bar with downscroll, where there's room).
            // Counted from their hits and misses as they come in, so it updates on every note.
            if (Gameplay.vs && Gameplay.opponentJudge != null) {
                PlayStats rival = Gameplay.opponentJudge.stats;
                String rivalLine = ClientVs.rivalName + ": " + (rival.totalPlayed == 0 ? "..." : String.format(Locale.ROOT,
                        "Score: %d | Misses: %d | Accuracy: %.2f%%", rival.score, rival.misses, rival.accuracy() * 100));
                int rivalY = FnfOptions.downscroll ? barY + HEALTH_BAR_HEIGHT + 4 : barY - 2 * font.lineHeight - 6;
                context.drawCenteredString(font, rivalLine, screenWidth / 2, rivalY, 0xFFFF7777);
            }

            // Rating popup, fades out
            long age = System.currentTimeMillis() - lastRatingTime;
            if (lastRating != null && age < POPUP_TIME) {
                int alpha = (int)(255 * (1 - (double) age / POPUP_TIME));
                alpha = Math.max(alpha, 0x10); // text with almost no alpha gets drawn fully visible, so stop a bit above 0
                int color = (alpha << 24) | ratingColor(lastRating);

                // under the middle of the player's strums (above them with downscroll)
                String text = lastRating.displayName + (lastCombo >= 2 ? " x" + lastCombo : "");
                int textX = Gameplay.laneX(0) + 2 * Gameplay.NOTE_SIZE - font.width(text) / 2;
                int textY = FnfOptions.downscroll ? Gameplay.strumY() - 8 - font.lineHeight : Gameplay.strumY() + Gameplay.NOTE_SIZE + 8;
                context.drawString(font, text, textX, textY, color, true);
            }

            // Countdown text, big in the middle of the screen. Over one beat it slowly grows and fades out.
            long countdownAge = System.currentTimeMillis() - countdownTime;
            if (countdownText != null && countdownAge < countdownDuration) {
                double progress = (double) countdownAge / countdownDuration; // 0 at the start of the beat, 1 at the end
                int alpha = (int)(255 * (1 - progress));
                alpha = Math.max(alpha, 0x10);
                // grows from 3x to 6x, "GO!" is bigger: 4x to 9x
                boolean go = countdownText.equals("GO!");
                float scale = go ? (float)(4 + 5 * progress) : (float)(3 + 3 * progress);

                context.pose().pushPose();
                context.pose().translate(screenWidth / 2f, screenHeight / 2f - 30, 0);
                context.pose().scale(scale, scale, 1f);
                context.drawCenteredString(font, countdownText, 0, -font.lineHeight / 2, (alpha << 24) | countdownColor);
                context.pose().popPose();
            }
        });
    }

    // Called by Gameplay once per beat of the countdown. tick: 0 = "3", 1 = "2", 2 = "1", 3 = "GO!", each in its own color.
    // Uses Minecraft's note block sounds, FNF's own countdown sounds and pictures aren't free to use.
    public static void countdownTick(int tick, double beatLength) {
        countdownText = switch (tick) {
            case 0 -> "3";
            case 1 -> "2";
            case 2 -> "1";
            case 3 -> "GO!";
            default -> null;
        };
        countdownColor = switch (tick) {
            case 0 -> 0xFF5555; // red
            case 1 -> 0xFFAA00; // orange
            case 2 -> 0xFFFF55; // yellow
            default -> 0x55FF55; // green
        };
        countdownTime = System.currentTimeMillis();
        countdownDuration = (long) beatLength;

        boolean go = tick == 3;
        SoundEvent sound = go ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.NOTE_BLOCK_HAT.value();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, go ? 1.5f : 1f, 0.6f));
    }

    // after the pause menu: move the popup timers forward, so they continue where they stopped
    public static void shiftTimers(long ms) {
        lastRatingTime += ms;
        countdownTime += ms;
    }

    public static void showRating(Rating rating, int combo) {
        lastRating = rating;
        lastCombo = combo;
        lastRatingTime = System.currentTimeMillis();
    }

    // "Score: 1234 | Misses: 2 | Rating: Great (85.23%) - FC"
    public static String scoreLine(PlayStats stats) {
        String rating = stats.ratingName();
        if (stats.totalPlayed > 0) {
            rating += String.format(Locale.ROOT, " (%.2f%%)", stats.accuracy() * 100);
            String fc = stats.fullComboLabel();
            if (!fc.isEmpty()) rating += " - " + fc;
        }
        return "Score: " + stats.score + " | Misses: " + stats.misses + " | Rating: " + rating;
    }

    // Writes how the song went into the chat.
    public static void showResults(String title, PlayStats stats) {
        if (Minecraft.getInstance().player == null) return;

        Component results = Component.literal(title).withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA)
                .append(Component.literal("\n" + scoreLine(stats)).withStyle(style -> style.withBold(false).withColor(ChatFormatting.WHITE)))
                .append(Component.literal("\nSick: " + stats.sicks + "  Good: " + stats.goods + "  Bad: " + stats.bads
                        + "  Shit: " + stats.shits + "  Max combo: " + stats.maxCombo).withStyle(style -> style.withBold(false).withColor(ChatFormatting.GRAY)));

        Minecraft.getInstance().player.displayClientMessage(results, false);
    }

    private static int ratingColor(Rating rating) {
        return switch (rating) {
            case SICK -> 0x55FFFF;
            case GOOD -> 0x55FF55;
            case BAD  -> 0xFFAA00;
            case SHIT -> 0xAA5555;
        };
    }
}
