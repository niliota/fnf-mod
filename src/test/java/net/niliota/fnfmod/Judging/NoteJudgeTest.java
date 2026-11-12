package net.niliota.fnfmod.Judging;

import net.niliota.fnfmod.Charting.BpmChange;
import net.niliota.fnfmod.Charting.ChartNote;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests the hit detection against Psych Engine's numbers and rules.
class NoteJudgeTest {
    private static final boolean[] NOTHING_HELD = new boolean[4];
    private static final double BPM = 150; // step = 100 ms, makes hold math easy

    private static ChartNote note(double time, int lane, double length, boolean isPlayer) {
        ChartNote note = new ChartNote();
        note.time = time;
        note.lane = lane;
        note.length = length;
        note.isPlayer = isPlayer;
        return note;
    }

    private static NoteJudge judge(ChartNote... notes) {
        return new NoteJudge(new ArrayList<>(List.of(notes)), true, BPM);
    }

    private static boolean[] held(int lane) {
        boolean[] held = new boolean[4];
        held[lane] = true;
        return held;
    }

    @Test
    void ratingWindowsMatchPsych() {
        assertEquals(Rating.SICK, Rating.judge(0));
        assertEquals(Rating.SICK, Rating.judge(-45));
        assertEquals(Rating.GOOD, Rating.judge(46));
        assertEquals(Rating.GOOD, Rating.judge(90));
        assertEquals(Rating.BAD,  Rating.judge(-135));
        assertEquals(Rating.SHIT, Rating.judge(136));
        assertEquals(166.67, NoteJudge.SAFE_ZONE, 0.01);
    }

    @Test
    void onlyPlaysOneSide() {
        NoteJudge judge = judge(note(1000, 0, 0, true), note(1000, 1, 0, false));
        assertEquals(1, judge.notes.size());
    }

    @Test
    void hitsEarliestNoteInLane() {
        NoteJudge judge = judge(note(1000, 0, 0, true), note(1100, 0, 0, true), note(1000, 1, 0, true));

        JudgedNote hit = judge.press(0, 1050);
        assertEquals(1000, hit.note.time);
        assertEquals(Rating.GOOD, hit.rating); // 50 ms late
        assertEquals(200, judge.stats.score);
        assertEquals(1, judge.stats.combo);
    }

    @Test
    void ghostTappingDoesNothing() {
        NoteJudge judge = judge(note(1000, 0, 0, true));

        assertNull(judge.press(0, 500));  // way too early
        assertNull(judge.press(2, 1000)); // wrong lane
        assertEquals(0, judge.stats.misses);
        assertEquals(1.0, judge.stats.health);
    }

    @Test
    void hitWindowEdges() {
        NoteJudge judge = judge(note(1000, 0, 0, true), note(2000, 1, 0, true));

        assertNull(judge.press(0, 1000 - 167), "167 ms early is outside the window");
        assertNotNull(judge.press(0, 1000 - 166), "166 ms early is inside the window");
        assertEquals(Rating.SHIT, judge.notes.get(0).rating);

        assertNotNull(judge.press(1, 2000 + 166), "166 ms late is still hittable");
    }

    @Test
    void lateNoteIsAMiss() {
        NoteJudge judge = judge(note(1000, 0, 0, true));
        judge.stats.combo = 5;

        judge.update(1100, NOTHING_HELD);
        assertEquals(0, judge.stats.misses, "not late enough yet");

        judge.update(1200, NOTHING_HELD);
        assertEquals(1, judge.stats.misses);
        assertEquals(0, judge.stats.combo);
        assertEquals(-10, judge.stats.score);
        assertEquals(0.9, judge.stats.health, 1e-9);
        assertNull(judge.press(0, 1200), "a missed note can't be hit anymore");
    }

    @Test
    void holdHeldToTheEnd() {
        // 400 ms long at 100 ms per step = 4 pieces at 1000, 1100, 1200, 1300
        NoteJudge judge = judge(note(1000, 2, 400, true));
        JudgedNote hold = judge.notes.get(0);
        assertEquals(4, hold.pieceCount);

        judge.press(2, 1000);
        for (double t = 1000; t <= 1400; t += 16) judge.update(t, held(2));

        assertEquals(4, hold.piecesHit);
        assertTrue(hold.isDone());
        assertEquals(0, judge.stats.misses);
        assertEquals(350, judge.stats.score, "pieces don't give score");
        assertEquals(1.02, judge.stats.health, 1e-9, "pieces don't give health");
    }

    @Test
    void holdReleasedEarlyIsOneMiss() {
        NoteJudge judge = judge(note(1000, 2, 400, true));
        JudgedNote hold = judge.notes.get(0);

        judge.press(2, 1000);
        judge.update(1000, held(2));
        judge.update(1150, held(2)); // pieces at 1000 and 1100 are hit
        for (double t = 1166; t <= 2000; t += 16) judge.update(t, NOTHING_HELD);

        assertEquals(2, hold.piecesHit);
        assertTrue(hold.missed);
        assertEquals(1, judge.stats.misses, "dropping a hold is only one miss");
        assertEquals(1.02 - 0.1, judge.stats.health, 1e-9);
    }

    @Test
    void holdCanBeSavedByPressingAgainQuickly() {
        NoteJudge judge = judge(note(1000, 2, 400, true));
        JudgedNote hold = judge.notes.get(0);

        judge.press(2, 1000);
        judge.update(1000, held(2));
        judge.update(1150, NOTHING_HELD); // let go, piece at 1100 is waiting
        judge.update(1200, held(2));      // pressed again 100 ms later, still within the safe zone
        judge.update(1400, held(2));

        assertEquals(4, hold.piecesHit);
        assertEquals(0, judge.stats.misses);
    }

    @Test
    void missedHoldHeadCostsMoreHealth() {
        NoteJudge judge = judge(note(1000, 2, 400, true));
        judge.update(1500, NOTHING_HELD);

        assertEquals(1, judge.stats.misses);
        assertEquals(1.0 - 0.1 * 5, judge.stats.health, 1e-9); // 4 pieces + the head
    }

    @Test
    void duplicateNotesOnlyCountOnce() {
        NoteJudge judge = judge(note(1000, 0, 0, true), note(1000.5, 0, 0, true));

        judge.press(0, 1000);
        assertNull(judge.press(0, 1000), "the duplicate was thrown away");
        judge.update(2000, NOTHING_HELD);

        assertEquals(1, judge.stats.totalPlayed);
        assertEquals(0, judge.stats.misses);
    }

    @Test
    void holdsUseTheBpmAtTheirTime() {
        // 150 bpm (100 ms steps) until 5000 ms, then 300 bpm (50 ms steps)
        List<BpmChange> changes = List.of(new BpmChange(0, 150), new BpmChange(5000, 300));
        NoteJudge judge = new NoteJudge(List.of(note(1000, 0, 400, true), note(6000, 0, 400, true)), true, 150, changes);

        assertEquals(4, judge.notes.get(0).pieceCount);
        assertEquals(8, judge.notes.get(1).pieceCount);
    }

    @Test
    void opponentPlaysItself() {
        NoteJudge opponent = new NoteJudge(List.of(note(1000, 1, 400, false)), false, BPM);

        assertTrue(opponent.autoPlay(900).isEmpty(), "too early");
        assertEquals(1, opponent.autoPlay(1000).size());
        for (double t = 1000; t <= 1500; t += 16) opponent.autoPlay(t);

        assertTrue(opponent.notes.get(0).isDone());
        assertFalse(opponent.notes.get(0).missed);
    }

    @Test
    void listenerHearsMissesAndDroppedHolds() {
        NoteJudge judge = judge(note(1000, 0, 0, true), note(2000, 2, 400, true));
        java.util.List<String> heard = new java.util.ArrayList<>();
        judge.listener = new NoteJudge.Listener() {
            @Override public void onMiss(JudgedNote note) { heard.add("miss " + note.index); }
            @Override public void onHoldDropped(JudgedNote note) { heard.add("drop " + note.index); }
        };

        judge.update(1500, NOTHING_HELD); // note 0 missed
        judge.press(2, 2000);
        judge.update(2000, held(2));
        for (double t = 2016; t <= 2600; t += 16) judge.update(t, NOTHING_HELD); // let go of the hold

        assertEquals(java.util.List.of("miss 0", "drop 1"), heard);
    }

    @Test
    void rivalSideOnlyChangesThroughEvents() {
        NoteJudge rival = new NoteJudge(List.of(note(1000, 0, 0, false), note(2000, 1, 400, false), note(3000, 3, 0, false)), false, BPM);

        // way past every note: nothing is missed by itself (their hit might still be on its way over the network)
        rival.remoteUpdate(10000);
        assertEquals(0, rival.stats.misses);
        assertFalse(rival.notes.get(0).missed);

        assertNotNull(rival.applyHit(0, Rating.GOOD));
        assertNull(rival.applyHit(0, Rating.SICK), "the same hit twice does nothing");
        rival.applyMiss(2);
        assertEquals(200 - 10, rival.stats.score); // Good = 200, a miss = -10
        assertEquals(1, rival.stats.misses);

        // a hold they hit keeps going until a "dropped" comes in
        rival.applyHit(1, Rating.SICK);
        rival.remoteUpdate(2150);
        assertTrue(rival.notes.get(1).isBeingHeld());
        rival.applyHoldDropped(1);
        assertTrue(rival.notes.get(1).missed);
        assertEquals(2, rival.stats.misses);

        assertNull(rival.applyHit(99, Rating.SICK), "note numbers that don't exist are ignored");
    }

    @Test
    void notesAreNumberedTheSameOnBothSides() {
        List<ChartNote> chart = List.of(note(3000, 0, 0, true), note(1000, 1, 0, true), note(2000, 2, 0, false));
        NoteJudge mine = new NoteJudge(chart, true, BPM);
        NoteJudge theirsOfMe = new NoteJudge(chart, true, BPM); // how the rival's game sees my side

        for (int i = 0; i < mine.notes.size(); i++) {
            assertEquals(i, mine.notes.get(i).index);
            assertEquals(mine.notes.get(i).note.time, theirsOfMe.notes.get(i).note.time);
        }
    }

    @Test
    void accuracyAndLabels() {
        PlayStats stats = new PlayStats();
        assertEquals("?", stats.ratingName());

        stats.hit(Rating.SICK);
        assertEquals("Perfect!!", stats.ratingName());
        assertEquals("SFC", stats.fullComboLabel());

        stats.hit(Rating.GOOD); // (1 + 0.67) / 2 = 83.5%
        assertEquals(0.835, stats.accuracy(), 1e-9);
        assertEquals("Great", stats.ratingName());
        assertEquals("GFC", stats.fullComboLabel());

        stats.miss(PlayStats.MISS_HEALTH); // 1.67 / 3 = 55.7%
        assertEquals("Bruh", stats.ratingName());
        assertEquals("SDCB", stats.fullComboLabel());
    }

    @Test
    void healthStaysInBounds() {
        PlayStats stats = new PlayStats();
        for (int i = 0; i < 100; i++) stats.hit(Rating.SICK);
        assertEquals(PlayStats.MAX_HEALTH, stats.health);

        for (int i = 0; i < 30; i++) stats.miss(PlayStats.MISS_HEALTH);
        assertEquals(0, stats.health);
        assertTrue(stats.isDead());
    }
}
