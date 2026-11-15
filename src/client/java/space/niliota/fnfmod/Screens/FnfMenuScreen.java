package space.niliota.fnfmod.Screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import space.niliota.fnfmod.HighScores;
import space.niliota.fnfmod.network.SongCatalogPayload;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// The layout every FNFmod menu shares: a title at the top (with a grey line under it, or a search box),
// a scrolling list in the middle (MenuList) and a row of buttons at the bottom.
// Screens fill in buildRows() and say which buttons they want.
public abstract class FnfMenuScreen extends Screen {
    protected MenuList list;
    protected EditBox searchBox;       // null on screens without searching
    protected Component heading;       // the title shown at the top (can change, unlike Screen's title)
    protected String subtitle = "";    // grey line under the title (screens without searching)
    protected int titleColor = 0xFFFFFFFF;
    private String searchText = "";    // kept when the screen is rebuilt (window resized)

    protected FnfMenuScreen(Component title) {
        super(title);
        this.heading = title;
    }

    // The rows to show, for this search text (lowercase, "" = show everything).
    protected abstract List<MenuList.Row> buildRows(String search);

    // Shown in the middle of the list when it has no rows.
    protected String emptyText() {
        return "Nothing here.";
    }

    // Sets up the title, the search box (if searchHint isn't null), the list, and the bottom buttons.
    protected void setupLayout(String searchHint, List<Button> bottomButtons) {
        int listTop = 30;
        if (searchHint != null) {
            int searchWidth = Math.min(300, this.width - 40);
            searchBox = new EditBox(this.font, this.width / 2 - searchWidth / 2, 22, searchWidth, 18, Component.literal("Search"));
            searchBox.setHint(Component.literal(searchHint).withStyle(ChatFormatting.DARK_GRAY));
            searchBox.setValue(searchText);
            searchBox.setResponder(text -> {
                searchText = text;
                refreshRows();
            });
            listTop = 46;
        }

        list = new MenuList(this.minecraft, this.width, this.height - listTop - 34, listTop);
        this.addRenderableWidget(list);
        if (searchBox != null) {
            this.addRenderableWidget(searchBox);
            this.setInitialFocus(searchBox); // type to search right away
        }

        // the buttons share the bottom row, as wide as they fit (at most 100 each)
        int gap = 4;
        int buttonWidth = Math.min(100, (this.width - 20 - gap * (bottomButtons.size() - 1)) / Math.max(1, bottomButtons.size()));
        int rowWidth = buttonWidth * bottomButtons.size() + gap * (bottomButtons.size() - 1);
        int x = this.width / 2 - rowWidth / 2;
        for (Button button : bottomButtons) {
            button.setRectangle(buttonWidth, 20, x, this.height - 26);
            this.addRenderableWidget(button);
            x += buttonWidth + gap;
        }

        refreshRows();
    }

    protected void refreshRows() {
        list.setRows(buildRows(searchText.trim().toLowerCase(Locale.ROOT)));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, heading, this.width / 2, 8, titleColor);
        if (searchBox == null && !subtitle.isEmpty()) {
            context.drawCenteredString(this.font, subtitle, this.width / 2, 18, 0xFF9A9A9A);
        }
        if (list.isEmpty()) {
            context.drawCenteredString(this.font, emptyText(), this.width / 2, list.getY() + 20, 0xFFFF7777);
        }
    }

    protected static Button button(String text, Button.OnPress onPress) {
        return Button.builder(Component.literal(text), onPress).build();
    }

    // ---- text helpers shared by the menus ----

    // "hard · erect · pico": the versions of a song, from its chart file names
    // (bopeebo-chart-erect.json -> erect, bopeebo-hard.json -> hard, codename's hard.json -> hard)
    public static String chartVersions(SongCatalogPayload.SongEntry song) {
        Set<String> versions = new LinkedHashSet<>();
        for (String file : song.chartFiles()) versions.add(chartLabel(file, song.name()));
        if (versions.isEmpty()) return "no charts";
        return String.join(" · ", versions);
    }

    public static String chartLabel(String fileName, String songName) {
        String label = fileName.toLowerCase(Locale.ROOT).replaceFirst("\\.json$", "");
        String song = songName.toLowerCase(Locale.ROOT);
        if (label.startsWith(song)) label = label.substring(song.length());
        label = label.replaceFirst("^[-_ ]+", "").replaceFirst("^chart[-_ ]*", "");
        return label.isEmpty() ? "default" : label;
    }

    // "3 audio files" / "1 audio file"
    public static String audioCount(SongCatalogPayload.SongEntry song) {
        int count = song.instFiles().size() + song.voicesFiles().size();
        return count + (count == 1 ? " audio file" : " audio files");
    }

    // "  ·  Best 123,456" for the song's best of all its charts, "" if it was never finished
    public static String bestText(String packName, SongCatalogPayload.SongEntry song) {
        HighScores.Best best = HighScores.bestOfSong(packName, song.name());
        return best == null ? "" : String.format(Locale.ROOT, "  ·  Best %,d", best.score());
    }

    public static List<MenuList.Row> rows() {
        return new ArrayList<>();
    }
}
