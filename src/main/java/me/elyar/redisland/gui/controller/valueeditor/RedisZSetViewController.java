package me.elyar.redisland.gui.controller.valueeditor;

import javafx.beans.InvalidationListener;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.stage.Stage;
import me.elyar.redisland.StringUtils;
import me.elyar.redisland.client.RedisClient;
import me.elyar.redisland.gui.AdaptiveSplitPane;
import me.elyar.redisland.gui.ProperAlert;
import me.elyar.redisland.gui.buttontype.MyButtonType;
import me.elyar.redisland.gui.component.NameEditorPane;
import me.elyar.redisland.gui.component.TTLEditorPane;
import me.elyar.redisland.gui.component.ValueEditorPane;
import me.elyar.redisland.gui.controller.tabs.DataViewTabController;
import me.elyar.redisland.gui.util.GuiUtils;
import me.elyar.redisland.redis.RedisConnection;
import me.elyar.redisland.redis.resp.type.RespArray;
import me.elyar.redisland.redis.resp.type.RespString;
import me.elyar.redisland.util.Language;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RedisZSetViewController implements RedisValueViewController {
    public NameEditorPane nameEditorPane;

    public TableView<Map<String, String>> valueListView;
    private final ObservableList<Map<String, String>> valueList = FXCollections.observableList(new ArrayList<>());

    public TableColumn<Map<String, String>, String> indexColumn;
    public TableColumn<Map<String, String>, String> valueColumn;
    public TableColumn<Map<String, String>, String> scoreColumn;

    public MenuItem removeMenuItem;
    public AdaptiveSplitPane splitPane;
    public ValueEditorPane valueEdit;
    public TTLEditorPane ttlEditor;
    public TextField scoreTextField;
    private RedisClient client;

    private Integer selectedIndex = null;
    private DataViewTabController dataViewTabController;
    private boolean isNew = false;

    private void init(StringProperty key) {
        valueListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        splitPane.hide();
        valueListView.setItems(valueList);
        valueListView.getStylesheets().add(GuiUtils.getResourcePath("css/list-editor.css"));
        valueListView.getSelectionModel().getSelectedIndices().addListener((InvalidationListener) listener -> {
            boolean empty = valueListView.getSelectionModel().getSelectedIndices().isEmpty();
            removeMenuItem.setDisable(empty);

            selectedIndex = valueListView.getSelectionModel().getSelectedIndex();
            if (valueListView.getSelectionModel().getSelectedIndices().size() == 0 || valueListView.getSelectionModel().getSelectedIndices().size() > 1) {
                return;
            }
            splitPane.show();
            if (valueListView.getSelectionModel().getSelectedItem() != null) {
                Map<String, String> selected = valueListView.getSelectionModel().getSelectedItem();
                scoreTextField.setText(selected.get("score"));
                valueEdit.setText(selected.get("value"));
            }
        });
        ttlEditor.init(client, key);
        nameEditorPane.init(client, key, dataViewTabController);
        valueColumn.setCellFactory(TableValueFactory.factory);
    }

    @Override
    public void initEdit(RedisConnection connection, int dbIndex, StringProperty key, DataViewTabController dataViewTabController) {
        client = connection.getClient();
        this.dataViewTabController = dataViewTabController;
        init(key);
        refreshValueList();
    }

    public void refreshValueList() {
        try {
            loadListValue(nameEditorPane.getCurrentKey(), client);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadListValue(String key, RedisClient client) throws IOException {
        RespArray<RespString> values = client.zrange(key);
        valueList.clear();
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.size(); i += 2) {
            String k = values.get(i).getValue();
            String score = values.get(i + 1).getValue();
            Map<String, String> kv = Map.of("value", k, "score", score, "index", String.valueOf(i / 2 + 1));
            valueList.add(kv);
        }
    }

    @Override
    public void initNew(RedisConnection connection, int dbIndex, StringProperty key, Stage stage, DataViewTabController dataViewTabController) {
        this.dataViewTabController = dataViewTabController;
        init(key);
        client = connection.getClient();
        isNew = true;
    }

    @Override
    public void restore() {
        nameEditorPane.restore();
        ttlEditor.restore();
        valueEdit.restore();
    }


    public void remove() {
        List<Map<String, String>> selectedItems = valueListView.getSelectionModel().getSelectedItems();
        Alert alert = new ProperAlert(Alert.AlertType.CONFIRMATION);
        alert.getButtonTypes().setAll(MyButtonType.YES, MyButtonType.NO);
        alert.setTitle(Language.getString("redis_delete_confirmation"));
        String name = valueListView.getSelectionModel().getSelectedItem().get("value");

        if (selectedItems.size() == 1) {
            alert.setHeaderText(String.format(Language.getString("redis_delete_name"), name));
        } else {
            alert.setHeaderText(String.format(Language.getString("redis_delete_count"), selectedItems.size()));
        }
        alert.getDialogPane().getStylesheets().add(GuiUtils.getResourcePath("css/button.css"));
        alert.showAndWait().ifPresent(type -> {
                    String key = nameEditorPane.getCurrentKey();
                    if (type == MyButtonType.YES) {
                        try {
                            if (selectedItems.size() == 1) {
                                client.zrem(key, selectedItems.get(0).get("value"));
                            } else {
                                for (Map<String, String> item : selectedItems) {
                                    client.zrem(key, item.get("value"));
                                }
                            }
                            loadListValue(key, client);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );
    }

    public void edit() {
        var selectionModel = valueListView.getSelectionModel();
        if (!selectionModel.getSelectedIndices().isEmpty()) {
            int selectedIndex = selectionModel.getSelectedIndex();
        }
    }


    public void cancel() {
        splitPane.hide();
        valueListView.getSelectionModel().clearSelection();
        selectedIndex = null;
    }

    public void newValue() {
        splitPane.show();
        valueEdit.clear();
        scoreTextField.clear();
        valueListView.getSelectionModel().clearSelection();
        selectedIndex = null;
    }


    public void saveValue() throws IOException {
        String newValue = valueEdit.getText();
        String key = nameEditorPane.getCurrentKey();

        if(StringUtils.isEmpty(key)) {
            Alert alert = new ProperAlert(Alert.AlertType.ERROR);
            alert.getButtonTypes().setAll(MyButtonType.OK);
            alert.setHeaderText(Language.getString("redis_alert_key_empty"));
            alert.showAndWait();
            return;
        }
        Map<String, String> selectedItem = valueListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            client.zrem(key, selectedItem.get("value"));
        }
        client.zadd(key, scoreTextField.getText(), newValue);
        loadListValue(key, client);
        splitPane.hide();
        if (isNew) {
            dataViewTabController.refreshThenSelect(key);
        }
        isNew = false;
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
