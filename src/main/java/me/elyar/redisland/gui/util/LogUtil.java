package me.elyar.redisland.gui.util;

import me.elyar.redisland.gui.controller.PrimaryController;

public class LogUtil {
    public static void log(String log) {
        PrimaryController.logStatusBar(log, "INFO");
    }
    public static void logw(String log) {
        PrimaryController.logStatusBar(log, "WARNING");
    }
}
