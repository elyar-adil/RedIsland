package me.elyar.redisland.client.debug.result;

public abstract class DebugResult {
    private String additionalMessage;

    public String getAdditionalMessage() {
        return additionalMessage;
    }

    public void setAdditionalMessage(String additionalMessage) {
        this.additionalMessage = additionalMessage;
    }
}
