package me.elyar.redisland.gui.controller.valueeditor;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.stage.Stage;
import me.elyar.redisland.redis.RedisConnection;
import me.elyar.redisland.gui.controller.tabs.DataViewTabController;

import java.io.IOException;

public interface RedisValueViewController {
    // 值是否被保存
    boolean isSaved();

    void initEdit(RedisConnection connection, int dbIndex, StringProperty key, DataViewTabController dataViewTabController);
    void initNew(RedisConnection connection, int dbIndex, StringProperty key, Stage stage, DataViewTabController dataViewTabController);
    void restore();

    void save() throws IOException;
}
