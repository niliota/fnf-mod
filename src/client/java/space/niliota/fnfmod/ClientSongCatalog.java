package space.niliota.fnfmod;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import space.niliota.fnfmod.network.ClientVs;
import space.niliota.fnfmod.network.SongCatalogPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// The packs and songs the menus show. Two lists:
//  * the server's: filled in by SongCatalogPayload when joining, emptied again when leaving
//  * my own fnfpacks/: on servers without the mod, or when "My songs" is picked at the top of the Mic menu.
//    Songs then get loaded on my side and nothing goes to the server (ClientNetworking).
// "My songs" is remembered per server (FnfOptions.ownSongsServers). In a VS match it's always the server's songs.
// In singleplayer both are the same, the built-in server reads my fnfpacks/ too.
// Also turns the packs' icons into textures for the menus.
public class ClientSongCatalog {
    public static List<SongCatalogPayload.PackEntry> packs = new ArrayList<>(); // what the menus show right now
    public static boolean received = false; // the server has the mod and sent its packs
    public static boolean local = false;    // the server doesn't have the mod, only my own songs

    private static List<SongCatalogPayload.PackEntry> serverPacks = new ArrayList<>();
    private static List<SongCatalogPayload.PackEntry> ownPacks = null; // made the first time they're needed
    private static Boolean showingOwnPacks = null; // which list "packs" (and the icons) are right now

    // the pack icons as textures, same order as packs (null = that pack has no icon)
    private static final List<ResourceLocation> icons = new ArrayList<>();

    // the server's packs came in
    public static void set(List<SongCatalogPayload.PackEntry> newPacks) {
        serverPacks = newPacks;
        received = true;
        local = false;
        showingOwnPacks = null;
        refresh();
    }

    // the server doesn't have the mod: my own packs
    public static void setLocal() {
        received = false;
        local = true;
        refresh();
    }

    public static void clear() {
        releaseIcons();
        packs = new ArrayList<>();
        serverPacks = new ArrayList<>();
        received = false;
        local = false;
        showingOwnPacks = null;
    }

    // true if the menus show (and play) my own songs
    public static boolean showingOwn() {
        if (local) return true;
        if (!received || ClientVs.inMatch()) return false;
        String server = serverKey();
        return server != null && FnfOptions.ownSongsServers.contains(server);
    }

    // the Server songs / My songs switch is only there on servers that have the mod (not in singleplayer)
    public static boolean canChoose() {
        return received && serverKey() != null && !ClientVs.inMatch();
    }

    public static void chooseOwn(boolean own) {
        String server = serverKey();
        if (server == null) return;
        if (own) FnfOptions.ownSongsServers.add(server);
        else FnfOptions.ownSongsServers.remove(server);
        FnfOptions.save();
        refresh();
    }

    // makes "packs" the list that should be shown (called when a menu opens)
    public static void refresh() {
        boolean own = showingOwn();
        if (showingOwnPacks != null && showingOwnPacks == own) return;

        if (own && ownPacks == null) ownPacks = SongCatalogPayload.fromPacks(PackManager.packs).packs();
        releaseIcons();
        packs = own ? ownPacks : serverPacks;
        showingOwnPacks = own;
        for (int i = 0; i < packs.size(); i++) {
            icons.add(loadIcon(i, packs.get(i).icon()));
        }
    }

    // the server we're on, as typed in the server list ("play.example.com"), null in singleplayer
    private static String serverKey() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null || Minecraft.getInstance().isLocalServer()) return null;
        return server.ip.trim().toLowerCase(Locale.ROOT);
    }

    // the icon of a pack, or null if it has none
    public static ResourceLocation icon(int packId) {
        return packId >= 0 && packId < icons.size() ? icons.get(packId) : null;
    }

    private static ResourceLocation loadIcon(int packId, byte[] png) {
        if (png.length == 0) return null;
        try {
            NativeImage image = NativeImage.read(png);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("fnfmod", "pack_icon/" + packId);
            Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
            return location;
        } catch (Exception e) {
            LOGGER.warn("Couldn't load the icon of pack {}", packId, e);
            return null;
        }
    }

    private static void releaseIcons() {
        for (ResourceLocation icon : icons) {
            if (icon != null) Minecraft.getInstance().getTextureManager().release(icon);
        }
        icons.clear();
    }
}
