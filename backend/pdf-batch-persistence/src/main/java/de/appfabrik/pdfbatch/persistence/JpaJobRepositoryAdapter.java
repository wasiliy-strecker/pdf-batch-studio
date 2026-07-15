package de.appfabrik.pdfbatch.persistence;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.JobRepository;
import de.appfabrik.pdfbatch.core.JobStatus;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class JpaJobRepositoryAdapter implements JobRepository {
    private static final EnumSet<JobStatus> ACTIVE_STATUSES =
            EnumSet.of(
                    JobStatus.QUEUED,
                    JobStatus.PROCESSING,
                    JobStatus.PACKAGING,
                    JobStatus.CANCELLING);

    private final SpringDataBatchJobRepository repository;

    public JpaJobRepositoryAdapter(SpringDataBatchJobRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public BatchJob save(BatchJob job) {
        BatchJobEntity entity = repository.findById(job.id())
                .map(existing -> {
                    existing.updateFrom(job);
                    return existing;
                })
                .orElseGet(() -> BatchJobEntity.fromDomain(job));
        return repository.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BatchJob> findById(UUID id) {
        return repository.findById(id).map(BatchJobEntity::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveJobs() {
        return repository.countByStatusIn(ACTIVE_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCancellationRequested(UUID id) {
        return repository.existsByIdAndStatus(id, JobStatus.CANCELLING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BatchJob> findExpiredBefore(Instant cutoff) {
        return repository.findByExpiresAtLessThanEqualOrderByExpiresAtAsc(cutoff).stream()
                .map(BatchJobEntity::toDomain)
                .toList();
    }
}
