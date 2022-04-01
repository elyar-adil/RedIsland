package me.elyar.redisland.gui.component;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import me.elyar.redisland.client.RedisClient;
import me.elyar.redisland.gui.controller.tabs.DataViewTabController;
import me.elyar.redisland.gui.util.GuiUtils;
import me.elyar.redisland.util.Language;

import java.io.IOException;

public class NameEditorPane extends HBox {
    @FXML
    private TextField keyTextField;
    @FXML
    private Button renameButton;
    private BooleanProperty keyChanged = new SimpleBooleanProperty(false);
    private StringProperty currentKey = new SimpleStringProperty(null);
    private RedisClient client;
    private DataViewTabController dataViewTabController;

    public NameEditorPane() {
        FXMLLoader fxmlLoader = new FXMLLoader(GuiUtils.getResourceURL("fxml/component/name-editor.fxml"), Language.getBundle());
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public void init(RedisClient client, StringProperty key, DataViewTabController dataViewTabController) {
        keyTextField.textProperty().bindBidirectional(key);
        this.currentKey.set(key.getValue());
        this.client = client;
        keyTextField.textProperty().addListener(ignored -> {
            keyChanged.set(true);
        });
        renameButton.disableProperty().bind(isSavedBinding());
        renameButton.visibleProperty().bind(currentKey.isNull().not());
        this.dataViewTabController = dataViewTabController;
    }

    public void rename() throws IOException {
        String newKey = keyTextField.getText();
        if (!isSaved()) {
            client.rename(currentKey.getValue(), newKey);
        }
        currentKey.setValue(newKey);
        keyChanged.set(false);
        dataViewTabController.refreshThenSelect(newKey);
    }


    public void restore() {
        keyTextField.setText(currentKey.getValue());
    }

    public String getCurrentKey() {
        if (currentKey.getValue() == null) {
            return keyTextField.getText();
        } else {
            return currentKey.getValue();
        }
    }

    public boolean isSaved() {
        return currentKey.getValue() == null || currentKey.getValue().equals(keyTextField.getText());
    }

    public ObservableBooleanValue isSavedBinding() {
        return currentKey.isNull().or(currentKey.isEqualTo(keyTextField.textProperty()));
    }

}
