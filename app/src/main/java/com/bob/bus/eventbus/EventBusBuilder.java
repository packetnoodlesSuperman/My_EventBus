package com.bob.bus.eventbus;

import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Desc 配置类
 */
public class EventBusBuilder {
    private static final ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    public ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;


    Logger logger;
    public Logger getLogger() {
        if (logger != null) {
            return logger;
        } else {
            return null;
        }
    }

    MainThreadSupport mainThreadSupport;

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
}
