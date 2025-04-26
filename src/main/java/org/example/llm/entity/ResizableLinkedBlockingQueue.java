package org.example.llm.entity;

import java.util.concurrent.LinkedBlockingQueue;

public class ResizableLinkedBlockingQueue<E> extends LinkedBlockingQueue<E> {
    private volatile int capacity;

    public ResizableLinkedBlockingQueue(int initialCapacity) {
        super(initialCapacity);
        this.capacity = initialCapacity;
    }

    public synchronized void setCapacity(int newCapacity) {
        this.capacity = newCapacity;
        // 唤醒等待线程
        if (size() < newCapacity) {
            notifyAll();
        }
    }

    @Override
    public boolean offer(E e) {
        return size() < capacity && super.offer(e);
    }

    public double capacity() {
        return capacity;
    }
}