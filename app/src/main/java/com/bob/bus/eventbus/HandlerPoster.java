package com.bob.bus.eventbus;


import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class HandlerPoster extends Handler implements Poster {

    final PendingPostQueue queue;
    final int maxMillisInsideHandleMessage;
    final EventBus eventBus;

    public HandlerPoster(EventBus eventBus, Looper looper, int maxMillisInsideHandleMessage) {
        super(looper);
        this.eventBus = eventBus;
        this.maxMillisInsideHandleMessage = maxMillisInsideHandleMessage;
        queue = new PendingPostQueue();
    }

    @Override
    public void enqueue(Subscription subscription, Object event) {

    }

    @Override
    public void handleMessage(Message msg) {

    }
}
