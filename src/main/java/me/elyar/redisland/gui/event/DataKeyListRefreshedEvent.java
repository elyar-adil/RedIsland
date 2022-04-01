package me.elyar.redisland.gui.event;

public class DataKeyListRefreshedEvent extends Event {
    private int keyCount = 0;

    public DataKeyListRefreshedEvent(int keyCount) {
        this.keyCount = keyCount;
    }

    public int getKeyCount() {
        return keyCount;
    }
}
