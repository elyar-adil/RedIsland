package me.elyar.redisland.gui.controller.valueeditor;

import javafx.beans.InvalidationListener;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import me.elyar.redisland.gui.util.GuiUtils;
import me.elyar.redisland.redis.resp.type.RespArray;
import me.elyar.redisland.redis.resp.type.RespString;
import javafx.scene.control.SelectionMode;
import me.elyar.redisland.util.Language;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RedisListViewController implements RedisValueViewController {
    public NameEditorPane nameEditorPane;

    public TableView<Map<String, String>> valueTableView;
    private final ObservableList<Map<String, String>> valueList = FXCollections.observableList(new ArrayList<>());

    public TableColumn<Map<String, String>, String> indexColumn;
    public TableColumn<Map<String, String>, String> valueColumn;

    public MenuItem removeMenuItem;
    public AdaptiveSplitPane splitPane;
    public TTLEditorPane ttlEditor;
    public ValueEditorPane valueEdit;
    private RedisClient client;
    private Integer selectedIndex = null;
    private DataViewTabController dataViewTabController;

    private void init(StringProperty key) {
        valueTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        splitPane.hide();
        valueTableView.setItems(valueList);
        valueTableView.getStylesheets().add(GuiUtils.getResourcePath("css/list-editor.css"));
        valueTableView.getSelectionModel().getSelectedIndices().addListener((InvalidationListener) listener -> {
            boolean empty = valueTableView.getSelectionModel().getSelectedIndices().isEmpty();
            removeMenuItem.setDisable(empty);

            selectedIndex = valueTableView.getSelectionModel().getSelectedIndex();
            if (valueTableView.getSelectionModel().getSelectedIndices().size() == 0 || valueTableView.getSelectionModel().getSelectedIndices().size() > 1) {
                return;
            }
            splitPane.show();
            if (valueTableView.getSelectionModel().getSelectedItem() != null) {
                valueEdit.setText(valueTableView.getSelectionModel().getSelectedItem().get("value"));
            }
        });
        valueEdit.textProperty().addListener(observable -> {
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
        RespArray<RespString> values = client.lrange(key);
        valueList.clear();
        if (values == null) {
            return;
        }
        int index = 1;
        for (RespString value : values) {
            valueList.add(Map.of("index", String.valueOf(index++), "value", String.valueOf(value.getValue())));
        }
    }

    private boolean isNew = false;
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
        List<Integer> selectedIndices = new ArrayList<>(valueTableView.getSelectionModel().getSelectedIndices());
        Alert alert = new ProperAlert(Alert.AlertType.CONFIRMATION);
        alert.getButtonTypes().setAll(MyButtonType.YES, MyButtonType.NO);
        alert.setTitle(Language.getString("redis_delete_confirmation"));
        String name = valueTableView.getSelectionModel().getSelectedItem().get("value");
        // 保证从后面删除 为了保证不影响前面元素的位置
        selectedIndices.sort((a, b) -> Integer.compare(b, a));

        if (selectedIndices.size() == 1) {
            alert.setHeaderText(String.format(Language.getString("redis_delete_name"), name));
        } else {
            alert.setHeaderText(String.format(Language.getString("redis_delete_count"), selectedIndices.size()));
        }
        alert.getDialogPane().getStylesheets().add(GuiUtils.getResourcePath("css/button.css"));
        alert.showAndWait().ifPresent(type -> {
                    String key = nameEditorPane.getCurrentKey();
                    if (type == MyButtonType.YES) {
                        try {
                            if (selectedIndices.size() == 1) {
                                client.ldelete(key, selectedIndex);
                            } else {
                                for (int index : selectedIndices) {
                                    client.ldelete(key, index);
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
        var selectionModel = valueTableView.getSelectionModel();
        if (!selectionModel.getSelectedIndices().isEmpty()) {
            int selectedIndex = selectionModel.getSelectedIndex();
        }
    }


    public void cancel() {
        splitPane.hide();
        valueTableView.getSelectionModel().clearSelection();
        selectedIndex = null;
    }

    public void newValue() {
        splitPane.show();
        valueEdit.clear();
        valueTableView.getSelectionModel().clearSelection();
        selectedIndex = null;
        top = false;
    }

    boolean top = false;

    public void newValueTop() {
        splitPane.show();
        valueEdit.clear();
        valueTableView.getSelectionModel().clearSelection();
        selectedIndex = null;
        top = true;
    }

    public void saveValue() throws IOException {
        String key = nameEditorPane.getCurrentKey();

        if(StringUtils.isEmpty(key)) {
            Alert alert = new ProperAlert(Alert.AlertType.ERROR);
            alert.getButtonTypes().setAll(MyButtonType.OK);
            alert.setHeaderText(Language.getString("redis_alert_key_empty"));
            alert.showAndWait();
            return;
        }

        if (selectedIndex != null) {
            client.lset(key, selectedIndex, valueEdit.getText());
            loadListValue(key, client);
            valueTableView.getSelectionModel().select(selectedIndex);
        } else if (top) {
            client.lpush(key, valueEdit.getText());
            loadListValue(key, client);
            valueTableView.getSelectionModel().selectFirst();
        } else {
            client.rpush(key, valueEdit.getText());
            loadListValue(key, client);
            valueTableView.getSelectionModel().selectLast();
        }
        valueEdit.save();
        splitPane.hide();

        if (isNew) {
            dataViewTabController.refreshThenSelect(key);
        }
        isNew = false;
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
