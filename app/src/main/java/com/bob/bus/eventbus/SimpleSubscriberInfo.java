package com.bob.bus.eventbus;

import org.greenrobot.eventbus.meta.SubscriberMethodInfo;

/**
 * Created by xhb on 2019/6/30.
 */

public class SimpleSubscriberInfo extends AbstractSubscriberInfo {

    private final SubscriberMethodInfo[] methodInfos;

    public SimpleSubscriberInfo(
            Class subscriberClass,
            boolean shouldCheckSuperclass,
            SubscriberMethodInfo[] methodInfos
    ) {
        super(subscriberClass, null, shouldCheckSuperclass);
        this.methodInfos = methodInfos;
    }

    @Override
    public Class<?> getSubscriberClass() {
        return null;
    }

    @Override
    public SubscriberMethod[] getSubscriberMethods() {
        int length = methodInfos.length;
        SubscriberMethod[] methods = new SubscriberMethod[length];
        for (int i = 0; i < length; i++) {
            SubscriberMethodInfo info = methodInfos[i];


        }
        return methods;
    }
}
