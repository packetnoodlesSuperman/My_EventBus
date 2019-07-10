package com.bob.bus.eventbus;

/**
 * @Desc 异步task Runnable
 */
public class AsyncPoster implements Runnable, Poster{

    private final PendingPostQueue queue;
    private final EventBus eventBus;

    public AsyncPoster(PendingPostQueue queue, EventBus eventBus) {
        this.queue = queue;
        this.eventBus = eventBus;
    }

    @Override
    public void enqueue(Subscription subscription, Object event) {
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
        queue.enquue(pendingPost);
        eventBus.getExecutorService().execute(this);
    }

    @Override
    public void run() {

    }
}
