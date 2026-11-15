package space.niliota.fnfmod.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// Mic menu -> "Challenge (VS)": every other player on the server, with a search box. Clicking one sends them a VS challenge
// (/fnfvs challenge <name>), the server tells you if they don't have the mod.
public class VsPlayerListScreen extends FnfMenuScreen {
    private final Screen parent;

    public VsPlayerListScreen(Screen parent) {
        super(Component.literal("Challenge a player"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        setupLayout("Search players...", List.of(
                button("Back", btn -> Minecraft.getInstance().setScreen(parent))
        ));
    }

    @Override
    protected List<MenuList.Row> buildRows(String search) {
        Minecraft minecraft = Minecraft.getInstance();
        List<String> names = new ArrayList<>();
        if (minecraft.getConnection() != null && minecraft.player != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                String name = info.getProfile().getName();
                if (!info.getProfile().getId().equals(minecraft.player.getUUID())) names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);

        List<MenuList.Row> rows = rows();
        for (String name : names) {
            if (!search.isEmpty() && !name.toLowerCase().contains(search)) continue;
            rows.add(new MenuList.Entry(name, "Click to challenge to a VS match", () -> {
                minecraft.player.connection.sendCommand("fnfvs challenge " + name);
                onClose();
            }));
        }
        return rows;
    }

    @Override
    protected String emptyText() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean alone = minecraft.getConnection() == null || minecraft.getConnection().getOnlinePlayers().size() <= 1;
        return alone ? "Nobody else is online." : "No players found.";
    }
}
