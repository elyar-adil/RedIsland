package me.elyar.redisland.redis;

import me.elyar.redisland.StringUtils;
import me.elyar.redisland.client.AuthError;
import me.elyar.redisland.client.RedisClient;
import me.elyar.redisland.client.RespException;
import me.elyar.redisland.util.Language;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class RedisConnector {
    private final RedisConnection configuration;

    public RedisConnector(RedisConnection configuration) {
        this.configuration = configuration;
    }

    public RedisClient connect(AtomicBoolean test, AtomicReference<String> message) {
        String host = configuration.getHost();
        int port = configuration.getPort();
        String auth = configuration.getAuth();
        RedisClient client = null;
        test.set(false);

        try {
            if (StringUtils.isEmpty(auth)) {
                client = new RedisClient(host, port);
            } else {
                client = new RedisClient(host, port, auth);
            }
            client.ping();
            test.set(true);
        } catch (AuthError | RespException e) {
            e.printStackTrace();
            message.set(e.getLocalizedMessage());
        } catch (UnknownHostException e) {
            e.printStackTrace();
            message.set(Language.getString("connector_error_host") + host);
        } catch (ConnectException e) {
            message.set(Language.getString("connector_error_port") + port);
        } catch (IOException e) {
            e.printStackTrace();
            message.set(Language.getString("connector_error_network"));
        }
        return client;
    }
}
