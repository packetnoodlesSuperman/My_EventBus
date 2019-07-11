package com.bob.bus.eventbus;

import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Desc 配置类
 */
public class EventBusBuilder {
    //默认线程池
    private static final ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    public ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;

    boolean logSubscriberExceptions = true;
    boolean logNoSubscriberMessages = true;
    boolean sendSubscriberExceptionEvent = true;
    boolean sendNoSubscriberEvent = true;
    boolean throwSubscriberException;
    //表示我们自定义的待发布消息事件是否允许继承,，默认情况下eventInheritance==true
    boolean eventInheritance = true;
    boolean ignoreGeneratedIndex;
    boolean strictMethodVerification;

    List<Class<?>> skipMethodVerificationForClasses;
    List<SubscriberInfoIndex> subscriberInfoIndexes;
    MainThreadSupport mainThreadSupport;

    /**
     * @Desc APT策略
     */
    public EventBusBuilder addIndex(SubscriberInfoIndex index) {
        if (subscriberInfoIndexes == null) {
            subscriberInfoIndexes = new ArrayList<>();
        }
        subscriberInfoIndexes.add(index);
        return this;
    }

    //获取日志
    Logger logger;
    public Logger getLogger() {
        if (logger != null) {
            return logger;
        } else {
            return null;
        }
    }

    //线程支持
    public MainThreadSupport getMainThreadSupport() {
        if (mainThreadSupport != null) {
            return mainThreadSupport;
        } else if (Logger.AndroidLogger.isAndroidLogAvailable()) {
            Object looperOrNull = getAndroidMainLooperOrNull();
            return looperOrNull == null? null :
                    new MainThreadSupport.AndroidHandlerMainThreadSupport((Looper)looperOrNull);
        } else {
            return null;
        }
    }

    private Object getAndroidMainLooperOrNull() {
        try {
            return Looper.getMainLooper();
        } catch (RuntimeException e) {

            return null;
        }
    }

    public EventBus build() { return new EventBus(this); }
}
