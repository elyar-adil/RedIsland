package me.elyar.redisland.redis;

import javafx.beans.property.*;
import me.elyar.redisland.Ignore;
import me.elyar.redisland.client.RedisClient;

import java.util.Objects;

public class RedisConnection implements Cloneable{
    private final StringProperty name = new SimpleStringProperty();
    private String host = "localhost";
    private int port = 6379;
    private String auth;

    @Ignore
    private final ObjectProperty<ConnectionState> state = new SimpleObjectProperty<>(ConnectionState.DISCONNECTED);
    @Ignore
    private RedisClient client;
    @Ignore
    private final BooleanProperty openedProperty = new SimpleBooleanProperty(false);
    public BooleanProperty getOpenedProperty() {
        return openedProperty;
    }
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    @Override
    public String toString() {
        return "RedisConnection{" +
                "name=" + name +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", auth='" + auth + '\'' +
                ", state=" + state +
                ", client=" + client +
                '}';
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public ConnectionState getState() {
        return state.get();
    }

    public void setState(ConnectionState state) {
        this.state.set(state);
    }

    public StringProperty getNameProperty() {
        return name;
    }

    public ObjectProperty<?> getStateProperty() {
        return state;
    }

    public RedisClient getClient() {
        return client;
    }

    public void setClient(RedisClient client) {
        this.client = client;
    }

    @Override
    public RedisConnection clone() {
        RedisConnection clone = new RedisConnection();
        clone.setName(getName());
        clone.setHost(getHost());
        clone.setPort(getPort());
        clone.setAuth(getAuth());
        return clone;
    }

    public boolean opened() {
        return openedProperty.get();
    }

    public void setOpened(boolean opened) {
        openedProperty.set(opened);
    }

    public enum ConnectionState {
        CONNECTED, DISCONNECTED, CONNECTING
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RedisConnection that = (RedisConnection) o;
        return port == that.port &&
                Objects.equals(name, that.name) &&
                Objects.equals(host, that.host) &&
                Objects.equals(auth, that.auth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, host, port, auth);
    }
}
