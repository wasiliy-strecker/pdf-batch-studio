package de.appfabrik.pdfbatch.desktop.workflow;

import de.appfabrik.pdfbatch.core.JobCapacityException;
import de.appfabrik.pdfbatch.core.JobDispatchPort;
import de.appfabrik.pdfbatch.core.JobWorker;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class DesktopJobDispatcher implements JobDispatchPort, AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final JobWorker worker;

    public DesktopJobDispatcher(JobWorker worker) {
        this.worker = worker;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "pdf-batch-desktop-worker");
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void dispatch(UUID jobId) {
        try {
            executor.execute(() -> worker.process(jobId));
        } catch (RejectedExecutionException exception) {
            throw new JobCapacityException(1);
        }
    }

    @Override
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
