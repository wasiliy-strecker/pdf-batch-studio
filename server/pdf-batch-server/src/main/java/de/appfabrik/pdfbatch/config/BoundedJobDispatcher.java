package de.appfabrik.pdfbatch.config;

import de.appfabrik.pdfbatch.core.JobCapacityException;
import de.appfabrik.pdfbatch.core.JobDispatchPort;
import de.appfabrik.pdfbatch.core.JobWorker;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class BoundedJobDispatcher implements JobDispatchPort {
    private final ThreadPoolExecutor executor;
    private final JobWorker worker;
    private final int capacity;

    public BoundedJobDispatcher(JobWorker worker, int workers, int queueCapacity) {
        if (workers < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("Worker and queue capacity must be positive");
        }
        this.worker = worker;
        capacity = workers + queueCapacity;
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(
                        runnable, "pdf-batch-worker-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        executor = new ThreadPoolExecutor(
                workers,
                workers,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void dispatch(UUID jobId) {
        try {
            executor.execute(() -> worker.process(jobId));
        } catch (RejectedExecutionException exception) {
            throw new JobCapacityException(capacity);
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
