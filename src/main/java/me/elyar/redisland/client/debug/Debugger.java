package me.elyar.redisland.client.debug;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import me.elyar.redisland.client.AuthError;
import me.elyar.redisland.client.Pair;
import me.elyar.redisland.client.RedisClient;
import me.elyar.redisland.client.debug.result.DebugResult;
import me.elyar.redisland.client.debug.result.EndResult;
import me.elyar.redisland.client.debug.result.PauseResult;
import me.elyar.redisland.redis.resp.RespConnection;
import me.elyar.redisland.redis.resp.type.RespArray;
import me.elyar.redisland.redis.resp.type.RespString;
import me.elyar.redisland.redis.resp.type.RespType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Debugger {
    /*
     debug过程中可能返回的值
     程序结束完成 返回结果
     程序中断 中断理由 中断行数
    */
    private final RedisClient redisClient;
    // 调试状态
    private final ObjectProperty<State> debugState = new SimpleObjectProperty<>(State.STOPPED);

    private static final ObservableList<Pair<String, String>> variableList = FXCollections.observableList(new ArrayList<>());

    public ObjectProperty<State> debugStateProperty() {
        return debugState;
    }

    public ObservableList<Pair<String, String>> getVariableList() {
        return variableList;
    }

    public enum State {
        STOPPED, PAUSED, STARTED
    }

    // 调试程序可能会让连接卡死 所以需要单独的连接
    RespConnection debugConnection = null;

    public Debugger(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    public DebugResult debug(String script, List<String> keys, List<String> args, Set<Integer> breakPoints) throws IOException, AuthError {
        System.out.println("Debugger.debug");
        debugState.set(State.STARTED);
        if (debugConnection != null) {
            stopDebug();
        }
        debugConnection = redisClient.createConnection();

        System.out.println("Debugger.debug connection created");
        RespArray<RespType> responseArray = (RespArray<RespType>) redisClient.debug(debugConnection, script, keys, args, breakPoints);
        System.out.println("Debugger.debug response received");
        DebugResult debugResult = parseResponse(responseArray);
        int stoppedLineNo = ((PauseResult) debugResult).getStoppedLineNo();
        // ldb即使没有断点也会在第一行暂停程序 如果第一行没有在断点的Set中 则继续程序执行
        if (!breakPoints.contains(stoppedLineNo - 1)) {
            debugResult = resume();
        }
        return debugResult;
    }

    public DebugResult step() throws IOException {
        System.out.println("Debugger.step");
        if (!debugState.get().equals(State.PAUSED)) {
            throw new IllegalStateException();
        }
        debugState.set(State.STARTED);
        RespArray responseArray = (RespArray) redisClient.debugStep(debugConnection);
        return parseResponse(responseArray);
    }

    public DebugResult resume() throws IOException {
        System.out.println("Debugger.resume");
        debugState.set(State.STARTED);
        RespArray responseArray = (RespArray) redisClient.debugContinue(debugConnection);
        System.out.println("Debugger.resume response received");
        return parseResponse(responseArray);
    }

    private static final Map<String, PauseReason> pauseReasonMap = Map.of(
            "redis.breakpoint() called", PauseReason.CALLED,
            "break point", PauseReason.BREAK_POINT,
            "step over", PauseReason.STEP_OVER,
            "timeout reached, infinite loop?", PauseReason.TIME_OUT);


    /**
     * 停止调试
     */
    public void stopDebug() {
        System.out.println("Debugger.stopDebug");
        debugStateProperty().set(State.STOPPED);

        if (debugConnection != null) {
            redisClient.debugAbortAndCloseConnection(debugConnection);
        }
        debugConnection = null;
        debugState.set(State.STOPPED);
        onStopped();
    }

    private String LINE_SEPARATOR = System.getProperty("line.separator");

    /**
     * 解析ldb结果
     *
     * @param resultArray 结果数组
     * @return 解析的结果
     * @throws IOException 出现网络错误
     */
    private DebugResult parseResponse(RespArray<RespType> resultArray) throws IOException {
        System.out.println("Debugger.parseResponse");
        StringBuilder additionalMessage = new StringBuilder();
        for (RespType result : resultArray) {
            if (result instanceof RespString) {
                String state = ((RespString) result).getValue();
                if (state.startsWith("* Stopped at")) {
                    Pair<Integer, String> lineNoReasonPair = parsePauseState(state);
                    int stoppedLineNo = lineNoReasonPair.getFirst();
                    String reason = lineNoReasonPair.getSecond();

                    PauseReason pauseReason = pauseReasonMap.get(reason);
                    debugStateProperty().set(State.PAUSED);
                    onPaused();

                    DebugResult debugResult = new PauseResult(stoppedLineNo, pauseReason);
                    debugResult.setAdditionalMessage(additionalMessage.toString());
                    return debugResult;
                } else if ("<endsession>".equals(state)) {
                    // 程序结束 返回结果
                    RespType returnedResult = debugConnection.receive();
                    stopDebug();
                    DebugResult debugResult = new EndResult(returnedResult);
                    debugResult.setAdditionalMessage(additionalMessage.toString());
                    return debugResult;
                } else if (state.startsWith("<redis>")) {
                    additionalMessage.append(state).append(LINE_SEPARATOR);
                } else if (state.startsWith("<reply>")) {
                    additionalMessage.append(state);
                } else {
                    throw new IllegalStateException(state);
                }
            }
        }
        throw new RuntimeException();
    }

    private void onStopped() {
        System.out.println("Debugger.onStopped");

        variableList.clear();
    }

    private void onPaused() throws IOException {
        System.out.println("Debugger.onPaused");
        updateVariableList();
    }

    /**
     * 解析中断的行数和原因
     *
     * @param state 服务器返回的状态
     * @return 中断的行数和原因组成的pair
     */
    private Pair<Integer, String> parsePauseState(String state) {
        System.out.println("Debugger.parsePauseState");
        Pattern pattern = Pattern.compile("\\* Stopped at (\\d+), stop reason = (.*)");
        Matcher matcher = pattern.matcher(state);
        boolean find = matcher.find();
        if (!find) {
            throw new RuntimeException();
        }
        int stoppedLineNo = Integer.parseInt(matcher.group(1));
        String reason = matcher.group(2);
        return new Pair<>(stoppedLineNo, reason);
    }

    private final Pattern VALUE_PATTERN = Pattern.compile("<value> (.*) = (.*)");

    private void updateVariableList() throws IOException {
        System.out.println("Debugger.updateVariableList");
        RespArray<RespString> result = (RespArray<RespString>) redisClient.debugPrint(debugConnection);
            List<Pair<String, String>> _variableList = new ArrayList<>();
        for (RespString variableInfo : result) {
            String info = variableInfo.getValue();
            variableList.clear();
            if (info.startsWith("<value>")) {
                Matcher matcher = VALUE_PATTERN.matcher(info);
                if (matcher.find()) {
                    String variableName = matcher.group(1);
                    String variableValue = matcher.group(2);
                    _variableList.add(new Pair<>(variableName, variableValue));
                }
            }
        }
        Platform.runLater(() -> {
            variableList.clear();
            variableList.addAll(_variableList);
        });
    }
}
