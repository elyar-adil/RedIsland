package me.elyar.redisland.redis;

import java.util.HashMap;
import java.util.Map;

public class RedisInfoUtil {

    public static Map<String, String> getProperties(String value) {
        Map<String, String> properties = new HashMap<>();
        String[] splitted = value.split(",");
        for(String property : splitted) {
            String[] keyValue = property.split("=", 2);
            if(keyValue.length == 2){
                properties.put(keyValue[0], keyValue[1]);
            }
        }
        return properties;
    }
    public static Map<Integer, Integer> getDatabaseKeyNumberMap(Map<String, String> info) {
        Map<Integer, Integer> databaseKeyNumberMap = new HashMap<>();
        for (Map.Entry<String, String> entry : info.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.matches("^db\\d+$")) {
                int db = Integer.parseInt(key.substring(2));
                Map<String, String> properties = RedisInfoUtil.getProperties(value);
                int keys = Integer.parseInt(properties.get("keys"));
                databaseKeyNumberMap.put(db, keys);
            }
        }
        return databaseKeyNumberMap;
    }
    public static int totalKeyCount(Map<String, String> info) {
        Map<Integer, Integer> map = getDatabaseKeyNumberMap(info);
        int totalKeys = 0;
        for(int keys: map.values()) {
            totalKeys+=keys;
        }
        return totalKeys;
    }
}
