package me.elyar.redisland.util;

import javafx.scene.control.Cell;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListView;
import me.elyar.redisland.gui.cell.DraggableListCell;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

public class ListViewUtil {
    public static Cell getSelectedCell(ListView list, int index){
        Object[]cells = list.lookupAll(".cell").toArray();
        return (Cell)cells[index];
    }

    @SuppressWarnings("All")
    public static <T> DraggableListCell<T>[] getCells(ListView listView) {
        Object[] cells = listView.lookupAll(".cell").toArray();
        DraggableListCell<T>[] draggableListCells = Stream.of(cells).filter(c -> !((DraggableListCell) c).isEmpty()).toArray(DraggableListCell[]::new);
        Arrays.sort(draggableListCells, Comparator.comparingInt(IndexedCell::getIndex));
        return draggableListCells;
    }
}
