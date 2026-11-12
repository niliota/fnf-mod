package net.niliota.fnfmod.Audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class AudioManager {
    public static List<AudioTrack> tracks = new ArrayList<>();

    public static void addTrack(AudioTrack track) {
        tracks.add(track);
    }

    public static void stopAllTracks() {
        for (AudioTrack track : tracks) {
            track.destroy();
        }
        // forget the destroyed tracks, otherwise the next song would read its timing from a dead track
        tracks.clear();
    }

    // pause/resume every track at the same instant, so they stay in sync (pause menu)
    public static void pauseAll() {
        if (!tracks.isEmpty()) AL10.alSourcePausev(sourcesOf(tracks));
    }

    public static void resumeAll() {
        if (!tracks.isEmpty()) AL10.alSourcePlayv(sourcesOf(tracks));
    }

    private static IntBuffer sourcesOf(List<AudioTrack> tracks) {
        IntBuffer sources = BufferUtils.createIntBuffer(tracks.size());
        for (AudioTrack track : tracks) {
            sources.put(track.getSource());
        }
        sources.flip();
        return sources;
    }

    // to play multiple audio tracks at the same instant
    public static void playAll(List<AudioTrack> tracks) {
        IntBuffer sources = BufferUtils.createIntBuffer(tracks.size());
        for (AudioTrack track : tracks) {
            sources.put(track.getSource());
        }
        sources.flip();
        AL10.alSourcePlayv(sources);
    }
}
