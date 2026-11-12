package net.niliota.fnfmod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

// Registers every packet the mod sends. Both the client and the server need to know all of them,
// so this runs from the main entrypoint (which runs on both sides).
//
// How a song gets played:
//  1. player joins          server -> client  SongCatalogPayload   (list of packs, songs and file names)
//  2. player picks a song   client -> server  RequestSongPayload   (which files, by their position in the lists)
//  3. server parses chart   server -> client  SongDataPayload      (notes, bpm, speed and the hashes of the audio files)
//  4. client misses audio   client -> server  AudioRequestPayload  (hashes it doesn't have yet)
//  5. server sends audio    server -> client  AudioChunkPayload    (the audio files, in small pieces)
public class ModPayloads {
    public static void register() {
        // server -> client
        PayloadTypeRegistry.playS2C().register(SongCatalogPayload.TYPE, SongCatalogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SongDataPayload.TYPE, SongDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AudioChunkPayload.TYPE, AudioChunkPayload.CODEC);

        // client -> server
        PayloadTypeRegistry.playC2S().register(RequestSongPayload.TYPE, RequestSongPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AudioRequestPayload.TYPE, AudioRequestPayload.CODEC);
    }
}
