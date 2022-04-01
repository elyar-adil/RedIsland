package me.elyar.redisland.gui.component;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import me.elyar.redisland.client.RedisClient;
import me.elyar.redisland.gui.util.GuiUtils;
import me.elyar.redisland.redis.resp.type.RespInteger;
import me.elyar.redisland.util.Language;

import java.io.IOException;

public class TTLEditorPane extends VBox {
    public CheckBox persistCheckBox;
    public TextField ttlTextField;
    private RedisClient client = null;
    private StringProperty key = null;
    private boolean saved = true;
    private final BooleanProperty savedProperty = new SimpleBooleanProperty(true);
    private Long ttl;

    public void init(RedisClient client, StringProperty key) {
        this.client = client;
        this.key = key;
        if (this.client == null || this.key == null || this.key.getValue() == null) {
            return;
        }
        try {
            RespInteger ttl = (RespInteger) this.client.ttl(key.getValue());
            ttlTextField.setText(String.valueOf(ttl.getValue()));
            this.ttl = ttl.getValue();
            ttlTextField.textProperty().addListener(l -> {
                if (ttlTextField.isFocused() && !persistCheckBox.isSelected() && !ttlTextField.getText().equals(this.ttl.toString())) {
                    saved = false;
                    savedProperty.set(false);
                }
            });
            persistCheckBox.setSelected(ttl.getValue() == -1);
            final Timeline timeline = new Timeline(
                    new KeyFrame(
                            Duration.millis(1000),
                            event -> {
                                if (Integer.parseInt(ttlTextField.getText()) == -1 || ttlTextField.isFocused()) {
                                    return;
                                }
                                int time = Integer.parseInt(ttlTextField.getText()) - 1;
                                if (time >= -2) {
                                    ttlTextField.setText(String.valueOf(time));
                                }
                            }
                    )
            );
            timeline.setCycleCount(Animation.INDEFINITE);
            timeline.play();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public TTLEditorPane() {
        FXMLLoader fxmlLoader = new FXMLLoader(GuiUtils.getResourceURL("fxml/component/ttl-editor.fxml"), Language.getBundle());
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }


    public void setTtl() {
        try {
            if (persistCheckBox.isSelected()) {
                client.persist(key.getValue());
                ttlTextField.setText(String.valueOf(-1));
            } else {
                client.expire(key.getValue(), Integer.parseInt(ttlTextField.getText()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.ttl = Long.valueOf(ttlTextField.getText());
        saved = true;
        savedProperty.set(true);
    }

    public void persistChecked() throws IOException {
        if (persistCheckBox.isSelected()) {
            client.persist(key.getValue());
            ttlTextField.setText(String.valueOf(-1));
        }
        this.ttl = Long.valueOf(ttlTextField.getText());
        saved = true;
        savedProperty.set(true);
    }

    public boolean isSaved() {
        return saved;
    }

    public ObservableBooleanValue isSavedBinding() {
        return savedProperty;
    }

    public void restore() {
        if (!persistCheckBox.isSelected()) {
            ttlTextField.setText(String.valueOf(this.ttl));
            saved = true;
            savedProperty.set(true);
        }
    }
}
