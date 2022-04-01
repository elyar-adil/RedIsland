package me.elyar.redisland.util;

import me.elyar.redisland.gui.controller.PrimaryController;

import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;

public class Language {
    private static ResourceBundle bundle = ResourceBundle.getBundle("bundles.LangBundle", Locale.getDefault());

    static {
        String profile = null;
        PrimaryController.Settings settings = null;
        try {
            profile = ConnectionProfile.read();
            settings = PrimaryController.yaml.load(profile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (settings != null) {
            setLanguage(settings.getLanguage());
        }
    }

    //private static ResourceBundle bundle = ResourceBundle.getBundle("bundles.LangBundle", Locale.ENGLISH);
    public static String getString(String key) {
        return bundle.getString(key);
    }

    public static ResourceBundle getBundle() {
        return bundle;
    }

    private static String language;

    public static void setLanguage(Locale language) {
        Language.language = language.getLanguage();
        Locale.setDefault(language);
        bundle = ResourceBundle.getBundle("bundles.LangBundle", language);
    }

    public static String getLanguage() {
        return language;
    }

    public static void setLanguage(String language) {
        if (language.equals("zh")) {
            setLanguage(Locale.CHINESE);
        } else {
            setLanguage(Locale.ENGLISH);
        }
    }
}
