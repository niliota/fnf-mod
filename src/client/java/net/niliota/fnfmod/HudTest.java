package net.niliota.fnfmod;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;


public class HudTest {

    public static void run() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            int rectangleX = 10;
            int rectangleY = 10;
            int rectangleWidth = 100;
            int rectangleHeight = 50;

            context.fill(rectangleX, rectangleY, rectangleX + rectangleWidth, rectangleY + rectangleHeight, 0xFF0000FF);
            // TextRenderer, text (string, or Text object), x, y, color, shadow
            context.drawString(Minecraft.getInstance().font, "Sakam bijnik", 10, 200, 0xFFFFFFFF, false);

        });
    }
}
