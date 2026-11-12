package net.niliota.fnfmod.Charting;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Finds a chart's bpm and every bpm change, reading them from wherever the chart's engine keeps them,
// the same way that engine does (checked against each engine's source code):
//
//  * Psych / base game / Kade / most others - "bpm" in the chart, changes in "changeBPM" sections
//      (Psych's Conductor.mapBPMChanges), or in Kade's "eventObjects" (Kade's Song.hx / TimingStruct.hx)
//  * Codename - "bpm" in the song's meta.json (meta-<variant>-<difficulty>.json, meta-<variant>.json,
//      meta-<difficulty>.json or meta.json, in that order), changes are "BPM Change" and "Continuous BPM Change"
//      events in the chart and in events.json (Codename's Chart.hx / Conductor.hx)
//  * V-Slice (base game 0.5+) - "timeChanges" in <song>-metadata[-variation].json next to <song>-chart[-variation].json
//
// If the chart is in a format none of these recognize, the bpm comes from ChartParser.getBpm() like before
// (searching every file for a "bpm" key), so nothing that worked before loses its bpm.
public class    ChartTiming {
    // engines use 100 when a chart has no bpm at all (Codename's and V-Slice's DEFAULT_BPM)
    public static final double DEFAULT_BPM = 100;
    // a "Continuous BPM Change" longer than this many steps gets cut short, so a broken chart can't make thousands of changes
    private static final int MAX_RAMP_STEPS = 2048;

    public double startBpm;
    public List<BpmChange> changes; // always starts at time 0 with startBpm, sorted by time
    public String source;           // where the bpm came from, for logs and tests
    public Path metadataFile;       // the metadata file that was used (the player's choice or the one found), can be null

    private ChartTiming(double startBpm, List<BpmChange> changes, String source) {
        this.startBpm = startBpm;
        this.changes = changes;
        this.source = source;
    }

    // chosenMetadata = the metadata file the player picked, or null to find the one that belongs to this chart
    public static ChartTiming read(Path chartFile, Path chosenMetadata) throws IOException {
        JSONObject chart = new JSONObject(Files.readString(chartFile));
        Path metadata = chosenMetadata != null ? chosenMetadata : findMetadata(chartFile, chart);

        ChartTiming timing;
        if (chart.has("strumLines")) {
            timing = readCodename(chartFile, chart, metadata);
        } else if (chart.opt("notes") instanceof JSONObject) {
            timing = readVSlice(metadata);
        } else {
            JSONObject song = chart.optJSONObject("song");
            timing = readSections(song != null ? song : chart);
        }

        // fallback: the old way, any "bpm" anywhere in the chart or metadata
        if (timing == null || timing.startBpm <= 0) {
            int bpm = ChartParser.getBpm(Arrays.asList(chartFile, metadata));
            double fallbackBpm = bpm > 0 ? bpm : DEFAULT_BPM;
            timing = new ChartTiming(fallbackBpm, cleanUp(fallbackBpm, new ArrayList<>()), bpm > 0 ? "search" : "default");
        }
        timing.metadataFile = metadata;
        return timing;
    }

    // ---- Psych / base game / Kade ----

    private static ChartTiming readSections(JSONObject song) {
        double startBpm = song.optDouble("bpm", 0);
        if (!(startBpm > 0)) return null;

        // Kade Engine keeps bpm changes in "eventObjects" (position in beats). Only trust them if their first one
        // matches the chart's bpm: Kade's editor keeps those two the same, so a mismatch means they're left over
        // from an older version of the chart (seen in Crunchin's order-up: 174 there, but 150 actually fits the notes).
        List<BpmChange> kade = readKadeEvents(song, startBpm);
        if (kade != null && kade.size() > 1) return new ChartTiming(startBpm, kade, "kade eventObjects");

        // Psych's Conductor.mapBPMChanges(): a section with "changeBPM" switches the bpm from where that section starts.
        // A section is "sectionBeats" beats long (older engines: "lengthInSteps" / 4), 4 if neither is there.
        List<BpmChange> changes = new ArrayList<>();
        JSONArray sections = song.optJSONArray("notes");
        if (sections != null) {
            double curBpm = startBpm;
            double totalPos = 0;
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.optJSONObject(i);
                if (section == null) continue;

                if (section.optBoolean("changeBPM", false) && section.optDouble("bpm", 0) > 0 && section.getDouble("bpm") != curBpm) {
                    curBpm = section.getDouble("bpm");
                    changes.add(new BpmChange(totalPos, curBpm));
                }

                double beats = section.has("sectionBeats") ? section.optDouble("sectionBeats", 4)
                             : section.has("lengthInSteps") ? section.optDouble("lengthInSteps", 16) / 4
                             : 4;
                totalPos += (60 / curBpm) * 1000 / 4 * Math.round(beats * 4);
            }
        }
        return new ChartTiming(startBpm, cleanUp(startBpm, changes), "chart");
    }

    // Kade's Song.hx / TimingStruct.hx: {"type": "BPM Change", "position": <beat>, "value": <bpm>}.
    // Returns null if there are none, or if they don't start at the chart's bpm (stale).
    private static List<BpmChange> readKadeEvents(JSONObject song, double startBpm) {
        JSONArray events = song.optJSONArray("eventObjects");
        if (events == null) return null;

        List<double[]> bpmEvents = new ArrayList<>(); // {beat, bpm}
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null || !"BPM Change".equals(event.optString("type"))) continue;
            double beat = event.optDouble("position", -1), bpm = event.optDouble("value", 0);
            if (beat >= 0 && bpm > 0) bpmEvents.add(new double[] {beat, bpm});
        }
        if (bpmEvents.isEmpty()) return null;
        bpmEvents.sort((a, b) -> Double.compare(a[0], b[0]));
        if (bpmEvents.get(0)[0] != 0 || bpmEvents.get(0)[1] != startBpm) return null;

        // beats -> ms: each segment lasts (its beats) at (its bpm)
        List<BpmChange> changes = new ArrayList<>();
        double time = 0;
        for (int i = 1; i < bpmEvents.size(); i++) {
            double[] previous = bpmEvents.get(i - 1), current = bpmEvents.get(i);
            time += (current[0] - previous[0]) * 60000 / previous[1];
            changes.add(new BpmChange(time, current[1]));
        }
        return cleanUp(startBpm, changes);
    }

    // ---- Codename ----

    private static ChartTiming readCodename(Path chartFile, JSONObject chart, Path metadata) throws IOException {
        double startBpm = 0;
        if (metadata != null) startBpm = new JSONObject(Files.readString(metadata)).optDouble("bpm", 0);
        String source = "meta";
        if (!(startBpm > 0)) {
            startBpm = DEFAULT_BPM; // Codename's default when meta.json has no bpm
            source = "default";
        }

        // events from the chart, plus the song's events.json (events-<variant>.json for variants, like Codename's loadEventsJson)
        List<JSONObject> events = new ArrayList<>();
        addEvents(chart.optJSONArray("events"), events);
        CodenameSong song = CodenameSong.of(chartFile);
        if (song != null) {
            Path eventsFile = song.folder.resolve(song.variant != null ? "events-" + song.variant + ".json" : "events.json");
            if (Files.exists(eventsFile)) addEvents(new JSONObject(Files.readString(eventsFile)).optJSONArray("events"), events);
        }
        events.sort((a, b) -> Double.compare(a.optDouble("time", 0), b.optDouble("time", 0)));

        // Codename's Conductor.mapEvent()
        List<BpmChange> changes = new ArrayList<>();
        double curBpm = startBpm;
        double rampEnd = -1; // events during a continuous change are ignored by Codename
        for (JSONObject event : events) {
            String name = event.optString("name");
            double time = event.optDouble("time", 0);
            JSONArray params = event.optJSONArray("params");
            if (params == null || time < rampEnd) continue;

            double bpm = params.optDouble(0, 0);
            if (!(bpm > 0) || bpm == curBpm) continue;

            if (name.equals("BPM Change")) {
                changes.add(new BpmChange(time, bpm));
                curBpm = bpm;
            } else if (name.equals("Continuous BPM Change")) {
                // params: target bpm, length in steps. The bpm goes up/down evenly step by step,
                // so it's cut into one change per step (good enough for anything that works in steps).
                int steps = (int) Math.min(Math.max(1, params.optDouble(1, 1)), MAX_RAMP_STEPS);
                double startOfRamp = curBpm;
                for (int s = 0; s < steps; s++) {
                    double bpmAtStep = startOfRamp + (bpm - startOfRamp) * (s + 0.5) / steps;
                    changes.add(new BpmChange(time + rampTime(startOfRamp, bpm, steps, s), bpmAtStep));
                }
                rampEnd = time + rampTime(startOfRamp, bpm, steps, steps);
                changes.add(new BpmChange(rampEnd, bpm));
                curBpm = bpm;
            }
        }
        return new ChartTiming(startBpm, cleanUp(startBpm, changes), source);
    }

    // ms from the start of a continuous change until step s, with the bpm going evenly from "from" to "to" over "steps" steps
    // (Codename: endSongTime = time + steps / (to - from) * ln(to / from) * 15000)
    private static double rampTime(double from, double to, int steps, int s) {
        double bpmAtS = from + (to - from) * s / steps;
        return 15000.0 * steps / (to - from) * Math.log(bpmAtS / from);
    }

    private static void addEvents(JSONArray array, List<JSONObject> into) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject event = array.optJSONObject(i);
            if (event != null) into.add(event);
        }
    }

    // ---- V-Slice ----

    private static ChartTiming readVSlice(Path metadata) throws IOException {
        if (metadata == null) return null;
        JSONObject meta = new JSONObject(Files.readString(metadata));
        JSONArray timeChanges = meta.optJSONArray("timeChanges");
        if (timeChanges == null || timeChanges.isEmpty()) return null;
        if (!meta.optString("timeFormat", "ms").equals("ms")) return null; // other formats exist on paper, nothing uses them

        List<BpmChange> changes = new ArrayList<>();
        for (int i = 0; i < timeChanges.length(); i++) {
            JSONObject change = timeChanges.optJSONObject(i);
            if (change != null && change.optDouble("bpm", 0) > 0) changes.add(new BpmChange(change.optDouble("t", 0), change.getDouble("bpm")));
        }
        if (changes.isEmpty()) return null;
        changes.sort((a, b) -> Double.compare(a.time(), b.time()));

        double startBpm = changes.get(0).bpm();
        return new ChartTiming(startBpm, cleanUp(startBpm, changes), "metadata");
    }

    // ---- finding the files that belong to a chart ----

    // The metadata file of this chart, or null if it doesn't need one / there isn't one.
    public static Path findMetadata(Path chartFile, JSONObject chart) {
        String fileName = chartFile.getFileName().toString();
        Path folder = chartFile.getParent();

        // V-Slice: <song>-chart[-variation].json -> <song>-metadata[-variation].json
        if (fileName.contains("-chart")) {
            Path metadata = folder.resolve(fileName.replace("-chart", "-metadata"));
            if (Files.exists(metadata)) return metadata;
        }

        // Codename: songs/<song>/charts/[<variant>/]<difficulty>.json -> songs/<song>/meta*.json (Codename's loadChartMeta order)
        CodenameSong song = chart.has("strumLines") ? CodenameSong.of(chartFile) : null;
        if (song != null) {
            List<String> names = new ArrayList<>();
            if (song.variant != null) {
                names.add("meta-" + song.variant + "-" + song.difficulty + ".json");
                names.add("meta-" + song.variant + ".json");
            }
            names.add("meta-" + song.difficulty + ".json");
            names.add("meta.json");
            for (String name : names) {
                if (Files.exists(song.folder.resolve(name))) return song.folder.resolve(name);
            }
        }
        return null;
    }

    // Where a Codename chart sits: songs/<song>/charts/<difficulty>.json, or songs/<song>/charts/<variant>/<difficulty>.json
    record CodenameSong(Path folder, String variant, String difficulty) {
        static CodenameSong of(Path chartFile) {
            String difficulty = chartFile.getFileName().toString().replaceFirst("\\.json$", "");
            Path folder = chartFile.getParent();
            if (folder == null) return null;

            if (folder.getFileName().toString().equals("charts")) return folder.getParent() == null ? null : new CodenameSong(folder.getParent(), null, difficulty);

            Path charts = folder.getParent(); // a variant folder inside charts/
            if (charts != null && charts.getFileName().toString().equals("charts") && charts.getParent() != null) {
                return new CodenameSong(charts.getParent(), folder.getFileName().toString(), difficulty);
            }
            return null;
        }
    }

    // Sorted, starting at 0 with startBpm, and without "changes" to the bpm it already is.
    private static List<BpmChange> cleanUp(double startBpm, List<BpmChange> changes) {
        changes.removeIf(change -> !(change.bpm() > 0));
        changes.sort((a, b) -> Double.compare(a.time(), b.time()));

        List<BpmChange> result = new ArrayList<>();
        result.add(new BpmChange(0, startBpm));
        for (BpmChange change : changes) {
            BpmChange last = result.get(result.size() - 1);
            if (change.bpm() == last.bpm()) continue;
            if (change.time() <= 0) result.set(result.size() - 1, new BpmChange(0, change.bpm())); // a different bpm right at the start
            else result.add(change);
        }
        return result;
    }
}
