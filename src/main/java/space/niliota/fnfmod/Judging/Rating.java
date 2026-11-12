package space.niliota.fnfmod.Judging;

// The ratings a hit can get, with the same timings and points as Psych Engine (backend/Rating.hx + ClientPrefs.hx).
// A hit gets the first rating whose window the timing fits in, anything later than Bad (but still hittable) is a Shit.
public enum Rating {
    //    name      window(ms)  score  ratingMod
    SICK ("Sick!!", 45.0,       350,   1.0),
    GOOD ("Good",   90.0,       200,   0.67),
    BAD  ("Bad",    135.0,      100,   0.34),
    SHIT ("Shit",   NoteJudge.SAFE_ZONE, 50, 0.0);

    public final String displayName;
    public final double window;    // max distance from the note's time in ms
    public final int score;        // points given
    public final double ratingMod; // how much it counts towards accuracy (1 = 100%)

    Rating(String displayName, double window, int score, double ratingMod) {
        this.displayName = displayName;
        this.window = window;
        this.score = score;
        this.ratingMod = ratingMod;
    }

    // diff = how far off the hit was in ms (early or late doesn't matter)
    public static Rating judge(double diff) {
        diff = Math.abs(diff);
        if (diff <= SICK.window) return SICK;
        if (diff <= GOOD.window) return GOOD;
        if (diff <= BAD.window) return BAD;
        return SHIT;
    }
}
