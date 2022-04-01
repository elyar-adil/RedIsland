package me.elyar.redisland.gui.controller;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import me.elyar.redisland.Ignore;
import me.elyar.redisland.client.Pair;
import me.elyar.redisland.gui.AccMenuItem;
import me.elyar.redisland.gui.App;
import me.elyar.redisland.gui.ProperAlert;
import me.elyar.redisland.gui.buttontype.MyButtonType;
import me.elyar.redisland.gui.cell.ConnectionListCell;
import me.elyar.redisland.gui.controller.tabs.DataViewTabController;
import me.elyar.redisland.gui.controller.tabs.PerformanceTabController;
import me.elyar.redisland.gui.controller.tabs.ScriptTabController;
import me.elyar.redisland.gui.util.GuiUtils;
import me.elyar.redisland.gui.util.LogUtil;
import me.elyar.redisland.redis.RedisConnection;
import me.elyar.redisland.redis.RedisInfoUtil;
import me.elyar.redisland.util.ConnectionProfile;
import me.elyar.redisland.util.Language;
import me.elyar.redisland.util.ListViewUtil;
import org.controlsfx.control.StatusBar;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.awt.*;
import java.awt.Button;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.*;
import java.util.concurrent.CountDownLatch;


public class PrimaryController {
    final Robot robot;

    private static final SimpleObjectProperty<RedisConnection> connectionProperty = new SimpleObjectProperty<>();
    public ListView<String> databaseListView;
    public Map<Pair<String, Integer>, Pair<Tab, DataViewTabController>> dataViewTabMap = new HashMap<>();

    public Map<RedisConnection, List<Tab>> connectionTabMap = new HashMap<>();
    public TabPane tabPane;
    public final static Image redisLogoImage = new Image(Objects.requireNonNull(ConnectionListCell.class.getResourceAsStream("/images/redis.png")));

    private static PrimaryController primaryController;
    public StatusBar statusBar;
    public FontAwesomeIconView statusBarIconView;

    public MenuItem saveMenuItem;
    public MenuItem saveAsMenuItem;
    public MenuItem closeScriptMenuItem;

    private final BooleanProperty isScriptTab = new SimpleBooleanProperty(false);
    public RadioMenuItem chineseRadioMenuItem;
    public RadioMenuItem englishRadioMenuItem;

    public PrimaryController() throws AWTException {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
    }

    public void _logStatusBar(String log, String iconName) {
        statusBar.setText(log);
        statusBarIconView.setVisible(true);
        statusBarIconView.setIcon(FontAwesomeIcon.valueOf(iconName));
    }

    public static void logStatusBar(String log, String iconName) {
        primaryController._logStatusBar(log, iconName);
    }

    private void _closeTabsWithConnection(RedisConnection redisConnection) {
        List<Tab> tabList = connectionTabMap.get(redisConnection);
        if (tabList != null) {
            tabPane.getTabs().removeAll(tabList);
        }
    }

    public void _setSelectedConnection(RedisConnection redisConnection) {
        connectionListView.getSelectionModel().select(redisConnection);
    }

    public static void setSelectedConnection(RedisConnection redisConnection) {
        primaryController._setSelectedConnection(redisConnection);
    }

    public static void closeTabsWithConnection(RedisConnection redisConnection) {
        primaryController._closeTabsWithConnection(redisConnection);
    }

    public static void setRedisConnection(RedisConnection redisConnection) {
        RedisConnection prev = connectionProperty.get();
        if (prev != null) {
            connectionProperty.get().setOpened(false);
        }
        if (redisConnection != null) {
            redisConnection.setOpened(true);
        }
        connectionProperty.set(redisConnection);
    }

    public static RedisConnection getRedisConnection() {
        return connectionProperty.get();
    }

    private static final Representer representer = new Representer() {
        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue, Tag customTag) {
            Ignore ignore = null;
            try {
                ignore = javaBean.getClass().getDeclaredField(property.getName()).getAnnotation(Ignore.class);
            } catch (NoSuchFieldException ignored) {
            }
            if (ignore != null) {
                return null;
            } else {
                return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
            }
        }
    };
    public static final Yaml yaml = new Yaml(representer);
    private CountDownLatch updatingDataBaseListLatch;
    @FXML
    private ListView<RedisConnection> connectionListView;

    private static final Callback<RedisConnection, Observable[]> extractor =
            p -> new Observable[]{p.getNameProperty(), p.getStateProperty(), p.getOpenedProperty()};

    private static final ObservableList<RedisConnection> connectionList =
            FXCollections.observableArrayList(extractor);

    private static final ObservableList<String> databaseList = FXCollections.observableList(new ArrayList<>());

    public void feedback() throws IOException, URISyntaxException {
        Desktop desktop = Desktop.getDesktop();
        URI mailto = new URI("mailto:" + Language.getString("main_menu_help_feedback_mail"));
        desktop.mail(mailto);
    }


    public static class Settings {
        private List<RedisConnection> connectionList;
        private String language;

        public Settings() {
        }

        public Settings(List<RedisConnection> connectionList, String language) {
            this.connectionList = connectionList;
            this.language = language;
        }

        @SuppressWarnings("unused")
        public List<RedisConnection> getConnectionList() {
            return connectionList;
        }

        @SuppressWarnings("unused")
        public void setConnectionList(List<RedisConnection> connectionList) {
            this.connectionList = connectionList;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }

    public static void updateProfile() {
        String dump = yaml.dump(new Settings(connectionList, Language.getLanguage()));
        try {
            ConnectionProfile.write(dump);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void bindMenuItem() {
        saveMenuItem.disableProperty().bind(isScriptTab.not());
        saveAsMenuItem.disableProperty().bind(isScriptTab.not());
        closeScriptMenuItem.disableProperty().bind(isScriptTab.not());
    }

    public void init() {
        bindMenuItem();
        primaryController = this;
        tabPane.getTabs().addListener((InvalidationListener) listener -> {
            ObservableList<Tab> list = tabPane.getTabs();
            dataViewTabMap.entrySet().removeIf(entry -> !list.contains(entry.getValue().getFirst()));
            performanceTabMap.entrySet().removeIf(entry -> !list.contains(entry.getValue()));

            connectionTabMap.forEach((key, value) -> value.removeIf(v -> !list.contains(v)));
            scriptTabControllerMap.keySet().removeIf(tab -> !tabPane.getTabs().contains(tab));
        });

        tabPane.getSelectionModel().selectedItemProperty().addListener(listener -> {
            Tab tab = tabPane.getSelectionModel().selectedItemProperty().get();

            isScriptTab.set(scriptTabControllerMap.containsKey(tab));
            for (Map.Entry<RedisConnection, List<Tab>> entry : connectionTabMap.entrySet()) {
                RedisConnection key = entry.getKey();
                List<Tab> value = entry.getValue();
                if (value.contains(tab)) {
                    setRedisConnection(key);
                    break;
                }
            }
            dataViewTabMap.forEach((key, value) -> {
                if (tab == value.getFirst()) {

                    if (updatingDataBaseListLatch != null && updatingDataBaseListLatch.getCount() != 0)
                        new Thread(() -> {
                            try {
                                updatingDataBaseListLatch.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            Platform.runLater(() -> databaseListView.getSelectionModel().select(key.getSecond()));
                        }
                        ).start();
                    else {
                        databaseListView.getSelectionModel().select(key.getSecond());
                    }
                }
            });


        });

        connectionListView.setItems(connectionList);
        connectionListView.setCellFactory(o -> new
                ConnectionListCell());
        loadConnectionFromProfile();
        connectionProperty.set(null);
        connectionProperty.addListener(listener -> refreshDatabaseList());
        databaseListView.setItems(databaseList);

        databaseListView.getSelectionModel().

                getSelectedIndices().

                addListener((InvalidationListener) listener ->

                {
                    // 如果数据库列表在更新 则列表被选择的元素被更改时不进行操作
                    if (updatingDataBaseListLatch != null && updatingDataBaseListLatch.getCount() != 0) {
                        return;
                    }


                    ObservableList<Integer> indices = databaseListView.getSelectionModel().getSelectedIndices();
                    if (indices.size() <= 0) {
                        return;
                    }
                    int selectedIndex = indices.get(0);
                    LogUtil.log(String.format(Language.getString("log_select_db"), selectedIndex));

                    Pair<String, Integer> pair = new Pair<>(connectionProperty.get().getName(), selectedIndex);
                    Pair<Tab, DataViewTabController> tabControllerPair = dataViewTabMap.get(pair);
                    Tab tabInMap = tabControllerPair == null ? null : tabControllerPair.getFirst();
                    if (!dataViewTabMap.containsKey(pair)) {
                        String text = connectionProperty.get().getName() + " DB:" + selectedIndex;

                        ImageView graphic = new ImageView();
                        graphic.setFitWidth(16);
                        graphic.setFitHeight(16);
                        graphic.setImage(redisLogoImage);

                        String path = "fxml/tabs/data-view-tab.fxml";
                        Pair<Tab, Object> _tabControllerPair = addNewTab(text, graphic, path);
                        Tab tab = _tabControllerPair.getFirst();
                        DataViewTabController controller = (DataViewTabController) _tabControllerPair.getSecond();

                        tabControllerPair = new Pair<>(tab, controller);
                        dataViewTabMap.put(pair, tabControllerPair);

                        List<Tab> list = connectionTabMap.getOrDefault(connectionProperty.get(), new ArrayList<>());
                        list.add(tab);
                        connectionTabMap.put(connectionProperty.get(), list);

                        tabInMap = tab;
                        controller.setConnection(connectionProperty.get(), selectedIndex);
                        controller.init();
                        controller.setOnRefreshedEventHandler(event -> {
                            int keyCount = event.getKeyCount();
                            String newContent = makeDatabaseListContent(selectedIndex, keyCount);
                            if (!databaseList.get(selectedIndex).equals(newContent)) {  // 防止死循环
                                databaseList.set(selectedIndex, newContent);
                            }
                        });

                    } else if (!tabPane.getTabs().contains(tabInMap)) {
                        tabPane.getTabs().add(tabInMap);
                    }
                    tabPane.getSelectionModel().select(tabInMap);
                    if (tabControllerPair != null) {
                        tabControllerPair.getSecond().refresh();
                    }
                });
    }

    private void refreshDatabaseList() {
        RedisConnection redisConnection = connectionProperty.get();
        databaseList.clear();
        if (redisConnection == null) {
            return;
        }
        updatingDataBaseListLatch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                Map<String, String> info = redisConnection.getClient().info();

                Map<Integer, Integer> databaseKeyNumberMap = RedisInfoUtil.getDatabaseKeyNumberMap(info);

                int databaseCount = redisConnection.getClient().databaseCount();
                Platform.runLater(() -> {
                    for (int i = 0; i < databaseCount; i++) {
                        Integer x = databaseKeyNumberMap.get(i);
                        databaseList.add(makeDatabaseListContent(i, x));
                    }
                    connectionListView.getSelectionModel().select(redisConnection);
                    updatingDataBaseListLatch.countDown();
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }


    private String makeDatabaseListContent(int dbIndex, Integer keyCount) {
        String databaseName = String.format(Language.getString("redis_database_list_db_name"), dbIndex);
        String databaseDescription;
        if (keyCount == null || keyCount == 0) {
            databaseDescription = Language.getString("redis_database_list_empty_description");
        } else {
            databaseDescription = String.format(Language.getString("redis_database_list_not_empty_description"), keyCount);
        }
        return databaseName + " " + databaseDescription;
    }


    private void loadConnectionFromProfile() {
        String profile = null;
        try {
            profile = ConnectionProfile.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Settings settings = null;
        try {
            settings = yaml.load(profile);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        connectionList.clear();
        if (settings != null) {
            Language.setLanguage(settings.language);
            if (settings.connectionList != null) {
                connectionList.addAll(settings.connectionList);
            }
        } else {
            Language.setLanguage(Locale.getDefault());
        }

        updateLanguageRadioMenuItemSelection();
    }

    private void updateLanguageRadioMenuItemSelection() {
        if ("zh".equals(Language.getLanguage())) {
            chineseRadioMenuItem.setSelected(true);
        } else {
            englishRadioMenuItem.setSelected(true);
        }
    }


    @FXML
    private void newConnection() {
        RedisConnection redisConnection = new RedisConnection();
        boolean isSaved = showEditRedisConnectionDialog(false, redisConnection, null);
        if (isSaved) {
            connectionList.add(redisConnection);
            updateProfile();
        }
    }

    public static boolean showEditRedisConnectionDialog(boolean edit, RedisConnection redisConnection, RedisConnection originalItem) {
        FXMLLoader loader = new FXMLLoader(GuiUtils.getResourceURL("fxml/redis-connection-edit.fxml"), Language.getBundle());
        AnchorPane page = null;
        try {
            page = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Stage dialogStage = new Stage();
        dialogStage.setTitle(edit ? Language.getString("redis_connection_edit") : Language.getString("redis_connection_new"));
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(App.stage);
        dialogStage.setResizable(false);
        dialogStage.getIcons().add(App.icon);
        assert page != null;
        Scene scene = new Scene(page);
        dialogStage.setScene(scene);
        RedisConnectionEditController controller = loader.getController();
        controller.initialize(dialogStage, connectionList, redisConnection, originalItem);
        scene.getStylesheets().add(GuiUtils.getResourcePath("css/tab-pane.css"));
        dialogStage.showAndWait();
        return controller.isSaved();
    }

    public void dbListKeyReleased(javafx.scene.input.KeyEvent event) {
        if (event.getCode() == KeyCode.F5) {
            int selected = databaseListView.getSelectionModel().getSelectedIndex();
            refreshDatabaseList();
            new Thread(() -> {
                try {
                    updatingDataBaseListLatch.await();
                    Platform.runLater(() -> {
                        if (selected < databaseListView.getItems().size()) {
                            databaseListView.getSelectionModel().select(selected);
                        }
                        // TODO 如果刷新后发现 当前数据库不存在 处理已经打开的tab
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    public Map<Tab, ScriptTabController> scriptTabControllerMap = new HashMap<>();

    private Pair<Tab, Object> addNewTab(String text, Node graphic, String tabContentPath) {
        FXMLLoader fxmlLoader = new FXMLLoader(GuiUtils.getResourceURL(tabContentPath), Language.getBundle());
        Tab tab = new Tab();
        tab.setText(text);
        tab.setGraphic(graphic);
        Node tabContent = null;
        try {
            tabContent = fxmlLoader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
        tab.setContent(tabContent);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        return new Pair<>(tab, fxmlLoader.getController());
    }

    private final Map<RedisConnection, Tab> performanceTabMap = new HashMap<>();

    private void newInfoTab(RedisConnection connection) {
        if (performanceTabMap.containsKey(connection)) {
            Tab tab = performanceTabMap.get(connection);
            tabPane.getSelectionModel().select(tab);
            return;
        }
        FontAwesomeIconView graphic = new FontAwesomeIconView(FontAwesomeIcon.AREA_CHART);
        Pair<Tab, Object> pair = addNewTab(connection.getName(), graphic, "fxml/tabs/performance-tab.fxml");
        PerformanceTabController controller
                = (PerformanceTabController) pair.getSecond();
        pair.getFirst().setOnClosed(event -> controller.close());
        controller.init(connection.getClient());
        performanceTabMap.put(connection, pair.getFirst());
    }


    public static void infoTab(RedisConnection connection) {
        primaryController.newInfoTab(connection);
    }

    private void newScriptTab(boolean open, File openedFile) {
        FontAwesomeIconView graphic = new FontAwesomeIconView(FontAwesomeIcon.CODE);
        Pair<Tab, Object> pair = addNewTab(null, graphic, "fxml/tabs/script-tab.fxml");
        ScriptTabController controller = (ScriptTabController) pair.getSecond();
        Tab tab = pair.getFirst();

        scriptTabControllerMap.put(tab, controller);

        if (open) {
            controller.initOpen(tab, connectionList, connectionProperty, openedFile);
        } else {
            controller.initNew(tab, connectionList, connectionProperty);
        }
        tab.setOnCloseRequest(event -> {
            tab.getTabPane().setTabDragPolicy(TabPane.TabDragPolicy.FIXED);
            event.consume();
            boolean closed = closeScriptTab(tab);
            if (!closed) {
                Platform.runLater(() -> tab.getTabPane().setTabDragPolicy(TabPane.TabDragPolicy.REORDER));
            }
        });
        isScriptTab.set(true);
    }

    public void newScript() {
        newScriptTab(false, null);
    }

    public void openScript() {
        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("Lua脚本", "*.lua");
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(filter);
        File openedFile = fileChooser.showOpenDialog(App.stage);
        if (openedFile != null) {
            newScriptTab(true, openedFile);
        }
    }

    public void saveScript() {
        Tab tab = getCurrentTab();
        if (scriptTabControllerMap.containsKey(tab)) {
            ScriptTabController controller = scriptTabControllerMap.get(tab);
            controller.saveFile();
        }
    }

    public void saveAsScript() {
        Tab tab = getCurrentTab();
        if (scriptTabControllerMap.containsKey(tab)) {
            ScriptTabController controller = scriptTabControllerMap.get(tab);
            controller.saveAs();
        }
    }

    public Tab getCurrentTab() {
        return tabPane.getSelectionModel().getSelectedItem();
    }

    public void closeTab(Tab tab) {
        tabPane.getTabs().remove(tab);
    }

    public boolean closeScriptTab(Tab tab) {
        if (scriptTabControllerMap.containsKey(tab)) {
            ScriptTabController controller = scriptTabControllerMap.get(tab);
            if (controller.isChanged()) {
                Alert alert = closeScriptTabAlert();
                if (alert.getResult() == MyButtonType.YES) {
                    closeTab(tab);
                    return true;
                }
            } else {
                closeTab(tab);
                return true;
            }
        }
        return false;
    }

    public void closeScript() {
        Tab tab = getCurrentTab();
        closeScriptTab(tab);
    }

    public Alert closeScriptTabAlert() {
        Alert alert = new ProperAlert(Alert.AlertType.CONFIRMATION);
        alert.getButtonTypes().setAll(MyButtonType.YES, MyButtonType.NO);
        alert.setHeaderText(Language.getString("redis_script_not_saved"));
        alert.initOwner(App.stage);
        alert.showAndWait();
        return alert;
    }

    public void onListViewKeyReleased(javafx.scene.input.KeyEvent keyEvent) {
        if (keyEvent.getCode() == KeyCode.DELETE) {
            int index = connectionListView.getSelectionModel().getSelectedIndex();
            ConnectionListCell cell = (ConnectionListCell) ListViewUtil.getSelectedCell(connectionListView, index);
            cell.delete();
        }
    }

    public void about() {
        Alert alert = new ProperAlert(Alert.AlertType.INFORMATION);
        alert.getButtonTypes().setAll(MyButtonType.OK);
        alert.setTitle(Language.getString("main_menu_about_title"));
        alert.setHeaderText(Language.getString("main_menu_about_header"));
        alert.setContentText(Language.getString("main_menu_about_content"));
        alert.show();
    }


    public void menuAction(ActionEvent actionEvent) {
        var menuItem = (AccMenuItem) actionEvent.getTarget();
        var keyCodeCombination = (KeyCodeCombination) menuItem.getAcceleratorDecoration();


        assert keyCodeCombination != null;
        int code = keyCodeCombination.getCode().getCode();
        boolean isShortcutDown = keyCodeCombination.getShortcut() == KeyCombination.ModifierValue.DOWN;
        if (isShortcutDown) {
            robot.keyPress(KeyEvent.VK_CONTROL);
        }
        robot.keyPress(code);
        robot.keyRelease(code);
        if (isShortcutDown) {
            robot.keyRelease(KeyEvent.VK_CONTROL);
        }
    }


    public void languageChange() {
        if ((chineseRadioMenuItem.isSelected() && Language.getLanguage().equals("zh") ||
                englishRadioMenuItem.isSelected() && Language.getLanguage().equals("en"))) {
            return;
        }

        Alert alert = new ProperAlert(Alert.AlertType.CONFIRMATION);
        alert.getButtonTypes().setAll(MyButtonType.YES, MyButtonType.NO);

        alert.setHeaderText(Language.getString("main_menu_pref_language_restart"));
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(App.icon);
        alert.showAndWait().ifPresent(buttonType -> {
            if (buttonType == MyButtonType.YES) {
                Locale language;
                if (chineseRadioMenuItem.isSelected()) {
                    language = Locale.CHINA;
                } else {
                    language = Locale.ENGLISH;
                }
                Language.setLanguage(language);
                updateProfile();
                App.stage.close();
                App.showSplashScreen = false;
                new App().start(new Stage());
            } else {
                (Language.getLanguage().equals("zh") ? chineseRadioMenuItem : englishRadioMenuItem).setSelected(true);
            }
        });
    }
}