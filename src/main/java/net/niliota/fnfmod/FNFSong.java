package net.niliota.fnfmod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FNFSong {
    public String name;

    public String engineType;
    public Path searchPath;


//    public Path chartFolder;
    public List<Path> chartFiles = new ArrayList<>();
    public List<Path> metadataFiles = new ArrayList<>();

//    public Path audioFolder;
    public List<Path> audioInstFiles = new ArrayList<>();
    public List<Path> audioVoicesFiles = new ArrayList<>();


    public FNFSong(String name, String engineType, Path searchPath) {
        this.name = name;
        this.engineType = engineType;
        this.searchPath = searchPath;

//        detectFolders();
        detectFiles();

    }

//    public void detectFolders() {
//        switch (engineType) {
//            case "codename":
//                chartFolder = modPath.resolve("songs").resolve(name).resolve("charts");
//                audioFolder = modPath.resolve("songs").resolve(name).resolve("song");
//                break;
//
//            case "psych":
//                chartFolder = modPath.resolve("data").resolve(name);
//                audioFolder = modPath.resolve("songs").resolve(name);
//                break;
//
//            case "funkin":
//                chartFolder = modPath.resolve("assets").resolve("data").resolve("songs").resolve(name);
//                audioFolder = modPath.resolve("assets").resolve("songs").resolve(name);
//                break;
//
//            case "unknown":
//                chartFolder = modPath.resolve("assets").resolve("data").resolve("songs").resolve(name);
//                audioFolder = modPath.resolve("assets").resolve("songs").resolve(name);
//                break;
//        }
//    }

    // Used to detect every chart and Inst*.ogg and Voices*.ogg file and add them to appropriate lists.
    public void detectFiles() {
        // first find all folders named after the song
        List<Path> songNameDirs;
        try (Stream<Path> stream = Files.walk(searchPath, 6)) {
            songNameDirs = stream
                    .filter(p -> Files.isDirectory(p) && p.getFileName().toString().equals(name)) // filter for directories with the song's name
                    .filter(p -> !isInAssetFolder(p)) // but not ones like images/characters/<name>, those are sprites, not songs
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // then search inside each found folder
        for (Path songDir : songNameDirs) {
            try (Stream<Path> stream = Files.walk(songDir, 3)) {
                List<Path> files = stream
                        .filter(p -> !Files.isDirectory(p))
                        .collect(Collectors.toList());

                for (Path file : files) {
                    if (isInAutosaves(songDir, file)) continue; // chart editor backups, not real charts
                    String filename = file.getFileName().toString().toLowerCase();
                    if (!filename.contains("meta") && filename.endsWith(".json") && !filename.contains("events"))       chartFiles.add(file);
                    if (filename.contains("meta") && filename.endsWith(".json"))                                        metadataFiles.add(file);
                    if (filename.contains("inst") && filename.endsWith(".ogg"))                                         audioInstFiles.add(file);
                    if (filename.contains("voice") && filename.endsWith(".ogg"))                                        audioVoicesFiles.add(file);
                }
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    // Folders that hold sprites, sounds, menu music and so on. A folder with the song's name inside one of these
    // (like images/characters/pico for the song "pico") isn't the song's folder.
    private static final List<String> ASSET_FOLDERS = List.of("images", "characters", "stages", "sounds", "music", "videos", "fonts", "shaders");

    // true if the folder is somewhere inside one of the ASSET_FOLDERS (only looking at the part inside the pack)
    private boolean isInAssetFolder(Path folder) {
        for (Path part : searchPath.relativize(folder)) {
            if (ASSET_FOLDERS.contains(part.toString().toLowerCase())) return true;
        }
        return false;
    }

    // true if the file is somewhere inside an "autosaves" folder (Codename's chart editor puts backups there)
    private static boolean isInAutosaves(Path songDir, Path file) {
        for (Path folder : songDir.relativize(file)) {
            String name = folder.toString().toLowerCase();
            if (name.equals("autosaves") || name.equals("autosave")) return true;
        }
        return false;
    }

}