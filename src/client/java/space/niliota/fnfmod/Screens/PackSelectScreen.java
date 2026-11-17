package space.niliota.fnfmod.Screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import space.niliota.fnfmod.ClientSongCatalog;
import space.niliota.fnfmod.Gameplay;
import space.niliota.fnfmod.network.ClientVs;
import space.niliota.fnfmod.network.SongCatalogPayload;

import java.util.ArrayList;
import java.util.List;

// First menu (the Mic menu): lists the FNF mods (packs) the server has, plus Options and VS.
// Opened by right-clicking with the Mic, or with the menu key (O by default).
// On servers with the mod, "Server songs" / "My songs" at the top picks whose packs to play (see ClientSongCatalog).
// Typing in the search box finds packs and songs from every pack at once.
public class PackSelectScreen extends FnfMenuScreen {
    public List<SongCatalogPayload.PackEntry> packs;


    public PackSelectScreen(Component title) {
        super(Component.literal("Blocky Friday"));
    }

    @Override
    protected void init() {
        ClientSongCatalog.refresh();
        this.packs = ClientSongCatalog.packs;

        // Server songs / My songs, the picked one is yellow
        topRow = 0;
        if (ClientSongCatalog.canChoose()) {
            topRow = 24;
            boolean own = ClientSongCatalog.showingOwn();
            int width = Math.min(100, (this.width - 24) / 2);
            this.addRenderableWidget(Button.builder(choice("Server songs", !own), btn -> pick(false))
                    .bounds(this.width / 2 - width - 2, 20, width, 20).build());
            this.addRenderableWidget(Button.builder(choice("My songs", own), btn -> pick(true))
                    .bounds(this.width / 2 + 2, 20, width, 20).build());
        }

        if (ClientVs.inMatch()) {
            heading = Component.literal("VS " + ClientVs.rivalName + ": pick a song, first pick decides");
            titleColor = 0xFF55FFFF;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(button("Stop playing", btn -> Gameplay.stopByPlayer())); // in VS this counts as giving up
        // FNFmod's options (set once for every song)
        buttons.add(button("Options", btn -> Minecraft.getInstance().setScreen(new FnfOptionsScreen(this))));

        // VS: challenge someone, or leave the match while picking a song
        if (ClientVs.inMatch()) {
            buttons.add(button("Cancel VS", btn -> {
                Minecraft.getInstance().player.connection.sendCommand("fnfvs cancel");
                onClose();
            }));
        } else {
            Button challenge = button("Challenge (VS)", btn -> Minecraft.getInstance().setScreen(new VsPlayerListScreen(this)));
            challenge.active = ClientSongCatalog.received && !ClientSongCatalog.showingOwn(); // VS needs the server's songs
            buttons.add(challenge);
        }
        buttons.add(button("Done", btn -> onClose()));

        setupLayout("Search packs and songs...", buttons);
    }

    @Override
    protected List<MenuList.Row> buildRows(String search) {
        List<MenuList.Row> rows = rows();

        if (search.isEmpty()) {
            for (int i = 0; i < packs.size(); i++) {
                SongCatalogPayload.PackEntry pack = packs.get(i);
                rows.add(packRow(i, pack));
            }
            return rows;
        }

        // Searching: matching packs first, then matching songs from every pack
        List<MenuList.Row> songRows = rows();
        for (int p = 0; p < packs.size(); p++) {
            SongCatalogPayload.PackEntry pack = packs.get(p);
            if (pack.name().toLowerCase().contains(search)) rows.add(packRow(p, pack));

            for (int s = 0; s < pack.songs().size(); s++) {
                SongCatalogPayload.SongEntry song = pack.songs().get(s);
                if (!song.name().toLowerCase().contains(search)) continue;
                int packId = p, songId = s; // the server knows packs and songs by their position in the list
                songRows.add(new MenuList.Entry(song.name(), pack.name() + "  ·  " + chartVersions(song) + bestText(pack.name(), song), 0xFFFFFFFF, ClientSongCatalog.icon(packId), () ->
                        Minecraft.getInstance().setScreen(new SongOptionsScreen(Component.empty(), packId, songId, song))));
            }
        }
        if (!rows.isEmpty()) rows.add(0, new MenuList.Header("Packs"));
        if (!songRows.isEmpty()) {
            rows.add(new MenuList.Header("Songs"));
            rows.addAll(songRows);
        }
        return rows;
    }

    private static Component choice(String text, boolean picked) {
        return picked ? Component.literal(text).withStyle(ChatFormatting.YELLOW) : Component.literal(text);
    }

    private void pick(boolean own) {
        ClientSongCatalog.chooseOwn(own);
        this.rebuildWidgets(); // the other packs, the switch and the VS button change
    }

    private MenuList.Row packRow(int packId, SongCatalogPayload.PackEntry pack) {
        int songCount = pack.songs().size();
        // the pack's icon (from its .ico) in front of the name, if it has one
        return new MenuList.Entry(pack.name(), songCount + (songCount == 1 ? " song" : " songs"), 0xFFFFFFFF, ClientSongCatalog.icon(packId), () ->
                // Open the song select screen using the pack's songs.
                Minecraft.getInstance().setScreen(new SongSelectScreen(Component.literal(pack.name()), packId, pack.songs())));
    }

    // let the player know why the list is empty
    @Override
    protected String emptyText() {
        if (!ClientSongCatalog.received && !ClientSongCatalog.local) return "Waiting for the server's songs...";
        if (packs.isEmpty()) return "No packs found, put FNF mods into the fnfpacks folder.";
        return "Nothing found.";
    }
}
