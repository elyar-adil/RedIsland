package me.elyar.redisland.client;

public class AuthError extends Exception {
    public AuthError(String message) {
        super(message);
    }
}
