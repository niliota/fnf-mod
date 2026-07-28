package net.niliota.fnfmod.Charting;

public class ChartNote {
    public double time;    // when it spawns in ms
    public int lane;       // 0=left, 1=down, 2=up, 3=right
    public double length;  // 0 if tap, >0 if hold
}