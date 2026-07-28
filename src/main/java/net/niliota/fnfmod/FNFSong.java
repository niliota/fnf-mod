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
                    String filename = file.getFileName().toString().toLowerCase();
                    if (!filename.contains("meta") && filename.endsWith(".json") && !filename.contains("events"))       chartFiles.add(file);
                    if (filename.contains("meta") && filename.endsWith(".json"))                                        metadataFiles.add(file);
                    if (filename.contains("inst"))                                                                      audioInstFiles.add(file);
                    if (filename.contains("voice"))                                                                     audioVoicesFiles.add(file);
                }
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

}