package net.niliota.fnfmod.Audio;

// The song's current time in ms, smoothed out.
// OpenAL only updates the playback position in small jumps (every few ms), which would make notes stutter
// and hit timings a bit random. So instead we count the time ourselves every frame, and slowly pull our count
// towards the audio's position so it never drifts away from the music. Psych Engine does something similar.
public class SongClock {
    private static final double MAX_DRIFT = 50; // ms, if we're further off than this (lag spike), jump straight to the audio
    private static final double CORRECTION = 0.05; // how much of the difference to fix every frame

    private static double position = 0;
    private static long lastNanos = 0;
    private static boolean started = false;

    // call when a song starts
    public static void reset() {
        started = false;
    }

    // Call once per frame, returns the song's time in ms.
    public static double update(AudioTrack track) {
        long now = System.nanoTime();
        double audioPosition = track.getPositionMs();

        if (!started) {
            position = audioPosition;
            started = true;
        } else {
            position += (now - lastNanos) / 1_000_000.0; // time passed since last frame

            double drift = audioPosition - position;
            if (Math.abs(drift) > MAX_DRIFT) position = audioPosition;
            else position += drift * CORRECTION;
        }

        lastNanos = now;
        return position;
    }
}
