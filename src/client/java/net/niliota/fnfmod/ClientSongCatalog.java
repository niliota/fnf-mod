package net.niliota.fnfmod;

import net.niliota.fnfmod.network.SongCatalogPayload;

import java.util.ArrayList;
import java.util.List;

// The packs and songs the server we're connected to has. Filled in by SongCatalogPayload when joining,
// emptied again when leaving. The menus show these instead of the client's own fnfpacks/ folder.
public class ClientSongCatalog {
    public static List<SongCatalogPayload.PackEntry> packs = new ArrayList<>();
    public static boolean received = false; // false = the server doesn't have the mod (or hasn't answered yet)

    public static void set(List<SongCatalogPayload.PackEntry> newPacks) {
        packs = newPacks;
        received = true;
    }

    public static void clear() {
        packs = new ArrayList<>();
        received = false;
    }
}
