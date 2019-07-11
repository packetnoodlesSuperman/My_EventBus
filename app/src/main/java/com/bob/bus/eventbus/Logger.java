package com.bob.bus.eventbus;

import java.util.logging.Level;

public interface Logger {

    //输出日志
    void log(Level level, String msg);
    void log(Level level, String msg, Throwable throwable);

    //Android日志
    public static class AndroidLogger implements Logger {

        @Override
        public void log(Level level, String msg) {

        }

        @Override
        public void log(Level level, String msg, Throwable throwable) {

        }

        public static boolean isAndroidLogAvailable() {
            return false;
        }
    }

}
