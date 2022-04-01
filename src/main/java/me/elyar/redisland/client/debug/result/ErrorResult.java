package me.elyar.redisland.client.debug.result;

public class ErrorResult extends DebugResult {
    private final String message;

    public ErrorResult(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
