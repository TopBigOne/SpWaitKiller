package com.knightboot.spwaitkiller;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.LinkedList;

/**
 * created by Knight-ZXW on 2021/9/14
 */
class ProxySWork<T> extends LinkedList<T> {
    private static final String        TAG = "ProxySWork:";
    private final        LinkedList<T> proxy;

    // 创建一个新的Hander,并将sWork中的任务提交到这个Handler去执行，从而实现了无阻塞运行.
    private final Handler sHandler;

    private final AboveAndroid12Processor aboveAndroid12Processor;

    public ProxySWork(LinkedList<T> proxy, Looper looper, AboveAndroid12Processor aboveAndroid12Processor) {
        this.proxy = proxy;

        String name = looper.getThread().getName();
        Log.d(TAG, "ProxySWork: looper in thread is :" + name);
        sHandler = new Handler(looper);
        this.aboveAndroid12Processor = aboveAndroid12Processor;
    }

    // is thread safe
    @NonNull
    @Override
    public Object clone() {
        // <=31
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            delegateWork();
            return new LinkedList<T>();
        } else {
            return proxy.clone();
        }
    }

    private void delegateWork() {// Outbound
        if (proxy.size() == 0) {
            return;
        }
        LinkedList<Runnable> works = (LinkedList<Runnable>) proxy.clone();
        proxy.clear();

        Thread myself_thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long start_time = System.currentTimeMillis();
                Log.d(TAG, "delegateWork : start_time :" + start_time);
                Log.d(TAG, "delegateWork : in thread : " + Thread.currentThread().getName());
                for (Runnable w : works) {
                    w.run();
                }
                long end_time = System.currentTimeMillis();
                Log.d(TAG, "delegateWork : end_time :" + end_time);
                long all_time = end_time - start_time;
                Log.d(TAG, "delegateWork : all_time :" + all_time);
            }
        }, "myself_thread");

        myself_thread.start();


/*
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "delegateWork : in thread : " + Thread.currentThread().getName());

                for (Runnable w : works) {
                    w.run();
                }

            }
        });

        */

    }

    class AnotherThread extends Thread {
        @Override
        public void run() {


        }
    }

    @Override
    public boolean add(T t) {
        return proxy.add(t);
    }

    @Override
    public int size() {
        //Android 12 change:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            delegateWork();
            this.aboveAndroid12Processor.reProxySWork();
            return 0;
        } else {
            return proxy.size();
        }
    }

    // is thread safe
    @Override
    public void clear() {
        proxy.clear();
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    /**
     * Android 12及以上版本的特殊回调处理
     */
    interface AboveAndroid12Processor {

        /**
         * 重新代理 sWork字段
         */
        public void reProxySWork();

    }
}

