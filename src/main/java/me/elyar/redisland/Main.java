package me.elyar.redisland;


import me.elyar.redisland.gui.App;

//
// jlink --output minimal-jre --add-modules java.base,java.desktop,java.logging,java.scripting,java.sql,java.xml,jdk.unsupported,jdk.crypto.ec
public class Main {
    public static void main(String[] args) {
        App.main(args);
    }
}