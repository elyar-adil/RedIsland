package me.elyar.redisland.gui;

import javafx.beans.InvalidationListener;
import javafx.collections.ObservableSet;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.function.IntFunction;

class BreakPointFactory implements IntFunction<Node> {
    private ObservableSet<Integer> breakPoints;


    @Override
    public Node apply(int lineNumber) {
        Circle circle = new Circle(4);
        setColor(circle, lineNumber);

        breakPoints.addListener((InvalidationListener) l -> {
            setColor(circle, lineNumber);
        });
        return circle;
    }

    public void setColor(Circle circle, int lineNumber) {
        if (breakPoints.contains(lineNumber)) {
            circle.setFill(Color.RED);
        } else {
            circle.setFill(Color.TRANSPARENT);
        }
    }

    public Node apply(int lineNumber, ObservableSet<Integer> breakPoints) {
        this.breakPoints = breakPoints;
        return apply(lineNumber);
    }
}