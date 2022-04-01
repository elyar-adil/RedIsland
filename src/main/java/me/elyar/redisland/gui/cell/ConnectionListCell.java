package me.elyar.redisland.gui.cell;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import me.elyar.redisland.gui.ProperAlert;
import me.elyar.redisland.gui.buttontype.MyButtonType;
import me.elyar.redisland.redis.RedisConnection;
import me.elyar.redisland.redis.RedisConnector;
import me.elyar.redisland.client.RedisClient;
import me.elyar.redisland.gui.controller.PrimaryController;
import me.elyar.redisland.gui.util.GuiUtils;
import me.elyar.redisland.gui.util.LogUtil;
import me.elyar.redisland.util.Language;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


public class ConnectionListCell extends RemovableListCell<RedisConnection> {
    public final static Image redisLogoImage = new Image(GuiUtils.getResourcePath("/images/redis.png"));
    public final static Image redisGreyLogoImage = new Image(GuiUtils.getResourcePath("/images/redis-grey.png"));
    public final static Image loadingImage = new Image(GuiUtils.getResourcePath("/images/loading.gif"));

    private final static String OPENED_CLASS = "cell-selected";

    @Override
    protected void updateItem(RedisConnection item, boolean empty) {
        super.updateItem(item, empty);
        getStylesheets().add(GuiUtils.getResourcePath("css/style.css"));

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
            getStyleClass().removeAll(Collections.singleton(OPENED_CLASS));
            setOnMouseClicked(null);
        } else {
            setText(item.getName());

            boolean connecting = item.getState() == RedisConnection.ConnectionState.CONNECTING;
            boolean connected = item.getState() == RedisConnection.ConnectionState.CONNECTED;
            boolean disconnected = item.getState() == RedisConnection.ConnectionState.DISCONNECTED;

            ImageView imageView = new ImageView();
            imageView.setFitWidth(16);
            imageView.setFitHeight(16);
            if (connected) {
                imageView.setImage(redisLogoImage);
            } else if (connecting) {
                imageView.setImage(loadingImage);
            } else {
                imageView.setImage(redisGreyLogoImage);
            }
            setGraphic(imageView);

            if (item.opened()) {
                getStyleClass().add(OPENED_CLASS);
            } else {
                getStyleClass().removeAll(Collections.singleton(OPENED_CLASS));
            }



            Tooltip va = new Tooltip(item.getHost() + ":" + item.getPort());
            va.setShowDelay(new Duration(300));
            setTooltip(va);

            setOnMouseClicked(mouseEvent -> {
                if (dragged) {
                    dragged = false;
                    return;
                }
                if (mouseEvent.getClickCount() == 2) {
                    if (disconnected) {
                        LogUtil.log(String.format(Language.getString("redis_log_connect_opening"), item.getName()));
                        connect(item, true);
                    }
                } else {
                    if (mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED) {
                        if (connected) {
                            LogUtil.log(String.format(Language.getString("redis_log_connect_open"), item.getName()));
                            PrimaryController.setRedisConnection(item);
                        }
                    }
                }

            });


            ContextMenu contextMenu = createContextMenu(item, connecting, connected);

            setContextMenu(contextMenu);


        }

    }

    private ContextMenu createContextMenu(RedisConnection item, boolean connecting, boolean connected) {
        FontAwesomeIconView closeView =
                new FontAwesomeIconView(FontAwesomeIcon.CLOSE);

        FontAwesomeIconView editView =
                new FontAwesomeIconView(FontAwesomeIcon.EDIT);

        FontAwesomeIconView connectView =
                new FontAwesomeIconView();

        FontAwesomeIconView performanceView =
                new FontAwesomeIconView(FontAwesomeIcon.AREA_CHART);

        ContextMenu contextMenu = new ContextMenu();

        MenuItem editMenuItem = new MenuItem(Language.getString("main_connection_menu_edit"));
        editMenuItem.setGraphic(editView);
        MenuItem deleteMenuItem = new MenuItem(Language.getString("main_connection_menu_delete"));
        deleteMenuItem.setGraphic(closeView);
        deleteMenuItem.setOnAction((actionEvent) -> {
            delete();
        });
        editMenuItem.setOnAction(actionEvent -> {
            RedisConnection clone = item.clone();
            boolean isSaved = PrimaryController.showEditRedisConnectionDialog(true, clone, item);
            if (isSaved) {
                item.setName(clone.getName());
                item.setHost(clone.getHost());
                item.setPort(clone.getPort());
                item.setAuth(clone.getAuth());
                PrimaryController.updateProfile();
            }
        });
        editMenuItem.setDisable(item.getState() != RedisConnection.ConnectionState.DISCONNECTED);
        if (editMenuItem.isDisable()) {
            editMenuItem.setText(Language.getString("main_connection_menu_can_not_edit"));
        }
        MenuItem connectMenuItem = new MenuItem(connected ? Language.getString("main_connection_menu_disconnect") : connecting ? Language.getString("main_connection_menu_connecting") : Language.getString("main_connection_menu_connect"));
        connectMenuItem.setGraphic(connectView);
        connectMenuItem.setDisable(connecting);
        connectView.setIcon(connected ? FontAwesomeIcon.UNLINK : connecting ? FontAwesomeIcon.SPINNER : FontAwesomeIcon.LINK);

        connectMenuItem.setOnAction(actionEvent -> {
            if (connected) {
                disconnect(item);
                if (PrimaryController.getRedisConnection() == item) {
                    PrimaryController.setRedisConnection(null);
                }
            } else {
                connect(item, false);
            }
        });

        MenuItem performanceMenuItem = new MenuItem(Language.getString("main_connection_menu_performance"));
        performanceMenuItem.setGraphic(performanceView);
        performanceMenuItem.setDisable(!connected);
        performanceMenuItem.setOnAction(actionEvent -> {
            PrimaryController.infoTab(item);
        });
        contextMenu.getItems().addAll(connectMenuItem, new SeparatorMenuItem(), performanceMenuItem, new SeparatorMenuItem(), editMenuItem, deleteMenuItem);
        return contextMenu;
    }

    public void delete() {
        RedisConnection item = getItem();
        Alert alert = new ProperAlert(Alert.AlertType.CONFIRMATION);
        alert.getButtonTypes().setAll(MyButtonType.YES, MyButtonType.NO);
        alert.setTitle(Language.getString("redis_delete_confirmation"));
        alert.setHeaderText(String.format(Language.getString("redis_delete_name"), item.getName()));
        alert.getDialogPane().getStylesheets().add(GuiUtils.getResourcePath("css/button.css"));
        alert.showAndWait().ifPresent(type -> {
            if (type == MyButtonType.YES) {
                setOnRemoved(event -> {
                    PrimaryController.updateProfile();
                    Platform.runLater(() -> {
                        PrimaryController.closeTabsWithConnection(item);
                        // 如果删除了当前打开的链接 选择第一个链接
                        if (item.equals(PrimaryController.getRedisConnection())) {
                            ObservableList<RedisConnection> items = getListView().getItems();
                            for (RedisConnection i : items) {
                                if (i.getState() == RedisConnection.ConnectionState.CONNECTED) {
                                    PrimaryController.setRedisConnection(i);
                                    PrimaryController.setSelectedConnection(i);
                                    return;
                                }
                            }
                            PrimaryController.setRedisConnection(null);
                            PrimaryController.setSelectedConnection(null);
                        }
                    });
                });
                remove();
            }
        });
    }

    private void disconnect(RedisConnection item) {
        item.setState(RedisConnection.ConnectionState.DISCONNECTED);
    }

    public static Task<Void> connect(RedisConnection item, boolean openConnection) {
        item.setState(RedisConnection.ConnectionState.CONNECTING);

        RedisConnector connector = new RedisConnector(item);
        AtomicBoolean test = new AtomicBoolean();
        AtomicReference<String> message = new AtomicReference<>();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                RedisClient client = connector.connect(test, message);
                Platform.runLater(() -> {
                    if (test.get()) {
                        LogUtil.log(String.format(Language.getString("redis_log_connect_success"), item.getName()));
                        item.setClient(client);
                        item.setState(RedisConnection.ConnectionState.CONNECTED);
                        if (openConnection) {
                            PrimaryController.setRedisConnection(item);
                        }
                    } else {
                        LogUtil.logw(String.format(Language.getString("redis_log_connect_failed"), item.getName()));

                        item.setState(RedisConnection.ConnectionState.DISCONNECTED);
                        Alert alert = new ProperAlert(Alert.AlertType.ERROR);
                        alert.getButtonTypes().setAll(MyButtonType.OK);
                        alert.setTitle(Language.getString("redis_failed_to_connected_to_server"));
                        alert.setHeaderText(message.get());
                        alert.getDialogPane().getStylesheets().add(GuiUtils.getResourcePath("css/button.css"));
                        alert.show();
                    }
                });
                return null;
            }
        };
        new Thread(task).start();
        return task;
    }

    @Override
    protected void onReordered() {
        super.onReordered();
        new Thread(PrimaryController::updateProfile).start();
    }
}
