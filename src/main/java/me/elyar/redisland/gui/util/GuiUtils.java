package me.elyar.redisland.gui.util;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.scene.paint.Color;
import me.elyar.redisland.client.RedisType;
import me.elyar.redisland.gui.App;

import java.net.URL;
import java.util.Map;
import java.util.Objects;

public class GuiUtils {

    final static Map<RedisType, Color> colorMap = Map.of(
            RedisType.LIST, Color.PURPLE,
            RedisType.HASH, Color.ORANGE,
            RedisType.SET, Color.GREEN,
            RedisType.ZSET, Color.BLUE,
            RedisType.STRING, Color.RED
    );

    public static String getResourcePath(String path) {
        return Objects.requireNonNull(App.class.getResource(path)).toExternalForm();
    }

    public static URL getResourceURL(String path) {
        return App.class.getResource(path);
    }

    public static FontAwesomeIconView getRedisDataIconView(RedisType type) {
        return getIconView(FontAwesomeIcon.CUBE, colorMap.get(type));
    }

    public static FontAwesomeIconView getIconView(FontAwesomeIcon icon, Color color) {
        FontAwesomeIconView iconView = new FontAwesomeIconView(icon);
        iconView.setFill(color);
        return iconView;
    }
}
