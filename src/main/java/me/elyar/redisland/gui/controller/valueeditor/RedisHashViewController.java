package me.elyar.redisland.gui.controller.valueeditor;

import javafx.beans.InvalidationListener;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.stage.Stage;
import me.elyar.redisland.StringUtils;
import me.elyar.redisland.gui.AdaptiveSplitPane;
import me.elyar.redisland.gui.ProperAlert;
import me.elyar.redisland.gui.buttontype.MyButtonType;
import me.elyar.redisland.gui.component.NameEditorPane;
import me.elyar.redisland.gui.component.TTLEditorPane;
import me.elyar.redisland.gui.component.ValueEditorPane;
import me.elyar.redisland.redis.RedisConnection;
import me.elyar.redisland.client.RedisClient;
import me.elyar.redisland.gui.controller.tabs.DataViewTabController;
import me.elyar.redisland.redis.resp.type.RespArray;
import me.elyar.redisland.redis.resp.type.RespString;
import me.elyar.redisland.util.Language;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public class RedisHashViewController implements RedisValueViewController {
    public NameEditorPane nameEditorPane;

    public TableView<Map<String, String>> valueTableView;

    public TableColumn<Map<String, String>, String> keyColumn;
    public TableColumn<Map<String, String>, String> valueColumn;

    public TextField editFieldTextField;
    public AdaptiveSplitPane splitPane;
    public ValueEditorPane valueEdit;
    public MenuItem removeMenuItem;
    public TTLEditorPane ttlEditor;
    public Button saveButton;

    private RedisClient client;

    ObservableList<Map<String, String>> dataList = FXCollections.observableList(new ArrayList<>());
    private DataViewTabController dataViewTabController;

    private void init(StringProperty key) {
        splitPane.hide();
        valueTableView.setItems(dataList);
        valueTableView.getSelectionModel().getSelectedIndices().addListener((InvalidationListener) observable -> {
            Map<String, String> keyValueMap = valueTableView.getSelectionModel().getSelectedItem();
            if (keyValueMap != null) {
                editFieldTextField.setText(keyValueMap.get("key"));
                valueEdit.setText(keyValueMap.get("value"));
                splitPane.show();
            }
        });
        valueTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        valueColumn.setCellFactory(TableValueFactory.factory);
        ttlEditor.init(client, key);
        nameEditorPane.init(client, key, dataViewTabController);
    }


    @Override
    public void initEdit(RedisConnection connection, int dbIndex, StringProperty key, DataViewTabController dataViewTabController) {
        this.dataViewTabController = dataViewTabController;
        this.client = connection.getClient();
        init(key);
        retrieveData();
    }

    private boolean isNew = false;

    @Override
    public void initNew(RedisConnection connection, int dbIndex, StringProperty key, Stage stage, DataViewTabController dataViewTabController) {
        this.dataViewTabController = dataViewTabController;
        this.client = connection.getClient();
        init(key);
        isNew = true;
    }

    @Override
    public void restore() {
        nameEditorPane.restore();
        ttlEditor.restore();
        valueEdit.restore();
    }

    @Override
    public void save() throws IOException {
        saveValue();
        ttlEditor.setTtl();
        nameEditorPane.rename();
    }

    private void retrieveData() {
        String key = nameEditorPane.getCurrentKey();
        try {
            dataList.clear();
            RespArray<RespString> dataArray = client.hgetall(key);
            for (int i = 0; i < dataArray.size(); i += 2) {
                String field = dataArray.get(i).getValue();
                String value = dataArray.get(i + 1).getValue();
                Map<String, String> kv = Map.of("key", field, "value", value);
                dataList.add(kv);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void addKey() {
        valueTableView.getSelectionModel().clearSelection();
        editFieldTextField.clear();
        valueEdit.clear();
        splitPane.show();
    }

    public void cancel() {
        splitPane.hide();
        valueTableView.getSelectionModel().clearSelection();
    }

    public void saveValue() throws IOException {
        Map<String, String> selectedItem = valueTableView.getSelectionModel().getSelectedItem();
        String key = nameEditorPane.getCurrentKey();
        if (StringUtils.isEmpty(key)) {
            Alert alert = new ProperAlert(Alert.AlertType.ERROR);
            alert.getButtonTypes().setAll(MyButtonType.OK);
            alert.setHeaderText(Language.getString("redis_alert_key_empty"));
            alert.showAndWait();
            return;
        }
        if (selectedItem != null) {
            client.hdel(key, selectedItem.get("key"));
        }
        client.hset(key, editFieldTextField.getText(), valueEdit.getText());
        valueEdit.save();
        splitPane.hide();
        retrieveData();

        if (isNew) {
            dataViewTabController.refreshThenSelect(key);
        }
        isNew = false;
    }

    public void remove() {
        ObservableList<Map<String, String>> selectedItems = valueTableView.getSelectionModel().getSelectedItems();
        Alert alert = new ProperAlert(Alert.AlertType.CONFIRMATION);
        alert.getButtonTypes().setAll(MyButtonType.YES, MyButtonType.NO);
        String name = valueTableView.getSelectionModel().getSelectedItem().get("key");
        if (selectedItems.size() == 1) {
            alert.setHeaderText(String.format(Language.getString("redis_delete_name"), name));
        } else {
            alert.setHeaderText(String.format(Language.getString("redis_delete_count"), selectedItems.size()));
        }
        alert.showAndWait().ifPresent(type -> {
            String key = nameEditorPane.getCurrentKey();
            if (type == MyButtonType.YES) {
                for (Map<String, String> selectedItem : selectedItems) {
                    try {
                        client.hdel(key, selectedItem.get("key"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                retrieveData();
            }
        });
    }

    @Override
    public boolean isSaved() {
        return nameEditorPane.isSavedBinding().get() && valueEdit.isSavedBinding().get() && ttlEditor.isSavedBinding().get();
    }
}
