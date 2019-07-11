package com.bob.bus.eventbus;

/**
 * Created by xhb on 2019/6/30.
 */

public interface SubscriberInfo {

    //获取订阅者的Class
    Class<?> getSubscriberClass();

    //订阅方法
    SubscriberMethod[] getSubscriberMethods();

    //订阅者的Class的父类的SubscriberInfo
    SubscriberInfo getSuperSubscriberInfo();

    //是否解析父类的状态
    boolean shouldCheckSuperclass();

}
