package com.bob.bus.eventbus;

import java.util.logging.Level;

public interface Logger {

    void log(Level level, String msg);
    void log(Level level, String msg, Throwable throwable);


    public static class AndroidLogger implements Logger {

        @Override
        public void log(Level level, String msg) {

        }

        @Override
        public void log(Level level, String msg, Throwable throwable) {

        }
    }

}
