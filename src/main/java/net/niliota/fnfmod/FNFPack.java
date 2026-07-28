package net.niliota.fnfmod;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class FNFPack {
    public int id;
    public String name;
    public Path packPath;
    public Path modPath;
    public List<Path> modPaths = new ArrayList<>();
    public List<FNFSong> songs = new ArrayList<>();
    public String engineType; // Psych, Vanilla, Kade, whatev

    FNFPack(int id, String name, Path path) {
        this.id = id;
        this.name = name;
        this.packPath = path;
        this.modPath = resolveModPath();
        resolveModPaths();
        this.engineType = detectEngineType();

        universalScanSongs();
    }

//    public void scanSongs() {
//        Path songsDir;
//        switch (engineType) {
//            case "codename":
//                songsDir = FabricLoader.getInstance().getGameDir().resolve(modPath.toString()+"/songs");
//                break;
//
//            case "psych":
//                songsDir = FabricLoader.getInstance().getGameDir().resolve(modPath.toString()+"/songs");
//                break;
//
//            case "funkin":
//                songsDir = FabricLoader.getInstance().getGameDir().resolve(modPath.toString()+"/assets/songs");
//                break;
//
//            case "unknown":
//                songsDir = FabricLoader.getInstance().getGameDir().resolve(modPath.toString()+"/songs");
//                break;
//
//            default:
//                songsDir = FabricLoader.getInstance().getGameDir().resolve(modPath.toString()+"/songs");
//        }
//
//        if (!Files.exists(songsDir)) {
//            try { Files.createDirectories(songsDir); }
//            catch (IOException e) { e.printStackTrace(); }
//                    //return packs;
//        }
//
//        try (DirectoryStream<Path> stream = Files.newDirectoryStream(songsDir)) {
//            int i = 0;
//            for (Path path : stream) {
//                if (Files.isDirectory(path)) { // if the found 'thing' is a directory and not a file
//                    FNFSong song = new FNFSong(i, path.getFileName().toString(), engineType, modPath);
//                    songs.add(song);
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    public void universalScanSongs() {
        List<Path> songsDirectories;

        for (Path modPath : modPaths) { // for each modPath found, we search for songs inside
            try (Stream<Path> stream = Files.walk(modPath, 6)) {
                songsDirectories = stream // all folders named "songs" will be stored here
                        .filter(p -> Files.isDirectory(p) && p.getFileName().toString().equals("songs")) // only select folders named "songs"
                        .collect(Collectors.toList());
            } catch (IOException e) {
                e.printStackTrace();
                songsDirectories = new ArrayList<>();
            }

            // Search each found "songs" directory for other directories that are the songs themselves.
            for (Path songDir : songsDirectories) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(songDir)) {
                    for (Path path : stream) {
                        if (Files.isDirectory(path)) { // if the found 'thing' is a directory and not a file
                            FNFSong song = new FNFSong(path.getFileName().toString(), engineType, packPath); // packPath so that its able to scan for all mods inside
                            songs.add(song);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // If 0 songs are found, we can try looking at the packPath instead for anything.
        if (songs.toArray().length == 0) {
            try (Stream<Path> stream = Files.walk(packPath, 6)) { // start "walking" in the packPath folder
                songsDirectories = stream
                        .filter(p -> Files.isDirectory(p) && p.getFileName().toString().equals("songs")) // only select folders named "songs"
                        .collect(Collectors.toList());
            } catch (IOException e) {
                e.printStackTrace();
                songsDirectories = new ArrayList<>();
            }

            // Search each found "songs" directory for other directories that are the songs themselves.
            for (Path songDir : songsDirectories) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(songDir)) {
                    for (Path path : stream) {
                        if (Files.isDirectory(path)) { // if the found 'thing' is a directory and not a file
                            FNFSong song = new FNFSong(path.getFileName().toString(), engineType, packPath); // packPath so that its able to scan for all mods inside
                            songs.add(song);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String listSongs() {
        String output = "songName: ";

        for (FNFSong song : songs) {
            output += song.name + " " + Arrays.toString(song.audioVoicesFiles.toArray()) + " \n";
            output += song.audioVoicesFiles.toString();
        }

        return output;
    }

    // get the actual mod path, whether it's at root or inside mods/
    private Path resolveModPath() {
        Path modsDir = packPath.resolve("mods");
        Path contentDir = packPath.resolve("content"); // Search for mods inside [packName]/content

        if (Files.exists(modsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir)) {
                for (Path sub : stream) {
                    if (Files.isDirectory(sub)) return sub; // return first mod folder found
                }
            } catch (IOException e) { e.printStackTrace(); }
        } else if (Files.exists(contentDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(contentDir)) {
                for (Path sub : stream) {
                    if (Files.isDirectory(sub)) return sub; // add the mod FOLDER that is found into the list
                }
            } catch (IOException e) { e.printStackTrace(); }
        }
        return packPath; // fallback to root
    }

    private void resolveModPaths() {
        Path modsDir = packPath.resolve("mods"); // Search for mods inside [packName]/mods/
        Path contentDir = packPath.resolve("content"); // Search for mods inside [packName]/content
        // /
        if (Files.exists(modsDir)) { // check if the folder even exists
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir)) {
                for (Path sub : stream) {
                    if (Files.isDirectory(sub)) modPaths.add(sub); // add the mod FOLDER that is found into the list
                }
            } catch (IOException e) { e.printStackTrace(); }
        } else if (Files.exists(contentDir)) { // check if the content folder exists
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(contentDir)) {
                for (Path sub : stream) {
                    if (Files.isDirectory(sub)) modPaths.add(sub); // add the mod FOLDER that is found into the list
                }
            } catch (IOException e) { e.printStackTrace(); }
        } else { // If there is not a [packName]/mods folder, just add the root folder
            modPaths.add(packPath);
        }
    }

    public String detectEngineType() {
        Path mod = resolveModPath(); // resolves to actual mod folder

        if (Files.exists(mod.resolve("engine.txt"))) {
            try { return Files.readString(mod.resolve("engine.txt")).trim().toLowerCase(); }
            catch (IOException e) { e.printStackTrace(); }
        }

        if (Files.exists(mod.resolve("pack.json"))) return "psych";
        if (Files.exists(mod.resolve("songs")) && containsChartsFolder(mod.resolve("songs"))) return "codename";
        if (Files.exists(packPath.resolve("content"))) return "nightmarevision";
        if (Files.exists(mod.resolve("assets/data"))) return "funkin";
        if (Files.exists(mod.resolve("assets/shared/data"))) return "legacy";

        return "unknown";
    }


    private static boolean containsChartsFolder(Path songsDir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(songsDir)) {
            for (Path song : stream) {
                if (Files.exists(song.resolve("charts"))) return true;
            }
        } catch (IOException e) { e.printStackTrace(); }
        return false;
    }
}
