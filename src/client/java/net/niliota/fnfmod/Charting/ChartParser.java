package net.niliota.fnfmod.Charting;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.niliota.fnfmod.FNFmod.LOGGER;

// Used to parse charts into something intelligible(other Charting classes).
public class ChartParser {

    public static List<ChartNote> parseNotes(Path chartFile, String engineType) throws IOException {
        String raw = Files.readString(chartFile);
        JSONObject root = new JSONObject(raw);
        List<ChartNote> notes = new ArrayList<>();

        switch (engineType) {
            case "codename":
                JSONArray strumLines = root.getJSONArray("strumLines");


                for (int s = 0; s < strumLines.length(); s++) {
                    JSONObject strumLine = strumLines.getJSONObject(s);

                    // type 0 = opponent, type 1 = player, type 2 = gf
                    int type = strumLine.getInt("type");
                    if (type != 1) continue; // only parse player notes

                    JSONArray rawNotes = strumLine.getJSONArray("notes");

                    for (int i = 0; i < rawNotes.length(); i++) {
                        JSONObject n = rawNotes.getJSONObject(i);

                        ChartNote note = new ChartNote();
                        note.time   = n.getDouble("time");
                        note.lane   = n.getInt("id");     // 0=left, 1=down, 2=up, 3=right
                        note.length = n.getDouble("sLen");

                        notes.add(note);
                    }
                }

                // sort by time just in case
                notes.sort(Comparator.comparingDouble(n -> n.time));

                LOGGER.info(notes.toArray().toString());
                return notes;

            default:
                LOGGER.info(chartFile.toString());
                JSONArray sections = root.getJSONArray("notes");

                for (int s = 0; s < sections.length(); s++) {
                    JSONObject section = sections.getJSONObject(s);
                    boolean mustHit = section.optBoolean("mustHitSection", true);
                    JSONArray sectionNotes = section.getJSONArray("sectionNotes");

                    for (int i = 0; i < sectionNotes.length(); i++) {
                        JSONArray n = sectionNotes.getJSONArray(i);
                        int rawLane = n.getInt(1);
                        if (rawLane > 3) continue; // skip special notes like 24-27

                        boolean isPlayer = mustHit ? rawLane < 4 : rawLane >= 4;
                        if (!isPlayer) continue;

                        ChartNote note = new ChartNote();
                        note.time   = n.getDouble(0);
                        note.lane   = mustHit ? rawLane : rawLane - 4;
                        note.length = n.getDouble(2);
                        notes.add(note);
                    }
                }

                notes.sort(Comparator.comparingDouble(n -> n.time));
                return notes;
        }
    }



    public static double getScrollSpeed(List<Path> files) throws IOException{
        if (files == null || files.isEmpty()) return 0;
        String raw;
        JSONObject root;
        double scrollSpeed;

        for (Path file : files) {
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

//        switch (engineType) {
//            case "codename":
//                raw = Files.readString(chartFile);
//                root = new JSONObject(raw);
//                bpm = root.getDouble("bpm");
//
//                return bpm;
//
//            case "psych":
//                raw = Files.readString(chartFile);
//                root = new JSONObject(raw);
//                bpm = root.getDouble("speed");
//
//                return bpm;
//
//            default:
//                return 0.0;
//        }
    }

    public static int getBpm(List<Path> files) throws IOException {
        if (files == null || files.isEmpty()) return 0;

        String raw;
        JSONObject root;
        int bpm;

        for (Path file : files) {
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
    public static double findKey(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key)) return json.getDouble(key);
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
