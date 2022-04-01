module me.elyar {
    requires javafx.fxml;
    requires transitive javafx.graphics;
    requires transitive javafx.controls;
    requires org.yaml.snakeyaml;
    requires java.desktop;
    requires de.jensd.fx.fontawesomefx.fontawesome;
    requires org.fxmisc.richtext;

    requires reactfx;
    requires com.google.gson;
    requires org.controlsfx.controls;
    requires undofx;
    opens me.elyar.redisland.gui to javafx.graphics, javafx.fxml;
    opens me.elyar.redisland.gui.controller to javafx.fxml, org.yaml.snakeyaml;
    opens me.elyar.redisland.gui.controller.tabs to javafx.fxml;
    opens me.elyar.redisland.gui.controller.valueeditor to javafx.fxml;
    opens me.elyar.redisland.gui.component to javafx.fxml;

    exports me.elyar.redisland;
    exports me.elyar.redisland.client;
    exports me.elyar.redisland.gui;
    exports me.elyar.redisland.gui.component;
    exports me.elyar.redisland.gui.controller;
    exports me.elyar.redisland.gui.event;
    exports me.elyar.redisland.gui.controller.valueeditor;
    exports me.elyar.redisland.gui.controller.tabs;
    exports me.elyar.redisland.redis.resp.type;
    exports me.elyar.redisland.redis.resp;
    exports me.elyar.redisland.redis;
    exports me.elyar.redisland.gui.util;
}