package me.elyar.redisland.client;

import me.elyar.redisland.redis.resp.type.RespArray;
import me.elyar.redisland.redis.resp.type.RespString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ScanIterator {
    private String SCAN_SCRIPT = "local a={}local b=ARGV[1]local c=ARGV[2]local d=tonumber(ARGV[3])local e=tonumber(ARGV[4])local f=false;local g=0;repeat local h=redis.call('SCAN',b,'MATCH',c,'COUNT',d)b=h[1]local i=h[2]for j,k in ipairs(i)do g=g+1;a[#a+1]=k;a[#a+1]=redis.call('TYPE',k)end;if b=='0'or g>=e then f=true end until f;local l={}l[1]=b;l[2]=a;return l";
    private final String pattern;
    private final RedisClient client;
    private String cursor = "0";
    private int matchCount = 3;
    private int totalCount = 100;

    private int index = 0;

    private List<Pair<String, RedisType>> data;

    public ScanIterator(RedisClient client, String pattern) throws IOException {
        this.client = client;
        this.pattern = pattern;

        loadMoreData();
    }

    private void loadMoreData() throws IOException {
        List<String> sendArgument = List.of(cursor, pattern, String.valueOf(matchCount), String.valueOf(totalCount));
        RespArray result = (RespArray) client.eval(SCAN_SCRIPT, null, sendArgument);
        RespArray keyTypeData = ((RespArray) result.get(1));
        cursor = ((RespString) result.get(0)).getValue();
        data = new ArrayList<>(keyTypeData.size());
        for (int i = 0; i < keyTypeData.size(); i += 2) {
            String key = ((RespString) keyTypeData.get(i)).getValue();
            String typeString = ((RespString) keyTypeData.get(i + 1)).getValue();
            RedisType type = RedisType.valueOf(typeString.toUpperCase());
            Pair<String, RedisType> keyTypePair = new Pair<>(key, type);
            data.add(keyTypePair);
        }
        index = 0;
    }

    public boolean hasNext() {
        return index < data.size() || !this.cursor.equals("0");
    }

    public Pair<String, RedisType> next() throws IOException {
        if (index >= data.size() && !this.cursor.equals("0")) {
            loadMoreData();
        }
        return data.get(index++);
    }
}
