package space.niliota.fnfmod.Screens;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

// One scrolling panel of the song browser (packs, songs, or the song's details). Not a Minecraft widget:
// the screen tells it where it is, draws it, and passes the mouse on to it.
// Rows can be any height (the details panel has wrapping rows of chips), and draw themselves.
public class ScrollColumn {
    public interface Row {
        int height(int width);
        void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered);
        // clicked at mouseX, mouseY (the row is at x, y). true = something happened
        default boolean click(double mouseX, double mouseY, int x, int y, int width) {
            return false;
        }
        // true = holding the mouse after clicking keeps calling click() as it moves (sliders)
        default boolean draggable() {
            return false;
        }
    }

    private static final int PADDING = 4;

    public int x, y, width, height;
    private List<Row> rows = new ArrayList<>();
    private double scroll = 0;
    private Row dragging;                    // the row being dragged (a slider)
    private int dragX, dragY, dragWidth;     // where it was when the drag started

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    // new rows; keepScroll = stay where it was (only something inside a row changed)
    public void setRows(List<Row> rows, boolean keepScroll) {
        this.rows = rows;
        if (!keepScroll) scroll = 0;
        clampScroll();
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int innerWidth() {
        return width - PADDING * 2 - (contentHeight() > height ? 4 : 0); // room for the scrollbar
    }

    private int contentHeight() {
        int total = PADDING * 2;
        for (Row row : rows) total += row.height(width - PADDING * 2 - 4);
        return total;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        Ui.panel(g, x, y, x + width, y + height);
        g.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);

        int inner = innerWidth();
        int rowY = y + PADDING - (int) scroll;
        boolean mouseIn = isMouseOver(mouseX, mouseY);
        for (Row row : rows) {
            int h = row.height(inner);
            if (rowY + h > y && rowY < y + height) {
                boolean hovered = mouseIn && mouseY >= rowY && mouseY < rowY + h;
                row.render(g, x + PADDING, rowY, inner, mouseX, mouseY, hovered);
            }
            rowY += h;
        }
        g.disableScissor();

        // thin scrollbar on the right, if it doesn't all fit
        int content = contentHeight();
        if (content > height) {
            int barHeight = Math.max(16, height * height / content);
            int barY = y + (int) ((height - barHeight) * (scroll / (content - height)));
            g.fill(x + width - 4, barY + 1, x + width - 2, barY + barHeight - 1, 0x60FFFFFF);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        int inner = innerWidth();
        int rowY = y + PADDING - (int) scroll;
        for (Row row : rows) {
            int h = row.height(inner);
            if (mouseY >= rowY && mouseY < rowY + h) {
                if (row.draggable()) {
                    dragging = row;
                    dragX = x + PADDING;
                    dragY = rowY;
                    dragWidth = inner;
                }
                return row.click(mouseX, mouseY, x + PADDING, rowY, inner);
            }
            rowY += h;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (dragging == null) return false;
        dragging.click(mouseX, mouseY, dragX, dragY, dragWidth);
        return true;
    }

    public void mouseReleased() {
        dragging = null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        scroll -= amount * 24;
        clampScroll();
        return true;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - height)));
    }
}
