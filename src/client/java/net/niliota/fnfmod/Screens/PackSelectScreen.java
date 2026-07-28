package net.niliota.fnfmod.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.niliota.fnfmod.Audio.AudioManager;
import net.niliota.fnfmod.FNFPack;
import net.niliota.fnfmod.Gameplay;

import java.util.List;

public class PackSelectScreen extends Screen {
    public List<FNFPack> packs;


    public PackSelectScreen(Component title, List<FNFPack> packs) {
        super(title);
        this.packs = packs;
    }

    @Override
    protected void init() {
        int x = 40; int y = 40;
        for (FNFPack pack : packs) {
            Button packButton = Button.builder(Component.nullToEmpty(pack.name), (btn) -> {
                // Open the song select screen using the pack's songs.
                Minecraft.getInstance().setScreen(
                        new SongSelectScreen(Component.empty(), pack.songs)
                );
            }).bounds(x, y, 120, 20).build();
            // x, y, width, height
            // It's recommended to use the fixed height of 20 to prevent rendering issues with the button
            // textures.

            Button stopButton = Button.builder(Component.nullToEmpty("Stop playing"), (btn) -> {
                AudioManager.stopAllTracks();
                Gameplay.stopGameplay();

            }).bounds(200, 10, 120, 20).build();


            y += 20;
            this.addRenderableWidget(packButton);
            this.addRenderableWidget(stopButton);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Minecraft doesn't have a "label" widget, so we'll have to draw our own text.
        // We'll subtract the font height from the Y position to make the text appear above the button.
        // Subtracting an extra 10 pixels will give the text some padding.
        // textRenderer, text, x, y, color, hasShadow
        context.drawString(this.font, "Choose a FNF mod", 40, 40 - this.font.lineHeight - 10, 0xFFFFFFFF, true);


    }


}