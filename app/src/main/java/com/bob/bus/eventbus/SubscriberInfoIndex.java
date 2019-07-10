package com.bob.bus.eventbus;

/**
 * Created by xhb on 2019/6/30.
 */

public interface SubscriberInfoIndex {

    SubscriberInfo getSubscriberInfo(Class<?> subscriberClass);

}
