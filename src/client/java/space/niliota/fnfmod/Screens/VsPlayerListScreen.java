package space.niliota.fnfmod.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// Mic menu -> "Challenge (VS)": every other player on the server. Clicking one sends them a VS challenge
// (/fnfvs challenge <name>), the server tells you if they don't have the mod.
public class VsPlayerListScreen extends Screen {
    private final Screen parent;
    private int scrollOffset = 0;

    public VsPlayerListScreen(Screen parent) {
        super(Component.literal("Challenge a player"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        Minecraft minecraft = Minecraft.getInstance();

        List<String> names = new ArrayList<>();
        if (minecraft.getConnection() != null && minecraft.player != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                String name = info.getProfile().getName();
                if (!info.getProfile().getId().equals(minecraft.player.getUUID())) names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);

        int x = 40; int y = 40 - scrollOffset;
        for (String name : names) {
            Button playerButton = Button.builder(Component.literal(name), (btn) -> {
                minecraft.player.connection.sendCommand("fnfvs challenge " + name);
                onClose();
            }).bounds(x, y, 120, 20).build();
            this.addRenderableWidget(playerButton);
            y += 20;
        }

        Button backButton = Button.builder(Component.literal("Back"), (btn) -> minecraft.setScreen(parent))
                .bounds(200, 10, 120, 20).build();
        this.addRenderableWidget(backButton);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawString(this.font, "Challenge a player to a VS match", 40, 40 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
        if (Minecraft.getInstance().getConnection() == null || Minecraft.getInstance().getConnection().getOnlinePlayers().size() <= 1) {
            context.drawString(this.font, "Nobody else is online.", 40, 40, 0xFFFF5555, true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, scrollOffset - (int)(scrollY * 15));
        init();
        return true;
    }
}
