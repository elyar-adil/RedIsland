package me.elyar.redisland.gui.cell;

import javafx.animation.TranslateTransition;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.util.Duration;
import me.elyar.redisland.util.ListViewUtil;

import java.util.*;
import java.util.stream.Stream;

/**
 * 可以拖动的ListCell
 *
 * @param <T>
 */
public class DraggableListCell<T> extends ListCell<T> {
    // 鼠标点下时 在cell中的y
    private double mouseClickedYOffset;
    // 当前cell是否在移动
    private transient boolean moving = false;
    private transient TranslateTransition translateTransition = null;
    private static DraggableListCell<?> draggingCell;
    protected static boolean dragged = false;

    public static void onFocusLost() {
        if (draggingCell != null) {
            draggingCell.getOnMouseReleased().handle(null);
            draggingCell = null;
        }
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (!empty && item != null) {
            DraggableListCell<T>[] cells = ListViewUtil.getCells(getListView());

            setOnMousePressed(mouseEvent -> mouseClickedYOffset = getTranslateY() - mouseEvent.getSceneY());

            setOnDragDetected(event -> draggingCell = this);

            setOnMouseDragged(mouseEvent -> {
                dragged = true;
                if (draggingCell != this) {
                    return;
                }
                setTranslateY(mouseClickedYOffset + mouseEvent.getSceneY());
                int currentIndex = getCurrentIndex(cells);
                for (DraggableListCell<T> cell : cells) {
                    if (cell == this) {
                        continue;
                    }
                    if (cell.getIndex() > currentIndex) {
                        if (cell.getIndex() > getIndex()) {
                            setCellY(cell, 0);
                        } else {
                            setCellY(cell, getHeight());
                        }
                    } else {
                        if (cell.getIndex() > getIndex()) {
                            setCellY(cell, -getHeight());
                        } else {
                            setCellY(cell, 0);
                        }
                    }
                }
                toFront();
            });

            setOnMouseReleased(mouseEvent -> {
                if (draggingCell != this) {
                    return;
                }
                stopAllTransitions(cells);
                ObservableList<T> items = getListView().getItems();
                int index = getCurrentIndex(cells);
                T thisItem = items.remove(getIndex());
                if (index < getIndex()) {
                    index += 1;
                }
                index = Math.max(index, 0);
                items.add(index, thisItem);
                getListView().getSelectionModel().select(index);
                for (DraggableListCell<T> cell : cells) {
                    cell.setTranslateY(0);
                    cell.toFront();
                }
                draggingCell = null;
                onReordered();
            });
        } else {
            setOnMousePressed(null);
            setOnDragDetected(null);
            setOnMouseDragged(null);
            setOnMouseReleased(null);
        }
    }

    protected void onReordered() {
    }

    private int getCurrentIndex(DraggableListCell<T>[] cells) {
        int index = -1;

        double thisY = getLayoutY() + getTranslateY() + getHeight() / 2;
        for (DraggableListCell<T> cell : cells) {
            if (cell.getLayoutY() + cell.getTranslateY() + cell.getHeight() / 2 > thisY) {
                break;
            }
            index = cell.getIndex();
        }
        return index;
    }

    private void stopAllTransitions(DraggableListCell<T>[] cells) {
        for (DraggableListCell<T> cell : cells) {
            cell.moving = false;
            if (cell.translateTransition != null)
                cell.translateTransition.stop();
        }
    }

    protected static <T> void setCellY(DraggableListCell<T> cell, double height) {
        if (cell.moving) {
            cell.translateTransition.setToY(height);
            return;
        }
        TranslateTransition translate = new TranslateTransition();
        translate.setToY(height);
        translate.setDuration(Duration.millis(100));
        translate.setNode(cell);
        translate.play();
        cell.moving = true;
        cell.translateTransition = translate;
        translate.setOnFinished(actionEvent -> {
            cell.moving = false;
            cell.translateTransition = null;
        });
    }


}
