package me.elyar.redisland.util;

import me.elyar.redisland.Constants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConnectionProfile {
    private final static Path path = Paths.get(System.getProperty("user.home"), Constants.CONNECTION_DIR, Constants.CONNECTION_FILE_NAME);
    private final static Path folderPath = Paths.get(System.getProperty("user.home"), Constants.CONNECTION_DIR);

    public static void write(String data) throws IOException {
        File folder = new File(folderPath.toUri());
        if(!folder.exists()) {
            folder.mkdir();
        }
        Files.writeString(path, data);
    }

    public static String read() throws IOException {
        return Files.readString(path);
    }
}
