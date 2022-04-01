package me.elyar.redisland.gui;

import javafx.beans.InvalidationListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;

public class AdaptiveSplitPane extends SplitPane {
    private double adaptiveRatio = 1;
    private Node s;


    public AdaptiveSplitPane() {
        layoutBoundsProperty().addListener(observable -> {
            updateSplitPaneOrientation();
        });
        getItems().addListener((InvalidationListener) observable -> {
            updateSplitPaneOrientation();
        });
    }

    public void updateSplitPaneOrientation() {
        boolean isHigh = this.getHeight() > this.getWidth() * adaptiveRatio;
        this.setOrientation(isHigh ? Orientation.VERTICAL : Orientation.HORIZONTAL);
    }

    public void hide() {
        if (this.getItems().size() == 2) {
            s = this.getItems().get(1);
            this.getItems().remove(s);
        }
    }

    public void show() {
        if (this.getItems().size() == 1) {
            this.getItems().add(s);
        }
    }

    public double getAdaptiveRatio() {
        return adaptiveRatio;
    }

    public void setAdaptiveRatio(double adaptiveRatio) {
        this.adaptiveRatio = adaptiveRatio;
    }

}
