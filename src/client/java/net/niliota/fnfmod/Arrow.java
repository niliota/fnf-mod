package net.niliota.fnfmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public enum Arrow {
    LEFT  (0,   398, 156, 153),
    DOWN  (0,   240, 156, 153),
    UP    (162, 240, 156, 153),
    RIGHT (646, 397, 156, 153),

    // "Empty"
    E_LEFT  (489, 238, 156, 153),
    E_DOWN  (646, 239, 156, 153),
    E_UP    (323, 240, 156, 153),
    E_RIGHT (809, 238, 156, 153),

    // "Empty Pressed"
    EP_LEFT  (1897, 150, 156, 153),
    EP_DOWN  (1898, 0, 156, 153),
    EP_UP    (158, 399, 156, 153),
    EP_RIGHT (315, 399, 156, 153),

    // "Glowing""
    G_LEFT  (982, 12, 205, 201),
    G_DOWN  (14,  10, 205, 201),
    G_UP    (506, 12, 205, 201),
    G_RIGHT (1678,13, 205, 201),

    // Hold pieces (stretched along the tail) and hold ends (the bottom of the tail).
    // Positions are from the base game's NOTE_assets.xml, which uses the same spritesheet.
    HOLD_LEFT  (1337, 457, 51, 44),
    HOLD_DOWN  (1282, 457, 51, 44),
    HOLD_UP    (1227, 457, 51, 44),
    HOLD_RIGHT (1172, 457, 51, 44),

    HOLD_END_LEFT  (1117, 452, 51, 64),
    HOLD_END_DOWN  (1062, 452, 51, 64),
    HOLD_END_UP    (1007, 452, 51, 64),
    HOLD_END_RIGHT (952,  452, 51, 64);

    public final int spriteX, spriteY, regionWidth, regionHeight;

    Arrow(int u, int v, int regionWidth, int regionHeight) {
        this.spriteX = u;
        this.spriteY = v;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
    }

    // The function used to draw a sprite can just be called from this enum here.
    public void draw(GuiGraphics context, ResourceLocation texture, int x, int y) {
        // texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight
        context.blit(texture, x, y, 32, 32, spriteX, spriteY, regionWidth, regionHeight, 2048, 1024);
    }

    // Same as draw(), but with any size on screen. Used for glowing arrows and for stretching hold pieces.
    public void draw(GuiGraphics context, ResourceLocation texture, int x, int y, int width, int height) {
        context.blit(texture, x, y, width, height, spriteX, spriteY, regionWidth, regionHeight, 2048, 1024);
    }

    // Upside down (downscroll hold ends): the sprite is read from its bottom to its top.
    public void drawFlipped(GuiGraphics context, ResourceLocation texture, int x, int y, int width, int height) {
        context.blit(texture, x, y, width, height, spriteX, spriteY + regionHeight, regionWidth, -regionHeight, 2048, 1024);
    }

    // Lane number (0=left, 1=down, 2=up, 3=right) to the right sprite.
    public static Arrow note(int lane)    { return pick(lane, LEFT, DOWN, UP, RIGHT); }
    public static Arrow strum(int lane)   { return pick(lane, E_LEFT, E_DOWN, E_UP, E_RIGHT); }
    public static Arrow pressed(int lane) { return pick(lane, EP_LEFT, EP_DOWN, EP_UP, EP_RIGHT); }
    public static Arrow glow(int lane)    { return pick(lane, G_LEFT, G_DOWN, G_UP, G_RIGHT); }
    public static Arrow hold(int lane)    { return pick(lane, HOLD_LEFT, HOLD_DOWN, HOLD_UP, HOLD_RIGHT); }
    public static Arrow holdEnd(int lane) { return pick(lane, HOLD_END_LEFT, HOLD_END_DOWN, HOLD_END_UP, HOLD_END_RIGHT); }

    private static Arrow pick(int lane, Arrow left, Arrow down, Arrow up, Arrow right) {
        return switch (lane) {
            case 1 -> down;
            case 2 -> up;
            case 3 -> right;
            default -> left;
        };
    }

    /*
    x, y — where on screen to draw it
    width, height — how big to draw it on screen (can scale up/down)
    u, v — the pixel coordinate in the texture file to start from
    regionWidth, regionHeight — how many pixels to grab from the texture file
    textureWidth, textureHeight — the total size of the texture file
     */
}