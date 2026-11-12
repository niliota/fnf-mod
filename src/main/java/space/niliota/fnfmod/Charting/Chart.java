package space.niliota.fnfmod.Charting;

import java.util.ArrayList;
import java.util.List;

public class Chart {
    public double bpm;
    public double scrollSpeed;
    public List<ChartNote> notes = new ArrayList<>();
    public List<BpmChange> bpmChanges = new ArrayList<>(); // empty = the bpm never changes
    public ScrollSpeeds scrollSpeeds;                       // the speed at any point in the song (null = always scrollSpeed)

    public Chart(double scrollSpeed, List<ChartNote> notes, double bpm) {
        this.scrollSpeed = scrollSpeed;
        this.notes = notes;
        this.bpm = bpm;
    }

    @Override
    public String toString() {
        return "Chart{" +
                "bpm=" + bpm +
                ", speed=" + scrollSpeed +
                ", notes=" + notes +
                '}';
    }
}
