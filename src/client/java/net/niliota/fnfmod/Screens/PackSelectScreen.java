package net.niliota.fnfmod.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.niliota.fnfmod.Audio.AudioManager;
import net.niliota.fnfmod.ClientSongCatalog;
import net.niliota.fnfmod.Gameplay;
import net.niliota.fnfmod.network.SongCatalogPayload;

import java.util.List;

// First menu: lists the FNF mods (packs) the server has. Opened with the menu key (O).
public class PackSelectScreen extends Screen {
    public List<SongCatalogPayload.PackEntry> packs;


    public PackSelectScreen(Component title) {
        super(title);
        this.packs = ClientSongCatalog.packs;
    }

    @Override
    protected void init() {
        int x = 40; int y = 40;
        for (int i = 0; i < packs.size(); i++) {
            SongCatalogPayload.PackEntry pack = packs.get(i);
            int packId = i; // the server knows packs by their position in the list

            Button packButton = Button.builder(Component.nullToEmpty(pack.name()), (btn) -> {
                // Open the song select screen using the pack's songs.
                Minecraft.getInstance().setScreen(
                        new SongSelectScreen(Component.empty(), packId, pack.songs())
                );
            }).bounds(x, y, 120, 20).build();
            // x, y, width, height
            // It's recommended to use the fixed height of 20 to prevent rendering issues with the button
            // textures.

            y += 20;
            this.addRenderableWidget(packButton);
        }

        Button stopButton = Button.builder(Component.nullToEmpty("Stop playing"), (btn) -> {
            AudioManager.stopAllTracks();
            Gameplay.stopGameplay();

        }).bounds(200, 10, 120, 20).build();
        this.addRenderableWidget(stopButton);

        // FNFmod's options (set once for every song)
        Button optionsButton = Button.builder(Component.nullToEmpty("Options"), (btn) -> {
            Minecraft.getInstance().setScreen(new FnfOptionsScreen(this));
        }).bounds(330, 10, 80, 20).build();
        this.addRenderableWidget(optionsButton);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Minecraft doesn't have a "label" widget, so we'll have to draw our own text.
        // We'll subtract the font height from the Y position to make the text appear above the button.
        // Subtracting an extra 10 pixels will give the text some padding.
        // textRenderer, text, x, y, color, hasShadow
        context.drawString(this.font, "Choose a FNF mod", 40, 40 - this.font.lineHeight - 10, 0xFFFFFFFF, true);

        // let the player know why the list is empty
        if (!ClientSongCatalog.received) {
            context.drawString(this.font, "This server doesn't have FNFmod installed.", 40, 40, 0xFFFF5555, true);
        } else if (packs.isEmpty()) {
            context.drawString(this.font, "No packs found, put FNF mods into the fnfpacks folder.", 40, 40, 0xFFFF5555, true);
        }
    }


}
