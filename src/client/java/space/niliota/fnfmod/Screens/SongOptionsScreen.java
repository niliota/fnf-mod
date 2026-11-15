package space.niliota.fnfmod.Screens;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import space.niliota.fnfmod.ClientSongCatalog;
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

// Third menu: choose which chart and audio files of the song to use, then start playing.
// The files are on the server, so this only knows their names and asks the server for them by their position in the list.
//  Chart        - exactly one (like radio buttons)
//  Instrumental - any number
//  Voices       - any number
//  Advanced     - metadata files, hidden by default: none ticked = the server finds the chart's own metadata
public class SongOptionsScreen extends FnfMenuScreen {
    public int packId;
    public int songId;
    public SongCatalogPayload.SongEntry song;

    // What's ticked (positions in the song's lists). By default the first file of every list, except metadata.
    private final Set<Integer> selectedInst = new HashSet<>(Set.of(0));
    private final Set<Integer> selectedVoices = new HashSet<>(Set.of(0));
    private int selectedChart = 0;
    private int selectedMetadata = -1; // -1 = automatic

    private boolean showAdvanced = false; // metadata files shown
    private Button startButton;


    public SongOptionsScreen(Component title, int packId, int songId, SongCatalogPayload.SongEntry song) {
        super(Component.literal(song.name()));
        this.packId = packId;
        this.songId = songId;
        this.song = song;
    }

    @Override
    protected void init() {
        subtitle = packId < ClientSongCatalog.packs.size() ? ClientSongCatalog.packs.get(packId).name() : "";

        // Start button, asks the server for the song. It answers with the chart and the song starts once the audio is here.
        // In a VS match it picks the song (and side) for both players instead.
        boolean vs = ClientVs.state == VsMatchPayload.PICKING;
        startButton = button(vs ? "Start VS" : "Start playing", btn -> start());

        List<Button> buttons = new ArrayList<>();
        buttons.add(button("Back", btn -> {
            List<SongCatalogPayload.SongEntry> songs = packId < ClientSongCatalog.packs.size() ? ClientSongCatalog.packs.get(packId).songs() : List.of();
            Minecraft.getInstance().setScreen(new SongSelectScreen(Component.literal(subtitle), packId, songs));
        }));
        // VS: which side the picker plays, the rival gets the other one
        if (vs) {
            buttons.add(button(sideText(), btn -> {
                ClientVs.pickBoyfriend = !ClientVs.pickBoyfriend;
                btn.setMessage(Component.nullToEmpty(sideText()));
            }));
        }
        buttons.add(startButton);

        setupLayout(null, buttons);
        updateStartButton();
    }

    @Override
    protected List<MenuList.Row> buildRows(String search) {
        List<MenuList.Row> rows = rows();

        // Chart: exactly one
        rows.add(new MenuList.Header("Chart"));
        for (int i = 0; i < song.chartFiles().size(); i++) {
            int id = i;
            // the chart's personal best after its name
            String file = song.chartFiles().get(i);
            HighScores.Best best = HighScores.best(subtitle, song.name(), file);
            String label = best == null ? file : String.format(Locale.ROOT, "%s  ·  best %,d · %.1f%% %s",
                    file, best.score(), best.accuracy() * 100, best.fullCombo()).trim();
            rows.add(new MenuList.Toggle(label, () -> selectedChart == id, () -> {
                selectedChart = id;
                updateStartButton();
            }));
        }

        // Audio: any number of each
        addFileToggles(rows, "Instrumental", song.instFiles(), selectedInst);
        addFileToggles(rows, "Voices", song.voicesFiles(), selectedVoices);

        // Advanced: metadata files. The server picks the chart's own metadata by itself,
        // so these are hidden unless the player wants to override that.
        if (!song.metadataFiles().isEmpty()) {
            rows.add(new MenuList.Entry("Advanced: metadata files " + (showAdvanced ? "(hide)" : "(show)"),
                    "Normally picked automatically, tick one to use it instead", 0xFFAAAAAA, () -> {
                showAdvanced = !showAdvanced;
                refreshRows();
            }));
            if (showAdvanced) {
                for (int i = 0; i < song.metadataFiles().size(); i++) {
                    int id = i;
                    // clicking the ticked one again goes back to automatic
                    rows.add(new MenuList.Toggle(song.metadataFiles().get(i), () -> selectedMetadata == id,
                            () -> selectedMetadata = selectedMetadata == id ? -1 : id));
                }
            }
        }
        return rows;
    }

    private void addFileToggles(List<MenuList.Row> rows, String heading, List<String> files, Set<Integer> selected) {
        if (files.isEmpty()) return;
        rows.add(new MenuList.Header(heading));
        for (int i = 0; i < files.size(); i++) {
            int id = i;
            rows.add(new MenuList.Toggle(files.get(i), () -> selected.contains(id), () -> {
                if (!selected.remove(id)) selected.add(id);
                updateStartButton();
            }));
        }
    }

    // can't play without a chart or without any audio
    private void updateStartButton() {
        boolean hasAudio = !selectedInst.isEmpty() || !selectedVoices.isEmpty();
        startButton.active = selectedChart < song.chartFiles().size() && hasAudio;
    }

    private void start() {
        List<Integer> instIds = new ArrayList<>(selectedInst);
        List<Integer> voicesIds = new ArrayList<>(selectedVoices);
        instIds.sort(null);
        voicesIds.sort(null);

        RequestSongPayload request = new RequestSongPayload(
                packId,
                songId,
                instIds,
                voicesIds,
                selectedChart,
                selectedMetadata // -1 = automatic
        );
        if (ClientVs.state == VsMatchPayload.PICKING) {
            ClientPlayNetworking.send(new VsPickPayload(request, ClientVs.pickBoyfriend));
        } else {
            ClientNetworking.requestSong(request);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
//        context.drawString(this.font, "Instrumentals:", 40, y-120 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
//        context.drawString(this.font, "Voices:", 40, y-80 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
//        context.drawString(this.font, "Charting:", 40, y-40 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
//        context.drawString(this.font, "Metadata:", 40, y - this.font.lineHeight - 10, 0xFFFFFFFF, true);
    }

    @Override
    protected String emptyText() {
        return "This song has no files.";
    }

    private static String sideText() {
        return "Your side: " + (ClientVs.pickBoyfriend ? "Boyfriend" : "Opponent");
    }
}
