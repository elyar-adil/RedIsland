package me.elyar.redisland.gui.controller.tabs;

import me.elyar.redisland.client.RedisType;

public class DataTreeItem {
    String text;
    RedisType type;

    public DataTreeItem(String text, RedisType type) {
        this.text = text;
        this.type = type;
    }
}
