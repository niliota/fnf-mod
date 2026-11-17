package space.niliota.fnfmod;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import space.niliota.fnfmod.network.SongCatalogPayload;

import java.util.ArrayList;
import java.util.List;

import static space.niliota.fnfmod.FNFmod.LOGGER;

// The packs and songs the server we're connected to has. Filled in by SongCatalogPayload when joining,
// emptied again when leaving. The menus show these instead of the client's own fnfpacks/ folder.
// On a server without the mod, it's my own fnfpacks/ instead (setLocal), and songs get loaded on my side (ClientNetworking).
// Also turns the packs' icons into textures for the menus.
public class ClientSongCatalog {
    public static List<SongCatalogPayload.PackEntry> packs = new ArrayList<>();
    public static boolean received = false; // the server has the mod and sent its packs
    public static boolean local = false;    // the server doesn't have the mod, these are my own packs

    // the pack icons as textures, same order as packs (null = that pack has no icon)
    private static final List<ResourceLocation> icons = new ArrayList<>();

    public static void set(List<SongCatalogPayload.PackEntry> newPacks) {
        releaseIcons();
        packs = newPacks;
        received = true;
        local = false;

        for (int i = 0; i < packs.size(); i++) {
            icons.add(loadIcon(i, packs.get(i).icon()));
        }
    }

    // my own packs (the same list a server with the mod would send), for servers without the mod
    public static void setLocal() {
        set(SongCatalogPayload.fromPacks(PackManager.packs).packs());
        received = false;
        local = true;
    }

    public static void clear() {
        releaseIcons();
        packs = new ArrayList<>();
        received = false;
        local = false;
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
