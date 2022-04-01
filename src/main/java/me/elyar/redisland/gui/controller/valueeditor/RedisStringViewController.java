package me.elyar.redisland.gui.controller.valueeditor;

import javafx.beans.property.StringProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import me.elyar.redisland.StringUtils;
import me.elyar.redisland.gui.ProperAlert;
import me.elyar.redisland.gui.buttontype.MyButtonType;
import me.elyar.redisland.gui.component.NameEditorPane;
import me.elyar.redisland.gui.component.TTLEditorPane;
import me.elyar.redisland.gui.component.ValueEditorPane;
import me.elyar.redisland.redis.RedisConnection;
import me.elyar.redisland.client.RedisClient;
import me.elyar.redisland.client.RespException;
import me.elyar.redisland.gui.controller.tabs.DataViewTabController;
import me.elyar.redisland.util.Language;

import java.io.IOException;
import java.util.Objects;

public class RedisStringViewController implements RedisValueViewController {
    public NameEditorPane nameEditorPane;
    public Button saveButton;
    public ValueEditorPane valueEdit;
    public TTLEditorPane ttlEditor;
    private RedisConnection connection;

    private int dbIndex;
    private boolean edit = false;

    private DataViewTabController dataViewTabController;

    private String prevValue = "";

    private String initialValue = null;

    private boolean valueChanged = false;

    public void init(RedisConnection connection, int dbIndex, StringProperty key, DataViewTabController dataViewTabController) {
        saveButton.disableProperty().bind(valueEdit.isSavedBinding());
        this.connection = connection;
        this.dbIndex = dbIndex;
        valueEdit.textProperty().addListener(ignored -> {
            if (!prevValue.equals(valueEdit.getText())) {
                valueChanged = true;
                prevValue = valueEdit.getText();
            }
        });
        this.dataViewTabController = dataViewTabController;

        ttlEditor.init(connection.getClient(), key);
        nameEditorPane.init(connection.getClient(), key, dataViewTabController);
    }


    public void initEdit(RedisConnection connection, int dbIndex, StringProperty key, DataViewTabController dataViewTabController) {
        edit = true;
        try {
            RedisClient client = connection.getClient();
            client.select(dbIndex);
            String currentValue = client.get(key.getValue());

            String value = Objects.requireNonNullElse(currentValue, "");
            prevValue = value;
            valueEdit.setText(value);
            initialValue = value;
        } catch (IOException | RespException e) {
            e.printStackTrace();
        }
        init(connection, dbIndex, key, dataViewTabController);
    }


    @Override
    public void initNew(RedisConnection connection, int dbIndex, StringProperty key, Stage stage, DataViewTabController dataViewTabController) {
        init(connection, dbIndex, key, dataViewTabController);
    }

    @Override
    public void restore() {
        nameEditorPane.restore();
        ttlEditor.restore();
        valueEdit.restore();
    }

    public void saveValue() {
        try {
            RedisClient client = connection.getClient();
            client.select(dbIndex);

            String key = nameEditorPane.getCurrentKey();

            if(StringUtils.isEmpty(key)) {
                Alert alert = new ProperAlert(Alert.AlertType.ERROR);
                alert.getButtonTypes().setAll(MyButtonType.OK);
                alert.setHeaderText(Language.getString("redis_alert_key_empty"));
                alert.showAndWait();
                return;
            }

            if (StringUtils.isEmpty(key)) {
                Alert alert = new ProperAlert(Alert.AlertType.ERROR);
                alert.getButtonTypes().setAll(MyButtonType.OK);
                alert.show();
                return;
            }
            String value = valueEdit.getText();

            client.set(key, value);
            initialValue = value;
            edit = true;
        } catch (IOException | RespException e) {
            e.printStackTrace();
        }
        valueEdit.save();
    }

    @Override
    public void save() throws IOException {
        saveValue();
        ttlEditor.setTtl();
        nameEditorPane.rename();
    }

    @Override
    public boolean isSaved() {
        return nameEditorPane.isSavedBinding().get() && valueEdit.isSavedBinding().get() && ttlEditor.isSavedBinding().get();
    }
}

