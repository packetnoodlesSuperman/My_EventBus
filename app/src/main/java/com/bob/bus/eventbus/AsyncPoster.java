package com.bob.bus.eventbus;

/**
 * @Desc 异步task Runnable
 */
public class AsyncPoster implements Runnable, Poster{

    private final PendingPostQueue queue;
    private final EventBus eventBus;

    AsyncPoster(EventBus eventBus) {
        this.eventBus = eventBus;
        this.queue = new PendingPostQueue();
    }

    @Override
    public void enqueue(Subscription subscription, Object event) {
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
        queue.enqueue(pendingPost);
        eventBus.getExecutorService().execute(this);
    }

    @Override
    public void run() {
        PendingPost pendingPost = queue.poll();

        if (pendingPost == null) {
            throw new IllegalStateException("No Pending post available");
        }

        eventBus.invokeSubscriber(pendingPost);
    }
}
