package net.niliota.fnfmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.niliota.fnfmod.Audio.AudioManager;
import net.niliota.fnfmod.Audio.AudioTrack;
import net.niliota.fnfmod.Screens.PackSelectScreen;
import net.niliota.fnfmod.item.ModItems;
import net.niliota.fnfmod.network.ClientNetworking;
import org.lwjgl.glfw.GLFW;

public class FNFmodClient implements ClientModInitializer {
	public static KeyMapping leftKey;
	public static KeyMapping downKey;
	public static KeyMapping upKey;
	public static KeyMapping rightKey;

	// NoteManager Instance (maybe it shouldn't have one (prob should for multiplayer))
	public static NoteManager noteManager = new NoteManager();
	// I create the instance here cuz i cant think of a different universal place.
	// The renderer itself won't work, just keeps creating new Instances.

	@Override
	public void onInitializeClient() {
		leftKey  = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fnfmod.left",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT,  "category.fnfmod.keys"));
		downKey  = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fnfmod.down",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN,  "category.fnfmod.keys"));
		upKey    = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fnfmod.up",    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP,    "category.fnfmod.keys"));
		rightKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fnfmod.right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, "category.fnfmod.keys"));


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

		// Receiving songs from the server
		ClientNetworking.init();

		RenderNotes.render();
		// We initialize/register the HUDRenderCallback thing only once here, since it cannot be unregistered ever again.
		Gameplay.init();
		GameplayHud.init();

		// Check if music has ended every tick. (Seems not very efficient)
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (RenderNotes.visible) {
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