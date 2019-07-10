package com.bob.bus.eventbus;

public class PendingPostQueue {

    private PendingPost head;
    private PendingPost tail;

    synchronized void enqueue(PendingPost pendingPost) {
        if (pendingPost == null) {
            throw new NullPointerException();
        }

        if (tail != null) {
            tail.next = pendingPost;
        }
        notifyAll();
    }

}
