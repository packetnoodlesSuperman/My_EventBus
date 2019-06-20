package com.bob.bus.eventbus;

public interface Poster {

    void enqueue(Subscription subscription, Object event);

}
