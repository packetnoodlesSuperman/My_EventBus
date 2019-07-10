package com.bob.bus.eventbus;

/**
 * Created by xhb on 2019/6/30.
 */

public interface SubscriberInfo {

    Class<?> getSubscriberClass();

    SubscriberMethod[] getSubscriberMethods();

    SubscriberInfo getSuperSubscriberInfo();

    boolean shouldCheckSuperclass();

}
