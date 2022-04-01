package me.elyar.redisland.gui.controller.tabs;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Callback;
import me.elyar.redisland.gui.ProperAlert;

import me.elyar.redisland.gui.buttontype.MyButtonType;
import me.elyar.redisland.redis.RedisConnection;
import me.elyar.redisland.client.*;
import me.elyar.redisland.gui.controller.valueeditor.RedisValueViewController;
import me.elyar.redisland.gui.event.DataKeyListRefreshedEvent;
import me.elyar.redisland.gui.event.EventHandler;
import me.elyar.redisland.gui.util.GuiUtils;
import me.elyar.redisland.util.Language;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataViewTabController {
    public static final String SPLIT = ":";
    private static final boolean SHOW_LEAF_FULL_NAME = false; // 叶节点键值是否显示完全
    private RedisValueViewController currentController = null;

    public TreeView<DataTreeItem> keyTreeView;
    public AnchorPane dataPane;
    public TextField searchTextField;
    public Button newKeyButton;
    private RedisConnection redisConnection;

    private int dbIndex;
    private final Map<TreeItem<DataTreeItem>, Pair<String, RedisType>> treeItemPrefixMap = new HashMap<>();
    private final Map<String, TreeItem<DataTreeItem>> keyTreeItemMap = new HashMap<>();
    private String selectedPrefix;
    private ContextMenu newKeyContextMenu;
    Map<String, Integer> prefixAmountMap;


    public void init() {
        keyTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        keyTreeView.setCellFactory(new Callback<>() {
            @Override
            public TreeCell<DataTreeItem> call(TreeView<DataTreeItem> p) {
                return new TreeCell<>() {
                    @Override
                    protected void updateItem(DataTreeItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setText(null);
                            setGraphic(null);
                            setContextMenu(null);
                        } else {
                            TreeItem<DataTreeItem> i = getTreeItem();
                            if (i.getValue() == null) {
                                return;
                            }
                            HBox hBox = new HBox();
                            hBox.getChildren().add(i.getGraphic());
                            HBox innerHBox = new HBox(new Label(i.getValue().text));
                            hBox.getChildren().add(innerHBox);
                            HBox.setHgrow(innerHBox, Priority.ALWAYS);
                            if (i.getValue().type != null) {
                                Label label = new Label("[" + i.getValue().type.name() + "]");
                                label.setTextFill(Color.rgb(200, 200, 200));
                                label.getStyleClass().add("type-label");
                                hBox.getChildren().add(label);
                            }
                            setGraphic(hBox);
                            ContextMenu contextMenu = createContextMenu(this);
                            setContextMenu(contextMenu);
                        }
                    }
                };
            }

            private ContextMenu createContextMenu(TreeCell<DataTreeItem> cell) {
                TreeItem<DataTreeItem> treeItem = cell.getTreeItem();
                ContextMenu contextMenu = new ContextMenu();
                MenuItem deleteMenuItem = new MenuItem(Language.getString("redis_remove"));
                deleteMenuItem.setOnAction(event -> deleteTreeItemWithConfirmation(keyTreeView.getSelectionModel().getSelectedItems()));
                if (!treeItem.isLeaf()) {
                    MenuItem openItem = new MenuItem(treeItem.isExpanded() ? Language.getString("redis_collapse") :  Language.getString("redis_expand"));
                    openItem.setOnAction(event -> treeItem.setExpanded(!treeItem.isExpanded()));
                    contextMenu.getItems().add(openItem);
                }
                contextMenu.getItems().add(deleteMenuItem);
                return contextMenu;
            }
        });
        newKeyContextMenu = initNewKeyContextMenu();

        searchTextField.setOnKeyTyped(event -> refresh());

        keyTreeView.getSelectionModel().getSelectedItems().addListener((InvalidationListener) listener -> {
            var items = keyTreeView.getSelectionModel().getSelectedItems();
            if (items.size() > 0 && openDataWhenSelectionChanged) {
                TreeItem<DataTreeItem> treeItem = items.get(0);
                openTreeItem(treeItem);
            } else if (openDataWhenSelectionChanged && clearDataWhenSelectionNotFound) {
                dataPane.getChildren().clear();
            }
        });
        keyTreeView.addEventFilter(MouseEvent.MOUSE_CLICKED, mouseEvent -> {
            if (mouseEvent.getClickCount() == 1 && mouseEvent.getButton() == MouseButton.PRIMARY) {
                Node node = mouseEvent.getPickResult().getIntersectedNode();
                if (node instanceof Text || (node instanceof TreeCell && !((TreeCell<?>) node).isEmpty())) {
                    TreeItem<DataTreeItem> treeItem = keyTreeView.getSelectionModel().getSelectedItem();
                    if (treeItem != null && !treeItem.isLeaf()) {
                        treeItem.setExpanded(!treeItem.isExpanded());
                    }
                }
            }
        });
    }

    public void openTreeItem(TreeItem<DataTreeItem> treeItem) {
        Pair<String, RedisType> pair = treeItemPrefixMap.get(treeItem);
        if (pair != null && pair.getSecond() != null) {
            openData(pair.getFirst(), pair.getSecond(), true);
        }
    }

    private TreeItem<DataTreeItem> prevItem = null;

    private boolean isCurrentDataNewlyAdded = false;

    private boolean openDataWhenSelectionChanged = true;
    private boolean clearDataWhenSelectionNotFound = false;

    private void openData(String key, RedisType type, boolean edit) {
        TreeItem<DataTreeItem> root = keyTreeView.getRoot();

        if (currentController != null && !currentController.isSaved()) {
            Alert alert = new ProperAlert(Alert.AlertType.CONFIRMATION);

            alert.setHeaderText(Language.getString("redis_save_confirm"));
            alert.getButtonTypes().setAll(MyButtonType.save, MyButtonType.notSave, MyButtonType.cancel);
            alert.showAndWait();
            if (alert.getResult() == MyButtonType.notSave) {
                // 删除新增节点
                if (isCurrentDataNewlyAdded) {
                    openDataWhenSelectionChanged = false;
                    root.getChildren().remove(prevItem);
                    openDataWhenSelectionChanged = true;
                }
                prevItem = null;
                currentController.restore();
            } else if (alert.getResult() == MyButtonType.save) {
                try {
                    currentController.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                prevItem = null;
                return;
            } else {
                if (prevItem != null) {
                    openDataWhenSelectionChanged = false;
                    keyTreeView.getSelectionModel().clearSelection();
                    keyTreeView.getSelectionModel().select(prevItem);
                    openDataWhenSelectionChanged = true;
                }
                return;
            }
        }
        TreeItem<DataTreeItem> item;
        StringProperty keyProperty = new SimpleStringProperty(key);
        if (!edit) { // 新增数据
            // 添加新节点
            item = new TreeItem<>();
            FontAwesomeIconView fontAwesomeIconView = GuiUtils.getRedisDataIconView(type);
            item.setGraphic(fontAwesomeIconView);
            root.getChildren().add(item);
            keyTreeView.getSelectionModel().clearSelection();
            keyTreeView.getSelectionModel().select(item);
            //
            isCurrentDataNewlyAdded = true;
        } else {
            item = keyTreeItemMap.get(key);
            isCurrentDataNewlyAdded = false;
        }

        prevItem = item;


        Node dataNode = loadValueEditor(redisConnection, dbIndex, type, edit, keyProperty);

        dataPane.getChildren().clear();
        dataPane.getChildren().add(dataNode);

        AnchorPane.setTopAnchor(dataNode, 0.0);
        AnchorPane.setBottomAnchor(dataNode, 0.0);
        AnchorPane.setLeftAnchor(dataNode, 0.0);
        AnchorPane.setRightAnchor(dataNode, 0.0);

    }


    public void setConnection(RedisConnection redisConnection, int dbIndex) {
        this.redisConnection = redisConnection;
        this.dbIndex = dbIndex;
        refresh();
    }

    EventHandler<DataKeyListRefreshedEvent> eventHandler = null;

    public void setOnRefreshedEventHandler(EventHandler<DataKeyListRefreshedEvent> eventHandler) {
        this.eventHandler = eventHandler;
    }

    private Map<String, TreeItem<DataTreeItem>> prefixTreeItemMap = null;

    public void refresh() {
        keyTreeItemMap.clear();
        RedisClient client = redisConnection.getClient();
        String pattern = searchTextField.getText() + "*";
        prefixAmountMap = new HashMap<>();
        try {
            client.select(dbIndex);
            ScanIterator iterator = new ScanIterator(redisConnection.getClient(), pattern);
            TreeItem<DataTreeItem> rootItem = new TreeItem<>();
            prefixTreeItemMap = new HashMap<>();
            int keyCount = 0;
            TreeItem<DataTreeItem> selectedTreeItem = null;
            while (iterator.hasNext()) {
                keyCount++;
                Pair<String, RedisType> keyTypePair = iterator.next();

                String key = keyTypePair.getFirst();
                selectedTreeItem = addItem(pattern, rootItem, selectedTreeItem, keyTypePair, key);
            }

            TreeItem<DataTreeItem> finalSelectedTreeItem = selectedTreeItem;
            Platform.runLater(() -> {
                keyTreeView.setRoot(rootItem);
                openDataWhenSelectionChanged = false;
                clearDataWhenSelectionNotFound = true;
                keyTreeView.getSelectionModel().select(finalSelectedTreeItem);
                openDataWhenSelectionChanged = true;
                clearDataWhenSelectionNotFound = false;
            });

            if (eventHandler != null && "*".equals(pattern)) {
                int finalKeyCount = keyCount;
                Platform.runLater(() -> onKeyTreeRefreshed(finalKeyCount));
            }
        } catch (IOException |
                RespException e) {
            e.printStackTrace();
        }
    }

    private TreeItem<DataTreeItem> addItem(String pattern, TreeItem<DataTreeItem> rootItem, TreeItem<DataTreeItem> selectedTreeItem, Pair<String, RedisType> keyTypePair, String key) {
        String[] keySplit = key.split(SPLIT, -1);

        TreeItem<DataTreeItem> firstTreeItem = null;
        TreeItem<DataTreeItem> parentTreeItem = null;
        if (prefixTreeItemMap.containsKey(key)) {
            RedisType type = keyTypePair.getSecond();
            TreeItem<DataTreeItem> treeItem = new TreeItem<>(new DataTreeItem(key, type));

            FontAwesomeIconView fontAwesomeIconView = GuiUtils.getRedisDataIconView(type);
            treeItem.setGraphic(fontAwesomeIconView);

            treeItemPrefixMap.put(treeItem, keyTypePair);
            keyTreeItemMap.put(keyTypePair.getFirst(), treeItem);
            rootItem.getChildren().add(treeItem);
        } else {
            String prefix = "";
            for (int i = 0; i < keySplit.length; i++) {
                String keyPart = keySplit[i];
                prefix = prefix + (i == 0 ? "" : SPLIT) + keyPart;

                TreeItem<DataTreeItem> treeItem;
                if (prefixTreeItemMap.containsKey(prefix)) {
                    treeItem = prefixTreeItemMap.get(prefix);

                    updateCount(prefix, keyPart, treeItem);
                } else {
                    treeItem = new TreeItem<>(new DataTreeItem(SHOW_LEAF_FULL_NAME ? (i == keySplit.length - 1 ? key : keyPart) : keyPart, keyTypePair.getSecond()));

                    if (i == keySplit.length - 1) {

                        treeItemPrefixMap.put(treeItem, new Pair<>(prefix, keyTypePair.getSecond()));
                        FontAwesomeIconView fontAwesomeIconView = GuiUtils.getRedisDataIconView(keyTypePair.getSecond());

                        treeItem.setGraphic(fontAwesomeIconView);

                        keyTreeItemMap.put(keyTypePair.getFirst(), treeItem);
                        if (selectedPrefix != null) {
                            if (prefix.equals(selectedPrefix)) {
                                selectedTreeItem = treeItem;
                            }
                        }
                    } else {
                        FontAwesomeIconView fontAwesomeIconView = GuiUtils.getIconView(FontAwesomeIcon.FOLDER, Color.rgb(50, 50, 50));
                        treeItem.expandedProperty().addListener(listener -> {
                            ObservableBooleanValue expended = (ObservableBooleanValue) listener;
                            if (expended.get()) {
                                fontAwesomeIconView.setIcon(FontAwesomeIcon.FOLDER_OPEN);
                            } else {
                                fontAwesomeIconView.setIcon(FontAwesomeIcon.FOLDER);
                            }
                        });
                        treeItem.setGraphic(fontAwesomeIconView);

                        updateCount(prefix, keyPart, treeItem);

                        treeItemPrefixMap.put(treeItem, new Pair<>(prefix, null));
                        // 如果用户使用了过滤器 展开搜索结果中所有节点
                        if (!"*".equals(pattern)) {
                            treeItem.setExpanded(true);
                        }
                    }
                    if (i == 0) {
                        firstTreeItem = treeItem;
                    } else {
                        parentTreeItem.getChildren().add(treeItem);
                    }
                }

                if (i != keySplit.length - 1) {
                    prefixTreeItemMap.put(prefix, treeItem);
                }
                parentTreeItem = treeItem;

            }
            if (firstTreeItem != null) {
                rootItem.getChildren().add(firstTreeItem);
            }
        }
        return selectedTreeItem;
    }

    /**
     * 更新中间节点的子节点的数量
     *
     * @param prefix   当前中间节点的前缀
     * @param keyPart  当前中间节点对应的键值
     * @param treeItem 当前中间节点
     */
    private void updateCount(String prefix, String keyPart, TreeItem<DataTreeItem> treeItem) {
        int count = prefixAmountMap.getOrDefault(prefix, 0) + 1;
        prefixAmountMap.put(prefix, count);
        treeItem.setValue(new DataTreeItem(String.format("%s (%d)", keyPart, count), null));
    }


    private void onKeyTreeRefreshed(int keyCount) {
        eventHandler.handle(new DataKeyListRefreshedEvent(keyCount));
    }


    public void keyReleased(KeyEvent event) {
        if (event.getCode() == KeyCode.DELETE) {
            deleteTreeItemWithConfirmation(keyTreeView.getSelectionModel().getSelectedItems());
        } else if (event.getCode() == KeyCode.F5) {
            refreshKeepSelection();
        } else if (KeyCombination.keyCombination("Ctrl+F").match(event)) {
            searchTextField.requestFocus();
        }
    }

    public void deleteTreeItemWithConfirmation(List<TreeItem<DataTreeItem>> list) {
        boolean confirmed = confirmDelete(list);
        if (confirmed) {
            for (var item : list) {
                String key = treeItemPrefixMap.get(item).getFirst();
                deleteKey(item, key);
            }
        }
    }

    public void deleteKey(TreeItem<DataTreeItem> item, String key) {
        try {
            RedisClient client = redisConnection.getClient();
            if (!item.isLeaf()) {
                key += SPLIT + "*";
                client.batchDelete(key);
            } else {
                client.delete(key);
            }
            refresh();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get count of leaf items in a list of TreeItem.
     *
     * @param treeItemList the list to count leaf items.
     * @return leaf items count
     */
    private int getLeafCount(List<TreeItem<DataTreeItem>> treeItemList) {
        int leafCount = 0;
        for (var treeItem : treeItemList) {
            if (treeItem.isLeaf()) {
                leafCount++;
            } else {
                leafCount += getLeafCount(treeItem.getChildren());
            }
        }
        return leafCount;
    }

    public boolean confirmDelete(List<TreeItem<DataTreeItem>> list) {
        Alert alert = new ProperAlert(Alert.AlertType.CONFIRMATION);
        alert.getButtonTypes().setAll(MyButtonType.YES, MyButtonType.NO);
        alert.setTitle(Language.getString("redis_delete_confirmation"));
        if (list.size() == 1) {
            var item = list.get(0);
            var key = item.getValue().text;
            if (item.isLeaf()) {
                alert.setHeaderText(String.format(Language.getString("redis_delete_template_1"), key));
            } else {
                alert.setHeaderText(String.format(Language.getString("redis_delete_template_2"), key));
            }
        } else {
            alert.setHeaderText(String.format(Language.getString("redis_delete_template_3"), getLeafCount(list)));
        }

        alert.showAndWait();
        return alert.getResult() == MyButtonType.YES;
    }

    public void refreshKeepSelection() {
        TreeItem<DataTreeItem> selectedItem = keyTreeView.getSelectionModel().getSelectedItem();
        Pair<String, RedisType> pair = treeItemPrefixMap.get(selectedItem);
        if (pair != null) {
            selectedPrefix = pair.getFirst();
        }
        refresh();
    }

    public void refreshThenSelect(String key) {
        // 保存后刷新列表 丢弃之前的树的结点  下面刷新会自动添加
        prevItem = null;

        selectedPrefix = key;
        refresh();
    }

    public void mouseClicked(MouseEvent mouseEvent) {
        newKeyContextMenu.show(newKeyButton,
                // 左边跟按钮左边对其
                mouseEvent.getScreenX() - mouseEvent.getX(),
                // 顶边跟按钮下面对其
                mouseEvent.getScreenY() - mouseEvent.getY() + newKeyButton.getHeight());
    }

    private ContextMenu initNewKeyContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem stringItem = new MenuItem("STRING");
        FontAwesomeIconView stringIcon = GuiUtils.getRedisDataIconView(RedisType.STRING);
        stringItem.setGraphic(stringIcon);
        contextMenu.getItems().add(stringItem);
        stringItem.setOnAction(actionEvent -> openData(null, RedisType.STRING, false));

        MenuItem listItem = new MenuItem("LIST");
        FontAwesomeIconView listIcon = GuiUtils.getRedisDataIconView(RedisType.LIST);
        listItem.setGraphic(listIcon);
        listItem.setOnAction(actionEvent -> openData(null, RedisType.LIST, false));
        contextMenu.getItems().add(listItem);

        MenuItem setItem = new MenuItem("SET");
        FontAwesomeIconView setIcon = GuiUtils.getRedisDataIconView(RedisType.SET);
        setItem.setGraphic(setIcon);
        setItem.setOnAction(actionEvent -> openData(null, RedisType.SET, false));
        contextMenu.getItems().add(setItem);

        MenuItem zsetItem = new MenuItem("ZSET");
        FontAwesomeIconView zsetIcon = GuiUtils.getRedisDataIconView(RedisType.ZSET);
        zsetItem.setGraphic(zsetIcon);
        zsetItem.setOnAction(actionEvent -> openData(null, RedisType.ZSET, false));
        contextMenu.getItems().add(zsetItem);

        MenuItem hashItem = new MenuItem("HASH");
        FontAwesomeIconView hashIcon = GuiUtils.getRedisDataIconView(RedisType.HASH);
        hashItem.setGraphic(hashIcon);
        hashItem.setOnAction(actionEvent -> openData(null, RedisType.HASH, false));
        contextMenu.getItems().add(hashItem);


        return contextMenu;
    }

    private Parent loadValueEditor(RedisConnection connection, int dbIndex, RedisType redisType, boolean edit, StringProperty key) {
        String lowerCaseType = redisType.toString().toLowerCase();
        FXMLLoader loader = new FXMLLoader(GuiUtils.getResourceURL("fxml/value-editor/redis-" + lowerCaseType + "-view.fxml"), Language.getBundle());
        Parent page;
        try {
            page = loader.load();
        } catch (IOException e) {
            page = new Label(Language.getString("redis_unsupported_type"));
            e.printStackTrace();
        }

        RedisValueViewController controller = loader.getController();
        if (edit) {
            controller.initEdit(connection, dbIndex, key, this);
        } else {
            controller.initNew(connection, dbIndex, key, null, this);
        }

        currentController = controller;

        return page;
    }


}
