package net.niliota.fnfmod.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.niliota.fnfmod.Audio.AudioManager;
import net.niliota.fnfmod.Gameplay;
import net.niliota.fnfmod.network.SongCatalogPayload;

import java.util.List;

// Second menu: lists the songs of the pack chosen in PackSelectScreen.
public class SongSelectScreen extends Screen {
    public int packId;
    public List<SongCatalogPayload.SongEntry> songs;
    private int scrollOffset = 0;


    public SongSelectScreen(Component title, int packId, List<SongCatalogPayload.SongEntry> songs) {
        super(title);
        this.packId = packId;
        this.songs = songs;
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    public void rebuildButtons() {
        this.clearWidgets();
        int x = 40; int y = 40 - scrollOffset;
        for (int i = 0; i < songs.size(); i++) {
            SongCatalogPayload.SongEntry song = songs.get(i);
            int songId = i; // the server knows songs by their position in the list

            Button songButton = Button.builder(Component.nullToEmpty(song.name()), (btn) -> {
                // When the button is clicked, we can display a toast to the screen.
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


            }).bounds(x, y, 120, 20).build();
            // x, y, width, height
            // It's recommended to use the fixed height of 20 to prevent rendering issues with the button
            // textures.


            y += 20;
            this.addRenderableWidget(songButton);
        }

        Button stopButton = Button.builder(Component.nullToEmpty("Stop playing"), (btn) -> {
            AudioManager.stopAllTracks();
            Gameplay.stopGameplay();


        }).bounds(200, 10, 120, 20).build();
        this.addRenderableWidget(stopButton);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Minecraft doesn't have a "label" widget, so we'll have to draw our own text.
        // We'll subtract the font height from the Y position to make the text appear above the button.
        // Subtracting an extra 10 pixels will give the text some padding.
        // textRenderer, text, x, y, color, hasShadow
        context.drawString(this.font, "Choose a song", 40, 40 - this.font.lineHeight - 10, 0xFFFFFFFF, true);


    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int)(scrollY * 15);
        scrollOffset = Math.max(0, scrollOffset);
        rebuildButtons();
        return true;
    }
}