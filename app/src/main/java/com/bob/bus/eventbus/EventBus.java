package com.bob.bus.eventbus;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
        Class<?> eventType = subscriberMethod.eventType;
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod);

        //一个事件 会映射 很多订阅方法
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            if (subscriptions.contains(newSubscription)) {
                //抛异常


            }

        }

        //优先级排列
        int size = subscriptions.size();
        for (int i = 0; i <= size; i++) {
            //优先级排列
            if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
                subscriptions.add(i, newSubscription);
                break;
            }
        }

        //为一个订阅者 映射很多订阅事件
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        subscribedEvents.add(eventType);


        //判断粘性事件
        if (subscriberMethod.sticky) {
            //如果是粘性 则开始执行订阅方法


            //在postSticky的时候 stickyEvents会添加黏性事件
            Object stickyEvent = stickyEvents.get(eventType);
            //checkPostStickyEventToSubscription(newSubscription, stickyEvent);
        }
    }

    public void post(Object event) {

    }


    //这里的Class<?> 为订阅事件的类型 也就是EventType类型
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    private final Map<Object, List<Class<?>>> typesBySubscriber;
    private final Map<Class<?>, Object> stickyEvents;

    private final SubscriberMethodFinder subscriberMethodFinder;
    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
    //构造方法
    public EventBus() {
        this(DEFAULT_BUILDER);
    }

    EventBus(EventBusBuilder builder) {
        subscriberMethodFinder = new SubscriberMethodFinder(

                true);
        subscriptionsByEventType = new HashMap<>();
        typesBySubscriber = new HashMap<>();
        stickyEvents = new ConcurrentHashMap<>();
    }

    /******************** 订阅事件处理 ********************/
    private void postSingleEvent(Object event, PostingThreadState postingState) {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;

        postSingleEventForEventType(event, postingState, eventClass);

    }

    private void postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {

    }

    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        switch (subscription.subscriberMethod.threadMode) {
            case MAIN:
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                }

                break;
        }
    }

    private void invokeSubscriber(Subscription subscription, Object event) {
        try {
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        } catch (InvocationTargetException e) {
            handleSubscriberException(subscription, event, e.getCause());
        }
    }

    private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {
        if (event instanceof SubscriberExceptionEvent) {

        }
    }


    /**
     * @desc 内部类
     */
    final static class PostingThreadState {

    }

}
