package space.niliota.fnfmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import space.niliota.fnfmod.Audio.AudioManager;
import space.niliota.fnfmod.Audio.AudioTrack;
import space.niliota.fnfmod.Screens.PackSelectScreen;
import space.niliota.fnfmod.item.ModItems;
import space.niliota.fnfmod.network.ClientNetworking;
import space.niliota.fnfmod.network.ClientVs;
import org.lwjgl.glfw.GLFW;

public class FNFmodClient implements ClientModInitializer {
	public static KeyMapping leftKey;
	public static KeyMapping downKey;
	public static KeyMapping upKey;
	public static KeyMapping rightKey;
	public static KeyMapping cameraKey;

	// NoteManager Instance (maybe it shouldn't have one (prob should for multiplayer))
	public static NoteManager noteManager = new NoteManager();
	// I create the instance here cuz i cant think of a different universal place.
	// The renderer itself won't work, just keeps creating new Instances.

	@Override
	public void onInitializeClient() {
		// the player's saved options (config/fnfmod.json)
		FnfOptions.load();

		leftKey  = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fnfmod.left",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT,  "category.fnfmod.keys"));
		downKey  = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fnfmod.down",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN,  "category.fnfmod.keys"));
		upKey    = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fnfmod.up",    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP,    "category.fnfmod.keys"));
		rightKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fnfmod.right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, "category.fnfmod.keys"));

		// FNF camera (side view), toggled with V by default, only while a song is playing
		cameraKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fnfmod.camera", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.fnfmod.keys"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (cameraKey.consumeClick()) {
				if (Gameplay.running) FnfCamera.toggle();
			}
			FnfCamera.tick(client);
		});


		// Right-clicking a player with the Mic challenges them to VS, unless a song is playing or I'm already in a match:
		// then it does nothing special, and the Mic opens its menu like it does anywhere else (below).
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!level.isClientSide() || !player.getItemInHand(hand).is(ModItems.MIC) || !(entity instanceof Player target)) return InteractionResult.PASS;
			if (Gameplay.running || ClientVs.inMatch()) return InteractionResult.PASS;

			Minecraft.getInstance().player.connection.sendCommand("fnfvs challenge " + target.getGameProfile().getName());
			return InteractionResult.SUCCESS; // challenged, so don't also open the menu
		});

		// Right-clicking with the Mic opens the menu. PASS lets the item do its own thing too (sound, cooldown).
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide() && stack.is(ModItems.MIC)) {
				Minecraft.getInstance().setScreen(
						new PackSelectScreen(Component.empty())
				);
			}
			return InteractionResultHolder.pass(stack);
		});

		// Receiving songs from the server, and VS matches
		ClientNetworking.init();
		ClientVs.init();

		RenderNotes.render();
		// We initialize/register the HUDRenderCallback thing only once here, since it cannot be unregistered ever again.
		Gameplay.init();
		GameplayHud.init();

		// Check if music has ended every tick. (Seems not very efficient)
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Gameplay.checkPause(); // also here, in case the HUD isn't drawn (F1)
			if (RenderNotes.visible && Gameplay.musicStarted && !Gameplay.paused) { // during the countdown or a pause nothing is playing
				boolean anyPlaying = AudioManager.tracks.stream().anyMatch(AudioTrack::isPlaying);

				if (!anyPlaying) {
//					RenderNotes.visible = false;
//					AudioManager.tracks.clear();
					Gameplay.songFinished(); // shows the results, then stops
				}
			}
		});
	}
}