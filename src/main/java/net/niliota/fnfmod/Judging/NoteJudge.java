package net.niliota.fnfmod.Judging;

import net.niliota.fnfmod.Charting.BpmChange;
import net.niliota.fnfmod.Charting.ChartNote;

import java.util.ArrayList;
import java.util.List;

// Decides what happens when keys are pressed and held during a song, following Psych Engine's rules (PlayState.hx).
// Doesn't know anything about Minecraft, the client feeds it key presses and the song's time every frame.
//
//  * A note can be hit from SAFE_ZONE ms before its time until SAFE_ZONE ms after it.
//  * Pressing a key hits the earliest hittable note in that lane. Pressing with no note there does nothing (ghost tapping).
//  * A note that gets more than SAFE_ZONE ms late without being hit is a miss.
//  * Holds: see JudgedNote.
public class NoteJudge {
    // Psych's "safe frames": 10 frames at 60 fps = 166.67 ms
    public static final double SAFE_ZONE = 10.0 / 60.0 * 1000.0;

    public final List<JudgedNote> notes = new ArrayList<>(); // only the notes of the side being played, sorted by time
    public final PlayStats stats = new PlayStats();

    // every note before this one is done, so we don't have to look at them anymore
    private int firstActive = 0;

    // Told about misses and dropped holds as they happen (VS sends them to the rival, see ClientVs)
    public interface Listener {
        void onMiss(JudgedNote note);
        void onHoldDropped(JudgedNote note);
    }
    public Listener listener;

    // playerSide = true to play boyfriend's notes, false for the opponent's
    public NoteJudge(List<ChartNote> chartNotes, boolean playerSide, double bpm) {
        this(chartNotes, playerSide, bpm, List.of());
    }

    // Same, but holds use the bpm at their own time if the song changes bpm.
    public NoteJudge(List<ChartNote> chartNotes, boolean playerSide, double bpm, List<BpmChange> bpmChanges) {
        for (ChartNote note : chartNotes) {
            if (note.isPlayer != playerSide) continue;

            double noteBpm = bpmChanges.isEmpty() ? bpm : BpmChange.bpmAt(bpmChanges, note.time);
            // Psych cuts holds into steps (a quarter of a beat). Charts without a bpm get 100 so it still works.
            double stepCrochet = 60000.0 / (noteBpm > 0 ? noteBpm : 100) / 4.0;
            notes.add(new JudgedNote(note, stepCrochet));
        }
        notes.sort((a, b) -> Double.compare(a.note.time, b.note.time));

        // number the notes, so two players' games can talk about the same note (VS)
        for (int i = 0; i < notes.size(); i++) notes.get(i).index = i;
    }

    // A key was pressed. Returns the note that got hit, or null if nothing was there.
    public JudgedNote press(int lane, double songPosition) {
        JudgedNote target = null;

        for (int i = firstActive; i < notes.size(); i++) {
            JudgedNote n = notes.get(i);
            if (n.note.time >= songPosition + SAFE_ZONE) break; // this and every later note is still too far away

            boolean canHit = !n.hit && !n.missed && n.note.lane == lane && n.note.time > songPosition - SAFE_ZONE;
            if (canHit) {
                target = n; // notes are sorted, so the first one found is the earliest
                break;
            }
        }

        if (target == null) return null; // ghost tap

        target.hit = true;
        target.rating = Rating.judge(target.note.time - songPosition);
        stats.hit(target.rating);
        removeDuplicates(target);
        return target;
    }

    // Plays by itself, perfectly, like the opponent in FNF: every note is hit right on time and every hold is held.
    // Returns the notes that got hit this frame (to make the strums glow). Doesn't touch the stats.
    public List<JudgedNote> autoPlay(double songPosition) {
        List<JudgedNote> hits = new ArrayList<>();
        for (int i = firstActive; i < notes.size(); i++) {
            JudgedNote n = notes.get(i);
            if (n.note.time > songPosition) break;

            if (!n.hit && !n.missed) {
                n.hit = true;
                n.rating = Rating.SICK;
                hits.add(n);
                removeDuplicates(n);
            }
        }

        update(songPosition, new boolean[] {true, true, true, true});
        return hits;
    }

    // Called every frame. held[lane] = is that key held down right now.
    public void update(double songPosition, boolean[] held) {
        for (int i = firstActive; i < notes.size(); i++) {
            JudgedNote n = notes.get(i);
            if (n.note.time > songPosition) break; // nothing here or after can be late or hold yet

            // head got too late without being hit
            if (!n.hit && !n.missed && n.note.time < songPosition - SAFE_ZONE) {
                n.missed = true;
                // a missed hold costs more the longer it is (guitar hero sustains)
                stats.miss(PlayStats.MISS_HEALTH * (n.pieceCount + 1));
                removeDuplicates(n);
                if (listener != null) listener.onMiss(n);
            }

            // hold pieces, in order
            while (n.isBeingHeld()) {
                double pieceTime = n.pieceTime(n.piecesHit);
                if (pieceTime > songPosition) break; // the next piece's time hasn't come yet

                if (held[n.note.lane]) {
                    n.piecesHit++;
                } else if (pieceTime < songPosition - SAFE_ZONE) {
                    // let go too early and didn't press again in time, the rest of the tail is dead
                    n.missed = true;
                    stats.miss(PlayStats.MISS_HEALTH);
                    if (listener != null) listener.onHoldDropped(n);
                } else {
                    break; // can still be saved by pressing the key again
                }
            }
        }

        // skip over the notes at the start that are done
        while (firstActive < notes.size() && notes.get(firstActive).isDone()) {
            firstActive++;
        }
    }

    // ---- the rival's side in VS ----
    // Their game decides what happened and sends it over, these just apply it. Nothing here decides a miss by itself,
    // so a hit that arrives a bit late over the network never shows up as a fake miss.

    public JudgedNote applyHit(int index, Rating rating) {
        JudgedNote n = noteAt(index);
        if (n == null || n.hit || n.missed) return null;
        n.hit = true;
        n.rating = rating;
        stats.hit(rating);
        removeDuplicates(n);
        return n;
    }

    public void applyMiss(int index) {
        JudgedNote n = noteAt(index);
        if (n == null || n.hit || n.missed) return;
        n.missed = true;
        stats.miss(PlayStats.MISS_HEALTH * (n.pieceCount + 1));
        removeDuplicates(n);
    }

    public void applyHoldDropped(int index) {
        JudgedNote n = noteAt(index);
        if (n == null || !n.hit || n.missed) return;
        n.missed = true;
        stats.miss(PlayStats.MISS_HEALTH);
    }

    // Every frame: holds that were hit keep going until their end, unless a "dropped" comes in.
    public void remoteUpdate(double songPosition) {
        for (int i = firstActive; i < notes.size(); i++) {
            JudgedNote n = notes.get(i);
            if (n.note.time > songPosition) break;
            while (n.isBeingHeld() && n.pieceTime(n.piecesHit) <= songPosition) n.piecesHit++;
        }
        while (firstActive < notes.size() && notes.get(firstActive).isDone()) {
            firstActive++;
        }
    }

    private JudgedNote noteAt(int index) {
        return index >= 0 && index < notes.size() ? notes.get(index) : null;
    }

    // Some charts have the same note twice on top of each other. Psych throws the extra ones away
    // when one of them is hit or missed, so they don't count twice.
    private void removeDuplicates(JudgedNote judged) {
        for (int i = firstActive; i < notes.size(); i++) {
            JudgedNote n = notes.get(i);
            if (n.note.time > judged.note.time + 1) break;

            if (n != judged && !n.hit && !n.missed && n.note.lane == judged.note.lane
                    && Math.abs(n.note.time - judged.note.time) < 1) {
                n.hit = true;               // done, but without a rating, so it doesn't count anywhere
                n.piecesHit = n.pieceCount;
            }
        }
    }
}
