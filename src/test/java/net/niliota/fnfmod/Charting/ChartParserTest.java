package net.niliota.fnfmod.Charting;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests for ChartParser. Each supported chart format gets a tiny handmade chart,
// and there's one extra test that runs against the real charts in "../example chartings" if that folder exists
// (it isn't part of the repo, so it gets skipped on GitHub).
class ChartParserTest {

    @TempDir
    Path tempDir;

    // writes a chart into a temporary file and parses it
    private List<ChartNote> parse(String json, String engineType) throws IOException {
        Path file = tempDir.resolve("chart.json");
        Files.writeString(file, json);
        return ChartParser.parseNotes(file, engineType);
    }

    private static long countPlayer(List<ChartNote> notes)   { return notes.stream().filter(n -> n.isPlayer).count(); }
    private static long countOpponent(List<ChartNote> notes) { return notes.stream().filter(n -> !n.isPlayer).count(); }

    @Test
    void legacySectionsFollowMustHitSection() throws IOException {
        // section 1 is the player's, section 2 is the opponent's, so lanes 0-3 swap owners between them
        String json = """
                {"song": {"bpm": 150, "speed": 2.5, "notes": [
                    {"mustHitSection": true,  "sectionNotes": [[100, 0, 0], [200, 5, 50]]},
                    {"mustHitSection": false, "sectionNotes": [[300, 1, 0], [400, 6, 0], [500, 26, 0]]}
                ]}}
                """;
        List<ChartNote> notes = parse(json, "psych");

        assertEquals(4, notes.size(), "lane 26 is a special note and should be skipped");

        assertTrue(notes.get(0).isPlayer);  assertEquals(0, notes.get(0).lane);
        assertFalse(notes.get(1).isPlayer); assertEquals(1, notes.get(1).lane); assertEquals(50, notes.get(1).length);
        assertFalse(notes.get(2).isPlayer); assertEquals(1, notes.get(2).lane);
        // this one used to get dropped: player note (lane 6) inside an opponent section
        assertTrue(notes.get(3).isPlayer);  assertEquals(2, notes.get(3).lane);
    }

    @Test
    void psychV1LanesAreAbsolute() throws IOException {
        String json = """
                {"format": "psych_v1", "song": "Test", "bpm": 120, "speed": 2, "notes": [
                    {"mustHitSection": false, "sectionNotes": [[100, 0, 0], [200, 4, 0]]}
                ]}
                """;
        List<ChartNote> notes = parse(json, "psych");

        assertTrue(notes.get(0).isPlayer, "lanes 0-3 are always the player in psych 1.0");
        assertFalse(notes.get(1).isPlayer);
    }

    @Test
    void codenameStrumLines() throws IOException {
        String json = """
                {"scrollSpeed": 2.6, "strumLines": [
                    {"type": 0, "notes": [{"id": 1, "time": 100, "sLen": 0}]},
                    {"type": 1, "notes": [{"id": 2, "time": 50, "sLen": 120}]},
                    {"type": 2, "notes": [{"id": 3, "time": 75, "sLen": 0}]}
                ]}
                """;
        List<ChartNote> notes = parse(json, "codename");

        assertEquals(2, notes.size(), "gf's strumline should be skipped");
        assertTrue(notes.get(0).isPlayer);  assertEquals(2, notes.get(0).lane); assertEquals(120, notes.get(0).length);
        assertFalse(notes.get(1).isPlayer); assertEquals(1, notes.get(1).lane);
    }

    @Test
    void vSlicePicksHardestDifficulty() throws IOException {
        String json = """
                {"scrollSpeed": {"easy": 1.6, "hard": 1.8}, "notes": {
                    "easy": [{"t": 100, "d": 0}],
                    "hard": [{"t": 100, "d": 7}, {"t": 50, "d": 1, "l": 250}]
                }}
                """;
        List<ChartNote> notes = parse(json, "funkin");

        assertEquals(2, notes.size());
        assertTrue(notes.get(0).isPlayer);  assertEquals(1, notes.get(0).lane); assertEquals(250, notes.get(0).length);
        assertFalse(notes.get(1).isPlayer); assertEquals(3, notes.get(1).lane);
    }

    @Test
    void scrollSpeedAndBpm() throws IOException {
        Path vSlice = tempDir.resolve("vslice.json");
        Files.writeString(vSlice, "{\"scrollSpeed\": {\"easy\": 1.6, \"hard\": 1.8}, \"notes\": {}}");
        Path metadata = tempDir.resolve("metadata.json");
        Files.writeString(metadata, "{\"timeChanges\": [{\"t\": 0, \"bpm\": 120}]}");
        Path legacy = tempDir.resolve("legacy.json");
        Files.writeString(legacy, "{\"song\": {\"bpm\": 150, \"speed\": 2.5, \"notes\": []}}");

        assertEquals(1.8, ChartParser.getScrollSpeed(List.of(vSlice)));
        assertEquals(120, ChartParser.getBpm(List.of(vSlice, metadata)), "bpm should be found in the metadata file");
        assertEquals(2.5, ChartParser.getScrollSpeed(List.of(legacy)));
        assertEquals(150, ChartParser.getBpm(List.of(legacy)));
    }

    @Test
    void bpmChangesLegacySections() throws IOException {
        // 100 bpm = 600 ms per beat, the first section is 4 beats = 2400 ms, then it switches to 200 bpm.
        // The third section "changes" to 200 again, which isn't a real change. The 4th is 2 beats long (sectionBeats).
        Path chart = tempDir.resolve("legacy.json");
        Files.writeString(chart, """
                {"song": {"bpm": 100, "notes": [
                    {"sectionNotes": []},
                    {"sectionNotes": [], "changeBPM": true, "bpm": 200},
                    {"sectionNotes": [], "changeBPM": true, "bpm": 200},
                    {"sectionNotes": [], "sectionBeats": 2},
                    {"sectionNotes": [], "changeBPM": true, "bpm": 50}
                ]}}
                """);
        List<BpmChange> changes = ChartParser.getBpmChanges(List.of(chart), 100);

        // 2400 + 2 sections of 4 beats at 300 ms + 2 beats at 300 ms = 2400 + 2400 + 600
        assertEquals(List.of(new BpmChange(0, 100), new BpmChange(2400, 200), new BpmChange(5400, 50)), changes);
        assertEquals(200, BpmChange.bpmAt(changes, 3000));
        assertEquals(100, BpmChange.bpmAt(changes, 0));
    }

    @Test
    void bpmChangesCodenameEvents() throws IOException {
        Path chart = tempDir.resolve("codename.json");
        Files.writeString(chart, """
                {"strumLines": [], "events": [
                    {"name": "Camera Movement", "time": 0, "params": [1]},
                    {"name": "BPM Change", "time": 16000, "params": [180]}
                ]}
                """);
        assertEquals(List.of(new BpmChange(0, 120), new BpmChange(16000, 180)),
                ChartParser.getBpmChanges(List.of(chart), 120));
    }

    @Test
    void bpmChangesCodenameEventsFile() throws IOException {
        // songs/<song>/charts/hard.json + songs/<song>/events.json
        Path charts = Files.createDirectories(tempDir.resolve("redscreen").resolve("charts"));
        Path chart = charts.resolve("hard.json");
        Files.writeString(chart, "{\"strumLines\": [], \"events\": []}");
        Files.writeString(tempDir.resolve("redscreen").resolve("events.json"),
                "{\"events\": [{\"name\": \"BPM Change\", \"time\": 194157.3, \"params\": [205]}]}");

        Path events = ChartParser.findEventsFile(chart);
        assertNotNull(events);
        assertEquals(205, BpmChange.bpmAt(ChartParser.getBpmChanges(java.util.Arrays.asList(chart, null, events), 160), 200000));
    }

    @Test
    void psychEventsFileIsIgnored() throws IOException {
        Path chart = tempDir.resolve("hard.json");
        Files.writeString(chart, "{\"song\": {\"bpm\": 150, \"notes\": []}}");
        Path events = tempDir.resolve("events.json");
        Files.writeString(events, "{\"song\": {\"events\": [[1000, [[\"Change Scroll Speed\", \"2\", \"\"]]]], \"notes\": []}}");

        assertEquals(List.of(new BpmChange(0, 150)), ChartParser.getBpmChanges(List.of(chart, events), 150));
    }

    @Test
    void bpmChangesVSliceMetadata() throws IOException {
        Path chart = tempDir.resolve("chart.json");
        Files.writeString(chart, "{\"scrollSpeed\": {\"hard\": 2}, \"notes\": {\"hard\": []}}");
        Path metadata = tempDir.resolve("metadata.json");
        Files.writeString(metadata, "{\"timeChanges\": [{\"t\": 0, \"bpm\": 120}, {\"t\": 5000, \"bpm\": 150}]}");

        List<BpmChange> changes = ChartParser.getBpmChanges(List.of(chart, metadata), 120);
        assertEquals(150, BpmChange.bpmAt(changes, 6000));
        assertEquals(120, BpmChange.bpmAt(changes, 4999));
    }

    @Test
    void noBpmChanges() throws IOException {
        Path chart = tempDir.resolve("plain.json");
        Files.writeString(chart, "{\"song\": {\"bpm\": 150, \"notes\": [{\"sectionNotes\": []}]}}");
        assertEquals(List.of(new BpmChange(0, 150)), ChartParser.getBpmChanges(List.of(chart), 150));
    }

    // Real charts from different engines. Expected numbers were counted straight from the json files.
    @Test
    void exampleChartings() throws IOException {
        Path dir = Path.of("..", "example chartings");
        Assumptions.assumeTrue(Files.isDirectory(dir), "example chartings folder not found, skipping");

        assertSides(dir.resolve("codename - overthrone - hard.json"), "codename", 1059, 863);
        assertSides(dir.resolve("funkinv2-roses-chart.json"),         "funkin",   234,  231);
        assertSides(dir.resolve("psych-steve-hard.json"),             "psych",    218,  240);
        assertSides(dir.resolve("nightmarevision-smile-hard.json"),   "unknown",  229,  266);
        assertSides(dir.resolve("indie-despair-hard.json"),           "unknown",  1131, 1364);
    }

    private static void assertSides(Path chart, String engineType, long player, long opponent) throws IOException {
        Assumptions.assumeTrue(Files.exists(chart), chart + " not found, skipping");
        List<ChartNote> notes = ChartParser.parseNotes(chart, engineType);

        assertEquals(player,   countPlayer(notes),   chart.getFileName() + " player notes");
        assertEquals(opponent, countOpponent(notes), chart.getFileName() + " opponent notes");
        for (int i = 1; i < notes.size(); i++) {
            assertTrue(notes.get(i - 1).time <= notes.get(i).time, "notes should be sorted by time");
        }
    }
}
