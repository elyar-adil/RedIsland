package me.elyar.redisland.gui.controller.valueeditor;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Map;

public class TableValueFactory {
    public final static Callback<TableColumn<Map<String, String>, String>, TableCell<Map<String, String>, String>> factory = TextFieldTableCell.forTableColumn(new StringConverter<>() {
        @Override
        public String toString(String string) {
            return string.replace("\n", "⏎");
        }

        @Override
        public String fromString(String string) {
            return string;
        }
    });

}
