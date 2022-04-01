package me.elyar.redisland.gui.controller.tabs;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import me.elyar.redisland.gui.ProperAlert;
import me.elyar.redisland.gui.buttontype.MyButtonType;
import me.elyar.redisland.redis.RedisConnection;
import me.elyar.redisland.StringUtils;
import me.elyar.redisland.client.AuthError;
import me.elyar.redisland.client.Pair;
import me.elyar.redisland.client.debug.Debugger;
import me.elyar.redisland.client.RedisClient;
import me.elyar.redisland.client.debug.result.DebugResult;
import me.elyar.redisland.client.debug.result.EndResult;
import me.elyar.redisland.client.debug.result.ErrorResult;
import me.elyar.redisland.client.debug.result.PauseResult;
import me.elyar.redisland.gui.App;
import me.elyar.redisland.gui.LuaCodeField;
import me.elyar.redisland.gui.cell.ConnectionListCell;
import me.elyar.redisland.redis.resp.type.*;
import me.elyar.redisland.util.FileUtil;
import me.elyar.redisland.util.Language;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.StringJoiner;


public class ScriptTabController {
    public Button debugStepButton;
    public Button debugContinueButton;
    public Button debugStopButton;
    public Node debugBar;
    public StyleClassedTextArea logTextArea;
    public TableView<Pair<String, String>> variableTable;
    public TableColumn<Pair<String, String>, String> variableValueColumn;
    public TableColumn<Pair<String, String>, String> variableNameColumn;
    public SplitPane splitPane;
    @FXML
    private LuaCodeField codeArea;
    public ComboBox<RedisConnection> connectionComboBox;
    private final StringProperty scriptName = new SimpleStringProperty(Language.getString("redis_script_lua_default_file_name"));
    private File scriptFile = null;


    private Debugger debugger = null;
    private final Callback<ListView<RedisConnection>, ListCell<RedisConnection>> cellFactory = new Callback<>() {
        @Override
        public ListCell<RedisConnection> call(ListView<RedisConnection> l) {
            return new ListCell<>() {
                @Override
                protected void updateItem(RedisConnection item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setGraphic(null);
                        setText(null);
                        setOnMouseClicked(null);
                    } else {
                        ImageView imageView = new ImageView();
                        imageView.setFitWidth(16);
                        imageView.setFitHeight(16);
                        boolean connected = item.getState() == RedisConnection.ConnectionState.CONNECTED;

                        Image image = null;
                        switch (item.getState()) {
                            case CONNECTED:
                                image = ConnectionListCell.redisLogoImage;
                                break;
                            case DISCONNECTED:
                                image = ConnectionListCell.redisGreyLogoImage;
                                break;
                            case CONNECTING:
                                image = ConnectionListCell.loadingImage;
                                break;
                        }
                        imageView.setImage(image);
                        setGraphic(imageView);
                        if (connected) {
                            setText(item.getName());
                        } else {
                            setText(String.format(Language.getString("redis_script_lua_connection_list_cell_unconnected_name"), item.getName()));
                        }
                        setOnMousePressed(event -> {
                            if (!connected) {
                                Task<Void> task = ConnectionListCell.connect(item, true);
                                task.setOnSucceeded(v ->
                                        Platform.runLater(() -> {
                                            if (item.getState() != RedisConnection.ConnectionState.CONNECTED) {
                                                resetComboBox(connectionComboBox);
                                            } else {
                                                initDebugger(item.getClient());
                                            }
                                        }));
                            }
                        });
                    }
                }

            };
        }
    };

    private void initDebugger(RedisClient client) {
        debugger = new Debugger(client);
        attachDebuggerButton(debugger);
    }

    private final SimpleBooleanProperty changed = new SimpleBooleanProperty(false);

    private String initialCode;
    private double[] dividerPosition = {0.7};

    public void init(Tab tab, ObservableList<RedisConnection> connection, SimpleObjectProperty<RedisConnection> currentConnection, String content, String fileName) {
        debugBar.managedProperty().bind(debugBar.visibleProperty());

        hideVariableTable();

        variableNameColumn.setCellValueFactory(
                new PropertyValueFactory<>("first"));
        variableValueColumn.setCellValueFactory(
                new PropertyValueFactory<>("second"));


        codeArea.replaceText(content);
        codeArea.getUndoManager().forgetHistory();
        scriptName.set(fileName);
        scriptName.addListener(listener ->
                tab.setText(scriptName.get() + (changed.get() ? "*" : "")));
        codeArea.addEventHandler(KeyEvent.KEY_TYPED, keyEvent -> {
            boolean codeChanged = !codeArea.getText().equals(initialCode);
            changed.set(codeChanged);
        });

        changed.addListener(listener ->
                tab.setText(scriptName.get() + (changed.get() ? "*" : "")));
        tab.setText(scriptName.get());
        connectionComboBox.setItems(connection);
        connectionComboBox.setCellFactory(cellFactory);
        connectionComboBox.setButtonCell(cellFactory.call(null));
        RedisConnection current = currentConnection.get();
        if (current != null) {
            connectionComboBox.getSelectionModel().select(current);
            initDebugger(current.getClient());
        }
        initialCode = content;
    }

    public void initNew(Tab tab, ObservableList<RedisConnection> connection, SimpleObjectProperty<RedisConnection> currentConnection) {
        init(tab, connection, currentConnection, "", Language.getString("redis_script_lua_default_file_name"));
    }

    public void run() {
        RedisConnection selected = connectionComboBox.getSelectionModel().getSelectedItem();

        if (selected == null) {
            connectionNotSelectedAlert();
        } else {
            RedisClient client = selected.getClient();
            try {
                RespType result = client.eval(codeArea.getText(), null, null);
                displayResult(result);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void debugMode(boolean isOn) {
        connectionComboBox.setDisable(isOn);
        if (!isOn) {
            codeArea.setHighlightLine(null);
        }
    }

    public void debug() {
        if (debugger == null) {
            connectionNotSelectedAlert();
        } else {
            Task<DebugResult> task = new Task<>() {
                @Override
                protected DebugResult call() {
                    try {
                        synchronized (debugger) {
                            return debugger.debug(codeArea.getText(), null, null, codeArea.getBreakPoints());
                        }
                    } catch (IOException | AuthError e) {
                        return new ErrorResult(e.getMessage());
                    }
                }
            };
            task.setOnSucceeded(result -> Platform.runLater(() -> dealWithDebugResult(task.getValue())));
            new Thread(task).start();

        }
    }

    public void connectionNotSelectedAlert() {
        Alert alert = new ProperAlert(Alert.AlertType.ERROR, Language.getString("redis_script_lua_no_connection_selected"));
        alert.getButtonTypes().setAll(MyButtonType.OK);
        alert.showAndWait();
    }

    private void attachDebuggerButton(Debugger debugger) {
        variableTable.setItems(debugger.getVariableList());

        debugger.debugStateProperty().addListener(listener -> {
            Debugger.State state = debugger.debugStateProperty().get();
            Platform.runLater(() -> {
                if (state == Debugger.State.PAUSED) {
                    showVariableTable();
                } else {
                    hideVariableTable();
                }
                debugMode(state != Debugger.State.STOPPED);
            });
        });
        debugBar.visibleProperty().bind(debugger.debugStateProperty().isNotEqualTo(Debugger.State.STOPPED));

        debugStepButton.disableProperty().bind(debugger.debugStateProperty().isEqualTo(Debugger.State.PAUSED).not());
        debugContinueButton.disableProperty().bind(debugger.debugStateProperty().isEqualTo(Debugger.State.PAUSED).not());
        debugStopButton.disableProperty().bind(debugger.debugStateProperty().isEqualTo(Debugger.State.STOPPED));
    }

    private boolean isShowingVariableTable() {
        return splitPane.getItems().contains(variableTable);
    }

    private void hideVariableTable() {
        dividerPosition = splitPane.getDividerPositions();
        splitPane.getItems().remove(variableTable);
    }

    private void showVariableTable() {
        if (!isShowingVariableTable()) {
            splitPane.getItems().add(variableTable);
            splitPane.setDividerPositions(dividerPosition);
        }
    }

    public void step() {
        Task<DebugResult> task = new Task<>() {
            @Override
            protected DebugResult call() {
                try {
                    synchronized (debugger) {
                        return debugger.step();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        task.setOnSucceeded(ignored ->
                Platform.runLater(() ->
                        dealWithDebugResult(task.getValue())));
        new Thread(task).start();
    }

    private void dealWithDebugResult(DebugResult debugResult) {
        if (debugResult instanceof ErrorResult) {
            if (debugger.debugStateProperty().get() == Debugger.State.STOPPED) {
                return;
            } else {
                System.out.println("debugResult = " + ((ErrorResult) debugResult).getMessage());
                Alert alert = new ProperAlert(Alert.AlertType.ERROR);
                alert.getButtonTypes().setAll(MyButtonType.OK);
                alert.setHeaderText(((ErrorResult) debugResult).getMessage());
                alert.show();
            }
        }
        String additionalMessage = debugResult.getAdditionalMessage();
        if (!StringUtils.isEmpty(additionalMessage)) {
            debugAdditionalMessageLog(additionalMessage);
        }
        if (debugResult instanceof EndResult) {
            EndResult endResult = (EndResult) debugResult;
            RespType result = endResult.getReturnedResult();
            displayResult(result);
            codeArea.setHighlightLine(null);
        } else if (debugResult instanceof PauseResult) {
            PauseResult pauseResult = (PauseResult) debugResult;
            int lineNo = pauseResult.getStoppedLineNo();
            String reason = pauseResult.getPauseReason().toString();
            debugLog("* Stopped at %d, reason: %s.", lineNo, reason);
            codeArea.setHighlightLine(lineNo);
        }
    }

    private String resultToString(RespType result) {
        if (result instanceof RespError) {
            return ((RespError) result).getMessage();
        } else if (result instanceof RespInteger) {
            Long value = ((RespInteger) result).getValue();
            String longValueTemplate = "%d";
            return String.format(longValueTemplate, value);
        } else if (result instanceof RespString) {
            String value = ((RespString) result).getValue();
            String stringValueTemplate = "%s";
            return String.format(stringValueTemplate, value);
        } else if (result instanceof RespArray) {
            StringJoiner stringJoiner = new StringJoiner(", ", "{", "}");
            for (RespType respType : (RespArray<RespType>) result) {
                String element = resultToString(respType);
                stringJoiner.add(element);
            }
            return stringJoiner.toString();
        }
        return null;
    }

    private void displayResult(RespType result) {
        String resultString = resultToString(result);
        if (result instanceof RespError) {
            errorLog(resultString);
        } else {
            debugLog(resultString);
        }
    }

    private void errorLog(String message) {
        message += "\n";
        int startIndex = logTextArea.getText().length();
        logTextArea.insertText(startIndex, message);
        logTextArea.setStyle(startIndex, startIndex + message.length() - 1, Collections.singleton("error-log"));
    }

    private void debugLog(String format, Object... args) {
        String message = String.format(format, args) + "\n";
        int startIndex = logTextArea.getText().length();
        logTextArea.insertText(startIndex, message);
        logTextArea.setStyle(startIndex, startIndex + message.length() - 1, Collections.singleton("debug-log"));
    }

    private void debugAdditionalMessageLog(String format, Object... args) {
        String message = String.format(format, args) + "\n";
        int startIndex = logTextArea.getText().length();
        logTextArea.insertText(startIndex, message);
        logTextArea.setStyle(startIndex, startIndex + message.length() - 1, Collections.singleton("debug-additional-log"));
    }

    public void initOpen(Tab tab, ObservableList<RedisConnection> connection, SimpleObjectProperty<RedisConnection> currentConnection, File openedFile) {
        try {
            scriptFile = openedFile;
            String content = FileUtil.read(openedFile);
            init(tab, connection, currentConnection, content, openedFile.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveAs() {
        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(Language.getString("redis_script_lua_file_chooser_description"), "*.lua");
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(filter);
        File newFile = fileChooser.showSaveDialog(App.stage);
        if (newFile != null) {
            scriptFile = newFile;
        }
        saveFile();
    }

    public void saveFile() {
        if (scriptFile == null) {
            FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(Language.getString("redis_script_lua_file_chooser_description"), "*.lua");
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName(scriptName.get());
            fileChooser.getExtensionFilters().add(filter);
            scriptFile = fileChooser.showSaveDialog(App.stage);
        }
        if (scriptFile != null) {
            initialCode = codeArea.getText();
            changed.set(false);
            scriptName.setValue(scriptFile.getName());
            try {
                FileUtil.write(scriptFile, initialCode);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isChanged() {
        return changed.get();
    }

    @FXML
    private void keyReleased(KeyEvent keyEvent) {
        if (keyEvent.isControlDown() && keyEvent.getCode() == KeyCode.S) {
            saveFile();
        }
    }

    public static <T> void resetComboBox(ComboBox<T> combo) {
        combo.getSelectionModel().clearSelection();
    }


    public void resume() {
        Task<DebugResult> task = new Task<>() {
            @Override
            protected DebugResult call() {
                try {
                    synchronized (debugger) {
                        return debugger.resume();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        task.setOnSucceeded(ignored ->
                Platform.runLater(() ->
                        dealWithDebugResult(task.getValue())));
        new Thread(task).start();
    }

    public void stop(ActionEvent actionEvent) {
        debugger.stopDebug();
    }
}
