package space.niliota.fnfmod;

import net.fabricmc.api.ModInitializer;

import space.niliota.fnfmod.item.ModItems;
import space.niliota.fnfmod.network.ModPayloads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public class FNFmod implements ModInitializer {
	public static final String MOD_ID = "fnfmod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// Initialize the items
		ModItems.registerModItems();

		// Networking: register all packets, then the server side of sending songs to players
		ModPayloads.register();
		SongSender.register();
		VsManager.register();

		LOGGER.info("Gonna FNF it so good!");
		List<Path> packs = PackManager.findPacks();
		int i = 0;
		for (Path pack : packs) {
			PackManager.addPack(++i, pack.getFileName().toString(), pack);
			LOGGER.info("Found pack: {}", pack);
		}

		LOGGER.info(PackManager.listPacks());


		for (FNFPack pack : PackManager.packs) {
			LOGGER.info("Pack: " + pack.name);
//			LOGGER.info(pack.listSongs());

			// charts
			for (FNFSong song : pack.songs) {
				LOGGER.info(song.chartFiles.toString());
			}
		}



	}
}