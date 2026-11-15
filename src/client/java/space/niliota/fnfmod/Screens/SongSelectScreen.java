package space.niliota.fnfmod.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import space.niliota.fnfmod.Gameplay;
import space.niliota.fnfmod.network.SongCatalogPayload;

import java.util.List;

// Second menu: lists the songs of the pack chosen in PackSelectScreen, with a search box.
// Each song shows its versions (from the chart names) and how many audio files it has.
public class SongSelectScreen extends FnfMenuScreen {
    public int packId;
    public List<SongCatalogPayload.SongEntry> songs;


    // title = the pack's name
    public SongSelectScreen(Component title, int packId, List<SongCatalogPayload.SongEntry> songs) {
        super(title);
        this.packId = packId;
        this.songs = songs;
    }

    @Override
    protected void init() {
        setupLayout("Search songs...", List.of(
                button("Back", btn -> Minecraft.getInstance().setScreen(new PackSelectScreen(Component.empty()))),
                button("Stop playing", btn -> Gameplay.stopByPlayer()), // in VS this counts as giving up
                button("Done", btn -> onClose())
        ));
    }

    @Override
    protected List<MenuList.Row> buildRows(String search) {
        List<MenuList.Row> rows = rows();
        for (int i = 0; i < songs.size(); i++) {
            SongCatalogPayload.SongEntry song = songs.get(i);
            if (!search.isEmpty() && !song.name().toLowerCase().contains(search)) continue;
            int songId = i; // the server knows songs by their position in the list

            rows.add(new MenuList.Entry(song.name(), chartVersions(song) + "  ·  " + audioCount(song), () -> {
                // When the song is clicked, we can display a toast to the screen.
                this.minecraft.getToasts().addToast(
                        SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty(String.format("Opening %s!", song.name())), Component.nullToEmpty(song.chartFiles().size() + " charts, " + (song.instFiles().size() + song.voicesFiles().size()) + " audio files"))
                );

//                if (song.audioVoicesFiles.toArray().length > 1 || song.audioInstFiles.toArray().length > 1) {
                    Minecraft.getInstance().setScreen(
                            new SongOptionsScreen(Component.empty(), packId, songId, song)
                    );
//                } else {
//                    AudioManager.stopAllTracks();
//
//                    AudioTrack inst = new AudioTrack();
//                    AudioTrack voices = new AudioTrack();
//
//                    AudioManager.addTrack(inst);
//                    AudioManager.addTrack(voices);
//
//                    inst.load(song.audioInstFiles.get(0)); voices.load(song.audioVoicesFiles.get(0));
////                    inst.play(); voices.play(); // not perfectly synced
//                    AudioManager.playAll(List.of(inst, voices)); // should be perfectly synced
//
//
////                    Gameplay.startGameplay(song);
//                }
            }));
        }
        return rows;
    }

    @Override
    protected String emptyText() {
        return songs.isEmpty() ? "This pack has no songs." : "No songs found.";
    }
}
