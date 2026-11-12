package net.niliota.fnfmod;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.niliota.fnfmod.Judging.PlayStats;
import net.niliota.fnfmod.Judging.Rating;

import java.util.Locale;

// Everything on screen during a song besides the notes: health bar, score line and the rating popups.
// Also writes the results into the chat when a song ends.
public class GameplayHud {
    private static final int HEALTH_BAR_WIDTH = 150;
    private static final int HEALTH_BAR_HEIGHT = 6;
    private static final long POPUP_TIME = 600; // ms a rating popup stays on screen

    // the last rating popup
    private static Rating lastRating;
    private static int lastCombo;
    private static long lastRatingTime = 0;

    public static void init() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!Gameplay.running || Gameplay.judge == null) return;

            PlayStats stats = Gameplay.judge.stats;
            Font font = Minecraft.getInstance().font;
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();

            // Health bar, top middle. Like FNF: the opponent's red on the left, the player's green on the right.
            int barX = screenWidth / 2 - HEALTH_BAR_WIDTH / 2;
            int barY = 4;
            int playerWidth = (int)(HEALTH_BAR_WIDTH * stats.health / PlayStats.MAX_HEALTH);
            context.fill(barX - 1, barY - 1, barX + HEALTH_BAR_WIDTH + 1, barY + HEALTH_BAR_HEIGHT + 1, 0xFF000000);
            context.fill(barX, barY, barX + HEALTH_BAR_WIDTH - playerWidth, barY + HEALTH_BAR_HEIGHT, 0xFFFF0000);
            context.fill(barX + HEALTH_BAR_WIDTH - playerWidth, barY, barX + HEALTH_BAR_WIDTH, barY + HEALTH_BAR_HEIGHT, 0xFF66FF33);

            // Score line under it, same text as Psych
            context.drawCenteredString(font, scoreLine(stats), screenWidth / 2, barY + HEALTH_BAR_HEIGHT + 4, 0xFFFFFFFF);

            // Rating popup, left of the strums, fades out
            long age = System.currentTimeMillis() - lastRatingTime;
            if (lastRating != null && age < POPUP_TIME) {
                int alpha = (int)(255 * (1 - (double) age / POPUP_TIME));
                alpha = Math.max(alpha, 0x10); // text with almost no alpha gets drawn fully visible, so stop a bit above 0
                int color = (alpha << 24) | ratingColor(lastRating);

                String text = lastRating.displayName + (lastCombo >= 2 ? " x" + lastCombo : "");
                int textX = Gameplay.laneX(0) - 8 - font.width(text);
                context.drawString(font, text, textX, Gameplay.strumY() + Gameplay.NOTE_SIZE + 8, color, true);
            }
        });
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
