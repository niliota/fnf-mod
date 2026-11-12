package space.niliota.fnfmod.Charting;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// Used to parse charts into something intelligible(other Charting classes).
// Lives in the main source set so the server can parse charts too and only send the notes to clients.
//
// Supported chart formats:
//  * Codename       - "strumLines" array, each with its own "notes"
//  * V-Slice (v2)   - "notes" is an object with one array per difficulty, notes look like {t, d, l}
//  * Psych 1.0      - "format": "psych_v1...", sections where lanes 0-3 are ALWAYS the player
//  * Legacy/others  - sections where lanes 0-3 belong to whoever the section is focused on (mustHitSection)
//                     (sometimes wrapped inside a "song" object, like Indie Cross and NightmareVision)
public class    ChartParser {

    // Order in which difficulties are picked when a chart file has multiple of them inside.
    private static final String[] DIFFICULTY_PREFERENCE = {"hard", "normal", "easy"};

    // Parses both the player's and the opponent's notes, sorted by time.
    public static List<ChartNote> parseNotes(Path chartFile, String engineType) throws IOException {
        String raw = Files.readString(chartFile);
        JSONObject root = new JSONObject(raw);
        List<ChartNote> notes = new ArrayList<>();

        // Codename charts can be detected from the file itself, so we don't fully rely on engineType.
        if (engineType.equals("codename") || root.has("strumLines")) {
            parseCodename(root, notes);
        } else {
            // Older charts keep everything inside a "song" object. (psych 1.0 has "song" too, but it's just the name)
            JSONObject song = root.optJSONObject("song");
            if (song != null) root = song;

            Object rawNotes = root.get("notes");
            if (rawNotes instanceof JSONObject difficulties) { // V-Slice
                parseVSlice(pickDifficulty(difficulties, JSONArray.class), notes);
            } else { // Psych / legacy sections
                boolean absoluteLanes = root.optString("format", "").startsWith("psych_v1");
                parseSections((JSONArray) rawNotes, absoluteLanes, notes);
            }
        }

        // sort by time just in case
        notes.sort(Comparator.comparingDouble(n -> n.time));
        return notes;
    }

    private static void parseCodename(JSONObject root, List<ChartNote> notes) {
        JSONArray strumLines = root.getJSONArray("strumLines");

        for (int s = 0; s < strumLines.length(); s++) {
            JSONObject strumLine = strumLines.getJSONObject(s);

            // type 0 = opponent, type 1 = player, type 2 = gf
            int type = strumLine.getInt("type");
            if (type != 0 && type != 1) continue; // gf doesn't get to play

            JSONArray rawNotes = strumLine.getJSONArray("notes");

            for (int i = 0; i < rawNotes.length(); i++) {
                JSONObject n = rawNotes.getJSONObject(i);

                ChartNote note = new ChartNote();
                note.time     = n.getDouble("time");
                note.lane     = n.getInt("id");     // 0=left, 1=down, 2=up, 3=right
                note.length   = n.optDouble("sLen", 0);
                note.isPlayer = type == 1;

                notes.add(note);
            }
        }
    }

    private static void parseVSlice(JSONArray rawNotes, List<ChartNote> notes) {
        for (int i = 0; i < rawNotes.length(); i++) {
            JSONObject n = rawNotes.getJSONObject(i);
            int d = n.getInt("d");
            if (d > 7) continue; // skip anything that isn't a normal 4 key note

            ChartNote note = new ChartNote();
            note.time     = n.getDouble("t");
            note.lane     = d % 4;
            note.length   = n.optDouble("l", 0);
            note.isPlayer = d < 4; // 0-3 player, 4-7 opponent
            notes.add(note);
        }
    }

    // absoluteLanes = true:  lanes 0-3 are always the player (psych 1.0)
    // absoluteLanes = false: lanes 0-3 belong to whoever the section is focused on (older engines)
    private static void parseSections(JSONArray sections, boolean absoluteLanes, List<ChartNote> notes) {
        for (int s = 0; s < sections.length(); s++) {
            JSONObject section = sections.getJSONObject(s);
            boolean mustHit = section.optBoolean("mustHitSection", true);
            JSONArray sectionNotes = section.optJSONArray("sectionNotes");
            if (sectionNotes == null) continue;

            for (int i = 0; i < sectionNotes.length(); i++) {
                JSONArray n = sectionNotes.getJSONArray(i);
                int rawLane = n.getInt(1);
                if (rawLane < 0 || rawLane > 7) continue; // skip special notes like 24-27

                boolean firstHalf = rawLane < 4;

                ChartNote note = new ChartNote();
                note.time     = n.getDouble(0);
                note.lane     = rawLane % 4;
                note.length   = n.optDouble(2, 0);
                note.isPlayer = (absoluteLanes || mustHit) ? firstHalf : !firstHalf;
                notes.add(note);
            }
        }
    }

    // Some formats (V-Slice) store things per difficulty, like {"easy": ..., "normal": ..., "hard": ...}.
    // This picks the preferred difficulty, or the first one if none of the preferred ones exist.
    private static <T> T pickDifficulty(JSONObject difficulties, Class<T> type) {
        for (String difficulty : DIFFICULTY_PREFERENCE) {
            Object val = difficulties.opt(difficulty);
            if (type.isInstance(val)) return type.cast(val);
        }
        for (String key : difficulties.keySet()) {
            Object val = difficulties.get(key);
            if (type.isInstance(val)) return type.cast(val);
        }
        return null;
    }



    public static double getScrollSpeed(List<Path> files) throws IOException{
        if (files == null || files.isEmpty()) return 0;
        String raw;
        JSONObject root;
        double scrollSpeed;

        for (Path file : files) {
            if (file == null) continue;
            LOGGER.info(file.toString());

            raw = Files.readString(file);
            root = new JSONObject(raw);
            scrollSpeed = findKey(root, "scrollSpeed");
            if (scrollSpeed == -1) {
                scrollSpeed = findKey(root, "speed");
            }

            // If found, return. If not, continue over to the next file.
            if (scrollSpeed != -1) return scrollSpeed;
        }

        return 0;
    }

    public static int getBpm(List<Path> files) throws IOException {
        if (files == null || files.isEmpty()) return 0;

        String raw;
        JSONObject root;
        int bpm;

        for (Path file : files) {
            if (file == null) continue;
            LOGGER.info(file.toString());

            raw = Files.readString(file);
            root = new JSONObject(raw);
            bpm = (int)findKey(root, "bpm");

            // If found, return. If not, continue over to the next file.
            if (bpm != -1) return bpm;
        }

        return 0;
    }


    // This function "walks" through a JSON file to find the first key that matches the input.
    // If the value is per difficulty (like V-Slice's scrollSpeed), the preferred difficulty's value is used.
    public static double findKey(JSONObject json, String... keys) {
        for (String key : keys) {
            Object val = json.opt(key);
            if (val instanceof Number number) return number.doubleValue();
            if (val instanceof JSONObject difficulties) {
                Number number = pickDifficulty(difficulties, Number.class);
                if (number != null) return number.doubleValue();
            }
        }

        for (String k : json.keySet()) {
            Object val = json.get(k);
            if (val instanceof JSONObject nested) { // For regular JSON Objects
                double result = findKey(nested, keys);
                if (result != -1) return result;
            } else if (val instanceof JSONArray arr) { // If the item is an JSON Array
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof JSONObject nested) {
                        double result = findKey(nested, keys);
                        if (result != -1) return result;
                    }
                }
            }
        }

        return -1;
    }
}
