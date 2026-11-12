package net.niliota.fnfmod.Judging;

import net.niliota.fnfmod.Charting.ChartNote;

// A chart note while it's being played: was it hit, missed, how much of its hold is done.
//
// Holds work like Psych Engine's (with its default "guitar hero sustains" setting on):
// the tail is cut into pieces, one every step (a quarter of a beat). While the key is held, each piece gets hit
// when its time comes. Pieces only count if the head of the note was hit. Letting go too early counts as a single miss
// and the rest of the tail is dead. Pieces don't give score or health, only the head does.
public class JudgedNote {
    public final ChartNote note;
    public final int pieceCount;      // how many hold pieces this note has (0 = normal tap note)
    public final double stepCrochet;  // time between pieces in ms
    public int index;                 // position in its NoteJudge's list (the same on both players' games in VS)

    public boolean hit = false;       // the head was hit
    public boolean missed = false;    // the head was missed, or the hold was dropped
    public Rating rating;             // what the head was rated, null if not hit
    public int piecesHit = 0;         // hold pieces are hit in order, so this is all we need

    public JudgedNote(ChartNote note, double stepCrochet) {
        this.note = note;
        this.stepCrochet = stepCrochet;
        this.pieceCount = note.length > 0 ? (int) Math.round(note.length / stepCrochet) : 0;
    }

    public double pieceTime(int piece) {
        return note.time + stepCrochet * piece;
    }

    public boolean isHold() {
        return pieceCount > 0;
    }

    // the key is being held through this note's tail right now
    public boolean isBeingHeld() {
        return hit && !missed && piecesHit < pieceCount;
    }

    // nothing about this note can change anymore
    public boolean isDone() {
        return missed || (hit && piecesHit >= pieceCount);
    }
}
