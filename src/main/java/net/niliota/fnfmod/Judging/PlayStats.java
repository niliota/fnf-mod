package net.niliota.fnfmod.Judging;

// Score, combo, health and accuracy of one player in one song.
// Works the same way as Psych Engine's PlayState (popUpScore, noteMissCommon, RecalculateRating, fullComboFunction).
public class PlayStats {
    public static final double MAX_HEALTH = 2.0;
    public static final double HIT_HEALTH = 0.02;  // health gained per hit (Note.hitHealth)
    public static final double MISS_HEALTH = 0.1;  // health lost per miss (Note.missHealth)

    public int score = 0;
    public int combo = 0;
    public int maxCombo = 0;
    public int misses = 0;
    public int sicks = 0, goods = 0, bads = 0, shits = 0;
    public double health = 1.0; // starts in the middle of the health bar

    // for accuracy: every hit adds its ratingMod, every hit or miss counts as "played"
    public int totalPlayed = 0;
    public double totalNotesHit = 0;

    // Psych's rating names, each one is used while the accuracy is below its number
    private static final Object[][] RATING_NAMES = {
            {"You Suck!", 0.2},
            {"Shit", 0.4},
            {"Bad", 0.5},
            {"Bruh", 0.6},
            {"Meh", 0.69},
            {"Nice", 0.7},
            {"Good", 0.8},
            {"Great", 0.9},
            {"Sick!", 1.0},
    };

    public void hit(Rating rating) {
        combo = Math.min(combo + 1, 9999);
        maxCombo = Math.max(maxCombo, combo);
        score += rating.score;
        totalNotesHit += rating.ratingMod;
        totalPlayed++;
        addHealth(HIT_HEALTH);

        switch (rating) {
            case SICK -> sicks++;
            case GOOD -> goods++;
            case BAD  -> bads++;
            case SHIT -> shits++;
        }
    }

    // damage = how much health to lose, normally MISS_HEALTH but missed holds take more
    public void miss(double damage) {
        combo = 0;
        score -= 10;
        misses++;
        totalPlayed++;
        addHealth(-damage);
    }

    private void addHealth(double amount) {
        health = Math.max(0, Math.min(MAX_HEALTH, health + amount));
    }

    public boolean isDead() {
        return health <= 0;
    }

    // from 0 to 1
    public double accuracy() {
        if (totalPlayed == 0) return 0;
        return Math.max(0, Math.min(1, totalNotesHit / totalPlayed));
    }

    // "Sick!", "Great", ... "?" before any note was played
    public String ratingName() {
        if (totalPlayed == 0) return "?";

        double accuracy = accuracy();
        if (accuracy >= 1) return "Perfect!!";
        for (Object[] ratingName : RATING_NAMES) {
            if (accuracy < (double) ratingName[1]) return (String) ratingName[0];
        }
        return "Perfect!!";
    }

    // SFC = only sicks, GFC = sicks and goods, FC = no misses, SDCB = under 10 misses, Clear = 10 or more
    public String fullComboLabel() {
        if (misses == 0) {
            if (bads > 0 || shits > 0) return "FC";
            if (goods > 0) return "GFC";
            if (sicks > 0) return "SFC";
            return "";
        }
        return misses < 10 ? "SDCB" : "Clear";
    }
}
