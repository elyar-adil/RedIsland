package me.elyar.redisland.gui.buttontype;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import me.elyar.redisland.util.Language;

public class MyButtonType {
    public final static ButtonType save = new ButtonType(Language.getString("redis_save"), ButtonBar.ButtonData.YES);
    public final static ButtonType notSave = new ButtonType(Language.getString("redis_not_save"), ButtonBar.ButtonData.NO);
    public final static ButtonType cancel = new ButtonType(Language.getString("redis_cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);

    public static final ButtonType OK = new ButtonType(Language.getString("redis_ok"), ButtonBar.ButtonData.YES);
    public static final ButtonType YES = new ButtonType(Language.getString("redis_yes"), ButtonBar.ButtonData.YES);
    public static final ButtonType NO = new ButtonType(Language.getString("redis_no"), ButtonBar.ButtonData.NO);
}
