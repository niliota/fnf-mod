package space.niliota.fnfmod.Screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// The scrolling list used by FNFmod's menus, the same kind Minecraft uses for its world and server lists.
// Rows can be:
//  * Entry   - a title with a grey line under it (and an icon in front, if it has one), does something when clicked
//  * Header  - a section heading, can't be clicked
//  * Toggle  - a tick box with a label (pick audio files...)
public class MenuList extends ObjectSelectionList<MenuList.Row> {
    public static final int ROW_HEIGHT = 24;
    private static final int ICON_SIZE = 20;
    private static final int MAX_ROW_WIDTH = 300;

    private static final ResourceLocation CHECKBOX = ResourceLocation.withDefaultNamespace("widget/checkbox");
    private static final ResourceLocation CHECKBOX_SELECTED = ResourceLocation.withDefaultNamespace("widget/checkbox_selected");
    private static final ResourceLocation CHECKBOX_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/checkbox_highlighted");
    private static final ResourceLocation CHECKBOX_SELECTED_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/checkbox_selected_highlighted");

    public MenuList(Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, ROW_HEIGHT);
    }

    // swap all the rows (after searching or changing what's shown) and go back to the top
    public void setRows(List<Row> rows) {
        replaceEntries(rows);
        setScrollAmount(0);
    }

    public boolean isEmpty() {
        return children().isEmpty();
    }

    @Override
    public int getRowWidth() {
        return Math.min(MAX_ROW_WIDTH, this.width - 40);
    }

    // no selection outline, rows light up when the mouse is over them instead
    @Override
    protected boolean isSelectedItem(int index) {
        return false;
    }

    public abstract static class Row extends ObjectSelectionList.Entry<Row> {
    }

    // A title with a grey info line under it. Clicking it runs onClick.
    public static class Entry extends Row {
        private final String title;
        private final String subtitle;
        private final int titleColor;
        private final ResourceLocation icon; // null = no icon
        private final Runnable onClick;

        public Entry(String title, String subtitle, int titleColor, ResourceLocation icon, Runnable onClick) {
            this.title = title;
            this.subtitle = subtitle;
            this.titleColor = titleColor;
            this.icon = icon;
            this.onClick = onClick;
        }

        public Entry(String title, String subtitle, int titleColor, Runnable onClick) {
            this(title, subtitle, titleColor, null, onClick);
        }

        public Entry(String title, String subtitle, Runnable onClick) {
            this(title, subtitle, 0xFFFFFFFF, null, onClick);
        }

        @Override
        public void render(GuiGraphics context, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (hovering) context.fill(left - 2, top - 2, left + width - 2, top + height + 2, 0x30FFFFFF);

            // the icon (64x64 texture drawn at 20x20), the text moves over to make room for it
            int textX = left + 4;
            if (icon != null) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                context.blit(icon, left + 2, top, ICON_SIZE, ICON_SIZE, 0, 0, 64, 64, 64, 64);
                RenderSystem.disableBlend();
                textX = left + ICON_SIZE + 7;
            }

            Font font = Minecraft.getInstance().font;
            int textWidth = left + width - textX - 4;
            context.drawString(font, cut(font, title, textWidth), textX, top + 1, titleColor, true);
            if (subtitle != null && !subtitle.isEmpty()) {
                context.drawString(font, cut(font, subtitle, textWidth), textX, top + 12, 0xFF9A9A9A, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            onClick.run();
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(title);
        }
    }

    // A section heading.
    public static class Header extends Row {
        private final String text;

        public Header(String text) {
            this.text = text;
        }

        @Override
        public void render(GuiGraphics context, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = Minecraft.getInstance().font;
            context.drawString(font, text, left + 2, top + 10, 0xFFFFDD55, true);
            context.fill(left + 2, top + 20, left + width - 4, top + 21, 0x40FFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(text);
        }
    }

    // A tick box with a label. isOn says if it's ticked, onClick changes it (the screen decides what clicking means,
    // e.g. charts work like radio buttons: clicking one unticks the others).
    public static class Toggle extends Row {
        private final String label;
        private final java.util.function.BooleanSupplier isOn;
        private final Runnable onClick;

        public Toggle(String label, java.util.function.BooleanSupplier isOn, Runnable onClick) {
            this.label = label;
            this.isOn = isOn;
            this.onClick = onClick;
        }

        @Override
        public void render(GuiGraphics context, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (hovering) context.fill(left - 2, top - 2, left + width - 2, top + height + 2, 0x30FFFFFF);
            boolean on = isOn.getAsBoolean();
            ResourceLocation sprite = on ? (hovering ? CHECKBOX_SELECTED_HIGHLIGHTED : CHECKBOX_SELECTED)
                                         : (hovering ? CHECKBOX_HIGHLIGHTED : CHECKBOX);
            context.blitSprite(sprite, left + 2, top + 1, 17, 17);
            Font font = Minecraft.getInstance().font;
            context.drawString(font, cut(font, label, width - 30), left + 24, top + 6, 0xFFFFFFFF, true);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            onClick.run();
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(label);
        }
    }

    // shortens text that doesn't fit, with "..." at the end
    private static String cut(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
    }
}
