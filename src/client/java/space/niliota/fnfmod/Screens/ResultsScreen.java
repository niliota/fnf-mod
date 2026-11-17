package space.niliota.fnfmod.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import space.niliota.fnfmod.ClientSongCatalog;
import space.niliota.fnfmod.HighScores;
import space.niliota.fnfmod.Judging.PlayStats;
import space.niliota.fnfmod.network.ClientNetworking;
import space.niliota.fnfmod.network.SongCatalogPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Shown when a song ends: rating, accuracy, score, combo, how many of each rating, and whether it's a new personal best.
// In VS it also shows the rival and who won (the winner line updates when the server's result comes in).
// The text is a panel on the left, the buttons are stacked on the right: Retry (Rematch in VS), Songs and Close.
public class ResultsScreen extends Screen {
    private final String heading;
    private final int headingColor;
    private final String songName, packName, chartFile;
    private final PlayStats stats;
    private final boolean newBest;
    private final HighScores.Best previousBest; // the best before this song (null = first time)

    // VS (rivalName == null when it wasn't a VS match)
    private final String rivalName;
    private final PlayStats rivalStats;
    private String vsResult; // who won, "" while waiting for the server

    public ResultsScreen(String heading, int headingColor, String songName, String packName, String chartFile,
                         PlayStats stats, boolean newBest, HighScores.Best previousBest,
                         String rivalName, PlayStats rivalStats, String vsResult) {
        super(Component.literal(heading));
        this.heading = heading;
        this.headingColor = headingColor;
        this.songName = songName;
        this.packName = packName;
        this.chartFile = chartFile;
        this.stats = stats;
        this.newBest = newBest;
        this.previousBest = previousBest;
        this.rivalName = rivalName;
        this.rivalStats = rivalStats;
        this.vsResult = vsResult;
    }

    // the server decided who won (VS), called by ClientVs
    public void setVsResult(String result) {
        this.vsResult = result;
    }

    @Override
    protected void init() {
        // stacked on the right side of the screen, in the middle. The first one (Retry/Rematch) is the purple one.
        int buttonWidth = 100;
        int x = this.width - buttonWidth - margin();
        int y = this.height / 2 - (3 * 24 - 4) / 2;
        List<Ui.FlatButton> buttons = new ArrayList<>();
        if (rivalName != null) {
            // VS: challenge the same rival again (they still have to accept)
            buttons.add(new Ui.FlatButton(x, y, buttonWidth, 20, "Rematch", () -> {
                Minecraft.getInstance().player.connection.sendCommand("fnfvs challenge " + rivalName);
                onClose();
            }).accent());
        } else {
            // play the same song again with the same files
            Ui.FlatButton retry = new Ui.FlatButton(x, y, buttonWidth, 20, "Retry", () -> {
                onClose();
                ClientNetworking.retryLastSong();
            }).accent();
            retry.active = ClientNetworking.canRetry();
            buttons.add(retry);
        }
        buttons.add(new Ui.FlatButton(x, y + 24, buttonWidth, 20, "Songs", this::openSongList));
        buttons.add(new Ui.FlatButton(x, y + 48, buttonWidth, 20, "Close", this::onClose));
        for (Ui.FlatButton button : buttons) this.addRenderableWidget(button);
    }

    // space between the screen's edge and the panel/buttons
    private int margin() {
        return Math.max(12, this.width / 12);
    }

    // back to the song browser, with this song picked
    private void openSongList() {
        Minecraft.getInstance().setScreen(new PackSelectScreen(packName, songName));
    }

    private static final int PANEL_WIDTH = 230;
    private static final int PADDING = 10;
    private static final int LINE = 12;    // height of a normal line
    private static final int VALUE_X = 72; // where values start, after their labels

    // how tall the panel's text is, so it can sit in the middle of the screen
    private int contentHeight() {
        int height = 18 + LINE + 6 + 24 + 6 + LINE * 3 + 4 + LINE * 2 + 8 + LINE;
        if (newBest && previousBest != null) height += LINE;
        if (rivalName != null) height += 8 + LINE * 2;
        return height;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int left = margin();
        int width = Math.min(PANEL_WIDTH, this.width - left - 100 - margin() - 16); // leave room for the buttons
        int top = Math.max(6, this.height / 2 - (contentHeight() + PADDING * 2) / 2);
        int x = left + PADDING;
        int y = top + PADDING;

        // dark see-through panel, so the text is easy to read over the world, with a strip in the heading's color on top
        Ui.panel(context, left, top, left + width, top + contentHeight() + PADDING * 2);
        context.fill(left, top, left + width, top + 2, headingColor);

        drawScaled(context, heading, x, y, 1.5f, headingColor);
        y += 18;
        context.drawString(this.font, songName + "  ·  " + FnfMenuScreen.chartLabel(chartFile, songName), x, y, Ui.TEXT_DIM, false);
        y += LINE + 6;

        // the rating big, accuracy and FC next to it
        String fc = stats.fullComboLabel();
        drawScaled(context, stats.ratingName(), x, y, 2f, 0xFFFFFFFF);
        int afterRating = x + this.font.width(stats.ratingName()) * 2 + 8;
        context.drawString(this.font, String.format(Locale.ROOT, "%.2f%%", stats.accuracy() * 100), afterRating, y + 1, 0xFFFFFFFF, true);
        if (!fc.isEmpty()) context.drawString(this.font, fc, afterRating, y + 11, Ui.GOLD, true);
        y += 24 + 6;

        // label  value
        row(context, x, y, "Score", String.format(Locale.ROOT, "%,d", stats.score), 0xFFFFFFFF);
        y += LINE;
        row(context, x, y, "Misses", String.valueOf(stats.misses), 0xFFFFFFFF);
        y += LINE;
        row(context, x, y, "Max combo", String.valueOf(stats.maxCombo), 0xFFFFFFFF);
        y += LINE + 4;

        // how many of each rating, two columns, in their colors
        int column = (width - PADDING * 2) / 2;
        context.drawString(this.font, "Sick: " + stats.sicks, x, y, 0xFF55FFFF, true);
        context.drawString(this.font, "Good: " + stats.goods, x + column, y, 0xFF55FF55, true);
        y += LINE;
        context.drawString(this.font, "Bad: " + stats.bads, x, y, 0xFFFFAA00, true);
        context.drawString(this.font, "Shit: " + stats.shits, x + column, y, 0xFFAA5555, true);
        y += LINE + 8;

        // personal best
        if (newBest) {
            context.drawString(this.font, "NEW BEST!", x, y, Ui.GOLD, true);
            if (previousBest != null) {
                y += LINE;
                context.drawString(this.font, String.format(Locale.ROOT, "Old best: %,d (%.2f%%)",
                        previousBest.score(), previousBest.accuracy() * 100), x, y, Ui.TEXT_DIM, false);
            }
        } else if (previousBest != null) {
            context.drawString(this.font, String.format(Locale.ROOT, "Best: %,d (%.2f%%) %s",
                    previousBest.score(), previousBest.accuracy() * 100, previousBest.fullCombo()).trim(), x, y, Ui.TEXT_DIM, false);
        } else {
            context.drawString(this.font, "First clear!", x, y, Ui.TEXT_DIM, false);
        }
        y += LINE;

        // VS: the rival and who won
        if (rivalName != null) {
            y += 8;
            context.drawString(this.font, String.format(Locale.ROOT, "%s: %,d  ·  %.2f%%  ·  %d misses",
                    rivalName, rivalStats.score, rivalStats.accuracy() * 100, rivalStats.misses), x, y, 0xFFFF7777, true);
            y += LINE;
            context.drawString(this.font, vsResult.isEmpty() ? "Waiting for " + rivalName + "'s result..." : vsResult, x, y, 0xFF55FFFF, true);
        }
    }

    // "Score      123,456": grey label, value in line after it
    private void row(GuiGraphics context, int x, int y, String label, String value, int color) {
        context.drawString(this.font, label, x, y, Ui.TEXT_DIM, false);
        context.drawString(this.font, value, x + VALUE_X, y, color, true);
    }

    // bigger text, starting at x
    private void drawScaled(GuiGraphics context, String text, int x, int y, float scale, int color) {
        context.pose().pushPose();
        context.pose().translate(x, y, 0);
        context.pose().scale(scale, scale, 1f);
        context.drawString(this.font, text, 0, 0, color, true);
        context.pose().popPose();
    }
}
