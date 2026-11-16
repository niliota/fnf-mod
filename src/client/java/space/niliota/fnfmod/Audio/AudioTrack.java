package space.niliota.fnfmod.Audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.libc.LibCStdlib;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;

public class AudioTrack {
    private int source = -1;
    private int buffer = -1;

    // A whole song decoded into raw sound, not handed to OpenAL yet.
    // Decoding is the slow part (seconds for a long song), so it can be done on another thread, see decode().
    public record Decoded(ShortBuffer samples, int channels, int sampleRate) {
        // stb allocates the decoded audio with malloc, Java won't free it by itself
        public void free() {
            LibCStdlib.free(samples);
        }
    }

    public void load(Path audioFile) {
        upload(decode(audioFile));
    }

    // the slow part: works on any thread. null if the file couldn't be decoded.
    public static Decoded decode(Path audioFile) {
        IntBuffer channels   = BufferUtils.createIntBuffer(1);
        IntBuffer sampleRate = BufferUtils.createIntBuffer(1);

        ShortBuffer rawAudio = STBVorbis.stb_vorbis_decode_filename(
                audioFile.toString(), channels, sampleRate
        );

        if (rawAudio == null) {
            System.err.println("Failed to load: " + audioFile);
            return null;
        }
        return new Decoded(rawAudio, channels.get(0), sampleRate.get(0));
    }

    // the fast part: hands the decoded sound to OpenAL (on the main thread), then frees it
    public void upload(Decoded decoded) {
        if (decoded == null) return;
        int format = decoded.channels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;

        buffer = AL10.alGenBuffers();
        AL10.alBufferData(buffer, format, decoded.samples(), decoded.sampleRate());
        // OpenAL made its own copy, so free the decoded audio
        decoded.free();
        source = AL10.alGenSources();
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
    }

    public boolean isLoaded() { return source != -1; }

    public void play()    { if (source != -1) AL10.alSourcePlay(source);  }
    public void pause()   { if (source != -1) AL10.alSourcePause(source); }
    public void resume()  { if (source != -1) AL10.alSourcePlay(source);  }

    public void destroy() {
        if (source != -1) { AL10.alSourceStop(source); AL10.alDeleteSources(source); source = -1; }
        if (buffer != -1) { AL10.alDeleteBuffers(buffer); buffer = -1; }
    }

    public long getPositionMs() {
        if (source == -1) return 0;
        return (long)(AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET) * 1000);
    }

    public void setPositionMs(long ms) {
        if (source != -1) AL10.alSourcef(source, AL11.AL_SEC_OFFSET, ms / 1000f);
    }

    public int getSource() { return source; }

    // 0 = silent, 1 = normal (watching someone from further away)
    public void setGain(float gain) { if (source != -1) AL10.alSourcef(source, AL10.AL_GAIN, gain); }

    public boolean isPlaying() {
        if (source == -1) return false;
        return AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }

}