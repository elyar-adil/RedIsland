package me.elyar.redisland.redis.resp.type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * RESP数组
 *
 * @author e1y4r
 */
public class RespArray<T extends RespType> extends RespType implements Iterable<T> {
    // 用于存放数据
    private List<T> array;

    /**
     * 无参构造函数
     */
    public RespArray() {
        array = new ArrayList<>();
    }

    /**
     * 根据容量初始化
     *
     * @param initialCapacity 初始容量
     */
    public RespArray(int initialCapacity) {
        array = new ArrayList<>(initialCapacity);
    }

    /**
     * 添加数据
     *
     * @param respType 要添加的数据
     */
    public void add(T respType) {
        array.add(respType);
    }

    /**
     * 获取数据
     *
     * @param index 要获取数据的下标
     * @return index对应的数据
     */
    public T get(int index) {
        return array.get(index);
    }

    /**
     * 包含数据的个数
     *
     * @return 数据个数
     */
    public int size() {
        return array.size();
    }

    @Override
    public String toString() {
        return "RespArray{" +
                "array=" + array +
                '}';
    }

    @Override
    public Iterator<T> iterator() {
        return array.iterator();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        array.forEach(action);
    }

    @Override
    public Spliterator<T> spliterator() {
        return array.spliterator();
    }
}
