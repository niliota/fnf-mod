package net.niliota.fnfmod.Screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.niliota.fnfmod.Audio.AudioManager;
import net.niliota.fnfmod.Audio.AudioTrack;
import net.niliota.fnfmod.Charting.Chart;
import net.niliota.fnfmod.Charting.ChartNote;
import net.niliota.fnfmod.Charting.ChartParser;
import net.niliota.fnfmod.FNFSong;
import net.niliota.fnfmod.Gameplay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.niliota.fnfmod.FNFmod.MOD_ID;

public class SongOptionsScreen extends Screen {
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public FNFSong song;

    public static List<Path> selectedAudioFiles = new ArrayList<>();
    private final Map<Checkbox, Path> checkboxToAudioFile = new HashMap<>();

    public static Path selectedChartFile;
    private final Map<Checkbox, Path> checkboxToChartFile = new HashMap<>();

    public static Path selectedMetadataFile;
    private final Map<Checkbox, Path> checkboxToMetadataFile = new HashMap<>();

    private int scrollOffset = 0;
    int x = 40; int y = 40;


    public SongOptionsScreen(Component title, FNFSong song) {
        super(title);
        this.song = song;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        y = 40 - scrollOffset;

        // List all Inst* files as checkboxes
        renderFilesCheckboxes(song.audioInstFiles, checkboxToAudioFile);

        y += 20;
        // List all Voices* files as checkboxes
        renderFilesCheckboxes(song.audioVoicesFiles, checkboxToAudioFile);

        y += 40;
        // List all chart files as checkboxes
        renderFilesCheckboxes(song.chartFiles, checkboxToChartFile);

        y += 20;
        // List all json/chart files as checkboxes
        renderFilesCheckboxes(song.metadataFiles, checkboxToMetadataFile);


        // Start button and everything related to starting the gameplay
        Button startButton = Button.builder(Component.nullToEmpty("Start playing"), (btn) -> {
            AudioManager.stopAllTracks();
            // reset the list of audio files
            selectedAudioFiles.clear();

            for (Map.Entry<Checkbox, Path> entry : checkboxToAudioFile.entrySet()) {
                if (entry.getKey().selected()) {
                    selectedAudioFiles.add(entry.getValue());
                }
            }


            for (Map.Entry<Checkbox, Path> entry : checkboxToChartFile.entrySet()) {
                if (entry.getKey().selected()) {
                    selectedChartFile = entry.getValue();
                }
            }

            for (Map.Entry<Checkbox, Path> entry : checkboxToMetadataFile.entrySet()) {
                if (entry.getKey().selected()) {
                    selectedMetadataFile = entry.getValue();
                }
            }

            List<AudioTrack> allTracks = new ArrayList<>();
            for (Path files : selectedAudioFiles) {
                AudioTrack voiceTrack = new AudioTrack();
                AudioManager.addTrack(voiceTrack);
                voiceTrack.load(files);

                // Add all voice tracks into a list that will then be given to AudioManager to be played at the same instant.
                allTracks.add(voiceTrack);
            }

            // Play all selected audio files
            AudioManager.playAll(allTracks);


            // start gameplay
            double scrollSpeed;
            List<ChartNote> chartNotes;
            int bpm;
            try {
                // It would have been smarter to use List.of(),
                // but it returns a NullPointerException if one of the files(namely the metadata file) is missing.
                List<Path> selectedDataFiles = new ArrayList<>();
                selectedDataFiles.add(selectedChartFile);
                selectedDataFiles.add(selectedMetadataFile);


                scrollSpeed = ChartParser.getScrollSpeed(selectedDataFiles);
                bpm = ChartParser.getBpm(selectedDataFiles);

                chartNotes = ChartParser.parseNotes(selectedChartFile, song.engineType);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Chart chart = new Chart(scrollSpeed, chartNotes, bpm);
            LOGGER.info(chart.toString());
            Gameplay.running = true;
            Gameplay.startGameplay(chart);


        }).bounds(200, 10, 120, 20).build();
        this.addRenderableWidget(startButton);
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


    public void renderFilesCheckboxes(List<Path> files, Map<Checkbox, Path> checkboxes) {
        boolean isFirst = true;
        for (Path file : files) {
            y += 20;
            String fileString = file.toString().split("/")[file.toString().split("/").length-1];
            Checkbox checkbox = Checkbox.builder(Component.literal(fileString), this.font)
                    .pos(x, y)
                    .selected(isFirst)
                    .build();
            this.addRenderableWidget(checkbox);
            checkboxes.put(checkbox, file);

            isFirst = false;
        }
    }
}