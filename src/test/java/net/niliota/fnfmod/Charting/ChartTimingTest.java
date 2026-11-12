package net.niliota.fnfmod.Charting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests for ChartTiming: every engine's way of storing bpm and bpm changes, following each engine's source code.
class ChartTimingTest {

    @TempDir
    Path tempDir;

    private Path write(String relativePath, String json) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
        return file;
    }

    // ---- Psych / base game / Kade ----

    @Test
    void psychChangeBpmSections() throws IOException {
        // 100 bpm = 600 ms per beat, the first section is 4 beats = 2400 ms, then it switches to 200 bpm.
        // The third section "changes" to 200 again, which isn't a real change. The 4th is 2 beats long (sectionBeats).
        Path chart = write("legacy.json", """
                {"song": {"bpm": 100, "notes": [
                    {"sectionNotes": []},
                    {"sectionNotes": [], "changeBPM": true, "bpm": 200},
                    {"sectionNotes": [], "changeBPM": true, "bpm": 200},
                    {"sectionNotes": [], "sectionBeats": 2},
                    {"sectionNotes": [], "changeBPM": true, "bpm": 50}
                ]}}
                """);
        ChartTiming timing = ChartTiming.read(chart, null);

        // 2400 + 2 sections of 4 beats at 300 ms + 2 beats at 300 ms = 2400 + 2400 + 600
        assertEquals(List.of(new BpmChange(0, 100), new BpmChange(2400, 200), new BpmChange(5400, 50)), timing.changes);
        assertEquals("chart", timing.source);
    }

    @Test
    void sectionBpmWithoutChangeBpmIsIgnored() throws IOException {
        // Psych's chart editor writes a bpm into every section, it only counts with "changeBPM": true
        Path chart = write("psych.json", """
                {"format": "psych_v1", "song": "Test", "bpm": 150, "notes": [{"sectionNotes": [], "bpm": 90}]}
                """);
        assertEquals(List.of(new BpmChange(0, 150)), ChartTiming.read(chart, null).changes);
    }

    @Test
    void fractionalBpm() throws IOException {
        Path chart = write("despair.json", "{\"song\": {\"bpm\": 187.5, \"notes\": []}}");
        assertEquals(187.5, ChartTiming.read(chart, null).startBpm);
    }

    @Test
    void kadeEventObjects() throws IOException {
        // position is in beats: 16 beats at 150 bpm (400 ms per beat) = 6400 ms
        Path chart = write("kade.json", """
                {"song": {"bpm": 150, "chartVersion": "KE1", "notes": [], "eventObjects": [
                    {"type": "BPM Change", "name": "Init BPM", "position": 0, "value": 150},
                    {"type": "BPM Change", "name": "faster", "position": 16, "value": 200}
                ]}}
                """);
        ChartTiming timing = ChartTiming.read(chart, null);
        assertEquals(List.of(new BpmChange(0, 150), new BpmChange(6400, 200)), timing.changes);
    }

    @Test
    void staleKadeEventObjectsAreIgnored() throws IOException {
        // Crunchin's order-up: eventObjects say 174, the chart says 150, and 150 is the one that fits the notes
        Path chart = write("order-up.json", """
                {"song": {"bpm": 150, "notes": [], "eventObjects": [
                    {"type": "BPM Change", "name": "0SM", "position": 0, "value": 174},
                    {"type": "BPM Change", "name": "x", "position": 8, "value": 200}
                ]}}
                """);
        assertEquals(List.of(new BpmChange(0, 150)), ChartTiming.read(chart, null).changes);
    }

    // ---- Codename ----

    @Test
    void codenameMetaAndEvents() throws IOException {
        write("songs/redscreen/meta.json", "{\"name\": \"redscreen\", \"bpm\": 160}");
        write("songs/redscreen/events.json", "{\"events\": [{\"name\": \"BPM Change\", \"time\": 194157.3, \"params\": [205]}]}");
        Path chart = write("songs/redscreen/charts/hard.json", """
                {"strumLines": [], "events": [
                    {"name": "Camera Movement", "time": 0, "params": [1]},
                    {"name": "BPM Change", "time": 16000, "params": [180]}
                ]}
                """);
        ChartTiming timing = ChartTiming.read(chart, null);

        assertEquals(160, timing.startBpm);
        assertEquals("meta", timing.source);
        assertEquals(List.of(new BpmChange(0, 160), new BpmChange(16000, 180), new BpmChange(194157.3, 205)), timing.changes);
    }

    @Test
    void codenameMetaPriority() throws IOException {
        // meta-<difficulty>.json comes before meta.json
        write("songs/x/meta.json", "{\"bpm\": 100}");
        write("songs/x/meta-hard.json", "{\"bpm\": 140}");
        Path hard = write("songs/x/charts/hard.json", "{\"strumLines\": []}");
        Path easy = write("songs/x/charts/easy.json", "{\"strumLines\": []}");

        assertEquals(140, ChartTiming.read(hard, null).startBpm);
        assertEquals(100, ChartTiming.read(easy, null).startBpm);
    }

    @Test
    void codenameVariantUsesItsOwnFiles() throws IOException {
        // charts/<variant>/<difficulty>.json reads meta-<variant>.json and ONLY events-<variant>.json
        write("songs/x/meta.json", "{\"bpm\": 100}");
        write("songs/x/meta-pico.json", "{\"bpm\": 120}");
        write("songs/x/events.json", "{\"events\": [{\"name\": \"BPM Change\", \"time\": 1000, \"params\": [300]}]}");
        write("songs/x/events-pico.json", "{\"events\": [{\"name\": \"BPM Change\", \"time\": 5000, \"params\": [130]}]}");
        Path chart = write("songs/x/charts/pico/hard.json", "{\"strumLines\": []}");

        assertEquals(List.of(new BpmChange(0, 120), new BpmChange(5000, 130)), ChartTiming.read(chart, null).changes);
    }

    @Test
    void codenameContinuousBpmChange() throws IOException {
        // 100 -> 200 bpm over 16 steps, starting at 1000 ms.
        // Codename: it ends at 1000 + 16 / (200 - 100) * ln(200 / 100) * 15000 ms
        write("songs/x/meta.json", "{\"bpm\": 100}");
        Path chart = write("songs/x/charts/hard.json", """
                {"strumLines": [], "events": [
                    {"name": "Continuous BPM Change", "time": 1000, "params": [200, 16]},
                    {"name": "BPM Change", "time": 1500, "params": [50]}
                ]}
                """);
        List<BpmChange> changes = ChartTiming.read(chart, null).changes;
        double rampEnd = 1000 + 16.0 / 100 * Math.log(2) * 15000;

        assertEquals(100, BpmChange.bpmAt(changes, 999));
        double middle = BpmChange.bpmAt(changes, 1000 + (rampEnd - 1000) / 2);
        assertTrue(middle > 100 && middle < 200, "halfway through the ramp the bpm is in between, was " + middle);
        assertEquals(200, BpmChange.bpmAt(changes, rampEnd + 1));
        assertFalse(changes.stream().anyMatch(c -> c.bpm() == 50), "events during a continuous change are ignored, like in Codename");
    }

    @Test
    void codenameWithoutMetaUsesDefault() throws IOException {
        Path chart = write("songs/x/charts/hard.json", "{\"strumLines\": []}");
        assertEquals(ChartTiming.DEFAULT_BPM, ChartTiming.read(chart, null).startBpm);
    }

    // ---- V-Slice ----

    @Test
    void vSliceVariationsGetTheirOwnMetadata() throws IOException {
        write("bopeebo/bopeebo-metadata.json", "{\"timeChanges\": [{\"t\": 0, \"bpm\": 100}]}");
        write("bopeebo/bopeebo-metadata-erect.json", "{\"timeChanges\": [{\"t\": 0, \"bpm\": 123}, {\"t\": 30000, \"bpm\": 130}]}");
        Path normal = write("bopeebo/bopeebo-chart.json", "{\"scrollSpeed\": {\"hard\": 2}, \"notes\": {\"hard\": []}}");
        Path erect = write("bopeebo/bopeebo-chart-erect.json", "{\"scrollSpeed\": {\"hard\": 2}, \"notes\": {\"hard\": []}}");

        assertEquals(100, ChartTiming.read(normal, null).startBpm);
        ChartTiming erectTiming = ChartTiming.read(erect, null);
        assertEquals(List.of(new BpmChange(0, 123), new BpmChange(30000, 130)), erectTiming.changes);
        assertEquals("metadata", erectTiming.source);
    }

    @Test
    void chosenMetadataWins() throws IOException {
        Path metadata = write("bopeebo/bopeebo-metadata.json", "{\"timeChanges\": [{\"t\": 0, \"bpm\": 100}]}");
        write("bopeebo/bopeebo-metadata-erect.json", "{\"timeChanges\": [{\"t\": 0, \"bpm\": 123}]}");
        Path erect = write("bopeebo/bopeebo-chart-erect.json", "{\"notes\": {\"hard\": []}}");

        assertEquals(100, ChartTiming.read(erect, metadata).startBpm, "the player picked this one, so it's used");
    }

    // ---- fallback ----

    @Test
    void unknownFormatFallsBackToSearching() throws IOException {
        // not a format we know, but there's a "bpm" somewhere: the old search (ChartParser.getBpm) still finds it
        Path chart = write("weird.json", "{\"song\": {\"notes\": [{\"sectionNotes\": [], \"bpm\": 140}]}}");
        ChartTiming timing = ChartTiming.read(chart, null);
        assertEquals(140, timing.startBpm);
        assertEquals("search", timing.source);

        Path nothing = write("nothing.json", "{\"song\": {\"notes\": []}}");
        assertEquals(ChartTiming.DEFAULT_BPM, ChartTiming.read(nothing, null).startBpm);
    }
}
