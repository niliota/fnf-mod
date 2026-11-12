package space.niliota.fnfmod.Charting;

// A single note from a chart. Both the player's and the opponent's notes use this, isPlayer tells them apart.
public class ChartNote {
    public double time;      // when it should be hit in ms
    public int lane;         // 0=left, 1=down, 2=up, 3=right
    public double length;    // 0 if tap, >0 if hold
    public boolean isPlayer; // true = boyfriend's side, false = opponent's side

    @Override
    public String toString() {
        return "ChartNote{" +
                "time=" + time +
                ", lane=" + lane +
                ", length=" + length +
                ", isPlayer=" + isPlayer +
                '}';
    }
}
