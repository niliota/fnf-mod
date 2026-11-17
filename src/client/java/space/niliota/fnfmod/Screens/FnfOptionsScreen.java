package space.niliota.fnfmod.Screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import space.niliota.fnfmod.Audio.AudioCache;
import space.niliota.fnfmod.Audio.AudioManager;
import space.niliota.fnfmod.Audio.AudioTrack;
import space.niliota.fnfmod.FnfCamera;
import space.niliota.fnfmod.FnfOptions;
import space.niliota.fnfmod.network.ClientSpectate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

// Blocky Friday's options, opened with the "Options" button in the Mic menu. Set once and used for every song,
// like Minecraft's own options. Saved to config/fnfmod.json when the screen is closed.
// Same look as the song browser: categories on the left, the picked category's options on the right
// (switches and sliders, each with a short line saying what it does).
public class FnfOptionsScreen extends Screen {
    private static final int TOP = 30, BOTTOM = 28, GAP = 6, MARGIN = 8;
    private static final String[] CATEGORIES = {"Gameplay", "Audio", "Camera", "Multiplayer", "Downloads"};
    private static int category = 0; // remembered while the game runs

    private final Screen parent; // the screen to go back to
    private final ScrollColumn categoryColumn = new ScrollColumn();
    private final ScrollColumn optionColumn = new ScrollColumn();

    public FnfOptionsScreen(Screen parent) {
        super(Component.literal("Options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int bodyTop = TOP + 2, bodyBottom = this.height - BOTTOM - 2;
        int sideWidth = Math.max(90, Math.min(150, (this.width - MARGIN * 2) / 4));
        categoryColumn.setBounds(MARGIN, bodyTop, sideWidth, bodyBottom - bodyTop);
        optionColumn.setBounds(MARGIN + sideWidth + GAP, bodyTop, this.width - MARGIN * 2 - sideWidth - GAP, bodyBottom - bodyTop);

        List<ScrollColumn.Row> categories = new ArrayList<>();
        for (int i = 0; i < CATEGORIES.length; i++) categories.add(new CategoryRow(i));
        categoryColumn.setRows(categories, false);
        showCategory();

        int buttonWidth = Math.min(90, this.width / 4);
        this.addRenderableWidget(new Ui.FlatButton(this.width - MARGIN - buttonWidth, this.height - BOTTOM + 5, buttonWidth, 18, "Done", this::onClose));
    }

    // the options of the picked category
    private void showCategory() {
        List<ScrollColumn.Row> rows = new ArrayList<>();
        rows.add(new TitleRow(CATEGORIES[category]));

        switch (category) {
            case 0 -> { // Gameplay
                // off: pressing with no note there counts as a miss
                rows.add(new SwitchRow("Ghost tapping", "Pressing with no note there is fine. Off: it's a miss",
                        () -> FnfOptions.ghostTapping, value -> FnfOptions.ghostTapping = value));
                rows.add(new SwitchRow("No fail", "Keep playing when your health runs out",
                        () -> FnfOptions.noFail, value -> FnfOptions.noFail = value));
                rows.add(new SwitchRow("Downscroll", "Strums at the bottom, notes come down from the top",
                        () -> FnfOptions.downscroll, value -> FnfOptions.downscroll = value));
                rows.add(new SwitchRow("Middle scroll", "Your strums in the middle of the screen",
                        () -> FnfOptions.middleScroll, value -> FnfOptions.middleScroll = value));
                rows.add(new SwitchRow("Show opponent", "The opponent's strums and notes next to yours",
                        () -> FnfOptions.showOpponent, value -> FnfOptions.showOpponent = value));
                // VS on the opponent side: my side on the left, so both players see the same stage
                rows.add(new SwitchRow("True POV", "Boyfriend always on the right, like on stage. Off: you're always on the right",
                        () -> FnfOptions.truePov, value -> FnfOptions.truePov = value));
            }
            case 1 -> { // Audio
                // how loud FNF songs are (on top of Minecraft's Master volume)
                rows.add(new SliderRow("Song volume", "On top of Minecraft's Master volume",
                        () -> Math.round(FnfOptions.songVolume * 100) + "%",
                        () -> FnfOptions.songVolume,
                        value -> {
                            FnfOptions.songVolume = Math.round(value * 100) / 100.0;
                            for (AudioTrack track : AudioManager.tracks) track.setGain((float) FnfOptions.songVolume); // a song might be paused right now
                        }));
                // Audio offset: if the music reaches your ears late (bluetooth headphones...), move this up until hits feel right
                rows.add(new SliderRow("Audio offset", "Music reaches you late (Bluetooth)? Move this up until hits feel right",
                        () -> FnfOptions.audioOffset + " ms",
                        () -> (FnfOptions.audioOffset - FnfOptions.MIN_AUDIO_OFFSET) / (double)(FnfOptions.MAX_AUDIO_OFFSET - FnfOptions.MIN_AUDIO_OFFSET),
                        value -> FnfOptions.audioOffset = (int) Math.round(FnfOptions.MIN_AUDIO_OFFSET + value * (FnfOptions.MAX_AUDIO_OFFSET - FnfOptions.MIN_AUDIO_OFFSET))));
            }
            case 2 -> { // Camera
                rows.add(new SwitchRow("FNF camera", "Side view while a song plays, like an FNF stage (V toggles it)",
                        () -> FnfOptions.fnfCamera, value -> FnfOptions.fnfCamera = value));
                rows.add(new SliderRow("Camera distance", "How far the FNF camera is from you",
                        () -> String.format(Locale.ROOT, "%.1f blocks", FnfOptions.cameraDistance),
                        () -> (FnfOptions.cameraDistance - FnfCamera.MIN_DISTANCE) / (FnfCamera.MAX_DISTANCE - FnfCamera.MIN_DISTANCE),
                        value -> FnfOptions.cameraDistance = Math.round((FnfCamera.MIN_DISTANCE + value * (FnfCamera.MAX_DISTANCE - FnfCamera.MIN_DISTANCE)) * 2) / 2.0));
            }
            case 3 -> { // Multiplayer
                // arrows above players playing near me (with their music)
                rows.add(new SwitchRow("Show players playing nearby", "Their arrows float above them, and you hear the song",
                        () -> FnfOptions.watchNearby, value -> {
                            FnfOptions.watchNearby = value;
                            if (!value) ClientSpectate.stop();
                        }));
            }
            case 4 -> { // Downloads
                // How much space songs downloaded from servers can take. Over it, the ones played the longest ago get deleted.
                int steps = FnfOptions.CACHE_LIMITS.length - 1;
                rows.add(new SliderRow("Downloaded songs limit", "When it's full, songs played the longest ago go first",
                        () -> FnfOptions.cacheLimitMB == 0 ? "no limit"
                                : FnfOptions.cacheLimitMB < 1024 ? FnfOptions.cacheLimitMB + " MB" : FnfOptions.cacheLimitMB / 1024 + " GB",
                        () -> cacheStep() / (double) steps,
                        value -> FnfOptions.cacheLimitMB = FnfOptions.CACHE_LIMITS[(int) Math.round(value * steps)]));

                // Songs downloaded from servers (fnfcache/), with how much space they take. Asks before deleting.
                long cacheSize = AudioCache.cacheSize();
                rows.add(new ButtonRow("Clear downloaded songs", formatSize(cacheSize) + " right now. They download again when played",
                        () -> cacheSize > 0, () -> this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
                            if (confirmed) AudioCache.clear();
                            this.minecraft.setScreen(this); // back here, it shows the new size
                        }, Component.literal("Clear downloaded songs?"),
                           Component.literal("This deletes " + formatSize(cacheSize) + " of songs downloaded from servers. "
                                   + "They'll be downloaded again the next time they're played.")))));
            }
        }
        optionColumn.setRows(rows, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(this.font, "Options", MARGIN, 11, Ui.TEXT, true);
        categoryColumn.render(g, mouseX, mouseY);
        optionColumn.render(g, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        // the top and bottom bars, like the song browser
        g.fill(0, 0, this.width, TOP, 0x90000000);
        g.fill(0, this.height - BOTTOM, this.width, this.height, 0x90000000);
        g.fill(0, TOP, this.width, TOP + 1, Ui.PANEL_LINE);
        g.fill(0, this.height - BOTTOM - 1, this.width, this.height - BOTTOM, Ui.PANEL_LINE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        return categoryColumn.mouseClicked(mouseX, mouseY) || optionColumn.mouseClicked(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return optionColumn.mouseDragged(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        optionColumn.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return categoryColumn.mouseScrolled(mouseX, mouseY, scrollY) || optionColumn.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // called when this screen goes away (Done, Esc, or anything else), so the options always get saved
    @Override
    public void removed() {
        FnfOptions.save();
        AudioCache.trim(); // the limit might be lower now
    }

    // where the current cache limit is on the slider (the closest step)
    private static int cacheStep() {
        int best = 0;
        for (int i = 0; i < FnfOptions.CACHE_LIMITS.length; i++) {
            int limit = FnfOptions.CACHE_LIMITS[i];
            if (limit == FnfOptions.cacheLimitMB) return i;
            if (limit != 0 && Math.abs(limit - FnfOptions.cacheLimitMB) < Math.abs(FnfOptions.CACHE_LIMITS[best] - FnfOptions.cacheLimitMB)) best = i;
        }
        return best;
    }

    // 1536 -> "1.5 KB", 52428800 -> "50.0 MB"
    private static String formatSize(long bytes) {
        if (bytes <= 0) return "Empty";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ---- rows ----

    private class CategoryRow implements ScrollColumn.Row {
        private final int id;

        CategoryRow(int id) {
            this.id = id;
        }

        @Override public int height(int width) { return 22; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            if (id == category) {
                g.fill(x, y, x + width, y + 20, Ui.ACCENT_SOFT);
                g.fill(x, y, x + 2, y + 20, Ui.ACCENT);
            } else if (hovered) {
                g.fill(x, y, x + width, y + 20, Ui.HOVER);
            }
            g.drawString(font, Ui.cut(font, CATEGORIES[id], width - 12), x + 7, y + 6, id == category ? Ui.TEXT : 0xFFCFCFD8, false);
        }

        @Override
        public boolean click(double mouseX, double mouseY, int x, int y, int width) {
            category = id;
            showCategory();
            return true;
        }
    }

    // the category's name big
    private class TitleRow implements ScrollColumn.Row {
        private final String text;

        TitleRow(String text) {
            this.text = text;
        }

        @Override public int height(int width) { return 24; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            Ui.bigText(g, font, text, x + 4, y + 5, 1.5f, Ui.TEXT, width - 8);
        }
    }

    // name and what it does on the left, an on/off switch on the right. Clicking anywhere on the row flips it.
    private class SwitchRow implements ScrollColumn.Row {
        private final String label, description;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;

        SwitchRow(String label, String description, BooleanSupplier get, Consumer<Boolean> set) {
            this.label = label;
            this.description = description;
            this.get = get;
            this.set = set;
        }

        @Override public int height(int width) { return 30; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            if (hovered) g.fill(x, y, x + width, y + 28, Ui.HOVER);
            boolean on = get.getAsBoolean();
            int textWidth = width - 44;
            g.drawString(font, Ui.cut(font, label, textWidth), x + 6, y + 5, Ui.TEXT, false);
            g.drawString(font, Ui.cut(font, description, textWidth), x + 6, y + 16, Ui.TEXT_DIM, false);

            // the switch: a pill with a knob, purple and knob on the right when on
            int sx = x + width - 30, sy = y + 8;
            g.fill(sx, sy, sx + 22, sy + 12, on ? Ui.ACCENT : 0x40FFFFFF);
            int knob = on ? sx + 12 : sx + 2;
            g.fill(knob, sy + 2, knob + 8, sy + 10, on ? Ui.TEXT : 0xFFBBBBC5);
        }

        @Override
        public boolean click(double mouseX, double mouseY, int x, int y, int width) {
            set.accept(!get.getAsBoolean());
            return true;
        }
    }

    // name and value on top, what it does under it, and the bar. Click or drag on the bar to change it.
    private class SliderRow implements ScrollColumn.Row {
        private final String label, description;
        private final Supplier<String> valueText;
        private final DoubleSupplier get;   // 0 to 1
        private final DoubleConsumer set;   // 0 to 1

        SliderRow(String label, String description, Supplier<String> valueText, DoubleSupplier get, DoubleConsumer set) {
            this.label = label;
            this.description = description;
            this.valueText = valueText;
            this.get = get;
            this.set = set;
        }

        @Override public int height(int width) { return 44; }
        @Override public boolean draggable() { return true; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            if (hovered) g.fill(x, y, x + width, y + 42, Ui.HOVER);
            String value = valueText.get();
            g.drawString(font, Ui.cut(font, label, width - font.width(value) - 20), x + 6, y + 5, Ui.TEXT, false);
            g.drawString(font, value, x + width - 6 - font.width(value), y + 5, Ui.GOLD, false);
            g.drawString(font, Ui.cut(font, description, width - 12), x + 6, y + 16, Ui.TEXT_DIM, false);

            // the bar, filled up to the value, with a knob
            int barX = x + 6, barWidth = width - 12, barY = y + 31;
            double v = Math.max(0, Math.min(1, get.getAsDouble()));
            int filled = (int) (barWidth * v);
            g.fill(barX, barY, barX + barWidth, barY + 3, 0x40FFFFFF);
            g.fill(barX, barY, barX + filled, barY + 3, Ui.ACCENT);
            int knobX = barX + filled - 3;
            g.fill(knobX, barY - 4, knobX + 6, barY + 7, Ui.TEXT);
        }

        @Override
        public boolean click(double mouseX, double mouseY, int x, int y, int width) {
            double v = (mouseX - (x + 6)) / (width - 12);
            set.accept(Math.max(0, Math.min(1, v)));
            return true;
        }
    }

    // a button-looking row (Clear downloaded songs). Greyed out when it can't be used.
    private class ButtonRow implements ScrollColumn.Row {
        private final String label, description;
        private final BooleanSupplier active;
        private final Runnable onClick;

        ButtonRow(String label, String description, BooleanSupplier active, Runnable onClick) {
            this.label = label;
            this.description = description;
            this.active = active;
            this.onClick = onClick;
        }

        @Override public int height(int width) { return 32; }

        @Override
        public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY, boolean hovered) {
            boolean on = active.getAsBoolean();
            g.fill(x, y + 2, x + width, y + 30, !on ? 0x14FFFFFF : hovered ? 0x40FFFFFF : 0x22FFFFFF);
            g.drawString(font, Ui.cut(font, label, width - 12), x + 6, y + 6, on ? Ui.TEXT : Ui.TEXT_FAINT, false);
            g.drawString(font, Ui.cut(font, description, width - 12), x + 6, y + 18, Ui.TEXT_DIM, false);
        }

        @Override
        public boolean click(double mouseX, double mouseY, int x, int y, int width) {
            if (!active.getAsBoolean()) return false;
            onClick.run();
            return true;
        }
    }
}
