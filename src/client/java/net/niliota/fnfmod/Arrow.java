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
    G_RIGHT (1678,13, 205, 201);

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

    /*
    x, y — where on screen to draw it
    width, height — how big to draw it on screen (can scale up/down)
    u, v — the pixel coordinate in the texture file to start from
    regionWidth, regionHeight — how many pixels to grab from the texture file
    textureWidth, textureHeight — the total size of the texture file
     */
}