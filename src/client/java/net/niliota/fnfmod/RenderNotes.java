package net.niliota.fnfmod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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
            // (the layout lives in Gameplay, so the notes land exactly on these strums)
            int x = Gameplay.laneX(3);
            int y = Gameplay.strumY();

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


            // Glow when a note was just hit, or while a hold note is being held
            for (int lane = 0; lane < 4; lane++) {
                boolean justHit = Gameplay.songPosition - Gameplay.lastHitTime[lane] < 150;
                if (Gameplay.running && (justHit || Gameplay.holding[lane])) {
                    int glowSize = Math.round(32 * 205f / 156f); // the glowing sprite is bigger than a normal arrow
                    int offset = (glowSize - 32) / 2;
                    Arrow.glow(lane).draw(context, noteSpriteAtlas, Gameplay.laneX(lane) - offset, y - offset, glowSize, glowSize);
                }
            }


            // Opponent's strums (if turned on). They glow when the opponent hits a note, there's no pressing for them.
            if (Gameplay.showOpponent) {
                context.setColor(1f, 1f, 1f, Gameplay.opponentAlpha());
                for (int lane = 0; lane < 4; lane++) {
                    int laneX = Gameplay.opponentLaneX(lane);
                    boolean justHit = Gameplay.songPosition - Gameplay.opponentLastHitTime[lane] < 150;
                    if (Gameplay.running && (justHit || Gameplay.opponentHolding[lane])) {
                        int glowSize = Math.round(32 * 205f / 156f);
                        int offset = (glowSize - 32) / 2;
                        Arrow.glow(lane).draw(context, noteSpriteAtlas, laneX - offset, y - offset, glowSize, glowSize);
                    } else {
                        Arrow.strum(lane).draw(context, noteSpriteAtlas, laneX, y);
                    }
                }
                context.setColor(1f, 1f, 1f, 1f);
            }


            // Unfix transparency
            RenderSystem.disableBlend();
        });
    }
}
