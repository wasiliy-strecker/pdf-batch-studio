package de.appfabrik.pdfbatch.desktop.workflow;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.JobRepository;
import de.appfabrik.pdfbatch.core.JobStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryJobRepository implements JobRepository {
    private final Map<UUID, BatchJob> jobs = new HashMap<>();

    @Override
    public synchronized BatchJob save(BatchJob job) {
        BatchJob current = jobs.get(job.id());
        if (current != null
                && current.status() == JobStatus.CANCELLING
                && job.status() != JobStatus.CANCELLING
                && job.status() != JobStatus.CANCELLED) {
            return copy(current);
        }
        BatchJob stored = copy(job);
        jobs.put(stored.id(), stored);
        return copy(stored);
    }

    @Override
    public synchronized Optional<BatchJob> findById(UUID id) {
        return Optional.ofNullable(jobs.get(id)).map(InMemoryJobRepository::copy);
    }

    @Override
    public synchronized void deleteById(UUID id) {
        jobs.remove(id);
    }

    @Override
    public synchronized long countActiveJobs() {
        return jobs.values().stream().filter(job -> job.status().isActive()).count();
    }

    @Override
    public synchronized boolean isCancellationRequested(UUID id) {
        BatchJob job = jobs.get(id);
        return job != null && job.status() == JobStatus.CANCELLING;
    }

    @Override
    public synchronized List<BatchJob> findExpiredBefore(Instant cutoff) {
        return jobs.values().stream()
                .filter(job -> !job.expiresAt().isAfter(cutoff))
                .map(InMemoryJobRepository::copy)
                .toList();
    }

    private static BatchJob copy(BatchJob job) {
        return BatchJob.restore(
                job.id(),
                job.status(),
                job.pageCount(),
                job.pdfFields(),
                job.csvDelimiter(),
                job.csvHeaders(),
                job.totalRows(),
                job.mappings(),
                job.filenamePattern(),
                job.processedRows(),
                job.successfulRows(),
                job.failedRows(),
                job.failureCode(),
                job.failureMessage(),
                job.createdAt(),
                job.updatedAt(),
                job.expiresAt());
    }
}
