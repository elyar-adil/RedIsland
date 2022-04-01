package me.elyar.redisland.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FileUtil {
    public static void write(File file, String data) throws IOException {
        Files.writeString(file.toPath(), data);
    }

    public static String read(File file) throws IOException {
        return Files.readString(file.toPath());
    }
}
