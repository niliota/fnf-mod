package net.niliota.fnfmod.Charting;

import java.util.List;

// From "time" (ms) on, the song plays at "bpm". A chart's list of these always starts at time 0.
public record BpmChange(double time, double bpm) {

    // the bpm at a point in the song, from a list sorted by time
    public static double bpmAt(List<BpmChange> changes, double time) {
        double bpm = changes.isEmpty() ? 0 : changes.get(0).bpm();
        for (BpmChange change : changes) {
            if (change.time() > time) break;
            bpm = change.bpm();
        }
        return bpm;
    }
}
