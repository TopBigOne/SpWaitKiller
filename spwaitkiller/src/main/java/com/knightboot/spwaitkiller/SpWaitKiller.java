package com.knightboot.spwaitkiller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * created by Knight-ZXW on 2021/9/14
 */
public class SpWaitKiller {
    private static final String TAG = "SpWaitKiller:";


    private HiddenApiExempter hiddenApiExempter;

    private boolean working;

    private final boolean isNeverWaitingFinishQueue;
    private final boolean isNeverProcessWorkOnMainThread;

    private final UnExpectExceptionCatcher unExpectExceptionCatcher;
    private       int                      targetSdkVersion = 0;
    private       Context                  mContext;

    private SpWaitKiller(SpWaitKiller.Builder builder) {
        // 使用隐藏API： Landroid/app/QueuedWork
        if (builder.hiddenApiExempter == null) {
            builder.hiddenApiExempter = new DefaultHiddenApiExempter();
        }
        if (builder.unExpectExceptionCatcher == null) {
            builder.unExpectExceptionCatcher = new UnExpectExceptionCatcher() {
                @Override
                public void onException(Throwable ex) {
                    Log.e("SpWaitKillerException", "catch Exception \n" + Log.getStackTraceString(ex));
                }
            };
        }
        this.hiddenApiExempter = builder.hiddenApiExempter;
        this.isNeverProcessWorkOnMainThread = builder.neverProcessWorkOnMainThread;
        this.isNeverWaitingFinishQueue = builder.neverWaitingFinishQueue;
        this.mContext = builder.context;
        this.unExpectExceptionCatcher = builder.unExpectExceptionCatcher;
        this.targetSdkVersion = this.mContext.getApplicationInfo().targetSdkVersion;


    }

    public static SpWaitKiller.Builder builder(Context context) {
        return new Builder(context);
    }


    /**
     * inner: invoke realWork()
     */
    public void startWork() {
        Log.d(TAG, "startWork: ");
        try {
            if (working) {
                return;
            }
            realWork();
            working = true;
        } catch (Exception e) {
            unExpectExceptionCatcher.onException(e);
        }
    }

    private void realWork() throws Exception {
        Log.d(TAG, "realWork: ");
        Class QueuedWorkClass = Class.forName("android.app.QueuedWork");

        if (isNeverWaitingFinishQueue) {
            // android 小于26
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Field sPendingWorkFinishersField = QueuedWorkClass.getDeclaredField("sPendingWorkFinishers");
                sPendingWorkFinishersField.setAccessible(true);

                // 并发
                ConcurrentLinkedQueue sPendingWorkFinishers = (ConcurrentLinkedQueue) sPendingWorkFinishersField.get(null);
                // 使用我们自己的 变量
                ProxyFinishersLinkedList proxyedSFinishers = new ProxyFinishersLinkedList(sPendingWorkFinishers);
                sPendingWorkFinishersField.set(null, proxyedSFinishers);

            } else {
                // private static final LinkedList<Runnable> sFinishers = new LinkedList<>();
                Field sFinishersField = QueuedWorkClass.getDeclaredField("sFinishers");
                sFinishersField.setAccessible(true);
                // the OS` sFinisher
                LinkedList sFinishers = (LinkedList) sFinishersField.get(null);
                // 将系统的 sFinisher ，主要是里面包含了需要执行的Task，
                ProxyFinishersList proxyedSFinishers = new ProxyFinishersList(sFinishers);
                sFinishersField.set(null, proxyedSFinishers);
            }
        }

        if (isNeverProcessWorkOnMainThread) {
            // 通过调用 getHandler函数
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return;
            }

            if (targetSdkVersion >= Build.VERSION_CODES.R) {
                this.hiddenApiExempter.exempt(mContext);
            }
            QueueWorksWorkFieldHooker queueWorksWorkFieldHooker = new QueueWorksWorkFieldHooker();
            queueWorksWorkFieldHooker.proxyWork();
        }
    }

    private static class QueueWorksWorkFieldHooker implements ProxySWork.AboveAndroid12Processor {

        private boolean reflectionFailed = false;
        /**
         * sLock对象， 操作
         */
        private Object  sLock            = null;
        private Field   sWorkField;
        private Looper  mLooper;

        @SuppressLint("SoonBlockedPrivateApi")
        public QueueWorksWorkFieldHooker() {
            try {

                // 获取 QueuedWork 的 handle 和 looper；
                Class  QueuedWorkClass = Class.forName("android.app.QueuedWork");
                Method method          = QueuedWorkClass.getDeclaredMethod("getHandler");
                method.setAccessible(true);
                Handler handler = (android.os.Handler) method.invoke(null);
                mLooper = handler.getLooper();

                // 源码：private static LinkedList<Runnable> sWork = new LinkedList<>();
                sWorkField = QueuedWorkClass.getDeclaredField("sWork");
                sWorkField.setAccessible(true);
                // private static final Object sLock = new Object();
                Field sLockField = QueuedWorkClass.getDeclaredField("sLock");
                sLockField.setAccessible(true);
                sLock = sLockField.get(null);
            } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException | NoSuchMethodException | InvocationTargetException e) {
                reflectionFailed = true;
            }

        }


        @Override
        public void reProxySWork() {
            //Android12开始,sWork字段在每次执行ProcessPendingWork时，sWork字段都会重新指向一个新的集合对象
            //因此需要重新代理
            proxyWork();
        }

        private void proxyWork() {
            if (reflectionFailed) {
                return;
            }
            synchronized (sLock) {
                //Android12以下，sWork自始至终是同一个对象
                try {
                    // 原本要执行的 Runnable 集合
                    LinkedList<Runnable> sWork      = (LinkedList) sWorkField.get(null);
                    ProxySWork           sWorkProxy = new ProxySWork(sWork, mLooper, this);
                    sWorkField.set(null, sWorkProxy);
                } catch (IllegalAccessException e) {
                    reflectionFailed = true;
                }
            }
        }
    }

    public static class Builder {
        private boolean                  neverWaitingFinishQueue;
        private boolean                  neverProcessWorkOnMainThread;
        private UnExpectExceptionCatcher unExpectExceptionCatcher;


        private Builder(Context context) {
            this.context = context;
            this.neverWaitingFinishQueue = true;
            this.neverProcessWorkOnMainThread = true;
        }

        Context           context;
        HiddenApiExempter hiddenApiExempter;

        public Builder hiddenApiExempter(HiddenApiExempter hiddenApiExempter) {
            this.hiddenApiExempter = hiddenApiExempter;
            return this;
        }

        public Builder neverWaitingFinishQueue(boolean neverWaitingFinishQueue) {
            this.neverWaitingFinishQueue = neverWaitingFinishQueue;
            return this;
        }

        public Builder unExpectExceptionCatcher(UnExpectExceptionCatcher unExpectExceptionCatcher) {
            this.unExpectExceptionCatcher = unExpectExceptionCatcher;
            return this;
        }

        public Builder neverProcessWorkOnMainThread(boolean neverProcessWorkOnMainThread) {
            this.neverProcessWorkOnMainThread = neverProcessWorkOnMainThread;
            return this;
        }


        public SpWaitKiller build() {
            return new SpWaitKiller(this);
        }

    }


}
