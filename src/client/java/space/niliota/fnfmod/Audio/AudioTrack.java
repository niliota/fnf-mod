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

    public void load(Path audioFile) {
        IntBuffer channels   = BufferUtils.createIntBuffer(1);
        IntBuffer sampleRate = BufferUtils.createIntBuffer(1);

        ShortBuffer rawAudio = STBVorbis.stb_vorbis_decode_filename(
                audioFile.toString(), channels, sampleRate
        );

        if (rawAudio == null) {
            System.err.println("Failed to load: " + audioFile);
            return;
        }

        int format = channels.get(0) == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;

        buffer = AL10.alGenBuffers();
        AL10.alBufferData(buffer, format, rawAudio, sampleRate.get(0));
        // OpenAL made its own copy, so free stb's decoded audio (stb allocates it with malloc, Java won't free it by itself)
        LibCStdlib.free(rawAudio);
        source = AL10.alGenSources();
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
    }

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

    public boolean isPlaying() {
        if (source == -1) return false;
        return AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }

}