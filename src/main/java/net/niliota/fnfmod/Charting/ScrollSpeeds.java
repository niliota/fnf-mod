package net.niliota.fnfmod.Charting;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Scroll speed changes during a song, read the way each engine does it (checked against their source code):
//
//  * Psych - "Change Scroll Speed" events: value1 = multiplier of the chart's speed, value2 = seconds to slide there (linear).
//      Events can be in the chart, in data/<song>/events.json, or as old-style "event notes" in sections (lane below 0).
//  * Codename - "Scroll Speed Change" events, params: [tween?, speed, length in steps, ease, ease direction, relative?].
//      In the chart and in the song's events.json (PlayState.hx). Mods' own "Change Scroll Speed" scripts with the same params count too.
//  * V-Slice - "ScrollSpeed" events: {scroll, duration (steps), ease, absolute, strumline}. Not absolute = times the chart's speed.
//      Can change only the player's or only the opponent's notes (ScrollSpeedEvent.hx).
//  * Kade - "Scroll Speed Change" in eventObjects: at a beat, the speed gets multiplied by the value.
//
// A new change starts from whatever the speed is at that moment, even halfway through another one (like the engines' tweens).
public class ScrollSpeeds {
    public static final int BOTH = 0, PLAYER = 1, OPPONENT = 2;

    // from "time" on, the speed goes to "target" over "duration" ms with the "ease" curve.
    // relative = target is a multiplier of the speed at that moment (Codename's relative option, Kade)
    public record Change(double time, double target, double duration, String ease, int side, boolean relative) {}

    public final double baseSpeed;
    public final List<Change> changes;

    public ScrollSpeeds(double baseSpeed, List<Change> changes) {
        this.baseSpeed = baseSpeed;
        this.changes = changes;
        this.changes.sort((a, b) -> Double.compare(a.time(), b.time()));
    }

    // The scroll speed at a point in the song, for the player's (player = true) or the opponent's notes.
    public double speedAt(double time, boolean player) {
        Change active = null;
        double activeFrom = baseSpeed; // the speed when the active change started

        for (Change change : changes) {
            if (change.time() > time) break;
            if (change.side() == (player ? OPPONENT : PLAYER)) continue; // only for the other side

            double speedNow = active == null ? baseSpeed : valueOf(active, activeFrom, change.time());
            active = change;
            activeFrom = speedNow;
        }
        return active == null ? baseSpeed : valueOf(active, activeFrom, time);
    }

    private static double valueOf(Change change, double from, double time) {
        double target = change.relative() ? from * change.target() : change.target();
        if (change.duration() <= 0 || time >= change.time() + change.duration()) return target;

        double progress = (time - change.time()) / change.duration();
        return from + (target - from) * Easing.apply(change.ease(), progress);
    }

    // ---- reading ----

    // baseSpeed = the chart's own speed (ChartParser.getScrollSpeed), bpmChanges = from ChartTiming (for events measured in steps or beats)
    public static ScrollSpeeds read(Path chartFile, double baseSpeed, List<BpmChange> bpmChanges) throws IOException {
        JSONObject chart = new JSONObject(Files.readString(chartFile));
        List<Change> changes = new ArrayList<>();

        if (chart.has("strumLines")) {
            readCodename(chartFile, chart, bpmChanges, changes);
        } else if (chart.opt("notes") instanceof JSONObject) {
            readVSlice(chart, baseSpeed, bpmChanges, changes);
        } else {
            JSONObject song = chart.optJSONObject("song");
            JSONObject songData = song != null ? song : chart;
            readPsych(songData, baseSpeed, changes);
            readKade(songData, bpmChanges, changes);

            // Psych also loads data/<song>/events.json, which sits next to the charts
            Path eventsFile = chartFile.resolveSibling("events.json");
            if (Files.exists(eventsFile) && !eventsFile.equals(chartFile)) {
                JSONObject events = new JSONObject(Files.readString(eventsFile));
                JSONObject eventsSong = events.optJSONObject("song");
                readPsych(eventsSong != null ? eventsSong : events, baseSpeed, changes);
            }
        }
        return new ScrollSpeeds(baseSpeed, changes);
    }

    // Psych: "events": [[time, [[name, value1, value2], ...]], ...], older: [time, name, value1, value2],
    // oldest: event notes inside sections [time, -1, name, value1, value2]
    private static void readPsych(JSONObject song, double baseSpeed, List<Change> into) {
        JSONArray events = song.optJSONArray("events");
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONArray event = events.optJSONArray(i);
                if (event == null || event.length() < 2) continue;
                double time = event.optDouble(0, 0);

                JSONArray subEvents = event.optJSONArray(1);
                if (subEvents != null) {
                    for (int j = 0; j < subEvents.length(); j++) {
                        JSONArray sub = subEvents.optJSONArray(j);
                        if (sub != null) addPsych(time, sub.opt(0), sub.opt(1), sub.opt(2), baseSpeed, into);
                    }
                } else {
                    addPsych(time, event.opt(1), event.opt(2), event.opt(3), baseSpeed, into);
                }
            }
        }

        JSONArray sections = song.optJSONArray("notes");
        if (sections == null) return;
        for (int s = 0; s < sections.length(); s++) {
            JSONObject section = sections.optJSONObject(s);
            JSONArray notes = section == null ? null : section.optJSONArray("sectionNotes");
            if (notes == null) continue;
            for (int i = 0; i < notes.length(); i++) {
                JSONArray note = notes.optJSONArray(i);
                if (note != null && note.length() > 2 && note.optDouble(1, 0) < 0) {
                    addPsych(note.optDouble(0, 0), note.opt(2), note.opt(3), note.opt(4), baseSpeed, into);
                }
            }
        }
    }

    // PlayState.triggerEvent "Change Scroll Speed": new speed = chart speed * value1, sliding over value2 seconds (linear)
    private static void addPsych(double time, Object name, Object value1, Object value2, double baseSpeed, List<Change> into) {
        if (!"Change Scroll Speed".equals(name)) return;
        double multiplier = psychFloat(value1, 1);
        double seconds = psychFloat(value2, 0);
        into.add(new Change(time, baseSpeed * multiplier, seconds * 1000, "linear", BOTH, false));
    }

    // Psych's values are usually text like "1.05" or ".2" (Std.parseFloat), empty or broken = the default
    private static double psychFloat(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (!Double.isNaN(parsed)) return parsed;
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    // Kade: {"type": "Scroll Speed Change", "position": <beat>, "value": <multiplier>}
    private static void readKade(JSONObject song, List<BpmChange> bpmChanges, List<Change> into) {
        JSONArray events = song.optJSONArray("eventObjects");
        if (events == null) return;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null || !"Scroll Speed Change".equals(event.optString("type"))) continue;
            double value = event.optDouble("value", 0);
            if (!(value > 0)) continue;
            double time = BpmChange.timeAtBeat(bpmChanges, event.optDouble("position", 0));
            into.add(new Change(time, value, 0, "linear", BOTH, true));
        }
    }

    // Codename: chart events + events.json (events-<variant>.json for variants), like ChartTiming
    private static void readCodename(Path chartFile, JSONObject chart, List<BpmChange> bpmChanges, List<Change> into) throws IOException {
        List<JSONObject> events = new ArrayList<>();
        addObjects(chart.optJSONArray("events"), events);
        ChartTiming.CodenameSong song = ChartTiming.CodenameSong.of(chartFile);
        if (song != null) {
            Path eventsFile = song.folder().resolve(song.variant() != null ? "events-" + song.variant() + ".json" : "events.json");
            if (Files.exists(eventsFile)) addObjects(new JSONObject(Files.readString(eventsFile)).optJSONArray("events"), events);
        }

        for (JSONObject event : events) {
            JSONArray params = event.optJSONArray("params");
            if (params == null || params.length() < 2) continue;

            // Codename's built-in "Scroll Speed Change", or a mod's own "Change Scroll Speed" script with the same params
            // (Friday Night Dustin has one: [tween?, speed, steps, ease, ease direction], made when porting from Psych)
            String name = event.optString("name");
            boolean builtIn = name.equals("Scroll Speed Change");
            boolean sameAsBuiltIn = name.equals("Change Scroll Speed") && params.opt(0) instanceof Boolean;
            if (!builtIn && !sameAsBuiltIn) continue;

            double time = event.optDouble("time", 0);
            double speed = params.optDouble(1, 0);
            if (!(speed > 0)) continue;

            boolean tween = params.optBoolean(0, false);
            double steps = params.optDouble(2, 4);
            String ease = params.optString(3, "linear") + params.optString(4, "");
            if (ease.toLowerCase().startsWith("linear")) ease = "linear";
            boolean relative = params.optBoolean(5, false);

            double duration = tween ? steps * stepLength(bpmChanges, time) : 0;
            into.add(new Change(time, speed, duration, ease, BOTH, relative));
        }
    }

    // V-Slice: "events": [{"t": time, "e": "ScrollSpeed", "v": {...}}]
    private static void readVSlice(JSONObject chart, double baseSpeed, List<BpmChange> bpmChanges, List<Change> into) {
        JSONArray events = chart.optJSONArray("events");
        if (events == null) return;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null || !"ScrollSpeed".equals(event.optString("e"))) continue;
            JSONObject v = event.optJSONObject("v");
            if (v == null) v = new JSONObject();

            double time = event.optDouble("t", 0);
            double scroll = v.optDouble("scroll", 1);
            if (!v.optBoolean("absolute", false)) scroll *= baseSpeed; // ScrollSpeedEvent: scroll * the chart's speed
            if (!(scroll > 0)) continue;

            // ease is like "expoIn", or "expo" + easeDir ("In" if missing), or "linear", or "INSTANT"
            String ease = v.optString("ease", "linear");
            boolean instant = ease.equals("INSTANT");
            if (!ease.equalsIgnoreCase("linear") && !ease.matches("(?i).*(In|Out|InOut)$")) ease += v.optString("easeDir", "In");

            double duration = instant ? 0 : v.optDouble("duration", 4) * stepLength(bpmChanges, time);
            int side = switch (v.optString("strumline", "both")) {
                case "player" -> PLAYER;
                case "opponent" -> OPPONENT;
                default -> BOTH;
            };
            into.add(new Change(time, scroll, duration, ease, side, false));
        }
    }

    // ms per step (a quarter of a beat) at that time
    private static double stepLength(List<BpmChange> bpmChanges, double time) {
        double bpm = bpmChanges.isEmpty() ? ChartTiming.DEFAULT_BPM : BpmChange.bpmAt(bpmChanges, time);
        return 15000 / (bpm > 0 ? bpm : ChartTiming.DEFAULT_BPM);
    }

    private static void addObjects(JSONArray array, List<JSONObject> into) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) into.add(object);
        }
    }
}
