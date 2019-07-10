package com.bob.bus.eventbus;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * 实例
 * public class Activity {
 *   //EventBus注册
 *   @Override
 *   protected void onCreate(Bundle savedInstanceState) {
 *       super···
 *       EventBus.getDefault().register(this));
 *   }
 *   //EventBus接受post事件
 *   @Subscribe(threadMode = ThreadMode.MAIN)
 *   public void onGetMessage(MessageWrap message) {
 *       ···
 *   }
 *   //EventBus注销
 *   @Override
 *   protected void onDestroy() {
 *      super.onDestroy();
 *      EventBus.getDefault().unregister(this);
 *    }
 * }
 *
 * public class Test {
 *     //在Test的某个方法内 post 事件
 *     EventBus.getDefault().post(MessageWrap.getInstance(msg));
 * }
 */
public class EventBus {

    //线程相关联-->本地数据
    private final ThreadLocal<PostingThreadState> currentPostingThreadState =
            new ThreadLocal<PostingThreadState>() {
                @Override
                protected PostingThreadState initialValue() {
                    return new PostingThreadState();
                }
            };

    //EventBus配置类
    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
    //主线程 MainHandler支撑相关
    private final MainThreadSupport mainThreadSupport;
    //缓存
    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<>();
    //这里的Class<?> 为订阅事件的类型 也就是EventType类型
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    private final Map<Object, List<Class<?>>> typesBySubscriber;
    private final Map<Class<?>, Object> stickyEvents;
    private final SubscriberMethodFinder subscriberMethodFinder;
    private final Poster mainThreadPoster;
    private final BackgroundPoster backgroundPoster;
    private final AsyncPoster asyncPoster;
    private final SubscriberMethodFinder subscriberMethodFinder;
    private final ExecutorService executorService;
    private final boolean throwSubscriberException;
    private final boolean logSubscriberExceptions;
    private final boolean logNoSubscriberMessages;
    private final boolean sendSubscriberExceptionEvent;
    private final boolean sendNoSubscriberEvent;
    private final boolean eventInheritance;
    private final int indexCount;

    //构造方法
    public EventBus() {
        this(DEFAULT_BUILDER);
    }

    EventBus(EventBusBuilder builder) {
        subscriptionsByEventType = new HashMap<>();
        subscriberMethodFinder = new SubscriberMethodFinder(builder.subscriberInfoIndexes, builder.strictMethodVerification, builder.ignoreGeneratedIndex);
        typesBySubscriber = new HashMap<>();
        stickyEvents = new ConcurrentHashMap<>();
    }



    /**
     * @Desc 单例
     */
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

    //注册EventBus的 注册者  是一个对象
    public void register(Object subscriber) {
        //获取该对象的Class
        Class<?> subscriberClass = subscriber.getClass();
        /** 寻找Class类总所有被@Subscribe修饰的方法 并解析成{@link SubscribeMethod} **/
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
        PostingThreadState postingThreadState = currentPostingThreadState.get();

        List<Object> eventQueue = postingThreadState.eventQueue;
        eventQueue.add(event);

        if (!postingThreadState.isPosting) {
            //设置是否为主线程 通过MainThreadSupport 辅助类 得到
            postingThreadState.isMainThread = isMainThread();
            //正在提交 任务
            postingThreadState.isPosting = true;
            if (postingThreadState.canceled) {
                throw new EventBusException("");
            }

            try {
                while (!eventQueue.isEmpty()) {
                    postSingleEvent(eventQueue.remove(0), postingThreadState);
                }
            }finally {
                postingThreadState.isPosting = false;
                postingThreadState.isMainThread = false;
            }

        }
    }

    private boolean isMainThread() {
        return mainThreadSupport != null ? mainThreadSupport.isMainThread() : true;
    }


    /******************** 订阅事件处理 ********************/
    private void postSingleEvent(Object event, PostingThreadState postingState) {
        //事件类型
        Class<?> eventClass = event.getClass();
        //
        boolean subscriptionFound = false;
        if (eventInheritance) {

        } else {
            postSingleEventForEventType(event, postingState, eventClass);
        }

    }

    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {

                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted = false;
                try {
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }

                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;

    }

    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        switch (subscription.subscriberMethod.threadMode) {
            case MAIN:
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                }
            case POSTING:
                invokeSubscriber(subscription, event);
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

    ExecutorService getExecutorService() {
        return null;
    }


    /**
     * @desc 内部类 记录线程状态
     */
    final static class PostingThreadState {
        //Object 订阅事件对象 就是订阅方法中的参数对象
        final List<Object> eventQueue = new ArrayList<>();
        boolean isPosting;
        boolean isMainThread;
        Subscription subscription;
        Object event;
        boolean canceled;
    }

}
