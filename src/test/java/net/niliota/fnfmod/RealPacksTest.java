package net.niliota.fnfmod;

import net.niliota.fnfmod.Charting.BpmChange;
import net.niliota.fnfmod.Charting.ChartNote;
import net.niliota.fnfmod.Charting.ChartParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

// Runs every chart of every song in run/fnfpacks through the same steps the server does when a song is requested.
// Only runs on a machine that has packs in run/fnfpacks (they aren't in the repo, so it's skipped on GitHub).
// Prints a summary, and fails if something that parsed fine gives obviously broken bpm/speed info.
class RealPacksTest {

    // is this time on a 16th note of the bpm that's playing at that time
    private static boolean onGrid(double time, List<BpmChange> changes) {
        BpmChange current = changes.get(0);
        for (BpmChange change : changes) {
            if (change.time() > time) break;
            current = change;
        }
        double step = 60000.0 / current.bpm() / 4;
        double steps = (time - current.time()) / step;
        return Math.abs(steps - Math.round(steps)) * step < 3;
    }

    @Test
    void everyChartInEveryPack() throws IOException {
        Path packsDir = Path.of("run", "fnfpacks");
        Assumptions.assumeTrue(Files.isDirectory(packsDir), "run/fnfpacks not found, skipping");

        List<FNFPack> packs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(packsDir)) {
            int i = 0;
            for (Path path : stream) {
                if (Files.isDirectory(path)) packs.add(new FNFPack(++i, path.getFileName().toString(), path));
            }
        }

        int charts = 0, parsed = 0, withChanges = 0, noBpm = 0, noSpeed = 0;
        long notesAfterChange = 0, onGridWithChanges = 0, onGridWithout = 0;
        List<String> worseCharts = new ArrayList<>();
        List<String> driftingCharts = new ArrayList<>();
        List<String> notCharts = new ArrayList<>();

        for (FNFPack pack : packs) {
            for (FNFSong song : pack.songs) {
                for (Path chart : song.chartFiles) {
                    charts++;
                    assertFalse(chart.toString().toLowerCase().contains("autosave"), "autosaves should be skipped: " + chart.getFileName());

                    List<ChartNote> notes;
                    try {
                        notes = ChartParser.parseNotes(chart, song.engineType);
                    } catch (Exception e) {
                        // not every .json next to a song is a chart (events, dialogue, ...), the server just says it can't load those
                        notCharts.add(pack.name + " / " + song.name + " / " + chart.getFileName());
                        continue;
                    }
                    parsed++;

                    List<Path> dataFiles = new ArrayList<>();
                    dataFiles.add(chart);
                    dataFiles.add(song.metadataFiles.isEmpty() ? null : song.metadataFiles.get(0));

                    int bpm = ChartParser.getBpm(dataFiles);
                    double speed = ChartParser.getScrollSpeed(dataFiles);
                    List<Path> bpmFiles = new ArrayList<>(dataFiles); // same as SongSender
                    bpmFiles.add(ChartParser.findEventsFile(chart));
                    List<BpmChange> changes = ChartParser.getBpmChanges(bpmFiles, bpm);

                    if (bpm <= 0) noBpm++;
                    if (speed <= 0) noSpeed++;
                    // Charts without bpm changes should sit on their bpm's grid the whole way through.
                    // If one drifts off the grid partway, it might have a bpm change we don't know about.
                    if (changes.size() == 1 && notes.size() > 50) {
                        int firstHalf = 0, secondHalf = 0, half = notes.size() / 2;
                        for (int i = 0; i < notes.size(); i++) {
                            if (onGrid(notes.get(i).time, changes)) { if (i < half) firstHalf++; else secondHalf++; }
                        }
                        double first = (double) firstHalf / half, second = (double) secondHalf / (notes.size() - half);
                        if (first - second > 0.3) driftingCharts.add(String.format(Locale.ROOT, "%s / %s / %s (%.0f%% -> %.0f%%)",
                                pack.name, song.name, chart.getFileName(), first * 100, second * 100));
                    }

                    if (changes.size() > 1) {
                        withChanges++;

                        // Charters put notes on a beat grid. After a bpm change, notes should line up with the NEW bpm's grid,
                        // counted from where the change happens. If the change times were wrong, they wouldn't.
                        int chartWith = 0, chartWithout = 0;
                        for (ChartNote note : notes) {
                            if (note.time < changes.get(1).time()) continue;
                            notesAfterChange++;
                            if (onGrid(note.time, changes)) { onGridWithChanges++; chartWith++; }
                            if (onGrid(note.time, List.of(changes.get(0)))) { onGridWithout++; chartWithout++; }
                        }
                        if (chartWithout > chartWith) worseCharts.add(pack.name + " / " + song.name + " / " + chart.getFileName());
                    }

                    for (int i = 1; i < changes.size(); i++) {
                        assertTrue(changes.get(i).bpm() > 0, chart + " has a bpm change to " + changes.get(i).bpm());
                        assertTrue(changes.get(i).time() >= changes.get(i - 1).time(), chart + " bpm changes aren't sorted");
                    }
                    for (ChartNote note : notes) {
                        assertTrue(note.lane >= 0 && note.lane <= 3, chart + " has a note in lane " + note.lane);
                    }
                }
            }
        }

        System.out.println("BPM changes, notes after the first change that sit on the beat grid (16th notes, within 3 ms):");
        System.out.println(String.format(Locale.ROOT, "  using the detected bpm changes: %.1f%%   ignoring them: %.1f%%   (%d notes in %d charts)",
                100.0 * onGridWithChanges / Math.max(1, notesAfterChange), 100.0 * onGridWithout / Math.max(1, notesAfterChange),
                notesAfterChange, withChanges));
        System.out.println("  charts where ignoring the changes lines up better: " + worseCharts);
        assertTrue(onGridWithChanges > onGridWithout, "detected bpm changes should line notes up with the beat grid better");
        System.out.println("  charts without bpm changes that drift off their grid in the second half: " + driftingCharts);

        System.out.println("Charts: " + charts + ", parsed: " + parsed + ", with bpm changes: " + withChanges
                + ", no bpm found: " + noBpm + ", no speed found: " + noSpeed);
        System.out.println("Couldn't parse " + notCharts.size() + " files, first few: " + notCharts.subList(0, Math.min(15, notCharts.size())));
    }
}
