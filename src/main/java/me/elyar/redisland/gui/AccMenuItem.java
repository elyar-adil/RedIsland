package me.elyar.redisland.gui;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class AccMenuItem extends MenuItem {
    public ObjectProperty<KeyCombination> acceleratorDecoration;
    public Label desc = new Label();
    public Label acc = new Label();
    public HBox hBox = new HBox();

    public AccMenuItem() {
        super();
        getStyleClass().add("acc-menu-item");
        acc.getStyleClass().add("accelerator-text");
        HBox innerHBox = new HBox(desc);
        hBox.getChildren().add(innerHBox);
        HBox.setHgrow(innerHBox, Priority.ALWAYS);
        hBox.getChildren().add(acc);
        hBox.setSpacing(10);
        setGraphic(hBox);
        hBox.setAlignment(Pos.CENTER);
        parentPopupProperty().addListener(listener -> {
            var contextMenu = getParentPopup();
            var maxContentLength = 0;
            AccMenuItem menuItemHasMaxContentLength = null;
            for (var x : contextMenu.getItems()) {
                if (x instanceof AccMenuItem) {
                    var y = (AccMenuItem) x;
                    int contentLength = y.acc.getText().length() + y.desc.getText().length();
                    if (maxContentLength < contentLength) {
                        maxContentLength = contentLength;
                        menuItemHasMaxContentLength = y;
                    }
                }
            }
            for (var x : contextMenu.getItems()) {
                if (x instanceof AccMenuItem && x != menuItemHasMaxContentLength) {
                    var y = (AccMenuItem) x;
                    assert menuItemHasMaxContentLength != null;
                    AccMenuItem finalA = menuItemHasMaxContentLength;
                    AccMenuItem finalA1 = menuItemHasMaxContentLength;
                    InvalidationListener lis = new InvalidationListener() {
                        @Override
                        public void invalidated(Observable observable) {
                            y.hBox.setPrefWidth(finalA.hBox.getWidth());
                            finalA1.hBox.widthProperty().removeListener(this);
                        }
                    };
                    menuItemHasMaxContentLength.hBox.widthProperty().addListener(lis);
                }
            }

        });
    }

    public final ObjectProperty<KeyCombination> acceleratorDecorationProperty() {
        if (this.acceleratorDecoration == null) {
            this.acceleratorDecoration = new SimpleObjectProperty(this, "accelerator");
        }

        return this.acceleratorDecoration;
    }

    public final void setAcceleratorDecoration(KeyCombination var1) {
        this.acceleratorDecorationProperty().set(var1);
        acc.setText(var1.getDisplayText());
    }

    public final KeyCombination getAcceleratorDecoration() {
        return this.acceleratorDecoration == null ? null : (KeyCombination) this.acceleratorDecoration.get();
    }

    private ObjectProperty<Node> icon;

    public final void setIcon(Node value) {
        iconProperty().set(value);
        if (hBox.getChildren().size() > 2) {
            hBox.getChildren().remove(0);
        }
        hBox.getChildren().add(0, value);
    }

    public final Node getIcon() {
        return icon == null ? null : icon.get();
    }

    public final ObjectProperty<Node> iconProperty() {
        if (icon == null) {
            icon = new SimpleObjectProperty<Node>(this, "icon");
        }
        return icon;
    }

    private StringProperty description;

    public final void setDescription(String value) {
        DescriptionProperty().set(value);
        desc.setText(value);
    }

    public final String getDescription() {
        return description == null ? null : description.get();
    }

    public final StringProperty DescriptionProperty() {
        if (description == null) {
            description = new SimpleStringProperty(this, "Description");
        }
        return description;
    }
}
