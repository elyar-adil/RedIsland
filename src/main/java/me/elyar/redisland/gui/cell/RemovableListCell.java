package me.elyar.redisland.gui.cell;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import me.elyar.redisland.gui.event.Event;
import me.elyar.redisland.gui.event.EventHandler;
import me.elyar.redisland.util.ListViewUtil;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * 带删除动画的ListCell
 *
 * @param <T>
 */
public class RemovableListCell<T> extends DraggableListCell<T> {
    class RemovedEvent extends Event {
    }

    EventHandler<RemovedEvent> removedEventEventHandler = null;

    public void setOnRemoved(EventHandler<RemovedEvent> removedEventEventHandler) {
        this.removedEventEventHandler = removedEventEventHandler;
    }

    /**
     * 删除当前ListCell
     */
    protected void remove() {
        T item = getItem();
        FadeTransition fadeTransition = new FadeTransition();
        fadeTransition.setToValue(0);
        fadeTransition.setNode(this);
        fadeTransition.setDuration(Duration.millis(100));
        fadeTransition.setOnFinished(_ignored -> {
            DraggableListCell<T>[] cells = ListViewUtil.getCells(getListView());
            // 要移动的cell数量
            AtomicInteger translateCellCount = new AtomicInteger(0);
            for (DraggableListCell<T> cell : cells) {
                // index大于当前cell的都要移动
                if (cell.getIndex() > getIndex()) {
                    translateCellCount.incrementAndGet();
                }
            }
            if (translateCellCount.get() == 0) {
                deleteCurrentItem();
            } else {
                for (DraggableListCell<T> cell : cells) {
                    if (cell.getIndex() > getIndex()) {
                        TranslateTransition translate = new TranslateTransition();
                        translate.setToY(-getHeight());
                        translate.setDuration(Duration.millis(100));
                        translate.setNode(cell);
                        translate.setOnFinished(__ignored -> {
                                    if (translateCellCount.decrementAndGet() == 0) {
                                        // 移动完最后一个cell
                                        Platform.runLater(() ->
                                        {
                                            for (DraggableListCell<T> cc : cells) {
                                                cc.setTranslateY(0);
                                            }
                                            deleteCurrentItem();
                                        });
                                    }
                                }
                        );
                        translate.play();
                    }
                }
            }
        });
        fadeTransition.playFromStart();
    }

    // 删除当元素
    private void deleteCurrentItem() {
        T item = getItem();
        getListView().getItems().remove(item);
        setOpacity(1);
        getListView().refresh();
        if (removedEventEventHandler != null) {
            removedEventEventHandler.handle(new RemovedEvent());
        }
    }
}
