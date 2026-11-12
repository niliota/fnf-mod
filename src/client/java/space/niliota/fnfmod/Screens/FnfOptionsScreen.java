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
    private static final int WIDTH = 200; // width of the column of options

    private final Screen parent; // the screen to go back to

    public FnfOptionsScreen(Screen parent) {
        super(Component.literal("FNFmod Options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - WIDTH / 2;
        int y = 40;

        // Gameplay
        addCheckbox(x, y, "No fail", FnfOptions.noFail, value -> FnfOptions.noFail = value);
        y += 24;
        addCheckbox(x, y, "Middle scroll", FnfOptions.middleScroll, value -> FnfOptions.middleScroll = value);
        y += 24;
        addCheckbox(x, y, "Downscroll", FnfOptions.downscroll, value -> FnfOptions.downscroll = value);
        y += 24;
        addCheckbox(x, y, "Show opponent", FnfOptions.showOpponent, value -> FnfOptions.showOpponent = value);
        y += 30;

        // Audio offset: if the music reaches your ears late (bluetooth headphones...), move this up until hits feel right
        addSlider(x, y,
                () -> (FnfOptions.audioOffset - FnfOptions.MIN_AUDIO_OFFSET) / (double)(FnfOptions.MAX_AUDIO_OFFSET - FnfOptions.MIN_AUDIO_OFFSET),
                value -> FnfOptions.audioOffset = (int) Math.round(FnfOptions.MIN_AUDIO_OFFSET + value * (FnfOptions.MAX_AUDIO_OFFSET - FnfOptions.MIN_AUDIO_OFFSET)),
                () -> "Audio offset: " + FnfOptions.audioOffset + " ms");
        y += 30;

        // Camera
        addCheckbox(x, y, "FNF camera (side view)", FnfOptions.fnfCamera, value -> FnfOptions.fnfCamera = value);
        y += 24;
        addSlider(x, y,
                () -> (FnfOptions.cameraDistance - FnfCamera.MIN_DISTANCE) / (FnfCamera.MAX_DISTANCE - FnfCamera.MIN_DISTANCE),
                value -> FnfOptions.cameraDistance = Math.round((FnfCamera.MIN_DISTANCE + value * (FnfCamera.MAX_DISTANCE - FnfCamera.MIN_DISTANCE)) * 2) / 2.0,
                () -> String.format(Locale.ROOT, "Camera distance: %.1f", FnfOptions.cameraDistance));
        y += 30;

        // Songs downloaded from servers (fnfcache/), with how much space they take. Asks before deleting.
        long cacheSize = AudioCache.cacheSize();
        Button clearCacheButton = Button.builder(Component.literal("Clear downloaded songs (" + formatSize(cacheSize) + ")"), btn ->
                this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
                    if (confirmed) AudioCache.clear();
                    this.minecraft.setScreen(this); // back here, the button shows the new size
                }, Component.literal("Clear downloaded songs?"),
                   Component.literal("This deletes " + formatSize(cacheSize) + " of songs downloaded from servers. "
                           + "They'll be downloaded again the next time they're played.")))
        ).bounds(x, y, WIDTH, 20).build();
        clearCacheButton.active = cacheSize > 0;
        this.addRenderableWidget(clearCacheButton);
        y += 34;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(x, y, WIDTH, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
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

    private void addCheckbox(int x, int y, String text, boolean selected, BooleanConsumer onChange) {
        this.addRenderableWidget(Checkbox.builder(Component.literal(text), this.font)
                .pos(x, y)
                .selected(selected)
                .onValueChange((checkbox, value) -> onChange.accept(value))
                .build());
    }

    // a slider from 0 to 1: getValue reads the option as 0..1, setValue writes it back, label is the text shown on it
    private void addSlider(int x, int y, DoubleSupplier getValue, DoubleConsumer setValue, java.util.function.Supplier<String> label) {
        this.addRenderableWidget(new AbstractSliderButton(x, y, WIDTH, 20, Component.empty(), getValue.getAsDouble()) {
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
}
