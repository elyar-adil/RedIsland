package me.elyar.redisland.gui;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.*;
import me.elyar.redisland.gui.buttontype.MyButtonType;
import me.elyar.redisland.gui.controller.PrimaryController;
import me.elyar.redisland.Constants;
import me.elyar.redisland.gui.cell.DraggableListCell;
import me.elyar.redisland.gui.util.GuiUtils;
import me.elyar.redisland.util.Language;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleConsumer;
import java.util.regex.Pattern;

public class App extends Application {

    public static boolean showSplashScreen = true;
    public static Stage stage;
    public static App app;
    private Stage showSplash() {
        var stage = new Stage(StageStyle.UNDECORATED);

        FXMLLoader fxmlLoader = new FXMLLoader(GuiUtils.getResourceURL("fxml/splash-screen.fxml"), Language.getBundle());
        VBox splashLayout = null;
        try {
            splashLayout = fxmlLoader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }

        assert splashLayout != null;
        Scene splashScene = new Scene(splashLayout);

        stage.setScene(splashScene);
        stage.getIcons().add(icon);
        stage.centerOnScreen();
        Screen screen = Screen.getPrimary();
        Rectangle2D screenBounds = screen.getBounds();

        double sw = screenBounds.getWidth() ;
        double sh = screenBounds.getHeight();

        listenToSizeInitialization(stage.widthProperty(),
                w -> stage.setX(( sw - w) /2));
        listenToSizeInitialization(stage.heightProperty(),
                h -> stage.setY(( sh - h) /2));

        stage.show();
        return stage;
    }
    private void listenToSizeInitialization(ObservableDoubleValue size,
                                            DoubleConsumer handler) {

        ChangeListener<Number> listener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newSize) {
                if (!Double.isNaN(newSize.doubleValue())) {
                    handler.accept(newSize.doubleValue());
                    size.removeListener(this);
                }
            }

        };
        size.addListener(listener);
    }
    final static boolean disable_update_check = true;
    @Override
    public void start(Stage stage) {
        app = this;
        icon =  new Image(GuiUtils.getResourcePath("/images/icon.png"));
        if (showSplashScreen) {
            Stage splashStage = showSplash();
            AtomicReference<String> onlineVersionString = new AtomicReference<>();
            new Thread(() -> {
                try {
                    if(!disable_update_check) {
                        onlineVersionString.set(getVersionString());
                    }
                    System.out.println(onlineVersionString.get());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Platform.runLater(() -> {
                    try {
                        if (!disable_update_check && onlineVersionString.get() != null && compareWithCurrentVersion(onlineVersionString.get()) < 0) {
                            Alert alert = new ProperAlert(Alert.AlertType.INFORMATION);
                            alert.getButtonTypes().setAll(MyButtonType.OK);
                            alert.setHeaderText(Language.getString("redis_version_expired"));
                            FlowPane fp = new FlowPane();
                            Label lbl = new Label(Language.getString("redis_update_address"));
                            Hyperlink link = new Hyperlink(Constants.updateUrl);
                            link.setOnAction((evt) -> getHostServices().showDocument(link.getText()));
                            fp.getChildren().addAll(lbl, link);
                            alert.getDialogPane().contentProperty().set(fp);
                            alert.showAndWait().ifPresent(action -> System.exit(-1));
                        }
                        startPrimary(stage);
                        splashStage.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }).start();
        } else {
            try {
                startPrimary(stage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public HostServices hostServices() {
        return getHostServices();
    }
    public static Image icon;

    public void startPrimary(Stage stage) throws IOException {
        Scene scene = new Scene(loadFXML(), 800, 600);
        stage.initStyle(StageStyle.DECORATED);
        stage.setScene(scene);
        stage.setMinWidth(650);
        stage.show();
        stage.setTitle("RedIsland v" + Constants.version);
        stage.getIcons().add(icon);
        App.stage = stage;
        App.stage.focusedProperty().addListener(listener -> {
            boolean focused = ((ReadOnlyBooleanProperty) listener).get();
            if (!focused) {
                DraggableListCell.onFocusLost();
            }
        });

    }

    private String getVersionString() throws IOException {
        URLConnection connection = new URL(Constants.versionUrl).openConnection();
        Scanner scanner = new Scanner(connection.getInputStream());
        scanner.useDelimiter("\\Z");
        String content = scanner.next();
        scanner.close();
        return content;
    }

    private static int compareWithCurrentVersion(String version) {
        String versionString1 = normalisedVersion(Constants.version);
        String versionString2 = normalisedVersion(version);
        return versionString1.compareTo(versionString2);
    }

    public static String normalisedVersion(String version) {
        return normalisedVersion(version, ".", 4);
    }

    public static String normalisedVersion(String version, String sep, int maxWidth) {
        String[] split = Pattern.compile(sep, Pattern.LITERAL).split(version);
        StringBuilder sb = new StringBuilder();
        for (String s : split) {
            sb.append(String.format("%" + maxWidth + 's', s));
        }
        return sb.toString();
    }

    private static Parent loadFXML() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(GuiUtils.getResourceURL("fxml/primary.fxml"), Language.getBundle());
        Parent node = fxmlLoader.load();
        PrimaryController controller = fxmlLoader.getController();
        controller.init();
        return node;
    }

    public static void main(String[] args) {
        App.launch(args);
    }

}