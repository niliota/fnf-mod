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

    // the time in ms of a beat, counting through every bpm change (for things placed in beats, like Kade's events)
    public static double timeAtBeat(List<BpmChange> changes, double beat) {
        double beatsBefore = 0; // beats from the start of the song to the current change
        for (int i = 0; i < changes.size(); i++) {
            BpmChange change = changes.get(i);
            double beatLength = 60000 / change.bpm();
            double end = i + 1 < changes.size() ? changes.get(i + 1).time() : Double.POSITIVE_INFINITY;
            double beatsInThisPart = (end - change.time()) / beatLength;

            if (beat <= beatsBefore + beatsInThisPart) return change.time() + (beat - beatsBefore) * beatLength;
            beatsBefore += beatsInThisPart;
        }
        return 0; // no changes at all
    }
}
