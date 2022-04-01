package me.elyar.redisland.client;

import me.elyar.redisland.redis.resp.RespConnection;
import me.elyar.redisland.redis.resp.type.*;

import java.io.IOException;
import java.util.*;

public class RedisClient {
    private RespConnection respConnection;
    private final String host;
    private final int port;
    private String auth;
    private final boolean useAuth;

    public RedisClient(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        this.useAuth = false;
        try {
            this.respConnection = createConnection();
        } catch (AuthError ignored) {
        }
    }

    public RedisClient(String host, int port, String auth) throws IOException, AuthError {
        this.host = host;
        this.port = port;
        this.auth = auth;
        this.useAuth = true;
        this.respConnection = createConnection();
    }

    public synchronized RespConnection createConnection() throws IOException, AuthError {
        if (useAuth) {
            RespConnection respConnection = new RespConnection(host, port);
            respConnection.send("auth", auth);
            RespType result = respConnection.receive();
            if (result instanceof RespError) {
                throw new AuthError(((RespError) result).getMessage());
            }
            return respConnection;
        } else {
            return new RespConnection(host, port);
        }
    }

    public synchronized void ping() throws IOException, RespException {
        respConnection.send("PING");
        RespType result = respConnection.receive();
        if (result instanceof RespError) {
            throw new RespException(((RespError) result).getMessage());
        }
    }

    public synchronized void select(int dbIndex) throws IOException, RespException {
        respConnection.send("select", String.valueOf(dbIndex));
        RespType result = respConnection.receive();
        if (result instanceof RespError) {
            throw new RespException(((RespError) result).getMessage());
        }
    }

    public synchronized void delete(String key) throws IOException {
        respConnection.send("del", key);
        // ignore result
        respConnection.receive();
    }

    public synchronized void rename(String oldKeyName, String newKeyName) throws IOException {
        respConnection.send("rename", oldKeyName, newKeyName);
        // ignore result
        respConnection.receive();
    }

    public synchronized void set(String key, String value) throws IOException {
        respConnection.send("set", key, value);
        // ignore result
        respConnection.receive();
    }

    public synchronized String get(String key) throws IOException {
        respConnection.send("get", key);
        RespType result = respConnection.receive();
        return ((RespString) result).getValue();
    }

    public synchronized int databaseCount() throws IOException {
        int databaseCount = 0;
        while (true) {
            respConnection.send("select", String.valueOf(databaseCount));
            RespType result = respConnection.receive();
            if (result instanceof RespError) {
                break;
            }
            databaseCount++;
        }
        try {
            select(0);
        } catch (RespException e) {
            e.printStackTrace();
        }
        return databaseCount;
    }

    public synchronized Map<String, String> info() throws IOException {
        respConnection.send("info");
        String infoResult = ((RespString) respConnection.receive()).getValue();
        String[] lines = infoResult.split("\\r?\\n");
        Map<String, String> result = new HashMap<>();
        for (String line : lines) {
            if (!line.startsWith("#")) {
                String[] keyValue = line.split(":", 2);
                if (keyValue.length >= 2) {
                    result.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return result;
    }

    /**
     * 支持用 * 批量删除
     *
     * @param pattern 要删除的pattern
     * @return 被删除数据个数
     * @throws IOException 网络异常
     */
    public synchronized void batchDelete(String pattern) throws IOException {
        int batchSize = 5000;
        respConnection.send("eval", "local a=redis.call('keys',ARGV[1])for b=1,#a,ARGV[2] do redis.call('del',unpack(a,b,math.min(b+ARGV[2]-1,#a)))end;return#a", "0", pattern, String.valueOf(batchSize));
        respConnection.receive();
    }

    private RespType _eval(RespConnection connection, String script, List<String> keys, List<String> args, boolean debug) throws IOException {
        synchronized (connection) {
            connection.send("SCRIPT", "DEBUG", debug ? "YES" : "NO");
            connection.receive();
        }
        int keySize = keys == null ? 0 : keys.size();
        int argSize = args == null ? 0 : args.size();
        String[] sendArguments = new String[3 + keySize + argSize];
        sendArguments[0] = "eval";
        sendArguments[1] = script;
        sendArguments[2] = String.valueOf(keySize);
        addListToArray(keys, sendArguments, 3);
        addListToArray(args, sendArguments, 3 + keySize);
        synchronized (connection) {
            connection.send(sendArguments);
            return connection.receive();
        }
    }

    public synchronized RespType eval(String script, List<String> keys, List<String> args) throws IOException {
        return _eval(respConnection, script, keys, args, false);
    }

    public RespType debug(RespConnection debugConnection, String script, List<String> keys, List<String> args, Set<Integer> line) throws IOException {
        RespType result = _eval(debugConnection, script, keys, args, true);
        if (line != null && line.size() > 0) {
            debugAddBreakPoints(debugConnection, line);
        }
        return result;
    }

    private void debugAddBreakPoints(RespConnection debugConnection, Set<Integer> line) throws IOException {
        String[] args = new String[line.size() + 1];
        args[0] = "break";
        int i = 1;
        for (Integer b : line) {
            args[i++] = String.valueOf(b + 1);
        }
        synchronized (debugConnection) {
            debugConnection.send(args);
            RespType result = debugConnection.receive();
        }
    }


    public void debugAbortAndCloseConnection(RespConnection debugConnection) {
        debugConnection.send("abort");
        try {
            debugConnection.close();
        } catch (IOException ignored) {
        }
    }

    public RespType debugContinue(RespConnection debugConnection) throws IOException {
        RespType result;
        synchronized (debugConnection) {
            debugConnection.send("continue");
            result = debugConnection.receive();
        }
        return result;
    }

    public RespType debugStep(RespConnection debugConnection) throws IOException {
        synchronized (debugConnection) {
            debugConnection.send("step");
            return debugConnection.receive();
        }
    }

    public RespType debugPrint(RespConnection debugConnection) throws IOException {
        synchronized (debugConnection) {
            debugConnection.send("print");
            return debugConnection.receive();
        }
    }

    public RespType monitor(RespConnection monitorConnection) throws IOException {
        synchronized (monitorConnection) {
            monitorConnection.send("monitor");
            return monitorConnection.receive();
        }
    }

    private void addListToArray(List<String> fromList, String[] toArray, int arrayStartIndex) {
        if (fromList == null) {
            return;
        }
        for (int i = 0; i < fromList.size(); i++) {
            toArray[i + arrayStartIndex] = fromList.get(i);
        }
    }

    public synchronized void close() throws IOException {
        this.respConnection.close();
    }

    public synchronized RespArray lrange(String key) throws IOException {
        this.respConnection.send("lrange", key, "0", "-1");
        return (RespArray) this.respConnection.receive();
    }

    public synchronized RespType lpush(String key, String value) throws IOException {
        this.respConnection.send("lpush", key, value);
        return this.respConnection.receive();
    }

    public synchronized RespType rpush(String key, String value) throws IOException {
        this.respConnection.send("rpush", key, value);
        return this.respConnection.receive();
    }

    public synchronized RespType ldelete(String key, int index) throws IOException {
        this.respConnection.send("multi");
        this.respConnection.receive();
        this.respConnection.send("lset", key, String.valueOf(index), "__RDM_DELETED__");
        this.respConnection.receive();
        this.respConnection.send("lrem", key, "0", "__RDM_DELETED__");
        this.respConnection.receive();
        this.respConnection.send("exec");
        return this.respConnection.receive();
    }

    public RespType lset(String key, int index, String newValue) throws IOException {
        this.respConnection.send("lset", key, String.valueOf(index), newValue);
        return this.respConnection.receive();
    }

    public RespArray<RespString> hgetall(String key) throws IOException {
        this.respConnection.send("hgetall", key);
        return (RespArray<RespString>) this.respConnection.receive();
    }

    public synchronized RespType hset(String key, String field, String value) throws IOException {
        this.respConnection.send("hset", key, field, value);
        return this.respConnection.receive();
    }


    public synchronized RespType hdel(String key, String field) throws IOException {
        this.respConnection.send("hdel", key, field);
        return this.respConnection.receive();
    }

    public synchronized RespType sadd(String key, String value) throws IOException {
        this.respConnection.send("sadd", key, value);
        return this.respConnection.receive();
    }

    public synchronized RespArray smembers(String key) throws IOException {
        this.respConnection.send("smembers", key);
        return (RespArray) this.respConnection.receive();
    }

    public synchronized RespType srem(String key, String value) throws IOException {
        this.respConnection.send("srem", key, value);
        return this.respConnection.receive();
    }

    public synchronized RespType zadd(String key, String score, String value) throws IOException {
        this.respConnection.send("zadd", key, score, value);
        return this.respConnection.receive();
    }

    public synchronized RespArray zrange(String key) throws IOException {
        this.respConnection.send("zrange", key, "0", "-1", "WITHSCORES");
        return (RespArray) this.respConnection.receive();
    }
    public synchronized RespType zrem(String key, String value) throws IOException {
        this.respConnection.send("zrem", key, value);
        return this.respConnection.receive();
    }

    public synchronized RespType ttl(String key) throws IOException {
        this.respConnection.send("ttl", key);
        return this.respConnection.receive();
    }

    public synchronized RespType expire(String key, int seconds) throws IOException {
        this.respConnection.send("expire", key, String.valueOf(seconds));
        return this.respConnection.receive();
    }

    public synchronized RespType persist(String key) throws IOException {
        this.respConnection.send("persist", key);
        return this.respConnection.receive();
    }

}
