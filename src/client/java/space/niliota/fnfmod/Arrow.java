package space.niliota.fnfmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// The note sprites, all on one spritesheet (assets/fnfmod/note_assets.png).
// Each one is a rectangle on the sheet: x, y, width, height.
public enum Arrow {
    LEFT  (0,   0, 160, 160),
    DOWN  (160, 0, 160, 160),
    UP    (320, 0, 160, 160),
    RIGHT (480, 0, 160, 160),

    // "Empty"
    E_LEFT  (0,   160, 160, 160),
    E_DOWN  (160, 160, 160, 160),
    E_UP    (320, 160, 160, 160),
    E_RIGHT (480, 160, 160, 160),

    // "Empty Pressed"
    EP_LEFT  (0,   320, 160, 160),
    EP_DOWN  (160, 320, 160, 160),
    EP_UP    (320, 320, 160, 160),
    EP_RIGHT (480, 320, 160, 160),

    // "Glowing" (bigger frames, the glow goes around the arrow)
    G_LEFT  (0,   480, 208, 208),
    G_DOWN  (208, 480, 208, 208),
    G_UP    (416, 480, 208, 208),
    G_RIGHT (624, 480, 208, 208),

    // Hold pieces (stretched along the tail) and hold ends (the bottom of the tail).
    HOLD_LEFT  (0,   700, 52, 44),
    HOLD_DOWN  (60,  700, 52, 44),
    HOLD_UP    (120, 700, 52, 44),
    HOLD_RIGHT (180, 700, 52, 44),

    HOLD_END_LEFT  (260, 700, 52, 64),
    HOLD_END_DOWN  (320, 700, 52, 64),
    HOLD_END_UP    (380, 700, 52, 64),
    HOLD_END_RIGHT (440, 700, 52, 64);

    public static final int TEXTURE_WIDTH = 1024, TEXTURE_HEIGHT = 1024; // size of the whole spritesheet

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
        context.blit(texture, x, y, 32, 32, spriteX, spriteY, regionWidth, regionHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    // Same as draw(), but with any size on screen. Used for glowing arrows and for stretching hold pieces.
    public void draw(GuiGraphics context, ResourceLocation texture, int x, int y, int width, int height) {
        context.blit(texture, x, y, width, height, spriteX, spriteY, regionWidth, regionHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    // Upside down (downscroll hold ends): the sprite is read from its bottom to its top.
    public void drawFlipped(GuiGraphics context, ResourceLocation texture, int x, int y, int width, int height) {
        context.blit(texture, x, y, width, height, spriteX, spriteY + regionHeight, regionWidth, -regionHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
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