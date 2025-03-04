package io.yupiik.metrics.metricsscrapper.common;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadFactoryImpl implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger();

    public ThreadFactoryImpl(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(final Runnable worker) {
        final Thread thread = new Thread(worker, prefix + "-" + counter.incrementAndGet());
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.setDaemon(false);
        return thread;
    }
}
