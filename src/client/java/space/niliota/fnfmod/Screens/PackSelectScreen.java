package space.niliota.fnfmod.Screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import space.niliota.fnfmod.ClientSongCatalog;
import space.niliota.fnfmod.Gameplay;
import space.niliota.fnfmod.HighScores;
import space.niliota.fnfmod.network.ClientNetworking;
import space.niliota.fnfmod.network.ClientVs;
import space.niliota.fnfmod.network.RequestSongPayload;
import space.niliota.fnfmod.network.SongCatalogPayload;
import space.niliota.fnfmod.network.VsMatchPayload;
import space.niliota.fnfmod.network.VsPickPayload;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

// The Mic menu: a song browser in three panels, packs | songs | the picked song's details with a Start button.
// Opened by right-clicking with the Mic, with the menu key (O by default), or with "Songs" on the results screen.
//  * top bar:    title (or the VS rival), Server songs / My songs (on servers with the mod), search box
//  * bottom bar: Options, Challenge (VS) or Cancel VS, Stop playing, Done
// Typing in the search box finds packs and songs from every pack at once.
// On narrow screens (big GUI scale) packs and songs share the left panel: pick a pack, then "< All packs" goes back.
// The picked pack and song are remembered, so the menu opens where it was left.
//
// The details panel decides which files of the song get played (all on the server, known by their position in the lists):
//  Chart        - exactly one
//  Instrumental - any number
//  Voices       - any number
//  Metadata     - hidden under "Advanced": none ticked = the server finds the chart's own metadata
public class PackSelectScreen extends Screen {
    private static final int TOP = 30, BOTTOM = 28, GAP = 6, MARGIN = 8;
    private static final int WIDE = 560; // screens at least this wide get all three panels

    // remembered between openings (names, the lists' order can change between servers)
    private static String lastPack, lastSong;
    private static String lastSearch = "";

    private List<SongCatalogPayload.PackEntry> packs;
    private int packId = -1, songId = -1;
    private boolean narrow;
    private boolean narrowShowsSongs = false; // narrow screens: the left panel shows the picked pack's songs

    private EditBox search;
    private final ScrollColumn packColumn = new ScrollColumn();
    private final ScrollColumn songColumn = new ScrollColumn();
    private final ScrollColumn detailColumn = new ScrollColumn();
    private Ui.FlatButton startButton;

    // the picked song's files (positions in its lists). By default the first file of every list, except metadata.
    private int selectedChart = 0;
    private final Set<Integer> selectedInst = new HashSet<>();
    private final Set<Integer> selectedVoices = new HashSet<>();
    private int selectedMetadata = -1; // -1 = automatic
    private boolean showAdvanced = false;

    public PackSelectScreen(Component title) {
        super(Component.literal("Blocky Friday"));
    }

    // open with this pack and song picked (the results screen's "Songs")
    public PackSelectScreen(String packName, String songName) {
        this(Component.empty());
        lastPack = packName;
        lastSong = songName;
        lastSearch = "";
    }

    @Override
    protected void init() {
        ClientSongCatalog.refresh();
        packs = ClientSongCatalog.packs;
        narrow = this.width < WIDE;

        // what was picked last time (by name), or the first pack
        packId = -1;
        for (int i = 0; i < packs.size(); i++) if (packs.get(i).name().equals(lastPack)) packId = i;
        if (packId == -1 && !packs.isEmpty()) packId = 0;
        int oldSong = songId;
        songId = -1;
        if (packId != -1) {
            List<SongCatalogPayload.SongEntry> songs = packs.get(packId).songs();
            for (int i = 0; i < songs.size(); i++) if (songs.get(i).name().equals(lastSong)) songId = i;
        }
        if (songId != oldSong || selectedInst.isEmpty() && selectedVoices.isEmpty()) resetFiles();
        if (narrow && songId != -1) narrowShowsSongs = true;

        // ---- top bar: title, Server songs / My songs, search ----
        int searchWidth = Math.min(180, this.width / 3);
        search = new EditBox(this.font, this.width - MARGIN - searchWidth, 6, searchWidth, 18, Component.literal("Search"));
        search.setHint(Component.literal("Search packs and songs...").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(lastSearch);
        search.setResponder(text -> {
            lastSearch = text;
            if (narrow && !text.isBlank()) narrowShowsSongs = true;
            rebuildColumns(false);
        });
        this.addRenderableWidget(search);

        if (ClientSongCatalog.canChoose()) {
            boolean own = ClientSongCatalog.showingOwn();
            int switchWidth = Math.min(80, (this.width - searchWidth - MARGIN * 3 - 80) / 2);
            int x = this.width - MARGIN - searchWidth - GAP * 2 - switchWidth * 2;
            Ui.FlatButton server = new Ui.FlatButton(x, 6, switchWidth, 18, "Server songs", () -> pickOwn(false));
            Ui.FlatButton mine = new Ui.FlatButton(x + switchWidth + 1, 6, switchWidth, 18, "My songs", () -> pickOwn(true));
            server.picked = !own;
            mine.picked = own;
            this.addRenderableWidget(server);
            this.addRenderableWidget(mine);
        }

        // ---- bottom bar ----
        int y = this.height - BOTTOM + 5;
        int buttonWidth = Math.min(90, (this.width - MARGIN * 2 - GAP * 3) / 4);
        int x = MARGIN;
        this.addRenderableWidget(new Ui.FlatButton(x, y, buttonWidth, 18, "Options",
                () -> Minecraft.getInstance().setScreen(new FnfOptionsScreen(this))));
        x += buttonWidth + GAP;
        if (ClientVs.inMatch()) {
            this.addRenderableWidget(new Ui.FlatButton(x, y, buttonWidth, 18, "Cancel VS", () -> {
                Minecraft.getInstance().player.connection.sendCommand("fnfvs cancel");
                onClose();
            }));
        } else {
            Ui.FlatButton challenge = new Ui.FlatButton(x, y, buttonWidth, 18, "Challenge (VS)",
                    () -> Minecraft.getInstance().setScreen(new VsPlayerListScreen(this)));
            challenge.active = ClientSongCatalog.received && !ClientSongCatalog.showingOwn(); // VS needs the server's songs
            this.addRenderableWidget(challenge);
        }
        x += buttonWidth + GAP;
        Ui.FlatButton stop = new Ui.FlatButton(x, y, buttonWidth, 18, "Stop playing", Gameplay::stopByPlayer); // in VS this counts as giving up
        stop.active = Gameplay.running;
        this.addRenderableWidget(stop);
        this.addRenderableWidget(new Ui.FlatButton(this.width - MARGIN - buttonWidth, y, buttonWidth, 18, "Done", this::onClose));

        // ---- the panels ----
        int bodyTop = TOP + 2, bodyBottom = this.height - BOTTOM - 2;
        int available = this.width - MARGIN * 2;
        int listsWidth;
        if (narrow) {
            listsWidth = (available - GAP) * 9 / 20;
            packColumn.setBounds(MARGIN, bodyTop, listsWidth, bodyBottom - bodyTop);
            songColumn.setBounds(MARGIN, bodyTop, listsWidth, bodyBottom - bodyTop);
        } else {
            int packWidth = Math.max(120, Math.min(190, available * 26 / 100));
            int songWidth = Math.max(150, Math.min(260, available * 33 / 100));
            packColumn.setBounds(MARGIN, bodyTop, packWidth, bodyBottom - bodyTop);
            songColumn.setBounds(MARGIN + packWidth + GAP, bodyTop, songWidth, bodyBottom - bodyTop);
            listsWidth = packWidth + GAP + songWidth;
        }
        int detailX = MARGIN + listsWidth + GAP;
        int detailWidth = this.width - MARGIN - detailX;
        detailColumn.setBounds(detailX, bodyTop, detailWidth, bodyBottom - bodyTop - 24);

        boolean vs = ClientVs.state == VsMatchPayload.PICKING;
        startButton = new Ui.FlatButton(detailX, bodyBottom - 20, detailWidth, 20, vs ? "Start VS" : "Start playing", this::start).accent();
        this.addRenderableWidget(startButton);

        rebuildColumns(false);
        this.setInitialFocus(search); // type to search right away
    }

    // ---- what's picked ----

    private SongCatalogPayload.SongEntry song() {
        if (packId < 0 || packId >= packs.size()) return null;
        List<SongCatalogPayload.SongEntry> songs = packs.get(packId).songs();
        return songId >= 0 && songId < songs.size() ? songs.get(songId) : null;
    }

    private void pickPack(int id) {
        packId = id;
        lastPack = packs.get(id).name();
        if (narrow) narrowShowsSongs = true;
        rebuildColumns(false);
    }

    private void pickSong(int pack, int id) {
        packId = pack;
        songId = id;
        lastPack = packs.get(pack).name();
        lastSong = packs.get(pack).songs().get(id).name();
        resetFiles();
        rebuildColumns(true);
    }

    private void resetFiles() {
        selectedChart = 0;
        selectedInst.clear();
        selectedVoices.clear();
        selectedMetadata = -1;
        showAdvanced = false;
        SongCatalogPayload.SongEntry song = song();
        if (song == null) return;
        if (!song.instFiles().isEmpty()) selectedInst.add(0);
        if (!song.voicesFiles().isEmpty()) selectedVoices.add(0);
    }

    private void pickOwn(boolean own) {
        ClientSongCatalog.chooseOwn(own);
        this.rebuildWidgets(); // the other packs, the switch and the VS button change
    }

    // ---- the panels' rows ----

    private void rebuildColumns(boolean keepListScroll) {
        String query = lastSearch.trim().toLowerCase(Locale.ROOT);
        List<ScrollColumn.Row> packRows = new ArrayList<>();
        List<ScrollColumn.Row> songRows = new ArrayList<>();

        for (int p = 0; p < packs.size(); p++) {
            SongCatalogPayload.PackEntry pack = packs.get(p);
            boolean packMatches = pack.name().toLowerCase(Locale.ROOT).contains(query);
            boolean anySong = false;
            for (int s = 0; s < pack.songs().size(); s++) {
                SongCatalogPayload.SongEntry song = pack.songs().get(s);
                boolean songMatches = song.name().toLowerCase(Locale.ROOT).contains(query);
                if (query.isEmpty() ? p == packId : songMatches) {
                    songRows.add(new SongRow(p, s, !query.isEmpty()));
                    anySong = true;
                }
            }
            if (query.isEmpty() || packMatches || anySong) packRows.add(new PackRow(p));
        }

        if (packs.isEmpty()) {
            packRows.add(new TextRow(!ClientSongCatalog.received && !ClientSongCatalog.local
                    ? "Waiting for the server's songs..." : "No packs found, put FNF mods into the fnfpacks folder.", 0xFFFF7777));
        } else if (songRows.isEmpty()) {
            songRows.add(new TextRow(query.isEmpty() ? "This pack has no songs." : "No songs found.", Ui.TEXT_DIM));
        }

        if (narrow) {
            // one panel for both: the songs (with a way back), or the packs
            if (narrowShowsSongs) {
                if (query.isEmpty()) songRows.add(0, new BackRow());
                songColumn.setRows(songRows, keepListScroll);
            } else {
                packColumn.setRows(packRows, keepListScroll);
            }
        } else {
            packColumn.setRows(packRows, keepListScroll);
            songColumn.setRows(songRows, keepListScroll);
        }
        detailColumn.setRows(detailRows(), false);
        updateStartButton();
    }

    private List<ScrollColumn.Row> detailRows() {
        List<ScrollColumn.Row> rows = new ArrayList<>();
        SongCatalogPayload.SongEntry song = song();
        if (song == null) {
            rows.add(new TextRow(packs.isEmpty() ? "" : "Pick a song", Ui.TEXT_DIM));
            return rows;
        }
        String packName = packs.get(packId).name();
        rows.add(new TitleRow(song.name(), packName));

        // the picked chart's personal best
        String chartFile = selectedChart < song.chartFiles().size() ? song.chartFiles().get(selectedChart) : "";
        HighScores.Best best = HighScores.best(packName, song.name(), chartFile);
        rows.add(best == null
                ? new TextRow("Not cleared yet", Ui.TEXT_DIM)
                : new TextRow(String.format(Locale.ROOT, "Best  %,d  ·  %.2f%%  ·  %d misses %s",
                        best.score(), best.accuracy() * 100, best.misses(), best.fullCombo()).trim(), Ui.GOLD));

        // VS: the picker's side, the rival gets the other one
        if (ClientVs.state == VsMatchPayload.PICKING) {
            rows.add(new HeaderRow("Your side"));
            rows.add(new ChipsRow(List.of(
                    new Chip("Boyfriend", () -> ClientVs.pickBoyfriend, () -> ClientVs.pickBoyfriend = true),
                    new Chip("Opponent", () -> !ClientVs.pickBoyfriend, () -> ClientVs.pickBoyfriend = false))));
        }

        // Chart: exactly one. Shown by their version ("hard", "erect"), the file name if two would look the same.
        rows.add(new HeaderRow("Chart"));
        List<Chip> charts = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String file : song.chartFiles()) labels.add(FnfMenuScreen.chartLabel(file, song.name()));
        for (int i = 0; i < song.chartFiles().size(); i++) {
            int id = i;
            String label = labels.indexOf(labels.get(i)) != labels.lastIndexOf(labels.get(i)) ? song.chartFiles().get(i) : labels.get(i);
            charts.add(new Chip(label, () -> selectedChart == id, () -> {
                selectedChart = id;
                detailColumn.setRows(detailRows(), true); // the best changes with the chart
            }));
        }
        rows.add(new ChipsRow(charts));

        // Audio: any number of each
        addFileChips(rows, "Instrumental", song.instFiles(), selectedInst);
        addFileChips(rows, "Voices", song.voicesFiles(), selectedVoices);

        // Advanced: metadata files. The server picks the chart's own metadata by itself,
        // so these are hidden unless the player wants to override that.
        if (!song.metadataFiles().isEmpty()) {
            rows.add(new LinkRow((showAdvanced ? "Hide" : "Show") + " metadata files (advanced)", () -> {
                showAdvanced = !showAdvanced;
                detailColumn.setRows(detailRows(), true);
            }));
            if (showAdvanced) {
                List<Chip> metadata = new ArrayList<>();
                for (int i = 0; i < song.metadataFiles().size(); i++) {
                    int id = i;
                    // clicking the ticked one again goes back to automatic
                    metadata.add(new Chip(song.metadataFiles().get(i), () -> selectedMetadata == id,
                            () -> selectedMetadata = selectedMetadata == id ? -1 : id));
                }
                rows.add(new ChipsRow(metadata));
                rows.add(new TextRow("None picked = found automatically", Ui.TEXT_FAINT));
            }
        }
        return rows;
    }

    private void addFileChips(List<ScrollColumn.Row> rows, String heading, List<String> files, Set<Integer> selected) {
        if (files.isEmpty()) return;
        rows.add(new HeaderRow(heading));
        List<Chip> chips = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            int id = i;
            chips.add(new Chip(files.get(i).replaceFirst("(?i)\\.ogg$", ""), () -> selected.contains(id), () -> {
                if (!selected.remove(id)) selected.add(id);
                updateStartButton();
            }));
        }
        rows.add(new ChipsRow(chips));
    }

    // can't play without a song, a chart or any audio
    private void updateStartButton() {
        SongCatalogPayload.SongEntry song = song();
        boolean hasAudio = !selectedInst.isEmpty() || !selectedVoices.isEmpty();
        startButton.active = song != null && selectedChart < song.chartFiles().size() && hasAudio;
    }

    private void start() {
        if (song() == null) return;
        List<Integer> instIds = new ArrayList<>(selectedInst);
        List<Integer> voicesIds = new ArrayList<>(selectedVoices);
        instIds.sort(null);
        voicesIds.sort(null);

        RequestSongPayload request = new RequestSongPayload(packId, songId, instIds, voicesIds, selectedChart, selectedMetadata);
        if (ClientVs.state == VsMatchPayload.PICKING) {
            ClientPlayNetworking.send(new VsPickPayload(request, ClientVs.pickBoyfriend));
        } else {
            ClientNetworking.requestSong(request);
        }
    }

    // ---- drawing and the mouse ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        // title (or the VS rival), left in the top bar
        String title = ClientVs.inMatch() ? "VS " + ClientVs.rivalName + ": pick a song" : "Blocky Friday";
        int titleSpace = this.width - MARGIN * 2 - Math.min(180, this.width / 3) - (ClientSongCatalog.canChoose() ? 180 : 0);
        g.drawString(this.font, Ui.cut(this.font, title, titleSpace), MARGIN, 11, ClientVs.inMatch() ? Ui.CYAN : Ui.TEXT, true);

        if (narrow) {
            (narrowShowsSongs ? songColumn : packColumn).render(g, mouseX, mouseY);
        } else {
            packColumn.render(g, mouseX, mouseY);
            songColumn.render(g, mouseX, mouseY);
        }
        detailColumn.render(g, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        // the top and bottom bars
        g.fill(0, 0, this.width, TOP, 0x90000000);
        g.fill(0, this.height - BOTTOM, this.width, this.height, 0x90000000);
        g.fill(0, TOP, this.width, TOP + 1, Ui.PANEL_LINE);
        g.fill(0, this.height - BOTTOM - 1, this.width, this.height - BOTTOM, Ui.PANEL_LINE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        if (narrow) {
            if ((narrowShowsSongs ? songColumn : packColumn).mouseClicked(mouseX, mouseY)) return true;
        } else if (packColumn.mouseClicked(mouseX, mouseY) || songColumn.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        return detailColumn.mouseClicked(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (narrow) {
            if ((narrowShowsSongs ? songColumn : packColumn).mouseScrolled(mouseX, mouseY, scrollY)) return true;
        } else if (packColumn.mouseScrolled(mouseX, mouseY, scrollY) || songColumn.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return detailColumn.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---- rows ----

    // a list row's background: purple when picked, lighter under the mouse
    private static void rowBackground(GuiGraphics g, int x, int y, int width, int height, boolean picked, boolean hovered) {
        if (picked) {
            g.fill(x, y, x + width, y + height, Ui.ACCENT_SOFT);
            g.fill(x, y, x + 2, y + height, Ui.ACCENT);
        } else if (hovered) {
            g.fill(x, y, x + width, y + height, Ui.HOVER);
        }
    }

    private class PackRow implements ScrollColumn.Row {
        private final int id;

        PackRow(int id) {
            this.id = id;
        }

        @Override public int height(int width) { return 30; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            rowBackground(g, x, y, width, 28, id == packId, hovered);
            SongCatalogPayload.PackEntry pack = packs.get(id);
            ResourceLocation icon = ClientSongCatalog.icon(id);
            int textX = x + 7;
            if (icon != null) {
                RenderSystem.enableBlend();
                g.blit(icon, x + 5, y + 4, 20, 20, 0, 0, 64, 64, 64, 64);
                RenderSystem.disableBlend();
                textX = x + 30;
            }
            int count = pack.songs().size();
            g.drawString(font, Ui.cut(font, pack.name(), x + width - textX - 4), textX, y + 5, Ui.TEXT, true);
            g.drawString(font, count + (count == 1 ? " song" : " songs"), textX, y + 16, Ui.TEXT_DIM, false);
        }

        @Override
        public boolean click(double mouseX, double mouseY, int x, int y, int width) {
            pickPack(id);
            return true;
        }
    }

    private class SongRow implements ScrollColumn.Row {
        private final int pack, id;
        private final boolean showPack; // searching: songs from every pack, so say which

        SongRow(int pack, int id, boolean showPack) {
            this.pack = pack;
            this.id = id;
            this.showPack = showPack;
        }

        @Override public int height(int width) { return 30; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            rowBackground(g, x, y, width, 28, pack == packId && id == songId, hovered);
            SongCatalogPayload.PackEntry packEntry = packs.get(pack);
            SongCatalogPayload.SongEntry song = packEntry.songs().get(id);
            HighScores.Best best = HighScores.bestOfSong(packEntry.name(), song.name());

            int right = x + width - 6;
            if (best != null) { // the best on the right, like a leaderboard
                String score = String.format(Locale.ROOT, "%,d", best.score());
                g.drawString(font, score, right - font.width(score), y + 5, Ui.GOLD, true);
                right -= font.width(score) + 6;
            }
            g.drawString(font, Ui.cut(font, song.name(), right - x - 7), x + 7, y + 5, Ui.TEXT, true);
            String info = showPack ? packEntry.name() : FnfMenuScreen.chartVersions(song);
            g.drawString(font, Ui.cut(font, info, width - 14), x + 7, y + 16, Ui.TEXT_DIM, false);
        }

        @Override
        public boolean click(double mouseX, double mouseY, int x, int y, int width) {
            pickSong(pack, id);
            return true;
        }
    }

    // narrow screens: back from the songs to the packs
    private class BackRow implements ScrollColumn.Row {
        @Override public int height(int width) { return 18; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            if (hovered) g.fill(x, y, x + width, y + 16, Ui.HOVER);
            g.drawString(font, "< All packs", x + 5, y + 4, hovered ? Ui.TEXT : Ui.TEXT_DIM, false);
        }

        @Override
        public boolean click(double mouseX, double mouseY, int x, int y, int width) {
            narrowShowsSongs = false;
            rebuildColumns(false);
            return true;
        }
    }

    // the song's name big, the pack under it
    private class TitleRow implements ScrollColumn.Row {
        private final String title, subtitle;

        TitleRow(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        @Override public int height(int width) { return 34; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            Ui.bigText(g, font, title, x + 4, y + 4, 1.5f, Ui.TEXT, width - 8);
            g.drawString(font, Ui.cut(font, subtitle, width - 8), x + 4, y + 21, Ui.TEXT_DIM, false);
        }
    }

    private class TextRow implements ScrollColumn.Row {
        private final String text;
        private final int color;

        TextRow(String text, int color) {
            this.text = text;
            this.color = color;
        }

        @Override public int height(int width) { return 14; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            g.drawString(font, Ui.cut(font, text, width - 8), x + 4, y + 3, color, false);
        }
    }

    // a small heading in the details panel
    private class HeaderRow implements ScrollColumn.Row {
        private final String text;

        HeaderRow(String text) {
            this.text = text;
        }

        @Override public int height(int width) { return 18; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            g.drawString(font, text.toUpperCase(Locale.ROOT), x + 4, y + 8, Ui.TEXT_DIM, false);
        }
    }

    // clickable text ("Show metadata files")
    private class LinkRow implements ScrollColumn.Row {
        private final String text;
        private final Runnable onClick;

        LinkRow(String text, Runnable onClick) {
            this.text = text;
            this.onClick = onClick;
        }

        @Override public int height(int width) { return 18; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            boolean over = hovered && mouseX < x + 8 + font.width(text);
            g.drawString(font, (over ? ChatFormatting.UNDERLINE : "") + text, x + 4, y + 7, over ? Ui.TEXT : Ui.TEXT_DIM, false);
        }

        @Override
        public boolean click(double mouseX, double mouseY, int x, int y, int width) {
            onClick.run();
            return true;
        }
    }

    // a pill that can be on (purple) or off, like a tick box
    private record Chip(String label, BooleanSupplier on, Runnable onClick) {}

    // chips side by side, going on to the next line when there's no room
    private class ChipsRow implements ScrollColumn.Row {
        private static final int HEIGHT = 16, SPACE = 4;
        private final List<Chip> chips;

        ChipsRow(List<Chip> chips) {
            this.chips = chips;
        }

        private int chipWidth(Chip chip, int width) {
            return Math.min(font.width(chip.label()) + 14, width - 8);
        }

        // where each chip goes: {x, y} relative to the row
        private List<int[]> layout(int width) {
            List<int[]> places = new ArrayList<>();
            int cx = 4, cy = 2;
            for (Chip chip : chips) {
                int w = chipWidth(chip, width);
                if (cx > 4 && cx + w > width - 4) {
                    cx = 4;
                    cy += HEIGHT + SPACE;
                }
                places.add(new int[] {cx, cy});
                cx += w + SPACE;
            }
            return places;
        }

        @Override
        public int height(int width) {
            List<int[]> places = layout(width);
            return places.isEmpty() ? 0 : places.get(places.size() - 1)[1] + HEIGHT + 4;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            List<int[]> places = layout(width);
            for (int i = 0; i < chips.size(); i++) {
                Chip chip = chips.get(i);
                int x0 = x + places.get(i)[0], y0 = y + places.get(i)[1], w = chipWidth(chip, width);
                boolean on = chip.on().getAsBoolean();
                boolean over = hovered && mouseX >= x0 && mouseX < x0 + w && mouseY >= y0 && mouseY < y0 + HEIGHT;
                g.fill(x0, y0, x0 + w, y0 + HEIGHT, on ? (over ? Ui.ACCENT_HOVER : Ui.ACCENT) : (over ? 0x40FFFFFF : 0x22FFFFFF));
                if (!on) g.renderOutline(x0, y0, w, HEIGHT, 0x30FFFFFF);
                g.drawCenteredString(font, Ui.cut(font, chip.label(), w - 8), x0 + w / 2, y0 + 4, on ? Ui.TEXT : 0xFFCFCFD8);
            }
        }

        @Override
        public boolean click(double mouseX, double mouseY, int x, int y, int width) {
            List<int[]> places = layout(width);
            for (int i = 0; i < chips.size(); i++) {
                int x0 = x + places.get(i)[0], y0 = y + places.get(i)[1], w = chipWidth(chips.get(i), width);
                if (mouseX >= x0 && mouseX < x0 + w && mouseY >= y0 && mouseY < y0 + HEIGHT) {
                    chips.get(i).onClick().run();
                    return true;
                }
            }
            return false;
        }
    }
}
