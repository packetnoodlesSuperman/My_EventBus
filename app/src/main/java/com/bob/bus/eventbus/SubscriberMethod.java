package com.bob.bus.eventbus;

import java.lang.reflect.Method;

/**
 * Created by xhb on 2019/6/19.
 */

public class SubscriberMethod {

    final Method method;
    final ThreadMode threadMode;
    final Class<?> eventType;
    final int priority;
    final boolean sticky;

    String methodString;


    public SubscriberMethod(Method method, ThreadMode threadMode, Class<?> eventType, int priority, boolean sticky) {
        this.method = method;
        this.threadMode = threadMode;
        this.eventType = eventType;
        this.priority = priority;
        this.sticky = sticky;
    }
}
