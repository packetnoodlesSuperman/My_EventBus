package com.bob.bus.eventbus;

/**
 * Created by xhb on 2019/6/23.
 */

public class BackgroundPoster implements Runnable, Poster {

    private final PendingPostQueue queue;
    private final EventBus eventBus;

    private volatile boolean executorRunning;

    BackgroundPoster(EventBus eventBus) {
        this.eventBus = eventBus;
        queue = new PendingPostQueue();
    }

    @Override
    public void enqueue(Subscription subscription, Object event) {
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
        synchronized (this) {

            queue.enqueue(pendingPost);

            if (!executorRunning) {
                executorRunning = true;
                eventBus.getExecutorService().execute(this);
            }
        }


    }

    @Override
    public void run() {

        while (true) {
            try {
                PendingPost pendingPost = queue.poll(1000);
                if (pendingPost == null) {
                    synchronized (this) {
                        pendingPost = queue.poll();
                        if (pendingPost == null) {
                            executorRunning = false;
                            return;
                        }

                    }
                }
                eventBus.invokeSubscriber(pendingPost);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
