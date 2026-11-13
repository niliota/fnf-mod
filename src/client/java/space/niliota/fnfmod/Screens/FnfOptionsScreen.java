package space.niliota.fnfmod.Screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import space.niliota.fnfmod.Audio.AudioCache;
import space.niliota.fnfmod.FnfCamera;
import space.niliota.fnfmod.FnfOptions;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

// FNFmod's options, opened with the "Options" button in the Mic menu. Set once and used for every song,
// like Minecraft's own options. Saved to config/fnfmod.json when the screen is closed.
public class FnfOptionsScreen extends Screen {
    private static final int WIDTH = 200; // width of the column of options (smaller if the window is narrower)

    private final Screen parent; // the screen to go back to

    // The options scroll with the mouse wheel, so they fit at any GUI scale. Done stays at the bottom.
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int contentTop, contentBottom; // the area the options are shown in
    private int y;                         // where the next option goes

    public FnfOptionsScreen(Screen parent) {
        super(Component.literal("FNFmod Options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int columnWidth = Math.min(WIDTH, this.width - 20);
        int x = this.width / 2 - columnWidth / 2;
        contentTop = 30;
        contentBottom = this.height - 44;
        y = contentTop - scrollOffset;

        // Gameplay
        addCheckbox(x, "No fail", FnfOptions.noFail, value -> FnfOptions.noFail = value);
        addCheckbox(x, "Middle scroll", FnfOptions.middleScroll, value -> FnfOptions.middleScroll = value);
        addCheckbox(x, "Downscroll", FnfOptions.downscroll, value -> FnfOptions.downscroll = value);
        addCheckbox(x, "Show opponent", FnfOptions.showOpponent, value -> FnfOptions.showOpponent = value);
        // VS on the opponent side: my side on the left, so both players see the same stage
        addCheckbox(x, "True POV (VS, opponent side)", FnfOptions.truePov, value -> FnfOptions.truePov = value);

        // Audio offset: if the music reaches your ears late (bluetooth headphones...), move this up until hits feel right
        addSlider(x, columnWidth,
                () -> (FnfOptions.audioOffset - FnfOptions.MIN_AUDIO_OFFSET) / (double)(FnfOptions.MAX_AUDIO_OFFSET - FnfOptions.MIN_AUDIO_OFFSET),
                value -> FnfOptions.audioOffset = (int) Math.round(FnfOptions.MIN_AUDIO_OFFSET + value * (FnfOptions.MAX_AUDIO_OFFSET - FnfOptions.MIN_AUDIO_OFFSET)),
                () -> "Audio offset: " + FnfOptions.audioOffset + " ms");
        y += 6;

        // Camera
        addCheckbox(x, "FNF camera (side view)", FnfOptions.fnfCamera, value -> FnfOptions.fnfCamera = value);
        addSlider(x, columnWidth,
                () -> (FnfOptions.cameraDistance - FnfCamera.MIN_DISTANCE) / (FnfCamera.MAX_DISTANCE - FnfCamera.MIN_DISTANCE),
                value -> FnfOptions.cameraDistance = Math.round((FnfCamera.MIN_DISTANCE + value * (FnfCamera.MAX_DISTANCE - FnfCamera.MIN_DISTANCE)) * 2) / 2.0,
                () -> String.format(Locale.ROOT, "Camera distance: %.1f", FnfOptions.cameraDistance));
        y += 6;

        // Songs downloaded from servers (fnfcache/), with how much space they take. Asks before deleting.
        long cacheSize = AudioCache.cacheSize();
        if (fits(20)) {
            Button clearCacheButton = Button.builder(Component.literal("Clear downloaded songs (" + formatSize(cacheSize) + ")"), btn ->
                    this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
                        if (confirmed) AudioCache.clear();
                        this.minecraft.setScreen(this); // back here, the button shows the new size
                    }, Component.literal("Clear downloaded songs?"),
                       Component.literal("This deletes " + formatSize(cacheSize) + " of songs downloaded from servers. "
                               + "They'll be downloaded again the next time they're played.")))
            ).bounds(x, y, columnWidth, 20).build();
            clearCacheButton.active = cacheSize > 0;
            this.addRenderableWidget(clearCacheButton);
        }
        y += 24;

        // how far it can scroll: everything below the visible area
        int contentHeight = y + scrollOffset - contentTop;
        maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
        if (scrollOffset > maxScroll) { // the window got bigger: don't stay scrolled past the end
            scrollOffset = maxScroll;
            init();
            return;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width / 2 - columnWidth / 2, this.height - 28, columnWidth, 20).build());
    }

    // true if a row of this height at the current y is completely inside the visible area (only those get shown)
    private boolean fits(int height) {
        return y >= contentTop && y + height <= contentBottom;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int newOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 15)));
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset;
            init();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        // let the player know there's more below
        if (scrollOffset < maxScroll) {
            context.drawCenteredString(this.font, "Scroll for more", this.width / 2, contentBottom + 3, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // called when this screen goes away (Done, Esc, or anything else), so the options always get saved
    @Override
    public void removed() {
        FnfOptions.save();
    }

    // 1536 -> "1.5 KB", 52428800 -> "50.0 MB"
    private static String formatSize(long bytes) {
        if (bytes <= 0) return "empty";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private interface BooleanConsumer {
        void accept(boolean value);
    }

    // one checkbox row (only shown if it fits in the visible area)
    private void addCheckbox(int x, String text, boolean selected, BooleanConsumer onChange) {
        if (fits(20)) {
            this.addRenderableWidget(Checkbox.builder(Component.literal(text), this.font)
                    .pos(x, y)
                    .selected(selected)
                    .onValueChange((checkbox, value) -> onChange.accept(value))
                    .build());
        }
        y += 22;
    }

    // one slider row, from 0 to 1: getValue reads the option as 0..1, setValue writes it back, label is the text shown on it
    private void addSlider(int x, int width, DoubleSupplier getValue, DoubleConsumer setValue, java.util.function.Supplier<String> label) {
        if (fits(20)) {
            this.addRenderableWidget(new AbstractSliderButton(x, y, width, 20, Component.empty(), getValue.getAsDouble()) {
                {
                    updateMessage();
                }

                @Override
                protected void updateMessage() {
                    setMessage(Component.literal(label.get()));
                }

                @Override
                protected void applyValue() {
                    setValue.accept(value);
                }
            });
        }
        y += 24;
    }
}
