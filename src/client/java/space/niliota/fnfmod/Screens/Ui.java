package space.niliota.fnfmod.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

// The look of Blocky Friday's menus: flat see-through panels, FNF colors, and a flat button to go with them.
// Everything is drawn with plain rectangles, so it looks the same with any resource pack.
public class Ui {
    // colors (ARGB)
    public static final int PANEL        = 0xB0101016; // panels' background
    public static final int PANEL_LINE   = 0x26FFFFFF; // thin border around panels
    public static final int HOVER        = 0x1AFFFFFF; // a row/chip under the mouse
    public static final int ACCENT       = 0xFFC24B99; // FNF's left arrow purple
    public static final int ACCENT_SOFT  = 0x50C24B99; // selected row
    public static final int ACCENT_HOVER = 0xFFD565AC;
    public static final int TEXT         = 0xFFFFFFFF;
    public static final int TEXT_DIM     = 0xFF9A9AA5;
    public static final int TEXT_FAINT   = 0xFF6A6A75;
    public static final int GOLD         = 0xFFFFD34D; // personal bests, headings
    public static final int CYAN         = 0xFF55E0FF; // VS

    // a panel: background with a 1px border
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, PANEL);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, PANEL_LINE);
    }

    // shortens text that doesn't fit, with "..." at the end
    public static String cut(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    // bigger text, starting at x, y (cut to fit maxWidth, measured at the bigger size)
    public static void bigText(GuiGraphics g, Font font, String text, int x, int y, float scale, int color, int maxWidth) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, cut(font, text, (int) (maxWidth / scale)), 0, 0, color, true);
        g.pose().popPose();
    }

    // A flat button: dark, lighter under the mouse. "accent" ones are purple (the main thing to press, like Start).
    // "picked" ones show a purple line under them (one side of a switch, like Server songs / My songs).
    public static class FlatButton extends AbstractButton {
        private final Runnable onPress;
        public boolean accent = false;
        public boolean picked = false;

        public FlatButton(int x, int y, int width, int height, String text, Runnable onPress) {
            super(x, y, width, height, Component.literal(text));
            this.onPress = onPress;
        }

        public FlatButton accent() {
            accent = true;
            return this;
        }

        @Override
        public void onPress() {
            onPress.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
            boolean hot = active && isHoveredOrFocused();
            int background;
            if (!active) background = 0x40000000;
            else if (accent) background = hot ? ACCENT_HOVER : ACCENT;
            else background = hot ? 0x40FFFFFF : 0x22FFFFFF;
            g.fill(x0, y0, x1, y1, background);
            if (picked) g.fill(x0, y1 - 2, x1, y1, ACCENT);

            Font font = Minecraft.getInstance().font;
            int color = !active ? TEXT_FAINT : picked || accent ? TEXT : hot ? TEXT : 0xFFDDDDE5;
            String text = cut(font, getMessage().getString(), width - 6);
            g.drawCenteredString(font, text, x0 + width / 2, y0 + (height - 8) / 2, color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
