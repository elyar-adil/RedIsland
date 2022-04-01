package me.elyar.redisland.redis.resp.type;

/**
 * RESP字符串
 *
 * @author e1y4r
 */
public class RespString extends RespType {
    // 字符串的值
    private final String value;

    /**
     * 初始化字符串的值
     *
     * @param value 字符串的值
     */
    public RespString(String value) {
        this.value = value;
    }

    /**
     * 获取字符串的值
     *
     * @return 字符串的值
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "RespString{" +
                "value='" + value + '\'' +
                '}';
    }
}
