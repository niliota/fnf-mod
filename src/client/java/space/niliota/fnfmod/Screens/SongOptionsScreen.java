package space.niliota.fnfmod.Screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import space.niliota.fnfmod.network.ClientNetworking;
import space.niliota.fnfmod.network.ClientVs;
import space.niliota.fnfmod.network.VsMatchPayload;
import space.niliota.fnfmod.network.VsPickPayload;
import space.niliota.fnfmod.network.RequestSongPayload;
import space.niliota.fnfmod.network.SongCatalogPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Third menu: choose which audio, chart and metadata files of the song to use, then start playing.
// The files are on the server, so this only knows their names and asks the server for them by their position in the list.
public class SongOptionsScreen extends Screen {
    public int packId;
    public int songId;
    public SongCatalogPayload.SongEntry song;

    // checkbox -> position of its file in the song's list
    private final Map<Checkbox, Integer> checkboxToInstFile = new HashMap<>();
    private final Map<Checkbox, Integer> checkboxToVoicesFile = new HashMap<>();
    private final Map<Checkbox, Integer> checkboxToChartFile = new HashMap<>();
    private final Map<Checkbox, Integer> checkboxToMetadataFile = new HashMap<>();

    // What's ticked, so it doesn't get lost when the checkboxes are rebuilt while scrolling.
    // By default the first file of every list is ticked, except metadata (automatic unless the player picks one).
    private final Set<Integer> selectedInst = new HashSet<>(Set.of(0));
    private final Set<Integer> selectedVoices = new HashSet<>(Set.of(0));
    private final Set<Integer> selectedChart = new HashSet<>(Set.of(0));
    private final Set<Integer> selectedMetadata = new HashSet<>(); // nothing ticked = the server finds the chart's own metadata

    private boolean showAdvanced = false; // metadata checkboxes shown
    private int scrollOffset = 0;
    int x = 40; int y = 40;


    public SongOptionsScreen(Component title, int packId, int songId, SongCatalogPayload.SongEntry song) {
        super(title);
        this.packId = packId;
        this.songId = songId;
        this.song = song;
    }

    @Override
    protected void init() {
        // remember what was ticked before throwing the old checkboxes away
        rememberSelection(checkboxToInstFile, selectedInst);
        rememberSelection(checkboxToVoicesFile, selectedVoices);
        rememberSelection(checkboxToChartFile, selectedChart);
        rememberSelection(checkboxToMetadataFile, selectedMetadata);

        this.clearWidgets();
        y = 40 - scrollOffset;

        // List all Inst* files as checkboxes
        renderFilesCheckboxes(song.instFiles(), checkboxToInstFile, selectedInst);

        y += 20;
        // List all Voices* files as checkboxes
        renderFilesCheckboxes(song.voicesFiles(), checkboxToVoicesFile, selectedVoices);

        y += 40;
        // List all chart files as checkboxes
        renderFilesCheckboxes(song.chartFiles(), checkboxToChartFile, selectedChart);

        // Advanced: metadata files. The server picks the chart's own metadata by itself,
        // so these are hidden unless the player wants to override that.
        if (!song.metadataFiles().isEmpty()) {
            y += 30;
            Button advancedButton = Button.builder(Component.nullToEmpty(showAdvanced ? "Advanced: hide" : "Advanced..."), (btn) -> {
                showAdvanced = !showAdvanced;
                init();
            }).bounds(x, y, 120, 20).build();
            this.addRenderableWidget(advancedButton);

            if (showAdvanced) {
                y += 4;
                // List all metadata files as checkboxes (none ticked = automatic)
                renderFilesCheckboxes(song.metadataFiles(), checkboxToMetadataFile, selectedMetadata);
            } else {
                checkboxToMetadataFile.clear(); // hidden, the ticked ones are remembered in selectedMetadata
            }
        }


        // Start button, asks the server for the song. It answers with the chart and the song starts once the audio is here.
        // In a VS match it picks the song (and side) for both players instead.
        boolean vs = ClientVs.state == VsMatchPayload.PICKING;
        Button startButton = Button.builder(Component.nullToEmpty(vs ? "Start VS" : "Start playing"), (btn) -> {
            List<Integer> instIds = selectedIds(checkboxToInstFile);
            List<Integer> voicesIds = selectedIds(checkboxToVoicesFile);
            List<Integer> chartIds = selectedIds(checkboxToChartFile);
            rememberSelection(checkboxToMetadataFile, selectedMetadata); // works whether the advanced options are open or not
            List<Integer> metadataIds = new ArrayList<>(selectedMetadata);
            metadataIds.sort(null);

            // can't play without a chart or without any audio
            if (chartIds.isEmpty() || (instIds.isEmpty() && voicesIds.isEmpty())) return;

            RequestSongPayload request = new RequestSongPayload(
                    packId,
                    songId,
                    instIds,
                    voicesIds,
                    chartIds.get(0), // only one chart can be played at a time, the first ticked one is used
                    metadataIds.isEmpty() ? -1 : metadataIds.get(0)
            );
            if (ClientVs.state == VsMatchPayload.PICKING) {
                ClientPlayNetworking.send(new VsPickPayload(request, ClientVs.pickBoyfriend));
            } else {
                ClientNetworking.requestSong(request);
            }
        }).bounds(200, 10, 120, 20).build();
        this.addRenderableWidget(startButton);

        // VS: which side the picker plays, the rival gets the other one
        if (vs) {
            Button sideButton = Button.builder(Component.nullToEmpty(sideText()), (btn) -> {
                ClientVs.pickBoyfriend = !ClientVs.pickBoyfriend;
                btn.setMessage(Component.nullToEmpty(sideText()));
            }).bounds(200, 34, 120, 20).build();
            this.addRenderableWidget(sideButton);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawString(this.font, "Choose options for this song:", 40, 40 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
//        context.drawString(this.font, "Instrumentals:", 40, y-120 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
//        context.drawString(this.font, "Voices:", 40, y-80 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
//        context.drawString(this.font, "Charting:", 40, y-40 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
//        context.drawString(this.font, "Metadata:", 40, y - this.font.lineHeight - 10, 0xFFFFFFFF, true);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int)(scrollY * 15);
        scrollOffset = Math.max(0, scrollOffset);
        init();
        return true;
    }


    private static String sideText() {
        return "Your side: " + (ClientVs.pickBoyfriend ? "Boyfriend" : "Opponent");
    }

    public void renderFilesCheckboxes(List<String> fileNames, Map<Checkbox, Integer> checkboxes, Set<Integer> selected) {
        checkboxes.clear();
        for (int i = 0; i < fileNames.size(); i++) {
            y += 20;
            Checkbox checkbox = Checkbox.builder(Component.literal(fileNames.get(i)), this.font)
                    .pos(x, y)
                    .selected(selected.contains(i))
                    .build();
            this.addRenderableWidget(checkbox);
            checkboxes.put(checkbox, i);
        }
    }

    // copies which checkboxes are ticked into the selected set (does nothing the first time, when there are no checkboxes yet)
    private static void rememberSelection(Map<Checkbox, Integer> checkboxes, Set<Integer> selected) {
        if (checkboxes.isEmpty()) return;
        selected.clear();
        selected.addAll(selectedIds(checkboxes));
    }

    // positions of the ticked files, in order
    private static List<Integer> selectedIds(Map<Checkbox, Integer> checkboxes) {
        List<Integer> ids = new ArrayList<>();
        for (Map.Entry<Checkbox, Integer> entry : checkboxes.entrySet()) {
            if (entry.getKey().selected()) ids.add(entry.getValue());
        }
        ids.sort(null);
        return ids;
    }
}
