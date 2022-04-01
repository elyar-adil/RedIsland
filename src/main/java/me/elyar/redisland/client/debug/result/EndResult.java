package me.elyar.redisland.client.debug.result;

import me.elyar.redisland.redis.resp.type.RespType;

public class EndResult extends DebugResult {
    private final RespType returnedResult;

    public EndResult(RespType returnedResult) {
        this.returnedResult = returnedResult;
    }

    public RespType getReturnedResult() {
        return returnedResult;
    }

    @Override
    public String toString() {
        return "EndResult{" +
                "returnedResult=" + returnedResult +
                '}';
    }
}