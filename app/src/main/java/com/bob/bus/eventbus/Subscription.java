package com.bob.bus.eventbus;

public class Subscription {

    //订阅者
    final Object subscriber;
    //订阅方法
    final SubscriberMethod subscriberMethod;

    public Subscription(Object subscriber, SubscriberMethod subscriberMethod) {
        this.subscriber = subscriber;
        this.subscriberMethod = subscriberMethod;
    }
}
