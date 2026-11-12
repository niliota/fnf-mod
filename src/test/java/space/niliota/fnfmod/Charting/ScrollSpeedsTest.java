package space.niliota.fnfmod.Charting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests for ScrollSpeeds: every engine's way of changing the scroll speed during a song, following each engine's source code.
class ScrollSpeedsTest {
    private static final List<BpmChange> BPM_150 = List.of(new BpmChange(0, 150)); // 100 ms per step

    @TempDir
    Path tempDir;

    private Path write(String relativePath, String json) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
        return file;
    }

    @Test
    void easingCurves() {
        for (String name : List.of("linear", "quadIn", "quadOut", "quadInOut", "cubeInOut", "quartOut", "sineInOut", "expoIn", "expoInOut", "circOut", "bounceOut")) {
            assertEquals(0, Easing.apply(name, 0), 1e-9, name + " starts at 0");
            assertEquals(1, Easing.apply(name, 1), 1e-9, name + " ends at 1");
        }
        assertEquals(0.75, Easing.apply("quadOut", 0.5), 1e-9);
        assertEquals(0.25, Easing.apply("quadIn", 0.5), 1e-9);
        assertEquals(0.5, Easing.apply("sineInOut", 0.5), 1e-9);
        assertEquals(0.3, Easing.apply("someUnknownEase", 0.3), 1e-9, "unknown eases act like linear");
    }

    @Test
    void newChangeStartsFromTheSpeedAtThatMoment() {
        // slide 2 -> 4 over 1000 ms, interrupted halfway by a slide to 1
        ScrollSpeeds speeds = new ScrollSpeeds(2, new ArrayList<>(List.of(
                new ScrollSpeeds.Change(1000, 4, 1000, "linear", ScrollSpeeds.BOTH, false),
                new ScrollSpeeds.Change(1500, 1, 1000, "linear", ScrollSpeeds.BOTH, false))));

        assertEquals(2, speeds.speedAt(999, true));
        assertEquals(3, speeds.speedAt(1500, true), 1e-9);  // halfway through the first slide
        assertEquals(2, speeds.speedAt(2000, true), 1e-9);  // halfway from 3 to 1
        assertEquals(1, speeds.speedAt(5000, true), 1e-9);
    }

    @Test
    void psychEventsChartEventsJsonAndEventNotes() throws IOException {
        // chart speed 2: x1.5 instantly at 1000 (chart events), x0.5 over 0.5 s at 3000 (events.json), x1 at 6000 (old event note)
        Path chart = write("data/song/song-hard.json", """
                {"song": {"song": "Song", "bpm": 150, "speed": 2,
                    "events": [[1000, [["Change Scroll Speed", "1.5", ""]]]],
                    "notes": [{"mustHitSection": true, "sectionNotes": [[6000, -1, "Change Scroll Speed", "1", "0"], [100, 0, 0]]}]
                }}
                """);
        write("data/song/events.json", """
                {"song": {"events": [[3000, [["Hey!", "", ""], ["Change Scroll Speed", ".5", ".5"]]]], "notes": []}}
                """);
        ScrollSpeeds speeds = ScrollSpeeds.read(chart, 2, BPM_150);

        assertEquals(3, speeds.changes.size());
        assertEquals(2, speeds.speedAt(999, true));
        assertEquals(3, speeds.speedAt(1000, true), 1e-9);
        assertEquals(2, speeds.speedAt(3250, true), 1e-9); // halfway from 3 to 1
        assertEquals(1, speeds.speedAt(4000, true), 1e-9);
        assertEquals(2, speeds.speedAt(7000, false), 1e-9);
    }

    @Test
    void psychV1RootEvents() throws IOException {
        Path chart = write("psych/chart.json", """
                {"format": "psych_v1", "song": "Test", "speed": 3, "notes": [], "events": [[500, [["Change Scroll Speed", 2, 0]]]]}
                """);
        assertEquals(6, ScrollSpeeds.read(chart, 3, BPM_150).speedAt(600, true), 1e-9);
    }

    @Test
    void codenameEvents() throws IOException {
        write("songs/x/events.json", """
                {"events": [{"name": "Scroll Speed Change", "time": 5000, "params": [false, 1.5]}]}
                """);
        // tween to 3 over 4 steps (400 ms at 150 bpm) with quad Out; then an instant change from events.json
        Path chart = write("songs/x/charts/hard.json", """
                {"strumLines": [], "scrollSpeed": 2, "events": [{"name": "Scroll Speed Change", "time": 1000, "params": [true, 3, 4, "quad", "Out", false]}]}
                """);
        ScrollSpeeds speeds = ScrollSpeeds.read(chart, 2, BPM_150);

        assertEquals(2, speeds.speedAt(1000, true), 1e-9);
        assertEquals(2 + 1 * 0.75, speeds.speedAt(1200, true), 1e-9); // quadOut(0.5) = 0.75
        assertEquals(3, speeds.speedAt(1400, true), 1e-9);
        assertEquals(1.5, speeds.speedAt(5000, true), 1e-9);
    }

    @Test
    void codenameModScriptWithBuiltInParams() throws IOException {
        // Friday Night Dustin's own "Change Scroll Speed" script: same params as Codename's built-in event
        Path chart = write("songs/z/charts/hard.json", """
                {"strumLines": [], "events": [
                    {"name": "Change Scroll Speed", "time": 1000, "params": [true, 1.7, 2, "linear", "In"]},
                    {"name": "Change Scroll Speed", "time": 3000, "params": [false, 2.8, 4, "linear", "In"]}
                ]}
                """);
        ScrollSpeeds speeds = ScrollSpeeds.read(chart, 2, BPM_150);

        assertEquals(1.85, speeds.speedAt(1100, true), 1e-9); // halfway through 2 steps (200 ms) from 2 to 1.7
        assertEquals(2.8, speeds.speedAt(3000, true), 1e-9);
    }

    @Test
    void codenameRelative() throws IOException {
        Path chart = write("songs/y/charts/hard.json", """
                {"strumLines": [], "events": [{"name": "Scroll Speed Change", "time": 0, "params": [false, 1.5, 4, "linear", "In", true]}]}
                """);
        assertEquals(3, ScrollSpeeds.read(chart, 2, BPM_150).speedAt(10, true), 1e-9);
    }

    @Test
    void vSliceEvents() throws IOException {
        // not absolute: 1.5 x the chart's 2 = 3, over 4 steps; absolute 1 instantly, only for the opponent
        Path chart = write("vslice/song-chart.json", """
                {"scrollSpeed": {"hard": 2}, "notes": {"hard": []}, "events": [
                    {"t": 1000, "e": "ScrollSpeed", "v": {"scroll": 1.5, "duration": 4, "ease": "cubeInOut", "absolute": false, "strumline": "both"}},
                    {"t": 3000, "e": "ScrollSpeed", "v": {"scroll": 1, "ease": "INSTANT", "absolute": true, "strumline": "opponent"}},
                    {"t": 4000, "e": "FocusCamera", "v": {"char": 0}}
                ]}
                """);
        ScrollSpeeds speeds = ScrollSpeeds.read(chart, 2, BPM_150);

        assertEquals(2.5, speeds.speedAt(1200, true), 1e-9); // cubeInOut(0.5) = 0.5
        assertEquals(3, speeds.speedAt(2000, true), 1e-9);
        assertEquals(3, speeds.speedAt(3500, true), 1e-9, "the player's speed doesn't change");
        assertEquals(1, speeds.speedAt(3500, false), 1e-9, "the opponent's does");
    }

    @Test
    void kadeEventObjects() throws IOException {
        // at beat 8 (8 x 400 ms = 3200 ms at 150 bpm) the speed gets multiplied by 1.5, at beat 16 again
        Path chart = write("kade/song.json", """
                {"song": {"bpm": 150, "speed": 2, "notes": [], "eventObjects": [
                    {"type": "BPM Change", "position": 0, "value": 150},
                    {"type": "Scroll Speed Change", "position": 8, "value": 1.5},
                    {"type": "Scroll Speed Change", "position": 16, "value": 1.5}
                ]}}
                """);
        ScrollSpeeds speeds = ScrollSpeeds.read(chart, 2, BPM_150);

        assertEquals(2, speeds.speedAt(3199, true), 1e-9);
        assertEquals(3, speeds.speedAt(3200, true), 1e-9);
        assertEquals(4.5, speeds.speedAt(6400, true), 1e-9);
    }

    @Test
    void noEventsMeansConstantSpeed() throws IOException {
        Path chart = write("plain/song.json", "{\"song\": {\"bpm\": 150, \"speed\": 2.5, \"notes\": []}}");
        ScrollSpeeds speeds = ScrollSpeeds.read(chart, 2.5, BPM_150);
        assertTrue(speeds.changes.isEmpty());
        assertEquals(2.5, speeds.speedAt(100000, true));
    }
}
