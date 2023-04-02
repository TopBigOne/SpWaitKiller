package com.knightboot.spwaitkiller;

import androidx.annotation.Nullable;

import java.util.LinkedList;

/**
 * created by Knight-ZXW on 2021/9/14
 * // 替换 QueueWork 类中sFinishers
 *
 * android 8.0 之后 使用的是LinkedList
 */
public class ProxyFinishersList<T> extends LinkedList<T> {


    // 替换 QueueWork 类中sFinishers
    private final LinkedList<T> sFinishers;

    public ProxyFinishersList(LinkedList<T> sFinishers) {
        this.sFinishers = sFinishers;
    }

    /**
     * always return null，to aVoid block;
     *
     * @return
     */
    @Nullable
    @Override
    public T poll() {
        return null;
    }

    @Override
    public boolean add(T t) {
        return sFinishers.add(t);
    }

    @Override
    public boolean remove(@Nullable Object o) {
        return sFinishers.remove(o);
    }

    @Override
    public boolean isEmpty() {
        return true;
    }
}
