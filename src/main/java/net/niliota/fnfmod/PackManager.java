package net.niliota.fnfmod;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PackManager {
    public static List<FNFPack> packs = new ArrayList<>();


    public static void addPack(int id, String name, Path path) {
        packs.add(new FNFPack(id, name, path));
    }

    public static String listPacks() {
        String output = "\n";

        for (FNFPack pack : packs) {
            output += "Name: " + pack.name + " ID: " + pack.id + " Type: " + pack.engineType + " Mod Path: " + pack.modPath.getFileName() +"\n";
        }

        return output;
    }


    // find and return a list of paths of packs inside the "fnfpacks" folder
    public static List<Path> findPacks() {
        List<Path> packs = new ArrayList<>();
        Path packsDir = FabricLoader.getInstance().getGameDir().resolve("fnfpacks");

        if (!Files.exists(packsDir)) {
            try { Files.createDirectories(packsDir); }
            catch (IOException e) { e.printStackTrace(); }
            return packs;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(packsDir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    packs.add(path);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return packs;
    }
}
