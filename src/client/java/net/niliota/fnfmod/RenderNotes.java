package net.niliota.fnfmod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public class RenderNotes {
    public static boolean visible = false;


    public static void render() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {

            // disable if shouldn't be visible
            if (!visible) return;

            // Main spritesheet
            ResourceLocation noteSpriteAtlas = ResourceLocation.fromNamespaceAndPath("fnfmod", "note_assets.png");

            // Fix transparency
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // Always anchored to top-right, regardless of GUI scale
            int elementWidth = 29; // width of 1 note (29px)
            int offsetFromRight = 10;
            int offsetFromTop = 5;

            int x = Minecraft.getInstance().getWindow().getGuiScaledWidth() - elementWidth - offsetFromRight;
            int y = offsetFromTop;

            // Main Rendering -----------------------------------------------
            // Left Arrow
            // texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight
            if (!FNFmodClient.noteManager.leftArrowState) {
                Arrow.E_LEFT.draw(context, noteSpriteAtlas, x-96, y);
                //context.blit(noteSpriteAtlas, x-96, y, 32, 32, Arrow.E_LEFT.spriteX, Arrow.E_LEFT.spriteY, Arrow.E_LEFT.regionWidth, Arrow.E_LEFT.regionHeight, 2048, 1024);
            }

            // Down Arrow
            if (!FNFmodClient.noteManager.downArrowState) {
                Arrow.E_DOWN.draw(context, noteSpriteAtlas, x-64, y);
            }

            // Up Arrow
            if (!FNFmodClient.noteManager.upArrowState) {
                Arrow.E_UP.draw(context, noteSpriteAtlas, x-32, y);
            }

            // Right Arrow
            if (!FNFmodClient.noteManager.rightArrowState) {
                Arrow.E_RIGHT.draw(context, noteSpriteAtlas, x, y);
            }


            // Pressing Handling ------------------------------------------------------
            if (FNFmodClient.leftKey.isDown()) {
                Arrow.EP_LEFT.draw(context, noteSpriteAtlas, x-96, y);
                FNFmodClient.noteManager.leftArrowState = true;
            } else {
                FNFmodClient.noteManager.leftArrowState = false;
            }

            if (FNFmodClient.downKey.isDown()) {
                Arrow.EP_DOWN.draw(context, noteSpriteAtlas, x-64, y);
                FNFmodClient.noteManager.downArrowState = true;
            } else {
                FNFmodClient.noteManager.downArrowState = false;
            }

            if (FNFmodClient.upKey.isDown()) {
                Arrow.EP_UP.draw(context, noteSpriteAtlas, x-32, y);
                FNFmodClient.noteManager.upArrowState = true;
            } else {
                FNFmodClient.noteManager.upArrowState = false;
            }

            if (FNFmodClient.rightKey.isDown()) {
                Arrow.EP_RIGHT.draw(context, noteSpriteAtlas, x, y);
                FNFmodClient.noteManager.rightArrowState = true;
            } else {
                FNFmodClient.noteManager.rightArrowState = false;
            }


            // Unfix transparency
            RenderSystem.disableBlend();
        });
    }
}
