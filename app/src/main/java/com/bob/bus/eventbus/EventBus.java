package com.bob.bus.eventbus;

import java.util.List;

/**
 * Created by xhb on 2019/6/19.
 */

public class EventBus {

    static volatile EventBus defaultInstance;

    public static EventBus getDefault() {
        if (defaultInstance == null) {
            synchronized (EventBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new EventBus();
                }
            }
        }
        return defaultInstance;

    }

    public void register(Object subscriber) {
        Class<?> subscriberClass = subscriber.getClass();

        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
        //this因为是单例
        synchronized (this) {
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                subscribe(subscriber, subscriberMethod);
            }
        }

    }

    /**
     * @desc 订阅
     */
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {

    }

    private final SubscriberMethodFinder subscriberMethodFinder;
    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
    //构造方法
    public EventBus() {
        this(DEFAULT_BUILDER);
    }

    EventBus(EventBusBuilder builder) {
        subscriberMethodFinder = new SubscriberMethodFinder(

        );
    }

}
