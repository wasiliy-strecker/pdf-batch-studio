package de.appfabrik.pdfbatch.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository {
    BatchJob save(BatchJob job);

    Optional<BatchJob> findById(UUID id);

    void deleteById(UUID id);

    long countActiveJobs();

    boolean isCancellationRequested(UUID id);

    List<BatchJob> findExpiredBefore(Instant cutoff);
}
